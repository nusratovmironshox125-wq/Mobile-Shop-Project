package sayt.dto.catalog;

import jakarta.validation.constraints.*;

public record CategoryEdit(@NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=100) String slug,
    @NotBlank @Size(max=100) String nameRu, @NotBlank @Size(max=100) String nameUz,
    @NotNull Boolean enabled, @NotNull Integer sortOrder, Long version) {}
