package sayt.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import org.springframework.data.domain.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.dto.identity.*;
import sayt.entity.AdminUser;
import sayt.exception.Problem;
import sayt.mapper.AdminMapper;
import sayt.repository.AdminUserRepository;
import sayt.security.ShopPrincipal;

@Service
public class AccountService implements UserDetailsService {
    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    AccountService(AdminUserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    public static String normalize(String login) {
        return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public ShopPrincipal loadUserByUsername(String login) {
        var user = users.findByLogin(normalize(login)).orElseThrow(() -> new UsernameNotFoundException("ACCOUNT"));
        return new ShopPrincipal(user.getId(), user.getLogin(), user.getPasswordHash(), user.getRole(), user.isEnabled(),
            user.isMustChange(), user.getAuthVersion());
    }

    public AdminResponse me(long id) {
        return AdminMapper.view(find(id));
    }

    public boolean sessionValid(ShopPrincipal principal) {
        return users.existsByIdAndEnabledTrueAndAuthVersion(principal.id(), principal.authVersion());
    }

    @Transactional
    public void loggedIn(long id) {
        users.markLogin(id);
        audit.record(id, "LOGIN", "account:" + id);
    }

    public Page<AdminResponse> list(int page) {
        return users.findAll(PageRequest.of(page, 24, Sort.by("id"))).map(AdminMapper::view);
    }

    @Transactional
    public IssuedPasswordResponse create(CreateAdminRequest input, long actor) {
        var user = new AdminUser();
        user.setLogin(normalize(input.login()));
        user.setName(input.name().strip());
        user.setRole("ADMIN");
        String temporary = issue(user);
        users.saveAndFlush(user);
        audit.record(actor, "ADMIN_CREATED", "account:" + user.getId());
        return new IssuedPasswordResponse(AdminMapper.view(user), temporary);
    }

    @Transactional
    public AdminResponse update(long id, UpdateAdminRequest input, long actor) {
        var user = admin(id);
        Problem.version(input.version(), user.getVersion());
        if (user.isEnabled() != input.enabled()) {
            user.setEnabled(input.enabled());
            user.setAuthVersion(user.getAuthVersion() + 1);
            audit.record(actor, user.isEnabled() ? "ADMIN_ENABLED" : "ADMIN_DISABLED", "account:" + id);
        }
        return AdminMapper.view(users.saveAndFlush(user));
    }

    @Transactional
    public IssuedPasswordResponse reset(long id, long actor) {
        var user = admin(id);
        String temporary = issue(user);
        users.flush();
        audit.record(actor, "PASSWORD_RESET", "account:" + id);
        return new IssuedPasswordResponse(AdminMapper.view(user), temporary);
    }

    @Transactional
    public void change(long id, ChangePasswordRequest input) {
        var user = find(id);
        if (!encoder.matches(input.currentPassword(), user.getPasswordHash())
            || encoder.matches(input.newPassword(), user.getPasswordHash())) {
            throw Problem.bad("PASSWORD_INVALID");
        }
        user.setPasswordHash(encoder.encode(input.newPassword()));
        user.setMustChange(false);
        user.setAuthVersion(user.getAuthVersion() + 1);
        audit.record(id, "PASSWORD_CHANGED", "account:" + id);
    }

    @Transactional
    public void bootstrap(String login, String name, String password, boolean recover) {
        login = normalize(login);
        if (!login.matches("[a-z0-9._-]{3,60}") || password == null || password.isBlank()
            || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("Set OWNER_LOGIN and OWNER_PASSWORD (12–128 characters)");
        }
        users.lockOwnerBootstrap();
        var user = users.findByLogin(login).orElse(null);
        if (recover) {
            if (user == null || !"OWNER".equals(user.getRole())) throw new IllegalStateException("OWNER not found");
            user.setAuthVersion(user.getAuthVersion() + 1);
            user.setMustChange(false);
        } else {
            if (name == null || name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Set OWNER_NAME");
            if (users.existsByRole("OWNER")) throw new IllegalStateException("OWNER already exists");
            user = new AdminUser();
            user.setLogin(login);
            user.setName(name.strip());
            user.setRole("OWNER");
        }
        user.setPasswordHash(encoder.encode(password));
        users.saveAndFlush(user);
        audit.record(user.getId(), recover ? "OWNER_RECOVERED" : "OWNER_BOOTSTRAPPED", "account:" + user.getId());
    }

    private AdminUser find(long id) {
        return users.findById(id).orElseThrow(Problem::missing);
    }

    private AdminUser admin(long id) {
        var user = find(id);
        if (!"ADMIN".equals(user.getRole())) throw Problem.conflict("OWNER_PROTECTED");
        return user;
    }

    private String issue(AdminUser user) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String temporary = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setPasswordHash(encoder.encode(temporary));
        user.setMustChange(true);
        user.setAuthVersion(user.getAuthVersion() + 1);
        return temporary;
    }

}
