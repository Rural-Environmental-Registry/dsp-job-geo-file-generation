package br.car.dsp_geo_file.generation;

import br.car.dsp_geo_file.export.FeatureTable;
import br.car.dsp_geo_file.export.FeatureTableResolver;
import br.car.dsp_geo_file.export.GeneratedGeoFile;
import br.car.dsp_geo_file.export.GeoFileExportContext;
import br.car.dsp_geo_file.export.GeoFileExporter;
import br.car.dsp_geo_file.export.GeoFileExporterRegistry;
import br.car.dsp_geo_file.export.TerritoryFeatureFilterBuilder;
import br.car.dsp_geo_file.storage.ObjectStorageClient;
import br.car.dsp_geo_file.storage.ObjectStorageException;
import br.car.dsp_geo_file.storage.S3ObjectKeyBuilder;
import br.car.dsp_geo_file.storage.StoredObject;
import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import br.car.dsp_geo_file.theme.DownloadTerritoryFilterConfig;
import br.car.dsp_geo_file.theme.DownloadThemeConfig;
import br.car.dsp_geo_file.theme.DownloadThemesService;
import br.car.dsp_geo_file.batch.config.GeoFileGenerationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the orchestrator against the {@link ObjectStorageClient} contract, not a provider:
 * whatever answers the S3 API in production must behave exactly like these five operations.
 */
class GeoFileGenerationOrchestratorTest {

    private static final Territory LEVEL_2 =
            new Territory(TerritoryLevel.LEVEL_2, "35", "São Paulo", null, null);

    @TempDir
    Path stagingDir;

    private final ObjectStorageClient storage = mock(ObjectStorageClient.class);
    private final DownloadThemesService themesService = mock(DownloadThemesService.class);
    private final FeatureTableResolver tableResolver = mock(FeatureTableResolver.class);

    @Test
    void publish_WritesOneObjectPerThemeFormatWithGeneratedAtMetadata() {
        Instant before = Instant.now().minusSeconds(1);
        GeneratedGeoFile generated = new GeneratedGeoFile(
                "FID\r\n".getBytes(StandardCharsets.UTF_8),
                3L);

        var result = orchestrator(exporter(generated)).publish(LEVEL_2);

        ArgumentCaptor<Map<String, String>> metadata = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Path> stagingPath = ArgumentCaptor.forClass(Path.class);
        verify(storage).putFile(
                eq("csv/level-2/sao-paulo_area_of_interest.csv"),
                stagingPath.capture(),
                anyString(),
                metadata.capture());
        assertTrue(stagingPath.getValue().toString().contains("sao-paulo_area_of_interest.csv"));
        String generatedAt = metadata.getValue().get(GeoFileGenerationOrchestrator.GENERATED_AT_METADATA);
        assertNotNull(generatedAt);
        Instant parsed = Instant.parse(generatedAt);
        assertFalse(parsed.isBefore(before));
        assertFalse(parsed.isAfter(Instant.now().plusSeconds(1)));
        assertEquals(1, result.published());
        assertTrue(result.complete());
    }

    @Test
    void publish_DeletesTheObjectWhenTheCutBecameEmpty() {
        String key = "csv/level-2/sao-paulo_area_of_interest.csv";
        when(storage.head(key)).thenReturn(
                Optional.of(new StoredObject(key, 10L, Instant.now(), Map.of())));

        var result = orchestrator(exporter(GeneratedGeoFile.empty())).publish(LEVEL_2);

        verify(storage).delete(key);
        verify(storage, never()).putFile(anyString(), any(Path.class), anyString(), any());
        assertEquals(1, result.emptied());
        assertTrue(result.complete());
    }

    @Test
    void publish_DoesNotDeleteWhenTheEmptyCutHasNoObject() {
        when(storage.head(anyString())).thenReturn(Optional.empty());

        orchestrator(exporter(GeneratedGeoFile.empty())).publish(LEVEL_2);

        verify(storage, never()).delete(anyString());
    }

    @Test
    void publish_KeepsTheTerritoryPendingWhenStorageFails() {
        GeneratedGeoFile generated = new GeneratedGeoFile(new byte[]{1}, 1L);
        org.mockito.Mockito.doThrow(new ObjectStorageException("endpoint down", new RuntimeException()))
                .when(storage).putFile(anyString(), any(Path.class), anyString(), any());

        var result = orchestrator(exporter(generated)).publish(LEVEL_2);

        assertEquals(1, result.failed());
        assertEquals(0, result.configFailures());
        assertEquals(1, result.transientFailures());
        assertFalse(result.complete());
        assertFalse(Files.exists(stagingDir.resolve("csv/level-2/sao-paulo_area_of_interest.csv")));
    }

    @Test
    void publish_CountsConfigurationErrorsSeparatelyFromTransientFailures() {
        when(themesService.getEnabledThemes()).thenReturn(List.of(theme(List.of("csv"))));
        when(tableResolver.resolve(any())).thenThrow(new IllegalStateException("table not migrated"));

        var result = new GeoFileGenerationOrchestrator(
                themesService,
                new GeoFileExporterRegistry(List.of(exporter(new GeneratedGeoFile(new byte[]{1}, 1L)))),
                tableResolver,
                new TerritoryFeatureFilterBuilder(),
                new S3ObjectKeyBuilder(),
                stagingService(),
                storage,
                null).publish(LEVEL_2);

        assertEquals(1, result.failed());
        assertEquals(1, result.configFailures());
        assertEquals(0, result.transientFailures());
        assertFalse(result.complete());
        verify(storage, never()).putFile(anyString(), any(Path.class), anyString(), any());
    }

    @Test
    void publish_SkipsFormatsWithoutExporterInsteadOfFailing() {
        when(themesService.getEnabledThemes()).thenReturn(List.of(theme(List.of("gpkg"))));
        GeoFileGenerationOrchestrator orchestrator = new GeoFileGenerationOrchestrator(
                themesService,
                new GeoFileExporterRegistry(List.of(exporter(GeneratedGeoFile.empty()))),
                tableResolver,
                new TerritoryFeatureFilterBuilder(),
                new S3ObjectKeyBuilder(),
                stagingService(),
                storage,
                null);

        var result = orchestrator.publish(LEVEL_2);

        assertEquals(0, result.published());
        assertEquals(0, result.failed());
        assertTrue(result.complete());
        verify(storage, never()).putFile(anyString(), any(Path.class), anyString(), any());
    }

    private LocalStagingService stagingService() {
        GeoFileGenerationProperties properties = new GeoFileGenerationProperties();
        properties.setStagingDir(stagingDir.toString());
        return new LocalStagingService(properties);
    }

    private GeoFileGenerationOrchestrator orchestrator(GeoFileExporter exporter) {
        when(themesService.getEnabledThemes()).thenReturn(List.of(theme(List.of("csv"))));
        when(tableResolver.resolve(any())).thenReturn(new FeatureTable(
                "dsp.area_of_interest",
                "id",
                List.of(new FeatureTable.FeatureColumn("id", "int4"))));
        return new GeoFileGenerationOrchestrator(
                themesService,
                new GeoFileExporterRegistry(List.of(exporter)),
                tableResolver,
                new TerritoryFeatureFilterBuilder(),
                new S3ObjectKeyBuilder(),
                stagingService(),
                storage,
                null);
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

    /** Fixed-answer CSV exporter: the orchestrator's job is the plumbing, not the bytes. */
    private static GeoFileExporter exporter(GeneratedGeoFile answer) {
        return new GeoFileExporter() {
            @Override
            public boolean supports(String format) {
                return "csv".equals(format);
            }

            @Override
            public String contentType() {
                return "text/csv;charset=UTF-8";
            }

            @Override
            public String fileExtension() {
                return "csv";
            }

            @Override
            public GeneratedGeoFile generate(GeoFileExportContext context) {
                return answer;
            }
        };
    }
}
