package br.car.dsp_geo_file.generation;

/**
 * In-memory progress for one {@code geoFileGenerationStep} execution (not persisted in Batch).
 */
public class GeoFileGenerationRunProgress {

    private final int totalTerritories;
    private int territoryIndex;
    private int filesInTerritory;
    private int fileIndex;

    public GeoFileGenerationRunProgress(int totalTerritories) {
        this.totalTerritories = Math.max(totalTerritories, 0);
    }

    /** Called once per territory before {@link GeoFileGenerationOrchestrator#publish}. */
    public void startTerritory(int exporterBackedFileCount) {
        territoryIndex++;
        filesInTerritory = Math.max(exporterBackedFileCount, 0);
        fileIndex = 0;
    }

    /** Called before logging each (theme, format) pair handled by an exporter. */
    public void advanceFile() {
        fileIndex++;
    }

    public String territoryProgress() {
        if (totalTerritories == 0) {
            return territoryIndex + "/0";
        }
        return territoryIndex + "/" + totalTerritories;
    }

    public String fileProgress() {
        if (filesInTerritory == 0) {
            return fileIndex + "/0";
        }
        return fileIndex + "/" + filesInTerritory;
    }

    public int territoryIndex() {
        return territoryIndex;
    }
}
