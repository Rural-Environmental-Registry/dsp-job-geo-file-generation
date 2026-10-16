package br.car.dsp_geo_file.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the exporter for a format declared in the theme catalogue.
 *
 * <p>A format with no exporter is not an error: the catalogue can announce a format this
 * version does not pre-generate yet, and the backend keeps answering it from the WFS.
 */
@Slf4j
@Component
public class GeoFileExporterRegistry {

    private final List<GeoFileExporter> exporters;

    public GeoFileExporterRegistry(List<GeoFileExporter> exporters) {
        this.exporters = exporters;
        log.info("Registered {} geo file exporter(s)", exporters.size());
    }

    public Optional<GeoFileExporter> find(String format) {
        if (format == null || format.isBlank()) {
            return Optional.empty();
        }
        return exporters.stream()
                .filter(exporter -> exporter.supports(format))
                .findFirst();
    }
}
