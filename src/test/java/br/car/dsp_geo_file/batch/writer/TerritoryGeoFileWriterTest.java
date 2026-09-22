package br.car.dsp_geo_file.batch.writer;

import br.car.dsp_geo_file.batch.config.GeoFileGenerationJobConfig;
import br.car.dsp_geo_file.batch.config.GeoFileGenerationProperties;
import br.car.dsp_geo_file.generation.GeoFileGenerationLogging;
import br.car.dsp_geo_file.generation.GeoFileGenerationOrchestrator;
import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryFileStateRepository;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerritoryGeoFileWriterTest {

    private final GeoFileGenerationOrchestrator orchestrator = mock(GeoFileGenerationOrchestrator.class);
    private final TerritoryFileStateRepository territoryRepository = mock(TerritoryFileStateRepository.class);
    private final GeoFileGenerationProperties properties = new GeoFileGenerationProperties();
    private final GeoFileGenerationLogging generationLogging = new GeoFileGenerationLogging(properties);
    private final TerritoryGeoFileWriter writer = new TerritoryGeoFileWriter(
            orchestrator, territoryRepository, properties, generationLogging);

    @BeforeEach
    void setUpStep() {
        when(territoryRepository.countPending(TerritoryLevel.LEVEL_2)).thenReturn(0);
        when(territoryRepository.countPending(TerritoryLevel.LEVEL_3)).thenReturn(0);
        when(orchestrator.exporterBackedFileCount()).thenReturn(1);
        writer.beforeStep(stepExecution());
    }

    @Test
    void write_MarksGeneratedWhenTheRoundIsComplete() {
        Territory territory = level2("35");
        when(orchestrator.publish(eq(territory), any())).thenReturn(
                new GeoFileGenerationOrchestrator.TerritoryPublishResult(2, 0, 0, 0));

        writer.write(new Chunk<>(territory));

        verify(territoryRepository).markGenerated(TerritoryLevel.LEVEL_2, "35");
    }

    @Test
    void write_LeavesThePendingFlagWhenAnyFormatFailed() {
        Territory territory = level2("35");
        when(orchestrator.publish(eq(territory), any())).thenReturn(
                new GeoFileGenerationOrchestrator.TerritoryPublishResult(1, 0, 0, 1));

        writer.write(new Chunk<>(territory));

        verify(territoryRepository, never()).markGenerated(any(), anyString());
    }

    @Test
    void write_ProcessesEveryItemInTheChunkIndependently() {
        Territory complete = level2("35");
        Territory partial = level2("42");
        when(orchestrator.publish(eq(complete), any())).thenReturn(
                new GeoFileGenerationOrchestrator.TerritoryPublishResult(1, 0, 0, 0));
        when(orchestrator.publish(eq(partial), any())).thenReturn(
                new GeoFileGenerationOrchestrator.TerritoryPublishResult(0, 0, 0, 1));

        writer.write(new Chunk<>(complete, partial));

        verify(territoryRepository).markGenerated(TerritoryLevel.LEVEL_2, "35");
        verify(territoryRepository, never()).markGenerated(eq(TerritoryLevel.LEVEL_2), eq("42"));
    }

    private static Territory level2(String id) {
        return new Territory(TerritoryLevel.LEVEL_2, id, "Territory " + id, null, null);
    }

    private static StepExecution stepExecution() {
        JobExecution jobExecution = new JobExecution(
                new JobInstance(1L, GeoFileGenerationJobConfig.JOB_NAME),
                1L,
                new JobParameters());
        return new StepExecution(GeoFileGenerationJobConfig.GEO_FILE_GENERATION_STEP, jobExecution);
    }
}
