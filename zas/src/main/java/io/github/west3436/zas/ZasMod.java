package io.github.west3436.zas;

import io.github.west3436.zas.geometry.Geometries;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** The ZAS³ framework mod: engine, platform and the API implementation. */
@Mod(ZasMod.MOD_ID)
public final class ZasMod {
    public static final String MOD_ID = "zas";

    public ZasMod() {
        Geometries.register(NeoForge.EVENT_BUS);
    }
}
