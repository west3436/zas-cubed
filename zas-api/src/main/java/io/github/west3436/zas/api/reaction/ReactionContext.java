package io.github.west3436.zas.api.reaction;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.gas.GasMixture;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * What a {@link ReactionType} sees when it reacts (API-020).
 *
 * @param mixture the whole mixture, as it is at the start of the step
 * @param available the moles of each gas the reaction may use: all of the mixture's, or a fire's
 *     parcel of it (FIRE-011); sorted by id
 * @param seconds the step's length; 0 for a reaction that requires ignition, which burns its
 *     parcel at once
 * @param ignition whether an ignition source is present
 */
@AnyThread
public record ReactionContext(
        GasMixture mixture, Map<ResourceLocation, Double> available, double seconds, boolean ignition) {
    /**
     * Copies the arguments.
     *
     * @throws NullPointerException if the mixture or the map is null
     */
    public ReactionContext {
        Objects.requireNonNull(mixture, "mixture");
        available = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(available, "available")));
    }

    /**
     * The moles of a gas the reaction may use.
     *
     * @param gas the gas id
     * @return its moles, or 0
     */
    public double available(ResourceLocation gas) {
        return available.getOrDefault(gas, 0.0);
    }
}
