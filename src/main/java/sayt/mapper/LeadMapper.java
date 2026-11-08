package sayt.mapper;

import java.time.OffsetDateTime;
import java.util.UUID;
import sayt.dto.lead.LeadResponse;

public final class LeadMapper {
    private LeadMapper() {}

    public static LeadResponse view(java.sql.ResultSet row, OffsetDateTime cutoff) throws java.sql.SQLException {
        var created = row.getObject("created_at", OffsetDateTime.class);
        var closed = row.getObject("closed_at", OffsetDateTime.class);
        var anonymized = row.getObject("anonymized_at", OffsetDateTime.class);
        return new LeadResponse(row.getLong("id"), row.getObject("public_number", UUID.class), row.getString("name"),
            row.getString("phone"), row.getLong("product_id"), row.getLong("variant_id"), row.getString("product_name"),
            row.getString("sku"), row.getString("variant_label"), row.getBigDecimal("price"),
            row.getBigDecimal("original_price"), row.getString("currency"), row.getBoolean("installment"),
            row.getString("installment_terms"), row.getString("status"), row.getString("comment"),
            row.getString("privacy_version"), created, closed, anonymized,
            closed == null && anonymized == null && created.isBefore(cutoff),
            row.getLong("version"));
    }
}
