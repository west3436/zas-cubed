package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.LevelThread;
import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.ZasApi;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * The breathability helper (API-014): whether an entity can breathe where it is, and whether it
 * is protected. It is the one call that ZAS³'s own hazards and HUD and other mods' compat layers,
 * such as a space mod's oxygen and temperature checks, route their checks through (D-12, D-51).
 *
 * <p><b>The verdict with ZAS³.</b> It applies ZAS³'s life-support rules and every life-support
 * provider:
 *
 * <ul>
 *   <li>who breathes: players and the entity types in {@code zas:breathers} (LIFE-001);
 *   <li>what it breathes: its internals when they are on and supplied, otherwise the atmosphere at
 *       its eyes (LIFE-002, API-015);
 *   <li>the O₂ minimum (LIFE-003) and the gases' hazard profiles (LIFE-004);
 *   <li>pressure and temperature, and the protection of its worn items and its own life support
 *       (LIFE-020, LIFE-025, LIFE-026, API-022);
 *   <li>vanilla drowning while its eyes are in water (LIFE-022), and the creative and spectator
 *       exemption (LIFE-037).
 * </ul>
 *
 * <p><b>Not yet built.</b> ZAS³ applies who breathes, the atmosphere at the eyes, the O₂ minimum,
 * the gases' hazard profiles ({@link Hazard#TOXIC_GAS}), the pressure and temperature hazards for
 * every living entity but those in {@code zas:atmosphere_immune} ({@link Protection#IMMUNE}),
 * vanilla's rule in water, the creative and spectator exemption, and the pressure and thermal
 * protection of worn items and of the entity itself, from the life-support capability ({@link
 * io.github.west3436.zas.api.capability.ILifeSupport}) or data (LIFE-020, LIFE-025, LIFE-026).
 * Internals (LIFE-021) and gas filtering are still to come: until then no verdict breathes from
 * {@link BreathSource#INTERNALS} or lists {@link Protection#SEALED_BREATHING} or {@link
 * Protection#GAS_FILTERING}. On the client, ZAS³ answers for the local player with the verdict the
 * server last synced (LIFE-023), without its breath gas, and for other entities with {@link
 * #vanilla vanilla's verdict}.
 *
 * <p><b>Without ZAS³</b> the answer is {@link #vanilla vanilla's verdict} (API-009, D-17): in air
 * an entity breathes, and with its eyes in water it drowns unless vanilla lets it breathe there.
 *
 * <p><b>Threads.</b> Call it on the main thread of the entity's logical side
 * ({@link LevelThread}): the server thread for an entity in a server level, where any other thread
 * is refused with {@link IllegalStateException}, or the client thread for an entity in a client
 * level. The answer reads the entity's live state, such as its position, effects and equipment,
 * which only that thread may read safely. The server's answer is the one that counts: the client
 * may know less than the server, so use a client-side answer for display only. The verdict itself
 * is an immutable value that may be passed to any thread.
 */
public final class Breathing {
    /**
     * LIFE-003's default O₂ minimum, in kPa: the O₂ partial pressure of the breath below which a
     * breathing entity runs out of air, as in vgstation13. A server may configure another ({@link
     * #oxygenMinimum()}).
     */
    public static final double DEFAULT_OXYGEN_MINIMUM = 16.0;

    private static final Breathability IN_AIR = Breathability.of(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE);
    private static final Breathability IN_SAFE_FLUID =
            Breathability.of(BreathReason.FLUID_BREATHING, BreathSource.FLUID);
    private static final Breathability EXEMPT_IN_FLUID = Breathability.of(BreathReason.EXEMPT, BreathSource.FLUID);
    private static final Breathability DROWNING = Breathability.of(BreathReason.DROWNING, BreathSource.FLUID);

    private Breathing() {}

    /**
     * Whether an entity can breathe where it is, and whether it is protected.
     *
     * <p>Thread: the main thread of the entity's logical side.
     *
     * @param entity the entity, server or client side
     * @return the verdict, an immutable value
     * @throws IllegalStateException if the entity is in a server level and this isn't its server's
     *     thread
     */
    @LevelThread
    public static Breathability of(LivingEntity entity) {
        requireThread(entity, "Breathing.of");
        return ZasApi.provider().breathability(entity);
    }

    /**
     * Vanilla's verdict alone, which never looks at the atmosphere. It is what the API answers
     * without ZAS³ (API-009), and the rule ZAS³ keeps while an entity's eyes are in water
     * (LIFE-022).
     *
     * <p>It follows NeoForge's breathing hook, the verdict {@code LivingBreatheEvent} starts with:
     *
     * <ul>
     *   <li>eyes in air, or in a bubble column: {@link BreathReason#BREATHABLE}, from the
     *       {@link BreathSource#ATMOSPHERE};
     *   <li>eyes in a fluid it doesn't drown in, because it has Water Breathing or Conduit Power,
     *       its type can breathe under water, or nothing drowns in that fluid:
     *       {@link BreathReason#FLUID_BREATHING}, from the {@link BreathSource#FLUID};
     *   <li>eyes in a fluid, as a player whose abilities make it invulnerable (creative or
     *       spectator mode): {@link BreathReason#EXEMPT}, from the fluid;
     *   <li>otherwise, eyes in a fluid: {@link BreathReason#DROWNING}, from the fluid.
     * </ul>
     *
     * <p>It carries no breath gas, no hazards and no protection: nothing in vanilla's air harms an
     * entity, so it is always {@linkplain Breathability#isProtected() protected}. The fluid at the
     * eyes is the one the entity saw on its last tick, as in vanilla. A water animal drying out on
     * land, such as a fish, is a separate vanilla rule that this verdict doesn't report.
     *
     * <p>Thread: the main thread of the entity's logical side.
     *
     * @param entity the entity, server or client side
     * @return vanilla's verdict
     * @throws IllegalStateException if the entity is in a server level and this isn't its server's
     *     thread
     */
    @LevelThread
    public static Breathability vanilla(LivingEntity entity) {
        requireThread(entity, "Breathing.vanilla");
        FluidType eyes = entity.getEyeInFluidType();
        if (eyes.isAir() || inBubbleColumn(entity)) {
            return IN_AIR;
        }
        if (MobEffectUtil.hasWaterBreathing(entity) || !entity.canDrownInFluidType(eyes)) {
            return IN_SAFE_FLUID;
        }
        if (entity instanceof Player player && player.getAbilities().invulnerable) {
            return EXEMPT_IN_FLUID;
        }
        return DROWNING;
    }

    /**
     * The O₂ partial pressure, in kPa, below which a breathing entity runs out of air (LIFE-003).
     * Air whose O₂ partial pressure is at or above it is breathable as far as O₂ goes, so a device
     * that wants to know whether a point's air is breathable compares the point's O₂ partial
     * pressure ({@link io.github.west3436.zas.api.atmosphere.AtmosphereSample#partialPressures()})
     * with it; a gas's hazard profile may still make that air harmful.
     *
     * <p>With ZAS³ it is the server's configured minimum, {@value #DEFAULT_OXYGEN_MINIMUM} kPa by
     * default; without ZAS³ it is that default.
     *
     * <p>Thread: any. On a client, it is the default, not the server's setting.
     *
     * @return the minimum in kPa
     */
    @AnyThread
    public static double oxygenMinimum() {
        return ZasApi.provider().oxygenMinimum();
    }

    /** Whether the block at the entity's eyes is a bubble column, which vanilla counts as air. */
    private static boolean inBubbleColumn(LivingEntity entity) {
        Level level = entity.level();
        BlockPos eyes = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        return level.isLoaded(eyes) && level.getBlockState(eyes).is(Blocks.BUBBLE_COLUMN);
    }

    private static void requireThread(LivingEntity entity, String operation) {
        Objects.requireNonNull(entity, "entity");
        if (entity.level() instanceof ServerLevel level) {
            ServerThreads.require(level, operation);
        }
    }
}
