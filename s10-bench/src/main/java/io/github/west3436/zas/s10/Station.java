package io.github.west3436.zas.s10;

import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlock;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlock;
import io.github.west3436.zas.atmos.vent.VentBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * S-10's station (00-overview): 100 rooms of station wall in the vacuum dimension, holding 40,000
 * cells of air, with 20 pipe networks and 300 powered devices.
 *
 * <ul>
 *   <li>The big room: 20 × 20 × 20 inside, 8,000 cells, hot and burning.
 *   <li>99 rooms of 8 × 5 × 8 inside (320 cells), in rows of 10 sharing walls; the last is 8 × 10
 *       × 8 (640). 98 × 320 + 640 + 8,000 = 40,000.
 *   <li>Rows 0 to 7 and the big room are the main station, kept loaded. Rows 8 and 9 are a wing
 *       400 blocks east, loaded only by the player there, so it pauses when that player goes.
 *   <li>Each room has a generator under an area air controller on its north wall, a fire alarm on
 *       the same wall, a vent in its floor over its row's supply pipe and a scrubber in its floor
 *       over its row's waste pipe: 100 controllers, and 100 each of vents, scrubbers and fire
 *       alarms, the 300 powered devices. Each row's two pipes are capped at both ends; row 0's run
 *       on under the big room. 10 rows, 20 networks.
 * </ul>
 *
 * Every wall is station wall, which conducts no heat (CONT-129), so rooms don't warm each other.
 */
final class Station {
    static final int FLOOR = 64;
    static final int PIPE_Y = FLOOR - 1;
    static final int ROOMS_PER_ROW = 10;
    static final int GRID_X = 30;
    static final int WING_SHIFT = 400;
    static final int WING_FIRST_ROW = 8;
    static final int BIG = 99;

    /** One room: its walls run from (x0, FLOOR, z0) to (x0 + w + 1, FLOOR + h + 1, z0 + w + 1). */
    record Room(int slot, int row, int x0, int z0, int w, int h, boolean wing) {
        int cells() {
            return w * w * h;
        }

        int x1() {
            return x0 + w + 1;
        }

        int z1() {
            return z0 + w + 1;
        }

        int ceiling() {
            return FLOOR + h + 1;
        }

        BlockPos centre() {
            return new BlockPos(x0 + 1 + w / 2, FLOOR + 1 + h / 2, z0 + 1 + w / 2);
        }

        Vec3 centrePoint() {
            return Vec3.atCenterOf(centre());
        }

        BlockPos vent() {
            return new BlockPos(x0 + 2, FLOOR, z0 + 2);
        }

        BlockPos scrubber() {
            return new BlockPos(x0 + 5, FLOOR, z0 + 5);
        }

        BlockPos generator() {
            return new BlockPos(x0 + 1, FLOOR + 1, z0 + 1);
        }

        BlockPos controller() {
            return new BlockPos(x0 + 1, FLOOR + 2, z0 + 1);
        }

        BlockPos alarm() {
            return new BlockPos(x0 + 6, FLOOR + 2, z0 + 1);
        }

        /** Where a player or a cow stands: on the floor, away from the devices. */
        Vec3 standing(int i) {
            return new Vec3(x0 + 3.5 + (i % 3), FLOOR + 1, z0 + 6.5 - (i / 3));
        }

        /** The wall block a breach takes out: the middle of the north wall, at head height. */
        BlockPos breach() {
            return new BlockPos(x0 + 4, FLOOR + 3, z0);
        }

        String label() {
            return slot == BIG ? "big room" : "room " + slot;
        }
    }

    final List<Room> rooms = new ArrayList<>();
    final Room big;

    Station() {
        for (int slot = 0; slot < 99; slot++) {
            int row = slot / ROOMS_PER_ROW;
            int col = slot % ROOMS_PER_ROW;
            boolean wing = row >= WING_FIRST_ROW;
            int x0 = GRID_X + col * 9 + (wing ? WING_SHIFT : 0);
            rooms.add(new Room(slot, row, x0, row * 9, 8, slot == 98 ? 10 : 5, wing));
        }
        big = new Room(BIG, 0, 0, 0, 20, 20, false);
        rooms.add(big);
    }

    Room room(int slot) {
        return slot == BIG ? big : rooms.get(slot);
    }

    int cells() {
        return rooms.stream().mapToInt(Room::cells).sum();
    }

    /** The chunks the main station stands in (with its pipes), kept loaded throughout. */
    Set<Long> mainChunks() {
        return chunks(false);
    }

    /** The wing's chunks. */
    Set<Long> wingChunks() {
        return chunks(true);
    }

    private Set<Long> chunks(boolean wing) {
        Set<Long> out = new TreeSet<>();
        for (Room r : rooms) {
            if (r.wing() != wing) {
                continue;
            }
            for (int x = (r.x0() - 1) >> 4; x <= (r.x1() + 1) >> 4; x++) {
                for (int z = (r.z0() - 1) >> 4; z <= (r.z1() + 1) >> 4; z++) {
                    out.add(ChunkPos.asLong(x, z));
                }
            }
        }
        return out;
    }

    /** Builds everything into loaded chunks. Server thread. */
    void build(ServerLevel level) {
        BlockState wall = AtmosBlocks.STATION_WALL.get().defaultBlockState();
        for (Room r : rooms) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                for (int y = FLOOR; y <= r.ceiling(); y++) {
                    for (int z = r.z0(); z <= r.z1(); z++) {
                        boolean shell = x == r.x0()
                                || x == r.x1()
                                || y == FLOOR
                                || y == r.ceiling()
                                || z == r.z0()
                                || z == r.z1();
                        if (shell) {
                            set(level, new BlockPos(x, y, z), wall);
                        }
                    }
                }
            }
        }
        List<BlockPos> pipes = new ArrayList<>();
        for (int row = 0; row < 10; row++) {
            int finalRow = row;
            List<Room> inRow = rooms.stream()
                    .filter(r -> r.row() == finalRow && r.slot() != BIG)
                    .toList();
            int first = row == 0 ? big.vent().getX() : inRow.getFirst().vent().getX();
            int last = inRow.getLast().scrubber().getX();
            for (int line : new int[] {
                inRow.getFirst().vent().getZ(), inRow.getFirst().scrubber().getZ()
            }) {
                for (int x = first; x <= last; x++) {
                    BlockPos p = new BlockPos(x, PIPE_Y, line);
                    set(level, p, AtmosBlocks.PIPE.get().defaultBlockState());
                    pipes.add(p);
                }
                set(level, new BlockPos(first - 1, PIPE_Y, line), cap(Direction.EAST));
                set(level, new BlockPos(last + 1, PIPE_Y, line), cap(Direction.WEST));
            }
        }
        for (Room r : rooms) {
            set(level, r.vent(), AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.UP));
            set(
                    level,
                    r.scrubber(),
                    AtmosBlocks.SCRUBBER.get().defaultBlockState().setValue(ScrubberBlock.FACING, Direction.UP));
            set(level, r.generator(), AtmosBlocks.GENERATOR.get().defaultBlockState());
            set(
                    level,
                    r.controller(),
                    AtmosBlocks.AREA_AIR_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(AreaAirControllerBlock.FACING, Direction.SOUTH));
            set(
                    level,
                    r.alarm(),
                    AtmosBlocks.FIRE_ALARM.get().defaultBlockState().setValue(FireAlarmBlock.FACING, Direction.SOUTH));
            if (level.getBlockEntity(r.generator()) instanceof GeneratorBlockEntity g) {
                g.fuel().insertItem(0, new ItemStack(Items.COAL, 64), false);
            } else {
                throw new IllegalStateException("no generator in " + r.label());
            }
        }
        // Each pipe's arms from its neighbours, now they are all placed.
        for (BlockPos p : pipes) {
            BlockState s = level.getBlockState(p);
            level.setBlock(p, Block.updateFromNeighbourShapes(s, level, p), Block.UPDATE_CLIENTS);
        }
    }

    private static BlockState cap(Direction toward) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, toward);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
