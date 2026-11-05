package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.valve.ValveBlock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The valves (CONT-010) and open valves joining networks (CONT-075). Most tests drive device steps
 * and atmos ticks themselves, in {@link PipeTests}' batch. The ones whose chunks really leave and
 * return wait in real time, in a batch of their own, as {@link PipeChunkTests} do.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ValveTests {
    private static final String CHUNK_BATCH = "zas_atmos_valve_chunks";
    private static final TicketType<ChunkPos> HOLD =
            TicketType.create("zas_atmos_gametest_valve_hold", Comparator.comparingLong(ChunkPos::toLong));
    private static final BlockState REDSTONE = Blocks.REDSTONE_BLOCK.defaultBlockState();

    /** The volume on each side of {@link #line}'s valve. */
    private static final double LEFT = 3 * PipeVolumes.PIPE;

    private static final double RIGHT = 2 * PipeVolumes.PIPE;
    private static final double ALL = LEFT + PipeVolumes.VALVE + RIGHT;

    private ValveTests() {}

    private static BlockState manual(Direction.Axis axis) {
        return AtmosBlocks.MANUAL_VALVE.get().defaultBlockState().setValue(ValveBlock.AXIS, axis);
    }

    private static BlockState redstone(Direction.Axis axis) {
        return AtmosBlocks.REDSTONE_VALVE.get().defaultBlockState().setValue(ValveBlock.AXIS, axis);
    }

    /** At y = 1, z = 1: a cap at x = 0, pipes at 1 and 2, the valve at 3, a pipe at 4, a cap at 5. */
    private static void line(Area a, BlockState valve) {
        a.clear(-1, 0, 7, 3);
        PipeTests.cap(a, 0, 1, 1, Direction.EAST);
        PipeTests.pipe(a, 1, 1, 1);
        PipeTests.pipe(a, 2, 1, 1);
        a.place(3, 1, 1, valve);
        PipeTests.pipe(a, 4, 1, 1);
        PipeTests.cap(a, 5, 1, 1, Direction.WEST);
    }

    private static PipeNetwork net(Area a, int x) {
        return PipeTests.network(a, x, 1, 1);
    }

    /** Uses a block by hand, as a right click with an empty hand does. */
    private static void use(Area a, int x, int y, int z, Player player) {
        BlockPos pos = a.abs(x, y, z);
        a.level
                .getBlockState(pos)
                .useWithoutItem(a.level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static boolean open(Area a, int x, int y, int z) {
        return a.get(x, y, z).getValue(ValveBlock.OPEN);
    }

    /** Turns {@link #line}'s valve the way its kind turns: by hand, or by a redstone block on it. */
    private static void turn(Area a, boolean to) {
        if (a.get(3, 1, 1).is(AtmosBlocks.MANUAL_VALVE.get())) {
            use(a, 3, 1, 1, a.h.makeMockPlayer(GameType.SURVIVAL));
        } else {
            a.set(3, 2, 1, to ? REDSTONE : AIR);
        }
        a.h.assertValueEqual(open(a, 3, 1, 1), to, "the valve's position");
    }

    private static double pressure(PipeNodeEntity node) {
        GasParcel share = node.share();
        return new GasMixture(share.moles(), share.temperature(), node.nodeVolume()).pressure();
    }

    /** CONT-010: a manual valve turns by hand, and redstone doesn't move it. */
    @GameTest(template = "empty", batch = PipeTests.BATCH)
    public static void cont_010_a_manual_valve_turns_by_hand_only(GameTestHelper h) {
        Area a = new Area(h, 110);
        line(a, manual(Direction.Axis.X));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertFalse(open(a, 3, 1, 1), "a new manual valve is open");

        use(a, 3, 1, 1, player);
        h.assertTrue(open(a, 3, 1, 1), "using a shut valve didn't open it");
        use(a, 3, 1, 1, player);
        h.assertFalse(open(a, 3, 1, 1), "using an open valve didn't shut it");

        a.set(3, 2, 1, REDSTONE);
        h.assertFalse(open(a, 3, 1, 1), "a signal opened a manual valve");
        use(a, 3, 1, 1, player);
        a.set(3, 2, 1, AIR);
        h.assertTrue(open(a, 3, 1, 1), "losing a signal shut a manual valve");

        // Only its flanges connect: a pipe beside it, off its axis, grows no arm and doesn't join it.
        PipeTests.pipe(a, 3, 1, 2);
        h.assertFalse(a.get(3, 1, 2).getValue(PipeBlock.CONNECTIONS.get(Direction.NORTH)), "joined off its axis");
        h.assertValueEqual(net(a, 3).members().size(), 6, "the open valve's network, without the pipe beside it");
        h.succeed();
    }

    /** CONT-010: a redstone valve is open exactly while it gets a signal, and a hand doesn't move it. */
    @GameTest(template = "empty", batch = PipeTests.BATCH)
    public static void cont_010_a_redstone_valve_follows_its_signal_only(GameTestHelper h) {
        Area a = new Area(h, 111);
        line(a, redstone(Direction.Axis.X));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertFalse(open(a, 3, 1, 1), "a redstone valve with no signal is open");

        use(a, 3, 1, 1, player);
        h.assertFalse(open(a, 3, 1, 1), "a hand opened a redstone valve");

        a.set(3, 2, 1, REDSTONE);
        h.assertTrue(open(a, 3, 1, 1), "a signal didn't open it");
        use(a, 3, 1, 1, player);
        h.assertTrue(open(a, 3, 1, 1), "a hand shut a redstone valve");

        a.set(3, 2, 1, AIR);
        h.assertFalse(open(a, 3, 1, 1), "losing the signal didn't shut it");

        // A real lever on the floor beside it, thrown by hand.
        a.set(3, 0, 2, Area.STONE);
        a.set(3, 1, 2, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
        use(a, 3, 1, 2, player);
        h.assertTrue(open(a, 3, 1, 1), "a lever didn't open it");
        use(a, 3, 1, 2, player);
        h.assertFalse(open(a, 3, 1, 1), "a lever didn't shut it");
        h.succeed();
    }

    /**
     * CONT-010: a valve lines up with the pipes beside it, whichever way it is clicked. With none
     * around, it takes the line the player looks along. It drops itself when mined, and a redstone
     * valve placed in a signal starts open.
     */
    @GameTest(template = "empty", batch = PipeTests.BATCH)
    public static void cont_010_a_placed_valve_lines_up_with_its_pipes(GameTestHelper h) {
        Area a = new Area(h, 112);
        a.clear(-1, -1, 12, 4);
        for (int x = 0; x <= 10; x++) {
            a.set(x, 0, 1, Area.STONE);
        }
        Player player = h.makeMockPlayer(GameType.SURVIVAL);

        // A gap in a run along x, clicked from the floor: the look says z and the click says y.
        PipeTests.pipe(a, 1, 1, 1);
        PipeTests.pipe(a, 3, 1, 1);
        placeOnFloor(a, player, new ItemStack(AtmosItems.MANUAL_VALVE.get()), 2);
        h.assertValueEqual(a.get(2, 1, 1).getValue(ValveBlock.AXIS), Direction.Axis.X, "the valve in the gap");
        h.assertTrue(a.get(1, 1, 1).getValue(PipeBlock.CONNECTIONS.get(Direction.EAST)), "the left pipe's arm");
        h.assertTrue(a.get(3, 1, 1).getValue(PipeBlock.CONNECTIONS.get(Direction.WEST)), "the right pipe's arm");

        // No pipes around: the player's look decides.
        placeOnFloor(a, player, new ItemStack(AtmosItems.MANUAL_VALVE.get()), 6);
        h.assertValueEqual(a.get(6, 1, 1).getValue(ValveBlock.AXIS), Direction.Axis.Z, "looking south");
        player.setXRot(90);
        placeOnFloor(a, player, new ItemStack(AtmosItems.REDSTONE_VALVE.get()), 8);
        player.setXRot(0);
        h.assertValueEqual(a.get(8, 1, 1).getValue(ValveBlock.AXIS), Direction.Axis.Y, "looking down");
        h.assertFalse(open(a, 8, 1, 1), "a redstone valve placed with no signal is open");

        // In a signal, a redstone valve starts open.
        a.set(10, 2, 1, REDSTONE);
        placeOnFloor(a, player, new ItemStack(AtmosItems.REDSTONE_VALVE.get()), 10);
        h.assertTrue(open(a, 10, 1, 1), "a redstone valve placed in a signal is shut");

        for (int x : new int[] {2, 8}) {
            BlockPos pos = a.abs(x, 1, 1);
            List<ItemStack> drops =
                    Block.getDrops(a.level.getBlockState(pos), a.level, pos, a.level.getBlockEntity(pos));
            h.assertValueEqual(drops.size(), 1, "stacks dropped at x = " + x);
            h.assertTrue(
                    drops.get(0).is((x == 2 ? AtmosItems.MANUAL_VALVE : AtmosItems.REDSTONE_VALVE).get()),
                    "the valve dropped " + drops.get(0));
        }
        h.succeed();
    }

    private static void placeOnFloor(Area a, Player player, ItemStack stack, int x) {
        BlockPos below = a.abs(x, 0, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
        a.h.assertValueEqual(stack.getCount(), 0, "valves left after placing at x = " + x);
    }

    /** CONT-075 through a manual valve, turned by hand. */
    @GameTest(template = "empty", batch = PipeTests.BATCH)
    public static void cont_075_a_manual_valve_joins_networks_and_splits_them_by_volume(GameTestHelper h) {
        joinAndSplit(h, 113, manual(Direction.Axis.X));
    }

    /** CONT-075 through a redstone valve, turned by a signal. */
    @GameTest(template = "empty", batch = PipeTests.BATCH)
    public static void cont_075_a_redstone_valve_joins_networks_and_splits_them_by_volume(GameTestHelper h) {
        joinAndSplit(h, 114, redstone(Direction.Axis.X));
    }

    /**
     * CONT-075's Verify plan, with conservation checks: shut, a valve keeps networks at different
     * pressures and temperatures apart. Opened, they equalize as one network, with the gas and the
     * thermal energy of both. Shut again, each part, the valve's body included, takes its share by
     * volume, and they are apart again.
     */
    private static void joinAndSplit(GameTestHelper h, int index, BlockState valve) {
        Area a = new Area(h, index);
        line(a, valve);
        h.assertTrue(net(a, 1) != net(a, 4), "a shut valve joined its sides");
        h.assertTrue(net(a, 3) != net(a, 1) && net(a, 3) != net(a, 4), "a shut valve joined a side");
        a.close(net(a, 1).volume(), LEFT, "the left side's volume");
        a.close(net(a, 3).volume(), PipeVolumes.VALVE, "the shut valve's volume");
        a.close(net(a, 4).volume(), RIGHT, "the right side's volume");
        net(a, 1).gas().add(new GasParcel(Map.of(N2, 30.0), 300));
        net(a, 4).gas().add(new GasParcel(Map.of(O2, 7.0), 600));
        double energy = net(a, 1).gas().thermalEnergy() + net(a, 4).gas().thermalEnergy();
        h.assertTrue(net(a, 1).gas().pressure() > net(a, 4).gas().pressure() + 100, "the sides' pressures are close");

        a.step();
        a.step();
        a.close(net(a, 1).gas().moles(N2), 30.0, "N2 on the left behind a shut valve");
        a.close(net(a, 1).gas().moles(O2), 0.0, "O2 on the left behind a shut valve");
        a.close(net(a, 4).gas().moles(O2), 7.0, "O2 on the right behind a shut valve");
        a.close(net(a, 4).gas().moles(N2), 0.0, "N2 on the right behind a shut valve");
        a.close(net(a, 3).gas().totalMoles(), 0.0, "gas in the shut valve");

        turn(a, true);
        a.step();

        PipeNetwork one = net(a, 3);
        h.assertTrue(net(a, 1) == one && net(a, 4) == one, "an open valve didn't join its sides");
        h.assertValueEqual(one.members().size(), 6, "the joined network's members");
        a.close(one.volume(), ALL, "the joined volume, with the valve's");
        a.close(one.gas().moles(N2), 30.0, "N2 joined");
        a.close(one.gas().moles(O2), 7.0, "O2 joined");
        a.close(one.gas().thermalEnergy(), energy, "the thermal energy joined");
        double mixedP = one.gas().pressure();
        double mixedT = one.gas().temperature();
        for (int x = 0; x <= 5; x++) {
            a.close(pressure(a.entity(x, 1, 1)), mixedP, "the pressure at x = " + x);
        }

        turn(a, false);
        a.step();

        PipeNetwork left = net(a, 1);
        PipeNetwork body = net(a, 3);
        PipeNetwork right = net(a, 4);
        h.assertTrue(left != right && left != body && right != body, "shutting the valve didn't separate them");
        a.close(left.volume() + body.volume() + right.volume(), ALL, "the parts' volumes");
        for (PipeNetwork part : List.of(left, body, right)) {
            double share = part.volume() / ALL;
            a.close(part.gas().moles(N2), 30.0 * share, "N2 by volume in " + part.volume() + " L");
            a.close(part.gas().moles(O2), 7.0 * share, "O2 by volume in " + part.volume() + " L");
            a.close(part.gas().pressure(), mixedP, "the pressure in " + part.volume() + " L");
            a.close(part.gas().temperature(), mixedT, "the temperature in " + part.volume() + " L");
        }
        a.close(
                left.gas().thermalEnergy()
                        + body.gas().thermalEnergy()
                        + right.gas().thermalEnergy(),
                energy,
                "the thermal energy split");

        double rightN2 = right.gas().moles(N2);
        left.gas().add(new GasParcel(Map.of(N2, 5.0), mixedT));
        a.step();
        a.close(net(a, 4).gas().moles(N2), rightN2, "gas passed a valve shut again");
        a.close(net(a, 1).gas().moles(N2), 30.0 * LEFT / ALL + 5.0, "the gas added on the left");
        h.succeed();
    }

    /**
     * CONT-075 and CONT-004: pipes keep their arms at a shut valve and have no open end there, so
     * nothing leaks.
     */
    @GameTest(template = "empty", batch = PipeTests.BATCH)
    public static void cont_075_a_pipe_at_a_shut_valve_does_not_leak(GameTestHelper h) {
        Area a = new Area(h, 115);
        a.box(-1, -1, 7, 3);
        a.fill(3, 2, 2, Map.of(N2, 100.0));
        PipeTests.cap(a, 0, 1, 1, Direction.EAST);
        PipeTests.pipe(a, 1, 1, 1);
        PipeTests.pipe(a, 2, 1, 1);
        a.place(3, 1, 1, manual(Direction.Axis.X));
        PipeTests.pipe(a, 4, 1, 1);
        PipeTests.cap(a, 5, 1, 1, Direction.WEST);
        a.tick();

        h.assertTrue(a.get(2, 1, 1).getValue(PipeBlock.CONNECTIONS.get(Direction.EAST)), "no arm to a shut valve");
        h.assertTrue(a.get(4, 1, 1).getValue(PipeBlock.CONNECTIONS.get(Direction.WEST)), "no arm to a shut valve");
        h.assertFalse(a.<PipeBlockEntity>entity(2, 1, 1).isOpen(), "a pipe at a shut valve has an open end");
        h.assertFalse(a.<PipeBlockEntity>entity(4, 1, 1).isOpen(), "a pipe at a shut valve has an open end");

        net(a, 1).gas().add(new GasParcel(Map.of(O2, 10.0), T));
        net(a, 4).gas().add(new GasParcel(Map.of(O2, 3.0), T));
        double roomO2 = a.zone(3, 2, 2).mixture().moles(O2);
        for (int i = 0; i < PipeBlockEntity.SETTLE_STEPS + 2; i++) {
            a.step();
        }
        a.close(net(a, 1).gas().moles(O2), 10.0, "O2 on the left");
        a.close(net(a, 4).gas().moles(O2), 3.0, "O2 on the right");
        a.close(a.zone(3, 2, 2).mixture().moles(O2), roomO2, "O2 in the room");
        h.succeed();
    }

    /**
     * CONT-009 and CONT-075: a removed valve releases its share into its cell, open or shut, and the
     * rest stays in the pipes, with no gas lost.
     */
    @GameTest(template = "empty", batch = PipeTests.BATCH)
    public static void cont_075_a_removed_valve_releases_its_share(GameTestHelper h) {
        for (boolean shut : new boolean[] {false, true}) {
            Area a = new Area(h, shut ? 116 : 122);
            a.box(-1, -1, 7, 3);
            a.fill(3, 2, 2, Map.of(N2, 100.0));
            PipeTests.cap(a, 0, 1, 1, Direction.EAST);
            PipeTests.pipe(a, 1, 1, 1);
            PipeTests.pipe(a, 2, 1, 1);
            a.place(3, 1, 1, manual(Direction.Axis.X).setValue(ValveBlock.OPEN, true));
            PipeTests.pipe(a, 4, 1, 1);
            PipeTests.cap(a, 5, 1, 1, Direction.WEST);
            a.tick();
            net(a, 3).gas().add(new GasParcel(Map.of(O2, ALL / 10), T));
            if (shut) {
                turn(a, false);
                a.step();
            }
            double roomO2 = a.zone(3, 2, 2).mixture().moles(O2);

            a.set(3, 1, 1, AIR);
            a.tick();

            double released = a.zone(3, 2, 2).mixture().moles(O2) - roomO2;
            a.close(released, PipeVolumes.VALVE / 10, "the share released, shut = " + shut);
            a.close(net(a, 1).gas().moles(O2), LEFT / 10, "the left side's share, shut = " + shut);
            a.close(net(a, 4).gas().moles(O2), RIGHT / 10, "the right side's share, shut = " + shut);
        }
        h.succeed();
    }

    /**
     * CONT-075: valves in a row each decide for themselves, and a valve on a bypass splits nothing:
     * the pipes around it stay one network while it is shut.
     */
    @GameTest(template = "empty", batch = PipeTests.BATCH)
    public static void cont_075_valves_in_a_row_and_on_a_bypass(GameTestHelper h) {
        Area a = new Area(h, 117);
        a.clear(-1, 0, 7, 3);
        PipeTests.cap(a, 0, 1, 1, Direction.EAST);
        a.place(1, 1, 1, manual(Direction.Axis.X).setValue(ValveBlock.OPEN, true));
        a.place(2, 1, 1, manual(Direction.Axis.X));
        PipeTests.cap(a, 3, 1, 1, Direction.WEST);
        h.assertTrue(net(a, 0) == net(a, 1), "an open valve didn't join the cap");
        h.assertTrue(net(a, 2) != net(a, 1), "a shut valve joined the open valve beside it");
        h.assertTrue(net(a, 3) != net(a, 2), "a shut valve joined the cap beside it");
        net(a, 0).gas().add(new GasParcel(Map.of(O2, 9.0), T));
        use(a, 2, 1, 1, h.makeMockPlayer(GameType.SURVIVAL));
        a.step();
        h.assertTrue(net(a, 0) == net(a, 3), "two open valves in a row didn't join");
        a.close(net(a, 0).volume(), 2 * PipeVolumes.PIPE + 2 * PipeVolumes.VALVE, "the row's volume");
        a.close(net(a, 0).gas().moles(O2), 9.0, "the row's O2");

        // A ring of five pipes with a valve as its sixth side.
        a.clear(-1, 0, 7, 3);
        a.place(2, 1, 1, manual(Direction.Axis.X).setValue(ValveBlock.OPEN, true));
        for (int[] p : new int[][] {{1, 1}, {1, 2}, {2, 2}, {3, 2}, {3, 1}}) {
            PipeTests.pipe(a, p[0], p[1], 1);
        }
        h.assertValueEqual(net(a, 1).members().size(), 6, "the ring's members");
        net(a, 2).gas().add(new GasParcel(Map.of(O2, 12.0), T));
        double volume = 5 * PipeVolumes.PIPE + PipeVolumes.VALVE;
        use(a, 2, 1, 1, h.makeMockPlayer(GameType.SURVIVAL));
        a.step();
        h.assertValueEqual(net(a, 1).members().size(), 5, "the ring's members round a shut valve");
        h.assertTrue(net(a, 1) == net(a, 3), "the ring split");
        a.close(net(a, 1).gas().moles(O2), 12.0 * 5 * PipeVolumes.PIPE / volume, "the ring's share");
        a.close(net(a, 2).gas().moles(O2), 12.0 * PipeVolumes.VALVE / volume, "the valve's share");
        h.succeed();
    }

    /**
     * CONT-075 and CONT-006: networks are rebuilt only when a valve has moved since its last step. A
     * neighbour's change that leaves the signal alone, or a turn undone before the valve steps, keeps
     * the network it had, and however often a valve turns between steps it rebuilds at most once.
     */
    @GameTest(template = "empty", batch = PipeTests.BATCH)
    public static void cont_075_a_valve_rebuilds_networks_only_when_it_has_moved(GameTestHelper h) {
        Area a = new Area(h, 118);
        line(a, redstone(Direction.Axis.X));
        turn(a, true);
        a.step();
        PipeNetwork one = net(a, 1);
        h.assertValueEqual(one.members().size(), 6, "the joined network's members");

        // A second source: the signal doesn't change, nor does the valve.
        a.set(3, 0, 1, REDSTONE);
        a.step();
        h.assertTrue(net(a, 1) == one, "a second signal rebuilt the network");
        a.set(3, 0, 1, AIR);
        a.step();
        h.assertTrue(net(a, 1) == one, "losing the second signal rebuilt the network");

        // Off and on between steps: the valve is where it was.
        turn(a, false);
        turn(a, true);
        a.step();
        h.assertTrue(net(a, 1) == one, "a turn undone before the step rebuilt the network");

        // Many turns between steps count as the one they add up to, and only at the step.
        for (int i = 0; i < 5; i++) {
            turn(a, false);
            turn(a, true);
        }
        turn(a, false);
        h.assertTrue(one.valid(), "turning the valve rebuilt the network before its step");
        a.step();
        h.assertFalse(one.valid(), "the shut valve's network outlived its step");
        h.assertTrue(net(a, 1) != net(a, 4), "the sides didn't part");
        h.succeed();
    }

    /** A row across a chunk border with a valve at x = 15: pipes at 12 to 14 and 16 to 19, caps at 11 and 20. */
    private static final class Row {
        static final int VALVE = 15;
        static final double WEST_VOLUME = 4 * PipeVolumes.PIPE;
        static final double EAST_VOLUME = 5 * PipeVolumes.PIPE;
        static final double VOLUME = WEST_VOLUME + PipeVolumes.VALVE + EAST_VOLUME;

        final GameTestHelper h;
        final Area a;
        final ChunkPos chunkA;
        final ChunkPos chunkB;

        Row(GameTestHelper h, int index, boolean open) {
            this.h = h;
            this.a = new Area(h, index);
            this.chunkA = new ChunkPos(a.abs(0, 0, 0));
            this.chunkB = new ChunkPos(a.abs(16, 0, 0));
            hold(chunkA, true);
            hold(chunkB, true);
            a.clear(10, 1, 21, 4);
            PipeTests.cap(a, 11, 1, 2, Direction.EAST);
            for (int x = 12; x < 20; x++) {
                if (x != VALVE) {
                    PipeTests.pipe(a, x, 1, 2);
                }
            }
            PipeTests.cap(a, 20, 1, 2, Direction.WEST);
            a.place(VALVE, 1, 2, manual(Direction.Axis.X).setValue(ValveBlock.OPEN, open));
        }

        void hold(ChunkPos chunk, boolean on) {
            if (on) {
                a.level.getChunkSource().addRegionTicket(HOLD, chunk, 0, chunk);
            } else {
                a.level.getChunkSource().removeRegionTicket(HOLD, chunk, 0, chunk);
            }
        }

        /** Whether a chunk is loaded in full: asking loads nothing. */
        boolean full(ChunkPos chunk) {
            return a.level.getChunkSource().hasChunk(chunk.x, chunk.z);
        }

        PipeNodeEntity node(int x) {
            return a.entity(x, 1, 2);
        }

        PipeNetwork net(int x) {
            return node(x).network();
        }

        boolean open() {
            return a.get(VALVE, 1, 2).getValue(ValveBlock.OPEN);
        }

        void turn() {
            use(a, VALVE, 1, 2, h.makeMockPlayer(GameType.SURVIVAL));
        }

        void done() {
            a.clear(10, 1, 21, 4);
            hold(chunkA, false);
            hold(chunkB, false);
        }
    }

    /** Waits for both chunks to unload, for two atmos ticks, and for them to load and settle again. */
    private static void reload(
            Row r, PipeNodeEntity oldA, PipeNodeEntity oldB, net.minecraft.gametest.framework.GameTestSequence seq) {
        seq.thenExecute(() -> {
                    r.hold(r.chunkA, false);
                    r.hold(r.chunkB, false);
                })
                .thenWaitUntil(() -> {
                    r.h.assertTrue(oldA.isRemoved(), "chunk A's nodes haven't unloaded");
                    r.h.assertTrue(oldB.isRemoved(), "chunk B's nodes haven't unloaded");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    r.hold(r.chunkA, true);
                    r.hold(r.chunkB, true);
                })
                .thenWaitUntil(() -> r.h.assertTrue(r.full(r.chunkA) && r.full(r.chunkB), "the chunks haven't loaded"))
                .thenIdle(60);
    }

    /**
     * A shut valve through a real save and reload: it stays shut, and its sides keep what they held,
     * apart. Then it opens, and they join.
     */
    @GameTest(template = "empty", batch = CHUNK_BATCH, timeoutTicks = 1200)
    public static void cont_075_a_shut_valve_stays_shut_through_a_reload(GameTestHelper h) {
        Row r = new Row(h, 119, false);
        r.net(12).gas().add(new GasParcel(Map.of(N2, 40.0), 300));
        r.net(16).gas().add(new GasParcel(Map.of(O2, 10.0), 500));
        double energy = r.net(12).gas().thermalEnergy() + r.net(16).gas().thermalEnergy();
        PipeNodeEntity oldA = r.node(12);
        PipeNodeEntity oldB = r.node(16);
        var seq = h.startSequence();
        reload(r, oldA, oldB, seq);
        seq.thenExecute(() -> {
                    h.assertTrue(r.node(12) != oldA, "chunk A's node is the one from before the unload");
                    h.assertFalse(r.open(), "the valve opened over the reload");
                    h.assertTrue(r.net(12) != r.net(16), "the sides joined over the reload");
                    r.a.close(r.net(12).gas().moles(N2), 40.0, "N2 on the west side after the reload");
                    r.a.close(r.net(16).gas().moles(O2), 10.0, "O2 on the east side after the reload");
                    r.a.close(r.net(Row.VALVE).gas().totalMoles(), 0.0, "gas in the valve after the reload");
                    r.a.close(
                            r.net(12).gas().thermalEnergy() + r.net(16).gas().thermalEnergy(),
                            energy,
                            "the energy after the reload");
                    r.turn();
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(r.net(12) == r.net(16), "the opened valve didn't join its sides");
                    r.a.close(r.net(12).volume(), Row.VOLUME, "the joined volume");
                    r.a.close(r.net(12).gas().moles(N2), 40.0, "N2 joined");
                    r.a.close(r.net(12).gas().moles(O2), 10.0, "O2 joined");
                    r.a.close(r.net(12).gas().thermalEnergy(), energy, "the energy joined");
                    r.done();
                })
                .thenSucceed();
    }

    /**
     * An open valve through a real save and reload: it stays open, with one network of all the gas
     * and energy, saved across two chunks. Then it shuts, and the gas splits by volume.
     */
    @GameTest(template = "empty", batch = CHUNK_BATCH, timeoutTicks = 1200)
    public static void cont_075_an_open_valve_stays_open_through_a_reload(GameTestHelper h) {
        Row r = new Row(h, 120, true);
        r.net(12).gas().add(new GasParcel(Map.of(N2, 50.0), 400));
        double energy = r.net(12).gas().thermalEnergy();
        PipeNodeEntity oldA = r.node(12);
        PipeNodeEntity oldB = r.node(16);
        var seq = h.startSequence();
        reload(r, oldA, oldB, seq);
        seq.thenExecute(() -> {
                    h.assertTrue(r.open(), "the valve shut over the reload");
                    h.assertTrue(r.net(12) == r.net(16), "the sides parted over the reload");
                    h.assertValueEqual(r.net(12).members().size(), 10, "members after the reload");
                    r.a.close(r.net(12).gas().moles(N2), 50.0, "N2 after the reload");
                    r.a.close(r.net(12).gas().thermalEnergy(), energy, "the energy after the reload");
                    r.turn();
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(r.net(12) != r.net(16), "the shut valve didn't part its sides");
                    r.a.close(r.net(12).gas().moles(N2), 50.0 * Row.WEST_VOLUME / Row.VOLUME, "the west share");
                    r.a.close(r.net(16).gas().moles(N2), 50.0 * Row.EAST_VOLUME / Row.VOLUME, "the east share");
                    r.a.close(
                            r.net(Row.VALVE).gas().moles(N2),
                            50.0 * PipeVolumes.VALVE / Row.VOLUME,
                            "the valve's share");
                    r.done();
                })
                .thenSucceed();
    }

    /**
     * A valve opened while its far side's chunk is away (out of full status, its nodes in memory with
     * their network) joins that side when the chunk returns, with no gas lost and nothing loading
     * the chunk meanwhile.
     */
    @GameTest(template = "empty", batch = CHUNK_BATCH, timeoutTicks = 600)
    public static void cont_075_a_valve_opened_while_its_far_side_is_away_joins_it(GameTestHelper h) {
        Row r = new Row(h, 121, false);
        r.net(12).gas().add(new GasParcel(Map.of(N2, 40.0), T));
        r.net(16).gas().add(new GasParcel(Map.of(O2, 10.0), T));
        PipeNetwork[] east = new PipeNetwork[1];
        h.startSequence()
                .thenIdle(2) // the game's own onLoad calls dissolve the networks built above
                .thenExecute(() -> {
                    east[0] = r.net(16);
                    r.hold(r.chunkB, false);
                })
                .thenWaitUntil(() -> h.assertFalse(r.full(r.chunkB), "chunk B is still loaded"))
                .thenExecute(() -> {
                    r.turn();
                    // Checked now: chunk B would be back for only two game ticks, and whether a network
                    // is built in them depends on where the devices fall in the atmos period.
                    h.assertFalse(r.full(r.chunkB), "turning the valve loaded chunk B");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertFalse(r.full(r.chunkB), "chunk B came back by itself");
                    r.a.close(r.net(12).gas().moles(N2), 40.0, "N2 with chunk B away");
                    r.a.close(r.net(12).gas().moles(O2), 0.0, "O2 from chunk B, away");
                    h.assertTrue(east[0].valid(), "chunk B's network dissolved while it was away");
                    r.a.close(east[0].gas().moles(O2), 10.0, "O2 in chunk B, away");
                    r.hold(r.chunkB, true);
                })
                .thenWaitUntil(() -> h.assertTrue(r.full(r.chunkB), "chunk B hasn't returned"))
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(r.net(12) == r.net(16), "the open valve didn't join the side that returned");
                    r.a.close(r.net(12).volume(), Row.VOLUME, "the joined volume");
                    r.a.close(r.net(12).gas().moles(N2), 40.0, "N2 joined");
                    r.a.close(r.net(12).gas().moles(O2), 10.0, "O2 joined");
                    r.done();
                })
                .thenSucceed();
    }
}
