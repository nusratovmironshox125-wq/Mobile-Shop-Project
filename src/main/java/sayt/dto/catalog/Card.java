package sayt.dto.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record Card(long id, String slug, String brand, String name, long variantId, String color,
    Integer storageGb, BigDecimal price, BigDecimal originalPrice, String stock, boolean installment,
    String image, long variantCount) {}
