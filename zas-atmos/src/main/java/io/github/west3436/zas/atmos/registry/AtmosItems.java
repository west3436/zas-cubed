package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The content mod's items, block items included. Add new ones to {@link AtmosCreativeTab} too. */
public final class AtmosItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ZasAtmosMod.MOD_ID);

    public static final DeferredItem<BlockItem> PIPE = ITEMS.registerSimpleBlockItem(AtmosBlocks.PIPE);
    public static final DeferredItem<BlockItem> PIPE_CAP = ITEMS.registerSimpleBlockItem(AtmosBlocks.PIPE_CAP);

    private AtmosItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
