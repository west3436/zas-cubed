package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
}
