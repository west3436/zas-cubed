package io.github.west3436.zas.environment;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.PresetOptions;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Each dimension's ambient atmosphere on the running server (ENV-001, ENV-010, ENV-011,
 * ENV-003, ENV-007).
 *
 * <p>A dimension's ambient comes from the first of these that defines it (ENV-014, D-45):
 *
 * <ol>
 *   <li>its datapack definition's variant for the active preset, or the variant the {@code
 *       ambient_variant} preset option overrides it with;
 *   <li>its registered provider's answer (ENV-004, ENV-019), which {@link #takeProviderAnswer} takes
 *       once per atmos tick (ENV-015);
 *   <li>a seeded ambient from another mod's data (SPACE-020), which comes in R2 with the Ad Astra
 *       adapter (D-66), so only a test fills this slot for now ({@link #seedForTest});
 *   <li>{@link Ambient#STANDARD_AIR} (ENV-011).
 * </ol>
 *
 * <p>The atmos scheduler puts {@link #ambient(ResourceKey)} into each dimension's {@code Batch}
 * (architecture §2.3) and skips dimensions where {@link #isExcluded(ResourceKey)} holds.
 *
 * <p>Definitions reload with {@code /reload}. A definition naming a gas the running server
 * doesn't have is logged and skipped. Lookups may be called from any thread; the variant is read
 * from the server config on each call, so a preset change applies to the next lookup, and to the
 * simulation from its next atmos tick, whose batch carries the ambient.
 */
public final class Ambients {

    /** The most recent load, checked for syntax only. */
    private static volatile Map<ResourceLocation, DimensionAmbient> loaded = Map.of();
    /** The definitions in force: the most recent load, checked against the running server's gases. */
    private static volatile Map<ResourceLocation, DimensionAmbient> active = Map.of();

    private static volatile boolean running;

    /** Ambients GameTests put in place of a dimension's own, by dimension. */
    private static final Map<ResourceLocation, Ambient> testOverrides = new ConcurrentHashMap<>();

    /** Definitions GameTests put in place of a dimension's own, by dimension. */
    private static final Map<ResourceLocation, DimensionAmbient> testDefinitions = new ConcurrentHashMap<>();

    /** Ambients seeded from another mod's data, by dimension: ENV-014's third source (SPACE-020). */
    private static final Map<ResourceLocation, Ambient> seeded = new ConcurrentHashMap<>();

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
        // So a provider's dimension doesn't read as the fallback until its first atmos tick.
        gameBus.addListener(
                (ServerStartedEvent e) -> e.getServer().getAllLevels().forEach(Ambients::takeProviderAnswer));
        gameBus.addListener((ServerStoppedEvent e) -> serverStopped());
    }

    /**
     * The ambient of a dimension in its variant that applies: the active preset's, unless {@code
     * ambient_variant} overrides it ({@link PresetOptions#AMBIENT_VARIANT}, OPS-007, OPS-014).
     */
    public static Ambient ambient(ResourceKey<Level> dimension) {
        return ambient(dimension, PresetOptions.AMBIENT_VARIANT.get());
    }

    /** The ambient of a dimension in a preset's variant (ENV-010). */
    public static Ambient ambient(ResourceKey<Level> dimension, AtmospherePreset variant) {
        ResourceLocation id = dimension.location();
        Ambient override = testOverrides.get(id);
        if (override != null) {
            return override;
        }
        DimensionAmbient definition = definitionOf(id);
        if (definition != null) {
            Optional<Ambient> ambient = definition.ambient(variant);
            if (ambient.isPresent()) {
                return ambient.get();
            }
        }
        Ambient provided = ProvidedAmbients.answer(id);
        if (provided != null) {
            return provided;
        }
        Ambient seed = seeded.get(id);
        return seed != null ? seed : Ambient.STANDARD_AIR;
    }

    /**
     * Takes the answer of a level's provider for this atmos tick (ENV-015), which every lookup then
     * reads until the next. The atmos scheduler calls this as it closes a batch, whether or not the
     * dimension simulates, since an excluded one keeps its ambient (ENV-018). Server thread.
     */
    public static void takeProviderAnswer(ServerLevel level) {
        ProvidedAmbients.refresh(level);
    }

    /** The datapack definition of a dimension, if it has a valid one. */
    public static Optional<DimensionAmbient> definition(ResourceKey<Level> dimension) {
        return Optional.ofNullable(definitionOf(dimension.location()));
    }

    private static DimensionAmbient definitionOf(ResourceLocation dimension) {
        DimensionAmbient test = testDefinitions.get(dimension);
        return test != null ? test : active.get(dimension);
    }

    /**
     * Whether a dimension is excluded from simulation, by its datapack definition or by the
     * server config (ENV-007). An excluded dimension keeps its ambient (ENV-018).
     */
    public static boolean isExcluded(ResourceKey<Level> dimension) {
        DimensionAmbient definition = definitionOf(dimension.location());
        return (definition != null && definition.excluded()) || ZasServerConfig.excludes(dimension.location());
    }

    /** Whether a dimension's definition seals its build height and world border (GEO-031). */
    public static boolean sealsWorldLimits(ResourceKey<Level> dimension) {
        DimensionAmbient definition = definitionOf(dimension.location());
        return definition != null && definition.sealedWorldLimits();
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
     * Gives a dimension another datapack definition, as a {@code /reload} that loaded it would, until
     * cleared: for GameTests. The GameTest server has only the vanilla dimensions, and zas ships a
     * definition for each, so a test of what comes after a definition (ENV-014) borrows one with
     * {@link DimensionAmbient#NONE}. Any thread.
     *
     * @param definition the definition, or null to give the dimension back its own
     */
    public static void overrideDefinitionForTest(ResourceKey<Level> dimension, DimensionAmbient definition) {
        if (definition == null) {
            testDefinitions.remove(dimension.location());
        } else {
            testDefinitions.put(dimension.location(), definition);
        }
    }

    /**
     * Fills ENV-014's third source for a dimension until cleared: for GameTests, until the Ad Astra
     * adapter fills it in R2 (SPACE-020, D-66). Any thread.
     *
     * @param ambient the seeded ambient, or null for none
     */
    public static void seedForTest(ResourceKey<Level> dimension, Ambient ambient) {
        if (ambient == null) {
            seeded.remove(dimension.location());
        } else {
            seeded.put(dimension.location(), ambient);
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
        testDefinitions.clear();
        seeded.clear();
        ProvidedAmbients.serverStopped();
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
