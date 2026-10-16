package br.car.dsp_geo_file.export;

import java.util.List;

/**
 * A SQL predicate plus its arguments — the pre-generation equivalent of the CQL filter the
 * backend sends to the WFS.
 */
public record FeatureFilter(String sql, List<Object> args) {

    public Object[] argsArray() {
        return args.toArray();
    }
}
