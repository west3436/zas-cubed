package io.github.west3436.zas.fire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.geometry.GeometryOverride;
import java.util.List;

/**
 * A fuel-burning block's profile (FIRE-025): the power it burns at, as carbon (C + O₂ → CO₂, 394
 * kJ/mol), so its O₂ use and CO₂ output follow from the power.
 *
 * <p>Files live at {@code data/<namespace>/zas/fuel_profile/<path>.json}:
 *
 * <pre>{@code
 * {
 *   "blocks": ["minecraft:furnace", "#minecraft:campfires"],
 *   "watts": 5000
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code blocks}: block ids, or block tags prefixed with {@code #}. Unknown ids and tags are
 *       skipped.
 *   <li>{@code watts}: the power, in W, 0 to 10,000,000.
 *   <li>{@code burning_on}: optional; for fire, the blocks a fire must burn on for this profile
 *       to apply (FIRE-022), ids or {@code #} tags. A profile with it beats one without.
 * </ul>
 *
 * @param targets the blocks and tags it applies to
 * @param watts its power, W
 * @param burningOn the blocks a fire burns on for it to apply; empty for any
 */
public record FuelProfile(
        List<GeometryOverride.Target> targets, double watts, List<GeometryOverride.Target> burningOn) {
    /** The datapack directory, under {@code data/<namespace>/}. */
    public static final String DIRECTORY = "zas/fuel_profile";

    /** The highest power accepted, W. */
    static final double MAX_WATTS = 1e7;

    private static final Codec<Double> WATTS = Codec.DOUBLE.validate(w -> w >= 0 && w <= MAX_WATTS
            ? DataResult.success(w)
            : DataResult.error(() -> "Power must be 0 to " + MAX_WATTS + " W, got " + w));

    public static final Codec<FuelProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
                    DatapackErrors.field(GeometryOverride.Target.CODEC.listOf(), "blocks")
                            .forGetter(FuelProfile::targets),
                    DatapackErrors.field(WATTS, "watts").forGetter(FuelProfile::watts),
                    DatapackErrors.optionalField(GeometryOverride.Target.CODEC.listOf(), "burning_on", List.of())
                            .forGetter(FuelProfile::burningOn))
            .apply(i, FuelProfile::new));

    public FuelProfile {
        targets = List.copyOf(targets);
        burningOn = List.copyOf(burningOn);
    }
}
