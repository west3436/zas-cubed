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
import java.util.function.Predicate;
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
import net.neoforged.bus.api.EventPriority;
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
 * <p>Once tags are bound ({@link TagsUpdatedEvent}, server side), the sources are resolved on the
 * server thread, before the geometry table is rebuilt from the same event: block sources become the
 * predicate the geometry table flags (Track 2's {@code Geometry.FLAG_IGNITION_SOURCE}), so the
 * engine finds standing sources itself; sparks become a table the change hook reads; items, entity
 * types and explosions are read by {@link IgnitionEvents}. Reads are safe from any thread.
 */
public final class IgnitionSources {
    /** The kind of entry, in {@link DatapackErrors} messages. */
    static final String KIND = "ignition source";

    /**
     * The resolved sources: the source id of each block state that is a standing source or a
     * spark, by state id (null where none); the item and entity type sources; the explosion source.
     */
    private record Resolved(
            ResourceLocation[] standing,
            ResourceLocation[] spark,
            boolean anySpark,
            Map<Item, ResourceLocation> items,
            Map<EntityType<?>, ResourceLocation> entities,
            @Nullable ResourceLocation explosion) {
        static final Resolved NONE =
                new Resolved(new ResourceLocation[0], new ResourceLocation[0], false, Map.of(), Map.of(), null);
    }

    private static volatile Map<ResourceLocation, IgnitionSource> loaded = Map.of();
    private static volatile Map<ResourceLocation, IgnitionSource> testFiles = Map.of();
    private static volatile Resolved resolved = Resolved.NONE;

    private IgnitionSources() {}

    /** Registers the loader and the tag listener on the game event bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
        // Before the geometry table's own rebuild on the same event, which reads the predicate.
        gameBus.addListener(EventPriority.HIGH, IgnitionSources::onTagsUpdated);
    }

    // ---- lookups (any thread) ----

    /** Whether {@code state} is a standing source: one that ignites while it is there. */
    public static boolean isStanding(BlockState state) {
        return standingSource(state) != null;
    }

    /** The standing source {@code state} is, or null. */
    public static @Nullable ResourceLocation standingSource(BlockState state) {
        return at(resolved.standing, Block.getId(state));
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
     * datapacks alone. The geometry table must then be rebuilt for block sources to apply. For
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
     * claim the same block state, item or entity type, the later id names it. Hands the standing
     * predicate to the geometry table.
     */
    private static void rebuild() {
        int states = Block.BLOCK_STATE_REGISTRY.size();
        ResourceLocation[] standing = new ResourceLocation[states];
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
                case BLOCK -> markStates(s, id, standing);
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
        Resolved r = new Resolved(standing, spark, anySpark, Map.copyOf(items), Map.copyOf(entities), explosion);
        resolved = r;
        EngineLink.setStandingSources(standingPredicate(r));
    }

    /** A predicate over this resolution's table alone, so a later rebuild doesn't change it. */
    private static Predicate<BlockState> standingPredicate(Resolved r) {
        ResourceLocation[] table = r.standing;
        return state -> at(table, Block.getId(state)) != null;
    }

    /** Marks the states of a block or spark source's blocks that match its state predicate; whether any did. */
    private static boolean markStates(IgnitionSource s, ResourceLocation id, ResourceLocation[] table) {
        boolean any = false;
        for (Block block : resolve(BuiltInRegistries.BLOCK, Registries.BLOCK, s.blocks())) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (s.state().map(p -> p.matches(state)).orElse(true)) {
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
                        .ifPresent(err ->
                                DatapackErrors.skip(KIND, IgnitionSource.DIRECTORY, e.getKey(), err.message()));
                result.result().ifPresent(s -> out.put(e.getKey(), s));
            }
            loaded = out;
        }
    }
}
