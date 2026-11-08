package sayt.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sayt.entity.Variant;

public interface VariantRepository extends JpaRepository<Variant, Long> {
    List<Variant> findByProductIdOrderById(long productId);
    boolean existsByProductId(long productId);
}
