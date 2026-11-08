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
        var channel = new TelegramNotificationChannel("", "", client);
        assertFalse(channel.enabled());
        assertFalse(channel.send(message).sent());
        verifyNoInteractions(client);
    }

    @Test
    void successAndThrottlingFollowDeliveryContract() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
            .thenReturn(response);
        when(response.statusCode()).thenReturn(200, 429);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After", List.of("120")), (k, v) -> true));
        var channel = new TelegramNotificationChannel("1:test", "-100", client);
        assertTrue(channel.send(message).sent());
        var rejected = channel.send(message);
        assertFalse(rejected.sent());
        assertEquals("TELEGRAM_HTTP_429", rejected.error());
        assertEquals(120, rejected.retryAfter());
    }
}
