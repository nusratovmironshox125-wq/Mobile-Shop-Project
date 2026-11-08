package sayt.identity;

import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.common.Audit;
import sayt.common.Problem;

@Service
public class Accounts implements UserDetailsService {
    public record Create(@NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,60}") String login,
                         @NotBlank @Size(max = 100) String name) {}
    public record Update(@NotNull Boolean enabled, @NotNull Long version) {}
    public record Password(@NotBlank @Size(max = 128) String currentPassword,
                           @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    public record View(long id, String login, String name, String role, boolean enabled, boolean mustChange,
                       Instant createdAt, Instant lastLogin, long version) {}
    public record Issued(View account, String temporaryPassword) {}

    private final AdminUsers users;
    private final PasswordEncoder encoder;
    private final Audit audit;
    private final JdbcClient db;
    private final SecureRandom random = new SecureRandom();

    Accounts(AdminUsers users, PasswordEncoder encoder, Audit audit, JdbcClient db) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.db = db;
    }

    static String normalize(String login) {
        return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public ShopPrincipal loadUserByUsername(String login) {
        var user = users.findByLogin(normalize(login)).orElseThrow(() -> new UsernameNotFoundException("ACCOUNT"));
        return new ShopPrincipal(user.id, user.login, user.passwordHash, user.role, user.enabled,
            user.mustChange, user.authVersion);
    }

    public View me(long id) {
        return view(find(id));
    }

    public boolean sessionValid(ShopPrincipal principal) {
        return db.sql("select exists(select 1 from admin_user where id=? and enabled and auth_version=?)")
            .param(principal.id()).param(principal.authVersion()).query(Boolean.class).single();
    }

    @Transactional
    public void loggedIn(long id) {
        db.sql("update admin_user set last_login=now() where id=?").param(id).update();
        audit.record(id, "LOGIN", "account:" + id);
    }

    public Page<View> list(int page) {
        return users.findAll(PageRequest.of(page, 24, Sort.by("id"))).map(Accounts::view);
    }

    @Transactional
    public Issued create(Create input, long actor) {
        var user = new AdminUser();
        user.login = normalize(input.login());
        user.name = input.name().strip();
        user.role = "ADMIN";
        String temporary = issue(user);
        users.saveAndFlush(user);
        audit.record(actor, "ADMIN_CREATED", "account:" + user.id);
        return new Issued(view(user), temporary);
    }

    @Transactional
    public View update(long id, Update input, long actor) {
        var user = admin(id);
        Problem.version(input.version(), user.version);
        if (user.enabled != input.enabled()) {
            user.enabled = input.enabled();
            user.authVersion++;
            audit.record(actor, user.enabled ? "ADMIN_ENABLED" : "ADMIN_DISABLED", "account:" + id);
        }
        return view(users.saveAndFlush(user));
    }

    @Transactional
    public Issued reset(long id, long actor) {
        var user = admin(id);
        String temporary = issue(user);
        users.flush();
        audit.record(actor, "PASSWORD_RESET", "account:" + id);
        return new Issued(view(user), temporary);
    }

    @Transactional
    public void change(long id, Password input) {
        var user = find(id);
        if (!encoder.matches(input.currentPassword(), user.passwordHash)
            || encoder.matches(input.newPassword(), user.passwordHash)) {
            throw Problem.bad("PASSWORD_INVALID");
        }
        user.passwordHash = encoder.encode(input.newPassword());
        user.mustChange = false;
        user.authVersion++;
        audit.record(id, "PASSWORD_CHANGED", "account:" + id);
    }

    @Transactional
    public void bootstrap(String login, String name, String password, boolean recover) {
        login = normalize(login);
        if (!login.matches("[a-z0-9._-]{3,60}") || password == null || password.isBlank()
            || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("Set OWNER_LOGIN and OWNER_PASSWORD (12–128 characters)");
        }
        db.sql("select 1 from pg_advisory_xact_lock(78198312)").query(Integer.class).single();
        var user = users.findByLogin(login).orElse(null);
        if (recover) {
            if (user == null || !"OWNER".equals(user.role)) throw new IllegalStateException("OWNER not found");
            user.authVersion++;
            user.mustChange = false;
        } else {
            if (name == null || name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Set OWNER_NAME");
            if (Boolean.TRUE.equals(db.sql("select exists(select 1 from admin_user where role='OWNER')")
                .query(Boolean.class).single())) throw new IllegalStateException("OWNER already exists");
            user = new AdminUser();
            user.login = login;
            user.name = name.strip();
            user.role = "OWNER";
        }
        user.passwordHash = encoder.encode(password);
        users.saveAndFlush(user);
        audit.record(user.id, recover ? "OWNER_RECOVERED" : "OWNER_BOOTSTRAPPED", "account:" + user.id);
    }

    private AdminUser find(long id) {
        return users.findById(id).orElseThrow(Problem::missing);
    }

    private AdminUser admin(long id) {
        var user = find(id);
        if (!"ADMIN".equals(user.role)) throw Problem.conflict("OWNER_PROTECTED");
        return user;
    }

    private String issue(AdminUser user) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String temporary = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.passwordHash = encoder.encode(temporary);
        user.mustChange = true;
        user.authVersion++;
        return temporary;
    }

    private static View view(AdminUser user) {
        return new View(user.id, user.login, user.name, user.role, user.enabled, user.mustChange,
            user.createdAt, user.lastLogin, user.version);
    }
}

@Configuration
class AccountConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    @ConditionalOnProperty("shop.owner-command")
    ApplicationRunner ownerCommand(Accounts accounts) {
        return args -> accounts.bootstrap(System.getenv("OWNER_LOGIN"), System.getenv("OWNER_NAME"),
            System.getenv("OWNER_PASSWORD"), "recover".equals(System.getProperty("shop.owner-command")));
    }
}
