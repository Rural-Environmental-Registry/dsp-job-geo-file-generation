package br.car.dsp_geo_file.batch.config;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoFileGenerationExitStatusResolverTest {

    @Test
    void fromGenerationStepContext_CompletedWithPublishStats() {
        ExecutionContext context = generationContext(10, 2, 5, 0, 0, 0);

        ExitStatus status = GeoFileGenerationExitStatusResolver.fromGenerationStepContext(context);

        assertEquals(GeoFileGenerationExitCodes.COMPLETED, status.getExitCode());
        assertTrue(status.getExitDescription().contains("filesPublished=10"));
        assertTrue(status.getExitDescription().contains("filesEmptied=2"));
        assertTrue(status.getExitDescription().contains("territoriesCompleted=5"));
        assertTrue(status.getExitDescription().contains("territoriesWithFailures=0"));
    }

    @Test
    void fromGenerationStepContext_CompletedWithZeroWorkStillDescribesStats() {
        ExecutionContext context = generationContext(0, 0, 0, 0, 0, 0);

        ExitStatus status = GeoFileGenerationExitStatusResolver.fromGenerationStepContext(context);

        assertEquals(GeoFileGenerationExitCodes.COMPLETED, status.getExitCode());
        assertTrue(status.getExitDescription().contains("filesPublished=0"));
        assertTrue(status.getExitDescription().contains("territoriesCompleted=0"));
    }

    @Test
    void fromGenerationStepContext_ConfigErrorsIncludePublishStats() {
        ExecutionContext context = generationContext(3, 1, 2, 1, 2, 0);

        ExitStatus status = GeoFileGenerationExitStatusResolver.fromGenerationStepContext(context);

        assertEquals(GeoFileGenerationExitCodes.PUBLISH_CONFIG_ERRORS, status.getExitCode());
        assertTrue(status.getExitDescription().contains("filesPublished=3"));
        assertTrue(status.getExitDescription().contains("configFailures=2"));
        assertTrue(status.getExitDescription().contains("transientFailures=0"));
    }

    @Test
    void fromGenerationStepContext_TransientFailuresIncludePublishStats() {
        ExecutionContext context = generationContext(5, 0, 4, 2, 0, 3);

        ExitStatus status = GeoFileGenerationExitStatusResolver.fromGenerationStepContext(context);

        assertEquals(GeoFileGenerationExitCodes.PUBLISH_TRANSIENT_FAILURES, status.getExitCode());
        assertTrue(status.getExitDescription().contains("filesPublished=5"));
        assertTrue(status.getExitDescription().contains("transientFailures=3"));
    }

    @Test
    void fromGenerationStepContext_MixedFailures() {
        ExecutionContext context = generationContext(1, 0, 0, 2, 1, 2);

        ExitStatus status = GeoFileGenerationExitStatusResolver.fromGenerationStepContext(context);

        assertEquals(GeoFileGenerationExitCodes.PUBLISH_MIXED_FAILURES, status.getExitCode());
    }

    @Test
    void storageNotReady_BucketMissing() {
        ExitStatus status = GeoFileGenerationExitStatusResolver.storageNotReady(
                GeoFileGenerationExitCodes.REASON_BUCKET_MISSING, "dsp-geo-files");

        assertEquals(GeoFileGenerationExitCodes.OBJECT_STORAGE_NOT_READY, status.getExitCode());
        assertEquals("reason=bucket_missing bucket=dsp-geo-files", status.getExitDescription());
    }

    @Test
    void storageNotReady_Unreachable() {
        ExitStatus status = GeoFileGenerationExitStatusResolver.storageNotReady(
                GeoFileGenerationExitCodes.REASON_UNREACHABLE, "connection refused");

        assertEquals(GeoFileGenerationExitCodes.OBJECT_STORAGE_NOT_READY, status.getExitCode());
        assertEquals("reason=unreachable message=connection refused", status.getExitDescription());
    }

    @Test
    void forJob_StorageNotReadyTakesPriorityOverPublishFailures() {
        JobExecution jobExecution = completedJob();
        jobExecution.getExecutionContext().put(GeoFileGenerationContextKeys.STORAGE_READY, false);
        jobExecution.getExecutionContext().put(
                GeoFileGenerationContextKeys.STORAGE_NOT_READY_REASON,
                GeoFileGenerationExitCodes.REASON_BUCKET_MISSING);
        jobExecution.getExecutionContext().put(
                GeoFileGenerationContextKeys.STORAGE_NOT_READY_DETAIL, "dsp-geo-files");

        StepExecution generationStep = new StepExecution(
                GeoFileGenerationJobConfig.GEO_FILE_GENERATION_STEP, jobExecution);
        generationStep.getExecutionContext().putInt(GeoFileGenerationContextKeys.PUBLISH_CONFIG_FAILURES, 5);
        jobExecution.addStepExecutions(List.of(generationStep));

        ExitStatus status = GeoFileGenerationExitStatusResolver.forJob(jobExecution);

        assertEquals(GeoFileGenerationExitCodes.OBJECT_STORAGE_NOT_READY, status.getExitCode());
    }

    @Test
    void forJob_PublishFailuresWhenStorageReady() {
        JobExecution jobExecution = completedJob();
        jobExecution.getExecutionContext().put(GeoFileGenerationContextKeys.STORAGE_READY, true);

        StepExecution generationStep = new StepExecution(
                GeoFileGenerationJobConfig.GEO_FILE_GENERATION_STEP, jobExecution);
        generationStep.getExecutionContext().putInt(GeoFileGenerationContextKeys.FILES_PUBLISHED, 8);
        generationStep.getExecutionContext().putInt(GeoFileGenerationContextKeys.PUBLISH_TRANSIENT_FAILURES, 2);
        generationStep.getExecutionContext().putInt(GeoFileGenerationContextKeys.TERRITORIES_WITH_FAILURES, 1);
        jobExecution.addStepExecutions(List.of(generationStep));

        ExitStatus status = GeoFileGenerationExitStatusResolver.forJob(jobExecution);

        assertEquals(GeoFileGenerationExitCodes.PUBLISH_TRANSIENT_FAILURES, status.getExitCode());
        assertTrue(status.getExitDescription().contains("filesPublished=8"));
    }

    @Test
    void forJob_CompletedWhenStorageSkippedAndNoGenerationStep() {
        JobExecution jobExecution = completedJob();
        jobExecution.getExecutionContext().put(GeoFileGenerationContextKeys.STORAGE_READY, false);
        jobExecution.getExecutionContext().put(
                GeoFileGenerationContextKeys.STORAGE_NOT_READY_REASON,
                GeoFileGenerationExitCodes.REASON_UNREACHABLE);
        jobExecution.getExecutionContext().put(
                GeoFileGenerationContextKeys.STORAGE_NOT_READY_DETAIL, "timeout");

        ExitStatus status = GeoFileGenerationExitStatusResolver.forJob(jobExecution);

        assertEquals(GeoFileGenerationExitCodes.OBJECT_STORAGE_NOT_READY, status.getExitCode());
    }

    @Test
    void forJob_DoesNotOverrideFailedJob() {
        JobExecution jobExecution = completedJob();
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.setExitStatus(ExitStatus.FAILED);

        ExitStatus status = GeoFileGenerationExitStatusResolver.forJob(jobExecution);

        assertEquals(ExitStatus.FAILED.getExitCode(), status.getExitCode());
    }

    private static ExecutionContext generationContext(
            int filesPublished,
            int filesEmptied,
            int territoriesCompleted,
            int territoriesWithFailures,
            int configFailures,
            int transientFailures) {
        ExecutionContext context = new ExecutionContext();
        context.putInt(GeoFileGenerationContextKeys.FILES_PUBLISHED, filesPublished);
        context.putInt(GeoFileGenerationContextKeys.FILES_EMPTIED, filesEmptied);
        context.putInt(GeoFileGenerationContextKeys.TERRITORIES_COMPLETED, territoriesCompleted);
        context.putInt(GeoFileGenerationContextKeys.TERRITORIES_WITH_FAILURES, territoriesWithFailures);
        context.putInt(GeoFileGenerationContextKeys.PUBLISH_CONFIG_FAILURES, configFailures);
        context.putInt(GeoFileGenerationContextKeys.PUBLISH_TRANSIENT_FAILURES, transientFailures);
        return context;
    }

    private static JobExecution completedJob() {
        JobExecution execution = new JobExecution(
                new JobInstance(1L, GeoFileGenerationJobConfig.JOB_NAME),
                1L,
                new JobParameters());
        execution.setStatus(BatchStatus.COMPLETED);
        return execution;
    }
}
