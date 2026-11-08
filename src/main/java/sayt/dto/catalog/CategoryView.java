package sayt.dto.catalog;

import jakarta.validation.constraints.*;

public record CategoryView(long id, String slug, String nameRu, String nameUz, String kind,
    boolean enabled, int sortOrder, long version) {}
