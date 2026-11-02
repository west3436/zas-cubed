package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNode;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pipe networks across a chunk border while chunks really leave and return (CONT-003, CONT-009,
 * DATA-002). Each test holds its two chunks with a ticket of its own, removes a ticket, and waits
 * for the game to act on it. A chunk next to a loaded one can't unload: ticket levels fall by one
 * per chunk, so it drops only out of full status, with its block entities still in memory. A real
 * unload takes both chunks.
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
            a.place(EAST, 1, 2, cap(Direction.WEST));
            for (int x = WEST; x <= EAST; x++) {
                (x < BORDER ? nodesA : nodesB).add(a.entity(x, 1, 2));
            }
            nodeA().network().gas().add(new GasParcel(Map.of(N2, MOLES), T));
            h.assertValueEqual(nodeA().network().members().size(), EAST - WEST + 1, "the row's nodes");
        }

        PipeNodeEntity nodeA() {
            return nodesA.get(2);
        }

        void hold(ChunkPos chunk, boolean on) {
            if (on) {
                a.level.getChunkSource().addRegionTicket(HOLD, chunk, 0, chunk);
            } else {
                a.level.getChunkSource().removeRegionTicket(HOLD, chunk, 0, chunk);
            }
        }

        /** Whether a chunk is loaded in full: asking loads nothing. */
        boolean full(ChunkPos chunk) {
            return a.level.getChunkSource().hasChunk(chunk.x, chunk.z);
        }

        /** Releases both chunks and clears the row, when the test ends. */
        void done() {
            hold(chunkA, true);
            hold(chunkB, true);
            a.clear(WEST - 1, 1, EAST + 1, 4);
            hold(chunkA, false);
            hold(chunkB, false);
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

        /** Every member of a node's network is loaded and not removed. */
        void noStale(PipeNodeEntity node, String when) {
            for (PipeNode m : node.network().members()) {
                PipeNodeEntity e = (PipeNodeEntity) m;
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
     * gas, and every member stays the level's own node.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 600)
    public static void cont_003_a_network_keeps_its_gas_while_its_far_chunk_leaves(GameTestHelper h) {
        Row r = new Row(h, 40);
        h.startSequence()
                .thenExecute(() -> r.hold(r.chunkB, false))
                .thenWaitUntil(() -> h.assertFalse(r.full(r.chunkB), "chunk B is still loaded"))
                .thenIdle(60) // three atmos ticks with chunk B away
                .thenExecute(() -> {
                    h.assertFalse(r.full(r.chunkB), "chunk B came back by itself");
                    r.close(r.total(r.all()), MOLES, "the row's gas while chunk B is away");
                    r.hold(r.chunkB, true);
                })
                .thenWaitUntil(() -> h.assertTrue(r.full(r.chunkB), "chunk B hasn't returned"))
                .thenIdle(60)
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
     * gas it had, once, in one network of the new nodes.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void cont_003_a_network_unloaded_and_reloaded_keeps_its_gas(GameTestHelper h) {
        Row r = new Row(h, 41);
        PipeNodeEntity oldA = r.nodeA();
        PipeNodeEntity oldB = r.nodesB.get(1);
        h.startSequence()
                .thenExecute(() -> {
                    r.hold(r.chunkA, false);
                    r.hold(r.chunkB, false);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(oldA.isRemoved(), "chunk A's nodes haven't unloaded");
                    h.assertTrue(oldB.isRemoved(), "chunk B's nodes haven't unloaded");
                })
                .thenIdle(40) // two atmos ticks with the row unloaded
                .thenExecute(() -> {
                    r.hold(r.chunkA, true);
                    r.hold(r.chunkB, true);
                })
                .thenWaitUntil(() -> h.assertTrue(r.full(r.chunkA) && r.full(r.chunkB), "the chunks haven't loaded"))
                .thenIdle(60)
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
     * holds what it had less what the branch released.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 600)
    public static void cont_003_a_network_rebuilt_while_its_far_chunk_is_away_keeps_its_gas(GameTestHelper h) {
        Row r = new Row(h, 42);
        double[] released = new double[1];
        h.startSequence()
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
                .thenIdle(40)
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
                    r.hold(r.chunkB, true);
                })
                .thenWaitUntil(() -> h.assertTrue(r.full(r.chunkB), "chunk B hasn't returned"))
                .thenIdle(60)
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
}
