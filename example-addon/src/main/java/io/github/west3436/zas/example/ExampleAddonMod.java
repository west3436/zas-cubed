package io.github.west3436.zas.example;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * An example addon built on the ZAS³ API alone (API-048). It runs the same
 * way whether or not ZAS³ is installed: without it, the API's fallback answers
 * with standard air and vanilla's breathing rules (D-17).
 */
@Mod(ExampleAddonMod.MOD_ID)
public final class ExampleAddonMod {
    public static final String MOD_ID = "zas_example";

    private static final Logger LOGGER = LogUtils.getLogger();

    public ExampleAddonMod(IEventBus modBus) {
        modBus.addListener(ExampleAddonMod::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(ExampleAddonMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ExampleAddonMod::onPlayerLoggedIn);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // Safe whether or not zas is installed (API-040).
        LOGGER.info("ZAS³ API present: {}", ZasApi.isPresent());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        // Queries may be called from any thread; this is the server thread.
        ServerLevel overworld = event.getServer().overworld();
        AtmosphereSample air = Atmospheres.at(overworld, BlockPos.ZERO);
        LOGGER.info(
                "Atmosphere at the overworld origin: {} at {} kPa and {} K",
                air.state(),
                String.format("%.3f", air.pressure()),
                String.format("%.2f", air.temperature()));
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // The breathability helper (API-014) reads the player's live state, so
        // it runs on the thread that owns the player: here the server thread.
        Breathability breath = Breathing.of(event.getEntity());
        LOGGER.info(
                "{} can breathe: {} ({}, from {}); protected: {}",
                event.getEntity().getName().getString(),
                breath.canBreathe(),
                breath.reason(),
                breath.source(),
                breath.isProtected());
    }
}
