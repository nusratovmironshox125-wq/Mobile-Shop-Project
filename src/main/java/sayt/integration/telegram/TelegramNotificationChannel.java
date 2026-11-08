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

    TelegramNotificationChannel(@Value("${shop.telegram.token}") String token,
           @Value("${shop.telegram.chat}") String chat,
                                @Qualifier("telegramHttpClient") HttpClient client) {
        this.chat = chat;
        this.client = client;
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
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) return new Delivery(true, null, 0);
            int retry = response.headers().firstValueAsLong("Retry-After").stream()
                .mapToInt(value -> (int) Math.clamp(value, 0, 86400)).findFirst().orElse(0);
            return new Delivery(false, "TELEGRAM_HTTP_" + response.statusCode(), retry);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new Delivery(false, "TELEGRAM_INTERRUPTED", 0);
        } catch (IOException | IllegalArgumentException error) {
            return new Delivery(false, "TELEGRAM_UNAVAILABLE", 0);
        }
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
