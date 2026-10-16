package br.car.dsp_geo_file.storage;

import java.time.Instant;
import java.util.Map;

/**
 * An object as the storage reports it. {@code userMetadata} is empty on listings —
 * only {@code head} reads it back.
 */
public record StoredObject(
        String key,
        long size,
        Instant lastModified,
        Map<String, String> userMetadata
) {
}
