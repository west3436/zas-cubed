package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Connected textures for the station walls and reinforced glass, when Fusion is installed (CONT-132).
 * A built-in resource pack, {@value #PACK}, gives each block's texture as Fusion's pieced layout: five
 * tiles in one 80 by 16 strip, with a {@code .png.mcmeta} that joins it to blocks of its own kind. The
 * pack is added, always on and above the mod's own assets, only when Fusion is loaded. Without Fusion
 * the game would squash a strip onto each face, so the blocks keep their own textures, which are the
 * strips' first tile. The pack holds textures only: the models stay vanilla's {@code cube_all}, which
 * Fusion joins as they are.
 */
public final class ConnectedTextures {
    /** Fusion's mod id. */
    public static final String FUSION = "fusion";

    /** The pack's folder in the mod's resources. */
    public static final String PACK = "resourcepacks/fusion_connected";

    private ConnectedTextures() {}

    static void addPack(AddPackFindersEvent event) {
        if (!ModList.get().isLoaded(FUSION)) {
            return;
        }
        event.addPackFinders(
                ZasAtmosMod.id(PACK),
                PackType.CLIENT_RESOURCES,
                Component.translatable("pack.zas_atmos.fusion_connected"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP);
    }
}
