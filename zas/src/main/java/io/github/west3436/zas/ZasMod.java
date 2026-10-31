package io.github.west3436.zas;

import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.command.ZasCommands;
import io.github.west3436.zas.config.ZasClientConfig;
import io.github.west3436.zas.config.ZasCommonConfig;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gas.GasRegistryEvents;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.provider.ZasApiProviderImpl;
import io.github.west3436.zas.simulation.AtmosService;
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
        Geometries.register(NeoForge.EVENT_BUS);
        ChangeTracker.register(NeoForge.EVENT_BUS);
        GasRegistryEvents.register(modBus);
        Ambients.register(NeoForge.EVENT_BUS);
        ZasCommonConfig.register(container);
        ZasClientConfig.register(container);
        AtmosService.register(NeoForge.EVENT_BUS);
        ZasCommands.register(NeoForge.EVENT_BUS);
    }
}
