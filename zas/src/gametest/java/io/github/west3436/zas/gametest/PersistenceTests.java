package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.service;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.persist.ChunkRecordCodec;
import io.github.west3436.zas.engine.scheduler.ChunkRecord;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.ZoneShare;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryOverride;
import io.github.west3436.zas.persistence.AtmosPersistence;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Chunk-aligned persistence in a real level (DATA-001 to DATA-004, DATA-008 to DATA-011, DATA-013).
 *
 * <p>A GameTest can neither unload chunks on demand nor restart the server, so two stand-ins are
 * used, both going through the real save code and byte format:
 *
 * <ul>
 *   <li><b>Unload and load</b>: {@link DimensionAtmos#simulateUnload} writes a chunk's atmosphere
 *       exactly as at a real unload and treats the chunk as unloaded; {@link
 *       DimensionAtmos#simulateLoad} loads it again from those bytes.
 *   <li><b>Restart</b>: {@code AtmosService#stopForTest} stops one level's atmosphere as a server
 *       stop does and returns what its chunks write in the final save; {@code startForTest} starts
 *       a fresh engine for the level from those bytes. These run in the Nether, in a batch of
 *       their own, so stopping it can't disturb other tests.
 * </ul>
 *
 * <p>The real chunk save and load events run too, whenever the server saves or unloads these
 * areas, and at the test server's shutdown.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PersistenceTests {
    private PersistenceTests() {}

    /** The batch of the restart tests, apart from tests that leave work pending in other levels. */
    private static final String RESTART = "data_restart";

    /**
     * The batch of the geometry palette test: the palette is the whole server's, so nothing else
     * may run while it is changed.
     */
    private static final String PALETTE = "data_009_palette";

    /** The level the restart tests stop and start: the Nether, which only quick tests otherwise use. */
    private static ServerLevel restartLevel(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw new GameTestAssertException("no Nether");
        }
        return nether;
    }

    /** A sealed room from (11, 11) to (20, 20): across the four chunks that meet at (16, 16). */
    private static Rooms fourChunks(GameTestHelper h, ServerLevel level, int index, double o2) {
        Rooms r = new Rooms(h, level, index);
        r.clear(8, 8, 23, 23);
        r.box(10, 10, 21, 21);
        MutationResult m = r.settle(GasMutations.add(r.level, r.point(12, 2, 12), oxygen(o2), source("data")));
        h.assertValueEqual(m.outcome(), MutationOutcome.ZONE, "the room didn't zone");
        return r;
    }

    private static List<ChunkPos> chunks(Rooms r) {
        return List.of(
                new ChunkPos(r.abs(0, 0, 0)),
                new ChunkPos(r.abs(16, 0, 0)),
                new ChunkPos(r.abs(0, 0, 16)),
                new ChunkPos(r.abs(16, 0, 16)));
    }

    /**
     * Each chunk saves its own cells and share; a zone across four chunks reassembles as they load
     * one at a time, paused until the last, and holds the sum of their shares (DATA-002, DATA-008).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void data_002_zone_reassembles_chunk_by_chunk(GameTestHelper h) {
        Rooms r = fourChunks(h, h.getLevel(), 20, 80);
        ZoneInfo before = r.zone(12, 2, 12);
        DimensionAtmos d = r.dimension();
        Map<ChunkPos, byte[]> saved = new LinkedHashMap<>();
        double o2Shares = 0;
        int cells = 0;
        for (ChunkPos c : chunks(r)) {
            byte[] bytes = d.simulateUnload(c);
            h.assertTrue(bytes != null, "chunk " + c + " saved nothing");
            saved.put(c, bytes);
            ChunkRecord rec = decode(h, d, bytes, c);
            h.assertValueEqual(rec.zones().size(), 1, "zones in chunk " + c);
            cells += rec.zones().get(0).cellCount();
            o2Shares += rec.zones().get(0).moles(d.registry().indexOf(O2.toString()));
            r.tick();
        }
        h.assertValueEqual(cells, before.cellCount(), "each chunk saved its own cells");
        r.close(o2Shares, before.mixture().moles(O2), "the shares' O2");
        h.assertValueEqual(
                Atmospheres.at(r.level, r.abs(12, 2, 12)).state(), AtmosphereState.UNRESOLVED, "a stored zone's point");
        int loaded = 0;
        for (Map.Entry<ChunkPos, byte[]> e : saved.entrySet()) {
            d.simulateLoad(e.getKey(), e.getValue());
            r.ticks(2);
            loaded++;
            if (loaded < saved.size()) {
                ZoneInfo partial = Atmospheres.zone(r.level, before.id()).orElseThrow();
                h.assertTrue(partial.paused(), "a partly loaded zone isn't paused");
            }
        }
        ZoneInfo after = Atmospheres.zone(r.level, before.id())
                .orElseThrow(() -> new GameTestAssertException("the zone wasn't rebuilt under its id"));
        h.assertFalse(after.paused(), "the rebuilt zone is paused");
        r.same(before, after, "the reassembled zone");
        h.succeed();
    }

    /** A zone rebuilt from its chunks holds the sum of their shares' moles (DATA-008). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void data_008_reloaded_zone_moles(GameTestHelper h) {
        Rooms r = fourChunks(h, h.getLevel(), 21, 33);
        ZoneInfo before = r.zone(12, 2, 12);
        DimensionAtmos d = r.dimension();
        Map<ChunkPos, byte[]> saved = new LinkedHashMap<>();
        double n2 = 0;
        double o2 = 0;
        for (ChunkPos c : chunks(r)) {
            saved.put(c, d.simulateUnload(c));
            ChunkRecord rec = decode(h, d, saved.get(c), c);
            n2 += rec.zones().get(0).moles(d.registry().indexOf(N2.toString()));
            o2 += rec.zones().get(0).moles(d.registry().indexOf(O2.toString()));
        }
        r.tick();
        saved.forEach(d::simulateLoad);
        r.ticks(2);
        ZoneInfo after = Atmospheres.zone(r.level, before.id()).orElseThrow();
        r.close(after.mixture().moles(N2), n2, "N2");
        r.close(after.mixture().moles(O2), o2, "O2");
        r.close(after.mixture().moles(O2), before.mixture().moles(O2), "O2 kept");
        h.succeed();
    }

    /**
     * Edges aren't saved; they are rebuilt when the zones are, and start awake (DATA-009). Hotspots
     * don't exist before FIRE (R1), so there is nothing of them to save or rebuild yet.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void data_009_edges_rebuilt(GameTestHelper h) {
        Rooms r = new Rooms(h, 22);
        r.clear(0, 23);
        r.shell(2, 9);
        r.shell(9, 21);
        r.settle(GasMutations.add(r.level, r.point(5, 2, 2), oxygen(600), source("data_009")));
        r.settle(GasMutations.add(r.level, r.point(15, 2, 2), oxygen(5), source("data_009")));
        r.set(9, 2, 2, AIR);
        r.tick();
        ZoneInfo a = r.zone(5, 2, 2);
        ZoneInfo b = r.zone(15, 2, 2);
        h.assertFalse(a.id().equals(b.id()), "merged already");
        h.assertValueEqual(a.edges().zoneEdges(), 1, "the edge before saving");
        DimensionAtmos d = r.dimension();
        byte[] west = d.simulateUnload(r.chunk(0));
        byte[] east = d.simulateUnload(r.chunk(16));
        r.tick();
        h.assertFalse(r.zoned(5, 2, 2), "the zones stayed in memory");
        d.simulateLoad(r.chunk(0), west);
        d.simulateLoad(r.chunk(16), east);
        r.ticks(2);
        ZoneInfo a2 = r.zone(5, 2, 2);
        h.assertValueEqual(a2.id(), a.id(), "zone A's id");
        h.assertValueEqual(r.zone(15, 2, 2).id(), b.id(), "zone B's id");
        h.assertValueEqual(a2.edges().zoneEdges(), 1, "the edge wasn't rebuilt");
        h.succeed();
    }

    /**
     * Corrupt atmos data never stops a chunk loading: its cells revert to unzoned, with one warning
     * naming the dimension and chunk; the rest of the zone is rebuilt (DATA-004, DATA-013).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void data_004_corrupt_chunk_reverts_to_unzoned(GameTestHelper h) {
        Rooms r = new Rooms(h, 23);
        r.clear(8, 23);
        r.shell(10, 21);
        r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(90), source("data_004")));
        ZoneInfo before = r.zone(12, 2, 2);
        DimensionAtmos d = r.dimension();
        ChunkPos westPos = r.chunk(0);
        ChunkPos eastPos = r.chunk(16);
        byte[] west = d.simulateUnload(westPos);
        byte[] east = d.simulateUnload(eastPos);
        r.tick();
        double westO2 = decode(h, d, west, westPos)
                .share(before.id().value())
                .moles(d.registry().indexOf(O2.toString()));
        byte[] corrupt = east.clone();
        corrupt[corrupt.length / 2] ^= 0x5A;
        int warnings = AtmosPersistence.corruptionWarnings().size();
        d.simulateLoad(westPos, west);
        d.simulateLoad(eastPos, corrupt);
        List<String> logged = AtmosPersistence.corruptionWarnings();
        h.assertValueEqual(logged.size(), warnings + 1, "corruption warnings");
        String w = logged.get(logged.size() - 1);
        h.assertTrue(
                w.contains(r.level.dimension().location().toString())
                        && w.contains("[" + eastPos.x + ", " + eastPos.z + "]"),
                "the warning doesn't name the dimension and chunk: " + w);
        r.ticks(3);
        ZoneInfo after = r.zone(12, 2, 2);
        h.assertFalse(after.paused(), "paused");
        // The corrupt chunk's 45 cells came back unzoned, holding the ambient, and joined again.
        double ambientO2 = Ambients.ambient(Level.OVERWORLD).moles(O2.toString(), 45_000);
        r.close(after.mixture().moles(O2), westO2 + ambientO2, "O2: the west share plus the east cells' ambient");
        h.succeed();
    }

    /**
     * A restart keeps zone membership and mixtures, applies queued additions once, completes queued
     * removals with nothing moved, and never reissues a zone id (DATA-001, DATA-011, CORE-026).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = RESTART)
    public static void data_001_restart_preserves_atmosphere(GameTestHelper h) {
        ServerLevel end = restartLevel(h);
        Rooms r = fourChunks(h, end, 24, 50);
        ZoneInfo before = r.zone(12, 2, 12);
        Map<Long, byte[]> saved = service().stopForTest(end, keys(r));
        h.assertValueEqual(saved.size(), 4, "chunks saved at shutdown");
        service().startForTest(end, saved);
        r.ticks(2);
        ZoneInfo after = Atmospheres.zone(end, before.id())
                .orElseThrow(() -> new GameTestAssertException("the zone didn't survive the restart"));
        h.assertFalse(after.paused(), "paused after the restart");
        r.same(before, after, "after the restart");
        // A new zone gets a new id.
        r.clear(30, 50);
        r.shell(32, 38);
        r.settle(GasMutations.add(end, r.point(35, 2, 2), oxygen(1), source("data_001")));
        h.assertTrue(r.zone(35, 2, 2).id().value() > before.id().value(), "a zone id was reissued");
        h.succeed();
    }

    /**
     * Queued additions are saved at shutdown and applied after load; queued removals complete with
     * nothing moved (DATA-011).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = RESTART)
    public static void data_011_queued_operations_at_shutdown(GameTestHelper h) {
        ServerLevel end = restartLevel(h);
        Rooms r = fourChunks(h, end, 25, 50);
        ZoneInfo before = r.zone(12, 2, 12);
        CompletableFuture<MutationResult> add =
                GasMutations.add(end, r.point(13, 2, 13), oxygen(4), source("data_011"));
        CompletableFuture<MutationResult> remove =
                GasMutations.remove(end, r.point(13, 2, 13), Map.of(O2, 3.0), source("data_011"));
        Map<Long, byte[]> saved = service().stopForTest(end, keys(r));
        h.assertTrue(remove.isDone(), "the removal didn't complete at shutdown");
        MutationResult removed = remove.join();
        h.assertValueEqual(removed.outcome(), MutationOutcome.PAUSED, "removal outcome");
        h.assertTrue(removed.moved().isEmpty(), "the removal moved gas");
        try {
            add.join();
            throw new GameTestAssertException("the addition completed as if applied");
        } catch (CancellationException | CompletionException expected) {
            // its gas is saved instead
        }
        service().startForTest(end, saved);
        r.ticks(2);
        ZoneInfo after = Atmospheres.zone(end, before.id()).orElseThrow();
        r.close(after.mixture().moles(O2), before.mixture().moles(O2) + 4, "O2: the addition, once");
        h.succeed();
    }

    /**
     * A change held for a paused zone is saved in its chunk and applies when the zone resumes,
     * across a restart (DATA-010).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = RESTART)
    public static void data_010_held_change_survives_restart(GameTestHelper h) {
        ServerLevel end = restartLevel(h);
        Rooms r = new Rooms(h, end, 26);
        r.clear(8, 23);
        r.shell(10, 21);
        r.settle(GasMutations.add(end, r.point(12, 2, 2), oxygen(300), source("data_010")));
        ZoneInfo before = r.zone(12, 2, 2);
        DimensionAtmos d = r.dimension();
        byte[] east = d.simulateUnload(r.chunk(16));
        r.tick();
        r.set(10, 2, 2, AIR); // held: the zone is paused
        r.tick();
        h.assertFalse(r.zone(12, 2, 2).breached(), "the change applied while paused");
        Map<Long, byte[]> saved = new HashMap<>(service().stopForTest(end, r.chunkKey(0)));
        ChunkRecord west = decode(h, d, saved.get(r.chunkKey(0)), r.chunk(0));
        h.assertTrue(west.heldBlocks().length > 0, "the held change wasn't saved");
        saved.put(r.chunkKey(16), east);
        service().startForTest(end, saved);
        r.ticks(3);
        ZoneInfo after = Atmospheres.zone(end, before.id()).orElseThrow();
        h.assertTrue(after.breached(), "the held change didn't apply after the restart");
        h.succeed();
    }

    /**
     * A geometry palette change rebuilds the zones from the loaded chunks' records, as after a
     * restart (DATA-008, DATA-009, GEO-040, architecture §4.5): zones in loaded chunks come back
     * under their ids with their gas, a block whose geometry changed is classified again, a queued
     * addition applies once, a pinned zone paused at an unloaded chunk keeps its pin and waits for
     * that chunk, whose saved record no save overwrites, and the checker finds nothing lost.
     *
     * <p>The change is a test override that opens glass (a full cube, so solid) into one cell of
     * 1,000 L, applied as a {@code /reload} would; it is undone, and the level rebuilt again, when
     * the test ends.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = PALETTE)
    public static void data_009_palette_change_rebuilds_zones(GameTestHelper h) {
        Rooms r = new Rooms(h, 30);
        DimensionAtmos d = r.dimension();
        try {
            r.clear(8, 56);
            r.shell(10, 21); // across chunks 0 and 16
            r.set(13, 1, 1, Blocks.GLASS.defaultBlockState()); // solid, inside the room
            r.shell(42, 53); // across chunks 32 and 48
            r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(200), source("data_009")));
            r.settle(GasMutations.add(r.level, r.point(44, 2, 2), oxygen(150), source("data_009")));
            h.assertFalse(r.zoned(13, 1, 1), "the glass is a cell before the change");
            long pinnedId = r.zone(44, 2, 2).id().value();
            // Nothing in R0 pins a zone, so the test pins it directly, between atmos ticks.
            d.inspect(engine -> {
                        engine.graph().setPinned(engine.graph().zone(pinnedId), true);
                        return true;
                    })
                    .get(5, TimeUnit.SECONDS);
            r.tick();
            h.assertTrue(d.published().zone(pinnedId).pinned(), "the zone isn't pinned");
            byte[] farSaved = d.simulateUnload(r.chunk(48));
            h.assertTrue(farSaved != null, "the unloaded chunk saved nothing");
            r.tick();
            ZoneInfo room = r.zone(12, 2, 2);
            ZoneInfo pinned = r.zone(44, 2, 2);
            h.assertTrue(pinned.paused(), "the zone across the unloaded chunk isn't paused");
            CompletableFuture<MutationResult> queued =
                    GasMutations.add(r.level, r.point(12, 2, 2), oxygen(4), source("data_009"));
            DimensionPipeline oldEngine = d.pipeline();

            Geometries.overrideForTest(List.of(new GeometryOverride(
                    List.of(new GeometryOverride.Target(ResourceLocation.withDefaultNamespace("glass"), false)),
                    Set.of(),
                    1000)));
            MutationResult added = r.settle(queued);
            h.assertTrue(d.pipeline() != oldEngine, "the engine wasn't rebuilt");
            h.assertValueEqual(added.outcome(), MutationOutcome.ZONE, "the queued addition's outcome");
            r.ticks(2);
            CompletableFuture<ConservationReport> check = d.checkConservation();
            r.tick();
            ConservationReport report = check.getNow(null);
            h.assertTrue(report != null && report.conserved(), "the checker reported " + report);

            // The room: rebuilt under its id, the glass now one of its cells, holding the ambient
            // it opened with; the queued addition applied once.
            ZoneInfo after = r.zone(12, 2, 2);
            h.assertValueEqual(after.id(), room.id(), "the room's id");
            h.assertFalse(after.paused(), "the room is paused");
            h.assertValueEqual(r.zone(13, 1, 1).id(), room.id(), "the glass wasn't classified again");
            h.assertValueEqual(after.cellCount(), room.cellCount() + 1, "the room's cells");
            double ambientO2 = Ambients.ambient(Level.OVERWORLD).moles(O2.toString(), 1000);
            double ambientN2 = Ambients.ambient(Level.OVERWORLD).moles(N2.toString(), 1000);
            r.close(after.mixture().moles(O2), room.mixture().moles(O2) + 4 + ambientO2, "the room's O2");
            r.close(after.mixture().moles(N2), room.mixture().moles(N2) + ambientN2, "the room's N2");
            ChunkRecord west = decode(h, d, d.simulateSave(r.chunk(0)), r.chunk(0));
            h.assertTrue(west.share(room.id().value()) != null, "a save of a loaded chunk drops the room");

            // The pinned zone: rebuilt from its loaded chunk, waiting for the unloaded one, whose
            // saved record a save leaves as it was.
            ZoneView waiting = d.published().zone(pinnedId);
            h.assertTrue(waiting != null && waiting.paused(), "the zone across the unloaded chunk: " + waiting);
            h.assertTrue(waiting.pinned(), "the pin was lost in the rebuild");
            ZoneShare near =
                    decode(h, d, d.simulateSave(r.chunk(32)), r.chunk(32)).share(pinnedId);
            h.assertTrue(near != null && near.pinned(), "the loaded chunk's save of the pinned zone: " + near);
            h.assertTrue(
                    Arrays.equals(d.simulateSave(r.chunk(48)), farSaved), "the unloaded chunk's saved record changed");
            d.simulateLoad(r.chunk(48), farSaved);
            r.ticks(2);
            ZoneInfo resumed = Atmospheres.zone(r.level, pinned.id())
                    .orElseThrow(() -> new GameTestAssertException("the pinned zone wasn't rebuilt"));
            h.assertFalse(resumed.paused(), "the pinned zone didn't resume");
            r.same(pinned, resumed, "the pinned zone");
            h.assertTrue(d.published().zone(pinnedId).pinned(), "the pin was lost when the chunk loaded");
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException
                | InterruptedException e) {
            throw new GameTestAssertException("pinning the zone failed: " + e);
        } finally {
            Geometries.overrideForTest(List.of());
            r.tick(); // rebuilds the level with the normal palette before the next batch
        }
        h.succeed();
    }

    private static long[] keys(Rooms r) {
        List<ChunkPos> cs = chunks(r);
        long[] out = new long[cs.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = cs.get(i).toLong();
        }
        return out;
    }

    private static ChunkRecord decode(GameTestHelper h, DimensionAtmos d, byte[] bytes, ChunkPos pos) {
        try {
            return ChunkRecordCodec.decode(bytes, pos.toLong(), d.registry(), m -> {});
        } catch (ChunkRecordCodec.CorruptRecordException e) {
            throw new GameTestAssertException("unreadable save of " + pos + ": " + e.getMessage());
        }
    }
}
