package io.github.west3436.zas.client;

import io.github.west3436.zas.airflow.AirflowSoundPlan;
import io.github.west3436.zas.airflow.HeldFlows;
import io.github.west3436.zas.airflow.VisibleFlow;
import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.api.client.LoopRequest;
import io.github.west3436.zas.config.AirflowConfig;
import io.github.west3436.zas.sound.ZasSounds;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Asks for the client's airflow sounds each game tick (CLIENT-010), from what {@link
 * AirflowSoundPlan} works out. The loop manager does the rest: it fades them, applies thin air, so a
 * listener in a vacuum hears nothing, and stops each one when it isn't asked for again. So when a flow
 * ends, or the list lapses, this has nothing to turn off.
 */
final class AirflowSounds {
    private AirflowSounds() {}

    /** Call after {@link HeldFlows#tick}, so a list that has lapsed is already gone. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        List<VisibleFlow> flows = HeldFlows.CLIENT.flows();
        if (flows.isEmpty()) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 ear = camera.isInitialized() ? camera.getPosition() : mc.player.getEyePosition();
        for (AirflowSoundPlan.Loop loop :
                AirflowSoundPlan.loops(flows, ear.x, ear.y, ear.z, AirflowConfig.mobKpa(), AirflowConfig.heavyKpa())) {
            ClientSounds.loop(new LoopRequest(
                    loop.source(),
                    event(loop.sound()),
                    SoundSource.AMBIENT,
                    loop.x(),
                    loop.y(),
                    loop.z(),
                    loop.volume(),
                    loop.pitch(),
                    AirflowSoundPlan.RANGE));
        }
    }

    private static SoundEvent event(AirflowSoundPlan.Sound sound) {
        return switch (sound) {
            case HISS -> ZasSounds.AIRFLOW_HISS.get();
            case WIND_LIGHT -> ZasSounds.AIRFLOW_LIGHT.get();
            case WIND_MOB -> ZasSounds.AIRFLOW_MOB.get();
            case WIND_HEAVY -> ZasSounds.AIRFLOW_HEAVY.get();
        };
    }
}
