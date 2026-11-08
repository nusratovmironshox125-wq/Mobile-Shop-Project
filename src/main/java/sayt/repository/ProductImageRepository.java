package sayt.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sayt.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    long countByProductId(long productId);
    List<ProductImage> findByProductIdOrderById(long productId);

    @Query(value = """
        select exists(select 1 from product_image i join product p on p.id=i.product_id
          join category c on c.id=p.category_id where i.storage_key=:key
          and (:admin or (p.status='PUBLISHED' and c.enabled)))
        """, nativeQuery = true)
    boolean visible(UUID key, boolean admin);
}
