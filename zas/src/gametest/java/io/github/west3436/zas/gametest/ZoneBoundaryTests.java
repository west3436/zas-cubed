package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Doors and trapdoors as zone boundaries in a real level (GEO-007, GEO-044, CORE-034, CORE-067):
 * open, they pass gas but keep the rooms on either side separate zones; their own cells belong to
 * the room on the side away from their panel; opening and closing them never splits or rebuilds a
 * zone; and rooms joined only through them equalize to one mixture.
 *
 * <p>Rooms are 3 × 3 × 3 inside, in a row along x, sharing walls at x = 14 and 18 ({@link
 * Rooms#box}). A door is an oak door two blocks high at z = 2 in a shared wall, facing east with its
 * hinge on the left: closed, its panel lies against its west face; open, against its north face.
 * Areas 80 to 84.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZoneBoundaryTests {
    private static final BlockState DOOR = Blocks.OAK_DOOR
            .defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.EAST)
            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
    /** A bottom trapdoor facing north: closed, it seals its bottom face; open, its south face. */
    private static final BlockState TRAPDOOR = Blocks.OAK_TRAPDOOR
            .defaultBlockState()
            .setValue(TrapDoorBlock.FACING, Direction.NORTH)
            .setValue(TrapDoorBlock.HALF, Half.BOTTOM);

    private ZoneBoundaryTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    /** Rooms in a row from x = 10, each 3 × 3 × 3 inside, with a door in each shared wall. */
    private static Rooms row(GameTestHelper h, int index, int rooms, boolean open) {
        Rooms r = new Rooms(h, index);
        r.clear(8, 0, 12 + 4 * rooms, 6);
        for (int i = 0; i < rooms; i++) {
            r.box(10 + 4 * i, 0, 14 + 4 * i, 4);
        }
        for (int i = 1; i < rooms; i++) {
            door(r, 10 + 4 * i, open);
        }
        return r;
    }

    /** Sets both halves of the door at x, lower half first, as placing one does. */
    private static void door(Rooms r, int x, boolean open) {
        BlockState d = DOOR.setValue(DoorBlock.OPEN, open);
        r.set(x, 1, 2, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        r.set(x, 2, 2, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** The interior point of room {@code i} of a row. */
    private static int centre(int i) {
        return 12 + 4 * i;
    }

    private static GasParcel nitrogen(double moles) {
        return new GasParcel(Map.of(N2, moles), StandardAir.TEMPERATURE);
    }

    private static double density(ZoneInfo z, ResourceLocation gas) {
        return z.mixture().moles(gas) / z.volume();
    }

    private static long splitChecks(Rooms r) throws Exception {
        return r.dimension().inspect(engine -> engine.graph().splitCheckCount()).get(5, TimeUnit.SECONDS);
    }

    // ---- GEO-007 and GEO-044 ----------------------------------------------------------

    /**
     * The tag holds every door and trapdoor; each of their states keeps zones apart across just
     * the faces its panel lies against, closed and open, and seals the same other faces either
     * way, so a toggle never changes a direct opening (GEO-007, GEO-044). Blocks outside the tag
     * aren't boundaries; a tagged block that doesn't open is a boundary on every face.
     */
    @GameTest(template = "empty")
    public static void geo_007_doors_and_trapdoors_are_boundaries_on_their_panel_faces(GameTestHelper h) {
        GeometryTable t = Geometries.table();
        int states = 0;
        for (TagKey<Block> tag : List.of(BlockTags.DOORS, BlockTags.TRAPDOORS)) {
            for (Holder<Block> block : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                h.assertTrue(block.is(Geometries.ZONE_BOUNDARY), block.getRegisteredName() + " isn't a zone boundary");
                for (BlockState s : block.value().getStateDefinition().getPossibleStates()) {
                    Geometry g = t.geometry(s);
                    Geometry toggled = t.geometry(s.cycle(BlockStateProperties.OPEN));
                    Set<Face> faces = EnumSet.noneOf(Face.class);
                    for (Face f : Face.values()) {
                        if (g.isBoundary(f)) {
                            faces.add(f);
                        } else {
                            h.assertTrue(
                                    g.isFaceFull(f) == toggled.isFaceFull(f),
                                    s + ": " + f + " isn't a boundary but opens or closes");
                        }
                        h.assertTrue(g.isBoundary(f) == toggled.isBoundary(f), s + ": " + f + " changes on a toggle");
                    }
                    h.assertValueEqual(faces.size(), 2, s + ": boundary faces " + faces);
                    states++;
                }
            }
        }
        h.assertTrue(states > 0 && t.boundaryStates() >= states, "boundary states: " + t.boundaryStates());

        BlockState closed = DOOR.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        h.assertValueEqual(boundaryFaces(t.geometry(closed)), EnumSet.of(Face.NORTH, Face.WEST), "the oak door");
        h.assertValueEqual(
                boundaryFaces(t.geometry(TRAPDOOR.setValue(TrapDoorBlock.OPEN, true))),
                EnumSet.of(Face.DOWN, Face.SOUTH),
                "the oak trapdoor");
        h.assertValueEqual(boundaryFaces(t.geometry(Blocks.STONE.defaultBlockState())), Set.of(), "stone");
        h.assertValueEqual(boundaryFaces(t.geometry(Blocks.OAK_FENCE_GATE.defaultBlockState())), Set.of(), "a gate");
        // If a datapack tags a block that doesn't open, or seals the same faces either way, its
        // cells become zones of their own.
        h.assertValueEqual(
                Geometries.boundaryFlags(Blocks.COBWEB.defaultBlockState(), t::geometry),
                Geometry.FLAG_ZONE_BOUNDARY,
                "a block that doesn't open");
        h.assertValueEqual(
                Geometries.boundaryFlags(Blocks.OAK_FENCE_GATE.defaultBlockState(), t::geometry),
                Geometry.FLAG_ZONE_BOUNDARY,
                "a gate seals no face either way");
        h.succeed();
    }

    private static Set<Face> boundaryFaces(Geometry g) {
        Set<Face> out = EnumSet.noneOf(Face.class);
        for (Face f : Face.values()) {
            if (g.isBoundary(f)) {
                out.add(f);
            }
        }
        return out;
    }

    /**
     * Opening a door between two zoned rooms: the door's cells stay with the east room, the zones
     * stay apart and gas crosses; closing and opening it again never checks for a split or makes a
     * zone (GEO-007, GEO-044, #107).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_007_open_door_passes_gas_and_keeps_zones_apart(GameTestHelper h) throws Exception {
        Rooms r = row(h, 80, 2, false);
        r.settle(GasMutations.add(r.level, r.point(centre(0), 2, 2), oxygen(300), source("geo_007")));
        r.settle(GasMutations.add(r.level, r.point(centre(1), 2, 2), nitrogen(100), source("geo_007")));
        ZoneInfo west = r.zone(centre(0), 2, 2);
        ZoneInfo east = r.zone(centre(1), 2, 2);
        h.assertValueEqual(west.cellCount(), 27, "west cells, door closed");
        h.assertValueEqual(east.cellCount(), 29, "east cells: the room and both halves of the door");
        h.assertValueEqual(r.zone(14, 1, 2).id(), east.id(), "the closed door's zone");
        long checks = splitChecks(r);

        door(r, 14, true);
        r.tick();
        ZoneInfo w = r.zone(centre(0), 2, 2);
        ZoneInfo e = r.zone(centre(1), 2, 2);
        h.assertValueEqual(w.id(), west.id(), "the west zone after opening");
        h.assertValueEqual(e.id(), east.id(), "the east zone after opening");
        h.assertValueEqual(r.zone(14, 1, 2).id(), east.id(), "the open door's lower half");
        h.assertValueEqual(r.zone(14, 2, 2).id(), east.id(), "the open door's upper half");
        h.assertValueEqual(w.cellCount(), 27, "west cells, door open");
        h.assertValueEqual(e.cellCount(), 29, "east cells, door open");
        h.assertValueEqual(w.edges().zoneEdges(), 1, "the door edge");
        double eastO2 = density(e, O2);
        r.ticks(3);
        h.assertTrue(density(r.zone(centre(1), 2, 2), O2) > eastO2, "oxygen didn't cross the open door");
        h.assertValueEqual(r.zone(centre(0), 2, 2).id(), west.id(), "the rooms merged");

        for (int i = 0; i < 6; i++) {
            door(r, 14, i % 2 == 1);
            r.tick();
            h.assertValueEqual(r.zone(centre(0), 2, 2).id(), west.id(), "toggle " + i + ": the west zone");
            h.assertValueEqual(r.zone(centre(1), 2, 2).id(), east.id(), "toggle " + i + ": the east zone");
            h.assertValueEqual(r.zone(14, 1, 2).id(), east.id(), "toggle " + i + ": the door's zone");
            h.assertValueEqual(r.zone(centre(0), 2, 2).cellCount(), 27, "toggle " + i + ": west cells");
            h.assertValueEqual(
                    r.zone(centre(0), 2, 2).edges().zoneEdges(), i % 2 == 1 ? 1 : 0, "toggle " + i + ": edges");
        }
        h.assertValueEqual(splitChecks(r), checks, "a toggle checked for a split (GEO-044)");
        h.succeed();
    }

    /**
     * A trapdoor in the floor between two rooms, stacked: open, gas passes and the rooms stay
     * separate zones; the trapdoor's cell belongs to the room above (GEO-007).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_007_open_trapdoor_passes_gas_and_keeps_zones_apart(GameTestHelper h) {
        Rooms r = new Rooms(h, 81);
        for (BlockPos p : BlockPos.betweenClosed(r.abs(8, 0, 0), r.abs(16, 10, 6))) {
            r.level.setBlockAndUpdate(p, AIR);
        }
        // The lower room from y = 0 to 4, the upper from 4 to 8, sharing the floor at y = 4.
        for (int y0 : new int[] {0, 4}) {
            for (BlockPos p : BlockPos.betweenClosed(r.abs(10, y0, 0), r.abs(14, y0 + 4, 4))) {
                int x = p.getX() - r.origin.getX();
                int y = p.getY() - r.origin.getY();
                int z = p.getZ() - r.origin.getZ();
                boolean wall = x == 10 || x == 14 || y == y0 || y == y0 + 4 || z == 0 || z == 4;
                r.level.setBlockAndUpdate(p, wall ? STONE : AIR);
            }
        }
        r.set(12, 4, 2, TRAPDOOR);
        r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(300), source("geo_007")));
        r.settle(GasMutations.add(r.level, r.point(12, 6, 2), nitrogen(100), source("geo_007")));
        ZoneInfo below = r.zone(12, 2, 2);
        ZoneInfo above = r.zone(12, 6, 2);
        h.assertValueEqual(below.cellCount(), 27, "the room below");
        h.assertValueEqual(above.cellCount(), 28, "the room above and the trapdoor");

        r.set(12, 4, 2, TRAPDOOR.setValue(TrapDoorBlock.OPEN, true));
        r.tick();
        h.assertValueEqual(r.zone(12, 2, 2).id(), below.id(), "the zone below after opening");
        h.assertValueEqual(r.zone(12, 6, 2).id(), above.id(), "the zone above after opening");
        h.assertValueEqual(r.zone(12, 4, 2).id(), above.id(), "the open trapdoor's zone");
        h.assertValueEqual(r.zone(12, 2, 2).edges().zoneEdges(), 1, "the trapdoor edge");
        double aboveO2 = density(r.zone(12, 6, 2), O2);
        r.ticks(3);
        h.assertTrue(density(r.zone(12, 6, 2), O2) > aboveO2, "oxygen didn't rise through the trapdoor");
        h.assertValueEqual(r.zone(12, 2, 2).id(), below.id(), "the rooms merged");
        h.succeed();
    }

    // ---- CORE-034 ---------------------------------------------------------------------

    /**
     * Two rooms with an open door, zoned by one addition: the classification makes two zones, which
     * share gas for a minute and never merge (CORE-034).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void core_034_door_separated_zones_never_merge(GameTestHelper h) {
        Rooms r = row(h, 82, 2, true);
        h.assertValueEqual(
                r.settle(GasMutations.add(r.level, r.point(centre(0), 2, 2), oxygen(300), source("core_034")))
                        .outcome(),
                MutationOutcome.ZONE,
                "the addition's outcome");
        ZoneInfo west = r.zone(centre(0), 2, 2);
        ZoneInfo east = r.zone(centre(1), 2, 2);
        h.assertFalse(west.id().equals(east.id()), "one zone through the open door");
        h.assertValueEqual(r.zone(14, 1, 2).id(), east.id(), "the door's zone");
        for (int t = 0; t < 60; t++) {
            r.tick();
            h.assertValueEqual(r.zone(centre(0), 2, 2).id(), west.id(), "tick " + t + ": the west zone");
            h.assertValueEqual(r.zone(centre(1), 2, 2).id(), east.id(), "tick " + t + ": the east zone");
        }
        r.close(
                r.zone(centre(1), 2, 2).mixture().pressure(),
                r.zone(centre(0), 2, 2).mixture().pressure(),
                "the rooms' pressures once equalized");
        h.succeed();
    }

    // ---- CORE-067 ---------------------------------------------------------------------

    /**
     * Three rooms in a row joined by open doors, oxygen added in the first: they share until they
     * are equivalent, then all three hold one combined mixture, with the oxygen conserved
     * (CORE-067).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void core_067_three_rooms_equalize(GameTestHelper h) {
        Rooms r = row(h, 83, 3, true);
        r.settle(GasMutations.add(r.level, r.point(centre(0), 2, 2), oxygen(300), source("core_067")));
        ZoneInfo[] start = new ZoneInfo[3];
        double o2 = 0;
        for (int i = 0; i < 3; i++) {
            start[i] = r.zone(centre(i), 2, 2);
            o2 += start[i].mixture().moles(O2);
        }
        h.assertFalse(start[0].id().equals(start[1].id()) || start[1].id().equals(start[2].id()), "zones merged");
        h.assertTrue(density(start[0], O2) > 1.1 * density(start[2], O2), "the rooms started alike");
        int ticks = 0;
        while (!equal(r) && ticks < 400) {
            r.tick();
            ticks++;
        }
        h.assertTrue(equal(r), "the rooms didn't equalize in " + ticks + " atmos ticks");
        h.assertTrue(ticks > 1, "the rooms equalized without sharing first");
        double after = 0;
        for (int i = 0; i < 3; i++) {
            ZoneInfo z = r.zone(centre(i), 2, 2);
            h.assertValueEqual(z.id(), start[i].id(), "room " + i + "'s zone");
            after += z.mixture().moles(O2);
        }
        r.close(after, o2, "the oxygen in the three rooms");
        h.succeed();
    }

    /** Whether the three rooms of a row hold the same mixture, to rounding. */
    private static boolean equal(Rooms r) {
        ZoneInfo a = r.zone(centre(0), 2, 2);
        for (int i = 1; i < 3; i++) {
            ZoneInfo b = r.zone(centre(i), 2, 2);
            if (Math.abs(a.mixture().temperature() - b.mixture().temperature())
                    > 1e-9 * a.mixture().temperature()) {
                return false;
            }
            for (ResourceLocation gas : List.of(O2, N2)) {
                if (Math.abs(density(a, gas) - density(b, gas)) > 1e-12 * density(a, gas)) {
                    return false;
                }
            }
        }
        return true;
    }
}
