package br.car.dsp_geo_file.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The six S3 operations this product needs. Everything above this interface — key layout,
 * exporters, orphan cleanup — is written against the contract, never against a provider SDK.
 */
public interface ObjectStorageClient {

    /** True when the configured bucket answers; the job never creates it. */
    boolean bucketExists();

    /** Publishes a file already on disk; the caller deletes staging after success. */
    void putFile(String key, Path file, String contentType, Map<String, String> userMetadata);

    Optional<byte[]> get(String key);

    Optional<StoredObject> head(String key);

    List<StoredObject> list(String prefix);

    void delete(String key);
}
