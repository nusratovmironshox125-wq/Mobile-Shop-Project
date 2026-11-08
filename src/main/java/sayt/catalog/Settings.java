package sayt.catalog;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import sayt.common.Audit;
import sayt.common.Problem;
import sayt.identity.ShopPrincipal;

@Entity
@Table(name = "shop_settings")
class ShopSettings {
    @Id Long id;
    String name;
    String phone;
    String telegram;
    String address;
    String directions;
    String mapUrl;
    String hours;
    @Version long version;
}

interface SettingsRepository extends JpaRepository<ShopSettings, Long> {}

@Service
public class Settings {
    record Edit(@NotBlank @Size(max=100) String name, @Size(max=30) String phone, @Size(max=200) String telegram,
        @Size(max=250) String address, @Size(max=1000) String directions, @Size(max=1000) String mapUrl,
        @Size(max=200) String hours, @NotNull Long version) {}
    record View(String name, String phone, String telegram, String address, String directions, String mapUrl,
        String hours, long version, String privacyVersion, String privacyRu, String privacyUz) {}

    private final SettingsRepository repository;
    private final Audit audit;
    private final String privacyVersion;
    private final String privacyRu;
    private final String privacyUz;

    Settings(SettingsRepository repository, Audit audit, @Value("${shop.privacy-version}") String privacyVersion,
        @Value("${shop.privacy-ru}") String privacyRu, @Value("${shop.privacy-uz}") String privacyUz) {
        this.repository = repository;
        this.audit = audit;
        this.privacyVersion = privacyVersion;
        this.privacyRu = privacyRu;
        this.privacyUz = privacyUz;
    }

    View get() { return view(repository.findById(1L).orElseThrow()); }

    @Transactional
    View update(Edit edit, long actor) {
        var settings = repository.findById(1L).orElseThrow();
        Problem.version(edit.version(), settings.version);
        settings.name = edit.name().strip();
        settings.phone = clean(edit.phone());
        if (settings.phone != null && !settings.phone.matches("\\+[1-9][0-9 ()-]{6,26}[0-9]")) {
            throw Problem.bad("PHONE_INVALID");
        }
        settings.telegram = clean(edit.telegram());
        if (settings.telegram != null) {
            String username = settings.telegram.replaceFirst("^(?:https://t\\.me/|@)", "");
            if (!username.matches("[A-Za-z][A-Za-z0-9_]{4,31}")) throw Problem.bad("TELEGRAM_INVALID");
            settings.telegram = "https://t.me/" + username;
        }
        settings.address = clean(edit.address());
        settings.directions = clean(edit.directions());
        settings.mapUrl = map(clean(edit.mapUrl()));
        settings.hours = clean(edit.hours());
        repository.flush();
        audit.record(actor, "SETTINGS_CHANGED", "settings:1");
        return view(settings);
    }

    private View view(ShopSettings s) {
        return new View(s.name, s.phone, s.telegram, s.address, s.directions, s.mapUrl, s.hours, s.version,
            privacyVersion, privacyRu, privacyUz);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    private static String map(String value) {
        if (value == null) return null;
        try {
            var uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                && Set.of("maps.google.com","www.google.com","maps.app.goo.gl","yandex.uz","yandex.com",
                    "2gis.uz","www.openstreetmap.org").contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                return uri.toASCIIString();
            }
        } catch (IllegalArgumentException ignored) {
            // Report an invalid map link using the public error code.
        }
        throw Problem.bad("MAP_INVALID");
    }
}

@RestController
@RequestMapping("/api/v1")
class SettingsApi {
    private final Settings settings;
    SettingsApi(Settings settings) { this.settings = settings; }

    @GetMapping({"/settings/public","/admin/settings"})
    Settings.View get() { return settings.get(); }

    @PatchMapping("/admin/settings")
    Settings.View update(@Valid @RequestBody Settings.Edit edit, @AuthenticationPrincipal ShopPrincipal actor) {
        return settings.update(edit, actor.id());
    }
}
