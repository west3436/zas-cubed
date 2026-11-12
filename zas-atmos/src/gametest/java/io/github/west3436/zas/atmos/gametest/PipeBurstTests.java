package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.particle.GasJet;
import io.github.west3436.zas.atmos.particle.GasJetKind;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pipe bursts (CONT-108): a network more than 100 atm over a segment's cell loses one segment and
 * dumps everything into that cell, with no explosion. Each test builds in its own {@link Area} and
 * drives its steps within one game tick, in a batch of its own. {@code PipeBurstTest} checks the
 * threshold and the choice among segments, since a bare run can't span two cells at different
 * pressures: a bare pipe doesn't seal the gap it passes through. A run through a station wall can,
 * and {@code EncasedPipeTests} bursts one.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeBurstTests {
    private static final String BATCH = "zas_atmos_pipe_bursts";
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");

    /** A run's first and last x: a cap, three pipes, a cap. */
    private static final int WEST = 2;

    private static final int EAST = 6;
    private static final int SEGMENTS = EAST - WEST + 1;

    /** Explosions so far. Compared across a step, within one game tick, so no other test intrudes. */
    private static final AtomicInteger EXPLOSIONS = new AtomicInteger();

    static {
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Start e) -> EXPLOSIONS.incrementAndGet());
    }

    private PipeBurstTests() {}

    private static BlockState cap(Direction facing) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing);
    }

    /** A capped run at y = 2, z = 2, closed so that nothing leaks but a burst. */
    static PipeNetwork run(Area a) {
        a.place(WEST, 2, 2, cap(Direction.EAST));
        for (int x = WEST + 1; x < EAST; x++) {
            a.place(x, 2, 2, AtmosBlocks.PIPE.get().defaultBlockState());
        }
        a.place(EAST, 2, 2, cap(Direction.WEST));
        return a.<PipeNodeEntity>entity(WEST, 2, 2).network();
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

    /** Fills a network with CO2 to a pressure, at {@link Area#T}. */
    static void fillTo(PipeNetwork net, double kPa) {
        GasStore gas = net.gas();
        double more = (kPa - gas.pressure()) * gas.volume() / (GasMixture.GAS_CONSTANT * T);
        gas.add(new GasParcel(Map.of(CO2, more), T));
    }

    /**
     * In a sealed room at several atm: just below 100 atm over the room, which is above 100 atm
     * absolute, nothing happens. Just above, one segment goes and the room gets everything, with no
     * explosion.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_108_a_network_bursts_just_above_100_atm_over_its_room(GameTestHelper h) {
        Area a = new Area(h, 130);
        a.box(0, 0, 8, 4);
        PipeNetwork net = run(a);
        a.fill(4, 3, 3, Map.of(N2, 12_000.0));
        a.tick();
        a.tick();
        double room = Atmospheres.at(a.level, a.abs(4, 2, 2)).mixture().pressure();
        h.assertTrue(room > 300, "the room should be well above 1 atm, is at " + room);

        fillTo(net, room + PipeBurst.THRESHOLD_KPA - 1);
        h.assertTrue(net.gas().pressure() > PipeBurst.THRESHOLD_KPA, "not above 100 atm absolute");
        double below = net.gas().moles(CO2);
        a.step();
        a.step();
        h.assertValueEqual(segmentsLeft(a), SEGMENTS, "segments just below the threshold");
        a.close(net.gas().moles(CO2), below, "CO2 in the network just below the threshold");

        fillTo(net, room + PipeBurst.THRESHOLD_KPA + 1);
        double all = net.gas().moles(CO2);
        double roomCo2 = a.zone(4, 3, 3).mixture().moles(CO2);
        int explosions = EXPLOSIONS.get();
        a.step();
        a.tick();

        h.assertValueEqual(segmentsLeft(a), SEGMENTS - 1, "segments just above the threshold");
        h.assertValueEqual(EXPLOSIONS.get(), explosions, "explosions");
        h.assertTrue(a.get(0, 2, 2).equals(Area.STONE), "the room's wall went");
        a.close(a.zone(4, 3, 3).mixture().moles(CO2), roomCo2 + all, "CO2 in the room after the burst");
        double left = 0;
        for (int x = WEST; x <= EAST; x++) {
            if (a.level.getBlockEntity(a.abs(x, 2, 2)) instanceof PipeNodeEntity node) {
                left += node.network().gas().totalMoles();
            }
        }
        a.close(left, 0, "gas left in the remaining segments");
        h.succeed();
    }

    /**
     * Just below the threshold the server sends nothing. Just above, it sends one burst, from the
     * segment that went, in every direction, of all the network held (CLIENT-018).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_018_a_pipe_burst_sends_a_burst_of_the_whole_network(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 866);
        a.box(0, 0, 8, 4);
        PipeNetwork net = run(a);
        a.tick();
        a.tick();
        double room = Atmospheres.at(a.level, a.abs(4, 2, 2)).mixture().pressure();
        Vec3 near = a.point(4, 2, 2);

        fillTo(net, room + PipeBurst.THRESHOLD_KPA - 1);
        JetLog.take(near);
        a.step();
        h.assertTrue(JetLog.take(near).isEmpty(), "a network just below the threshold sent a jet");

        fillTo(net, room + PipeBurst.THRESHOLD_KPA + 1);
        double all = net.gas().totalMoles();
        a.step();
        h.assertValueEqual(segmentsLeft(a), SEGMENTS - 1, "segments just above the threshold");

        List<GasJet> jets = JetLog.take(near);
        h.assertValueEqual(jets.size(), 1, "jets sent");
        GasJet jet = jets.get(0);
        h.assertValueEqual(jet.kind(), GasJetKind.BURST, "the jet's kind");
        h.assertTrue(jet.dx() == 0 && jet.dy() == 0 && jet.dz() == 0, "a burst has a direction: " + jet);
        h.assertTrue(Math.abs(jet.amount() - all) / all < 1e-6, "the jet's moles: " + jet.amount() + ", not " + all);
        BlockPos from = BlockPos.containing(jet.x(), jet.y(), jet.z());
        h.assertFalse(a.level.getBlockEntity(from) instanceof PipeNodeEntity, "the jet isn't where a segment went");
        h.assertTrue(
                from.getY() == a.abs(0, 2, 0).getY()
                        && from.getZ() == a.abs(0, 0, 2).getZ(),
                "off the run");
        h.succeed();
    }

    /** In open air, with no zone, a burst gives the gas to the ambient. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_108_a_network_in_open_air_bursts(GameTestHelper h) {
        Area a = new Area(h, 131);
        a.clear(0, 0, 8, 4);
        PipeNetwork net = run(a);
        fillTo(net, 101.325 + PipeBurst.THRESHOLD_KPA + 1);
        a.step();
        h.assertValueEqual(segmentsLeft(a), SEGMENTS - 1, "segments after the burst");
        h.assertFalse(net.valid(), "the network outlived its burst");
        h.succeed();
    }

    /** A network with no pipe has no segment to burst: a lone connector port holds. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_108_a_network_with_no_segment_does_not_burst(GameTestHelper h) {
        Area a = new Area(h, 132);
        a.clear(0, 0, 4, 4);
        a.place(2, 1, 2, AtmosBlocks.CONNECTOR_PORT.get().defaultBlockState());
        PipeNetwork net = a.<PipeNodeEntity>entity(2, 1, 2).network();
        fillTo(net, 2 * PipeBurst.THRESHOLD_KPA);
        double moles = net.gas().moles(CO2);
        a.step();
        h.assertTrue(a.level.getBlockEntity(a.abs(2, 1, 2)) instanceof PipeNodeEntity, "the port went");
        a.close(net.gas().moles(CO2), moles, "CO2 in the port");
        h.succeed();
    }

    /**
     * The pressure a reaction raises bursts the network (CONT-100, CONT-108): the burn takes it past
     * the threshold at one atmos tick, and the next bursts it, so a canister on the network gets its
     * own step in between. The gas that goes is the burned mixture.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_108_a_reaction_overpressures_a_network_and_it_bursts(GameTestHelper h) {
        Area a = new Area(h, 133);
        a.clear(0, 0, 8, 4);
        PipeNetwork net = run(a);
        double total = 6000 * net.volume() / (GasMixture.GAS_CONSTANT * 900);
        net.gas().add(new GasParcel(Map.of(H2, 0.2 * total, O2, 0.3 * total, N2, 0.5 * total), 900));
        h.assertTrue(net.gas().pressure() < PipeBurst.THRESHOLD_KPA, "the network starts over the threshold");

        a.step();
        h.assertTrue(
                net.gas().pressure() > PipeBurst.THRESHOLD_KPA + 101.325,
                "the burn left only " + net.gas().pressure() + " kPa");
        h.assertValueEqual(segmentsLeft(a), SEGMENTS, "segments in the atmos tick of the burn");

        a.step();
        h.assertValueEqual(segmentsLeft(a), SEGMENTS - 1, "segments in the next atmos tick");
        h.succeed();
    }
}
