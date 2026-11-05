package io.github.west3436.zas.geometry;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.fire.BlockIgnitions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * The current {@link GeometryTable}: derived geometry with datapack overrides applied, blocks with
 * seal providers flagged, zone boundaries marked and heat data added (architecture §4.1, §4.7, §4.8).
 *
 * <p>Lifecycle: overrides and heat data are read on every server data load (start and {@code
 * /reload}). Once tags are bound ({@link TagsUpdatedEvent}, server side), they and the {@link
 * #ZONE_BOUNDARY} tag are resolved to blocks, and the {@linkplain #setIgnitionSources ignition-source
 * predicate} and {@linkplain BlockIgnitions#flammableStates flammability} are asked of every state,
 * on the server thread, and the table is rebuilt on a
 * background thread. That listener runs at {@link EventPriority#LOW}, so a mod that resolves its
 * own tags at normal priority, as the ignition sources do, has its new answers in by then. {@link #table()} waits for that build, and the server-started handler waits
 * for it too, so it is ready before the first atmos tick.
 */
public final class Geometries {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Blocks that pass gas but keep zones apart (GEO-007): by default every door and trapdoor. See
     * {@link #boundaryFlags} for the faces each state keeps zones apart across.
     */
    public static final TagKey<Block> ZONE_BOUNDARY =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "zone_boundary"));

    private static volatile List<GeometryOverride> overrides = List.of();
    private static volatile List<GeometryOverride> testOverrides = List.of();
    private static volatile List<ThermalData> thermal = List.of();
    private static volatile CompletableFuture<GeometryTable> current;
    private static volatile Predicate<BlockState> ignitionSources = state -> false;

    private Geometries() {}

    /** Registers the lifecycle listeners on the game event bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(Geometries::onAddReloadListeners);
        gameBus.addListener(EventPriority.LOW, Geometries::onTagsUpdated);
        gameBus.addListener(Geometries::onServerStarted);
    }

    /**
     * The current table, waiting for a build in progress. Before the first data load this is the
     * table derived from collision shapes alone.
     */
    public static GeometryTable table() {
        CompletableFuture<GeometryTable> f = current;
        return f != null ? f.join() : GeometryDerivation.derived();
    }

    /**
     * The current table without waiting: {@code null} while a rebuild is in progress. For hot paths
     * on the server thread, such as the change hook, that must never block.
     */
    public static GeometryTable tableIfReady() {
        CompletableFuture<GeometryTable> f = current;
        if (f == null) {
            return GeometryDerivation.derived();
        }
        return f.isDone() && !f.isCompletedExceptionally() ? f.join() : null;
    }

    /** The geometry id of {@code state} in the current table. */
    public static int geometryId(BlockState state) {
        return table().geometryId(state);
    }

    /**
     * Sets which block states are standing ignition sources (FIRE-014, FIRE-015): the table flags
     * them {@link Geometry#FLAG_IGNITION_SOURCE}, and the engine's hotspots ignite flammable gas at
     * and next to them each atmos tick. The predicate is asked of every state on the server thread
     * each time the table is rebuilt: on every server data load, once tags are bound. It may read
     * tags. Setting it doesn't rebuild the table; a GameTest calls {@link #rebuildForTest}. Any
     * thread, normally once at mod construction.
     */
    public static void setIgnitionSources(Predicate<BlockState> predicate) {
        ignitionSources = predicate;
    }

    /** The ignition-source predicate set last; none is a source before one is set. Any thread. */
    public static Predicate<BlockState> ignitionSources() {
        return ignitionSources;
    }

    /**
     * Rebuilds the table now from the current overrides, tags and ignition sources, as a data load
     * would: for a GameTest that changed one of them. A dimension whose palette changes gets a new
     * engine at its next batch. Server thread, since tags are read here.
     */
    public static void rebuildForTest() {
        current = CompletableFuture.completedFuture(build(
                resolve(allOverrides()),
                boundaryBlocks(),
                resolveThermal(thermal),
                sourceStates(),
                BlockIgnitions.flammableStates()));
    }

    static void setOverrides(List<GeometryOverride> loaded) {
        overrides = List.copyOf(loaded);
    }

    static void setThermal(List<ThermalData> loaded) {
        thermal = List.copyOf(loaded);
    }

    /**
     * Rebuilds the table now with {@code extra} applied after the datapack overrides, as a {@code
     * /reload} that added them would; an empty list goes back to the datapack overrides alone. For
     * GameTests of a geometry palette change. Server thread, since tags are read here.
     */
    public static void overrideForTest(List<GeometryOverride> extra) {
        testOverrides = List.copyOf(extra);
        rebuildForTest();
    }

    private static List<GeometryOverride> allOverrides() {
        List<GeometryOverride> test = testOverrides;
        if (test.isEmpty()) {
            return overrides;
        }
        List<GeometryOverride> all = new java.util.ArrayList<>(overrides);
        all.addAll(test);
        return all;
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new GeometryOverrideLoader());
        event.addListener(new ThermalDataLoader());
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        Map<Block, GeometryOverride> byBlock = resolve(allOverrides());
        List<Block> boundaries = boundaryBlocks();
        Map<Block, double[]> heat = resolveThermal(thermal);
        int[] sources = sourceStates();
        List<BlockIgnitions.Flammable> flammable = BlockIgnitions.flammableStates();
        current = CompletableFuture.supplyAsync(
                () -> build(byBlock, boundaries, heat, sources, flammable), Util.backgroundExecutor());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        GeometryTable t = table();
        LOGGER.info(
                "Gas geometry ready: {} block states, {} geometries, {} states overridden, {} zone boundaries",
                t.stateCount(),
                t.palette().size(),
                t.overriddenStates(),
                t.boundaryStates());
    }

    /**
     * The ids of the states the ignition-source predicate accepts, ascending. Asked on the thread
     * that bound the tags; a predicate that throws counts as no source, and is logged once.
     */
    static int[] sourceStates() {
        Predicate<BlockState> predicate = ignitionSources;
        List<Integer> ids = new ArrayList<>();
        boolean logged = false;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            try {
                if (predicate.test(state)) {
                    ids.add(Block.getId(state));
                }
            } catch (RuntimeException e) {
                if (!logged) {
                    LOGGER.warn("The ignition-source predicate failed for {}; treating it as no source", state, e);
                    logged = true;
                }
            }
        }
        return ids.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    /** The blocks tagged {@link #ZONE_BOUNDARY}, in registry order. Read on the thread that bound the tags. */
    static List<Block> boundaryBlocks() {
        List<Block> out = new ArrayList<>();
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(ZONE_BOUNDARY)) {
            out.add(holder.value());
        }
        out.sort(Comparator.comparingInt(BuiltInRegistries.BLOCK::getId));
        return out;
    }

    /**
     * The faces a zone-boundary block's state keeps zones apart across (GEO-007), as {@link
     * Geometry#boundaryFlag} bits. For a block that opens and closes, such as a door or trapdoor,
     * these are the faces it seals in one of the two states but not in the other: where its panel
     * lies closed and where it lies open. Its cells join the space on its other sides, and opening
     * or closing it only adds or removes openings across those faces, which never splits a zone
     * (GEO-044). A block that doesn't open, or seals the same faces either way, keeps zones apart
     * across every face, so its cells are zones of their own.
     *
     * @param state the state
     * @param geometry the geometry of any state of the block
     */
    public static int boundaryFlags(BlockState state, Function<BlockState, Geometry> geometry) {
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            Geometry here = geometry.apply(state);
            Geometry toggled = geometry.apply(state.cycle(BlockStateProperties.OPEN));
            int flags = 0;
            for (Face f : Face.values()) {
                if (here.isFaceFull(f) != toggled.isFaceFull(f)) {
                    flags |= Geometry.boundaryFlag(f);
                }
            }
            if (flags != 0) {
                return flags;
            }
        }
        return Geometry.FLAG_ZONE_BOUNDARY;
    }

    /**
     * Maps each block to the override that applies to it. Tags are read here, on the thread that
     * bound them. A block named directly beats one matched through a tag; otherwise later
     * overrides win.
     */
    static Map<Block, GeometryOverride> resolve(List<GeometryOverride> list) {
        Map<Block, GeometryOverride> viaTag = new HashMap<>();
        Map<Block, GeometryOverride> direct = new HashMap<>();
        for (GeometryOverride o : list) {
            for (GeometryOverride.Target target : o.targets()) {
                if (target.tag()) {
                    TagKey<Block> key = TagKey.create(Registries.BLOCK, target.id());
                    for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(key)) {
                        viaTag.put(holder.value(), o);
                    }
                } else {
                    BuiltInRegistries.BLOCK.getOptional(target.id()).ifPresent(block -> direct.put(block, o));
                }
            }
        }
        viaTag.putAll(direct);
        // In registry order, so override geometries get the same ids on every run.
        Map<Block, GeometryOverride> ordered = new LinkedHashMap<>();
        viaTag.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> BuiltInRegistries.BLOCK.getId(e.getKey())))
                .forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered;
    }

    /**
     * Maps each block with heat data to its {@code {transmittance, temperature}}, NaN where none
     * applies (HEAT-002, HEAT-003). Each field is resolved on its own, as {@link #resolve} resolves
     * overrides: a block named directly beats one matched through a tag; otherwise later files
     * win. Tags are read here, on the thread that bound them.
     */
    static Map<Block, double[]> resolveThermal(List<ThermalData> list) {
        Map<Block, Double> uTag = new HashMap<>();
        Map<Block, Double> uDirect = new HashMap<>();
        Map<Block, Double> tTag = new HashMap<>();
        Map<Block, Double> tDirect = new HashMap<>();
        for (ThermalData d : list) {
            for (GeometryOverride.Target target : d.targets()) {
                List<Block> blocks = new ArrayList<>();
                if (target.tag()) {
                    TagKey<Block> key = TagKey.create(Registries.BLOCK, target.id());
                    for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(key)) {
                        blocks.add(holder.value());
                    }
                } else {
                    BuiltInRegistries.BLOCK.getOptional(target.id()).ifPresent(blocks::add);
                }
                for (Block b : blocks) {
                    d.transmittance().ifPresent(u -> (target.tag() ? uTag : uDirect).put(b, u));
                    d.temperature().ifPresent(t -> (target.tag() ? tTag : tDirect).put(b, t));
                }
            }
        }
        uTag.putAll(uDirect);
        tTag.putAll(tDirect);
        Map<Block, double[]> merged = new HashMap<>();
        uTag.forEach((b, u) -> merged.computeIfAbsent(b, k -> new double[] {Double.NaN, Double.NaN})[0] = u);
        tTag.forEach((b, t) -> merged.computeIfAbsent(b, k -> new double[] {Double.NaN, Double.NaN})[1] = t);
        // In registry order, so the geometries get the same ids on every run.
        Map<Block, double[]> ordered = new LinkedHashMap<>();
        merged.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> BuiltInRegistries.BLOCK.getId(e.getKey())))
                .forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered;
    }

    /**
     * The derived table with the overrides applied, then the blocks with seal providers flagged
     * {@link Geometry#FLAG_POSITIONAL}, then the zone boundaries marked: each tagged
     * state's geometry, override or derived, with its {@link #boundaryFlags} added; then each
     * block's heat data added to its states' geometries; then the ignition sources flagged; then the
     * flammable states given their odds and ignition temperatures (FIRE-033, FIRE-034).
     */
    private static GeometryTable build(
            Map<Block, GeometryOverride> byBlock,
            List<Block> boundaries,
            Map<Block, double[]> heat,
            int[] sources,
            List<BlockIgnitions.Flammable> flammable) {
        GeometryTable derived = GeometryDerivation.derived();
        Set<Block> sealProviders = SealProviders.blocks();
        if (byBlock.isEmpty()
                && sealProviders.isEmpty()
                && boundaries.isEmpty()
                && heat.isEmpty()
                && sources.length == 0
                && flammable.isEmpty()) {
            return derived;
        }
        GeometryPalette.Builder palette = derived.palette().toBuilder();
        int[] byStateId = derived.byStateIdCopy();
        Map<GeometryOverride, Integer> ids = new HashMap<>();
        int overridden = 0;
        for (Map.Entry<Block, GeometryOverride> e : byBlock.entrySet()) {
            int id = ids.computeIfAbsent(e.getValue(), o -> palette.intern(o.geometry()));
            for (BlockState state : e.getKey().getStateDefinition().getPossibleStates()) {
                int stateId = Block.getId(state);
                if (stateId >= 0 && stateId < byStateId.length) {
                    byStateId[stateId] = id;
                    overridden++;
                }
            }
        }
        // A block with a seal provider has its geometry asked for per position, unless an override
        // has the last word (API-023, GEO-005). In registry order, so the ids are the same every run.
        List<Block> asked = new ArrayList<>(sealProviders);
        asked.sort(Comparator.comparingInt(BuiltInRegistries.BLOCK::getId));
        for (Block block : asked) {
            if (byBlock.containsKey(block)) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int stateId = Block.getId(state);
                if (stateId >= 0 && stateId < byStateId.length) {
                    byStateId[stateId] =
                            palette.intern(palette.geometry(byStateId[stateId]).withFlags(Geometry.FLAG_POSITIONAL));
                }
            }
        }
        int[] before = byStateId.clone();
        Function<BlockState, Geometry> geometry = s -> palette.geometry(lookup(before, s));
        int marked = 0;
        for (Block block : boundaries) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int stateId = Block.getId(state);
                if (stateId >= 0 && stateId < byStateId.length) {
                    Geometry g = geometry.apply(state);
                    byStateId[stateId] = palette.intern(g.withFlags(boundaryFlags(state, geometry)));
                    marked++;
                }
            }
        }
        for (Map.Entry<Block, double[]> e : heat.entrySet()) {
            double u = e.getValue()[0];
            double t = e.getValue()[1];
            for (BlockState state : e.getKey().getStateDefinition().getPossibleStates()) {
                int stateId = Block.getId(state);
                if (stateId >= 0 && stateId < byStateId.length) {
                    byStateId[stateId] =
                            palette.intern(palette.geometry(byStateId[stateId]).withThermal(u, t));
                }
            }
        }
        for (int stateId : sources) {
            if (stateId >= 0 && stateId < byStateId.length) {
                byStateId[stateId] =
                        palette.intern(palette.geometry(byStateId[stateId]).withFlags(Geometry.FLAG_IGNITION_SOURCE));
            }
        }
        for (BlockIgnitions.Flammable f : flammable) {
            int stateId = f.stateId();
            if (stateId >= 0 && stateId < byStateId.length) {
                byStateId[stateId] =
                        palette.intern(palette.geometry(byStateId[stateId]).withIgnition(f.odds(), f.temperature()));
            }
        }
        return new GeometryTable(palette.build(), byStateId, derived.distinctShapes(), overridden, marked);
    }

    private static int lookup(int[] byStateId, BlockState state) {
        int stateId = Block.getId(state);
        return stateId >= 0 && stateId < byStateId.length ? byStateId[stateId] : GeometryPalette.SOLID_ID;
    }
}
