package br.car.dsp_geo_file.batch.config;

import br.car.dsp_geo_file.generation.LocalStagingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Sets the job {@link org.springframework.batch.core.ExitStatus} from storage readiness and
 * publish counters so {@code BATCH_JOB_EXECUTION.EXIT_CODE} reflects business outcome.
 */
@Slf4j
@Component
public class GeoFileGenerationJobListener implements JobExecutionListener {

    private final LocalStagingService localStagingService;

    public GeoFileGenerationJobListener(LocalStagingService localStagingService) {
        this.localStagingService = localStagingService;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        try {
            localStagingService.prepareRun();
        } catch (Exception ex) {
            log.error("Failed to prepare staging directory: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Could not prepare staging directory", ex);
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            return;
        }
        jobExecution.setExitStatus(GeoFileGenerationExitStatusResolver.forJob(jobExecution));
    }
}
