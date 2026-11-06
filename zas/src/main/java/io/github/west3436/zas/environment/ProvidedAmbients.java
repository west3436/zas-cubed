package io.github.west3436.zas.environment;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.gas.ServerGasRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * What each dimension's ambient provider last answered (ENV-004, ENV-015): called once per atmos
 * tick on the server thread, and read by every lookup from any thread until the next.
 *
 * <p>{@link #call} is the only place that runs a provider, and it guards against one that throws:
 * what that provider last answered for the dimension stands (NFR-013). So a dimension's answer is
 * also its provider's last good answer. There's one for each dimension, which every new answer
 * replaces, so a provider that changes its answer each atmos tick adds nothing (ENV-006).
 */
final class ProvidedAmbients {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Each dimension's answer; a dimension with none has no entry. Any thread. */
    private static final Map<ResourceLocation, Answer> ANSWERS = new ConcurrentHashMap<>();

    /** The providers last warned about for each dimension, so a conflict warns once (ENV-020). Server thread. */
    private static final Map<ResourceLocation, List<ResourceLocation>> WARNED_CONFLICTS = new HashMap<>();

    /** Providers already warned about for naming a gas the server doesn't have. Server thread. */
    private static final Set<ResourceLocation> WARNED_GASES = new HashSet<>();

    private ProvidedAmbients() {}

    /** An answer and the id of the provider that gave it, the only provider whose fault it outlasts. */
    private record Answer(ResourceLocation provider, Ambient ambient) {}

    /** A dimension's answer, or null if it has none. Any thread. */
    static Ambient answer(ResourceLocation dimension) {
        Answer answer = ANSWERS.get(dimension);
        return answer == null ? null : answer.ambient();
    }

    /** Takes a level's answer for this atmos tick. Server thread. */
    static void refresh(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        List<AmbientProviders.Registration> providers = AmbientProviders.registered(level.dimension());
        warnOfConflict(dimension, providers);
        Answer answer = providers.isEmpty() ? null : call(providers.get(0), level, ANSWERS.get(dimension));
        if (answer == null) {
            ANSWERS.remove(dimension);
        } else {
            ANSWERS.put(dimension, answer);
        }
    }

    /**
     * Runs a provider. The one place third-party ambient code is called; see the class comment. One
     * that throws is skipped for this atmos tick and keeps its last good answer, so that a vacuum
     * doesn't read as the next source and let gas in (NFR-013). A null it gave is its last good answer
     * too, which leaves no entry, so nothing from before the null comes back.
     *
     * @param last the dimension's answer so far, or null if it has none
     * @return the dimension's answer for this atmos tick, or null if it has none
     */
    private static Answer call(AmbientProviders.Registration provider, ServerLevel level, Answer last) {
        AmbientDefinition answer;
        try {
            answer = provider.provider().ambient(level);
        } catch (Exception | LinkageError e) {
            String source = "Ambient provider " + provider.id();
            if (ThirdPartyFaults.shouldLog(source)) {
                LOGGER.error(
                        "{} threw; skipped for the atmos tick, and its last good answer stands if it has one"
                                + " (logged at most once a minute)",
                        source,
                        e);
            }
            return last != null && last.provider().equals(provider.id()) ? last : null;
        }
        Ambient ambient = checked(provider, answer);
        return ambient == null ? null : new Answer(provider.id(), ambient);
    }

    /** An answer as the simulation's ambient, or null if it defines nothing the server can use. */
    private static Ambient checked(AmbientProviders.Registration provider, AmbientDefinition answer) {
        if (answer == null) {
            return null;
        }
        GasRegistry gases = ServerGasRegistry.current();
        TreeMap<String, Double> pressures = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Double> e : answer.partialPressures().entrySet()) {
            String gas = e.getKey().toString();
            if (gases.indexOf(gas) < 0) {
                if (WARNED_GASES.add(provider.id())) {
                    LOGGER.warn(
                            "Ambient provider {} names gas {}, which no gas definition has; its answer is skipped",
                            provider.id(),
                            gas);
                }
                return null;
            }
            pressures.put(gas, e.getValue());
        }
        return new Ambient(pressures, answer.temperature());
    }

    /** One warning per set of conflicting providers (ENV-020). */
    private static void warnOfConflict(ResourceLocation dimension, List<AmbientProviders.Registration> providers) {
        if (providers.size() < 2) {
            WARNED_CONFLICTS.remove(dimension);
            return;
        }
        List<ResourceLocation> ids =
                providers.stream().map(AmbientProviders.Registration::id).toList();
        if (!ids.equals(WARNED_CONFLICTS.put(dimension, ids))) {
            LOGGER.warn(
                    "{} ambient providers are registered for {}: {}; {} applies",
                    ids.size(),
                    dimension,
                    ids,
                    ids.get(0));
        }
    }

    static void serverStopped() {
        ANSWERS.clear();
        WARNED_CONFLICTS.clear();
        WARNED_GASES.clear();
    }
}
