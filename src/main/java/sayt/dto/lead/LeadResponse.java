package sayt.dto.lead;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public record LeadResponse(long id, UUID number, String name, String phone, long productId, long variantId, String productName,
    String sku, String variantLabel, BigDecimal price, BigDecimal originalPrice, String currency,
    boolean installment, String installmentTerms, String status, String comment, String privacyVersion,
    OffsetDateTime createdAt, OffsetDateTime closedAt, OffsetDateTime anonymizedAt, boolean retentionReview,
    long version) {}
