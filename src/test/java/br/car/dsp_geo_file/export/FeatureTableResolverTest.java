package br.car.dsp_geo_file.export;

import br.car.dsp_geo_file.theme.DownloadTerritoryFilterConfig;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureTableResolverTest {

    @Test
    void tableName_UsesNativeNameWhenTheGeoServerLayerDiffersFromPostgis() {
        DownloadThemeConfig theme = theme("dsp:300m", "theme_1");

        assertEquals("theme_1", FeatureTableResolver.tableName(theme));
    }

    @Test
    void tableName_FallsBackToTypeNameSlugWhenNativeNameIsAbsent() {
        DownloadThemeConfig theme = theme("dsp:area-of-interest", null);

        assertEquals("area_of_interest", FeatureTableResolver.tableName(theme));
    }

    @Test
    void resolve_LooksUpNativeNameOnGeoTarget() throws Exception {
        JdbcTemplate jdbc = jdbcWithTable("theme_1");
        FeatureTableResolver resolver = new FeatureTableResolver(jdbc);

        FeatureTable table = resolver.resolve(theme("dsp:300m", "theme_1"));

        assertEquals("dsp.theme_1", table.qualifiedName());
        assertEquals("id", table.primaryKeyColumn());
        assertEquals("varchar", table.columns().getFirst().udtName());
        assertFalse(table.columns().getFirst().timestamp());
        assertFalse(table.columns().getFirst().geometry());
    }

    @Test
    void resolve_LooksUpTypeNameSlugWhenNativeNameIsAbsent() throws Exception {
        JdbcTemplate jdbc = jdbcWithTable("area_of_interest");
        FeatureTableResolver resolver = new FeatureTableResolver(jdbc);

        FeatureTable table = resolver.resolve(theme("dsp:area-of-interest", null));

        assertEquals("dsp.area_of_interest", table.qualifiedName());
    }

    @Test
    void resolve_FailsWithTheDerivedTableWhenItDoesNotExist() throws Exception {
        JdbcTemplate jdbc = jdbcWithTable("theme_1");
        FeatureTableResolver resolver = new FeatureTableResolver(jdbc);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve(theme("dsp:300m", null)));
        assertTrue(ex.getMessage().contains("dsp.300m"));
    }

    private static DownloadThemeConfig theme(String typeName, String nativeName) {
        return new DownloadThemeConfig(
                "code",
                "name",
                typeName,
                List.of("csv"),
                true,
                new DownloadTerritoryFilterConfig("direct", "territory_level_3_id", null),
                nativeName);
    }

    private static JdbcTemplate jdbcWithTable(String existingTable) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            String table = invocation.getArgument(3);
            if (!existingTable.equals(table)) {
                return null;
            }
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("column_name")).thenReturn("id");
            when(rs.getString("udt_name")).thenReturn("varchar");
            invocation.<RowCallbackHandler>getArgument(1).processRow(rs);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(), any());
        when(jdbc.queryForList(anyString(), eq(String.class), eq("dsp"), eq(existingTable)))
                .thenReturn(List.of("id"));
        return jdbc;
    }
}
