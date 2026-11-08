package sayt.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sayt.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query(value = "select status from product where id=:id for update", nativeQuery = true)
    Optional<String> lockStatus(long id);
}
