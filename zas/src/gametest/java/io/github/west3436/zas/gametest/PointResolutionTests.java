package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.provider.PointResolver;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Point resolution in a real level (API-015, D-44): an exact point uses the sub-cell holding it,
 * or the nearest cell if it is inside a block's shape; a block position uses its largest cell; an
 * entity uses its eyes. Mutations resolve their point the same way.
 *
 * <p>Each test builds a sealed room (area x 2 to 6, a 3 × 3 × 3 interior from x 3 to 5) whose east
 * wall holds a glass pane at (6, 2, 2), running north to south: its west half (sub-cell 0) opens
 * into the room, its east half (sub-cell 1) onto the open air.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PointResolutionTests {
    private static final BlockState PANE = Blocks.GLASS_PANE
            .defaultBlockState()
            .setValue(IronBarsBlock.NORTH, true)
            .setValue(IronBarsBlock.SOUTH, true);

    private PointResolutionTests() {}

    /** The room with the pane, zoned by adding oxygen. */
    static Rooms roomWithPane(GameTestHelper h, int index) {
        Rooms r = new Rooms(h, index);
        r.clear(0, 14);
        r.shell(2, 6);
        r.set(6, 2, 2, PANE);
        MutationResult m = r.settle(GasMutations.add(r.level, r.point(4, 2, 2), oxygen(50), source("api_015")));
        h.assertValueEqual(m.outcome(), MutationOutcome.ZONE, "the room didn't zone");
        h.assertValueEqual(r.zone(4, 2, 2).cellCount(), 28, "the room's 27 cells and the pane's west half");
        return r;
    }

    /** A point in area block {@code (x, y, z)} at the given fractions of the block. */
    static Vec3 at(Rooms r, int x, int y, int z, double fx, double fy, double fz) {
        BlockPos p = r.abs(x, y, z);
        return new Vec3(p.getX() + fx, p.getY() + fy, p.getZ() + fz);
    }

    private static void zoned(GameTestHelper h, Rooms r, Vec3 point, ZoneInfo zone, String what) {
        AtmosphereSample s = Atmospheres.at(r.level, point);
        h.assertValueEqual(s.state(), AtmosphereState.ZONED, what + ": state");
        h.assertValueEqual(s.zone(), Optional.of(zone.id()), what + ": zone");
    }

    private static void ambient(GameTestHelper h, Rooms r, Vec3 point, String what) {
        AtmosphereSample s = Atmospheres.at(r.level, point);
        h.assertValueEqual(s.state(), AtmosphereState.AMBIENT, what + ": state");
        h.assertTrue(s.zone().isEmpty(), what + ": an ambient point has a zone");
    }

    /**
     * Points in each sub-cell of a pane, inside the pane's glass and inside a full block resolve to
     * the cell holding them or the nearest one; a block position uses its largest cell; an entity
     * its eyes (API-015).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_015_points_resolve_to_their_cell(GameTestHelper h) {
        Rooms r = roomWithPane(h, 70);
        ZoneInfo room = r.zone(4, 2, 2);

        // Each sub-cell of the pane.
        zoned(h, r, at(r, 6, 2, 2, 0.2, 0.5, 0.5), room, "the pane's west half");
        ambient(h, r, at(r, 6, 2, 2, 0.8, 0.5, 0.5), "the pane's east half");
        zoned(h, r, at(r, 6, 2, 2, 0.05, 0.95, 0.05), room, "the west half's corner");
        ambient(h, r, at(r, 6, 2, 2, 0.95, 0.05, 0.95), "the east half's corner");
        h.assertValueEqual(
                PointResolver.resolve(r.level, at(r, 6, 2, 2, 0.8, 0.5, 0.5)),
                new PointResolver.Cell(r.abs(6, 2, 2), 1, 2),
                "the east half's cell");

        // Inside the pane's glass, x 7/16 to 9/16: the nearest half.
        zoned(h, r, at(r, 6, 2, 2, 7.5 / 16, 0.5, 0.5), room, "the glass's west layer");
        ambient(h, r, at(r, 6, 2, 2, 8.5 / 16, 0.5, 0.5), "the glass's east layer");

        // Inside a full block of the west wall: the nearest cell next to it.
        zoned(h, r, at(r, 2, 2, 2, 0.9, 0.5, 0.5), room, "the wall, next to the room");
        ambient(h, r, at(r, 2, 2, 2, 0.1, 0.5, 0.5), "the wall, next to the open air");
        h.assertValueEqual(
                PointResolver.resolve(r.level, at(r, 2, 2, 2, 0.9, 0.5, 0.5)),
                new PointResolver.Cell(r.abs(3, 2, 2), 0, 1),
                "the wall's nearest cell");

        // Buried in full blocks, with no cell next to it: the ambient.
        for (BlockPos p : BlockPos.betweenClosed(r.abs(9, 1, 1), r.abs(11, 3, 3))) {
            r.level.setBlockAndUpdate(p, STONE);
        }
        ambient(h, r, at(r, 10, 2, 2, 0.5, 0.5, 0.5), "a point buried in stone");
        h.assertTrue(Atmospheres.zoneAt(r.level, at(r, 10, 2, 2, 0.5, 0.5, 0.5)).isEmpty(), "buried zone");

        // A block position: its largest cell, the first of the pane's two equal halves.
        AtmosphereSample pos = Atmospheres.at(r.level, r.abs(6, 2, 2));
        h.assertValueEqual(pos.state(), AtmosphereState.ZONED, "the pane's position");
        h.assertValueEqual(pos.zone(), Optional.of(room.id()), "the pane's position's zone");
        h.assertValueEqual(
                PointResolver.resolve(r.level, r.abs(6, 2, 2)),
                new PointResolver.Cell(r.abs(6, 2, 2), 0, 2),
                "the pane's largest cell");
        h.assertValueEqual(
                PointResolver.resolve(r.level, PointResolver.pointIn(r.level, r.abs(6, 2, 2))),
                new PointResolver.Cell(r.abs(6, 2, 2), 0, 2),
                "a point in the pane's largest cell, for commands at a position");
        h.assertValueEqual(Atmospheres.at(r.level, r.abs(4, 2, 2)), Atmospheres.at(r.level, r.point(4, 2, 2)), "air");

        // An entity: its eyes.
        Pig pig = EntityType.PIG.create(r.level);
        h.assertTrue(pig != null, "no pig");
        pig.moveTo(at(r, 4, 1, 2, 0.5, 0, 0.5));
        pig.setNoAi(true);
        r.level.addFreshEntity(pig);
        try {
            AtmosphereSample eyes = Atmospheres.of(pig);
            h.assertValueEqual(eyes, Atmospheres.at(r.level, pig.getEyePosition()), "the entity's sample");
            h.assertValueEqual(eyes.zone(), Optional.of(room.id()), "the entity's zone");
        } finally {
            pig.discard();
        }
        h.succeed();
    }

    /**
     * A mutation at an exact point acts on the cell the point resolves to: gas added in the pane's
     * west half or inside the wall beside the room goes into the room, gas added or removed in its
     * east half to and from the open air (API-015, API-012).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_015_mutations_resolve_like_queries(GameTestHelper h) {
        Rooms r = roomWithPane(h, 71);
        ZoneInfo room = r.zone(4, 2, 2);
        MutationResult west =
                r.settle(GasMutations.add(r.level, at(r, 6, 2, 2, 0.2, 0.5, 0.5), oxygen(5), source("api_015")));
        h.assertValueEqual(west.outcome(), MutationOutcome.ZONE, "the west half");
        MutationResult east =
                r.settle(GasMutations.add(r.level, at(r, 6, 2, 2, 0.8, 0.5, 0.5), oxygen(5), source("api_015")));
        h.assertValueEqual(east.outcome(), MutationOutcome.AMBIENT, "the east half");
        MutationResult wall =
                r.settle(GasMutations.add(r.level, at(r, 2, 2, 2, 0.9, 0.5, 0.5), oxygen(5), source("api_015")));
        h.assertValueEqual(wall.outcome(), MutationOutcome.ZONE, "inside the wall, next to the room");
        MutationResult glass = r.settle(
                GasMutations.remove(r.level, at(r, 6, 2, 2, 8.5 / 16, 0.5, 0.5), Map.of(O2, 1.0), source("api_015")));
        h.assertValueEqual(glass.outcome(), MutationOutcome.AMBIENT, "the glass's east layer");
        r.close(
                r.zone(4, 2, 2).mixture().moles(O2),
                room.mixture().moles(O2) + 10,
                "the room's oxygen: the west half's and the wall's additions only");
        h.succeed();
    }
}
