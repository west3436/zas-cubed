package io.github.west3436.zas.life;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.data.DatapackErrors;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * The gases' hazard profiles on the running server (LIFE-004, LIFE-041), from {@code
 * data/<namespace>/zas/hazard_profile/<path>.json}, each for the gas {@code <namespace>:<path>}. A
 * datapack replaces a shipped profile by providing the same file. A profile for a gas that isn't
 * defined is kept and never applies, so a pack can ship profiles for another mod's gases.
 *
 * <p>Profiles load when the server starts and reload with {@code /reload}; an invalid file is
 * logged, naming the file and field, and skipped (OPS-008). Lookups may be called from any thread.
 */
public final class HazardProfiles {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The directory under {@code data/<namespace>/}. */
    public static final String DIRECTORY = "zas/hazard_profile";

    /** The kind of entry, in {@link DatapackErrors} messages. */
    static final String KIND = "hazard profile";

    /** By gas id, in id order, so evaluating them is deterministic. */
    private static volatile Map<ResourceLocation, HazardProfile> profiles = Map.of();

    private HazardProfiles() {}

    /** Registers the reload listener. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
    }

    /** Every gas's profile, by gas id in id order; unmodifiable. */
    public static Map<ResourceLocation, HazardProfile> all() {
        return profiles;
    }

    /** A gas's profile, if it has one. */
    public static Optional<HazardProfile> of(ResourceLocation gas) {
        return Optional.ofNullable(profiles.get(gas));
    }

    /** For GameTests: puts a profile in place of a gas's own, or with null removes it, until the next reload. */
    public static synchronized void putForTest(ResourceLocation gas, HazardProfile profile) {
        Map<ResourceLocation, HazardProfile> next = new TreeMap<>(profiles);
        if (profile == null) {
            next.remove(gas);
        } else {
            next.put(gas, profile);
        }
        profiles = Collections.unmodifiableMap(new LinkedHashMap<>(next));
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller p) {
            Map<ResourceLocation, HazardProfile> loaded = new LinkedHashMap<>();
            DatapackErrors.beginLoad(KIND);
            // Sorted, so errors are logged and profiles evaluated in the same order every time.
            new TreeMap<>(files).forEach((gas, json) -> {
                DataResult<HazardProfile> parsed = HazardProfile.CODEC.parse(JsonOps.INSTANCE, json);
                parsed.result()
                        .ifPresentOrElse(
                                profile -> loaded.put(gas, profile),
                                () -> DatapackErrors.skip(
                                        KIND,
                                        DIRECTORY,
                                        gas,
                                        parsed.error()
                                                .map(DataResult.Error::message)
                                                .orElse("invalid")));
            });
            LOGGER.info("Loaded {} hazard profiles", loaded.size());
            profiles = Collections.unmodifiableMap(loaded);
        }
    }
}
