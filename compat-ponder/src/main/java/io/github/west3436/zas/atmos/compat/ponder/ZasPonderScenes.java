package io.github.west3436.zas.atmos.compat.ponder;

import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;

/**
 * The entry point of the Ponder scenes for ZAS³ Atmospherics (CLIENT-023). {@code zas_atmos}'s {@code
 * PonderGate} loads this class by name, on a client that has a Ponder the scenes support, and calls
 * {@link #init}. Nothing else in ZAS³ names this package, so its classes are loaded only with Ponder
 * installed (NFR-022).
 */
public final class ZasPonderScenes {
    /**
     * The namespace of the scenes' ids, tag, text keys and floors: the mod they are part of. Ponder
     * heads a mod's tags in its index with the name of the mod that has this id, so the heading reads
     * "ZAS³ Atmospherics" whether Ponder came with Create or with the addon. The addon as release
     * 1.0.0 shipped it registers its own copy under {@code zas_ponder}.
     */
    public static final String NAMESPACE = "zas_atmos";

    private ZasPonderScenes() {}

    /** Hands Ponder the plugin that registers the scenes. Called once, as the client sets up. */
    public static void init() {
        PonderIndex.addPlugin(new ZasPonderPlugin());
    }

    /** An id in the scenes' namespace. */
    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }
}
