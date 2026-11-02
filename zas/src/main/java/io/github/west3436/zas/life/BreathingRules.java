package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * ZAS³'s breathing verdict (API-014) from LIFE's breathing rules, on the server:
 *
 * <ol>
 *   <li>With its eyes in a fluid, vanilla's verdict ({@link Breathing#vanilla}), so water stays
 *       vanilla's domain (LIFE-022).
 *   <li>A player whose abilities make it invulnerable, in creative or spectator mode, is {@link
 *       BreathReason#EXEMPT} and never runs out of air (LIFE-037).
 *   <li>Anything else that is neither a player nor in {@code zas:breathers} doesn't breathe
 *       (LIFE-001).
 *   <li>A breather breathes the atmosphere at its eyes (LIFE-002, API-015). With an O2 partial
 *       pressure below the minimum it can't breathe (LIFE-003); a gas its hazard profile makes
 *       harmful there is a {@link Hazard#TOXIC_GAS} (LIFE-004).
 * </ol>
 *
 * <p><b>Not yet:</b> internals (LIFE-021, LIFE-002's tank, API-022), protection (LIFE-020,
 * LIFE-025, LIFE-026) and the pressure and temperature hazards (LIFE-010, LIFE-011, LIFE-014) come
 * with #35 and #36. Until then no breath comes from {@link BreathSource#INTERNALS}, and a verdict
 * lists no protection but {@link Protection#EXEMPT} and no hazard but {@link Hazard#TOXIC_GAS}.
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
     * @param sample the atmosphere at the eyes, when the verdict looked at it
     * @param drain the fraction of the vanilla underwater rate at which the air meter drains, 0 to
     *     1: 1 − pO2 / minimum while it can't breathe for lack of O2, else 0 (LIFE-003)
     */
    public record Assessment(
            Breathability verdict, @Nullable AtmosphereSample sample, double drain) {}

    /** Whether an entity type breathes: players, and the types in {@code zas:breathers} (LIFE-001). */
    public static boolean breathes(LivingEntity entity) {
        return entity instanceof Player || entity.getType().is(BREATHERS);
    }

    /** The verdict for an entity where it is now. */
    public static Assessment assess(LivingEntity entity) {
        Breathability vanilla = Breathing.vanilla(entity);
        if (vanilla.source() == BreathSource.FLUID) {
            return new Assessment(vanilla, null, 0);
        }
        if (entity instanceof Player player && player.getAbilities().invulnerable) {
            AtmosphereSample sample = Atmospheres.of(entity);
            Breathability exempt = Breathability.builder(BreathReason.EXEMPT, BreathSource.ATMOSPHERE)
                    .breath(sample.mixture())
                    .protection(Protection.EXEMPT)
                    .build();
            return new Assessment(exempt, sample, 0);
        }
        if (!breathes(entity)) {
            return new Assessment(Breathability.of(BreathReason.DOES_NOT_BREATHE, BreathSource.NONE), null, 0);
        }
        AtmosphereSample sample = Atmospheres.of(entity);
        GasMixture breath = sample.mixture();
        double minimum = BreathingConfig.oxygenMinimum();
        double oxygen = breath.partialPressure(StandardAir.OXYGEN);
        boolean enough = oxygen >= minimum;
        Breathability.Builder b = Breathability.builder(
                        enough ? BreathReason.BREATHABLE : BreathReason.LOW_OXYGEN, BreathSource.ATMOSPHERE)
                .breath(breath);
        if (GasHazards.harmful(breath)) {
            b.hazard(Hazard.TOXIC_GAS);
        }
        double drain = enough ? 0 : Math.min(1, Math.max(0, 1 - oxygen / minimum));
        return new Assessment(b.build(), sample, drain);
    }
}
