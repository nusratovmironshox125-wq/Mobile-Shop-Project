package sayt.catalog;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sayt.common.RateLimits;
import sayt.identity.ShopPrincipal;

@RestController
@RequestMapping("/api/v1")
class CatalogApi {
    private final Catalog catalog;
    private final RateLimits limits;
    private final int searchLimit;
    private final int clientLimit;
    private final int clientBurst;

    CatalogApi(Catalog catalog, RateLimits limits, @Value("${shop.limits.search-source}") int searchLimit,
        @Value("${shop.limits.search-client}") int clientLimit, @Value("${shop.limits.search-burst}") int clientBurst) {
        this.catalog = catalog;
        this.limits = limits;
        this.searchLimit = searchLimit;
        this.clientLimit = clientLimit;
        this.clientBurst = clientBurst;
    }

    @GetMapping("/categories")
    List<Catalog.CategoryView> categories() { return catalog.categories(false); }

    @GetMapping("/products")
    Catalog.Result list(@RequestParam(required=false) @Size(max=100) String category,
        @RequestParam(required=false) @Size(max=100) String q,
        @RequestParam(required=false) @PositiveOrZero @Digits(integer=14,fraction=2) BigDecimal minPrice,
        @RequestParam(required=false) @PositiveOrZero @Digits(integer=14,fraction=2) BigDecimal maxPrice,
        @RequestParam(defaultValue="false") boolean discounted,
        @RequestParam(defaultValue="new") @Size(max=12) String sort,
        @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
        @RequestParam(defaultValue="24") @Min(1) @Max(48) int size, HttpServletRequest request) {
        if (q != null && !q.isBlank()) {
            String source = request.getRemoteAddr();
            limits.acquire("search:" + source, searchLimit, Duration.ofMinutes(1));
            String marker = request.getHeader("X-Client-Marker");
            if (marker != null && marker.matches("[0-9a-fA-F-]{36}")) {
                limits.acquire("search-client:" + source + ":" + marker, clientLimit, Duration.ofMinutes(1));
                limits.acquire("search-burst:" + source + ":" + marker, clientBurst, Duration.ofSeconds(3));
            }
        }
        return catalog.list(category, q, minPrice, maxPrice, discounted, sort, page, size, null);
    }

    @GetMapping("/products/by-ids")
    Catalog.Result favorites(@RequestParam @Size(min=1,max=100) List<@Positive Long> ids) {
        return catalog.list(null, null, null, null, false, "new", 0, 100, ids);
    }

    @GetMapping("/products/{slug}")
    Catalog.Detail detail(@PathVariable @Size(max=150) String slug) { return catalog.detail(slug); }

    @GetMapping("/admin/categories")
    List<Catalog.CategoryView> adminCategories() { return catalog.categories(true); }

    @PostMapping("/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    Catalog.CategoryView createCategory(@Valid @RequestBody Catalog.CategoryEdit input,
                                       @AuthenticationPrincipal ShopPrincipal actor) {
        return catalog.saveCategory(null, input, actor.id());
    }

    @PatchMapping("/admin/categories/{id}")
    Catalog.CategoryView updateCategory(@PathVariable long id, @Valid @RequestBody Catalog.CategoryEdit input,
                                       @AuthenticationPrincipal ShopPrincipal actor) {
        return catalog.saveCategory(id, input, actor.id());
    }

    @GetMapping("/admin/products")
    Object adminProducts(@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page) {
        return catalog.adminProducts(page);
    }

    @GetMapping("/admin/products/{id}")
    Catalog.Detail adminDetail(@PathVariable long id) { return catalog.adminDetail(id); }

    @PostMapping("/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    Catalog.ProductView createProduct(@Valid @RequestBody Catalog.ProductEdit input,
                                     @AuthenticationPrincipal ShopPrincipal actor) {
        return catalog.saveProduct(null, input, actor.id());
    }

    @PatchMapping("/admin/products/{id}")
    Catalog.ProductView updateProduct(@PathVariable long id, @Valid @RequestBody Catalog.ProductEdit input,
                                     @AuthenticationPrincipal ShopPrincipal actor) {
        return catalog.saveProduct(id, input, actor.id());
    }

    @PostMapping("/admin/products/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    Catalog.VariantView createVariant(@PathVariable long id, @Valid @RequestBody Catalog.VariantEdit input,
                                      @AuthenticationPrincipal ShopPrincipal actor) {
        return catalog.saveVariant(id, null, input, actor.id());
    }

    @PatchMapping("/admin/products/{id}/variants/{variantId}")
    Catalog.VariantView updateVariant(@PathVariable long id, @PathVariable long variantId,
        @Valid @RequestBody Catalog.VariantEdit input, @AuthenticationPrincipal ShopPrincipal actor) {
        return catalog.saveVariant(id, variantId, input, actor.id());
    }
}
