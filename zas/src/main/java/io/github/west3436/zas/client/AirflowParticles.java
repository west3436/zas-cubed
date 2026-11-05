package io.github.west3436.zas.client;

import io.github.west3436.zas.airflow.AirflowParticlePlan;
import io.github.west3436.zas.airflow.HeldFlows;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Starts the client's airflow particles each game tick (CLIENT-003), from what {@link
 * AirflowParticlePlan} works out: vanilla's white smoke sprite, tinted a neutral pale grey in code,
 * since which gas is flowing isn't shown (CLIENT-002 is R2).
 *
 * <p>It starts them through the particle engine, not the level, to tint each one. That skips the
 * level's own distance and Particles checks, which {@link AirflowParticlePlan} does instead.
 */
final class AirflowParticles {
    private static final Random RANDOM = new Random();

    /** The palest and darkest shades of grey a particle takes. */
    private static final float SHADE_MIN = 0.78f;

    private static final float SHADE_MAX = 0.94f;

    /** The shortest a particle lasts, game ticks, and how much longer it may. */
    private static final int LIFETIME_MIN = 8;

    private static final int LIFETIME_SPREAD = 9;

    private AirflowParticles() {}

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        HeldFlows.CLIENT.tick(level.dimension().location());
        double density = switch (mc.options.particles().get()) {
            case ALL -> AirflowParticlePlan.ALL;
            case DECREASED -> AirflowParticlePlan.DECREASED;
            case MINIMAL -> AirflowParticlePlan.MINIMAL;
        };
        Vec3 c = mc.player.getBoundingBox().getCenter();
        AirflowParticlePlan.plan(HeldFlows.CLIENT.flows(), c.x, c.y, c.z, density, RANDOM, (x, y, z, vx, vy, vz) -> {
            Particle particle = mc.particleEngine.createParticle(ParticleTypes.WHITE_SMOKE, x, y, z, vx, vy, vz);
            if (particle != null) {
                particle.setParticleSpeed(vx, vy, vz);
                float shade = SHADE_MIN + RANDOM.nextFloat() * (SHADE_MAX - SHADE_MIN);
                particle.setColor(shade, shade, shade);
                particle.setLifetime(LIFETIME_MIN + RANDOM.nextInt(LIFETIME_SPREAD));
            }
        });
    }
}
