package br.car.dsp_geo_file.export;

import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Everything an exporter needs to produce one file: the territory, the theme, the resolved
 * table, the predicate that narrows it and the connection to read it.
 *
 * <p>The exporter runs the query itself rather than receiving rows, because formats disagree on
 * how geometry should come out of the database (WKT for CSV, WKB for GPKG).
 */
public record GeoFileExportContext(
        Territory territory,
        DownloadThemeConfig theme,
        FeatureTable featureTable,
        FeatureFilter filter,
        JdbcTemplate geoTargetJdbcTemplate
) {
}
