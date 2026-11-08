package sayt.dto.identity;

import jakarta.validation.constraints.*;

public record CreateAdminRequest(@NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,60}") String login,
                     @NotBlank @Size(max = 100) String name) {}
