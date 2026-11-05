package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.api.client.LoopRequest;
import io.github.west3436.zas.atmos.firealarm.FireAlarmVoice;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays what {@link FireAlarmVoice#LOCAL} says (CONT-128), as one loop with one source, so all the
 * alarms a client hears are one sound. Alarms report during the level's tick, and this runs after it.
 * While the game is paused neither happens. Client thread only.
 */
final class FireAlarmSounds {
    /** Who wants the sound: every alarm, as one. */
    private static final Object SOURCE = "zas_atmos:fire_alarm";

    private FireAlarmSounds() {}

    /** Registers the listener. Called once, from the client entry point. */
    static void register(IEventBus gameBus) {
        gameBus.addListener((ClientTickEvent.Post e) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        FireAlarmVoice.Request request = FireAlarmVoice.LOCAL.tick(ear.x, ear.y, ear.z);
        if (request != null) {
            ClientSounds.loop(new LoopRequest(
                    SOURCE,
                    AtmosSounds.FIRE_ALARM_ALARM.get(),
                    SoundSource.BLOCKS,
                    request.x(),
                    request.y(),
                    request.z(),
                    request.volume(),
                    1f,
                    FireAlarmVoice.RANGE));
        }
    }
}
