package br.car.dsp_geo_file.export;

import java.util.List;
import java.util.Locale;

/**
 * A theme's table on {@code dsp-geoserver-db}, as introspected.
 */
public record FeatureTable(
        String qualifiedName,
        String primaryKeyColumn,
        List<FeatureColumn> columns
) {

    /**
     * One column, with the Postgres {@code udt_name} the CSV needs to render WKT
     * and UTC timestamps instead of the raw JDBC string.
     */
    public record FeatureColumn(String name, String udtName) {

        public boolean geometry() {
            return "geometry".equalsIgnoreCase(udtName);
        }

        public boolean timestamp() {
            if (udtName == null) {
                return false;
            }
            String type = udtName.toLowerCase(Locale.ROOT);
            return "timestamp".equals(type) || "timestamptz".equals(type);
        }
    }
}
