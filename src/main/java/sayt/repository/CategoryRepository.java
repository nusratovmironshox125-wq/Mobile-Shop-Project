package sayt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sayt.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {}
