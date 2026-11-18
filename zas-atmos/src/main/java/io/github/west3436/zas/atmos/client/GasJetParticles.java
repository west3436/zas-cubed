package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.device.ClientDeviceActivity;
import io.github.west3436.zas.atmos.particle.DeviceJets;
import io.github.west3436.zas.atmos.particle.GasJet;
import io.github.west3436.zas.atmos.particle.GasJetKind;
import io.github.west3436.zas.atmos.particle.JetEmitters;
import io.github.west3436.zas.atmos.particle.ParticleDensity;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlock;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentMode;
import java.util.SplittableRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Turns gas jets into particles (CLIENT-017): the {@link GasJet}s the server sends, and those it makes
 * itself for the vents and scrubbers it is told are working (CLIENT-019). They are vanilla's poof,
 * tinted grey in code, so there is no new texture and no gas's colour. {@link JetEmitters} decides how
 * many, and when; this only makes them, and tells it about the camera and the Particles setting.
 */
final class GasJetParticles {
    private static final JetEmitters EMITTERS = new JetEmitters(new SplittableRandom());

    /** What names a device's jet: its block, so a jet on a moving ship keeps its source. */
    private record Device(long pos) {}

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
        askForDevices(mc.level, camera);
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

    /**
     * Asks for a jet from each vent and scrubber in range that is working, for this game tick. Its way
     * comes from its block state, so a device whose block has gone, or that has just been switched off,
     * shows nothing whatever the table says. One on a Sable ship starts at its grille where the ship
     * shows it, the way the ship turns it, and is carried along as the ship moves (COMPAT-101).
     */
    private static void askForDevices(ClientLevel level, Vec3 camera) {
        ClientDeviceActivity.LOCAL.forEachHeard(level, camera, JetEmitters.RANGE, (at, activity, shown) -> {
            BlockState state = level.getBlockState(at);
            double amount;
            Direction facing;
            if (state.getBlock() instanceof VentBlock) {
                amount = DeviceJets.vent(state.getValue(VentMode.PROPERTY), activity);
                facing = state.getValue(VentBlock.FACING);
            } else if (state.getBlock() instanceof ScrubberBlock) {
                amount = DeviceJets.scrubber(state.getValue(ScrubberMode.PROPERTY), activity);
                facing = state.getValue(ScrubberBlock.FACING);
            } else {
                return;
            }
            if (amount != 0) {
                DeviceJets.Placed jet = DeviceJets.place(level, at, facing);
                EMITTERS.add(
                        new Device(at.asLong()),
                        GasJetKind.DEVICE,
                        jet.mouth().x,
                        jet.mouth().y,
                        jet.mouth().z,
                        jet.way().x,
                        jet.way().y,
                        jet.way().z,
                        amount);
            }
        });
    }
}
