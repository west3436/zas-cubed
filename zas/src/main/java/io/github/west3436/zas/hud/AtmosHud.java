package io.github.west3436.zas.hud;

import io.github.west3436.zas.ZasMod;
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
import org.jetbrains.annotations.Nullable;

/**
 * Sends each player its own atmosphere HUD state (CLIENT-008, DATA-005, DATA-014).
 *
 * <p>Every {@value #INTERVAL_TICKS} game ticks, staggered by entity id, the server works out a
 * player's state ({@link HudLevels#of}) and sends it to that player alone if it {@linkplain
 * AtmosHudPayload#differsFrom differs} from the last one sent: so at most 4 times a second, and
 * only when a threshold is crossed or a shown value changes by its display resolution. In normal
 * air nothing is shown, so nothing is sent. While the atmosphere at the player's eyes is
 * unresolved the last state stands. A new player object, as after a respawn, starts with nothing
 * sent, so its first state goes at once. Fake players get nothing.
 */
public final class AtmosHud {
    /** Game ticks between a player's HUD updates: 4 a second at most (DATA-014). */
    public static final int INTERVAL_TICKS = 5;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ZasMod.MOD_ID);

    /** What a player was last sent; not saved. */
    private static final Supplier<AttachmentType<Sent>> SENT = ATTACHMENTS.register(
            "atmos_hud", () -> AttachmentType.builder(Sent::new).build());

    /** The state last sent to a player, and when. */
    private static final class Sent {
        @Nullable
        AtmosHudPayload state;

        long time = -1;
    }

    private AtmosHud() {}

    /** Registers the attachment, the payload and the tick listener. Called once, from the mod constructor. */
    public static void register(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(AtmosHud::registerPayloads);
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
        event.registrar("1").playToClient(AtmosHudPayload.TYPE, AtmosHudPayload.STREAM_CODEC, ClientAtmosHud::handle);
    }

    /**
     * Works out a player's HUD state at game time {@code now} and passes it to {@code out} if it is
     * due: it differs from the last state sent, and at least {@value #INTERVAL_TICKS} ticks have
     * passed since that one.
     *
     * @return the state passed on, or null if none was
     */
    public static @Nullable AtmosHudPayload syncAt(Player player, long now, Consumer<AtmosHudPayload> out) {
        Sent sent = player.getData(SENT);
        if (sent.time >= 0 && now - sent.time < INTERVAL_TICKS) {
            return null;
        }
        AtmosHudPayload state = HudLevels.of(player);
        if (state == null || !state.differsFrom(sent.state)) {
            return null;
        }
        sent.state = state;
        sent.time = now;
        out.accept(state);
        return state;
    }

    private static void send(ServerPlayer player, AtmosHudPayload payload) {
        if (player.connection.hasChannel(AtmosHudPayload.TYPE)) {
            player.connection.send(payload);
        }
    }
}
