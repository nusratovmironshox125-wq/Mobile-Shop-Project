package sayt.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PricingTests {
    @Test
    void startsInclusivelyAndEndsExclusively() {
        var v = new Variant();
        v.salePrice = new BigDecimal("2500000.00");
        v.saleFrom = Instant.parse("2026-11-08T06:00:00Z");
        v.saleUntil = v.saleFrom.plusSeconds(60);
        assertFalse(Catalog.activeSale(v, v.saleFrom.minusNanos(1)));
        assertTrue(Catalog.activeSale(v, v.saleFrom));
        assertTrue(Catalog.activeSale(v, v.saleUntil.minusNanos(1)));
        assertFalse(Catalog.activeSale(v, v.saleUntil));
    }

    @Test
    void supportsIndefiniteAndRemovedDiscounts() {
        var v = new Variant();
        v.salePrice = BigDecimal.ONE;
        assertTrue(Catalog.activeSale(v, Instant.EPOCH));
        v.salePrice = null;
        assertFalse(Catalog.activeSale(v, Instant.EPOCH));
    }
}
