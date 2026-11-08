package sayt.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import sayt.service.AccountService;

@Configuration
class AccountConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    @org.springframework.context.annotation.Profile("!db-inspect")
    @ConditionalOnProperty("shop.owner-command")
    ApplicationRunner ownerCommand(AccountService accounts) {
        return args -> accounts.bootstrap(System.getenv("OWNER_LOGIN"), System.getenv("OWNER_NAME"),
            System.getenv("OWNER_PASSWORD"), "recover".equals(System.getProperty("shop.owner-command")));
    }
}
