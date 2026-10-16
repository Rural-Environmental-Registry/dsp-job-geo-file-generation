package br.car.dsp_geo_file.storage;

/**
 * Storage failure the caller must treat as "file not published" — the territorial flag
 * stays on so the next run retries.
 */
public class ObjectStorageException extends RuntimeException {

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
