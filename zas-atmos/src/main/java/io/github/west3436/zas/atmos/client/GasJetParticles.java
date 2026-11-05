package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.particle.GasJet;
import io.github.west3436.zas.atmos.particle.JetEmitters;
import io.github.west3436.zas.atmos.particle.ParticleDensity;
import java.util.SplittableRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Turns the {@link GasJet}s the server sends into particles (CLIENT-017): vanilla's poof, tinted grey
 * in code, so there is no new texture and no gas's colour. {@link JetEmitters} decides how many, and
 * when; this only makes them, and tells it about the camera and the Particles setting.
 */
final class GasJetParticles {
    private static final JetEmitters EMITTERS = new JetEmitters(new SplittableRandom());

    /** The level the jets are in: another level, or none, ends them. */
    private static ClientLevel showingIn;

    private GasJetParticles() {}

    /** Starts taking jets, and listens for the client's ticks, on the game's event bus. */
    static void register(IEventBus gameBus) {
        GasJet.receiver = GasJetParticles::receive;
        gameBus.addListener(GasJetParticles::tick);
    }

    private static void receive(GasJet jet) {
        EMITTERS.add(jet.kind(), jet.x(), jet.y(), jet.z(), jet.dx(), jet.dy(), jet.dz(), jet.amount());
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != showingIn) {
            EMITTERS.clear();
            showingIn = mc.level;
        }
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        ParticleDensity density = switch (mc.options.particles().get()) {
            case ALL -> ParticleDensity.ALL;
            case DECREASED -> ParticleDensity.DECREASED;
            case MINIMAL -> ParticleDensity.MINIMAL;
        };
        EMITTERS.tick(camera.x, camera.y, camera.z, density, (x, y, z, vx, vy, vz, lifetime, shade, size) -> {
            Particle p = mc.particleEngine.createParticle(ParticleTypes.POOF, x, y, z, 0, 0, 0);
            if (p != null) {
                p.setParticleSpeed(vx, vy, vz);
                p.setLifetime(lifetime);
                p.setColor(shade, shade, shade);
                p.scale(size);
            }
        });
    }
}
