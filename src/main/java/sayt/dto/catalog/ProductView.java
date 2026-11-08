package sayt.dto.catalog;

import jakarta.validation.constraints.*;

public record ProductView(long id, long categoryId, String brand, String name, String slug,
    String description, String status, long version) {}
