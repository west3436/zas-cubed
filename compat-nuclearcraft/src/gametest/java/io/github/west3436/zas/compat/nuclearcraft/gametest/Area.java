package io.github.west3436.zas.compat.nuclearcraft.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.simulation.AtmosService;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A GameTest's own area of the level: a sealed stone room far from every other test's, with
 * NuclearCraft's blocks in it, and the gas of the room read through the public API. Atmos ticks are
 * run with the framework's {@link AtmosService#tickNow}, which GameTests may use and the adapter
 * never does.
 */
final class Area {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final SourceId SOURCE = SourceId.of(NcGateTests.NAMESPACE, "gametest");

    /** The room's inside, from (1, 1, 1): 7 along x, 5 up and 5 along z, room for a 5 by 3 by 3 reactor. */
    static final int X = 7;

    static final int Y = 5;
    static final int Z = 5;

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;

    /** Whether the game has been told to keep ticking this area's chunk. */
    private boolean ticking;

    /** Who pins this area's room, if a test has it pinned. */
    private SourceId pinner;

    /**
     * Two tests with one number build in the same place and spoil each other's rooms, so each test
     * has a number of its own.
     *
     * @param h the test
     * @param index the area's number
     */
    Area(GameTestHelper h, int index) {
        this.h = h;
        this.level = h.getLevel();
        // Apart from the framework's and the content mod's numbered areas, which are at z = 16384,
        // and the other adapters'.
        this.origin = new BlockPos(8192 + 64 * index, 100, 30720);
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    /** The room: X by Y by Z of air from (1, 1, 1), in a stone box, in cleared air. */
    void room() {
        clear();
        for (BlockPos p : BlockPos.betweenClosed(abs(0, 0, 0), abs(X + 1, Y + 1, Z + 1))) {
            int x = p.getX() - origin.getX();
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == 0 || x == X + 1 || y == 0 || y == Y + 1 || z == 0 || z == Z + 1;
            level.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
    }

    /**
     * Has the game tick this area's blocks from its next tick on, until the area is cleared: for a
     * test that leaves a machine to the game.
     */
    void keepTicking() {
        ChunkPos chunk = new ChunkPos(origin);
        level.setChunkForced(chunk.x, chunk.z, true);
        ticking = true;
    }

    /** Pins the room that holds a cell until the area is cleared, so the game doesn't dissolve it. */
    void pin(int x, int y, int z) {
        pinner = SourceId.of(NcGateTests.NAMESPACE, "gametest_area_" + origin.getX());
        CompletableFuture<PinResult> future = ZonePins.pin(level, Vec3.atCenterOf(abs(x, y, z)), pinner);
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(
                future.isDone() && future.join().outcome() == PinOutcome.ZONE,
                "the room at " + x + ", " + y + ", " + z + " wasn't pinned");
    }

    /** Sets everything this area built back to air, with a block of margin. */
    void clear() {
        if (pinner != null) {
            ZonePins.unpin(level, pinner);
            pinner = null;
        }
        if (ticking) {
            ChunkPos chunk = new ChunkPos(origin);
            level.setChunkForced(chunk.x, chunk.z, false);
            ticking = false;
        }
        for (BlockPos p : BlockPos.betweenClosed(abs(-1, -1, -1), abs(X + 2, Y + 2, Z + 2))) {
            if (!level.getBlockState(p).isAir()) {
                level.setBlockAndUpdate(p, AIR);
            }
        }
    }

    static ResourceLocation nc(String path) {
        return ResourceLocation.fromNamespaceAndPath("nuclearcraft", path);
    }

    /** One of NuclearCraft's blocks, by its path. */
    BlockState block(String path) {
        Block block = BuiltInRegistries.BLOCK.get(nc(path));
        h.assertTrue(block != Blocks.AIR, "NuclearCraft registers no block " + path);
        return block.defaultBlockState();
    }

    /** Places one of NuclearCraft's blocks. */
    void set(int x, int y, int z, String path) {
        level.setBlockAndUpdate(abs(x, y, z), block(path));
    }

    /** Places one of NuclearCraft's blocks and returns its block entity. */
    BlockEntity place(int x, int y, int z, String path) {
        set(x, y, z, path);
        BlockEntity be = level.getBlockEntity(abs(x, y, z));
        if (be == null) {
            throw new GameTestAssertException(path + " has no block entity");
        }
        return be;
    }

    /** One of NuclearCraft's items, by its path. */
    ItemStack item(String path) {
        Item item = BuiltInRegistries.ITEM.get(nc(path));
        h.assertTrue(item != Items.AIR, "NuclearCraft registers no item " + path);
        return new ItemStack(item);
    }

    // ---- The atmosphere -----------------------------------------------------------------

    private AtmosService service() {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return s;
    }

    /** One atmos tick: what was released so far is applied. */
    void tick() {
        service().tickNow(level);
    }

    /** Forms the zone of the room that holds a cell, by adding nitrogen there, and waits for it. */
    void form(int x, int y, int z) {
        CompletableFuture<MutationResult> future = GasMutations.add(
                level,
                Vec3.atCenterOf(abs(x, y, z)),
                new GasParcel(Map.of(StandardAir.NITROGEN, 100.0), StandardAir.TEMPERATURE),
                SOURCE);
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(future.isDone(), "the room's gas didn't arrive within 8 atmos ticks");
        zone(x, y, z);
    }

    ZoneInfo zone(int x, int y, int z) {
        return Atmospheres.zoneAt(level, abs(x, y, z))
                .orElseThrow(() -> new GameTestAssertException("no zone at " + x + ", " + y + ", " + z));
    }
}
