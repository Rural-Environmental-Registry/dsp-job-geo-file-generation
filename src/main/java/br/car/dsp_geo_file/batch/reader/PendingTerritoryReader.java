package br.car.dsp_geo_file.batch.reader;

import br.car.dsp_geo_file.territory.Territory;
import br.car.dsp_geo_file.territory.TerritoryFileStateRepository;
import br.car.dsp_geo_file.territory.TerritoryLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reads the territories with {@code requires_s3_file_regeneration = true}, level 2 first.
 *
 * <p>Loads on the first read rather than at startup, so the object storage check runs before
 * the database is queried and a missing bucket costs nothing.
 *
 * <p>This bean is a singleton with mutable state ({@code pending}), which is only safe because
 * {@code DspGeoFileGenerationApplication} runs the job exactly once and calls
 * {@code System.exit} right after — one process, one execution. If this job ever becomes
 * long-running (rescheduling executions without restarting the JVM), this reader must become
 * {@code @StepScope} or reset {@code pending} explicitly between executions, or the second run
 * will silently read zero territories (the exhausted iterator from the first run).
 */
@Slf4j
@Component
public class PendingTerritoryReader implements ItemReader<Territory> {

    private final TerritoryFileStateRepository territoryRepository;

    private Iterator<Territory> pending;

    public PendingTerritoryReader(TerritoryFileStateRepository territoryRepository) {
        this.territoryRepository = territoryRepository;
    }

    @Override
    public Territory read() {
        if (pending == null) {
            pending = loadPending().iterator();
        }
        return pending.hasNext() ? pending.next() : null;
    }

    private List<Territory> loadPending() {
        List<Territory> territories = new ArrayList<>();
        for (TerritoryLevel level : TerritoryLevel.values()) {
            List<Territory> ofLevel = territoryRepository.findPending(level);
            log.info("{} territory(ies) pending file generation on {}", ofLevel.size(), level.table());
            territories.addAll(ofLevel);
        }
        return territories;
    }
}
