package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.atmos.ttv.TransferValveMenu;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A held TTV's screen (CONT-103, CONT-104): its tank slots, and every way a menu on a held item could
 * duplicate or lose one. Each test counts what the player has, everywhere it could be, after each
 * step. The areas are 930 and up.
 *
 * <p>Most drive the menu directly, with a fake player that keeps what it drops, so no test waits for a
 * chunk to find an item. A fake player opens no menus and ignores packets, so the tests of the packets
 * themselves use a real {@link ServerPlayer} on a connection that keeps what it is sent.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransferValveMenuTests {
    private static final String BATCH = "zas_atmos_ttv_menu";

    /** For the tests that drive atmos ticks themselves. */
    private static final String FAILURE_BATCH = "zas_atmos_ttv_menu_failure";

    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final double T = StandardAir.TEMPERATURE;

    private static final int FIRST = TransferValve.FIRST;
    private static final int SECOND = TransferValve.SECOND;

    /** The moles in the two tanks most tests use. */
    private static final double O2_MOLES = 5.0;

    private static final double H2_MOLES = 1.5;

    /** The menu's slots: the two tanks, three rows of the inventory, the hotbar. */
    private static final int ROWS_START = 2;

    private static final int HOTBAR_START = ROWS_START + 27;

    private TransferValveMenuTests() {}

    // ---- Helpers ---------------------------------------------------------------------------

    /** A fake player that keeps what it drops. */
    private static final class TestPlayer extends FakePlayer {
        final List<ItemStack> drops = new ArrayList<>();

        TestPlayer(Area a) {
            super(a.level, new GameProfile(UUID.randomUUID(), "zas_ttv_menu"));
            moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 2)));
        }

        @Override
        public ItemEntity drop(ItemStack stack, boolean dropAround, boolean includeThrowerName) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
            return null;
        }
    }

    private static TestPlayer player(Area a, InteractionHand hand, ItemStack held) {
        a.box(0, 0, 4, 4);
        TestPlayer p = new TestPlayer(a);
        p.setItemInHand(hand, held);
        return p;
    }

    /** A TTV with no tanks. */
    private static ItemStack ttv() {
        return new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
    }

    /** A TTV with the tanks most tests use. */
    private static ItemStack loaded() {
        ItemStack stack = ttv();
        TransferValveItem.save(stack, new TransferValve(oxygen(), hydrogen(), false));
        return stack;
    }

    /** A plain 70 L tank holding a gas. */
    private static ItemStack tank(Map<ResourceLocation, Double> moles) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, new GasParcel(moles, T));
        return tank;
    }

    private static ItemStack oxygen() {
        return tank(Map.of(O2, O2_MOLES));
    }

    private static ItemStack hydrogen() {
        return tank(Map.of(H2, H2_MOLES));
    }

    /** A plain 70 L tank of N2 at a pressure. */
    private static ItemStack nitrogen(double atm) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, GasTankItem.fill(Map.of(N2, 1.0), atm * ContainerFailure.ATM_KPA, 70));
        return tank;
    }

    /** The screen of what a hand holds. */
    private static TransferValveMenu open(TestPlayer p, InteractionHand hand) {
        TransferValveMenu menu = new TransferValveMenu(1, p.getInventory(), hand);
        p.containerMenu = menu;
        return menu;
    }

    private static TransferValveMenu open(TestPlayer p) {
        return open(p, InteractionHand.MAIN_HAND);
    }

    /** The menu's slot for a place in the inventory: the hotbar is 0 to 8, the rows 9 to 35. */
    private static int slotOf(int inventoryIndex) {
        return inventoryIndex < 9 ? HOTBAR_START + inventoryIndex : ROWS_START + inventoryIndex - 9;
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /** Every tank a player has, wherever it is: inventory, cursor, what it dropped, or in a TTV or bundle. */
    private static List<ItemStack> tanks(TestPlayer p) {
        List<ItemStack> found = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            collect(found, p.getInventory().getItem(i));
        }
        collect(found, p.containerMenu.getCarried());
        p.drops.forEach(s -> collect(found, s));
        return found;
    }

    private static void collect(List<ItemStack> found, ItemStack stack) {
        if (stack.getItem() instanceof GasTankItem) {
            for (int i = 0; i < stack.getCount(); i++) {
                found.add(stack);
            }
        } else if (stack.getItem() instanceof TransferValveItem) {
            collect(found, TransferValveItem.tank(stack, FIRST));
            collect(found, TransferValveItem.tank(stack, SECOND));
        } else if (stack.has(DataComponents.BUNDLE_CONTENTS)) {
            stack.get(DataComponents.BUNDLE_CONTENTS).items().forEach(s -> collect(found, s));
        }
    }

    /** Checks the player has exactly these tanks, with these moles between them. */
    private static void once(GameTestHelper h, TestPlayer p, int tanks, double o2, double hydrogen, String when) {
        List<ItemStack> found = tanks(p);
        h.assertValueEqual(found.size(), tanks, "tanks " + when);
        close(h, found.stream().mapToDouble(t -> GasTankItem.gas(t).moles(O2)).sum(), o2, "O2 " + when);
        close(h, found.stream().mapToDouble(t -> GasTankItem.gas(t).moles(H2)).sum(), hydrogen, "H2 " + when);
    }

    /** Checks the usual two tanks are all there, whatever became of them. */
    private static void bothOnce(GameTestHelper h, TestPlayer p, String when) {
        once(h, p, 2, O2_MOLES, H2_MOLES, when);
    }

    /** The primed TTVs that joined a level since {@link #watchPrimed}. */
    private static final List<PrimedTransferValve> PRIMED = new ArrayList<>();

    private static boolean watching;

    /**
     * Starts counting primed TTVs afresh. A level only finds entities in a chunk that is ticking them,
     * so these tests see each one as it joins. Each runs within one call, so nothing else adds to this
     * in the meantime.
     */
    private static void watchPrimed() {
        if (!watching) {
            watching = true;
            NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
                if (e.getEntity() instanceof PrimedTransferValve primed) {
                    PRIMED.add(primed);
                }
            });
        }
        PRIMED.clear();
    }

    /** Takes the only primed TTV there should be since {@link #watchPrimed}, and removes it from the level. */
    private static PrimedTransferValve onePrimed(GameTestHelper h) {
        List<PrimedTransferValve> primed = new ArrayList<>(PRIMED);
        PRIMED.clear();
        primed.forEach(PrimedTransferValve::discard);
        h.assertValueEqual(primed.size(), 1, "primed TTVs");
        return primed.get(0);
    }

    // ---- Loading -----------------------------------------------------------------------------

    /**
     * Every way through the slots gives the item data that right-clicks give, down to the last
     * component, and with the same order: slot 1 is the first tank, whose gas moves into the second.
     * The sprite follows the count of tanks, which goes 0, 1, 2 the same way.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_slots_give_the_same_item_data_as_right_clicks(GameTestHelper h) {
        Area a = new Area(h, 930);

        // By right-clicks, which stay as they were.
        ItemStack byRightClick = ttv();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemStack[] cursor = {oxygen()};
        SlotAccess access = SlotAccess.of(() -> cursor[0], s -> cursor[0] = s);
        Slot slot = new Slot(new SimpleContainer(1), 0, 0, 0);
        slot.set(byRightClick);
        AtmosItems.TANK_TRANSFER_VALVE
                .get()
                .overrideOtherStackedOnMe(byRightClick, cursor[0], slot, ClickAction.SECONDARY, p, access);
        h.assertValueEqual(TransferValveItem.tankCount(byRightClick), 1, "tanks after one right-click");
        cursor[0] = hydrogen();
        AtmosItems.TANK_TRANSFER_VALVE
                .get()
                .overrideOtherStackedOnMe(byRightClick, cursor[0], slot, ClickAction.SECONDARY, p, access);
        h.assertValueEqual(TransferValveItem.tankCount(byRightClick), 2, "tanks after two right-clicks");

        // Carried to each slot, left and right button alike.
        for (int button : new int[] {0, 1}) {
            ItemStack bySlots = ttv();
            p = player(a, InteractionHand.MAIN_HAND, bySlots);
            TransferValveMenu menu = open(p);
            h.assertValueEqual(TransferValveItem.tankCount(bySlots), 0, "tanks in a new TTV");
            menu.setCarried(oxygen());
            menu.clicked(FIRST, button, ClickType.PICKUP, p);
            h.assertTrue(menu.getCarried().isEmpty(), "the cursor kept the first tank");
            h.assertValueEqual(TransferValveItem.tankCount(bySlots), 1, "tanks after the first slot");
            h.assertTrue(TransferValveItem.valve(bySlots).second().isEmpty(), "the first slot filled the second");
            menu.setCarried(hydrogen());
            menu.clicked(SECOND, button, ClickType.PICKUP, p);
            h.assertTrue(menu.getCarried().isEmpty(), "the cursor kept the second tank");
            h.assertValueEqual(TransferValveItem.tankCount(bySlots), 2, "tanks after the second slot");
            h.assertTrue(ItemStack.matches(bySlots, byRightClick), "carried, button " + button + ": " + bySlots);
            close(h, GasTankItem.gas(TransferValveItem.valve(bySlots).first()).moles(O2), O2_MOLES, "the first's O2");
            close(h, GasTankItem.gas(TransferValveItem.valve(bySlots).second()).moles(H2), H2_MOLES, "the second's H2");
        }

        // A shift-click takes the first free place, as a right-click does.
        ItemStack byShift = ttv();
        p = player(a, InteractionHand.MAIN_HAND, byShift);
        TransferValveMenu menu = open(p);
        p.getInventory().setItem(9, oxygen());
        p.getInventory().setItem(10, hydrogen());
        menu.clicked(slotOf(9), 0, ClickType.QUICK_MOVE, p);
        menu.clicked(slotOf(10), 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(p.getInventory().getItem(9).isEmpty(), "the shift-clicked first tank is still there");
        h.assertTrue(p.getInventory().getItem(10).isEmpty(), "the shift-clicked second tank is still there");
        h.assertTrue(ItemStack.matches(byShift, byRightClick), "shift-clicked: " + byShift);
        bothOnce(h, p, "after shift-clicks");

        // A number key from the hotbar, and the second place filled before the first.
        ItemStack byKey = ttv();
        p = player(a, InteractionHand.MAIN_HAND, byKey);
        menu = open(p);
        p.getInventory().setItem(1, hydrogen());
        p.getInventory().setItem(2, oxygen());
        menu.clicked(SECOND, 1, ClickType.SWAP, p);
        h.assertTrue(TransferValveItem.valve(byKey).first().isEmpty(), "the second place filled the first");
        menu.clicked(FIRST, 2, ClickType.SWAP, p);
        h.assertTrue(ItemStack.matches(byKey, byRightClick), "by number keys: " + byKey);
        bothOnce(h, p, "after number keys");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** What it shows is what it holds, and a tank's gas comes through a slot to the cursor untouched. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_slots_show_the_tanks_and_keep_their_gas(GameTestHelper h) {
        Area a = new Area(h, 931);
        ItemStack stack = loaded();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        TransferValveMenu menu = open(p);
        close(h, GasTankItem.gas(menu.getSlot(FIRST).getItem()).moles(O2), O2_MOLES, "the first slot's O2");
        close(h, GasTankItem.gas(menu.getSlot(SECOND).getItem()).moles(H2), H2_MOLES, "the second slot's H2");

        // What is on the TTV changes behind the menu's back: it shows that, as it keeps no copy.
        TransferValveItem.save(stack, new TransferValve(ItemStack.EMPTY, tank(Map.of(O2, 3.0)), false));
        h.assertTrue(menu.getSlot(FIRST).getItem().isEmpty(), "the first slot shows a tank that's gone");
        close(h, GasTankItem.gas(menu.getSlot(SECOND).getItem()).moles(O2), 3.0, "the second slot's new gas");

        menu.clicked(SECOND, 0, ClickType.PICKUP, p);
        close(h, GasTankItem.gas(menu.getCarried()).moles(O2), 3.0, "the taken tank's O2");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 0, "tanks left");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** Only one tank goes in, only while the valve is closed, however it comes. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_slots_refuse_what_a_right_click_refuses(GameTestHelper h) {
        Area a = new Area(h, 932);
        ItemStack stack = ttv();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        TransferValveMenu menu = open(p);

        // Not a tank: carried, shift-clicked, by number key, dragged.
        menu.setCarried(new ItemStack(AtmosItems.MULTITOOL.get()));
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getCarried().is(AtmosItems.MULTITOOL.get()), "the cursor lost its multitool");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 0, "tanks after a multitool");
        menu.setCarried(ItemStack.EMPTY);
        p.getInventory().setItem(1, new ItemStack(Items.DIRT));
        p.getInventory().setItem(9, new ItemStack(Items.DIRT));
        menu.clicked(FIRST, 1, ClickType.SWAP, p);
        menu.clicked(slotOf(9), 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(p.getInventory().getItem(1).is(Items.DIRT), "a number key moved dirt");
        h.assertTrue(p.getInventory().getItem(9).is(Items.DIRT), "a shift-click moved dirt");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 0, "tanks after dirt");
        // A TTV isn't a tank.
        menu.setCarried(ttv());
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getCarried().is(AtmosItems.TANK_TRANSFER_VALVE.get()), "a TTV went in as a tank");
        menu.setCarried(ItemStack.EMPTY);

        // A stack of more than one: only a command can make it, and a right-click refuses it as well.
        ItemStack two = oxygen();
        two.set(DataComponents.MAX_STACK_SIZE, 2);
        two.setCount(2);
        menu.setCarried(two);
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        h.assertValueEqual(menu.getCarried().getCount(), 2, "a stack of two on the cursor");
        menu.clicked(FIRST, 1, ClickType.PICKUP, p);
        h.assertValueEqual(menu.getCarried().getCount(), 2, "a stack of two, right-clicked");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 0, "tanks after a stack of two");
        menu.setCarried(ItemStack.EMPTY);

        // A drag places nothing, and it stays on the cursor.
        menu.setCarried(oxygen());
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, p);
        menu.clicked(FIRST, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, p);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, p);
        h.assertValueEqual(menu.getCarried().getCount(), 1, "the cursor after a drag");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 0, "tanks after a drag");
        menu.setCarried(ItemStack.EMPTY);

        // Full: a third tank stays where it is.
        TransferValveItem.save(stack, new TransferValve(oxygen(), hydrogen(), false));
        ItemStack third = tank(Map.of(O2, 1.0));
        p.getInventory().setItem(9, third);
        menu.clicked(slotOf(9), 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(p.getInventory().getItem(9) == third, "a third tank went on");
        once(h, p, 3, O2_MOLES + 1, H2_MOLES, "with a third tank");

        // A tank onto a full place while closed changes places with it, as any slot does.
        p.getInventory().setItem(9, ItemStack.EMPTY);
        menu.setCarried(third);
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        close(h, GasTankItem.gas(menu.getCarried()).moles(O2), O2_MOLES, "the tank it changed places with");
        close(h, GasTankItem.gas(TransferValveItem.tank(stack, FIRST)).moles(O2), 1.0, "the new first tank");
        once(h, p, 3, O2_MOLES + 1, H2_MOLES, "after changing places");
        menu.setCarried(ItemStack.EMPTY);
        p.getInventory().setItem(9, third.copy());

        // Open: no tank goes on or changes places, by any way, and the valve stays open.
        TransferValveItem.save(stack, new TransferValve(oxygen(), hydrogen(), false));
        h.assertTrue(TransferValveItem.toggle(stack), "the valve refused to open");
        ItemStack before = stack.copy();
        menu.clicked(slotOf(9), 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(ItemStack.matches(stack, before), "a shift-click changed an open valve");
        menu.setCarried(tank(Map.of(O2, 1.0)));
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        h.assertTrue(ItemStack.matches(stack, before), "a click changed an open valve");
        h.assertFalse(menu.getCarried().isEmpty(), "the cursor lost its tank to an open valve");
        menu.setCarried(ItemStack.EMPTY);
        p.getInventory().setItem(1, tank(Map.of(O2, 1.0)));
        menu.clicked(FIRST, 1, ClickType.SWAP, p);
        h.assertTrue(ItemStack.matches(stack, before), "a number key changed an open valve");
        h.assertFalse(p.getInventory().getItem(1).isEmpty(), "a number key lost a tank to an open valve");
        h.assertTrue(TransferValveItem.valve(stack).isOpen(), "the valve closed");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** The hand valve needs both tanks however they came, and greys out nothing a test can't see. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_the_hand_valve_needs_both_tanks_from_slots(GameTestHelper h) {
        Area a = new Area(h, 933);
        ItemStack stack = ttv();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        TransferValveMenu menu = open(p);
        menu.setCarried(oxygen());
        menu.clicked(SECOND, 0, ClickType.PICKUP, p);
        h.assertFalse(menu.clickMenuButton(p, TransferValveMenu.VALVE), "the valve opened with only the second tank");
        menu.setCarried(hydrogen());
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.clickMenuButton(p, TransferValveMenu.VALVE), "the valve refused both tanks");
        h.assertTrue(menu.valve().isOpen(), "the valve didn't open");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- Taking off --------------------------------------------------------------------------

    /**
     * Taking a tank off an open valve closes it first, so the tank has its share of the gas, as a
     * right-click does. The number key is the one to watch: the game copies the slot's stack into the
     * inventory before it empties the slot, so it would otherwise take the tank without its share.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_every_way_of_taking_a_tank_gives_its_share(GameTestHelper h) {
        Area a = new Area(h, 934);
        List<TakeWay> ways = List.of(
                new TakeWay(
                        "a left click",
                        (m, p) -> m.clicked(FIRST, 0, ClickType.PICKUP, p),
                        p -> p.containerMenu.getCarried()),
                new TakeWay(
                        "a right click",
                        (m, p) -> m.clicked(FIRST, 1, ClickType.PICKUP, p),
                        p -> p.containerMenu.getCarried()),
                new TakeWay(
                        "a shift-click",
                        (m, p) -> m.clicked(FIRST, 0, ClickType.QUICK_MOVE, p),
                        p -> p.getInventory().getItem(8)),
                new TakeWay(
                        "a number key",
                        (m, p) -> m.clicked(FIRST, 3, ClickType.SWAP, p),
                        p -> p.getInventory().getItem(3)),
                new TakeWay(
                        "the off hand's key",
                        (m, p) -> m.clicked(FIRST, 40, ClickType.SWAP, p),
                        p -> p.getInventory().getItem(Inventory.SLOT_OFFHAND)),
                new TakeWay("the drop key", (m, p) -> m.clicked(FIRST, 0, ClickType.THROW, p), p -> p.drops.get(0)),
                new TakeWay(
                        "the drop-all key", (m, p) -> m.clicked(FIRST, 1, ClickType.THROW, p), p -> p.drops.get(0)));

        for (TakeWay way : ways) {
            ItemStack stack = loaded();
            TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
            TransferValveMenu menu = open(p);
            h.assertTrue(TransferValveItem.toggle(stack), "the valve refused to open");
            bothOnce(h, p, "before " + way.name);

            way.take.accept(menu, p);

            h.assertFalse(TransferValveItem.valve(stack).isOpen(), way.name + " left the valve open");
            h.assertTrue(TransferValveItem.valve(stack).first().isEmpty(), way.name + " left the tank on");
            ItemStack taken = way.result.apply(p);
            close(h, GasTankItem.gas(taken).moles(O2), O2_MOLES / 2, "the tank's O2 after " + way.name);
            close(h, GasTankItem.gas(taken).moles(H2), H2_MOLES / 2, "the tank's H2 after " + way.name);
            bothOnce(h, p, "after " + way.name);
        }
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    private record TakeWay(
            String name,
            BiConsumer<TransferValveMenu, TestPlayer> take,
            java.util.function.Function<TestPlayer, ItemStack> result) {}

    /** A shift-click with nowhere to go changes nothing: the valve doesn't close for a tank that stays. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_full_inventory_leaves_the_valve_as_it_was(GameTestHelper h) {
        Area a = new Area(h, 935);
        ItemStack stack = loaded();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        TransferValveMenu menu = open(p);
        for (int i = 0; i < 36; i++) {
            if (i != p.getInventory().selected) {
                p.getInventory().setItem(i, new ItemStack(Items.DIRT));
            }
        }
        h.assertTrue(TransferValveItem.toggle(stack), "the valve refused to open");
        ItemStack before = stack.copy();
        menu.clicked(FIRST, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(ItemStack.matches(stack, before), "a shift-click into a full inventory changed the TTV");
        bothOnce(h, p, "with a full inventory");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** A bundle or another TTV takes a tank from a slot as it takes one anywhere: it moves, once. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_tank_taken_by_a_bundle_or_ttv_moves_once(GameTestHelper h) {
        Area a = new Area(h, 936);
        ItemStack stack = loaded();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        TransferValveMenu menu = open(p);

        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        menu.setCarried(bundle);
        menu.clicked(FIRST, 1, ClickType.PICKUP, p);
        h.assertTrue(TransferValveItem.valve(stack).first().isEmpty(), "the bundle left the tank on");
        h.assertValueEqual(bundle.get(DataComponents.BUNDLE_CONTENTS).size(), 1, "tanks in the bundle");
        bothOnce(h, p, "with one in a bundle");

        // Back out onto the empty place, by the rules.
        menu.clicked(FIRST, 1, ClickType.PICKUP, p);
        h.assertFalse(TransferValveItem.valve(stack).first().isEmpty(), "the bundle didn't put the tank back");
        h.assertValueEqual(bundle.get(DataComponents.BUNDLE_CONTENTS).size(), 0, "tanks in the bundle");
        bothOnce(h, p, "back from a bundle");
        close(h, GasTankItem.gas(TransferValveItem.tank(stack, FIRST)).moles(O2), O2_MOLES, "the tank's O2");

        // A bundle of dirt can't put it in a slot, and keeps it.
        BundleContents.Mutable dirt = new BundleContents.Mutable(BundleContents.EMPTY);
        dirt.tryInsert(new ItemStack(Items.DIRT));
        bundle.set(DataComponents.BUNDLE_CONTENTS, dirt.toImmutable());
        TransferValveItem.save(stack, new TransferValve(ItemStack.EMPTY, hydrogen(), false));
        menu.clicked(FIRST, 1, ClickType.PICKUP, p);
        h.assertValueEqual(bundle.get(DataComponents.BUNDLE_CONTENTS).size(), 1, "dirt in the bundle");
        h.assertTrue(TransferValveItem.valve(stack).first().isEmpty(), "dirt went on as a tank");

        // Another TTV on the cursor takes a tank as it takes one from any slot.
        ItemStack other = ttv();
        menu.setCarried(other);
        menu.clicked(SECOND, 1, ClickType.PICKUP, p);
        h.assertTrue(TransferValveItem.valve(stack).second().isEmpty(), "the other TTV left the tank on");
        close(h, GasTankItem.gas(TransferValveItem.tank(other, FIRST)).moles(H2), H2_MOLES, "the other TTV's tank");
        once(h, p, 1, 0, H2_MOLES, "with one in another TTV");

        // An open valve's slot holds all the gas in its second tank until the valve closes. Another
        // TTV reads a slot before it takes from it, so it mustn't take from one, or that gas is twice.
        menu.setCarried(ttv());
        TransferValveItem.save(stack, new TransferValve(oxygen(), hydrogen(), false));
        h.assertTrue(TransferValveItem.toggle(stack), "the valve refused to open");
        ItemStack before = stack.copy();
        for (int place : new int[] {FIRST, SECOND}) {
            menu.clicked(place, 1, ClickType.PICKUP, p);
            h.assertValueEqual(TransferValveItem.tankCount(menu.getCarried()), 0, "tanks another TTV took");
            h.assertTrue(ItemStack.matches(stack, before), "another TTV changed an open valve");
        }
        menu.setCarried(ItemStack.EMPTY);

        // A bundle takes the tank's own share, closing the valve as any other way does.
        ItemStack shares = new ItemStack(Items.BUNDLE);
        shares.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        menu.setCarried(shares);
        menu.clicked(SECOND, 1, ClickType.PICKUP, p);
        h.assertFalse(TransferValveItem.valve(stack).isOpen(), "the bundle left the valve open");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 1, "tanks left on after the bundle");
        bothOnce(h, p, "with an open valve's tank in a bundle");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- The TTV itself ----------------------------------------------------------------------

    /**
     * Nothing a click can do moves the TTV while its screen is open: not clicking its slot, not a
     * number key pressed on its slot or on any other, not a shift-click, not the drop keys, not a
     * bundle. The same in either hand. Only a right-click reaches it, for what a right-click does to
     * tanks, and that is tested below.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_the_ttv_cant_be_moved_with_its_screen_open(GameTestHelper h) {
        Area a = new Area(h, 937);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = loaded();
            TestPlayer p = player(a, hand, ItemStack.EMPTY);
            p.getInventory().selected = 4;
            p.setItemInHand(hand, stack);
            int index = hand == InteractionHand.MAIN_HAND ? 4 : Inventory.SLOT_OFFHAND;
            TransferValveMenu menu = open(p, hand);
            p.getInventory().setItem(1, oxygen()); // an occupied key
            p.getInventory().setItem(10, new ItemStack(Items.DIRT)); // an occupied slot
            ItemStack before = stack.copy();
            Runnable stayed = () -> {
                h.assertTrue(p.getInventory().getItem(index) == stack, "the TTV left its place");
                h.assertValueEqual(stack.getCount(), 1, "the TTV's count");
                h.assertTrue(ItemStack.matches(stack, before), "the TTV changed");
                h.assertTrue(p.drops.isEmpty(), "something was dropped: " + p.drops);
                h.assertFalse(menu.getCarried().is(AtmosItems.TANK_TRANSFER_VALVE.get()), "the TTV is on the cursor");
                h.assertTrue(menu.stillValid(p), "the menu closed");
                once(h, p, 3, 2 * O2_MOLES, H2_MOLES, "with the TTV in place");
            };

            // Its slot: clicked, shift-clicked, thrown, gathered, dragged to.
            int own = menu.valveSlot();
            menu.clicked(own, 0, ClickType.PICKUP, p);
            stayed.run();
            for (int button : new int[] {0, 1}) {
                menu.clicked(own, button, ClickType.QUICK_MOVE, p);
                stayed.run();
                menu.clicked(own, button, ClickType.THROW, p);
                stayed.run();
                menu.clicked(own, button, ClickType.PICKUP_ALL, p);
                stayed.run();
            }
            menu.clicked(own, 0, ClickType.CLONE, p);
            stayed.run();

            // A number key on its slot, whichever key.
            for (int key : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 40}) {
                menu.clicked(own, key, ClickType.SWAP, p);
                stayed.run();
            }

            // Its own number key on any other slot: the tanks, a free one and a full one.
            for (int slot : new int[] {FIRST, SECOND, slotOf(9), slotOf(10), slotOf(2)}) {
                menu.clicked(slot, index, ClickType.SWAP, p);
                stayed.run();
            }

            // A bundle, a TTV or a stack of dirt carried onto it.
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            bundle.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
            for (ItemStack carried : new ItemStack[] {bundle, ttv(), new ItemStack(Items.DIRT, 4)}) {
                menu.setCarried(carried);
                menu.clicked(own, 0, ClickType.PICKUP, p);
                menu.clicked(own, 1, ClickType.PICKUP, p);
                menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, p);
                menu.clicked(own, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, p);
                menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, p);
                menu.setCarried(ItemStack.EMPTY);
                stayed.run();
                h.assertValueEqual(bundle.get(DataComponents.BUNDLE_CONTENTS).size(), 0, "what a bundle took");
            }
        }
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** A tank right-clicked onto the TTV's own slot on its own screen goes on, as it does anywhere. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_right_click_works_on_its_own_screen(GameTestHelper h) {
        Area a = new Area(h, 938);
        ItemStack stack = ttv();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        TransferValveMenu menu = open(p);
        menu.setCarried(oxygen());
        menu.clicked(menu.valveSlot(), 0, ClickType.PICKUP, p);
        h.assertFalse(menu.getCarried().isEmpty(), "a left click fitted a tank");
        menu.clicked(menu.valveSlot(), 1, ClickType.PICKUP, p);
        h.assertTrue(menu.getCarried().isEmpty(), "a right click didn't fit the tank");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 1, "tanks on");
        h.assertFalse(TransferValveItem.valve(stack).first().isEmpty(), "the tank isn't the first");

        menu.clicked(menu.valveSlot(), 1, ClickType.PICKUP, p);
        close(h, GasTankItem.gas(menu.getCarried()).moles(O2), O2_MOLES, "the tank a right click took");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 0, "tanks left");
        h.assertTrue(p.getInventory().getSelected() == stack, "the TTV moved");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** In the off hand it has its own slot, so its data syncs, and its slots work the same. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_ttv_in_the_off_hand_works_the_same(GameTestHelper h) {
        Area a = new Area(h, 939);
        ItemStack stack = ttv();
        TestPlayer p = player(a, InteractionHand.OFF_HAND, stack);
        p.getInventory().setItem(p.getInventory().selected, hydrogen());
        TransferValveMenu menu = open(p, InteractionHand.OFF_HAND);
        h.assertTrue(menu.getSlot(menu.valveSlot()).getItem() == stack, "the TTV's slot shows something else");
        h.assertValueEqual(menu.slots.size(), HOTBAR_START + 9 + 1, "slots");

        // What the main hand holds isn't locked, and is the one a number key would take in from.
        menu.setCarried(oxygen());
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        menu.clicked(slotOf(p.getInventory().selected), 0, ClickType.QUICK_MOVE, p);
        ItemStack byRightClick = ttv();
        TransferValveItem.save(byRightClick, new TransferValve(oxygen(), hydrogen(), false));
        h.assertTrue(ItemStack.matches(stack, byRightClick), "loaded in the off hand: " + stack);
        h.assertTrue(p.getMainHandItem().isEmpty(), "the main hand kept its tank");
        bothOnce(h, p, "loaded in the off hand");

        // The buttons act on it, and on nothing in the main hand.
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        h.assertTrue(menu.clickMenuButton(p, TransferValveMenu.UP_1), "the timer button");
        h.assertValueEqual(TransferValveItem.timer(stack), TransferValve.DEFAULT_TIMER + 1, "the timer");
        watchPrimed();
        h.assertTrue(menu.clickMenuButton(p, TransferValveMenu.START), "the start button");
        onePrimed(h);
        h.assertTrue(stack.isEmpty(), "the TTV is still in the off hand");
        h.assertFalse(menu.stillValid(p), "the menu is valid on a primed TTV");
        h.assertTrue(p.getMainHandItem().is(Items.DIRT), "the main hand's dirt");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- Creative ----------------------------------------------------------------------------

    /**
     * A creative player's stacks don't shrink when used and a middle click clones what it is on, so
     * this is where a tank could exist twice. Nothing comes of either here: tanks move once, a middle
     * click on the screen's own slots clones nothing, and starting the timer uses the TTV up.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_creative_player_gets_no_copies(GameTestHelper h) {
        Area a = new Area(h, 940);
        ItemStack stack = ttv();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        p.getAbilities().instabuild = true;
        h.assertTrue(p.hasInfiniteMaterials(), "the player isn't creative");
        TransferValveMenu menu = open(p);

        menu.setCarried(oxygen());
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        p.getInventory().setItem(9, hydrogen());
        menu.clicked(slotOf(9), 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(menu.getCarried().isEmpty(), "the cursor kept a tank");
        h.assertTrue(p.getInventory().getItem(9).isEmpty(), "the inventory kept a tank");
        bothOnce(h, p, "loaded in creative");

        for (int slot : new int[] {FIRST, SECOND, menu.valveSlot()}) {
            menu.clicked(slot, 2, ClickType.CLONE, p);
            h.assertTrue(menu.getCarried().isEmpty(), "a middle click cloned slot " + slot + ": " + menu.getCarried());
        }
        bothOnce(h, p, "after middle clicks");

        // Off and on again, in a full inventory, where creative's "add" would throw it away.
        for (int i = 0; i < 36; i++) {
            if (i != p.getInventory().selected) {
                p.getInventory().setItem(i, new ItemStack(Items.DIRT));
            }
        }
        menu.clicked(SECOND, 0, ClickType.QUICK_MOVE, p);
        bothOnce(h, p, "a shift-click into a full inventory");
        menu.clicked(SECOND, 0, ClickType.PICKUP, p);
        p.closeContainer();
        h.assertTrue(p.containerMenu == p.inventoryMenu, "the menu didn't close");
        h.assertValueEqual(tanks(p).size(), 2, "tanks after closing with one on the cursor and no room");

        // Starting the timer takes it from the hand: the entity has the only tanks.
        ItemStack started = loaded();
        p.getInventory().clearContent();
        p.drops.clear();
        p.setItemInHand(InteractionHand.MAIN_HAND, started);
        menu = open(p);
        watchPrimed();
        h.assertTrue(menu.clickMenuButton(p, TransferValveMenu.START), "the start button");
        h.assertTrue(started.isEmpty(), "the TTV is still in a creative player's hand");
        h.assertFalse(menu.stillValid(p), "the menu is valid on a primed TTV");
        h.assertFalse(menu.clickMenuButton(p, TransferValveMenu.START), "it started twice");
        TransferValve valve = onePrimed(h).valve();
        close(h, GasTankItem.gas(valve.first()).moles(O2), O2_MOLES, "the primed first tank");
        close(h, GasTankItem.gas(valve.second()).moles(H2), H2_MOLES, "the primed second tank");
        once(h, p, 0, 0, 0, "a creative player's after starting");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- A stack that changes while it is open -------------------------------------------------

    /**
     * Whatever happens to the stack, the menu stops acting: no click or button moves or makes a tank,
     * and it writes nothing to the stack it was opened on, wherever that went, or to what replaced it.
     * Closing it then puts a tank on the cursor back in the inventory.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_menu_stops_when_its_stack_changes(GameTestHelper h) {
        Area a = new Area(h, 941);
        record Change(String name, Consumer<TestPlayer> apply) {}
        List<Change> changes = List.of(
                new Change(
                        "a second item in the stack", p -> p.getMainHandItem().setCount(2)),
                new Change("the stack used up", p -> p.getMainHandItem().setCount(0)),
                new Change(
                        "a copy in its place, as a creative client can send",
                        p -> p.getInventory()
                                .setItem(
                                        p.getInventory().selected,
                                        p.getMainHandItem().copy())),
                new Change(
                        "another TTV in its place", p -> p.getInventory().setItem(p.getInventory().selected, loaded())),
                new Change(
                        "dirt in its place",
                        p -> p.getInventory().setItem(p.getInventory().selected, new ItemStack(Items.DIRT))),
                new Change("moved to another slot", p -> {
                    ItemStack held = p.getMainHandItem();
                    p.getInventory().setItem(p.getInventory().selected, ItemStack.EMPTY);
                    p.getInventory().setItem(20, held);
                }),
                new Change("swapped to the off hand", p -> {
                    p.setItemInHand(InteractionHand.OFF_HAND, p.getMainHandItem());
                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }),
                new Change("another hotbar slot selected", p -> p.getInventory().selected = 5),
                new Change("the player's drop key, with no screen to stop it", p -> p.drop(false)),
                new Change("the player a spectator", p -> p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR)));

        for (Change change : changes) {
            ItemStack stack = loaded();
            TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
            TransferValveMenu menu = open(p);
            h.assertTrue(menu.stillValid(p), "a new menu before " + change.name);
            ItemStack cursor = tank(Map.of(O2, 1.0));
            menu.setCarried(cursor);
            p.getInventory().setItem(9, tank(Map.of(O2, 2.0)));
            int timer = TransferValveItem.timer(stack);

            change.apply.accept(p);
            h.assertFalse(menu.stillValid(p), "still valid after " + change.name);

            ItemStack original = stack.copy();
            List<ItemStack> inventory = new ArrayList<>();
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                inventory.add(p.getInventory().getItem(i).copy());
            }
            List<ItemStack> drops = new ArrayList<>(p.drops);

            for (int button : new int[] {0, 1}) {
                for (int slot : new int[] {FIRST, SECOND, slotOf(9), menu.valveSlot()}) {
                    menu.clicked(slot, button, ClickType.PICKUP, p);
                    menu.clicked(slot, button, ClickType.QUICK_MOVE, p);
                    menu.clicked(slot, button, ClickType.THROW, p);
                    menu.clicked(slot, button, ClickType.SWAP, p);
                }
            }
            for (int id = TransferValveMenu.DOWN_10; id <= TransferValveMenu.START; id++) {
                h.assertFalse(menu.clickMenuButton(p, id), "button " + id + " after " + change.name);
            }

            h.assertTrue(menu.getCarried() == cursor && cursor.getCount() == 1, "the cursor after " + change.name);
            h.assertTrue(p.drops.equals(drops), "something was dropped after " + change.name);
            h.assertTrue(ItemStack.matches(stack, original), "the old stack changed after " + change.name);
            h.assertValueEqual(TransferValveItem.timer(stack), timer, "the old stack's timer");
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                h.assertTrue(
                        ItemStack.matches(p.getInventory().getItem(i), inventory.get(i)),
                        "slot " + i + " changed after " + change.name);
            }

            // The tank on the cursor is safe, and closing puts it back.
            p.closeContainer();
            h.assertTrue(p.containerMenu == p.inventoryMenu, "the menu didn't close after " + change.name);
            h.assertTrue(menu.getCarried().isEmpty(), "the closed menu kept its cursor");
            h.assertTrue(cursor.isEmpty() || !p.getInventory().contains(cursor), "the cursor's stack was reused");
            double o2 = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.getItem() instanceof GasTankItem && GasTankItem.gas(s).moles(O2) == 1.0) {
                    o2 += 1;
                }
            }
            h.assertValueEqual(o2, 1.0, "tanks of the cursor's after closing, " + change.name);
        }
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** A tank that fails with the screen open goes from its slot, and one that explodes closes the menu. */
    @GameTest(template = "empty", batch = FAILURE_BATCH)
    public static void cont_103_a_tank_can_fail_with_the_screen_open(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 942);
        ItemStack stack = ttv();
        TransferValveItem.save(stack, new TransferValve(nitrogen(45), hydrogen(), false));
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        a.fill(2, 2, 2, Map.of(N2, 1.0));
        TransferValveMenu menu = open(p);
        menu.setCarried(tank(Map.of(O2, 1.0)));

        // A rupture: its place empties, and the menu shows that, with nothing to write back.
        h.assertFalse(TransferValveItem.step(stack, a.level, p), "a rupture destroyed the TTV");
        h.assertTrue(menu.getSlot(FIRST).getItem().isEmpty(), "the slot still shows the burst tank");
        h.assertFalse(menu.getSlot(SECOND).getItem().isEmpty(), "the other tank went too");
        h.assertTrue(menu.stillValid(p), "a rupture closed the menu");

        // An explosion uses the stack up: the menu stops, and the cursor's tank is unharmed.
        TransferValveItem.save(stack, new TransferValve(nitrogen(60), hydrogen(), false));
        h.assertTrue(TransferValveItem.step(stack, a.level, p), "the TTV outlasted its tank");
        stack.setCount(0);
        h.assertFalse(menu.stillValid(p), "the menu is valid on an exploded TTV");
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        close(h, GasTankItem.gas(menu.getCarried()).moles(O2), 1.0, "the cursor's tank");
        p.closeContainer();
        once(h, p, 1, 1.0, 0, "after the explosion");
        a.tick();
        Blasts.take(a.point(2, 2, 2));
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- Closing --------------------------------------------------------------------------

    /** Closing leaves the tanks in the TTV, with nothing dropped or given back. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_closing_the_screen_leaves_the_tanks_on(GameTestHelper h) {
        Area a = new Area(h, 943);
        ItemStack stack = ttv();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        TransferValveMenu menu = open(p);
        menu.setCarried(oxygen());
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        p.getInventory().setItem(9, hydrogen());
        menu.clicked(slotOf(9), 0, ClickType.QUICK_MOVE, p);

        p.closeContainer();
        h.assertTrue(p.containerMenu == p.inventoryMenu, "the menu didn't close");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 2, "tanks on after closing");
        h.assertTrue(p.getMainHandItem() == stack, "the TTV moved");
        h.assertTrue(p.drops.isEmpty(), "closing dropped " + p.drops);
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            h.assertTrue(s == stack || s.isEmpty(), "closing gave back " + s);
        }
        bothOnce(h, p, "after closing");

        // What it was left holding is there next time, and a tank left on the cursor goes to the
        // inventory rather than the TTV, or the ground.
        menu = open(p);
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        h.assertFalse(menu.getCarried().isEmpty(), "reopened, it had no first tank");
        bothOnce(h, p, "with the first on the cursor");
        p.closeContainer();
        h.assertValueEqual(TransferValveItem.tankCount(stack), 1, "tanks on after closing with one on the cursor");
        h.assertTrue(p.drops.isEmpty(), "closing dropped " + p.drops);
        bothOnce(h, p, "with the first back in the inventory");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- Starting ------------------------------------------------------------------------------

    /**
     * Start waits while a tank is on the cursor, so a tank being moved is neither left behind nor primed
     * and kept. Once it starts, the entity has the tanks, the menu stops, and later clicks do nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_start_waits_for_a_tank_being_moved(GameTestHelper h) {
        Area a = new Area(h, 944);
        ItemStack stack = loaded();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        TransferValveMenu menu = open(p);

        watchPrimed();
        menu.clicked(SECOND, 0, ClickType.PICKUP, p);
        h.assertFalse(menu.getCarried().isEmpty(), "the second tank isn't on the cursor");
        h.assertFalse(menu.clickMenuButton(p, TransferValveMenu.START), "it started with a tank on the cursor");
        h.assertValueEqual(stack.getCount(), 1, "the TTV in the hand");
        h.assertTrue(PRIMED.isEmpty(), "it primed with a tank on the cursor");
        bothOnce(h, p, "after a refused start");

        menu.clicked(SECOND, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.clickMenuButton(p, TransferValveMenu.START), "it refused with an empty cursor");
        h.assertTrue(stack.isEmpty(), "the TTV is still in the hand");
        h.assertFalse(menu.stillValid(p), "the menu is valid on a primed TTV");
        once(h, p, 0, 0, 0, "a player's after starting");

        // A click that was already on its way finds nothing, and takes nothing from the entity.
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        menu.clicked(SECOND, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(menu.getCarried().isEmpty(), "a late click took a tank");
        once(h, p, 0, 0, 0, "after late clicks");
        h.assertFalse(menu.clickMenuButton(p, TransferValveMenu.START), "it started twice");

        TransferValve valve = onePrimed(h).valve();
        close(h, GasTankItem.gas(valve.first()).moles(O2), O2_MOLES, "the primed first tank");
        close(h, GasTankItem.gas(valve.second()).moles(H2), H2_MOLES, "the primed second tank");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- Sounds ------------------------------------------------------------------------------

    /** Adding or removing a tank sounds as a bundle does, once, whichever way, and a refusal sounds nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_slots_play_the_bundle_sounds(GameTestHelper h) {
        SoundLog.listen();
        Area a = new Area(h, 945);
        ItemStack stack = ttv();
        TestPlayer p = player(a, InteractionHand.MAIN_HAND, stack);
        TransferValveMenu menu = open(p);
        Vec3 at = p.position();
        ResourceLocation in = SoundEvents.BUNDLE_INSERT.getLocation();
        ResourceLocation out = SoundEvents.BUNDLE_REMOVE_ONE.getLocation();

        menu.setCarried(oxygen());
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        heard(h, at, "a tank put in", in);
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        heard(h, at, "a tank taken out", out);

        // Onto a full place: one change, one sound.
        TransferValveItem.setTimer(stack, 1);
        menu.setCarried(oxygen());
        menu.clicked(SECOND, 0, ClickType.PICKUP, p);
        heard(h, at, "a tank put in the second place", in);
        menu.setCarried(hydrogen());
        menu.clicked(SECOND, 0, ClickType.PICKUP, p);
        heard(h, at, "a tank changed for another", in);
        menu.setCarried(ItemStack.EMPTY);

        // Shift-clicks both ways.
        menu.clicked(SECOND, 0, ClickType.QUICK_MOVE, p);
        heard(h, at, "a tank shift-clicked out", out);
        p.getInventory().setItem(9, oxygen());
        menu.clicked(slotOf(9), 0, ClickType.QUICK_MOVE, p);
        heard(h, at, "a tank shift-clicked in", in);

        // Number keys both ways.
        menu.clicked(FIRST, 3, ClickType.SWAP, p);
        heard(h, at, "a tank taken by a number key", out);
        menu.clicked(FIRST, 3, ClickType.SWAP, p);
        heard(h, at, "a tank put in by a number key", in);

        // Refused: silence.
        menu.setCarried(new ItemStack(Items.DIRT));
        menu.clicked(SECOND, 0, ClickType.PICKUP, p);
        heard(h, at, "dirt refused");
        menu.setCarried(ItemStack.EMPTY);
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    private static void heard(GameTestHelper h, Vec3 at, String when, ResourceLocation... sounds) {
        List<SoundLog.Played> played = SoundLog.take(at);
        h.assertValueEqual(played.size(), sounds.length, when + ": played " + played);
        for (int i = 0; i < sounds.length; i++) {
            h.assertTrue(
                    played.get(i).id().equals(sounds[i]) && played.get(i).volume() == 0.8f,
                    when + ": played " + played.get(i));
        }
    }

    // ---- The client's side ---------------------------------------------------------------------

    /**
     * A client predicts each click with the same code, on its own copy of the stack, and is told the
     * tanks by slot. Both end up agreeing, a stack changed by a slot's update keeps its other data,
     * and an update that finds no TTV writes to nothing, least of all the one shared empty stack.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_clients_menu_agrees_with_the_servers(GameTestHelper h) {
        Area a = new Area(h, 946);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack serverStack = ttv();
            ItemStack clientStack = serverStack.copy();
            TestPlayer server = player(a, hand, serverStack);
            TestPlayer client = new TestPlayer(a);
            client.setItemInHand(hand, clientStack);
            int index = hand == InteractionHand.MAIN_HAND ? server.getInventory().selected : Inventory.SLOT_OFFHAND;

            TransferValveMenu serverMenu = open(server, hand);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), a.level.registryAccess());
            buf.writeVarInt(index);
            TransferValveMenu clientMenu = new TransferValveMenu(1, client.getInventory(), buf);
            client.containerMenu = clientMenu;
            h.assertValueEqual(clientMenu.slots.size(), serverMenu.slots.size(), "slots on each side");
            h.assertValueEqual(clientMenu.valveSlot(), serverMenu.valveSlot(), "the TTV's slot on each side");

            record Click(int slot, int button, ClickType type) {}
            server.getInventory().setItem(9, oxygen());
            client.getInventory().setItem(9, oxygen());
            server.getInventory().setItem(10, hydrogen());
            client.getInventory().setItem(10, hydrogen());
            server.getInventory().setItem(1, ItemStack.EMPTY);
            client.getInventory().setItem(1, ItemStack.EMPTY);
            for (Click click : List.of(
                    new Click(slotOf(9), 0, ClickType.PICKUP), // the first tank onto the cursor
                    new Click(FIRST, 0, ClickType.PICKUP), // and into its slot
                    new Click(slotOf(10), 0, ClickType.QUICK_MOVE), // the second by a shift-click
                    new Click(SECOND, 1, ClickType.SWAP), // out by a number key
                    new Click(SECOND, 1, ClickType.SWAP), // and back
                    new Click(serverMenu.valveSlot(), 0, ClickType.QUICK_MOVE), // refused
                    new Click(serverMenu.valveSlot(), 1, ClickType.PICKUP), // the last tank off by a right-click
                    new Click(FIRST, 0, ClickType.PICKUP))) { // and it changes places with the first
                serverMenu.clicked(click.slot, click.button, click.type, server);
                clientMenu.clicked(click.slot, click.button, click.type, client);
                h.assertTrue(
                        ItemStack.matches(serverStack, clientStack),
                        click + ": the server has " + serverStack + ", the client " + clientStack);
                h.assertTrue(
                        ItemStack.matches(serverMenu.getCarried(), clientMenu.getCarried()),
                        click + ": the cursors " + serverMenu.getCarried() + ", " + clientMenu.getCarried());
            }

            // Told a tank, the client's stack has it, with its other data as it was.
            TransferValveItem.setTimer(clientStack, 42);
            ItemStack told = tank(Map.of(O2, 7.0));
            clientMenu.setItem(SECOND, clientMenu.getStateId(), told);
            close(
                    h,
                    GasTankItem.gas(TransferValveItem.tank(clientStack, SECOND)).moles(O2),
                    7.0,
                    "the told tank's O2");
            h.assertValueEqual(TransferValveItem.timer(clientStack), 42, "the client's timer after an update");

            // The stack replaced as the game syncs it: the menu follows it, with no reference to the old one.
            ItemStack synced = serverStack.copy();
            clientMenu.setItem(clientMenu.valveSlot(), clientMenu.getStateId(), synced);
            h.assertTrue(clientMenu.stillValid(client), "the client's menu after a sync");
            h.assertTrue(
                    ItemStack.matches(clientMenu.getSlot(FIRST).getItem(), TransferValveItem.tank(synced, FIRST)),
                    "the first slot after a sync");

            // With no TTV there, an update has nothing to write to.
            client.getInventory().setItem(index, ItemStack.EMPTY);
            h.assertFalse(clientMenu.stillValid(client), "the client's menu with an empty hand");
            clientMenu.setItem(FIRST, clientMenu.getStateId(), oxygen());
            h.assertFalse(ItemStack.EMPTY.has(AtmosComponents.TTV_TANKS.get()), "an update wrote to the empty stack");
            client.getInventory().setItem(index, new ItemStack(Items.DIRT));
            clientMenu.setItem(FIRST, clientMenu.getStateId(), oxygen());
            h.assertFalse(
                    client.getInventory().getItem(index).has(AtmosComponents.TTV_TANKS.get()),
                    "an update wrote tanks to dirt");
        }
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- A real player and the game's own packets ----------------------------------------------

    /** A player as a server has one, whose menus open and whose packets reach the game's handlers. */
    private static ServerPlayer realPlayer(Area a, ItemStack held, List<Packet<?>> sent) {
        a.box(0, 0, 4, 4);
        ServerPlayer p = new ServerPlayer(
                a.level.getServer(),
                a.level,
                new GameProfile(UUID.randomUUID(), "zas_ttv_real"),
                ClientInformation.createDefault());
        new ListeningPlayer.Recording(a.level, p, sent);
        p.moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 2)));
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        return p;
    }

    private static void click(ServerPlayer p, int slot, int button, ClickType type) {
        AbstractContainerMenu menu = p.containerMenu;
        p.connection.handleContainerClick(new ServerboundContainerClickPacket(
                menu.containerId,
                menu.getStateId(),
                slot,
                button,
                type,
                ItemStack.EMPTY,
                new Int2ObjectOpenHashMap<>()));
    }

    /** Using it opens the menu through the game's own path, and clicks reach it and sync back. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_using_it_opens_slots_that_sync_to_the_client(GameTestHelper h) {
        Area a = new Area(h, 947);
        List<Packet<?>> sent = new ArrayList<>();
        ItemStack stack = ttv();
        ServerPlayer p = realPlayer(a, stack, sent);
        p.getInventory().setItem(9, oxygen());

        stack.use(a.level, p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.containerMenu instanceof TransferValveMenu, "using it opened " + p.containerMenu);
        TransferValveMenu menu = (TransferValveMenu) p.containerMenu;

        click(p, slotOf(9), 0, ClickType.PICKUP);
        h.assertFalse(menu.getCarried().isEmpty(), "the click took nothing from the inventory");
        click(p, FIRST, 0, ClickType.PICKUP);
        h.assertTrue(menu.getCarried().isEmpty(), "the click put nothing on the TTV");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 1, "tanks on");
        close(h, GasTankItem.gas(TransferValveItem.tank(stack, FIRST)).moles(O2), O2_MOLES, "the first tank's O2");

        // The client that claims nothing changed is told the tank, in the first slot, with its gas.
        boolean told = sent.stream()
                .filter(ClientboundContainerSetSlotPacket.class::isInstance)
                .map(ClientboundContainerSetSlotPacket.class::cast)
                .anyMatch(s -> s.getSlot() == FIRST
                        && s.getContainerId() == menu.containerId
                        && GasTankItem.gas(s.getItem()).moles(O2) == O2_MOLES);
        h.assertTrue(told, "the client wasn't told the first tank");

        // A button goes through too, and closing leaves the tank on.
        p.connection.handleContainerButtonClick(
                new ServerboundContainerButtonClickPacket(menu.containerId, TransferValveMenu.UP_10));
        h.assertValueEqual(TransferValveItem.timer(stack), TransferValve.DEFAULT_TIMER + 10, "the timer");
        p.closeContainer();
        h.assertValueEqual(TransferValveItem.tankCount(stack), 1, "tanks on after closing");

        // A stack of two has no screen of its own, as it can't be loaded.
        stack.set(DataComponents.MAX_STACK_SIZE, 2);
        stack.setCount(2);
        stack.use(a.level, p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.containerMenu == p.inventoryMenu, "a stack of two opened a screen");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /**
     * Through the packets, the case that matters most: the TTV changes between ticks while a client
     * still shows its screen. The game's handlers refuse its clicks and buttons, the player's next tick
     * closes the menu and tells the client, and the tank on the cursor goes back to the inventory.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_stale_screen_is_refused_and_closed(GameTestHelper h) {
        Area a = new Area(h, 948);
        List<Packet<?>> sent = new ArrayList<>();
        ItemStack stack = loaded();
        ServerPlayer p = realPlayer(a, stack, sent);
        stack.use(a.level, p, InteractionHand.MAIN_HAND);
        TransferValveMenu menu = (TransferValveMenu) p.containerMenu;
        click(p, SECOND, 0, ClickType.PICKUP);
        h.assertFalse(menu.getCarried().isEmpty(), "the second tank isn't on the cursor");

        // Valid until something happens: a tick leaves it open.
        p.tick();
        h.assertTrue(p.containerMenu == menu, "a tick closed a valid menu");

        // A creative client may replace the stack: the menu no longer has the stack it opened on.
        ItemStack replacement = stack.copy();
        p.getInventory().setItem(p.getInventory().selected, replacement);
        click(p, FIRST, 0, ClickType.QUICK_MOVE);
        p.connection.handleContainerButtonClick(
                new ServerboundContainerButtonClickPacket(menu.containerId, TransferValveMenu.START));
        h.assertValueEqual(TransferValveItem.tankCount(replacement), 1, "tanks left on the replacement");
        h.assertFalse(replacement.isEmpty(), "the replacement started");
        h.assertFalse(menu.getCarried().isEmpty(), "the cursor lost its tank");

        p.tick();
        h.assertTrue(p.containerMenu == p.inventoryMenu, "the player's tick left a stale menu open");
        h.assertTrue(
                sent.stream().anyMatch(ClientboundContainerClosePacket.class::isInstance),
                "the client wasn't told to close");
        h.assertTrue(menu.getCarried().isEmpty(), "the closed menu kept its cursor");
        int found = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof GasTankItem && GasTankItem.gas(s).moles(H2) == H2_MOLES) {
                found++;
            }
        }
        h.assertValueEqual(found, 1, "the cursor's tank in the inventory");

        // Clicks for the closed menu's id find a different menu, and do nothing to it.
        int closed = menu.containerId;
        p.connection.handleContainerClick(new ServerboundContainerClickPacket(
                closed, 0, FIRST, 0, ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
        p.connection.handleContainerButtonClick(
                new ServerboundContainerButtonClickPacket(closed, TransferValveMenu.START));
        h.assertFalse(replacement.isEmpty(), "a late button started the TTV");
        h.assertValueEqual(TransferValveItem.tankCount(replacement), 1, "tanks after a late click");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** Through the packets, in either hand: the number key for the TTV's own place is refused. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_the_game_cant_swap_the_ttv_away_by_packet(GameTestHelper h) {
        Area a = new Area(h, 949);
        List<Packet<?>> sent = new ArrayList<>();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = loaded();
            ServerPlayer p = realPlayer(a, ItemStack.EMPTY, sent);
            p.setItemInHand(hand, stack);
            stack.use(a.level, p, hand);
            TransferValveMenu menu = (TransferValveMenu) p.containerMenu;
            int key = hand == InteractionHand.MAIN_HAND ? p.getInventory().selected : Inventory.SLOT_OFFHAND;
            int index = key;
            for (int slot : new int[] {slotOf(9), FIRST, SECOND, menu.valveSlot()}) {
                click(p, slot, key, ClickType.SWAP);
                h.assertTrue(p.getInventory().getItem(index) == stack, "the TTV left slot " + slot);
                h.assertValueEqual(TransferValveItem.tankCount(stack), 2, "tanks on");
            }
            click(p, menu.valveSlot(), 0, ClickType.THROW);
            click(p, menu.valveSlot(), 1, ClickType.THROW);
            click(p, menu.valveSlot(), 0, ClickType.QUICK_MOVE);
            click(p, menu.valveSlot(), 0, ClickType.PICKUP);
            h.assertTrue(p.getInventory().getItem(index) == stack, "the TTV left its place");
            h.assertTrue(menu.getCarried().isEmpty(), "the cursor took something");
            p.tick();
            h.assertTrue(p.containerMenu == menu, "the menu closed");
            p.closeContainer();
        }
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }
}
