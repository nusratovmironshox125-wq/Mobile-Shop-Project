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

    public org.springframework.data.domain.Page<CategoryView> page(int page) {
        return categories.findAll(org.springframework.data.domain.PageRequest.of(page,10,Sort.by("sortOrder","id"))).map(CatalogMapper::categoryView);
    }

    @Transactional
    public CategoryView saveCategory(Long id, CategoryEdit input, long actor) {
        var c = id == null ? new Category() : categories.findById(id).orElseThrow(Problem::missing);
        if (id != null) {
            Problem.version(input.version(), c.getVersion());
            if (!c.getSlug().equals(input.slug()) && !"GENERAL".equals(c.getKind())) throw Problem.bad("SLUG_PROTECTED");
            if (input.kind() != null && !c.getKind().equals(input.kind())) throw Problem.bad("CATEGORY_TYPE_IMMUTABLE");
        } else {
            if (input.kind() == null) throw Problem.bad("CATEGORY_TYPE_REQUIRED");
            c.setKind(input.kind());
        }
        var before = id == null ? null : CatalogMapper.categoryView(c);
        c.setSlug(input.slug());
        c.setNameRu(input.nameRu().strip());
        c.setNameUz(input.nameUz().strip());
        c.setEnabled(input.enabled());
        c.setSortOrder(input.sortOrder());
        categories.saveAndFlush(c);
        audit.change(actor, "CATEGORY_SAVED", "category:" + c.getId(), c.getNameRu(), before, CatalogMapper.categoryView(c));
        return CatalogMapper.categoryView(c);
    }
}
