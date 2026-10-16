package br.car.dsp_geo_file.export;

import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import br.car.dsp_geo_file.theme.DownloadTerritoryFilterConfig;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CsvGeoFileExporterTest {

    private final CsvGeoFileExporter exporter = new CsvGeoFileExporter();

    @Test
    void supports_OnlyCsv() {
        assertTrue(exporter.supports("csv"));
        assertTrue(exporter.supports("CSV"));
        assertFalse(exporter.supports("gpkg"));
        assertFalse(exporter.supports(null));
    }

    @Test
    void fileExtensionAndContentType_MatchTheFormat() {
        assertEquals("csv", exporter.fileExtension());
        assertTrue(exporter.contentType().startsWith("text/csv"));
    }

    @Test
    void generate_WritesFidHeaderAndAttributesInTableOrder() throws SQLException {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("id")).thenReturn("aoi-1");
        when(row.getString("name")).thenReturn("Sítio Boa Vista");
        when(row.getString("geom")).thenReturn("MULTIPOLYGON(((0 0,1 0,1 1,0 0)))");
        when(row.getObject("updated_at", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.parse("2026-08-18T18:43:48.092583Z"));

        GeneratedGeoFile file = exporter.generate(context(singleRow(row)));

        String csv = new String(file.content(), StandardCharsets.UTF_8);
        // WKT carries commas, so it comes out quoted — same as the WFS CSV output.
        assertEquals(
                "FID,id,name,geom,updated_at\r\n"
                        + "area-of-interest.aoi-1,aoi-1,Sítio Boa Vista,"
                        + "\"MULTIPOLYGON(((0 0,1 0,1 1,0 0)))\",2026-08-18T18:43:48Z\r\n",
                csv);
        assertEquals(1L, file.featureCount());
    }

    @Test
    void generate_QuotesValuesCarryingSeparators() throws SQLException {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("id")).thenReturn("aoi-1");
        when(row.getString("name")).thenReturn("Fazenda \"Dois Irmãos\", lote 3");
        when(row.getString("geom")).thenReturn("POINT(0 0)");

        GeneratedGeoFile file = exporter.generate(context(singleRow(row)));

        String csv = new String(file.content(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"Fazenda \"\"Dois Irmãos\"\", lote 3\""), csv);
    }

    @Test
    void generate_ReturnsEmptyWhenTheCutHasNoFeature() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        GeneratedGeoFile file = exporter.generate(context(jdbcTemplate));

        assertTrue(file.isEmpty());
        assertEquals(0, file.content().length);
    }

    @Test
    void writeToFile_WritesSameContentAsGenerate(@TempDir Path tempDir) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("id")).thenReturn("aoi-1");
        when(row.getString("name")).thenReturn("Sítio Boa Vista");
        when(row.getString("geom")).thenReturn("POINT(0 0)");
        when(row.getObject("updated_at", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.parse("2026-03-04T10:00:00Z"));

        Path target = tempDir.resolve("campinas_area.csv");
        GeneratedGeoFile file = exporter.writeToFile(context(singleRow(row)), target);

        assertFalse(file.isEmpty());
        assertEquals(new String(file.content(), StandardCharsets.UTF_8), Files.readString(target));
    }

    @Test
    void generate_SelectsGeometryAsWkt() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        StringBuilder executedSql = new StringBuilder();
        doAnswer(invocation -> {
            executedSql.append(invocation.<String>getArgument(0));
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        exporter.generate(context(jdbcTemplate));

        assertTrue(executedSql.toString().contains("ST_AsText(\"geom\") AS \"geom\""), executedSql.toString());
        assertTrue(executedSql.toString().contains("FROM dsp.area_of_interest WHERE"), executedSql.toString());
    }

    private static JdbcTemplate singleRow(ResultSet row) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            invocation.<RowCallbackHandler>getArgument(1).processRow(row);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        return jdbcTemplate;
    }

    private static GeoFileExportContext context(JdbcTemplate jdbcTemplate) {
        FeatureTable table = new FeatureTable(
                "dsp.area_of_interest",
                "id",
                List.of(
                        new FeatureTable.FeatureColumn("id", "varchar"),
                        new FeatureTable.FeatureColumn("name", "varchar"),
                        new FeatureTable.FeatureColumn("geom", "geometry"),
                        new FeatureTable.FeatureColumn("updated_at", "timestamptz")
                ));
        DownloadThemeConfig theme = new DownloadThemeConfig(
                "area_of_interest",
                "Area of interest",
                "dsp:area-of-interest",
                List.of("csv"),
                true,
                new DownloadTerritoryFilterConfig("direct", "territory_level_3_id", null),
                null);
        return new GeoFileExportContext(
                new Territory(TerritoryLevel.LEVEL_3, "3509502", "Campinas", "35", "São Paulo"),
                theme,
                table,
                new FeatureFilter("\"territory_level_3_id\" = ?", List.of("3509502")),
                jdbcTemplate);
    }
}
