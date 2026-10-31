package io.github.west3436.zas.environment;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.gas.ServerGasRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Each dimension's ambient atmosphere on the running server (ENV-001, ENV-010, ENV-011,
 * ENV-003, ENV-007).
 *
 * <p>A dimension's ambient is its datapack definition's variant for the active preset, or
 * {@link Ambient#STANDARD_AIR} if it has none (ENV-011). Providers (ENV-004) and seeded
 * ambients (SPACE-020) slot in between in R1 (ENV-014). The atmos scheduler puts {@link
 * #ambient(ResourceKey)} into each dimension's {@code Batch} (architecture §2.3) and skips
 * dimensions where {@link #isExcluded(ResourceKey)} holds.
 *
 * <p>Definitions reload with {@code /reload}. A definition naming a gas the running server
 * doesn't have is logged and skipped. Lookups may be called from any thread; the preset is read
 * from {@link ZasServerConfig} on each call, so a preset change applies to the next lookup.
 */
public final class Ambients {

    /** The most recent load, checked for syntax only. */
    private static volatile Map<ResourceLocation, DimensionAmbient> loaded = Map.of();
    /** The definitions in force: the most recent load, checked against the running server's gases. */
    private static volatile Map<ResourceLocation, DimensionAmbient> active = Map.of();

    private static volatile boolean running;

    /** Ambients GameTests put in place of a dimension's own, by dimension. */
    private static final Map<ResourceLocation, Ambient> testOverrides = new ConcurrentHashMap<>();

    private Ambients() {}

    /**
     * Registers the server config (which holds the preset and excluded dimensions) and the
     * ambient lifecycle listeners. Called once, from the mod constructor.
     *
     * @param gameBus NeoForge's game event bus
     */
    public static void register(IEventBus gameBus) {
        ZasServerConfig.register(ModLoadingContext.get().getActiveContainer());
        gameBus.addListener((AddReloadListenerEvent e) -> e.addListener(new AmbientLoader()));
        // After the gas registry fixes the server's gases at normal priority.
        gameBus.addListener(EventPriority.LOW, (ServerAboutToStartEvent e) -> serverStarting());
        gameBus.addListener((ServerStoppedEvent e) -> serverStopped());
    }

    /** The ambient of a dimension under the active preset. */
    public static Ambient ambient(ResourceKey<Level> dimension) {
        return ambient(dimension, ZasServerConfig.preset());
    }

    /** The ambient of a dimension under a preset. */
    public static Ambient ambient(ResourceKey<Level> dimension, AtmospherePreset preset) {
        Ambient override = testOverrides.get(dimension.location());
        if (override != null) {
            return override;
        }
        DimensionAmbient definition = active.get(dimension.location());
        return definition == null
                ? Ambient.STANDARD_AIR
                : definition.ambient(preset).orElse(Ambient.STANDARD_AIR);
    }

    /** The datapack definition of a dimension, if it has a valid one. */
    public static Optional<DimensionAmbient> definition(ResourceKey<Level> dimension) {
        return Optional.ofNullable(active.get(dimension.location()));
    }

    /**
     * Whether a dimension is excluded from simulation, by its datapack definition or by the
     * server config (ENV-007). An excluded dimension keeps its ambient (ENV-018).
     */
    public static boolean isExcluded(ResourceKey<Level> dimension) {
        DimensionAmbient definition = active.get(dimension.location());
        return (definition != null && definition.excluded()) || ZasServerConfig.excludes(dimension.location());
    }

    /**
     * Gives a dimension another ambient, under every preset, until cleared: for GameTests. The
     * GameTest server has only the vanilla dimensions, so a test that needs one with another
     * ambient, such as a vacuum, borrows a vanilla one and gives it a test datapack's ambient. The
     * simulation picks it up with its next batch. Any thread.
     *
     * @param ambient the ambient, or null to give the dimension back its own
     */
    public static void overrideForTest(ResourceKey<Level> dimension, Ambient ambient) {
        if (ambient == null) {
            testOverrides.remove(dimension.location());
        } else {
            testOverrides.put(dimension.location(), ambient);
        }
    }

    /**
     * One full air cell (1,000 L) of an ambient, as the API reports open cells.
     *
     * @throws IllegalArgumentException if a gas id isn't a valid resource location
     */
    public static GasMixture cell(Ambient ambient) {
        Map<ResourceLocation, Double> moles = new HashMap<>();
        for (String gas : ambient.partialPressures().keySet()) {
            moles.put(ResourceLocation.parse(gas), ambient.moles(gas, StandardAir.CELL_VOLUME));
        }
        return new GasMixture(moles, ambient.temperature(), StandardAir.CELL_VOLUME);
    }

    static void loaded(Map<ResourceLocation, DimensionAmbient> definitions) {
        loaded = Map.copyOf(definitions);
        if (running) {
            activate();
        }
    }

    private static void serverStarting() {
        running = true;
        activate();
    }

    private static void serverStopped() {
        running = false;
        active = Map.of();
        testOverrides.clear();
    }

    /** Checks the loaded definitions against the running server's gases and puts them in force. */
    private static void activate() {
        GasRegistry gases = ServerGasRegistry.current();
        Map<ResourceLocation, DimensionAmbient> checked = new HashMap<>();
        loaded.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            List<String> missing = e.getValue().gases().stream()
                    .filter(id -> gases.indexOf(id) < 0)
                    .toList();
            if (missing.isEmpty()) {
                checked.put(e.getKey(), e.getValue());
            } else {
                DatapackErrors.skip(
                        AmbientLoader.KIND,
                        AmbientFormat.DIRECTORY,
                        e.getKey(),
                        "gas ids " + missing + ": no gas definition has these ids");
            }
        });
        active = Map.copyOf(checked);
    }
}
