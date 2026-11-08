package sayt.dto.identity;

import jakarta.validation.constraints.*;

public record UpdateAdminRequest(@NotNull Boolean enabled, @NotNull Long version) {}
