package br.car.dsp_geo_file.generation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoFileGenerationLoggingTest {

    @Test
    void formatBreakdown_SortsKeysInInsertionOrder() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("csv", 2);
        counts.put("gpkg", 5);

        assertEquals("{csv:2,gpkg:5}", GeoFileGenerationLogging.formatBreakdown(counts));
    }

    @Test
    void formatBreakdown_EmptyMap() {
        assertEquals("{}", GeoFileGenerationLogging.formatBreakdown(Map.of()));
    }
}
