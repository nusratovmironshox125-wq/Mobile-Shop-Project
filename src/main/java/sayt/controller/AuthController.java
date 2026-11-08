package sayt.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import sayt.dto.identity.*;
import sayt.security.ShopPrincipal;
import sayt.service.AccountService;
import sayt.service.RateLimits;

@RestController
@RequestMapping("/api/v1")
class AuthController {
    private final AccountService accounts;
    private final RateLimits limits;
    private final int csrfMax;

    AuthController(AccountService accounts, RateLimits limits, @Value("${shop.limits.csrf-source}") int csrfMax) {
        this.accounts = accounts;
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
    AdminResponse me(@AuthenticationPrincipal ShopPrincipal user) {
        return accounts.me(user.id());
    }

    @PostMapping("/auth/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void change(@AuthenticationPrincipal ShopPrincipal user, @Valid @RequestBody ChangePasswordRequest password,
                HttpServletRequest request) {
        accounts.change(user.id(), password);
        request.getSession().invalidate();
    }
}
