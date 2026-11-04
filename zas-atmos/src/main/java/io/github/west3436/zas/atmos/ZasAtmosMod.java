package io.github.west3436.zas.atmos;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.failure.CanisterFailure;
import io.github.west3436.zas.atmos.gear.GearMaterials;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosCapabilities;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosCreativeTab;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * ZAS³ Atmospherics: the SS13-style content mod, built on the public API (D-50). It compiles
 * against {@code zas-api} only (CONT-095); the design is in architecture §11.
 */
@Mod(ZasAtmosMod.MOD_ID)
public final class ZasAtmosMod {
    public static final String MOD_ID = "zas_atmos";

    public ZasAtmosMod(IEventBus modBus, ModContainer container) {
        AtmosBlocks.register(modBus);
        GearMaterials.register(modBus);
        AtmosItems.register(modBus);
        AtmosComponents.register(modBus);
        AtmosBlockEntities.register(modBus);
        AtmosEntities.register(modBus);
        AtmosMenus.register(modBus);
        AtmosCreativeTab.register(modBus);
        AtmosCapabilities.register(modBus);
        AtmosServerConfig.register(container);
        DeviceTicker.register(NeoForge.EVENT_BUS);
        CanisterFailure.register(NeoForge.EVENT_BUS);
    }

    /** An id in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
