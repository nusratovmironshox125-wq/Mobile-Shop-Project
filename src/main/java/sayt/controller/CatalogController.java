package sayt.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import sayt.service.RateLimits;
import sayt.service.CatalogService;
import sayt.service.CategoryService;
import sayt.dto.catalog.*;

@RestController
@RequestMapping("/api/v1")
class CatalogController {
    private final CatalogService catalog;
    private final CategoryService categories;
    private final RateLimits limits;
    private final int searchLimit;
    private final int clientLimit;
    private final int clientBurst;

    CatalogController(CatalogService catalog, CategoryService categories, RateLimits limits, @Value("${shop.limits.search-source}") int searchLimit,
        @Value("${shop.limits.search-client}") int clientLimit, @Value("${shop.limits.search-burst}") int clientBurst) {
        this.catalog = catalog;
        this.categories = categories;
        this.limits = limits;
        this.searchLimit = searchLimit;
        this.clientLimit = clientLimit;
        this.clientBurst = clientBurst;
    }

    @GetMapping("/categories")
    List<CategoryView> categories() { return categories.categories(false); }

    @GetMapping("/products")
    Result list(@RequestParam(required=false) @Size(max=100) String category,
        @RequestParam(required=false) @Size(max=100) String q,
        @RequestParam(required=false) @PositiveOrZero @Digits(integer=14,fraction=2) BigDecimal minPrice,
        @RequestParam(required=false) @PositiveOrZero @Digits(integer=14,fraction=2) BigDecimal maxPrice,
        @RequestParam(defaultValue="false") boolean discounted,
        @RequestParam(defaultValue="new") @Size(max=12) String sort,
        @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
        @RequestParam(defaultValue="10") int size, HttpServletRequest request) {
        if (q != null && !q.isBlank()) {
            String source = request.getRemoteAddr();
            limits.acquire("search:" + source, searchLimit, Duration.ofMinutes(1));
            String marker = request.getHeader("X-Client-Marker");
            if (marker != null && marker.matches("[0-9a-fA-F-]{36}")) {
                limits.acquire("search-client:" + source + ":" + marker, clientLimit, Duration.ofMinutes(1));
                limits.acquire("search-burst:" + source + ":" + marker, clientBurst, Duration.ofSeconds(3));
            }
        }
        return catalog.list(category, q, minPrice, maxPrice, discounted, sort, page, 10, null);
    }

    @GetMapping("/products/by-ids")
    Result favorites(@RequestParam @Size(min=1,max=100) List<@Positive Long> ids) {
        return catalog.list(null, null, null, null, false, "new", 0, 100, ids);
    }

    @GetMapping("/products/{slug}")
    Detail detail(@PathVariable @Size(max=150) String slug) { return catalog.detail(slug); }
}
