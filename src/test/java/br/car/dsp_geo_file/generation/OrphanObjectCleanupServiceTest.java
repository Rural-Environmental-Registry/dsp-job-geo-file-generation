package br.car.dsp_geo_file.generation;

import br.car.dsp_geo_file.export.CsvGeoFileExporter;
import br.car.dsp_geo_file.export.GeoFileExporterRegistry;
import br.car.dsp_geo_file.storage.ObjectStorageClient;
import br.car.dsp_geo_file.storage.S3ObjectKeyBuilder;
import br.car.dsp_geo_file.storage.StoredObject;
import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryFileStateRepository;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import br.car.dsp_geo_file.theme.DownloadTerritoryFilterConfig;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import br.car.dsp_geo_file.theme.DownloadThemesService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrphanObjectCleanupServiceTest {

    private final ObjectStorageClient storage = mock(ObjectStorageClient.class);
    private final DownloadThemesService themesService = mock(DownloadThemesService.class);
    private final TerritoryFileStateRepository territoryRepository =
            mock(TerritoryFileStateRepository.class);

    @Test
    void cleanUp_RemovesKeysNoTerritoryProducesAnyMore() {
        givenCatalogue();
        when(territoryRepository.findAll(TerritoryLevel.LEVEL_2)).thenReturn(List.of(
                new Territory(TerritoryLevel.LEVEL_2, "35", "São Paulo", null, null)));
        when(territoryRepository.findAll(TerritoryLevel.LEVEL_3)).thenReturn(List.of());
        when(storage.list("csv/level-2/")).thenReturn(List.of(
                object("csv/level-2/sao-paulo_area_of_interest.csv"),
                object("csv/level-2/sao-paolo_area_of_interest.csv")));
        when(storage.list("csv/level-3/")).thenReturn(List.of());

        int deleted = service().cleanUp();

        assertEquals(1, deleted);
        verify(storage).delete("csv/level-2/sao-paolo_area_of_interest.csv");
        verify(storage, never()).delete("csv/level-2/sao-paulo_area_of_interest.csv");
    }

    @Test
    void cleanUp_KeepsLevel3KeysBuiltWithTheParentSlug() {
        givenCatalogue();
        when(territoryRepository.findAll(TerritoryLevel.LEVEL_2)).thenReturn(List.of());
        when(territoryRepository.findAll(TerritoryLevel.LEVEL_3)).thenReturn(List.of(
                new Territory(TerritoryLevel.LEVEL_3, "3509502", "Campinas", "35", "São Paulo")));
        when(storage.list("csv/level-2/")).thenReturn(List.of());
        when(storage.list("csv/level-3/")).thenReturn(List.of(
                object("csv/level-3/sao-paulo_campinas_area_of_interest.csv")));

        assertEquals(0, service().cleanUp());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void cleanUp_IgnoresTerritoriesThatCannotBeKeyed() {
        givenCatalogue();
        // A level 3 with no parent has no valid key; whitelisting a guess could spare
        // a file that belongs to another territory.
        when(territoryRepository.findAll(TerritoryLevel.LEVEL_2)).thenReturn(List.of());
        when(territoryRepository.findAll(TerritoryLevel.LEVEL_3)).thenReturn(List.of(
                new Territory(TerritoryLevel.LEVEL_3, "3509502", "Campinas", null, null)));
        when(storage.list("csv/level-2/")).thenReturn(List.of());
        when(storage.list("csv/level-3/")).thenReturn(List.of(
                object("csv/level-3/sao-paulo_campinas_area_of_interest.csv")));

        assertEquals(1, service().cleanUp());
        verify(storage).delete("csv/level-3/sao-paulo_campinas_area_of_interest.csv");
    }

    @Test
    void cleanUp_DoesNotTouchPrefixesOfFormatsItDoesNotGenerate() {
        when(themesService.getEnabledThemes()).thenReturn(List.of(theme(List.of("gpkg"))));
        when(territoryRepository.findAll(TerritoryLevel.LEVEL_2)).thenReturn(List.of());
        when(territoryRepository.findAll(TerritoryLevel.LEVEL_3)).thenReturn(List.of());

        assertEquals(0, service().cleanUp());
        verify(storage, never()).list(anyString());
    }

    private void givenCatalogue() {
        when(themesService.getEnabledThemes()).thenReturn(List.of(theme(List.of("csv"))));
    }

    private OrphanObjectCleanupService service() {
        return new OrphanObjectCleanupService(
                themesService,
                new GeoFileExporterRegistry(List.of(new CsvGeoFileExporter())),
                territoryRepository,
                new S3ObjectKeyBuilder(),
                storage);
    }

    private static DownloadThemeConfig theme(List<String> formats) {
        return new DownloadThemeConfig(
                "area_of_interest",
                "Area of interest",
                "dsp:area-of-interest",
                formats,
                true,
                new DownloadTerritoryFilterConfig("direct", "territory_level_3_id", null),
                null);
    }

    private static StoredObject object(String key) {
        return new StoredObject(key, 100L, Instant.now(), Map.of());
    }
}
