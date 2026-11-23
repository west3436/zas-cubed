package io.github.west3436.zas.compat.sable.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.SidePortBlock;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
import io.github.west3436.zas.atmos.label.Labelled;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.valve.ValveBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A ship's parts come back turned with it (CONT-156, COMPAT-100). A small station of pipes and
 * devices is assembled into a real sub-level, as {@code /sable assemble area} does, and taken apart
 * a quarter, half and three quarters round, as Create Aeronautics takes a ship apart the way it was
 * turned: every part faces where the ship turned it, its pipes still join it, and its networks are
 * whole. Before, a vent came back facing the way it faced on board, away from its pipe.
 */
@GameTestHolder(SableGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SableTurnTests {
    private SableTurnTests() {}

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cont_156_a_ship_taken_apart_a_quarter_round_keeps_its_pipes(GameTestHelper h) {
        takenApart(h, 12, Rotation.CLOCKWISE_90);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cont_156_a_ship_taken_apart_half_round_keeps_its_pipes(GameTestHelper h) {
        takenApart(h, 13, Rotation.CLOCKWISE_180);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cont_156_a_ship_taken_apart_three_quarters_round_keeps_its_pipes(GameTestHelper h) {
        takenApart(h, 14, Rotation.COUNTERCLOCKWISE_90);
    }

    private static void takenApart(GameTestHelper h, int index, Rotation rotation) {
        Deck d = new Deck(h, h.getLevel(), index);
        d.clear();
        station().forEach((p, state) -> {
            BlockPos at = d.abs(p.getX(), p.getY(), p.getZ());
            d.level.setBlockAndUpdate(at, Block.updateFromNeighbourShapes(state, d.level, at));
            loaded(d.level, at);
        });
        ((Labelled) d.level.getBlockEntity(d.abs(4, 1, 2))).setLabel("Cabin");
        Station before = Station.read(h, d.level, p -> d.abs(p.getX(), p.getY(), p.getZ()), "before");

        ServerSubLevel sub = d.assemble();
        UnaryOperator<BlockPos> onBoard = p -> Deck.onBoard(sub, p.getX(), p.getY(), p.getZ());
        station().keySet().forEach(p -> loaded(d.level, onBoard.apply(p)));
        d.ticks(3);
        Station.read(h, d.level, onBoard, "on board").sameAs(h, before, Rotation.NONE, "on board");

        // Taken apart as Simulated's assembler does: the transform's angle is its rotation's, the
        // other way round, and the blocks land where the box stood.
        BlockPos plotAnchor = Deck.onBoard(sub, 0, 0, 0);
        int angle = rotation == Rotation.NONE ? 0 : 4 - rotation.ordinal();
        SubLevelAssemblyHelper.AssemblyTransform trial =
                new SubLevelAssemblyHelper.AssemblyTransform(plotAnchor, d.abs(0, 0, 0), angle, rotation, d.level);
        BlockPos min = null;
        for (BlockPos p : BlockPos.betweenClosed(plotAnchor, Deck.onBoard(sub, 4, 4, 4))) {
            BlockPos q = trial.apply(p.immutable());
            min = min == null
                    ? q
                    : new BlockPos(
                            Math.min(min.getX(), q.getX()),
                            Math.min(min.getY(), q.getY()),
                            Math.min(min.getZ(), q.getZ()));
        }
        SubLevelAssemblyHelper.AssemblyTransform back = new SubLevelAssemblyHelper.AssemblyTransform(
                plotAnchor, d.abs(0, 0, 0).subtract(min.subtract(d.abs(0, 0, 0))), angle, rotation, d.level);
        Map<BlockPos, BlockPos> landed = new LinkedHashMap<>();
        for (BlockPos p : station().keySet()) {
            landed.put(p, back.apply(onBoard.apply(p)));
        }
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(plotAnchor, Deck.onBoard(sub, 4, 4, 4))) {
            if (!d.level.getBlockState(p).isAir()) {
                blocks.add(p.immutable());
            }
        }
        SubLevelAssemblyHelper.moveBlocks(d.level, back, blocks);
        d.container().processSubLevelRemovals();
        landed.values().forEach(p -> loaded(d.level, p));
        d.ticks(3);

        Station after = Station.read(h, d.level, landed::get, rotation + ", back in the world");
        after.sameAs(h, before, rotation, "back in the world " + rotation);
        h.succeed();
    }

    /** A device the game would start at the next tick, started now. */
    private static void loaded(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AtmosDevice device && !DeviceTicker.isScheduled(device)) {
            device.onLoad();
        }
    }

    /** The station's cells, from its corner: a stone floor at y 0 and its parts on it. */
    static Map<BlockPos, BlockState> station() {
        Map<BlockPos, BlockState> s = new LinkedHashMap<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                s.put(new BlockPos(x, 0, z), Deck.STONE);
            }
        }
        s.put(new BlockPos(0, 1, 2), facing(AtmosBlocks.SCRUBBER.get(), Direction.WEST));
        s.put(new BlockPos(1, 1, 2), AtmosBlocks.PIPE.get().defaultBlockState());
        s.put(new BlockPos(2, 1, 2), facing(AtmosBlocks.PRESSURE_PUMP.get(), Direction.EAST));
        s.put(new BlockPos(3, 1, 2), AtmosBlocks.PIPE.get().defaultBlockState());
        s.put(new BlockPos(4, 1, 2), facing(AtmosBlocks.VENT.get(), Direction.EAST));
        s.put(
                new BlockPos(3, 1, 1),
                AtmosBlocks.MANUAL_VALVE.get().defaultBlockState().setValue(ValveBlock.AXIS, Direction.Axis.Z));
        s.put(new BlockPos(3, 1, 0), facing(AtmosBlocks.PIPE_CAP.get(), Direction.SOUTH));
        s.put(new BlockPos(1, 2, 2), facing(AtmosBlocks.FILTER.get(), Direction.UP));
        s.put(new BlockPos(1, 3, 2), facing(AtmosBlocks.PIPE_CAP.get(), Direction.DOWN));
        s.put(new BlockPos(2, 2, 2), facing(AtmosBlocks.PIPE_CAP.get(), Direction.WEST));
        s.put(new BlockPos(1, 1, 3), facing(AtmosBlocks.PASSIVE_VENT.get(), Direction.SOUTH));
        s.put(
                new BlockPos(4, 1, 3),
                AtmosBlocks.GAUGE
                        .get()
                        .defaultBlockState()
                        .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
                        .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.SOUTH));
        s.put(new BlockPos(0, 1, 4), facing(AtmosBlocks.CANISTER.get(), Direction.EAST));
        s.put(new BlockPos(2, 1, 4), facing(AtmosBlocks.PORTABLE_PUMP.get(), Direction.WEST));
        s.put(new BlockPos(4, 1, 4), facing(AtmosBlocks.RECHARGER.get(), Direction.NORTH));
        return s;
    }

    private static BlockState facing(Block block, Direction way) {
        BlockState state = block.defaultBlockState();
        for (Property<?> p : state.getProperties()) {
            if (p instanceof DirectionProperty facing && facing.getName().equals("facing")) {
                return state.setValue(facing, way);
            }
        }
        throw new IllegalArgumentException(block + " has no facing");
    }

    /** What a station's parts face and join, read from the level. */
    record Station(Map<BlockPos, BlockState> states, List<String> joins, Map<String, Boolean> together, String label) {
        /** Reads the station, each of its cells where {@code at} puts it. */
        static Station read(GameTestHelper h, ServerLevel level, UnaryOperator<BlockPos> at, String when) {
            Map<BlockPos, BlockState> want = station();
            Map<BlockPos, BlockState> states = new LinkedHashMap<>();
            for (BlockPos p : want.keySet()) {
                states.put(p, level.getBlockState(at.apply(p)));
                h.assertTrue(
                        states.get(p).getBlock() == want.get(p).getBlock(),
                        when + ": " + p.toShortString() + " is " + states.get(p));
            }
            // Which neighbours offer each other a pipe connection, by the station's own cells.
            List<String> joins = new ArrayList<>();
            for (BlockPos p : states.keySet()) {
                for (Direction face : Direction.values()) {
                    if (PipeConnector.connected(
                            states.get(p), face, level.getBlockState(at.apply(p).relative(face)))) {
                        joins.add(p.toShortString() + " to the " + face);
                    }
                }
            }
            Map<String, Boolean> together = new LinkedHashMap<>();
            PipeNetwork left = node(level, at.apply(new BlockPos(1, 1, 2)));
            PipeNetwork right = node(level, at.apply(new BlockPos(3, 1, 2)));
            InlineDeviceEntity pump = (InlineDeviceEntity) level.getBlockEntity(at.apply(new BlockPos(2, 1, 2)));
            FilterBlockEntity filter = (FilterBlockEntity) level.getBlockEntity(at.apply(new BlockPos(1, 2, 2)));
            together.put("the vent and its pipe", node(level, at.apply(new BlockPos(4, 1, 2))) == right);
            together.put("the scrubber and its pipe", node(level, at.apply(new BlockPos(0, 1, 2))) == left);
            together.put("the passive vent and its pipe", node(level, at.apply(new BlockPos(1, 1, 3))) == left);
            together.put("the pump's inlet and its pipe", pump.port(0).network() == left);
            together.put("the pump's outlet and its pipe", pump.port(1).network() == right);
            together.put("the filter's inlet and its pipe", filter.port(0).network() == left);
            together.put(
                    "the filter's outlet and its cap",
                    filter.port(1).network() == node(level, at.apply(new BlockPos(1, 3, 2))));
            together.put(
                    "the filter's side port and its cap",
                    filter.port(SidePortEntity.SIDE).network() == node(level, at.apply(new BlockPos(2, 2, 2))));
            String label = ((Labelled) level.getBlockEntity(at.apply(new BlockPos(4, 1, 2)))).label();
            return new Station(states, joins, together, label);
        }

        private static PipeNetwork node(ServerLevel level, BlockPos pos) {
            return ((PipeNodeEntity) level.getBlockEntity(pos)).network();
        }

        /** Checks this station faces and joins as {@code before} did, turned {@code r} round. */
        void sameAs(GameTestHelper h, Station before, Rotation r, String when) {
            before.together.forEach((what, was) -> h.assertTrue(was, "before, " + what + " aren't joined"));
            h.assertTrue(before.joins.size() >= 16, "before, only " + before.joins + " join");
            together.forEach((what, is) -> h.assertTrue(is, when + ": " + what + " aren't joined"));
            List<String> want = new ArrayList<>();
            for (String j : before.joins) {
                String[] parts = j.split(" to the ");
                want.add(parts[0] + " to the " + r.rotate(Direction.byName(parts[1])));
            }
            h.assertValueEqual(
                    joins.stream().sorted().toList(), want.stream().sorted().toList(), when + ": the pipe connections");
            before.states.forEach((p, was) -> {
                BlockState is = states.get(p);
                for (Property<?> prop : was.getProperties()) {
                    if (prop instanceof DirectionProperty facing) {
                        h.assertValueEqual(
                                is.getValue(facing),
                                r.rotate(was.getValue(facing)),
                                when + ": " + is.getBlock().getName().getString() + "'s " + prop.getName());
                    }
                }
                if (is.getBlock() instanceof SidePortBlock) {
                    h.assertValueEqual(
                            SidePortBlock.sideFace(is),
                            r.rotate(SidePortBlock.sideFace(was)),
                            when + ": the filter's side port");
                    h.assertValueEqual(
                            is.getValue(SidePortBlock.MIRRORED),
                            was.getValue(SidePortBlock.MIRRORED),
                            when + ": the filter's hand");
                    h.assertValueEqual(is.getValue(InlineDeviceBlock.FACING), Direction.UP, when + ": the filter");
                }
                if (is.getBlock() instanceof PipeBlock) {
                    for (Direction arm : Direction.values()) {
                        h.assertValueEqual(
                                is.getValue(PipeBlock.CONNECTIONS.get(r.rotate(arm))),
                                was.getValue(PipeBlock.CONNECTIONS.get(arm)),
                                when + ": the arm of the pipe at " + p.toShortString() + " that went " + arm);
                    }
                }
            });
            h.assertValueEqual(label, before.label, when + ": the vent's label");
        }
    }
}
