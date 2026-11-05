package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.airflow.PressureBreaks;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.AirflowConfig;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.flow.FragileSettings;
import io.github.west3436.zas.engine.gas.GasConstants;
import io.github.west3436.zas.engine.scheduler.PressureBreak;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Pressure damage to blocks in a real level (FLOW-007, FLOW-015, FLOW-016), with what a paused zone or
 * an unloaded chunk across the block does (GEO-034, GEO-035).
 *
 * <p>The preset and the override are the whole server's, so these tests run in a batch of their own,
 * and each sets them and restores them within one game tick, driving the atmos ticks it needs with
 * {@link io.github.west3436.zas.simulation.AtmosService#tickNow}. Their areas are at z = ±16384. The
 * chunk unloads are the atmosphere's alone, as in {@link PausedZoneTests}.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PressureDamageTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "flow_007";
    private static final int EAST = 16;
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    /** A pane running north to south, in a wall of constant x. */
    private static final BlockState PANE = Blocks.GLASS_PANE
            .defaultBlockState()
            .setValue(CrossCollisionBlock.NORTH, true)
            .setValue(CrossCollisionBlock.SOUTH, true);

    private PressureDamageTests() {}

    // ---- helpers ------------------------------------------------------------------------

    private static Rooms rooms(GameTestHelper h, int index) {
        return new Rooms(h, h.getLevel(), new BlockPos(-16384 + 256 * index, 100, 16384));
    }

    /**
     * Runs a test's body under a preset with no override, the checker on, and no heat, so a pressure
     * stays as set: all within one game tick. Everything is restored after.
     */
    private static void under(GameTestHelper h, AtmospherePreset preset, Runnable body) {
        h.assertTrue(ZasServerConfig.SPEC.isLoaded(), "the server config isn't loaded");
        h.assertValueEqual(PresetOptions.overridden(), 0, "preset options overridden before the test");
        AtmospherePreset before = ZasServerConfig.preset();
        boolean checker = ZasServerConfig.CONSERVATION_CHECKER.get();
        try {
            ZasServerConfig.setPreset(preset);
            ZasServerConfig.CONSERVATION_CHECKER.set(true);
            Rooms.withoutHeat(body);
        } finally {
            PresetOptions.BLOCK_PRESSURE_DAMAGE.clearOverride();
            ZasServerConfig.CONSERVATION_CHECKER.set(checker);
            ZasServerConfig.setPreset(before);
        }
    }

    /** Runs a body with a vacuum as the End's ambient, as a level with nothing outside the window. */
    private static void inVacuum(GameTestHelper h, Consumer<ServerLevel> body) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum")));
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            body.accept(end);
        } finally {
            Ambients.overrideForTest(Level.END, null);
        }
    }

    /** A sealed room of 27 cells from x 2 to 4, with a window in its east wall at (5, 2, 2) and air beyond. */
    private static void windowRoom(Rooms r, BlockState window) {
        r.clear(0, 8);
        r.shell(1, 5);
        r.set(5, 2, 2, window);
    }

    /** Fills the zone at a point to a pressure, forming it if need be, as the API does. */
    private static void raise(Rooms r, int x, int y, int z, double kpa) {
        if (!r.zoned(x, y, z)) {
            r.settle(GasMutations.add(r.level, r.point(x, y, z), oxygen(0.001), source("flow_007")));
        }
        ZoneInfo zone = r.zone(x, y, z);
        double moles = (kpa - zone.mixture().pressure())
                * zone.volume()
                / (GasConstants.R * zone.mixture().temperature());
        r.settle(GasMutations.add(r.level, r.point(x, y, z), oxygen(moles), source("flow_007")));
        r.close(r.zone(x, y, z).mixture().pressure(), kpa, "the zone's pressure");
    }

    private static void holds(Rooms r, int x, int y, int z, BlockState window, String what) {
        r.h.assertValueEqual(r.level.getBlockState(r.abs(x, y, z)), window, what);
    }

    private static void broke(Rooms r, int x, int y, int z, String what) {
        r.h.assertTrue(r.level.getBlockState(r.abs(x, y, z)).isAir(), what);
    }

    /** The checker's last report finds every mole (CORE-073). */
    private static void conserved(Rooms r) {
        ConservationReport report = r.dimension().lastConservation();
        r.h.assertTrue(report != null && report.conserved(), "the checker reported " + report);
    }

    // ---- FLOW-007: what breaks ----------------------------------------------------------

    /**
     * Under Hardcore, glass with a zone on one side and the ambient on the other breaks above the
     * threshold and not below it, through vanilla's block breaking on the server thread, and the
     * stone around it doesn't (FLOW-007).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_007_glass_breaks_above_the_threshold_under_hardcore(GameTestHelper h) {
        under(h, AtmospherePreset.HARDCORE, () -> {
            Rooms r = rooms(h, 0);
            windowRoom(r, GLASS);
            double ambient = Atmospheres.at(r.level, r.abs(7, 2, 2)).mixture().pressure();
            raise(r, 3, 2, 2, ambient + 149);
            r.ticks(2);
            holds(r, 5, 2, 2, GLASS, "149 kPa over the ambient");
            raise(r, 3, 2, 2, ambient + 151);
            r.tick();
            broke(r, 5, 2, 2, "151 kPa over the ambient");
            h.assertTrue(r.dimension().lastApplyOnServerThread(), "applied off the server thread");
            h.assertFalse(
                    r.dimension()
                            .lastWorkerThread()
                            .equals(Thread.currentThread().getName()),
                    "judged on the server thread");
            holds(r, 5, 1, 2, Rooms.STONE, "the wall beside it");
            holds(r, 4, 0, 2, Rooms.STONE, "the floor");
            r.ticks(3);
            conserved(r);
        });
        h.succeed();
    }

    /** A pane's two halves are its two sides (GEO-004): it breaks as a block does (FLOW-007). */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_007_a_pane_breaks_above_the_threshold(GameTestHelper h) {
        under(h, AtmospherePreset.HARDCORE, () -> {
            Rooms r = rooms(h, 1);
            windowRoom(r, PANE);
            double ambient = Atmospheres.at(r.level, r.abs(7, 2, 2)).mixture().pressure();
            raise(r, 3, 2, 2, ambient + 149);
            r.ticks(2);
            holds(r, 5, 2, 2, PANE, "149 kPa over the ambient");
            raise(r, 3, 2, 2, ambient + 151);
            r.tick();
            broke(r, 5, 2, 2, "151 kPa over the ambient");
            r.ticks(3);
            conserved(r);
        });
        h.succeed();
    }

    /**
     * Glass between two zones: the difference between them counts, not either's with the ambient. It
     * breaks, the zones meet through the usual path and merge once close (CORE-023), and every mole is
     * accounted for (CORE-073).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_007_glass_between_two_zones_breaks_and_conserves_gas(GameTestHelper h) {
        under(h, AtmospherePreset.HARDCORE, () -> {
            Rooms r = rooms(h, 2);
            r.clear(0, 12);
            r.shell(1, 5);
            r.shell(5, 9);
            r.set(5, 2, 2, GLASS);
            // In steps, never 150 kPa apart: a room not yet zoned reads as the ambient.
            raise(r, 7, 2, 2, 240);
            raise(r, 3, 2, 2, 300);
            raise(r, 7, 2, 2, 300);
            raise(r, 3, 2, 2, 400);
            r.ticks(2);
            holds(r, 5, 2, 2, GLASS, "100 kPa between them, though each is far from the ambient");
            raise(r, 3, 2, 2, 451);
            double moles = total(r.zone(3, 2, 2)) + total(r.zone(7, 2, 2));
            r.tick();
            broke(r, 5, 2, 2, "151 kPa between them");
            for (int i = 0;
                    i < 100 && !r.zone(3, 2, 2).id().equals(r.zone(7, 2, 2).id());
                    i++) {
                r.tick();
                conserved(r);
            }
            h.assertValueEqual(r.zone(3, 2, 2).id(), r.zone(7, 2, 2).id(), "the rooms didn't merge");
            r.close(total(r.zone(3, 2, 2)), moles, "the merged zone's moles");
            h.assertValueEqual(r.zone(5, 2, 2).id(), r.zone(3, 2, 2).id(), "the broken window's cell");
        });
        h.succeed();
    }

    private static double total(ZoneInfo zone) {
        double sum = 0;
        for (double moles : zone.mixture().moles().values()) {
            sum += moles;
        }
        return sum;
    }

    /**
     * The default tag: every vanilla glass block and pane, with every state of each flagged, and not a
     * block of stone or iron bars (FLOW-007, OPS-002).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_007_the_default_tag_is_glass_and_glass_panes(GameTestHelper h) {
        List<Block> fragile = new ArrayList<>(List.of(Blocks.GLASS, Blocks.TINTED_GLASS, Blocks.GLASS_PANE));
        for (Block block : List.of(
                Blocks.WHITE_STAINED_GLASS,
                Blocks.ORANGE_STAINED_GLASS,
                Blocks.MAGENTA_STAINED_GLASS,
                Blocks.LIGHT_BLUE_STAINED_GLASS,
                Blocks.YELLOW_STAINED_GLASS,
                Blocks.LIME_STAINED_GLASS,
                Blocks.PINK_STAINED_GLASS,
                Blocks.GRAY_STAINED_GLASS,
                Blocks.LIGHT_GRAY_STAINED_GLASS,
                Blocks.CYAN_STAINED_GLASS,
                Blocks.PURPLE_STAINED_GLASS,
                Blocks.BLUE_STAINED_GLASS,
                Blocks.BROWN_STAINED_GLASS,
                Blocks.GREEN_STAINED_GLASS,
                Blocks.RED_STAINED_GLASS,
                Blocks.BLACK_STAINED_GLASS,
                Blocks.WHITE_STAINED_GLASS_PANE,
                Blocks.ORANGE_STAINED_GLASS_PANE,
                Blocks.MAGENTA_STAINED_GLASS_PANE,
                Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
                Blocks.YELLOW_STAINED_GLASS_PANE,
                Blocks.LIME_STAINED_GLASS_PANE,
                Blocks.PINK_STAINED_GLASS_PANE,
                Blocks.GRAY_STAINED_GLASS_PANE,
                Blocks.LIGHT_GRAY_STAINED_GLASS_PANE,
                Blocks.CYAN_STAINED_GLASS_PANE,
                Blocks.PURPLE_STAINED_GLASS_PANE,
                Blocks.BLUE_STAINED_GLASS_PANE,
                Blocks.BROWN_STAINED_GLASS_PANE,
                Blocks.GREEN_STAINED_GLASS_PANE,
                Blocks.RED_STAINED_GLASS_PANE,
                Blocks.BLACK_STAINED_GLASS_PANE)) {
            fragile.add(block);
        }
        GeometryTable table = Geometries.table();
        for (Block block : fragile) {
            h.assertTrue(block.defaultBlockState().is(Geometries.PRESSURE_FRAGILE), block + " isn't in the tag");
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                h.assertTrue(
                        table.palette().geometry(table.geometryId(state)).hasFlag(Geometry.FLAG_PRESSURE_FRAGILE),
                        state + " isn't flagged");
            }
        }
        for (Block block : List.of(Blocks.STONE, Blocks.IRON_BARS, Blocks.AIR, Blocks.ICE, Blocks.OAK_DOOR)) {
            h.assertFalse(block.defaultBlockState().is(Geometries.PRESSURE_FRAGILE), block + " is in the tag");
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                h.assertFalse(
                        table.palette().geometry(table.geometryId(state)).hasFlag(Geometry.FLAG_PRESSURE_FRAGILE),
                        state + " is flagged");
            }
        }
        h.succeed();
    }

    /**
     * A break is vanilla's without a tool: glass and panes drop nothing, since their loot needs silk
     * touch, and a waterlogged pane leaves its water. A block no longer fragile doesn't break, and a
     * chunk not settled, or a neighbouring chunk not at full status, stops it without loading that
     * chunk (GEO-034).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_007_a_break_is_vanillas_and_checks_the_world_first(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos corner = new BlockPos(-24576, 100, 16384);
        int cx = corner.getX() >> 4;
        int cz = corner.getZ() >> 4;
        LevelChunk chunk = level.getChunk(cx, cz);
        h.assertTrue(level.getChunkSource().getChunkNow(cx + 1, cz) == null, "the next chunk is already loaded");

        for (BlockState window : List.of(GLASS, PANE)) {
            h.assertTrue(Block.getDrops(window, level, corner, null).isEmpty(), window + " drops something");
        }
        h.assertFalse(
                Block.getDrops(Blocks.DIRT.defaultBlockState(), level, corner, null)
                        .isEmpty(),
                "dirt drops");

        // Set in the chunk alone: a neighbour update at the border would load the next chunk.
        BlockPos border = corner.offset(15, 0, 8);
        chunk.setBlockState(border, GLASS, false);
        PressureBreaks.apply(level, List.of(new PressureBreak(border.asLong(), 1, 0)), c -> true);
        h.assertValueEqual(level.getBlockState(border), GLASS, "broke beside a chunk not at full status");
        h.assertTrue(level.getChunkSource().getChunkNow(cx + 1, cz) == null, "the next chunk was pulled up");
        h.assertValueEqual(level.getBlockState(border), GLASS, "the border window");
        chunk.setBlockState(border, Blocks.AIR.defaultBlockState(), false);

        BlockPos inside = corner.offset(8, 0, 8);
        chunk.setBlockState(inside, GLASS, false);
        PressureBreaks.apply(level, List.of(new PressureBreak(inside.asLong(), 1, 0)), c -> false);
        h.assertValueEqual(level.getBlockState(inside), GLASS, "broke in a chunk not settled");
        PressureBreaks.apply(
                level, List.of(new PressureBreak(inside.asLong(), 1, 0)), c -> c != ChunkPos.asLong(cx, cz));
        h.assertValueEqual(level.getBlockState(inside), GLASS, "broke in its own unsettled chunk");
        int broken = PressureBreaks.apply(level, List.of(new PressureBreak(inside.asLong(), 1, 0)), c -> true);
        h.assertValueEqual(broken, 1, "blocks broken");
        h.assertTrue(level.getBlockState(inside).isAir(), "the glass didn't break");

        chunk.setBlockState(inside, Blocks.STONE.defaultBlockState(), false);
        broken = PressureBreaks.apply(level, List.of(new PressureBreak(inside.asLong(), 1, 0)), c -> true);
        h.assertValueEqual(broken, 0, "a block no longer fragile");
        h.assertValueEqual(level.getBlockState(inside), Blocks.STONE.defaultBlockState(), "the stone");

        chunk.setBlockState(inside, PANE.setValue(BlockStateProperties.WATERLOGGED, true), false);
        PressureBreaks.apply(level, List.of(new PressureBreak(inside.asLong(), 1, 0)), c -> true);
        h.assertTrue(level.getFluidState(inside).is(Fluids.WATER), "the waterlogged pane's water");
        chunk.setBlockState(inside, Blocks.AIR.defaultBlockState(), false);
        h.succeed();
    }

    // ---- FLOW-015: the default threshold ------------------------------------------------

    /** The default threshold is 150 kPa, in the config and in what the engine gets (FLOW-015). */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_015_the_default_threshold_is_150_kpa(GameTestHelper h) {
        h.assertValueEqual(AirflowConfig.FRAGILE_KPA.getDefault(), 150.0, "the config's default");
        h.assertValueEqual(FragileSettings.DEFAULT_THRESHOLD_KPA, 150.0, "the engine's default");
        under(h, AtmospherePreset.HARDCORE, () -> {
            h.assertValueEqual(AirflowConfig.fragile().thresholdKpa(), 150.0, "under Hardcore");
            h.assertValueEqual(
                    AirflowConfig.fragile().maxBreaks(), FragileSettings.DEFAULT_MAX_BREAKS, "the cap under Hardcore");
        });
        h.succeed();
    }

    /** A window holds 101 kPa against a vacuum and breaks at 160 kPa, glass and pane alike (FLOW-015). */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_015_a_window_holds_101_kpa_against_vacuum_and_breaks_at_160(GameTestHelper h) {
        under(
                h,
                AtmospherePreset.HARDCORE,
                () -> inVacuum(h, end -> {
                    int index = 0;
                    for (BlockState window : List.of(GLASS, PANE)) {
                        Rooms r = new Rooms(h, end, new BlockPos(-16384 + 256 * index++, 100, -16384));
                        windowRoom(r, window);
                        r.tick();
                        r.close(Atmospheres.at(end, r.abs(7, 2, 2)).mixture().pressure(), 0, "the far side's pressure");
                        raise(r, 3, 2, 2, 101);
                        r.ticks(3);
                        holds(r, 5, 2, 2, window, "101 kPa against a vacuum");
                        raise(r, 3, 2, 2, 160);
                        r.tick();
                        broke(r, 5, 2, 2, "160 kPa against a vacuum");
                        r.ticks(3);
                        conserved(r);
                    }
                }));
        h.succeed();
    }

    // ---- FLOW-016: the preset and its override -------------------------------------------

    /** Under Normal, glass holds any difference, and the override turns the damage on (FLOW-016). */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_016_normal_holds_and_the_override_turns_damage_on(GameTestHelper h) {
        under(h, AtmospherePreset.NORMAL, () -> {
            Rooms r = rooms(h, 3);
            windowRoom(r, GLASS);
            raise(r, 3, 2, 2, 1000);
            r.ticks(3);
            holds(r, 5, 2, 2, GLASS, "under Normal");
            PresetOptions.BLOCK_PRESSURE_DAMAGE.setOverride(true);
            r.tick();
            broke(r, 5, 2, 2, "under Normal, overridden on");
        });
        h.succeed();
    }

    /** Hardcore's default can be overridden off too, and clearing that goes back to it (FLOW-016). */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_016_hardcore_can_override_damage_off(GameTestHelper h) {
        under(h, AtmospherePreset.HARDCORE, () -> {
            Rooms r = rooms(h, 4);
            windowRoom(r, GLASS);
            PresetOptions.BLOCK_PRESSURE_DAMAGE.setOverride(false);
            raise(r, 3, 2, 2, 1000);
            r.ticks(3);
            holds(r, 5, 2, 2, GLASS, "under Hardcore, overridden off");
            PresetOptions.BLOCK_PRESSURE_DAMAGE.clearOverride();
            r.tick();
            broke(r, 5, 2, 2, "under Hardcore");
        });
        h.succeed();
    }

    // ---- paused zones and unloaded chunks ------------------------------------------------

    /**
     * A paused zone across the window holds it, whatever its last-known pressure says: nothing forces a
     * chunk, and when the zone resumes the numbers then decide (GEO-034, GEO-035).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_035_a_paused_zone_across_a_window_holds_it_until_it_resumes(GameTestHelper h) {
        LongOpenHashSet forced = new LongOpenHashSet(h.getLevel().getForcedChunks());
        under(h, AtmospherePreset.HARDCORE, () -> {
            Rooms r = rooms(h, 5);
            r.clear(0, 22);
            r.shell(1, 7); // west, all in one chunk
            r.shell(7, 20); // east, across the chunk border at x 16
            r.set(7, 2, 2, GLASS);
            PresetOptions.BLOCK_PRESSURE_DAMAGE.setOverride(false);
            raise(r, 3, 2, 2, 400);
            raise(r, 12, 2, 2, 110);
            byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
            r.tick();
            h.assertTrue(r.zone(12, 2, 2).paused(), "the far side didn't pause");
            h.assertFalse(r.zone(3, 2, 2).paused(), "the near side paused");
            PresetOptions.BLOCK_PRESSURE_DAMAGE.clearOverride();
            r.ticks(3);
            holds(r, 7, 2, 2, GLASS, "290 kPa against a paused zone's last-known state");
            r.dimension().simulateLoad(r.chunk(EAST), saved);
            r.ticks(2);
            h.assertFalse(r.zone(12, 2, 2).paused(), "the far side didn't resume");
            broke(r, 7, 2, 2, "the same difference, the far side live");
            r.ticks(2);
            conserved(r);
        });
        h.assertValueEqual(new LongOpenHashSet(h.getLevel().getForcedChunks()), forced, "forced chunks");
        h.succeed();
    }

    /** A paused zone against the open air: no live number, so no break until it resumes (GEO-035). */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_035_a_paused_zone_holds_its_window_against_the_ambient(GameTestHelper h) {
        under(h, AtmospherePreset.HARDCORE, () -> {
            Rooms r = rooms(h, 6);
            r.clear(4, 22);
            r.shell(7, 20); // across the chunk border at x 16
            r.set(7, 2, 2, GLASS);
            PresetOptions.BLOCK_PRESSURE_DAMAGE.setOverride(false);
            raise(r, 12, 2, 2, 400);
            byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
            r.tick();
            h.assertTrue(r.zone(12, 2, 2).paused(), "the room didn't pause");
            PresetOptions.BLOCK_PRESSURE_DAMAGE.clearOverride();
            r.ticks(3);
            holds(r, 7, 2, 2, GLASS, "a paused zone against the ambient");
            r.dimension().simulateLoad(r.chunk(EAST), saved);
            r.ticks(2);
            broke(r, 7, 2, 2, "resumed");
            r.ticks(2);
            conserved(r);
        });
        h.succeed();
    }

    /**
     * An unloaded chunk across the window holds it: nothing is known there, and nothing is loaded to
     * find out. It breaks once the chunk loads and shows open air (GEO-034).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_034_an_unloaded_chunk_across_a_window_holds_it(GameTestHelper h) {
        LongOpenHashSet forced = new LongOpenHashSet(h.getLevel().getForcedChunks());
        under(h, AtmospherePreset.HARDCORE, () -> {
            Rooms r = rooms(h, 7);
            r.clear(9, 18);
            r.shell(11, 15);
            r.set(15, 2, 2, GLASS);
            byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
            raise(r, 13, 2, 2, 400);
            r.ticks(3);
            holds(r, 15, 2, 2, GLASS, "with an unloaded chunk beyond it");
            r.dimension().simulateLoad(r.chunk(EAST), saved);
            r.ticks(3);
            broke(r, 15, 2, 2, "the chunk loaded, open air beyond it");
        });
        h.assertValueEqual(new LongOpenHashSet(h.getLevel().getForcedChunks()), forced, "forced chunks");
        h.succeed();
    }

    // ---- a chain, and the cap ------------------------------------------------------------

    /**
     * A chain as pressure moves: each window lets the gas into the next room, the pressure falls, and
     * the next window goes or holds on the new number. One at a time, with the checker on throughout.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_007_a_chain_of_windows_fails_in_sequence(GameTestHelper h) {
        under(
                h,
                AtmospherePreset.HARDCORE,
                () -> inVacuum(h, end -> {
                    Rooms r = new Rooms(h, end, new BlockPos(-16384 + 256 * 2, 100, -16384));
                    r.clear(0, 20);
                    r.shell(1, 5);
                    r.shell(5, 9);
                    r.shell(9, 13);
                    r.shell(13, 17);
                    r.set(5, 2, 2, GLASS);
                    r.set(9, 2, 2, GLASS);
                    r.set(13, 2, 2, GLASS);
                    raise(r, 3, 2, 2, 400);
                    broke(r, 5, 2, 2, "the first window, at 400 kPa");
                    holds(r, 9, 2, 2, GLASS, "the second, with vacuum on both sides");
                    r.tick();
                    broke(r, 9, 2, 2, "the second, at about half");
                    holds(r, 13, 2, 2, GLASS, "the third, with vacuum on both sides");
                    r.ticks(3);
                    holds(r, 13, 2, 2, GLASS, "the third, under the threshold now");
                    conserved(r);
                }));
        h.succeed();
    }

    /**
     * However many windows fail together, a tick breaks no more than the cap, and the rest go in later
     * ticks while the pressure says so. Logs what a full tick's breaks cost the server thread: the
     * number is for the benchmark notes, not a limit to test.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_007_a_tick_breaks_no_more_than_the_cap(GameTestHelper h) {
        under(h, AtmospherePreset.HARDCORE, () -> {
            Rooms r = rooms(h, 8);
            r.clear(0, 0, 8, 16);
            r.box(1, 0, 5, 16);
            List<BlockPos> windows = new ArrayList<>();
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 15; z++) {
                    r.set(5, y, z, GLASS);
                    windows.add(r.abs(5, y, z));
                }
            }
            int cap = AirflowConfig.fragile().maxBreaks();
            h.assertTrue(windows.size() > cap, "fewer windows than the cap");
            raise(r, 3, 2, 8, 10_000);
            int before = windows.size();
            int left = standing(r, windows);
            h.assertValueEqual(before - left, cap, "windows broken in the tick");
            r.tick();
            int next = standing(r, windows);
            h.assertTrue(left - next <= cap, "the next tick broke " + (left - next));
            conserved(r);
        });
        h.succeed();
    }

    private static int standing(Rooms r, List<BlockPos> windows) {
        int n = 0;
        for (BlockPos p : windows) {
            if (r.level.getBlockState(p).is(Blocks.GLASS)) {
                n++;
            }
        }
        return n;
    }

    /** What a tick's worth of breaks costs the server thread, for the benchmark notes. Not a limit. */
    @GameTest(template = "empty", batch = BATCH)
    public static void flow_007_measures_what_a_full_tick_of_breaks_costs(GameTestHelper h) {
        Rooms r = rooms(h, 9);
        r.clear(0, 40);
        List<PressureBreak> breaks = new ArrayList<>();
        for (int x = 1; x <= FragileSettings.DEFAULT_MAX_BREAKS; x++) {
            r.set(x, 2, 2, GLASS);
            breaks.add(new PressureBreak(r.abs(x, 2, 2).asLong(), 1, 0));
        }
        long start = System.nanoTime();
        int broken = PressureBreaks.apply(r.level, breaks, c -> true);
        long nanos = System.nanoTime() - start;
        h.assertValueEqual(broken, breaks.size(), "blocks broken");
        LOGGER.info("flow_007: {} pressure breaks took the server thread {} us", broken, nanos / 1000);
        h.succeed();
    }
}
