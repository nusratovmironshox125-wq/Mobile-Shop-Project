package sayt.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sayt.dto.catalog.*;
import sayt.mapper.CatalogMapper;

@Repository
public class CatalogQueryRepository {
    private static final String ACTIVE_SALE = """
        v.sale_price is not null and (v.sale_from is null or v.sale_from <= :now)
        and (v.sale_until is null or v.sale_until > :now)
        """;
    private static final String PUBLIC = "p.status='PUBLISHED' and c.enabled";

    public enum Order {
        PRICE_ASC("price asc, id desc"), PRICE_DESC("price desc, id desc"), NEWEST("created_at desc, id desc");

        private final String sql;
        Order(String sql) { this.sql = sql; }
    }

    private final JdbcClient db;

    CatalogQueryRepository(JdbcClient db) { this.db = db; }

    public boolean categoryVisible(String slug) {
        return db.sql("select exists(select 1 from category where enabled and slug=?)")
            .param(slug).query(Boolean.class).single();
    }

    public Optional<Long> publicProductId(String slug) {
        return db.sql("select p.id from product p join category c on c.id=p.category_id where " + PUBLIC
            + " and p.slug=?").param(slug).query(Long.class).optional();
    }

    public Result list(String category, String search, BigDecimal min, BigDecimal max, boolean discounted, Order sort,
                       int page, int size, Collection<Long> ids, OffsetDateTime now) {
        String order = sort.sql;
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
        String countSql = sql.substring(0,sql.indexOf(", matches as ("))
            + " select count(distinct id) from priced where true "
            + (hasPriceFilter ? "and stock='IN_STOCK' " : "")
            + (min != null ? "and price>=:min " : "") + (max != null ? "and price<=:max " : "")
            + (discounted ? "and price<base_price " : "");
        var args = new HashMap<String,Object>();
        args.put("now",now);
        if(category != null) args.put("category",category);
        if(searchPattern != null) args.put("search",searchPattern);
        if(ids != null) args.put("ids",ids);
        if(min != null) args.put("min",min);
        if(max != null) args.put("max",max);
        long total = db.sql(countSql).params(args).query(Long.class).single();
        var query = db.sql(sql).param("now", now).param("take", size).param("skip", page * size);
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
                CatalogMapper.mediaUrl(row.getString("image")), row.getLong("variant_count"));
        }).list();
        return new Result(cards, (page+1L)*size<total, page, size, total, (int)((total+size-1)/size));
    }

    public Optional<Selection> lockSelection(long id, OffsetDateTime now) {
        return db.sql("""
            select p.id product_id, p.brand, p.name, c.kind, v.sku, v.color, v.ram_gb, v.storage_gb, v.storage_type, v.cpu, v.gpu,
              v.price base_price, case when %s then v.sale_price else v.price end price,
              v.installment, v.installment_terms
            from product_variant v join product p on p.id=v.product_id join category c on c.id=p.category_id
            where %s and v.stock='IN_STOCK' and v.id=:id for share of p,c,v
            """.formatted(ACTIVE_SALE, PUBLIC)).param("id", id).param("now", now)
            .query((row, index) -> {
                var labels = new ArrayList<String>();
                if (row.getString("color") != null && !row.getString("color").isBlank()) labels.add("Цвет / Rang: " + row.getString("color"));
                if (row.getObject("ram_gb") != null) labels.add("Оперативная память / RAM: " + row.getInt("ram_gb") + " GB");
                if (row.getObject("storage_gb") != null) labels.add(("LAPTOP".equals(row.getString("kind")) ? "Накопитель / Disk: " : "Встроенная память / Xotira: ") + (row.getString("storage_type") == null ? "" : row.getString("storage_type")+" ") + row.getInt("storage_gb") + " GB");
                if(row.getString("cpu") != null && !row.getString("cpu").isBlank()) labels.add("Процессор / Protsessor: "+row.getString("cpu"));
                if("LAPTOP".equals(row.getString("kind")) && row.getString("gpu") != null && !row.getString("gpu").isBlank()) labels.add("Видеокарта / GPU: "+row.getString("gpu"));
                var price = row.getBigDecimal("price");
                var base = row.getBigDecimal("base_price");
                return new Selection(row.getLong("product_id"), id, row.getString("brand") + " " + row.getString("name"),
                    row.getString("sku"), String.join(" · ", labels), price, price.compareTo(base) < 0 ? base : null,
                    row.getBoolean("installment"), row.getString("installment_terms"));
            }).optional();
    }

    public List<String> publicSlugs() {
        return db.sql("select p.slug from product p join category c on c.id=p.category_id where " + PUBLIC
            + " order by p.id limit 49990").query(String.class).list();
    }
}
