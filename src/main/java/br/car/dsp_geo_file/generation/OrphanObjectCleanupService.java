package br.car.dsp_geo_file.generation;

import br.car.dsp_geo_file.export.GeoFileExporterRegistry;
import br.car.dsp_geo_file.storage.ObjectStorageClient;
import br.car.dsp_geo_file.storage.S3ObjectKeyBuilder;
import br.car.dsp_geo_file.storage.StoredObject;
import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryFileStateRepository;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import br.car.dsp_geo_file.theme.DownloadThemesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deletes objects that no longer correspond to a territory, theme and format that exist.
 *
 * <p>Works by listing each {@code {format}/{level}/} prefix and removing whatever is not in the
 * set of keys the current catalogue and territory names would produce. Renames are the common
 * case: the file moves to a new slug and the old one would otherwise be served forever.
 */
@Slf4j
@Service
public class OrphanObjectCleanupService {

    private final DownloadThemesService downloadThemesService;
    private final GeoFileExporterRegistry exporterRegistry;
    private final TerritoryFileStateRepository territoryRepository;
    private final S3ObjectKeyBuilder keyBuilder;
    private final ObjectStorageClient objectStorageClient;

    public OrphanObjectCleanupService(
            DownloadThemesService downloadThemesService,
            GeoFileExporterRegistry exporterRegistry,
            TerritoryFileStateRepository territoryRepository,
            S3ObjectKeyBuilder keyBuilder,
            ObjectStorageClient objectStorageClient) {
        this.downloadThemesService = downloadThemesService;
        this.exporterRegistry = exporterRegistry;
        this.territoryRepository = territoryRepository;
        this.keyBuilder = keyBuilder;
        this.objectStorageClient = objectStorageClient;
    }

    public int cleanUp() {
        List<DownloadThemeConfig> themes = downloadThemesService.getEnabledThemes();
        Set<String> validKeys = new LinkedHashSet<>();
        for (TerritoryLevel level : TerritoryLevel.values()) {
            for (Territory territory : territoryRepository.findAll(level)) {
                collectValidKeys(territory, themes, validKeys);
            }
        }

        int deleted = 0;
        for (String format : generatedFormats(themes)) {
            for (TerritoryLevel level : TerritoryLevel.values()) {
                String prefix = keyBuilder.prefix(format, level);
                for (StoredObject object : objectStorageClient.list(prefix)) {
                    if (!validKeys.contains(object.key())) {
                        objectStorageClient.delete(object.key());
                        deleted++;
                    }
                }
            }
        }
        log.info("Orphan cleanup removed {} object(s); {} key(s) considered valid",
                deleted, validKeys.size());
        return deleted;
    }

    private void collectValidKeys(Territory territory,
                                  List<DownloadThemeConfig> themes,
                                  Set<String> validKeys) {
        for (DownloadThemeConfig theme : themes) {
            if (theme.formats() == null) {
                continue;
            }
            for (String rawFormat : theme.formats()) {
                String format = normalize(rawFormat);
                var exporter = exporterRegistry.find(format);
                if (exporter.isEmpty()) {
                    continue;
                }
                try {
                    validKeys.add(keyBuilder.build(
                            format, territory, theme.code(), exporter.get().fileExtension()));
                } catch (IllegalStateException ex) {
                    // Unkeyable territory (blank or parentless name): it has no valid object,
                    // and guessing one here could whitelist someone else's file.
                    log.warn("Skipping territory {} in orphan cleanup: {}",
                            territory.id(), ex.getMessage());
                }
            }
        }
    }

    private Set<String> generatedFormats(List<DownloadThemeConfig> themes) {
        Set<String> formats = new LinkedHashSet<>();
        for (DownloadThemeConfig theme : themes) {
            if (theme.formats() == null) {
                continue;
            }
            for (String rawFormat : theme.formats()) {
                String format = normalize(rawFormat);
                if (exporterRegistry.find(format).isPresent()) {
                    formats.add(format);
                }
            }
        }
        return formats;
    }

    private static String normalize(String format) {
        return format == null ? null : format.trim().toLowerCase(Locale.ROOT);
    }
}
