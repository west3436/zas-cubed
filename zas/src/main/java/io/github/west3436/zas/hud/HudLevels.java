package io.github.west3436.zas.hud;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.config.HazardConfig;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.EffectLevel;
import io.github.west3436.zas.life.EnvironmentHazards;
import io.github.west3436.zas.life.HazardProfile;
import io.github.west3436.zas.life.HazardProfiles;
import io.github.west3436.zas.life.HazardType;
import io.github.west3436.zas.life.Shielding;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The HUD's alert levels (CLIENT-008), judged on what reaches the player through its protection,
 * so that a danger is where LIFE harms it (owner's choice, 2026-11-02):
 *
 * <ul>
 *   <li><b>Pressure:</b> the effective pressure (LIFE-020). Warning below 50 kPa or above 325 kPa;
 *       danger below 20 kPa (LIFE-010) or at 550 kPa and above (LIFE-014).
 *   <li><b>O₂:</b> the partial pressure in its breath. Warning below 19.8 kPa (19.5 % of 1 atm);
 *       danger below the breathing minimum, 16 kPa by default (LIFE-003).
 *   <li><b>Temperature:</b> the exposure temperature, unless its thermal protection takes all
 *       temperature damage there (LIFE-025). Warning within 20 K of the comfort band's edges (240 K
 *       and 340 K by default); danger outside the band (220–360 K, LIFE-011).
 *   <li><b>Toxins:</b> each gas's partial pressure in its breath against its hazard profile
 *       (LIFE-004, LIFE-041). Warning at its first threshold; danger at its first threshold that
 *       incapacitates or deals damage. Durations are ignored, so the warning comes before the
 *       effects do. The HUD shows the gas with the worst level.
 * </ul>
 *
 * <p>A side of a reading whose hazard is off in the player's dimension (LIFE-031, LIFE-036) has no
 * alert, nor has anything for a player exempt from hazards (LIFE-037). O₂ and toxins have none
 * while its eyes are in a fluid, where vanilla's breathing applies (LIFE-022). Server thread.
 */
public final class HudLevels {
    /** The items that show every reading, alert or not, while held, such as an analyzer (CLIENT-008, CONT-034). */
    public static final TagKey<Item> SHOWS_READINGS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "shows_readings"));

    /** No alert. */
    public static final int NONE = 0;

    /** A warning. Negative for low pressure and cold. */
    public static final int WARNING = 1;

    /** A danger. Negative for low pressure and cold. */
    public static final int DANGER = 2;

    /** The pressure below which the HUD warns, in kPa (vg's 50 kPa). */
    public static final double LOW_PRESSURE_WARNING = 50.0;

    /** The pressure above which the HUD warns, in kPa (vg's 325 kPa). */
    public static final double HIGH_PRESSURE_WARNING = 325.0;

    /** The O₂ partial pressure below which the HUD warns, in kPa: 19.5 % of 1 atm (OSHA). */
    public static final double OXYGEN_WARNING = 19.8;

    /** How far inside the comfort band the temperature warnings start, in K. */
    public static final double TEMPERATURE_MARGIN = 20.0;

    private HudLevels() {}

    /** The level at an effective pressure in kPa: −2 to 2. */
    public static int pressure(double kPa) {
        if (kPa < EnvironmentHazards.LOW_PRESSURE) {
            return -DANGER;
        }
        if (kPa < LOW_PRESSURE_WARNING) {
            return -WARNING;
        }
        if (kPa >= EnvironmentHazards.HIGH_PRESSURE) {
            return DANGER;
        }
        if (kPa > HIGH_PRESSURE_WARNING) {
            return WARNING;
        }
        return NONE;
    }

    /**
     * The level at an O₂ partial pressure in kPa: 0 to 2.
     *
     * @param minimum the breathing minimum in kPa (LIFE-003)
     */
    public static int oxygen(double kPa, double minimum) {
        if (kPa < minimum) {
            return DANGER;
        }
        return kPa < OXYGEN_WARNING ? WARNING : NONE;
    }

    /**
     * The level at a temperature in K: −2 to 2.
     *
     * @param comfortMin the bottom of the comfort band in K (LIFE-011)
     * @param comfortMax the top of the comfort band in K
     */
    public static int temperature(double kelvin, double comfortMin, double comfortMax) {
        if (kelvin < comfortMin) {
            return -DANGER;
        }
        if (kelvin > comfortMax) {
            return DANGER;
        }
        if (kelvin < comfortMin + TEMPERATURE_MARGIN) {
            return -WARNING;
        }
        if (kelvin > comfortMax - TEMPERATURE_MARGIN) {
            return WARNING;
        }
        return NONE;
    }

    /** A gas's level at a partial pressure in the breath, by its hazard profile: 0 to 2. */
    public static int toxin(HazardProfile profile, double kPa) {
        int level = NONE;
        if (!(kPa > 0)) {
            return level;
        }
        for (HazardProfile.Threshold t : profile.thresholds()) {
            if (!t.reachedAt(kPa)) {
                continue;
            }
            if (t.damage() > 0
                    || t.effectLevel()
                            .filter(l -> l.compareTo(EffectLevel.INCAPACITATED) >= 0)
                            .isPresent()) {
                return DANGER;
            }
            level = WARNING;
        }
        return level;
    }

    /**
     * Whether an entity is shown every reading, alert or not (CLIENT-008, CONT-034): it holds an item
     * in {@link #SHOWS_READINGS} in either hand. A spectator holds nothing, so it isn't.
     */
    public static boolean showsReadings(LivingEntity entity) {
        return !entity.isSpectator()
                && (entity.getMainHandItem().is(SHOWS_READINGS)
                        || entity.getOffhandItem().is(SHOWS_READINGS));
    }

    /**
     * A living entity's HUD state where it is now, or null while the atmosphere at its eyes is
     * unresolved (LIFE-048, API-010), when the HUD keeps what it last showed.
     *
     * <p>While it {@linkplain #showsReadings is shown every reading}, the state keeps each value, and
     * an exempt or immune entity has its values with no alerts. O₂ and toxins have {@link
     * AtmosHudPayload#NO_BREATH} while it has no breath.
     */
    public static @Nullable AtmosHudPayload of(LivingEntity entity) {
        boolean readings = showsReadings(entity);
        boolean spared = EnvironmentHazards.exempt(entity) || EnvironmentHazards.immune(entity);
        if (spared && !readings) {
            return AtmosHudPayload.NONE;
        }
        BreathingRules.Assessment a = BreathingRules.assess(entity);
        EnvironmentHazards.Exposure exposure = a.exposure();
        AtmosphereSample sample = a.sample();
        if (spared) {
            // Nothing harms it, so nothing assessed its exposure: take it from the air at its eyes.
            if (sample == null) {
                sample = Atmospheres.of(entity);
            }
            exposure = sample.state() == AtmosphereState.UNRESOLVED
                    ? null
                    : EnvironmentHazards.exposure(entity, sample.mixture());
        }
        if (exposure == null || (sample != null && sample.state() == AtmosphereState.UNRESOLVED)) {
            return null;
        }
        ResourceKey<Level> dimension = entity.level().dimension();

        double effective = exposure.effectivePressure();
        int pressure = pressure(effective);
        if (pressure < 0 && off(dimension, HazardType.DECOMPRESSION)
                || pressure > 0 && off(dimension, HazardType.BAROTRAUMA)) {
            pressure = NONE;
        }

        double kelvin = exposure.temperature();
        int temperature = temperature(kelvin, HazardConfig.comfortMin(), HazardConfig.comfortMax());
        if (temperature < 0 && off(dimension, HazardType.FROSTBITE)
                || temperature > 0 && off(dimension, HazardType.BURNS)
                || temperature != 0 && !(Shielding.of(entity).thermalFactor(kelvin) > 0)) {
            temperature = NONE;
        }

        int oxygen = NONE;
        double oxygenKpa = AtmosHudPayload.NO_BREATH;
        int toxins = NONE;
        ResourceLocation toxinGas = null;
        double toxinKpa = AtmosHudPayload.NO_BREATH;
        Optional<GasMixture> breath = a.verdict().breath();
        if (breath.isPresent()) {
            GasMixture b = breath.get();
            oxygenKpa = b.partialPressure(StandardAir.OXYGEN);
            if (!off(dimension, HazardType.HYPOXIA)) {
                oxygen = oxygen(oxygenKpa, BreathingConfig.oxygenMinimum());
            }
            // The worst gas, and of those at the same level the one with the most.
            toxinKpa = 0;
            for (Map.Entry<ResourceLocation, HazardProfile> e :
                    HazardProfiles.all().entrySet()) {
                double p = b.partialPressure(e.getKey());
                int level = toxin(e.getValue(), p);
                if (level > toxins || level == toxins && p > toxinKpa) {
                    toxins = level;
                    toxinGas = e.getKey();
                    toxinKpa = p;
                }
            }
            if (off(dimension, HazardType.TOXIC_GAS)) {
                toxins = NONE;
            }
        }
        if (spared) {
            pressure = NONE;
            temperature = NONE;
            oxygen = NONE;
            toxins = NONE;
        }
        return new AtmosHudPayload(
                pressure,
                oxygen,
                temperature,
                toxins,
                (float) effective,
                (float) oxygenKpa,
                (float) kelvin,
                Optional.ofNullable(toxinGas),
                (float) toxinKpa,
                readings);
    }

    private static boolean off(ResourceKey<Level> dimension, HazardType hazard) {
        return !HazardConfig.enabled(dimension, hazard);
    }
}
