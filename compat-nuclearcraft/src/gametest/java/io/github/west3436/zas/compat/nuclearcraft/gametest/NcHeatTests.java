package io.github.west3436.zas.compat.nuclearcraft.gametest;

import igentuman.nc.block_entity.fission.FissionReactorControllerBE;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.compat.nuclearcraft.NuclearCraftSettings;
import io.github.west3436.zas.compat.nuclearcraft.ReactorHeat;
import io.github.west3436.zas.compat.nuclearcraft.ReactorHeat.Flow;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A fission reactor's heat in the room round it (COMPAT-153). Each test builds a 5 by 3 by 3
 * reactor, NuclearCraft's own blocks, in a sealed stone room whose gas is read through the public
 * API: a casing shell with the controller in one face, and inside it a fuel cell between two water
 * heat sinks.
 *
 * <p>The driven tests set what the controller shows, count it for a second's ticks, run the
 * adapter's step and follow the heat to where it lands. They build, run and clear within one game
 * tick, so they leave nothing standing for the adapter's own step to find, and share a batch. The
 * last test leaves a fuelled reactor to NuclearCraft and the game.
 */
@GameTestHolder(NcGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class NcHeatTests {
    /** The batch of every test that drives the adapter itself. */
    static final String BATCH = "zas_nuclearcraft_driven";

    /** The reactor's corners, in the area's cells. */
    private static final int[] MIN = {2, 2, 2};

    private static final int[] MAX = {6, 4, 4};

    /** The controller, in the middle of the reactor's face towards z = 1. */
    private static final int[] CONTROLLER = {4, 3, 2};

    private NcHeatTests() {}

    /** Builds the reactor in the area and returns its controller. */
    private static FissionReactorControllerBE reactor(Area area) {
        for (int x = MIN[0]; x <= MAX[0]; x++) {
            for (int y = MIN[1]; y <= MAX[1]; y++) {
                for (int z = MIN[2]; z <= MAX[2]; z++) {
                    boolean shell =
                            x == MIN[0] || x == MAX[0] || y == MIN[1] || y == MAX[1] || z == MIN[2] || z == MAX[2];
                    if (shell && !(x == CONTROLLER[0] && y == CONTROLLER[1] && z == CONTROLLER[2])) {
                        area.set(x, y, z, "fission_reactor_casing");
                    }
                }
            }
        }
        area.set(3, 3, 3, "water_heat_sink");
        area.set(4, 3, 3, "fission_reactor_solid_fuel_cell");
        area.set(5, 3, 3, "water_heat_sink");
        if (!(area.place(CONTROLLER[0], CONTROLLER[1], CONTROLLER[2], "fission_reactor_controller")
                instanceof FissionReactorControllerBE controller)) {
            throw new GameTestAssertException("the controller's block entity isn't a FissionReactorControllerBE");
        }
        return controller;
    }

    private static BlockPos controllerPos(Area area) {
        return area.abs(CONTROLLER[0], CONTROLLER[1], CONTROLLER[2]);
    }

    /** Shows the controller as NuclearCraft's logic leaves it after a tick of a formed, running reactor. */
    private static void running(
            Area area,
            FissionReactorControllerBE controller,
            double currentHeat,
            double heatPerTick,
            double cooldownPerTick,
            int reactivity) {
        controller.formed = true;
        controller.reactivity = reactivity;
        controller.heatBuffer.currentHeat = currentHeat;
        controller.heatBuffer.heatPerTick = heatPerTick;
        controller.heatBuffer.cooldownPerTick = cooldownPerTick;
        controller.aabbMinPacked = area.abs(MIN[0], MIN[1], MIN[2]).asLong();
        controller.aabbMaxPacked = area.abs(MAX[0], MAX[1], MAX[2]).asLong();
    }

    /** Counts a second's game ticks, then steps. */
    private static List<Flow> second(Area area) {
        for (int i = 0; i < 20; i++) {
            ReactorHeat.count(area.level);
        }
        return ReactorHeat.step(area.level);
    }

    /** The flows from this area's reactor. */
    private static List<Flow> mine(Area area, List<Flow> flows) {
        BlockPos at = controllerPos(area);
        return flows.stream().filter(f -> f.reactor().equals(at)).toList();
    }

    /** The step's one flow from the reactor to a zone. */
    private static Flow only(Area area, List<Flow> flows, ZoneId zone) {
        List<Flow> mine = mine(area, flows);
        area.h.assertValueEqual(mine.size(), 1, "flows from the reactor");
        area.h.assertValueEqual(mine.getFirst().zone(), zone, "the zone the heat went to");
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

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /** What a second of heat at some H a tick gives a room: worked out here, not by the adapter. */
    private static double expectedJoules(double perTick) {
        double joules = 20 * perTick * NuclearCraftSettings.joulesPerHeatUnit();
        double ceiling = NuclearCraftSettings.maxWatts();
        return ceiling > 0 ? Math.min(joules, ceiling) : joules;
    }

    /**
     * A running reactor in a sealed room warms the room by what its sinks removed. Its buffer still
     * holds heat after its sinks, so they carried all they can, 120 H a tick; a second of that, at
     * the server's joules per H, reaches the one zone round the reactor, which takes it all, and its
     * temperature rises by that over its heat capacity.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_153_a_running_reactor_warms_its_sealed_room(GameTestHelper h) {
        Area area = new Area(h, 1);
        try {
            area.room();
            FissionReactorControllerBE controller = reactor(area);
            area.form(1, 1, 1);
            h.assertTrue(ReactorHeat.knows(area.level, controllerPos(area)), "the reactor wasn't learned");
            running(area, controller, 100, 50, 120, 100);
            ZoneInfo before = area.zone(1, 1, 1);
            double joules = expectedJoules(120);
            h.assertTrue(joules > 1000, "the test expects a real flow, and works out " + joules + " J");

            Flow flow = only(area, second(area), before.id());
            close(h, flow.joules(), joules, "the heat sent to the room, J");
            HeatResult landed = land(area, flow);
            close(h, landed.joules(), joules, "the heat the room took, J");
            double gas = GasProperties.heatCapacity(before.mixture().moles());
            double after = area.zone(1, 1, 1).mixture().temperature();
            double expected = before.mixture().temperature() + joules / gas;
            // The atmos tick that lands the heat also lets the walls conduct, by thousandths of a kelvin.
            h.assertTrue(
                    Math.abs(after - expected) <= 0.05,
                    "the room's temperature: expected " + expected + " K, got " + after);

            // A reactor whose sinks empty its buffer gives what its fuel made: 50 H a tick at 80 %.
            running(area, controller, 0, 50, 120, 80);
            close(h, only(area, second(area), before.id()).joules(), expectedJoules(40), "the fuel's heat, J");
        } finally {
            area.clear();
        }
        h.succeed();
    }

    /** No reactor gives more than the server's ceiling, however much its sinks removed. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_153_the_heat_is_held_to_the_ceiling(GameTestHelper h) {
        Area area = new Area(h, 2);
        try {
            area.room();
            FissionReactorControllerBE controller = reactor(area);
            area.form(1, 1, 1);
            running(area, controller, 1000, 5000, 10_000, 100);
            double ceiling = NuclearCraftSettings.maxWatts();
            h.assertTrue(ceiling > 0, "the test expects a ceiling");
            h.assertTrue(
                    20 * 10_000 * NuclearCraftSettings.joulesPerHeatUnit() > ceiling,
                    "the test expects more heat than the ceiling");
            Flow flow = only(area, second(area), area.zone(1, 1, 1).id());
            close(h, flow.joules(), ceiling, "the heat sent in a second, J");
            land(area, flow);
        } finally {
            area.clear();
        }
        h.succeed();
    }

    /**
     * Nothing reaches a room from a reactor that isn't formed, nor from an idle one, whose cooling
     * NuclearCraft sets to nothing; and a running reactor in the open air sends nothing, since no
     * zone touches it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_153_an_unformed_idle_or_open_air_reactor_heats_no_room(GameTestHelper h) {
        Area room = new Area(h, 3);
        Area open = new Area(h, 4);
        try {
            room.room();
            FissionReactorControllerBE controller = reactor(room);
            room.form(1, 1, 1);
            running(room, controller, 100, 50, 120, 100);
            controller.formed = false;
            h.assertValueEqual(mine(room, second(room)), List.of(), "the heat an unformed reactor sent");
            running(room, controller, 100, 0, 0, 0);
            h.assertValueEqual(mine(room, second(room)), List.of(), "the heat an idle reactor sent");

            open.clear();
            FissionReactorControllerBE inOpenAir = reactor(open);
            open.tick();
            h.assertTrue(ReactorHeat.knows(open.level, controllerPos(open)), "the reactor wasn't learned");
            running(open, inOpenAir, 100, 50, 120, 100);
            h.assertValueEqual(mine(open, second(open)), List.of(), "the heat a reactor in the open air sent");
        } finally {
            room.clear();
            open.clear();
        }
        h.succeed();
    }

    /**
     * A real reactor, fuelled and left to NuclearCraft and the game, forms, burns and warms its
     * sealed room: what NuclearCraft's controller shows is what the adapter reads. Its fuel makes 50
     * H a tick at full reactivity and at least half that, and its two water sinks could carry 120,
     * so once it has run 400 ticks the room has had at least 25 H a tick of it, and never more
     * than 120; the room's air is a large heat capacity, so that is some tenths of a kelvin.
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void compat_153_a_fuelled_reactor_warms_its_room_in_the_game(GameTestHelper h) {
        Area area = new Area(h, 5);
        area.room();
        FissionReactorControllerBE controller = reactor(area);
        area.form(1, 1, 1);
        area.pin(1, 1, 1);
        IItemHandler items = controller.getItemHandler(null);
        h.assertTrue(items != null, "the controller has no item handler");
        ItemStack left = items.insertItem(0, area.item("fuel_uranium_leu_235"), false);
        h.assertTrue(left.isEmpty(), "the controller took no fuel");
        area.keepTicking();
        double perUnit = NuclearCraftSettings.joulesPerHeatUnit();
        // When the reactor was first seen formed, the room's temperature and heat capacity then.
        double[] formed = {-1, 0, 0};
        h.succeedWhen(() -> {
            h.assertTrue(controller.formed, "the reactor hasn't formed");
            ZoneInfo room = area.zone(1, 1, 1);
            if (formed[0] < 0) {
                formed[0] = h.getTick();
                formed[1] = room.mixture().temperature();
                formed[2] = GasProperties.heatCapacity(room.mixture().moles());
            }
            h.assertTrue(controller.heatBuffer.heatPerTick > 0, "the reactor isn't burning");
            h.assertTrue(controller.heatBuffer.cooldownPerTick > 0, "its sinks carry nothing");
            h.assertTrue(ReactorHeat.knows(area.level, controllerPos(area)), "the reactor wasn't learned");
            double ran = h.getTick() - formed[0];
            h.assertTrue(ran >= 400, "the reactor has run " + ran + " ticks");
            double rise = room.mixture().temperature() - formed[1];
            double least = 25 * 400 * perUnit / formed[2];
            double most = 120 * (ran + 20) * perUnit / formed[2];
            h.assertTrue(
                    rise >= least && rise <= most,
                    "the room warmed " + rise + " K in " + ran + " ticks, not " + least + " to " + most + " K");
            area.clear();
        });
    }
}
