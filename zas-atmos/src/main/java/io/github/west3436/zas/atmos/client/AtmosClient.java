package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The content mod's client setup: the device screens. Loaded on the client only. */
@EventBusSubscriber(modid = ZasAtmosMod.MOD_ID, value = Dist.CLIENT)
public final class AtmosClient {
    private AtmosClient() {}

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(AtmosMenus.AREA_AIR_CONTROLLER.get(), ControllerScreen::new);
        event.register(AtmosMenus.GENERATOR.get(), GeneratorScreen::new);
    }
}
