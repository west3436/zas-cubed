package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.simulation.AtmosService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Building and ticking for the controller and generator GameTests. Each test builds in its own
 * chunk-aligned area of a level, far from the framework's test areas, and runs within one game
 * tick: it drives atmos ticks itself with the framework's {@code tickNow} and steps the devices it
 * placed with {@link AtmosDevice#step()}, so the regular schedule never steps them in between.
 * Each test clears its area when it ends, which removes its controllers and so their pins.
 */
final class Site {
    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "gametest");

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;

    /** Area {@code index} of a level, at x = −16384 − 256 × index, z = 16384. */
    Site(GameTestHelper h, ServerLevel level, int index) {
        this.h = h;
        this.level = level;
        this.origin = new BlockPos(-16384 - 256 * index, 100, 16384);
    }

    Site(GameTestHelper h, int index) {
        this(h, h.getLevel(), index);
    }

    static AtmosService service() {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return s;
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

    /** Clears x 0 to 16, 7 high and 7 deep, to air. */
    void clear() {
        for (BlockPos p : BlockPos.betweenClosed(abs(0, 0, 0), abs(16, 6, 6))) {
            level.setBlockAndUpdate(p, AIR);
        }
    }

    /** A hollow stone box from (x0, 0, 0) to (x1, 4, 4): a 3 × 3 × 3 room for x0 + 4 = x1. */
    void room(int x0, int x1) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, 0), abs(x1, 4, 4))) {
            int x = p.getX() - origin.getX();
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == x0 || x == x1 || y == 0 || y == 4 || z == 0 || z == 4;
            level.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
    }

    /** A controller on the north wall of a room, facing south into it, at (x, 2, 1). */
    AreaAirControllerBlockEntity controller(int x) {
        BlockState state = AtmosBlocks.AREA_AIR_CONTROLLER
                .get()
                .defaultBlockState()
                .setValue(AreaAirControllerBlock.FACING, Direction.SOUTH);
        h.assertTrue(state.canSurvive(level, abs(x, 2, 1)), "the controller has no wall behind it");
        set(x, 2, 1, state);
        if (!(level.getBlockEntity(abs(x, 2, 1)) instanceof AreaAirControllerBlockEntity c)) {
            throw new GameTestAssertException("no controller block entity");
        }
        loaded(c);
        return c;
    }

    @SuppressWarnings("unchecked")
    <T> T entity(int x, int y, int z, Class<T> type) {
        Object be = level.getBlockEntity(abs(x, y, z));
        if (!type.isInstance(be)) {
            throw new GameTestAssertException("no " + type.getSimpleName() + " at " + x + ", " + y + ", " + z);
        }
        loaded(be);
        return (T) be;
    }

    <T> T place(int x, int y, int z, BlockState state, Class<T> type) {
        set(x, y, z, state);
        return entity(x, y, z, type);
    }

    /**
     * Loads a device placed this game tick at once. NeoForge loads a new block entity at the start of
     * the next game tick, which a test running within one tick never reaches, and a controller finds
     * its parts among the loaded devices. Loading twice is harmless.
     */
    static void loaded(Object be) {
        if (be instanceof AtmosDevice d && !io.github.west3436.zas.atmos.device.DeviceTicker.isScheduled(d)) {
            d.onLoad();
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

    /** One atmos tick: each device steps, then the framework applies what they did. */
    void cycle(AtmosDevice... devices) {
        for (AtmosDevice d : devices) {
            d.step();
        }
        tick();
    }

    void cycles(int n, AtmosDevice... devices) {
        for (int i = 0; i < n; i++) {
            cycle(devices);
        }
    }

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

    /** A zone's pin owners in the published table; empty if the zone is gone. */
    List<String> pins(ZoneId id) {
        ZoneView v = service().dimensionFor(level).published().zone(id.value());
        return v == null ? List.of() : v.pins();
    }

    /** Adds a gas to the zone at a point, at the zone's temperature, to raise its partial pressure by {@code kpa}. */
    void addKpa(int x, int y, int z, ResourceLocation gas, double kpa) {
        ZoneInfo zone = zone(x, y, z);
        double t = zone.mixture().totalMoles() > 0 ? zone.mixture().temperature() : 293.15;
        double moles = kpa * zone.volume() / (GasMixture.GAS_CONSTANT * t);
        settle(GasMutations.add(level, point(x, y, z), new GasParcel(Map.of(gas, moles), t), SOURCE));
    }

    /** Removes every mole of the zone at a point. */
    void empty(int x, int y, int z) {
        ZoneInfo zone = zone(x, y, z);
        settle(GasMutations.remove(level, point(x, y, z), zone.mixture().moles(), SOURCE));
    }

    /** Removes every mole of one gas from the zone at a point. */
    void removeGas(int x, int y, int z, ResourceLocation gas) {
        ZoneInfo zone = zone(x, y, z);
        settle(GasMutations.remove(
                level, point(x, y, z), Map.of(gas, zone.mixture().moles(gas)), SOURCE));
    }

    void close(double actual, double expected, double tolerance, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }
}
