package io.github.west3436.zas.geometry;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.engine.GeometryPalette;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * The current {@link GeometryTable}: derived geometry with datapack overrides applied
 * (architecture §4.1).
 *
 * <p>Lifecycle: overrides are read on every server data load (start and {@code /reload}). Once
 * tags are bound ({@link TagsUpdatedEvent}, server side), the overrides are resolved to blocks on
 * the server thread and the table is rebuilt on a background thread. {@link #table()} waits for
 * that build, and the server-started handler waits for it too, so it is ready before the first
 * atmos tick.
 */
public final class Geometries {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile List<GeometryOverride> overrides = List.of();
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

    /** The geometry id of {@code state} in the current table. */
    public static int geometryId(BlockState state) {
        return table().geometryId(state);
    }

    static void setOverrides(List<GeometryOverride> loaded) {
        overrides = List.copyOf(loaded);
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new GeometryOverrideLoader());
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        Map<Block, GeometryOverride> byBlock = resolve(overrides);
        current = CompletableFuture.supplyAsync(() -> build(byBlock), Util.backgroundExecutor());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        GeometryTable t = table();
        LOGGER.info(
                "Gas geometry ready: {} block states, {} geometries, {} states overridden",
                t.stateCount(),
                t.palette().size(),
                t.overriddenStates());
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

    private static GeometryTable build(Map<Block, GeometryOverride> byBlock) {
        GeometryTable derived = GeometryDerivation.derived();
        if (byBlock.isEmpty()) {
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
        return new GeometryTable(palette.build(), byStateId, derived.distinctShapes(), overridden);
    }
}
