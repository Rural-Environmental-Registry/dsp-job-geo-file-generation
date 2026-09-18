package br.car.dsp_geo_file.batch.writer;

import br.car.dsp_geo_file.generation.GeoFileGenerationOrchestrator;
import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryFileStateRepository;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import org.junit.jupiter.api.Test;
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
    private final TerritoryGeoFileWriter writer = new TerritoryGeoFileWriter(orchestrator, territoryRepository);

    @Test
    void write_MarksGeneratedWhenTheRoundIsComplete() {
        Territory territory = level2("35");
        when(orchestrator.publish(territory)).thenReturn(
                new GeoFileGenerationOrchestrator.TerritoryPublishResult(2, 0, 0, 0));

        writer.write(new Chunk<>(territory));

        verify(territoryRepository).markGenerated(TerritoryLevel.LEVEL_2, "35");
    }

    @Test
    void write_LeavesThePendingFlagWhenAnyFormatFailed() {
        Territory territory = level2("35");
        when(orchestrator.publish(territory)).thenReturn(
                new GeoFileGenerationOrchestrator.TerritoryPublishResult(1, 0, 0, 1));

        writer.write(new Chunk<>(territory));

        verify(territoryRepository, never()).markGenerated(any(), anyString());
    }

    @Test
    void write_ProcessesEveryItemInTheChunkIndependently() {
        Territory complete = level2("35");
        Territory partial = level2("42");
        when(orchestrator.publish(complete)).thenReturn(
                new GeoFileGenerationOrchestrator.TerritoryPublishResult(1, 0, 0, 0));
        when(orchestrator.publish(partial)).thenReturn(
                new GeoFileGenerationOrchestrator.TerritoryPublishResult(0, 0, 0, 1));

        writer.write(new Chunk<>(complete, partial));

        verify(territoryRepository).markGenerated(TerritoryLevel.LEVEL_2, "35");
        verify(territoryRepository, never()).markGenerated(eq(TerritoryLevel.LEVEL_2), eq("42"));
    }

    private static Territory level2(String id) {
        return new Territory(TerritoryLevel.LEVEL_2, id, "Territory " + id, null, null);
    }
}
