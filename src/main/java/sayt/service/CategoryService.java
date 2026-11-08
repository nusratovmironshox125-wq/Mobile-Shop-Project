package sayt.service;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.dto.catalog.*;
import sayt.entity.*;
import sayt.exception.Problem;
import sayt.mapper.CatalogMapper;
import sayt.repository.*;

@Service
public class CategoryService {
    private final CategoryRepository categories;
    private final AuditService audit;

    CategoryService(CategoryRepository categories, AuditService audit) {
        this.categories = categories;
        this.audit = audit;
    }

    public List<CategoryView> categories(boolean all) {
        return categories.findAll(Sort.by("sortOrder", "id")).stream().filter(c -> all || c.isEnabled())
            .map(CatalogMapper::categoryView).toList();
    }

    @Transactional
    public CategoryView saveCategory(Long id, CategoryEdit input, long actor) {
        var c = id == null ? new Category() : categories.findById(id).orElseThrow(Problem::missing);
        if (id != null) {
            Problem.version(input.version(), c.getVersion());
            if (!c.getSlug().equals(input.slug()) && !"GENERAL".equals(c.getKind())) throw Problem.bad("SLUG_PROTECTED");
        } else c.setKind("GENERAL");
        c.setSlug(input.slug());
        c.setNameRu(input.nameRu().strip());
        c.setNameUz(input.nameUz().strip());
        c.setEnabled(input.enabled());
        c.setSortOrder(input.sortOrder());
        categories.saveAndFlush(c);
        audit.record(actor, "CATEGORY_SAVED", "category:" + c.getId());
        return CatalogMapper.categoryView(c);
    }
}
