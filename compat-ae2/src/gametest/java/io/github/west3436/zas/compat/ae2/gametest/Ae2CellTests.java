package io.github.west3436.zas.compat.ae2.gametest;

import static io.github.west3436.zas.compat.ae2.gametest.Lab.N2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.O2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.T;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.Upgrades;
import appeng.items.storage.BasicStorageCell;
import appeng.items.storage.StorageTier;
import appeng.items.tools.powered.AbstractPortableCell;
import appeng.items.tools.powered.PortableCellItem;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.common.MEStorageMenu;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import io.github.west3436.zas.compat.ae2.GasCells;
import io.github.west3436.zas.compat.ae2.GasKey;
import io.github.west3436.zas.compat.ae2.GasKeyType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The ME gas cells (COMPAT-075 to COMPAT-077): storage cells in a drive, portable cells, and the
 * recipes that make and unmake them. The cells are AE2's own cell classes, outside its API package
 * (COMPAT-003), so these tests run with the oldest and the newest AE2.
 */
@GameTestHolder(Ae2Tests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class Ae2CellTests {
    private static final String BATCH = "zas_ae2_cells";
    private static final int WAIT = 600;

    /** The units of gas in one byte of a cell: 320 mol. */
    private static final long PER_BYTE = GasKeyType.PER_BYTE;

    private static final List<StorageTier> TIERS = List.of(
            StorageTier.SIZE_1K,
            StorageTier.SIZE_4K,
            StorageTier.SIZE_16K,
            StorageTier.SIZE_64K,
            StorageTier.SIZE_256K);

    private Ae2CellTests() {}

    private static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    private static Item ae2(String path) {
        return item(ResourceLocation.fromNamespaceAndPath("ae2", path));
    }

    private static StorageCell inventory(GameTestHelper h, ItemStack stack) {
        StorageCell cell = StorageCells.getCellInventory(stack, null);
        h.assertTrue(cell != null, "AE2 sees no cell in " + stack);
        return cell;
    }

    // ---- COMPAT-075: gas storage cells ------------------------------------------------------------

    /**
     * Each size is a cell of AE2's for gas, as its fluid cells are for fluids: the tier's bytes and
     * idle drain, 8 bytes a type for each kilobyte, 18 types, and the same cards. The cell workbench's
     * filter takes a gas, and then the cell takes no other.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_075_each_gas_cell_is_a_fluid_cell_for_gas(GameTestHelper h) {
        List<Item> cells = GasCells.cells();
        h.assertValueEqual(cells.size(), 5, "the gas storage cells");
        for (int i = 0; i < TIERS.size(); i++) {
            StorageTier tier = TIERS.get(i);
            Item item = cells.get(i);
            h.assertTrue(item == item(GasCells.cell(tier)), "the " + tier.namePrefix() + " cell's id");
            h.assertTrue(item instanceof BasicStorageCell, tier.namePrefix() + " isn't AE2's storage cell");
            IBasicCellItem cell = (IBasicCellItem) item;
            ItemStack stack = new ItemStack(item);
            h.assertTrue(cell.getKeyType() == GasKeyType.TYPE, "the " + tier.namePrefix() + " cell's type");
            h.assertValueEqual(cell.getBytes(stack), tier.bytes(), "the " + tier.namePrefix() + " cell's bytes");
            h.assertValueEqual(
                    cell.getBytesPerType(stack),
                    tier.bytes() / 128,
                    "the " + tier.namePrefix() + " cell's bytes a type");
            h.assertValueEqual(cell.getTotalTypes(stack), GasCells.TYPES, "the " + tier.namePrefix() + " cell's types");
            h.assertValueEqual(
                    cell.getIdleDrain(), tier.idleDrain(), "the " + tier.namePrefix() + " cell's idle drain");
            h.assertValueEqual(item.getDefaultMaxStackSize(), 1, "a cell's stack size");
            for (String card : List.of("inverter_card", "equal_distribution_card", "void_card")) {
                h.assertValueEqual(Upgrades.getMaxInstallable(ae2(card), item), 1, card + " in a cell");
            }
            h.assertValueEqual(Upgrades.getMaxInstallable(ae2("fuzzy_card"), item), 0, "a fuzzy card in a cell");
        }

        // The cell workbench sets the filter through the cell's config inventory.
        ItemStack stack = new ItemStack(cells.get(0));
        IBasicCellItem cell = (IBasicCellItem) stack.getItem();
        cell.getConfigInventory(stack).setStack(0, new GenericStack(GasKey.of(O2), 1));
        h.assertValueEqual(cell.getConfigInventory(stack).getKey(0), GasKey.of(O2), "the gas in the filter");
        StorageCell inventory = inventory(h, stack);
        IActionSource source = IActionSource.empty();
        h.assertValueEqual(
                inventory.insert(GasKey.of(N2), 1000, Actionable.MODULATE, source), 0L, "N2 into an O2 cell");
        h.assertValueEqual(inventory.insert(GasKey.of(O2), 1000, Actionable.MODULATE, source), 1000L, "O2 into it");
        h.succeed();
    }

    /**
     * A 1k gas cell holds 325,120 mol of one gas: 1,024 bytes less 8 for the type, at 320 mol a byte.
     * Then it is full, takes no second gas and no item, shows full on its light, and keeps what it
     * holds on the item.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_075_a_1k_gas_cell_holds_325_120_mol(GameTestHelper h) {
        ItemStack stack = new ItemStack(GasCells.cells().get(0));
        IActionSource source = IActionSource.empty();
        StorageCell cell = inventory(h, stack);
        h.assertValueEqual(cell.getStatus(), CellState.EMPTY, "a new cell's state");
        long expected = (1024 - 8) * PER_BYTE;
        h.assertValueEqual(expected, 325_120_000L, "the units of 325,120 mol");
        h.assertValueEqual(
                cell.insert(GasKey.of(O2), Long.MAX_VALUE, Actionable.MODULATE, source), expected, "O2 taken");
        h.assertValueEqual(cell.insert(GasKey.of(N2), 1, Actionable.MODULATE, source), 0L, "N2 taken by a full cell");
        AEKey stone = AEItemKey.of(Items.STONE);
        h.assertValueEqual(cell.insert(stone, 1, Actionable.MODULATE, source), 0L, "stone taken by a gas cell");
        h.assertValueEqual(cell.getStatus(), CellState.FULL, "a full cell's state");
        h.assertValueEqual(
                BasicStorageCell.getColor(stack, 1), CellState.FULL.getStateColor(), "the light of a full cell");
        cell.persist();

        StorageCell again = inventory(h, stack);
        h.assertValueEqual(again.getAvailableStacks().get(GasKey.of(O2)), expected, "O2 in the cell, read again");
        h.succeed();
    }

    /**
     * An import bus on a tank fills a gas cell in a drive on the network, gas by gas, and a fraction
     * of a millimole stays in the tank.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_075_a_drive_keeps_imported_gas_in_a_gas_cell(GameTestHelper h) {
        Lab lab = new Lab(h, 11);
        TestTank tank = lab.tank(0, 1, 0, 1000, T).with(O2, 3.0004).with(N2, 2.0);
        lab.cable(1, 1, 0);
        IPart bus = lab.part(1, 1, 0, Direction.WEST, "import_bus");
        drive(lab, 2, 1, 0, new ItemStack(GasCells.cells().get(0)));
        lab.energy(1, 2, 0);
        h.succeedWhen(() -> {
            h.assertValueEqual(Lab.stored(bus, O2), 3000L, "the O2 in the drive, in mmol");
            h.assertValueEqual(Lab.stored(bus, N2), 2000L, "the N2 in the drive, in mmol");
            lab.close(tank.moles(O2), 0.0004, "the O2 left in the tank");
            lab.close(tank.moles(N2), 0, "the N2 left in the tank");
            lab.done();
        });
    }

    /** A drive at a place, with a cell in its first slot. */
    private static void drive(Lab lab, int x, int y, int z, ItemStack cell) {
        lab.set(
                x,
                y,
                z,
                BuiltInRegistries.BLOCK
                        .get(ResourceLocation.fromNamespaceAndPath("ae2", "drive"))
                        .defaultBlockState());
        IItemHandler slots = lab.level.getCapability(Capabilities.ItemHandler.BLOCK, lab.abs(x, y, z), null);
        lab.h.assertTrue(slots != null, "the drive has no slots");
        ItemStack left = slots.insertItem(0, cell, false);
        lab.h.assertTrue(left.isEmpty(), "the drive didn't take the gas cell");
    }

    // ---- COMPAT-074: no gas from rounding, through a cell -----------------------------------------

    /**
     * Gas taken into a cell and out of it again, by an import bus and an export bus at once, ends in
     * the other tank to the last millimole: none made, none lost.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_074_gas_through_a_gas_cell_makes_none(GameTestHelper h) {
        Lab lab = new Lab(h, 12);
        TestTank from = lab.tank(0, 1, 0, 1000, T).with(O2, 2.0004);
        TestTank to = lab.tank(2, 1, 0, 1000, T);
        lab.cable(1, 1, 0);
        IPart bus = lab.part(1, 1, 0, Direction.WEST, "import_bus");
        Lab.filter(lab.part(1, 1, 0, Direction.EAST, "export_bus"), O2);
        drive(lab, 1, 1, -1, new ItemStack(GasCells.cells().get(0)));
        lab.energy(1, 2, 0);
        h.succeedWhen(() -> {
            lab.close(to.moles(O2), 2.0, "the O2 that came through");
            lab.close(from.moles(O2), 0.0004, "the O2 left behind, less than a millimole");
            h.assertValueEqual(Lab.stored(bus, O2), 0L, "the O2 still in the cell");
            lab.close(from.moles(O2) + to.moles(O2), 2.0004, "all the O2");
            lab.done();
        });
    }

    // ---- COMPAT-076: portable gas cells -----------------------------------------------------------

    /**
     * Each portable gas cell is AE2's portable cell for gas: half its tier's bytes, AE2's cards, and
     * charged, it opens the portable cell's own terminal.
     */
    @GameTest(template = "empty", batch = BATCH)
    @SuppressWarnings("removal") // a server player, which the cells' menus and inventories need
    public static void compat_076_a_portable_gas_cell_holds_gas_and_opens_its_terminal(GameTestHelper h) {
        List<Item> portable = GasCells.portableCells();
        h.assertValueEqual(portable.size(), 5, "the portable gas cells");
        for (int i = 0; i < TIERS.size(); i++) {
            StorageTier tier = TIERS.get(i);
            Item item = portable.get(i);
            h.assertTrue(item == item(GasCells.portable(tier)), "the " + tier.namePrefix() + " portable cell's id");
            h.assertTrue(item instanceof PortableCellItem, tier.namePrefix() + " isn't AE2's portable cell");
            IBasicCellItem cell = (IBasicCellItem) item;
            ItemStack stack = new ItemStack(item);
            h.assertTrue(cell.getKeyType() == GasKeyType.TYPE, "the portable cell's type");
            h.assertValueEqual(cell.getBytes(stack), tier.bytes() / 2, "the " + tier.namePrefix() + " portable bytes");
            h.assertValueEqual(cell.getTotalTypes(stack), GasCells.TYPES, "the portable cell's types");
            h.assertValueEqual(Upgrades.getMaxInstallable(ae2("energy_card"), item), 2, "energy cards in it");
            h.assertValueEqual(Upgrades.getMaxInstallable(ae2("void_card"), item), 1, "void cards in it");
        }

        ItemStack stack = new ItemStack(portable.get(0));
        long expected = (512 - 8) * PER_BYTE;
        StorageCell inventory = inventory(h, stack);
        h.assertValueEqual(
                inventory.insert(GasKey.of(O2), Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()),
                expected,
                "O2 taken by a 1k portable cell");
        inventory.persist();

        ((IAEItemPowerStorage) stack.getItem()).injectAEPower(stack, 1e9, Actionable.MODULATE);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        // The terminal it opens is AE2's portable cell window, and it shows the cell's gas.
        h.assertTrue(menuType(portable.get(0)) == MEStorageMenu.PORTABLE_FLUID_CELL_TYPE, "the portable cell's window");
        PortableCellItem item = (PortableCellItem) stack.getItem();
        MEStorage shown = item.getMenuHost(player, MenuLocators.forHand(player, InteractionHand.MAIN_HAND), null)
                .getInventory();
        h.assertValueEqual(shown.getAvailableStacks().get(GasKey.of(O2)), expected, "the O2 its terminal shows");

        // Used, it opens that window. A test player can't be sent a window, so the attempt to send it
        // is how the test sees the window open; AE2 sends nothing when there's no terminal to open.
        try {
            item.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            h.fail("the portable cell opened no window");
        } catch (UnsupportedOperationException sent) {
            h.assertTrue(
                    String.valueOf(sent.getMessage()).contains("open_screen"),
                    "using the portable cell failed: " + sent.getMessage());
        }
        h.succeed();
    }

    /** The window a portable cell opens, which AE2 keeps private. */
    private static MenuType<?> menuType(Item portable) {
        try {
            Field field = AbstractPortableCell.class.getDeclaredField("menuType");
            field.setAccessible(true);
            return (MenuType<?>) field.get(portable);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AE2's portable cell has no window type", e);
        }
    }

    // ---- COMPAT-077: made and taken apart ---------------------------------------------------------

    /**
     * A housing is made of quartz glass, redstone and gold; a housing and a storage component make a
     * cell of the component's size; with an ME chest and an energy cell they make a portable cell.
     * Each comes apart into what made it, by AE2's own recipe, as an empty cell used while sneaking
     * does; a cell with gas in it doesn't. A cell and a bigger component make the bigger cell, gas
     * and all, as AE2's cells do.
     */
    @GameTest(template = "empty", batch = BATCH)
    @SuppressWarnings("removal") // a server player, which the cells' menus and inventories need
    public static void compat_077_gas_cells_are_made_and_taken_apart(GameTestHelper h) {
        Item housing = item(GasCells.HOUSING);
        Item glass = ae2("quartz_glass");
        ItemStack made = craft(
                h,
                CraftingInput.of(
                        3,
                        3,
                        List.of(
                                new ItemStack(glass),
                                new ItemStack(Items.REDSTONE),
                                new ItemStack(glass),
                                new ItemStack(Items.REDSTONE),
                                ItemStack.EMPTY,
                                new ItemStack(Items.REDSTONE),
                                new ItemStack(Items.GOLD_INGOT),
                                new ItemStack(Items.GOLD_INGOT),
                                new ItemStack(Items.GOLD_INGOT))));
        h.assertTrue(made.is(housing), "quartz glass, redstone and gold made " + made);

        for (int i = 0; i < TIERS.size(); i++) {
            StorageTier tier = TIERS.get(i);
            Item component = ae2("cell_component_" + tier.namePrefix());
            Item cell = GasCells.cells().get(i);
            Item portable = GasCells.portableCells().get(i);
            ItemStack fromHousing =
                    craft(h, CraftingInput.of(2, 1, List.of(new ItemStack(housing), new ItemStack(component))));
            h.assertTrue(
                    fromHousing.is(cell), "a housing and a " + tier.namePrefix() + " component made " + fromHousing);
            ItemStack fromParts = craft(
                    h,
                    CraftingInput.of(
                            2,
                            2,
                            List.of(
                                    new ItemStack(ae2("chest")),
                                    new ItemStack(component),
                                    new ItemStack(ae2("energy_cell")),
                                    new ItemStack(housing))));
            h.assertTrue(fromParts.is(portable), "the portable cell's parts made " + fromParts);

            h.assertValueEqual(
                    items(StorageCellDisassemblyRecipe.getDisassemblyResult(h.getLevel(), cell)),
                    Set.of(housing, component),
                    "what a " + tier.namePrefix() + " cell comes apart into");
            h.assertValueEqual(
                    items(StorageCellDisassemblyRecipe.getDisassemblyResult(h.getLevel(), portable)),
                    Set.of(ae2("chest"), ae2("energy_cell"), housing, component),
                    "what a " + tier.namePrefix() + " portable cell comes apart into");
        }

        // Used while sneaking, an empty cell comes apart in the hand; one with gas doesn't.
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        Item cell = GasCells.cells().get(0);
        ItemStack full = new ItemStack(cell);
        StorageCell inventory = inventory(h, full);
        inventory.insert(GasKey.of(O2), 1000, Actionable.MODULATE, IActionSource.empty());
        inventory.persist();
        player.setItemInHand(InteractionHand.MAIN_HAND, full);
        cell.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getMainHandItem().is(cell), "a cell with gas in it came apart");

        // A 1k cell with a 4k component makes a 4k cell that keeps the gas, and gives the 1k back.
        CraftingInput upgrade = CraftingInput.of(2, 1, List.of(full.copy(), new ItemStack(ae2("cell_component_4k"))));
        ItemStack bigger = craft(h, upgrade);
        h.assertTrue(bigger.is(GasCells.cells().get(1)), "a 1k gas cell and a 4k component made " + bigger);
        h.assertValueEqual(
                inventory(h, bigger).getAvailableStacks().get(GasKey.of(O2)), 1000L, "the O2 in the upgraded cell");
        h.assertTrue(
                h.getLevel()
                        .getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, upgrade, h.getLevel())
                        .map(r -> r.value().getRemainingItems(upgrade).get(0).is(ae2("cell_component_1k")))
                        .orElse(false),
                "the upgrade gave no 1k component back");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(cell));
        cell.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getInventory().contains(new ItemStack(housing)), "no housing back from an empty cell");
        h.assertTrue(
                player.getInventory().contains(new ItemStack(ae2("cell_component_1k"))),
                "no component back from an empty cell");
        h.assertFalse(player.getInventory().contains(new ItemStack(cell)), "the empty cell is still there");
        h.succeed();
    }

    private static ItemStack craft(GameTestHelper h, CraftingInput input) {
        return h.getLevel()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, h.getLevel())
                .map(r -> r.value().assemble(input, h.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private static Set<Item> items(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::getItem).collect(Collectors.toSet());
    }
}
