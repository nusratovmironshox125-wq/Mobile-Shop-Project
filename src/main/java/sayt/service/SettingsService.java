package sayt.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.dto.settings.*;
import sayt.exception.Problem;
import sayt.mapper.SettingsMapper;
import sayt.repository.SettingsRepository;

@Service
public class SettingsService {
    private final SettingsRepository repository;
    private final AuditService audit;
    private final String privacyVersion;
    private final String privacyRu;
    private final String privacyUz;

    SettingsService(SettingsRepository repository, AuditService audit, @Value("${shop.privacy-version}") String privacyVersion,
        @Value("${shop.privacy-ru}") String privacyRu, @Value("${shop.privacy-uz}") String privacyUz) {
        this.repository = repository;
        this.audit = audit;
        this.privacyVersion = privacyVersion;
        this.privacyRu = privacyRu;
        this.privacyUz = privacyUz;
    }

    public ShopSettingsResponse get() { return SettingsMapper.view(repository.findById(1L).orElseThrow(), privacyVersion, privacyRu, privacyUz); }

    @Transactional
    public ShopSettingsResponse update(ShopSettingsEdit edit, long actor) {
        var settings = repository.findById(1L).orElseThrow();
        Problem.version(edit.version(), settings.getVersion());
        settings.setName(edit.name().strip());
        settings.setPhone(clean(edit.phone()));
        if (settings.getPhone() != null && !settings.getPhone().matches("\\+[1-9][0-9 ()-]{6,26}[0-9]")) {
            throw Problem.bad("PHONE_INVALID");
        }
        settings.setTelegram(clean(edit.telegram()));
        if (settings.getTelegram() != null) {
            String username = settings.getTelegram().replaceFirst("^(?:https://t\\.me/|@)", "");
            if (!username.matches("[A-Za-z][A-Za-z0-9_]{4,31}")) throw Problem.bad("TELEGRAM_INVALID");
            settings.setTelegram("https://t.me/" + username);
        }
        settings.setAddress(clean(edit.address()));
        settings.setDirections(clean(edit.directions()));
        settings.setMapUrl(map(clean(edit.mapUrl())));
        settings.setHours(clean(edit.hours()));
        repository.flush();
        audit.record(actor, "SETTINGS_CHANGED", "settings:1");
        return SettingsMapper.view(settings, privacyVersion, privacyRu, privacyUz);
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
