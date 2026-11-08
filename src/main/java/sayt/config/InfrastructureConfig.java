package sayt.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class InfrastructureConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean HttpClient telegramHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
