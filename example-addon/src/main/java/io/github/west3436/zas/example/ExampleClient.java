package io.github.west3436.zas.example;

import io.github.west3436.zas.api.client.ClientSounds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * The addon's client entry point, loaded only on a physical client. Its sounds fade in thin air, as
 * ZAS³'s do (API-060), including those a server plays. Without ZAS³ this does nothing.
 */
@Mod(value = ExampleAddonMod.MOD_ID, dist = Dist.CLIENT)
public final class ExampleClient {
    public ExampleClient() {
        ClientSounds.fadeInThinAir(ExampleAddonMod.MOD_ID);
    }
}
