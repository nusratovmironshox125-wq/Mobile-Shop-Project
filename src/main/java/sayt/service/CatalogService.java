package sayt.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import sayt.repository.CatalogQueryRepository.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.dto.catalog.*;
import sayt.entity.*;
import sayt.exception.Problem;
import sayt.mapper.CatalogMapper;
import sayt.repository.*;

@Service
public class CatalogService {
    private final CatalogQueryRepository queries;
    private final ProductRepository products;
    private final VariantRepository variants;
    private final ProductImageRepository images;
    private final Clock clock;
    private final CategoryRepository categories;

    CatalogService(CatalogQueryRepository queries, ProductRepository products, VariantRepository variants,
                   ProductImageRepository images, Clock clock, CategoryRepository categories) {
        this.queries = queries;
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.clock = clock;
        this.categories = categories;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Result list(String category, String search, BigDecimal min, BigDecimal max, boolean discounted, String sort,
                       int page, int size, Collection<Long> ids) {
        if (min != null && max != null && min.compareTo(max) > 0) throw Problem.bad("PRICE_RANGE");
        var order = switch (sort) {
            case "price-asc" -> Order.PRICE_ASC;
            case "price-desc" -> Order.PRICE_DESC;
            case "new" -> Order.NEWEST;
            default -> throw Problem.bad("INVALID_SORT");
        };
        if (category != null && !queries.categoryVisible(category)) throw Problem.missing();
        return queries.list(category, search, min, max, discounted, order, page, size, ids, OffsetDateTime.now(clock));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail detail(String slug) {
        var id = queries.publicProductId(slug).orElseThrow(Problem::missing);
        return detail(product(id), false);
    }

    @Transactional(readOnly = true)
    public Detail adminDetail(long id) { return detail(product(id), true); }

    @Transactional(propagation = Propagation.MANDATORY)
    public Selection lockSelection(long id) {
        return queries.lockSelection(id, OffsetDateTime.now(clock)).orElseThrow(Problem::missing);
    }

    public List<String> publicSlugs() { return queries.publicSlugs(); }

    private Product product(long id) {
        return products.findById(id).orElseThrow(Problem::missing);
    }

    private Detail detail(Product p, boolean admin) {
        var now = clock.instant();
        return new Detail(CatalogMapper.productView(p), variants.findByProductIdOrderById(p.getId()).stream()
            .map(v -> CatalogMapper.variantView(v, PricingService.activeSale(v, now), admin)).toList(),
            images.findByProductIdOrderById(p.getId()).stream().map(CatalogMapper::imageView).toList(), categories.findById(p.getCategoryId()).orElseThrow().getKind());
    }
}
