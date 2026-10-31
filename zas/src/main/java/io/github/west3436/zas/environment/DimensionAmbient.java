package io.github.west3436.zas.environment;

import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.engine.Ambient;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A dimension's datapack ambient definition (ENV-001): the ambient for each preset (ENV-010)
 * and whether the dimension is excluded from simulation (ENV-007).
 *
 * <p>A preset with no ambient here falls through to the next source, which in R0 is the
 * fallback (ENV-011, ENV-014).
 *
 * @param normal the ambient under the Normal preset, if defined
 * @param hardcore the ambient under the Hardcore preset, if defined
 * @param excluded whether the dimension is excluded from simulation
 */
public record DimensionAmbient(Optional<Ambient> normal, Optional<Ambient> hardcore, boolean excluded) {
    public DimensionAmbient {
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(hardcore, "hardcore");
    }

    /** The ambient defined for a preset, if any. */
    public Optional<Ambient> ambient(AtmospherePreset preset) {
        return switch (preset) {
            case NORMAL -> normal;
            case HARDCORE -> hardcore;
        };
    }

    /** Every gas id the variants use, sorted. */
    Set<String> gases() {
        Set<String> ids = new TreeSet<>();
        normal.ifPresent(a -> ids.addAll(a.partialPressures().keySet()));
        hardcore.ifPresent(a -> ids.addAll(a.partialPressures().keySet()));
        return ids;
    }
}
