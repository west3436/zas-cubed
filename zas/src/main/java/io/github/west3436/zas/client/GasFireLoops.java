package io.github.west3436.zas.client;

import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.api.client.LoopRequest;
import io.github.west3436.zas.hotspot.GasFireScan;
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
 * <p>Client thread only.
 */
final class GasFireLoops {
    private static final FireLoopGroups GROUPS = new FireLoopGroups();
    private static int wait;

    private GasFireLoops() {}

    /** One client tick. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || --wait > 0) {
            return;
        }
        wait = FireLoopGroups.SCAN_TICKS;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 ear = camera.isInitialized() || mc.player == null ? camera.getPosition() : mc.player.getEyePosition();
        GasFireScan.forEach(level, ear.x, ear.y, ear.z, FireLoopGroups.RANGE, GROUPS::add);
        for (FireLoopGroups.Group g : GROUPS.choose(ear.x, ear.y, ear.z)) {
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
}
