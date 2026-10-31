package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Building and ticking for the paused-zone and persistence GameTests: each test gets a
 * chunk-aligned area of its own near the origin of a level (cell ids only reach about 8 million
 * blocks out, see {@code AtmosTests}), so rooms can be placed across chunk borders, and drives
 * atmos ticks itself. Nothing forces a chunk (GEO-034): building loads the area, and each test runs
 * within one game tick.
 *
 * <p>Areas are at z = 8192, apart from the {@code AtmosTests} areas.
 */
final class Rooms {
    static final double REL = 1e-9;
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;
    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;

    /**
     * @param index the test's own area number
     */
    Rooms(GameTestHelper h, ServerLevel level, int index) {
        this.h = h;
        this.level = level;
        this.origin = new BlockPos(4096 + 256 * index, 100, 8192);
    }

    Rooms(GameTestHelper h, int index) {
        this(h, h.getLevel(), index);
    }

    static SourceId source(String test) {
        return SourceId.of(ZasMod.MOD_ID, test);
    }

    static AtmosService service() {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return s;
    }

    DimensionAtmos dimension() {
        return service().dimensionFor(level);
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    Vec3 point(int x, int y, int z) {
        return Vec3.atCenterOf(abs(x, y, z));
    }

    /** The chunk holding area x (z 0 to 15). */
    ChunkPos chunk(int x) {
        return new ChunkPos(abs(x, 0, 0));
    }

    long chunkKey(int x) {
        return chunk(x).toLong();
    }

    void set(int x, int y, int z, BlockState state) {
        level.setBlockAndUpdate(abs(x, y, z), state);
    }

    /** Clears the area from x0 to x1 to air, 7 high and 7 deep. */
    void clear(int x0, int x1) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, 0), abs(x1, 6, 6))) {
            level.setBlockAndUpdate(p, AIR);
        }
    }

    /** A hollow stone box with corners (x0, 0, 0) and (x1, 4, 4): a 3 × 3 interior from x0 + 1 to x1 - 1. */
    void shell(int x0, int x1) {
        box(x0, 0, x1, 4);
    }

    /** A hollow stone box with corners (x0, 0, z0) and (x1, 4, z1). */
    void box(int x0, int z0, int x1, int z1) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, z0), abs(x1, 4, z1))) {
            int x = p.getX() - origin.getX();
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == x0 || x == x1 || y == 0 || y == 4 || z == z0 || z == z1;
            level.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
    }

    /** Clears the area from (x0, 0, z0) to (x1, 6, z1) to air. */
    void clear(int x0, int z0, int x1, int z1) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, z0), abs(x1, 6, z1))) {
            level.setBlockAndUpdate(p, AIR);
        }
    }

    void tick() {
        service().tickNow(level);
    }

    void ticks(int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    /** Runs atmos ticks until the future completes; a classification may take a few. */
    <T> T settle(CompletableFuture<T> future) {
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(future.isDone(), "the mutation didn't complete within 8 atmos ticks");
        return future.join();
    }

    ZoneInfo zone(int x, int y, int z) {
        return Atmospheres.zoneAt(level, abs(x, y, z))
                .orElseThrow(() -> new GameTestAssertException("no zone at " + x + ", " + y + ", " + z));
    }

    boolean zoned(int x, int y, int z) {
        return Atmospheres.zoneAt(level, abs(x, y, z)).isPresent();
    }

    static GasParcel oxygen(double moles) {
        return new GasParcel(Map.of(O2, moles), StandardAir.TEMPERATURE);
    }

    void close(double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= REL * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /** Checks two snapshots of a zone have the same state, paused flag aside. */
    void same(ZoneInfo expected, ZoneInfo actual, String what) {
        h.assertValueEqual(actual.id(), expected.id(), what + ": id");
        h.assertValueEqual(actual.cellCount(), expected.cellCount(), what + ": cells");
        close(actual.volume(), expected.volume(), what + ": volume");
        h.assertValueEqual(actual.mixture().moles(), expected.mixture().moles(), what + ": moles");
        close(actual.mixture().temperature(), expected.mixture().temperature(), what + ": temperature");
    }
}
