package br.car.dsp_geo_file.batch.config;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;

/**
 * Maps run outcomes to Spring Batch {@link ExitStatus} codes stored in the batch metadata tables.
 */
public final class GeoFileGenerationExitStatusResolver {

    private GeoFileGenerationExitStatusResolver() {
    }

    public static ExitStatus storageNotReady(String reason, String detail) {
        String message = formatStorageMessage(reason, detail);
        return new ExitStatus(GeoFileGenerationExitCodes.OBJECT_STORAGE_NOT_READY)
                .addExitDescription(message);
    }

    public static ExitStatus fromGenerationStepContext(ExecutionContext stepContext) {
        int filesPublished = stepContext.getInt(GeoFileGenerationContextKeys.FILES_PUBLISHED, 0);
        int filesEmptied = stepContext.getInt(GeoFileGenerationContextKeys.FILES_EMPTIED, 0);
        int territoriesCompleted = stepContext.getInt(GeoFileGenerationContextKeys.TERRITORIES_COMPLETED, 0);
        int territoriesWithFailures = stepContext.getInt(GeoFileGenerationContextKeys.TERRITORIES_WITH_FAILURES, 0);
        int configFailures = stepContext.getInt(GeoFileGenerationContextKeys.PUBLISH_CONFIG_FAILURES, 0);
        int transientFailures = stepContext.getInt(GeoFileGenerationContextKeys.PUBLISH_TRANSIENT_FAILURES, 0);

        String message = formatPublishMessage(
                filesPublished,
                filesEmptied,
                territoriesCompleted,
                territoriesWithFailures,
                configFailures,
                transientFailures);

        if (configFailures == 0 && transientFailures == 0) {
            return ExitStatus.COMPLETED.addExitDescription(message);
        }

        String code;
        if (configFailures > 0 && transientFailures > 0) {
            code = GeoFileGenerationExitCodes.PUBLISH_MIXED_FAILURES;
        } else if (configFailures > 0) {
            code = GeoFileGenerationExitCodes.PUBLISH_CONFIG_ERRORS;
        } else {
            code = GeoFileGenerationExitCodes.PUBLISH_TRANSIENT_FAILURES;
        }
        return new ExitStatus(code).addExitDescription(message);
    }

    public static ExitStatus forJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            return jobExecution.getExitStatus();
        }

        ExecutionContext jobContext = jobExecution.getExecutionContext();
        if (Boolean.FALSE.equals(jobContext.get(GeoFileGenerationContextKeys.STORAGE_READY))) {
            String reason = jobContext.getString(
                    GeoFileGenerationContextKeys.STORAGE_NOT_READY_REASON, "unknown");
            String detail = jobContext.getString(
                    GeoFileGenerationContextKeys.STORAGE_NOT_READY_DETAIL, "");
            return storageNotReady(reason, detail);
        }

        StepExecution generationStep = jobExecution.getStepExecutions().stream()
                .filter(step -> GeoFileGenerationJobConfig.GEO_FILE_GENERATION_STEP.equals(step.getStepName()))
                .findFirst()
                .orElse(null);
        if (generationStep == null) {
            return ExitStatus.COMPLETED;
        }

        return fromGenerationStepContext(generationStep.getExecutionContext());
    }

    public static ExitStatus fromJobContext(ExecutionContext jobContext) {
        if (Boolean.FALSE.equals(jobContext.get(GeoFileGenerationContextKeys.STORAGE_READY))) {
            String reason = jobContext.getString(
                    GeoFileGenerationContextKeys.STORAGE_NOT_READY_REASON, "unknown");
            String detail = jobContext.getString(
                    GeoFileGenerationContextKeys.STORAGE_NOT_READY_DETAIL, "");
            return storageNotReady(reason, detail);
        }
        return ExitStatus.COMPLETED;
    }

    private static String formatPublishMessage(
            int filesPublished,
            int filesEmptied,
            int territoriesCompleted,
            int territoriesWithFailures,
            int configFailures,
            int transientFailures) {
        return String.format(
                "filesPublished=%d filesEmptied=%d territoriesCompleted=%d "
                        + "territoriesWithFailures=%d configFailures=%d transientFailures=%d",
                filesPublished,
                filesEmptied,
                territoriesCompleted,
                territoriesWithFailures,
                configFailures,
                transientFailures);
    }

    private static String formatStorageMessage(String reason, String detail) {
        if (GeoFileGenerationExitCodes.REASON_BUCKET_MISSING.equals(reason)) {
            return "reason=bucket_missing bucket=" + detail;
        }
        if (GeoFileGenerationExitCodes.REASON_UNREACHABLE.equals(reason)) {
            return "reason=unreachable message=" + detail;
        }
        return "reason=" + reason + " detail=" + detail;
    }
}
