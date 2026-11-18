package io.github.west3436.zas.compat.cosmonautics;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Where the Create Cosmonautics adapter starts (SPACE-012). {@code zas}'s adapter loader calls
 * {@link #init} by name, and only with Cosmonautics installed in a version its row supports
 * (COMPAT-001, COMPAT-002); otherwise this class is never loaded, and nothing of the adapter
 * registers.
 *
 * <p>Nothing here needs the content mod, so all of it works with the content off (CONT-155).
 */
public final class CosmonauticsCompat {
    /** Cosmonautics' mod id, which its code still calls by its first name. */
    public static final String MOD_ID = "rocketnautics";

    private static volatile boolean active;

    private CosmonauticsCompat() {}

    /**
     * Starts the adapter. It only registers listeners; nothing here names Cosmonautics.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        // After Cosmonautics' own listener, which runs at HIGHEST, and before ZAS³'s, at NORMAL.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingBreatheEvent.class, ZoneBreath::onBreathe);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ZoneBreath.left(player);
            }
        });
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .optional()
                .playToClient(ZoneBreathPayload.TYPE, ZoneBreathPayload.STREAM_CODEC, ZoneBreath::received));
        active = true;
    }

    /**
     * Whether the adapter has started.
     *
     * @return true once {@link #init} has run
     */
    public static boolean isActive() {
        return active;
    }
}
