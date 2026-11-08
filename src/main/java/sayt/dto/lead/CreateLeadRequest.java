package sayt.dto.lead;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

public record CreateLeadRequest(@NotBlank @Size(min=2,max=80) String name, @NotBlank @Size(max=45) String phone,
    @NotNull @Positive Long variantId, @NotNull @Positive @Digits(integer=14,fraction=2) BigDecimal expectedPrice,
    @NotBlank @Size(max=80) String privacyVersion, @AssertTrue boolean informed) {}
