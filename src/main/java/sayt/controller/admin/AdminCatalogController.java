package sayt.controller.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sayt.service.CatalogService;
import sayt.service.CategoryService;
import sayt.service.ProductService;
import sayt.dto.catalog.*;
import sayt.security.ShopPrincipal;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/v1")
class AdminCatalogController {
    private final CatalogService catalog;
    private final CategoryService categories;
    private final ProductService products;

    AdminCatalogController(CatalogService catalog, CategoryService categories, ProductService products) {
        this.catalog = catalog;
        this.categories = categories;
        this.products = products;
    }

    @GetMapping("/admin/categories")
    List<CategoryView> adminCategories() { return categories.categories(true); }

    @GetMapping("/admin/categories/page")
    Page<CategoryView> categoryPage(@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page) { return categories.page(page); }

    @PostMapping("/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryView createCategory(@Valid @RequestBody CategoryEdit input,
                                       @AuthenticationPrincipal ShopPrincipal actor) {
        return categories.saveCategory(null, input, actor.id());
    }

    @PatchMapping("/admin/categories/{id}")
    CategoryView updateCategory(@PathVariable long id, @Valid @RequestBody CategoryEdit input,
                                       @AuthenticationPrincipal ShopPrincipal actor) {
        return categories.saveCategory(id, input, actor.id());
    }

    @GetMapping("/admin/products")
    Page<ProductView> adminProducts(@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
        @RequestParam(required=false) @Size(max=100) String q) {
        return products.adminProducts(page, q);
    }

    @GetMapping("/admin/products/{id}")
    Detail adminDetail(@PathVariable long id) { return catalog.adminDetail(id); }

    @PostMapping("/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    ProductView createProduct(@Valid @RequestBody ProductEdit input,
                                     @AuthenticationPrincipal ShopPrincipal actor) {
        return products.saveProduct(null, input, actor.id());
    }

    @PatchMapping("/admin/products/{id}")
    ProductView updateProduct(@PathVariable long id, @Valid @RequestBody ProductEdit input,
                                     @AuthenticationPrincipal ShopPrincipal actor) {
        return products.saveProduct(id, input, actor.id());
    }

    @PostMapping("/admin/products/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    VariantView createVariant(@PathVariable long id, @Valid @RequestBody VariantEdit input,
                                      @AuthenticationPrincipal ShopPrincipal actor) {
        return products.saveVariant(id, null, input, actor.id());
    }

    @PatchMapping("/admin/products/{id}/variants/{variantId}")
    VariantView updateVariant(@PathVariable long id, @PathVariable long variantId,
        @Valid @RequestBody VariantEdit input, @AuthenticationPrincipal ShopPrincipal actor) {
        return products.saveVariant(id, variantId, input, actor.id());
    }
}
