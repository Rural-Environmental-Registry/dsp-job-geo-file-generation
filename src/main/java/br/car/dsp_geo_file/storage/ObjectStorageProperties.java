package br.car.dsp_geo_file.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection parameters of the object storage exposed through the S3 API.
 * Which product answers on {@code endpoint} is an operational choice, not a code one.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "dsp.object-storage")
public class ObjectStorageProperties {

    private String endpoint;
    private String region = "us-east-1";
    /** Must already exist: the job never creates the bucket. */
    private String bucket;
    private String accessKey;
    private String secretKey;
    /**
     * Path-style addressing ({@code endpoint/bucket/key}). On by default because
     * self-hosted endpoints rarely resolve virtual-host style bucket names.
     */
    private boolean pathStyleAccess = true;

    public void validate() {
        requireNonBlank("endpoint", endpoint);
        requireNonBlank("bucket", bucket);
        requireNonBlank("access-key", accessKey);
        requireNonBlank("secret-key", secretKey);
        requireNonBlank("region", region);
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("dsp.object-storage: '" + field + "' is required");
        }
    }

    /** Explicit override so a future {@code @Data}/log-everything change can't leak the keys. */
    @Override
    public String toString() {
        return "ObjectStorageProperties{endpoint=" + endpoint
                + ", region=" + region
                + ", bucket=" + bucket
                + ", accessKey=****, secretKey=****"
                + ", pathStyleAccess=" + pathStyleAccess + "}";
    }
}
