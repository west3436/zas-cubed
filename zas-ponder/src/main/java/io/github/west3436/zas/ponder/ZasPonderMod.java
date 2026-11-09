package io.github.west3436.zas.ponder;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * The Ponder scenes for ZAS³ Atmospherics (CLIENT-023), in an addon of their own that nests Ponder and
 * Flywheel (D-83). It is a client mod: on a dedicated server it has no entry point, so none of its code
 * runs there (CLIENT-025).
 */
@Mod(value = ZasPonderMod.MOD_ID, dist = Dist.CLIENT)
public final class ZasPonderMod {
    public static final String MOD_ID = "zas_ponder";

    public ZasPonderMod() {
        // Every scene shows zas_atmos's blocks, so with its content off there is nothing to show (CONT-155).
        if (ZasAtmosMod.contentEnabled()) {
            PonderIndex.addPlugin(new ZasPonderPlugin());
        }
    }

    /** An id in this addon's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
