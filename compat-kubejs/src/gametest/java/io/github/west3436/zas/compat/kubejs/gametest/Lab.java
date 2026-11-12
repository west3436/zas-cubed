package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A test's rooms and entities. Each test has an area of its own, 256 blocks square, far from the
 * others and from the tests' structures, and drives every atmos tick and breath check itself
 * within one game tick, so the tests don't see each other. Nothing forces a chunk: building loads
 * the area for the game tick the test runs in.
 *
 * <p>A room is a hollow stone box with a 3 × 3 × 3 inside, from area x {@code x0} to {@code x0 +
 * 4}.
 */
final class Lab implements AutoCloseable {
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;

    /** Standard air's partial pressures, in kPa. */
    static final Map<ResourceLocation, Double> AIR = Map.of(
            N2, StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION,
            O2, StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION);

    static final SourceId SOURCE = SourceId.of("zas_kubejs", "gametest");
    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState NOTHING = Blocks.AIR.defaultBlockState();

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;
    private final List<Entity> entities = new ArrayList<>();
    private final List<Runnable> restore = new ArrayList<>();

    /** An area on the Overworld. */
    Lab(GameTestHelper h, int index) {
        this(h, h.getLevel(), new BlockPos(4096 + 256 * index, 100, 8192));
    }

    /** An area in any level; keep its corner chunk-aligned, so area x 0 and 16 are chunk borders. */
    Lab(GameTestHelper h, ServerLevel level, BlockPos origin) {
        this.h = h;
        this.level = level;
        this.origin = origin;
        level.getChunk(origin);
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas_kubejs", path);
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    Vec3 point(int x, int y, int z) {
        return Vec3.atCenterOf(abs(x, y, z));
    }

    /** Whether a position is in this area. */
    boolean holds(BlockPos pos) {
        int x = pos.getX() - origin.getX();
        int z = pos.getZ() - origin.getZ();
        return x >= 0 && x < 256 && z >= 0 && z < 256;
    }

    void set(int x, int y, int z, BlockState state) {
        level.setBlockAndUpdate(abs(x, y, z), state);
    }

    /** The zone at a block of the area. */
    ZoneInfo zone(int x, int y, int z) {
        return Atmospheres.zoneAt(level, abs(x, y, z))
                .orElseThrow(() -> new GameTestAssertException("no zone at " + x + ", " + y + ", " + z));
    }

    DimensionAtmos dimension() {
        return service().dimensionFor(level);
    }

    /** The chunk holding area x (z 0 to 15). */
    ChunkPos chunk(int x) {
        return new ChunkPos(abs(x, 0, 0));
    }

    private static AtmosService service() {
        AtmosService service = AtmosService.current();
        if (service == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return service;
    }

    void tick() {
        service().tickNow(level);
    }

    void ticks(int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    /** Runs atmos ticks until a mutation completes; classifying a new room may take a few. */
    <T> T settle(CompletableFuture<T> future) {
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(future.isDone(), "the mutation didn't complete within 8 atmos ticks");
        return future.join();
    }

    /** Clears the area from x0 to x1 to air, 7 high and 7 deep. */
    void clear(int x0, int x1) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, 0), abs(x1, 6, 6))) {
            level.setBlockAndUpdate(p, NOTHING);
        }
    }

    /** A hollow stone box with corners (x0, 0, 0) and (x1, 4, 4): an inside from x0 + 1 to x1 - 1. */
    void shell(int x0, int x1) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, 0), abs(x1, 4, 4))) {
            int x = p.getX() - origin.getX();
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == x0 || x == x1 || y == 0 || y == 4 || z == 0 || z == 4;
            level.setBlockAndUpdate(p, wall ? STONE : NOTHING);
        }
    }

    /**
     * Builds a sealed room from x0 and fills it with these partial pressures, in kPa at 293.15 K,
     * in place of the air it formed with.
     */
    ZoneInfo room(int x0, Map<ResourceLocation, Double> kPa) {
        clear(x0, x0 + 5);
        shell(x0, x0 + 4);
        Vec3 middle = point(x0 + 2, 2, 2);
        settle(GasMutations.remove(level, middle, Map.of(N2, 1e6, O2, 1e6), SOURCE));
        Map<ResourceLocation, Double> moles = new HashMap<>();
        kPa.forEach((gas, p) -> moles.put(gas, p * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE)));
        settle(GasMutations.add(level, middle, new GasParcel(moles, StandardAir.TEMPERATURE), SOURCE));
        return zone(x0 + 2, 2, 2);
    }

    /** A room of standard air with something more in it, in kPa. */
    ZoneInfo airRoom(int x0, Map<ResourceLocation, Double> more) {
        Map<ResourceLocation, Double> kPa = new HashMap<>(AIR);
        kPa.putAll(more);
        return room(x0, kPa);
    }

    /** A mob on the floor of the room from x0, with its AI off, discarded when the test ends. */
    <T extends Mob> T mob(EntityType<T> type, int x0) {
        T mob = type.create(level);
        h.assertTrue(mob != null, "couldn't create a " + type);
        mob.moveTo(Vec3.atBottomCenterOf(abs(x0 + 2, 1, 2)));
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        entities.add(mob);
        return mob;
    }

    /** Something to put back when the test ends, last first. */
    void restoring(Runnable undo) {
        restore.add(undo);
    }

    void close(double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    @Override
    public void close() {
        entities.forEach(Entity::discard);
        for (int i = restore.size() - 1; i >= 0; i--) {
            restore.get(i).run();
        }
    }
}
