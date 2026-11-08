package sayt.dto.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record Selection(long productId, long variantId, String productName, String sku, String label,
    BigDecimal price, BigDecimal originalPrice, boolean installment, String installmentTerms) {}
