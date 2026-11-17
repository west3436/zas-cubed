package io.github.west3436.zas.compat.sable.gametest;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * A GameTest's own area of a level, far from every other test's: a sealed stone box that a test
 * turns into a sub-level as Sable's {@code /sable assemble area} does, and the gas of its room read
 * through the public API. Atmos ticks are run with the framework's {@link AtmosService#tickNow},
 * which GameTests may use and the adapter never does.
 *
 * <p>The box is 5 by 5 by 5, its walls stone, its room the 3 by 3 by 3 of air from (1, 1, 1) to (3,
 * 3, 3). Sable's physics is paused for the whole run, so a sub-level stays where a test puts it.
 */
final class Deck {
    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final SourceId SOURCE = SourceId.of(SableGateTests.NAMESPACE, "gametest");
    static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;

    /**
     * Two tests with one number build in the same place and spoil each other's rooms, so each test
     * has a number of its own.
     */
    Deck(GameTestHelper h, ServerLevel level, int index) {
        this.h = h;
        this.level = level;
        // Chunk-aligned, apart from the framework's and the content mod's areas, at z = 16384, and
        // the other adapters'.
        this.origin = new BlockPos(-12288 + 64 * index, 100, 30720);
        ServerSubLevelContainer subLevels = container();
        subLevels.physicsSystem().setPaused(true);
        // Kept loaded for the run: the test world is thrown away after it.
        ChunkPos chunk = new ChunkPos(origin);
        level.setChunkForced(chunk.x, chunk.z, true);
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    ServerSubLevelContainer container() {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) {
            throw new GameTestAssertException(
                    "Sable has no sub-levels in " + level.dimension().location());
        }
        return c;
    }

    /** Builds the box in cleared air. */
    void box() {
        clear();
        for (BlockPos p : BlockPos.betweenClosed(abs(0, 0, 0), abs(4, 4, 4))) {
            int x = p.getX() - origin.getX();
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
            level.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
    }

    /** Sets the area back to air, with a block of margin. */
    void clear() {
        for (BlockPos p : BlockPos.betweenClosed(abs(-1, -1, -1), abs(5, 5, 5))) {
            if (!level.getBlockState(p).isAir()) {
                level.setBlockAndUpdate(p, AIR);
            }
        }
    }

    /**
     * Turns the box into a sub-level as {@code /sable assemble area} does: every block of the box, air
     * too, anchored at its first corner.
     */
    ServerSubLevel assemble() {
        BlockPos min = abs(0, 0, 0);
        BlockPos max = abs(4, 4, 4);
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            blocks.add(p.immutable());
        }
        ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, min, blocks, new BoundingBox3i(min, max));
        h.assertTrue(sub != null && !sub.isRemoved(), "Sable made no sub-level of the box");
        // Where the world shows it, as Sable works it out on the sub-level's next tick.
        sub.updateBoundingBox();
        return sub;
    }

    /** Where the box's cell (x, y, z) is in a sub-level made of it: in its plot, as assembled. */
    static BlockPos onBoard(ServerSubLevel sub, int x, int y, int z) {
        return sub.getPlot().getCenterBlock().offset(x, y, z);
    }

    /** The plot chunks of a sub-level. */
    static List<ChunkPos> chunks(ServerSubLevel sub) {
        return sub.getPlot().getLoadedChunks().stream().map(c -> c.getPos()).toList();
    }

    // ---- The atmosphere -----------------------------------------------------------------

    static AtmosService service() {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return s;
    }

    DimensionAtmos dimension() {
        DimensionAtmos d = service().dimension(level);
        if (d == null) {
            throw new GameTestAssertException(
                    "no atmos for " + level.dimension().location());
        }
        return d;
    }

    /** One atmos tick. */
    void tick() {
        service().tickNow(level);
    }

    void ticks(int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    /** Runs atmos ticks until the future completes. */
    <T> T settle(CompletableFuture<T> future) {
        for (int i = 0; i < 12 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(future.isDone(), "the step didn't complete within 12 atmos ticks");
        return future.join();
    }

    /**
     * Fills the room that holds a block to about 150 kPa with a mix that isn't the ambient's: carbon
     * dioxide under its hazard, and the rest oxygen and nitrogen half and half.
     */
    ZoneInfo fill(BlockPos cell) {
        MutationResult seed = settle(GasMutations.add(
                level, Vec3.atCenterOf(cell), new GasParcel(Map.of(CO2, 0.01), StandardAir.TEMPERATURE), SOURCE));
        h.assertValueEqual(seed.outcome(), MutationOutcome.ZONE, "where the seed went");
        ZoneInfo zone = zone(cell);
        double now = zone.mixture().totalMoles();
        double target = now * 150 / zone.mixture().pressure();
        double co2 = target * 0.012;
        double rest = target - now - co2;
        settle(GasMutations.add(
                level,
                Vec3.atCenterOf(cell),
                new GasParcel(
                        Map.of(CO2, co2, StandardAir.OXYGEN, rest / 2, StandardAir.NITROGEN, rest / 2),
                        zone.mixture().temperature()),
                SOURCE));
        ZoneInfo filled = zone(cell);
        h.assertTrue(
                Math.abs(filled.mixture().pressure() - 150) < 0.5,
                "the room isn't at 150 kPa: " + filled.mixture().pressure());
        return filled;
    }

    /** Takes every bit of oxygen out of the room that holds a block. */
    ZoneInfo suffocate(BlockPos cell) {
        double o2 = zone(cell).mixture().moles().getOrDefault(StandardAir.OXYGEN, 0.0);
        settle(GasMutations.remove(level, Vec3.atCenterOf(cell), Map.of(StandardAir.OXYGEN, o2 * 2), SOURCE));
        ZoneInfo after = zone(cell);
        h.assertTrue(
                after.mixture().moles().getOrDefault(StandardAir.OXYGEN, 0.0) < 1e-9,
                "oxygen is left in the room: " + after.mixture().moles());
        return after;
    }

    Optional<ZoneInfo> zoneAt(BlockPos cell) {
        return Atmospheres.zoneAt(level, cell);
    }

    ZoneInfo zone(BlockPos cell) {
        return zoneAt(cell).orElseThrow(() -> new GameTestAssertException("no zone at " + cell.toShortString()));
    }

    /** Checks a room holds what it held before, gas for gas, unbreached and not paused. */
    void same(ZoneInfo before, ZoneInfo after, String what) {
        h.assertValueEqual(after.cellCount(), before.cellCount(), what + ": cells");
        Map<ResourceLocation, Double> was = before.mixture().moles();
        Map<ResourceLocation, Double> is = after.mixture().moles();
        h.assertValueEqual(is.keySet(), was.keySet(), what + ": gases");
        for (Map.Entry<ResourceLocation, Double> e : was.entrySet()) {
            double n = is.get(e.getKey());
            h.assertTrue(
                    Math.abs(n - e.getValue()) <= 1e-9 * Math.max(1, e.getValue()),
                    what + ": " + e.getKey() + " was " + e.getValue() + ", is " + n);
        }
        h.assertFalse(after.breached(), what + ": breached");
        h.assertFalse(after.paused(), what + ": paused");
    }

    // ---- A player -----------------------------------------------------------------------

    /** A player whose feet are at a point, with no armor. Not in the level's player list. */
    FakePlayer player(Vec3 feet) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "zas_sable_test")) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        player.moveTo(feet.x, feet.y, feet.z);
        return player;
    }
}
