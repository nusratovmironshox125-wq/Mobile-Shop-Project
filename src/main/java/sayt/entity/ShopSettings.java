package sayt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "shop_settings")
public class ShopSettings {
    @Id private Long id;
    private String name;
    private String phone;
    private String telegram;
    private String address;
    private String directions;
    private String mapUrl;
    private String hours;
    @Version private long version;
}
