package sayt.service;

import java.util.Locale;
import sayt.dto.catalog.VariantEdit;
import sayt.exception.Problem;

public final class VariantRules {
    private VariantRules() {}
    public static void validate(String kind, VariantEdit input) {
        boolean wrong = switch (kind) {
            case "PHONE" -> present(input.gpu()) || present(input.weight()) || present(input.storageType());
            case "LAPTOP" -> present(input.cameras()) || present(input.battery()) || present(input.sim());
            default -> true;
        };
        if (wrong) throw Problem.bad("CATEGORY_SPEC_MISMATCH");
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    public static String sku(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9._-]{1,80}")) throw Problem.bad("SKU_INVALID");
        return normalized;
    }
}
