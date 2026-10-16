package br.car.dsp_geo_file.generation;

import br.car.dsp_geo_file.batch.config.GeoFileGenerationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Local directory mirroring S3 keys: write to disk, publish, then delete.
 *
 * <p>The local path follows the object-key layout for traceability during the publish step.
 */
@Slf4j
@Service
public class LocalStagingService {

    private final Path stagingRoot;

    public LocalStagingService(GeoFileGenerationProperties properties) {
        this.stagingRoot = Path.of(properties.getStagingDir()).toAbsolutePath().normalize();
    }

    /** Recreates the staging directory at the start of each job run. */
    public void prepareRun() throws IOException {
        if (Files.exists(stagingRoot)) {
            deleteRecursively(stagingRoot);
        }
        Files.createDirectories(stagingRoot);
        log.info("Staging directory ready: {}", stagingRoot);
    }

    /**
     * Resolves the local path for an S3 key.
     * Example: {@code csv/level-2/sp_area.csv} → {@code {stagingRoot}/csv/level-2/sp_area.csv}
     */
    public Path resolvePath(String objectKey) {
        return stagingRoot.resolve(objectKey).normalize();
    }

    public void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.delete(path);
            deleteEmptyParents(path.getParent());
        } catch (IOException ex) {
            log.warn("Could not delete staging file {}: {}", path, ex.getMessage());
        }
    }

    private void deleteEmptyParents(Path directory) throws IOException {
        if (directory == null || !directory.startsWith(stagingRoot) || directory.equals(stagingRoot)) {
            return;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            if (entries.findAny().isEmpty()) {
                Files.delete(directory);
                deleteEmptyParents(directory.getParent());
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to delete " + path, ex);
                }
            });
        }
    }
}
