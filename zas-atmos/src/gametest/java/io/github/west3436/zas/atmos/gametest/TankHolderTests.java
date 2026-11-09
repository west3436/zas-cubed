package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.gear.TankKind;
import io.github.west3436.zas.atmos.holder.TankHolderBlock;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The tank holder (CONT-149 to CONT-151): what it takes, that every tank comes back exactly once with
 * the item data it went in with, the two ways in and out, and the count and kinds the block shows. It
 * moves no gas itself, so the exploit to look for is a tank made or lost: the tests count every tank
 * and every mole after each step. The areas are 3720 and up.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TankHolderTests {
    private static final String BATCH = "zas_atmos_tank_holder";

    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");

    private static final int PLACES = TankHolderBlockEntity.PLACES;

    private TankHolderTests() {}

    // ---- Helpers ---------------------------------------------------------------------------

    /** A fake player that keeps what it drops. */
    private static final class TestPlayer extends FakePlayer {
        final List<ItemStack> drops = new ArrayList<>();

        TestPlayer(Area a) {
            super(a.level, new GameProfile(UUID.randomUUID(), "zas_tank_holder"));
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

    /** A holder at (2, 1, 4), its open front facing north, toward a player at (2, 1, 2). */
    private static TankHolderBlockEntity holder(Area a) {
        a.clear(0, 0, 4, 6);
        a.set(2, 1, 4, AtmosBlocks.TANK_HOLDER.get().defaultBlockState());
        return a.entity(2, 1, 4);
    }

    /** A hit on the holder's front. */
    private static BlockHitResult front(Area a) {
        BlockPos pos = a.abs(2, 1, 4);
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, -0.5), Direction.NORTH, pos, false);
    }

    /** Puts a stack in the main hand and right-clicks the holder's front with it, through the game's own path. */
    private static InteractionResult use(Area a, TestPlayer p, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        return p.gameMode.useItemOn(p, a.level, held, InteractionHand.MAIN_HAND, front(a));
    }

    /** Right-clicks the holder with an empty hand, and puts what that took into an inventory slot. */
    private static InteractionResult takeInto(Area a, TestPlayer p, int slot) {
        InteractionResult result = use(a, p, ItemStack.EMPTY);
        p.getInventory().setItem(slot, p.getMainHandItem());
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return result;
    }

    /** A plain 70 L tank of N2 and H2 at a temperature, with its own distribution pressure. */
    private static ItemStack plain(double n2, double h2, double kelvin, double distribution) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        Map<ResourceLocation, Double> moles = new TreeMap<>();
        moles.put(Area.N2, n2);
        moles.put(H2, h2);
        GasTankItem.setGas(tank, new GasParcel(moles, kelvin));
        GasTankItem.setDistributionPressure(tank, distribution);
        return tank;
    }

    private static ItemStack emergency(boolean filled) {
        ItemStack tank = new ItemStack(AtmosItems.EMERGENCY_OXYGEN_TANK.get());
        if (filled) {
            GasTankItem.setGas(tank, AtmosItems.emergencyFill());
        }
        return tank;
    }

    /** Ten tanks, no two alike: one of each kind, full and empty, and five plain ones of odd mixtures. */
    private static List<ItemStack> tenTanks() {
        List<ItemStack> tanks = new ArrayList<>();
        tanks.add(new ItemStack(AtmosItems.OXYGEN_TANK.get()));
        tanks.add(new ItemStack(AtmosItems.AIR_TANK.get()));
        tanks.add(emergency(true));
        tanks.add(emergency(false));
        tanks.add(new ItemStack(AtmosItems.EMPTY_TANK.get()));
        for (int i = 0; i < 5; i++) {
            tanks.add(plain(1.25 + i / 3.0, (i + 1) / 7.0, 250 + 17.3 * i, 10.0 + i / 9.0));
        }
        return tanks;
    }

    private static List<ItemStack> copies(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    /** What the holder holds, place by place. */
    private static List<ItemStack> held(TankHolderBlockEntity holder) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < PLACES; i++) {
            out.add(holder.tanks().getStackInSlot(i));
        }
        return out;
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= Area.REL * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /** Checks a tank is the one that went in: every component, and its gas to 1e-9 (DATA-016). */
    private static void same(GameTestHelper h, ItemStack actual, ItemStack expected, String what) {
        h.assertTrue(
                ItemStack.matches(actual, expected),
                what + ": got " + actual + " " + actual.getComponentsPatch() + ", expected " + expected + " "
                        + expected.getComponentsPatch());
        GasParcel got = GasTankItem.gas(actual);
        GasParcel want = GasTankItem.gas(expected);
        want.moles().forEach((gas, n) -> close(h, got.moles(gas), n, what + "'s " + gas));
        h.assertValueEqual(got.moles().size(), want.moles().size(), what + "'s gases");
        close(h, got.temperature(), want.temperature(), what + "'s temperature");
    }

    /** Checks the places hold these tanks, in this order, the rest empty, and the block shows the count. */
    private static void holds(
            GameTestHelper h, Area a, TankHolderBlockEntity holder, List<ItemStack> tanks, String when) {
        for (int i = 0; i < PLACES; i++) {
            ItemStack there = holder.tanks().getStackInSlot(i);
            if (i < tanks.size()) {
                same(h, there, tanks.get(i), "place " + i + " " + when);
            } else {
                h.assertTrue(there.isEmpty(), "place " + i + " " + when + " holds " + there);
            }
        }
        h.assertValueEqual(a.get(2, 1, 4).getValue(TankHolderBlock.TANKS), tanks.size(), "the count shown " + when);
    }

    /** Every tank, wherever it is: the player's inventory, what it dropped, the holder. */
    private static List<ItemStack> everyTank(TestPlayer p, TankHolderBlockEntity holder) {
        List<ItemStack> found = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            found.add(p.getInventory().getItem(i));
        }
        found.add(p.containerMenu.getCarried());
        found.addAll(p.drops);
        found.addAll(held(holder));
        List<ItemStack> tanks = new ArrayList<>();
        for (ItemStack stack : found) {
            if (stack.getItem() instanceof GasTankItem) {
                tanks.add(stack);
            } else if (stack.getItem() instanceof TransferValveItem) {
                tanks.add(TransferValveItem.tank(stack, TransferValve.FIRST));
                tanks.add(TransferValveItem.tank(stack, TransferValve.SECOND));
            } else if (stack.getItem() instanceof SuitItem) {
                tanks.add(SuitItem.tank(stack));
            }
        }
        return tanks.stream().filter(s -> !s.isEmpty()).toList();
    }

    /** Checks each of these tanks exists exactly once, somewhere, with the same item data. */
    private static void each(GameTestHelper h, List<ItemStack> found, List<ItemStack> tanks, String when) {
        int count = found.stream().mapToInt(ItemStack::getCount).sum();
        h.assertValueEqual(count, tanks.size(), "tanks " + when);
        List<ItemStack> left = new ArrayList<>(found);
        for (ItemStack tank : tanks) {
            ItemStack match = left.stream()
                    .filter(s -> ItemStack.matches(s, tank))
                    .findFirst()
                    .orElse(null);
            h.assertTrue(match != null, "a tank " + when + " is missing or changed: " + tank.getComponentsPatch());
            left.remove(match);
        }
        Map<ResourceLocation, Double> before = moles(tanks);
        Map<ResourceLocation, Double> after = moles(found);
        before.forEach((gas, n) -> close(h, after.getOrDefault(gas, 0.0), n, gas + " in all " + when));
        h.assertValueEqual(after.keySet(), before.keySet(), "the gases " + when);
    }

    private static Map<ResourceLocation, Double> moles(List<ItemStack> tanks) {
        Map<ResourceLocation, Double> total = new TreeMap<>();
        for (ItemStack tank : tanks) {
            GasTankItem.gas(tank).moles().forEach((gas, n) -> total.merge(gas, n * tank.getCount(), Double::sum));
        }
        return total;
    }

    /** The item entities that joined the level since {@link #watchDrops}. */
    private static final List<ItemEntity> DROPPED = new ArrayList<>();

    private static boolean watching;

    /**
     * Starts catching dropped items afresh. A level only finds entities in a chunk that is ticking
     * them, so these tests see each one as it joins. Each runs within one call, so nothing else adds to
     * this in the meantime.
     */
    private static void watchDrops() {
        if (!watching) {
            watching = true;
            NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
                if (e.getEntity() instanceof ItemEntity item) {
                    DROPPED.add(item);
                }
            });
        }
        DROPPED.clear();
    }

    /** The tanks dropped since {@link #watchDrops}, each as a stack; their entities are removed. */
    private static List<ItemStack> droppedTanks() {
        List<ItemStack> tanks = new ArrayList<>();
        for (ItemEntity item : DROPPED) {
            if (item.getItem().getItem() instanceof GasTankItem) {
                tanks.add(item.getItem().copy());
            }
            item.discard();
        }
        DROPPED.clear();
        return tanks;
    }

    // ---- CONT-149: what it holds, and keeping it --------------------------------------------

    /**
     * It takes a tank of every kind, full or empty, up to ten. An eleventh tank stays in the hand, and
     * the click stops there, so a client doesn't go on to open the tank's own screen. Nothing else is
     * taken, by a click or by its places, and a suit or a TTV in hand doesn't move a tank either way.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_149_it_holds_ten_tanks_and_nothing_else(GameTestHelper h) {
        Area a = new Area(h, 3720);
        TankHolderBlockEntity holder = holder(a);
        TestPlayer p = new TestPlayer(a);
        List<ItemStack> tanks = tenTanks();
        List<ItemStack> expected = copies(tanks);

        for (ItemStack tank : tanks) {
            h.assertTrue(use(a, p, tank).consumesAction(), "using " + tank + " on it did nothing");
            h.assertTrue(p.getMainHandItem().isEmpty(), "the hand kept " + p.getMainHandItem());
        }
        holds(h, a, holder, expected, "with ten");

        ItemStack eleventh = plain(2, 0.5, 300, 20);
        ItemStack eleventhBefore = eleventh.copy();
        h.assertValueEqual(
                a.get(2, 1, 4).useItemOn(eleventh, a.level, p, InteractionHand.MAIN_HAND, front(a)),
                ItemInteractionResult.CONSUME,
                "an eleventh tank on the block");
        h.assertTrue(use(a, p, eleventh).consumesAction(), "an eleventh tank's click went on");
        same(h, p.getMainHandItem(), eleventhBefore, "the eleventh tank in hand");
        h.assertFalse(holder.put(eleventh.copy()), "it took an eleventh tank");
        holds(h, a, holder, expected, "after an eleventh");

        while (holder.count() > 0) {
            holder.takeLast();
        }
        holds(h, a, holder, List.of(), "emptied");
        ItemStack suit = new ItemStack(AtmosItems.SOFTSUIT_CHESTPLATE.get());
        SuitItem.setTank(suit, new ItemStack(AtmosItems.OXYGEN_TANK.get()));
        List<ItemStack> others = List.of(
                new ItemStack(Items.STONE),
                new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get()),
                new ItemStack(AtmosItems.CANISTER.get()),
                suit,
                new ItemStack(AtmosItems.BATTERY.get()));
        for (ItemStack other : others) {
            ItemStack inHand = other.copy();
            p.setItemInHand(InteractionHand.MAIN_HAND, inHand);
            h.assertValueEqual(
                    a.get(2, 1, 4).useItemOn(inHand, a.level, p, InteractionHand.MAIN_HAND, front(a)),
                    ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "using " + other);
            h.assertValueEqual(
                    a.get(2, 1, 4).useWithoutItem(a.level, p, front(a)),
                    InteractionResult.PASS,
                    "using the block holding " + other);
            h.assertTrue(ItemStack.matches(p.getMainHandItem(), other), "the hand's " + other + " changed");
            h.assertFalse(holder.put(other.copy()), "it took " + other);
            h.assertFalse(holder.tanks().isItemValid(0, other), "a place takes " + other);
            h.assertValueEqual(
                    holder.tanks().insertItem(0, other.copy(), false).getCount(), 1, "a place took " + other);
        }
        holds(h, a, holder, List.of(), "offered other things");
        a.clear(0, 0, 4, 6);
        h.succeed();
    }

    /**
     * Saved to bytes as a chunk is, and read back, every tank is the one that went in, in its place:
     * its item, its components and its gas (DATA-016).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_149_tanks_come_back_as_they_went_in_after_a_save(GameTestHelper h) throws IOException {
        Area a = new Area(h, 3721);
        TankHolderBlockEntity holder = holder(a);
        List<ItemStack> tanks = tenTanks();
        List<ItemStack> expected = copies(tanks);
        tanks.forEach(holder::put);
        holds(h, a, holder, expected, "before saving");

        CompoundTag saved = holder.saveWithFullMetadata(a.level.registryAccess());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(saved, bytes);
        CompoundTag read =
                NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
        BlockState state = a.get(2, 1, 4);
        BlockEntity loaded = BlockEntity.loadStatic(holder.getBlockPos(), state, read, a.level.registryAccess());
        h.assertTrue(loaded instanceof TankHolderBlockEntity, "it read back as " + loaded);
        TankHolderBlockEntity reloaded = (TankHolderBlockEntity) loaded;
        for (int i = 0; i < PLACES; i++) {
            same(h, reloaded.tanks().getStackInSlot(i), expected.get(i), "place " + i + " after a save");
        }

        // A save with fewer, read into the holder in the level, as a command would write it.
        holder.takeLast();
        holder.takeLast();
        CompoundTag fewer = holder.saveWithoutMetadata(a.level.registryAccess());
        h.assertValueEqual(a.get(2, 1, 4).getValue(TankHolderBlock.TANKS), 8, "the count after taking two");
        holder.loadWithComponents(saved, a.level.registryAccess());
        h.assertTrue(
                a.level.getBlockTicks().hasScheduledTick(holder.getBlockPos(), AtmosBlocks.TANK_HOLDER.get()),
                "a wrong count wasn't put right");
        a.get(2, 1, 4).tick(a.level, holder.getBlockPos(), a.level.random);
        holds(h, a, holder, expected, "read from a save");
        holder.loadWithComponents(fewer, a.level.registryAccess());
        a.get(2, 1, 4).tick(a.level, holder.getBlockPos(), a.level.random);
        holds(h, a, holder, expected.subList(0, 8), "read from a save of eight");
        a.clear(0, 0, 4, 6);
        h.succeed();
    }

    /**
     * Its tanks are stored as in a chest: a tank above the leak pressure, which leaks when carried
     * (CONT-038), is unchanged after three seconds in it.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void cont_149_tanks_in_it_dont_change(GameTestHelper h) {
        Area a = new Area(h, 3722);
        TankHolderBlockEntity holder = holder(a);
        ItemStack leaky = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(leaky, new GasParcel(Map.of(Area.N2, 102.0), 293.15));
        double kPa = GasTankItem.contents(leaky).pressure();
        h.assertTrue(
                kPa > ContainerFailure.TANK_LEAK_KPA && kPa < ContainerFailure.TANK_RUPTURE_KPA,
                "the tank isn't between leaking and rupturing: " + kPa + " kPa");
        List<ItemStack> expected = copies(List.of(leaky, emergency(true)));
        holder.put(leaky);
        holder.put(emergency(true));
        h.runAfterDelay(60, () -> {
            holds(h, a, holder, expected, "after three seconds");
            a.clear(0, 0, 4, 6);
            h.succeed();
        });
    }

    /**
     * Taken out by hand, or dropped when it's broken, each tank exists once afterwards, as it went in,
     * and no gas is made or lost.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_149_tanks_come_back_as_they_went_in_taken_out_or_dropped(GameTestHelper h) {
        Area a = new Area(h, 3723);
        TankHolderBlockEntity holder = holder(a);
        TestPlayer p = new TestPlayer(a);
        List<ItemStack> tanks = tenTanks();
        List<ItemStack> expected = copies(tanks);
        tanks.forEach(holder::put);

        for (int i = 0; i < 3; i++) {
            takeInto(a, p, 9 + i);
            each(h, everyTank(p, holder), expected, "after " + (i + 1) + " taken out");
        }
        h.assertValueEqual(holder.count(), 7, "tanks left");

        watchDrops();
        a.level.destroyBlock(holder.getBlockPos(), true);
        List<ItemStack> dropped = droppedTanks();
        h.assertValueEqual(dropped.size(), 7, "tanks dropped");
        List<ItemStack> found = new ArrayList<>(everyTank(p, holder));
        found.addAll(dropped);
        each(h, found, expected, "after it was broken");
        h.assertTrue(held(holder).stream().allMatch(ItemStack::isEmpty), "the broken holder still holds tanks");
        a.clear(0, 0, 4, 6);
        h.succeed();
    }

    // ---- CONT-150: moving tanks in and out ----------------------------------------------------

    /**
     * Using a tank puts it in the first free place, and it leaves the hand, in creative too, so no copy
     * is left behind.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_150_using_a_tank_puts_it_in(GameTestHelper h) {
        Area a = new Area(h, 3724);
        TankHolderBlockEntity holder = holder(a);
        TestPlayer p = new TestPlayer(a);
        ItemStack oxygen = new ItemStack(AtmosItems.OXYGEN_TANK.get());
        ItemStack air = new ItemStack(AtmosItems.AIR_TANK.get());
        List<ItemStack> expected = copies(List.of(oxygen, air));

        h.assertTrue(use(a, p, oxygen).consumesAction(), "using a tank did nothing");
        h.assertTrue(p.getMainHandItem().isEmpty(), "the tank stayed in hand");
        p.setGameMode(GameType.CREATIVE);
        h.assertTrue(p.isCreative(), "the player isn't creative");
        h.assertTrue(use(a, p, air).consumesAction(), "using a tank in creative did nothing");
        h.assertTrue(p.getMainHandItem().isEmpty(), "a creative player kept a copy");
        holds(h, a, holder, expected, "after two uses");
        each(h, everyTank(p, holder), expected, "after two uses");
        a.clear(0, 0, 4, 6);
        h.succeed();
    }

    /**
     * An empty hand takes out the last tank put in, into that hand, so tanks come out in the reverse
     * of the order they went in. A hand holding something else takes nothing, and an empty holder gives
     * nothing. After each step every tank is there once.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_150_an_empty_hand_takes_out_the_last_tank_put_in(GameTestHelper h) {
        Area a = new Area(h, 3725);
        TankHolderBlockEntity holder = holder(a);
        TestPlayer p = new TestPlayer(a);
        List<ItemStack> tanks = List.of(
                new ItemStack(AtmosItems.OXYGEN_TANK.get()),
                new ItemStack(AtmosItems.OXYGEN_TANK.get()),
                new ItemStack(AtmosItems.AIR_TANK.get()),
                plain(3, 1, 290, 15),
                emergency(true));
        List<ItemStack> expected = copies(tanks);
        for (ItemStack tank : tanks) {
            use(a, p, tank);
        }
        holds(h, a, holder, expected, "with five");

        ItemStack stick = new ItemStack(Items.STICK);
        h.assertFalse(use(a, p, stick).consumesAction(), "a stick's click was taken");
        h.assertTrue(p.getMainHandItem() == stick && stick.getCount() == 1, "the stick left the hand");
        holds(h, a, holder, expected, "after a stick");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        for (int n = expected.size(); n > 0; n--) {
            h.assertTrue(use(a, p, ItemStack.EMPTY).consumesAction(), "an empty hand took nothing from " + n);
            same(h, p.getMainHandItem(), expected.get(n - 1), "the tank taken from " + n);
            p.getInventory().setItem(9 + n, p.getMainHandItem());
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            holds(h, a, holder, expected.subList(0, n - 1), "after taking from " + n);
            each(h, everyTank(p, holder), expected, "after taking from " + n);
        }

        h.assertValueEqual(use(a, p, ItemStack.EMPTY), InteractionResult.PASS, "an empty hand on an empty holder");
        h.assertTrue(p.getMainHandItem().isEmpty(), "an empty holder gave " + p.getMainHandItem());
        each(h, everyTank(p, holder), expected, "at the end");
        a.clear(0, 0, 4, 6);
        h.succeed();
    }

    // ---- CONT-151: what the block shows -------------------------------------------------------

    /**
     * The block shows as many tanks as it holds, at every count from 0 to 10 as they go in and come
     * out, and clients are sent each one's kind in order, which a client turns into its tint: O2 and
     * emergency O2 tanks blue, air tanks yellow, the rest grey.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_151_the_block_shows_as_many_tanks_as_it_holds(GameTestHelper h) {
        Area a = new Area(h, 3726);
        TankHolderBlockEntity holder = holder(a);
        TestPlayer p = new TestPlayer(a);
        List<ItemStack> tanks = tenTanks();
        List<TankKind> kinds = List.of(
                TankKind.OXYGEN,
                TankKind.AIR,
                TankKind.OXYGEN,
                TankKind.OXYGEN,
                TankKind.OTHER,
                TankKind.OTHER,
                TankKind.OTHER,
                TankKind.OTHER,
                TankKind.OTHER,
                TankKind.OTHER);
        for (int i = 0; i < tanks.size(); i++) {
            h.assertValueEqual(TankKind.of(tanks.get(i)), kinds.get(i), "the kind of " + tanks.get(i));
        }
        h.assertValueEqual(TankKind.OXYGEN.tint(), 0x0066FF, "O2's tint");
        h.assertValueEqual(TankKind.AIR.tint(), 0xF7C631, "air's tint");
        h.assertValueEqual(TankKind.OTHER.tint(), 0xC8C8C8, "the rest's tint");

        h.assertValueEqual(a.get(2, 1, 4).getValue(TankHolderBlock.TANKS), 0, "the count shown empty");
        tints(h, a, holder, List.of(), "empty");
        for (int i = 0; i < tanks.size(); i++) {
            use(a, p, tanks.get(i));
            h.assertValueEqual(a.get(2, 1, 4).getValue(TankHolderBlock.TANKS), i + 1, "the count shown going in");
            tints(h, a, holder, kinds.subList(0, i + 1), "with " + (i + 1) + " put in");
        }
        for (int n = PLACES - 1; n >= 0; n--) {
            takeInto(a, p, 9);
            p.getInventory().setItem(9, ItemStack.EMPTY);
            h.assertValueEqual(a.get(2, 1, 4).getValue(TankHolderBlock.TANKS), n, "the count shown coming out");
            tints(h, a, holder, kinds.subList(0, n), "with " + n + " left");
        }
        a.clear(0, 0, 4, 6);
        h.succeed();
    }

    /** Checks what a client is sent gives these tints, in order, and no tint past them. */
    private static void tints(
            GameTestHelper h, Area a, TankHolderBlockEntity holder, List<TankKind> kinds, String when) {
        TankHolderBlockEntity client = new TankHolderBlockEntity(holder.getBlockPos(), holder.getBlockState());
        client.handleUpdateTag(holder.getUpdateTag(a.level.registryAccess()), a.level.registryAccess());
        for (int i = 0; i < PLACES; i++) {
            int want = i < kinds.size() ? 0xFF000000 | kinds.get(i).tint() : -1;
            h.assertValueEqual(client.tint(i), want, "the tint of place " + i + " " + when);
        }
    }
}
