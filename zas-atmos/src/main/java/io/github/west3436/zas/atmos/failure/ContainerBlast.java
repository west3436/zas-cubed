package io.github.west3436.zas.atmos.failure;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.particle.GasJets;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
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
     * A vanilla explosion with vg's sounds in place of vanilla's (FIRE-037). It hurts entities under
     * either preset, and breaks blocks only where {@link Atmospheres#containerExplosionsBreakBlocks()}
     * says so.
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
                Explosion.getDefaultDamageSource(level, source),
                null,
                at.x,
                at.y,
                at.z,
                (float) power,
                false,
                Atmospheres.containerExplosionsBreakBlocks()
                        ? Level.ExplosionInteraction.BLOCK
                        : Level.ExplosionInteraction.NONE,
                ParticleTypes.EXPLOSION,
                ParticleTypes.EXPLOSION_EMITTER,
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY));
        playBlast(level, at, power);
    }

    /**
     * Sends each player in the level the one sound it hears of a blast. The packets go out player by
     * player because {@code level.playSound} sends one sound to everyone in its range, and a near
     * listener would then hear the far sound over the near one. Everyone gets the same seed, so
     * everyone hears the same one of the near files.
     */
    private static void playBlast(ServerLevel level, Vec3 at, double power) {
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            BlastSound sound = BlastSound.heard(power, Math.sqrt(player.distanceToSqr(at)));
            if (sound != null) {
                player.connection.send(new ClientboundSoundPacket(
                        event(sound), SoundSource.BLOCKS, at.x, at.y, at.z, sound.volume(), 1, seed));
            }
        }
    }

    private static Holder<SoundEvent> event(BlastSound sound) {
        return switch (sound) {
            case NEAR -> AtmosSounds.BLAST_NEAR;
            case SMALL -> AtmosSounds.BLAST_SMALL;
            case FAR -> AtmosSounds.BLAST_FAR;
            case SMALL_FAR -> AtmosSounds.BLAST_SMALL_FAR;
        };
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
