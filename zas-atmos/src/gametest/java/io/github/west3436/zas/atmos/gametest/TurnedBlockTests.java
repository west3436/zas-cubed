package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.ss13.api.label.Labelled;
import io.github.west3436.ss13.device.DeviceTicker;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.relocation.Destination;
import io.github.west3436.zas.api.relocation.RelocationOutcome;
import io.github.west3436.zas.api.relocation.RelocationResult;
import io.github.west3436.zas.api.relocation.RelocationTicket;
import io.github.west3436.zas.api.relocation.Relocations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.PortedBlock;
import io.github.west3436.zas.atmos.inline.SidePortBlock;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Blocks turn with what they are part of (CONT-156): a structure placed turned, a contraption, or a
 * ship whose blocks are put back into the world the way it was turned. Before, a vent, scrubber,
 * passive vent, cap, canister, recharger, portable, heat exchanger, radiator adaptor and the pipes'
 * arms kept the way they faced, and an upright filter or mixer couldn't take its side port round, so
 * a vent on a ship taken apart a quarter turn round faced away from its pipe.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurnedBlockTests {
    private static final SourceId MOVER = SourceId.of(ZasAtmosMod.MOD_ID, "turn_test");

    private TurnedBlockTests() {}

    /**
     * Every state of every block of the two mods, turned each way and mirrored each way: each way it
     * faces turns or mirrors with it, its pipe faces and ports with them, and turning back gives the
     * state it was.
     */
    @GameTest(template = "empty")
    public static void cont_156_every_block_turns_with_what_it_is_part_of(GameTestHelper h) {
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        BlockState radiatorPipe = AtmosBlocks.RADIATOR_PIPE.get().defaultBlockState();
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            String ns = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
            if (!(ns.equals("zas") || ns.equals(ZasAtmosMod.MOD_ID))
                    || block.getClass().getPackageName().equals(TurnedBlockTests.class.getPackageName())) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (block instanceof SidePortBlock && SidePortBlock.settled(state) != state) {
                    continue; // a spin facing sideways, which nothing makes
                }
                checked++;
                for (Rotation r : Rotation.values()) {
                    check(state, state.rotate(r), r::rotate, r.toString(), true, pipe, radiatorPipe, wrong);
                    BlockState back = state.rotate(r).rotate(inverse(r));
                    if (back != state) {
                        wrong.add(state + " turned " + r + " and back is " + back);
                    }
                }
                for (Mirror m : Mirror.values()) {
                    check(state, state.mirror(m), m::mirror, m.toString(), false, pipe, radiatorPipe, wrong);
                    BlockState back = state.mirror(m).mirror(m);
                    if (back != state) {
                        wrong.add(state + " mirrored " + m + " twice is " + back);
                    }
                }
            }
        }
        h.assertTrue(checked > 100, "only " + checked + " states checked");
        h.assertTrue(
                wrong.isEmpty(),
                wrong.size() + " turns wrong, in "
                        + wrong.stream()
                                .map(w -> w.split("[\\[ ]")[0])
                                .distinct()
                                .toList()
                        + "; first " + String.join("; ", wrong.subList(0, Math.min(8, wrong.size()))));
        h.succeed();
    }

    private interface Way {
        Direction apply(Direction d);
    }

    private static void check(
            BlockState state,
            BlockState turned,
            Way way,
            String how,
            boolean inOrder,
            BlockState pipe,
            BlockState radiatorPipe,
            List<String> wrong) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof DirectionProperty facing) {
                Direction want = way.apply(state.getValue(facing));
                if (facing.getPossibleValues().contains(want) && turned.getValue(facing) != want) {
                    wrong.add(state + " " + how + ": " + p.getName() + " " + turned.getValue(facing) + ", not " + want);
                }
            }
        }
        for (Map.Entry<Direction, BooleanProperty> arm : PipeBlock.CONNECTIONS.entrySet()) {
            if (state.hasProperty(arm.getValue())) {
                BooleanProperty to = PipeBlock.CONNECTIONS.get(way.apply(arm.getKey()));
                if (turned.getValue(to) != state.getValue(arm.getValue())) {
                    wrong.add(state + " " + how + ": the arm " + arm.getKey() + " didn't go " + to.getName());
                }
            }
        }
        if (state.getBlock() instanceof PipeConnector connector) {
            for (Direction d : Direction.values()) {
                for (BlockState neighbour : new BlockState[] {pipe, radiatorPipe}) {
                    boolean was = connector.pipeConnects(state, d, neighbour);
                    boolean is = connector.pipeConnects(turned, way.apply(d), neighbour);
                    if (was != is) {
                        wrong.add(state + " " + how + ": its pipe face " + d + " is " + is + " at " + way.apply(d));
                    }
                }
            }
        }
        if (state.getBlock() instanceof PortedBlock ported) {
            List<Direction> want =
                    ported.portFaces(state).stream().map(way::apply).toList();
            // Turned, each port is where it went. Mirrored, its ports are on the mirrored faces, but
            // a device with no other hand, such as the electrolyzer, keeps which gas goes where.
            List<Direction> is = ported.portFaces(turned);
            if (inOrder ? !is.equals(want) : !(is.containsAll(want) && want.containsAll(is))) {
                wrong.add(state + " " + how + ": ports " + ported.portFaces(turned) + ", not " + want);
            }
        }
    }

    private static Rotation inverse(Rotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> r;
        };
    }

    // ---- A small station, moved turned --------------------------------------------------------

    /**
     * A small station, a quarter turn round: moved as a mover mod moves blocks, turning each block
     * with {@link BlockState#rotate}, its vent faces where the station turned it and still joins its
     * pipe, and so does everything else that faced a way.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cont_156_a_station_moved_a_quarter_turn_keeps_its_pipes(GameTestHelper h) {
        moveTurned(h, 6400, Rotation.CLOCKWISE_90);
    }

    /** The same, half a turn round. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cont_156_a_station_moved_half_a_turn_keeps_its_pipes(GameTestHelper h) {
        moveTurned(h, 6401, Rotation.CLOCKWISE_180);
    }

    /** The same, three quarters round, as a quarter turn the other way. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cont_156_a_station_moved_three_quarters_round_keeps_its_pipes(GameTestHelper h) {
        moveTurned(h, 6402, Rotation.COUNTERCLOCKWISE_90);
    }

    /** The station's cells, from its corner: a stone floor at y 0 and its parts on it. */
    static Map<BlockPos, BlockState> station() {
        Map<BlockPos, BlockState> s = new LinkedHashMap<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                s.put(new BlockPos(x, 0, z), Area.STONE);
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

    private static void moveTurned(GameTestHelper h, int area, Rotation rotation) {
        Area a = new Area(h, area);
        a.clear(0, 0, 14, 14);
        BlockPos corner = new BlockPos(2, 0, 2);
        station().forEach((p, state) -> a.place(corner.getX() + p.getX(), p.getY(), corner.getZ() + p.getZ(), state));
        BlockPos from = a.abs(corner.getX(), 0, corner.getZ());
        ((Labelled) a.level.getBlockEntity(from.offset(4, 1, 2))).setLabel("Cabin");
        Station before = Station.read(h, a.level, from, p -> p, "before");

        // Moved 8 blocks east, turned about the station's corner.
        BlockPos to = from.offset(8, 0, 0);
        Destination d = new Destination(a.level, from, to, rotation);
        BoundingBox box = BoundingBox.fromCorners(from, from.offset(4, 3, 4));
        RelocationResult result = move(a, box, d);
        h.assertValueEqual(result.outcome(), RelocationOutcome.MOVED, "the move");

        Station after = Station.read(h, a.level, from, d::map, rotation.toString());
        before.sameAs(h, after, rotation);
        h.succeed();
    }

    /** Moves a box as a mover mod does, each block turned as the game turns one. */
    private static RelocationResult move(Area a, BoundingBox box, Destination d) {
        ServerLevel level = a.level;
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> data = new LinkedHashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(
                new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
            BlockPos at = p.immutable();
            states.put(at, level.getBlockState(at));
            BlockEntity be = level.getBlockEntity(at);
            if (be != null) {
                data.put(at, be.saveWithoutMetadata(level.registryAccess()));
            }
        }
        RelocationTicket ticket = Relocations.begin(level, box, MOVER);
        for (BlockPos p : states.keySet()) {
            level.setBlock(p, Area.AIR, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
        states.forEach((p, state) -> d.level().setBlockAndUpdate(d.map(p), state.rotate(d.rotation())));
        data.forEach((p, tag) -> {
            BlockEntity be = d.level().getBlockEntity(d.map(p));
            if (be != null) {
                be.loadWithComponents(tag, level.registryAccess());
                be.setChanged();
                if (be instanceof AtmosDevice device && !DeviceTicker.isScheduled(device)) {
                    device.onLoad(); // the game would at the start of the next tick
                }
            }
        });
        return a.settle(Relocations.complete(ticket, d));
    }

    /** What a station's parts face and join, read from the world. */
    record Station(Map<BlockPos, BlockState> states, List<String> joins, Map<String, Boolean> together, String label) {

        /**
         * Reads the station whose corner was at {@code corner}, its cells found where {@code at}
         * puts them now.
         */
        static Station read(
                GameTestHelper h, ServerLevel level, BlockPos corner, UnaryOperator<BlockPos> at, String when) {
            Map<BlockPos, BlockState> states = new LinkedHashMap<>();
            for (BlockPos p : station().keySet()) {
                states.put(p, level.getBlockState(at.apply(corner.offset(p))));
                h.assertTrue(
                        states.get(p).getBlock() == station().get(p).getBlock(),
                        when + ": " + p.toShortString() + " is " + states.get(p));
            }
            // Which neighbours offer each other a pipe connection, by the station's own cells.
            List<String> joins = new ArrayList<>();
            for (BlockPos p : states.keySet()) {
                for (Direction face : Direction.values()) {
                    BlockPos q = at.apply(corner.offset(p)).relative(face);
                    if (PipeConnector.connected(states.get(p), face, level.getBlockState(q))) {
                        joins.add(p.toShortString() + " to the " + face);
                    }
                }
            }
            Map<String, Boolean> together = new LinkedHashMap<>();
            PipeNetwork left = node(level, at.apply(corner.offset(1, 1, 2)));
            PipeNetwork right = node(level, at.apply(corner.offset(3, 1, 2)));
            InlineDeviceEntity pump = (InlineDeviceEntity) level.getBlockEntity(at.apply(corner.offset(2, 1, 2)));
            FilterBlockEntity filter = (FilterBlockEntity) level.getBlockEntity(at.apply(corner.offset(1, 2, 2)));
            together.put("the vent and its pipe", node(level, at.apply(corner.offset(4, 1, 2))) == right);
            together.put("the scrubber and its pipe", node(level, at.apply(corner.offset(0, 1, 2))) == left);
            together.put("the passive vent and its pipe", node(level, at.apply(corner.offset(1, 1, 3))) == left);
            together.put("the pump's inlet and its pipe", pump.port(0).network() == left);
            together.put("the pump's outlet and its pipe", pump.port(1).network() == right);
            together.put("the filter's inlet and its pipe", filter.port(0).network() == left);
            together.put(
                    "the filter's outlet and its cap",
                    filter.port(1).network() == node(level, at.apply(corner.offset(1, 3, 2))));
            together.put(
                    "the filter's side port and its cap",
                    filter.port(SidePortEntity.SIDE).network() == node(level, at.apply(corner.offset(2, 2, 2))));
            String label = ((Labelled) level.getBlockEntity(at.apply(corner.offset(4, 1, 2)))).label();
            return new Station(states, joins, together, label);
        }

        private static PipeNetwork node(ServerLevel level, BlockPos pos) {
            return ((PipeNodeEntity) level.getBlockEntity(pos)).network();
        }

        /** Checks the station, moved {@code r} round, faces and joins as it did, turned. */
        void sameAs(GameTestHelper h, Station after, Rotation r) {
            together.forEach((what, was) -> h.assertTrue(was, "before the move, " + what + " aren't joined"));
            h.assertTrue(joins.size() >= 16, "before the move, only " + joins + " join");
            after.together.forEach((what, is) -> h.assertTrue(is, r + ": " + what + " aren't joined"));
            List<String> want = new ArrayList<>();
            for (String j : joins) {
                // Turned, a cell's join on a face is on the turned face.
                String[] parts = j.split(" to the ");
                want.add(parts[0] + " to the " + r.rotate(Direction.byName(parts[1])));
            }
            List<String> got = after.joins.stream().sorted().toList();
            h.assertValueEqual(got, want.stream().sorted().toList(), r + ": the pipe connections");
            states.forEach((p, was) -> {
                BlockState is = after.states.get(p);
                for (Property<?> prop : was.getProperties()) {
                    if (prop instanceof DirectionProperty facing) {
                        h.assertValueEqual(
                                is.getValue(facing),
                                r.rotate(was.getValue(facing)),
                                r + ": " + is.getBlock().getName().getString() + "'s " + prop.getName());
                    }
                }
                if (is.getBlock() instanceof SidePortBlock) {
                    h.assertValueEqual(
                            SidePortBlock.sideFace(is), r.rotate(SidePortBlock.sideFace(was)), r + ": the side port");
                    h.assertValueEqual(
                            is.getValue(SidePortBlock.MIRRORED),
                            was.getValue(SidePortBlock.MIRRORED),
                            r + ": the filter's hand");
                    h.assertValueEqual(is.getValue(InlineDeviceBlock.FACING), Direction.UP, r + ": the filter");
                }
                if (is.getBlock() instanceof PipeBlock) {
                    for (Direction arm : Direction.values()) {
                        h.assertValueEqual(
                                is.getValue(PipeBlock.CONNECTIONS.get(r.rotate(arm))),
                                was.getValue(PipeBlock.CONNECTIONS.get(arm)),
                                r + ": the arm of the pipe at " + p.toShortString() + " that went " + arm);
                    }
                }
            });
            h.assertValueEqual(after.label, label, r + ": the vent's label");
        }
    }
}
