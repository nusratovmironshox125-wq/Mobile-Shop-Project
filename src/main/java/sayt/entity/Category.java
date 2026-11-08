package sayt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "category")
public class Category extends CatalogRow {
    private String slug;
    private String nameRu;
    private String nameUz;
    private String kind;
    private boolean enabled;
    private int sortOrder;
}
