package io.github.west3436.zas.life;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.data.DatapackErrors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * A gas's hazard profile (LIFE-004): the partial pressures in the breath at which it harms a
 * breathing entity, each optionally only once it has been sustained for a duration, and what it
 * does there. Data, from {@code data/<namespace>/zas/hazard_profile/<path>.json}, for the gas
 * {@code <namespace>:<path>} (docs/datapacks.md).
 *
 * <p>A threshold applies at or above its partial pressure, or, with {@code "above": true}, only
 * strictly above it. A threshold with no effect level, status effects or damage is a warning level: it does
 * nothing to the entity, and the HUD warns at it (CLIENT-008). The thresholds are kept in order of
 * partial pressure, then duration.
 *
 * @param thresholds the thresholds, in order; empty for a gas that is harmless to breathe
 */
public record HazardProfile(List<Threshold> thresholds) {
    /**
     * One threshold of a profile.
     *
     * @param partialPressure the gas's partial pressure in the breath at or above which it applies,
     *     or with {@code above} strictly above which, in kPa
     * @param above whether it applies only strictly above {@code partialPressure}, for a row that
     *     says "above" (LIFE-041)
     * @param duration the seconds the partial pressure must reach it for first; 0
     *     for at once
     * @param effectLevel the effect level it applies (LIFE-040), if any
     * @param effects other status effects it applies
     * @param damage the toxic gas damage it deals, in HP per second; 0 for none
     */
    public record Threshold(
            double partialPressure,
            boolean above,
            double duration,
            Optional<EffectLevel> effectLevel,
            List<EffectLevel.Effect> effects,
            double damage) {
        /** A threshold that applies at or above its partial pressure. */
        public Threshold(
                double partialPressure,
                double duration,
                Optional<EffectLevel> effectLevel,
                List<EffectLevel.Effect> effects,
                double damage) {
            this(partialPressure, false, duration, effectLevel, effects, damage);
        }

        /** Whether a partial pressure of the gas reaches it: at or above it, or strictly above. */
        public boolean reachedAt(double p) {
            return above ? p > partialPressure : p >= partialPressure;
        }

        /** Whether it does anything to the entity, rather than only warning. */
        public boolean harmful() {
            return effectLevel.isPresent() || !effects.isEmpty() || damage > 0;
        }
    }

    public HazardProfile {
        List<Threshold> sorted = new ArrayList<>(thresholds);
        sorted.sort(Comparator.comparingDouble(Threshold::partialPressure).thenComparingDouble(Threshold::duration));
        thresholds = List.copyOf(sorted);
    }

    /**
     * Whether a partial pressure of the gas is harmful at once or once sustained: whether a
     * threshold that does something applies at it.
     */
    public boolean harmfulAt(double partialPressure) {
        for (Threshold t : thresholds) {
            if (t.harmful() && t.reachedAt(partialPressure)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a threshold that deals damage applies at a partial pressure of the gas, at once or once sustained. */
    public boolean damagingAt(double partialPressure) {
        for (Threshold t : thresholds) {
            if (t.damage() > 0 && t.reachedAt(partialPressure)) {
                return true;
            }
        }
        return false;
    }

    // ---- Data format ------------------------------------------------------------------

    private static final Codec<EffectLevel.Effect> EFFECT = RecordCodecBuilder.create(i -> i.group(
                    DatapackErrors.field(BuiltInRegistries.MOB_EFFECT.holderByNameCodec(), "id")
                            .forGetter(EffectLevel.Effect::effect),
                    DatapackErrors.optionalField(Codec.intRange(0, 255), "amplifier", 0)
                            .forGetter(EffectLevel.Effect::amplifier))
            .apply(i, EffectLevel.Effect::new));

    private static final Codec<Threshold> THRESHOLD = RecordCodecBuilder.create(i -> i.group(
                    DatapackErrors.field(positive(), "partial_pressure").forGetter(Threshold::partialPressure),
                    DatapackErrors.optionalField(Codec.BOOL, "above", false).forGetter(Threshold::above),
                    DatapackErrors.optionalField(notNegative(), "duration", 0.0).forGetter(Threshold::duration),
                    DatapackErrors.optionalField(EffectLevel.CODEC, "effect_level")
                            .forGetter(Threshold::effectLevel),
                    DatapackErrors.optionalField(EFFECT.listOf(), "effects", List.of())
                            .forGetter(Threshold::effects),
                    DatapackErrors.optionalField(notNegative(), "damage", 0.0).forGetter(Threshold::damage))
            .apply(i, Threshold::new));

    /** The file format: {@code {"thresholds": [...]}}. */
    public static final Codec<HazardProfile> CODEC = RecordCodecBuilder.create(
            i -> i.group(DatapackErrors.field(THRESHOLD.listOf(), "thresholds").forGetter(HazardProfile::thresholds))
                    .apply(i, HazardProfile::new));

    private static Codec<Double> positive() {
        return Codec.DOUBLE.validate(v -> Double.isFinite(v) && v > 0
                ? DataResult.success(v)
                : DataResult.error(() -> "must be finite and greater than 0, got " + v));
    }

    private static Codec<Double> notNegative() {
        return Codec.DOUBLE.validate(v -> Double.isFinite(v) && v >= 0
                ? DataResult.success(v)
                : DataResult.error(() -> "must be finite and at least 0, got " + v));
    }
}
