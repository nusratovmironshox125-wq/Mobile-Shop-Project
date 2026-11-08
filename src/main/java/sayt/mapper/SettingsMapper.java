package sayt.mapper;

import sayt.dto.settings.ShopSettingsResponse;
import sayt.entity.ShopSettings;

public final class SettingsMapper {
    private SettingsMapper() {}

    public static ShopSettingsResponse view(ShopSettings s, String privacyVersion, String privacyRu, String privacyUz) {
        return new ShopSettingsResponse(s.getName(), s.getPhone(), s.getTelegram(), s.getAddress(), s.getDirections(), s.getMapUrl(), s.getHours(), s.getVersion(),
            privacyVersion, privacyRu, privacyUz);
    }
}
