package sayt.dto.catalog;

import jakarta.validation.constraints.*;

public record ImageView(long id, String url, int width, int height, String alt, Long variantId) {}
