package sayt.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.filter.OncePerRequestFilter;
import sayt.exception.*;
import sayt.security.ShopPrincipal;
import sayt.service.AccountService;
import sayt.service.AuditService;
import sayt.service.RateLimits;

@Configuration
@ConditionalOnWebApplication
class SecurityConfig {
    @Bean
    SecurityFilterChain shopSecurityChain(HttpSecurity http, AccountService accounts, AuditService audit, RateLimits limits,
        @Value("${shop.limits.login-account}") int accountMax,
        @Value("${shop.limits.login-source}") int sourceMax,
        @Value("${server.port}") int publicPort,
        @Value("${management.server.port:-1}") int managementPort) throws Exception {
        String prefix = "/api/v1";
        http.authorizeHttpRequests(rules -> rules
            .requestMatchers("/actuator/**").access((auth, context) -> {
                var request = context.getRequest();
                boolean local = "127.0.0.1".equals(request.getRemoteAddr()) || "::1".equals(request.getRemoteAddr());
                return new org.springframework.security.authorization.AuthorizationDecision(local
                    && managementPort > 0 && managementPort != publicPort && request.getLocalPort() == managementPort);
            })
            .requestMatchers(prefix + "/admin/users/**", prefix + "/admin/audit",
                prefix + "/admin/notifications/**", prefix + "/admin/leads/*/anonymize").hasRole("OWNER")
            .requestMatchers(prefix + "/admin/**").hasAnyRole("ADMIN", "OWNER")
            .requestMatchers(prefix + "/auth/me", prefix + "/auth/change-password").authenticated()
            .requestMatchers(HttpMethod.GET, prefix + "/settings/public", prefix + "/categories",
                prefix + "/products", prefix + "/products/*", prefix + "/auth/csrf").permitAll()
            .requestMatchers(HttpMethod.POST, prefix + "/leads", prefix + "/auth/login", prefix + "/auth/logout").permitAll()
            .requestMatchers("/api/**").denyAll()
            .anyRequest().permitAll())
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.loginPage("/admin/login").loginProcessingUrl(prefix + "/auth/login")
                .successHandler((request, response, auth) -> {
                    var user = (ShopPrincipal) auth.getPrincipal();
                    request.getSession().setAttribute("signedInAt", System.currentTimeMillis());
                    accounts.loggedIn(user.id());
                    limits.clear("login:" + user.login());
                    response.setStatus(204);
                })
                .failureHandler((request, response, error) -> {
                    String login = AccountService.normalize(request.getParameter("username"));
                    limits.record("login:" + login, Duration.ofMinutes(15));
                    limits.record("login-ip:" + request.getRemoteAddr(), Duration.ofMinutes(15));
                    audit.record(null, "LOGIN_FAILED", null);
                    GlobalExceptionHandler.send(request, response, 401, "LOGIN_FAILED");
                }))
            .logout(logout -> logout.logoutUrl(prefix + "/auth/logout")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
            .exceptionHandling(handler -> handler
                .authenticationEntryPoint((request, response, error) -> GlobalExceptionHandler.send(request, response, 401, "AUTH_REQUIRED"))
                .accessDeniedHandler((request, response, error) -> GlobalExceptionHandler.send(request, response, 403,
                    error instanceof org.springframework.security.web.csrf.CsrfException ? "CSRF_INVALID" : "FORBIDDEN")))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(policy -> policy.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                    + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'")));
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                       FilterChain chain) throws ServletException, IOException {
                String path = request.getServletPath();
                response.setHeader("X-Correlation-Id", GlobalExceptionHandler.correlation(request).toString());
                if (path.equals("/favorites") || path.startsWith("/admin")) response.setHeader("X-Robots-Tag", "noindex");
                if ("POST".equals(request.getMethod()) && path.equals(prefix + "/auth/login")) {
                    String login = AccountService.normalize(request.getParameter("username"));
                    String password = request.getParameter("password");
                    if (!login.matches("[a-z0-9._-]{3,60}") || password == null || password.length() > 128) {
                        GlobalExceptionHandler.send(request, response, 401, "LOGIN_FAILED");
                        return;
                    }
                    try {
                        limits.check("login:" + login, accountMax);
                        limits.check("login-ip:" + request.getRemoteAddr(), sourceMax);
                    } catch (Problem error) {
                        response.setHeader("Retry-After", error.details.get("retryAfter").toString());
                        GlobalExceptionHandler.send(request, response, 429, error.code);
                        return;
                    }
                }
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (path.startsWith(prefix + "/") && auth != null && auth.getPrincipal() instanceof ShopPrincipal user) {
                    var session = request.getSession(false);
                    if (session == null || !(session.getAttribute("signedInAt") instanceof Long start)
                        || System.currentTimeMillis() - start >= Duration.ofHours(12).toMillis()
                        || !accounts.sessionValid(user)) {
                        if (session != null) session.invalidate();
                        SecurityContextHolder.clearContext();
                        GlobalExceptionHandler.send(request, response, 401, "AUTH_REQUIRED");
                        return;
                    }
                    if (user.mustChange() && path.startsWith(prefix + "/admin/")) {
                        GlobalExceptionHandler.send(request, response, 403, "PASSWORD_CHANGE_REQUIRED");
                        return;
                    }
                }
                chain.doFilter(request, response);
            }
        }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
