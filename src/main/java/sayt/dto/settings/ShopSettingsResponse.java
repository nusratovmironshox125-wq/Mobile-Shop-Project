package sayt.dto.settings;

import jakarta.validation.constraints.*;

public record ShopSettingsResponse(String name, String phone, String telegram, String address, String directions, String mapUrl,
    String hours, long version, String privacyVersion, String privacyRu, String privacyUz) {}
