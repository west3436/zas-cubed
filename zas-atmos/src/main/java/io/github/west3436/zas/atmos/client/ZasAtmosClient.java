package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** The content mod's client entry point: block tints. */
@Mod(value = ZasAtmosMod.MOD_ID, dist = Dist.CLIENT)
public final class ZasAtmosClient {
    public ZasAtmosClient(IEventBus modBus) {
        modBus.addListener(ZasAtmosClient::blockColors);
    }

    /** Dyed pipes and caps are tinted with their dye's colour (CONT-114); tint index 0 is the whole pipe. */
    private static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> tintIndex == 0 && state.hasProperty(PipeColor.PROPERTY)
                        ? 0xFF000000 | state.getValue(PipeColor.PROPERTY).tint()
                        : -1,
                AtmosBlocks.PIPE.get(),
                AtmosBlocks.PIPE_CAP.get());
    }
}
