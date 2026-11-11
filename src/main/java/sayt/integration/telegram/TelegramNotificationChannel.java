package sayt.integration.telegram;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Qualifier;
import sayt.dto.notification.LeadNotification;
import sayt.service.NotificationChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class TelegramNotificationChannel implements NotificationChannel {

    private final URI endpoint;
    private final String chat;
    private final HttpClient client;
    private final tools.jackson.databind.ObjectMapper json;

    TelegramNotificationChannel(@Value("${shop.telegram.token}") String token,
           @Value("${shop.telegram.chat}") String chat,
                                @Qualifier("telegramHttpClient") HttpClient client, tools.jackson.databind.ObjectMapper json) {
        this.chat = chat;
        this.client = client;
        this.json=json;
        if (token.isBlank() || chat.isBlank()) endpoint = null;
        else {
            if (!token.matches("[0-9]+:[A-Za-z0-9_-]+") || !chat.matches("-?[0-9]+")) {
                throw new IllegalArgumentException("Invalid Telegram configuration");
            }
            endpoint = URI.create("https://api.telegram.org/bot" + token + "/sendMessage");
        }
    }

    @Override
    public boolean enabled() { return endpoint != null; }

    @Override
    public Delivery send(LeadNotification message) {
        if (!enabled()) return new Delivery(false, "NOTIFICATIONS_DISABLED", 0);
        String body = "chat_id=" + URLEncoder.encode(chat, StandardCharsets.UTF_8)
            + "&text=" + URLEncoder.encode(format(message), StandardCharsets.UTF_8);
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, info -> new LimitedBody());
            var result=json.readTree(response.body());
            int status=response.statusCode();
            if (status == 200 && result.path("ok").asBoolean(false)) return new Delivery(true, null, 0);
            int retry = response.headers().firstValueAsLong("Retry-After").stream()
                .mapToInt(value -> (int) Math.clamp(value, 0, 86400)).findFirst().orElse(0);
            retry=Math.max(retry,Math.clamp(result.path("parameters").path("retry_after").asInt(0),0,86400));
            return new Delivery(false, status==200 ? "TELEGRAM_REJECTED" : "TELEGRAM_HTTP_" + status, retry);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new Delivery(false, "TELEGRAM_INTERRUPTED", 0);
        } catch (IOException | RuntimeException error) {
            return new Delivery(false, "TELEGRAM_UNAVAILABLE", 0);
        }
    }

    static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final java.util.concurrent.CompletableFuture<String> body=new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        public java.util.concurrent.CompletionStage<String> getBody() { return body; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { this.subscription=subscription; subscription.request(1); }
        public void onNext(java.util.List<java.nio.ByteBuffer> chunks) {
            for(var chunk:chunks) {
                if(bytes.size()+chunk.remaining()>65536) { subscription.cancel(); body.completeExceptionally(new IOException("Response too large")); return; }
                byte[] part=new byte[chunk.remaining()];chunk.get(part);bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toString(StandardCharsets.UTF_8)); }
    }

    private static String format(LeadNotification message) {
        return """
            %s
            Заявка / Ariza %s
            %s · %s
            %s · %s · %s
            %s UZS
            %s
            """.formatted(message.shop(), message.number(), message.name(), message.phone(), message.productName(),
                message.variantLabel(), message.sku(), message.price().stripTrailingZeros().toPlainString(),
                message.createdAt().atZoneSameInstant(ZoneId.of("Asia/Tashkent"))
                    .format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm VV")));
    }
}
