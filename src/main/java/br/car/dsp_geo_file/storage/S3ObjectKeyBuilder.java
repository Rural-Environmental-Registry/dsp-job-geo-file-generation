package br.car.dsp_geo_file.storage;

import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import org.springframework.stereotype.Component;

/**
 * Builds the object key: {@code {format}/{level}/{slug}_{themeCode}.{ext}}, with the level 2
 * slug prefixed on level 3.
 *
 * <p>The key carries the slug and not the {@code id} because the bucket is browsable by people;
 * the API and the flags keep using ids. A renamed territory therefore lands on a new key and
 * the old one becomes an orphan, which is what the cleanup step collects.
 */
@Component
public class S3ObjectKeyBuilder {

    public String prefix(String format, TerritoryLevel level) {
        return format + "/" + level.folder() + "/";
    }

    public String build(String format, Territory territory, String themeCode, String extension) {
        return prefix(format, territory.level()) + fileName(territory, themeCode, extension);
    }

    private String fileName(Territory territory, String themeCode, String extension) {
        String slug = requireSlug(territory.slug(), "name", territory);
        String safeThemeCode = requireSafeThemeCode(themeCode);
        if (territory.level() == TerritoryLevel.LEVEL_3) {
            String parentSlug = requireSlug(territory.parentSlug(), "parent name", territory);
            return parentSlug + "_" + slug + "_" + safeThemeCode + "." + extension;
        }
        return slug + "_" + safeThemeCode + "." + extension;
    }

    /**
     * Theme codes come from the shared catalogue, not this repository — a stray {@code /} or
     * {@code ..} there must not be able to reshape the object key's path.
     */
    private static String requireSafeThemeCode(String themeCode) {
        if (themeCode == null || themeCode.isBlank() || !themeCode.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException(
                    "Theme code '" + themeCode + "' is not a valid object key segment");
        }
        return themeCode;
    }

    private static String requireSlug(String slug, String field, Territory territory) {
        if (slug == null) {
            throw new IllegalStateException(
                    "Territory " + territory.level() + " id=" + territory.id()
                            + " has no usable " + field + " for the object key");
        }
        return slug;
    }
}
