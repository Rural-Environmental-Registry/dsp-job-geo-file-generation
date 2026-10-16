package br.car.dsp_geo_file.export;

import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import br.car.dsp_geo_file.theme.DownloadTerritoryFilterConfig;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryFeatureFilterBuilderTest {

    private final TerritoryFeatureFilterBuilder builder = new TerritoryFeatureFilterBuilder();

    @Test
    void build_DirectOnLevel3ComparesTheFieldItself() {
        FeatureFilter filter = builder.build(
                theme("direct", "territory_level_3_id", null),
                level3());

        assertEquals("\"territory_level_3_id\" = ?", filter.sql());
        assertEquals(List.of("3509502"), filter.args());
    }

    @Test
    void build_DirectOnLevel2GoesThroughTheChildren() {
        FeatureFilter filter = builder.build(
                theme("direct", "territory_level_3_id", null),
                level2());

        assertEquals(
                "\"territory_level_3_id\" IN (SELECT id FROM dsp.territory_level_3 WHERE parent_id = ?)",
                filter.sql());
        assertEquals(List.of("35"), filter.args());
    }

    @Test
    void build_AoiLinkedOnLevel3GoesThroughTheAreasOfInterest() {
        FeatureFilter filter = builder.build(
                theme("aoi_linked", null, "area_of_interest_id"),
                level3());

        assertEquals(
                "\"area_of_interest_id\" IN (SELECT id FROM dsp.area_of_interest"
                        + " WHERE territory_level_3_id = ?)",
                filter.sql());
        assertEquals(List.of("3509502"), filter.args());
    }

    @Test
    void build_AoiLinkedOnLevel2NestsBothSubqueries() {
        FeatureFilter filter = builder.build(
                theme("aoi_linked", null, "area_of_interest_id"),
                level2());

        assertEquals(
                "\"area_of_interest_id\" IN (SELECT id FROM dsp.area_of_interest"
                        + " WHERE territory_level_3_id IN ("
                        + "SELECT id FROM dsp.territory_level_3 WHERE parent_id = ?))",
                filter.sql());
    }

    @Test
    void build_IsCaseInsensitiveOnTheStrategy() {
        FeatureFilter filter = builder.build(
                theme("DIRECT", "territory_level_3_id", null),
                level3());

        assertEquals("\"territory_level_3_id\" = ?", filter.sql());
    }

    @Test
    void build_RejectsUnknownStrategy() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> builder.build(theme("bbox", "field", null), level3()));
        assertTrue(ex.getMessage().contains("bbox"));
    }

    @Test
    void build_RejectsMissingTerritoryFilter() {
        DownloadThemeConfig theme = new DownloadThemeConfig(
                "area_of_interest", "AOI", "dsp:area-of-interest", List.of("csv"), true, null, null);

        assertThrows(IllegalStateException.class, () -> builder.build(theme, level3()));
    }

    @Test
    void build_RejectsStrategyWithoutItsField() {
        assertThrows(
                IllegalStateException.class,
                () -> builder.build(theme("direct", null, null), level3()));
    }

    private static DownloadThemeConfig theme(String strategy, String level3Field, String aoiLinkField) {
        return new DownloadThemeConfig(
                "area_of_interest",
                "Area of interest",
                "dsp:area-of-interest",
                List.of("csv"),
                true,
                new DownloadTerritoryFilterConfig(strategy, level3Field, aoiLinkField),
                null);
    }

    private static Territory level2() {
        return new Territory(TerritoryLevel.LEVEL_2, "35", "São Paulo", null, null);
    }

    private static Territory level3() {
        return new Territory(TerritoryLevel.LEVEL_3, "3509502", "Campinas", "35", "São Paulo");
    }
}
