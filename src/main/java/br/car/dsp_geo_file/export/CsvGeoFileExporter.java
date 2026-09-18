package br.car.dsp_geo_file.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CSV in the same shape the GeoServer WFS returns: an {@code FID} column built from the layer
 * name and the primary key, then every attribute in table order, geometry rendered as WKT.
 *
 * <p>That shape is the contract: the endpoint falls back to the WFS when the object is missing,
 * and a citizen must not be able to tell which of the two answered.
 *
 * <p>Timestamp cells are UTC ISO-8601 with a literal {@code Z} and whole seconds
 * ({@code 2026-08-18T18:43:48Z}), matching GeoServer Download {@code csvDateFormat}.
 */
@Slf4j
@Component
public class CsvGeoFileExporter implements GeoFileExporter {

    public static final String FORMAT = "csv";

    private static final String FID_HEADER = "FID";
    private static final String LINE_SEPARATOR = "\r\n";
    private static final DateTimeFormatter CSV_UTC = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    @Override
    public boolean supports(String format) {
        return FORMAT.equalsIgnoreCase(format == null ? null : format.trim());
    }

    @Override
    public String contentType() {
        return "text/csv;charset=UTF-8";
    }

    @Override
    public String fileExtension() {
        return FORMAT;
    }

    @Override
    public GeneratedGeoFile generate(GeoFileExportContext context) {
        FeatureTable table = context.featureTable();
        String featureIdPrefix = FeatureTableResolver.featureIdPrefix(context.theme().typeName());

        StringBuilder csv = new StringBuilder();
        csv.append(header(table)).append(LINE_SEPARATOR);

        AtomicLong featureCount = new AtomicLong();

        context.geoTargetJdbcTemplate().query(
                selectSql(table, context.filter()),
                resultSet -> {
                    StringJoiner line = new StringJoiner(",");
                    line.add(escape(featureIdPrefix + "." + resultSet.getString(table.primaryKeyColumn())));
                    for (FeatureTable.FeatureColumn column : table.columns()) {
                        line.add(cell(column, resultSet));
                    }
                    csv.append(line).append(LINE_SEPARATOR);
                    featureCount.incrementAndGet();
                },
                context.filter().argsArray());

        if (featureCount.get() == 0L) {
            return GeneratedGeoFile.empty();
        }

        log.debug("Generated CSV for territory={} theme={} features={}",
                context.territory().id(), context.theme().code(), featureCount.get());
        return new GeneratedGeoFile(
                csv.toString().getBytes(StandardCharsets.UTF_8),
                featureCount.get());
    }

    private static String header(FeatureTable table) {
        StringJoiner header = new StringJoiner(",");
        header.add(FID_HEADER);
        for (FeatureTable.FeatureColumn column : table.columns()) {
            header.add(escape(column.name()));
        }
        return header.toString();
    }

    private static String selectSql(FeatureTable table, FeatureFilter filter) {
        StringJoiner columns = new StringJoiner(", ");
        for (FeatureTable.FeatureColumn column : table.columns()) {
            String quoted = TerritoryFeatureFilterBuilder.quoteIdentifier(column.name());
            columns.add(column.geometry() ? "ST_AsText(" + quoted + ") AS " + quoted : quoted);
        }
        return "SELECT " + columns + " FROM " + table.qualifiedName() + " WHERE " + filter.sql();
    }

    private static String cell(FeatureTable.FeatureColumn column, ResultSet resultSet) throws SQLException {
        if (!column.timestamp()) {
            return escape(resultSet.getString(column.name()));
        }
        return escape(formatUtc(resultSet, column.name()));
    }

    private static String formatUtc(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime offsetDateTime = readOffsetDateTime(resultSet, column);
        if (offsetDateTime != null) {
            return CSV_UTC.format(offsetDateTime.toInstant());
        }
        Timestamp timestamp = resultSet.getTimestamp(
                column, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        if (timestamp == null) {
            return "";
        }
        return CSV_UTC.format(timestamp.toInstant());
    }

    private static OffsetDateTime readOffsetDateTime(ResultSet resultSet, String column) {
        try {
            return resultSet.getObject(column, OffsetDateTime.class);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
