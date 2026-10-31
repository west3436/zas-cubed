package io.github.west3436.zas.gas;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Wires gas definition loading and client sync into NeoForge (CORE-010, CORE-014). */
public final class GasRegistryEvents {
    private GasRegistryEvents() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(GasRegistryEvents::registerPayloads);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new GasDefinitionLoader()));
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> ServerGasRegistry.serverStarting());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ServerGasRegistry.serverStopped());
        NeoForge.EVENT_BUS.addListener(GasRegistryEvents::sync);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(
                        GasDefinitionsPayload.TYPE, GasDefinitionsPayload.STREAM_CODEC, ClientGasRegistry::handle);
    }

    /** Fires for a joining player, and for everyone after {@code /reload}. */
    private static void sync(OnDatapackSyncEvent event) {
        GasDefinitionsPayload payload = GasDefinitionsPayload.of(ServerGasRegistry.current());
        event.getRelevantPlayers().forEach(player -> send(player, payload));
    }

    private static void send(ServerPlayer player, GasDefinitionsPayload payload) {
        // Every real client has the channel (zas is required on both sides);
        // GameTest mock players don't.
        if (player.connection.hasChannel(GasDefinitionsPayload.TYPE)) {
            player.connection.send(payload);
        }
    }
}
