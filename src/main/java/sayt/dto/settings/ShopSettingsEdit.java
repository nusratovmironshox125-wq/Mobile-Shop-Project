package sayt.dto.settings;

import jakarta.validation.constraints.*;

public record ShopSettingsEdit(@NotBlank @Size(max=100) String name, @Size(max=30) String phone, @Size(max=200) String telegram,
    @Size(max=250) String address, @Size(max=1000) String directions, @Size(max=1000) String mapUrl,
    @Size(max=200) String hours, @NotNull Long version) {}
