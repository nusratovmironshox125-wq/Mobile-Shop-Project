package sayt.dto.notification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LeadNotification(String shop, UUID number, String name, String phone, String productName, String sku,
               String variantLabel, BigDecimal price, OffsetDateTime createdAt) {}
