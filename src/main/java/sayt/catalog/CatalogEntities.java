package sayt.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@MappedSuperclass
abstract class CatalogRow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Version long version;
}

@Entity
@Table(name = "category")
class Category extends CatalogRow {
    String slug;
    String nameRu;
    String nameUz;
    String kind;
    boolean enabled;
    int sortOrder;
}

@Entity
@Table(name = "product")
class Product extends CatalogRow {
    Long categoryId;
    String brand;
    String name;
    String slug;
    String description;
    String status;
    Instant createdAt = Instant.now();
}

@Entity
@Table(name = "product_variant")
class Variant extends CatalogRow {
    Long productId;
    String sku;
    String color;
    Integer ramGb;
    Integer storageGb;
    String screen;
    String cpu;
    String gpu;
    String cameras;
    String battery;
    String sim;
    String os;
    String weight;
    String notes;
    BigDecimal price;
    String stock;
    boolean installment;
    String installmentTerms;
    BigDecimal salePrice;
    Instant saleFrom;
    Instant saleUntil;
}

@Entity
@Table(name = "product_image")
class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long productId;
    Long variantId;
    UUID storageKey;
    int width;
    int height;
    String alt;
}

interface Categories extends JpaRepository<Category, Long> {}
interface Products extends JpaRepository<Product, Long> {}
interface Variants extends JpaRepository<Variant, Long> {
    List<Variant> findByProductIdOrderById(long productId);
    boolean existsByProductId(long productId);
}
interface ProductImages extends JpaRepository<ProductImage, Long> {
    long countByProductId(long productId);
    List<ProductImage> findByProductIdOrderById(long productId);
}
