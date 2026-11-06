package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNode;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.pump.PumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Two sides of a pipe line, one each side of a chunk border, that come together or come back while
 * a chunk is away (CONT-003). {@link PipeChunkTests} covers a line that was whole and whose far side
 * simply returns; here each side may hold a network of its own that is still valid, which only a
 * rebuild can join to the other. As there, a chunk next to a loaded one drops only out of full
 * status, and the tests wait in real time, in a batch of their own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeGapChunkTests {
    private static final String BATCH = "zas_atmos_pipe_gap_chunks";
    private static final TicketType<ChunkPos> HOLD =
            TicketType.create("zas_atmos_gametest_gap_hold", Comparator.comparingLong(ChunkPos::toLong));

    /** The last block of chunk A. The block past it is the first of chunk B. */
    private static final int GAP = 15;

    private static final int WEST = GAP - 1;
    private static final int EAST = GAP + 1;
    private static final int Y = 1;
    private static final int Z = 2;

    /** The nodes of a {@link Border#line}: a pipe and two caps each side, and a pipe in the gap. */
    private static final int LINE_NODES = 2 * 3 + 1;

    private PipeGapChunkTests() {}

    /** A test's two chunks, A and B, held by tickets of its own, and a clear place across their border. */
    private static final class Border {
        final GameTestHelper h;
        final Area a;
        final ChunkPos chunkA;
        final ChunkPos chunkB;

        Border(GameTestHelper h, int index) {
            this.h = h;
            this.a = new Area(h, index);
            this.chunkA = new ChunkPos(a.abs(0, 0, 0));
            this.chunkB = new ChunkPos(a.abs(EAST, 0, 0));
            hold(chunkA, true);
            hold(chunkB, true);
            a.clear(WEST - 1, Z - 2, EAST + 1, Z + 2);
        }

        /**
         * Each side of the gap: a pipe with a cap on two faces, so that with the gap empty it has two
         * connections and no open end, and no gas leaves the line (CONT-004).
         *
         * @return the nodes
         */
        List<PipeNode> line() {
            List<PipeNode> nodes = new ArrayList<>();
            for (int x : new int[] {WEST, EAST}) {
                PipeTests.pipe(a, x, Y, Z);
                PipeTests.cap(a, x, Y, Z - 1, Direction.SOUTH);
                PipeTests.cap(a, x, Y, Z + 1, Direction.NORTH);
                for (int z = Z - 1; z <= Z + 1; z++) {
                    nodes.add(a.<PipeNodeEntity>entity(x, Y, z));
                }
            }
            return nodes;
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

        PipeNetwork net(int x) {
            return a.<PipeNodeEntity>entity(x, Y, Z).network();
        }

        /** The moles of a gas over the distinct networks of some nodes, and the shares of those in none. */
        double moles(ResourceLocation gas, List<PipeNode> nodes) {
            double sum = 0;
            List<PipeNetwork> seen = new ArrayList<>();
            for (PipeNode n : nodes) {
                PipeNetwork net = n.currentNetwork();
                if (net != null && net.valid()) {
                    if (seen.stream().noneMatch(s -> s == net)) {
                        seen.add(net);
                        sum += net.gas().moles(gas);
                    }
                } else {
                    sum += n.stored().moles(gas);
                }
            }
            return sum;
        }

        void assertJoined(PipeNode west, PipeNode east, int members, double volume, double n2, double o2, String when) {
            PipeNetwork one = west.currentNetwork();
            h.assertTrue(one != null && one == east.currentNetwork(), "the sides are apart " + when);
            h.assertValueEqual(one.members().size(), members, "the network's nodes " + when);
            a.close(one.volume(), volume, "the network's volume " + when);
            a.close(one.gas().moles(N2), n2, "the network's N2 " + when);
            a.close(one.gas().moles(O2), o2, "the network's O2 " + when);
        }

        void done() {
            a.clear(WEST - 1, Z - 2, EAST + 1, Z + 2);
            hold(chunkA, false);
            hold(chunkB, false);
        }
    }

    /**
     * A pipe placed in a gap at a chunk border, with the chunk past the gap out of full status, joins
     * that side once it returns, with no gas lost (#226).
     *
     * <p>Placing the pipe reads its neighbour's block, which brings that chunk back for two game ticks,
     * and the game loads the new pipe in the first of them, so it reaches the far side's old network
     * and ends it. A node that steps in those two ticks builds a network across the border, so the
     * outcome could turn on where they fall in the atmos period: the test places the pipe at each of
     * the period's game ticks in turn.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_003_a_pipe_placed_beside_a_chunk_away_joins_it_when_it_returns(GameTestHelper h) {
        Border b = new Border(h, 3000);
        b.a.set(GAP, Y - 1, Z, Area.STONE); // to click on when placing the pipe
        List<PipeNode> sides = b.line();
        var seq = h.startSequence().thenIdle(2); // the game's own onLoad calls dissolve the networks built above
        for (int i = 0; i < AtmosTicks.PERIOD; i++) {
            int phase = i;
            double[] n2 = new double[1];
            double[] o2 = new double[1];
            PipeNetwork[] east = new PipeNetwork[1];
            List<PipeNode> all = new ArrayList<>(sides);
            seq.thenExecute(() -> {
                        b.net(WEST).gas().add(new GasParcel(Map.of(N2, 20.0), T));
                        b.net(EAST).gas().add(new GasParcel(Map.of(O2, 5.0), T));
                        h.assertTrue(b.net(WEST) != b.net(EAST), "the sides are together with the gap empty");
                        n2[0] = b.moles(N2, sides);
                        o2[0] = b.moles(O2, sides);
                        east[0] = b.net(EAST);
                        b.hold(b.chunkB, false);
                    })
                    .thenWaitUntil(() -> h.assertFalse(b.full(b.chunkB), "chunk B is still loaded"))
                    .thenWaitUntil(() -> h.assertValueEqual(
                            Math.floorMod(h.getLevel().getServer().getTickCount(), AtmosTicks.PERIOD),
                            phase,
                            "the game tick in the period"))
                    .thenExecute(() -> {
                        h.assertFalse(b.full(b.chunkB), "chunk B returned before the pipe was placed");
                        h.assertTrue(east[0].valid(), "chunk B's network ended while it was away");
                        all.add(placePipe(b));
                    })
                    .thenWaitUntil(() -> h.assertFalse(b.full(b.chunkB), "chunk B hasn't left again"))
                    .thenIdle(2 * AtmosTicks.PERIOD)
                    .thenExecute(() -> {
                        h.assertFalse(b.full(b.chunkB), "chunk B came back by itself");
                        b.a.close(b.moles(N2, all), n2[0], "the line's N2 with chunk B away, at " + phase);
                        b.a.close(b.moles(O2, all), o2[0], "the line's O2 with chunk B away, at " + phase);
                        b.hold(b.chunkB, true);
                    })
                    .thenWaitUntil(() -> h.assertTrue(b.full(b.chunkB), "chunk B hasn't returned"))
                    .thenIdle(2 * AtmosTicks.PERIOD)
                    .thenExecute(() -> {
                        b.assertJoined(
                                b.a.<PipeNodeEntity>entity(WEST, Y, Z),
                                b.a.<PipeNodeEntity>entity(EAST, Y, Z),
                                LINE_NODES,
                                LINE_NODES * PipeVolumes.PIPE,
                                n2[0],
                                o2[0],
                                "placing at " + phase);
                        b.a.set(GAP, Y, Z, AIR); // its share goes to the air, and the sides part
                    });
        }
        seq.thenExecute(b::done).thenSucceed();
    }

    /** Places the pipe in the gap as a player's click does; the game then loads it itself. */
    private static PipeNodeEntity placePipe(Border b) {
        BlockPos floor = b.a.abs(GAP, Y - 1, Z);
        Player player = b.h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(AtmosItems.PIPE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        b.h.assertValueEqual(stack.getCount(), 0, "pipes left after placing");
        return b.a.entity(GAP, Y, Z);
    }

    /** A whole pipe line's sides, away in turn: see {@link #awayInTurn}. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_003_sides_away_in_turn_join_when_both_return(GameTestHelper h) {
        Border b = new Border(h, 3001);
        List<PipeNode> all = b.line();
        PipeTests.pipe(b.a, GAP, Y, Z);
        all.add(b.a.<PipeNodeEntity>entity(GAP, Y, Z));
        awayInTurn(
                b,
                b.a.<PipeNodeEntity>entity(WEST, Y, Z),
                b.a.<PipeNodeEntity>entity(EAST, Y, Z),
                all,
                LINE_NODES * PipeVolumes.PIPE);
    }

    /** Two pumps' ports that meet across the border, with no pipe to look for them: see {@link #awayInTurn}. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_003_ports_away_in_turn_join_when_both_return(GameTestHelper h) {
        Border b = new Border(h, 3002);
        BlockState pump =
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST);
        b.a.place(GAP, Y, Z, pump);
        b.a.place(EAST, Y, Z, pump);
        PipeNode out = b.a.<PumpBlockEntity>entity(GAP, Y, Z).output();
        PipeNode in = b.a.<PumpBlockEntity>entity(EAST, Y, Z).input();
        awayInTurn(b, out, in, List.of(out, in), 2 * PipeVolumes.PORT);
    }

    /**
     * Chunk B leaves, and chunk A's side builds a network of its own. Chunk A leaves too, and chunk B
     * returns and does the same. When chunk A returns, each side holds a network that is still valid,
     * and each has the other connected beside it. They join, with no gas lost. A held chunk beyond
     * each end keeps its neighbour in memory, as a player nearby does a chunk just out of full status.
     *
     * @param b the border
     * @param west a node in chunk A, on the border
     * @param east the node it connects to in chunk B
     * @param all every node that can hold the gas, joined in one network while both chunks are loaded
     * @param volume the volume of that network
     */
    private static void awayInTurn(Border b, PipeNode west, PipeNode east, List<PipeNode> all, double volume) {
        GameTestHelper h = b.h;
        ChunkPos beyondA = new ChunkPos(b.chunkA.x - 1, b.chunkA.z);
        ChunkPos beyondB = new ChunkPos(b.chunkB.x + 1, b.chunkB.z);
        b.hold(beyondA, true);
        b.hold(beyondB, true);
        h.startSequence()
                .thenIdle(2) // the game's own onLoad calls dissolve the networks built by the test
                .thenWaitUntil(() -> h.assertTrue(
                        west.currentNetwork() != null && west.currentNetwork() == east.currentNetwork(),
                        "the sides haven't joined"))
                .thenExecute(() -> {
                    west.currentNetwork().gas().add(new GasParcel(Map.of(N2, 70.0, O2, 35.0), T));
                    b.hold(b.chunkB, false);
                })
                .thenWaitUntil(() -> h.assertFalse(b.full(b.chunkB), "chunk B is still loaded"))
                .thenWaitUntil(() -> {
                    PipeNetwork net = west.currentNetwork();
                    h.assertTrue(net != null && !net.members().contains(east), "chunk A's side hasn't parted");
                })
                .thenExecute(() -> b.hold(b.chunkA, false))
                .thenWaitUntil(() -> h.assertFalse(b.full(b.chunkA), "chunk A is still loaded"))
                .thenExecute(() -> b.hold(b.chunkB, true))
                .thenWaitUntil(() -> h.assertTrue(b.full(b.chunkB), "chunk B hasn't returned"))
                .thenWaitUntil(() -> {
                    PipeNetwork net = east.currentNetwork();
                    h.assertTrue(net != null && !net.members().contains(west), "chunk B's side hasn't built its own");
                })
                .thenExecute(() -> {
                    h.assertFalse(b.full(b.chunkA), "chunk A came back by itself");
                    h.assertTrue(west.currentNetwork() != null, "chunk A's network ended while it was away");
                    b.a.close(b.moles(N2, all), 70.0, "the N2 with chunk A away");
                    b.a.close(b.moles(O2, all), 35.0, "the O2 with chunk A away");
                    b.hold(b.chunkA, true);
                })
                .thenWaitUntil(() -> h.assertTrue(b.full(b.chunkA), "chunk A hasn't returned"))
                .thenIdle(2 * AtmosTicks.PERIOD)
                .thenExecute(() -> {
                    b.assertJoined(west, east, all.size(), volume, 70.0, 35.0, "after both returned");
                    b.done();
                    b.hold(beyondA, false);
                    b.hold(beyondB, false);
                })
                .thenSucceed();
    }
}
