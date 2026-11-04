package io.github.west3436.zas.fire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.geometry.GeometryOverride;
import java.util.List;

/**
 * An ignition temperature for some flammable blocks (FIRE-033): the gas temperature they catch fire
 * at, in place of {@code [fire] block_ignition_temperature}. Whether a block is flammable at all, and
 * its odds, stay vanilla's.
 *
 * <p>Files live at {@code data/<namespace>/zas/block_ignition/<path>.json}:
 *
 * <pre>{@code
 * {
 *   "blocks": ["#minecraft:wool", "#minecraft:leaves"],
 *   "temperature": 500
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code blocks}: block ids, or block tags prefixed with {@code #}. Unknown ids and tags are
 *       skipped.
 *   <li>{@code temperature}: the ignition temperature, in K, above 0, up to 100,000.
 * </ul>
 *
 * @param targets the blocks and tags it applies to
 * @param temperature their ignition temperature, K
 */
public record BlockIgnition(List<GeometryOverride.Target> targets, double temperature) {
    /** The datapack directory, under {@code data/<namespace>/}. */
    public static final String DIRECTORY = "zas/block_ignition";

    /** The highest temperature accepted, K. */
    static final double MAX_TEMPERATURE = 100_000;

    private static final Codec<Double> TEMPERATURE = Codec.DOUBLE.validate(t -> t > 0 && t <= MAX_TEMPERATURE
            ? DataResult.success(t)
            : DataResult.error(() -> "Temperature must be above 0, up to " + MAX_TEMPERATURE + " K, got " + t));

    public static final Codec<BlockIgnition> CODEC = RecordCodecBuilder.create(i -> i.group(
                    DatapackErrors.field(GeometryOverride.Target.CODEC.listOf(), "blocks")
                            .forGetter(BlockIgnition::targets),
                    DatapackErrors.field(TEMPERATURE, "temperature").forGetter(BlockIgnition::temperature))
            .apply(i, BlockIgnition::new));

    public BlockIgnition {
        targets = List.copyOf(targets);
    }
}
