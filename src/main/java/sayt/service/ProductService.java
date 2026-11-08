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

    public Page<ProductView> adminProducts(int page) {
        return products.findAll(PageRequest.of(page, 24, Sort.by(Sort.Direction.DESC, "id")))
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
        categories.findById(input.categoryId()).orElseThrow(Problem::missing);
        p.setCategoryId(input.categoryId());
        p.setBrand(input.brand().strip());
        p.setName(input.name().strip());
        p.setSlug(input.slug());
        p.setDescription(input.description() == null ? "" : input.description().strip());
        p.setStatus(status);
        products.saveAndFlush(p);
        audit.record(actor, "PRODUCT_" + status, "product:" + p.getId());
        return CatalogMapper.productView(p);
    }

    @Transactional
    public VariantView saveVariant(long productId, Long id, VariantEdit in, long actor) {
        product(productId);
        var v = id == null ? new Variant() : variants.findById(id)
            .filter(it -> it.getProductId() == productId).orElseThrow(Problem::missing);
        if (id != null) {
            Problem.version(in.version(), v.getVersion());
            if (!v.getSku().equals(in.sku())) throw Problem.bad("SKU_IMMUTABLE");
        }
        PricingService.validateDiscount(in);
        v.setProductId(productId);
        v.setSku(in.sku());
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
        audit.record(actor, "VARIANT_SAVED", "variant:" + v.getId());
        return CatalogMapper.variantView(v, PricingService.activeSale(v, clock.instant()), true);
    }

    private Product product(long id) {
        return products.findById(id).orElseThrow(Problem::missing);
    }
}
