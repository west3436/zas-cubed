package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterItem;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.tool.CrowbarItem;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
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
    public static final DeferredItem<BlockItem> AREA_AIR_CONTROLLER =
            ITEMS.registerSimpleBlockItem(AtmosBlocks.AREA_AIR_CONTROLLER);
    public static final DeferredItem<BlockItem> GENERATOR = ITEMS.registerSimpleBlockItem(AtmosBlocks.GENERATOR);

    public static final DeferredItem<DoubleHighBlockItem> FIRELOCK =
            ITEMS.registerItem("firelock", p -> new DoubleHighBlockItem(AtmosBlocks.FIRELOCK.get(), p));

    /** Forces firelocks open or shut (CONT-094). */
    public static final DeferredItem<CrowbarItem> CROWBAR = ITEMS.registerItem("crowbar", CrowbarItem::new);

    /** Links parts to an area air controller (CONT-042). */
    public static final DeferredItem<MultitoolItem> MULTITOOL = ITEMS.registerItem("multitool", MultitoolItem::new);

    private AtmosItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
