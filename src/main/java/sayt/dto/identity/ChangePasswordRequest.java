package sayt.dto.identity;

import jakarta.validation.constraints.*;

public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                       @NotBlank @Size(min = 12, max = 128) String newPassword) {}
