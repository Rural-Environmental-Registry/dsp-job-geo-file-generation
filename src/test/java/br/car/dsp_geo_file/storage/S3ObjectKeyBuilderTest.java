package br.car.dsp_geo_file.storage;

import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3ObjectKeyBuilderTest {

    private final S3ObjectKeyBuilder keyBuilder = new S3ObjectKeyBuilder();

    @Test
    void build_Level2UsesOwnSlugOnly() {
        Territory territory = new Territory(
                TerritoryLevel.LEVEL_2, "35", "São Paulo", null, null);

        assertEquals(
                "csv/level-2/sao-paulo_area_of_interest.csv",
                keyBuilder.build("csv", territory, "area_of_interest", "csv"));
    }

    @Test
    void build_Level3PrefixesParentSlug() {
        Territory territory = new Territory(
                TerritoryLevel.LEVEL_3, "3509502", "Campinas", "35", "São Paulo");

        assertEquals(
                "csv/level-3/sao-paulo_campinas_area_of_interest.csv",
                keyBuilder.build("csv", territory, "area_of_interest", "csv"));
    }

    @Test
    void build_KeepsSameLayoutForAnotherFormat() {
        Territory territory = new Territory(
                TerritoryLevel.LEVEL_2, "35", "São Paulo", null, null);

        assertEquals(
                "gpkg/level-2/sao-paulo_area_of_interest.gpkg",
                keyBuilder.build("gpkg", territory, "area_of_interest", "gpkg"));
    }

    @Test
    void build_RejectsLevel3WithoutParentName() {
        Territory territory = new Territory(
                TerritoryLevel.LEVEL_3, "3509502", "Campinas", null, null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> keyBuilder.build("csv", territory, "area_of_interest", "csv"));
        assertTrue(ex.getMessage().contains("parent name"));
    }

    @Test
    void build_RejectsTerritoryWithoutUsableName() {
        Territory territory = new Territory(
                TerritoryLevel.LEVEL_2, "35", "///", null, null);

        assertThrows(
                IllegalStateException.class,
                () -> keyBuilder.build("csv", territory, "area_of_interest", "csv"));
    }

    @Test
    void build_RejectsThemeCodeWithPathSeparator() {
        Territory territory = new Territory(
                TerritoryLevel.LEVEL_2, "35", "São Paulo", null, null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> keyBuilder.build("csv", territory, "area/of_interest", "csv"));
        assertTrue(ex.getMessage().contains("Theme code"));
    }

    @Test
    void prefix_IsFormatThenLevel() {
        assertEquals("csv/level-2/", keyBuilder.prefix("csv", TerritoryLevel.LEVEL_2));
        assertEquals("csv/level-3/", keyBuilder.prefix("csv", TerritoryLevel.LEVEL_3));
    }
}
