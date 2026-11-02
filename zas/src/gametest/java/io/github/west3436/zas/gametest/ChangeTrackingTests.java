package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.change.SectionCapture;
import io.github.west3436.zas.engine.ChunkGenerations;
import io.github.west3436.zas.engine.DirtySection;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.geometry.Geometries;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block-change tracking in the world (GEO-010 to GEO-012).
 *
 * <p>Each test owns the dirty positions inside its own area: it clears them first with {@link
 * ChangeTracker#drainWithin}, so tests running side by side don't see each other's changes. Only
 * the test in batch {@code geo_012_drain} drains a whole level, so it runs alone.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChangeTrackingTests {
    private ChangeTrackingTests() {}

    private static final String AREA = "geo_010_area";
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // ---- helpers ----

    private static void check(GameTestHelper h, boolean condition, String message) {
        if (!condition) {
            h.fail(message);
        }
    }

    /** The test's area in absolute coordinates. */
    private static BoundingBox area(GameTestHelper h) {
        return BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(9, 7, 9)));
    }

    /** Forgets the area's dirty positions, such as those left by placing the test structure. */
    private static void reset(GameTestHelper h) {
        ChangeTracker.drainWithin(h.getLevel(), area(h));
    }

    private static boolean dirty(GameTestHelper h, BlockPos rel) {
        return ChangeTracker.isDirty(h.getLevel(), h.absolutePos(rel));
    }

    private static long blockGeneration(GameTestHelper h, BlockPos rel) {
        return ChangeTracker.blockGeneration(h.getLevel(), new ChunkPos(h.absolutePos(rel)));
    }

    /** The dirty positions in the area, relative to the test, removing them. */
    private static List<BlockPos> takeDirty(GameTestHelper h) {
        // Not GameTestHelper#relativePos, which turns positions 180 degrees in unrotated tests.
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        List<BlockPos> out = new ArrayList<>();
        for (DirtySection s : ChangeTracker.drainWithin(h.getLevel(), area(h))) {
            s.forEachIndex(i -> out.add(new BlockPos(
                            (s.sectionX() << 4) | (i & 15),
                            (s.sectionY() << 4) | (i >> 8),
                            (s.sectionZ() << 4) | ((i >> 4) & 15))
                    .subtract(origin)));
        }
        return out;
    }

    private static int geometryId(GameTestHelper h, BlockPos rel) {
        return Geometries.geometryId(h.getBlockState(rel));
    }

    /** Runs a command as the server, with output suppressed, at absolute coordinates. */
    private static void command(GameTestHelper h, String command) {
        ServerLevel level = h.getLevel();
        CommandSourceStack source = level.getServer()
                .createCommandSourceStack()
                .withLevel(level)
                .withPermission(4)
                .withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static String abs(GameTestHelper h, BlockPos rel) {
        BlockPos p = h.absolutePos(rel);
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /** A stone wall block at {@code rel}, with the area's change record reset after it. */
    private static void wall(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, STONE);
        reset(h);
    }

    // ---- GEO-010: each cause of a change reaches the tracker ----

    private static final BlockPos WALL = new BlockPos(4, 2, 4);

    /** {@code Level#setBlock} with neighbour updates (flags 3). */
    @GameTest(template = AREA)
    public static void geo_010_set_block(GameTestHelper h) {
        wall(h, WALL);
        long before = blockGeneration(h, WALL);
        h.setBlock(WALL, AIR);
        check(h, dirty(h, WALL), "the opened wall should be dirty");
        check(h, blockGeneration(h, WALL) != before, "the block generation should change");
        h.succeed();
    }

    /** {@code Level#setBlock} without neighbour updates (flags 2 | 16). */
    @GameTest(template = AREA)
    public static void geo_010_set_block_without_updates(GameTestHelper h) {
        wall(h, WALL);
        h.getLevel().setBlock(h.absolutePos(WALL), AIR, 2 | 16);
        check(h, dirty(h, WALL), "a write without updates should still be seen");
        h.succeed();
    }

    /** Another mod writing straight into the chunk, as WorldEdit-style tools do. */
    @GameTest(template = AREA)
    public static void geo_010_direct_chunk_write(GameTestHelper h) {
        wall(h, WALL);
        BlockPos abs = h.absolutePos(WALL);
        LevelChunk chunk = h.getLevel().getChunkAt(abs);
        chunk.setBlockState(abs, AIR, false);
        check(h, dirty(h, WALL), "a direct chunk write should be seen");
        h.succeed();
    }

    /** A chunk write from another thread is queued and folded in on the server thread. */
    @GameTest(template = AREA)
    public static void geo_010_off_thread_write(GameTestHelper h) {
        wall(h, WALL);
        BlockPos abs = h.absolutePos(WALL);
        LevelChunk chunk = h.getLevel().getChunkAt(abs);
        // The server thread waits, so the chunk is never touched by two threads at once.
        CompletableFuture.runAsync(() -> chunk.setBlockState(abs, AIR, false)).join();
        check(h, dirty(h, WALL), "an off-thread write should be seen");
        h.succeed();
    }

    /** A player breaking a wall block and placing one. */
    @GameTest(template = AREA)
    public static void geo_010_player(GameTestHelper h) {
        BlockPos floor = new BlockPos(2, 1, 2);
        BlockPos placed = floor.above();
        h.setBlock(floor, STONE);
        wall(h, WALL);
        FakePlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        check(h, player.gameMode.destroyBlock(h.absolutePos(WALL)), "the player should break the wall");
        check(h, dirty(h, WALL), "a broken wall should be dirty");

        ItemStack stone = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        BlockPos absFloor = h.absolutePos(floor);
        player.gameMode.useItemOn(
                player,
                h.getLevel(),
                stone,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absFloor).add(0, 0.5, 0), Direction.UP, absFloor, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(h, h.getBlockState(placed).is(Blocks.STONE), "the player should place stone");
        check(h, dirty(h, placed), "a placed block should be dirty");
        h.succeed();
    }

    /** A piston pushing a block into an empty position. */
    @GameTest(template = AREA, timeoutTicks = 100)
    public static void geo_010_piston(GameTestHelper h) {
        BlockPos piston = new BlockPos(2, 2, 4);
        BlockPos block = piston.east();
        BlockPos target = block.east();
        h.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        h.setBlock(block, STONE);
        reset(h);
        h.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.STONE, target);
            check(h, dirty(h, target), "the pushed-into position should be dirty");
            check(h, dirty(h, block), "the vacated position should be dirty");
        });
    }

    /** An explosion inside a glass shell. */
    @GameTest(template = AREA)
    public static void geo_010_explosion(GameTestHelper h) {
        BlockPos centre = new BlockPos(4, 3, 4);
        List<BlockPos> shell = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
            if (!p.equals(centre)) {
                h.setBlock(p, Blocks.GLASS);
                shell.add(p.immutable());
            }
        }
        reset(h);
        Vec3 at = Vec3.atCenterOf(h.absolutePos(centre));
        h.getLevel().explode(null, at.x, at.y, at.z, 2.0f, Level.ExplosionInteraction.TNT);
        int broken = 0;
        for (BlockPos p : shell) {
            if (h.getBlockState(p).isAir()) {
                broken++;
                check(h, dirty(h, p), "glass broken by the explosion should be dirty at " + p);
            }
        }
        check(h, broken > 0, "the explosion should break some glass");
        h.succeed();
    }

    /** Flowing water meets lava and turns it to stone, cobblestone or obsidian. */
    @GameTest(template = AREA, timeoutTicks = 200)
    public static void geo_010_fluid(GameTestHelper h) {
        // A basin: floor at y = 1, rim at y = 2 around x 0–6, z 0–4.
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 4; z++) {
                h.setBlock(new BlockPos(x, 1, z), STONE);
                boolean rim = x == 0 || x == 6 || z == 0 || z == 4;
                h.setBlock(new BlockPos(x, 2, z), rim ? STONE : AIR);
            }
        }
        h.setBlock(new BlockPos(5, 2, 2), Blocks.LAVA);
        reset(h);
        h.setBlock(new BlockPos(1, 2, 2), Blocks.WATER);
        h.succeedWhen(() -> {
            boolean seen = false;
            for (int x = 1; x <= 5 && !seen; x++) {
                for (int z = 1; z <= 3 && !seen; z++) {
                    BlockPos p = new BlockPos(x, 2, z);
                    BlockState s = h.getBlockState(p);
                    boolean solidified = s.is(Blocks.OBSIDIAN) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE);
                    seen = solidified && dirty(h, p);
                }
            }
            check(h, seen, "a block made by the fluids should be dirty");
        });
    }

    /** {@code /fill} and {@code /setblock}. */
    @GameTest(template = AREA)
    public static void geo_010_commands(GameTestHelper h) {
        reset(h);
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos max = new BlockPos(3, 3, 3);
        command(h, "fill " + abs(h, min) + " " + abs(h, max) + " minecraft:stone");
        BlockPos single = new BlockPos(6, 2, 6);
        command(h, "setblock " + abs(h, single) + " minecraft:stone");
        List<BlockPos> changed = takeDirty(h);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            check(h, changed.contains(p.immutable()), "/fill should mark " + p);
        }
        check(h, changed.contains(single), "/setblock should mark " + single);
        check(h, changed.size() == 28, "exactly 28 positions changed, got " + changed.size());
        h.succeed();
    }

    /**
     * Placing a structure template: every position it gives non-air geometry is dirty, and every
     * position it leaves as air geometry is not (GEO-011).
     */
    @GameTest(template = AREA)
    public static void geo_010_structure_placement(GameTestHelper h) {
        reset(h);
        command(h, "place template minecraft:igloo/top " + abs(h, new BlockPos(1, 1, 1)));
        List<BlockPos> changed = takeDirty(h);
        int airId = Geometries.geometryId(AIR);
        int placed = 0;
        // From y = 1: y = 0 is the test floor.
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(9, 7, 9))) {
            boolean expected = geometryId(h, p) != airId;
            placed += expected ? 1 : 0;
            check(h, changed.contains(p.immutable()) == expected, "dirty should be " + expected + " at " + p);
        }
        check(h, placed > 50, "the igloo should place blocks, placed " + placed);
        h.succeed();
    }

    // ---- GEO-011: the locality filter ----

    /**
     * Writes that keep the geometry id leave nothing dirty, though they still bump the chunk's
     * block generation (architecture §3.3).
     */
    @GameTest(template = AREA)
    public static void geo_011_same_geometry_is_filtered(GameTestHelper h) {
        BlockPos solid = new BlockPos(2, 2, 2);
        BlockPos open = new BlockPos(5, 2, 5);
        h.setBlock(solid, STONE);
        h.setBlock(open.below(), STONE);
        h.setBlock(open, AIR);
        reset(h);
        check(
                h,
                Geometries.geometryId(STONE) == Geometries.geometryId(Blocks.COBBLESTONE.defaultBlockState()),
                "stone and cobblestone should share a geometry");
        check(
                h,
                Geometries.geometryId(AIR) == Geometries.geometryId(Blocks.TORCH.defaultBlockState()),
                "air and a torch should share a geometry");
        long before = blockGeneration(h, solid);
        h.setBlock(solid, Blocks.COBBLESTONE);
        h.setBlock(open, Blocks.TORCH);
        h.setBlock(solid, Blocks.COBBLESTONE); // no change at all
        check(h, blockGeneration(h, solid) != before, "the block generation should still change");
        List<BlockPos> changed = takeDirty(h);
        check(h, changed.isEmpty(), "no geometry changed, but dirty: " + changed);
        h.succeed();
    }

    // ---- GEO-012: coalescing ----

    /** 100 writes to one position and one write to each of 100 positions: each position once. */
    @GameTest(template = AREA)
    public static void geo_012_changes_coalesce(GameTestHelper h) {
        reset(h);
        BlockPos busy = new BlockPos(0, 1, 0);
        for (int i = 0; i < 100; i++) {
            h.setBlock(busy, i % 2 == 0 ? STONE : AIR);
        }
        List<BlockPos> once = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            BlockPos p = new BlockPos(i % 10, 2 + i / 50, (i / 10) % 5 + 1);
            h.setBlock(p, STONE);
            once.add(p);
        }
        List<BlockPos> changed = takeDirty(h);
        check(h, changed.size() == 101, "expected 101 dirty positions, got " + changed.size());
        check(h, changed.contains(busy), "the busy position should be dirty once");
        check(h, changed.containsAll(once), "each written position should be dirty");
        check(h, takeDirty(h).isEmpty(), "a second drain should find nothing");
        h.succeed();
    }

    /**
     * {@link ChangeTracker#drain(ServerLevel)} returns every dirty section once, sorted, and
     * leaves the level clean. Alone in its batch, since it drains the whole level.
     */
    @GameTest(template = AREA, batch = "geo_012_drain")
    public static void geo_012_drain_level(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 2, 1);
        BlockPos b = new BlockPos(8, 6, 8);
        h.setBlock(a, STONE);
        h.setBlock(b, STONE);
        h.setBlock(a, AIR);
        h.setBlock(a, STONE);
        List<DirtySection> drained = ChangeTracker.drain(h.getLevel());
        for (int i = 1; i < drained.size(); i++) {
            check(
                    h,
                    SectionKeys.compare(drained.get(i - 1).key(), drained.get(i).key()) < 0,
                    "sorted, no repeats");
        }
        for (BlockPos rel : List.of(a, b)) {
            long key = SectionPos.asLong(h.absolutePos(rel));
            BlockPos abs = h.absolutePos(rel);
            boolean found = drained.stream()
                    .anyMatch(s -> s.key() == key && s.isDirty(abs.getX() & 15, abs.getY() & 15, abs.getZ() & 15));
            check(h, found, "the drain should include " + rel);
        }
        check(h, ChangeTracker.drain(h.getLevel()).isEmpty(), "the level should be clean after a drain");
        h.succeed();
    }

    // ---- snapshots ----

    /** A captured section decodes to the geometry ids of the world at capture time. */
    @GameTest(template = AREA)
    public static void core_045_section_snapshot_matches_world(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(4, 2, 4));
        h.setBlock(new BlockPos(4, 2, 4), Blocks.STONE_SLAB);
        h.setBlock(new BlockPos(5, 2, 4), Blocks.GLASS_PANE);
        long key = SectionPos.asLong(abs);
        check(h, key == SectionKeys.sectionOfBlock(abs.getX(), abs.getY(), abs.getZ()), "same section key layout");
        SectionCapture capture = SectionCapture.capture(level, key);
        check(h, capture != null, "a loaded section should be captured");
        ChunkPos chunk = new ChunkPos(abs);
        long blockGen = ChangeTracker.blockGeneration(level, chunk);
        SectionSnapshot snapshot = capture.decode();
        check(h, snapshot.blockGeneration() == blockGen, "the snapshot should carry the block generation");
        check(h, snapshot.lifecycleGeneration() != ChunkGenerations.UNLOADED, "the chunk should be tracked");
        SectionPos section = SectionPos.of(key);
        for (BlockPos p : BlockPos.betweenClosed(
                section.minBlockX(),
                section.minBlockY(),
                section.minBlockZ(),
                section.maxBlockX(),
                section.maxBlockY(),
                section.maxBlockZ())) {
            int id = Geometries.geometryId(level.getBlockState(p));
            if (snapshot.geometryId(p.getX() & 15, p.getY() & 15, p.getZ() & 15) != id) {
                h.fail("the snapshot differs from the world at " + p.toShortString());
            }
        }
        // The copy is private: later writes don't reach it.
        h.setBlock(new BlockPos(4, 2, 4), AIR);
        check(
                h,
                capture.decode().geometryId(abs.getX() & 15, abs.getY() & 15, abs.getZ() & 15)
                        == Geometries.geometryId(Blocks.STONE_SLAB.defaultBlockState()),
                "the snapshot should keep the slab placed before the capture");
        check(
                h,
                SectionCapture.capture(level, SectionKeys.section(abs.getX() >> 4, 10_000, abs.getZ() >> 4)) == null,
                "a section above the build height can't be captured");
        h.succeed();
    }
}
