package br.car.dsp_geo_file.export;

/**
 * The result of one export. {@code featureCount} zero means the cut is empty and no object
 * should exist for it.
 */
public record GeneratedGeoFile(
        byte[] content,
        long featureCount
) {

    public static GeneratedGeoFile empty() {
        return new GeneratedGeoFile(new byte[0], 0L);
    }

    public boolean isEmpty() {
        return featureCount == 0L;
    }
}
