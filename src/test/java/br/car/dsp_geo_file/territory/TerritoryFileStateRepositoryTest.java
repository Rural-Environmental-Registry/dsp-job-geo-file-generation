package br.car.dsp_geo_file.territory;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerritoryFileStateRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TerritoryFileStateRepository repository = new TerritoryFileStateRepository(jdbcTemplate);

    @Test
    void countPending_UsesPendingClauseOnLevel2Table() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM dsp.territory_level_2 t WHERE t.requires_s3_file_regeneration = TRUE"),
                eq(Integer.class)))
                .thenReturn(42);

        assertEquals(42, repository.countPending(TerritoryLevel.LEVEL_2));
    }

    @Test
    void countPending_UsesPendingClauseOnLevel3Table() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM dsp.territory_level_3 t WHERE t.requires_s3_file_regeneration = TRUE"),
                eq(Integer.class)))
                .thenReturn(7);

        assertEquals(7, repository.countPending(TerritoryLevel.LEVEL_3));
    }
}
