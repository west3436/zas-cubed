package io.github.west3436.zas.environment;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientSeeds;
import io.github.west3436.zas.engine.Ambient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * What the registered ambient seeds last answered for each dimension (ENV-014's third source,
 * SPACE-020): asked once per atmos tick on the server thread, right after the dimension's provider,
 * and read by every lookup from any thread until the next.
 *
 * <p>{@link #refresh} is the only place that runs a seed, and it guards against one that throws
 * (NFR-013): the seed is skipped for that atmos tick, and if the dimension's answer so far is that
 * seed's, the answer stands, as a provider's does, so a vacuum doesn't read as the fallback and let
 * gas in.
 */
final class SeededAmbients {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Each dimension's answer; a dimension no seed covers has no entry. Any thread. */
    private static final Map<ResourceLocation, Answer> ANSWERS = new ConcurrentHashMap<>();

    private SeededAmbients() {}

    /** An answer and the id of the seed that gave it, the only seed whose fault it outlasts. */
    private record Answer(ResourceLocation seed, Ambient ambient) {}

    /** A dimension's seeded ambient, or null if no seed covers it. Any thread. */
    static Ambient answer(ResourceLocation dimension) {
        Answer answer = ANSWERS.get(dimension);
        return answer == null ? null : answer.ambient();
    }

    /**
     * Takes a level's answer for this atmos tick: the first seed's, in order of id, that covers the
     * dimension. Server thread. With no seed registered it is one read and one lookup.
     */
    static void refresh(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        Answer last = ANSWERS.get(dimension);
        Answer next = null;
        for (AmbientSeeds.Registration seed : AmbientSeeds.registered()) {
            AmbientDefinition answer;
            try {
                answer = seed.seed().seed(level);
            } catch (Exception | LinkageError e) {
                String source = "Ambient seed " + seed.id();
                if (ThirdPartyFaults.shouldLog(source)) {
                    LOGGER.error(
                            "{} threw; skipped for the atmos tick, and its last answer for the dimension stands"
                                    + " if it gave one (logged at most once a minute)",
                            source,
                            e);
                }
                if (last != null && last.seed().equals(seed.id())) {
                    next = last;
                    break;
                }
                continue;
            }
            Ambient ambient = ProvidedAmbients.checked("Ambient seed", seed.id(), answer);
            if (ambient != null) {
                next = new Answer(seed.id(), ambient);
                break;
            }
        }
        if (next == null) {
            if (last != null) {
                ANSWERS.remove(dimension);
            }
        } else if (!next.equals(last)) {
            ANSWERS.put(dimension, next);
        }
    }

    static void serverStopped() {
        ANSWERS.clear();
    }
}
