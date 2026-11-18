package io.github.west3436.zas.client;

import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.api.client.LoopRequest;
import io.github.west3436.zas.hotspot.GasFireScan;
import io.github.west3436.zas.provider.Frame;
import io.github.west3436.zas.provider.Frames;
import io.github.west3436.zas.sound.FireLoopGroups;
import io.github.west3436.zas.sound.ZasSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Asks for the gas fire loops around the listener (FIRE-036): the thin shell between the blocks ({@link
 * GasFireScan}), their grouping ({@link FireLoopGroups}) and the client's loops ({@link ClientSounds}).
 *
 * <p>Each round looks at the blocks as they are, so a fire that has gone out or a chunk that has left
 * asks for nothing, and its loop stops when its lease lapses. Nothing here turns a loop off.
 *
 * <p>A fire on blocks another mod shows somewhere else, as in a Sable ship's cabin, is found where
 * its blocks are kept and plays where the world shows them (COMPAT-101). Between looks its loops are
 * placed again each tick, so they keep up with the ship; a fire on the ground is asked for at each
 * look only, as it always was.
 *
 * <p>Client thread only.
 */
final class GasFireLoops {
    private static final FireLoopGroups GROUPS = new FireLoopGroups();
    private static int wait;

    /** The level of the last look: what was chosen there is followed in no other. */
    private static ClientLevel looked;

    private GasFireLoops() {}

    /** One client tick. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            looked = null; // so a level left isn't kept
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Frame.Lookup frames = Frames.lookup(level);
        if (--wait > 0) {
            if (level == looked) {
                for (FireLoopGroups.Group g : GROUPS.follow(frames)) {
                    ask(g);
                }
            }
            return;
        }
        wait = FireLoopGroups.SCAN_TICKS;
        looked = level;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 ear = camera.isInitialized() || mc.player == null ? camera.getPosition() : mc.player.getEyePosition();
        GasFireScan.around(level, ear.x, ear.y, ear.z, FireLoopGroups.RANGE, GROUPS::add);
        for (FireLoopGroups.Group g : GROUPS.choose(ear.x, ear.y, ear.z, frames)) {
            ask(g);
        }
    }

    private static void ask(FireLoopGroups.Group g) {
        ClientSounds.loop(new LoopRequest(
                g.source(),
                ZasSounds.FIRE_LOOP.get(),
                SoundSource.BLOCKS,
                g.x(),
                g.y(),
                g.z(),
                g.volume(),
                g.pitch(),
                FireLoopGroups.RANGE));
    }
}
