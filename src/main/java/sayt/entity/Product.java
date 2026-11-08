package sayt.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product")
public class Product extends CatalogRow {
    private Long categoryId;
    private String brand;
    private String name;
    private String slug;
    private String description;
    private String status;
    private Instant createdAt = Instant.now();
}
