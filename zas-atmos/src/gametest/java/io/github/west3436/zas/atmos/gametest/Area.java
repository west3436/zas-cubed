package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.simulation.AtmosService;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A GameTest's own area in a level, for tests that build rooms and drive atmos ticks and device
 * steps themselves, all within one game tick (as the framework's {@code Rooms}). Numbered areas
 * are at z = 16384 in the Overworld, apart from the framework's tests.
 *
 * <p>Atmos ticks are run with the framework's {@link AtmosService#tickNow}: GameTests may use it,
 * the mod never does (CONT-095).
 */
final class Area {
    static final double REL = 1e-9;
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;
    static final double T = StandardAir.TEMPERATURE;
    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "gametest");

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;

    Area(GameTestHelper h, int index) {
        this.h = h;
        this.level = h.getLevel();
        this.origin = new BlockPos(4096 + 256 * index, 100, 16384);
    }

    /**
     * Force-loads the area's chunk and its neighbours on x, or releases them. A test that waits in
     * real time needs it: otherwise the chunk can unload mid-test, and the test is left holding
     * removed block entities.
     */
    void forceLoad(boolean on) {
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            level.setChunkForced(cx + dx, cz, on);
        }
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
     * Places a block as a player would, with its shape worked out from its neighbours, and loads
     * its block entity now. The game would call {@code onLoad} at the start of the next tick, and
     * these tests run within one; the later call changes nothing.
     */
    void place(int x, int y, int z, BlockState state) {
        BlockPos pos = abs(x, y, z);
        level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(state, level, pos));
        if (level.getBlockEntity(pos) instanceof AtmosDevice d && !DeviceTicker.isScheduled(d)) {
            d.onLoad();
        }
    }

    BlockState get(int x, int y, int z) {
        return level.getBlockState(abs(x, y, z));
    }

    @SuppressWarnings("unchecked")
    <E extends BlockEntity> E entity(int x, int y, int z) {
        BlockEntity be = level.getBlockEntity(abs(x, y, z));
        if (be == null) {
            throw new GameTestAssertException("no block entity at " + x + ", " + y + ", " + z);
        }
        return (E) be;
    }

    /** Clears (x0, 0, z0) to (x1, 6, z1) to air. */
    void clear(int x0, int z0, int x1, int z1) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, z0), abs(x1, 6, z1))) {
            level.setBlockAndUpdate(p, AIR);
        }
    }

    /** A hollow stone box with corners (x0, 0, z0) and (x1, 4, z1), cleared around. */
    void box(int x0, int z0, int x1, int z1) {
        clear(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, z0), abs(x1, 4, z1))) {
            int x = p.getX() - origin.getX();
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == x0 || x == x1 || y == 0 || y == 4 || z == z0 || z == z1;
            level.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
    }

    AtmosService service() {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return s;
    }

    /** One atmos tick of the framework: mutations queued so far are applied and their futures complete. */
    void tick() {
        service().tickNow(level);
    }

    /** Steps every ready device of the level once, then runs one atmos tick. */
    void step() {
        DeviceTicker.stepNow(level);
        tick();
    }

    <T> T settle(CompletableFuture<T> future) {
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(future.isDone(), "the mutation didn't complete within 8 atmos ticks");
        return future.join();
    }

    /** Adds gas to a room through the API, forming its zone, and waits for it. */
    void fill(int x, int y, int z, Map<ResourceLocation, Double> moles) {
        settle(GasMutations.add(level, point(x, y, z), new GasParcel(moles, T), SOURCE));
    }

    ZoneInfo zone(int x, int y, int z) {
        return Atmospheres.zoneAt(level, abs(x, y, z))
                .orElseThrow(() -> new GameTestAssertException("no zone at " + x + ", " + y + ", " + z));
    }

    void close(double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= REL * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }
}
