package io.github.west3436.zas.compat.mi.gametest;

import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.simulation.AtmosService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A GameTest's own place in the level, far from the framework's test areas, with ZAS³ pipes and
 * Modern Industrialization's tanks in it. Spot {@code index} is at x = 8192 + 64 × index, y = 100,
 * z = 40960. Devices are stepped and atmos ticks run with the framework's and the content's own
 * hooks, {@link DeviceTicker#stepNow} and {@link AtmosService#tickNow}, which GameTests may use
 * and the adapter never does; so each test runs within one game tick.
 */
final class Spot {
    static final double REL = 1e-9;

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;

    Spot(GameTestHelper h, int index) {
        this.h = h;
        this.level = h.getLevel();
        this.origin = new BlockPos(8192 + 64 * index, 100, 40960);
        clear();
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    /** Sets (0, 0, 0) to (7, 3, 3) back to air. */
    void clear() {
        for (BlockPos p : BlockPos.betweenClosed(abs(0, 0, 0), abs(7, 3, 3))) {
            if (!level.getBlockState(p).isAir()) {
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * Places a block as a player would, with its shape worked out from its neighbours, and loads a
     * device's block entity now: the game would at the start of the next tick, and these tests run
     * within one.
     */
    void place(int x, int y, int z, BlockState state) {
        BlockPos pos = abs(x, y, z);
        level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(state, level, pos));
        if (level.getBlockEntity(pos) instanceof AtmosDevice d && !DeviceTicker.isScheduled(d)) {
            d.onLoad();
        }
    }

    /** A cap at x - 1 facing east and a pipe at x, so the pipe's arm to whatever is at x + 1 is its second. */
    PipeBlockEntity cappedPipe(int x, int y, int z) {
        place(
                x - 1,
                y,
                z,
                AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.EAST));
        place(x, y, z, AtmosBlocks.PIPE.get().defaultBlockState());
        if (!(level.getBlockEntity(abs(x, y, z)) instanceof PipeBlockEntity pipe)) {
            throw new GameTestAssertException("the pipe has no block entity");
        }
        return pipe;
    }

    /** Whether the pipe at a place has its arm out to a face. */
    boolean arm(int x, int y, int z, Direction face) {
        return level.getBlockState(abs(x, y, z)).getValue(PipeBlock.CONNECTIONS.get(face));
    }

    /** One of Modern Industrialization's blocks, by its path. */
    BlockState block(String path) {
        Block block = BuiltInRegistries.BLOCK.get(MiGateTests.mi(path));
        h.assertTrue(block != Blocks.AIR, "Modern Industrialization registers no block " + path);
        return block.defaultBlockState();
    }

    /** Places one of Modern Industrialization's tanks and returns its fluid handler. */
    IFluidHandler tank(int x, int y, int z, String path) {
        place(x, y, z, block(path));
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, abs(x, y, z), Direction.WEST);
        if (handler == null) {
            throw new GameTestAssertException(path + " offers no fluid handler");
        }
        return handler;
    }

    /** One of Modern Industrialization's fluids, by its path. */
    Fluid fluid(String path) {
        Fluid fluid = BuiltInRegistries.FLUID.get(MiGateTests.mi(path));
        h.assertTrue(fluid != Fluids.EMPTY, "Modern Industrialization registers no fluid " + path);
        return fluid;
    }

    /** Steps every ready device of the level once, then runs one atmos tick. */
    void step() {
        DeviceTicker.stepNow(level);
        AtmosService service = AtmosService.current();
        if (service == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        service.tickNow(level);
    }

    void close(double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= REL * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }
}
