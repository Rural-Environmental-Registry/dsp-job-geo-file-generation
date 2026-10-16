package br.car.dsp_geo_file.generation;

import br.car.dsp_geo_file.export.GeoFileExportContext;
import br.car.dsp_geo_file.export.GeoFileExporter;
import br.car.dsp_geo_file.export.GeoFileExporterRegistry;
import br.car.dsp_geo_file.export.FeatureTableResolver;
import br.car.dsp_geo_file.export.GeneratedGeoFile;
import br.car.dsp_geo_file.export.TerritoryFeatureFilterBuilder;
import br.car.dsp_geo_file.storage.ObjectStorageClient;
import br.car.dsp_geo_file.storage.S3ObjectKeyBuilder;
import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import br.car.dsp_geo_file.theme.DownloadThemesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * For one territory, walks the enabled themes and the formats each theme declares, exports and
 * publishes every file.
 *
 * <p>A failure is scoped to the pair (theme, format) so one broken theme does not cost the
 * others their file — but any failure keeps the territory pending, which is what makes the next
 * run a retry instead of a no-op.
 */
@Slf4j
@Service
public class GeoFileGenerationOrchestrator {

    /** User-metadata with the PutObject instant; the backend shows it as last file generate. */
    public static final String GENERATED_AT_METADATA = "generated-at";

    private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private final DownloadThemesService downloadThemesService;
    private final GeoFileExporterRegistry exporterRegistry;
    private final FeatureTableResolver featureTableResolver;
    private final TerritoryFeatureFilterBuilder filterBuilder;
    private final S3ObjectKeyBuilder keyBuilder;
    private final LocalStagingService localStagingService;
    private final ObjectStorageClient objectStorageClient;
    private final JdbcTemplate geoTargetJdbcTemplate;
    private final GeoFileGenerationLogging generationLogging;

    public GeoFileGenerationOrchestrator(
            DownloadThemesService downloadThemesService,
            GeoFileExporterRegistry exporterRegistry,
            FeatureTableResolver featureTableResolver,
            TerritoryFeatureFilterBuilder filterBuilder,
            S3ObjectKeyBuilder keyBuilder,
            LocalStagingService localStagingService,
            ObjectStorageClient objectStorageClient,
            @Qualifier("geoTargetJdbcTemplate") JdbcTemplate geoTargetJdbcTemplate,
            GeoFileGenerationLogging generationLogging) {
        this.downloadThemesService = downloadThemesService;
        this.exporterRegistry = exporterRegistry;
        this.featureTableResolver = featureTableResolver;
        this.filterBuilder = filterBuilder;
        this.keyBuilder = keyBuilder;
        this.localStagingService = localStagingService;
        this.objectStorageClient = objectStorageClient;
        this.geoTargetJdbcTemplate = geoTargetJdbcTemplate;
        this.generationLogging = generationLogging;
    }

    /** Pairs (theme, format) that this job exports, for progress denominators. */
    public int exporterBackedFileCount() {
        int count = 0;
        for (DownloadThemeConfig theme : downloadThemesService.getEnabledThemes()) {
            if (theme.formats() == null) {
                continue;
            }
            for (String rawFormat : theme.formats()) {
                if (exporterRegistry.find(normalize(rawFormat)).isPresent()) {
                    count++;
                }
            }
        }
        return count;
    }

    public TerritoryPublishResult publish(Territory territory, GeoFileGenerationRunProgress progress) {
        int published = 0;
        int emptied = 0;
        int configFailures = 0;
        int transientFailures = 0;
        Map<String, Integer> publishedByFormat = new HashMap<>();
        Map<String, Integer> emptiedByFormat = new HashMap<>();

        for (DownloadThemeConfig theme : downloadThemesService.getEnabledThemes()) {
            if (theme.formats() == null) {
                continue;
            }
            for (String rawFormat : theme.formats()) {
                String format = normalize(rawFormat);
                var exporter = exporterRegistry.find(format);
                if (exporter.isEmpty()) {
                    log.debug("No exporter for format={} (theme={}) — served by WFS", format, theme.code());
                    continue;
                }
                progress.advanceFile();
                String key = keyBuilder.build(format, territory, theme.code(), exporter.get().fileExtension());
                try {
                    PublishOutcome outcome = publishOne(territory, theme, format, exporter.get());
                    if (outcome.published) {
                        published++;
                        incrementFormat(publishedByFormat, format);
                        generationLogging.logPublished(
                                territory, theme.code(), format, key, outcome.featureCount, progress);
                    } else {
                        emptied++;
                        incrementFormat(emptiedByFormat, format);
                        generationLogging.logEmpty(territory, theme.code(), format, key, progress);
                    }
                } catch (IllegalStateException ex) {
                    configFailures++;
                    generationLogging.logConfigError(
                            territory,
                            theme.code(),
                            format,
                            progress,
                            ex.getClass().getSimpleName(),
                            ex.getMessage(),
                            ex);
                } catch (RuntimeException ex) {
                    transientFailures++;
                    generationLogging.logTransientFailure(
                            territory,
                            theme.code(),
                            format,
                            progress,
                            ex.getClass().getSimpleName(),
                            ex.getMessage(),
                            ex);
                }
            }
        }
        return new TerritoryPublishResult(
                published, emptied, configFailures, transientFailures, publishedByFormat, emptiedByFormat);
    }

    private record PublishOutcome(boolean published, long featureCount) {
    }

    /** @return outcome with feature count when published */
    private PublishOutcome publishOne(Territory territory,
                                        DownloadThemeConfig theme,
                                        String format,
                                        GeoFileExporter exporter) {
        String key = keyBuilder.build(format, territory, theme.code(), exporter.fileExtension());
        Path stagingPath = localStagingService.resolvePath(key);
        try {
            GeneratedGeoFile file = exporter.writeToFile(new GeoFileExportContext(
                    territory,
                    theme,
                    featureTableResolver.resolve(theme),
                    filterBuilder.build(theme, territory),
                    geoTargetJdbcTemplate
            ), stagingPath);

            if (file.isEmpty()) {
                if (objectStorageClient.head(key).isPresent()) {
                    objectStorageClient.delete(key);
                }
                localStagingService.deleteQuietly(stagingPath);
                return new PublishOutcome(false, 0L);
            }

            try {
                objectStorageClient.putFile(
                        key, stagingPath, exporter.contentType(), generationMetadata());
                return new PublishOutcome(true, file.featureCount());
            } finally {
                localStagingService.deleteQuietly(stagingPath);
            }
        } catch (IOException ex) {
            localStagingService.deleteQuietly(stagingPath);
            throw new RuntimeException("Failed to stage file for " + key, ex);
        }
    }

    private static void incrementFormat(Map<String, Integer> counts, String format) {
        counts.merge(format, 1, Integer::sum);
    }

    private static Map<String, String> generationMetadata() {
        return Map.of(GENERATED_AT_METADATA, GENERATED_AT_FORMAT.format(Instant.now()));
    }

    private static String normalize(String format) {
        return format == null ? null : format.trim().toLowerCase(Locale.ROOT);
    }

    /** What happened for one territory; {@code failed() == 0} is what allows clearing the flag. */
    public record TerritoryPublishResult(
            int published,
            int emptied,
            int configFailures,
            int transientFailures,
            Map<String, Integer> publishedByFormat,
            Map<String, Integer> emptiedByFormat) {

        public TerritoryPublishResult {
            publishedByFormat = publishedByFormat == null ? Map.of() : Map.copyOf(publishedByFormat);
            emptiedByFormat = emptiedByFormat == null ? Map.of() : Map.copyOf(emptiedByFormat);
        }

        public TerritoryPublishResult(int published, int emptied, int configFailures, int transientFailures) {
            this(published, emptied, configFailures, transientFailures, Map.of(), Map.of());
        }

        public int failed() {
            return configFailures + transientFailures;
        }

        public boolean complete() {
            return failed() == 0;
        }

        public int filesProcessed() {
            return published + emptied + failed();
        }
    }
}
