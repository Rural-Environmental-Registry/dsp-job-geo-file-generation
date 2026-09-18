package br.car.dsp_geo_file.generation;

import br.car.dsp_geo_file.batch.config.GeoFileGenerationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStagingServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvePath_MirrorsObjectKeyUnderStagingRoot() {
        LocalStagingService service = service(tempDir);

        Path path = service.resolvePath("csv/level-2/sao-paulo_area.csv");

        assertEquals(tempDir.resolve("csv/level-2/sao-paulo_area.csv").normalize(), path);
    }

    @Test
    void prepareRun_RecreatesStagingDirectory() throws Exception {
        LocalStagingService service = service(tempDir);
        Path leftover = tempDir.resolve("old-run.csv");
        Files.writeString(leftover, "stale");

        service.prepareRun();

        assertTrue(Files.isDirectory(tempDir));
        assertFalse(Files.exists(leftover));
    }

    @Test
    void deleteQuietly_RemovesFileAndEmptyParents() throws Exception {
        LocalStagingService service = service(tempDir);
        service.prepareRun();

        Path file = service.resolvePath("csv/level-2/sao-paulo_area.csv");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");

        service.deleteQuietly(file);

        assertFalse(Files.exists(file));
        assertFalse(Files.exists(tempDir.resolve("csv/level-2")));
    }

    private static LocalStagingService service(Path stagingDir) {
        GeoFileGenerationProperties properties = new GeoFileGenerationProperties();
        properties.setStagingDir(stagingDir.toString());
        return new LocalStagingService(properties);
    }
}
