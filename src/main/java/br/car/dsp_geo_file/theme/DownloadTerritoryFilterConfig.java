package br.car.dsp_geo_file.theme;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * How a theme is narrowed to a territory. {@code direct} filters the level 3 column on the
 * feature itself; {@code aoi_linked} goes through the area of interest.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DownloadTerritoryFilterConfig(
        String strategy,
        String level3Field,
        String aoiLinkField
) {

    public static final String STRATEGY_DIRECT = "direct";
    public static final String STRATEGY_AOI_LINKED = "aoi_linked";
}
