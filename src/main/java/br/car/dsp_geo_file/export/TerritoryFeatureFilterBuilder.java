package br.car.dsp_geo_file.export;

import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import br.car.dsp_geo_file.theme.DownloadTerritoryFilterConfig;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Translates {@code territoryFilter} into SQL over {@code dsp-geoserver-db}.
 *
 * <p>Same two strategies the backend applies to the WFS, resolved with subqueries instead of
 * an id list: the level 3 and area of interest tables live in the very database being read, so
 * there is no reason to carry thousands of ids through the query text.
 */
@Component
public class TerritoryFeatureFilterBuilder {

    private static final String AREA_OF_INTEREST_TABLE = "dsp.area_of_interest";
    private static final String AREA_OF_INTEREST_LEVEL_3_COLUMN = "territory_level_3_id";

    public FeatureFilter build(DownloadThemeConfig theme, Territory territory) {
        DownloadTerritoryFilterConfig filter = requireFilter(theme);
        String strategy = filter.strategy().toLowerCase(Locale.ROOT);
        return switch (strategy) {
            case DownloadTerritoryFilterConfig.STRATEGY_DIRECT -> buildDirect(filter, territory);
            case DownloadTerritoryFilterConfig.STRATEGY_AOI_LINKED -> buildAoiLinked(filter, territory);
            default -> throw new IllegalStateException(
                    "Unsupported territory strategy '" + filter.strategy()
                            + "' for theme " + theme.code());
        };
    }

    private FeatureFilter buildDirect(DownloadTerritoryFilterConfig filter, Territory territory) {
        String field = requireField(filter.level3Field(), "level3Field");
        if (territory.level() == TerritoryLevel.LEVEL_3) {
            return new FeatureFilter(quoteIdentifier(field) + " = ?", List.of(territory.id()));
        }
        return new FeatureFilter(
                quoteIdentifier(field) + " IN (SELECT id FROM "
                        + TerritoryLevel.LEVEL_3.table() + " WHERE parent_id = ?)",
                List.of(territory.id()));
    }

    private FeatureFilter buildAoiLinked(DownloadTerritoryFilterConfig filter, Territory territory) {
        String field = requireField(filter.aoiLinkField(), "aoiLinkField");
        if (territory.level() == TerritoryLevel.LEVEL_3) {
            return new FeatureFilter(
                    quoteIdentifier(field) + " IN (SELECT id FROM " + AREA_OF_INTEREST_TABLE
                            + " WHERE " + AREA_OF_INTEREST_LEVEL_3_COLUMN + " = ?)",
                    List.of(territory.id()));
        }
        return new FeatureFilter(
                quoteIdentifier(field) + " IN (SELECT id FROM " + AREA_OF_INTEREST_TABLE
                        + " WHERE " + AREA_OF_INTEREST_LEVEL_3_COLUMN + " IN ("
                        + "SELECT id FROM " + TerritoryLevel.LEVEL_3.table() + " WHERE parent_id = ?))",
                List.of(territory.id()));
    }

    private static DownloadTerritoryFilterConfig requireFilter(DownloadThemeConfig theme) {
        DownloadTerritoryFilterConfig filter = theme.territoryFilter();
        if (filter == null || filter.strategy() == null || filter.strategy().isBlank()) {
            throw new IllegalStateException(
                    "Territory configuration missing for theme " + theme.code());
        }
        return filter;
    }

    private static String requireField(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required territory field missing: " + label);
        }
        return value.trim();
    }

    static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
