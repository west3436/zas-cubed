package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The content mod's items, block items included. Add new ones to {@link AtmosCreativeTab} too. */
public final class AtmosItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ZasAtmosMod.MOD_ID);

    public static final DeferredItem<PipeItem> PIPE =
            ITEMS.registerItem("pipe", p -> new PipeItem(AtmosBlocks.PIPE.get(), p));
    public static final DeferredItem<PipeItem> PIPE_CAP =
            ITEMS.registerItem("pipe_cap", p -> new PipeItem(AtmosBlocks.PIPE_CAP.get(), p));

    private AtmosItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
