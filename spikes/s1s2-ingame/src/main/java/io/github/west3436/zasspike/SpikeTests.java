package io.github.west3436.zasspike;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(SpikeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpikeTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String HOOKS = System.getProperty("zasspike.hooks", "chunk,poi");
    private static final List<String> OUT = new ArrayList<>();

    private SpikeTests() {}

    static void out(String fmt, Object... args) {
        String line = String.format(fmt, args);
        LOG.info("SPIKE {}", line);
        synchronized (OUT) {
            OUT.add(line);
            try {
                Files.write(Path.of("zasspike-results-" + HOOKS.replace(',', '-') + ".txt"), OUT);
            } catch (IOException e) {
                LOG.warn("could not write results", e);
            }
        }
    }

    // ------------------------------------------------------------------ S1 coverage

    record Step(String name, int waitTicks, java.util.function.Consumer<GameTestHelper> action) {}

    @GameTest(template = "empty", timeoutTicks = 400, batch = "a_coverage")
    public static void s1_hook_coverage(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).offset(4, 0, 4);
        // A stone floor under the work area, so fluids and pistons behave.
        for (int x = -2; x < 40; x++) {
            for (int z = -2; z < 12; z++) {
                level.setBlock(o.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("Level#setBlock flags=3 (player/mod style)", 1,
                t -> level.setBlock(o.offset(0, 0, 0), Blocks.STONE.defaultBlockState(), 3)));
        steps.add(new Step("Level#setBlock flags=2|16 (no neighbour updates)", 1,
                t -> level.setBlock(o.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), 2 | 16)));
        steps.add(new Step("LevelChunk#setBlockState direct (WorldEdit style)", 1,
                t -> ((LevelChunk) level.getChunk(o.offset(2, 0, 0))).setBlockState(o.offset(2, 0, 0),
                        Blocks.STONE.defaultBlockState(), false)));
        steps.add(new Step("/fill 3x3x3 stone", 1, t -> command(level, o.offset(4, 0, 0), "fill ~ ~ ~ ~2 ~2 ~2 minecraft:stone")));
        steps.add(new Step("/setblock", 1, t -> command(level, o.offset(8, 0, 0), "setblock ~ ~ ~ minecraft:glass")));
        steps.add(new Step("/place template igloo/top", 2,
                t -> command(level, o.offset(10, 0, 0), "place template minecraft:igloo/top ~ ~ ~")));
        steps.add(new Step("explosion (power 3) in a 5x5x5 glass cube", 2, t -> {
            BlockPos c = o.offset(24, 0, 0);
            for (BlockPos p : BlockPos.betweenClosed(c, c.offset(4, 4, 4))) {
                level.setBlock(p, Blocks.GLASS.defaultBlockState(), 2);
            }
            ChangeQueue.CHUNK.clear();
            ChangeQueue.POI.clear();
            ChangeQueue.CHUNK.total = 0;
            ChangeQueue.POI.total = 0;
            level.explode(null, c.getX() + 2.5, c.getY() + 2.5, c.getZ() + 2.5, 3f, Level.ExplosionInteraction.TNT);
        }));
        steps.add(new Step("piston pushes a block (redstone trigger)", 12, t -> {
            BlockPos p = o.offset(32, 0, 0);
            level.setBlock(p, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), 3);
            level.setBlock(p.east(), Blocks.STONE.defaultBlockState(), 3);
            ChangeQueue.CHUNK.clear();
            ChangeQueue.POI.clear();
            ChangeQueue.CHUNK.total = 0;
            ChangeQueue.POI.total = 0;
            level.setBlock(p.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            t.runAfterDelay(11, () -> out("S1   (piston check: block two east is %s)", level.getBlockState(p.east(2)).getBlock()));
        }));
        steps.add(new Step("door opened via DoorBlock#setOpen", 1, t -> {
            BlockPos p = o.offset(0, 0, 6);
            level.setBlock(p, Blocks.OAK_DOOR.defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            ChangeQueue.CHUNK.clear();
            ChangeQueue.POI.clear();
            ChangeQueue.CHUNK.total = 0;
            ChangeQueue.POI.total = 0;
            BlockState s = level.getBlockState(p);
            ((DoorBlock) Blocks.OAK_DOOR).setOpen(null, level, s, p, true);
        }));
        steps.add(new Step("water source spreading for 40 ticks", 40,
                t -> level.setBlock(o.offset(10, 0, 8), Blocks.WATER.defaultBlockState(), 3)));
        runSteps(h, steps, 0, new LinkedHashMap<>());
    }

    private static void command(ServerLevel level, BlockPos at, String cmd) {
        var src = level.getServer().createCommandSourceStack().withLevel(level).withPosition(at.getCenter())
                .withPermission(4).withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(src, cmd);
    }

    private static void runSteps(GameTestHelper h, List<Step> steps, int i, Map<String, long[]> results) {
        if (i == steps.size()) {
            out("S1 coverage (hooks=%s): change source | LevelChunk#setBlockState hook | ServerLevel#onBlockStateChange hook", HOOKS);
            results.forEach((k, v) -> out("S1   %-52s | %6d | %6d", k, v[0], v[1]));
            h.succeed();
            return;
        }
        Step s = steps.get(i);
        ChangeQueue.CHUNK.clear();
        ChangeQueue.POI.clear();
        ChangeQueue.CHUNK.total = 0;
        ChangeQueue.POI.total = 0;
        s.action().accept(h);
        h.runAfterDelay(s.waitTicks(), () -> {
            results.put(s.name(), new long[] {ChangeQueue.CHUNK.total, ChangeQueue.POI.total});
            runSteps(h, steps, i + 1, results);
        });
    }

    // ------------------------------------------------------------------ S1 cost

    @GameTest(template = "empty", timeoutTicks = 2000, batch = "b_cost")
    public static void s1_hook_cost(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).offset(-40, 0, -40);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int side = 32;
        // Warm up, then time alternating writes over a 32x8x32 volume.
        for (int round = 0; round < 3; round++) {
            writes(level, o, side, stone, air, 3);
        }
        int rounds = 10;
        long best3 = Long.MAX_VALUE;
        long best2 = Long.MAX_VALUE;
        long bestDirect = Long.MAX_VALUE;
        int n = side * side * 8 * 2;
        for (int round = 0; round < rounds; round++) {
            best3 = Math.min(best3, writes(level, o, side, stone, air, 3));
            best2 = Math.min(best2, writes(level, o, side, stone, air, 2 | 16));
            bestDirect = Math.min(bestDirect, directWrites(level, o, side, stone, air));
        }
        ChangeQueue.CHUNK.clear();
        ChangeQueue.POI.clear();
        out("S1 cost (hooks=%s): Level#setBlock flags=3 %.1f ns/change; flags=18 %.1f ns/change; direct LevelChunk#setBlockState %.1f ns/change (best of %d rounds of %d)",
                HOOKS, best3 / (double) n, best2 / (double) n, bestDirect / (double) n, rounds, n);

        // Coalescing cost: sort + distinct over 100k queued positions.
        for (int i = 0; i < 100_000; i++) {
            ChangeQueue.CHUNK.enqueue(level, o.offset(i % 64, (i / 64) % 8, i / 512));
        }
        long t0 = System.nanoTime();
        int distinct = ChangeQueue.CHUNK.drainDistinct();
        out("S1 coalesce: 100,000 queued -> %d distinct in %.2f ms", distinct, (System.nanoTime() - t0) / 1e6);
        h.succeed();
    }

    private static long writes(ServerLevel level, BlockPos o, int side, BlockState a, BlockState b, int flags) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long t0 = System.nanoTime();
        for (int pass = 0; pass < 2; pass++) {
            BlockState s = pass == 0 ? a : b;
            for (int x = 0; x < side; x++) {
                for (int y = 0; y < 8; y++) {
                    for (int z = 0; z < side; z++) {
                        level.setBlock(p.setWithOffset(o, x, y, z), s, flags);
                    }
                }
            }
        }
        long dt = System.nanoTime() - t0;
        ChangeQueue.CHUNK.clear();
        ChangeQueue.POI.clear();
        return dt;
    }

    private static long directWrites(ServerLevel level, BlockPos o, int side, BlockState a, BlockState b) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long t0 = System.nanoTime();
        for (int pass = 0; pass < 2; pass++) {
            BlockState s = pass == 0 ? a : b;
            for (int x = 0; x < side; x++) {
                for (int y = 0; y < 8; y++) {
                    for (int z = 0; z < side; z++) {
                        p.setWithOffset(o, x, y, z);
                        level.getChunkAt(p).setBlockState(p, s, false);
                    }
                }
            }
        }
        long dt = System.nanoTime() - t0;
        ChangeQueue.CHUNK.clear();
        ChangeQueue.POI.clear();
        return dt;
    }

    // ------------------------------------------------------------------ S1/S5 snapshot cost

    @GameTest(template = "empty", timeoutTicks = 400, batch = "c_snapshot")
    public static void s5_section_snapshot_cost(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        // Make some sections varied: a hollow stone room with glass and slabs.
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-20, 0, -20), o.offset(20, 15, 20))) {
            int k = Math.floorMod(p.getX() * 31 + p.getY() * 17 + p.getZ() * 7, 11);
            BlockState s = k == 0 ? Blocks.GLASS.defaultBlockState()
                    : k == 1 ? Blocks.STONE_SLAB.defaultBlockState()
                    : k < 6 ? Blocks.AIR.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            level.setBlock(p, s, 2 | 16);
        }
        List<LevelChunkSection> sections = new ArrayList<>();
        int cx = o.getX() >> 4;
        int cz = o.getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                LevelChunk c = level.getChunk(cx + dx, cz + dz);
                for (LevelChunkSection s : c.getSections()) {
                    if (!s.hasOnlyAir()) {
                        sections.add(s);
                    }
                }
            }
        }
        // Server thread: copy. Then decode to per-state geometry ids (worker side).
        int reps = 50;
        long copyNs = Long.MAX_VALUE;
        List<PalettedContainer<BlockState>> copies = new ArrayList<>();
        for (int r = 0; r < reps; r++) {
            copies.clear();
            long t0 = System.nanoTime();
            for (LevelChunkSection s : sections) {
                copies.add(s.getStates().copy());
            }
            copyNs = Math.min(copyNs, System.nanoTime() - t0);
        }
        int[] geoIdByState = new int[Block.BLOCK_STATE_REGISTRY.size()];
        short[] decoded = new short[4096];
        long decodeNs = Long.MAX_VALUE;
        for (int r = 0; r < reps; r++) {
            long t0 = System.nanoTime();
            for (PalettedContainer<BlockState> c : copies) {
                for (int i = 0; i < 4096; i++) {
                    decoded[i] = (short) geoIdByState[Block.getId(c.get(i & 15, i >> 8, (i >> 4) & 15))];
                }
            }
            decodeNs = Math.min(decodeNs, System.nanoTime() - t0);
        }
        out("S5 section snapshot: %d non-empty sections in 7x7 chunks; PalettedContainer#copy %.2f us/section (server thread); decode to geometry ids %.1f us/section (worker)",
                sections.size(), copyNs / 1e3 / sections.size(), decodeNs / 1e3 / sections.size());
        h.succeed();
    }

    // ------------------------------------------------------------------ S2

    @GameTest(template = "empty", timeoutTicks = 2000, batch = "d_s2")
    public static void s2_derive_all_states(GameTestHelper h) {
        int states = Block.BLOCK_STATE_REGISTRY.size();
        int failures = 0;
        int positional = 0;
        int dynamic = 0;
        int multi = 0;
        int full = 0;
        int empty = 0;
        Map<Long, SealDerivation.Geometry> unique = new HashMap<>();
        Runtime rt = Runtime.getRuntime();
        // Warm-up pass, then a timed pass.
        long elapsed = 0;
        for (int pass = 0; pass < 2; pass++) {
            unique.clear();
            failures = positional = dynamic = multi = full = empty = 0;
            long t0 = System.nanoTime();
            for (BlockState s : Block.BLOCK_STATE_REGISTRY) {
                try {
                    VoxelShape shape = SealDerivation.shape(s, BlockPos.ZERO);
                    if (s.getBlock().hasDynamicShape()) {
                        dynamic++;
                    }
                    VoxelShape other = SealDerivation.shape(s, new BlockPos(7, 0, 13));
                    if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape, other,
                            net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME)) {
                        positional++;
                    }
                    SealDerivation.Geometry g = SealDerivation.derive(shape);
                    unique.putIfAbsent(g.key(), g);
                    if (g.subCells() > 1) {
                        multi++;
                    }
                    if (g.freeVoxels() == 0) {
                        full++;
                    }
                    if (g.freeVoxels() == 4096) {
                        empty++;
                    }
                } catch (Throwable t) {
                    failures++;
                }
            }
            elapsed = System.nanoTime() - t0;
        }
        out("S2: %d block states derived in %.1f ms (%.1f us/state); %d unique geometries; %d empty, %d full, %d with >1 sub-cell; %d positional, %d declare dynamicShape; %d failures",
                states, elapsed / 1e6, elapsed / 1e3 / states, unique.size(), empty, full, multi, positional, dynamic, failures);
        long bytes = (long) states * 4 + unique.size() * (24L * 8 + 64);
        // Variant: fetch shapes only, and derive once per distinct VoxelShape instance.
        java.util.IdentityHashMap<VoxelShape, SealDerivation.Geometry> byShape = new java.util.IdentityHashMap<>();
        long tf0 = System.nanoTime();
        for (BlockState s : Block.BLOCK_STATE_REGISTRY) {
            SealDerivation.shape(s, BlockPos.ZERO);
        }
        long fetch = System.nanoTime() - tf0;
        long tc0 = System.nanoTime();
        for (BlockState s : Block.BLOCK_STATE_REGISTRY) {
            byShape.computeIfAbsent(SealDerivation.shape(s, BlockPos.ZERO), SealDerivation::derive);
        }
        long cached = System.nanoTime() - tc0;
        out("S2: shape fetch only %.1f ms; derive once per distinct VoxelShape instance %.1f ms (%d distinct instances)",
                fetch / 1e6, cached / 1e6, byShape.size());
        out("S2: memory estimate %.1f KiB (4 B id per state + %d unique x ~256 B)", bytes / 1024.0, unique.size());
        out("S2: free volume and sub-cells for selected states (faces DUNSWE: # sealed, . open, ~ partial)");
        String[] picks = {"glass_pane", "iron_bars", "cobblestone_wall", "oak_fence", "oak_slab", "oak_stairs", "oak_door",
                "oak_trapdoor", "chest", "cactus", "bamboo", "scaffolding", "powder_snow", "snow", "farmland", "dirt_path",
                "glass", "oak_leaves", "hopper", "lectern", "moving_piston", "shulker_box", "pointed_dripstone", "chain",
                "lantern", "anvil", "cauldron", "composter", "end_portal_frame", "piston_head", "water", "lava", "barrier",
                "light", "sculk_sensor", "decorated_pot", "big_dripleaf"};
        for (String id : picks) {
            Block b = BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id));
            Map<String, Integer> seen = new LinkedHashMap<>();
            for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                SealDerivation.Geometry g = SealDerivation.derive(SealDerivation.shape(s, BlockPos.ZERO));
                String desc = String.format("faces %s free %4.0f L sub-cells %d %s", g.faceSummary(), g.freeVoxels() * 1000.0 / 4096,
                        g.subCells(), java.util.Arrays.toString(java.util.Arrays.stream(g.subCellVoxels()).map(v -> (int) Math.round(v * 1000.0 / 4096)).toArray()));
                seen.merge(desc, 1, Integer::sum);
            }
            seen.forEach((d, c) -> out("S2   %-18s x%-3d %s", id, c, d));
        }
        out("S2: heap in use after derivation %d MiB", (rt.totalMemory() - rt.freeMemory()) >> 20);
        h.succeed();
    }
}
