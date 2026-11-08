package sayt.leads;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class Telegram {
    record Delivery(boolean sent, String error, int retryAfter) {}

    private final URI endpoint;
    private final String chat;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    Telegram(@Value("${shop.telegram.token}") String token,
           @Value("${shop.telegram.chat}") String chat) {
        this.chat = chat;
        if (token.isBlank() || chat.isBlank()) endpoint = null;
        else {
            if (!token.matches("[0-9]+:[A-Za-z0-9_-]+") || !chat.matches("-?[0-9]+")) {
                throw new IllegalArgumentException("Invalid Telegram configuration");
            }
            endpoint = URI.create("https://api.telegram.org/bot" + token + "/sendMessage");
        }
    }

    boolean enabled() { return endpoint != null; }

    Delivery send(String message) {
        String body = "chat_id=" + URLEncoder.encode(chat, StandardCharsets.UTF_8)
            + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
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

}
