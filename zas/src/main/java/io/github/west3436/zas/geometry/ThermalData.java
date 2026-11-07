package io.github.west3436.zas.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.data.DatapackErrors;
import java.util.List;
import java.util.Optional;

/**
 * Datapack heat data for blocks (HEAT-002, HEAT-003): a block's thermal transmittance, and the
 * fixed temperature of a heat-source block.
 *
 * <p>Files live at {@code data/<namespace>/zas/thermal/<path>.json}:
 *
 * <pre>{@code
 * {
 *   "blocks": ["#minecraft:wool", "minecraft:hay_block"],
 *   "transmittance": 0.04
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code blocks}: block ids, or block tags prefixed with {@code #}. Unknown ids and tags
 *       are skipped.
 *   <li>{@code transmittance}: U in W/(m²·K) per block-thick layer, 0 or more. Optional. A block of
 *       U 0 lets no heat through, by conduction or by radiating to a vacuum (HEAT-019).
 *   <li>{@code temperature}: makes the blocks heat sources at this temperature in K (HEAT-003).
 *       Optional.
 * </ul>
 *
 * <p>At least one of the two is required. Each field is resolved on its own: for a block, a file
 * naming it directly beats one matching it through a tag, and between equals the file whose id
 * sorts last wins. So one file can give a tag's U and another a single block's temperature.
 *
 * @param targets the blocks and tags it applies to
 * @param transmittance U, if given
 * @param temperature the heat-source temperature, if given
 */
public record ThermalData(
        List<GeometryOverride.Target> targets, Optional<Double> transmittance, Optional<Double> temperature) {
    /** The datapack directory, under {@code data/<namespace>/}. */
    public static final String DIRECTORY = "zas/thermal";

    /** The highest U or temperature accepted, so a typo can't make a block a perfect conductor. */
    static final double MAX = 1e6;

    private static final Codec<Double> TRANSMITTANCE = Codec.DOUBLE.validate(u -> u >= 0 && u <= MAX
            ? DataResult.success(u)
            : DataResult.error(() -> "Transmittance must be 0 to " + MAX + " W/(m²·K), got " + u));

    private static final Codec<Double> TEMPERATURE = Codec.DOUBLE.validate(t -> t >= 0 && t <= MAX
            ? DataResult.success(t)
            : DataResult.error(() -> "Temperature must be 0 to " + MAX + " K, got " + t));

    public static final Codec<ThermalData> CODEC = RecordCodecBuilder.<ThermalData>create(i -> i.group(
                            DatapackErrors.field(GeometryOverride.Target.CODEC.listOf(), "blocks")
                                    .forGetter(ThermalData::targets),
                            DatapackErrors.optionalField(TRANSMITTANCE, "transmittance")
                                    .forGetter(ThermalData::transmittance),
                            DatapackErrors.optionalField(TEMPERATURE, "temperature")
                                    .forGetter(ThermalData::temperature))
                    .apply(i, ThermalData::new))
            .validate(d -> d.transmittance().isPresent() || d.temperature().isPresent()
                    ? DataResult.success(d)
                    : DataResult.error(() -> "Needs a transmittance, a temperature or both"));

    public ThermalData {
        targets = List.copyOf(targets);
    }
}
