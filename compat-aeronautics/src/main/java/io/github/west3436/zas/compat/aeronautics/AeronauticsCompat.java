package io.github.west3436.zas.compat.aeronautics;

import io.github.west3436.zas.api.environment.AmbientSeeds;
import io.github.west3436.zas.compat.aeronautics.mixin.AeronauticsMixinPlugin;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Where the Create Aeronautics adapter starts (COMPAT-080 to COMPAT-083). {@code zas}'s adapter
 * loader calls {@link #init} by name, and only with Aeronautics installed in a version its row
 * supports (COMPAT-001, COMPAT-002); otherwise this class is never loaded, and nothing of the
 * adapter registers. The hook into Sable is a mixin, which its plugin applies under the same rule
 * ({@link AeronauticsMixinPlugin}).
 *
 * <p>Nothing here needs the content mod, so all of it works with the content off (CONT-155).
 */
public final class AeronauticsCompat {
    private static volatile boolean active;

    private AeronauticsCompat() {}

    /**
     * Starts the adapter. It only registers; nothing here calls into Sable or Aeronautics, which may
     * not be constructed yet.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        AeroSettings.register(container);

        // Ambients from Sable's dimension_physics, where nothing of ZAS³'s gives one (COMPAT-082).
        AmbientSeeds.register(SableSeed.ID, SableSeed::seed);

        // The factors (COMPAT-080, COMPAT-083): worked out after the atmos scheduler, at the end of
        // each server tick, and sent to clients at once (COMPAT-081).
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, (ServerTickEvent.Post e) -> LiftFactors.serverTicked(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                LiftFactors.joined(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> LiftFactors.serverStopped());
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .optional()
                .playToClient(LiftFactorsPayload.TYPE, LiftFactorsPayload.STREAM_CODEC, LiftFactors::received));
        if (FMLEnvironment.dist.isClient()) {
            LiftClient.register();
        }

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
