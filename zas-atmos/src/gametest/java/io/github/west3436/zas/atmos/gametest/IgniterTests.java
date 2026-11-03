package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.igniter.IgniterBlock;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.ignition.IgnitionSources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The igniter (CONT-105): it sparks while it has a redstone signal and power, and the framework
 * counts it as an ignition source only while it sparks (API-053).
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IgniterTests {
    private IgniterTests() {}

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
}
