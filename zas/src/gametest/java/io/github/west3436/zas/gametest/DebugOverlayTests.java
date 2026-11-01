package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.debug.DebugLayer;
import io.github.west3436.zas.debug.DebugOverlay;
import io.github.west3436.zas.debug.DebugOverlayPayload;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.simulation.AtmosService;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server side of the operator debug overlay (CLIENT-005, CLIENT-011 to CLIENT-013): the
 * {@code /zas debug} permission, and the payload an operator would be sent for known rooms. The
 * rendering itself is checked by hand (the requirements' Verify plans are Manual).
 *
 * <p>Each test builds in its own area far from the other test classes' (see {@link #area}), sets
 * the debug region on its dimension's scheduler as the overlay would for an operator standing
 * there, drives atmos ticks with {@link AtmosService#tickNow}, and builds the payload with {@link
 * DebugOverlay#build(ServerLevel, BlockPos, BlockPos, int)}, all within one game tick.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DebugOverlayTests {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int ALL = DebugLayer.ALL;

    private DebugOverlayTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    /** A test's building area: away from the origin's other test areas, 256 blocks apart. */
    private static BlockPos area(int index) {
        return new BlockPos(-4096 - 256 * index, 100, -4096);
    }

    private static AtmosService service() {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return s;
    }

    private static void tick(GameTestHelper h) {
        service().tickNow(h.getLevel());
    }

    private static void settle(GameTestHelper h, CompletableFuture<?> future) {
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick(h);
        }
        h.assertTrue(future.isDone(), "the mutation didn't complete within 8 atmos ticks");
    }

    /** A hollow stone box with corners {@code min} and {@code max}, air inside. */
    private static void shell(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            boolean wall = p.getX() == min.getX()
                    || p.getX() == max.getX()
                    || p.getY() == min.getY()
                    || p.getY() == max.getY()
                    || p.getZ() == min.getZ()
                    || p.getZ() == max.getZ();
            level.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
    }

    /** Forms a zone in the room around {@code pos} by adding oxygen there. */
    private static ZoneInfo zoneWith(GameTestHelper h, BlockPos pos, double moles, String test) {
        GasParcel o2 = new GasParcel(Map.of(StandardAir.OXYGEN, moles), StandardAir.TEMPERATURE);
        settle(h, GasMutations.add(h.getLevel(), Vec3.atCenterOf(pos), o2, SourceId.of(ZasMod.MOD_ID, test)));
        return Atmospheres.zoneAt(h.getLevel(), pos)
                .orElseThrow(() -> new GameTestAssertException("no zone formed at " + pos));
    }

    /** Asks the worker for the debug export around {@code center}, as for an operator standing there. */
    private static void watch(GameTestHelper h, BlockPos center) {
        DimensionPipeline p = service().dimensionFor(h.getLevel()).pipeline();
        if (p == null) {
            throw new GameTestAssertException("the Overworld has no engine");
        }
        p.scheduler().setDebugRegions(List.of(DebugOverlay.regionAround(center)));
    }

    private static void unwatch(GameTestHelper h) {
        DimensionPipeline p = service().dimensionFor(h.getLevel()).pipeline();
        if (p != null) {
            p.scheduler().setDebugRegions(List.of());
        }
    }

    /** The payload sent through the network codec and back. */
    private static DebugOverlayPayload roundTrip(DebugOverlayPayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        DebugOverlayPayload.STREAM_CODEC.encode(buf, payload);
        DebugOverlayPayload back = DebugOverlayPayload.STREAM_CODEC.decode(buf);
        if (buf.readableBytes() != 0) {
            throw new GameTestAssertException(buf.readableBytes() + " bytes left after decoding");
        }
        return back;
    }

    private static void near(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= 1e-3 * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    // ---- Permission (CLIENT-005, OPS-011) ------------------------------------------------

    /**
     * {@code /zas debug} needs permission level 2 by default: a player below it is refused, an
     * operator can switch layers on and off (CLIENT-005); the level follows {@code [commands]
     * permission_level} like every {@code /zas} command (OPS-011).
     */
    @GameTest(template = "empty")
    public static void client_005_overlay_needs_permission_level_2(GameTestHelper h) throws CommandSyntaxException {
        ServerLevel level = h.getLevel();
        FakePlayer player = FakePlayerFactory.get(
                level, new GameProfile(UUID.fromString("5a5c0de0-0005-4000-8000-000000000005"), "zas_client_005"));
        var dispatcher = level.getServer().getCommands().getDispatcher();
        CommandSourceStack player0 = player.createCommandSourceStack().withPermission(0);
        CommandSourceStack player1 = player.createCommandSourceStack().withPermission(1);
        CommandSourceStack operator = player.createCommandSourceStack().withPermission(2);
        try {
            for (CommandSourceStack refused : List.of(player0, player1)) {
                try {
                    dispatcher.execute("zas debug zones", refused);
                    throw new GameTestAssertException("a player below level 2 switched the overlay on");
                } catch (CommandSyntaxException expected) {
                    // refused: the command doesn't exist for them
                }
            }
            h.assertValueEqual(DebugOverlay.layers(player), 0, "layers after refusals");

            dispatcher.execute("zas debug zones", operator);
            h.assertValueEqual(DebugOverlay.layers(player), DebugLayer.ZONES.bit(), "zones on");
            dispatcher.execute("zas debug edges", operator);
            h.assertValueEqual(
                    DebugOverlay.layers(player), DebugLayer.ZONES.bit() | DebugLayer.EDGES.bit(), "zones and edges");
            dispatcher.execute("zas debug zones", operator);
            h.assertValueEqual(DebugOverlay.layers(player), DebugLayer.EDGES.bit(), "zones toggled off");
            dispatcher.execute("zas debug all", operator);
            h.assertValueEqual(DebugOverlay.layers(player), ALL, "all");
            dispatcher.execute("zas debug off", operator);
            h.assertValueEqual(DebugOverlay.layers(player), 0, "off");

            // The level follows the configurable /zas level (OPS-011).
            int was = ZasServerConfig.PERMISSION_LEVEL.get();
            try {
                ZasServerConfig.PERMISSION_LEVEL.set(3);
                try {
                    dispatcher.execute("zas debug zones", operator);
                    throw new GameTestAssertException("level 2 switched the overlay on with the level set to 3");
                } catch (CommandSyntaxException expected) {
                    // refused
                }
                dispatcher.execute(
                        "zas debug zones", player.createCommandSourceStack().withPermission(3));
                h.assertValueEqual(DebugOverlay.layers(player), DebugLayer.ZONES.bit(), "zones on at level 3");
            } finally {
                ZasServerConfig.PERMISSION_LEVEL.set(was);
            }
        } finally {
            DebugOverlay.setLayers(player, 0);
        }
        h.succeed();
    }

    // ---- Payload contents (CLIENT-005, CLIENT-011) -----------------------------------------

    /**
     * For a sealed 3 × 3 × 3 room, the payload lists its 27 cells, all of one zone with its
     * published id and pressure, and no edges, unresolved cells or hotspots (CLIENT-005). It
     * survives the network codec unchanged.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void client_005_payload_lists_room_cells(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = area(1);
        shell(level, o, o.offset(4, 4, 4));
        BlockPos center = o.offset(2, 2, 2);
        watch(h, center);
        try {
            ZoneInfo zone = zoneWith(h, center, 10, "client_005");
            tick(h);
            DebugOverlayPayload p = DebugOverlay.build(level, center, center, ALL);
            h.assertValueEqual(p.zones().size(), 1, "zones");
            DebugOverlayPayload.Zone z = p.zones().get(0);
            h.assertValueEqual(z.id(), zone.id().value(), "zone id");
            near(
                    h,
                    z.pressure(),
                    Atmospheres.zoneAt(level, center).orElseThrow().mixture().pressure(),
                    "pressure");
            h.assertFalse(z.paused(), "paused");
            h.assertValueEqual(p.cells().size(), 27, "cells");
            for (DebugOverlayPayload.Cell c : p.cells()) {
                h.assertTrue(
                        Math.abs(c.dx()) <= 1 && Math.abs(c.dy()) <= 1 && Math.abs(c.dz()) <= 1,
                        "a cell outside the room at " + c);
                h.assertValueEqual(c.zone(), 0, "cell zone index");
                h.assertValueEqual(c.sub(), 0, "sub-cell");
            }
            h.assertTrue(p.faces().isEmpty() && p.edges().isEmpty(), "edges of a sealed room: " + p.edges());
            h.assertTrue(p.unresolved().isEmpty(), "unresolved cells");
            h.assertValueEqual(p.dimension(), level.dimension().location(), "dimension");
            h.assertFalse(p.truncated(), "truncated");
            h.assertValueEqual(roundTrip(p), p, "after the network codec");
        } finally {
            unwatch(h);
        }
        h.succeed();
    }

    /**
     * Only what is within 2 chunks of the operator is sent: of two zoned rooms, the one 48
     * blocks away is left out (CLIENT-005).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void client_005_payload_limited_to_radius(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = area(2);
        shell(level, o, o.offset(4, 4, 4));
        shell(level, o.offset(48, 0, 0), o.offset(52, 4, 4));
        BlockPos near = o.offset(2, 2, 2);
        BlockPos far = o.offset(50, 2, 2);
        watch(h, near);
        try {
            ZoneInfo nearZone = zoneWith(h, near, 10, "client_005");
            ZoneInfo farZone = zoneWith(h, far, 10, "client_005");
            tick(h);
            DebugOverlayPayload p = DebugOverlay.build(level, near, near, ALL);
            h.assertValueEqual(p.cells().size(), 27, "cells within the radius");
            for (DebugOverlayPayload.Cell c : p.cells()) {
                h.assertTrue(
                        Math.max(Math.abs(c.dx()), Math.max(Math.abs(c.dy()), Math.abs(c.dz()))) <= DebugOverlay.RADIUS,
                        "a cell beyond the radius: " + c);
            }
            h.assertValueEqual(p.zones().size(), 1, "zones");
            h.assertValueEqual(p.zones().get(0).id(), nearZone.id().value(), "the near zone");
            h.assertFalse(
                    p.zones().stream().anyMatch(z -> z.id() == farZone.id().value()), "the far zone was sent");
        } finally {
            unwatch(h);
        }
        h.succeed();
    }

    /**
     * A zone paused by an unloaded chunk (GEO-030) is sent with its paused flag set, so the client
     * draws it grey, and its cells are still listed (CLIENT-005). The chunk is unloaded only as far
     * as the atmosphere is concerned ({@code DimensionAtmos#simulateUnload}, see {@link
     * PausedZoneTests}).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void client_005_payload_flags_paused_zone(GameTestHelper h) {
        Rooms r = new Rooms(h, 60);
        r.clear(8, 23);
        r.shell(10, 21); // across the chunk border at x 16
        BlockPos center = r.abs(12, 2, 2);
        watch(h, center);
        byte[] saved = null;
        try {
            r.settle(GasMutations.add(r.level, r.point(12, 2, 2), Rooms.oxygen(40), Rooms.source("client_005")));
            ZoneInfo zone = r.zone(12, 2, 2);
            saved = r.dimension().simulateUnload(r.chunk(16));
            r.tick();
            h.assertTrue(r.zone(12, 2, 2).paused(), "the zone didn't pause");
            DebugOverlayPayload p = roundTrip(DebugOverlay.build(r.level, center, center, ALL));
            h.assertValueEqual(p.zones().size(), 1, "zones");
            DebugOverlayPayload.Zone z = p.zones().get(0);
            h.assertValueEqual(z.id(), zone.id().value(), "zone id");
            h.assertTrue(z.paused(), "the paused flag wasn't sent");
            h.assertValueEqual(p.cells().size(), zone.cellCount(), "the paused zone's cells");
        } finally {
            unwatch(h);
            r.dimension().simulateLoad(r.chunk(16), saved);
            r.tick();
        }
        h.succeed();
    }

    /**
     * Opening a zone onto a region that reaches an unloaded chunk holds the breach (GEO-026): the
     * zone is sent paused and the held region's cells are sent as unresolved, so the client draws
     * them magenta (CLIENT-005).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void client_005_payload_lists_unresolved_region(GameTestHelper h) {
        Rooms r = new Rooms(h, 61);
        r.clear(0, 23);
        r.shell(2, 9); // A, in the west chunk
        r.shell(9, 21); // B, unzoned, across both chunks; they share the wall at x 9
        BlockPos center = r.abs(9, 2, 2);
        watch(h, center);
        byte[] saved = null;
        try {
            r.settle(GasMutations.add(r.level, r.point(5, 2, 2), Rooms.oxygen(50), Rooms.source("client_005")));
            saved = r.dimension().simulateUnload(r.chunk(16));
            r.tick();
            r.set(9, 2, 2, Rooms.AIR);
            r.tick();
            h.assertTrue(r.zone(5, 2, 2).paused(), "the zone didn't pause at the held breach");
            DebugOverlayPayload p = roundTrip(DebugOverlay.build(r.level, center, center, ALL));
            h.assertTrue(p.zones().stream().allMatch(DebugOverlayPayload.Zone::paused), "a zone not paused");
            h.assertFalse(p.unresolved().isEmpty(), "no unresolved cells sent");
            boolean opening = false;
            for (DebugOverlayPayload.Unresolved u : p.unresolved()) {
                // Relative to the opening at area (9, 2, 2): B's interior runs from x 10 to 20.
                h.assertTrue(
                        u.dx() >= 0 && u.dx() <= 11 && Math.abs(u.dy()) <= 1 && Math.abs(u.dz()) <= 1,
                        "an unresolved cell outside B: " + u);
                opening |= u.dx() == 0 && u.dy() == 0 && u.dz() == 0;
            }
            h.assertTrue(opening, "the opening isn't unresolved");
        } finally {
            unwatch(h);
            r.dimension().simulateLoad(r.chunk(16), saved);
            r.ticks(3);
        }
        h.succeed();
    }

    /**
     * A broken window gives one direct face to the ambient, labelled with the zone's pressure
     * minus the ambient's (CLIENT-011).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void client_011_breach_edge_to_ambient(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = area(3);
        shell(level, o, o.offset(4, 4, 4));
        BlockPos window = o.offset(4, 2, 2);
        BlockPos center = o.offset(2, 2, 2);
        level.setBlockAndUpdate(window, Blocks.GLASS.defaultBlockState());
        watch(h, center);
        try {
            zoneWith(h, center, 500, "client_011");
            level.setBlockAndUpdate(window, AIR);
            tick(h);
            ZoneInfo zone = Atmospheres.zoneAt(level, center)
                    .orElseThrow(() -> new GameTestAssertException("the zone dissolved at once"));
            DebugOverlayPayload p = DebugOverlay.build(level, center, center, ALL);
            h.assertValueEqual(p.edges().size(), 1, "edges");
            DebugOverlayPayload.Edge e = p.edges().get(0);
            h.assertTrue(e.ambient(), "the edge doesn't lead to the ambient");
            h.assertTrue(e.direct(), "the opening isn't direct");
            near(
                    h,
                    e.pressureDifference(),
                    zone.mixture().pressure()
                            - Ambients.ambient(level.dimension()).pressure(),
                    "pressure difference");
            h.assertValueEqual(p.faces().size(), 1, "faces");
            DebugOverlayPayload.Face f = p.faces().get(0);
            h.assertValueEqual(List.of(f.dx(), f.dy(), f.dz()), List.of(1, 0, 0), "face position");
            h.assertValueEqual(f.face(), Face.EAST.ordinal(), "face");
            h.assertValueEqual(f.edge(), 0, "face's edge");
            h.assertValueEqual(roundTrip(p), p, "after the network codec");
        } finally {
            unwatch(h);
        }
        h.succeed();
    }

    // ---- CLIENT-012 and CLIENT-013 -------------------------------------------------------

    /**
     * The payload carries the gas geometry around the operator's eyes, so the client can show the
     * seal mask and sub-cells of the block it looks at: a pane joining east and west has two
     * sub-cells, stone none (CLIENT-012).
     */
    @GameTest(template = "empty")
    public static void client_012_probe_carries_block_geometry(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = area(4);
        BlockState pane = Blocks.GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.EAST, true)
                .setValue(IronBarsBlock.WEST, true);
        BlockPos panePos = o.offset(2, 0, 0);
        BlockPos stonePos = o.offset(0, 0, 3);
        level.setBlockAndUpdate(panePos, pane);
        level.setBlockAndUpdate(stonePos, STONE);
        DebugOverlayPayload p = roundTrip(DebugOverlay.build(level, o, o, DebugLayer.CELLS.bit()));
        h.assertFalse(p.probe().isEmpty(), "no probe");
        Geometry g = p.probe().geometryAt(panePos);
        h.assertValueEqual(g, Geometries.table().geometry(pane), "the pane's geometry");
        h.assertValueEqual(g.cellCount(), 2, "the pane's sub-cells");
        Geometry s = p.probe().geometryAt(stonePos);
        h.assertValueEqual(s.cellCount(), 0, "stone's cells");
        for (Face f : Face.values()) {
            h.assertTrue(s.isFaceFull(f), "stone's " + f + " face isn't sealed");
        }
        h.assertTrue(
                p.probe().geometryAt(o.offset(DebugOverlay.PROBE_RADIUS + 1, 0, 0)) == null,
                "the probe reaches beyond its radius");
        h.assertTrue(p.cells().isEmpty() && p.faces().isEmpty(), "zones or edges sent without their layers");
        h.succeed();
    }

    /**
     * The hotspot layer is carried, but nothing makes hotspots before fire (R1), so it is empty
     * (CLIENT-013).
     */
    @GameTest(template = "empty")
    public static void client_013_hotspot_layer_empty_before_fire(GameTestHelper h) {
        BlockPos o = area(5);
        DebugOverlayPayload p = roundTrip(DebugOverlay.build(h.getLevel(), o, o, DebugLayer.HOTSPOTS.bit()));
        h.assertTrue(p.has(DebugLayer.HOTSPOTS), "the hotspot layer is off");
        h.assertTrue(p.hotspots().isEmpty(), "hotspots without fire: " + p.hotspots());
        h.assertTrue(p.probe().isEmpty(), "probe sent without its layer");
        h.succeed();
    }
}
