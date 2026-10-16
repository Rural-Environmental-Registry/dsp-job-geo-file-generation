package br.car.dsp_geo_file.batch.config;

/**
 * Exit codes persisted in {@code BATCH_JOB_EXECUTION.EXIT_CODE} and
 * {@code BATCH_STEP_EXECUTION.EXIT_CODE}.
 */
public final class GeoFileGenerationExitCodes {

    public static final String COMPLETED = "COMPLETED";

    public static final String OBJECT_STORAGE_NOT_READY = "OBJECT_STORAGE_NOT_READY";
    public static final String PUBLISH_CONFIG_ERRORS = "PUBLISH_CONFIG_ERRORS";
    public static final String PUBLISH_TRANSIENT_FAILURES = "PUBLISH_TRANSIENT_FAILURES";
    public static final String PUBLISH_MIXED_FAILURES = "PUBLISH_MIXED_FAILURES";

    public static final String REASON_BUCKET_MISSING = "bucket_missing";
    public static final String REASON_UNREACHABLE = "unreachable";

    private GeoFileGenerationExitCodes() {
    }
}
