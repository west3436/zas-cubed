package io.github.west3436.zas.compat.mekanism.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.compat.mekanism.HeatFlow;
import io.github.west3436.zas.compat.mekanism.MekanismHeat;
import io.github.west3436.zas.compat.mekanism.MekanismHeat.Flow;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IMekanismHeatHandler;
import mekanism.common.lib.multiblock.FormationProtocol.FormationResult;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Heat between zones and Mekanism's heat blocks on a server that has Mekanism (MEK-040). The blocks
 * are Mekanism's own, in a sealed stone room whose gas is read through the public API; each test
 * heats or chills a block's capacitor, runs the adapter's step for a length of time, and follows
 * the heat to where it lands.
 *
 * <p>All but one build, run and clear within one game tick, driving atmos ticks themselves, so they
 * share the venting tests' batch and leave nothing standing for the adapter's own step, or another
 * test's, to find. The one that leaves the exchange to the game has a batch of its own.
 *
 * <p>Those tests run with no ceiling on what a body passes ({@code [heat] max_body_watts}) unless
 * they set one: the exchange itself is tested with blocks hotter than any ceiling a server may come
 * with, and the ceiling by the tests named for it.
 */
@GameTestHolder(MekanismFoundationTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismHeatTests {
    /** A full block with one heat capacitor. */
    private static final String FUELWOOD_HEATER = "mekanism:fuelwood_heater";

    /** A block with one heat capacitor that doesn't fill its position: gas stands between its fins. */
    private static final String RESISTIVE_HEATER = "mekanism:resistive_heater";

    private static final SourceId SOURCE = SourceId.of(MekanismFoundationTests.NAMESPACE, "heat_gametest");

    /** Keeps an area's chunks loaded, and ticking, for a test that runs over many game ticks; lapses by itself. */
    private static final TicketType<ChunkPos> HELD =
            TicketType.create("zas_mekanism_heat_test", Comparator.comparingLong(ChunkPos::toLong), 1200);

    private MekanismHeatTests() {}

    // ---- Helpers -------------------------------------------------------------------------------

    /**
     * Runs a test in its own area, clears the area, and passes the test. While it runs a body has no
     * ceiling, unless the test sets one; the server's own is put back afterwards.
     */
    private static void inArea(GameTestHelper h, int index, Consumer<VentArea> test) {
        VentArea area = new VentArea(h, index);
        double most = MekanismSettings.maxBodyWatts();
        MekanismSettings.HEAT_MAX_BODY_WATTS.set(0.0);
        try {
            test.accept(area);
        } finally {
            MekanismSettings.HEAT_MAX_BODY_WATTS.set(most);
            area.clear();
        }
        h.succeed();
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

    /** Places a heat block in a room and has the room's zone take account of it. */
    private static IHeatCapacitor heatBlock(VentArea area, int x, int y, int z, String id) {
        TileEntityMekanism block = area.place(x, y, z, id, TileEntityMekanism.class);
        area.tick();
        area.h.assertTrue(MekanismHeat.knows(area.level, block.getBlockPos()), id + " wasn't learned");
        return capacitor(block);
    }

    /** A heat block's one capacitor. */
    private static IHeatCapacitor capacitor(BlockEntity block) {
        List<IHeatCapacitor> capacitors = ((IMekanismHeatHandler) block).getHeatCapacitors(null);
        if (capacitors.size() != 1) {
            throw new GameTestAssertException(block + " has " + capacitors.size() + " heat capacitors");
        }
        return capacitors.getFirst();
    }

    private static void heatTo(IHeatCapacitor capacitor, double kelvin) {
        capacitor.setHeat(kelvin * capacitor.getHeatCapacity());
    }

    /** The step's flows that concern one zone. */
    private static List<Flow> of(List<Flow> flows, ZoneId zone) {
        List<Flow> mine = new ArrayList<>();
        for (Flow flow : flows) {
            if (flow.zone().equals(zone)) {
                mine.add(flow);
            }
        }
        return mine;
    }

    /** The step's one flow to or from a zone. */
    private static Flow only(GameTestHelper h, List<Flow> flows, ZoneId zone) {
        List<Flow> mine = of(flows, zone);
        h.assertValueEqual(mine.size(), 1, "flows between the blocks and the room");
        return mine.getFirst();
    }

    /** Runs atmos ticks until a flow has landed, and says what ZAS³ made of it. */
    private static HeatResult land(VentArea area, Flow flow) {
        for (int i = 0; i < 8 && !flow.result().isDone(); i++) {
            area.tick();
        }
        area.h.assertTrue(flow.result().isDone(), "the heat didn't land within 8 atmos ticks");
        return flow.result().join();
    }

    private static double capacity(ZoneInfo zone) {
        return GasProperties.heatCapacity(zone.mixture().moles());
    }

    /** What two bodies joined by a conductance exchange in a time: worked out here, not by the adapter. */
    private static double expected(double cb, double tb, double cg, double tg, double conductance, double seconds) {
        double reduced = cb * cg / (cb + cg);
        return (tb - tg) * reduced * (1 - Math.exp(-conductance * seconds / reduced));
    }

    /** One face's conductance: the setting in series with the capacitor's own 20 × C / R. */
    private static double face(double setting, double joulesPerUnit, IHeatCapacitor capacitor) {
        double own = 20
                * joulesPerUnit
                * capacitor.getHeatCapacity()
                / (capacitor.getInverseConduction() + capacitor.getInverseInsulation());
        return 1 / (1 / setting + 1 / own);
    }

    // ---- MEK-040 -------------------------------------------------------------------------------

    /**
     * A hot heat block warms the sealed room it stands in by exactly what it loses (MEK-040). A
     * Fuelwood Heater on the floor of a 3 by 3 by 3 room shares five faces with the room's zone; at
     * 1,000 K, one second moves what five faces of 10 W/K, each in series with the heater's own
     * insulation, carry between its capacitor and the room's gas. The capacitor has lost that at
     * once, the zone is given that and takes it all, and its temperature rises by that over its
     * heat capacity.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_a_hot_block_warms_its_room_by_what_it_loses(GameTestHelper h) {
        inArea(h, 40, area -> {
            area.room();
            area.form(2, 2, 2);
            IHeatCapacitor capacitor = heatBlock(area, 2, 1, 2, FUELWOOD_HEATER);
            heatTo(capacitor, 1000);
            ZoneInfo before = area.zone(2, 2, 2);
            double cb = capacitor.getHeatCapacity();
            double cg = capacity(before);
            double tg = before.mixture().temperature();
            double joules = expected(cb, 1000, cg, tg, 5 * face(10, 1, capacitor), 1);
            h.assertTrue(joules > 0.2 * cb * (1000 - tg), "the test moves too little to show anything: " + joules);

            Flow flow = only(h, MekanismHeat.step(area.level, 1), before.id());
            close(h, flow.joules(), joules, 1e-9, "the heat sent to the room, J");
            close(h, capacitor.getHeat(), 1000 * cb - joules, 1e-9, "the capacitor's heat afterwards");

            HeatResult landed = land(area, flow);
            h.assertValueEqual(landed.outcome(), MutationOutcome.ZONE, "where the heat went");
            close(h, landed.joules(), joules, 1e-9, "the heat the zone took, J");
            h.assertTrue(joules / cg > 0.5, "the room warms too little to show anything: " + joules / cg + " K");
            kelvin(h, landed.temperature(), tg + joules / cg, "the zone's temperature afterwards");
            kelvin(h, area.zone(2, 2, 2).mixture().temperature(), tg + joules / cg, "the room's temperature");
        });
    }

    /**
     * A cold heat block cools its room by exactly what it gains (MEK-040). The capacitor gains
     * nothing until the zone has delivered the heat, and then gains what was delivered. The block is
     * a Resistive Heater, which gas stands in: its sides count as any other block's.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_a_cold_block_cools_its_room_by_what_it_gains(GameTestHelper h) {
        inArea(h, 41, area -> {
            area.room();
            area.form(2, 2, 2);
            IHeatCapacitor capacitor = heatBlock(area, 2, 1, 2, RESISTIVE_HEATER);
            heatTo(capacitor, 100);
            ZoneInfo before = area.zone(2, 2, 2);
            h.assertValueEqual(
                    Atmospheres.at(area.level, area.abs(2, 1, 2)).zone(),
                    Optional.of(before.id()),
                    "the zone of the gas the heater stands in");
            double cb = capacitor.getHeatCapacity();
            double cg = capacity(before);
            double tg = before.mixture().temperature();
            double joules = expected(cb, 100, cg, tg, 5 * face(10, 1, capacitor), 1);
            h.assertTrue(joules < -0.2 * cb * (tg - 100), "the test moves too little to show anything: " + joules);

            Flow flow = only(h, MekanismHeat.step(area.level, 1), before.id());
            close(h, flow.joules(), joules, 1e-9, "the heat asked of the room, J");
            close(h, capacitor.getHeat(), 100 * cb, 0, "the capacitor's heat before the zone delivers");

            HeatResult landed = land(area, flow);
            h.assertValueEqual(landed.outcome(), MutationOutcome.ZONE, "where the heat came from");
            close(h, landed.joules(), -joules, 1e-9, "the heat the zone delivered, J");
            kelvin(h, landed.temperature(), tg + joules / cg, "the zone's temperature afterwards");
            close(h, capacitor.getHeat(), 100 * cb - joules, 1e-9, "the capacitor's heat once delivered");
        });
    }

    /**
     * The exchange stops at equilibrium (MEK-040, HEAT-006). With the most a face can be set to
     * conduct and an hour in one step, a 2,000 K block and its room end at the one temperature their
     * heat capacities give, and neither passes it. A zone that still has heat on its way sits a step
     * out, so a second step at once moves nothing; once it has landed, nothing is left to move.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_the_exchange_stops_at_equilibrium(GameTestHelper h) {
        double setting = MekanismSettings.heatFaceConductance();
        inArea(h, 42, area -> {
            try {
                area.room();
                area.form(2, 2, 2);
                IHeatCapacitor capacitor = heatBlock(area, 2, 1, 2, FUELWOOD_HEATER);
                heatTo(capacitor, 2000);
                ZoneInfo before = area.zone(2, 2, 2);
                double cb = capacitor.getHeatCapacity();
                double cg = capacity(before);
                double tg = before.mixture().temperature();
                double meet = (cb * 2000 + cg * tg) / (cb + cg);

                MekanismSettings.HEAT_FACE_CONDUCTANCE.set(HeatFlow.MAX_FACE_CONDUCTANCE);
                Flow flow = only(h, MekanismHeat.step(area.level, 3600), before.id());
                close(h, flow.joules(), cb * (2000 - meet), 1e-9, "the heat to equilibrium, J");
                close(h, capacitor.getTemperature(), meet, 1e-9, "the block's temperature, K");

                // The heat is still on its way: the zone reads as it did, and sits this step out.
                h.assertValueEqual(
                        of(MekanismHeat.step(area.level, 3600), before.id()), List.of(), "a second step at once");
                close(h, capacitor.getTemperature(), meet, 1e-9, "the block's temperature after it, K");

                HeatResult landed = land(area, flow);
                kelvin(h, landed.temperature(), meet, "the zone's temperature");
                // Landed: the two are level, so the step that makes up the time has nothing to move.
                for (Flow more : of(MekanismHeat.step(area.level, 3600), before.id())) {
                    h.assertTrue(
                            Math.abs(more.joules()) < 1e-3 * cb * (2000 - meet),
                            "heat moved between a block and a room at one temperature: " + more.joules() + " J");
                }
            } finally {
                MekanismSettings.HEAT_FACE_CONDUCTANCE.set(setting);
            }
        });
    }

    /**
     * A multiblock's shared capacitor is counted once (MEK-040). A Thermal Evaporation Plant's two
     * valves expose the one capacitor of the plant, and each shares two faces with the room the
     * plant stands in: one outside, and one inside, where the plant is open to the room. It is one
     * body with four faces: a second moves what four faces carry from one capacitor, and a step long
     * enough brings the plant level with the room once, not once for each valve.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_a_multiblocks_shared_capacitor_is_counted_once(GameTestHelper h) {
        inArea(h, 43, area -> {
            area.box(9, 6, 9);
            area.form(1, 1, 1);
            List<TileEntityMekanism> plant = plant(area);
            TileEntityMekanism west = plant.get(0);
            TileEntityMekanism east = plant.get(1);
            area.tick();
            IHeatCapacitor capacitor = capacitor(west);
            h.assertTrue(capacitor == capacitor(east), "the two valves don't share a capacitor");
            h.assertTrue(MekanismHeat.knows(area.level, west.getBlockPos()), "a valve wasn't learned");
            h.assertTrue(MekanismHeat.knows(area.level, east.getBlockPos()), "a valve wasn't learned");

            // The room's zone reaches inside the plant through its open top.
            ZoneInfo before = area.zone(1, 1, 1);
            h.assertValueEqual(area.zone(4, 2, 4).id(), before.id(), "the zone inside the plant");
            heatTo(capacitor, 1500);
            double cb = capacitor.getHeatCapacity();
            double cg = capacity(before);
            double tg = before.mixture().temperature();

            // Four faces, in a step short enough to tell four from any other number.
            double joules = expected(cb, 1500, cg, tg, 4 * face(10, 1, capacitor), 1);
            Flow flow = only(h, MekanismHeat.step(area.level, 1), before.id());
            close(h, flow.joules(), joules, 1e-9, "the heat four faces carry in 1 s, J");
            close(h, capacitor.getHeat(), 1500 * cb - joules, 1e-9, "the plant's heat afterwards");
            land(area, flow);

            // And to equilibrium: once.
            ZoneInfo warmed = area.zone(1, 1, 1);
            double tb = capacitor.getTemperature();
            double cw = capacity(warmed);
            double tw = warmed.mixture().temperature();
            double meet = (cb * tb + cw * tw) / (cb + cw);
            flow = only(h, MekanismHeat.step(area.level, 1e7), warmed.id());
            close(h, flow.joules(), cb * (tb - meet), 1e-9, "the heat to equilibrium, J");
            close(h, capacitor.getTemperature(), meet, 1e-9, "the plant's temperature, K");
            land(area, flow);
        });
    }

    /**
     * Builds the smallest Thermal Evaporation Plant, 4 by 3 by 4, on the floor of a room, with (3, 1,
     * 3) its low corner, and forms it: a valve in its west wall and one in its east, both in the
     * middle layer, and its controller in the top edge. The two valves come first in the list.
     */
    private static List<TileEntityMekanism> plant(VentArea area) {
        List<TileEntityMekanism> parts = new ArrayList<>();
        parts.add(area.place(3, 2, 4, "mekanism:thermal_evaporation_valve", TileEntityMekanism.class));
        parts.add(area.place(6, 2, 5, "mekanism:thermal_evaporation_valve", TileEntityMekanism.class));
        TileEntityMultiblock<?> controller =
                area.place(4, 3, 3, "mekanism:thermal_evaporation_controller", TileEntityMultiblock.class);
        parts.add(controller);
        for (int x = 3; x <= 6; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 3; z <= 6; z++) {
                    boolean inside = x > 3 && x < 6 && z > 3 && z < 6 && y > 1;
                    if (inside || !area.level.getBlockState(area.abs(x, y, z)).isAir()) {
                        continue;
                    }
                    parts.add(area.place(x, y, z, "mekanism:thermal_evaporation_block", TileEntityMekanism.class));
                }
            }
        }
        // Each part's ticks find the others; then the plant forms, as it would a game tick after
        // its last block was set, and each part's next tick takes account of it.
        tick(parts, 12);
        FormationResult formed = controller.getStructure().runUpdate(controller);
        area.h.assertTrue(formed.isFormed(), "the plant didn't form");
        tick(parts, 1);
        area.h.assertTrue(controller.getMultiblock().isFormed(), "the plant isn't formed");
        return parts;
    }

    /**
     * Builds a Fusion Reactor in a room, with (2, 2, 2) its low corner, and forms it. It is 5 by 5 by
     * 5 with no corners and only the middle of each edge, every block a frame but for the controller
     * in the middle of the top, a port in the middle of the west side and one in the middle of the
     * east. The two ports are returned, the west one first.
     */
    private static List<TileEntityMekanism> fusionReactor(VentArea area) {
        List<TileEntityMekanism> parts = new ArrayList<>();
        List<TileEntityMekanism> ports = new ArrayList<>();
        TileEntityMultiblock<?> controller = null;
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    int outer = (x % 4 == 0 ? 1 : 0) + (y % 4 == 0 ? 1 : 0) + (z % 4 == 0 ? 1 : 0);
                    boolean edge = outer == 2 && (x == 2 || y == 2 || z == 2);
                    if (outer == 0 || outer == 3 || outer == 2 && !edge) {
                        continue; // inside, a corner, or an edge away from its middle
                    }
                    String id = "mekanismgenerators:fusion_reactor_frame";
                    if (x == 2 && y == 4 && z == 2) {
                        id = "mekanismgenerators:fusion_reactor_controller";
                    } else if (y == 2 && z == 2 && (x == 0 || x == 4)) {
                        id = "mekanismgenerators:fusion_reactor_port";
                    }
                    TileEntityMekanism part = area.place(2 + x, 2 + y, 2 + z, id, TileEntityMekanism.class);
                    parts.add(part);
                    if (id.endsWith("controller")) {
                        controller = (TileEntityMultiblock<?>) part;
                    } else if (id.endsWith("port")) {
                        ports.add(part);
                    }
                }
            }
        }
        if (controller == null) {
            throw new GameTestAssertException("the reactor has no controller");
        }
        tick(parts, 12);
        FormationResult formed = controller.getStructure().runUpdate(controller);
        area.h.assertTrue(formed.isFormed(), "the reactor didn't form");
        tick(parts, 1);
        area.h.assertTrue(controller.getMultiblock().isFormed(), "the reactor isn't formed");
        return ports;
    }

    private static void tick(List<TileEntityMekanism> parts, int ticks) {
        for (int i = 0; i < ticks; i++) {
            for (TileEntityMekanism part : parts) {
                VentArea.run(part, 1);
            }
        }
    }

    /**
     * Heat blocks are learned without a search (MEK-040). One that is set is known from then on, and
     * so is a Thermodynamic Conductor, which exposes heat on no side until it connects. A block
     * entity next to a block that is set is looked at again. One that was in a chunk when it loaded
     * is known from that chunk's block entities. A block entity that exposes no heat isn't known,
     * and a block that is gone is forgotten at the next step.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_heat_blocks_are_learned_as_they_are_set_and_as_chunks_load(GameTestHelper h) {
        inArea(h, 44, area -> {
            area.room();
            area.form(2, 2, 2);
            BlockPos set = area.abs(2, 1, 2);
            h.assertFalse(MekanismHeat.knows(area.level, set), "an empty position is known");
            area.place(2, 1, 2, FUELWOOD_HEATER, TileEntityMekanism.class);
            h.assertTrue(MekanismHeat.knows(area.level, set), "a heater that was set isn't known");

            area.place(1, 1, 1, "mekanism:basic_thermodynamic_conductor", BlockEntity.class);
            h.assertTrue(MekanismHeat.knows(area.level, area.abs(1, 1, 1)), "a conductor on its own isn't known");

            // Set with no neighbour update, as nothing in play does, a heater stays unknown...
            BlockPos quiet = area.abs(3, 1, 3);
            area.level.setBlock(quiet, area.block(FUELWOOD_HEATER), 2);
            h.assertFalse(MekanismHeat.knows(area.level, quiet), "a block set with no update is known");
            // ...until a block with a block entity is set next to it. A chest has one, and no heat.
            area.set(3, 2, 3, Blocks.CHEST.defaultBlockState());
            h.assertTrue(MekanismHeat.knows(area.level, quiet), "a heater next to a block that was set isn't known");
            h.assertFalse(MekanismHeat.knows(area.level, area.abs(3, 2, 3)), "a chest is known");

            // Or until its chunk loads.
            BlockPos saved = area.abs(1, 3, 1);
            area.level.setBlock(saved, area.block(FUELWOOD_HEATER), 2);
            h.assertFalse(MekanismHeat.knows(area.level, saved), "a block set with no update is known");
            MekanismHeat.chunkLoaded(new ChunkEvent.Load(area.level.getChunkAt(saved), false));
            h.assertTrue(MekanismHeat.knows(area.level, saved), "a heater in a chunk that loaded isn't known");
            h.assertFalse(MekanismHeat.knows(area.level, area.abs(3, 2, 3)), "a chest in a chunk that loaded is known");

            area.set(2, 1, 2, Blocks.AIR.defaultBlockState());
            MekanismHeat.step(area.level, 1);
            h.assertFalse(MekanismHeat.knows(area.level, set), "a heater that is gone is still known");
            h.assertTrue(MekanismHeat.knows(area.level, saved), "another heater was forgotten with it");
        });
    }

    /**
     * Only a zone's gas at a face takes part (MEK-040). A hot heat block in the open air, where
     * Mekanism has its own loss, exchanges nothing. Nor does one with a full block on every side,
     * though a room is just beyond one of them. Nor does any block while {@code face_conductance}
     * is 0.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_open_air_and_solid_neighbours_take_no_part(GameTestHelper h) {
        double setting = MekanismSettings.heatFaceConductance();
        inArea(h, 45, area -> {
            try {
                area.room();
                area.form(2, 2, 2);
                ZoneId room = area.zone(2, 2, 2).id();

                // Against the outside of the room's wall, boxed in with stone: the wall block at
                // (0, 2, 2) is all that parts it from the room's gas at (1, 2, 2).
                area.stone(-2, 1, 1, -1, 3, 3);
                IHeatCapacitor walled = heatBlock(area, -1, 2, 2, FUELWOOD_HEATER);
                heatTo(walled, 1000);
                // In the open air, well clear of the room.
                IHeatCapacitor open = heatBlock(area, 2, 7, 2, FUELWOOD_HEATER);
                heatTo(open, 1000);
                h.assertValueEqual(MekanismHeat.step(area.level, 1), List.of(), "flows with no zone at any face");
                close(h, walled.getTemperature(), 1000, 0, "the walled heater's temperature");
                close(h, open.getTemperature(), 1000, 0, "the open heater's temperature");

                // One in the room does exchange, unless the setting is 0.
                IHeatCapacitor inside = heatBlock(area, 2, 1, 2, FUELWOOD_HEATER);
                heatTo(inside, 1000);
                MekanismSettings.HEAT_FACE_CONDUCTANCE.set(0.0);
                h.assertValueEqual(MekanismHeat.step(area.level, 1), List.of(), "flows with the exchange off");
                close(h, inside.getTemperature(), 1000, 0, "the heater's temperature with the exchange off");
                MekanismSettings.HEAT_FACE_CONDUCTANCE.set(setting);
                land(area, only(h, MekanismHeat.step(area.level, 1), room));
                h.assertTrue(inside.getTemperature() < 1000, "the heater in the room kept its heat");
            } finally {
                MekanismSettings.HEAT_FACE_CONDUCTANCE.set(setting);
            }
        });
    }

    /**
     * Gas too thin to carry heat takes no part (MEK-040, HEAT-008). With all but a thousandth of a
     * room's gas taken out, it is below the server's near-vacuum density, and a hot block in it
     * keeps its heat; with the room filled again, the block warms it.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_gas_too_thin_to_carry_heat_takes_no_part(GameTestHelper h) {
        inArea(h, 46, area -> {
            area.room();
            area.form(2, 2, 2);
            IHeatCapacitor capacitor = heatBlock(area, 2, 1, 2, FUELWOOD_HEATER);
            ZoneInfo full = area.zone(2, 2, 2);
            Vec3 point = Vec3.atCenterOf(area.abs(2, 2, 2));
            Map<ResourceLocation, Double> most = new HashMap<>();
            full.mixture().moles().forEach((gas, moles) -> most.put(gas, moles * 0.999));
            CompletableFuture<MutationResult> removed = GasMutations.remove(area.level, point, most, SOURCE);
            for (int i = 0; i < 8 && !removed.isDone(); i++) {
                area.tick();
            }
            h.assertTrue(removed.isDone(), "the gas wasn't taken out within 8 atmos ticks");
            ZoneInfo thin = area.zone(2, 2, 2);
            h.assertTrue(
                    thin.mixture().totalMoles() > 0
                            && HeatFlow.thin(
                                    thin.mixture().totalMoles(),
                                    thin.mixture().volume(),
                                    Atmospheres.nearVacuumDensity()),
                    "the room isn't a near vacuum: " + thin.mixture().totalMoles() + " mol in "
                            + thin.mixture().volume() + " L");

            heatTo(capacitor, 1000);
            h.assertValueEqual(MekanismHeat.step(area.level, 1), List.of(), "flows to a near vacuum");
            close(h, capacitor.getTemperature(), 1000, 0, "the heater's temperature in a near vacuum");

            CompletableFuture<MutationResult> back =
                    GasMutations.add(area.level, point, removed.join().moved(), SOURCE);
            for (int i = 0; i < 8 && !back.isDone(); i++) {
                area.tick();
            }
            h.assertTrue(back.isDone(), "the gas wasn't put back within 8 atmos ticks");
            Flow flow = only(h, MekanismHeat.step(area.level, 1), full.id());
            h.assertTrue(flow.joules() > 0, "the heater didn't warm the room once it held gas again");
            land(area, flow);
        });
    }

    /**
     * The exchange steps once an atmos tick, for an atmos tick's length (MEK-040). At a game time on
     * the atmos period it moves what that length of time moves. At any other it moves nothing. At
     * the same game time again, as while the game is frozen, it moves nothing more.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_the_exchange_steps_once_an_atmos_tick(GameTestHelper h) {
        inArea(h, 49, area -> {
            area.room();
            area.form(2, 2, 2);
            IHeatCapacitor capacitor = heatBlock(area, 2, 1, 2, FUELWOOD_HEATER);
            heatTo(capacitor, 1000);
            ZoneInfo before = area.zone(2, 2, 2);
            int period = Atmospheres.periodTicks();
            double cb = capacitor.getHeatCapacity();
            double joules = expected(
                    cb,
                    1000,
                    capacity(before),
                    before.mixture().temperature(),
                    5 * face(10, 1, capacitor),
                    period / 20.0);
            // A game time on the period, far from any the level will reach.
            long time = 1_000_000L * period;

            h.assertValueEqual(MekanismHeat.ticked(area.level, time + 1), List.of(), "flows off the atmos tick");
            Flow flow = only(h, MekanismHeat.ticked(area.level, time), before.id());
            close(h, flow.joules(), joules, 1e-9, "the heat an atmos tick's length moves, J");
            land(area, flow);
            double heat = capacitor.getHeat();
            h.assertValueEqual(MekanismHeat.ticked(area.level, time), List.of(), "flows at the same game time again");
            close(h, capacitor.getHeat(), heat, 0, "the capacitor's heat while the game's time stands still");
            land(area, only(h, MekanismHeat.ticked(area.level, time + period), before.id()));
            h.assertTrue(capacitor.getHeat() < heat, "the next atmos tick moved nothing");
        });
    }

    /**
     * The heat settings are read at each step (MEK-040, OPS-016). Twice the face conductance carries
     * what twice the conductance carries. At 2 J to a unit of Mekanism's heat the capacitor counts
     * for twice the heat capacity, and loses half as many units as the zone gains joules. At 100 W a
     * body at most, five faces that would carry kilowatts pass 100 J in a second between them.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_the_heat_settings_are_read_on_use(GameTestHelper h) {
        double setting = MekanismSettings.heatFaceConductance();
        double unit = MekanismSettings.joulesPerHeatUnit();
        h.assertValueEqual(setting, HeatFlow.DEFAULT_FACE_CONDUCTANCE, "face_conductance by default");
        h.assertValueEqual(unit, HeatFlow.DEFAULT_JOULES_PER_HEAT_UNIT, "joules_per_heat_unit by default");
        inArea(h, 47, area -> {
            try {
                area.room();
                area.form(2, 2, 2);
                IHeatCapacitor capacitor = heatBlock(area, 2, 1, 2, FUELWOOD_HEATER);
                double cb = capacitor.getHeatCapacity();

                MekanismSettings.HEAT_FACE_CONDUCTANCE.set(20.0);
                heatTo(capacitor, 1000);
                ZoneInfo before = area.zone(2, 2, 2);
                double joules = expected(
                        cb, 1000, capacity(before), before.mixture().temperature(), 5 * face(20, 1, capacitor), 1);
                Flow flow = only(h, MekanismHeat.step(area.level, 1), before.id());
                close(h, flow.joules(), joules, 1e-9, "the heat at 20 W/K a face, J");
                land(area, flow);

                MekanismSettings.HEAT_FACE_CONDUCTANCE.set(setting);
                MekanismSettings.HEAT_JOULES_PER_UNIT.set(2.0);
                heatTo(capacitor, 1000);
                before = area.zone(2, 2, 2);
                joules = expected(
                        2 * cb, 1000, capacity(before), before.mixture().temperature(), 5 * face(10, 2, capacitor), 1);
                flow = only(h, MekanismHeat.step(area.level, 1), before.id());
                close(h, flow.joules(), joules, 1e-9, "the heat at 2 J a unit, J");
                close(h, capacitor.getHeat(), 1000 * cb - joules / 2, 1e-9, "the capacitor's heat, in units");
                close(h, land(area, flow).joules(), joules, 1e-9, "the heat the zone took, J");

                MekanismSettings.HEAT_JOULES_PER_UNIT.set(unit);
                MekanismSettings.HEAT_MAX_BODY_WATTS.set(100.0);
                heatTo(capacitor, 1000);
                before = area.zone(2, 2, 2);
                flow = only(h, MekanismHeat.step(area.level, 1), before.id());
                close(h, flow.joules(), 100, 1e-9, "the heat at 100 W a body at most, J");
                close(h, capacitor.getHeat(), 1000 * cb - 100, 1e-9, "the capacitor's heat");
                close(h, land(area, flow).joules(), 100, 1e-9, "the heat the zone took, J");
            } finally {
                MekanismSettings.HEAT_FACE_CONDUCTANCE.set(setting);
                MekanismSettings.HEAT_JOULES_PER_UNIT.set(unit);
            }
        });
    }

    /**
     * A Fusion Reactor passes no more than the most watts set for a body, and under that passes what
     * it did (MEK-040). A formed reactor stands clear in a room, with a port in its west side and one
     * in its east: one case, two faces on the room. Mekanism gives the case a heat capacity of 1 and
     * an insulation of 100,000, so a face conducts 0.0002 W/K. With the case at 9.8 × 10⁹ K, which is
     * Mekanism's figure for an air-cooled reactor at its highest injection rate, a face would pass
     * 1.96 MW. At 45 kW a body at most, the two faces pass 45 kJ in a second between them, the case
     * loses just that and the room gains just that. At 2 × 10⁷ K the two pass 8 kW with the ceiling
     * or without. With no ceiling the hot case's two faces pass their 3.9 MJ.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_a_fusion_reactor_passes_no_more_than_the_most_watts_set(GameTestHelper h) {
        inArea(h, 50, area -> {
            area.box(8, 8, 8);
            List<TileEntityMekanism> ports = fusionReactor(area);
            // The room's zone forms round the reactor: what is inside the reactor is no part of it.
            area.form(1, 1, 1);
            area.tick();
            IHeatCapacitor casing = capacitor(ports.get(0));
            h.assertTrue(casing == capacitor(ports.get(1)), "the two ports don't share the case's capacitor");
            for (TileEntityMekanism port : ports) {
                h.assertTrue(MekanismHeat.knows(area.level, port.getBlockPos()), "a port wasn't learned");
            }
            double cb = casing.getHeatCapacity();
            double conductance = 2 * face(10, 1, casing);
            close(h, cb, 1, 0, "the case's heat capacity");
            h.assertTrue(
                    Math.abs(conductance - 2 * 0.0002) < 1e-7,
                    "the two port faces conduct " + conductance + " W/K, not 0.0004");
            double hot = 9.8e9;

            // 45 kW a body at most: 45 kJ in a second, from both faces together.
            MekanismSettings.HEAT_MAX_BODY_WATTS.set(45_000.0);
            heatTo(casing, hot);
            ZoneInfo before = area.zone(1, 1, 1);
            double cg = capacity(before);
            double tg = before.mixture().temperature();
            Flow flow = only(h, MekanismHeat.step(area.level, 1), before.id());
            close(h, flow.joules(), 45_000, 1e-9, "the heat the reactor passes at 45 kW at most, J");
            close(h, hot * cb - casing.getHeat(), 45_000, 1e-6, "the heat the case lost");
            HeatResult landed = land(area, flow);
            close(h, landed.joules(), 45_000, 1e-9, "the heat the zone took, J");
            kelvin(h, landed.temperature(), tg + 45_000 / cg, "the zone's temperature afterwards");

            // A cooler case, whose two faces pass 8 kW: the ceiling changes nothing.
            heatTo(casing, 2e7);
            before = area.zone(1, 1, 1);
            double under = expected(cb, 2e7, capacity(before), before.mixture().temperature(), conductance, 1);
            h.assertTrue(under > 7_000 && under < 9_000, "the test's cooler case passes " + under + " J");
            flow = only(h, MekanismHeat.step(area.level, 1), before.id());
            close(h, flow.joules(), under, 1e-9, "the heat the reactor passes under the ceiling, J");
            land(area, flow);

            // No ceiling: megawatts.
            MekanismSettings.HEAT_MAX_BODY_WATTS.set(0.0);
            heatTo(casing, hot);
            before = area.zone(1, 1, 1);
            double free = expected(cb, hot, capacity(before), before.mixture().temperature(), conductance, 1);
            h.assertTrue(free > 2 * 1.9e6, "two faces with no ceiling pass " + free + " J");
            flow = only(h, MekanismHeat.step(area.level, 1), before.id());
            close(h, flow.joules(), free, 1e-9, "the heat the reactor passes with no ceiling, J");
            close(h, land(area, flow).joules(), free, 1e-9, "the heat the zone took, J");
        });
    }

    /**
     * A body's faces share one ceiling, whichever way the heat goes, and the exchange still ends
     * level (MEK-040). A heater's five faces would pass 26 kW from 1,000 K: at 10 kW a body at most
     * they pass 10 kJ in a second between them, not 10 kJ each. At 100 K the heater would take
     * kilowatts: at 500 W at most it asks the room for 500 J in a second, and gains them when the
     * room delivers. Given an hour, the two end at the one temperature their heat capacities give,
     * as with no ceiling.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_a_bodys_faces_share_one_ceiling_whichever_way_the_heat_goes(GameTestHelper h) {
        inArea(h, 51, area -> {
            area.room();
            area.form(2, 2, 2);
            IHeatCapacitor capacitor = heatBlock(area, 2, 1, 2, FUELWOOD_HEATER);
            double cb = capacitor.getHeatCapacity();

            MekanismSettings.HEAT_MAX_BODY_WATTS.set(10_000.0);
            heatTo(capacitor, 1000);
            ZoneInfo before = area.zone(2, 2, 2);
            double cg = capacity(before);
            double tg = before.mixture().temperature();
            double free = expected(cb, 1000, cg, tg, 5 * face(10, 1, capacitor), 1);
            h.assertTrue(free > 2 * 10_000, "the test's heater would pass only " + free + " J");
            Flow flow = only(h, MekanismHeat.step(area.level, 1), before.id());
            close(h, flow.joules(), 10_000, 1e-9, "the heat five faces pass at 10 kW a body at most, J");
            close(h, capacitor.getHeat(), 1000 * cb - 10_000, 1e-9, "the capacitor's heat afterwards");
            close(h, land(area, flow).joules(), 10_000, 1e-9, "the heat the zone took, J");

            MekanismSettings.HEAT_MAX_BODY_WATTS.set(500.0);
            heatTo(capacitor, 100);
            before = area.zone(2, 2, 2);
            tg = before.mixture().temperature();
            flow = only(h, MekanismHeat.step(area.level, 1), before.id());
            close(h, flow.joules(), -500, 1e-9, "the heat asked of the room, J");
            close(h, capacitor.getHeat(), 100 * cb, 0, "the capacitor's heat before the zone delivers");
            HeatResult landed = land(area, flow);
            close(h, landed.joules(), 500, 1e-9, "the heat the zone delivered, J");
            kelvin(h, landed.temperature(), tg - 500 / cg, "the zone's temperature afterwards");
            close(h, capacitor.getHeat(), 100 * cb + 500, 1e-9, "the capacitor's heat once delivered");

            // An hour is far longer than 500 W take to bring the two level.
            before = area.zone(2, 2, 2);
            tg = before.mixture().temperature();
            double tb = capacitor.getTemperature();
            double meet = (cb * tb + cg * tg) / (cb + cg);
            h.assertTrue(cb * (meet - tb) < 500 * 3600, "an hour isn't enough for the test's heater");
            flow = only(h, MekanismHeat.step(area.level, 3600), before.id());
            close(h, flow.joules(), -cb * (meet - tb), 1e-9, "the heat to equilibrium, J");
            landed = land(area, flow);
            kelvin(h, landed.temperature(), meet, "the zone's temperature");
            close(h, capacitor.getTemperature(), meet, 1e-9, "the block's temperature, K");
        });
    }

    /**
     * A body between two rooms shares its ceiling out between them by what each would be passed
     * (MEK-040). A heater stands in the wall between two rooms, a face on each, and one room is the
     * warmer, so the heater at 1,000 K would pass it less. Together the faces would pass over 10 kW.
     * At 5 kW a body at most each room gets its part of the 5 kJ a second, in that proportion, and
     * the heater loses 5 kJ.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_a_ceiling_is_shared_out_between_a_bodys_rooms(GameTestHelper h) {
        inArea(h, 52, area -> {
            // Two rooms, x 1 to 3 and x 5 to 7, with the wall between them at x 4.
            area.box(8, 4, 4);
            area.stone(4, 1, 1, 4, 3, 3);
            area.form(1, 1, 1);
            area.form(7, 1, 1);
            IHeatCapacitor capacitor = heatBlock(area, 4, 2, 2, FUELWOOD_HEATER);
            ZoneInfo east = area.zone(7, 1, 1);
            h.assertTrue(!east.id().equals(area.zone(1, 1, 1).id()), "the two rooms are one zone");
            CompletableFuture<HeatResult> warmed =
                    HeatMutations.add(area.level, Vec3.atCenterOf(area.abs(7, 1, 1)), 150 * capacity(east), SOURCE);
            for (int i = 0; i < 8 && !warmed.isDone(); i++) {
                area.tick();
            }
            h.assertTrue(warmed.isDone(), "the east room wasn't warmed within 8 atmos ticks");
            area.tick();

            MekanismSettings.HEAT_MAX_BODY_WATTS.set(5_000.0);
            heatTo(capacitor, 1000);
            ZoneInfo west = area.zone(1, 1, 1);
            east = area.zone(7, 1, 1);
            double cb = capacitor.getHeatCapacity();
            double face = face(10, 1, capacitor);
            double toWest = face * (1000 - west.mixture().temperature());
            double toEast = face * (1000 - east.mixture().temperature());
            h.assertTrue(toEast < 0.9 * toWest, "the east room isn't the warmer by enough to show");
            h.assertTrue(toWest + toEast > 2 * 5_000, "the test's heater would pass only " + (toWest + toEast));

            List<Flow> flows = MekanismHeat.step(area.level, 1);
            Flow westward = only(h, flows, west.id());
            Flow eastward = only(h, flows, east.id());
            close(h, westward.joules(), 5_000 * toWest / (toWest + toEast), 1e-9, "the west room's part, J");
            close(h, eastward.joules(), 5_000 * toEast / (toWest + toEast), 1e-9, "the east room's part, J");
            close(h, westward.joules() + eastward.joules(), 5_000, 1e-9, "the heat the heater passes, J");
            close(h, capacitor.getHeat(), 1000 * cb - 5_000, 1e-9, "the capacitor's heat afterwards");
            close(h, land(area, westward).joules(), westward.joules(), 1e-9, "the heat the west room took, J");
            close(h, land(area, eastward).joules(), eastward.joules(), 1e-9, "the heat the east room took, J");
        });
    }

    /**
     * A room that sits a step out keeps its part of a body's ceiling (MEK-040). A heater in the west
     * room alone sends it heat, which is on its way until the next atmos tick, so the west room sits
     * the next step out. A heater in the wall between the west room and the east, at 5 kW a body at
     * most, then passes the east room its half of the 5 kJ a second and no more, though its east face
     * alone would pass over 6 kW.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_040_a_room_that_sits_a_step_out_keeps_its_part_of_the_ceiling(GameTestHelper h) {
        inArea(h, 53, area -> {
            area.box(8, 4, 4);
            area.stone(4, 1, 1, 4, 3, 3);
            area.form(1, 1, 1);
            area.form(7, 1, 1);
            IHeatCapacitor alone = heatBlock(area, 2, 1, 2, FUELWOOD_HEATER);
            MekanismSettings.HEAT_MAX_BODY_WATTS.set(5_000.0);
            heatTo(alone, 1000);
            ZoneInfo west = area.zone(1, 1, 1);
            ZoneInfo east = area.zone(7, 1, 1);
            h.assertTrue(!east.id().equals(west.id()), "the two rooms are one zone");
            List<Flow> first = MekanismHeat.step(area.level, 1);
            Flow sent = only(h, first, west.id());
            h.assertTrue(first.size() == 1, "the first step sent heat to " + first.size() + " zones");

            // The heater in the wall, set with no atmos tick: the west room's heat is still on its way.
            TileEntityMekanism wall = area.place(4, 2, 2, FUELWOOD_HEATER, TileEntityMekanism.class);
            h.assertTrue(MekanismHeat.knows(area.level, wall.getBlockPos()), "the heater in the wall wasn't learned");
            IHeatCapacitor capacitor = capacitor(wall);
            double cb = capacitor.getHeatCapacity();
            heatTo(capacitor, 1000);
            double held = alone.getHeat();
            double face = face(10, 1, capacitor);
            double toWest = face * (1000 - west.mixture().temperature());
            double toEast = face * (1000 - east.mixture().temperature());
            h.assertTrue(toEast > 5_000, "the east face alone would pass only " + toEast + " W");

            List<Flow> second = MekanismHeat.step(area.level, 1);
            Flow eastward = only(h, second, east.id());
            h.assertTrue(second.size() == 1, "the second step sent heat to " + second.size() + " zones");
            close(h, eastward.joules(), 5_000 * toEast / (toWest + toEast), 1e-9, "the east room's part, J");
            close(h, capacitor.getHeat(), 1000 * cb - eastward.joules(), 1e-9, "the heat of the heater in the wall");
            close(h, alone.getHeat(), held, 0, "the heat of the heater whose room sits the step out");
            close(h, land(area, sent).joules(), sent.joules(), 1e-9, "the heat the west room took, J");
            close(h, land(area, eastward).joules(), eastward.joules(), 1e-9, "the heat the east room took, J");
        });
    }

    /**
     * Left to the game, a hot block warms its room, and the room gains no more than the block has
     * lost (MEK-040). Nothing here runs the adapter: its own listeners learn the heater as it is
     * set and exchange once an atmos tick, and the atmos service lands the heat in its own time.
     * Within a few seconds most of the heat the heater held above the room is in the room; the rest
     * is Mekanism's own loss to its ambient, and what the room's walls have carried off.
     *
     * <p>It stands for many game ticks, so it has a batch of its own: no other test's step finds
     * its heater, and no other test's atmos ticks move its room.
     */
    @GameTest(template = "empty", batch = "zas_mekanism_heat_live", timeoutTicks = 1000)
    public static void mek_040_a_hot_block_warms_its_room_as_the_game_ticks(GameTestHelper h) {
        VentArea area = new VentArea(h, 48);
        ChunkPos chunk = new ChunkPos(area.origin);
        area.level.getChunkSource().addRegionTicket(HELD, chunk, 2, chunk);
        area.room();
        area.form(2, 2, 2);
        IHeatCapacitor capacitor = capacitor(area.place(2, 1, 2, FUELWOOD_HEATER, TileEntityMekanism.class));
        heatTo(capacitor, 1500);
        ZoneInfo before = area.zone(2, 2, 2);
        double cb = capacitor.getHeatCapacity();
        double cg = capacity(before);
        double tg = before.mixture().temperature();
        h.succeedWhen(() -> {
            double lost = cb * (1500 - capacitor.getTemperature());
            double gained = cg * (area.zone(2, 2, 2).mixture().temperature() - tg);
            h.assertTrue(gained <= lost, "the room has gained " + gained + " J, and the heater lost " + lost);
            h.assertTrue(gained > 0.5 * lost, "the room has gained " + gained + " J of the " + lost + " lost");
            h.assertTrue(
                    gained > 0.5 * cb * (1500 - tg), "the room has gained " + gained + " J, " + gained / cg + " K");
            area.clear();
        });
    }
}
