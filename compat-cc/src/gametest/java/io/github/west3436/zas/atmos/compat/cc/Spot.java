package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.simulation.AtmosService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A GameTest's own place in the level, as the content mod's tests have: far from the framework's
 * and the content mod's test areas, and cleared when the test ends. Spot {@code index} is at x =
 * 8192 + 64 × index, z = 28672, so x = 0 is a chunk's first column and x = -1 is in the chunk before.
 */
final class Spot {
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;
    static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    static final double T = StandardAir.TEMPERATURE;
    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;

    Spot(GameTestHelper h, int index) {
        this.h = h;
        this.level = h.getLevel();
        this.origin = new BlockPos(8192 + 64 * index, 100, 28672);
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    Vec3 point(int x, int y, int z) {
        return Vec3.atCenterOf(abs(x, y, z));
    }

    void set(int x, int y, int z, BlockState state) {
        level.setBlockAndUpdate(abs(x, y, z), state);
    }

    /**
     * Places a block as a player would, its shape worked out from its neighbours, and returns its
     * block entity, loaded now: the game would load it at the start of the next tick, which a test
     * running within one never reaches. The later call changes nothing.
     */
    <T> T place(int x, int y, int z, BlockState state, Class<T> type) {
        BlockPos pos = abs(x, y, z);
        level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(state, level, pos));
        Object be = level.getBlockEntity(pos);
        if (!type.isInstance(be)) {
            throw new GameTestAssertException("no " + type.getSimpleName() + " at " + x + ", " + y + ", " + z);
        }
        if (be instanceof AtmosDevice device && !DeviceTicker.isScheduled(device)) {
            device.onLoad();
        }
        return type.cast(be);
    }

    /** Clears x -8 to 31, 7 high and 7 deep, to air. */
    void clear() {
        for (BlockPos p : BlockPos.betweenClosed(abs(-8, 0, 0), abs(31, 6, 6))) {
            level.setBlockAndUpdate(p, AIR);
        }
    }

    /** A hollow stone box from (x0, 0, 0) to (x0 + 4, 4, 4): a sealed 3 × 3 × 3 room. */
    void room(int x0) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, 0), abs(x0 + 4, 4, 4))) {
            int x = p.getX() - origin.getX() - x0;
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
            level.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
    }

    /**
     * Force-loads the spot's chunk and those around it, or releases them. A test that waits in real
     * time needs it: otherwise the chunk can unload mid-test, and nothing in it ticks.
     */
    void forceLoad(boolean on) {
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(cx + dx, cz + dz, on);
            }
        }
    }

    /**
     * Loads the spot's chunk and those around it now, for a test within one tick whose room is
     * opened: the air beyond the opening must be loaded to count as open.
     */
    void load() {
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk(cx + dx, cz + dz);
            }
        }
    }

    /** One atmos tick of the framework, now. Only tests may ask for one (CONT-095). */
    void tick() {
        AtmosService service = AtmosService.current();
        if (service == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        service.tickNow(level);
    }

    /** The peripheral CC: Tweaked finds at a block, as a computer or a modem beside it asks for it; null for none. */
    IPeripheral peripheral(BlockPos pos, Direction side) {
        return level.getCapability(PeripheralCapability.get(), pos, side);
    }

    /** The peripheral at a block, of the class its device has. */
    <P> P peripheral(int x, int y, int z, Class<P> type) {
        IPeripheral found = peripheral(abs(x, y, z), Direction.NORTH);
        if (!type.isInstance(found)) {
            throw new GameTestAssertException(
                    "no " + type.getSimpleName() + " at " + x + ", " + y + ", " + z + ": " + found);
        }
        return type.cast(found);
    }

    /** A call a computer makes of a peripheral. */
    @FunctionalInterface
    interface LuaCall {
        void run() throws LuaException;
    }

    /** The message of the Lua error a call ends in. */
    String refused(LuaCall call, String what) {
        try {
            call.run();
        } catch (LuaException e) {
            return e.getMessage();
        }
        throw new GameTestAssertException(what + " was no Lua error");
    }

    /** Runs a call that must not end in a Lua error. */
    void allowed(LuaCall call, String what) {
        try {
            call.run();
        } catch (LuaException e) {
            throw new GameTestAssertException(what + " was a Lua error: " + e.getMessage());
        }
    }

    /** Whether two values agree to a float's precision. */
    static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= 1e-5 * Math.max(1, Math.abs(expected));
    }

    void close(double actual, double expected, String what) {
        if (!close(actual, expected)) {
            throw new GameTestAssertException(what + ": " + actual + ", expected " + expected);
        }
    }
}
