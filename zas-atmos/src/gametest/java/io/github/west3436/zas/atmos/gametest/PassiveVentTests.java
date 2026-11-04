package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.vent.PassiveVentBlock;
import io.github.west3436.zas.atmos.vent.PassiveVentBlockEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The passive vent in the world (CONT-016). Like {@link GasDeviceTests}, each builds in its own
 * {@link Area} and drives device steps and atmos ticks itself, in a batch of its own. A passive vent
 * needs no power.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PassiveVentTests {
    private static final String BATCH = "zas_atmos_passive_vent";
    private static final double RATIO = PipeBlockEntity.OPEN_END_RATIO;
    private static final double RT = GasMixture.GAS_CONSTANT * T;

    private PassiveVentTests() {}

    private static BlockState passiveVent(Direction facing) {
        return AtmosBlocks.PASSIVE_VENT.get().defaultBlockState().setValue(PassiveVentBlock.FACING, facing);
    }

    /** A sealed 3×3×3 room with its zone formed, and a passive vent in its west wall, facing in. */
    private static PassiveVentBlockEntity rig(Area a) {
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 10.0));
        a.place(0, 2, 2, passiveVent(Direction.EAST));
        a.tick();
        return a.entity(0, 2, 2);
    }

    /** Replaces the network's gas with one gas at a pressure. */
    private static void network(PassiveVentBlockEntity v, ResourceLocation gas, double kPa) {
        GasStore net = v.network().gas();
        net.drain();
        net.add(new GasParcel(Map.of(gas, kPa * net.volume() / RT), T));
    }

    private static double room(Area a) {
        return a.zone(2, 2, 2).mixture().pressure();
    }

    /** How far its network's pressure is above the room's, kPa. */
    private static double gap(Area a, PassiveVentBlockEntity v) {
        return v.network().gas().pressure() - room(a);
    }

    private static void steps(Area a, int n) {
        for (int i = 0; i < n; i++) {
            a.step();
        }
    }

    /**
     * CONT-016: a network well above the room equalizes with it at the one-connection ratio, each
     * atmos tick closing that share of the gap, and stops once the two are within 0.5 kPa, with no gas
     * lost.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_016_a_passive_vent_equalizes_only_above_the_threshold(GameTestHelper h) {
        Area a = new Area(h, 730);
        PassiveVentBlockEntity v = rig(a);
        a.close(v.network().volume(), PipeVolumes.VENT, "a lone passive vent's network");
        steps(a, 5); // its first look at the air, with an empty network that also takes some
        network(v, N2, room(a) + 300);
        double all = a.zone(2, 2, 2).mixture().moles(N2) + v.network().gas().moles(N2);
        double gap = gap(a, v);

        a.step();
        double next = gap(a, v);
        h.assertTrue(
                Math.abs(next - (1 - RATIO) * gap) < 1e-6, "one atmos tick took the gap from " + gap + " to " + next);

        steps(a, 60);
        gap = gap(a, v);
        h.assertTrue(gap <= PassiveVentBlockEntity.THRESHOLD_KPA, "it stopped " + gap + " kPa above the room");
        h.assertTrue(gap > (1 - RATIO) * PassiveVentBlockEntity.THRESHOLD_KPA - 1e-6, "it went on to " + gap + " kPa");
        a.close(a.zone(2, 2, 2).mixture().moles(N2) + v.network().gas().moles(N2), all, "the N2 there is");

        double held = v.network().gas().totalMoles();
        steps(a, 5);
        a.close(v.network().gas().totalMoles(), held, "the network once it has stopped");
        h.succeed();
    }

    /**
     * CONT-016: within 0.5 kPa nothing moves, above or below the room, even between different gases;
     * just past it, gas moves.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_016_a_passive_vent_stays_idle_within_the_threshold(GameTestHelper h) {
        Area a = new Area(h, 731);
        PassiveVentBlockEntity v = rig(a);
        steps(a, 5);
        for (double above : new double[] {0.4, -0.4, 0}) {
            network(v, ControllerPreset.CO2, room(a) + above);
            GasMixture before = a.zone(2, 2, 2).mixture();
            double net = v.network().gas().totalMoles();
            steps(a, 4);
            GasMixture after = a.zone(2, 2, 2).mixture();
            a.close(after.moles(N2), before.moles(N2), above + " kPa above: the room's N2");
            a.close(after.moles(ControllerPreset.CO2), 0, above + " kPa above: CO2 in the room");
            a.close(v.network().gas().totalMoles(), net, above + " kPa above: the network");
        }
        network(v, ControllerPreset.CO2, room(a) + 0.6);
        a.step();
        h.assertTrue(a.zone(2, 2, 2).mixture().moles(ControllerPreset.CO2) > 0, "0.6 kPa above: no CO2 left");
        h.succeed();
    }

    /** CONT-016: below the room, its network takes the room's gases, each by its own gap. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_016_a_passive_vent_takes_from_a_room_above_it(GameTestHelper h) {
        Area a = new Area(h, 732);
        PassiveVentBlockEntity v = rig(a);
        steps(a, 5);
        v.network().gas().drain();
        GasMixture before = a.zone(2, 2, 2).mixture();
        double vp = v.network().volume();
        double vz = before.volume();
        a.step();
        GasMixture after = a.zone(2, 2, 2).mixture();
        for (ResourceLocation gas : new ResourceLocation[] {N2, O2}) {
            double in = RATIO * before.moles(gas) * vp / (vp + vz);
            a.close(after.moles(gas), before.moles(gas) - in, gas + " in the room");
            a.close(v.network().gas().moles(gas), in, gas + " in the network");
        }
        h.succeed();
    }

    /** CONT-016: a pipe joins a passive vent only on its back, whichever way it faces, and no power is needed. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_016_a_pipe_joins_a_passive_vent_only_on_its_back(GameTestHelper h) {
        Area a = new Area(h, 733);
        for (Direction facing : Direction.values()) {
            a.clear(0, 0, 6, 6);
            a.place(3, 3, 3, passiveVent(facing));
            for (Direction d : Direction.values()) {
                BlockPos p = new BlockPos(3, 3, 3).relative(d);
                a.place(p.getX(), p.getY(), p.getZ(), GasDeviceTests.pipe());
            }
            for (Direction d : Direction.values()) {
                BlockPos p = new BlockPos(3, 3, 3).relative(d);
                boolean expect = d == facing.getOpposite();
                h.assertTrue(
                        a.get(p.getX(), p.getY(), p.getZ()).getValue(PipeBlock.CONNECTIONS.get(d.getOpposite()))
                                == expect,
                        "passive vent facing " + facing + ": pipe on its " + d + " side joined " + !expect);
            }
            PassiveVentBlockEntity v = a.entity(3, 3, 3);
            a.close(v.network().volume(), PipeVolumes.VENT + PipeVolumes.PIPE, "the passive vent and its one pipe");
            h.assertFalse(v instanceof PoweredDevice, "a passive vent draws power");
        }
        h.succeed();
    }

    /**
     * CONT-016: in an open cell a passive vent equalizes with the ambient and forms no zone. It runs
     * in real time, as the scrubber's open cell test does.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void cont_016_a_passive_vent_in_an_open_cell_vents_to_the_ambient(GameTestHelper h) {
        Area a = new Area(h, 734);
        a.forceLoad(true); // it waits in real time
        a.clear(0, 0, 4, 4);
        a.place(2, 1, 2, passiveVent(Direction.NORTH));
        PassiveVentBlockEntity v = a.entity(2, 1, 2);
        network(v, N2, 1_000);
        double start = v.network().gas().moles(N2);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(v.network().gas().moles(N2) < start * 0.9, "the network still holds its gas");
                    h.assertTrue(Atmospheres.zoneAt(a.level, a.abs(2, 1, 1)).isEmpty(), "venting formed a zone");
                })
                .thenExecute(() -> a.forceLoad(false))
                .thenSucceed();
    }
}
