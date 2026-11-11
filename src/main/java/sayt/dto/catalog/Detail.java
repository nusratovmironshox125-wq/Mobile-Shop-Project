package sayt.dto.catalog;

import jakarta.validation.constraints.*;
import java.util.List;

public record Detail(ProductView product, List<VariantView> variants, List<ImageView> images, String kind) {}
