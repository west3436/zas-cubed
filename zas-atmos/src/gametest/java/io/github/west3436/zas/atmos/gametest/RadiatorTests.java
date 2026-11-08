package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.radiator.HeatExchangerBlock;
import io.github.west3436.zas.atmos.radiator.HeatExchangerTint;
import io.github.west3436.zas.atmos.radiator.RadiatorAdaptorBlock;
import io.github.west3436.zas.atmos.radiator.RadiatorMath;
import io.github.west3436.zas.atmos.radiator.RadiatorPipeBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.environment.Ambients;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Radiator pipe, the radiator adaptor and the heat exchanger (CONT-143 to CONT-148, HEAT-020 to
 * HEAT-022). Each test builds in its own {@link Area}, or a {@link Site} in the End for the vacuum,
 * and drives device steps and atmos ticks itself, within one game tick, in a batch of its own. The
 * tests of heat in a room turn the framework's heat off while they run, so a room's walls don't
 * conduct and the room's energy changes only by what the radiators send.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadiatorTests {
    private static final String BATCH = "zas_atmos_radiators";
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceKey<Level> VACUUM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));

    /** Rounds after which a segment just placed acts: its cell watch's wait, and one to spare. */
    private static final int SETTLE = CellWatch.SETTLE_STEPS + 2;

    private RadiatorTests() {}

    private static BlockState radiator() {
        return AtmosBlocks.RADIATOR_PIPE.get().defaultBlockState();
    }

    private static BlockState adaptor(Direction radiatorEnd) {
        return AtmosBlocks.RADIATOR_ADAPTOR
                .get()
                .defaultBlockState()
                .setValue(RadiatorAdaptorBlock.FACING, radiatorEnd);
    }

    private static BlockState exchanger(Direction box) {
        return AtmosBlocks.HEAT_EXCHANGER.get().defaultBlockState().setValue(HeatExchangerBlock.FACING, box);
    }

    private static BlockState pipe() {
        return AtmosBlocks.PIPE.get().defaultBlockState();
    }

    private static BlockState cap(Direction facing) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing);
    }

    /** Radiator pipe from x0 to x1 at (y, z). */
    private static void run(Area a, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) {
            a.place(x, y, z, radiator());
        }
    }

    /** Whether the pipe or radiator pipe at a position shows a connection on a face. */
    private static boolean joins(Area a, int x, int y, int z, Direction face) {
        return a.get(x, y, z).getValue(PipeBlock.CONNECTIONS.get(face));
    }

    private static PipeNetwork network(Area a, int x, int y, int z) {
        return a.<PipeNodeEntity>entity(x, y, z).network();
    }

    /** The network of whatever node is at a position, or null if it isn't one. */
    private static PipeNetwork networkOf(Area a, int x, int y, int z) {
        BlockEntity be = a.level.getBlockEntity(a.abs(x, y, z));
        return be instanceof PipeNodeEntity node ? node.network() : null;
    }

    private static void steps(Area a, int n) {
        for (int i = 0; i < n; i++) {
            a.step();
        }
    }

    /** A zone's thermal energy, C·T, in J. */
    private static double energy(ZoneInfo zone) {
        GasMixture m = zone.mixture();
        return GasProperties.heatCapacity(m.moles()) * m.temperature();
    }

    private static void near(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }

    // ---- CONT-143 to CONT-147: what joins what ------------------------------------------------

    /**
     * CONT-143: radiator pipe beside a pipe, a vent's pipe face, a heat exchanger's pipe face, a gas
     * handler and more radiator pipe joins only the radiator pipe, 70 L a segment, and passes no gas
     * to the handler.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_143_radiator_pipe_joins_only_radiator_pipe(GameTestHelper h) {
        Area a = new Area(h, 3870);
        a.clear(0, 0, 8, 6);
        run(a, 2, 4, 2, 2);
        a.place(5, 2, 2, pipe());
        a.place(3, 3, 2, AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.UP));
        a.place(3, 1, 2, TestGasHandler.block().defaultBlockState());
        a.place(2, 2, 3, exchanger(Direction.SOUTH)); // its pipe face is north, on the radiator pipe

        h.assertTrue(joins(a, 2, 2, 2, Direction.EAST) && joins(a, 3, 2, 2, Direction.WEST), "radiator to radiator");
        h.assertTrue(joins(a, 3, 2, 2, Direction.EAST) && joins(a, 4, 2, 2, Direction.WEST), "radiator to radiator");
        h.assertFalse(joins(a, 4, 2, 2, Direction.EAST), "radiator pipe joined a pipe");
        h.assertFalse(joins(a, 5, 2, 2, Direction.WEST), "a pipe joined radiator pipe");
        h.assertFalse(joins(a, 3, 2, 2, Direction.UP), "radiator pipe joined a vent");
        h.assertFalse(joins(a, 3, 2, 2, Direction.DOWN), "radiator pipe joined a gas handler");
        h.assertFalse(joins(a, 2, 2, 2, Direction.SOUTH), "radiator pipe joined a heat exchanger");

        PipeNetwork net = network(a, 3, 2, 2);
        a.close(net.volume(), 3 * PipeVolumes.PIPE, "three segments' volume");
        h.assertTrue(network(a, 2, 2, 2) == net && network(a, 4, 2, 2) == net, "the run isn't one network");
        for (int[] p : new int[][] {{5, 2, 2}, {3, 3, 2}, {2, 2, 3}}) {
            h.assertTrue(networkOf(a, p[0], p[1], p[2]) != net, "joined the run: " + a.get(p[0], p[1], p[2]));
        }

        net.gas().add(new GasParcel(Map.of(O2, 10.0), T));
        steps(a, 3);
        TestGasHandler.Entity handler = a.entity(3, 1, 2);
        h.assertValueEqual(handler.fills + handler.drains, 0, "transfers with the gas handler");
        a.close(handler.tank.totalMoles(), 0, "gas in the handler");
        a.close(network(a, 3, 2, 2).gas().moles(O2), 10.0, "O2 in the run");
        h.succeed();
    }

    /** CONT-144: a lone radiator pipe and a dead-end run in a room keep their gas: their ends are closed. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_144_radiator_pipe_ends_keep_their_gas(GameTestHelper h) {
        Area a = new Area(h, 3871);
        a.box(0, 0, 8, 4);
        a.place(2, 2, 2, radiator());
        run(a, 4, 6, 2, 2);
        a.fill(4, 3, 3, Map.of(N2, 1000.0));
        a.tick();
        PipeNetwork lone = network(a, 2, 2, 2);
        PipeNetwork run = network(a, 4, 2, 2);
        h.assertTrue(lone != run, "the lone segment joined the run");
        lone.gas().add(new GasParcel(Map.of(O2, 5.0), T));
        run.gas().add(new GasParcel(Map.of(O2, 10.0), T));
        double roomO2 = a.zone(4, 3, 3).mixture().moles(O2);

        steps(a, SETTLE + 3);

        a.close(network(a, 2, 2, 2).gas().moles(O2), 5.0, "O2 in the lone segment");
        a.close(network(a, 4, 2, 2).gas().moles(O2), 10.0, "O2 in the dead-end run");
        a.close(a.zone(4, 3, 3).mixture().moles(O2), roomO2, "O2 in the room");
        h.succeed();
    }

    /**
     * CONT-145: a capped pipe, an adaptor and radiator pipe in line share one network, with the
     * adaptor's 70 L. Turned round, as a wrench turns it, the adaptor faces its radiator end to the
     * pipe and its pipe end to the radiator pipe, and joins neither: three networks, sharing the gas
     * by volume.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_145_an_adaptor_joins_pipe_to_radiator_pipe(GameTestHelper h) {
        Area a = new Area(h, 3872);
        a.clear(0, 0, 8, 4);
        a.place(1, 2, 2, cap(Direction.EAST));
        a.place(2, 2, 2, pipe());
        a.place(3, 2, 2, adaptor(Direction.EAST));
        run(a, 4, 5, 2, 2);

        h.assertTrue(joins(a, 2, 2, 2, Direction.EAST), "the pipe didn't join the adaptor's pipe end");
        h.assertTrue(joins(a, 4, 2, 2, Direction.WEST), "radiator pipe didn't join the adaptor's radiator end");
        PipeNetwork net = network(a, 3, 2, 2);
        for (int x = 1; x <= 5; x++) {
            h.assertTrue(network(a, x, 2, 2) == net, "not in the one network: x = " + x);
        }
        a.close(net.volume(), 5 * PipeVolumes.PIPE, "a cap, a pipe, the adaptor and two segments");
        net.gas().add(new GasParcel(Map.of(O2, 5.0), T));

        a.level.setBlock(a.abs(3, 2, 2), adaptor(Direction.WEST), Block.UPDATE_ALL);
        a.<PipeNodeEntity>entity(3, 2, 2).reconnect();

        h.assertFalse(joins(a, 2, 2, 2, Direction.EAST), "the pipe joined the adaptor's radiator end");
        h.assertFalse(joins(a, 4, 2, 2, Direction.WEST), "radiator pipe joined the adaptor's pipe end");
        PipeNetwork pipes = network(a, 2, 2, 2);
        PipeNetwork alone = network(a, 3, 2, 2);
        PipeNetwork radiators = network(a, 4, 2, 2);
        h.assertTrue(pipes != alone && alone != radiators && pipes != radiators, "turning it didn't part them");
        a.close(pipes.volume(), 2 * PipeVolumes.PIPE, "the cap and pipe");
        a.close(alone.volume(), PipeVolumes.PIPE, "the adaptor");
        a.close(radiators.volume(), 2 * PipeVolumes.PIPE, "the radiator pipe");
        a.close(pipes.gas().moles(O2), 2.0, "the pipes' share");
        a.close(alone.gas().moles(O2), 1.0, "the adaptor's share");
        a.close(radiators.gas().moles(O2), 2.0, "the radiator pipe's share");
        h.succeed();
    }

    /**
     * CONT-146: a network of an adaptor and radiator pipe raised just above 100 atm over its room
     * loses one of them, and the room gets everything, as pipe does (CONT-108).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_146_radiator_pipe_bursts_above_100_atm(GameTestHelper h) {
        Area a = new Area(h, 3873);
        a.box(0, 0, 8, 4);
        a.place(2, 2, 2, adaptor(Direction.EAST));
        run(a, 3, 5, 2, 2);
        a.fill(4, 3, 3, Map.of(N2, 1000.0));
        a.tick();
        a.tick();
        PipeNetwork net = network(a, 2, 2, 2);
        a.close(net.volume(), 4 * PipeVolumes.PIPE, "the adaptor and three segments");
        double room = Atmospheres.at(a.level, a.abs(4, 2, 2)).mixture().pressure();
        PipeBurstTests.fillTo(net, room + PipeBurst.THRESHOLD_KPA + 1);
        double all = net.gas().moles(CO2);
        double roomCo2 = a.zone(4, 3, 3).mixture().moles(CO2);

        a.step();
        a.tick();

        int left = 0;
        for (int x = 2; x <= 5; x++) {
            if (a.level.getBlockEntity(a.abs(x, 2, 2)) instanceof PipeNodeEntity) {
                left++;
            }
        }
        h.assertValueEqual(left, 3, "segments left after the burst");
        a.close(a.zone(4, 3, 3).mixture().moles(CO2), roomCo2 + all, "CO2 in the room after the burst");
        h.succeed();
    }

    /**
     * CONT-147: a pipe joins a heat exchanger only on the face opposite its box, adding 200 L, and so
     * in each of its six facings.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_147_a_heat_exchanger_takes_pipe_opposite_its_box(GameTestHelper h) {
        Area a = new Area(h, 3874);
        a.clear(0, 0, 32, 6);
        a.place(1, 2, 2, cap(Direction.EAST));
        a.place(2, 2, 2, pipe());
        a.place(3, 2, 2, exchanger(Direction.EAST));
        a.place(4, 2, 2, pipe()); // on its box
        a.place(3, 3, 2, pipe()); // on its side
        a.place(3, 2, 3, pipe()); // and another

        h.assertTrue(joins(a, 2, 2, 2, Direction.EAST), "the pipe opposite the box didn't join");
        h.assertFalse(joins(a, 4, 2, 2, Direction.WEST), "a pipe on the box joined");
        h.assertFalse(joins(a, 3, 3, 2, Direction.DOWN), "a pipe on its side joined");
        h.assertFalse(joins(a, 3, 2, 3, Direction.NORTH), "a pipe on its side joined");
        PipeNetwork net = network(a, 3, 2, 2);
        a.close(net.volume(), 2 * PipeVolumes.PIPE + PipeVolumes.PORT, "a cap, a pipe and the exchanger's 200 L");
        h.assertTrue(network(a, 2, 2, 2) == net, "the pipe isn't in the exchanger's network");
        for (int[] p : new int[][] {{4, 2, 2}, {3, 3, 2}, {3, 2, 3}}) {
            h.assertTrue(network(a, p[0], p[1], p[2]) != net, "joined: " + p[0] + ", " + p[1] + ", " + p[2]);
        }

        int x = 8;
        for (Direction d : Direction.values()) {
            BlockPos at = new BlockPos(x, 3, 3);
            BlockPos back = at.relative(d.getOpposite());
            BlockPos front = at.relative(d);
            a.place(x, 3, 3, exchanger(d));
            a.place(back.getX(), back.getY(), back.getZ(), pipe());
            a.place(front.getX(), front.getY(), front.getZ(), pipe());
            h.assertTrue(
                    joins(a, back.getX(), back.getY(), back.getZ(), d), d + ": the pipe opposite the box didn't join");
            h.assertFalse(
                    joins(a, front.getX(), front.getY(), front.getZ(), d.getOpposite()),
                    d + ": a pipe on the box joined");
            a.close(network(a, x, 3, 3).volume(), PipeVolumes.PIPE + PipeVolumes.PORT, d + ": the network's volume");
            x += 4;
        }
        h.succeed();
    }

    // ---- HEAT-020: radiator pipe and the gas around it ---------------------------------------

    /**
     * HEAT-020: a run of five segments in a sealed room, with the framework's heat off, moves 20 W/K a
     * segment in series with its cell's share of the room, solved for the network's new temperature,
     * each atmos tick. The room gains exactly the heat the network loses, or loses what it gains,
     * and neither passes the other's temperature.
     */
    private static void inASealedRoom(GameTestHelper h, int index, double networkTemperature) {
        Area a = new Area(h, index);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            a.box(0, 0, 8, 4);
            run(a, 2, 6, 2, 2);
            a.fill(4, 3, 3, Map.of(N2, 1000.0));
            a.tick();
            network(a, 2, 2, 2).gas().add(new GasParcel(Map.of(N2, 20.0), networkTemperature));
            ZoneId room = a.zone(4, 3, 3).id();
            for (int x = 2; x <= 6; x++) {
                h.assertTrue(
                        Atmospheres.at(a.level, a.abs(x, 2, 2)).zone().equals(Optional.of(room)),
                        "segment " + x + " isn't in the room");
            }
            steps(a, SETTLE);
            double conductance = AtmosServerConfig.get(AtmosServerConfig.RADIATOR_CONDUCTANCE);
            double total = energy(a.zone(4, 3, 3)) + network(a, 2, 2, 2).gas().thermalEnergy();
            boolean hot = networkTemperature > a.zone(4, 3, 3).mixture().temperature();
            for (int i = 0; i < 6; i++) {
                ZoneInfo zone = a.zone(4, 3, 3);
                GasStore gas = network(a, 2, 2, 2).gas();
                double c = gas.heatCapacity();
                double t = gas.temperature();
                double tz = zone.mixture().temperature();
                double cell = RadiatorMath.cellCapacity(
                        GasProperties.heatCapacity(zone.mixture().moles()), zone.volume());
                double coupling = 5 * RadiatorMath.convection(conductance, AtmosTicks.SECONDS, cell);
                double expected = RadiatorMath.settle(c, t, coupling, coupling * tz);
                double before = energy(zone);

                a.step();

                double now = network(a, 2, 2, 2).gas().temperature();
                near(h, now, expected, 1e-9 * expected, "round " + i + ": the network's temperature");
                double lost = c * (t - now);
                h.assertTrue(hot ? lost > 0 : lost < 0, "round " + i + ": no heat moved: " + lost + " J");
                near(
                        h,
                        energy(a.zone(4, 3, 3)) - before,
                        lost,
                        1e-6 * Math.abs(lost),
                        "round " + i + ": the room's gain");
                double roomNow = a.zone(4, 3, 3).mixture().temperature();
                h.assertTrue(hot ? roomNow < now : roomNow > now, "round " + i + ": the room passed the network");
            }
            double after = energy(a.zone(4, 3, 3)) + network(a, 2, 2, 2).gas().thermalEnergy();
            near(h, after, total, 1e-9 * total, "the room's and the network's thermal energy together");
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
        h.succeed();
    }

    /** HEAT-020: a hot run in a sealed room warms it by the heat its network loses. */
    @GameTest(template = "empty", batch = BATCH)
    public static void heat_020_a_hot_run_warms_a_sealed_room_by_what_it_loses(GameTestHelper h) {
        inASealedRoom(h, 3875, 600);
    }

    /** HEAT-020: a cold run in a sealed room cools it by the heat its network gains, drawn from the room. */
    @GameTest(template = "empty", batch = BATCH)
    public static void heat_020_a_cold_run_cools_a_sealed_room_by_what_it_gains(GameTestHelper h) {
        inASealedRoom(h, 3876, 200);
    }

    /**
     * HEAT-020: a hot run in the open air cools to the ambient's temperature without passing it, and
     * the open air is unchanged: no zone forms, and its temperature stays the ambient's (HEAT-010).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void heat_020_a_run_in_the_open_air_cools_to_the_ambient(GameTestHelper h) {
        Area a = new Area(h, 3877);
        a.clear(0, 0, 8, 4);
        run(a, 2, 4, 2, 2);
        network(a, 2, 2, 2).gas().add(new GasParcel(Map.of(N2, 10.0), 600));
        AtmosphereSample before = Atmospheres.at(a.level, a.abs(3, 2, 2));
        h.assertValueEqual(before.state(), AtmosphereState.AMBIENT, "the run's cell");
        double ambient = before.mixture().temperature();

        steps(a, SETTLE);
        double t = network(a, 2, 2, 2).gas().temperature();
        h.assertTrue(t < 600, "the run didn't start cooling");
        for (int i = 0; i < 40; i++) {
            a.step();
            double next = network(a, 2, 2, 2).gas().temperature();
            h.assertTrue(next <= t, "round " + i + ": the run warmed, " + t + " K to " + next + " K");
            h.assertTrue(next >= ambient - 1e-9, "round " + i + ": the run passed the ambient: " + next + " K");
            t = next;
        }
        h.assertTrue(t - ambient < 0.5, "after 45 s the run is still " + (t - ambient) + " K above the ambient");
        a.close(network(a, 2, 2, 2).gas().moles(N2), 10.0, "N2 in the run");
        for (int x = 2; x <= 4; x++) {
            AtmosphereSample after = Atmospheres.at(a.level, a.abs(x, 2, 2));
            h.assertValueEqual(after.state(), AtmosphereState.AMBIENT, "segment " + x + "'s cell");
            h.assertTrue(after.zone().isEmpty(), "a zone formed in the open air");
            a.close(after.mixture().temperature(), ambient, "the open air's temperature");
        }
        h.succeed();
    }

    /**
     * HEAT-020: a hot run in a room thinner than the near-vacuum density (HEAT-008, API-064) moves no
     * heat: the run, the room's temperature and its gas are unchanged.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void heat_020_a_run_in_a_near_vacuum_room_changes_nothing(GameTestHelper h) {
        Area a = new Area(h, 3878);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            a.box(0, 0, 8, 4);
            run(a, 2, 4, 2, 2);
            a.fill(4, 3, 3, Map.of(N2, 1.0));
            a.settle(GasMutations.remove(
                    a.level, a.point(4, 3, 3), a.zone(4, 3, 3).mixture().moles(), Area.SOURCE));
            double line = Atmospheres.nearVacuumDensity();
            a.fill(4, 3, 3, Map.of(N2, 0.5 * line * a.zone(4, 3, 3).volume()));
            ZoneInfo room = a.zone(4, 3, 3);
            h.assertTrue(room.mixture().totalMoles() > 0, "the room is empty");
            h.assertTrue(room.mixture().totalMoles() < line * room.volume(), "the room isn't thinner than the line");
            network(a, 2, 2, 2).gas().add(new GasParcel(Map.of(N2, 10.0), 600));

            steps(a, SETTLE + 5);

            a.close(network(a, 2, 2, 2).gas().temperature(), 600, "the run's temperature");
            ZoneInfo after = a.zone(4, 3, 3);
            a.close(after.mixture().temperature(), room.mixture().temperature(), "the room's temperature");
            a.close(after.mixture().totalMoles(), room.mixture().totalMoles(), "the room's gas");
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
        h.succeed();
    }

    // ---- HEAT-021: radiator pipe in a vacuum ---------------------------------------------------

    /**
     * HEAT-021: a hot run of three segments in the open air of the test datapack's 2.7 K vacuum
     * radiates εσA(T⁴ − T_bg⁴) a segment, with ε 0.9 and A 1 m²: each atmos tick the network's
     * temperature is the implicit step's, within 1e-9, and its loss within 2 % of the law, the
     * difference being the step's. It stays above the background, and the vacuum is unchanged.
     */
    @GameTest(template = "empty", batch = "zas_atmos_radiator_vacuum")
    public static void heat_021_a_hot_run_in_a_vacuum_radiates_at_the_law(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(VACUUM);
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Site s = new Site(h, end, 5470);
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            s.clear();
            RadiatorPipeBlockEntity[] run = new RadiatorPipeBlockEntity[3];
            for (int i = 0; i < run.length; i++) {
                BlockPos pos = s.abs(2 + i, 2, 2);
                end.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(radiator(), end, pos));
                run[i] = s.entity(2 + i, 2, 2, RadiatorPipeBlockEntity.class);
            }
            h.assertTrue(run[0].network() == run[2].network(), "the run isn't one network");
            run[0].network().gas().add(new GasParcel(Map.of(N2, 200.0), 600));
            AtmosphereSample cell = Atmospheres.at(end, s.abs(3, 2, 2));
            h.assertValueEqual(cell.state(), AtmosphereState.AMBIENT, "the run's cell");
            h.assertTrue(cell.mixture().totalMoles() == 0, "the vacuum has gas");
            double background = cell.mixture().temperature();

            s.cycles(SETTLE, run);
            double emissivity = AtmosServerConfig.get(AtmosServerConfig.RADIATOR_EMISSIVITY);
            for (int i = 0; i < 10; i++) {
                GasStore gas = run[0].network().gas();
                double c = gas.heatCapacity();
                double t = gas.temperature();
                double coupling = run.length * RadiatorMath.radiation(emissivity, AtmosTicks.SECONDS, t, background);
                double expected = RadiatorMath.settle(c, t, coupling, coupling * background);

                s.cycle(run);

                double now = run[0].network().gas().temperature();
                s.close(now, expected, 1e-9 * t, "round " + i + ": the network's temperature");
                double watts = c * (t - now) / AtmosTicks.SECONDS;
                double law = run.length
                        * emissivity
                        * RadiatorMath.STEFAN_BOLTZMANN
                        * RadiatorMath.AREA
                        * (Math.pow(t, 4) - Math.pow(background, 4));
                s.close(watts, law, 0.02 * law, "round " + i + ": three segments' εσA(T⁴ − T_bg⁴), in W");
                h.assertTrue(now > background, "round " + i + ": the run reached the background");
            }
            AtmosphereSample after = Atmospheres.at(end, s.abs(3, 2, 2));
            h.assertValueEqual(after.state(), AtmosphereState.AMBIENT, "the run's cell");
            h.assertTrue(after.zone().isEmpty() && after.mixture().totalMoles() == 0, "the vacuum changed");
        } finally {
            s.clear();
            Ambients.overrideForTest(Level.END, null);
        }
        h.succeed();
    }

    // ---- HEAT-022 and CONT-148: heat exchanger pairs --------------------------------------------

    /**
     * HEAT-022: a pair joins a hot network, a cap and an exchanger, to a cold one, an exchanger alone.
     * One atmos tick brings the two exchangers' shares by volume to one temperature: the hot network
     * loses ΔT × C₁C₂ / (C₁ + C₂) and the cold one gains it. A lone exchanger, and two side by side
     * that don't face each other, move nothing. CONT-148: each exchanger's band is its network's
     * temperature's, and one with no gas shows copper.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void heat_022_a_pair_shares_one_temperature(GameTestHelper h) {
        Area a = new Area(h, 3879);
        a.clear(0, 0, 16, 4);
        a.place(2, 2, 2, cap(Direction.EAST));
        a.place(3, 2, 2, exchanger(Direction.EAST));
        a.place(4, 2, 2, exchanger(Direction.WEST));
        a.place(7, 2, 2, exchanger(Direction.EAST)); // alone
        a.place(10, 2, 2, exchanger(Direction.EAST)); // side by side, both facing east
        a.place(11, 2, 2, exchanger(Direction.EAST));
        a.place(14, 2, 2, exchanger(Direction.EAST)); // empty

        h.assertTrue(HeatExchangerBlock.paired(a.get(3, 2, 2), a.get(4, 2, 2)), "the pair isn't paired");
        h.assertFalse(HeatExchangerBlock.paired(a.get(10, 2, 2), a.get(11, 2, 2)), "two facing east are paired");
        PipeNetwork hot = network(a, 3, 2, 2);
        PipeNetwork cold = network(a, 4, 2, 2);
        h.assertTrue(hot != cold, "the pair shares a network");
        h.assertTrue(network(a, 10, 2, 2) != network(a, 11, 2, 2), "the two side by side share a network");
        a.close(hot.volume(), PipeVolumes.PIPE + PipeVolumes.PORT, "the hot network: a cap and an exchanger");
        a.close(cold.volume(), PipeVolumes.PORT, "the cold network: an exchanger");
        hot.gas().add(new GasParcel(Map.of(N2, 20.0), 600));
        cold.gas().add(new GasParcel(Map.of(N2, 10.0), 250));
        network(a, 7, 2, 2).gas().add(new GasParcel(Map.of(N2, 10.0), 600));
        network(a, 10, 2, 2).gas().add(new GasParcel(Map.of(N2, 10.0), 600));
        network(a, 11, 2, 2).gas().add(new GasParcel(Map.of(N2, 10.0), 250));

        double ch = hot.gas().heatCapacity();
        double cc = cold.gas().heatCapacity();
        double th = hot.gas().temperature();
        double tc = cold.gas().temperature();
        double sh = ch * PipeVolumes.PORT / hot.volume();
        double sc = cc * PipeVolumes.PORT / cold.volume();
        double q = (th - tc) * sh * sc / (sh + sc);

        a.step();

        double thNow = network(a, 3, 2, 2).gas().temperature();
        double tcNow = network(a, 4, 2, 2).gas().temperature();
        near(h, thNow, th - q / ch, 1e-9 * th, "the hot network's temperature");
        near(h, tcNow, tc + q / cc, 1e-9 * th, "the cold network's temperature");
        near(h, ch * thNow + cc * tcNow, ch * th + cc * tc, 1e-9 * ch * th, "the pair's thermal energy");
        h.assertTrue(q > 0, "no heat moved");
        a.close(network(a, 3, 2, 2).gas().moles(N2), 20.0, "N2 in the hot network");
        a.close(network(a, 4, 2, 2).gas().moles(N2), 10.0, "N2 in the cold network");
        a.close(network(a, 7, 2, 2).gas().temperature(), 600, "a lone exchanger's network");
        a.close(network(a, 10, 2, 2).gas().temperature(), 600, "the west of two not facing");
        a.close(network(a, 11, 2, 2).gas().temperature(), 250, "the east of two not facing");

        steps(a, 4); // so each half has shown its network after the pair settled
        for (int x : new int[] {3, 4, 7, 10, 11}) {
            double t = network(a, x, 2, 2).gas().temperature();
            h.assertValueEqual(
                    a.get(x, 2, 2).getValue(HeatExchangerBlock.HEAT),
                    HeatExchangerTint.band(t),
                    "x = " + x + ": the band at " + t + " K");
        }
        h.assertValueEqual(
                a.get(14, 2, 2).getValue(HeatExchangerBlock.HEAT), HeatExchangerTint.COPPER, "an empty exchanger");
        h.succeed();
    }
}
