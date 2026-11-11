package sayt.service;

import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.dto.catalog.*;
import sayt.entity.*;
import sayt.exception.Problem;
import sayt.mapper.CatalogMapper;
import sayt.repository.*;

@Service
public class ProductService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final VariantRepository variants;
    private final ProductImageRepository images;
    private final AuditService audit;
    private final Clock clock;

    ProductService(CategoryRepository categories, ProductRepository products, VariantRepository variants,
                   ProductImageRepository images, AuditService audit, Clock clock) {
        this.categories = categories;
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.audit = audit;
        this.clock = clock;
    }

    public Page<ProductView> adminProducts(int page, String query) {
        return products.search(query == null ? "" : query.strip(), PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "id")))
            .map(CatalogMapper::productView);
    }

    @Transactional
    public ProductView saveProduct(Long id, ProductEdit input, long actor) {
        if (id != null) products.lockStatus(id).orElseThrow(Problem::missing);
        var p = id == null ? new Product() : product(id);
        if (id != null) Problem.version(input.version(), p.getVersion());
        String status = input.status() == null ? (id == null ? "DRAFT" : p.getStatus()) : input.status();
        if ("PUBLISHED".equals(status) && (id == null || !variants.existsByProductId(id)
            || images.countByProductId(id) == 0)) throw Problem.bad("PUBLICATION_REQUIREMENTS");
        if (id != null && "DRAFT".equals(status) && !"DRAFT".equals(p.getStatus())) {
            throw Problem.bad("INVALID_PUBLICATION_STATUS");
        }
        var category = categories.findById(input.categoryId()).orElseThrow(Problem::missing);
        if ("GENERAL".equals(category.getKind())) throw Problem.bad("CATEGORY_TYPE_REQUIRED");
        if (id != null && variants.existsByProductId(id) && !categories.findById(p.getCategoryId()).orElseThrow().getKind().equals(category.getKind())) throw Problem.bad("PRODUCT_TYPE_IMMUTABLE");
        var before = id == null ? null : CatalogMapper.productView(p);
        p.setCategoryId(input.categoryId());
        p.setBrand(input.brand().strip());
        p.setName(input.name().strip());
        if (id == null) {
            String stem = (input.brand()+"-"+input.name()).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
            if (stem.length() > 100) stem = stem.substring(0,100);
            p.setSlug(input.slug() != null && !input.slug().isBlank() ? input.slug() : (stem.isEmpty() ? "product" : stem) + "-" + java.util.UUID.randomUUID());
        }
        p.setDescription(input.description() == null ? "" : input.description().strip());
        p.setStatus(status);
        products.saveAndFlush(p);
        if (id == null && (input.slug() == null || input.slug().isBlank())) {
            String stem=(p.getBrand()+"-"+p.getName()).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
            if(stem.length()>100) stem=stem.substring(0,100);
            p.setSlug((stem.isEmpty()?"product":stem)+"-"+p.getId());
            products.flush();
        }
        audit.change(actor, "PRODUCT_" + status, "product:" + p.getId(), p.getBrand()+" "+p.getName(), before, CatalogMapper.productView(p));
        return CatalogMapper.productView(p);
    }

    @Transactional
    public VariantView saveVariant(long productId, Long id, VariantEdit in, long actor) {
        products.lockStatus(productId).orElseThrow(Problem::missing);
        var product = product(productId);
        VariantRules.validate(categories.findById(product.getCategoryId()).orElseThrow().getKind(), in);
        String sku = VariantRules.sku(in.sku());
        var v = id == null ? new Variant() : variants.findById(id)
            .filter(it -> it.getProductId() == productId).orElseThrow(Problem::missing);
        if (id != null) {
            Problem.version(in.version(), v.getVersion());
            if (!VariantRules.sku(v.getSku()).equals(sku)) throw Problem.bad("SKU_IMMUTABLE");
        }
        if (v.isSpecsReviewRequired() && !Boolean.TRUE.equals(in.confirmSpecCleanup())) throw Problem.bad("SPECS_REVIEW_REQUIRED");
        var before = id == null ? null : CatalogMapper.variantView(v, false, true);
        PricingService.validateDiscount(in);
        v.setProductId(productId);
        if (id == null) v.setSku(sku);
        v.setStorageType(in.storageType());
        v.setSpecsReviewRequired(false);
        v.setColor(in.color());
        v.setRamGb(in.ramGb());
        v.setStorageGb(in.storageGb());
        v.setScreen(in.screen());
        v.setCpu(in.cpu());
        v.setGpu(in.gpu());
        v.setCameras(in.cameras());
        v.setBattery(in.battery());
        v.setSim(in.sim());
        v.setOs(in.os());
        v.setWeight(in.weight());
        v.setNotes(in.notes());
        v.setPrice(in.price());
        v.setStock(in.stock());
        v.setInstallment(in.installment());
        v.setInstallmentTerms(in.installment() ? in.installmentTerms() : null);
        v.setSalePrice(in.salePrice());
        v.setSaleFrom(in.saleFrom());
        v.setSaleUntil(in.saleUntil());
        variants.saveAndFlush(v);
        audit.change(actor, "VARIANT_SAVED", "variant:" + v.getId(), v.getSku(), before, CatalogMapper.variantView(v, false, true));
        return CatalogMapper.variantView(v, PricingService.activeSale(v, clock.instant()), true);
    }

    private Product product(long id) {
        return products.findById(id).orElseThrow(Problem::missing);
    }
}
