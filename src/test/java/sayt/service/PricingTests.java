package sayt.service;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import sayt.entity.Variant;
import static org.junit.jupiter.api.Assertions.*;

class PricingTests {
    @Test
    void startsInclusivelyAndEndsExclusively() {
        var v = new Variant();
        v.setSalePrice(new BigDecimal("2500000.00"));
        v.setSaleFrom(Instant.parse("2026-11-08T06:00:00Z"));
        v.setSaleUntil(v.getSaleFrom().plusSeconds(60));
        assertFalse(PricingService.activeSale(v, v.getSaleFrom().minusNanos(1)));
        assertTrue(PricingService.activeSale(v, v.getSaleFrom()));
        assertTrue(PricingService.activeSale(v, v.getSaleUntil().minusNanos(1)));
        assertFalse(PricingService.activeSale(v, v.getSaleUntil()));
    }

    @Test
    void supportsIndefiniteAndRemovedDiscounts() {
        var v = new Variant();
        v.setSalePrice(BigDecimal.ONE);
        assertTrue(PricingService.activeSale(v, Instant.EPOCH));
        v.setSalePrice(null);
        assertFalse(PricingService.activeSale(v, Instant.EPOCH));
    }
}
