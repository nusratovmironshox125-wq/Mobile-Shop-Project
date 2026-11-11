package sayt.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product_variant")
public class Variant extends CatalogRow {
    private Long productId;
    private String sku;
    private String storageType;
    private boolean specsReviewRequired;
    private String color;
    private Integer ramGb;
    private Integer storageGb;
    private String screen;
    private String cpu;
    private String gpu;
    private String cameras;
    private String battery;
    private String sim;
    private String os;
    private String weight;
    private String notes;
    private BigDecimal price;
    private String stock;
    private boolean installment;
    private String installmentTerms;
    private BigDecimal salePrice;
    private Instant saleFrom;
    private Instant saleUntil;
}
