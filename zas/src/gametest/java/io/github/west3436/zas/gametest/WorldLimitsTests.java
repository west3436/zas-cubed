package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import com.google.gson.JsonParser;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.environment.AmbientFormat;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.environment.DimensionAmbient;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Where the world ends: its build height and border count as open, and a dimension's definition can
 * seal them (GEO-031). Each test runs within one game tick. The Nether's definition and border are
 * changed only by the tests here, in a batch of their own since {@code tickNow} wants one.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldLimitsTests {
    private static final String BATCH = "world_limits";

    /** Chunk-aligned, and clear of the other tests' areas in the Nether. */
    private static final BlockPos NETHER_ROOFED = new BlockPos(-16384, 100, 18432);

    private static final BlockPos NETHER_BORDER = new BlockPos(-16640, 100, 18432);

    /** Enough gas that a zone opened to the ambient doesn't vent away within a test. */
    private static final double MOLES = 2000;

    private WorldLimitsTests() {}

    private static MutationOutcome add(Rooms r, int x, int y, int z) {
        return r.settle(GasMutations.add(r.level, r.point(x, y, z), oxygen(MOLES), source("geo_031")))
                .outcome();
    }

    /** Walls and a floor at the top of the level, 3 × 2 × 3 inside, with nothing above; returns its y. */
    private static int roomAtTheTop(Rooms r) {
        int y = r.level.getMaxBuildHeight() - 1 - r.origin.getY();
        for (BlockPos p : BlockPos.betweenClosed(r.abs(9, y - 2, 1), r.abs(13, y, 5))) {
            int dx = p.getX() - r.origin.getX();
            int dz = p.getZ() - r.origin.getZ();
            boolean wall = dx == 9
                    || dx == 13
                    || dz == 1
                    || dz == 5
                    || p.getY() == r.abs(0, y - 2, 0).getY();
            r.level.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
        return y;
    }

    private static DimensionAmbient sealedNether() {
        DimensionAmbient own = Ambients.definition(Level.NETHER).orElse(DimensionAmbient.NONE);
        return new DimensionAmbient(own.normal(), own.hardcore(), own.excluded(), true);
    }

    private static ServerLevel nether(GameTestHelper h) {
        ServerLevel level = h.getLevel().getServer().getLevel(Level.NETHER);
        if (level == null) {
            throw new GameTestAssertException("no Nether");
        }
        return level;
    }

    /** GEO-031's plan: a room touching the build limit vents, here because its roof at the limit goes. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_031_a_room_touching_the_build_limit_vents(GameTestHelper h) {
        Rooms r = new Rooms(h, 673);
        int y = roomAtTheTop(r);
        h.assertValueEqual(add(r, 11, y, 3), MutationOutcome.AMBIENT, "the room with no roof");
        h.assertFalse(r.zoned(11, y, 3), "a zone formed");

        for (BlockPos p : BlockPos.betweenClosed(r.abs(10, y, 2), r.abs(12, y, 4))) {
            r.level.setBlockAndUpdate(p, STONE);
        }
        h.assertValueEqual(add(r, 11, y - 1, 3), MutationOutcome.ZONE, "the room roofed at the limit");
        h.assertFalse(r.zone(11, y - 1, 3).breached(), "the room under its roof is breached");

        r.set(11, y, 3, AIR);
        r.ticks(3);
        ZoneInfo open = r.zone(11, y - 1, 3);
        h.assertTrue(open.breached(), "the hole at the limit doesn't vent: " + open);
        h.succeed();
    }

    /** GEO-031's plan: a dimension override seals it, and taking the override away vents the zone. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void geo_031_a_dimension_override_seals_the_build_limit(GameTestHelper h) {
        ServerLevel nether = nether(h);
        Rooms r = new Rooms(h, nether, NETHER_ROOFED);
        Ambients.overrideDefinitionForTest(Level.NETHER, sealedNether());
        try {
            h.assertTrue(Ambients.sealsWorldLimits(Level.NETHER), "the override doesn't seal");
            int y = roomAtTheTop(r);
            h.assertValueEqual(add(r, 11, y, 3), MutationOutcome.ZONE, "the sealed room");
            h.assertValueEqual(r.zone(11, y, 3).cellCount(), 18, "its cells");

            Ambients.overrideDefinitionForTest(Level.NETHER, null);
            r.ticks(2);
            ZoneInfo open = r.zone(11, y, 3);
            h.assertTrue(open.breached(), "the zone doesn't vent: " + open);
        } finally {
            Ambients.overrideDefinitionForTest(Level.NETHER, null);
            r.tick();
        }
        h.succeed();
    }

    /**
     * A room against the default world border, whose last block inside is 29,999,983. Stone on it seals
     * the room. Stone a block further is past the border, so the room's open.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_031_the_default_world_border_is_open(GameTestHelper h) {
        Rooms r = new Rooms(h, h.getLevel(), new BlockPos(29_999_968, 100, 8192));
        h.assertTrue(r.level.getWorldBorder().isWithinBounds(r.abs(15, 0, 0)), "the last block inside");
        h.assertFalse(r.level.getWorldBorder().isWithinBounds(r.abs(16, 0, 0)), "the first block past it");
        r.clear(10, 0, 16, 14);
        r.box(11, 0, 15, 4);
        h.assertValueEqual(add(r, 13, 2, 2), MutationOutcome.ZONE, "stone on the last block inside");

        r.box(12, 8, 16, 12);
        h.assertValueEqual(add(r, 14, 2, 10), MutationOutcome.AMBIENT, "stone a block past it");
        h.succeed();
    }

    /** The real border, moved in on a dimension: a room against it vents, unless it's sealed. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void geo_031_a_world_border_moved_in_counts_at_once(GameTestHelper h) {
        ServerLevel nether = nether(h);
        Rooms r = new Rooms(h, nether, NETHER_BORDER);
        WorldBorder border = nether.getWorldBorder();
        // A level's border gives its centre divided by the dimension's scale, but takes it undivided.
        double scale = nether.dimensionType().coordinateScale();
        double centreX = border.getCenterX() * scale;
        double centreZ = border.getCenterZ() * scale;
        double size = border.getSize();
        try {
            // The border's east side at area x = 13: blocks up to area x = 12 are inside.
            double half = 40_000;
            border.setCenter((r.abs(13, 0, 0).getX() - half) * scale, r.origin.getZ() * scale);
            border.setSize(2 * half);
            h.assertTrue(border.isWithinBounds(r.abs(12, 0, 0)), "the last block inside");
            h.assertFalse(border.isWithinBounds(r.abs(13, 0, 0)), "the first block past it");
            r.clear(6, 0, 13, 6);
            r.box(8, 0, 13, 4);
            h.assertValueEqual(add(r, 10, 2, 2), MutationOutcome.AMBIENT, "the wall at x = 13 is past the border");
            Ambients.overrideDefinitionForTest(Level.NETHER, sealedNether());
            h.assertValueEqual(add(r, 10, 2, 2), MutationOutcome.ZONE, "with the world's edges sealed");
            h.assertValueEqual(r.zone(10, 2, 2).cellCount(), 36, "the sealed room's cells");
        } finally {
            Ambients.overrideDefinitionForTest(Level.NETHER, null);
            border.setCenter(centreX, centreZ);
            border.setSize(size);
            r.tick();
        }
        h.succeed();
    }

    private static DimensionAmbient parse(String json) throws AmbientFormat.InvalidAmbientException {
        return AmbientFormat.parse(JsonParser.parseString(json), warning -> {});
    }

    /** The field in a definition's file: one with only this field is allowed, and it must be a boolean. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void geo_031_the_field_in_a_definition(GameTestHelper h) {
        try {
            DimensionAmbient sealed = parse("{\"sealed_world_limits\": true}");
            h.assertTrue(sealed.sealedWorldLimits(), "the field wasn't read");
            h.assertValueEqual(sealed.normal(), Optional.empty(), "its atmosphere");

            DimensionAmbient open = parse("{\"excluded\": true}");
            h.assertFalse(open.sealedWorldLimits(), "the default");
        } catch (AmbientFormat.InvalidAmbientException e) {
            throw new GameTestAssertException("rejected: " + e.getMessage());
        }
        try {
            parse("{\"sealed_world_limits\": 1}");
            throw new GameTestAssertException("accepted a number");
        } catch (AmbientFormat.InvalidAmbientException e) {
            h.assertTrue(e.getMessage().contains("sealed_world_limits"), "the message: " + e.getMessage());
        }
        h.succeed();
    }
}
