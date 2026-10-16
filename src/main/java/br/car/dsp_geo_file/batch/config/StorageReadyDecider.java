package br.car.dsp_geo_file.batch.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.stereotype.Component;

/**
 * Routes the job to generation only when the bucket answered.
 */
@Slf4j
@Component
public class StorageReadyDecider implements JobExecutionDecider {

    static final String PROCESS = "PROCESS";
    static final String SKIP = "SKIP";

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        Boolean ready = (Boolean) jobExecution.getExecutionContext()
                .get(GeoFileGenerationContextKeys.STORAGE_READY);
        if (Boolean.TRUE.equals(ready)) {
            return new FlowExecutionStatus(PROCESS);
        }
        log.warn("Object storage not ready — skipping generation and orphan cleanup");
        return new FlowExecutionStatus(SKIP);
    }
}
