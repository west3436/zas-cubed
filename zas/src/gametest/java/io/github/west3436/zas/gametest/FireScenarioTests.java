package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.fire.CarbonCombustion;
import io.github.west3436.zas.fire.Burners;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * S-04, the room fire: under Hardcore a fire in a sealed room forms a zone, uses its O₂, makes CO₂,
 * heats the air and goes out, and burns again once a door opens to fresh air and it is relit;
 * under Normal the same room stays unzoned and the fire behaves as vanilla.
 *
 * <p>The room is a sealed 3 × 3 × 3 stone room (27 cells) with an oak door in one wall, the fire on
 * a netherrack floor block so it never burns out of fuel. It burns the shipped fire profile, 10 kW
 * of carbon (FIRE-025). That takes about an hour of game time to bring 27 cells of air down to the
 * fire threshold, so the test burns it in steps of {@link #STEP_SECONDS} simulated seconds through
 * {@link Burners#burn}, landing each step with atmos ticks, all within the test's game tick. The
 * preset is the whole server's, so each test sets it and restores it within that tick too.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireScenarioTests {
    private static final ResourceLocation O2 = Rooms.O2;
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "carbon_dioxide");
    private static final BlockState FIRE = Blocks.FIRE.defaultBlockState();

    /** Simulated seconds per burn step: 2 minutes, about 0.3 % of the room's O₂ fraction at 10 kW. */
    private static final double STEP_SECONDS = 120;

    /** The O₂ fraction fire needs (FIRE-030). */
    private static final double THRESHOLD = 0.14;

    private FireScenarioTests() {}

    /** The test's own area, apart from every other test's. */
    private static Rooms rooms(GameTestHelper h, int index) {
        return new Rooms(h, h.getLevel(), new BlockPos(-12288 - 256 * index, 100, 14336));
    }

    /**
     * The room: a stone shell from (0, 0, 0) to (4, 4, 4), netherrack under (2, 1, 2), and a closed
     * oak door in the east wall at (4, 1–2, 2), opening onto the open air.
     */
    private static void build(Rooms r) {
        r.clear(-1, 6);
        r.shell(0, 4);
        r.set(2, 0, 2, Blocks.NETHERRACK.defaultBlockState());
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
        r.set(4, 1, 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        r.set(4, 2, 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        r.ticks(2);
    }

    private static AtmosphereSample air(Rooms r) {
        return Atmospheres.at(r.level, r.point(2, 3, 2));
    }

    private static double o2Fraction(GasMixture m) {
        return m.composition().getOrDefault(O2, 0.0);
    }

    /** Burns the fire for one step and lands what it burned; whether anything burned. */
    private static boolean burnStep(Rooms r, BlockPos fire) {
        Map<Long, CompletableFuture<CarbonCombustion.Burn>> sent =
                Burners.burn(r.level, STEP_SECONDS, p -> p.equals(fire));
        for (CompletableFuture<CarbonCombustion.Burn> f : sent.values()) {
            r.settle(f);
        }
        r.tick();
        return !sent.isEmpty();
    }

    /** Runs a vanilla fire tick at the fire: where M2 puts out a fire without O₂ (FIRE-020). */
    private static void fireTick(Rooms r, BlockPos fire) {
        BlockState s = r.level.getBlockState(fire);
        if (s.is(Blocks.FIRE)) {
            s.tick(r.level, fire, r.level.random);
        }
    }

    /**
     * S-04 under Hardcore: the fire forms a zone of the room's air (CORE-056, FIRE-024), uses O₂,
     * makes CO₂ and heats the air (FIRE-022, FIRE-025), and goes out once the O₂ fraction is below
     * 0.14 (FIRE-030, FIRE-020). The door opened, fresh air comes in, and a fire lit again burns.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void s_04_room_fire_suffocates_and_rekindles_under_hardcore(GameTestHelper h) {
        Rooms r = rooms(h, 0);
        build(r);
        h.assertFalse(r.zoned(2, 3, 2), "the room is zoned before the fire");
        BlockPos fire = r.abs(2, 1, 2);
        AtmospherePreset preset = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(AtmospherePreset.HARDCORE);
            r.set(2, 1, 2, FIRE);
            for (int i = 0; i < 4 && !r.zoned(2, 3, 2); i++) {
                r.tick();
            }
            h.assertTrue(r.zoned(2, 3, 2), "the fire formed no zone under Hardcore");
            ZoneInfo formed = r.zone(2, 3, 2);
            // 27 cells, and the door's two, which belong to the room they face (GEO-007).
            h.assertValueEqual(formed.cellCount(), 29, "cells of the fire's room");
            GasMixture start = formed.mixture();

            // It burns until it suffocates.
            int steps = 0;
            while (r.level.getBlockState(fire).is(Blocks.FIRE) && steps < 60) {
                burnStep(r, fire);
                fireTick(r, fire);
                steps++;
            }
            h.assertFalse(
                    r.level.getBlockState(fire).is(Blocks.FIRE), "the fire still burns after " + steps + " steps");
            GasMixture out = r.zone(2, 3, 2).mixture();
            h.assertTrue(o2Fraction(out) < THRESHOLD, "the fire went out at an O₂ fraction of " + o2Fraction(out));
            h.assertTrue(o2Fraction(out) > 0.12, "it burned on well below the threshold: " + o2Fraction(out));
            h.assertTrue(
                    out.moles(O2) < start.moles(O2) * 0.7,
                    "O₂ hardly fell: " + start.moles(O2) + " → " + out.moles(O2));
            h.assertTrue(out.moles(CO2) > 10, "little CO₂ made: " + out.moles(CO2) + " mol");
            h.assertTrue(
                    out.temperature() > start.temperature() + 20,
                    "the air hardly heated: " + start.temperature() + " → " + out.temperature() + " K");
            h.assertFalse(
                    BaseFireBlock.canBePlacedAt(r.level, fire, Direction.UP), "fire can be lit again in the stale air");

            // The door opens to fresh air: the room airs out and a fire lit again burns on.
            BlockState lower = r.level.getBlockState(fire.east(2));
            ((DoorBlock) Blocks.OAK_DOOR).setOpen(null, r.level, lower, fire.east(2), true);
            for (int i = 0; i < 120 && o2Fraction(air(r).mixture()) < 0.20; i++) {
                r.tick();
            }
            h.assertTrue(
                    o2Fraction(air(r).mixture()) >= 0.20,
                    "the room didn't air out: O₂ fraction " + o2Fraction(air(r).mixture()));
            h.assertTrue(
                    BaseFireBlock.canBePlacedAt(r.level, fire, Direction.UP), "fire can't be lit in the fresh air");
            r.set(2, 1, 2, FIRE);
            for (int i = 0; i < 5; i++) {
                burnStep(r, fire);
                fireTick(r, fire);
            }
            h.assertTrue(r.level.getBlockState(fire).is(Blocks.FIRE), "the relit fire went out in fresh air");
        } finally {
            ZasServerConfig.setPreset(preset);
        }
        h.succeed();
    }

    /**
     * S-04 under Normal: the same room with a fire stays unzoned (CORE-055), its air stays the
     * ambient, and the fire burns on as vanilla's does.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void s_04_room_fire_stays_vanilla_under_normal(GameTestHelper h) {
        Rooms r = rooms(h, 1);
        build(r);
        BlockPos fire = r.abs(2, 1, 2);
        AtmospherePreset preset = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(AtmospherePreset.NORMAL);
            r.set(2, 1, 2, FIRE);
            r.ticks(4);
            h.assertFalse(r.zoned(2, 3, 2), "the fire formed a zone under Normal");
            for (int i = 0; i < 20; i++) {
                h.assertTrue(burnStep(r, fire) == false, "an unzoned fire burned the room's gas");
                fireTick(r, fire);
            }
            h.assertTrue(r.level.getBlockState(fire).is(Blocks.FIRE), "the fire went out under Normal");
            h.assertFalse(r.zoned(2, 3, 2), "the room was zoned after the fire burned");
            AtmosphereSample a = air(r);
            h.assertValueEqual(a.state(), AtmosphereState.AMBIENT, "the room's air");
            h.assertTrue(
                    Math.abs(o2Fraction(a.mixture()) - 0.21) < 1e-6, "the ambient's O₂: " + o2Fraction(a.mixture()));
        } finally {
            ZasServerConfig.setPreset(preset);
        }
        h.succeed();
    }
}
