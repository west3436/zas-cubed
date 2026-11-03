package io.github.west3436.zas.ignition;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.geometry.GeometryOverride.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The ignition sources from datapacks (FIRE-014, FIRE-015), resolved to blocks, items and entity
 * types.
 *
 * <p>Files are read on every server data load ({@code data/<namespace>/zas/ignition_source/}). A
 * pack replaces a shipped file by shipping one with the same id, which is how it turns a source off
 * ({@code "enabled": false}) or on, such as the redstone spark, shipped off. An invalid file is
 * logged, naming the file and field, and skipped (OPS-008).
 *
 * <p>Block sources are the states the geometry table flags as ignition sources: it is handed {@link
 * #isStanding} once, and asks it of every state whenever it is rebuilt, after each data load once
 * tags are bound, so the engine finds standing sources itself. The one-off sources are resolved
 * when tags are bound ({@link TagsUpdatedEvent}, server side): sparks into a table the change hook
 * reads; items, entity types and explosions for {@link IgnitionEvents}. Reads are safe from any
 * thread.
 */
public final class IgnitionSources {
    /** The kind of entry, in {@link DatapackErrors} messages. */
    static final String KIND = "ignition source";

    /**
     * Blocks that are standing ignition sources, for mods (API-053): the shipped source {@code
     * zas:tagged} names this tag, so a block in it with a boolean {@code lit} property ignites while
     * lit, and one without always does.
     */
    public static final TagKey<Block> TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("zas", "ignition_sources"));

    /**
     * The resolved one-off sources: the source id of each block state that is a spark, by state id
     * (null where none); the item and entity type sources; the explosion source.
     */
    private record Resolved(
            ResourceLocation[] spark,
            boolean anySpark,
            Map<Item, ResourceLocation> items,
            Map<EntityType<?>, ResourceLocation> entities,
            @Nullable ResourceLocation explosion) {
        static final Resolved NONE = new Resolved(new ResourceLocation[0], false, Map.of(), Map.of(), null);
    }

    private static volatile Map<ResourceLocation, IgnitionSource> loaded = Map.of();
    private static volatile Map<ResourceLocation, IgnitionSource> testFiles = Map.of();
    private static volatile Resolved resolved = Resolved.NONE;

    private IgnitionSources() {}

    /** Registers the loader and the tag listener on the game event bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
        gameBus.addListener(IgnitionSources::onTagsUpdated);
        EngineLink.setStandingSources(IgnitionSources::isStanding);
    }

    // ---- lookups (any thread) ----

    /**
     * Whether {@code state} is a standing source: one that ignites while it is there. The geometry
     * table asks this of every state when it is built, after each data load once tags are bound;
     * it reads the sources the last load swapped in, and the tags as they are bound now, so it
     * doesn't depend on which listener runs first.
     */
    public static boolean isStanding(BlockState state) {
        return standingSource(state) != null;
    }

    /** The standing source {@code state} is, or null: the last, in file id order, that names it. */
    public static @Nullable ResourceLocation standingSource(BlockState state) {
        ResourceLocation found = null;
        for (Map.Entry<ResourceLocation, IgnitionSource> e : all().entrySet()) {
            if (e.getValue().is(IgnitionSource.Type.BLOCK) && matches(e.getValue(), state, true)) {
                found = e.getKey();
            }
        }
        return found;
    }

    /**
     * The spark source a write to state id {@code nowId} ({@link Block#getId}) strikes, or null.
     * For the change hook: with no spark source on, as by default, it costs one read.
     */
    static @Nullable ResourceLocation spark(int nowId) {
        Resolved r = resolved;
        return r.anySpark ? at(r.spark, nowId) : null;
    }

    /** The item-use source {@code item} is, or null. */
    static @Nullable ResourceLocation item(Item item) {
        return resolved.items.get(item);
    }

    /** The entity source {@code type} is, or null. */
    static @Nullable ResourceLocation entity(EntityType<?> type) {
        return resolved.entities.get(type);
    }

    /** The explosion source, or null if explosions don't ignite. */
    static @Nullable ResourceLocation explosion() {
        return resolved.explosion;
    }

    /** The loaded sources by id, test overrides included, enabled or not. */
    public static Map<ResourceLocation, IgnitionSource> sources() {
        return all();
    }

    private static @Nullable ResourceLocation at(ResourceLocation[] table, int id) {
        return id >= 0 && id < table.length ? table[id] : null;
    }

    // ---- loading ----

    /**
     * Resolves the sources again with {@code files} replacing or adding to the datapacks' files by
     * id, as a datapack holding them would after {@code /reload}; an empty map goes back to the
     * datapacks alone. The geometry table must then be rebuilt for block sources to reach the engine
     * ({@code Geometries.rebuildForTest}). For
     * GameTests. Server thread, since tags are read here.
     */
    public static void overrideForTest(Map<ResourceLocation, IgnitionSource> files) {
        testFiles = Map.copyOf(files);
        rebuild();
    }

    private static Map<ResourceLocation, IgnitionSource> all() {
        Map<ResourceLocation, IgnitionSource> test = testFiles;
        if (test.isEmpty()) {
            return loaded;
        }
        Map<ResourceLocation, IgnitionSource> all = new TreeMap<>(loaded);
        all.putAll(test);
        return all;
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            rebuild();
        }
    }

    /**
     * Resolves every enabled source against the bound tags, in file id order: where two sources
     * claim the same block state, item or entity type, the later id names it.
     */
    private static void rebuild() {
        int states = Block.BLOCK_STATE_REGISTRY.size();
        ResourceLocation[] spark = new ResourceLocation[states];
        boolean anySpark = false;
        Map<Item, ResourceLocation> items = new HashMap<>();
        Map<EntityType<?>, ResourceLocation> entities = new HashMap<>();
        ResourceLocation explosion = null;
        for (Map.Entry<ResourceLocation, IgnitionSource> e : new TreeMap<>(all()).entrySet()) {
            IgnitionSource s = e.getValue();
            if (!s.enabled() || s.type().isEmpty()) {
                continue;
            }
            ResourceLocation id = e.getKey();
            switch (s.type().get()) {
                case BLOCK -> {} // asked of each state by the geometry table: isStanding
                case SPARK -> anySpark |= markStates(s, id, spark);
                case ITEM_USE -> {
                    for (Item item : resolve(BuiltInRegistries.ITEM, Registries.ITEM, s.items())) {
                        items.put(item, id);
                    }
                }
                case ENTITY -> {
                    for (EntityType<?> type :
                            resolve(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, s.entities())) {
                        entities.put(type, id);
                    }
                }
                case EXPLOSION -> explosion = id;
            }
        }
        resolved = new Resolved(spark, anySpark, Map.copyOf(items), Map.copyOf(entities), explosion);
    }

    /**
     * Whether a block or spark source names {@code state}: its block is one of the source's, directly
     * or through a tag, and the state matches the source's state predicate. Without a predicate, a
     * block source's block with a boolean {@code lit} property counts only while lit, as {@code
     * #zas:combustion} does; a spark's counts in every state.
     */
    private static boolean matches(IgnitionSource s, BlockState state, boolean litRule) {
        boolean named = false;
        for (Target t : s.blocks()) {
            if (t.tag()
                    ? state.is(TagKey.create(Registries.BLOCK, t.id()))
                    : state.getBlockHolder().is(t.id())) {
                named = true;
                break;
            }
        }
        if (!named) {
            return false;
        }
        if (s.state().isPresent()) {
            return s.state().get().matches(state);
        }
        return !litRule
                || !(state.getBlock().getStateDefinition().getProperty("lit") instanceof BooleanProperty lit)
                || state.getValue(lit);
    }

    /** Marks the states of a spark source's blocks it names; whether any. */
    private static boolean markStates(IgnitionSource s, ResourceLocation id, ResourceLocation[] table) {
        boolean any = false;
        for (Block block : resolve(BuiltInRegistries.BLOCK, Registries.BLOCK, s.blocks())) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (matches(s, state, false)) {
                    int stateId = Block.getId(state);
                    if (stateId >= 0 && stateId < table.length) {
                        table[stateId] = id;
                        any = true;
                    }
                }
            }
        }
        return any;
    }

    /** The registry entries the targets name, directly or through a tag; unknown ones are skipped. */
    private static <T> List<T> resolve(
            Registry<T> registry, ResourceKey<? extends Registry<T>> key, List<Target> targets) {
        List<T> out = new ArrayList<>();
        for (Target t : targets) {
            if (t.tag()) {
                for (Holder<T> holder : registry.getTagOrEmpty(TagKey.create(key, t.id()))) {
                    out.add(holder.value());
                }
            } else {
                registry.getOptional(t.id()).ifPresent(out::add);
            }
        }
        return out;
    }

    /** Reads the files on every server data load. */
    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), IgnitionSource.DIRECTORY);
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, IgnitionSource> out = new TreeMap<>();
            DatapackErrors.beginLoad(KIND);
            for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
                var result = IgnitionSource.CODEC.parse(JsonOps.INSTANCE, e.getValue());
                result.error()
                        .ifPresent(
                                err -> DatapackErrors.skip(KIND, IgnitionSource.DIRECTORY, e.getKey(), err.message()));
                result.result().ifPresent(s -> out.put(e.getKey(), s));
            }
            loaded = out;
        }
    }
}
