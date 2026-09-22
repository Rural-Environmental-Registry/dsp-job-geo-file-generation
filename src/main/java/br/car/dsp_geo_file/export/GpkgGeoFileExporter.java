package br.car.dsp_geo_file.export;

import lombok.extern.slf4j.Slf4j;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.srs.SpatialReferenceSystem;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.features.columns.GeometryColumns;
import mil.nga.geopackage.features.user.FeatureColumn;
import mil.nga.geopackage.features.user.FeatureTableMetadata;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.proj.ProjectionConstants;
import mil.nga.sf.Geometry;
import mil.nga.sf.GeometryType;
import mil.nga.sf.wkb.GeometryReader;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * GeoPackage for one territory and theme. One SQLite file, one feature table named after the
 * theme code, geometry in {@code the_geom} (SRID taken from PostGIS; the undefined geographic
 * SRS when the cut has none), and the remaining columns of the geo-target table.
 *
 * <p>Rows without geometry are skipped. The file is
 * written straight to disk: {@link #writeToFile} does not keep the bytes in memory.
 */
@Slf4j
@Component
public class GpkgGeoFileExporter implements GeoFileExporter {

    public static final String FORMAT = "gpkg";

    static final String GEOMETRY_COLUMN = "the_geom";

    /** GeoPackage row id. Not {@code id}, which is a common column on the source tables. */
    static final String FEATURE_ID_COLUMN = "fid";

    private static final int BATCH_SIZE = 2500;

    private static final DateTimeFormatter UTC_SECONDS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    @Override
    public boolean supports(String format) {
        return FORMAT.equalsIgnoreCase(format == null ? null : format.trim());
    }

    @Override
    public String contentType() {
        return "application/geopackage+sqlite3";
    }

    @Override
    public String fileExtension() {
        return FORMAT;
    }

    @Override
    public GeneratedGeoFile generate(GeoFileExportContext context) {
        Path temp = null;
        try {
            temp = Files.createTempFile("dsp-", ".gpkg");
            GeneratedGeoFile written = writeToFile(context, temp);
            if (written.isEmpty()) {
                return written;
            }
            return new GeneratedGeoFile(Files.readAllBytes(temp), written.featureCount());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate GeoPackage", ex);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    log.warn("Could not delete temporary GeoPackage {}", temp);
                }
            }
        }
    }

    /**
     * Writes the GeoPackage at {@code target}. {@code content} stays empty: the bytes are the
     * file itself, which the publisher uploads from disk.
     */
    @Override
    public GeneratedGeoFile writeToFile(GeoFileExportContext context, Path target) throws IOException {
        long count = writePackage(context, target);
        if (count == 0L) {
            return GeneratedGeoFile.empty();
        }
        return new GeneratedGeoFile(new byte[0], count);
    }

    private long writePackage(GeoFileExportContext context, Path target) throws IOException {
        FeatureTable table = context.featureTable();
        FeatureTable.FeatureColumn geometry = geometryColumn(table);
        if (geometry == null) {
            throw new IllegalStateException(
                    "Theme " + context.theme().code() + " has no geometry column");
        }

        List<Attribute> attributes = attributes(table);
        int srid = resolveSrid(context, geometry);
        String featureTable = featureTableName(context.theme().code());

        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.deleteIfExists(target);

        GeoPackageManager.create(target.toFile());
        GeoPackage gpkg = GeoPackageManager.open(target.toFile());
        long count = 0L;
        try {
            applyPerformancePragmas(gpkg.getConnection().getConnection());
            int headerSrid = createFeatureTable(gpkg, featureTable, srid, attributes);
            count = insertFeatures(gpkg, context, featureTable, geometry, attributes, headerSrid);
        } catch (RuntimeException ex) {
            closeQuietly(gpkg);
            Files.deleteIfExists(target);
            throw ex;
        } catch (Exception ex) {
            closeQuietly(gpkg);
            Files.deleteIfExists(target);
            throw new IllegalStateException(
                    "Failed to write GeoPackage for theme " + context.theme().code(), ex);
        }
        closeQuietly(gpkg);

        if (count == 0L) {
            Files.deleteIfExists(target);
            return 0L;
        }

        log.debug("Generated GPKG for territory={} theme={} features={} srid={}",
                context.territory().id(), context.theme().code(), count, srid);
        return count;
    }

    private long insertFeatures(GeoPackage gpkg,
                                GeoFileExportContext context,
                                String featureTable,
                                FeatureTable.FeatureColumn geometry,
                                List<Attribute> attributes,
                                int headerSrid) throws SQLException {
        String sql = insertSql(featureTable, attributes);
        Connection conn = gpkg.getConnection().getConnection();
        long[] count = {0L};
        int[] pending = {0};

        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            RowCallbackHandler handler = resultSet -> bindRow(
                    ps, resultSet, geometry, attributes, headerSrid, conn, count, pending);
            context.geoTargetJdbcTemplate().query(
                    selectSql(context.featureTable(), geometry, context.filter()),
                    handler,
                    context.filter().argsArray());
            if (pending[0] % BATCH_SIZE != 0) {
                ps.executeBatch();
            }
            conn.commit();
        } catch (RuntimeException ex) {
            rollbackQuietly(conn);
            throw ex;
        } catch (Exception ex) {
            rollbackQuietly(conn);
            throw new IllegalStateException("Failed to insert GeoPackage features", ex);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // The connection is closed with the GeoPackage.
            }
        }
        return count[0];
    }

    private void bindRow(PreparedStatement ps,
                         ResultSet resultSet,
                         FeatureTable.FeatureColumn geometry,
                         List<Attribute> attributes,
                         int headerSrid,
                         Connection conn,
                         long[] count,
                         int[] pending) throws SQLException {
        byte[] wkb = readGeometry(resultSet, geometry.name());
        if (wkb == null || wkb.length == 0) {
            return;
        }
        byte[] gpkgGeometry;
        try {
            Geometry parsed = GeometryReader.readGeometry(wkb);
            gpkgGeometry = GeoPackageGeometryData.create(headerSrid, parsed).toBytes();
        } catch (Exception ex) {
            log.warn("Skipping feature with unreadable geometry: {}", ex.getMessage());
            return;
        }
        ps.setBytes(1, gpkgGeometry);
        int index = 2;
        for (Attribute attribute : attributes) {
            bindValue(ps, index++, attribute.sourceName(), attribute.type(), resultSet);
        }
        ps.addBatch();
        count[0]++;
        pending[0]++;
        if (pending[0] % BATCH_SIZE == 0) {
            ps.executeBatch();
            ps.clearBatch();
            conn.commit();
        }
    }

    private int resolveSrid(GeoFileExportContext context, FeatureTable.FeatureColumn geometry) {
        String quoted = TerritoryFeatureFilterBuilder.quoteIdentifier(geometry.name());
        String sql = "SELECT ST_SRID(" + quoted + ") FROM " + context.featureTable().qualifiedName()
                + " WHERE " + context.filter().sql() + " AND " + quoted + " IS NOT NULL LIMIT 1";
        try {
            Integer srid = context.geoTargetJdbcTemplate().queryForObject(
                    sql, Integer.class, context.filter().argsArray());
            if (srid == null || srid == 0) {
                return 0;
            }
            return srid;
        } catch (EmptyResultDataAccessException ex) {
            return 0;
        }
    }

    private static String selectSql(FeatureTable table,
                                    FeatureTable.FeatureColumn geometry,
                                    FeatureFilter filter) {
        StringBuilder columns = new StringBuilder();
        boolean first = true;
        for (FeatureTable.FeatureColumn column : table.columns()) {
            if (column.geometry() && column != geometry) {
                continue;
            }
            if (!first) {
                columns.append(", ");
            }
            first = false;
            String quoted = TerritoryFeatureFilterBuilder.quoteIdentifier(column.name());
            if (column.geometry()) {
                columns.append("ST_AsBinary(").append(quoted).append(") AS ").append(quoted);
            } else {
                columns.append(quoted);
            }
        }
        return "SELECT " + columns + " FROM " + table.qualifiedName() + " WHERE " + filter.sql();
    }

    private static String insertSql(String featureTable, List<Attribute> attributes) {
        StringBuilder columns = new StringBuilder(quote(GEOMETRY_COLUMN));
        StringBuilder values = new StringBuilder("?");
        for (Attribute attribute : attributes) {
            columns.append(", ").append(quote(attribute.name()));
            values.append(",?");
        }
        return "INSERT INTO " + quote(featureTable) + " (" + columns + ") VALUES (" + values + ")";
    }

    /** @return the GeoPackage {@code srs_id} written in each geometry header */
    private int createFeatureTable(GeoPackage gpkg,
                                   String featureTable,
                                   int srid,
                                   List<Attribute> attributes) throws SQLException {
        if (!gpkg.getGeometryColumnsDao().isTableExists()) {
            gpkg.createGeometryColumnsTable();
        }
        List<FeatureColumn> columns = new ArrayList<>();
        for (Attribute attribute : attributes) {
            columns.add(FeatureColumn.createColumn(attribute.name(), attribute.type()));
        }
        SpatialReferenceSystem srs = ensureSrs(gpkg, srid);
        GeometryColumns geometryColumns = new GeometryColumns();
        geometryColumns.setTableName(featureTable);
        geometryColumns.setColumnName(GEOMETRY_COLUMN);
        geometryColumns.setGeometryType(GeometryType.GEOMETRY);
        geometryColumns.setSrs(srs);
        geometryColumns.setZ((byte) 0);
        geometryColumns.setM((byte) 0);
        gpkg.createFeatureTable(FeatureTableMetadata.create(
                geometryColumns, FEATURE_ID_COLUMN, columns));
        return (int) srs.getSrsId();
    }

    /**
     * The library only creates WGS84 and Web Mercator on its own. Any other EPSG, including
     * SIRGAS 2000 (4674), is inserted with {@code srs_id} equal to the EPSG code so the
     * geometry header and {@code gpkg_geometry_columns} agree.
     *
     * <p>{@code 0} is not an EPSG code. PostGIS uses it for an unknown SRID, and GeoPackage
     * already reserves {@code srs_id} 0 for the undefined geographic CRS. The column cannot
     * be null.
     */
    private static SpatialReferenceSystem ensureSrs(GeoPackage gpkg, int srid) throws SQLException {
        var dao = gpkg.getSpatialReferenceSystemDao();
        if (srid == 0) {
            return dao.getOrCreateCode(
                    ProjectionConstants.AUTHORITY_NONE,
                    ProjectionConstants.UNDEFINED_GEOGRAPHIC);
        }
        if (srid == ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM
                || srid == ProjectionConstants.EPSG_WEB_MERCATOR
                || srid == ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM_GEOGRAPHICAL_3D) {
            return dao.getOrCreateFromEpsg(srid);
        }
        SpatialReferenceSystem existing = dao.queryForOrganizationCoordsysId(
                ProjectionConstants.AUTHORITY_EPSG, srid);
        if (existing != null) {
            return existing;
        }
        SpatialReferenceSystem created = new SpatialReferenceSystem();
        created.setSrsName("EPSG:" + srid);
        created.setSrsId(srid);
        created.setOrganization(ProjectionConstants.AUTHORITY_EPSG);
        created.setOrganizationCoordsysId(srid);
        created.setDefinition(wkt(srid));
        created.setDescription("EPSG:" + srid);
        dao.create(created);
        return created;
    }

    private static String wkt(int srid) {
        if (srid == 4674) {
            return "GEOGCS[\"SIRGAS 2000\",DATUM[\"Sistema_de_Referencia_Geocentrico_para_las_AmericaS_2000\","
                    + "SPHEROID[\"GRS 1980\",6378137,298.257222101]],PRIMEM[\"Greenwich\",0],"
                    + "UNIT[\"degree\",0.0174532925199433],AUTHORITY[\"EPSG\",\"4674\"]]";
        }
        return "GEOGCS[\"EPSG:" + srid + "\",DATUM[\"unknown\",SPHEROID[\"unknown\",6378137,298.257223563]],"
                + "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],"
                + "AUTHORITY[\"EPSG\",\"" + srid + "\"]]";
    }

    private static void bindValue(PreparedStatement ps,
                                  int index,
                                  String sourceName,
                                  GeoPackageDataType type,
                                  ResultSet resultSet) throws SQLException {
        Object value = resultSet.getObject(sourceName);
        if (value == null || resultSet.wasNull()) {
            ps.setObject(index, null);
            return;
        }
        switch (type) {
            case INTEGER -> ps.setLong(index, ((Number) value).longValue());
            case REAL -> ps.setDouble(index, ((Number) value).doubleValue());
            case BOOLEAN -> ps.setInt(index, truth(value) ? 1 : 0);
            case BLOB -> ps.setBytes(index, (byte[]) value);
            default -> ps.setString(index, text(value));
        }
    }

    private static byte[] readGeometry(ResultSet resultSet, String column) {
        try {
            return resultSet.getBytes(column);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read geometry column " + column, ex);
        }
    }

    private static boolean truth(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static String text(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return UTC_SECONDS.format(offsetDateTime.toInstant());
        }
        if (value instanceof Instant instant) {
            return UTC_SECONDS.format(instant);
        }
        if (value instanceof Timestamp timestamp) {
            return UTC_SECONDS.format(timestamp.toInstant());
        }
        if (value instanceof Date date) {
            return UTC_SECONDS.format(date.toInstant());
        }
        return value.toString();
    }

    private static List<Attribute> attributes(FeatureTable table) {
        List<Attribute> attributes = new ArrayList<>();
        boolean geometryTaken = false;
        for (FeatureTable.FeatureColumn column : table.columns()) {
            if (column.geometry()) {
                if (!geometryTaken) {
                    geometryTaken = true;
                    continue;
                }
                log.warn("Ignoring extra geometry column {}", column.name());
                continue;
            }
            attributes.add(new Attribute(
                    column.name(), columnName(column.name()), dataType(column.udtName())));
        }
        return attributes;
    }

    private static FeatureTable.FeatureColumn geometryColumn(FeatureTable table) {
        for (FeatureTable.FeatureColumn column : table.columns()) {
            if (column.geometry()) {
                return column;
            }
        }
        return null;
    }

    static GeoPackageDataType dataType(String udtName) {
        if (udtName == null) {
            return GeoPackageDataType.TEXT;
        }
        return switch (udtName.toLowerCase(Locale.ROOT)) {
            case "int2", "int4", "int8", "integer", "bigint", "smallint" -> GeoPackageDataType.INTEGER;
            case "float4", "float8", "numeric", "decimal", "real", "float", "double" -> GeoPackageDataType.REAL;
            case "bool", "boolean" -> GeoPackageDataType.BOOLEAN;
            case "bytea" -> GeoPackageDataType.BLOB;
            default -> GeoPackageDataType.TEXT;
        };
    }

    /**
     * Theme code as a SQLite table name. Codes already match {@code [A-Za-z0-9_]+}; a leading
     * digit is prefixed so the name stays a valid identifier.
     */
    static String columnName(String sourceName) {
        if (FEATURE_ID_COLUMN.equalsIgnoreCase(sourceName)
                || GEOMETRY_COLUMN.equalsIgnoreCase(sourceName)) {
            return sourceName + "_attr";
        }
        return sourceName;
    }

    static String featureTableName(String themeCode) {
        String raw = themeCode == null ? "" : themeCode.trim();
        String safe = raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.isEmpty() || Character.isDigit(safe.charAt(0))) {
            safe = "t_" + safe;
        }
        return safe;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static void applyPerformancePragmas(Connection conn) {
        try (java.sql.Statement statement = conn.createStatement()) {
            statement.execute("PRAGMA cache_size = -16000");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA temp_store = MEMORY");
        } catch (SQLException ex) {
            log.warn("Could not apply SQLite pragmas: {}", ex.getMessage());
        }
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // The caller still fails the territory.
        }
    }

    private static void closeQuietly(GeoPackage gpkg) {
        try {
            gpkg.close();
        } catch (RuntimeException ignored) {
            // Best effort before deleting a partial file.
        }
    }

    private record Attribute(String sourceName, String name, GeoPackageDataType type) {
    }
}
