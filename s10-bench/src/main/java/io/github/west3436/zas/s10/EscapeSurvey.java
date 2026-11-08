package io.github.west3436.zas.s10;

import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Spike #80: how often an enclosure's fill escapes the cube of sections the server thread
 * prefetches around a zone-triggering operation (architecture §4.3: radius 3, a 7³ cube), how many
 * extra atmos ticks an escape costs, and what the prefetch costs the server thread.
 *
 * <p>Each shape is built of station wall at its own site far from the station, in chunks loaded
 * well past it, and filled by one API add at its trigger point, as a device or a player's first
 * gas would. The add's rounds are the atmos ticks from the batch that carried it to the one whose
 * result completed it: 1 when the fill stayed in the cube, one more for each time the worker had
 * to ask for sections it didn't hold. One shape runs at a time, with the station's breaches and
 * the travelling player stopped.
 */
final class EscapeSurvey {
    /** A shape: its inside's size, and where in it the gas goes. */
    record Shape(String name, int sx, int sy, int sz, int tx, int ty, int tz) {
        int cells() {
            return sx * sy * sz;
        }
    }

    /** What one shape did. */
    static final class Result {
        final Shape shape;
        long firstBatch = -1;
        long doneBatch = -1;
        String outcome = "timed out";
        int zoneCells;
        int requestedSections;
        int requestBatches;
        final List<Long> closeNanos = new ArrayList<>();
        final List<Integer> batchSections = new ArrayList<>();
        long maxWorkerNanos;

        Result(Shape shape) {
            this.shape = shape;
        }

        long rounds() {
            return firstBatch < 0 || doneBatch < 0 ? -1 : doneBatch - firstBatch + 1;
        }
    }

    /** The shapes: a station room, halls and corridors from their middle, corner or end. */
    static List<Shape> shapes(boolean quick) {
        List<Shape> all = List.of(
                new Shape("station room 8 × 5 × 8, from its middle", 8, 5, 8, 4, 2, 4),
                new Shape("hall 32 × 8 × 32, from its middle", 32, 8, 32, 16, 1, 16),
                new Shape("hall 32 × 8 × 32, from a corner", 32, 8, 32, 0, 0, 0),
                new Shape("hall 64 × 8 × 64, from its middle", 64, 8, 64, 32, 1, 32),
                new Shape("hall 64 × 8 × 64, from a corner", 64, 8, 64, 0, 0, 0),
                // The largest a fill takes (EnclosureFill's 65,536 cells) is about 100 × 6 × 100.
                new Shape("hall 100 × 6 × 100, from a corner", 100, 6, 100, 0, 0, 0),
                new Shape("corridor 3 × 3 × 64, from an end", 3, 3, 64, 1, 1, 0),
                new Shape("corridor 3 × 3 × 128, from its middle", 3, 3, 128, 1, 1, 64),
                new Shape("corridor 3 × 3 × 128, from an end", 3, 3, 128, 1, 1, 0),
                new Shape("corridor 3 × 3 × 256, from an end", 3, 3, 256, 1, 1, 0),
                new Shape("corridor 3 × 3 × 512, from an end", 3, 3, 512, 1, 1, 0),
                new Shape("shaft 3 × 128 × 3, from its bottom", 3, 128, 3, 1, 0, 1));
        return quick ? List.of(all.get(0), all.get(8)) : all;
    }

    private enum Step {
        BUILD,
        SETTLE,
        WAIT
    }

    private static final int SETTLE_TICKS = 60;
    private static final int TIMEOUT_TICKS = 40 * 20;
    private static final int SITE_SPACING = 800;

    private final ServerLevel level;
    private final DimensionAtmos atmos;
    private final List<Shape> shapes;
    final List<Result> results = new ArrayList<>();
    private int index;
    private Step step = Step.BUILD;
    private long stepTick;
    private Result current;
    private CompletableFuture<MutationResult> add;
    private long addedAtBatch;

    EscapeSurvey(ServerLevel level, DimensionAtmos atmos, boolean quick) {
        this.level = level;
        this.atmos = atmos;
        this.shapes = shapes(quick);
    }

    boolean done() {
        return index >= shapes.size();
    }

    private BlockPos origin(int i) {
        return new BlockPos(-3000 - i * SITE_SPACING, 0, -3000);
    }

    /** One server tick of the survey, before the game's own. Server thread. */
    void tick(long gameTick) {
        if (done()) {
            return;
        }
        Shape s = shapes.get(index);
        BlockPos o = origin(index);
        switch (step) {
            case BUILD -> {
                current = new Result(s);
                load(o, s, true);
                build(o, s);
                step = Step.SETTLE;
                stepTick = gameTick;
            }
            case SETTLE -> {
                if (gameTick - stepTick >= SETTLE_TICKS) {
                    DimensionPipeline p = atmos.pipeline();
                    addedAtBatch = p == null ? 0 : p.closedTick();
                    Vec3 at = Vec3.atCenterOf(inside(o, s, s.tx(), s.ty(), s.tz()));
                    long t = Clock.enter();
                    try {
                        add = GasMutations.add(level, at, Bench.air(s.cells(), 293.15), Bench.SOURCE);
                    } finally {
                        Clock.exit(Clock.BENCH_API, t);
                    }
                    Result r = current;
                    add.thenAccept(m -> {
                        DimensionPipeline q = atmos.pipeline();
                        r.doneBatch = q == null ? -1 : q.closedTick();
                        r.outcome = m.outcome().name();
                        BlockPos b = BlockPos.containing(at);
                        ZoneView z = atmos.published().zoneAt(b.getX(), b.getY(), b.getZ());
                        r.zoneCells = z == null ? 0 : z.cellCount();
                    });
                    step = Step.WAIT;
                    stepTick = gameTick;
                }
            }
            case WAIT -> {
                if (add.isDone() || gameTick - stepTick >= TIMEOUT_TICKS) {
                    results.add(current);
                    load(o, s, false);
                    index++;
                    step = Step.BUILD;
                }
            }
        }
    }

    /**
     * After the tick: what the station's batch closes and applies did for the shape in progress.
     *
     * @param closed whether a batch closed this tick
     * @param applied whether one was applied
     * @param requested the sections the worker asked for in the result applied
     */
    void after(boolean closed, boolean applied, long[] requested, List<long[]> workerTicks) {
        if (current == null || step != Step.WAIT) {
            return;
        }
        DimensionPipeline p = atmos.pipeline();
        if (closed && p != null) {
            if (current.firstBatch < 0 && p.closedTick() > addedAtBatch) {
                current.firstBatch = p.closedTick();
            }
            if (current.firstBatch >= 0 && !add.isDone()) {
                current.closeNanos.add(atmos.lastCloseNanos());
                current.batchSections.add(p.lastBatchSections());
            }
        }
        if (applied && requested.length > 0 && current.firstBatch >= 0) {
            current.requestBatches++;
            current.requestedSections += requested.length;
        }
        for (long[] row : workerTicks) {
            current.maxWorkerNanos = Math.max(current.maxWorkerNanos, row[WorkerTimes.WHOLE]);
        }
    }

    private static BlockPos inside(BlockPos o, Shape s, int x, int y, int z) {
        return new BlockPos(o.getX() + 1 + x, Station.FLOOR + 1 + y, o.getZ() + 1 + z);
    }

    /** Keeps the shape's chunks and 4 more on each side loaded, or lets them go. */
    private void load(BlockPos o, Shape s, boolean keep) {
        int x0 = (o.getX() >> 4) - 4;
        int x1 = ((o.getX() + s.sx() + 1) >> 4) + 4;
        int z0 = (o.getZ() >> 4) - 4;
        int z1 = ((o.getZ() + s.sz() + 1) >> 4) + 4;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                level.setChunkForced(x, z, keep);
                if (keep) {
                    level.getChunk(x, z);
                }
            }
        }
    }

    private void build(BlockPos o, Shape s) {
        BlockState wall = AtmosBlocks.STATION_WALL.get().defaultBlockState();
        int y0 = Station.FLOOR;
        for (int x = 0; x <= s.sx() + 1; x++) {
            for (int y = 0; y <= s.sy() + 1; y++) {
                for (int z = 0; z <= s.sz() + 1; z++) {
                    boolean shell = x == 0 || x == s.sx() + 1 || y == 0 || y == s.sy() + 1 || z == 0 || z == s.sz() + 1;
                    if (shell) {
                        level.setBlock(new BlockPos(o.getX() + x, y0 + y, o.getZ() + z), wall, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
