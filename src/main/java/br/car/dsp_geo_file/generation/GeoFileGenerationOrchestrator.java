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

    public GeoFileGenerationOrchestrator(
            DownloadThemesService downloadThemesService,
            GeoFileExporterRegistry exporterRegistry,
            FeatureTableResolver featureTableResolver,
            TerritoryFeatureFilterBuilder filterBuilder,
            S3ObjectKeyBuilder keyBuilder,
            LocalStagingService localStagingService,
            ObjectStorageClient objectStorageClient,
            @Qualifier("geoTargetJdbcTemplate") JdbcTemplate geoTargetJdbcTemplate) {
        this.downloadThemesService = downloadThemesService;
        this.exporterRegistry = exporterRegistry;
        this.featureTableResolver = featureTableResolver;
        this.filterBuilder = filterBuilder;
        this.keyBuilder = keyBuilder;
        this.localStagingService = localStagingService;
        this.objectStorageClient = objectStorageClient;
        this.geoTargetJdbcTemplate = geoTargetJdbcTemplate;
    }

    public TerritoryPublishResult publish(Territory territory) {
        int published = 0;
        int emptied = 0;
        int configFailures = 0;
        int transientFailures = 0;

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
                try {
                    if (publishOne(territory, theme, format, exporter.get())) {
                        published++;
                    } else {
                        emptied++;
                    }
                } catch (IllegalStateException ex) {
                    configFailures++;
                    log.error("[GEO_PUBLISH_CONFIG_ERROR] territory={} level={} theme={} format={} "
                                    + "exception={} message={}",
                            territory.id(), territory.level(), theme.code(), format,
                            ex.getClass().getSimpleName(), ex.getMessage(), ex);
                } catch (RuntimeException ex) {
                    transientFailures++;
                    log.error("[GEO_PUBLISH_FAILURE] territory={} level={} theme={} format={} "
                                    + "exception={} message={}",
                            territory.id(), territory.level(), theme.code(), format,
                            ex.getClass().getSimpleName(), ex.getMessage(), ex);
                }
            }
        }
        return new TerritoryPublishResult(published, emptied, configFailures, transientFailures);
    }

    /** True when an object was written, false when the cut is empty and the object was removed. */
    private boolean publishOne(Territory territory,
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
                // No features left in the cut: a stale object would keep answering downloads
                // the WFS itself would refuse.
                if (objectStorageClient.head(key).isPresent()) {
                    objectStorageClient.delete(key);
                }
                localStagingService.deleteQuietly(stagingPath);
                return false;
            }

            try {
                objectStorageClient.putFile(
                        key, stagingPath, exporter.contentType(), generationMetadata());
                return true;
            } finally {
                localStagingService.deleteQuietly(stagingPath);
            }
        } catch (IOException ex) {
            localStagingService.deleteQuietly(stagingPath);
            throw new RuntimeException("Failed to stage file for " + key, ex);
        }
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
            int transientFailures) {

        public int failed() {
            return configFailures + transientFailures;
        }

        public boolean complete() {
            return failed() == 0;
        }
    }
}
