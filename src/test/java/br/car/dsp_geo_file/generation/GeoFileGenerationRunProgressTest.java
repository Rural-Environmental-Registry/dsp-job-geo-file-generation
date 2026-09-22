package br.car.dsp_geo_file.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoFileGenerationRunProgressTest {

    @Test
    void progress_FormatsTerritoryAndFilePosition() {
        GeoFileGenerationRunProgress progress = new GeoFileGenerationRunProgress(3153);

        progress.startTerritory(10);
        assertEquals("1/3153", progress.territoryProgress());

        progress.advanceFile();
        progress.advanceFile();
        progress.advanceFile();
        assertEquals("3/10", progress.fileProgress());

        progress.startTerritory(10);
        assertEquals("2/3153", progress.territoryProgress());
        assertEquals("0/10", progress.fileProgress());
    }
}
