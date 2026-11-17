package io.github.west3436.zas.compat.ae2.gametest;

import static io.github.west3436.zas.compat.ae2.gametest.Lab.CO2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.N2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.O2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.T;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.compat.ae2.Ae2Settings;
import io.github.west3436.zas.compat.ae2.GasHandlerStorage;
import io.github.west3436.zas.compat.ae2.GasKey;
import io.github.west3436.zas.compat.ae2.GasKeyType;
import io.github.west3436.zas.compat.ae2.GasUnits;
import io.netty.buffer.Unpooled;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Gases in ME storage (COMPAT-070 to COMPAT-074), with AE2 and its buses as a player has them, on a
 * network powered by a creative energy cell, and {@link TestTank}s for another mod's blocks. None of
 * them needs the content mod, so they run with it off too. The ones with ZAS³'s own pipes and tanks
 * are in {@link Ae2ContentTests}.
 *
 * <p>Each test that builds a network waits for AE2: its nodes are ready a tick after they are placed,
 * and its buses work every 5 to 60 ticks.
 */
@GameTestHolder(Ae2Tests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class Ae2Tests {
    /** The namespace of the tests for a supported AE2. */
    public static final String NAMESPACE = "zas_ae2";

    /** The namespace of the tests for an unsupported AE2. */
    public static final String OFF = "zas_ae2_off";

    private static final String BATCH = "zas_ae2";
    private static final int WAIT = 600;

    private Ae2Tests() {}

    // ---- COMPAT-070: each gas its own key --------------------------------------------------------

    /** A key is its gas: two of one gas are one key, and it is saved and sent as the gas's id. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_070_each_gas_is_its_own_key(GameTestHelper h) {
        GasKey o2 = GasKey.of(O2);
        h.assertTrue(o2.equals(GasKey.of(O2)) && o2.hashCode() == GasKey.of(O2).hashCode(), "two O2 keys differ");
        h.assertFalse(o2.equals(GasKey.of(N2)), "O2 and N2 are one key");
        h.assertTrue(o2.getType() == GasKeyType.TYPE, "an O2 key's type");
        h.assertValueEqual(o2.getId(), O2, "an O2 key's id");
        h.assertTrue(
                o2.getDisplayName().getContents() instanceof TranslatableContents name
                        && name.getKey().equals(GasProperties.translationKey(O2)),
                "an O2 key's name is " + o2.getDisplayName());

        var registries = h.getLevel().registryAccess();
        CompoundTag saved = o2.toTagGeneric(registries);
        h.assertValueEqual(AEKey.fromTagGeneric(registries, saved), o2, "an O2 key saved and loaded");

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        AEKey.writeKey(buffer, o2);
        h.assertValueEqual(AEKey.readKey(buffer), o2, "an O2 key sent and read");
        h.succeed();
    }

    /**
     * A storage bus on another mod's tank shows each gas in it as its own stack, in whole millimoles:
     * what is under one isn't shown, and stays in the tank.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_070_a_storage_bus_shows_each_gas(GameTestHelper h) {
        Lab lab = new Lab(h, 0);
        lab.tank(0, 1, 0, 1000, T).with(O2, 2.5).with(N2, 1.0004).with(CO2, 0.0009);
        lab.cable(1, 1, 0);
        IPart bus = lab.part(1, 1, 0, Direction.WEST, "storage_bus");
        lab.energy(1, 2, 0);
        h.succeedWhen(() -> {
            KeyCounter stored = Lab.stored(bus);
            h.assertValueEqual(stored.get(GasKey.of(O2)), 2500L, "the O2 the network holds, in mmol");
            h.assertValueEqual(stored.get(GasKey.of(N2)), 1000L, "the N2 the network holds, in mmol");
            h.assertValueEqual(stored.get(GasKey.of(CO2)), 0L, "the CO2 the network holds, in mmol");
            lab.done();
        });
    }

    // ---- COMPAT-071: a mixture is stored as its gases ---------------------------------------------

    /**
     * An import bus takes a mixture out of a tank gas by gas, and two storage buses, each with one gas
     * in its filter, keep the gases apart: each tank behind them ends with one gas. A fraction of a
     * millimole of each stays behind, and no gas is made or lost.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_071_an_import_bus_stores_a_mixture_as_its_gases(GameTestHelper h) {
        Lab lab = new Lab(h, 1);
        TestTank mixed = lab.tank(0, 1, 0, 1000, T).with(O2, 2.0004).with(N2, 7.5);
        TestTank oxygen = lab.tank(3, 1, -1, 1000, T);
        TestTank nitrogen = lab.tank(3, 1, 1, 1000, T);
        lab.cable(1, 1, 0);
        lab.part(1, 1, 0, Direction.WEST, "import_bus");
        lab.cable(2, 1, 0);
        lab.energy(2, 2, 0);
        lab.cable(3, 1, 0);
        Lab.filter(lab.part(3, 1, 0, Direction.NORTH, "storage_bus"), O2);
        Lab.filter(lab.part(3, 1, 0, Direction.SOUTH, "storage_bus"), N2);
        h.succeedWhen(() -> {
            lab.close(oxygen.moles(O2), 2.0, "the O2 in the oxygen tank");
            lab.close(nitrogen.moles(N2), 7.5, "the N2 in the nitrogen tank");
            lab.close(oxygen.moles(N2), 0, "the N2 in the oxygen tank");
            lab.close(nitrogen.moles(O2), 0, "the O2 in the nitrogen tank");
            lab.close(mixed.moles(O2), 0.0004, "the O2 left in the mixture's tank");
            lab.close(mixed.moles(N2), 0, "the N2 left in the mixture's tank");
            lab.close(mixed.total() + oxygen.total() + nitrogen.total(), 9.5004, "all the gas");
            lab.done();
        });
    }

    // ---- COMPAT-072: the temperature gas leaves at ------------------------------------------------

    /**
     * Gas leaves ME storage at the set temperature, 293.15 K unless a pack sets another, whatever
     * temperature it went in at, and mixes with what the tank it goes into holds. Here a hot tank
     * behind a storage bus is ME storage, an import bus takes in gas hotter still, and an export bus
     * gives all of it to a warm tank of nitrogen.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_072_gas_leaves_me_storage_at_the_set_temperature(GameTestHelper h) {
        Lab lab = new Lab(h, 2);
        h.assertValueEqual(Ae2Settings.leavingTemperature(), 293.15, "the temperature gas leaves at");
        TestTank store = lab.tank(2, 1, 0, 1000, 500).with(O2, 4.0);
        TestTank hotter = lab.tank(0, 1, 2, 1000, 600).with(O2, 1.0);
        TestTank warm = lab.tank(0, 1, 0, 1000, 350).with(N2, 2.0);
        lab.cable(1, 1, 0);
        lab.part(1, 1, 0, Direction.EAST, "storage_bus");
        Lab.filter(lab.part(1, 1, 0, Direction.WEST, "export_bus"), O2);
        lab.energy(1, 2, 0);
        lab.cable(1, 1, 1);
        lab.cable(1, 1, 2);
        lab.part(1, 1, 2, Direction.WEST, "import_bus");
        // Gas mixes at the same temperature whatever order it comes in, so the end is known.
        double expected = GasProperties.mix(new GasParcel(Map.of(N2, 2.0), 350), new GasParcel(Map.of(O2, 5.0), 293.15))
                .temperature();
        h.succeedWhen(() -> {
            lab.close(hotter.total(), 0, "the gas left in the hotter tank");
            lab.close(store.total(), 0, "the gas left in the store");
            lab.close(warm.moles(O2), 5.0, "the O2 the warm tank got");
            lab.close(warm.temperature(), expected, "the warm tank's temperature");
            lab.done();
        });
    }

    // ---- COMPAT-073: buses reach gas handlers ----------------------------------------------------

    /**
     * An export bus set to a gas moves it out of ME storage, here another tank behind a storage bus,
     * into the block it faces; an import bus moves gas the other way. Neither touches a gas it isn't
     * set to.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_073_export_and_import_buses_reach_a_gas_handler(GameTestHelper h) {
        Lab lab = new Lab(h, 3);
        TestTank store = lab.tank(2, 1, 0, 1000, T).with(O2, 6.0).with(N2, 1.0);
        TestTank target = lab.tank(0, 1, 0, 1000, T);
        TestTank source = lab.tank(0, 1, 2, 1000, T).with(CO2, 3.0).with(N2, 2.0);
        lab.cable(1, 1, 0);
        Lab.filter(lab.part(1, 1, 0, Direction.WEST, "export_bus"), O2);
        lab.part(1, 1, 0, Direction.EAST, "storage_bus");
        lab.energy(1, 2, 0);
        lab.cable(1, 1, 1);
        lab.cable(1, 1, 2);
        Lab.filter(lab.part(1, 1, 2, Direction.WEST, "import_bus"), CO2);
        h.succeedWhen(() -> {
            lab.close(target.moles(O2), 6.0, "the O2 exported");
            lab.close(target.moles(N2), 0, "the N2 exported, which the bus isn't set to");
            lab.close(store.moles(CO2), 3.0, "the CO2 imported");
            lab.close(source.moles(N2), 2.0, "the N2 left where it was, which the bus isn't set to");
            lab.close(store.moles(N2), 1.0, "the N2 left in store");
            lab.done();
        });
    }

    /**
     * A gas handler that is a view of another mod's storage, as the fluid bridge offers on a fluid
     * tank and the Mekanism adapter on a chemical tank, is left alone: AE2 reaches that storage as
     * its own kind, and through the view too it would count the gas twice. A storage bus shows
     * nothing of it, an import bus takes nothing and an export bus puts nothing in.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_073_buses_leave_a_view_of_other_storage_alone(GameTestHelper h) {
        Lab lab = new Lab(h, 7);
        lab.view(0, 1, 0, 1000, T).with(O2, 5.0);
        TestTank imported = lab.view(2, 1, 0, 1000, T).with(N2, 4.0);
        TestTank store = lab.tank(1, 1, -1, 1000, T).with(CO2, 2.0);
        TestTank exported = lab.view(1, 1, 1, 1000, T);
        lab.cable(1, 1, 0);
        IPart viewBus = lab.part(1, 1, 0, Direction.WEST, "storage_bus");
        lab.part(1, 1, 0, Direction.NORTH, "storage_bus");
        lab.part(1, 1, 0, Direction.EAST, "import_bus");
        Lab.filter(lab.part(1, 1, 0, Direction.SOUTH, "export_bus"), CO2);
        lab.energy(1, 2, 0);
        h.runAfterDelay(100, () -> {
            KeyCounter stored = Lab.stored(viewBus);
            h.assertValueEqual(stored.get(GasKey.of(O2)), 0L, "the O2 in the view, seen by the network");
            h.assertValueEqual(stored.get(GasKey.of(CO2)), 2000L, "the CO2 in the tank that isn't a view");
            lab.close(imported.moles(N2), 4.0, "the N2 left in the view the import bus faces");
            lab.close(exported.moles(CO2), 0, "the CO2 put into the view the export bus faces");
            lab.close(store.moles(CO2), 2.0, "the CO2 left in store");
            lab.done();
            h.succeed();
        });
    }

    // ---- COMPAT-074: no gas from rounding --------------------------------------------------------

    /**
     * Thousands of one-millimole moves each way between two tanks, through the storage a bus works
     * through: all the gas is still there, no tank shows ME storage more than it holds, and the
     * fraction of a millimole that was never a whole unit stays where it was.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_074_many_small_transfers_make_no_gas(GameTestHelper h) {
        Lab lab = new Lab(h, 4);
        TestTank a = lab.tank(0, 1, 0, 1000, T).with(O2, 1.0004);
        TestTank b = lab.tank(2, 1, 0, 1000, T);
        GasHandlerStorage from = storage(lab, a, 0);
        GasHandlerStorage to = storage(lab, b, 2);
        GasKey o2 = GasKey.of(O2);
        IActionSource source = IActionSource.empty();
        long moved = 0;
        for (int i = 0; i < 1500; i++) {
            long got = from.extract(o2, 1, Actionable.MODULATE, source);
            long put = to.insert(o2, got, Actionable.MODULATE, source);
            h.assertValueEqual(put, got, "the units the second tank took on move " + i);
            moved += got;
            h.assertTrue(a.total() + b.total() <= 1.0004 + 1e-12, "gas was made on move " + i);
        }
        h.assertValueEqual(moved, 1000L, "the whole millimoles moved");
        lab.close(a.moles(O2), 0.0004, "the fraction left behind");
        lab.close(a.total() + b.total(), 1.0004, "all the gas");
        // Back again, a millimole at a time.
        for (int i = 0; i < 1500; i++) {
            long got = to.extract(o2, 1, Actionable.MODULATE, source);
            from.insert(o2, got, Actionable.MODULATE, source);
            h.assertTrue(a.total() + b.total() <= 1.0004 + 1e-12, "gas was made on move back " + i);
        }
        lab.close(a.total() + b.total(), 1.0004, "all the gas after the moves back");
        for (TestTank tank : new TestTank[] {a, b}) {
            KeyCounter shown = new KeyCounter();
            storage(lab, tank, tank == a ? 0 : 2).getAvailableStacks(shown);
            h.assertTrue(
                    GasUnits.moles(shown.get(o2)) <= tank.moles(O2),
                    "a tank shows " + shown.get(o2) + " mmol and holds " + tank.moles(O2) + " mol");
        }
        lab.done();
        h.succeed();
    }

    /** Less than a millimole of a gas is never taken: an import bus leaves it, for as long as it runs. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_074_a_fraction_of_a_unit_stays_put(GameTestHelper h) {
        Lab lab = new Lab(h, 5);
        TestTank crumbs = lab.tank(0, 1, 0, 1000, T).with(O2, 0.0009).with(N2, 0.0004);
        TestTank store = lab.tank(2, 1, 0, 1000, T);
        lab.cable(1, 1, 0);
        IPart bus = lab.part(1, 1, 0, Direction.WEST, "import_bus");
        lab.part(1, 1, 0, Direction.EAST, "storage_bus");
        lab.energy(1, 2, 0);
        // Long enough for the bus to have run many times, at its slowest.
        h.runAfterDelay(WAIT - 100, () -> {
            h.assertTrue(Lab.grid(bus) != null, "the bus never joined a network");
            lab.close(crumbs.moles(O2), 0.0009, "the O2 left");
            lab.close(crumbs.moles(N2), 0.0004, "the N2 left");
            lab.close(store.total(), 0, "the gas stored");
            lab.done();
            h.succeed();
        });
    }

    /**
     * A block that gives more than it is asked for gets the rest back, and one that takes less than it
     * said it would costs ME storage only the units that cover what it took: no gas is made either
     * way, and the part of a unit it didn't take goes into the air rather than nowhere.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_074_a_block_that_rounds_its_own_way_makes_no_gas(GameTestHelper h) {
        Lab lab = new Lab(h, 6);
        IActionSource source = IActionSource.empty();
        GasKey o2 = GasKey.of(O2);

        TestTank generous = lab.tank(0, 1, 0, 1000, T).with(O2, 5.0);
        generous.drainBonus = 0.0004;
        long got = storage(lab, generous, 0).extract(o2, 1000, Actionable.MODULATE, source);
        h.assertValueEqual(got, 1000L, "the units taken from a block that gives too much");
        lab.close(generous.moles(O2), 4.0, "what it holds once the rest is back");

        TestTank stingy = lab.tank(2, 1, 0, 1000, T);
        stingy.fillShortfall = 0.00035;
        long given = storage(lab, stingy, 2).insert(o2, 1000, Actionable.MODULATE, source);
        h.assertValueEqual(given, GasUnits.covering(stingy.moles(O2)), "the units ME storage gives up");
        h.assertTrue(GasUnits.moles(given) >= stingy.moles(O2), "the block holds more than ME storage gave up");
        h.assertValueEqual(given, 1000L, "the units ME storage gives up for 0.99965 mol");
        lab.done();
        h.succeed();
    }

    private static GasHandlerStorage storage(Lab lab, TestTank tank, int x) {
        return new GasHandlerStorage(() -> tank, lab.level, lab.abs(x, 1, 0), false, null);
    }
}
