package io.github.west3436.zas;

import io.github.west3436.zas.airflow.Airflow;
import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.change.TriggerStates;
import io.github.west3436.zas.command.ZasCommands;
import io.github.west3436.zas.config.ZasClientConfig;
import io.github.west3436.zas.config.ZasCommonConfig;
import io.github.west3436.zas.debug.DebugOverlay;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.fire.BlockIgnitions;
import io.github.west3436.zas.fire.Burners;
import io.github.west3436.zas.fire.FuelProfiles;
import io.github.west3436.zas.gas.GasRegistryEvents;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.hotspot.HotspotBlocks;
import io.github.west3436.zas.hud.AtmosHud;
import io.github.west3436.zas.ignition.IgnitionEvents;
import io.github.west3436.zas.ignition.IgnitionSources;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.Photosynthesis;
import io.github.west3436.zas.persistence.AtmosPersistence;
import io.github.west3436.zas.provider.ZasApiProviderImpl;
import io.github.west3436.zas.reaction.ServerReactions;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.StopWatchdog;
import io.github.west3436.zas.sound.ZasSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** The ZAS³ framework mod: engine, platform and the API implementation. */
@Mod(ZasMod.MOD_ID)
public final class ZasMod {
    public static final String MOD_ID = "zas";

    public ZasMod(IEventBus modBus, ModContainer container) {
        // Installed while constructing, so consumers ordered AFTER zas see it
        // in their own constructors (architecture §6.2).
        ZasApiProviderImpl.install(NeoForge.EVENT_BUS);
        HotspotBlocks.register(modBus);
        ZasSounds.register(modBus);
        Geometries.register(NeoForge.EVENT_BUS);
        ChangeTracker.register(NeoForge.EVENT_BUS);
        TriggerStates.register(NeoForge.EVENT_BUS);
        IgnitionSources.register(NeoForge.EVENT_BUS);
        IgnitionEvents.register(NeoForge.EVENT_BUS);
        GasRegistryEvents.register(modBus);
        ServerReactions.register(NeoForge.EVENT_BUS);
        Ambients.register(NeoForge.EVENT_BUS);
        ZasCommonConfig.register(container);
        ZasClientConfig.register(container);
        AtmosService.register(NeoForge.EVENT_BUS);
        StopWatchdog.register(NeoForge.EVENT_BUS);
        AtmosPersistence.register(NeoForge.EVENT_BUS);
        ZasCommands.register(NeoForge.EVENT_BUS);
        DebugOverlay.register(modBus);
        Breathers.register(modBus, NeoForge.EVENT_BUS);
        Airflow.register(modBus, NeoForge.EVENT_BUS);
        Internals.register(modBus);
        Photosynthesis.register(NeoForge.EVENT_BUS);
        AtmosHud.register(modBus, NeoForge.EVENT_BUS);
        FuelProfiles.register(NeoForge.EVENT_BUS);
        BlockIgnitions.register(NeoForge.EVENT_BUS);
        Burners.register(NeoForge.EVENT_BUS);
    }
}
