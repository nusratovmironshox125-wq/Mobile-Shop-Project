package sayt.controller;

import org.springframework.web.bind.annotation.*;
import sayt.dto.settings.ShopSettingsResponse;
import sayt.service.SettingsService;

@RestController
@RequestMapping("/api/v1/settings")
class SettingsController {
    private final SettingsService settings;
    SettingsController(SettingsService settings) { this.settings = settings; }

    @GetMapping("/public")
    ShopSettingsResponse get() { return settings.get(); }
}
