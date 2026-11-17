package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.Millibuckets;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.fluid.FluidGases;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.config.ZasServerConfig;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Gases that other mods store as fluids (COMPAT-140 to COMPAT-145), with {@link TestFluids}
 * standing in for another mod's fluids and tank. A pipe joins the tank and shares gas with it in the
 * steps the tests drive, and a pipe's own fluid handler is asked what another mod's fluid pipe or
 * machine would ask it. Everything happens within one game tick, in a batch of its own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidBridgeTests {
    private static final String BATCH = "zas_atmos_fluids";
    private static final ResourceLocation H2O = ResourceLocation.fromNamespaceAndPath("zas", "water_vapor");
    private static final ResourceLocation N2O = ResourceLocation.fromNamespaceAndPath("zas", "nitrous_oxide");
    private static final IFluidHandler.FluidAction EXECUTE = IFluidHandler.FluidAction.EXECUTE;
    private static final IFluidHandler.FluidAction SIMULATE = IFluidHandler.FluidAction.SIMULATE;

    private FluidBridgeTests() {}

    private static double factor() {
        return Millibuckets.molesPerMillibucket();
    }

    /** A cap at x - 1 and a pipe at x, so the pipe's arm to whatever is at x + 1 is its second. */
    private static PipeBlockEntity capped(Area a, int x, int y, int z) {
        PipeTests.cap(a, x - 1, y, z, Direction.EAST);
        PipeTests.pipe(a, x, y, z);
        return a.entity(x, y, z);
    }

    private static TestFluids.Tank tank(Area a, int x, int y, int z) {
        a.place(x, y, z, TestFluids.tank().defaultBlockState());
        return a.entity(x, y, z);
    }

    private static boolean arm(Area a, int x, int y, int z, Direction face) {
        return a.get(x, y, z).getValue(PipeBlock.CONNECTIONS.get(face));
    }

    /** A pipe's fluid handler, as another mod's fluid pipe above it would get it. */
    private static IFluidHandler fluids(Area a, int x, int y, int z) {
        IFluidHandler handler = a.level.getCapability(Capabilities.FluidHandler.BLOCK, a.abs(x, y, z), Direction.UP);
        if (handler == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("the pipe offers no fluid handler");
        }
        return handler;
    }

    /** The tank's gas handler, as the pipe to the west of it gets it. */
    private static IGasHandler gas(Area a, int x, int y, int z) {
        return a.level.getCapability(IGasHandler.BLOCK, a.abs(x, y, z), Direction.WEST);
    }

    private static void empty(GasStore store) {
        store.drain();
    }

    // ---- COMPAT-140: fluid tags map fluids to gases --------------------------------------------

    /**
     * The shipped {@code #c:} mappings and a pack's own map each fluid of their tags to a gas: the
     * test oxygen is oxygen, once, though two mapped tags hold it, and the tag that sorts first
     * gives its mapping; the pack's fluid is nitrous oxide. A fluid in {@code #zas_atmos:never_gas}
     * is no gas, though {@code #c:oxygen} holds it, and water is no gas.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_140_fluid_tags_map_fluids_to_gases_once(GameTestHelper h) {
        FluidGases.Mapped oxygen = FluidGases.mapping(TestFluids.oxygen()).orElse(null);
        h.assertTrue(oxygen != null, "the test oxygen isn't mapped");
        h.assertValueEqual(oxygen.gas(), O2, "the test oxygen's gas");
        h.assertValueEqual(oxygen.mapping().tag().toString(), "c:oxygen", "the tag the test oxygen's mapping is of");
        List<Fluid> leaving = FluidGases.leavingAs(O2, T).stream()
                .map(FluidGases.Mapped::fluid)
                .toList();
        h.assertValueEqual(leaving, List.of(TestFluids.oxygen()), "the fluids oxygen leaves as");

        FluidGases.Mapped pack = FluidGases.mapping(TestFluids.packGas()).orElse(null);
        h.assertTrue(pack != null, "the pack's fluid isn't mapped");
        h.assertValueEqual(pack.gas(), N2O, "the pack's fluid's gas");

        h.assertTrue(FluidGases.mapping(TestFluids.liquidOxygen()).isEmpty(), "a never-gas fluid is a gas");
        h.assertTrue(FluidGases.mapping(Fluids.WATER).isEmpty(), "water is a gas");
        h.assertTrue(FluidGases.mapping(Fluids.LAVA).isEmpty(), "lava is a gas");
        h.succeed();
    }

    // ---- COMPAT-141: pipes join fluid tanks that hold gas --------------------------------------

    /**
     * A pipe grows no arm to a tank of water, nor to a tank that holds water whatever it would
     * accept. Emptied, the tank would take a gas, and the arm comes; it stays while the tank holds
     * a gas. A tank that takes nothing by a fill, as a machine's output does, stays joined while it
     * is empty, since it could hold a gas.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_141_a_pipe_joins_a_gas_tank_and_never_a_water_tank(GameTestHelper h) {
        Area a = new Area(h, 5300);
        a.clear(0, 0, 6, 3);
        TestFluids.Tank tank = tank(a, 3, 1, 1);
        tank.hold(Fluids.WATER, 500);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        h.assertFalse(arm(a, 2, 1, 1, Direction.EAST), "an arm to a tank of water");
        h.assertTrue(gas(a, 3, 1, 1) == null, "a tank of water is a gas handler");
        a.step();
        h.assertFalse(arm(a, 2, 1, 1, Direction.EAST), "an arm to a tank of water after a step");

        tank.hold(null, 0);
        a.step();
        h.assertTrue(arm(a, 2, 1, 1, Direction.EAST), "no arm to an empty tank");
        h.assertFalse(pipe.isOpen(), "the tank's arm doesn't count against an open end");
        tank.hold(TestFluids.oxygen(), 500);
        a.step();
        h.assertTrue(arm(a, 2, 1, 1, Direction.EAST), "no arm to a tank of oxygen");

        // A tank that would hold water only offers nothing while empty.
        tank.hold(null, 0);
        tank.accepts = stack -> stack.is(Fluids.WATER);
        a.step();
        h.assertFalse(arm(a, 2, 1, 1, Direction.EAST), "an arm to an empty water tank");

        // An output tank that takes no fill but holds oxygen is joined, and stays joined once empty.
        tank.accepts = stack -> stack.is(TestFluids.oxygen());
        tank.outputOnly = true;
        tank.hold(TestFluids.oxygen(), 10);
        a.step();
        h.assertTrue(arm(a, 2, 1, 1, Direction.EAST), "no arm to an output tank of oxygen");
        tank.hold(null, 0);
        a.step();
        h.assertTrue(arm(a, 2, 1, 1, Direction.EAST), "the arm to an output tank went when it emptied");
        h.succeed();
    }

    /**
     * A full tank reads as one atmosphere of its gas at 293.15 K in a volume of its size, and an
     * empty one as nothing in the same volume.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_141_a_full_tank_is_one_atmosphere_and_an_empty_one_is_empty(GameTestHelper h) {
        Area a = new Area(h, 5301);
        a.clear(0, 0, 3, 3);
        TestFluids.Tank tank = tank(a, 1, 1, 1);
        GasMixture empty = gas(a, 1, 1, 1).contents();
        h.assertTrue(empty.isEmpty(), "an empty tank holds gas");
        a.close(empty.volume(), TestFluids.CAPACITY * Millibuckets.litresPerMillibucket(), "an empty tank's volume");
        tank.hold(TestFluids.oxygen(), TestFluids.CAPACITY);
        GasMixture full = gas(a, 1, 1, 1).contents();
        a.close(full.moles(O2), TestFluids.CAPACITY * factor(), "a full tank's oxygen");
        a.close(full.volume(), TestFluids.CAPACITY * Millibuckets.litresPerMillibucket(), "a full tank's volume");
        a.close(full.temperature(), T, "a full tank's temperature");
        a.close(full.pressure(), Millibuckets.STANDARD_PRESSURE, "a full tank's pressure");
        h.succeed();
    }

    /**
     * Gas moves both ways between a pipe and a tank, to an equal concentration, in whole
     * millibuckets: out of a full tank into empty pipes, and back out of the pipes into the tank
     * once it is emptied. None is made or lost, and the fraction of a millibucket stays in the pipes.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_141_gas_moves_both_ways_between_a_pipe_and_a_tank(GameTestHelper h) {
        Area a = new Area(h, 5302);
        a.clear(0, 0, 6, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        TestFluids.Tank tank = tank(a, 3, 1, 1);
        h.assertTrue(arm(a, 2, 1, 1, Direction.EAST), "no arm to the tank");
        tank.hold(TestFluids.oxygen(), TestFluids.CAPACITY);
        double all = TestFluids.CAPACITY * factor();

        a.step();
        double inPipes = pipe.network().gas().moles(O2);
        int left = tank.tank.getFluidAmount();
        h.assertTrue(inPipes > 0, "no oxygen came out of the tank");
        h.assertTrue(left < TestFluids.CAPACITY, "the tank gave nothing");
        a.close(inPipes, (TestFluids.CAPACITY - left) * factor(), "the oxygen in the pipes: whole millibuckets");
        a.close(inPipes + left * factor(), all, "the oxygen in all");
        double volume = pipe.network().volume();
        double share = volume / (volume + TestFluids.CAPACITY * Millibuckets.litresPerMillibucket());
        h.assertTrue(Math.abs(inPipes - all * share) <= factor(), "not an equal concentration: " + inPipes);

        // Back: the tank emptied, the pipes fill it as far as whole millibuckets go.
        tank.hold(null, 0);
        a.step();
        int back = tank.tank.getFluidAmount();
        h.assertTrue(back > 0, "no oxygen went into the tank");
        h.assertTrue(tank.tank.getFluid().is(TestFluids.oxygen()), "it went in as something else");
        a.close(pipe.network().gas().moles(O2) + back * factor(), inPipes, "the oxygen in all, the second time");
        h.succeed();
    }

    // ---- COMPAT-142: pipes offer a fluid handler -----------------------------------------------

    /**
     * Another mod's machine fills a pipe with a gas fluid and drains it back out, by fluid or by
     * amount; water and a fluid in {@code #zas_atmos:never_gas} are refused whole.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_142_a_fluid_fills_a_pipe_and_drains_out_again(GameTestHelper h) {
        Area a = new Area(h, 5303);
        a.clear(0, 0, 4, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        PipeTests.cap(a, 3, 1, 1, Direction.WEST);
        IFluidHandler fluids = fluids(a, 2, 1, 1);
        h.assertValueEqual(fluids.fill(new FluidStack(Fluids.WATER, 100), EXECUTE), 0, "water taken");
        h.assertFalse(fluids.isFluidValid(0, new FluidStack(Fluids.WATER, 1)), "water is valid");
        h.assertValueEqual(
                fluids.fill(new FluidStack(TestFluids.liquidOxygen(), 100), EXECUTE), 0, "liquid oxygen taken");
        h.assertTrue(pipe.network().gas().isEmpty(), "the pipe holds something it refused");

        FluidStack oxygen = new FluidStack(TestFluids.oxygen(), 100);
        h.assertValueEqual(fluids.fill(oxygen, SIMULATE), 100, "a simulated fill's answer");
        h.assertTrue(pipe.network().gas().isEmpty(), "a simulated fill changed the pipe");
        h.assertValueEqual(fluids.fill(oxygen, EXECUTE), 100, "what the fill took");
        a.close(pipe.network().gas().moles(O2), 100 * factor(), "the oxygen in the pipe");
        a.close(pipe.network().gas().temperature(), T, "its temperature");

        FluidStack shown = fluids.getFluidInTank(0);
        h.assertTrue(shown.is(TestFluids.oxygen()), "the pipe shows " + shown.getFluid());
        h.assertValueEqual(shown.getAmount(), 100, "the oxygen it shows, mB");
        FluidStack out = fluids.drain(new FluidStack(TestFluids.oxygen(), 30), EXECUTE);
        h.assertValueEqual(out.getAmount(), 30, "what a drain by fluid gave");
        h.assertTrue(fluids.drain(new FluidStack(Fluids.WATER, 30), EXECUTE).isEmpty(), "water drained");
        FluidStack rest = fluids.drain(1000, EXECUTE);
        h.assertTrue(rest.is(TestFluids.oxygen()), "a drain by amount gave " + rest.getFluid());
        h.assertValueEqual(rest.getAmount(), 70, "what a drain by amount gave");
        a.close(pipe.network().gas().moles(O2), 0, "the oxygen left in the pipe");
        h.succeed();
    }

    /**
     * A pipe of air shows no fluid and gives none, so no fluid pipe picks oxygen out of air; a pipe
     * of oxygen with a trace of nitrogen keeps the trace.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_142_a_pipe_of_air_gives_no_fluid(GameTestHelper h) {
        Area a = new Area(h, 5304);
        a.clear(0, 0, 4, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        PipeTests.cap(a, 3, 1, 1, Direction.WEST);
        IFluidHandler fluids = fluids(a, 2, 1, 1);
        pipe.network().gas().add(new GasParcel(Map.of(N2, 79 * factor(), O2, 21 * factor()), T));
        h.assertTrue(fluids.getFluidInTank(0).isEmpty(), "a pipe of air shows a fluid");
        h.assertTrue(fluids.drain(1000, EXECUTE).isEmpty(), "a pipe of air gave a fluid");
        h.assertTrue(
                fluids.drain(new FluidStack(TestFluids.oxygen(), 1000), EXECUTE).isEmpty(), "air gave oxygen");

        empty(pipe.network().gas());
        pipe.network().gas().add(new GasParcel(Map.of(O2, 995 * factor(), N2, 5 * factor()), T));
        // 5 mB of nitrogen keep 495 mB of oxygen; the rest, 500 mB less a rounding, may leave.
        FluidStack out = fluids.drain(1000, EXECUTE);
        h.assertTrue(
                out.getAmount() == 499 || out.getAmount() == 500,
                "the oxygen a 99.5 % pipe gives: " + out.getAmount() + " mB");
        a.close(pipe.network().gas().moles(O2), (995 - out.getAmount()) * factor(), "the oxygen left");
        a.close(pipe.network().gas().moles(N2), 5 * factor(), "the nitrogen left");
        h.succeed();
    }

    // ---- COMPAT-143: one unit for every mod ----------------------------------------------------

    /**
     * The unit factor is ZAS³'s one server setting: at 2 L per mB, 100 mB of oxygen is twice the
     * moles, both ways, and a tank twice the volume. A change applies at once.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_143_a_millibucket_is_the_server_setting(GameTestHelper h) {
        Area a = new Area(h, 5305);
        a.clear(0, 0, 6, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        PipeTests.cap(a, 3, 1, 1, Direction.WEST);
        tank(a, 5, 1, 1);
        double standard = Millibuckets.molesPerMillibucket(1.0);
        a.close(factor(), standard, "the default factor");
        // No test runs between the change and its undoing, since both are in this one call.
        try {
            ZasServerConfig.LITRES_PER_MILLIBUCKET.set(2.0);
            h.assertValueEqual(
                    fluids(a, 2, 1, 1).fill(new FluidStack(TestFluids.oxygen(), 100), EXECUTE), 100, "taken");
            a.close(pipe.network().gas().moles(O2), 200 * standard, "100 mB of oxygen at 2 L per mB");
            a.close(gas(a, 5, 1, 1).contents().volume(), 2 * TestFluids.CAPACITY, "a tank's volume at 2 L per mB");
            h.assertValueEqual(fluids(a, 2, 1, 1).drain(1000, EXECUTE).getAmount(), 100, "given back at 2 L per mB");
        } finally {
            ZasServerConfig.LITRES_PER_MILLIBUCKET.set(1.0);
        }
        a.close(factor(), standard, "the factor, set back");
        h.succeed();
    }

    // ---- COMPAT-144: no gas from rounding ------------------------------------------------------

    /**
     * Gas becomes whole millibuckets, rounded down, and the fraction stays in the pipe: 10.5 mB of
     * oxygen gives 10 mB and keeps half of one. A tank filled from a pipe with 2.7 mB of oxygen in
     * it gets whole millibuckets and nothing more, and the pipe keeps the rest.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_144_rounding_to_whole_millibuckets_makes_no_gas(GameTestHelper h) {
        Area a = new Area(h, 5306);
        a.clear(0, 0, 8, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        PipeTests.cap(a, 3, 1, 1, Direction.WEST);
        pipe.network().gas().add(new GasParcel(Map.of(O2, 10.5 * factor()), T));
        FluidStack out = fluids(a, 2, 1, 1).drain(new FluidStack(TestFluids.oxygen(), 100), EXECUTE);
        h.assertValueEqual(out.getAmount(), 10, "whole millibuckets drained");
        a.close(pipe.network().gas().moles(O2), 0.5 * factor(), "the half millibucket left");
        h.assertTrue(fluids(a, 2, 1, 1).drain(100, EXECUTE).isEmpty(), "half a millibucket drained");

        PipeBlockEntity feed = capped(a, 6, 1, 1);
        TestFluids.Tank tank = tank(a, 7, 1, 1);
        double offered = 2.7 * factor();
        feed.network().gas().add(new GasParcel(Map.of(O2, offered), T));
        a.step();
        int whole = tank.tank.getFluidAmount();
        double kept = feed.network().gas().moles(O2);
        h.assertTrue(whole >= 1 && whole <= 2, "the tank took " + whole + " mB of 2.7");
        a.close(kept + whole * factor(), offered, "the oxygen in all");
        h.succeed();
    }

    // ---- COMPAT-145: temperature ---------------------------------------------------------------

    /**
     * Steam arrives at its fluid type's temperature, 383 K, as the shipped {@code #c:steam} mapping
     * says; oxygen, whose mapping gives none, at 293.15 K; the pack's fluid at the 250 K its mapping
     * sets. Water vapour leaves as the hottest steam it is at least as hot as, and cold vapour as no
     * fluid at all.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_145_gas_takes_its_fluids_temperature(GameTestHelper h) {
        Area a = new Area(h, 5307);
        a.clear(0, 0, 4, 3);
        TestFluids.Tank tank = tank(a, 1, 1, 1);
        tank.hold(TestFluids.steam(), 500);
        GasMixture steam = gas(a, 1, 1, 1).contents();
        a.close(steam.moles(H2O), 500 * factor(), "the steam's water vapour");
        a.close(steam.temperature(), 383, "the steam's temperature");
        tank.hold(TestFluids.oxygen(), 500);
        a.close(gas(a, 1, 1, 1).contents().temperature(), T, "the oxygen's temperature");
        tank.hold(TestFluids.packGas(), 500);
        GasMixture pack = gas(a, 1, 1, 1).contents();
        a.close(pack.moles(N2O), 500 * factor(), "the pack's nitrous oxide");
        a.close(pack.temperature(), 250, "the pack's fluid's temperature");

        PipeBlockEntity pipe = capped(a, 3, 1, 2);
        PipeTests.cap(a, 4, 1, 2, Direction.WEST);
        IFluidHandler fluids = fluids(a, 3, 1, 2);
        double[] temperatures = {400, 450, 300};
        Fluid[] expected = {TestFluids.steam(), TestFluids.hotSteam(), null};
        for (int i = 0; i < temperatures.length; i++) {
            empty(pipe.network().gas());
            pipe.network().gas().add(new GasParcel(Map.of(H2O, 100 * factor()), temperatures[i]));
            FluidStack shown = fluids.getFluidInTank(0);
            if (expected[i] == null) {
                h.assertTrue(shown.isEmpty(), "vapour at " + temperatures[i] + " K shows as " + shown.getFluid());
                h.assertTrue(
                        fluids.drain(new FluidStack(TestFluids.steam(), 10), EXECUTE)
                                .isEmpty(),
                        "cold vapour drained as steam");
            } else {
                h.assertTrue(shown.is(expected[i]), "vapour at " + temperatures[i] + " K shows as " + shown.getFluid());
            }
        }
        // Vapour at 450 K may leave as the cooler steam too, if a machine asks for it.
        empty(pipe.network().gas());
        pipe.network().gas().add(new GasParcel(Map.of(H2O, 100 * factor()), 450));
        h.assertValueEqual(
                fluids.drain(new FluidStack(TestFluids.steam(), 10), EXECUTE).getAmount(),
                10,
                "383 K steam from 450 K vapour");
        h.succeed();
    }
}
