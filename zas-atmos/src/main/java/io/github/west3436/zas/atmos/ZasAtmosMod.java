package io.github.west3436.zas.atmos;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.config.AtmosStartupConfig;
import io.github.west3436.zas.atmos.controller.CentralChunks;
import io.github.west3436.zas.atmos.device.DeviceActivitySync;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.failure.CanisterFailure;
import io.github.west3436.zas.atmos.gear.Encumbrance;
import io.github.west3436.zas.atmos.gear.GearMaterials;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosCapabilities;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosCreativeTab;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

/**
 * ZAS³ Atmospherics: the SS13-style content mod, built on the public API (D-50). It compiles
 * against {@code zas-api} only (CONT-095); the design is in architecture §11. It ships in the
 * framework's jar (D-88), and its startup config can turn it off (CONT-155).
 */
@Mod(ZasAtmosMod.MOD_ID)
public final class ZasAtmosMod {
    public static final String MOD_ID = "zas_atmos";

    /** The built-in data pack in the mod's resources that holds the content's data (CONT-155). */
    public static final String CONTENT_PACK = "datapacks/content";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean contentEnabled = true;

    public ZasAtmosMod(IEventBus modBus, ModContainer container) {
        contentEnabled = AtmosStartupConfig.register(container);
        if (!contentEnabled) {
            LOGGER.info("ZAS³ Atmospherics' content is off in zas_atmos-startup.toml, so it loads nothing");
            return;
        }
        AtmosBlocks.register(modBus);
        GearMaterials.register(modBus);
        AtmosItems.register(modBus);
        AtmosComponents.register(modBus);
        AtmosBlockEntities.register(modBus);
        AtmosEntities.register(modBus);
        AtmosMenus.register(modBus);
        AtmosCreativeTab.register(modBus);
        AtmosCapabilities.register(modBus);
        AtmosSounds.register(modBus);
        AtmosServerConfig.register(container);
        CentralChunks.register(modBus);
        modBus.addListener(ZasAtmosMod::addContentPack);
        DeviceTicker.register(NeoForge.EVENT_BUS);
        CanisterFailure.register(NeoForge.EVENT_BUS);
        DeviceActivitySync.register(NeoForge.EVENT_BUS);
        Encumbrance.register(NeoForge.EVENT_BUS);
    }

    /**
     * Whether the content loads: the startup config's {@code [content] enabled}, read once when the mod
     * is built (CONT-155). Off, the mod registers nothing and its data pack isn't offered.
     */
    public static boolean contentEnabled() {
        return contentEnabled;
    }

    /** An id in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * The content's recipes, loot, tags and thermal data are in a built-in data pack, not the mod's own
     * data, because the framework shares the jar and its data loads either way (CONT-155). The pack is
     * always on and starts at the bottom, so any other data pack can override it.
     */
    private static void addContentPack(AddPackFindersEvent event) {
        event.addPackFinders(
                id(CONTENT_PACK),
                PackType.SERVER_DATA,
                Component.translatable("pack.zas_atmos.content"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.BOTTOM);
    }
}
