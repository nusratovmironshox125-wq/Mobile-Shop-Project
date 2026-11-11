package sayt.dto.catalog;

import jakarta.validation.constraints.*;

public record ProductEdit(@NotNull @Positive Long categoryId, @NotBlank @Size(max=100) String brand,
    @NotBlank @Size(max=150) String name, @Size(max=150)
    @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
    @Size(max=5000) String description, @Pattern(regexp="DRAFT|PUBLISHED|ARCHIVED") String status, Long version) {}
