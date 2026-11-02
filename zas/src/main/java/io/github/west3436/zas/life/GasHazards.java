package io.github.west3436.zas.life;

import io.github.west3436.zas.api.gas.GasMixture;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 */
final class GasHazards {
    /** Game ticks an effect outlasts the next check. */
    static final int EFFECT_GRACE_TICKS = 20;

    private GasHazards() {}

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

    /**
     * Counts exposures and applies the thresholds that apply.
     *
     * @param entity the breathing entity
     * @param breath the gas it breathes
     * @param seconds the seconds since its last check, for exposure and damage
     * @param state its breathing state
     * @param effectTicks how long the effects last
     */
    static void apply(LivingEntity entity, GasMixture breath, double seconds, BreathState state, int effectTicks) {
        Map<Holder<MobEffect>, Integer> effects = new LinkedHashMap<>();
        double damagePerSecond = 0;
        for (Map.Entry<ResourceLocation, HazardProfile> e : HazardProfiles.all().entrySet()) {
            ResourceLocation gas = e.getKey();
            List<HazardProfile.Threshold> thresholds = e.getValue().thresholds();
            double p = breath.partialPressure(gas);
            if (thresholds.stream().noneMatch(t -> t.reachedAt(p)) && !state.exposed(gas)) {
                continue; // below every threshold, and nothing to reset
            }
            double[] exposure = state.exposure(gas, thresholds.size());
            double gasDamage = 0;
            boolean any = false;
            for (int i = 0; i < thresholds.size(); i++) {
                HazardProfile.Threshold t = thresholds.get(i);
                if (!t.reachedAt(p)) {
                    exposure[i] = 0;
                    continue;
                }
                any = true;
                exposure[i] += seconds;
                if (exposure[i] < t.duration()) {
                    continue;
                }
                t.effectLevel().ifPresent(level -> level.effects().forEach(fx -> add(effects, fx)));
                t.effects().forEach(fx -> add(effects, fx));
                gasDamage = Math.max(gasDamage, t.damage());
            }
            if (!any) {
                state.clearExposure(gas);
            }
            damagePerSecond += gasDamage;
        }
        effects.forEach((effect, amplifier) ->
                entity.addEffect(new MobEffectInstance(effect, effectTicks, amplifier, false, false, true)));
        double damage = damagePerSecond * seconds;
        if (damage > 0) {
            entity.hurt(LifeDamage.source(entity, LifeDamage.TOXIC_GAS), (float) damage);
        }
    }

    private static void add(Map<Holder<MobEffect>, Integer> effects, EffectLevel.Effect fx) {
        effects.merge(fx.effect(), fx.amplifier(), Math::max);
    }
}
