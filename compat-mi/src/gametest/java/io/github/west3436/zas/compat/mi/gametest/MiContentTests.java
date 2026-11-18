package io.github.west3436.zas.compat.mi.gametest;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.Millibuckets;
import io.github.west3436.zas.atmos.fluid.FluidGases;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What Modern Industrialization's gas fluids do with the content on: each is a ZAS³ gas
 * (COMPAT-090, COMPAT-092), and gas moves both ways between a ZAS³ pipe and one of its tanks
 * (COMPAT-090). Its two steams stay apart (COMPAT-093). Without the content, the fluid bridge
 * doesn't exist, and these tests don't run.
 */
@GameTestHolder(MiContentTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MiContentTests {
    /** The namespace of the tests that need the content. */
    public static final String NAMESPACE = "zas_mi_content";

    private static final ResourceLocation WATER_VAPOR = MiGateTests.zas("water_vapor");
    private static final ResourceLocation OXYGEN = MiGateTests.zas("oxygen");
    private static final ResourceLocation NITROGEN = MiGateTests.zas("nitrogen");
    private static final IFluidHandler.FluidAction EXECUTE = IFluidHandler.FluidAction.EXECUTE;

    /** A tank every Modern Industrialization build has. */
    private static final String TANK = "bronze_tank";

    private MiContentTests() {}

    private static FluidGases.Mapped mapped(Spot s, String path) {
        FluidGases.Mapped mapped = FluidGases.mapping(s.fluid(path)).orElse(null);
        s.h.assertTrue(mapped != null, "modern_industrialization:" + path + " isn't mapped");
        return mapped;
    }

    private static List<Fluid> leaving(ResourceLocation gas, double temperature) {
        return FluidGases.leavingAs(gas, temperature).stream()
                .map(FluidGases.Mapped::fluid)
                .toList();
    }

    /** The temperature a fluid's gas arrives at. */
    private static double arrives(Spot s, String path) {
        return mapped(s, path).gas(new FluidStack(s.fluid(path), 1000), 1000).temperature();
    }

    private static void eachIsItsGas(Spot s, Map<String, String> gases) {
        gases.forEach((path, gas) -> {
            s.h.assertValueEqual(
                    mapped(s, path).gas(), MiGateTests.zas(gas), "modern_industrialization:" + path + "'s gas");
            s.h.assertTrue(
                    leaving(MiGateTests.zas(gas), 293.15).contains(s.fluid(path)),
                    "zas:" + gas + " can't leave as modern_industrialization:" + path);
        });
    }

    /**
     * Modern Industrialization's oxygen, nitrogen, hydrogen and methane are ZAS³'s, both ways, and
     * its steam is water vapour at boiling, though the fluid says 300 K.
     */
    @GameTest(template = "empty")
    public static void compat_090_its_gas_fluids_are_zas_gases(GameTestHelper h) {
        Spot s = new Spot(h, 0);
        eachIsItsGas(s, MiGateTests.OWN_GASES);
        h.assertValueEqual(mapped(s, "steam").gas(), WATER_VAPOR, "steam's gas");
        s.close(arrives(s, "steam"), 373.15, "steam's temperature");
        h.assertTrue(leaving(WATER_VAPOR, 373.15).contains(s.fluid("steam")), "water vapour can't leave as steam");
        h.assertFalse(leaving(WATER_VAPOR, 350).contains(s.fluid("steam")), "cool water vapour leaves as steam");
        h.succeed();
    }

    /**
     * A ZAS³ pipe joins a bronze tank of oxygen and takes gas out of it, to an equal concentration
     * in whole millibuckets, and none is made or lost. Emptied and given nitrogen, the pipe fills
     * the tank with Modern Industrialization's nitrogen.
     */
    @GameTest(template = "empty")
    public static void compat_090_gas_moves_both_ways_between_a_pipe_and_its_tank(GameTestHelper h) {
        Spot s = new Spot(h, 1);
        IFluidHandler tank = s.tank(3, 1, 1, TANK);
        int filled = tank.fill(new FluidStack(s.fluid("oxygen"), 1_000_000), EXECUTE);
        h.assertTrue(filled > 0, "the tank took no oxygen");
        PipeBlockEntity pipe = s.cappedPipe(2, 1, 1);
        h.assertTrue(s.arm(2, 1, 1, Direction.EAST), "no arm to a tank of oxygen");
        double factor = Millibuckets.molesPerMillibucket();

        s.step();
        double inPipes = pipe.network().gas().moles(OXYGEN);
        int left = tank.getFluidInTank(0).getAmount();
        h.assertTrue(inPipes > 0, "no oxygen came out of the tank");
        h.assertTrue(left < filled, "the tank gave nothing");
        s.close(inPipes, (filled - left) * factor, "the oxygen in the pipe: whole millibuckets");

        // Back: the pipe emptied of oxygen and given nitrogen, the tank emptied, the pipe fills it.
        tank.drain(Integer.MAX_VALUE, EXECUTE);
        pipe.network().gas().drain();
        pipe.network().gas().add(new GasParcel(Map.of(NITROGEN, 500 * factor), 293.15));
        s.step();
        FluidStack back = tank.getFluidInTank(0);
        h.assertTrue(back.getAmount() > 0, "no nitrogen went into the tank");
        h.assertTrue(back.is(s.fluid("nitrogen")), "it went in as " + back.getFluid());
        s.close(pipe.network().gas().moles(NITROGEN) + back.getAmount() * factor, 500 * factor, "the nitrogen in all");
        s.clear();
        h.succeed();
    }

    /**
     * A bronze tank of steam reads, through the gas handler the pipe gets, as water vapour at
     * boiling.
     */
    @GameTest(template = "empty")
    public static void compat_090_its_steam_is_water_vapour_at_boiling_in_a_tank(GameTestHelper h) {
        Spot s = new Spot(h, 2);
        IFluidHandler tank = s.tank(3, 1, 1, TANK);
        int filled = tank.fill(new FluidStack(s.fluid("steam"), 1000), EXECUTE);
        h.assertValueEqual(filled, 1000, "the steam the tank took");
        IGasHandler gas = s.level.getCapability(IGasHandler.BLOCK, s.abs(3, 1, 1), Direction.WEST);
        h.assertTrue(gas != null, "a tank of steam is no gas handler");
        GasMixture held = gas.contents();
        s.close(held.moles(WATER_VAPOR), 1000 * Millibuckets.molesPerMillibucket(), "the tank's water vapour");
        s.close(held.temperature(), 373.15, "its temperature");
        s.clear();
        h.succeed();
    }

    /**
     * Its argon, helium, helium-3, chlorine, deuterium and tritium are the ZAS³ gases of their names
     * and its ethylene is ethene, both ways.
     */
    @GameTest(template = "empty")
    public static void compat_092_its_other_gas_fluids_are_zas_gases(GameTestHelper h) {
        eachIsItsGas(new Spot(h, 3), MiGateTests.ADDED_GASES);
        h.succeed();
    }

    /**
     * High-pressure steam is water vapour at 383 K, ten degrees above its plain steam, which its
     * fluid doesn't say. So water vapour from plain steam goes back as plain steam, and as
     * high-pressure steam only once heated to 383 K, when it tries that first.
     */
    @GameTest(template = "empty")
    public static void compat_093_high_pressure_steam_is_hotter_than_steam(GameTestHelper h) {
        Spot s = new Spot(h, 4);
        Fluid steam = s.fluid("steam");
        Fluid high = s.fluid("high_pressure_steam");
        h.assertValueEqual(mapped(s, "high_pressure_steam").gas(), WATER_VAPOR, "high-pressure steam's gas");
        s.close(arrives(s, "high_pressure_steam"), 383, "high-pressure steam's temperature");
        h.assertValueEqual(leaving(WATER_VAPOR, 373.15), List.of(steam), "what water vapour at boiling leaves as");
        List<Fluid> hot = leaving(WATER_VAPOR, 383);
        h.assertTrue(hot.indexOf(high) == 0 && hot.contains(steam), "what water vapour at 383 K leaves as: " + hot);
        h.succeed();
    }
}
