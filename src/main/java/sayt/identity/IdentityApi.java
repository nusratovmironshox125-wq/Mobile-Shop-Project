package sayt.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import sayt.common.RateLimits;

@RestController
@RequestMapping("/api/v1")
class IdentityApi {
    private final Accounts accounts;
    private final JdbcClient db;
    private final RateLimits limits;
    private final int csrfMax;

    IdentityApi(Accounts accounts, JdbcClient db, RateLimits limits,
                @Value("${shop.limits.csrf-source}") int csrfMax) {
        this.accounts = accounts;
        this.db = db;
        this.limits = limits;
        this.csrfMax = csrfMax;
    }

    @GetMapping("/auth/csrf")
    Map<String, String> csrf(HttpServletRequest request) {
        limits.acquire("csrf:" + request.getRemoteAddr(), csrfMax, Duration.ofMinutes(1));
        var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/auth/me")
    Accounts.View me(@AuthenticationPrincipal ShopPrincipal user) {
        return accounts.me(user.id());
    }

    @PostMapping("/auth/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void change(@AuthenticationPrincipal ShopPrincipal user, @Valid @RequestBody Accounts.Password password,
                HttpServletRequest request) {
        accounts.change(user.id(), password);
        request.getSession().invalidate();
    }

    @GetMapping("/admin/users")
    Object users(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page) {
        return accounts.list(page);
    }

    @PostMapping("/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    Accounts.Issued create(@AuthenticationPrincipal ShopPrincipal user, @Valid @RequestBody Accounts.Create input) {
        return accounts.create(input, user.id());
    }

    @PatchMapping("/admin/users/{id}")
    Accounts.View update(@AuthenticationPrincipal ShopPrincipal user, @PathVariable long id,
                         @Valid @RequestBody Accounts.Update input) {
        return accounts.update(id, input, user.id());
    }

    @PostMapping("/admin/users/{id}/reset-password")
    Accounts.Issued reset(@AuthenticationPrincipal ShopPrincipal user, @PathVariable long id) {
        return accounts.reset(id, user.id());
    }

    @GetMapping("/admin/audit")
    Object audit(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page) {
        return db.sql("""
            select id, actor_id, action, target, correlation_id, created_at
            from audit_event order by created_at desc, id desc limit 24 offset ?
            """).param(page * 24).query().listOfRows();
    }
}
