package br.car.dsp_geo_file.territory;

/**
 * A territory to publish files for. {@code parentName} is only set on level 3 — the key needs
 * the level 2 slug because homonyms across parents are the rule, not the exception.
 */
public record Territory(
        TerritoryLevel level,
        String id,
        String name,
        String parentId,
        String parentName
) {

    public String slug() {
        return TerritoryNameSlugger.slugify(name);
    }

    public String parentSlug() {
        return TerritoryNameSlugger.slugify(parentName);
    }
}
