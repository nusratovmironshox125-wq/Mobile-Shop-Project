package sayt.controller.admin;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sayt.dto.settings.*;
import sayt.security.ShopPrincipal;
import sayt.service.SettingsService;

@RestController
@RequestMapping("/api/v1")
class AdminSettingsController {
    private final SettingsService settings;
    AdminSettingsController(SettingsService settings) { this.settings = settings; }

    @GetMapping("/admin/settings")
    ShopSettingsResponse get() { return settings.get(); }

    @PatchMapping("/admin/settings")
    ShopSettingsResponse update(@Valid @RequestBody ShopSettingsEdit edit, @AuthenticationPrincipal ShopPrincipal actor) {
        return settings.update(edit, actor.id());
    }
}
