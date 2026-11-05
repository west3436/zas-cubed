package io.github.west3436.zas.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.airflow.AirflowReceiver;
import io.github.west3436.zas.airflow.HeldFlows;
import io.github.west3436.zas.debug.ClientDebugOverlay;
import io.github.west3436.zas.hud.ClientAtmosHud;
import io.github.west3436.zas.hud.ClientListenerAir;
import io.github.west3436.zas.life.ClientBreath;
import io.github.west3436.zas.life.Internals;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The client-only entry point of {@code zas}. FML constructs it on the physical client only, so a
 * dedicated server never loads this package or the client classes it uses (NFR-023).
 */
@Mod(value = ZasMod.MOD_ID, dist = Dist.CLIENT)
public final class ZasClient {
    /** The key that toggles the player's internals (LIFE-029): I by default. */
    public static final KeyMapping TOGGLE_INTERNALS = new KeyMapping(
            "key.zas.toggle_internals",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.zas");

    public ZasClient(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(TOGGLE_INTERNALS));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (TOGGLE_INTERNALS.consumeClick()) {
                if (Minecraft.getInstance().getConnection() != null) {
                    PacketDistributor.sendToServer(Internals.TogglePayload.INSTANCE);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> DebugOverlayRenderer.render(e));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientDebugOverlay.clear();
            AirflowReceiver.CLIENT.clear();
            ClientAtmosHud.clear();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientBreath.LOCAL.clear());
        modBus.addListener(AtmosHudRenderer::registerLayer);

        // Looping sounds and thin air (CLIENT-015, CLIENT-016).
        ClientLoops.register();
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientListenerAir.clear());

        // Airflow's particles (CLIENT-003).
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> AirflowParticles.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> HeldFlows.CLIENT.clear());
    }
}
