package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.igniter.IgniterBlock;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.ignition.IgnitionSources;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The igniter (CONT-105): it sparks while it has a redstone signal and power, and the framework
 * counts it as an ignition source only while it sparks (API-053). The end-to-end tests light a
 * sealed room of 10 % hydrogen in air: hydrogen's combustion needs ignition (FIRE-006), and at room
 * temperature nothing else lights it.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IgniterTests {
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final ResourceLocation H2O = ResourceLocation.fromNamespaceAndPath("zas", "water_vapor");

    /** Atmos steps a lit igniter gets to light its room: the geometry, the hotspot, its block, a burn. */
    private static final int STEPS = 8;

    private IgniterTests() {}

    /**
     * A sealed 3 by 3 by 3 room with corner x0 holding 10 % hydrogen in air, with an igniter on its
     * floor against the west wall. With {@code signal}, the wall block beside it is a redstone block.
     */
    private static IgniterBlockEntity room(Area a, int x0, boolean signal, boolean powered) {
        a.box(x0, 0, x0 + 4, 4);
        a.place(x0 + 1, 1, 2, AtmosBlocks.IGNITER.get().defaultBlockState());
        if (signal) {
            a.set(x0, 1, 2, Blocks.REDSTONE_BLOCK.defaultBlockState());
        }
        IgniterBlockEntity ig = a.entity(x0 + 1, 1, 2);
        ig.power().setTestPowered(powered);
        double moles = 101_325.0 * 27 / (8.314_462_618 * Area.T);
        a.fill(x0 + 2, 2, 2, Map.of(H2, 0.10 * moles, Area.N2, 0.90 * 0.79 * moles, Area.O2, 0.90 * 0.21 * moles));
        return ig;
    }

    private static double water(Area a, int x0) {
        return a.zone(x0 + 2, 3, 2).mixture().moles(H2O);
    }

    /**
     * End to end: a powered igniter given a signal lights a flammable room, and one without power
     * or without a signal doesn't (CONT-105, FIRE-014).
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void cont_105_igniter_lights_a_flammable_zone(GameTestHelper h) {
        Area a = new Area(h, 61);
        try {
            room(a, 0, true, true);
            room(a, 6, true, false);
            room(a, 12, false, true);
            int steps = 0;
            while (water(a, 0) == 0 && steps < STEPS) {
                a.step();
                steps++;
            }
            h.assertTrue(
                    water(a, 0) > 0, "a powered igniter with a signal didn't light the room in " + STEPS + " steps");
            for (int i = steps; i < STEPS; i++) {
                a.step();
            }
            h.assertValueEqual(water(a, 6), 0.0, "water in the room whose igniter has no power");
            h.assertValueEqual(water(a, 12), 0.0, "water in the room whose igniter has no signal");
        } finally {
            a.clear(-1, -1, 17, 5);
        }
        h.succeed();
    }

    /**
     * A reaction requiring ignition waits until an igniter fires (FIRE-006): hydrogen in air beside
     * a powered igniter stays unburned until the igniter gets a signal, then burns.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fire_006_reaction_requiring_ignition_waits_for_the_igniter(GameTestHelper h) {
        Area a = new Area(h, 63);
        try {
            room(a, 0, false, true);
            double hydrogen = a.zone(2, 3, 2).mixture().moles(H2);
            for (int i = 0; i < STEPS; i++) {
                a.step();
            }
            h.assertValueEqual(water(a, 0), 0.0, "water before the igniter fired");
            a.close(a.zone(2, 3, 2).mixture().moles(H2), hydrogen, "hydrogen before the igniter fired");

            a.set(0, 1, 2, Blocks.REDSTONE_BLOCK.defaultBlockState());
            int steps = 0;
            while (water(a, 0) == 0 && steps < STEPS) {
                a.step();
                steps++;
            }
            h.assertTrue(water(a, 0) > 0, "the igniter fired but the room didn't light in " + STEPS + " steps");
            h.assertTrue(a.zone(2, 3, 2).mixture().moles(H2) < hydrogen, "the room lit but no hydrogen burned");
        } finally {
            a.clear(-1, -1, 5, 5);
        }
        h.succeed();
    }

    /**
     * Sparks only with a signal and power; draws 20 FE/t only with a signal; goes out as soon as the
     * signal does; and is an ignition source exactly while it sparks (CONT-105, API-053).
     */
    @GameTest(template = "empty")
    public static void cont_105_igniter_sparks_with_signal_and_power(GameTestHelper h) {
        Area a = new Area(h, 60);
        a.box(0, 0, 4, 4);
        a.place(2, 1, 2, AtmosBlocks.IGNITER.get().defaultBlockState());
        IgniterBlockEntity ig = a.entity(2, 1, 2);

        // Power, no signal: nothing, and no draw.
        ig.power().setTestPowered(true);
        ig.step();
        h.assertFalse(ig.lit(), "sparks with power and no signal");
        h.assertValueEqual(ig.power().draw(), 0, "draw without a signal");
        h.assertFalse(IgnitionSources.isStanding(a.get(2, 1, 2)), "an idle igniter is an ignition source");

        // Signal, no power: draws, but nothing until it is powered.
        ig.power().setTestPowered(false);
        a.set(3, 1, 2, Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.assertTrue(ig.signal(), "a redstone block beside it gives no signal");
        h.assertValueEqual(ig.power().draw(), 20, "draw with a signal");
        ig.step();
        h.assertFalse(ig.lit(), "sparks with a signal and no power");

        // Signal and power: it sparks, and the framework counts it as a source.
        ig.power().setTestPowered(true);
        ig.step();
        h.assertTrue(ig.lit(), "doesn't spark with a signal and power");
        h.assertTrue(a.get(2, 1, 2).getValue(IgniterBlock.LIT), "the block isn't lit");
        h.assertTrue(IgnitionSources.isStanding(a.get(2, 1, 2)), "a sparking igniter isn't an ignition source");

        // The signal goes: it stops at once, without waiting for a step.
        a.set(3, 1, 2, Blocks.AIR.defaultBlockState());
        h.assertFalse(ig.signal(), "a signal after the redstone block went");
        h.assertFalse(ig.lit(), "still sparks after the signal went");
        h.assertFalse(IgnitionSources.isStanding(a.get(2, 1, 2)), "still an ignition source without a signal");
        h.succeed();
    }

    /**
     * An igniter clicks as its signal comes and goes, and sparks as it lights and every 3 s while lit.
     * With no signal or no power it doesn't spark (CONT-127).
     */
    @GameTest(template = "empty")
    public static void cont_127_an_igniter_clicks_and_sparks(GameTestHelper h) {
        SoundLog.listen();
        Area a = new Area(h, 883);
        try {
            a.box(0, 0, 4, 4);
            a.place(1, 1, 2, AtmosBlocks.IGNITER.get().defaultBlockState());
            IgniterBlockEntity ig = a.entity(1, 1, 2);
            ig.power().setTestPowered(true);
            BlockPos at = a.abs(1, 1, 2);
            ig.step();
            DeviceSoundTests.played(h, at, "with no signal");

            a.set(0, 1, 2, Blocks.REDSTONE_BLOCK.defaultBlockState());
            DeviceSoundTests.played(h, at, "as the signal comes", AtmosSounds.IGNITER_CLICK);
            for (int i = 0; i < 7; i++) {
                ig.step();
            }
            DeviceSoundTests.played(
                    h,
                    at,
                    "seven steps lit",
                    AtmosSounds.IGNITER_SPARK,
                    AtmosSounds.IGNITER_SPARK,
                    AtmosSounds.IGNITER_SPARK);

            a.set(0, 1, 2, Area.STONE);
            DeviceSoundTests.played(h, at, "as the signal goes", AtmosSounds.IGNITER_CLICK);
            ig.step();
            DeviceSoundTests.played(h, at, "out");

            ig.power().setTestPowered(false);
            a.set(0, 1, 2, Blocks.REDSTONE_BLOCK.defaultBlockState());
            DeviceSoundTests.played(h, at, "a signal with no power", AtmosSounds.IGNITER_CLICK);
            ig.step();
            DeviceSoundTests.played(h, at, "no spark with no power");
            a.tick();
        } finally {
            a.clear(-1, -1, 5, 5);
        }
        h.succeed();
    }
}
