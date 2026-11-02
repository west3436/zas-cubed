package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterItem;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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

    public static final DeferredItem<BlockItem> HEATER = ITEMS.registerSimpleBlockItem(AtmosBlocks.HEATER);

    /** Links parts to an area air controller (CONT-042). */
    public static final DeferredItem<MultitoolItem> MULTITOOL = ITEMS.registerItem("multitool", MultitoolItem::new);

    // Crafting components, after vg's stock parts (D-72). Every one is crafted from vanilla
    // materials, so the content they go into still is (CONT-051). The recipes and the progression
    // are in docs/crafting.md (CONT-058).

    public static final DeferredItem<Item> CABLE_COIL = ITEMS.registerSimpleItem("cable_coil");
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.registerSimpleItem("circuit_board");
    public static final DeferredItem<Item> ELECTRONICS = ITEMS.registerSimpleItem("electronics");
    public static final DeferredItem<Item> MACHINE_FRAME = ITEMS.registerSimpleItem("machine_frame");
    public static final DeferredItem<Item> CAPACITOR = ITEMS.registerSimpleItem("capacitor");
    public static final DeferredItem<Item> ADVANCED_CAPACITOR = ITEMS.registerSimpleItem("advanced_capacitor");
    public static final DeferredItem<Item> SUPER_CAPACITOR = ITEMS.registerSimpleItem("super_capacitor");
    public static final DeferredItem<Item> SCANNING_MODULE = ITEMS.registerSimpleItem("scanning_module");
    public static final DeferredItem<Item> MICRO_MANIPULATOR = ITEMS.registerSimpleItem("micro_manipulator");
    public static final DeferredItem<Item> MICRO_LASER = ITEMS.registerSimpleItem("micro_laser");
    public static final DeferredItem<Item> CONSOLE_SCREEN = ITEMS.registerSimpleItem("console_screen");
    public static final DeferredItem<Item> IGNITER_ASSEMBLY = ITEMS.registerSimpleItem("igniter_assembly");
    public static final DeferredItem<Item> TIMER_ASSEMBLY = ITEMS.registerSimpleItem("timer_assembly");
    public static final DeferredItem<Item> ANSIBLE_CRYSTAL = ITEMS.registerSimpleItem("ansible_crystal");
    public static final DeferredItem<Item> SUBSPACE_ANSIBLE = ITEMS.registerSimpleItem("subspace_ansible");
    public static final DeferredItem<Item> SUBSPACE_TRANSMITTER = ITEMS.registerSimpleItem("subspace_transmitter");
    public static final DeferredItem<Item> BLUESPACE_CRYSTAL = ITEMS.registerSimpleItem("bluespace_crystal");
    public static final DeferredItem<Item> PLASTEEL_SHEET = ITEMS.registerSimpleItem("plasteel_sheet");
    public static final DeferredItem<Item> REINFORCED_GLASS = ITEMS.registerSimpleItem("reinforced_glass");

    /** The crafting components, in the creative tab's order. */
    public static List<DeferredItem<Item>> components() {
        return List.of(
                CABLE_COIL,
                CIRCUIT_BOARD,
                ELECTRONICS,
                MACHINE_FRAME,
                CAPACITOR,
                ADVANCED_CAPACITOR,
                SUPER_CAPACITOR,
                SCANNING_MODULE,
                MICRO_MANIPULATOR,
                MICRO_LASER,
                CONSOLE_SCREEN,
                IGNITER_ASSEMBLY,
                TIMER_ASSEMBLY,
                ANSIBLE_CRYSTAL,
                SUBSPACE_ANSIBLE,
                SUBSPACE_TRANSMITTER,
                BLUESPACE_CRYSTAL,
                PLASTEEL_SHEET,
                REINFORCED_GLASS);
    }

    private AtmosItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
