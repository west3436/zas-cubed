package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.portable.PortableBlock;
import io.github.west3436.zas.atmos.portable.PortablePumpBlockEntity;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentSettings;
import io.github.west3436.zas.config.ZasServerConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the world saves of the gas in pipes and portables, against the exploit checklist (NFR-012). A
 * chunk saves only when told it has something new, so gas that leaves after a save would come back
 * from that save unless the chunk is told again. These tests make the save happen when they choose,
 * then read what it wrote: what a restart would load, summed apart from the conservation checker
 * (CORE-073), which sees only zones.
 *
 * <p>They wait for chunks in real time, in batches of their own. The test that counts what the ledger
 * voided has a batch to itself, as the ledger covers the whole dimension: another test's row, cleared
 * away, would count as well.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeSaveTests {
    private static final String BATCH = "zas_atmos_pipe_saves";
    private static final String TRANSFER = "zas_atmos_pipe_saves_transfer";
    private static final TicketType<ChunkPos> HOLD =
            TicketType.create("zas_atmos_pipe_save_hold", Comparator.comparingLong(ChunkPos::toLong));

    /** The row's ends: a cap at x = 11, pipes at 12 to 19, and an end at x = 20, in chunk B. */
    private static final int WEST = 11;

    private static final int EAST = 20;
    static final int BORDER = 16;
    static final int Y = 2;
    static final int Z = 2;
    static final int ROOM = 22;
    static final double MOLES = 1_000;

    /** Game ticks for what was just built to settle: each node has stepped, so nothing else marks its chunk. */
    private static final int SETTLE = 30;

    /** What the row holds. A room starts with the ambient's air, so only this gas is the row's. */
    static final ResourceLocation GAS = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    private PipeSaveTests() {}

    private static BlockState cap(Direction facing) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing);
    }

    static void hold(Area a, ChunkPos chunk, boolean on) {
        if (on) {
            a.level.getChunkSource().addRegionTicket(HOLD, chunk, 0, chunk);
        } else {
            a.level.getChunkSource().removeRegionTicket(HOLD, chunk, 0, chunk);
        }
    }

    /** Whether a chunk is loaded in full: asking loads nothing. */
    private static boolean full(Area a, ChunkPos chunk) {
        return a.level.getChunkSource().hasChunk(chunk.x, chunk.z);
    }

    /** Saves every chunk there is something new to save in and flushes, as a save-all or a stop does. */
    private static void saveAll(Area a) {
        a.level.getChunkSource().save(true);
    }

    /** The moles of the row's gas a chunk's block entities last wrote: what a restart would load. */
    static double written(Area a, ChunkPos chunk) {
        CompoundTag saved = a.level
                .getChunkSource()
                .chunkMap
                .read(chunk)
                .join()
                .orElseThrow(() -> new AssertionError("chunk " + chunk + " has never been written"));
        ListTag entities = saved.getList("block_entities", Tag.TAG_COMPOUND);
        double moles = 0;
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = entities.getCompound(i);
            if (entity.contains("Gas")) {
                moles += GasStore.parcel(entity.getCompound("Gas")).moles(GAS);
            }
        }
        return moles;
    }

    /** A row of pipes across the border between an area's first two chunks, each with a ticket. */
    private static final class Row {
        final Area a;
        final ChunkPos chunkA;
        final ChunkPos chunkB;
        final PipeNodeEntity west;
        final PipeNodeEntity east;
        final List<PipeNodeEntity> nodesB = new ArrayList<>();

        Row(GameTestHelper h, int index) {
            a = new Area(h, index);
            chunkA = new ChunkPos(a.abs(0, 0, 0));
            chunkB = new ChunkPos(a.abs(BORDER, 0, 0));
            hold(a, chunkA, true);
            hold(a, chunkB, true);
            a.clear(WEST - 1, Z - 1, EAST + 1, Z + 1);
            a.place(WEST, Y, Z, cap(Direction.EAST));
            for (int x = WEST + 1; x < EAST; x++) {
                a.place(x, Y, Z, GasDeviceTests.pipe());
            }
            a.place(EAST, Y, Z, cap(Direction.WEST));
            west = a.entity(WEST + 1, Y, Z);
            east = a.entity(EAST - 1, Y, Z);
            for (int x = BORDER; x <= EAST; x++) {
                nodesB.add(a.entity(x, Y, Z));
            }
            west.network().gas().add(new GasParcel(Map.of(GAS, MOLES), T));
        }

        /** What chunk B's nodes hold: kept from before it left, so asking loads nothing. */
        double heldInB() {
            return nodesB.stream().mapToDouble(n -> n.share().totalMoles()).sum();
        }

        double written() {
            return PipeSaveTests.written(a, chunkA) + PipeSaveTests.written(a, chunkB);
        }

        void done() {
            hold(a, chunkA, true);
            hold(a, chunkB, true);
            a.clear(WEST - 1, Z - 1, EAST + 1, Z + 1);
            hold(a, chunkA, false);
            hold(a, chunkB, false);
        }
    }

    /**
     * A row in one chunk with no zone near it settles and is saved, then loses half its gas as a vent
     * elsewhere on it would take it. Nothing else in the chunk changes, so only the network can say it
     * has something new to save. The row comes back from an unload with what it had left, not what it
     * had.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void nfr_012_a_network_keeps_what_it_lost_since_its_chunk_last_saved(GameTestHelper h) {
        Area a = new Area(h, 2004);
        ChunkPos chunk = new ChunkPos(a.abs(0, 0, 0));
        hold(a, chunk, true);
        a.clear(0, 0, 12, 4);
        a.place(1, Y, Z, cap(Direction.EAST));
        for (int x = 2; x < 8; x++) {
            a.place(x, Y, Z, GasDeviceTests.pipe());
        }
        a.place(8, Y, Z, cap(Direction.WEST));
        PipeNodeEntity[] node = {a.entity(4, Y, Z)};
        node[0].network().gas().add(new GasParcel(Map.of(GAS, MOLES), T));
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    saveAll(a);
                    a.close(written(a, chunk), MOLES, "what the save wrote");
                    h.assertFalse(a.level.getChunk(chunk.x, chunk.z).isUnsaved(), "the chunk didn't save");
                    node[0].network().gas().takeFraction(0.5);
                    hold(a, chunk, false);
                })
                .thenWaitUntil(() -> h.assertTrue(node[0].isRemoved(), "the row hasn't unloaded"))
                .thenExecute(() -> {
                    a.close(written(a, chunk), MOLES / 2, "what the unload wrote");
                    hold(a, chunk, true);
                })
                .thenWaitUntil(() -> h.assertTrue(full(a, chunk), "the row hasn't come back"))
                .thenExecute(() -> {
                    a.close(a.<PipeNodeEntity>entity(4, Y, Z).network().gas().totalMoles(), MOLES / 2, "the row");
                    a.clear(0, 0, 12, 4);
                    hold(a, chunk, false);
                })
                .thenSucceed();
    }

    /**
     * Both of a row's chunks are told of a change, once it has saved. One save-all later, what they
     * wrote adds up to what the row holds. A network that sits idle tells neither, so it costs no saves.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void nfr_012_a_save_writes_both_sides_of_a_network_as_it_is_now(GameTestHelper h) {
        Row r = new Row(h, 2005);
        Area a = r.a;
        LevelChunk[] chunks = new LevelChunk[2];
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    saveAll(a);
                    a.close(r.written(), MOLES, "what the first save wrote");
                    chunks[0] = r.west.savedIn();
                    chunks[1] = r.east.savedIn();
                    h.assertTrue(chunks[0] != chunks[1], "the row doesn't cross a border");
                })
                .thenIdle(60) // three atmos ticks of an idle network
                .thenExecute(() -> {
                    h.assertFalse(chunks[0].isUnsaved(), "an idle network told chunk A to save");
                    h.assertFalse(chunks[1].isUnsaved(), "an idle network told chunk B to save");
                    r.west.network().gas().takeFraction(0.5);
                    h.assertTrue(chunks[0].isUnsaved(), "a change didn't tell chunk A");
                    h.assertTrue(chunks[1].isUnsaved(), "a change didn't tell chunk B");
                    saveAll(a);
                    a.close(r.written(), MOLES / 2, "what the second save wrote");
                    r.done();
                })
                .thenSucceed();
    }

    /**
     * The far chunk leaves full status, and a save-all writes it. The game won't write it in a later
     * one, as it is no longer in play: only its unload will. So gas taken at the near end must leave
     * the far chunk's share alone, and what the world's files hold stays what the row holds. Nothing
     * here loads the far chunk.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void nfr_012_a_network_stops_changing_a_chunk_that_has_left_full_status(GameTestHelper h) {
        Row r = new Row(h, 2006);
        Area a = r.a;
        double[] far = new double[1];
        double[] left = new double[1];
        h.startSequence()
                .thenExecute(() -> hold(a, r.chunkB, false))
                .thenWaitUntil(() -> h.assertFalse(full(a, r.chunkB), "chunk B is still loaded"))
                .thenExecute(() -> {
                    saveAll(a);
                    far[0] = written(a, r.chunkB);
                    h.assertTrue(far[0] > 0, "chunk B wrote no gas");
                    a.close(r.written(), MOLES, "what the first save wrote");
                    double near = r.west.network().gas().totalMoles();
                    r.west.network().gas().takeFraction(0.5);
                    left[0] = r.west.network().gas().totalMoles();
                    h.assertFalse(full(a, r.chunkB), "taking gas loaded chunk B");
                    h.assertTrue(left[0] < near, "the near side lost no gas");
                    saveAll(a);
                    a.close(written(a, r.chunkA), left[0], "what chunk A wrote");
                    a.close(written(a, r.chunkB), far[0], "what chunk B wrote");
                    a.close(r.heldInB(), far[0], "what chunk B holds");
                    hold(a, r.chunkB, true);
                })
                .thenWaitUntil(() -> h.assertTrue(full(a, r.chunkB), "chunk B hasn't returned"))
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(r.west.network() == r.east.network(), "chunk B didn't rejoin the row");
                    a.close(r.west.network().gas().totalMoles(), left[0] + far[0], "the row");
                    r.done();
                })
                .thenSucceed();
    }

    /**
     * Gas reaches a node whose chunk has left full status, as a removal on its way when the chunk left
     * would. The node keeps it, that doesn't load the chunk, and the chunk saves it when it unloads,
     * though the level no longer hands the chunk out.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void nfr_012_gas_reaching_a_chunk_that_has_left_full_status_is_saved(GameTestHelper h) {
        Row r = new Row(h, 2007);
        Area a = r.a;
        double expected = MOLES + 100.0;
        h.startSequence()
                .thenExecute(() -> hold(a, r.chunkB, false))
                .thenWaitUntil(() -> h.assertFalse(full(a, r.chunkB), "chunk B is still loaded"))
                .thenExecute(() -> {
                    saveAll(a);
                    r.east.network().gas().add(new GasParcel(Map.of(GAS, 100.0), T));
                    h.assertFalse(full(a, r.chunkB), "the gas loaded chunk B");
                    h.assertTrue(r.east.savedIn().isUnsaved(), "chunk B wasn't told");
                    h.assertFalse(r.west.network() == r.east.currentNetwork(), "the row reached into chunk B");
                    hold(a, r.chunkA, false);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(r.west.isRemoved(), "chunk A hasn't unloaded");
                    h.assertTrue(r.east.isRemoved(), "chunk B hasn't unloaded");
                })
                .thenExecute(() -> {
                    a.close(r.written(), expected, "what the unloads wrote");
                    hold(a, r.chunkA, true);
                    hold(a, r.chunkB, true);
                })
                .thenWaitUntil(() -> h.assertTrue(full(a, r.chunkA) && full(a, r.chunkB), "the row hasn't come back"))
                .thenIdle(60)
                .thenExecute(() -> {
                    PipeNodeEntity back = a.entity(WEST + 1, Y, Z);
                    a.close(back.network().gas().totalMoles(), expected, "the row");
                    r.done();
                })
                .thenSucceed();
    }

    /** A canister saved, emptied by half, and unloaded comes back with the half that's left. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void nfr_012_a_canister_keeps_what_it_lost_since_its_chunk_last_saved(GameTestHelper h) {
        Area a = new Area(h, 2008);
        ChunkPos chunk = new ChunkPos(a.abs(0, 0, 0));
        hold(a, chunk, true);
        a.clear(0, 0, 12, 4);
        a.place(2, Y, Z, GasDeviceTests.canister());
        CanisterBlockEntity[] canister = {a.entity(2, Y, Z)};
        canister[0].network().gas().add(new GasParcel(Map.of(GAS, MOLES), T));
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    saveAll(a);
                    a.close(written(a, chunk), MOLES, "what the save wrote");
                    canister[0].network().gas().takeFraction(0.5); // what its valve does
                    hold(a, chunk, false);
                })
                .thenWaitUntil(() -> h.assertTrue(canister[0].isRemoved(), "the canister hasn't unloaded"))
                .thenExecute(() -> hold(a, chunk, true))
                .thenWaitUntil(() -> h.assertTrue(full(a, chunk), "the canister hasn't come back"))
                .thenExecute(() -> {
                    CanisterBlockEntity back = a.entity(2, Y, Z);
                    a.close(back.network().gas().totalMoles(), MOLES / 2, "the canister");
                    a.clear(0, 0, 12, 4);
                    hold(a, chunk, false);
                })
                .thenSucceed();
    }

    /** An inline device's port saved, emptied by half, and unloaded comes back with the half that's left. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void nfr_012_a_device_port_keeps_what_it_lost_since_its_chunk_last_saved(GameTestHelper h) {
        Area a = new Area(h, 2010);
        ChunkPos chunk = new ChunkPos(a.abs(0, 0, 0));
        hold(a, chunk, true);
        a.clear(0, 0, 12, 4);
        a.place(
                2,
                Y,
                Z,
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        PressurePumpBlockEntity[] pump = {a.entity(2, Y, Z)};
        pump[0].port(0).network().gas().add(new GasParcel(Map.of(GAS, MOLES), T));
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    saveAll(a);
                    pump[0].port(0).network().gas().takeFraction(0.5);
                    h.assertTrue(pump[0].savedIn().isUnsaved(), "the port's chunk wasn't told");
                    hold(a, chunk, false);
                })
                .thenWaitUntil(() -> h.assertTrue(pump[0].isRemoved(), "the pump hasn't unloaded"))
                .thenExecute(() -> hold(a, chunk, true))
                .thenWaitUntil(() -> h.assertTrue(full(a, chunk), "the pump hasn't come back"))
                .thenExecute(() -> {
                    PressurePumpBlockEntity back = a.entity(2, Y, Z);
                    a.close(back.port(0).network().gas().totalMoles(), MOLES / 2, "the port");
                    a.clear(0, 0, 12, 4);
                    hold(a, chunk, false);
                })
                .thenSucceed();
    }

    /**
     * A portable pump pumps from its store into a held tank, with no zone to mark its chunk. Saved and
     * pumping on, then unloaded, it wrote what it had left, and it comes back with no more gas than
     * went in.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void nfr_012_a_portable_keeps_what_it_lost_since_its_chunk_last_saved(GameTestHelper h) {
        Area a = new Area(h, 2009);
        ChunkPos chunk = new ChunkPos(a.abs(0, 0, 0));
        hold(a, chunk, true);
        a.clear(0, 0, 12, 4);
        a.place(
                2,
                Y,
                Z,
                AtmosBlocks.PORTABLE_PUMP.get().defaultBlockState().setValue(PortableBlock.FACING, Direction.NORTH));
        PortablePumpBlockEntity[] pump = {a.entity(2, Y, Z)};
        pump[0].store().add(new GasParcel(Map.of(GAS, 100.0), T));
        BatteryTests.swap(pump[0].battery(), AtmosItems.BATTERY.get().full());
        pump[0].tank().insertItem(0, new ItemStack(AtmosItems.EMPTY_TANK.get()), false);
        pump[0].setTarget(1_000);
        pump[0].setOn(true);
        double[] saved = new double[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(pump[0].store().moles(GAS) < 100.0, "the pump hasn't started"))
                .thenExecute(() -> {
                    saveAll(a);
                    saved[0] = pump[0].store().moles(GAS);
                    a.close(written(a, chunk), saved[0], "what the save wrote");
                })
                .thenWaitUntil(() -> h.assertTrue(pump[0].store().moles(GAS) < saved[0], "the pump hasn't gone on"))
                .thenExecute(() -> hold(a, chunk, false))
                .thenWaitUntil(() -> h.assertTrue(pump[0].isRemoved(), "the pump hasn't unloaded"))
                .thenExecute(() -> {
                    a.close(written(a, chunk), pump[0].store().moles(GAS), "what the unload wrote");
                    hold(a, chunk, true);
                })
                .thenWaitUntil(() -> h.assertTrue(full(a, chunk), "the pump hasn't come back"))
                .thenExecute(() -> {
                    PortablePumpBlockEntity back = a.entity(2, Y, Z);
                    a.close(
                            back.store().moles(GAS)
                                    + GasTankItem.contents(back.tank().tank()).moles(GAS),
                            100.0,
                            "the store and the tank");
                    back.setOn(false);
                    a.clear(0, 0, 12, 4);
                    hold(a, chunk, false);
                })
                .thenSucceed();
    }

    /** A row across a border, ending in a test-powered vent into a sealed room in chunk B. */
    static final class VentRig {
        final Area a;
        final ChunkPos chunkA;
        final ChunkPos chunkB;
        VentBlockEntity vent;

        VentRig(GameTestHelper h, int index) {
            a = new Area(h, index);
            chunkA = new ChunkPos(a.abs(0, 0, 0));
            chunkB = new ChunkPos(a.abs(BORDER, 0, 0));
            hold(true);
            a.clear(WEST - 1, Z - 1, EAST - 1, Z + 1);
            a.box(EAST, 0, EAST + 4, 4);
            a.place(WEST, Y, Z, cap(Direction.EAST));
            for (int x = WEST + 1; x < EAST; x++) {
                a.place(x, Y, Z, GasDeviceTests.pipe());
            }
            // A canister on a port adds volume, which slows the vent, so a transfer outlasts an unload.
            a.place(WEST + 1, Y + 1, Z, GasDeviceTests.port());
            a.place(WEST + 1, Y + 2, Z, GasDeviceTests.canister());
            a.place(EAST, Y, Z, GasDeviceTests.vent(Direction.EAST));
            vent = a.entity(EAST, Y, Z);
            vent.power().setTestPowered(true);
            vent.network().gas().add(new GasParcel(Map.of(GAS, MOLES), T));
        }

        void hold(boolean on) {
            PipeSaveTests.hold(a, chunkA, on);
            PipeSaveTests.hold(a, chunkB, on);
        }

        boolean loaded() {
            return full(a, chunkA) && full(a, chunkB);
        }

        /** The row's gas, wherever it is in the two chunks, once nothing is in flight. */
        double stored() {
            return GasCensus.total(a, GAS, chunkA, chunkB);
        }

        void done() {
            vent.command(VentSettings.OFF);
            vent.power().setTestPowered(false);
            a.clear(WEST - 1, Z - 1, EAST + 5, Z + 3);
            hold(false);
        }
    }

    /**
     * Both chunks really unload during a transfer, twice, and load again: once nothing is in flight,
     * the row and the room hold what they held, and no atmos tick reported an unexplained change. Gas
     * that was on its way to the room as it paused is voided (GEO-036), so what the ledger says it
     * voided counts as held, and anything it says was supplied (GEO-038) doesn't. So gas may be lost
     * this way, by design, but can't be made.
     */
    @GameTest(template = "empty", batch = TRANSFER, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void nfr_012_chunks_unloading_during_a_transfer_make_no_gas(GameTestHelper h) {
        boolean was = ZasServerConfig.CONSERVATION_CHECKER.get();
        ZasServerConfig.CONSERVATION_CHECKER.set(true);
        VentRig r = new VentRig(h, 2001);
        Area a = r.a;
        double[] start = new double[1];
        VentBlockEntity[] old = new VentBlockEntity[1];
        CompletableFuture<?> formed =
                GasMutations.add(a.level, a.point(ROOM, Y, Z), new GasParcel(Map.of(N2, 10.0), T), Area.SOURCE);
        GasCensus.Ledger ledger = new GasCensus.Ledger(a, GAS);
        h.onEachTick(ledger::read);
        var sequence = h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(formed.isDone(), "the room's zone hasn't formed"))
                .thenWaitUntil(() -> h.assertTrue(
                        Atmospheres.zoneAt(a.level, a.abs(ROOM, Y, Z)).isPresent(), "no zone is published"))
                .thenExecute(() -> {
                    start[0] = r.stored();
                    r.vent.command(VentSettings.blow(5000));
                })
                .thenWaitUntil(() ->
                        h.assertTrue(r.vent.network().gas().totalMoles() < MOLES * 0.9, "the vent hasn't started"));
        for (int round = 0; round < 2; round++) {
            sequence = sequence.thenExecute(() -> {
                        old[0] = r.vent;
                        r.hold(false);
                    })
                    .thenWaitUntil(() -> h.assertTrue(old[0].isRemoved(), "the row hasn't unloaded"))
                    .thenIdle(40) // two atmos ticks away
                    .thenExecute(() -> r.hold(true))
                    .thenWaitUntil(() -> h.assertTrue(r.loaded(), "the chunks haven't loaded"))
                    .thenWaitUntil(() -> h.assertTrue(
                            Atmospheres.zoneAt(a.level, a.abs(ROOM, Y, Z)).isPresent(), "the zone hasn't returned"))
                    .thenExecute(() -> {
                        r.vent = a.entity(EAST, Y, Z);
                        h.assertTrue(r.vent != old[0], "the vent is the one from before the unload");
                    })
                    .thenIdle(60);
        }
        sequence.thenExecute(() -> {
                    double left = r.vent.network().gas().totalMoles();
                    h.assertTrue(left > 1, "the transfer finished too soon: the row holds " + left);
                    r.vent.command(VentSettings.OFF);
                })
                .thenWaitUntil(() -> h.assertTrue(r.vent.readyToStep(), "gas is still in flight"))
                .thenIdle(40)
                .thenExecute(() -> {
                    a.close(r.stored() + ledger.net(), start[0], "the row, the room and the ledger");
                    ledger.assertExplained();
                    r.done();
                    ZasServerConfig.CONSERVATION_CHECKER.set(was);
                })
                .thenSucceed();
    }
}
