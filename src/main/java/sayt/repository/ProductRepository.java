package sayt.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sayt.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("select p from Product p where :q = '' or locate(lower(:q),lower(concat(p.brand,' ',p.name))) > 0 or exists(select v.id from Variant v where v.productId=p.id and locate(lower(:q),lower(v.sku)) > 0)")
    org.springframework.data.domain.Page<Product> search(String q, org.springframework.data.domain.Pageable pageable);
    @Query(value = "select status from product where id=:id for update", nativeQuery = true)
    Optional<String> lockStatus(long id);
}
