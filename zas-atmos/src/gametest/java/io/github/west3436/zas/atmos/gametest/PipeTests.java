package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.particle.GasJet;
import io.github.west3436.zas.atmos.particle.GasJetKind;
import io.github.west3436.zas.atmos.particle.GasJets;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpBlockEntity;
import io.github.west3436.zas.atmos.pump.VolumePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.valve.ValveBlock;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.gametest.TestPlayers;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pipe networks in the world (CONT-001, CONT-003, CONT-004, CONT-009, CONT-114). Each test builds in
 * its own {@link Area} and drives device steps and atmos ticks itself, within one game tick. They
 * run in a batch of their own, so stepping every device of the level never touches the real-time
 * {@link DeviceTests}.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeTests {
    static final String BATCH = "zas_atmos_pipes";

    private PipeTests() {}

    static void place(Area a, int x, int y, int z, BlockState state) {
        a.place(x, y, z, state);
    }

    static void pipe(Area a, int x, int y, int z) {
        place(a, x, y, z, AtmosBlocks.PIPE.get().defaultBlockState());
    }

    static void cap(Area a, int x, int y, int z, Direction facing) {
        place(a, x, y, z, AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing));
    }

    static PipeNetwork network(Area a, int x, int y, int z) {
        return a.<PipeNodeEntity>entity(x, y, z).network();
    }

    /** CONT-001: connected pipes, a branch and a cap that faces them form one network with one mixture. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_001_connected_pipes_share_one_mixture(GameTestHelper h) {
        Area a = new Area(h, 0);
        a.clear(0, 0, 8, 4);
        for (int x = 1; x <= 5; x++) {
            pipe(a, x, 1, 1);
        }
        pipe(a, 3, 2, 1);
        pipe(a, 3, 3, 1);
        cap(a, 6, 1, 1, Direction.WEST);
        cap(a, 2, 1, 2, Direction.SOUTH); // faces away from the pipe beside it

        BlockState junction = a.get(3, 1, 1);
        h.assertTrue(junction.getValue(PipeBlock.CONNECTIONS.get(Direction.EAST)), "junction east");
        h.assertTrue(junction.getValue(PipeBlock.CONNECTIONS.get(Direction.WEST)), "junction west");
        h.assertTrue(junction.getValue(PipeBlock.CONNECTIONS.get(Direction.UP)), "junction up");
        h.assertFalse(junction.getValue(PipeBlock.CONNECTIONS.get(Direction.NORTH)), "junction north");
        h.assertTrue(a.get(5, 1, 1).getValue(PipeBlock.CONNECTIONS.get(Direction.EAST)), "pipe to the cap");
        h.assertFalse(a.get(2, 1, 1).getValue(PipeBlock.CONNECTIONS.get(Direction.SOUTH)), "pipe to a cap facing away");

        PipeNetwork net = network(a, 1, 1, 1);
        a.close(net.volume(), 8 * PipeVolumes.PIPE, "network volume: 7 pipes and a cap");
        for (int[] p : new int[][] {{1, 1, 1}, {3, 1, 1}, {5, 1, 1}, {3, 3, 1}, {6, 1, 1}}) {
            h.assertTrue(network(a, p[0], p[1], p[2]) == net, "not in the one network: " + p[0] + "," + p[1]);
        }
        h.assertTrue(network(a, 2, 1, 2) != net, "the cap facing away joined the network");
        h.assertTrue(DeviceTicker.isScheduled(a.entity(1, 1, 1)), "pipes step once per atmos tick (CONT-006)");

        net.gas().add(new GasParcel(Map.of(O2, 8.0), T));
        for (int[] p : new int[][] {{1, 1, 1}, {3, 3, 1}, {6, 1, 1}}) {
            a.close(a.<PipeNodeEntity>entity(p[0], p[1], p[2]).share().moles(O2), 1.0, "each segment's share");
        }
        h.succeed();
    }

    /**
     * CONT-003 and CONT-009: breaking a pipe in a filled run releases its share into its cell and
     * leaves each part its share by volume, with no gas lost.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_003_breaking_a_pipe_conserves_gas(GameTestHelper h) {
        Area a = new Area(h, 1);
        a.box(0, 0, 8, 4);
        a.fill(4, 2, 2, Map.of(N2, 100.0));
        for (int x = 1; x <= 7; x++) {
            pipe(a, x, 1, 2);
        }
        a.tick();
        network(a, 1, 1, 2).gas().add(new GasParcel(Map.of(O2, 7.0), T));
        // The room's zone formed holding its ambient air, O2 included.
        double zoneO2 = a.zone(4, 2, 2).mixture().moles(O2);

        a.set(4, 1, 2, AIR);
        a.tick();

        ZoneInfo zone = a.zone(4, 2, 2);
        a.close(zone.mixture().moles(O2) - zoneO2, 1.0, "the broken segment's share released into its cell (CONT-009)");
        PipeNetwork left = network(a, 1, 1, 2);
        PipeNetwork right = network(a, 7, 1, 2);
        h.assertTrue(left != right, "the run didn't split");
        a.close(left.volume(), 3 * PipeVolumes.PIPE, "left volume");
        a.close(left.gas().moles(O2), 3.0, "left share by volume");
        a.close(right.gas().moles(O2), 3.0, "right share by volume");
        a.close(left.gas().moles(O2) + right.gas().moles(O2) + zone.mixture().moles(O2), 7.0 + zoneO2, "total O2");
        h.succeed();
    }

    /** CONT-004: an open end exchanges gas with its room as a one-connection edge does. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_004_open_end_exchanges_at_the_one_connection_ratio(GameTestHelper h) {
        Area a = new Area(h, 2);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 1000.0));
        pipe(a, 2, 1, 2);
        a.tick();
        PipeBlockEntity pipe = a.entity(2, 1, 2);
        h.assertTrue(pipe.isOpen(), "a lone pipe isn't open");
        // A newly loaded open end waits, and moves nothing meanwhile.
        double roomN2 = a.zone(2, 2, 2).mixture().moles(N2);
        for (int i = 0; i < PipeBlockEntity.SETTLE_STEPS; i++) {
            a.step();
        }
        a.close(a.zone(2, 2, 2).mixture().moles(N2), roomN2, "N2 in the room while the end settles");
        GasStore gas = pipe.network().gas();
        gas.add(new GasParcel(Map.of(O2, 10.0), T));
        ZoneInfo before = a.zone(2, 2, 2);
        PipeBlockEntity.Flow flow = PipeBlockEntity.openEndFlow(
                gas.moles(), gas.volume(), before.mixture(), false, PipeBlockEntity.OPEN_END_RATIO);

        a.step();

        ZoneInfo after = a.zone(2, 2, 2);
        GasStore now = pipe.network().gas();
        h.assertTrue(flow.out().containsKey(O2) && flow.in().containsKey(N2), "expected O2 out and N2 in");
        for (var id : java.util.List.of(O2, N2)) {
            double moved = flow.out().getOrDefault(id, 0.0) - flow.in().getOrDefault(id, 0.0);
            a.close(now.moles(id), (id == O2 ? 10.0 : 0.0) - moved, id + " in the pipe");
            a.close(after.mixture().moles(id), before.mixture().moles(id) + moved, id + " in the room");
        }
        h.succeed();
    }

    /** CONT-004: caps close a pipe's ends, so a capped pipe exchanges nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_004_capped_pipe_does_not_leak(GameTestHelper h) {
        Area a = new Area(h, 3);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 1000.0));
        cap(a, 1, 1, 2, Direction.EAST);
        pipe(a, 2, 1, 2);
        cap(a, 3, 1, 2, Direction.WEST);
        a.tick();
        PipeBlockEntity pipe = a.entity(2, 1, 2);
        h.assertFalse(pipe.isOpen(), "a capped pipe is open");
        pipe.network().gas().add(new GasParcel(Map.of(O2, 10.0), T));
        double roomO2 = a.zone(2, 2, 2).mixture().moles(O2);
        a.step();
        a.step();
        a.close(pipe.network().gas().moles(O2), 10.0, "O2 in the capped pipe");
        a.close(a.zone(2, 2, 2).mixture().moles(O2), roomO2, "O2 in the room");
        h.succeed();
    }

    /**
     * CONT-004: a pipe has a mouth, in its shape as in its model, on each face it leaks from. A lone pipe
     * has six. One that joins a pipe, a cap, a valve or a vent's back has one, on the far face, and
     * joining it to something else takes that away. A straight run and a bend have none.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_004_a_pipe_has_a_mouth_on_each_face_it_leaks_from(GameTestHelper h) {
        Area a = new Area(h, 2610);
        a.clear(-1, -1, 7, 9);
        pipe(a, 0, 2, 0);
        mouths(a, 0, 2, 0, Direction.values());
        pipe(a, 1, 2, 0);
        mouths(a, 0, 2, 0, Direction.WEST);
        mouths(a, 1, 2, 0, Direction.EAST);
        pipe(a, 2, 2, 0);
        mouths(a, 1, 2, 0);
        mouths(a, 2, 2, 0, Direction.EAST);

        cap(a, 0, 2, 2, Direction.EAST);
        pipe(a, 1, 2, 2);
        mouths(a, 1, 2, 2, Direction.EAST);
        place(
                a,
                0,
                2,
                4,
                AtmosBlocks.MANUAL_VALVE.get().defaultBlockState().setValue(ValveBlock.AXIS, Direction.Axis.X));
        pipe(a, 1, 2, 4);
        mouths(a, 1, 2, 4, Direction.EAST);
        place(a, 0, 2, 6, AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.WEST));
        pipe(a, 1, 2, 6);
        mouths(a, 1, 2, 6, Direction.EAST);

        pipe(a, 4, 2, 2);
        pipe(a, 5, 2, 2);
        pipe(a, 5, 2, 3);
        mouths(a, 5, 2, 2);
        h.succeed();
    }

    /**
     * Checks a pipe's mouths by its shape: a point half a pixel inside each face and 3.5 px off its
     * axis is in a mouth's 8 px lip, and outside the 6 px flange of an arm.
     */
    private static void mouths(Area a, int x, int y, int z, Direction... want) {
        VoxelShape shape = a.get(x, y, z).getShape(a.level, a.abs(x, y, z));
        Set<Direction> mouths = EnumSet.noneOf(Direction.class);
        mouths.addAll(List.of(want));
        for (Direction d : Direction.values()) {
            double off = 3.5 / 16;
            Vec3 point = new Vec3(0.5, 0.5, 0.5)
                    .add(Vec3.atLowerCornerOf(d.getNormal()).scale(7.5 / 16))
                    .add(
                            d.getAxis() == Direction.Axis.X ? 0 : off,
                            d.getAxis() == Direction.Axis.Y ? 0 : off,
                            d.getAxis() == Direction.Axis.Z ? 0 : off);
            boolean lip = shape.toAabbs().stream().anyMatch(box -> box.contains(point));
            a.h.assertValueEqual(
                    lip, mouths.contains(d), "the pipe at " + x + ", " + y + ", " + z + ": a mouth to the " + d);
        }
    }

    // ---- CLIENT-019 ------------------------------------------------------------------------------

    /** A sealed room with its zone formed, holding gas. */
    private static void room(Area a) {
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 1000.0));
    }

    /** An empty pipe with one connection, a cap to its west, in a {@link #room}, its end settled. */
    static PipeBlockEntity endInRoom(Area a) {
        room(a);
        cap(a, 1, 1, 2, Direction.EAST);
        pipe(a, 2, 1, 2);
        a.tick();
        PipeBlockEntity pipe = a.entity(2, 1, 2);
        settle(a);
        a.h.assertTrue(pipe.isOpen(), "the pipe isn't open");
        return pipe;
    }

    /** The atmos ticks a newly placed open end waits, moving nothing. */
    static void settle(Area a) {
        for (int i = 0; i < PipeBlockEntity.SETTLE_STEPS; i++) {
            a.step();
        }
    }

    /** What an end's next atmos tick moves, with the moles it has now. */
    static PipeBlockEntity.Flow flowNow(Area a, PipeBlockEntity pipe) {
        GasStore gas = pipe.network().gas();
        return PipeBlockEntity.openEndFlow(
                gas.moles(), gas.volume(), a.zone(2, 2, 2).mixture(), false, PipeBlockEntity.OPEN_END_RATIO);
    }

    /** Fills a pipe's network to the room's level of each gas, plus some more N2 on top. */
    private static void fillToRoom(Area a, PipeBlockEntity pipe, double overRoom) {
        GasStore gas = pipe.network().gas();
        ZoneInfo room = a.zone(2, 2, 2);
        Map<ResourceLocation, Double> moles = new HashMap<>();
        room.mixture()
                .moles()
                .forEach((id, n) ->
                        moles.put(id, n * gas.volume() / room.mixture().volume()));
        moles.merge(N2, overRoom, Double::sum);
        gas.add(new GasParcel(moles, T));
    }

    private static void close(Area a, double got, double want, String what) {
        a.h.assertTrue(Math.abs(got - want) <= Math.abs(want) * 1e-6, what + ": " + got + ", not " + want);
    }

    /**
     * An open end venting into a room sends one jet per atmos tick, from its mouth on the free side,
     * looking away from its connection, with the net moles per second. A settling end sends nothing,
     * however full, because it moves nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_an_open_end_venting_sends_a_jet_out_of_its_free_side(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 867);
        room(a);
        cap(a, 1, 1, 2, Direction.EAST);
        pipe(a, 2, 1, 2);
        a.tick();
        PipeBlockEntity pipe = a.entity(2, 1, 2);
        Vec3 near = a.point(2, 1, 2);
        pipe.network().gas().add(new GasParcel(Map.of(N2, 300.0), T));
        JetLog.take(near);
        settle(a);
        h.assertTrue(JetLog.take(near).isEmpty(), "a settling end sent a jet");

        PipeBlockEntity.Flow flow = flowNow(a, pipe);
        h.assertTrue(flow.net() > 0, "expected gas going out, net " + flow.net());
        a.step();

        List<GasJet> jets = JetLog.take(near);
        h.assertValueEqual(jets.size(), 1, "jets in one atmos tick");
        GasJet jet = jets.get(0);
        h.assertValueEqual(jet.kind(), GasJetKind.OPEN_END, "the jet's kind");
        h.assertTrue(jet.dx() == 1 && jet.dy() == 0 && jet.dz() == 0, "the jet doesn't look east: " + jet);
        h.assertTrue(
                new Vec3(jet.x(), jet.y(), jet.z()).distanceTo(near.add(GasJets.PIPE_MOUTH, 0, 0)) < 1e-6,
                "the jet isn't at the pipe's east mouth: " + jet);
        close(a, jet.amount(), flow.net(), "the jet's flow");
        h.succeed();
    }

    /** An empty pipe in a room of gas draws it in: the same mouth and direction, with a negative flow. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_an_open_end_taking_gas_in_sends_a_negative_flow(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 868);
        PipeBlockEntity pipe = endInRoom(a);
        Vec3 near = a.point(2, 1, 2);
        JetLog.take(near);
        PipeBlockEntity.Flow flow = flowNow(a, pipe);
        h.assertTrue(flow.net() < -0.25, "expected a real flow in, net " + flow.net());
        a.step();
        List<GasJet> jets = JetLog.take(near);
        h.assertValueEqual(jets.size(), 1, "jets in one atmos tick");
        h.assertTrue(jets.get(0).dx() == 1, "the jet doesn't look east: " + jets.get(0));
        close(a, jets.get(0).amount(), flow.net(), "the jet's flow");
        h.succeed();
    }

    /**
     * A trickle sends nothing: an end a little over its room stays quiet, and the same end further over
     * it shows, on either side of the threshold.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_trickle_sends_nothing(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 869);
        PipeBlockEntity pipe = endInRoom(a);
        Vec3 near = a.point(2, 1, 2);

        pipe.network().gas().drain();
        fillToRoom(a, pipe, 1.0);
        double trickle = flowNow(a, pipe).net();
        h.assertTrue(trickle > 0.05 && trickle < 0.25, "expected a trickle, net " + trickle);
        JetLog.take(near);
        a.step();
        h.assertTrue(JetLog.take(near).isEmpty(), "a trickle of " + trickle + " mol/s sent a jet");

        pipe.network().gas().drain();
        fillToRoom(a, pipe, 3.0);
        double flowing = flowNow(a, pipe).net();
        h.assertTrue(flowing > 0.25, "expected more than a trickle, net " + flowing);
        a.step();
        h.assertValueEqual(JetLog.take(near).size(), 1, "jets from " + flowing + " mol/s");
        h.succeed();
    }

    /** Gases that only pass each other are no jet: plenty moves each way, and the net is about nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_gases_passing_each_other_send_nothing(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 870);
        PipeBlockEntity pipe = endInRoom(a);
        Vec3 near = a.point(2, 1, 2);
        GasStore gas = pipe.network().gas();
        gas.drain();
        ZoneInfo room = a.zone(2, 2, 2);
        double sameLevel =
                room.mixture().totalMoles() * gas.volume() / room.mixture().volume();
        ResourceLocation co2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
        gas.add(new GasParcel(Map.of(co2, sameLevel), T));

        PipeBlockEntity.Flow flow = flowNow(a, pipe);
        double out =
                flow.out().values().stream().mapToDouble(Double::doubleValue).sum();
        h.assertTrue(out > 0.25, "expected a real flow out, " + out);
        h.assertTrue(Math.abs(flow.net()) < 0.01, "expected about no net flow, " + flow.net());
        JetLog.take(near);
        a.step();
        h.assertTrue(JetLog.take(near).isEmpty(), "gases passing each other sent a jet");
        h.succeed();
    }

    /** A lone pipe has no connection to look away from, so its jet has no direction: every way. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_lone_pipe_sends_a_jet_every_way(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 871);
        room(a);
        pipe(a, 2, 1, 2);
        a.tick();
        PipeBlockEntity pipe = a.entity(2, 1, 2);
        settle(a);
        Vec3 near = a.point(2, 1, 2);
        pipe.network().gas().add(new GasParcel(Map.of(N2, 100.0), T));
        JetLog.take(near);
        a.step();
        List<GasJet> jets = JetLog.take(near);
        h.assertValueEqual(jets.size(), 1, "jets in one atmos tick");
        GasJet jet = jets.get(0);
        h.assertTrue(jet.dx() == 0 && jet.dy() == 0 && jet.dz() == 0, "the jet has a direction: " + jet);
        h.assertTrue(new Vec3(jet.x(), jet.y(), jet.z()).distanceTo(near) < 1e-6, "the jet isn't at the pipe: " + jet);
        h.succeed();
    }

    /** A pipe capped at both ends isn't open, so however full it is it sends nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_capped_pipe_sends_nothing(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 872);
        room(a);
        cap(a, 1, 1, 2, Direction.EAST);
        pipe(a, 2, 1, 2);
        cap(a, 3, 1, 2, Direction.WEST);
        a.tick();
        PipeBlockEntity pipe = a.entity(2, 1, 2);
        h.assertTrue(PipeBlock.openFace(a.get(2, 1, 2)) == null, "a pipe with two connections has an open face");
        settle(a);
        pipe.network().gas().add(new GasParcel(Map.of(N2, 300.0), T));
        JetLog.take(a.point(2, 1, 2));
        a.step();
        h.assertTrue(JetLog.take(a.point(2, 1, 2)).isEmpty(), "a capped pipe sent a jet");
        h.succeed();
    }

    /**
     * A pump at (2, 2, 2) facing east in the open air, on test power and off, with one pipe on each
     * side, so each pipe is an open end.
     */
    private static <T extends PumpBlockEntity> T pumpInOpenAir(Area a, Block block) {
        a.clear(-1, 0, 6, 5);
        place(a, 2, 2, 2, block.defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        pipe(a, 1, 2, 2);
        pipe(a, 3, 2, 2);
        T pump = a.entity(2, 2, 2);
        pump.power().setTestPowered(true);
        return pump;
    }

    /** Steps until a check passes; one that never does fails the test with its own message. */
    private static void stepUntil(Area a, Runnable check) {
        for (int i = 1; ; i++) {
            a.step();
            try {
                check.run();
                return;
            } catch (GameTestAssertException e) {
                if (i == 200) {
                    throw e;
                }
            }
        }
    }

    /** Passes once both sides of a new pump have drawn in the air, to within 2 kPa of it. */
    private static void airDrawnIn(Area a, PumpBlockEntity pump) {
        double air = Atmospheres.at(a.level, a.point(2, 4, 2)).mixture().pressure();
        a.h.assertTrue(air > 100, "the open air is at " + air + " kPa");
        for (PipeNetwork side : List.of(pump.input().network(), pump.output().network())) {
            double kPa = side.gas().pressure();
            a.h.assertTrue(Math.abs(kPa - air) < 2, "a side is at " + kPa + " kPa, in " + air + " kPa of air");
        }
    }

    /** Passes once a pump has shown its flow: out of its output's end, and in at its input's. */
    private static void jetsBothWays(Area a, Vec3 near, List<GasJet> seen) {
        seen.addAll(JetLog.take(near));
        Vec3 outMouth = a.point(3, 2, 2).add(GasJets.PIPE_MOUTH, 0, 0);
        Vec3 inMouth = a.point(1, 2, 2).add(-GasJets.PIPE_MOUTH, 0, 0);
        boolean out = false;
        boolean in = false;
        for (GasJet jet : seen) {
            Vec3 at = new Vec3(jet.x(), jet.y(), jet.z());
            out |= at.distanceTo(outMouth) < 1e-6 && jet.dx() == 1 && jet.amount() >= 0.25;
            in |= at.distanceTo(inMouth) < 1e-6 && jet.dx() == -1 && jet.amount() <= -0.25;
        }
        a.h.assertTrue(out, "no jet out of the output's end, in " + seen);
        a.h.assertTrue(in, "no jet in at the input's end, in " + seen);
    }

    /**
     * A running pressure pump between two open ends in 1 atm air shows nothing at its default target,
     * as the air already holds its output there, and shows its flow, out of one end and in at the
     * other, once it is set above that. The pump itself has no particles.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_pressure_pump_shows_flow_only_past_its_target(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 930);
        PressurePumpBlockEntity pump = pumpInOpenAir(a, AtmosBlocks.PRESSURE_PUMP.get());
        h.assertValueEqual(pump.setting(), PressurePumpBlockEntity.DEFAULT_TARGET, "a new pump's target");
        Vec3 near = a.point(2, 2, 2);
        stepUntil(a, () -> airDrawnIn(a, pump));
        JetLog.take(near);
        pump.setOn(true);
        for (int i = 0; i < 10; i++) {
            a.step();
        }
        h.assertValueEqual(JetLog.take(near), List.of(), "jets at its default target");
        h.assertValueEqual(pump.status(), InlineStatus.TARGET_REACHED, "its status at its default target");
        pump.set(300);
        List<GasJet> seen = new ArrayList<>();
        stepUntil(a, () -> jetsBothWays(a, near, seen));
        h.assertValueEqual(pump.status(), InlineStatus.MOVING, "its status past its target");
        h.succeed();
    }

    /** The same rig with a running volume pump shows its flow as new, since it moves its rate regardless. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_volume_pump_shows_flow_at_its_default_rate(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 931);
        VolumePumpBlockEntity pump = pumpInOpenAir(a, AtmosBlocks.VOLUME_PUMP.get());
        h.assertValueEqual(pump.setting(), VolumePumpBlockEntity.DEFAULT_RATE, "a new pump's rate");
        Vec3 near = a.point(2, 2, 2);
        stepUntil(a, () -> airDrawnIn(a, pump));
        JetLog.take(near);
        pump.setOn(true);
        List<GasJet> seen = new ArrayList<>();
        stepUntil(a, () -> jetsBothWays(a, near, seen));
        h.succeed();
    }

    /** A flowing end's jet goes to players within 16 blocks only: half the reach of a burst. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_an_open_ends_jet_reaches_16_blocks(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 873);
        PipeBlockEntity pipe = endInRoom(a);
        Vec3 near = a.point(2, 1, 2);
        pipe.network().gas().add(new GasParcel(Map.of(N2, 300.0), T));
        JetLog.take(near);
        a.step();
        List<GasJet> jets = JetLog.take(near);
        h.assertValueEqual(jets.size(), 1, "jets in one atmos tick");
        GasJet jet = jets.get(0);
        FakePlayer p = TestPlayers.hurtable(a.level, "zas_client_019");
        p.moveTo(jet.x() + 15, jet.y(), jet.z());
        h.assertTrue(GasJets.reaches(p, jet), "a player 15 blocks away isn't sent it");
        p.moveTo(jet.x() + 17, jet.y(), jet.z());
        h.assertFalse(GasJets.reaches(p, jet), "a player 17 blocks away is sent it");
        h.succeed();
    }

    /** CONT-001: a segment saves its share of the network by volume. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_001_a_pipe_saves_its_share(GameTestHelper h) {
        Area a = new Area(h, 4);
        a.clear(0, 0, 4, 4);
        for (int x = 1; x <= 3; x++) {
            pipe(a, x, 1, 1);
        }
        network(a, 1, 1, 1).gas().add(new GasParcel(Map.of(O2, 9.0), T));
        CompoundTag saved = a.<PipeNodeEntity>entity(2, 1, 1).saveWithoutMetadata(a.level.registryAccess());
        a.close(GasStore.parcel(saved.getCompound("Gas")).moles(O2), 3.0, "saved share");
        h.succeed();
    }

    /** CONT-114: a dye colours a pipe, uses up the dye and leaves its connections and gas alone. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_114_a_dye_colours_a_pipe(GameTestHelper h) {
        Area a = new Area(h, 5);
        a.clear(0, 0, 4, 4);
        pipe(a, 1, 1, 1);
        pipe(a, 2, 1, 1);
        cap(a, 3, 1, 1, Direction.WEST);
        PipeNetwork net = network(a, 1, 1, 1);
        net.gas().add(new GasParcel(Map.of(O2, 3.0), T));

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack dye = new ItemStack(Items.RED_DYE, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, dye);
        for (BlockPos p : new BlockPos[] {a.abs(2, 1, 1), a.abs(3, 1, 1)}) {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false);
            a.level.getBlockState(p).useItemOn(dye, a.level, player, InteractionHand.MAIN_HAND, hit);
        }
        h.assertValueEqual(a.get(2, 1, 1).getValue(PipeColor.PROPERTY), PipeColor.RED, "pipe colour");
        h.assertValueEqual(a.get(3, 1, 1).getValue(PipeColor.PROPERTY), PipeColor.RED, "cap colour");
        h.assertValueEqual(dye.getCount(), 0, "dyes left");
        h.assertTrue(a.get(2, 1, 1).getValue(PipeBlock.CONNECTIONS.get(Direction.WEST)), "lost a connection");
        h.assertTrue(network(a, 1, 1, 1) == net, "dyeing rebuilt the network");
        a.close(net.gas().moles(O2), 3.0, "gas after dyeing");
        h.succeed();
    }

    /**
     * CONT-114: a mined dyed pipe or cap drops an item of its colour, which places dyed and picks
     * dyed; an undyed one drops a plain item that stacks with new ones.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_114_a_mined_dyed_pipe_keeps_its_dye(GameTestHelper h) {
        Area a = new Area(h, 6);
        a.clear(0, 0, 6, 4);
        pipe(a, 1, 1, 1);
        cap(a, 2, 1, 1, Direction.WEST);
        pipe(a, 4, 1, 1);
        a.set(1, 1, 1, a.get(1, 1, 1).setValue(PipeColor.PROPERTY, PipeColor.RED));
        a.set(2, 1, 1, a.get(2, 1, 1).setValue(PipeColor.PROPERTY, PipeColor.LIGHT_BLUE));

        ItemStack redPipe = drop(a, 1, 1, 1);
        ItemStack blueCap = drop(a, 2, 1, 1);
        ItemStack plain = drop(a, 4, 1, 1);
        h.assertTrue(redPipe.is(AtmosItems.PIPE.get()), "a dyed pipe dropped " + redPipe);
        h.assertValueEqual(PipeItem.color(redPipe), PipeColor.RED, "the mined pipe's colour");
        h.assertTrue(blueCap.is(AtmosItems.PIPE_CAP.get()), "a dyed cap dropped " + blueCap);
        h.assertValueEqual(PipeItem.color(blueCap), PipeColor.LIGHT_BLUE, "the mined cap's colour");
        h.assertTrue(
                ItemStack.isSameItemSameComponents(plain, new ItemStack(AtmosItems.PIPE.get())),
                "an undyed pipe's drop doesn't stack with a new pipe: " + plain);
        h.assertTrue(
                redPipe.getHoverName().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("item.zas_atmos.dyed"),
                "a dyed pipe isn't named by its colour: " + redPipe.getHoverName());
        h.assertValueEqual(
                PipeItem.color(a.get(1, 1, 1)
                        .getCloneItemStack(
                                new BlockHitResult(a.point(1, 1, 1), Direction.UP, a.abs(1, 1, 1), false),
                                a.level,
                                a.abs(1, 1, 1),
                                h.makeMockPlayer(GameType.CREATIVE))),
                PipeColor.RED,
                "picking a dyed pipe");

        // The dropped items place dyed.
        a.clear(0, 0, 6, 4);
        a.set(1, 0, 1, Area.STONE);
        a.set(2, 0, 1, Area.STONE);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        for (int x : new int[] {2, 1}) {
            ItemStack stack = x == 1 ? redPipe : blueCap;
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos below = a.abs(x, 0, 1);
            stack.useOn(new UseOnContext(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
            h.assertValueEqual(stack.getCount(), 0, "items left after placing at x = " + x);
        }
        h.assertTrue(a.get(1, 1, 1).is(AtmosBlocks.PIPE.get()), "the red pipe wasn't placed");
        h.assertValueEqual(a.get(1, 1, 1).getValue(PipeColor.PROPERTY), PipeColor.RED, "the placed pipe's colour");
        h.assertTrue(a.get(2, 1, 1).is(AtmosBlocks.PIPE_CAP.get()), "the blue cap wasn't placed");
        h.assertValueEqual(
                a.get(2, 1, 1).getValue(PipeColor.PROPERTY), PipeColor.LIGHT_BLUE, "the placed cap's colour");
        a.clear(0, 0, 6, 4);
        h.succeed();
    }

    /** What a block drops when mined, which must be one stack. */
    private static ItemStack drop(Area a, int x, int y, int z) {
        BlockPos pos = a.abs(x, y, z);
        List<ItemStack> drops = Block.getDrops(a.level.getBlockState(pos), a.level, pos, a.level.getBlockEntity(pos));
        a.h.assertValueEqual(drops.size(), 1, "stacks dropped at " + x + ", " + y + ", " + z);
        return drops.get(0);
    }
}
