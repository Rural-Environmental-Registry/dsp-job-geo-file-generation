package br.car.dsp_geo_file.batch.writer;

import br.car.dsp_geo_file.batch.config.GeoFileGenerationContextKeys;
import br.car.dsp_geo_file.batch.config.GeoFileGenerationExitStatusResolver;
import br.car.dsp_geo_file.batch.config.GeoFileGenerationProperties;
import br.car.dsp_geo_file.generation.GeoFileGenerationLogging;
import br.car.dsp_geo_file.generation.GeoFileGenerationOrchestrator;
import br.car.dsp_geo_file.generation.GeoFileGenerationRunProgress;
import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryFileStateRepository;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Publishes every file of the territory and clears the flag only on a complete round.
 *
 * <p>A partial round (one format published, another failed) deliberately keeps
 * {@code requires_s3_file_regeneration = true}: republishing a file that is already correct
 * costs a cycle, serving a stale one costs the citizen wrong data.
 */
@Component
public class TerritoryGeoFileWriter implements ItemWriter<Territory>, StepExecutionListener {

    private final GeoFileGenerationOrchestrator orchestrator;
    private final TerritoryFileStateRepository territoryRepository;
    private final GeoFileGenerationProperties properties;
    private final GeoFileGenerationLogging generationLogging;

    private StepExecution stepExecution;
    private GeoFileGenerationRunProgress runProgress;

    public TerritoryGeoFileWriter(GeoFileGenerationOrchestrator orchestrator,
                                  TerritoryFileStateRepository territoryRepository,
                                  GeoFileGenerationProperties properties,
                                  GeoFileGenerationLogging generationLogging) {
        this.orchestrator = orchestrator;
        this.territoryRepository = territoryRepository;
        this.properties = properties;
        this.generationLogging = generationLogging;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        int pendingLevel2 = territoryRepository.countPending(TerritoryLevel.LEVEL_2);
        int pendingLevel3 = territoryRepository.countPending(TerritoryLevel.LEVEL_3);
        generationLogging.logGenerationQueue(pendingLevel2, pendingLevel3);
        runProgress = new GeoFileGenerationRunProgress(pendingLevel2 + pendingLevel3);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return GeoFileGenerationExitStatusResolver.fromGenerationStepContext(
                stepExecution.getExecutionContext());
    }

    @Override
    public void write(Chunk<? extends Territory> chunk) {
        for (Territory territory : chunk) {
            runProgress.startTerritory(orchestrator.exporterBackedFileCount());
            var result = orchestrator.publish(territory, runProgress);
            accumulateResult(result);
            if (result.complete()) {
                territoryRepository.markGenerated(territory.level(), territory.id());
                generationLogging.logTerritoryDone(
                        territory,
                        runProgress,
                        result.publishedByFormat(),
                        result.emptiedByFormat(),
                        result.configFailures(),
                        result.transientFailures());
            } else {
                generationLogging.logTerritoryPending(
                        territory,
                        runProgress,
                        result.configFailures(),
                        result.transientFailures(),
                        result.filesProcessed());
            }
            maybeLogHeartbeat();
        }
    }

    private void maybeLogHeartbeat() {
        int every = properties.getProgressLogEveryTerritories();
        if (every <= 0 || runProgress == null || stepExecution == null) {
            return;
        }
        if (runProgress.territoryIndex() % every != 0) {
            return;
        }
        var context = stepExecution.getExecutionContext();
        generationLogging.logGenerationProgress(
                runProgress.territoryProgress(),
                context.getInt(GeoFileGenerationContextKeys.FILES_PROCESSED, 0),
                context.getInt(GeoFileGenerationContextKeys.FILES_PUBLISHED, 0),
                context.getInt(GeoFileGenerationContextKeys.FILES_EMPTIED, 0));
    }

    private void accumulateResult(GeoFileGenerationOrchestrator.TerritoryPublishResult result) {
        if (stepExecution == null) {
            return;
        }
        var context = stepExecution.getExecutionContext();
        context.putInt(
                GeoFileGenerationContextKeys.FILES_PUBLISHED,
                context.getInt(GeoFileGenerationContextKeys.FILES_PUBLISHED, 0) + result.published());
        context.putInt(
                GeoFileGenerationContextKeys.FILES_EMPTIED,
                context.getInt(GeoFileGenerationContextKeys.FILES_EMPTIED, 0) + result.emptied());
        context.putInt(
                GeoFileGenerationContextKeys.FILES_PROCESSED,
                context.getInt(GeoFileGenerationContextKeys.FILES_PROCESSED, 0) + result.filesProcessed());
        if (result.complete()) {
            context.putInt(
                    GeoFileGenerationContextKeys.TERRITORIES_COMPLETED,
                    context.getInt(GeoFileGenerationContextKeys.TERRITORIES_COMPLETED, 0) + 1);
            return;
        }
        context.putInt(
                GeoFileGenerationContextKeys.PUBLISH_CONFIG_FAILURES,
                context.getInt(GeoFileGenerationContextKeys.PUBLISH_CONFIG_FAILURES, 0)
                        + result.configFailures());
        context.putInt(
                GeoFileGenerationContextKeys.PUBLISH_TRANSIENT_FAILURES,
                context.getInt(GeoFileGenerationContextKeys.PUBLISH_TRANSIENT_FAILURES, 0)
                        + result.transientFailures());
        context.putInt(
                GeoFileGenerationContextKeys.TERRITORIES_WITH_FAILURES,
                context.getInt(GeoFileGenerationContextKeys.TERRITORIES_WITH_FAILURES, 0) + 1);
    }
}
