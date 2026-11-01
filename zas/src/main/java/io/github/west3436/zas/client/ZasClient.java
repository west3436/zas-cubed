package io.github.west3436.zas.client;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.debug.ClientDebugOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client-only entry point of {@code zas}. FML constructs it on the physical client only, so a
 * dedicated server never loads this package or the client classes it uses (NFR-023).
 */
@Mod(value = ZasMod.MOD_ID, dist = Dist.CLIENT)
public final class ZasClient {
    public ZasClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> DebugOverlayRenderer.render(e));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientDebugOverlay.clear());
    }
}
