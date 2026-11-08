package sayt.service;

import java.time.Instant;
import sayt.dto.catalog.VariantEdit;
import sayt.entity.Variant;
import sayt.exception.Problem;

public final class PricingService {
    private PricingService() {}

    static void validateDiscount(VariantEdit in) {
        if ((in.salePrice() == null && (in.saleFrom() != null || in.saleUntil() != null))
            || (in.salePrice() != null && in.salePrice().compareTo(in.price()) >= 0)
            || (in.saleFrom() != null && in.saleUntil() != null && !in.saleFrom().isBefore(in.saleUntil()))) {
            throw Problem.bad("INVALID_DISCOUNT");
        }
    }

    static boolean activeSale(Variant v, Instant now) {
        return v.getSalePrice() != null && (v.getSaleFrom() == null || !now.isBefore(v.getSaleFrom()))
            && (v.getSaleUntil() == null || now.isBefore(v.getSaleUntil()));
    }
}
