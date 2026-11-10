package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.config.HazardConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * ZAS³'s breathing verdict (API-014) from LIFE's rules, on the server:
 *
 * <ol>
 *   <li>With its eyes in a fluid, vanilla's verdict ({@link Breathing#vanilla}), so water stays
 *       vanilla's domain (LIFE-022), unless it would drown there and its internals deliver: then it
 *       breathes the tank, as in rule 4 (LIFE-047).
 *   <li>A player whose abilities make it invulnerable, in creative or spectator mode, is {@link
 *       BreathReason#EXEMPT} and never runs out of air or takes a hazard (LIFE-037).
 *   <li>Anything else that is neither a player nor in {@code zas:breathers} doesn't breathe
 *       (LIFE-001).
 *   <li>A breather breathes the atmosphere at its eyes (LIFE-002, API-015), or, with its internals
 *       on, its breathing sealed and a tank that delivers, the tank's gas ({@link Internals},
 *       {@link BreathSource#INTERNALS}, {@link Protection#SEALED_BREATHING}: LIFE-021). A filter in
 *       its head slot, or of its own, takes the {@code #zas:toxic} gases out of that breath,
 *       wherever it came from ({@link Protection#GAS_FILTERING}: API-022, CONT-088). With an O2
 *       partial pressure below the minimum it can't breathe (LIFE-003); a gas its hazard profile
 *       makes harmful there is a {@link Hazard#TOXIC_GAS} (LIFE-004).
 *   <li>Whether or not it breathes, and with its eyes in a fluid too, the pressure and temperature
 *       of the atmosphere at its eyes may harm it ({@link EnvironmentHazards}: LIFE-010, LIFE-011,
 *       LIFE-014), unless it is in {@code zas:atmosphere_immune} ({@link Protection#IMMUNE},
 *       LIFE-015).
 * </ol>
 *
 * <p>A hazard turned off in the entity's dimension isn't one (LIFE-031, LIFE-036): with hypoxia
 * off, a breath short of O2 is still {@link BreathReason#BREATHABLE}.
 *
 * <p>Worn and built-in protection ({@link WornProtection}) shields it from pressure and temperature
 * (LIFE-020, LIFE-025), and a verdict lists {@link Protection#PRESSURE} and {@link
 * Protection#THERMAL} where it is rated for the conditions, whether or not a hazard needs it.
 *
 * <p>Server thread, for an entity in a server level.
 */
public final class BreathingRules {
    /** The entity types that breathe, besides players (LIFE-001, LIFE-006). */
    public static final TagKey<EntityType<?>> BREATHERS =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "breathers"));

    private BreathingRules() {}

    /**
     * A verdict and what it was made from.
     *
     * @param verdict the verdict
     * @param sample the atmosphere at the eyes, when the verdict looked at it out of a fluid; null
     *     with the eyes in a fluid, even with a breath from a tank (LIFE-047)
     * @param drain the fraction of the vanilla underwater rate at which the air meter drains, 0 to
     *     1: 1 − pO2 / minimum while it can't breathe for lack of O2, else 0 (LIFE-003)
     * @param exposure its exposure to pressure and temperature, when they can harm it and the
     *     atmosphere at its eyes was looked at
     * @param internals the tank it breathes from, when it breathes from its internals (LIFE-021)
     */
    public record Assessment(
            Breathability verdict,
            @Nullable AtmosphereSample sample,
            double drain,
            EnvironmentHazards.@Nullable Exposure exposure,
            Internals.@Nullable Active internals) {
        /** An assessment of an entity that doesn't breathe from its internals. */
        public Assessment(
                Breathability verdict,
                @Nullable AtmosphereSample sample,
                double drain,
                EnvironmentHazards.@Nullable Exposure exposure) {
            this(verdict, sample, drain, exposure, null);
        }
    }

    /** Whether an entity type breathes: players, and the types in {@code zas:breathers} (LIFE-001). */
    public static boolean breathes(LivingEntity entity) {
        return entity instanceof Player || entity.getType().is(BREATHERS);
    }

    /** Whether an entity has anything to check: it breathes, or pressure and temperature can harm it. */
    public static boolean checked(LivingEntity entity) {
        return breathes(entity) || EnvironmentHazards.affects(entity);
    }

    /** The verdict for an entity where it is now. */
    public static Assessment assess(LivingEntity entity) {
        Breathability vanilla = Breathing.vanilla(entity);
        boolean environment = EnvironmentHazards.affects(entity);
        if (vanilla.source() == BreathSource.FLUID) {
            Internals.Active tank =
                    vanilla.reason() == BreathReason.DROWNING && breathes(entity) ? Internals.active(entity) : null;
            if (!environment && tank == null) {
                return new Assessment(vanilla, null, 0, null);
            }
            EnvironmentHazards.Exposure exposure = null;
            if (environment) {
                AtmosphereSample at = Atmospheres.of(entity);
                exposure = at.state() == AtmosphereState.UNRESOLVED
                        ? null
                        : EnvironmentHazards.exposure(entity, around(entity, at));
            }
            if (tank != null) {
                return breathing(entity, null, tank.breath(), exposure, tank);
            }
            Breathability.Builder b = Breathability.builder(vanilla.reason(), vanilla.source());
            hazards(b, exposure);
            return new Assessment(b.build(), null, 0, exposure);
        }
        if (EnvironmentHazards.exempt(entity)) {
            AtmosphereSample sample = Atmospheres.of(entity);
            Breathability exempt = Breathability.builder(BreathReason.EXEMPT, BreathSource.ATMOSPHERE)
                    .breath(around(entity, sample))
                    .protection(Protection.EXEMPT)
                    .build();
            return new Assessment(exempt, sample, 0, null);
        }
        boolean breathes = breathes(entity);
        if (!breathes && !environment) {
            Breathability immune = Breathability.builder(BreathReason.DOES_NOT_BREATHE, BreathSource.NONE)
                    .protection(Protection.IMMUNE)
                    .build();
            return new Assessment(immune, null, 0, null);
        }
        AtmosphereSample sample = Atmospheres.of(entity);
        GasMixture around = around(entity, sample);
        EnvironmentHazards.Exposure exposure = environment ? EnvironmentHazards.exposure(entity, around) : null;
        if (!breathes) {
            Breathability.Builder b = Breathability.builder(BreathReason.DOES_NOT_BREATHE, BreathSource.NONE);
            hazards(b, exposure);
            return new Assessment(b.build(), sample, 0, exposure);
        }
        Internals.Active internals = Internals.active(entity);
        return breathing(entity, sample, internals != null ? internals.breath() : around, exposure, internals);
    }

    /**
     * The air the rules take an entity to be in: the atmosphere at its eyes, or, outside every zone,
     * the air of another mod's breathable place there if a provider names one (SPACE-017). A zone
     * always governs, and an unresolved atmosphere stays the guess it is.
     */
    private static GasMixture around(LivingEntity entity, AtmosphereSample sample) {
        if (sample.state() == AtmosphereState.AMBIENT && entity.level() instanceof ServerLevel level) {
            GasMixture local = LocalAir.at(level, entity.getEyePosition());
            if (local != null) {
                return local;
            }
        }
        return sample.mixture();
    }

    /**
     * The verdict for a breather breathing {@code source}, the atmosphere or its tank: the O2 and
     * toxic gas rules (LIFE-003, LIFE-004), with its filter (API-022) and the exposure's hazards.
     */
    private static Assessment breathing(
            LivingEntity entity,
            @Nullable AtmosphereSample sample,
            GasMixture source,
            EnvironmentHazards.@Nullable Exposure exposure,
            Internals.@Nullable Active internals) {
        ResourceKey<Level> dimension = entity.level().dimension();
        boolean filtered = WornProtection.gasFiltering(entity);
        GasMixture breath = filtered ? GasHazards.filtered(source) : source;
        double minimum = BreathingConfig.oxygenMinimum();
        double oxygen = breath.partialPressure(StandardAir.OXYGEN);
        boolean enough = oxygen >= minimum || !HazardConfig.enabled(dimension, HazardType.HYPOXIA);
        Breathability.Builder b = Breathability.builder(
                        enough ? BreathReason.BREATHABLE : BreathReason.LOW_OXYGEN,
                        internals != null ? BreathSource.INTERNALS : BreathSource.ATMOSPHERE)
                .breath(breath);
        if (HazardConfig.enabled(dimension, HazardType.TOXIC_GAS) && GasHazards.harmful(breath)) {
            b.hazard(Hazard.TOXIC_GAS);
        }
        hazards(b, exposure);
        if (internals != null) {
            b.protection(Protection.SEALED_BREATHING);
        }
        if (filtered) {
            b.protection(Protection.GAS_FILTERING);
        }
        if (!EnvironmentHazards.affects(entity)) {
            b.protection(Protection.IMMUNE);
        }
        double drain = enough ? 0 : Math.min(1, Math.max(0, 1 - oxygen / minimum));
        return new Assessment(b.build(), sample, drain, exposure, internals);
    }

    /** Adds the pressure and temperature hazards of an exposure to a verdict, and the protection rated for them. */
    private static void hazards(Breathability.Builder b, EnvironmentHazards.@Nullable Exposure exposure) {
        if (exposure != null) {
            exposure.hazards().forEach(b::hazard);
            exposure.protection().forEach(b::protection);
        }
    }
}
