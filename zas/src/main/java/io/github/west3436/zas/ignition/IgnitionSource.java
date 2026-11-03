package io.github.west3436.zas.ignition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.geometry.GeometryOverride.Target;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;

/**
 * One datapack ignition source (FIRE-014, FIRE-015): something that ignites a flammable mixture
 * in its cell or a neighbouring cell of its zone.
 *
 * <p>Files live at {@code data/<namespace>/zas/ignition_source/<path>.json}:
 *
 * <pre>{@code
 * {
 *   "type": "block",
 *   "blocks": ["#minecraft:campfires"],
 *   "state": {"lit": "true"}
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code type}: what ignites ({@link Type}). Required unless the file is disabled.
 *   <li>{@code enabled}: whether the source is on; default true. A pack turns a shipped source off
 *       by shipping a file with the same id and {@code "enabled": false}, and nothing else.
 *   <li>{@code blocks}: for {@code block} and {@code spark}, block ids or block tags prefixed with
 *       {@code #}. Unknown ids and tags are skipped.
 *   <li>{@code state}: for {@code block} and {@code spark}, the block state properties that must
 *       match, in the format of vanilla's block state predicates. Optional.
 *   <li>{@code items}: for {@code item_use}, item ids or item tags prefixed with {@code #}.
 *   <li>{@code entities}: for {@code entity}, entity type ids or entity type tags prefixed with
 *       {@code #}.
 * </ul>
 *
 * @param type what ignites; empty only in a disabled file
 * @param enabled whether the source is on
 * @param blocks the blocks of a block or spark source
 * @param state the state properties a block or spark source's blocks must have
 * @param items the items of an item-use source
 * @param entities the entity types of an entity source
 */
public record IgnitionSource(
        Optional<Type> type,
        boolean enabled,
        List<Target> blocks,
        Optional<StatePropertiesPredicate> state,
        List<Target> items,
        List<Target> entities) {

    /** The datapack directory, under {@code data/<namespace>/}. */
    public static final String DIRECTORY = "zas/ignition_source";

    /** What kind of thing ignites. */
    public enum Type {
        /**
         * A block that ignites while it is there: fire, lava, a lit campfire, a torch. The engine
         * finds these in the blocks it mirrors (a geometry flag), so they cost nothing per tick.
         */
        BLOCK,
        /**
         * A block that ignites once, at the moment a block is written to a matching state: a lever
         * thrown, a redstone wire's power changing. The redstone spark.
         */
        SPARK,
        /** An item used on a block, igniting the cell on the side used: flint and steel. */
        ITEM_USE,
        /** An entity appearing in the world, igniting its cell: lightning. */
        ENTITY,
        /** An explosion, igniting its centre. Wind charges' bursts, which break nothing, don't count. */
        EXPLOSION;

        /** The name in files. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
                s -> {
                    for (Type t : values()) {
                        if (t.id().equals(s)) {
                            return DataResult.success(t);
                        }
                    }
                    return DataResult.error(() ->
                            "Unknown type '" + s + "'; expected block, spark, item_use, entity or explosion");
                },
                Type::id);
    }

    public static final Codec<IgnitionSource> CODEC = RecordCodecBuilder.<IgnitionSource>create(i -> i.group(
                            DatapackErrors.optionalField(Type.CODEC, "type").forGetter(IgnitionSource::type),
                            DatapackErrors.optionalField(Codec.BOOL, "enabled", true)
                                    .forGetter(IgnitionSource::enabled),
                            DatapackErrors.optionalField(Target.CODEC.listOf(), "blocks", List.of())
                                    .forGetter(IgnitionSource::blocks),
                            DatapackErrors.optionalField(StatePropertiesPredicate.CODEC, "state")
                                    .forGetter(IgnitionSource::state),
                            DatapackErrors.optionalField(Target.CODEC.listOf(), "items", List.of())
                                    .forGetter(IgnitionSource::items),
                            DatapackErrors.optionalField(Target.CODEC.listOf(), "entities", List.of())
                                    .forGetter(IgnitionSource::entities))
                    .apply(i, IgnitionSource::new))
            .validate(IgnitionSource::check);

    public IgnitionSource {
        blocks = List.copyOf(blocks);
        items = List.copyOf(items);
        entities = List.copyOf(entities);
    }

    /** A source that is off, as a pack's file turning a shipped one off reads. */
    public static IgnitionSource disabled() {
        return new IgnitionSource(Optional.empty(), false, List.of(), Optional.empty(), List.of(), List.of());
    }

    /** Whether it is on and of type {@code t}. */
    public boolean is(Type t) {
        return enabled && type.orElse(null) == t;
    }

    private static DataResult<IgnitionSource> check(IgnitionSource s) {
        if (s.type.isEmpty()) {
            return s.enabled ? DataResult.error(() -> "type: is required") : DataResult.success(s);
        }
        Type t = s.type.get();
        boolean blockLike = t == Type.BLOCK || t == Type.SPARK;
        if (blockLike && s.blocks.isEmpty()) {
            return DataResult.error(() -> "blocks: is required for type " + t.id());
        }
        if (!blockLike && !s.blocks.isEmpty()) {
            return DataResult.error(() -> "blocks: only for types block and spark");
        }
        if (!blockLike && s.state.isPresent()) {
            return DataResult.error(() -> "state: only for types block and spark");
        }
        if (t == Type.ITEM_USE && s.items.isEmpty()) {
            return DataResult.error(() -> "items: is required for type item_use");
        }
        if (t != Type.ITEM_USE && !s.items.isEmpty()) {
            return DataResult.error(() -> "items: only for type item_use");
        }
        if (t == Type.ENTITY && s.entities.isEmpty()) {
            return DataResult.error(() -> "entities: is required for type entity");
        }
        if (t != Type.ENTITY && !s.entities.isEmpty()) {
            return DataResult.error(() -> "entities: only for type entity");
        }
        return DataResult.success(s);
    }
}
