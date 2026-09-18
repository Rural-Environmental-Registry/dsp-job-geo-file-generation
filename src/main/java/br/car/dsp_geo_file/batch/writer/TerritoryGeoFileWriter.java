package br.car.dsp_geo_file.batch.writer;

import br.car.dsp_geo_file.batch.config.GeoFileGenerationContextKeys;
import br.car.dsp_geo_file.batch.config.GeoFileGenerationExitStatusResolver;
import br.car.dsp_geo_file.generation.GeoFileGenerationOrchestrator;
import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryFileStateRepository;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
public class TerritoryGeoFileWriter implements ItemWriter<Territory>, StepExecutionListener {

    private final GeoFileGenerationOrchestrator orchestrator;
    private final TerritoryFileStateRepository territoryRepository;

    private StepExecution stepExecution;

    public TerritoryGeoFileWriter(GeoFileGenerationOrchestrator orchestrator,
                                  TerritoryFileStateRepository territoryRepository) {
        this.orchestrator = orchestrator;
        this.territoryRepository = territoryRepository;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return GeoFileGenerationExitStatusResolver.fromGenerationStepContext(
                stepExecution.getExecutionContext());
    }

    @Override
    public void write(Chunk<? extends Territory> chunk) {
        for (Territory territory : chunk) {
            var result = orchestrator.publish(territory);
            accumulateResult(result);
            if (result.complete()) {
                territoryRepository.markGenerated(territory.level(), territory.id());
                log.info("Territory {} ({}) done — {} file(s) published, {} empty cut(s)",
                        territory.id(), territory.level(), result.published(), result.emptied());
            } else {
                log.error("[GEO_TERRITORY_PENDING] territory={} level={} configFailures={} "
                                + "transientFailures={} attempts={}",
                        territory.id(),
                        territory.level(),
                        result.configFailures(),
                        result.transientFailures(),
                        result.published() + result.emptied() + result.failed());
            }
        }
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
