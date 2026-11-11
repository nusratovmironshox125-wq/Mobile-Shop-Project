package sayt.integration.telegram;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import sayt.dto.notification.LeadNotification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramNotificationChannelTests {
    private final HttpClient client = mock(HttpClient.class);
    private final LeadNotification message = new LeadNotification("Test shop", UUID.randomUUID(), "Test Contact",
        "+998901234568", "Model", "SKU", "Black", BigDecimal.TEN, OffsetDateTime.parse("2026-11-08T10:00:00Z"));

    @Test
    void disabledChannelDoesNotMakeHttpRequests() {
        var channel = new TelegramNotificationChannel("", "", client, tools.jackson.databind.json.JsonMapper.builder().build());
        assertFalse(channel.enabled());
        assertFalse(channel.send(message).sent());
        verifyNoInteractions(client);
    }

    @Test
    void responseBodyIsBounded() {
        var subscriber=new TelegramNotificationChannel.LimitedBody();
        var subscription=mock(java.util.concurrent.Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(java.nio.ByteBuffer.allocate(65537)));
        verify(subscription).cancel();
        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void successAndThrottlingFollowDeliveryContract() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(response);
        when(response.statusCode()).thenReturn(200, 429, 200);
        when(response.body()).thenReturn("{\"ok\":true}", "{\"ok\":false,\"parameters\":{\"retry_after\":180}}", "{\"ok\":false}");
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After", List.of("120")), (k, v) -> true));
        var channel = new TelegramNotificationChannel("1:test", "-100", client, tools.jackson.databind.json.JsonMapper.builder().build());
        assertTrue(channel.send(message).sent());
        var rejected = channel.send(message);
        assertFalse(rejected.sent());
        assertEquals("TELEGRAM_HTTP_429", rejected.error());
        assertEquals(180, rejected.retryAfter());
        assertFalse(channel.send(message).sent());
    }
}
