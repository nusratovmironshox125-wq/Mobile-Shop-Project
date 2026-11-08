package sayt.dto.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record VariantView(long id, String sku, String color, Integer ramGb, Integer storageGb,
    String screen, String cpu, String gpu, String cameras, String battery, String sim, String os, String weight,
    String notes, BigDecimal price, BigDecimal originalPrice, String stock, boolean installment,
    String installmentTerms, long version, BigDecimal salePrice, Instant saleFrom, Instant saleUntil) {}
