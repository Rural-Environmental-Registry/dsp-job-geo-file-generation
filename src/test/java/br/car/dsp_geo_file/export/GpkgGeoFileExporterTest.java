package br.car.dsp_geo_file.export;

import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import br.car.dsp_geo_file.theme.DownloadTerritoryFilterConfig;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.sf.LinearRing;
import mil.nga.sf.Point;
import mil.nga.sf.Polygon;
import mil.nga.sf.wkb.GeometryWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GpkgGeoFileExporterTest {

    private final GpkgGeoFileExporter exporter = new GpkgGeoFileExporter();

    @Test
    void supports_OnlyGpkg() {
        assertTrue(exporter.supports("gpkg"));
        assertTrue(exporter.supports("GPKG"));
        assertFalse(exporter.supports("csv"));
        assertFalse(exporter.supports(null));
    }

    @Test
    void fileExtensionAndContentType_MatchTheFormat() {
        assertEquals("gpkg", exporter.fileExtension());
        assertEquals("application/geopackage+sqlite3", exporter.contentType());
    }

    @Test
    void writeToFile_WritesFeatureTableWithGeometryAndAttributes(@TempDir Path tempDir) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getBytes("geom")).thenReturn(polygonWkb());
        when(row.getObject("id")).thenReturn("aoi-1");
        when(row.getObject("name")).thenReturn("Sítio Boa Vista");
        when(row.getObject("updated_at")).thenReturn(OffsetDateTime.parse("2026-08-18T18:43:48Z"));
        when(row.wasNull()).thenReturn(false);

        Path target = tempDir.resolve("sao-paulo_area_of_interest.gpkg");
        GeneratedGeoFile file = exporter.writeToFile(context(singleRow(row)), target);

        assertFalse(file.isEmpty());
        assertEquals(1L, file.featureCount());
        assertEquals(0, file.content().length);
        assertTrue(Files.size(target) > 0);

        try (GeoPackage gpkg = GeoPackageManager.open(target.toFile())) {
            assertTrue(gpkg.isFeatureTable("area_of_interest"));
            try (var rs = gpkg.getConnection().getConnection().createStatement().executeQuery(
                    "SELECT id, name, updated_at FROM \"area_of_interest\"")) {
                assertTrue(rs.next());
                assertEquals("aoi-1", rs.getString("id"));
                assertEquals("Sítio Boa Vista", rs.getString("name"));
                assertEquals("2026-08-18T18:43:48Z", rs.getString("updated_at"));
            }
        }
    }

    @Test
    void writeToFile_UsesUndefinedGeographicSrsWhenTheCutHasNoSrid(@TempDir Path tempDir) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getBytes("geom")).thenReturn(polygonWkb());
        when(row.getObject("id")).thenReturn("aoi-1");
        when(row.getObject("name")).thenReturn("Sítio");
        when(row.wasNull()).thenReturn(false);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        doAnswer(invocation -> {
            invocation.<RowCallbackHandler>getArgument(1).processRow(row);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        Path target = tempDir.resolve("sem-srid.gpkg");
        exporter.writeToFile(context(jdbcTemplate), target);

        try (GeoPackage gpkg = GeoPackageManager.open(target.toFile());
             var rs = gpkg.getConnection().getConnection().createStatement().executeQuery(
                     """
                     SELECT s.srs_id, s.organization, s.organization_coordsys_id
                     FROM gpkg_geometry_columns g
                     JOIN gpkg_spatial_ref_sys s ON s.srs_id = g.srs_id
                     """)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("srs_id"));
            assertEquals("NONE", rs.getString("organization"));
            assertEquals(0, rs.getInt("organization_coordsys_id"));
        }
    }

    @Test
    void writeToFile_SkipsRowsWithoutGeometry(@TempDir Path tempDir) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getBytes("geom")).thenReturn(null);

        Path target = tempDir.resolve("empty.gpkg");
        GeneratedGeoFile file = exporter.writeToFile(context(singleRow(row)), target);

        assertTrue(file.isEmpty());
        assertFalse(Files.exists(target));
    }

    @Test
    void writeToFile_ReturnsEmptyWhenTheCutHasNoFeature(@TempDir Path tempDir) throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenThrow(new EmptyResultDataAccessException(1));

        Path target = tempDir.resolve("empty.gpkg");
        GeneratedGeoFile file = exporter.writeToFile(context(jdbcTemplate), target);

        assertTrue(file.isEmpty());
        assertFalse(Files.exists(target));
    }

    @Test
    void writeToFile_SelectsGeometryAsWkb(@TempDir Path tempDir) throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(4674);
        StringBuilder executedSql = new StringBuilder();
        doAnswer(invocation -> {
            executedSql.append(invocation.<String>getArgument(0));
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        exporter.writeToFile(context(jdbcTemplate), tempDir.resolve("campinas.gpkg"));

        assertTrue(executedSql.toString().contains("ST_AsBinary(\"geom\") AS \"geom\""), executedSql.toString());
        assertTrue(executedSql.toString().contains("FROM dsp.area_of_interest WHERE"), executedSql.toString());
    }

    @Test
    void generate_ReturnsTheFileBytes(@TempDir Path tempDir) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getBytes("geom")).thenReturn(polygonWkb());
        when(row.getObject("id")).thenReturn("aoi-1");
        when(row.getObject("name")).thenReturn("Sítio");
        when(row.wasNull()).thenReturn(false);

        GeneratedGeoFile file = exporter.generate(context(singleRow(row)));

        assertEquals(1L, file.featureCount());
        assertTrue(file.content().length > 0);
        Path copy = tempDir.resolve("copy.gpkg");
        Files.write(copy, file.content());
        try (GeoPackage gpkg = GeoPackageManager.open(copy.toFile())) {
            assertTrue(gpkg.isFeatureTable("area_of_interest"));
        }
    }

    private static byte[] polygonWkb() throws IOException {
        LinearRing ring = new LinearRing(List.of(
                new Point(0, 0), new Point(1, 0), new Point(1, 1), new Point(0, 0)));
        Polygon polygon = new Polygon(ring);
        return GeometryWriter.writeGeometry(polygon);
    }

    private static JdbcTemplate singleRow(ResultSet row) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(4674);
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
                List.of("gpkg"),
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
