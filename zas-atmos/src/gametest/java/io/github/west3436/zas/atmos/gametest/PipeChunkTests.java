package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNode;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pipe networks across a chunk border while chunks really leave and return (CONT-003, CONT-006,
 * CONT-009, DATA-002). Each test holds its two chunks with a ticket of its own, removes a ticket, and waits
 * for the game to act on it. A chunk next to a loaded one can't unload: ticket levels fall by one
 * per chunk, so it drops only out of full status, with its block entities still in memory. A real
 * unload takes both chunks.
 *
 * <p>Devices and nodes step only in chunks that tick block entities (architecture §11.2), and a
 * chunk next to a ticking one is always loaded in full, so in play a network whose far chunk is
 * away is stepped by a member further along, in a ticking chunk beyond the near one. Here the near
 * chunk's nodes stand for it: the tests step them directly while the far chunk is away. Where a
 * test needs the schedule to step the row, it holds both chunks at a ticking level, as a player
 * near them would.
 *
 * <p>While a chunk is away, the tests touch nothing in it: they keep references to its nodes and
 * ask the chunk source whether it is loaded, which loads nothing. They wait in real time, in a
 * batch of their own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeChunkTests {
    private static final String BATCH = "zas_atmos_pipe_chunks";
    private static final TicketType<ChunkPos> HOLD =
            TicketType.create("zas_atmos_gametest_hold", Comparator.comparingLong(ChunkPos::toLong));

    /** The row's ends: a cap at x = 11, pipes at 12 to 19, a cap at 20. Chunk A has x < 16. */
    private static final int WEST = 11;

    private static final int EAST = 20;
    private static final int BORDER = 16;
    private static final double MOLES = 100.0;

    private PipeChunkTests() {}

    /** A test's row of pipes across the border between its area's first two chunks. */
    private static final class Row {
        final GameTestHelper h;
        final Area a;
        final ChunkPos chunkA;
        final ChunkPos chunkB;
        final List<PipeNodeEntity> nodesA = new ArrayList<>();
        final List<PipeNodeEntity> nodesB = new ArrayList<>();

        Row(GameTestHelper h, int index) {
            this(h, index, cap(Direction.WEST));
        }

        /** A row whose east end, in chunk B, is a given node: a cap facing west, or a device. */
        Row(GameTestHelper h, int index, BlockState eastEnd) {
            this.h = h;
            this.a = new Area(h, index);
            this.chunkA = new ChunkPos(a.abs(0, 0, 0));
            this.chunkB = new ChunkPos(a.abs(BORDER, 0, 0));
            hold(chunkA, true);
            hold(chunkB, true);
            a.clear(WEST - 1, 1, EAST + 1, 4);
            a.place(WEST, 1, 2, cap(Direction.EAST));
            for (int x = WEST + 1; x < EAST; x++) {
                a.place(x, 1, 2, AtmosBlocks.PIPE.get().defaultBlockState());
            }
            a.place(EAST, 1, 2, eastEnd);
            for (int x = WEST; x <= EAST; x++) {
                (x < BORDER ? nodesA : nodesB).add(a.entity(x, 1, 2));
            }
            nodeA().network().gas().add(new GasParcel(Map.of(N2, MOLES), T));
            h.assertValueEqual(nodeA().network().members().size(), EAST - WEST + 1, "the row's nodes");
        }

        PipeNodeEntity nodeA() {
            return nodesA.get(2);
        }

        /** Holds a chunk loaded in full, without ticking, or lets it go. */
        void hold(ChunkPos chunk, boolean on) {
            hold(chunk, 0, on);
        }

        /**
         * Holds a chunk where its block entities tick, as a player near it would, or lets it go. Its
         * neighbours are then loaded in full.
         */
        void holdTicking(ChunkPos chunk, boolean on) {
            hold(chunk, 1, on);
        }

        private void hold(ChunkPos chunk, int distance, boolean on) {
            if (on) {
                a.level.getChunkSource().addRegionTicket(HOLD, chunk, distance, chunk);
            } else {
                a.level.getChunkSource().removeRegionTicket(HOLD, chunk, distance, chunk);
            }
        }

        /** Whether a chunk is loaded in full: asking loads nothing. */
        boolean full(ChunkPos chunk) {
            return a.level.getChunkSource().hasChunk(chunk.x, chunk.z);
        }

        /** Whether a chunk ticks its block entities, so that the schedule steps them. */
        boolean ticking(ChunkPos chunk) {
            return LoadedBlocks.ticking(a.level, chunk.getWorldPosition());
        }

        /** Steps chunk A's nodes once, as a member further along, in a ticking chunk, would. */
        void stepA() {
            nodesA.forEach(AtmosDevice::step);
        }

        /** Holds both chunks where they tick and waits until they do. */
        void bothTicking(GameTestSequence seq) {
            seq.thenExecute(() -> {
                        holdTicking(chunkA, true);
                        holdTicking(chunkB, true);
                    })
                    .thenWaitUntil(() -> h.assertTrue(
                            ticking(chunkA) && ticking(chunkB), "the chunks don't tick their block entities"));
        }

        /** Releases both chunks and clears the row, when the test ends. */
        void done() {
            hold(chunkA, true);
            hold(chunkB, true);
            a.clear(WEST - 1, 1, EAST + 1, 4);
            for (ChunkPos c : List.of(chunkA, chunkB)) {
                hold(c, false);
                holdTicking(c, false);
            }
        }

        /** The total moles over the distinct networks of some nodes, and the shares of those in none. */
        double total(List<? extends PipeNode> nodes) {
            double sum = 0;
            List<PipeNetwork> seen = new ArrayList<>();
            for (PipeNode n : nodes) {
                PipeNetwork net = n.currentNetwork();
                if (net != null && net.valid()) {
                    if (seen.stream().noneMatch(s -> s == net)) {
                        seen.add(net);
                        sum += net.gas().totalMoles();
                    }
                } else {
                    sum += n.stored().totalMoles();
                }
            }
            return sum;
        }

        List<PipeNodeEntity> all() {
            List<PipeNodeEntity> out = new ArrayList<>(nodesA);
            out.addAll(nodesB);
            return out;
        }

        void close(double actual, double expected, String what) {
            h.assertTrue(
                    Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)),
                    what + ": expected " + expected + ", got " + actual);
        }

        /** Every block entity among a node's network's members is loaded and not removed. */
        void noStale(PipeNodeEntity node, String when) {
            for (PipeNode m : node.network().members()) {
                if (!(m instanceof PipeNodeEntity e)) {
                    continue; // a device's port, which isn't a block entity
                }
                h.assertFalse(e.isRemoved(), when + ": a removed node at " + e.getBlockPos() + " is in the network");
                h.assertTrue(
                        e.getLevel().getBlockEntity(e.getBlockPos()) == e,
                        when + ": the node at " + e.getBlockPos() + " isn't the level's");
            }
        }
    }

    private static BlockState cap(Direction facing) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing);
    }

    /**
     * Chunk B leaves full status while chunk A stays loaded, then returns: the network keeps all its
     * gas, and every member stays the level's own node. It stands for a network whose far end goes
     * away while a member nearer a player, in a ticking chunk, keeps stepping it: chunk A's nodes are
     * stepped directly while chunk B is away. Both chunks tick once it returns, as when the player
     * comes back near it.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 600)
    public static void cont_003_a_network_keeps_its_gas_while_its_far_chunk_leaves(GameTestHelper h) {
        Row r = new Row(h, 40);
        GameTestSequence seq = h.startSequence()
                .thenExecute(() -> r.hold(r.chunkB, false))
                .thenWaitUntil(() -> h.assertFalse(r.full(r.chunkB), "chunk B is still loaded"))
                .thenExecute(r::stepA) // three atmos ticks with chunk B away
                .thenIdle(20)
                .thenExecute(r::stepA)
                .thenIdle(20)
                .thenExecute(r::stepA)
                .thenExecute(() -> {
                    h.assertFalse(r.full(r.chunkB), "chunk B came back by itself");
                    for (PipeNode m : r.nodeA().network().members()) {
                        h.assertFalse(r.nodesB.contains(m), "a node of chunk B, away, is in chunk A's network");
                    }
                    r.close(r.total(r.all()), MOLES, "the row's gas while chunk B is away");
                });
        r.bothTicking(seq);
        seq.thenIdle(60)
                .thenExecute(() -> {
                    r.noStale(r.nodeA(), "after chunk B returned");
                    h.assertValueEqual(r.nodeA().network().members().size(), EAST - WEST + 1, "members");
                    r.close(r.nodeA().network().gas().totalMoles(), MOLES, "the row's gas after chunk B returned");
                    r.done();
                })
                .thenSucceed();
    }

    /**
     * Both chunks really unload, so the row is saved, then load again: the reloaded row holds the
     * gas it had, once, in one network of the new nodes. It stands for a player leaving the row and
     * coming back to it: the chunks tick once they have loaded again, so the schedule steps the new
     * nodes.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void cont_003_a_network_unloaded_and_reloaded_keeps_its_gas(GameTestHelper h) {
        Row r = new Row(h, 41);
        PipeNodeEntity oldA = r.nodeA();
        PipeNodeEntity oldB = r.nodesB.get(1);
        GameTestSequence seq = h.startSequence()
                .thenExecute(() -> {
                    r.hold(r.chunkA, false);
                    r.hold(r.chunkB, false);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(oldA.isRemoved(), "chunk A's nodes haven't unloaded");
                    h.assertTrue(oldB.isRemoved(), "chunk B's nodes haven't unloaded");
                })
                .thenIdle(40); // two atmos ticks with the row unloaded
        r.bothTicking(seq);
        seq.thenIdle(60)
                .thenExecute(() -> {
                    PipeNodeEntity newA = r.a.entity(WEST + 2, 1, 2);
                    h.assertTrue(newA != oldA, "chunk A's node is the one from before the unload");
                    r.noStale(newA, "after the reload");
                    h.assertValueEqual(newA.network().members().size(), EAST - WEST + 1, "members after the reload");
                    r.close(newA.network().gas().totalMoles(), MOLES, "the row's gas after the reload");
                    r.done();
                })
                .thenSucceed();
    }

    /**
     * While chunk B is away, the part in chunk A changes and rebuilds: a branch is added, then taken
     * away again, which releases its share. When chunk B returns, the row is one network again, and
     * holds what it had less what the branch released. It stands for a player building on the near
     * part of a network whose far end is away, with the near part stepped as a member in a ticking
     * chunk would step it: chunk A's nodes are stepped directly while chunk B is away. Both chunks
     * tick once it returns.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 600)
    public static void cont_003_a_network_rebuilt_while_its_far_chunk_is_away_keeps_its_gas(GameTestHelper h) {
        Row r = new Row(h, 42);
        double[] released = new double[1];
        GameTestSequence seq = h.startSequence()
                .thenExecute(() -> r.hold(r.chunkB, false))
                .thenWaitUntil(() -> h.assertFalse(r.full(r.chunkB), "chunk B is still loaded"))
                .thenExecute(() -> {
                    // A branch on chunk A's side: a pipe north of x = 13 and a cap on it.
                    r.a.place(WEST + 2, 1, 3, AtmosBlocks.PIPE.get().defaultBlockState());
                    r.a.place(WEST + 2, 1, 4, cap(Direction.NORTH));
                    PipeNetwork side = r.nodeA().network();
                    for (PipeNode m : side.members()) {
                        h.assertFalse(r.nodesB.contains(m), "a node of chunk B, away, is in the rebuilt network");
                    }
                    h.assertValueEqual(side.members().size(), BORDER - WEST + 2, "chunk A's side with its branch");
                    r.close(r.total(r.all()), MOLES, "the row's gas after the branch was added");
                })
                .thenExecute(r::stepA)
                .thenIdle(20)
                .thenExecute(r::stepA)
                .thenExecute(() -> {
                    // Chunk B's nodes wait while it is away: nothing steps them, so nothing loads it to
                    // rebuild their network with chunk A's.
                    h.assertFalse(r.full(r.chunkB), "chunk B came back by itself");
                    for (PipeNode m : r.nodeA().network().members()) {
                        h.assertFalse(r.nodesB.contains(m), "a node of chunk B joined chunk A's network while away");
                    }
                    // Take the branch away again; each removed node releases its share.
                    double before = r.total(r.all());
                    r.a.set(WEST + 2, 1, 4, AIR);
                    r.a.set(WEST + 2, 1, 3, AIR);
                    released[0] = before - r.total(r.all());
                    h.assertTrue(released[0] > 0, "the branch released nothing");
                    r.close(r.total(r.all()), MOLES - released[0], "the row's gas after the branch went");
                });
        r.bothTicking(seq);
        seq.thenIdle(60)
                .thenExecute(() -> {
                    r.noStale(r.nodeA(), "after chunk B returned");
                    h.assertValueEqual(
                            r.nodeA().network().members().size(),
                            EAST - WEST + 1,
                            "one network after chunk B returned");
                    r.close(
                            r.nodeA().network().gas().totalMoles(),
                            MOLES - released[0],
                            "the row's gas after chunk B returned");
                    r.done();
                })
                .thenSucceed();
    }

    /**
     * A vent at the row's east end, in chunk B, blows the row's gas out into open air while both
     * chunks tick. When chunk B no longer ticks but is still loaded in full, at the edge of what is
     * loaded next to a ticking chunk A, the vent waits: the row keeps its gas and stays one network,
     * though chunk A's nodes step it. When chunk B is away the vent waits too, so the row keeps its
     * gas and chunk B stays away; chunk A's nodes are stepped directly then, as a member further
     * along in a ticking chunk would. When chunk B ticks again, the vent blows again. While chunk B
     * is away two things hold the vent: the schedule doesn't step it, and the air in front of it
     * doesn't read as zoned or the ambient, so its {@code CellWatch} holds it too.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 900)
    public static void cont_006_a_vent_waits_while_its_chunk_doesnt_tick(GameTestHelper h) {
        Row r = new Row(h, 43, GasDeviceTests.vent(Direction.EAST));
        VentBlockEntity vent = (VentBlockEntity) r.nodesB.get(r.nodesB.size() - 1);
        vent.power().setTestPowered(true);
        vent.command(VentSettings.blow(500));
        double[] held = new double[1];
        GameTestSequence seq = h.startSequence();
        r.bothTicking(seq);
        seq.thenWaitUntil(() -> h.assertTrue(r.total(r.all()) < MOLES * (1 - 1e-6), "the vent didn't blow"))
                .thenExecute(() -> {
                    r.hold(r.chunkB, false);
                    r.holdTicking(r.chunkB, false); // chunk A's ticket still holds chunk B in full
                })
                .thenWaitUntil(() -> h.assertFalse(r.ticking(r.chunkB), "chunk B still ticks"))
                .thenExecute(() -> held[0] = r.total(r.all()))
                .thenIdle(60) // three atmos ticks with chunk B at the edge
                .thenExecute(() -> {
                    h.assertTrue(r.full(r.chunkB), "chunk B left full status");
                    h.assertTrue(r.ticking(r.chunkA), "chunk A stopped ticking");
                    r.close(r.total(r.all()), held[0], "the row's gas while the vent's chunk doesn't tick");
                    h.assertValueEqual(
                            r.nodeA().network().members().size(),
                            EAST - WEST + 1,
                            "the row's network while the vent's chunk doesn't tick");
                    r.holdTicking(r.chunkA, false);
                })
                .thenWaitUntil(() -> h.assertFalse(r.full(r.chunkB), "chunk B is still loaded"))
                .thenExecute(() -> held[0] = r.total(r.all()))
                .thenExecute(r::stepA) // three atmos ticks with chunk B away
                .thenIdle(20)
                .thenExecute(r::stepA)
                .thenIdle(20)
                .thenExecute(r::stepA)
                .thenExecute(() -> {
                    h.assertFalse(r.full(r.chunkB), "chunk B came back by itself");
                    r.close(r.total(r.all()), held[0], "the row's gas while the vent's chunk is away");
                });
        r.bothTicking(seq);
        seq.thenWaitUntil(() -> h.assertTrue(
                        r.total(r.all()) < held[0] * (1 - 1e-6), "the vent didn't blow again once chunk B ticked"))
                .thenExecute(() -> {
                    r.noStale(r.nodeA(), "after chunk B returned");
                    vent.command(VentSettings.OFF);
                    r.done();
                })
                .thenSucceed();
    }

    /**
     * A chunk at the edge of what is loaded, in full only because its ticking neighbour is, holds a
     * vent at the end of a row from that neighbour, and two more rows: 35 nodes, enough for one to
     * fall due at every game tick. That is the shape that kept the S-10 bench's wing loaded after its
     * player had gone, as each step's reads ticketed the chunk again. While the chunk doesn't tick,
     * nothing in it steps, so the vent waits. When the neighbour is no longer held, as when the
     * player leaves, the chunk unloads with it, and nothing loads it again.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 900)
    public static void cont_006_a_chunk_at_the_edge_unloads_with_its_devices(GameTestHelper h) {
        Row r = new Row(h, 44, GasDeviceTests.vent(Direction.EAST));
        VentBlockEntity vent = (VentBlockEntity) r.nodesB.get(r.nodesB.size() - 1);
        int last = 2 * BORDER - 1;
        r.a.clear(BORDER, 4, last, 7);
        List<PipeNodeEntity> inB = new ArrayList<>(r.nodesB);
        for (int z : new int[] {4, 6}) {
            r.a.place(BORDER + 1, 1, z, cap(Direction.EAST));
            for (int x = BORDER + 2; x < last; x++) {
                r.a.place(x, 1, z, AtmosBlocks.PIPE.get().defaultBlockState());
            }
            r.a.place(last, 1, z, cap(Direction.WEST));
            for (int x = BORDER + 1; x <= last; x++) {
                inB.add(r.a.entity(x, 1, z));
            }
        }
        h.assertValueEqual(inB.size(), 35, "the nodes in chunk B");
        vent.power().setTestPowered(true);
        vent.command(VentSettings.blow(500));
        double[] held = new double[1];
        h.startSequence()
                .thenExecute(() -> {
                    r.holdTicking(r.chunkA, true);
                    r.hold(r.chunkA, false);
                    r.hold(r.chunkB, false); // chunk A's ticket holds chunk B in full
                })
                .thenWaitUntil(() -> h.assertTrue(
                        r.ticking(r.chunkA) && r.full(r.chunkB) && !r.ticking(r.chunkB),
                        "chunk B isn't at the edge of a ticking chunk A"))
                .thenExecute(() -> held[0] = r.total(r.all()))
                .thenIdle(60) // three atmos ticks at the edge
                .thenExecute(() -> {
                    h.assertTrue(r.full(r.chunkB) && !r.ticking(r.chunkB), "chunk B left the edge");
                    r.close(r.total(r.all()), held[0], "the row's gas while the vent's chunk is at the edge");
                    r.holdTicking(r.chunkA, false);
                })
                .thenWaitUntil(() -> {
                    for (PipeNodeEntity n : inB) {
                        h.assertTrue(n.isRemoved(), "chunk B hasn't unloaded: " + n.getBlockPos());
                    }
                    h.assertTrue(r.nodeA().isRemoved(), "chunk A hasn't unloaded");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertFalse(r.full(r.chunkA), "chunk A loaded again");
                    h.assertFalse(r.full(r.chunkB), "chunk B loaded again");
                    r.hold(r.chunkA, true);
                    r.hold(r.chunkB, true);
                })
                .thenWaitUntil(() -> h.assertTrue(r.full(r.chunkA) && r.full(r.chunkB), "the chunks haven't loaded"))
                .thenExecute(() -> {
                    r.a.<VentBlockEntity>entity(EAST, 1, 2).command(VentSettings.OFF);
                    r.a.clear(BORDER, 4, last, 7);
                    r.done();
                })
                .thenSucceed();
    }
}
