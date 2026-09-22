package br.car.dsp_geo_file.generation;

import br.car.dsp_geo_file.batch.config.GeoFileGenerationProperties;
import br.car.dsp_geo_file.territory.Territory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Centralizes geo file generation log lines and levels (published INFO, empty DEBUG by default).
 */
@Slf4j
@Component
public class GeoFileGenerationLogging {

    private final GeoFileGenerationProperties properties;

    public GeoFileGenerationLogging(GeoFileGenerationProperties properties) {
        this.properties = properties;
    }

    public void logPublished(Territory territory,
                             String themeCode,
                             String format,
                             String key,
                             long featureCount,
                             GeoFileGenerationRunProgress progress) {
        log.info("[GEO_FILE_PUBLISHED] territory={} level={} territoryProgress={} fileProgress={} "
                        + "theme={} format={} features={} key={}",
                territory.id(),
                territory.level(),
                progress.territoryProgress(),
                progress.fileProgress(),
                themeCode,
                format,
                featureCount,
                key);
    }

    public void logEmpty(Territory territory,
                         String themeCode,
                         String format,
                         String key,
                         GeoFileGenerationRunProgress progress) {
        String message = "[GEO_FILE_EMPTY] territory={} level={} territoryProgress={} fileProgress={} "
                + "theme={} format={} key={}";
        Object[] args = {
                territory.id(),
                territory.level(),
                progress.territoryProgress(),
                progress.fileProgress(),
                themeCode,
                format,
                key
        };
        if (properties.isLogEachFile()) {
            log.info(message, args);
        } else {
            log.debug(message, args);
        }
    }

    public void logConfigError(Territory territory,
                               String themeCode,
                               String format,
                               GeoFileGenerationRunProgress progress,
                               String exceptionName,
                               String message,
                               Throwable cause) {
        log.error("[GEO_PUBLISH_CONFIG_ERROR] territory={} level={} territoryProgress={} fileProgress={} "
                        + "theme={} format={} exception={} message={}",
                territory.id(),
                territory.level(),
                progress.territoryProgress(),
                progress.fileProgress(),
                themeCode,
                format,
                exceptionName,
                message,
                cause);
    }

    public void logTransientFailure(Territory territory,
                                    String themeCode,
                                    String format,
                                    GeoFileGenerationRunProgress progress,
                                    String exceptionName,
                                    String message,
                                    Throwable cause) {
        log.error("[GEO_PUBLISH_FAILURE] territory={} level={} territoryProgress={} fileProgress={} "
                        + "theme={} format={} exception={} message={}",
                territory.id(),
                territory.level(),
                progress.territoryProgress(),
                progress.fileProgress(),
                themeCode,
                format,
                exceptionName,
                message,
                cause);
    }

    public void logGenerationQueue(int pendingLevel2, int pendingLevel3) {
        log.info("[GEO_GENERATION_QUEUE] pendingLevel2={} pendingLevel3={} total={}",
                pendingLevel2, pendingLevel3, pendingLevel2 + pendingLevel3);
    }

    public void logTerritoryDone(Territory territory,
                                 GeoFileGenerationRunProgress progress,
                                 Map<String, Integer> publishedByFormat,
                                 Map<String, Integer> emptiedByFormat,
                                 int configFailures,
                                 int transientFailures) {
        log.info("[GEO_TERRITORY_DONE] territory={} level={} territoryProgress={} "
                        + "publishedByFormat={} emptiedByFormat={} configFailures={} transientFailures={}",
                territory.id(),
                territory.level(),
                progress.territoryProgress(),
                formatBreakdown(publishedByFormat),
                formatBreakdown(emptiedByFormat),
                configFailures,
                transientFailures);
    }

    public void logGenerationProgress(String territoryProgress,
                                      int filesProcessed,
                                      int filesPublished,
                                      int filesEmptied) {
        log.info("[GEO_GENERATION_PROGRESS] territoryProgress={} filesProcessed={} filesPublished={} "
                        + "filesEmptied={}",
                territoryProgress, filesProcessed, filesPublished, filesEmptied);
    }

    public void logTerritoryPending(Territory territory,
                                    GeoFileGenerationRunProgress progress,
                                    int configFailures,
                                    int transientFailures,
                                    int attempts) {
        log.error("[GEO_TERRITORY_PENDING] territory={} level={} territoryProgress={} configFailures={} "
                        + "transientFailures={} attempts={}",
                territory.id(),
                territory.level(),
                progress.territoryProgress(),
                configFailures,
                transientFailures,
                attempts);
    }

    static String formatBreakdown(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(entry.getKey()).append(':').append(entry.getValue());
        }
        builder.append('}');
        return builder.toString();
    }
}
