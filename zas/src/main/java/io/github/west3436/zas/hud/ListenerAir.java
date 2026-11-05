package io.github.west3436.zas.hud;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.sound.ThinAir;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Sends each player the pressure around it, for how much of this mod's sounds it hears (CLIENT-016,
 * DATA-019).
 *
 * <p>The HUD's state can't serve for this. Its pressure is the one that reaches the player through
 * its protection (LIFE-020), and it has none without an alert, so a sealed suit in a vacuum, and
 * anyone in creative, would read as normal air. A sound is heard or not by the air outside: this is
 * the real pressure at the eyes, whatever the player wears or its game mode.
 *
 * <p>Every {@value #INTERVAL_TICKS} game ticks, staggered by entity id, the server reads it and sends
 * it to that player alone if it changed: at most 4 times a second. It is whole kPa capped at {@value
 * ThinAir#FULL_KPA}, so normal air, from there to any high pressure, sends nothing after the first.
 * The open air reads as its dimension's ambient: normal air in a breathable dimension, and a vacuum in
 * an airless one. While the atmosphere at the eyes is unresolved the last reading stands. A new player
 * object, as after a respawn, starts with nothing sent, so its first reading goes at once. Fake players
 * get nothing.
 */
public final class ListenerAir {
    /** Game ticks between a player's readings: 4 a second at most. */
    public static final int INTERVAL_TICKS = 5;

    /** A reading that says nothing: the atmosphere is unresolved. */
    static final int NO_READING = -1;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ZasMod.MOD_ID);

    /** What a player was last sent; not saved. */
    private static final Supplier<AttachmentType<Sent>> SENT = ATTACHMENTS.register(
            "listener_air", () -> AttachmentType.builder(Sent::new).build());

    /** The reading last sent to a player, and when. */
    private static final class Sent {
        int kPa = NO_READING;
        long time = -1;
    }

    private ListenerAir() {}

    /** Registers the attachment, the payload and the tick listener. Called once, from the mod constructor. */
    public static void register(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(ListenerAir::registerPayloads);
        gameBus.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                long now = player.serverLevel().getGameTime();
                if (Math.floorMod(now + player.getId(), INTERVAL_TICKS) == 0) {
                    syncAt(player, now, payload -> send(player, payload));
                }
            }
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(ListenerAirPayload.TYPE, ListenerAirPayload.STREAM_CODEC, ClientListenerAir::handle);
    }

    /**
     * What a sample tells a client: whole kPa up to {@value ThinAir#FULL_KPA}, or {@link #NO_READING}
     * where the atmosphere is unresolved. An ambient sample has its dimension's real pressure.
     */
    public static int reading(AtmosphereSample sample) {
        return sample.state() == AtmosphereState.UNRESOLVED
                ? NO_READING
                : ThinAir.reading(sample.mixture().pressure());
    }

    /**
     * Reads the pressure around a player at game time {@code now} and passes it to {@code out} if it
     * is due: it differs from the last reading sent, and at least {@value #INTERVAL_TICKS} ticks have
     * passed since that one.
     *
     * @return whether a reading was passed on
     */
    public static boolean syncAt(Player player, long now, Consumer<ListenerAirPayload> out) {
        Sent sent = player.getData(SENT);
        if (sent.time >= 0 && now - sent.time < INTERVAL_TICKS) {
            return false;
        }
        int kPa = reading(Atmospheres.of(player));
        if (kPa == NO_READING || kPa == sent.kPa) {
            return false;
        }
        sent.kPa = kPa;
        sent.time = now;
        out.accept(new ListenerAirPayload(kPa));
        return true;
    }

    private static void send(ServerPlayer player, ListenerAirPayload payload) {
        if (player.connection.hasChannel(ListenerAirPayload.TYPE)) {
            player.connection.send(payload);
        }
    }
}
