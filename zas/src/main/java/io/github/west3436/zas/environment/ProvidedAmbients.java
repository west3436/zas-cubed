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
 * <p>{@link #call} is the only place that runs a provider, and it guards against one that throws
 * (NFR-013).
 */
final class ProvidedAmbients {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Each dimension's answer; a dimension with none has no entry. Any thread. */
    private static final Map<ResourceLocation, Ambient> ANSWERS = new ConcurrentHashMap<>();

    /** The providers last warned about for each dimension, so a conflict warns once (ENV-020). Server thread. */
    private static final Map<ResourceLocation, List<ResourceLocation>> WARNED_CONFLICTS = new HashMap<>();

    /** Providers already warned about for naming a gas the server doesn't have. Server thread. */
    private static final Set<ResourceLocation> WARNED_GASES = new HashSet<>();

    private ProvidedAmbients() {}

    /** A dimension's answer, or null if it has none. Any thread. */
    static Ambient answer(ResourceLocation dimension) {
        return ANSWERS.get(dimension);
    }

    /** Takes a level's answer for this atmos tick. Server thread. */
    static void refresh(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        List<AmbientProviders.Registration> providers = AmbientProviders.registered(level.dimension());
        warnOfConflict(dimension, providers);
        Ambient answer = providers.isEmpty() ? null : checked(providers.get(0), call(providers.get(0), level));
        if (answer == null) {
            ANSWERS.remove(dimension);
        } else {
            ANSWERS.put(dimension, answer);
        }
    }

    /**
     * Runs a provider. The one place third-party ambient code is called; see the class comment. One
     * that throws is skipped for this atmos tick, as if it had answered null (NFR-013).
     */
    private static AmbientDefinition call(AmbientProviders.Registration provider, ServerLevel level) {
        try {
            return provider.provider().ambient(level);
        } catch (Exception | LinkageError e) {
            String source = "Ambient provider " + provider.id();
            if (ThirdPartyFaults.shouldLog(source)) {
                LOGGER.error("{} threw; skipped for the atmos tick (logged at most once a minute)", source, e);
            }
            return null;
        }
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
