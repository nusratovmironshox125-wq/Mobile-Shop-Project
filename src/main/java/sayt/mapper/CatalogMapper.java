package sayt.mapper;

import sayt.dto.catalog.*;
import sayt.entity.*;

public final class CatalogMapper {
    private CatalogMapper() {}

    public static ImageView imageView(ProductImage image) {
        return new ImageView(image.getId(), mediaUrl(image.getStorageKey().toString()), image.getWidth(), image.getHeight(),
            image.getAlt(), image.getVariantId());
    }

    public static String mediaUrl(String key) {
        return key == null ? null : "/media/" + key;
    }

    public static CategoryView categoryView(Category c) {
        return new CategoryView(c.getId(), c.getSlug(), c.getNameRu(), c.getNameUz(), c.getKind(), c.isEnabled(), c.getSortOrder(), c.getVersion());
    }

    public static ProductView productView(Product p) {
        return new ProductView(p.getId(), p.getCategoryId(), p.getBrand(), p.getName(), p.getSlug(), p.getDescription(), p.getStatus(), p.getVersion());
    }

    public static VariantView variantView(Variant v, boolean active, boolean admin) {
        return new VariantView(v.getId(), v.getSku(), v.getColor(), v.getRamGb(), v.getStorageGb(), v.getScreen(), v.getCpu(), v.getGpu(), v.getCameras(),
            v.getBattery(), v.getSim(), v.getOs(), v.getWeight(), v.getNotes(), active ? v.getSalePrice() : v.getPrice(), active ? v.getPrice() : null,
            v.getStock(), v.isInstallment(), v.getInstallmentTerms(), v.getVersion(), admin ? v.getSalePrice() : null,
            admin ? v.getSaleFrom() : null, admin ? v.getSaleUntil() : null);
    }
}
