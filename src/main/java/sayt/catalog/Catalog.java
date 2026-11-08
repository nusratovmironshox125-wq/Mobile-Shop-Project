package sayt.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.common.Audit;
import sayt.common.Problem;

@Service
public class Catalog {
    public record CategoryEdit(@NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=100) String slug,
        @NotBlank @Size(max=100) String nameRu, @NotBlank @Size(max=100) String nameUz,
        @NotNull Boolean enabled, @NotNull Integer sortOrder, Long version) {}
    public record CategoryView(long id, String slug, String nameRu, String nameUz, String kind,
        boolean enabled, int sortOrder, long version) {}
    public record ProductEdit(@NotNull @Positive Long categoryId, @NotBlank @Size(max=100) String brand,
        @NotBlank @Size(max=150) String name, @NotBlank @Size(max=150)
        @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        @Size(max=5000) String description, @Pattern(regexp="DRAFT|PUBLISHED|ARCHIVED") String status, Long version) {}
    public record VariantEdit(@NotBlank @Size(max=80) @Pattern(regexp="[a-zA-Z0-9._-]+") String sku,
        @Size(max=100) String color, @Positive Integer ramGb, @Positive Integer storageGb,
        @Size(max=100) String screen, @Size(max=100) String cpu, @Size(max=100) String gpu,
        @Size(max=150) String cameras, @Size(max=100) String battery, @Size(max=100) String sim,
        @Size(max=100) String os, @Size(max=100) String weight, @Size(max=1000) String notes,
        @NotNull @Positive @Digits(integer=14,fraction=2) BigDecimal price,
        @NotNull @Pattern(regexp="IN_STOCK|OUT_OF_STOCK") String stock, @NotNull Boolean installment,
        @Size(max=1000) String installmentTerms, @Positive @Digits(integer=14,fraction=2) BigDecimal salePrice,
        Instant saleFrom, Instant saleUntil, Long version) {}
    public record ProductView(long id, long categoryId, String brand, String name, String slug,
        String description, String status, long version) {}
    public record ImageView(long id, String url, int width, int height, String alt, Long variantId) {}
    public record VariantView(long id, String sku, String color, Integer ramGb, Integer storageGb,
        String screen, String cpu, String gpu, String cameras, String battery, String sim, String os, String weight,
        String notes, BigDecimal price, BigDecimal originalPrice, String stock, boolean installment,
        String installmentTerms, long version, BigDecimal salePrice, Instant saleFrom, Instant saleUntil) {}
    public record Detail(ProductView product, List<VariantView> variants, List<ImageView> images) {}
    public record Card(long id, String slug, String brand, String name, long variantId, String color,
        Integer storageGb, BigDecimal price, BigDecimal originalPrice, String stock, boolean installment,
        String image, long variantCount) {}
    public record Result(List<Card> items, boolean hasNext) {}
    public record Selection(long productId, long variantId, String productName, String sku, String label,
        BigDecimal price, BigDecimal originalPrice, boolean installment, String installmentTerms) {}

    private static final String ACTIVE_SALE = """
        v.sale_price is not null and (v.sale_from is null or v.sale_from <= :now)
        and (v.sale_until is null or v.sale_until > :now)
        """;
    private static final String PUBLIC = "p.status='PUBLISHED' and c.enabled";
    private final Categories categories;
    private final Products products;
    private final Variants variants;
    private final ProductImages images;
    private final JdbcClient db;
    private final Audit audit;
    private final Clock clock;

    Catalog(Categories categories, Products products, Variants variants, ProductImages images,
            JdbcClient db, Audit audit, Clock clock) {
        this.categories = categories;
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.db = db;
        this.audit = audit;
        this.clock = clock;
    }

    List<CategoryView> categories(boolean all) {
        return categories.findAll(Sort.by("sortOrder", "id")).stream().filter(c -> all || c.enabled)
            .map(Catalog::categoryView).toList();
    }

    @Transactional
    CategoryView saveCategory(Long id, CategoryEdit input, long actor) {
        var c = id == null ? new Category() : categories.findById(id).orElseThrow(Problem::missing);
        if (id != null) {
            Problem.version(input.version(), c.version);
            if (!c.slug.equals(input.slug()) && !"GENERAL".equals(c.kind)) throw Problem.bad("SLUG_PROTECTED");
        } else c.kind = "GENERAL";
        c.slug = input.slug();
        c.nameRu = input.nameRu().strip();
        c.nameUz = input.nameUz().strip();
        c.enabled = input.enabled();
        c.sortOrder = input.sortOrder();
        categories.saveAndFlush(c);
        audit.record(actor, "CATEGORY_SAVED", "category:" + c.id);
        return categoryView(c);
    }

    Object adminProducts(int page) {
        return products.findAll(PageRequest.of(page, 24, Sort.by(Sort.Direction.DESC, "id")))
            .map(Catalog::productView);
    }

    @Transactional
    ProductView saveProduct(Long id, ProductEdit input, long actor) {
        if (id != null) db.sql("select id from product where id=? for update").param(id)
            .query(Long.class).optional().orElseThrow(Problem::missing);
        var p = id == null ? new Product() : product(id);
        if (id != null) Problem.version(input.version(), p.version);
        String status = input.status() == null ? (id == null ? "DRAFT" : p.status) : input.status();
        if ("PUBLISHED".equals(status) && (id == null || !variants.existsByProductId(id)
            || images.countByProductId(id) == 0)) throw Problem.bad("PUBLICATION_REQUIREMENTS");
        if (id != null && "DRAFT".equals(status) && !"DRAFT".equals(p.status)) {
            throw Problem.bad("INVALID_PUBLICATION_STATUS");
        }
        categories.findById(input.categoryId()).orElseThrow(Problem::missing);
        p.categoryId = input.categoryId();
        p.brand = input.brand().strip();
        p.name = input.name().strip();
        p.slug = input.slug();
        p.description = input.description() == null ? "" : input.description().strip();
        p.status = status;
        products.saveAndFlush(p);
        audit.record(actor, "PRODUCT_" + status, "product:" + p.id);
        return productView(p);
    }

    @Transactional
    VariantView saveVariant(long productId, Long id, VariantEdit in, long actor) {
        product(productId);
        var v = id == null ? new Variant() : variants.findById(id)
            .filter(it -> it.productId == productId).orElseThrow(Problem::missing);
        if (id != null) {
            Problem.version(in.version(), v.version);
            if (!v.sku.equals(in.sku())) throw Problem.bad("SKU_IMMUTABLE");
        }
        if ((in.salePrice() == null && (in.saleFrom() != null || in.saleUntil() != null))
            || (in.salePrice() != null && in.salePrice().compareTo(in.price()) >= 0)
            || (in.saleFrom() != null && in.saleUntil() != null && !in.saleFrom().isBefore(in.saleUntil()))) {
            throw Problem.bad("INVALID_DISCOUNT");
        }
        v.productId = productId;
        v.sku = in.sku();
        v.color = in.color();
        v.ramGb = in.ramGb();
        v.storageGb = in.storageGb();
        v.screen = in.screen();
        v.cpu = in.cpu();
        v.gpu = in.gpu();
        v.cameras = in.cameras();
        v.battery = in.battery();
        v.sim = in.sim();
        v.os = in.os();
        v.weight = in.weight();
        v.notes = in.notes();
        v.price = in.price();
        v.stock = in.stock();
        v.installment = in.installment();
        v.installmentTerms = in.installment() ? in.installmentTerms() : null;
        v.salePrice = in.salePrice();
        v.saleFrom = in.saleFrom();
        v.saleUntil = in.saleUntil();
        variants.saveAndFlush(v);
        audit.record(actor, "VARIANT_SAVED", "variant:" + v.id);
        return variantView(v, clock.instant(), true);
    }

    Result list(String category, String search, BigDecimal min, BigDecimal max, boolean discounted, String sort,
                int page, int size, Collection<Long> ids) {
        if (min != null && max != null && min.compareTo(max) > 0) throw Problem.bad("PRICE_RANGE");
        var order = switch (sort) {
            case "price-asc" -> "price asc, id desc";
            case "price-desc" -> "price desc, id desc";
            case "new" -> "created_at desc, id desc";
            default -> throw Problem.bad("INVALID_SORT");
        };
        if (category != null && !db.sql("select exists(select 1 from category where enabled and slug=?)")
            .param(category).query(Boolean.class).single()) throw Problem.missing();
        String searchPattern = search == null ? null : "%" + search.strip().replaceAll("\\s+", " ")
            .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        boolean hasPriceFilter = min != null || max != null || discounted;
        String sql = """
            with priced as (
              select p.id, p.slug, p.brand, p.name, p.created_at, v.id variant_id, v.color, v.storage_gb,
                v.price base_price, v.stock, v.installment,
                case when %s then v.sale_price else v.price end price
              from product p join category c on c.id=p.category_id join product_variant v on v.product_id=p.id
              where %s %s %s %s
            ), matches as (
              select *, count(*) over(partition by id) variant_count,
                row_number() over(partition by id order by (stock='IN_STOCK') desc, price, variant_id) position
              from priced where true %s %s %s %s
            )
            select matches.*, (select storage_key from product_image i
              where i.product_id=matches.id and (i.variant_id is null or i.variant_id=matches.variant_id)
              order by (i.variant_id is not null) desc, i.id limit 1) image
            from matches where position=1 order by %s limit :take offset :skip
            """.formatted(ACTIVE_SALE, PUBLIC, category != null ? "and c.slug=:category" : "",
                searchPattern != null ? "and (p.brand || ' ' || p.name) collate pg_catalog.pg_unicode_fast ilike :search escape '!'" : "",
                ids != null ? "and p.id in (:ids)" : "", hasPriceFilter ? "and stock='IN_STOCK'" : "",
                min != null ? "and price >= :min" : "", max != null ? "and price <= :max" : "",
                discounted ? "and price < base_price" : "", order);
        var query = db.sql(sql).param("now", OffsetDateTime.now(clock)).param("take", size + 1).param("skip", page * size);
        if (category != null) query.param("category", category);
        if (searchPattern != null) query.param("search", searchPattern);
        if (ids != null) query.param("ids", ids);
        if (min != null) query.param("min", min);
        if (max != null) query.param("max", max);
        var cards = query.query((row, index) -> {
            var price = row.getBigDecimal("price");
            var base = row.getBigDecimal("base_price");
            return new Card(row.getLong("id"), row.getString("slug"), row.getString("brand"), row.getString("name"),
                row.getLong("variant_id"), row.getString("color"), row.getObject("storage_gb", Integer.class), price,
                price.compareTo(base) < 0 ? base : null, row.getString("stock"), row.getBoolean("installment"),
                mediaUrl(row.getString("image")), row.getLong("variant_count"));
        }).list();
        return new Result(cards.subList(0, Math.min(size, cards.size())), cards.size() > size);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    Detail detail(String slug) {
        long id = db.sql("select p.id from product p join category c on c.id=p.category_id where " + PUBLIC
            + " and p.slug=?").param(slug).query(Long.class).optional().orElseThrow(Problem::missing);
        return detail(product(id), false);
    }

    @Transactional(readOnly = true)
    Detail adminDetail(long id) {
        return detail(product(id), true);
    }

    public Selection lockSelection(long id) {
        return db.sql("""
            select p.id product_id, p.brand, p.name, v.sku, v.color, v.ram_gb, v.storage_gb,
              v.price base_price, case when %s then v.sale_price else v.price end price,
              v.installment, v.installment_terms
            from product_variant v join product p on p.id=v.product_id join category c on c.id=p.category_id
            where %s and v.stock='IN_STOCK' and v.id=:id for share of p,c,v
            """.formatted(ACTIVE_SALE, PUBLIC)).param("id", id).param("now", OffsetDateTime.now(clock))
            .query((row, index) -> {
                var labels = new ArrayList<String>();
                if (row.getString("color") != null && !row.getString("color").isBlank()) labels.add(row.getString("color"));
                if (row.getObject("ram_gb") != null) labels.add(row.getInt("ram_gb") + " GB RAM");
                if (row.getObject("storage_gb") != null) labels.add(row.getInt("storage_gb") + " GB");
                var price = row.getBigDecimal("price");
                var base = row.getBigDecimal("base_price");
                return new Selection(row.getLong("product_id"), id, row.getString("brand") + " " + row.getString("name"),
                    row.getString("sku"), String.join(" · ", labels), price, price.compareTo(base) < 0 ? base : null,
                    row.getBoolean("installment"), row.getString("installment_terms"));
            }).optional().orElseThrow(Problem::missing);
    }

    List<String> publicSlugs() {
        return db.sql("select p.slug from product p join category c on c.id=p.category_id where " + PUBLIC
            + " order by p.id limit 49990").query(String.class).list();
    }

    Product product(long id) {
        return products.findById(id).orElseThrow(Problem::missing);
    }

    private Detail detail(Product p, boolean admin) {
        var now = clock.instant();
        return new Detail(productView(p), variants.findByProductIdOrderById(p.id).stream()
            .map(v -> variantView(v, now, admin)).toList(),
            images.findByProductIdOrderById(p.id).stream().map(Catalog::imageView).toList());
    }

    static ImageView imageView(ProductImage image) {
        return new ImageView(image.id, mediaUrl(image.storageKey.toString()), image.width, image.height,
            image.alt, image.variantId);
    }

    private static String mediaUrl(String key) {
        return key == null ? null : "/media/" + key;
    }

    private static CategoryView categoryView(Category c) {
        return new CategoryView(c.id, c.slug, c.nameRu, c.nameUz, c.kind, c.enabled, c.sortOrder, c.version);
    }

    private static ProductView productView(Product p) {
        return new ProductView(p.id, p.categoryId, p.brand, p.name, p.slug, p.description, p.status, p.version);
    }

    static boolean activeSale(Variant v, Instant now) {
        return v.salePrice != null && (v.saleFrom == null || !now.isBefore(v.saleFrom))
            && (v.saleUntil == null || now.isBefore(v.saleUntil));
    }

    private static VariantView variantView(Variant v, Instant now, boolean admin) {
        boolean active = activeSale(v, now);
        return new VariantView(v.id, v.sku, v.color, v.ramGb, v.storageGb, v.screen, v.cpu, v.gpu, v.cameras,
            v.battery, v.sim, v.os, v.weight, v.notes, active ? v.salePrice : v.price, active ? v.price : null,
            v.stock, v.installment, v.installmentTerms, v.version, admin ? v.salePrice : null,
            admin ? v.saleFrom : null, admin ? v.saleUntil : null);
    }
}
