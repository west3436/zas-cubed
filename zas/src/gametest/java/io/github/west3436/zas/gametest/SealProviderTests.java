package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.capability.SealGeometry;
import io.github.west3436.zas.api.mutation.BlockGeometry;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryOverride;
import io.github.west3436.zas.geometry.GeometryTable;
import io.github.west3436.zas.geometry.PositionGeometries;
import io.github.west3436.zas.provider.PointResolver;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Blocks that give their own gas geometry through a seal provider, in a real level (API-023,
 * API-028, GEO-014).
 *
 * <p>Two rooms 3 × 3 × 3 inside, in a row along x, share a wall at x = 14, with a {@link
 * TestSealBlock} at (14, 2, 2) in it. Areas 640 to 643.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealProviderTests {
    private static final int WEST = 12;
    private static final int EAST = 16;
    private static final int WALL = 14;

    private SealProviderTests() {}

    /** The two rooms, the test block in the wall answering {@code answer}, and gas in both, so both have zones. */
    private static Rooms rooms(GameTestHelper h, int index, SealGeometry answer) {
        Rooms r = new Rooms(h, index);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        r.box(14, 0, 18, 4);
        TestSealBlock.answer(r.abs(WALL, 2, 2), answer);
        r.set(WALL, 2, 2, TestSealBlock.state());
        r.settle(GasMutations.add(r.level, r.point(WEST, 2, 2), oxygen(300), source("api_023")));
        r.settle(GasMutations.add(r.level, r.point(EAST, 2, 2), oxygen(100), source("api_023")));
        return r;
    }

    private static void apart(Rooms r, String when) {
        r.h.assertFalse(r.zone(WEST, 2, 2).id().equals(r.zone(EAST, 2, 2).id()), "the rooms are one zone " + when);
        r.h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 27, "the west room " + when);
        r.h.assertFalse(r.zoned(WALL, 2, 2), "the block has a zone " + when);
    }

    /**
     * Switching a block between sealed and open through its provider and markDirty, the zones follow;
     * with no markDirty they don't, and a provider with no answer leaves the block's own shape
     * (API-023, GEO-014).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_023_zones_follow_the_provider(GameTestHelper h) {
        Rooms r = rooms(h, 640, SealGeometry.SEALED);
        BlockPos pos = r.abs(WALL, 2, 2);
        apart(r, "sealed");

        TestSealBlock.answer(pos, SealGeometry.OPEN);
        r.tick();
        apart(r, "before markDirty");

        BlockGeometry.markDirty(r.level, pos);
        r.tick();
        ZoneInfo joined = r.zone(WEST, 2, 2);
        h.assertValueEqual(r.zone(EAST, 2, 2).id(), joined.id(), "the rooms, open");
        h.assertValueEqual(r.zone(WALL, 2, 2).id(), joined.id(), "the block, open");
        h.assertValueEqual(joined.cellCount(), 55, "the rooms and the opening");
        r.close(joined.volume(), 55_000, "the volume, open");
        h.assertValueEqual(PointResolver.resolve(r.level, r.point(WALL, 2, 2)).pos(), pos, "a point in the block");

        TestSealBlock.answer(pos, SealGeometry.SEALED);
        BlockGeometry.markDirty(r.level, pos);
        r.tick();
        apart(r, "sealed again");
        h.assertFalse(
                PointResolver.resolve(r.level, r.point(WALL, 2, 2)).pos().equals(pos),
                "a point in the sealed block stays in it");

        TestSealBlock.answer(pos, SealGeometry.OPEN);
        BlockGeometry.markDirty(r.level, pos);
        r.tick();
        h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 55, "open again");
        TestSealBlock.answer(pos, null);
        BlockGeometry.markDirty(r.level, pos);
        r.tick();
        apart(r, "with no answer, as a full block");
        h.succeed();
    }

    /** The provider's sealed faces and volume are what the zones get (API-023). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_023_provider_gives_faces_and_volume(GameTestHelper h) {
        Rooms r = rooms(h, 641, new SealGeometry(Set.of(Direction.WEST), Set.of(), 250));
        ZoneInfo west = r.zone(WEST, 2, 2);
        ZoneInfo east = r.zone(EAST, 2, 2);
        h.assertFalse(west.id().equals(east.id()), "its west face is sealed");
        h.assertValueEqual(west.cellCount(), 27, "the west room");
        h.assertValueEqual(east.cellCount(), 28, "the east room and the block");
        h.assertValueEqual(r.zone(WALL, 2, 2).id(), east.id(), "the block");
        r.close(east.volume(), 27_250, "the east room and the block's 250 L");
        h.succeed();
    }

    /** Zone-boundary faces pass gas and keep the zones apart, as a door's do (API-023, GEO-007). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_023_provider_gives_zone_boundaries(GameTestHelper h) {
        Rooms r = rooms(h, 642, new SealGeometry(Set.of(), Set.of(Direction.WEST), 1000));
        ZoneInfo west = r.zone(WEST, 2, 2);
        ZoneInfo east = r.zone(EAST, 2, 2);
        h.assertFalse(west.id().equals(east.id()), "the boundary keeps the zones apart");
        h.assertValueEqual(east.cellCount(), 28, "the east room and the block");
        h.assertValueEqual(west.edges().zoneEdges(), 1, "the boundary's edge");
        double density = west.mixture().moles(O2) / west.volume();
        r.ticks(3);
        ZoneInfo after = r.zone(WEST, 2, 2);
        h.assertValueEqual(after.id(), west.id(), "the west zone");
        h.assertTrue(after.mixture().moles(O2) / after.volume() < density, "oxygen didn't cross");
        h.succeed();
    }

    /**
     * The provider runs on the server thread, when its section is copied, and isn't asked again
     * however often the section is copied until markDirty or a change of the block (API-028).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_028_provider_is_kept_until_mark_dirty(GameTestHelper h) {
        Rooms r = rooms(h, 643, SealGeometry.SEALED);
        BlockPos pos = r.abs(WALL, 2, 2);
        h.assertTrue(TestSealBlock.asked(pos) > 0, "the provider wasn't asked");
        h.assertValueEqual(TestSealBlock.askedBy(pos), r.level.getServer().getRunningThread(), "the thread");
        int asked = TestSealBlock.asked(pos);

        // Stone in the room: its section is copied again, and the block's answer is still in use.
        for (int i = 0; i < 3; i++) {
            r.set(WEST - 1 + i, 1, 1, STONE);
            r.tick();
            h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 26 - i, "the section wasn't copied, step " + i);
            h.assertFalse(r.zone(WEST, 2, 2).id().equals(r.zone(EAST, 2, 2).id()), "one zone, step " + i);
            h.assertValueEqual(TestSealBlock.asked(pos), asked, "asked again at step " + i);
        }
        r.ticks(2);
        h.assertValueEqual(TestSealBlock.asked(pos), asked, "asked again with nothing changed");

        BlockGeometry.markDirty(r.level, pos);
        h.assertValueEqual(TestSealBlock.asked(pos), asked, "asked before the next copy");
        r.tick();
        h.assertValueEqual(TestSealBlock.asked(pos), asked + 1, "asked once after markDirty");
        h.assertValueEqual(TestSealBlock.askedBy(pos), r.level.getServer().getRunningThread(), "the thread, then");
        r.ticks(2);
        h.assertValueEqual(TestSealBlock.asked(pos), asked + 1, "asked again after markDirty");

        // Replacing the block forgets the answer: the block that comes back is asked.
        r.set(WALL, 2, 2, STONE);
        r.tick();
        r.set(WALL, 2, 2, TestSealBlock.state());
        r.tick();
        h.assertValueEqual(TestSealBlock.asked(pos), asked + 2, "asked after the block was placed again");
        h.succeed();
    }

    /** A datapack override of the block has the last word: nothing asks the provider (GEO-005). */
    @GameTest(template = "empty")
    public static void api_023_datapack_override_beats_the_provider(GameTestHelper h) {
        h.assertTrue(Geometries.table().perPosition(TestSealBlock.state()), "the block is read per position");
        Geometries.overrideForTest(List.of(new GeometryOverride(
                List.of(new GeometryOverride.Target(TestSealBlock.ID, false)), EnumSet.of(Face.DOWN), 500)));
        try {
            GeometryTable table = Geometries.table();
            h.assertFalse(table.perPosition(TestSealBlock.state()), "the override left it per position");
            Geometry g = PositionGeometries.geometry(h.getLevel(), BlockPos.ZERO, TestSealBlock.state(), table);
            h.assertValueEqual(g.volume(), 500.0, "the override's volume");
        } finally {
            Geometries.overrideForTest(List.of());
        }
        h.succeed();
    }

    /** markDirty from another thread is refused (API-045). */
    @GameTest(template = "empty")
    public static void api_045_mark_dirty_off_server_thread_is_refused(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread other = new Thread(
                () -> {
                    try {
                        BlockGeometry.markDirty(level, pos);
                    } catch (Throwable t) {
                        thrown.set(t);
                    }
                },
                "zas-api-045");
        other.start();
        try {
            other.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        h.assertTrue(thrown.get() instanceof IllegalStateException, "expected IllegalStateException, got " + thrown);
        h.succeed();
    }
}
