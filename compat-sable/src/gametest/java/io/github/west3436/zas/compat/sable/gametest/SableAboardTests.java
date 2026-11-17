package io.github.west3436.zas.compat.sable.gametest;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.life.BreathingRules;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What stands on a sub-level breathes its cabin (COMPAT-101), and a hole in the cabin opens it to the
 * air of the dimension the sub-level is in (COMPAT-102).
 */
@GameTestHolder(SableGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SableAboardTests {
    private SableAboardTests() {}

    /** Where the world shows a point of a sub-level's plot. */
    private static Vec3 shown(ServerSubLevel sub, Vec3 plotPoint) {
        return sub.logicalPose().transformPosition(plotPoint);
    }

    /** The feet of a player standing on the floor of the cabin, in the plot. */
    private static Vec3 feet(ServerSubLevel sub) {
        BlockPos floor = Deck.onBoard(sub, 2, 1, 2);
        return new Vec3(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
    }

    /** A player whose eyes are in the cabin can't breathe there, and reads the cabin. */
    private static void inTheCabin(Deck d, FakePlayer p, ZoneInfo cabin, String where) {
        BreathingRules.Assessment a = BreathingRules.assess(p);
        d.h.assertValueEqual(a.verdict().reason(), BreathReason.LOW_OXYGEN, where + ": the verdict");
        d.h.assertTrue(a.sample() != null, where + ": nothing sampled");
        d.h.assertValueEqual(a.sample().zone(), Optional.of(cabin.id()), where + ": the zone breathed");
        AtmosphereSample s = Atmospheres.of(p);
        d.h.assertValueEqual(s.state(), AtmosphereState.ZONED, where + ": what the HUD reads");
        d.h.assertValueEqual(s.zone(), Optional.of(cabin.id()), where + ": the zone the HUD reads");
    }

    /** A player in the open air breathes the dimension's. */
    private static void inTheOpen(Deck d, FakePlayer p, String where) {
        d.h.assertValueEqual(
                BreathingRules.assess(p).verdict().reason(), BreathReason.BREATHABLE, where + ": the verdict");
        d.h.assertValueEqual(Atmospheres.of(p).state(), AtmosphereState.AMBIENT, where + ": what the HUD reads");
    }

    /**
     * A player standing in the cabin of a sub-level, where the world shows it, breathes the cabin:
     * a cabin without oxygen leaves it short of breath, while a step outside the sub-level the
     * dimension's air is fine. The same holds after the sub-level moves and turns, and where the
     * cabin was before is open air again.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void compat_101_an_entity_on_board_breathes_the_cabin(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 4);
        d.box();
        d.fill(d.abs(2, 2, 2));
        ServerSubLevel sub = d.assemble();
        d.ticks(3);
        ZoneInfo cabin = d.suffocate(Deck.onBoard(sub, 2, 2, 2));

        Vec3 here = shown(sub, feet(sub));
        inTheCabin(d, d.player(here), cabin, "as assembled");
        inTheOpen(d, d.player(here.add(8, 0, 0)), "beside the sub-level");

        // Moved 20 blocks east and 3 up, and turned a quarter.
        sub.logicalPose().orientation().rotateY(Math.PI / 2);
        sub.logicalPose().position().add(20, 3, 0);
        d.container()
                .physicsSystem()
                .getPipeline()
                .teleport(sub, sub.logicalPose().position(), sub.logicalPose().orientation());
        sub.updateLastPose();
        sub.updateBoundingBox();
        Vec3 there = shown(sub, feet(sub));
        h.assertTrue(there.distanceTo(here) > 10, "the sub-level didn't move: " + here + " to " + there);
        inTheCabin(d, d.player(there), cabin, "moved and turned");
        inTheOpen(d, d.player(here), "where the cabin was");
        h.succeed();
    }

    /**
     * A hole in the hull of a sub-level's cabin, as breaking a block on board makes, opens the cabin
     * to the air of the dimension it is in: the cabin's air is the ambient, in its plot and where the
     * world shows it, and a player there breathes it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void compat_102_a_breach_on_board_opens_to_the_dimensions_air(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 5);
        d.box();
        d.fill(d.abs(2, 2, 2));
        ServerSubLevel sub = d.assemble();
        d.ticks(3);
        BlockPos cell = Deck.onBoard(sub, 2, 2, 2);
        d.zone(cell);
        AtmosphereSample ambient = Atmospheres.at(d.level, Vec3.atCenterOf(d.abs(2, 40, 2)));
        h.assertValueEqual(ambient.state(), AtmosphereState.AMBIENT, "the air well above the area");

        d.level.setBlockAndUpdate(Deck.onBoard(sub, 4, 2, 2), Deck.AIR);
        h.succeedWhen(() -> {
            d.tick();
            AtmosphereSample inPlot = Atmospheres.at(d.level, Vec3.atCenterOf(cell));
            h.assertValueEqual(inPlot.state(), AtmosphereState.AMBIENT, "the cabin's air");
            h.assertTrue(
                    Math.abs(inPlot.pressure() - ambient.pressure()) < 1e-6,
                    "the cabin is at " + inPlot.pressure() + " kPa, the dimension at " + ambient.pressure());
            Vec3 here = shown(sub, feet(sub));
            inTheOpen(d, d.player(here), "on board");
        });
    }
}
