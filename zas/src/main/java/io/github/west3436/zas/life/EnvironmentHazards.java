package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.config.HazardConfig;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The pressure and temperature hazards (LIFE-010, LIFE-011, LIFE-014), which harm every living
 * entity but those in {@code zas:atmosphere_immune} and creative and spectator players (LIFE-015,
 * LIFE-037), whether or not it breathes. Server thread.
 *
 * <p>The exposure is the atmosphere at the entity's eyes (API-015), the same sample its breath
 * comes from. Its protection ({@link Shielding}) takes its share of the pressure difference
 * (LIFE-020) and of temperature damage (LIFE-025) before the thresholds apply:
 *
 * <ul>
 *   <li><b>Decompression:</b> an effective pressure below 20 kPa deals 1 HP/s.
 *   <li><b>Barotrauma:</b> an effective pressure at or above 550 kPa deals min((P / 550 kPa − 1) ×
 *       2, 2) HP/s.
 *   <li><b>Frostbite:</b> below the comfort band (220 K by default), 0.5 HP/s; below 200 K, 1 HP/s;
 *       below 120 K, 2 HP/s.
 *   <li><b>Burns:</b> above the comfort band (360 K by default), 1 HP/s; above 400 K, 2 HP/s; above
 *       1,000 K, 4 HP/s.
 * </ul>
 *
 * <p>The colder or hotter, the higher the tier: each temperature hazard deals its highest tier
 * reached, so an unprotected entity in a 2.7 K vacuum takes 1 HP/s of decompression and 2 of
 * frostbite. Each rate is then multiplied by its hazard's scale in the entity's dimension, which is
 * 0 while the hazard is off (LIFE-031, LIFE-036).
 */
public final class EnvironmentHazards {
    /** The entity types pressure and temperature don't harm (LIFE-015, LIFE-016). */
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "atmosphere_immune"));

    /** 1 atm in kPa, the pressure that harms nothing; protection works on the difference from it. */
    public static final double ONE_ATMOSPHERE = StandardAir.PRESSURE;

    /** The effective pressure below which decompression harms, in kPa (LIFE-010). */
    public static final double LOW_PRESSURE = 20.0;

    /** Decompression's damage, in HP/s (LIFE-010). */
    public static final double DECOMPRESSION_RATE = 1.0;

    /** The effective pressure at or above which barotrauma harms, in kPa (LIFE-014). */
    public static final double HIGH_PRESSURE = 550.0;

    /** Barotrauma's most damage, in HP/s (LIFE-014). */
    public static final double BAROTRAUMA_CAP = 2.0;

    private EnvironmentHazards() {}

    /**
     * What reaches an entity where it is, and the damage each hazard deals it there, in HP/s, after
     * its protection and the hazard's scale.
     *
     * @param pressure the pressure around it in kPa
     * @param temperature the exposure temperature in K
     * @param effectivePressure the pressure its protection lets reach it in kPa (LIFE-020)
     */
    public record Exposure(
            double pressure,
            double temperature,
            double effectivePressure,
            double decompression,
            double barotrauma,
            double frostbite,
            double burns) {

        /** A hazard's damage in HP/s; 0 for the hazards that aren't pressure or temperature. */
        public double rate(HazardType hazard) {
            return switch (hazard) {
                case DECOMPRESSION -> decompression;
                case BAROTRAUMA -> barotrauma;
                case FROSTBITE -> frostbite;
                case BURNS -> burns;
                default -> 0;
            };
        }

        /** The hazards that harm it here: those with damage. */
        public Set<Hazard> hazards() {
            Set<Hazard> set = EnumSet.noneOf(Hazard.class);
            for (HazardType t : HazardType.values()) {
                if (rate(t) > 0) {
                    t.hazard().ifPresent(set::add);
                }
            }
            return set;
        }
    }

    /** Whether an entity's type is in {@code zas:atmosphere_immune} (LIFE-015). */
    public static boolean immune(LivingEntity entity) {
        return entity.getType().is(IMMUNE);
    }

    /**
     * Whether an entity is spared every hazard: a player whose abilities make it invulnerable, in
     * creative or spectator mode (LIFE-037).
     */
    public static boolean exempt(LivingEntity entity) {
        return entity instanceof Player player && player.getAbilities().invulnerable;
    }

    /** Whether pressure and temperature can harm an entity: it is neither immune nor exempt. */
    public static boolean affects(LivingEntity entity) {
        return !immune(entity) && !exempt(entity);
    }

    /**
     * The pressure that reaches an entity, in kPa: 1 atm plus the part of the difference its
     * protection lets through (LIFE-020).
     *
     * @param pressure the pressure around it in kPa
     * @param factor the fraction of the difference that reaches it, 0 to 1
     */
    public static double effectivePressure(double pressure, double factor) {
        return ONE_ATMOSPHERE + (pressure - ONE_ATMOSPHERE) * factor;
    }

    /** Decompression's damage at an effective pressure, in HP/s, before scaling (LIFE-010). */
    public static double decompression(double effectivePressure) {
        return effectivePressure < LOW_PRESSURE ? DECOMPRESSION_RATE : 0;
    }

    /** Barotrauma's damage at an effective pressure, in HP/s, before scaling (LIFE-014). */
    public static double barotrauma(double effectivePressure) {
        if (effectivePressure < HIGH_PRESSURE) {
            return 0;
        }
        return Math.min((effectivePressure / HIGH_PRESSURE - 1) * 2, BAROTRAUMA_CAP);
    }

    /**
     * Frostbite's damage at a temperature, in HP/s, before protection and scaling (LIFE-011): the
     * highest tier reached below the comfort band.
     *
     * @param comfortMin the bottom of the comfort band in K
     */
    public static double frostbite(double temperature, double comfortMin) {
        if (!(temperature < comfortMin)) {
            return 0;
        }
        if (temperature < 120) {
            return 2;
        }
        if (temperature < 200) {
            return 1;
        }
        return 0.5;
    }

    /**
     * Burns' damage at a temperature, in HP/s, before protection and scaling (LIFE-011): the highest
     * tier reached above the comfort band.
     *
     * @param comfortMax the top of the comfort band in K
     */
    public static double burns(double temperature, double comfortMax) {
        if (!(temperature > comfortMax)) {
            return 0;
        }
        if (temperature > 1000) {
            return 4;
        }
        if (temperature > 400) {
            return 2;
        }
        return 1;
    }

    /** An entity's exposure to the gas around it, with its own protection, in its dimension. */
    public static Exposure exposure(LivingEntity entity, GasMixture around) {
        return exposure(entity.level().dimension(), around, Shielding.of(entity));
    }

    /** The exposure to the gas around an entity with a protection, in a dimension. */
    public static Exposure exposure(ResourceKey<Level> dimension, GasMixture around, Shielding shielding) {
        double p = around.pressure();
        double t = around.temperature();
        double effective = effectivePressure(p, clamp01(shielding.pressureFactor(p)));
        double thermal = clamp01(shielding.thermalFactor(t));
        return new Exposure(
                p,
                t,
                effective,
                decompression(effective) * HazardConfig.scale(dimension, HazardType.DECOMPRESSION),
                barotrauma(effective) * HazardConfig.scale(dimension, HazardType.BAROTRAUMA),
                frostbite(t, HazardConfig.comfortMin()) * thermal * HazardConfig.scale(dimension, HazardType.FROSTBITE),
                burns(t, HazardConfig.comfortMax()) * thermal * HazardConfig.scale(dimension, HazardType.BURNS));
    }

    /** Deals an exposure's damage for {@code seconds}, each hazard as its own damage type. */
    static void apply(LivingEntity entity, Exposure exposure, double seconds) {
        if (seconds <= 0) {
            return;
        }
        for (HazardType t : HazardType.values()) {
            double damage = exposure.rate(t) * seconds;
            if (damage > 0 && entity.isAlive()) {
                entity.hurt(LifeDamage.source(entity, t.damageType()), (float) damage);
            }
        }
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 1 : Math.min(1, Math.max(0, v));
    }
}
