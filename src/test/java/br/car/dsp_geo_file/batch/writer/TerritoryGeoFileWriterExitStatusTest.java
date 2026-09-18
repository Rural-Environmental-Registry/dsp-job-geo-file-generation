package br.car.dsp_geo_file.batch.writer;

import br.car.dsp_geo_file.batch.config.GeoFileGenerationContextKeys;
import br.car.dsp_geo_file.batch.config.GeoFileGenerationExitCodes;
import br.car.dsp_geo_file.batch.config.GeoFileGenerationJobConfig;
import br.car.dsp_geo_file.generation.GeoFileGenerationOrchestrator;
import br.car.dsp_geo_file.territory.TerritoryFileStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TerritoryGeoFileWriterExitStatusTest {

    @Test
    void afterStep_ReturnsPublishConfigErrorsWhenContextHasConfigFailures() {
        TerritoryGeoFileWriter writer = new TerritoryGeoFileWriter(
                mock(GeoFileGenerationOrchestrator.class),
                mock(TerritoryFileStateRepository.class));

        StepExecution stepExecution = stepExecution();
        stepExecution.getExecutionContext().putInt(GeoFileGenerationContextKeys.FILES_PUBLISHED, 2);
        stepExecution.getExecutionContext().putInt(GeoFileGenerationContextKeys.PUBLISH_CONFIG_FAILURES, 1);
        stepExecution.getExecutionContext().putInt(GeoFileGenerationContextKeys.TERRITORIES_WITH_FAILURES, 1);

        ExitStatus status = writer.afterStep(stepExecution);

        assertEquals(GeoFileGenerationExitCodes.PUBLISH_CONFIG_ERRORS, status.getExitCode());
        assertTrue(status.getExitDescription().contains("filesPublished=2"));
    }

    private static StepExecution stepExecution() {
        JobExecution jobExecution = new JobExecution(
                new JobInstance(1L, GeoFileGenerationJobConfig.JOB_NAME),
                1L,
                new JobParameters());
        return new StepExecution(GeoFileGenerationJobConfig.GEO_FILE_GENERATION_STEP, jobExecution);
    }
}
