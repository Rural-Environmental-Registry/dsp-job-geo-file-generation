package br.car.dsp_geo_file.batch.tasklet;

import br.car.dsp_geo_file.generation.OrphanObjectCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Collects objects with no territory, theme or format behind them.
 *
 * <p>Best-effort: a cleanup failure never fails the job, because the files that matter were
 * already published and an orphan only costs storage.
 */
@Slf4j
public class OrphanObjectCleanupTasklet implements Tasklet {

    private final OrphanObjectCleanupService cleanupService;
    private final boolean enabled;

    public OrphanObjectCleanupTasklet(OrphanObjectCleanupService cleanupService, boolean enabled) {
        this.cleanupService = cleanupService;
        this.enabled = enabled;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (!enabled) {
            log.info("Orphan cleanup disabled by configuration");
            return RepeatStatus.FINISHED;
        }
        try {
            cleanupService.cleanUp();
        } catch (RuntimeException ex) {
            log.error("Orphan cleanup failed — published files are unaffected: {}", ex.getMessage(), ex);
        }
        return RepeatStatus.FINISHED;
    }
}
