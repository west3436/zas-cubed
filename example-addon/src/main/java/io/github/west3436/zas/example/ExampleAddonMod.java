package io.github.west3436.zas.example;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.reaction.Reactions;
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
 * An example addon built on the ZAS³ API alone, using each of its extension points (API-047). It
 * runs the same way whether or not ZAS³ is installed (API-048): without it, the API's fallback
 * answers with standard air and vanilla's breathing rules, and nothing calls what's registered
 * here (D-17).
 *
 * <p>The archived API guide lists the extension points and which class here shows each:
 * https://github.com/west3436/zas-cubed/blob/f6931d5b91f839b3835fc6c81b1e69c9e09070e3/docs/api-consumers.md#the-example-addon
 */
@Mod(ExampleAddonMod.MOD_ID)
public final class ExampleAddonMod {
    public static final String MOD_ID = "zas_example";

    private static final Logger LOGGER = LogUtils.getLogger();

    public ExampleAddonMod(IEventBus modBus) {
        ExampleContent.register(modBus);
        modBus.addListener(ExampleCapabilities::register);
        modBus.addListener(ExampleAddonMod::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(ExampleAddonMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ExampleAddonMod::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ExampleHazards::onHazardTick);
        NeoForge.EVENT_BUS.register(AtmosphereAlarm.class);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // Safe whether or not zas is installed (API-040).
        LOGGER.info("ZAS³ API present: {}", ZasApi.isPresent());
        // These take effect from any thread, and before datapacks load, as a reaction type must.
        Reactions.registerType(CatalyticConverter.ID, CatalyticConverter.TYPE);
        OutpostAir.register();
        ExampleHazards.register();
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
