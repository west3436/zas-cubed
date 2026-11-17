package io.github.west3436.zas.compat.pneumaticcraft.gametest;

import com.mojang.authlib.GameProfile;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * A GameTest's own area of the level: a sealed stone room far from every other test's, with
 * PneumaticCraft's blocks in it, and the gas of the room read through the public API. Atmos ticks
 * are run with the framework's {@link AtmosService#tickNow}, which GameTests may use and the
 * adapter never does.
 */
final class Area {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final SourceId SOURCE = SourceId.of(PncGateTests.NAMESPACE, "gametest");

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
        this.origin = new BlockPos(8192 + 64 * index, 100, 28672);
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    /** The usual room: 3 by 3 by 3 of air, (1, 1, 1) to (3, 3, 3), in a stone box, in cleared air. */
    void room() {
        clear();
        for (BlockPos p : BlockPos.betweenClosed(abs(0, 0, 0), abs(4, 4, 4))) {
            int x = p.getX() - origin.getX();
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
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
        pinner = SourceId.of(PncGateTests.NAMESPACE, "gametest_area_" + origin.getX());
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
        for (BlockPos p : BlockPos.betweenClosed(abs(-1, -1, -1), abs(5, 5, 5))) {
            if (!level.getBlockState(p).isAir()) {
                level.setBlockAndUpdate(p, AIR);
            }
        }
    }

    /** One of PneumaticCraft's blocks, by its path. */
    BlockState block(String path) {
        Block block = BuiltInRegistries.BLOCK.get(pnc(path));
        h.assertTrue(block != Blocks.AIR, "PneumaticCraft registers no block " + path);
        return block.defaultBlockState();
    }

    /** Places one of PneumaticCraft's blocks and returns its block entity. */
    BlockEntity place(int x, int y, int z, String path) {
        BlockPos pos = abs(x, y, z);
        level.setBlockAndUpdate(pos, block(path));
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            throw new GameTestAssertException(path + " has no block entity");
        }
        return be;
    }

    static ResourceLocation pnc(String path) {
        return ResourceLocation.fromNamespaceAndPath("pneumaticcraft", path);
    }

    /** One of PneumaticCraft's items, by its path. */
    ItemStack item(String path) {
        Item item = BuiltInRegistries.ITEM.get(pnc(path));
        h.assertTrue(item != Items.AIR, "PneumaticCraft registers no item " + path);
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

    /** Empties the room that holds a cell of all its gas, and waits for it. */
    void empty(int x, int y, int z) {
        Map<ResourceLocation, Double> all = new java.util.HashMap<>();
        zone(x, y, z).mixture().moles().forEach((gas, n) -> all.put(gas, n * 2));
        CompletableFuture<MutationResult> future =
                GasMutations.remove(level, Vec3.atCenterOf(abs(x, y, z)), all, SOURCE);
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(future.isDone(), "the room's gas didn't leave within 8 atmos ticks");
    }

    ZoneInfo zone(int x, int y, int z) {
        return Atmospheres.zoneAt(level, abs(x, y, z))
                .orElseThrow(() -> new GameTestAssertException("no zone at " + x + ", " + y + ", " + z));
    }

    /** The moles of every gas in the room that holds a cell. */
    double total(int x, int y, int z) {
        return zone(x, y, z).mixture().totalMoles();
    }

    // ---- A player -----------------------------------------------------------------------

    /** A player standing in a cell, with no armor. Not in the level's player list. */
    FakePlayer player(int x, int y, int z) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "zas_pnc_test")) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        BlockPos feet = abs(x, y, z);
        player.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        return player;
    }
}
