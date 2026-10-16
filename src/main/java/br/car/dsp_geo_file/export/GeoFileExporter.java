package br.car.dsp_geo_file.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One output format. Adding GPKG means adding an implementation and declaring the format in
 * the theme catalogue — the object key contract and the endpoints do not change.
 */
public interface GeoFileExporter {

    boolean supports(String format);

    /** Value written to {@code Content-Type} on the object. */
    String contentType();

    /** Extension of the object key, without the dot. */
    String fileExtension();

    GeneratedGeoFile generate(GeoFileExportContext context);

    /**
     * Writes the export to disk before S3 upload.
     * Implementations may override to stream straight to the file without an in-memory buffer.
     */
    default GeneratedGeoFile writeToFile(GeoFileExportContext context, Path target) throws IOException {
        GeneratedGeoFile file = generate(context);
        if (file.isEmpty()) {
            return file;
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, file.content());
        return file;
    }
}
