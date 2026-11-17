package io.github.west3436.zas.compat.ae2;

import appeng.api.ids.AECreativeTabIds;
import appeng.api.upgrades.Upgrades;
import appeng.items.storage.BasicStorageCell;
import appeng.items.storage.StorageTier;
import appeng.items.tools.powered.PortableCellItem;
import appeng.menu.me.common.MEStorageMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * ME gas storage cells (COMPAT-075, COMPAT-076, COMPAT-077): a gas cell housing, a storage cell for
 * each of AE2's five sizes, and a portable cell for each. They are AE2's own fluid cells in all but
 * the type they hold: the same bytes, types, idle drain and upgrades, the same cell workbench, drive
 * and ME chest, and the portable cell opens the same window. A cell is a housing and one of AE2's
 * storage components, and comes apart into them again when empty.
 *
 * <p>The cells are AE2's {@code BasicStorageCell} and {@code PortableCellItem}, which are outside its
 * API package (COMPAT-003): AE2 offers no other way to make a cell its drive and cell workbench
 * treat as its own. The adapter's GameTests use each of them with the oldest and the newest AE2.
 *
 * <p>The items exist only with the adapter on, under the {@code zas} namespace.
 */
public final class GasCells {
    /** The gases one cell holds, as many as a fluid cell holds fluids. */
    public static final int TYPES = 18;

    /** The portable cell's screen colour until it is dyed: AE2's own. */
    private static final int SCREEN = 0x80CAFF;

    /** The housing a storage cell is made of. */
    public static final ResourceLocation HOUSING = id("gas_cell_housing");

    /** AE2's sizes, smallest first. */
    static final List<StorageTier> TIERS = List.of(
            StorageTier.SIZE_1K,
            StorageTier.SIZE_4K,
            StorageTier.SIZE_16K,
            StorageTier.SIZE_64K,
            StorageTier.SIZE_256K);

    private static final List<Item> CELLS = new ArrayList<>();
    private static final List<Item> PORTABLE = new ArrayList<>();

    private GasCells() {}

    /** A storage cell's id, such as {@code zas:gas_storage_cell_1k}. */
    public static ResourceLocation cell(StorageTier tier) {
        return id("gas_storage_cell_" + tier.namePrefix());
    }

    /** A portable cell's id, such as {@code zas:portable_gas_cell_1k}. */
    public static ResourceLocation portable(StorageTier tier) {
        return id("portable_gas_cell_" + tier.namePrefix());
    }

    /** The storage cells, smallest first; empty until items register. */
    public static List<Item> cells() {
        return List.copyOf(CELLS);
    }

    /** The portable cells, smallest first; empty until items register. */
    public static List<Item> portableCells() {
        return List.copyOf(PORTABLE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas", path);
    }

    /** Registers the items, at the item registry's turn. */
    static void register(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> {
            helper.register(HOUSING, new Item(new Item.Properties()));
            for (StorageTier tier : TIERS) {
                int kilobytes = tier.bytes() / 1024;
                // AE2's fluid cells: 8 bytes a type for each kilobyte, and the tier's idle drain.
                Item cell = new BasicStorageCell(
                        new Item.Properties().stacksTo(1),
                        tier.idleDrain(),
                        kilobytes,
                        8 * kilobytes,
                        TYPES,
                        GasKeyType.TYPE);
                helper.register(cell(tier), cell);
                CELLS.add(cell);
            }
            for (StorageTier tier : TIERS) {
                Item cell = new PortableCellItem(
                        GasKeyType.TYPE,
                        TYPES,
                        MEStorageMenu.PORTABLE_FLUID_CELL_TYPE,
                        tier,
                        new Item.Properties().stacksTo(1),
                        SCREEN);
                helper.register(portable(tier), cell);
                PORTABLE.add(cell);
            }
        });
    }

    /** The cards each cell takes, as AE2's fluid cells take them. At common setup, on the main thread. */
    static void upgrades() {
        String storage = "gui.ae2.StorageCells";
        String portable = "gui.ae2.PortableCells";
        Item inverter = card("inverter_card");
        Item equal = card("equal_distribution_card");
        Item voiding = card("void_card");
        Item energy = card("energy_card");
        for (Item cell : CELLS) {
            add(inverter, cell, 1, storage);
            add(equal, cell, 1, storage);
            add(voiding, cell, 1, storage);
        }
        for (Item cell : PORTABLE) {
            add(inverter, cell, 1, portable);
            add(equal, cell, 1, portable);
            add(voiding, cell, 1, portable);
            add(energy, cell, 2, portable);
        }
    }

    private static Item card(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", path));
    }

    private static void add(Item card, Item cell, int count, String group) {
        // A card this AE2 doesn't have is AIR: then the cell just doesn't take it.
        if (card != Items.AIR) {
            Upgrades.add(card, cell, count, group);
        }
    }

    /** The housing and the cells in AE2's own creative tab, after its cells. */
    static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(AECreativeTabIds.MAIN)) {
            event.accept(BuiltInRegistries.ITEM.get(HOUSING));
            CELLS.forEach(event::accept);
            PORTABLE.forEach(event::accept);
        }
    }
}
