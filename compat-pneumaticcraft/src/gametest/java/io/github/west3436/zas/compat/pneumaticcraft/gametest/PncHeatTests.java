package io.github.west3436.zas.compat.pneumaticcraft.gametest;

import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.compat.pneumaticcraft.PncBlocks;
import io.github.west3436.zas.compat.pneumaticcraft.PncHeat;
import io.github.west3436.zas.compat.pneumaticcraft.PncHeat.Flow;
import io.github.west3436.zas.compat.pneumaticcraft.PncSettings;
import java.util.List;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Heat between zones and PneumaticCraft's heat blocks (COMPAT-013). The block is a Compressed Iron
 * Block, PneumaticCraft's own, on the floor of a sealed stone room whose gas is read through the
 * public API; each test heats or chills its exchanger, runs the adapter's step for a second, and
 * follows the heat to where it lands.
 *
 * <p>They build, run and clear within one game tick, driving atmos ticks themselves, so they leave
 * nothing standing for the adapter's own step, or another test's, to find. They share a batch with
 * the other tests that drive atmos ticks.
 */
@GameTestHolder(PncGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class PncHeatTests {
    /** The batch of every test that drives atmos ticks itself. */
    static final String BATCH = "zas_pneumaticcraft_driven";

    /** A Compressed Iron Block's thermal capacity, in PneumaticCraft's units. */
    private static final double IRON_CAPACITY = 10;

    private PncHeatTests() {}

    /** A Compressed Iron Block on the floor of the room, its exchanger joined to what is round it. */
    private static IHeatExchangerLogic ironBlock(Area area) {
        BlockEntity block = area.place(2, 1, 2, "compressed_iron_block");
        BlockPos pos = block.getBlockPos();
        IHeatExchangerLogic logic = area.level.getCapability(PNCCapabilities.HEAT_EXCHANGER_BLOCK, pos, null);
        area.h.assertTrue(logic != null, "the Compressed Iron Block has no heat exchanger");
        // What the block does as it loads, which a block placed this tick hasn't done yet.
        logic.initializeAsHull(area.level, pos, IHeatExchangerLogic.ALL_BLOCKS, Direction.values());
        area.h.assertValueEqual(logic.getThermalCapacity(), IRON_CAPACITY, "the block's thermal capacity");
        return logic;
    }

    /** The step's one flow to or from a zone. */
    private static Flow only(GameTestHelper h, List<Flow> flows, ZoneId zone) {
        List<Flow> mine = flows.stream().filter(f -> f.zone().equals(zone)).toList();
        h.assertValueEqual(mine.size(), 1, "flows between the block and the room");
        return mine.getFirst();
    }

    /** Runs atmos ticks until a flow has landed, and says what ZAS³ made of it. */
    private static HeatResult land(Area area, Flow flow) {
        for (int i = 0; i < 8 && !flow.result().isDone(); i++) {
            area.tick();
        }
        area.h.assertTrue(flow.result().isDone(), "the heat didn't land within 8 atmos ticks");
        return flow.result().join();
    }

    private static void close(GameTestHelper h, double actual, double expected, double relative, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= relative * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /**
     * A zone's temperature against what the heat alone would make it. The atmos tick that lands the
     * heat also lets the room's walls conduct, which moves it by thousandths of a kelvin.
     */
    private static void kelvin(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) <= 0.05, what + ": expected " + expected + " K, got " + actual);
    }

    /** What two bodies joined by a conductance exchange in a time: worked out here, not by the adapter. */
    private static double expected(double cb, double tb, double cg, double tg, double conductance, double seconds) {
        double reduced = cb * cg / (cb + cg);
        return (tb - tg) * reduced * (1 - Math.exp(-conductance * seconds / reduced));
    }

    /** The five faces of the block on the room's floor: each the setting in series with the block's own 20 × J / R. */
    private static double fiveFaces(IHeatExchangerLogic logic) {
        double own = 20 * PncSettings.joulesPerHeatUnit() / logic.getThermalResistance();
        return 5 / (1 / PncSettings.faceConductance() + 1 / own);
    }

    /**
     * A hot Compressed Iron Block warms the sealed room it stands on the floor of by exactly what it
     * loses. At 1,000 K it shares five faces with the room's zone; one second moves what five faces of
     * 10 W/K, each in series with the block's own resistance, carry between its 1,000 J/K and the
     * room's gas. The block has lost that at once, the zone is given that and takes it all, and its
     * temperature rises by that over its heat capacity.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_013_a_hot_block_warms_its_room(GameTestHelper h) {
        Area area = new Area(h, 20);
        try {
            area.room();
            IHeatExchangerLogic logic = ironBlock(area);
            logic.setTemperature(1000);
            area.form(2, 2, 2);
            h.assertTrue(PncBlocks.knows(area.level, area.abs(2, 1, 2)), "the block wasn't learned");
            ZoneInfo before = area.zone(2, 2, 2);
            double gas = GasProperties.heatCapacity(before.mixture().moles());
            double block = IRON_CAPACITY * PncSettings.joulesPerHeatUnit();
            double joules = expected(block, 1000, gas, before.mixture().temperature(), fiveFaces(logic), 1.0);
            h.assertTrue(joules > 1000, "the test expects a real flow, and works out " + joules + " J");

            Flow flow = only(h, PncHeat.step(area.level, 1.0), before.id());
            close(h, flow.joules(), joules, 1e-9, "the heat sent to the room, J");
            close(h, logic.getTemperature(), 1000 - joules / block, 1e-9, "the block's temperature at once, K");
            HeatResult landed = land(area, flow);
            close(h, landed.joules(), joules, 1e-9, "the heat the room took, J");
            kelvin(
                    h,
                    area.zone(2, 2, 2).mixture().temperature(),
                    before.mixture().temperature() + joules / gas,
                    "the room's temperature");
        } finally {
            area.clear();
        }
        h.succeed();
    }

    /**
     * A cold Compressed Iron Block cools its room by exactly what it gains: at 200 K the step asks the
     * zone for one second's flow, the zone delivers it, and the block gains what was delivered.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_013_a_cold_block_cools_its_room(GameTestHelper h) {
        Area area = new Area(h, 21);
        try {
            area.room();
            IHeatExchangerLogic logic = ironBlock(area);
            logic.setTemperature(200);
            area.form(2, 2, 2);
            ZoneInfo before = area.zone(2, 2, 2);
            double gas = GasProperties.heatCapacity(before.mixture().moles());
            double block = IRON_CAPACITY * PncSettings.joulesPerHeatUnit();
            double joules = expected(block, 200, gas, before.mixture().temperature(), fiveFaces(logic), 1.0);
            h.assertTrue(joules < -1000, "the test expects a real flow, and works out " + joules + " J");

            Flow flow = only(h, PncHeat.step(area.level, 1.0), before.id());
            close(h, flow.joules(), joules, 1e-9, "the heat asked of the room, J");
            close(h, logic.getTemperature(), 200, 1e-12, "the block's temperature before the heat arrives, K");
            HeatResult landed = land(area, flow);
            close(h, landed.joules(), -joules, 1e-9, "the heat the room gave, J");
            close(h, logic.getTemperature(), 200 + landed.joules() / block, 1e-9, "the block's temperature after, K");
            kelvin(
                    h,
                    area.zone(2, 2, 2).mixture().temperature(),
                    before.mixture().temperature() + joules / gas,
                    "the room's temperature");
        } finally {
            area.clear();
        }
        h.succeed();
    }

    /**
     * A hot block in the open air exchanges nothing with ZAS³: no zone touches it, so the step sends
     * no heat and leaves the block's temperature to PneumaticCraft.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_013_a_block_in_the_open_air_exchanges_nothing(GameTestHelper h) {
        Area area = new Area(h, 22);
        try {
            area.clear();
            IHeatExchangerLogic logic = ironBlock(area);
            logic.setTemperature(1000);
            area.tick();
            h.assertTrue(PncBlocks.knows(area.level, area.abs(2, 1, 2)), "the block wasn't learned");
            // The tests that drive atmos ticks build and clear within one game tick, so this is the
            // only heat block standing.
            h.assertValueEqual(PncHeat.step(area.level, 1.0), List.of(), "the heat sent");
            close(h, logic.getTemperature(), 1000, 1e-12, "the block's temperature");
        } finally {
            area.clear();
        }
        h.succeed();
    }
}
