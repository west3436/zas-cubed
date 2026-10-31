package io.github.west3436.zas;

import io.github.west3436.zas.gas.GasRegistryEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** The ZAS³ framework mod: engine, platform and the API implementation. */
@Mod(ZasMod.MOD_ID)
public final class ZasMod {
    public static final String MOD_ID = "zas";

    public ZasMod(IEventBus modBus) {
        GasRegistryEvents.register(modBus);
    }
}
