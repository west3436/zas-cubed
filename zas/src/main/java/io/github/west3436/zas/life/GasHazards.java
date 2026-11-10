package io.github.west3436.zas.life;

import io.github.west3436.zas.api.event.HazardTickEvent;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasTags;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies the gases' hazard profiles to a breathing entity at each breath check (LIFE-004,
 * LIFE-040, LIFE-041). Server thread.
 *
 * <p>For each gas with a profile, a threshold applies while the gas's partial pressure in the
 * breath is at or above it and has been for its duration; the exposure is counted from check to
 * check and starts again when the partial pressure falls below. Every threshold that applies adds
 * its effect level and status effects; an effect applied by several takes the highest amplifier.
 * For damage, each gas deals the highest damage among its thresholds that apply, and the gases'
 * damages add up, as toxic gas damage for the seconds since the last check (LIFE-007).
 *
 * <p>Effects last until a little after the next check, so they hold while the exposure does and
 * wear off soon after it ends.
 *
 * <p>All of that is one tick of the toxic gas hazard, which a listener can modify or cancel (API-033):
 * cancelling it also stops the effects, and the exposures go on counting either way.
 */
final class GasHazards {
    /** Game ticks an effect outlasts the next check. */
    static final int EFFECT_GRACE_TICKS = 20;

    private GasHazards() {}

    /**
     * A breath as a filter leaves it (API-022, CONT-088): without the {@code #zas:toxic} gases, at the
     * same temperature and volume, so the other gases keep their partial pressures. It only changes
     * what is breathed: the gases stay where they were, so a filter holds nothing and never wears out.
     */
    static GasMixture filtered(GasMixture breath) {
        Set<ResourceLocation> toxic = GasTags.gases(GasTags.TOXIC);
        if (Collections.disjoint(breath.moles().keySet(), toxic)) {
            return breath;
        }
        Map<ResourceLocation, Double> kept = new HashMap<>(breath.moles());
        kept.keySet().removeAll(toxic);
        return new GasMixture(kept, breath.temperature(), breath.volume());
    }

    /** Whether a breath holds a gas at a partial pressure its profile makes harmful, at once or once sustained. */
    static boolean harmful(GasMixture breath) {
        for (Map.Entry<ResourceLocation, HazardProfile> e : HazardProfiles.all().entrySet()) {
            double p = breath.partialPressure(e.getKey());
            if (p > 0 && e.getValue().harmfulAt(p)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a breath holds a gas at a partial pressure where it deals damage, at once or once sustained. Any thread. */
    static boolean damaging(GasMixture breath) {
        for (Map.Entry<ResourceLocation, HazardProfile> e : HazardProfiles.all().entrySet()) {
            double p = breath.partialPressure(e.getKey());
            if (p > 0 && e.getValue().damagingAt(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts exposures and applies the thresholds that apply.
     *
     * @param entity the breathing entity
     * @param breath the gas it breathes
     * @param seconds the seconds since its last check, for exposure and damage
     * @param state its breathing state
     * @param effectTicks how long the effects last
     * @param scale the toxic gas hazard's scale, which multiplies the damage but not the effects
     *     (LIFE-031)
     */
    static void apply(
            LivingEntity entity, GasMixture breath, double seconds, BreathState state, int effectTicks, double scale) {
        count(breath, seconds, state);
        Applying applying = applying(breath, state);
        Map<Holder<MobEffect>, Integer> effects = applying.effects();
        double damage = applying.damagePerSecond() * seconds * scale;
        if (effects.isEmpty() && !(damage > 0)) {
            return;
        }
        HazardTickEvent tick = LifeDamage.tick(entity, HazardType.TOXIC_GAS, damage);
        if (tick.isCanceled()) {
            return;
        }
        effects.forEach((effect, amplifier) ->
                entity.addEffect(new MobEffectInstance(effect, effectTicks, amplifier, false, false, true)));
        LifeDamage.hurt(entity, HazardType.TOXIC_GAS, tick.damage());
    }

    /**
     * The toxic gas damage a breath deals per second with the exposures the checks have counted, in
     * HP/s before the hazard's scale: what {@link #apply} charges for each second. It is 0 while
     * every damaging threshold the breath reaches still waits out its duration. Changes nothing.
     */
    static double damagePerSecond(GasMixture breath, BreathState state) {
        return applying(breath, state).damagePerSecond();
    }

    /**
     * What the thresholds that apply come to.
     *
     * @param effects their status effects, the highest amplifier of each
     * @param damagePerSecond their damage in HP/s, each gas's highest added up, before the hazard's
     *     scale
     */
    private record Applying(Map<Holder<MobEffect>, Integer> effects, double damagePerSecond) {}

    /**
     * Counts a breath's exposures for the seconds since the last check: for each gas with a profile,
     * the time its partial pressure has been at or above each threshold, which starts again when it
     * falls below.
     */
    private static void count(GasMixture breath, double seconds, BreathState state) {
        for (Map.Entry<ResourceLocation, HazardProfile> e : HazardProfiles.all().entrySet()) {
            ResourceLocation gas = e.getKey();
            List<HazardProfile.Threshold> thresholds = e.getValue().thresholds();
            double p = breath.partialPressure(gas);
            if (thresholds.stream().noneMatch(t -> t.reachedAt(p))) {
                state.clearExposure(gas);
                continue;
            }
            double[] exposure = state.exposure(gas, thresholds.size());
            for (int i = 0; i < exposure.length; i++) {
                exposure[i] = thresholds.get(i).reachedAt(p) ? exposure[i] + seconds : 0;
            }
        }
    }

    /**
     * The thresholds that apply to a breath with the exposures counted so far: those its partial
     * pressures reach and have reached for their duration (LIFE-004). The one place that decides
     * it, for the check that applies them and for whatever reports them. Changes nothing.
     */
    private static Applying applying(GasMixture breath, BreathState state) {
        Map<Holder<MobEffect>, Integer> effects = new LinkedHashMap<>();
        double damagePerSecond = 0;
        for (Map.Entry<ResourceLocation, HazardProfile> e : HazardProfiles.all().entrySet()) {
            ResourceLocation gas = e.getKey();
            List<HazardProfile.Threshold> thresholds = e.getValue().thresholds();
            double p = breath.partialPressure(gas);
            double gasDamage = 0;
            for (int i = 0; i < thresholds.size(); i++) {
                HazardProfile.Threshold t = thresholds.get(i);
                if (!t.reachedAt(p) || state.sustained(gas, i, thresholds.size()) < t.duration()) {
                    continue;
                }
                t.effectLevel().ifPresent(level -> level.effects().forEach(fx -> add(effects, fx)));
                t.effects().forEach(fx -> add(effects, fx));
                gasDamage = Math.max(gasDamage, t.damage());
            }
            damagePerSecond += gasDamage;
        }
        return new Applying(effects, damagePerSecond);
    }

    private static void add(Map<Holder<MobEffect>, Integer> effects, EffectLevel.Effect fx) {
        effects.merge(fx.effect(), fx.amplifier(), Math::max);
    }
}
