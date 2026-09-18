package br.car.dsp_geo_file.batch.config;

import br.car.dsp_geo_file.batch.tasklet.ObjectStorageReadinessTasklet;
import br.car.dsp_geo_file.batch.tasklet.OrphanObjectCleanupTasklet;
import br.car.dsp_geo_file.batch.writer.TerritoryGeoFileWriter;
import br.car.dsp_geo_file.generation.OrphanObjectCleanupService;
import br.car.dsp_geo_file.storage.ObjectStorageClient;
import br.car.dsp_geo_file.storage.ObjectStorageProperties;
import br.car.dsp_geo_file.territory.Territory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wires the single job of this repository:
 * storage check → (bucket ready?) → generation per pending territory → orphan cleanup.
 */
@Configuration
@EnableConfigurationProperties(GeoFileGenerationProperties.class)
public class GeoFileGenerationJobConfig {

    public static final String JOB_NAME = "geoFileGenerationJob";
    public static final String OBJECT_STORAGE_READINESS_STEP = "objectStorageReadinessStep";
    public static final String GEO_FILE_GENERATION_STEP = "geoFileGenerationStep";

    @Bean
    public Job geoFileGenerationJob(JobRepository jobRepository,
                                    StorageReadyDecider storageReadyDecider,
                                    GeoFileGenerationJobListener geoFileGenerationJobListener,
                                    Step objectStorageReadinessStep,
                                    Step geoFileGenerationStep,
                                    Step orphanObjectCleanupStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(geoFileGenerationJobListener)
                .start(objectStorageReadinessStep)
                .next(storageReadyDecider)
                .on(StorageReadyDecider.PROCESS).to(geoFileGenerationStep)
                .next(orphanObjectCleanupStep)
                .from(storageReadyDecider).on(StorageReadyDecider.SKIP).end()
                .from(storageReadyDecider).on("*").end()
                .end()
                .build();
    }

    @Bean
    public Step objectStorageReadinessStep(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager,
                                           ObjectStorageClient objectStorageClient,
                                           ObjectStorageProperties objectStorageProperties,
                                           ObjectStorageReadinessStepListener objectStorageReadinessStepListener) {
        return new StepBuilder(OBJECT_STORAGE_READINESS_STEP, jobRepository)
                .tasklet(new ObjectStorageReadinessTasklet(
                        objectStorageClient, objectStorageProperties.getBucket()), transactionManager)
                .listener(objectStorageReadinessStepListener)
                .build();
    }

    @Bean
    public Step geoFileGenerationStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      GeoFileGenerationProperties properties,
                                      ItemReader<Territory> pendingTerritoryReader,
                                      TerritoryGeoFileWriter territoryGeoFileWriter) {
        return new StepBuilder(GEO_FILE_GENERATION_STEP, jobRepository)
                .<Territory, Territory>chunk(properties.getChunkSize(), transactionManager)
                .reader(pendingTerritoryReader)
                .writer(territoryGeoFileWriter)
                .listener(territoryGeoFileWriter)
                .build();
    }

    @Bean
    public Step orphanObjectCleanupStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        GeoFileGenerationProperties properties,
                                        OrphanObjectCleanupService cleanupService) {
        return new StepBuilder("orphanObjectCleanupStep", jobRepository)
                .tasklet(new OrphanObjectCleanupTasklet(
                        cleanupService, properties.isOrphanCleanupEnabled()), transactionManager)
                .build();
    }
}
