package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.change.TriggerStates;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.PresetOption;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.config.ZoneTrigger;
import io.github.west3436.zas.environment.Ambients;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hardcore's zone triggers: combustion and planting in an enclosed, unzoned area form a zone
 * (CORE-056, FIRE-024, CONT-053), under Hardcore or an override, and nowhere else.
 *
 * <p>Each test builds a row of sealed 3 × 3 × 3 stone rooms in its own area, one trigger block in
 * the middle of each room's floor, and checks a point in the room's top corner. The preset and the
 * overrides are the whole server's, so, as in {@code PresetTests}, the tests run in a batch of
 * their own and each sets them and restores them within one game tick, driving the atmos ticks it
 * needs itself.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZoneTriggerTests {
    private static final String BATCH = "core_056";

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState NETHERRACK = Blocks.NETHERRACK.defaultBlockState();
    private static final BlockState SOUL_SOIL = Blocks.SOUL_SOIL.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState FARMLAND = Blocks.FARMLAND.defaultBlockState();

    /** One trigger: the block under it, and the block itself. */
    private record Case(String name, BlockState floor, BlockState trigger) {}

    private static final Case FURNACE = new Case("a lit furnace", STONE, lit(Blocks.FURNACE));
    private static final Case SMOKER = new Case("a lit smoker", STONE, lit(Blocks.SMOKER));
    private static final Case BLAST_FURNACE = new Case("a lit blast furnace", STONE, lit(Blocks.BLAST_FURNACE));
    private static final Case CAMPFIRE = new Case("a lit campfire", STONE, campfire(Blocks.CAMPFIRE, true));
    private static final Case SOUL_CAMPFIRE =
            new Case("a lit soul campfire", STONE, campfire(Blocks.SOUL_CAMPFIRE, true));
    private static final Case FIRE = new Case("fire", NETHERRACK, Blocks.FIRE.defaultBlockState());
    private static final Case SOUL_FIRE = new Case("soul fire", SOUL_SOIL, Blocks.SOUL_FIRE.defaultBlockState());
    private static final Case SAPLING = new Case("an oak sapling", DIRT, Blocks.OAK_SAPLING.defaultBlockState());
    private static final Case WHEAT = new Case("wheat", FARMLAND, Blocks.WHEAT.defaultBlockState());
    private static final Case LEAVES =
            new Case("oak leaves", STONE, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));

    private static final List<Case> COMBUSTION =
            List.of(FURNACE, SMOKER, BLAST_FURNACE, CAMPFIRE, SOUL_CAMPFIRE, FIRE, SOUL_FIRE);
    private static final List<Case> PLANTING = List.of(SAPLING, WHEAT, LEAVES);

    private ZoneTriggerTests() {}

    // ---- Hardcore -----------------------------------------------------------------------

    /**
     * Under Hardcore, fire, soul fire and a lit furnace, smoker, blast furnace, campfire or soul
     * campfire in a sealed room each form a zone, holding the room's ambient air and nothing added
     * (CORE-056, FIRE-024, CORE-051).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void core_056_combustion_forms_a_zone_under_hardcore(GameTestHelper h) {
        Rooms r = rooms(h, 0);
        build(r, COMBUSTION, false);
        withPreset(h, AtmospherePreset.HARDCORE, () -> {
            place(r, COMBUSTION);
            for (int i = 0; i < COMBUSTION.size(); i++) {
                ZoneInfo z = formed(r, i, COMBUSTION.get(i).name() + " under Hardcore");
                holdsAmbientAir(r, z, COMBUSTION.get(i).name());
            }
            // A full block acts on its open neighbour; fire fills its own cell.
            h.assertValueEqual(zoneOf(r, 0).cellCount(), 26, "cells of the furnace's room");
            r.close(zoneOf(r, 0).volume(), 26_000, "volume of the furnace's room");
            h.assertValueEqual(zoneOf(r, 5).cellCount(), 27, "cells of the fire's room");
        });
        h.succeed();
    }

    /**
     * Under Hardcore, planting a sapling, wheat or leaves in a sealed room forms a zone holding the
     * room's ambient air (CORE-056, CONT-053).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void core_056_planting_forms_a_zone_under_hardcore(GameTestHelper h) {
        Rooms r = rooms(h, 1);
        build(r, PLANTING, false);
        withPreset(h, AtmospherePreset.HARDCORE, () -> {
            place(r, PLANTING);
            for (int i = 0; i < PLANTING.size(); i++) {
                ZoneInfo z = formed(r, i, PLANTING.get(i).name() + " under Hardcore");
                holdsAmbientAir(r, z, PLANTING.get(i).name());
            }
            h.assertValueEqual(zoneOf(r, 0).cellCount(), 27, "cells of the sapling's room");
        });
        h.succeed();
    }

    /** Under Hardcore, a trigger in the open forms no zone: here, rooms with a hole in the roof (CORE-056). */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void core_056_triggers_in_the_open_form_no_zone(GameTestHelper h) {
        Rooms r = rooms(h, 2);
        List<Case> cases = List.of(FURNACE, CAMPFIRE, FIRE, SAPLING, WHEAT);
        build(r, cases, true);
        withPreset(h, AtmospherePreset.HARDCORE, () -> {
            place(r, cases);
            r.ticks(4);
            for (int i = 0; i < cases.size(); i++) {
                unzoned(r, i, cases.get(i).name() + " in the open");
                h.assertValueEqual(
                        Atmospheres.at(r.level, r.abs(6 * i + 1, 3, 1)).state(),
                        AtmosphereState.AMBIENT,
                        cases.get(i).name() + " in the open");
            }
        });
        h.succeed();
    }

    // ---- Normal ---------------------------------------------------------------------------

    /** Under Normal, no combustion or planting forms a zone, even in a sealed room (CORE-055, CORE-056). */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void core_056_normal_forms_no_zone_from_combustion_or_planting(GameTestHelper h) {
        Rooms r = rooms(h, 3);
        List<Case> cases = new java.util.ArrayList<>(COMBUSTION);
        cases.addAll(PLANTING);
        build(r, cases, false);
        withPreset(h, AtmospherePreset.NORMAL, () -> {
            h.assertFalse(ZoneTrigger.COMBUSTION.active(), "combustion is on under Normal");
            h.assertFalse(ZoneTrigger.PLANTING.active(), "planting is on under Normal");
            place(r, cases);
            r.ticks(4);
            for (int i = 0; i < cases.size(); i++) {
                unzoned(r, i, cases.get(i).name() + " under Normal");
            }
        });
        h.succeed();
    }

    /**
     * Each trigger's option can be overridden on its own: combustion on under Normal forms a zone
     * from a lit furnace while planting stays off, then planting on forms one from wheat; combustion
     * off under Hardcore forms none while planting still does (CORE-056, OPS-014).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void core_056_overrides_switch_each_trigger(GameTestHelper h) {
        Rooms r = rooms(h, 4);
        List<Case> cases = List.of(FURNACE, SAPLING, WHEAT, FURNACE, SAPLING);
        build(r, cases, false);
        requireNoOverrides(h);
        try {
            withPreset(h, AtmospherePreset.NORMAL, () -> {
                PresetOptions.COMBUSTION_FORMS_ZONES.setOverride(true);
                place(r, 0, FURNACE);
                place(r, 1, SAPLING);
                formed(r, 0, "a lit furnace under Normal with combustion overridden on");
                unzoned(r, 1, "a sapling under Normal with only combustion overridden");
                PresetOptions.PLANTING_FORMS_ZONES.setOverride(true);
                place(r, 2, WHEAT);
                formed(r, 2, "wheat under Normal with planting overridden on");
            });
            clearOverrides();
            withPreset(h, AtmospherePreset.HARDCORE, () -> {
                PresetOptions.COMBUSTION_FORMS_ZONES.setOverride(false);
                place(r, 3, FURNACE);
                place(r, 4, SAPLING);
                r.ticks(4);
                unzoned(r, 3, "a lit furnace under Hardcore with combustion overridden off");
                formed(r, 4, "a sapling under Hardcore with combustion overridden off");
            });
        } finally {
            clearOverrides();
        }
        h.succeed();
    }

    // ---- What starts a trigger ------------------------------------------------------------------

    /**
     * Only a write that starts a trigger forms a zone: trigger blocks already in place when the
     * preset changes stay unzoned, as do a lit furnace turning, a fire ageing, a crop growing and a
     * sapling's stage changing; lighting the furnace again and replanting the wheat each form one
     * (CORE-056, CORE-054).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void core_056_only_a_starting_trigger_forms_a_zone(GameTestHelper h) {
        Rooms r = rooms(h, 5);
        List<Case> cases = List.of(FURNACE, FIRE, WHEAT, SAPLING);
        build(r, cases, false);
        withPreset(h, AtmospherePreset.NORMAL, () -> place(r, cases));
        withPreset(h, AtmospherePreset.HARDCORE, () -> {
            r.ticks(3);
            for (int i = 0; i < cases.size(); i++) {
                unzoned(r, i, cases.get(i).name() + " placed under Normal, after switching to Hardcore");
            }
            set(r, 0, FURNACE.trigger().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
            set(r, 1, FIRE.trigger().setValue(FireBlock.AGE, 5));
            set(r, 2, WHEAT.trigger().setValue(CropBlock.AGE, 7));
            set(r, 3, SAPLING.trigger().setValue(SaplingBlock.STAGE, 1));
            r.ticks(3);
            for (int i = 0; i < cases.size(); i++) {
                unzoned(r, i, cases.get(i).name() + " after a change that starts nothing");
            }
            set(r, 0, FURNACE.trigger().setValue(AbstractFurnaceBlock.LIT, false));
            set(r, 0, FURNACE.trigger());
            set(r, 2, Rooms.AIR);
            set(r, 2, WHEAT.trigger());
            formed(r, 0, "the furnace lit again");
            formed(r, 2, "the wheat replanted");
            unzoned(r, 1, "the fire, untouched");
            unzoned(r, 3, "the sapling, untouched");
        });
        h.succeed();
    }

    /** A trigger in an area that's already zoned changes nothing: the zone keeps its id, cells and gas (CORE-056). */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void core_056_trigger_in_a_zone_changes_nothing(GameTestHelper h) {
        Rooms r = rooms(h, 6);
        build(r, List.of(FURNACE), false);
        r.set(1, 0, 1, DIRT);
        withPreset(h, AtmospherePreset.HARDCORE, () -> {
            place(r, List.of(FURNACE));
            ZoneInfo before = formed(r, 0, "a lit furnace");
            r.set(3, 1, 3, Blocks.FIRE.defaultBlockState());
            r.set(1, 1, 1, Blocks.OAK_SAPLING.defaultBlockState());
            r.ticks(3);
            r.same(before, zoneOf(r, 0), "the zone after fire and a sapling in it");
        });
        h.succeed();
    }

    /**
     * A fuel block of another mod counts once its data adds it to {@code #zas:combustion} with a
     * {@code lit} property, as the fuel generator will (CONT-067): a redstone lamp stands in for
     * it. Lit, it forms a zone under Hardcore; unlit, or under Normal, it doesn't (CORE-056).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void core_056_a_tagged_fuel_block_forms_a_zone_like_the_generator(GameTestHelper h) {
        Rooms r = rooms(h, 7);
        Case lamp = new Case("a lit redstone lamp", STONE, lit(Blocks.REDSTONE_LAMP));
        build(r, List.of(lamp, lamp), false);
        h.assertFalse(TriggerStates.isCombustion(lamp.trigger()), "a redstone lamp is combustion by default");
        try {
            TriggerStates.combustionForTest(Blocks.REDSTONE_LAMP);
            h.assertTrue(TriggerStates.isCombustion(lamp.trigger()), "the stand-in isn't combustion when lit");
            h.assertFalse(
                    TriggerStates.isCombustion(lamp.trigger().setValue(RedstoneLampBlock.LIT, false)),
                    "the stand-in is combustion unlit");
            withPreset(h, AtmospherePreset.NORMAL, () -> {
                place(r, 1, lamp);
                r.ticks(3);
                unzoned(r, 1, "the lit stand-in under Normal");
            });
            withPreset(h, AtmospherePreset.HARDCORE, () -> {
                set(r, 0, lamp.trigger().setValue(RedstoneLampBlock.LIT, false));
                r.ticks(3);
                unzoned(r, 0, "the unlit stand-in under Hardcore");
                set(r, 0, lamp.trigger());
                formed(r, 0, "the stand-in lit under Hardcore");
            });
        } finally {
            TriggerStates.combustionForTest();
        }
        h.assertFalse(TriggerStates.isCombustion(lamp.trigger()), "the stand-in stayed combustion");
        h.succeed();
    }

    /**
     * The shipped tags: {@code #zas:combustion} holds fire, soul fire, furnaces, smokers, blast
     * furnaces and campfires, burning only while lit; candles, torches and lava aren't combustion.
     * {@code #zas:plants} holds crops, saplings and leaves in every state. A write starts a trigger
     * only when it gives the position a flag it didn't have, and only under the trigger's option
     * (CORE-056).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void core_056_trigger_states_follow_the_tags(GameTestHelper h) {
        for (Case c : COMBUSTION) {
            h.assertTrue(TriggerStates.isCombustion(c.trigger()), c.name() + " isn't combustion");
            h.assertFalse(TriggerStates.isPlant(c.trigger()), c.name() + " is a plant");
        }
        h.assertFalse(TriggerStates.isCombustion(Blocks.FURNACE.defaultBlockState()), "an unlit furnace burns");
        h.assertFalse(TriggerStates.isCombustion(campfire(Blocks.CAMPFIRE, false)), "an unlit campfire burns");
        h.assertFalse(
                TriggerStates.isCombustion(Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true)),
                "a lit candle is combustion");
        h.assertFalse(TriggerStates.isCombustion(Blocks.TORCH.defaultBlockState()), "a torch is combustion");
        h.assertFalse(TriggerStates.isCombustion(Blocks.LAVA.defaultBlockState()), "lava is combustion");
        for (Case c : PLANTING) {
            h.assertTrue(TriggerStates.isPlant(c.trigger()), c.name() + " isn't a plant");
        }
        h.assertTrue(TriggerStates.isPlant(WHEAT.trigger().setValue(CropBlock.AGE, 7)), "grown wheat");
        h.assertTrue(TriggerStates.isPlant(Blocks.CHERRY_SAPLING.defaultBlockState()), "a cherry sapling");
        h.assertTrue(TriggerStates.isPlant(Blocks.CARROTS.defaultBlockState()), "carrots");
        h.assertTrue(TriggerStates.isPlant(Blocks.BIRCH_LEAVES.defaultBlockState()), "birch leaves");
        h.assertFalse(TriggerStates.isPlant(Blocks.GRASS_BLOCK.defaultBlockState()), "a grass block is a plant");

        int unlit = Block.getId(Blocks.FURNACE.defaultBlockState());
        int furnace = Block.getId(FURNACE.trigger());
        int air = Block.getId(Rooms.AIR);
        int wheat = Block.getId(WHEAT.trigger());
        int grown = Block.getId(WHEAT.trigger().setValue(CropBlock.AGE, 7));
        int fire = Block.getId(FIRE.trigger());
        int older = Block.getId(FIRE.trigger().setValue(FireBlock.AGE, 5));
        withPreset(h, AtmospherePreset.HARDCORE, () -> {
            h.assertValueEqual(TriggerStates.started(unlit, furnace), TriggerStates.COMBUSTION_FLAG, "lighting");
            h.assertValueEqual(TriggerStates.started(air, fire), TriggerStates.COMBUSTION_FLAG, "a fire starting");
            h.assertValueEqual(TriggerStates.started(air, wheat), TriggerStates.PLANTING_FLAG, "planting");
            h.assertValueEqual(TriggerStates.started(furnace, unlit), 0, "putting out");
            h.assertValueEqual(TriggerStates.started(fire, older), 0, "a fire ageing");
            h.assertValueEqual(TriggerStates.started(wheat, grown), 0, "a crop growing");
            h.assertValueEqual(TriggerStates.started(wheat, air), 0, "harvesting");
        });
        withPreset(h, AtmospherePreset.NORMAL, () -> {
            h.assertValueEqual(TriggerStates.started(unlit, furnace), 0, "lighting under Normal");
            h.assertValueEqual(TriggerStates.started(air, wheat), 0, "planting under Normal");
        });
        h.succeed();
    }

    // ---- Helpers ------------------------------------------------------------------------

    private static BlockState lit(Block block) {
        return block.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true);
    }

    private static BlockState campfire(Block block, boolean lit) {
        return block.defaultBlockState().setValue(CampfireBlock.LIT, lit);
    }

    /** The test's own area, apart from every other test's. */
    private static Rooms rooms(GameTestHelper h, int index) {
        return new Rooms(h, h.getLevel(), new BlockPos(-12288 - 256 * index, 100, -12288));
    }

    /**
     * Builds room {@code i} of each case at area x {@code 6 i} to {@code 6 i + 4}: a sealed stone
     * room, the case's floor under its centre (x {@code 6 i + 2}, z 2), and with {@code open} a hole
     * in the roof above the centre.
     */
    private static void build(Rooms r, List<Case> cases, boolean open) {
        r.clear(0, 0, 6 * cases.size(), 6);
        for (int i = 0; i < cases.size(); i++) {
            int x = 6 * i;
            r.box(x, 0, x + 4, 4);
            r.set(x + 2, 0, 2, cases.get(i).floor());
            if (open) {
                r.set(x + 2, 4, 2, Rooms.AIR);
            }
        }
        r.ticks(2);
    }

    private static void place(Rooms r, List<Case> cases) {
        for (int i = 0; i < cases.size(); i++) {
            place(r, i, cases.get(i));
        }
    }

    private static void place(Rooms r, int i, Case c) {
        set(r, i, c.trigger());
    }

    /** Sets the block at the centre of room {@code i}'s floor. */
    private static void set(Rooms r, int i, BlockState state) {
        r.set(6 * i + 2, 1, 2, state);
    }

    /** Runs atmos ticks until room {@code i} is zoned, and returns its zone. */
    private static ZoneInfo formed(Rooms r, int i, String what) {
        for (int t = 0; t < 4 && !r.zoned(6 * i + 1, 3, 1); t++) {
            r.tick();
        }
        r.h.assertTrue(r.zoned(6 * i + 1, 3, 1), what + " formed no zone");
        ZoneInfo z = zoneOf(r, i);
        r.h.assertTrue(z.enclosed(), what + ": the zone isn't enclosed");
        return z;
    }

    private static void unzoned(Rooms r, int i, String what) {
        r.h.assertFalse(r.zoned(6 * i + 1, 3, 1), what + " formed a zone");
    }

    private static ZoneInfo zoneOf(Rooms r, int i) {
        return r.zone(6 * i + 1, 3, 1);
    }

    /** The zone holds the Overworld's ambient air for its volume: nothing was added or removed. */
    private static void holdsAmbientAir(Rooms r, ZoneInfo z, String what) {
        var air = Ambients.ambient(Level.OVERWORLD);
        r.close(z.mixture().moles(Rooms.O2), air.moles(Rooms.O2.toString(), z.volume()), what + ": O2");
        r.close(z.mixture().moles(Rooms.N2), air.moles(Rooms.N2.toString(), z.volume()), what + ": N2");
        r.close(z.mixture().temperature(), air.temperature(), what + ": temperature");
    }

    private static void requireNoOverrides(GameTestHelper h) {
        h.assertTrue(ZasServerConfig.SPEC.isLoaded(), "the server config isn't loaded");
        h.assertValueEqual(PresetOptions.overridden(), 0, "preset options overridden before the test");
    }

    private static void clearOverrides() {
        for (PresetOption<?> option : PresetOptions.all()) {
            option.clearOverride();
        }
    }

    /** Runs checks under a preset, then restores the configured one. */
    private static void withPreset(GameTestHelper h, AtmospherePreset preset, Runnable checks) {
        AtmospherePreset before = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(preset);
            h.assertValueEqual(ZasServerConfig.preset(), preset, "active preset");
            checks.run();
        } finally {
            ZasServerConfig.setPreset(before);
        }
    }
}
