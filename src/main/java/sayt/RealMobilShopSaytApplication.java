package sayt;

import java.time.Clock;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RealMobilShopSaytApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(RealMobilShopSaytApplication.class);
        if (args.length == 1 && ("bootstrap-owner".equals(args[0]) || "recover-owner".equals(args[0]))) {
            app.setWebApplicationType(WebApplicationType.NONE);
            System.setProperty("shop.owner-command", args[0].equals("recover-owner") ? "recover" : "bootstrap");
            app.setDefaultProperties(Map.of("spring.main.banner-mode", "off"));
            app.run().close();
        } else {
            app.run(args);
        }
    }

    @Bean Clock clock() {
        return Clock.systemUTC();
    }
}
