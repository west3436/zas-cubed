package io.github.west3436.zas.geometry;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * The current {@link GeometryTable}: derived geometry with datapack overrides applied, and zone
 * boundaries marked (architecture §4.1, §4.7).
 *
 * <p>Lifecycle: overrides are read on every server data load (start and {@code /reload}). Once
 * tags are bound ({@link TagsUpdatedEvent}, server side), the overrides and the {@link
 * #ZONE_BOUNDARY} tag are resolved to blocks on the server thread and the table is rebuilt on a
 * background thread. {@link #table()} waits for that build, and the server-started handler waits
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
    private static volatile CompletableFuture<GeometryTable> current;

    private Geometries() {}

    /** Registers the lifecycle listeners on the game event bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(Geometries::onAddReloadListeners);
        gameBus.addListener(Geometries::onTagsUpdated);
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

    static void setOverrides(List<GeometryOverride> loaded) {
        overrides = List.copyOf(loaded);
    }

    /**
     * Rebuilds the table now with {@code extra} applied after the datapack overrides, as a {@code
     * /reload} that added them would; an empty list goes back to the datapack overrides alone. For
     * GameTests of a geometry palette change. Server thread, since tags are read here.
     */
    public static void overrideForTest(List<GeometryOverride> extra) {
        testOverrides = List.copyOf(extra);
        current = CompletableFuture.completedFuture(build(resolve(allOverrides()), boundaryBlocks()));
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
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        Map<Block, GeometryOverride> byBlock = resolve(allOverrides());
        List<Block> boundaries = boundaryBlocks();
        current = CompletableFuture.supplyAsync(() -> build(byBlock, boundaries), Util.backgroundExecutor());
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
     * The derived table with the overrides applied, then the zone boundaries marked: each tagged
     * state's geometry, override or derived, with its {@link #boundaryFlags} added.
     */
    private static GeometryTable build(Map<Block, GeometryOverride> byBlock, List<Block> boundaries) {
        GeometryTable derived = GeometryDerivation.derived();
        if (byBlock.isEmpty() && boundaries.isEmpty()) {
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
        return new GeometryTable(palette.build(), byStateId, derived.distinctShapes(), overridden, marked);
    }

    private static int lookup(int[] byStateId, BlockState state) {
        int stateId = Block.getId(state);
        return stateId >= 0 && stateId < byStateId.length ? byStateId[stateId] : GeometryPalette.SOLID_ID;
    }
}
