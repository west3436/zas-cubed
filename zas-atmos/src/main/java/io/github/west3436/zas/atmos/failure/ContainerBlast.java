package io.github.west3436.zas.atmos.failure;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.particle.GasJets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a failing container does to the world around it: its explosion (CONT-038, CONT-102), which
 * breaks blocks only if the preset says so (CONT-107), and the sounds of a leak and a burst.
 */
public final class ContainerBlast {
    private ContainerBlast() {}

    /** The most power a container's explosion can have now: the config's (CONT-109). */
    public static double powerCap() {
        return AtmosServerConfig.get(AtmosServerConfig.EXPLOSION_POWER_CAP);
    }

    /**
     * A vanilla explosion. It hurts entities under either preset, and breaks blocks only where
     * {@link Atmospheres#containerExplosionsBreakBlocks()} says so.
     *
     * @param level the level
     * @param at its centre
     * @param power its power, already capped
     * @param source the entity it comes from, or null. A vanilla explosion spares its source, so
     *     never pass the entity carrying the container: leave it null and the carrier is hurt too.
     */
    public static void explode(ServerLevel level, Vec3 at, double power, @Nullable Entity source) {
        level.explode(
                source,
                at.x,
                at.y,
                at.z,
                (float) power,
                Atmospheres.containerExplosionsBreakBlocks()
                        ? Level.ExplosionInteraction.BLOCK
                        : Level.ExplosionInteraction.NONE);
    }

    /** The hiss of a container losing gas. */
    public static void hiss(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.6f);
    }

    /**
     * The bang and burst of particles of a container bursting without exploding (CLIENT-018).
     *
     * @param level the level
     * @param at where it was
     * @param moles the moles it let out, which size the particles
     */
    public static void burst(ServerLevel level, Vec3 at, double moles) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.5f, 1.8f);
        GasJets.burst(level, at, moles);
    }
}
