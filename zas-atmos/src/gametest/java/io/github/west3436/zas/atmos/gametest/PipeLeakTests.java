package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.particle.GasJet;
import io.github.west3436.zas.atmos.particle.GasJetKind;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pipe leaks and a configurable burst (CONT-005, CONT-168, CONT-169). Each test builds a capped run in
 * a sealed room in its own {@link Area}, sets the {@code [pipes]} config it needs, drives its steps
 * within one game tick and puts the config back, in a batch of its own, so no other test sees it.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeLeakTests {
    private static final String BATCH = "zas_atmos_pipe_leaks";
    private static final ResourceLocation CO2 = PipeSaveTests.GAS;

    /** A run's first and last x, as {@link PipeBurstTests#run}: a cap, three pipes, a cap. */
    private static final int WEST = 2;

    private static final int EAST = 6;
    private static final int SEGMENTS = EAST - WEST + 1;

    private static final double LEAK_KPA = 5000;

    private PipeLeakTests() {}

    /** The {@code [pipes]} config, set for a test and put back after it. */
    private record Pipes(double burst, double leak) implements AutoCloseable {
        static Pipes set(double burst, double leak) {
            Pipes was = new Pipes(AtmosServerConfig.PIPE_BURST_KPA.get(), AtmosServerConfig.PIPE_LEAK_KPA.get());
            AtmosServerConfig.PIPE_BURST_KPA.set(burst);
            AtmosServerConfig.PIPE_LEAK_KPA.set(leak);
            return was;
        }

        @Override
        public void close() {
            AtmosServerConfig.PIPE_BURST_KPA.set(burst);
            AtmosServerConfig.PIPE_LEAK_KPA.set(leak);
        }
    }

    /** A sealed room of nitrogen at several atm, with a capped run in it, settled. */
    private static PipeNetwork room(Area a) {
        a.box(0, 0, 8, 4);
        PipeNetwork net = PipeBurstTests.run(a);
        a.fill(4, 3, 3, Map.of(N2, 12_000.0));
        a.tick();
        a.tick();
        return net;
    }

    private static double roomPressure(Area a) {
        return Atmospheres.at(a.level, a.abs(4, 2, 2)).mixture().pressure();
    }

    private static double roomCo2(Area a) {
        return a.zone(4, 3, 3).mixture().moles(CO2);
    }

    private static int segmentsLeft(Area a) {
        int left = 0;
        for (int x = WEST; x <= EAST; x++) {
            if (a.level.getBlockEntity(a.abs(x, 2, 2)) instanceof PipeNodeEntity) {
                left++;
            }
        }
        return left;
    }

    /**
     * Over the leak threshold, below the burst, a segment leaks into the room: no segment goes, the
     * moles the network loses are the moles the room gains, it leaks faster the higher the pressure
     * difference, it shows a leak's jet and hisses, and it stops once the network is back under the
     * threshold (CONT-168).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_168_a_network_over_the_leak_threshold_leaks_into_its_room(GameTestHelper h) {
        JetLog.listen();
        SoundLog.listen();
        Area a = new Area(h, 5700);
        try (Pipes was = Pipes.set(PipeBurst.THRESHOLD_KPA, LEAK_KPA)) {
            PipeNetwork net = room(a);
            Vec3 near = a.point(4, 2, 2);
            JetLog.take(near);

            PipeBurstTests.fillTo(net, roomPressure(a) + LEAK_KPA + 1000);
            double total = net.gas().moles(CO2) + roomCo2(a);
            double before = net.gas().moles(CO2);
            a.step();
            double slow = before - net.gas().moles(CO2);
            h.assertTrue(slow > 0, "the network didn't leak 1,000 kPa over the threshold");
            a.close(net.gas().moles(CO2) + roomCo2(a), total, "CO2 in the network and the room after a leak");
            h.assertValueEqual(segmentsLeft(a), SEGMENTS, "segments after a leak");

            List<GasJet> jets = JetLog.take(near);
            h.assertValueEqual(jets.size(), 1, "jets from one atmos tick's leak");
            GasJet jet = jets.get(0);
            h.assertValueEqual(jet.kind(), GasJetKind.LEAK, "the jet's kind");
            h.assertTrue(Math.abs(jet.amount() - slow) <= 1e-6 * slow, "the jet's moles: " + jet.amount());
            BlockPos from = BlockPos.containing(jet.x(), jet.y(), jet.z());
            h.assertTrue(a.level.getBlockEntity(from) instanceof PipeNodeEntity, "the jet isn't at a segment");
            List<SoundLog.Played> hiss = SoundLog.take(Vec3.atCenterOf(from));
            h.assertTrue(hiss.size() == 1 && hiss.get(0).is(AtmosSounds.PIPE_LEAK), "the leak's start played " + hiss);

            // It goes on at the same segment, faster at a higher pressure.
            PipeBurstTests.fillTo(net, roomPressure(a) + LEAK_KPA + 4000);
            before = net.gas().moles(CO2);
            total = before + roomCo2(a);
            a.step();
            double fast = before - net.gas().moles(CO2);
            // The difference over the room rose by half: from the threshold + 1,000 to + 4,000 kPa.
            h.assertTrue(fast > 1.3 * slow, "4,000 kPa over leaked " + fast + " mol, 1,000 over " + slow);
            a.close(net.gas().moles(CO2) + roomCo2(a), total, "CO2 in the network and the room after a fast leak");
            jets = JetLog.take(near);
            h.assertTrue(
                    jets.size() == 1
                            && BlockPos.containing(
                                            jets.get(0).x(),
                                            jets.get(0).y(),
                                            jets.get(0).z())
                                    .equals(from),
                    "the leak moved: " + jets);

            // It runs down to the threshold and stops there.
            for (int i = 0; i < 400 && net.gas().pressure() - roomPressure(a) > LEAK_KPA; i++) {
                a.step();
            }
            double over = net.gas().pressure() - roomPressure(a);
            h.assertTrue(over <= LEAK_KPA, "the leak never brought the network under: " + over + " kPa over");
            h.assertTrue(over > 0.95 * LEAK_KPA, "the leak went on to " + over + " kPa over");
            double settled = net.gas().moles(CO2);
            a.step();
            a.step();
            a.close(net.gas().moles(CO2), settled, "CO2 in the network under the threshold");
            a.close(net.gas().moles(CO2) + roomCo2(a), total, "CO2 in the network and the room at the end");
            h.assertValueEqual(segmentsLeft(a), SEGMENTS, "segments at the end");
        }
        h.succeed();
    }

    /** Below the leak threshold nothing leaks; with leaks off, nothing leaks below the burst (CONT-168). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_168_no_leak_below_the_threshold_or_when_off(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 5701);
        try (Pipes was = Pipes.set(PipeBurst.THRESHOLD_KPA, LEAK_KPA)) {
            PipeNetwork net = room(a);
            Vec3 near = a.point(4, 2, 2);
            PipeBurstTests.fillTo(net, roomPressure(a) + LEAK_KPA - 10);
            JetLog.take(near);
            double below = net.gas().moles(CO2);
            a.step();
            a.step();
            a.close(net.gas().moles(CO2), below, "CO2 just below the leak threshold");
            h.assertTrue(JetLog.take(near).isEmpty(), "a jet below the leak threshold");

            AtmosServerConfig.PIPE_LEAK_KPA.set(0.0);
            PipeBurstTests.fillTo(net, roomPressure(a) + PipeBurst.THRESHOLD_KPA - 10);
            double off = net.gas().moles(CO2);
            a.step();
            a.step();
            a.close(net.gas().moles(CO2), off, "CO2 with leaks off, just below the burst");
            h.assertTrue(JetLog.take(near).isEmpty(), "a jet with leaks off");
            h.assertValueEqual(segmentsLeft(a), SEGMENTS, "segments");
        }
        h.succeed();
    }

    /** A burst threshold set in the config is the one a network bursts at (CONT-169). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_169_a_network_bursts_at_the_configured_threshold(GameTestHelper h) {
        Area a = new Area(h, 5702);
        double burst = 2000;
        try (Pipes was = Pipes.set(burst, 0)) {
            PipeNetwork net = room(a);
            PipeBurstTests.fillTo(net, roomPressure(a) + burst - 1);
            double below = net.gas().moles(CO2);
            a.step();
            a.step();
            h.assertValueEqual(segmentsLeft(a), SEGMENTS, "segments just below the configured threshold");
            a.close(net.gas().moles(CO2), below, "CO2 just below the configured threshold");

            PipeBurstTests.fillTo(net, roomPressure(a) + burst + 1);
            double all = net.gas().moles(CO2);
            double room = roomCo2(a);
            a.step();
            a.tick();
            h.assertValueEqual(segmentsLeft(a), SEGMENTS - 1, "segments just above the configured threshold");
            a.close(roomCo2(a), room + all, "CO2 in the room after the burst");
        }
        h.succeed();
    }

    /** With bursting off, a network far over 100 atm holds (CONT-169). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_169_bursting_can_be_turned_off(GameTestHelper h) {
        Area a = new Area(h, 5703);
        try (Pipes was = Pipes.set(0, 0)) {
            PipeNetwork net = room(a);
            PipeBurstTests.fillTo(net, roomPressure(a) + 2 * PipeBurst.THRESHOLD_KPA);
            double moles = net.gas().moles(CO2);
            a.step();
            a.step();
            h.assertValueEqual(segmentsLeft(a), SEGMENTS, "segments at 200 atm with bursting off");
            a.close(net.gas().moles(CO2), moles, "CO2 at 200 atm with bursting off");
        }
        h.succeed();
    }

    /**
     * Above both thresholds the network bursts rather than leaks: the burst checks first, and takes
     * everything (CONT-168, CONT-169).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_168_over_the_burst_a_network_bursts_and_does_not_leak(GameTestHelper h) {
        Area a = new Area(h, 5704);
        try (Pipes was = Pipes.set(PipeBurst.THRESHOLD_KPA, LEAK_KPA)) {
            PipeNetwork net = room(a);
            PipeBurstTests.fillTo(net, roomPressure(a) + PipeBurst.THRESHOLD_KPA + 1);
            double all = net.gas().moles(CO2);
            double room = roomCo2(a);
            a.step();
            a.tick();
            h.assertValueEqual(segmentsLeft(a), SEGMENTS - 1, "segments over both thresholds");
            a.close(roomCo2(a), room + all, "CO2 in the room after the burst");
        }
        h.succeed();
    }
}
