package br.car.dsp_geo_file.territory;

/**
 * The two territorial levels that have pre-generated download files.
 * {@code folder} is the second segment of the object key; the table names are the DSP contract.
 */
public enum TerritoryLevel {

    LEVEL_2("level-2", "dsp.territory_level_2"),
    LEVEL_3("level-3", "dsp.territory_level_3");

    private final String folder;
    private final String table;

    TerritoryLevel(String folder, String table) {
        this.folder = folder;
        this.table = table;
    }

    public String folder() {
        return folder;
    }

    public String table() {
        return table;
    }
}
