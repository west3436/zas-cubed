package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.simulation.AtmosService;
import net.minecraft.core.BlockPos;
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
 * and the content mod's test areas, built and read within one game tick, and cleared when the test
 * ends. Spot {@code index} is at x = 8192 + 64 × index, z = 24576.
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
        this.origin = new BlockPos(8192 + 64 * index, 100, 24576);
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
     * running within one never reaches.
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

    /** Clears x 0 to 31, 7 high and 7 deep, to air. */
    void clear() {
        for (BlockPos p : BlockPos.betweenClosed(abs(0, 0, 0), abs(31, 6, 6))) {
            level.setBlockAndUpdate(p, AIR);
        }
    }

    /** A hollow stone box from (0, 0, 0) to (4, 4, 4): a sealed 3 × 3 × 3 room. */
    void room() {
        for (BlockPos p : BlockPos.betweenClosed(abs(0, 0, 0), abs(4, 4, 4))) {
            int x = p.getX() - origin.getX();
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
            level.setBlockAndUpdate(p, wall ? STONE : AIR);
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

    /** Whether two values agree to a float's precision, which is what a reading carries. */
    static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= 1e-5 * Math.max(1, Math.abs(expected));
    }
}
