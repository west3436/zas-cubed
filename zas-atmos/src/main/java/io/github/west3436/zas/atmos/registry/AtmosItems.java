package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterItem;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import net.minecraft.world.item.BlockItem;
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
    public static final DeferredItem<BlockItem> CONNECTOR_PORT =
            ITEMS.registerSimpleBlockItem(AtmosBlocks.CONNECTOR_PORT);
    public static final DeferredItem<CanisterItem> CANISTER =
            ITEMS.registerItem("canister", p -> new CanisterItem(AtmosBlocks.CANISTER.get(), p));
    public static final DeferredItem<BlockItem> VENT = ITEMS.registerSimpleBlockItem(AtmosBlocks.VENT);

    private AtmosItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
