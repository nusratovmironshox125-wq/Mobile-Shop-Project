package sayt.controller.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sayt.dto.identity.*;
import sayt.security.ShopPrincipal;
import sayt.service.AccountService;
import sayt.service.AuditService;

@RestController
@RequestMapping("/api/v1")
class AdminIdentityController {
    private final AccountService accounts;
    private final AuditService audit;

    AdminIdentityController(AccountService accounts, AuditService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @GetMapping("/admin/users")
    Page<AdminResponse> users(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page) {
        return accounts.list(page);
    }

    @PostMapping("/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    IssuedPasswordResponse create(@AuthenticationPrincipal ShopPrincipal user, @Valid @RequestBody CreateAdminRequest input) {
        return accounts.create(input, user.id());
    }

    @PatchMapping("/admin/users/{id}")
    AdminResponse update(@AuthenticationPrincipal ShopPrincipal user, @PathVariable long id,
                         @Valid @RequestBody UpdateAdminRequest input) {
        return accounts.update(id, input, user.id());
    }

    @PostMapping("/admin/users/{id}/reset-password")
    IssuedPasswordResponse reset(@AuthenticationPrincipal ShopPrincipal user, @PathVariable long id) {
        return accounts.reset(id, user.id());
    }

    @GetMapping("/admin/audit")
    sayt.dto.PageResult<Map<String, Object>> audit(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
        @RequestParam(required=false) @Size(max=60) String username,
        @RequestParam(required=false) @Size(max=80) String action,
        @RequestParam(required=false) @Size(max=120) String object,
        @RequestParam(required=false) java.time.LocalDate from,
        @RequestParam(required=false) java.time.LocalDate until) {
        return audit.list(page,username,action,object,from,until);
    }
}
