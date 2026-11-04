package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.change.TriggerStates;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.life.Photosynthesis;
import io.github.west3436.zas.life.Respiration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * S-11, the Hardcore greenhouse, and photosynthesis (CONT-053, CONT-057): under Hardcore planting
 * crops in a sealed glass greenhouse forms a zone and lit crops turn CO₂ into O₂; under Normal the
 * same greenhouse stays unzoned, and plants act only in zones that exist.
 *
 * <p>A room is a sealed box with a 3 × 3 × 3 interior, of glass for sky light or stone for a room lit
 * only by a light block. Light takes a few game ticks to spread, so each test builds its rooms, waits
 * for the light it needs to show, and then does the rest within one game tick: the preset and the
 * time of day are the whole server's, and a test sets them and restores them within that tick, driving
 * the atmos ticks and passes it needs itself. So the tests have a batch of their own. A pass acts on
 * every lit zone in the dimension, so a test only reads its own room.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhotosynthesisTests {
    private static final String BATCH = "s_11";

    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation CO2 = Respiration.CARBON_DIOXIDE;

    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState FARMLAND = Blocks.FARMLAND.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState WHEAT = Blocks.WHEAT.defaultBlockState();
    private static final BlockState SAPLING = Blocks.OAK_SAPLING.defaultBlockState();
    private static final BlockState LEAVES =
            Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState LIGHT_8 = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 8);

    /** The time of day when the sky is at its brightest, and at its darkest. */
    private static final long NOON = 6000;

    private static final long MIDNIGHT = 18000;

    /** CONT-057's table, in mol/h at light 15. */
    private static final double NORMAL_CROP = 0.125;

    private static final double NORMAL_LEAF = 0.0625;
    private static final double HARDCORE_CROP = 0.0625;
    private static final double HARDCORE_LEAF = 0.03125;

    private PhotosynthesisTests() {}

    // ---- S-11 ---------------------------------------------------------------------------

    /**
     * S-11 under Hardcore: planting crops in a sealed glass greenhouse forms a zone holding the
     * ambient air, and under the sky the lit crops turn CO₂ into the same moles of O₂ (CORE-056,
     * CONT-053).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void s_11_hardcore_greenhouse_forms_a_zone_and_lit_crops_make_o2(GameTestHelper h) {
        Rooms r = rooms(h, 0);
        greenhouse(r, 0);
        h.startSequence().thenWaitUntil(() -> lightIs(r, 2, 1, 2, 15)).thenExecute(() -> {
            withPreset(
                    h,
                    AtmospherePreset.HARDCORE,
                    () -> atTime(r, NOON, () -> {
                        h.assertFalse(r.zoned(2, 3, 2), "the greenhouse is zoned before planting");
                        plantRows(r, 0);
                        ZoneInfo formed = formed(r, 2, 3, 2);
                        h.assertTrue(formed.enclosed(), "the greenhouse's zone isn't enclosed");
                        h.assertValueEqual(formed.mixture().moles(CO2), 0.0, "CO2 in the air the zone formed with");

                        // Nothing to turn yet: the air holds no CO2.
                        h.assertFalse(
                                Photosynthesis.stepNow(r.level)
                                        .containsKey(formed.id().value()),
                                "plants sent a removal with no CO2");

                        addCo2(r, 2, 3, 2, 1);
                        double o2 = o2(r, 2, 3, 2);
                        step(r);
                        double made = 9 * perPass(HARDCORE_CROP, 15);
                        r.close(co2(r, 2, 3, 2), 1 - made, "CO2 after one pass of 9 lit crops");
                        r.close(o2(r, 2, 3, 2), o2 + made, "O2 after one pass of 9 lit crops");
                    }));
            h.succeed();
        });
    }

    /** S-11 under Normal: the same greenhouse, planted the same way, stays unzoned (CORE-055, CORE-056). */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void s_11_normal_greenhouse_stays_unzoned(GameTestHelper h) {
        Rooms r = rooms(h, 1);
        greenhouse(r, 0);
        h.startSequence().thenWaitUntil(() -> lightIs(r, 2, 1, 2, 15)).thenExecute(() -> {
            withPreset(
                    h,
                    AtmospherePreset.NORMAL,
                    () -> atTime(r, NOON, () -> {
                        plantRows(r, 0);
                        r.ticks(4);
                        h.assertFalse(r.zoned(2, 3, 2), "planting formed a zone under Normal");
                        step(r);
                        r.ticks(2);
                        h.assertFalse(r.zoned(2, 3, 2), "photosynthesis formed a zone under Normal");
                    }));
            h.succeed();
        });
    }

    /**
     * Under Normal plants act inside a zone that exists: CO₂ from the API forms the greenhouse's zone
     * (CORE-055), plants placed before it count, and they make O₂ at Normal's rate (S-11, CONT-053).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void cont_053_under_normal_plants_act_in_a_zone_that_exists(GameTestHelper h) {
        Rooms r = rooms(h, 2);
        greenhouse(r, 0);
        h.startSequence().thenWaitUntil(() -> lightIs(r, 2, 1, 2, 15)).thenExecute(() -> {
            withPreset(
                    h,
                    AtmospherePreset.NORMAL,
                    () -> atTime(r, NOON, () -> {
                        plantRows(r, 0);
                        r.ticks(2);
                        h.assertFalse(r.zoned(2, 3, 2), "planting formed a zone under Normal");
                        addCo2(r, 2, 3, 2, 1);
                        double o2 = o2(r, 2, 3, 2);
                        step(r);
                        double made = 9 * perPass(NORMAL_CROP, 15);
                        r.close(co2(r, 2, 3, 2), 1 - made, "CO2 under Normal");
                        r.close(o2(r, 2, 3, 2), o2 + made, "O2 under Normal");
                    }));
            h.succeed();
        });
    }

    // ---- CONT-053 -----------------------------------------------------------------------

    /**
     * Lit crops turn CO₂ into equal moles of O₂, limited by the CO₂ there is, and stop when it runs
     * out. The change is the ledger's {@code LIFE} (CORE-036), and the checker finds it explained.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void cont_053_lit_crops_stop_when_co2_runs_out(GameTestHelper h) {
        Rooms r = rooms(h, 3);
        greenhouse(r, 0);
        h.startSequence().thenWaitUntil(() -> lightIs(r, 2, 1, 2, 15)).thenExecute(() -> {
            withPreset(
                    h,
                    AtmospherePreset.NORMAL,
                    () -> atTime(r, NOON, () -> {
                        plantRows(r, 0);
                        double pass = 9 * perPass(NORMAL_CROP, 15);
                        double given = 1.5 * pass;
                        addCo2(r, 2, 3, 2, given);
                        double o2 = o2(r, 2, 3, 2);
                        double total = r.zone(2, 3, 2).mixture().totalMoles();

                        // A whole pass's worth, then only the half that is left.
                        int co2Index = r.dimension().registry().indexOf(CO2.toString());
                        Map<Long, CompletableFuture<GasParcel>> sent = Photosynthesis.stepNow(r.level);
                        h.assertTrue(sent.containsKey(r.zone(2, 3, 2).id().value()), "no removal sent to the zone");
                        CompletableFuture<ConservationReport> check =
                                r.dimension().checkConservation();
                        r.tick();
                        ConservationReport report = check.join();
                        h.assertTrue(report.conserved(), "unexplained change: " + report.violations());
                        // At least this room's pass: other tests' lit rooms may have sent theirs.
                        double recorded = report.recorded(LedgerCategory.LIFE, co2Index);
                        h.assertTrue(recorded <= -pass * (1 - 1e-9), "CO2 recorded under LIFE: " + recorded);
                        r.tick();
                        r.close(co2(r, 2, 3, 2), given - pass, "CO2 after a whole pass");
                        r.close(o2(r, 2, 3, 2), o2 + pass, "O2 after a whole pass");

                        step(r);
                        r.close(co2(r, 2, 3, 2), 0, "CO2 after it ran out");
                        r.close(o2(r, 2, 3, 2), o2 + given, "O2: all the CO2, and no more");
                        r.close(r.zone(2, 3, 2).mixture().totalMoles(), total, "the moles in the zone");

                        double last = o2(r, 2, 3, 2);
                        h.assertFalse(
                                Photosynthesis.stepNow(r.level)
                                        .containsKey(r.zone(2, 3, 2).id().value()),
                                "plants sent a removal once the CO2 ran out");
                        r.ticks(2);
                        r.close(o2(r, 2, 3, 2), last, "O2 once the CO2 has run out");
                    }));
            h.succeed();
        });
    }

    /** Crops in the dark, at light 0, make nothing, whatever CO₂ the zone holds (CONT-053, CONT-057). */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void cont_053_crops_in_the_dark_make_nothing(GameTestHelper h) {
        Rooms r = rooms(h, 4);
        room(r, 0, Rooms.STONE, FARMLAND);
        h.startSequence().thenWaitUntil(() -> lightIs(r, 2, 1, 2, 0)).thenExecute(() -> {
            plantRows(r, 0);
            addCo2(r, 2, 3, 2, 1);
            double o2 = o2(r, 2, 3, 2);
            step(r);
            r.close(co2(r, 2, 3, 2), 1, "CO2 in the dark");
            r.close(o2(r, 2, 3, 2), o2, "O2 in the dark");
            h.succeed();
        });
    }

    /**
     * A zone paused by an unloaded chunk changes nothing: its plants wait for it (CONT-053, GEO-036).
     * The room crosses the chunk border at area x 16, and the chunk east of it unloads.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void cont_053_plants_in_a_paused_zone_do_nothing(GameTestHelper h) {
        Rooms r = rooms(h, 5);
        greenhouse(r, 14);
        h.startSequence().thenWaitUntil(() -> lightIs(r, 15, 1, 2, 15)).thenExecute(() -> {
            atTime(r, NOON, () -> {
                plantRows(r, 14);
                addCo2(r, 15, 3, 2, 1);
                step(r);
                double before = co2(r, 15, 3, 2);
                h.assertTrue(before < 1, "the plants don't work before the pause");
                byte[] saved = r.dimension().simulateUnload(r.chunk(16));
                r.ticks(2);
                h.assertTrue(r.zone(15, 3, 2).paused(), "the zone didn't pause");
                h.assertFalse(
                        Photosynthesis.stepNow(r.level)
                                .containsKey(r.zone(15, 3, 2).id().value()),
                        "the paused zone's plants sent a removal");
                r.ticks(2);
                r.close(co2(r, 15, 3, 2), before, "CO2 while paused");
                r.dimension().simulateLoad(r.chunk(16), saved);
                r.ticks(2);
                h.assertFalse(r.zone(15, 3, 2).paused(), "the zone didn't resume");
                step(r);
                h.assertTrue(co2(r, 15, 3, 2) < before, "the plants didn't start again");
            });
            h.succeed();
        });
    }

    /**
     * Finding plants costs nothing per pass (NFR-001): a section is read once, when its zone arrives;
     * a plant placed after that is known from the change hook, and one harvested is forgotten; a crop
     * growing isn't a change. A later pass reads no section again (CONT-053).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void cont_053_a_section_is_read_once_and_then_followed(GameTestHelper h) {
        Rooms r = rooms(h, 6);
        room(r, 0, Rooms.STONE, FARMLAND);
        r.set(2, 1, 2, WHEAT);
        long unread = Photosynthesis.scans(r.level);
        addCo2(r, 2, 3, 2, 1);
        Photosynthesis.stepNow(r.level);
        int plants = Photosynthesis.plantCount(r.level);
        long scans = Photosynthesis.scans(r.level);
        h.assertTrue(scans > unread, "the room's section wasn't read");

        Photosynthesis.stepNow(r.level);
        h.assertValueEqual(Photosynthesis.scans(r.level), scans, "sections read by a second pass");
        h.assertValueEqual(Photosynthesis.plantCount(r.level), plants, "plants after a second pass");

        r.set(1, 1, 1, WHEAT);
        Photosynthesis.stepNow(r.level);
        h.assertValueEqual(Photosynthesis.scans(r.level), scans, "sections read after planting");
        h.assertValueEqual(Photosynthesis.plantCount(r.level), plants + 1, "plants after planting");

        r.set(1, 1, 1, WHEAT.setValue(CropBlock.AGE, 7));
        Photosynthesis.stepNow(r.level);
        h.assertValueEqual(Photosynthesis.plantCount(r.level), plants + 1, "plants after a crop grew");

        r.set(1, 1, 1, Rooms.AIR);
        r.set(2, 1, 2, Rooms.AIR);
        Photosynthesis.stepNow(r.level);
        h.assertValueEqual(Photosynthesis.plantCount(r.level), plants - 1, "plants after harvesting");
        h.assertValueEqual(Photosynthesis.scans(r.level), scans, "sections read after harvesting");
        h.succeed();
    }

    /**
     * A section with no plant in its palette is never read: a zone in plain stone adds no scans, and
     * its first plant joins through the change hook (CONT-053, NFR-003).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void cont_053_a_section_without_plants_is_never_read(GameTestHelper h) {
        Rooms r = rooms(h, 7);
        room(r, 0, Rooms.STONE, FARMLAND);
        Photosynthesis.stepNow(r.level); // reads any other test's section, so what follows is this room's
        long scans = Photosynthesis.scans(r.level);
        addCo2(r, 2, 3, 2, 1);
        Photosynthesis.stepNow(r.level);
        int plants = Photosynthesis.plantCount(r.level);
        h.assertValueEqual(Photosynthesis.scans(r.level), scans, "sections read with no plant in them");
        r.set(2, 1, 2, WHEAT);
        Photosynthesis.stepNow(r.level);
        h.assertValueEqual(Photosynthesis.scans(r.level), scans, "sections read after the first plant");
        h.assertValueEqual(Photosynthesis.plantCount(r.level), plants + 1, "plants after the first plant");
        h.succeed();
    }

    /**
     * The service runs by itself on the atmos cadence: with nothing driving it, in real game ticks, a
     * room's crop turns CO₂ into O₂ under the preset in force, at no more than a pass per period
     * (CONT-053, CONT-057). It has a batch to itself, away from the tests that run extra passes.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH + "_cadence")
    public static void cont_053_plants_run_on_the_atmos_cadence(GameTestHelper h) {
        Rooms r = rooms(h, 8);
        room(r, 0, Rooms.STONE, FARMLAND);
        r.set(1, 1, 1, LIGHT_8);
        double[] o2 = new double[1];
        long[] start = new long[1];
        h.startSequence()
                .thenWaitUntil(() -> lightIs(r, 2, 1, 1, 7))
                .thenExecute(() -> {
                    r.set(2, 1, 1, WHEAT);
                    addCo2(r, 2, 3, 2, 1);
                    o2[0] = o2(r, 2, 3, 2);
                    start[0] = r.level.getServer().getTickCount();
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(co2(r, 2, 3, 2) < 1, "no CO2 used yet");
                    h.assertTrue(o2(r, 2, 3, 2) > o2[0], "no O2 made yet");
                })
                .thenExecute(() -> {
                    long elapsed = r.level.getServer().getTickCount() - start[0];
                    long passes = elapsed / ZasServerConfig.scheduler().periodTicks() + 2;
                    double used = 1 - co2(r, 2, 3, 2);
                    double most = passes * perPass(PresetOptions.CROP_PHOTOSYNTHESIS_RATE.get(), 7);
                    h.assertTrue(used <= most, "used " + used + " in " + elapsed + " game ticks, at most " + most);
                })
                .thenSucceed();
    }

    // ---- CONT-057 -----------------------------------------------------------------------

    /**
     * The rates at light 15 and 7 under each preset, for wheat, a sapling and leaves: CONT-057's table,
     * scaled by the light. Each stands in a room of its own, under the sky for 15 and near a light
     * block for 7.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void cont_057_rates_at_light_15_and_7_under_each_preset(GameTestHelper h) {
        Rooms r = rooms(h, 9);
        List<Plant> plants = plantsAtEachLight(r);
        h.startSequence()
                .thenWaitUntil(() -> plants.forEach(p -> p.showsItsLight(r)))
                .thenExecute(() -> {
                    atTime(r, NOON, () -> {
                        h.assertValueEqual(r.level.getSkyDarken(), 0, "the sky's darkening at noon");
                        for (Plant p : plants) {
                            p.plant(r);
                            p.addCo2(r);
                        }
                        measure(r, plants, AtmospherePreset.NORMAL, NORMAL_CROP, NORMAL_LEAF);
                        measure(r, plants, AtmospherePreset.HARDCORE, HARDCORE_CROP, HARDCORE_LEAF);
                    });
                    h.succeed();
                });
    }

    /**
     * Each rate is configured on its own, and each applies to its tag's plants: an override of the
     * crop rate leaves the leaves' alone, and the other way round, from the next pass (CONT-057,
     * OPS-014).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void cont_057_each_rate_is_configured_on_its_own(GameTestHelper h) {
        Rooms r = rooms(h, 10);
        List<Plant> plants = plantsAtEachLight(r);
        h.startSequence()
                .thenWaitUntil(() -> plants.forEach(p -> p.showsItsLight(r)))
                .thenExecute(() -> {
                    atTime(r, NOON, () -> {
                        for (Plant p : plants) {
                            p.plant(r);
                            p.addCo2(r);
                        }
                        try {
                            PresetOptions.CROP_PHOTOSYNTHESIS_RATE.setOverride(1.0);
                            measure(r, plants, AtmospherePreset.NORMAL, 1.0, NORMAL_LEAF);
                            PresetOptions.CROP_PHOTOSYNTHESIS_RATE.clearOverride();
                            PresetOptions.LEAF_PHOTOSYNTHESIS_RATE.setOverride(0.5);
                            measure(r, plants, AtmospherePreset.NORMAL, NORMAL_CROP, 0.5);
                        } finally {
                            PresetOptions.CROP_PHOTOSYNTHESIS_RATE.clearOverride();
                            PresetOptions.LEAF_PHOTOSYNTHESIS_RATE.clearOverride();
                        }
                    });
                    h.succeed();
                });
    }

    /**
     * Light is what a player reads: the sky counts for less at night, so a glass greenhouse slows
     * (CONT-057).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void cont_057_a_greenhouse_slows_at_night(GameTestHelper h) {
        Rooms r = rooms(h, 11);
        greenhouse(r, 0);
        h.startSequence().thenWaitUntil(() -> lightIs(r, 2, 1, 2, 15)).thenExecute(() -> {
            withPreset(h, AtmospherePreset.NORMAL, () -> {
                r.set(2, 1, 2, WHEAT);
                addCo2(r, 2, 3, 2, 1);
                atTime(r, MIDNIGHT, () -> {
                    int darken = r.level.getSkyDarken();
                    h.assertTrue(darken > 0, "the sky isn't dark at midnight");
                    double before = co2(r, 2, 3, 2);
                    step(r);
                    r.close(before - co2(r, 2, 3, 2), perPass(NORMAL_CROP, 15 - darken), "CO2 used at midnight");
                });
                atTime(r, NOON, () -> {
                    double before = co2(r, 2, 3, 2);
                    step(r);
                    r.close(before - co2(r, 2, 3, 2), perPass(NORMAL_CROP, 15), "CO2 used at noon");
                });
            });
            h.succeed();
        });
    }

    /**
     * The shipped tags split the plants by rate: crops and saplings are in {@code #zas:crop_plants},
     * leaves in {@code #zas:leaf_plants}, and {@code #zas:plants} holds both (CONT-053, CONT-057). Only
     * a write that makes a position a plant or stops it being one counts as a plant change.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_057_the_tags_split_plants_by_rate(GameTestHelper h) {
        for (BlockState crop : List.of(WHEAT, Blocks.CARROTS.defaultBlockState(), SAPLING)) {
            h.assertTrue(TriggerStates.isPlant(crop), crop + " isn't a plant");
            h.assertTrue(crop.is(TriggerStates.CROP_PLANTS), crop + " isn't in the crop tag");
            h.assertValueEqual(
                    TriggerStates.flags(crop) & TriggerStates.LEAF_PLANT_FLAG, 0, crop + " at the leaf rate");
        }
        for (BlockState leaf : List.of(LEAVES, Blocks.BIRCH_LEAVES.defaultBlockState())) {
            h.assertTrue(TriggerStates.isPlant(leaf), leaf + " isn't a plant");
            h.assertValueEqual(
                    TriggerStates.flags(leaf) & TriggerStates.LEAF_PLANT_FLAG,
                    TriggerStates.LEAF_PLANT_FLAG,
                    leaf + " at the crop rate");
        }
        int air = Block.getId(Rooms.AIR);
        int young = Block.getId(WHEAT);
        int grown = Block.getId(WHEAT.setValue(CropBlock.AGE, 7));
        h.assertTrue(TriggerStates.plantChanged(air, young), "planting isn't a plant change");
        h.assertTrue(TriggerStates.plantChanged(grown, air), "harvesting isn't a plant change");
        h.assertFalse(TriggerStates.plantChanged(young, grown), "growing is a plant change");
        h.assertFalse(TriggerStates.plantChanged(air, air), "air to air is a plant change");
        h.succeed();
    }

    // ---- Helpers ------------------------------------------------------------------------

    /** The test's own area, apart from every other test's, held loaded across its game ticks. */
    private static Rooms rooms(GameTestHelper h, int index) {
        Rooms r = new Rooms(h, h.getLevel(), new BlockPos(16384 + 256 * index, 100, 16384));
        r.holdLoaded(List.of(r.chunk(0), r.chunk(16), r.chunk(32)));
        return r;
    }

    /**
     * A sealed box from (x0, 0, 0) to (x0 + 4, 4, 4) of {@code wall}, with {@code floor} under its
     * interior.
     */
    private static void room(Rooms r, int x0, BlockState wall, BlockState floor) {
        r.clear(x0, 0, x0 + 4, 4);
        for (BlockPos p : BlockPos.betweenClosed(r.abs(x0, 0, 0), r.abs(x0 + 4, 4, 4))) {
            int x = p.getX() - r.origin.getX() - x0;
            int y = p.getY() - r.origin.getY();
            int z = p.getZ() - r.origin.getZ();
            boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
            if (edge || y == 4) {
                r.level.setBlockAndUpdate(p, wall);
            } else if (y == 0) {
                r.level.setBlockAndUpdate(p, floor);
            }
        }
    }

    /** S-11's sealed glass greenhouse, its floor ready for crops, with nothing above it but sky. */
    private static void greenhouse(Rooms r, int x0) {
        room(r, x0, GLASS, FARMLAND);
    }

    /** Plants wheat on all 9 of a room's floor blocks. */
    private static void plantRows(Rooms r, int x0) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                r.set(x0 + x, 1, z, WHEAT);
            }
        }
    }

    /**
     * One plant to measure. It stands at (x0 + 2, 1, 1) in its own room, and its room's zone is read at
     * (x0 + 1, 3, 3).
     *
     * @param lightZ the z of the position at (x0 + 2, 1) that shows the light on it: its own, or for
     *     leaves near a lamp, which dim their own position, the air next to them towards the lamp
     */
    private record Plant(String name, BlockState state, int x0, int lightZ, int light, boolean leaf) {
        void showsItsLight(Rooms r) {
            lightIs(r, x0 + 2, 1, lightZ, light);
        }

        void plant(Rooms r) {
            r.set(x0 + 2, 1, 1, state);
        }

        void addCo2(Rooms r) {
            PhotosynthesisTests.addCo2(r, x0 + 1, 3, 3, 1);
        }

        double co2(Rooms r) {
            return PhotosynthesisTests.co2(r, x0 + 1, 3, 3);
        }

        double o2(Rooms r) {
            return PhotosynthesisTests.o2(r, x0 + 1, 3, 3);
        }
    }

    /**
     * Builds a room each for wheat, a sapling and leaves at light 15 and at light 7. The plants aren't
     * placed yet. At 15 the room is glass, under the sky. At 7 it is stone with a light block of level
     * 8: beside a crop, which reads its own position, and two blocks from leaves, whose brightest
     * neighbour is the air between.
     */
    private static List<Plant> plantsAtEachLight(Rooms r) {
        List<Plant> plants = new ArrayList<>();
        room(r, 0, GLASS, FARMLAND);
        plants.add(new Plant("wheat at light 15", WHEAT, 0, 1, 15, false));
        room(r, 6, Rooms.STONE, FARMLAND);
        r.set(6 + 1, 1, 1, LIGHT_8);
        plants.add(new Plant("wheat at light 7", WHEAT, 6, 1, 7, false));
        room(r, 16, GLASS, DIRT);
        plants.add(new Plant("a sapling at light 15", SAPLING, 16, 1, 15, false));
        room(r, 22, Rooms.STONE, DIRT);
        r.set(22 + 1, 1, 1, LIGHT_8);
        plants.add(new Plant("a sapling at light 7", SAPLING, 22, 1, 7, false));
        room(r, 32, GLASS, Rooms.STONE);
        plants.add(new Plant("leaves at light 15", LEAVES, 32, 1, 15, true));
        room(r, 38, Rooms.STONE, Rooms.STONE);
        r.set(38 + 2, 1, 3, LIGHT_8);
        plants.add(new Plant("leaves at light 7", LEAVES, 38, 2, 7, true));
        return plants;
    }

    /** Runs one pass under a preset, and checks each plant's room used and made what its rate says. */
    private static void measure(Rooms r, List<Plant> plants, AtmospherePreset preset, double crop, double leaf) {
        withPreset(r.h, preset, () -> {
            double[] co2 = new double[plants.size()];
            double[] o2 = new double[plants.size()];
            for (int i = 0; i < co2.length; i++) {
                co2[i] = plants.get(i).co2(r);
                o2[i] = plants.get(i).o2(r);
            }
            step(r);
            for (int i = 0; i < co2.length; i++) {
                Plant p = plants.get(i);
                double made = perPass(p.leaf() ? leaf : crop, p.light());
                String what = p.name() + " under " + preset;
                r.close(co2[i] - p.co2(r), made, what + ": CO2 used");
                r.close(p.o2(r) - o2[i], made, what + ": O2 made");
            }
        });
    }

    /** What a plant makes in one pass, an atmos period, at a light level. */
    private static double perPass(double molesPerHour, int light) {
        return molesPerHour * light / 15 / 3600 * ZasServerConfig.scheduler().periodSeconds();
    }

    /** Runs one pass and lands it: the removals with their atmos tick, the O₂ with the next. */
    private static void step(Rooms r) {
        for (CompletableFuture<GasParcel> f : Photosynthesis.stepNow(r.level).values()) {
            r.settle(f);
        }
        r.tick();
    }

    private static void addCo2(Rooms r, int x, int y, int z, double moles) {
        GasParcel gas = new GasParcel(Map.of(CO2, moles), StandardAir.TEMPERATURE);
        MutationResult result = r.settle(Rooms.service().add(r.level, r.abs(x, y, z), gas, Rooms.source("s_11")));
        r.h.assertValueEqual(result.outcome(), MutationOutcome.ZONE, "the CO2's outcome");
    }

    private static double co2(Rooms r, int x, int y, int z) {
        return r.zone(x, y, z).mixture().moles(CO2);
    }

    private static double o2(Rooms r, int x, int y, int z) {
        return r.zone(x, y, z).mixture().moles(O2);
    }

    /** Runs atmos ticks until a position is zoned, and returns its zone. */
    private static ZoneInfo formed(Rooms r, int x, int y, int z) {
        for (int t = 0; t < 4 && !r.zoned(x, y, z); t++) {
            r.tick();
        }
        r.h.assertTrue(r.zoned(x, y, z), "planting formed no zone");
        return r.zone(x, y, z);
    }

    /** Checks the light a position holds, with the sky at its brightest. For waiting on the light engine. */
    private static void lightIs(Rooms r, int x, int y, int z, int light) {
        r.h.assertValueEqual(r.level.getRawBrightness(r.abs(x, y, z), 0), light, "light at " + x + ", " + y + ", " + z);
    }

    /** Runs a body at a time of day, then puts the time back. */
    private static void atTime(Rooms r, long time, Runnable body) {
        long before = r.level.getDayTime();
        r.level.setDayTime(time);
        r.level.updateSkyBrightness();
        try {
            body.run();
        } finally {
            r.level.setDayTime(before);
            r.level.updateSkyBrightness();
        }
    }

    /** Runs a body under a preset, then restores the configured one. */
    private static void withPreset(GameTestHelper h, AtmospherePreset preset, Runnable body) {
        AtmospherePreset before = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(preset);
            h.assertValueEqual(ZasServerConfig.preset(), preset, "active preset");
            body.run();
        } finally {
            ZasServerConfig.setPreset(before);
        }
    }
}
