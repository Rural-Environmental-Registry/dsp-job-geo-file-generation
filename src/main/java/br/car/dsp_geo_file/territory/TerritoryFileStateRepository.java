package br.car.dsp_geo_file.territory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Reads the territories waiting for file generation and clears the flag on {@code dsp-db}.
 */
@Repository
public class TerritoryFileStateRepository {

    private static final String PENDING_CLAUSE = " WHERE t.requires_s3_file_regeneration = TRUE";

    private final JdbcTemplate targetJdbcTemplate;

    public TerritoryFileStateRepository(
            @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate) {
        this.targetJdbcTemplate = targetJdbcTemplate;
    }

    public List<Territory> findPending(TerritoryLevel level) {
        return query(level, true);
    }

    /** Territories with {@code requires_s3_file_regeneration = true} at the given level. */
    public int countPending(TerritoryLevel level) {
        String sql = "SELECT COUNT(*) FROM " + level.table() + " t" + PENDING_CLAUSE;
        Integer count = targetJdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    /** Every territory of the level — the reference the orphan cleanup compares the bucket to. */
    public List<Territory> findAll(TerritoryLevel level) {
        return query(level, false);
    }

    /**
     * Clears the flag and stamps the generation time. Only called when every enabled format
     * of the territory was published, so a partial run stays pending for the next cycle.
     */
    public int markGenerated(TerritoryLevel level, String territoryId) {
        return targetJdbcTemplate.update(
                "UPDATE " + level.table()
                        + " SET requires_s3_file_regeneration = FALSE, last_generated_s3_file_at = NOW()"
                        + " WHERE id = ?",
                territoryId);
    }

    private List<Territory> query(TerritoryLevel level, boolean pendingOnly) {
        String sql = switch (level) {
            case LEVEL_2 -> "SELECT t.id, t.name, NULL AS parent_id, NULL AS parent_name"
                    + " FROM " + TerritoryLevel.LEVEL_2.table() + " t";
            // LEFT JOIN: a level 3 without parent cannot be keyed, and losing it silently
            // would look like the job had nothing to do.
            case LEVEL_3 -> "SELECT t.id, t.name, t.parent_id, p.name AS parent_name"
                    + " FROM " + TerritoryLevel.LEVEL_3.table() + " t"
                    + " LEFT JOIN " + TerritoryLevel.LEVEL_2.table() + " p ON p.id = t.parent_id";
        };
        if (pendingOnly) {
            sql += PENDING_CLAUSE;
        }
        sql += " ORDER BY t.id";
        return targetJdbcTemplate.query(sql, rowMapper(level));
    }

    private static RowMapper<Territory> rowMapper(TerritoryLevel level) {
        return (rs, rowNum) -> new Territory(
                level,
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("parent_id"),
                rs.getString("parent_name")
        );
    }
}
