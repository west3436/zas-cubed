package io.github.west3436.zas.reaction;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionResult;
import io.github.west3436.zas.engine.reaction.ReactionSet;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Reactions on a mixture held outside the world (API-019, FIRE-031): the API's mixture converted
 * to the engine's, run through {@link ReactionSet#applyStandalone}, and converted back. Pure apart
 * from logging; any thread.
 */
public final class StandaloneReactions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final GasConfig CONFIG = GasConfig.DEFAULT.withLog(LOGGER::warn);

    private StandaloneReactions() {}

    /**
     * Applies a set of reactions to a mixture for a time step.
     *
     * <p>Gases the set's registry doesn't define take no part, except that they absorb their share
     * of the heat at {@link GasProperties#DEFAULT_MOLAR_HEAT_CAPACITY}. A gas no reaction touched
     * keeps its amount exactly.
     *
     * @param mixture the gas
     * @param seconds the time step, not negative
     * @param ignition whether an ignition source acts on it
     * @param set the reactions
     * @param fire the fire oxygen threshold
     * @return the gas after the step; {@code mixture} itself if nothing reacted
     */
    public static GasMixture apply(
            GasMixture mixture, double seconds, boolean ignition, ReactionSet set, FireSettings fire) {
        if (set.isEmpty() || !(seconds > 0) || mixture.isEmpty()) {
            return mixture;
        }
        GasRegistry registry = set.registry();
        io.github.west3436.zas.engine.gas.GasMixture gas = new io.github.west3436.zas.engine.gas.GasMixture(
                registry, CONFIG, mixture.volume(), mixture.temperature());
        double unknownCapacity = 0;
        for (Map.Entry<ResourceLocation, Double> e : mixture.moles().entrySet()) {
            int i = registry.indexOf(e.getKey().toString());
            if (i < 0) {
                unknownCapacity += e.getValue() * GasProperties.DEFAULT_MOLAR_HEAT_CAPACITY;
            } else {
                gas.setMoles(i, e.getValue());
            }
        }
        ReactionResult r = set.applyStandalone(gas, seconds, ignition, fire);
        if (!r.reacted()) {
            return mixture;
        }
        TreeMap<ResourceLocation, Double> moles = new TreeMap<>(mixture.moles());
        for (int i = 0; i < registry.size(); i++) {
            if (r.moles()[i] == 0) {
                continue;
            }
            ResourceLocation id = ResourceLocation.parse(registry.definition(i).id());
            double n = Math.max(0, moles.getOrDefault(id, 0.0) + r.moles()[i]);
            moles.put(id, n);
        }
        double temperature = gas.temperature();
        if (unknownCapacity > 0) {
            double known = gas.heatCapacity();
            temperature = (known * temperature + unknownCapacity * mixture.temperature()) / (known + unknownCapacity);
        }
        return new GasMixture(moles, temperature, mixture.volume());
    }
}
