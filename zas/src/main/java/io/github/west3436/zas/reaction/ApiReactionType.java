package io.github.west3436.zas.reaction;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.reaction.ReactionChange;
import io.github.west3436.zas.api.reaction.ReactionContext;
import io.github.west3436.zas.api.reaction.ReactionType;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.CodeReaction;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * A mod's {@link ReactionType} (API-020), bound to one definition, as the engine's {@link
 * CodeReaction}. It converts the engine's gas arrays to the API's records and back.
 *
 * <p><b>Fault isolation</b> (architecture §3.6, NFR-013): if the type throws, the call counts as no
 * change, and the failure is logged at most once a minute per definition. The engine itself checks
 * what comes back and skips an invalid change.
 */
final class ApiReactionType implements CodeReaction {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Names this reaction in the log, and to {@link ThirdPartyFaults}. */
    private final String source;

    private final ReactionType type;
    /** The gas ids of the last registry seen, by index. */
    private volatile Ids ids = new Ids(GasRegistry.EMPTY, new ResourceLocation[0]);

    private record Ids(GasRegistry registry, ResourceLocation[] byIndex) {}

    ApiReactionType(String definition, ResourceLocation typeId, ReactionType type) {
        this.source = "Reaction " + definition + " of type " + typeId;
        this.type = type;
    }

    @Override
    public Change compute(Input input) {
        ResourceLocation[] byIndex = ids(input.registry());
        Map<ResourceLocation, Double> moles = new TreeMap<>();
        Map<ResourceLocation, Double> available = new TreeMap<>();
        for (int i = 0; i < byIndex.length; i++) {
            if (input.moles()[i] > 0) {
                moles.put(byIndex[i], input.moles()[i]);
            }
            if (input.available()[i] > 0) {
                available.put(byIndex[i], input.available()[i]);
            }
        }
        ReactionChange change;
        try {
            change = type.react(new ReactionContext(
                    new GasMixture(moles, input.temperature(), input.volume()),
                    available,
                    input.seconds(),
                    input.ignition()));
        } catch (Exception | LinkageError e) {
            failed("threw", e);
            return null;
        }
        if (change == null) {
            return null;
        }
        double[] consumed = new double[byIndex.length];
        double[] produced = new double[byIndex.length];
        String unknown = indexed(change.consumed(), input.registry(), consumed);
        if (unknown == null) {
            unknown = indexed(change.produced(), input.registry(), produced);
        }
        if (unknown != null) {
            failed("named gas " + unknown + ", which the server doesn't define", null);
            return null;
        }
        return new Change(consumed, produced, change.heat());
    }

    /** Fills {@code out} by index; returns the first gas the registry lacks, or null. */
    private static String indexed(Map<ResourceLocation, Double> amounts, GasRegistry registry, double[] out) {
        for (Map.Entry<ResourceLocation, Double> e : amounts.entrySet()) {
            int i = registry.indexOf(e.getKey().toString());
            if (i < 0) {
                if (e.getValue() > 0) {
                    return e.getKey().toString();
                }
                continue;
            }
            out[i] = e.getValue();
        }
        return null;
    }

    private ResourceLocation[] ids(GasRegistry registry) {
        Ids cached = ids;
        if (cached.registry() == registry) {
            return cached.byIndex();
        }
        ResourceLocation[] byIndex = new ResourceLocation[registry.size()];
        for (int i = 0; i < byIndex.length; i++) {
            byIndex[i] = ResourceLocation.parse(registry.definition(i).id());
        }
        ids = new Ids(registry, byIndex);
        return byIndex;
    }

    private void failed(String what, Throwable error) {
        if (ThirdPartyFaults.shouldLog(source)) {
            LOGGER.error("{} {}; skipped (logged at most once a minute)", source, what, error);
        }
    }
}
