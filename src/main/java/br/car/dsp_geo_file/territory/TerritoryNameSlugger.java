package br.car.dsp_geo_file.territory;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Turns a territory {@code name} into the object key segment: lowercase, no accents,
 * anything else collapsed into a hyphen.
 *
 * <p>The backend recomputes this slug to find the object, so the rule is a contract between
 * the two repositories — changing it here orphans every published file.
 */
public final class TerritoryNameSlugger {

    private TerritoryNameSlugger() {
    }

    /** Returns the slug, or null when {@code name} carries no usable character. */
    public static String slugify(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String withoutAccents = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = withoutAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? null : slug;
    }
}
