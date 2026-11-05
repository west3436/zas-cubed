package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.atmos.ttv.TransferValveSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The tank transfer valve (CONT-103, CONT-104). The gas tests need no world. The primed TTV tests
 * build a floor in their own {@link Area} (areas 90 and up), and drive the entity's tick themselves
 * within one game tick, except the one that checks the level ticks it.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransferValveTests {
    private static final String BATCH = "zas_atmos_ttv";

    /** For the tests that drive atmos ticks themselves. */
    private static final String FAILURE_BATCH = "zas_atmos_ttv_failure";

    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final ResourceLocation H2O = ResourceLocation.fromNamespaceAndPath("zas", "water_vapor");
    private static final double T = StandardAir.TEMPERATURE;
    private static final int MAIN = InteractionHand.MAIN_HAND.ordinal();

    private TransferValveTests() {}

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /** A plain 70 L tank holding a gas. */
    private static ItemStack tank(Map<ResourceLocation, Double> moles, double temperature) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, new GasParcel(moles, temperature));
        return tank;
    }

    /** A TTV stack with two tanks, its timer as given. */
    private static ItemStack ttv(ItemStack first, ItemStack second, int timer) {
        ItemStack stack = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        TransferValveItem.save(stack, new TransferValve(first, second, false));
        TransferValveItem.setTimer(stack, timer);
        return stack;
    }

    /** O2 too hot to stay unlit and little enough H2 that the burn stays below any tank limit (CONT-038). */
    private static ItemStack hotBomb(int timer) {
        return ttv(tank(Map.of(O2, 5.0), 1500), tank(Map.of(H2, 1.5), T), timer);
    }

    /** The same gases at room temperature: below H2's autoignition, they never light (FIRE-010). */
    private static ItemStack coldMix(int timer) {
        return ttv(tank(Map.of(O2, 5.0), T), tank(Map.of(H2, 1.5), T), timer);
    }

    /** A player on a floor in an area, holding a stack. */
    private static FakePlayer player(Area a, ItemStack held) {
        a.box(0, 0, 4, 4);
        FakePlayer p = FakePlayerFactory.get(a.level, new GameProfile(UUID.randomUUID(), "zas_ttv"));
        p.moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 2)));
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        return p;
    }

    /** Starts the timer of the TTV in a player's hand, through its screen's button. */
    private static PrimedTransferValve prime(GameTestHelper h, FakePlayer p) {
        ItemStack held = p.getMainHandItem();
        PrimedTransferValve primed = TransferValveItem.start(p, InteractionHand.MAIN_HAND);
        h.assertTrue(primed != null, "the TTV refused to prime");
        h.assertTrue(held.isEmpty(), "the primed TTV is still an item in the hand");
        return primed;
    }

    /** The items lying in an area: a level only finds entities in a chunk that is ticking them. */
    private static List<ItemEntity> dropped(Area a) {
        return a.level.getEntitiesOfClass(
                ItemEntity.class, AABB.encapsulatingFullBlocks(a.abs(0, 0, 0), a.abs(5, 5, 5)));
    }

    // ---- CONT-103 --------------------------------------------------------------------------

    /**
     * Opening moves all of the first tank's gas into the second, mixing temperatures; closing shares
     * it back by volume, here between 70 L and 2 L. Each tank's kind follows what it holds.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_opening_moves_the_gas_and_closing_shares_it_by_volume(GameTestHelper h) {
        ItemStack first = new ItemStack(AtmosItems.OXYGEN_TANK.get());
        ItemStack second = new ItemStack(AtmosItems.EMERGENCY_OXYGEN_TANK.get());
        GasTankItem.setGas(second, new GasParcel(Map.of(N2, 1.0), 500));
        TransferValve valve = new TransferValve(first, second, false);

        valve.open();
        h.assertTrue(valve.isOpen(), "the valve didn't open");
        h.assertTrue(GasTankItem.gas(valve.first()).isEmpty(), "the first tank kept gas");
        h.assertTrue(valve.first().is(AtmosItems.EMPTY_TANK.get()), "the emptied O2 tank is still an O2 tank");
        GasParcel merged = GasTankItem.gas(valve.second());
        close(h, merged.moles(O2), 17.5, "the second tank's O2");
        close(h, merged.moles(N2), 1.0, "the second tank's N2");
        double mixed = GasProperties.mixedTemperature(
                GasProperties.heatCapacity(Map.of(O2, 17.5)), T, GasProperties.heatCapacity(Map.of(N2, 1.0)), 500);
        close(h, merged.temperature(), mixed, "the merged gas's temperature");
        h.assertTrue(valve.second().is(AtmosItems.EMERGENCY_OXYGEN_TANK.get()), "the second tank changed kind");

        valve.close();
        h.assertFalse(valve.isOpen(), "the valve didn't close");
        GasParcel a = GasTankItem.gas(valve.first());
        GasParcel b = GasTankItem.gas(valve.second());
        close(h, a.moles(O2), 17.5 * 70 / 72, "the first tank's O2 share");
        close(h, a.moles(N2), 1.0 * 70 / 72, "the first tank's N2 share");
        close(h, b.moles(O2), 17.5 * 2 / 72, "the second tank's O2 share");
        close(h, b.moles(N2), 1.0 * 2 / 72, "the second tank's N2 share");
        close(h, a.temperature(), mixed, "the first tank's temperature");
        close(h, b.temperature(), mixed, "the second tank's temperature");
        close(
                h,
                GasTankItem.contents(valve.first()).pressure(),
                GasTankItem.contents(valve.second()).pressure(),
                "the tanks' pressures after sharing");
        h.succeed();
    }

    /** With a tank missing the valve joins nothing: no gas moves, and it closes again with what it had. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_without_both_tanks_no_gas_moves(GameTestHelper h) {
        ItemStack only = new ItemStack(AtmosItems.OXYGEN_TANK.get());
        TransferValve valve = new TransferValve(only, ItemStack.EMPTY, false);
        valve.open();
        close(h, GasTankItem.gas(valve.first()).moles(O2), 17.5, "the lone tank's O2 after opening");
        valve.close();
        close(h, GasTankItem.gas(valve.first()).moles(O2), 17.5, "the lone tank's O2 after closing");
        h.succeed();
    }

    /**
     * A hot oxidizer mixed with fuel above its autoignition burns in the tank within one atmos tick
     * (CONT-100, FIRE-010, FIRE-031), with no ignition source; the same gases cold don't. Closing the
     * valve afterwards shares out what the burn left.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_hot_mixture_burns_in_the_tank(GameTestHelper h) {
        Area a = new Area(h, 90);
        FakePlayer p = player(a, ItemStack.EMPTY);

        ItemStack hot = hotBomb(TransferValve.DEFAULT_TIMER);
        h.assertTrue(TransferValveItem.toggle(hot), "the valve refused to open");
        GasParcel merged = GasTankItem.gas(TransferValveItem.valve(hot).second());
        h.assertTrue(merged.temperature() > 858, "the merged gas is below autoignition: " + merged.temperature());
        h.assertValueEqual(merged.moles(H2O), 0.0, "water before the burn");

        h.assertFalse(TransferValveItem.step(hot, a.level, p), "the burn destroyed a TTV well below 30 atm");
        GasParcel burnt = GasTankItem.gas(TransferValveItem.valve(hot).second());
        h.assertValueEqual(burnt.moles(H2), 0.0, "H2 left after the burn");
        close(h, burnt.moles(H2O), 1.5, "water after the burn");
        close(h, burnt.moles(O2), 5.0 - 0.75, "O2 after the burn");
        h.assertTrue(burnt.temperature() > merged.temperature(), "the burn released no heat");
        h.assertTrue(GasTankItem.gas(TransferValveItem.valve(hot).first()).isEmpty(), "the first tank has gas in it");

        h.assertTrue(TransferValveItem.toggle(hot), "the valve refused to close");
        TransferValve shared = TransferValveItem.valve(hot);
        close(h, GasTankItem.gas(shared.first()).moles(H2O), 0.75, "the first tank's share of the water");
        close(h, GasTankItem.gas(shared.second()).moles(H2O), 0.75, "the second tank's share of the water");

        ItemStack cold = coldMix(TransferValve.DEFAULT_TIMER);
        h.assertTrue(TransferValveItem.toggle(cold), "the cold valve refused to open");
        h.assertFalse(TransferValveItem.step(cold, a.level, p), "the cold TTV was destroyed");
        GasParcel unlit = GasTankItem.gas(TransferValveItem.valve(cold).second());
        h.assertValueEqual(unlit.moles(H2O), 0.0, "water from the cold mixture");
        close(h, unlit.moles(H2), 1.5, "H2 in the cold mixture");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** A plain 70 L tank of N2 at a pressure. */
    private static ItemStack nitrogen(double atm) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, GasTankItem.fill(Map.of(N2, 1.0), atm * ContainerFailure.ATM_KPA, 70));
        return tank;
    }

    /** A player in a sealed room with a zone, ready for a TTV to fail beside them. */
    private static FakePlayer roomWithZone(Area a) {
        FakePlayer p = player(a, ItemStack.EMPTY);
        a.fill(2, 2, 2, Map.of(N2, 1.0));
        return p;
    }

    /**
     * A tank that explodes takes the TTV with it, whichever tank it is. Its blast is the tank failure's
     * own (CONT-038), at the holder's middle, and the other tank's gas goes into the cell with its own.
     */
    @GameTest(template = "empty", batch = FAILURE_BATCH)
    public static void cont_103_a_tank_that_explodes_destroys_the_ttv(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 103);
        FakePlayer p = roomWithZone(a);
        double room = a.zone(2, 2, 2).mixture().totalMoles();
        for (boolean firstFails : new boolean[] {true, false}) {
            ItemStack burst = nitrogen(60);
            ItemStack other = tank(Map.of(O2, 5.0), T);
            room += GasTankItem.gas(burst).totalMoles() + 5;
            ItemStack bomb = firstFails
                    ? ttv(burst, other, TransferValve.DEFAULT_TIMER)
                    : ttv(other, burst, TransferValve.DEFAULT_TIMER);

            h.assertTrue(TransferValveItem.step(bomb, a.level, p), "the TTV outlasted its tank");
            a.tick();
            double now = a.zone(2, 2, 2).mixture().totalMoles();
            h.assertTrue(Math.abs(now - room) < 1e-6, "the gas in the room: expected " + room + ", got " + now);
            List<Explosion> blasts = Blasts.take(a.point(2, 2, 2));
            h.assertValueEqual(blasts.size(), 1, "explosions");
            h.assertTrue(
                    Math.abs(blasts.get(0).radius() - 1) < 1e-4,
                    "the power at 60 atm: " + blasts.get(0).radius());
        }
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** A primed TTV goes with its tank, and doesn't stay on to drop as a dud. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_primed_ttv_goes_off_with_its_tank(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 108);
        FakePlayer p = player(a, ttv(tank(Map.of(O2, 5.5), 1400), tank(Map.of(H2, 5.0), T), 0));
        PrimedTransferValve primed = prime(h, p);
        primed.tick();
        h.assertTrue(primed.isRemoved(), "the primed TTV outlasted its tank");
        h.assertValueEqual(Blasts.take(a.point(2, 2, 2)).size(), 1, "explosions");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /**
     * A tank that ruptures bursts alone, with no blast: its gas goes into the cell, and the TTV stays,
     * with the other tank and its gas.
     */
    @GameTest(template = "empty", batch = FAILURE_BATCH)
    public static void cont_103_a_tank_that_ruptures_leaves_the_ttv(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 104);
        FakePlayer p = roomWithZone(a);
        double room = a.zone(2, 2, 2).mixture().totalMoles();
        for (boolean firstFails : new boolean[] {true, false}) {
            ItemStack burst = nitrogen(45);
            ItemStack other = tank(Map.of(O2, 5.0), T);
            room += GasTankItem.gas(burst).totalMoles();
            ItemStack stack = firstFails
                    ? ttv(burst, other, TransferValve.DEFAULT_TIMER)
                    : ttv(other, burst, TransferValve.DEFAULT_TIMER);

            h.assertFalse(TransferValveItem.step(stack, a.level, p), "a rupture destroyed the TTV");
            a.tick();
            double now = a.zone(2, 2, 2).mixture().totalMoles();
            h.assertTrue(Math.abs(now - room) < 1e-6, "the gas in the room: expected " + room + ", got " + now);
            TransferValve left = TransferValveItem.valve(stack);
            h.assertTrue((firstFails ? left.first() : left.second()).isEmpty(), "the burst tank is still on");
            close(h, GasTankItem.gas(firstFails ? left.second() : left.first()).moles(O2), 5.0, "the other tank's O2");
        }
        h.assertTrue(Blasts.take(a.point(2, 2, 2)).isEmpty(), "a rupture exploded");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** A leaking tank stays on the TTV, which is written back with what the tank has left. */
    @GameTest(template = "empty", batch = FAILURE_BATCH)
    public static void cont_103_a_leaking_tank_stays_on_the_ttv(GameTestHelper h) {
        Area a = new Area(h, 105);
        FakePlayer p = roomWithZone(a);
        double room = a.zone(2, 2, 2).mixture().totalMoles();
        ItemStack leaky = nitrogen(35);
        double held = GasTankItem.gas(leaky).totalMoles();
        ItemStack stack = ttv(tank(Map.of(O2, 5.0), T), leaky, TransferValve.DEFAULT_TIMER);

        h.assertFalse(TransferValveItem.step(stack, a.level, p), "a leak destroyed the TTV");
        a.tick();
        double left = GasTankItem.gas(TransferValveItem.valve(stack).second()).totalMoles();
        h.assertTrue(left < held, "the saved tank didn't lose its gas: " + left + " of " + held);
        h.assertTrue(
                Math.abs(a.zone(2, 2, 2).mixture().totalMoles() - (room + held - left)) < 1e-6,
                "the gas in the room: expected " + (room + held - left) + ", got "
                        + a.zone(2, 2, 2).mixture().totalMoles());
        close(h, GasTankItem.gas(TransferValveItem.valve(stack).first()).moles(O2), 5.0, "the other tank's O2");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** In an inventory the TTV's own tick runs its tanks, and a failure uses the stack up. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_tank_failing_in_an_inventory_takes_the_ttv_with_it(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 106);
        a.forceLoad(true);
        ItemStack bomb = ttv(tank(Map.of(O2, 5.0), T), nitrogen(60), TransferValve.DEFAULT_TIMER);
        FakePlayer p = player(a, bomb);
        List<Explosion> blasts = new ArrayList<>();

        h.onEachTick(() -> p.getInventory().tick());
        h.succeedWhen(() -> {
            blasts.addAll(Blasts.take(a.point(2, 2, 2)));
            h.assertTrue(bomb.isEmpty(), "the TTV is still in the inventory");
            h.assertValueEqual(blasts.size(), 1, "explosions");
            a.clear(-1, -1, 5, 5);
            a.forceLoad(false);
        });
    }

    /** On the ground the same, and the item entity goes with it. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_103_a_tank_failing_on_the_ground_takes_the_ttv_with_it(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 107);
        a.forceLoad(true);
        a.box(0, 0, 4, 4);
        Vec3 at = a.point(2, 1, 2);
        ItemEntity item = new ItemEntity(
                a.level, at.x, at.y, at.z, ttv(tank(Map.of(O2, 5.0), T), nitrogen(60), TransferValve.DEFAULT_TIMER));
        // A blast destroys a dropped item by itself, and here it should be the TTV that removes it.
        item.setInvulnerable(true);
        a.level.addFreshEntity(item);
        List<Explosion> blasts = new ArrayList<>();
        boolean[] outlasted = {false};

        // Asked as soon as the blast is seen: an item left there would later despawn all the same.
        h.succeedWhen(() -> {
            blasts.addAll(Blasts.take(a.point(2, 2, 2)));
            h.assertValueEqual(blasts.size(), 1, "explosions");
            outlasted[0] |= !item.isRemoved();
            h.assertFalse(outlasted[0], "the dropped TTV outlasted its tank");
            a.clear(-1, -1, 5, 5);
            a.forceLoad(false);
        });
    }

    /**
     * Tanks go on and come off by right-clicks in an inventory, as a bundle's contents do: only tanks,
     * only two, only while the valve is closed. Taking a tank off an open valve closes it first, so
     * the gas is shared back and the tank taken has its share.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_tanks_go_on_and_come_off_in_an_inventory(GameTestHelper h) {
        Area a = new Area(h, 91);
        FakePlayer p = player(a, ItemStack.EMPTY);
        ItemStack stack = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        TransferValveItem item = AtmosItems.TANK_TRANSFER_VALVE.get();
        Slot slot = new Slot(new SimpleContainer(1), 0, 0, 0);
        slot.set(stack);
        ItemStack[] cursor = {new ItemStack(AtmosItems.OXYGEN_TANK.get())};
        SlotAccess access = SlotAccess.of(() -> cursor[0], st -> cursor[0] = st);

        h.assertFalse(
                item.overrideOtherStackedOnMe(stack, cursor[0], slot, ClickAction.PRIMARY, p, access),
                "a left click fitted a tank");
        h.assertFalse(
                item.overrideOtherStackedOnMe(
                        stack, new ItemStack(AtmosItems.MULTITOOL.get()), slot, ClickAction.SECONDARY, p, access),
                "it took a multitool");

        h.assertTrue(
                item.overrideOtherStackedOnMe(stack, cursor[0], slot, ClickAction.SECONDARY, p, access),
                "it refused the first tank");
        h.assertTrue(cursor[0].isEmpty(), "the cursor kept the first tank");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 1, "tanks fitted");
        h.assertTrue(TransferValveItem.valve(stack).first().is(AtmosItems.OXYGEN_TANK.get()), "the first tank");

        // Carrying the TTV onto a tank fits it as well.
        Slot other = new Slot(new SimpleContainer(1), 0, 0, 0);
        other.set(tank(Map.of(H2, 2.0), T));
        h.assertTrue(item.overrideStackedOnOther(stack, other, ClickAction.SECONDARY, p), "it refused the second tank");
        h.assertTrue(other.getItem().isEmpty(), "the slot kept the second tank");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 2, "tanks fitted");
        close(h, GasTankItem.gas(TransferValveItem.valve(stack).second()).moles(H2), 2.0, "the second tank's H2");

        // It's full, then its valve is open: no third tank either way.
        ItemStack third = new ItemStack(AtmosItems.AIR_TANK.get());
        h.assertFalse(
                item.overrideOtherStackedOnMe(stack, third, slot, ClickAction.SECONDARY, p, access),
                "it took a third tank");
        h.assertTrue(TransferValveItem.toggle(stack), "the valve refused to open");

        // An empty cursor takes the last tank: closing the valve shares the gas back first.
        h.assertTrue(
                item.overrideOtherStackedOnMe(stack, ItemStack.EMPTY, slot, ClickAction.SECONDARY, p, access),
                "an empty cursor took nothing");
        h.assertFalse(TransferValveItem.valve(stack).isOpen(), "the valve is open with one tank");
        close(h, GasTankItem.gas(cursor[0]).moles(H2), 1.0, "the taken tank's H2");
        close(h, GasTankItem.gas(cursor[0]).moles(O2), 8.75, "the taken tank's O2");
        close(h, GasTankItem.gas(TransferValveItem.valve(stack).first()).moles(O2), 8.75, "the remaining tank's O2");
        h.assertValueEqual(TransferValveItem.tankCount(stack), 1, "tanks left");

        // A tank can't go on an open valve: only a valve holding both can be open, so check the rule directly.
        TransferValve open = new TransferValve(ItemStack.EMPTY, ItemStack.EMPTY, true);
        h.assertFalse(open.attach(new ItemStack(AtmosItems.AIR_TANK.get())), "a tank went on an open valve");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- CONT-104 --------------------------------------------------------------------------

    /**
     * The valve opens and closes by hand, through the screen's button, which the server checks
     * (CONT-074): it needs both tanks, a known hand and button, and a hand holding a TTV.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_the_valve_opens_by_hand(GameTestHelper h) {
        Area a = new Area(h, 92);
        ItemStack stack = coldMix(TransferValve.DEFAULT_TIMER);
        FakePlayer p = player(a, stack);
        TransferValveSettings valve = new TransferValveSettings(MAIN, TransferValveSettings.VALVE);

        h.assertTrue(TransferValveSettings.apply(p, valve), "the valve refused to open by hand");
        h.assertTrue(TransferValveItem.valve(stack).isOpen(), "the valve isn't open");
        h.assertTrue(GasTankItem.gas(TransferValveItem.valve(stack).first()).isEmpty(), "the first tank kept gas");
        close(h, GasTankItem.gas(TransferValveItem.valve(stack).second()).moles(O2), 5.0, "the merged O2");

        h.assertTrue(TransferValveSettings.apply(p, valve), "the valve refused to close by hand");
        h.assertFalse(TransferValveItem.valve(stack).isOpen(), "the valve isn't closed");

        h.assertFalse(TransferValveSettings.apply(p, new TransferValveSettings(MAIN, 9)), "an unknown button");
        h.assertFalse(TransferValveSettings.apply(p, new TransferValveSettings(MAIN, -1)), "a negative button");
        h.assertFalse(
                TransferValveSettings.apply(p, new TransferValveSettings(2, TransferValveSettings.VALVE)),
                "an unknown hand");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.MULTITOOL.get()));
        h.assertFalse(TransferValveSettings.apply(p, valve), "a hand holding no TTV");

        ItemStack empty = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, empty);
        h.assertFalse(TransferValveSettings.apply(p, valve), "a TTV with no tanks opened");
        h.assertFalse(TransferValveItem.valve(empty).isOpen(), "a TTV with no tanks is open");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** The timer runs from 0 to 600 s and defaults to 10 s, and the server clamps every setting. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_the_timer_is_clamped_to_its_range(GameTestHelper h) {
        Area a = new Area(h, 93);
        ItemStack stack = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        FakePlayer p = player(a, stack);
        h.assertValueEqual(TransferValveItem.timer(stack), 10, "a new TTV's timer");
        press(h, p, TransferValveSettings.UP_10);
        h.assertValueEqual(TransferValveItem.timer(stack), 20, "after +10");
        press(h, p, TransferValveSettings.DOWN_1);
        h.assertValueEqual(TransferValveItem.timer(stack), 19, "after -1");
        press(h, p, TransferValveSettings.UP_1);
        press(h, p, TransferValveSettings.DOWN_10);
        press(h, p, TransferValveSettings.DOWN_10);
        press(h, p, TransferValveSettings.DOWN_10);
        h.assertValueEqual(TransferValveItem.timer(stack), 0, "the floor");
        for (int i = 0; i < 61; i++) {
            press(h, p, TransferValveSettings.UP_10);
        }
        h.assertValueEqual(TransferValveItem.timer(stack), 600, "the ceiling");
        press(h, p, TransferValveSettings.DEFAULT);
        h.assertValueEqual(TransferValveItem.timer(stack), 10, "back to the default");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    private static void press(GameTestHelper h, FakePlayer p, int button) {
        h.assertTrue(TransferValveSettings.apply(p, new TransferValveSettings(MAIN, button)), "button " + button);
    }

    /** As an item it never runs its timer (the owner's decision): only starting the timer primes it. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_an_item_never_runs_its_timer(GameTestHelper h) {
        Area a = new Area(h, 94);
        ItemStack stack = coldMix(1);
        FakePlayer p = player(a, stack);
        for (int i = 0; i < 100; i++) {
            stack.inventoryTick(a.level, p, 0, true);
        }
        h.assertValueEqual(stack.getCount(), 1, "the TTV in the hand");
        h.assertFalse(TransferValveItem.valve(stack).isOpen(), "the valve opened as an item");
        h.assertValueEqual(TransferValveItem.timer(stack), 1, "the timer setting");
        h.assertTrue(
                a.level
                        .getEntitiesOfClass(PrimedTransferValve.class, new AABB(p.blockPosition()).inflate(4))
                        .isEmpty(),
                "an item primed itself");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** At 0 s the valve opens as soon as the timer starts, and a hot mixture burns at the next tick. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_at_0_s_the_valve_opens_when_the_timer_starts(GameTestHelper h) {
        Area a = new Area(h, 95);
        FakePlayer p = player(a, hotBomb(0));
        PrimedTransferValve primed = prime(h, p);
        try {
            h.assertTrue(primed.valve().isOpen(), "the valve didn't open at 0 s");
            h.assertTrue(primed.valveOpen(), "clients aren't told the valve is open");
            h.assertTrue(GasTankItem.gas(primed.valve().first()).isEmpty(), "the first tank kept gas");
            close(h, GasTankItem.gas(primed.valve().second()).moles(H2), 1.5, "H2 before the first tick");
            primed.tick();
            h.assertValueEqual(GasTankItem.gas(primed.valve().second()).moles(H2), 0.0, "H2 after the first tick");
        } finally {
            primed.discard();
            a.clear(-1, -1, 5, 5);
        }
        h.succeed();
    }

    /** A primed TTV's valve stays closed until its timer runs out, then opens, moving the gas. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_the_valve_opens_when_the_timer_runs_out(GameTestHelper h) {
        Area a = new Area(h, 96);
        FakePlayer p = player(a, coldMix(2));
        PrimedTransferValve primed = prime(h, p);
        try {
            h.assertValueEqual(primed.timer(), 2, "the primed timer");
            for (int tick = 0; tick < 39; tick++) {
                primed.tick();
                h.assertFalse(primed.valve().isOpen(), "the valve opened early, tick " + tick);
            }
            close(h, GasTankItem.gas(primed.valve().first()).moles(O2), 5.0, "the first tank before the valve opens");
            primed.tick();
            h.assertTrue(primed.valve().isOpen(), "the valve didn't open when the timer ran out");
            h.assertTrue(primed.valveOpen(), "clients aren't told the valve is open");
            h.assertTrue(GasTankItem.gas(primed.valve().first()).isEmpty(), "the first tank kept gas");
            close(h, GasTankItem.gas(primed.valve().second()).moles(O2), 5.0, "the merged O2");
        } finally {
            primed.discard();
            a.clear(-1, -1, 5, 5);
        }
        h.succeed();
    }

    /** The level ticks a primed TTV in a loaded chunk on its own, so its timer does run out. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_104_a_primed_valve_ticks_in_the_world(GameTestHelper h) {
        Area a = new Area(h, 97);
        a.forceLoad(true);
        FakePlayer p = player(a, coldMix(1));
        PrimedTransferValve primed = prime(h, p);
        h.succeedWhen(() -> {
            h.assertTrue(primed.valve().isOpen(), "the valve hasn't opened");
            primed.discard();
            a.forceLoad(false);
        });
    }

    /**
     * No one can pick a primed TTV up or use it, with an empty hand or anything else, nor hit it. It
     * can't be targeted, and using or attacking it directly does nothing to it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_a_primed_valve_cant_be_picked_up_or_used(GameTestHelper h) {
        Area a = new Area(h, 98);
        FakePlayer p = player(a, coldMix(60));
        PrimedTransferValve primed = prime(h, p);
        try {
            h.assertFalse(primed.isPickable(), "a crosshair can target it");
            h.assertFalse(primed.isAttackable(), "it can be attacked");
            h.assertTrue(primed.getPickResult() == null, "creative pick gives an item");

            h.assertValueEqual(primed.interact(p, InteractionHand.MAIN_HAND), InteractionResult.PASS, "using it");
            h.assertValueEqual(
                    p.interactOn(primed, InteractionHand.MAIN_HAND),
                    InteractionResult.PASS,
                    "using it with an empty hand");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.OXYGEN_TANK.get()));
            h.assertValueEqual(
                    p.interactOn(primed, InteractionHand.MAIN_HAND), InteractionResult.PASS, "using a tank on it");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            h.assertFalse(primed.hurt(a.level.damageSources().playerAttack(p), 1000), "hurting it");
            p.attack(primed);
            h.assertTrue(primed.isAlive(), "it was destroyed");
            h.assertValueEqual(primed.timer(), 60, "its timer");
            h.assertValueEqual(primed.fuse(), 1200, "its fuse");
            h.assertFalse(primed.valve().isOpen(), "its valve");
            close(h, GasTankItem.gas(primed.valve().first()).moles(O2), 5.0, "its first tank");
            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                h.assertTrue(p.getInventory().getItem(slot).isEmpty(), "the player got something: slot " + slot);
            }
        } finally {
            primed.discard();
            a.clear(-1, -1, 5, 5);
        }
        h.succeed();
    }

    /** A primed TTV that survives its valve opening drops as an item again, its valve as it left it. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_104_a_dud_drops_as_an_item_again(GameTestHelper h) {
        Area a = new Area(h, 99);
        a.forceLoad(true);
        FakePlayer p = player(a, coldMix(0));
        PrimedTransferValve primed = prime(h, p);
        for (int tick = 0; tick < PrimedTransferValve.SETTLE_TICKS - 1; tick++) {
            primed.tick();
            h.assertFalse(primed.isRemoved(), "it dropped early, tick " + tick);
        }
        primed.tick();
        h.assertTrue(primed.isRemoved(), "the dud is still an entity");
        h.succeedWhen(() -> {
            List<ItemEntity> drops = dropped(a);
            h.assertValueEqual(drops.size(), 1, "dropped items");
            ItemStack stack = drops.get(0).getItem();
            h.assertTrue(stack.is(AtmosItems.TANK_TRANSFER_VALVE.get()), "it dropped " + stack);
            TransferValve valve = TransferValveItem.valve(stack);
            h.assertTrue(valve.isOpen(), "the valve closed");
            close(h, GasTankItem.gas(valve.second()).moles(O2), 5.0, "the dropped TTV's merged O2");
            close(h, GasTankItem.gas(valve.second()).moles(H2), 1.5, "the dropped TTV's merged H2");
            h.assertValueEqual(TransferValveItem.timer(stack), 0, "the dropped TTV's timer");
            drops.forEach(ItemEntity::discard);
            a.clear(-1, -1, 5, 5);
            a.forceLoad(false);
        });
    }

    /** Saving keeps a primed TTV's tanks and how long it has left. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_a_primed_valve_is_saved(GameTestHelper h) {
        Area a = new Area(h, 100);
        FakePlayer p = player(a, coldMix(30));
        PrimedTransferValve primed = prime(h, p);
        try {
            primed.tick();
            CompoundTag tag = primed.saveWithoutId(new CompoundTag());
            PrimedTransferValve loaded =
                    new PrimedTransferValve(AtmosEntities.PRIMED_TANK_TRANSFER_VALVE.get(), a.level);
            loaded.load(tag);
            h.assertValueEqual(loaded.timer(), 30, "the timer");
            h.assertValueEqual(loaded.fuse(), primed.fuse(), "the fuse");
            h.assertFalse(loaded.valve().isOpen(), "the valve");
            close(h, GasTankItem.gas(loaded.valve().first()).moles(O2), 5.0, "the first tank's O2");
            close(h, GasTankItem.gas(loaded.valve().second()).moles(H2), 1.5, "the second tank's H2");
            GasMixture second = GasTankItem.contents(loaded.secondTank());
            close(h, second.moles(H2), 1.5, "the second tank clients are told of");
        } finally {
            primed.discard();
            a.clear(-1, -1, 5, 5);
        }
        h.succeed();
    }

    /** A primed TTV won't start twice: an open valve refuses the timer. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_an_open_valve_wont_start_its_timer(GameTestHelper h) {
        Area a = new Area(h, 101);
        ItemStack stack = coldMix(5);
        FakePlayer p = player(a, stack);
        h.assertTrue(TransferValveItem.toggle(stack), "the valve refused to open");
        h.assertFalse(
                TransferValveSettings.apply(p, new TransferValveSettings(MAIN, TransferValveSettings.START)),
                "an open valve started its timer");
        h.assertValueEqual(stack.getCount(), 1, "the TTV in the hand");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** The screen's take buttons put a tank in the inventory, closing an open valve first. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_the_screen_takes_tanks_off(GameTestHelper h) {
        Area a = new Area(h, 102);
        ItemStack stack = coldMix(5);
        FakePlayer p = player(a, stack);
        h.assertTrue(TransferValveItem.toggle(stack), "the valve refused to open");
        press(h, p, TransferValveSettings.TAKE_FIRST);
        h.assertFalse(TransferValveItem.valve(stack).isOpen(), "the valve stayed open");
        h.assertTrue(TransferValveItem.valve(stack).first().isEmpty(), "the first tank is still on");
        ItemStack taken = p.getInventory().getItem(1);
        close(h, GasTankItem.gas(taken).moles(O2), 2.5, "the taken tank's O2");
        close(h, GasTankItem.gas(TransferValveItem.valve(stack).second()).moles(O2), 2.5, "the remaining tank's O2");
        h.assertFalse(
                TransferValveSettings.apply(p, new TransferValveSettings(MAIN, TransferValveSettings.TAKE_FIRST)),
                "it took a tank that isn't there");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }
}
