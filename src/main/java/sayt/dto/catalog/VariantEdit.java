package sayt.dto.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record VariantEdit(@NotBlank @Size(max=80) @Pattern(regexp="[a-zA-Z0-9._-]+") String sku,
    @Size(max=100) String color, @Positive Integer ramGb, @Positive Integer storageGb,
    @Size(max=100) String screen, @Size(max=100) String cpu, @Size(max=100) String gpu,
    @Size(max=150) String cameras, @Size(max=100) String battery, @Size(max=100) String sim,
    @Size(max=100) String os, @Size(max=100) String weight, @Size(max=1000) String notes,
    @NotNull @Positive @Digits(integer=14,fraction=2) BigDecimal price,
    @NotNull @Pattern(regexp="IN_STOCK|OUT_OF_STOCK") String stock, @NotNull Boolean installment,
    @Size(max=1000) String installmentTerms, @Positive @Digits(integer=14,fraction=2) BigDecimal salePrice,
    Instant saleFrom, Instant saleUntil, Long version) {}
