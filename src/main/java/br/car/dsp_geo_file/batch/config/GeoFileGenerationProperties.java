package br.car.dsp_geo_file.batch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "geo-file-generation")
public class GeoFileGenerationProperties {

    /**
     * Territories per chunk. One by default, kept small for granular Spring Batch bookkeeping
     * (read/write counts, restart position) and to bound how much work a single chunk retries
     * on failure. Note this is not what protects against re-publishing on crash — that
     * protection comes from {@code requires_s3_file_regeneration}, cleared per territory by
     * {@code TerritoryFileStateRepository.markGenerated} on the {@code target} datasource, which
     * commits independently of this chunk's transaction (a different connection pool than the
     * one backing the chunk's {@code transactionManager}).
     */
    private int chunkSize = 1;

    /** Orphan collection can be turned off while diagnosing the bucket by hand. */
    private boolean orphanCleanupEnabled = true;

    /**
     * Local directory where files are written before S3 upload.
     * Subfolders mirror the object key.
     */
    private String stagingDir = System.getProperty("java.io.tmpdir") + "/dsp-geo-files";
}
