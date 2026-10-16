package br.car.dsp_geo_file.batch.config;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Persists {@link GeoFileGenerationExitCodes#OBJECT_STORAGE_NOT_READY} on the readiness step when
 * the bucket did not answer.
 */
@Component
public class ObjectStorageReadinessStepListener implements StepExecutionListener {

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();
        return GeoFileGenerationExitStatusResolver.fromJobContext(jobContext);
    }
}
