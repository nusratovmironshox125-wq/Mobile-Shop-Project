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
        return variantView(v,active,admin,null);
    }

    public static VariantView variantView(Variant v, boolean active, boolean admin, String kind) {
        boolean phone=admin || "PHONE".equals(kind), laptop=admin || "LAPTOP".equals(kind);
        return new VariantView(v.getId(), v.getSku(), v.getColor(), v.getRamGb(), v.getStorageGb(), v.getScreen(), v.getCpu(), laptop ? v.getGpu() : null, phone ? v.getCameras() : null,
            phone ? v.getBattery() : null, phone ? v.getSim() : null, v.getOs(), laptop ? v.getWeight() : null, v.getNotes(), active ? v.getSalePrice() : v.getPrice(), active ? v.getPrice() : null,
            v.getStock(), v.isInstallment(), v.getInstallmentTerms(), v.getVersion(), admin ? v.getSalePrice() : null,
            admin ? v.getSaleFrom() : null, admin ? v.getSaleUntil() : null, laptop ? v.getStorageType() : null, admin && v.isSpecsReviewRequired());
    }
}
