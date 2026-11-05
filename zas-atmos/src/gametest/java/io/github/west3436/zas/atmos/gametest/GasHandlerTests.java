package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.pipe.GasHandlers;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipePort;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.valve.ValveBlock;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The gas-handler capability (API-021), pipes and devices connecting to it (API-025), and the
 * transfers through it (API-026), with {@link TestGasHandler} standing in for another mod's block.
 * Most tests drive device steps themselves within one game tick, in a batch of their own. Those that
 * need the game's own ticks, the checker or a chunk really leaving have batches of their own too.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasHandlerTests {
    private static final String BATCH = "zas_atmos_gas_handler";
    private static final String CHECKER_BATCH = "zas_atmos_gas_handler_checker";
    private static final String REAL_TIME_BATCH = "zas_atmos_gas_handler_real_time";
    private static final String CHUNK_BATCH = "zas_atmos_gas_handler_chunks";
    private static final TicketType<ChunkPos> HOLD =
            TicketType.create("zas_atmos_gas_handler_hold", Comparator.comparingLong(ChunkPos::toLong));

    private GasHandlerTests() {}

    private static TestGasHandler.Entity handler(Area a, int x, int y, int z) {
        a.place(x, y, z, TestGasHandler.block().defaultBlockState());
        return a.entity(x, y, z);
    }

    private static GasParcel gas(double o2, double n2) {
        return new GasParcel(Map.of(O2, o2, N2, n2), T);
    }

    private static boolean arm(Area a, int x, int y, int z, Direction face) {
        return a.get(x, y, z).getValue(PipeBlock.CONNECTIONS.get(face));
    }

    /** A cap at x - 1 and a pipe at x, so the pipe's arm to whatever is at x + 1 is its second. */
    private static PipeBlockEntity capped(Area a, int x, int y, int z) {
        PipeTests.cap(a, x - 1, y, z, Direction.EAST);
        PipeTests.pipe(a, x, y, z);
        return a.entity(x, y, z);
    }

    // ---- API-021: the capability ---------------------------------------------------------------

    /** A test block fills and drains, and simulating says what executing does and changes nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_021_a_block_fills_and_drains_in_both_modes(GameTestHelper h) {
        Area a = new Area(h, 1100);
        a.clear(0, 0, 3, 3);
        TestGasHandler.Entity tank = handler(a, 1, 1, 1);
        BlockPos pos = a.abs(1, 1, 1);
        IGasHandler handler = a.level.getCapability(IGasHandler.BLOCK, pos, Direction.UP);
        h.assertTrue(handler != null, "the block gives no handler on a face");
        h.assertTrue(a.level.getCapability(IGasHandler.BLOCK, pos, null) != null, "the block gives none on no face");
        h.assertTrue(a.level.getCapability(IGasHandler.BLOCK, a.abs(2, 1, 1), Direction.UP) == null, "air gives one");

        GasParcel offer = new GasParcel(Map.of(O2, 4.0, N2, 6.0), 300);
        GasParcel would = handler.fill(offer, IGasHandler.Action.SIMULATE);
        a.close(would.totalMoles(), 10, "a simulated fill's answer");
        h.assertTrue(handler.contents().isEmpty(), "a simulated fill changed the block");
        GasParcel took = handler.fill(offer, IGasHandler.Action.EXECUTE);
        h.assertValueEqual(took.moles(), would.moles(), "what the fill took");
        GasMixture held = handler.contents();
        a.close(held.moles(O2), 4, "O2 held after the fill");
        a.close(held.moles(N2), 6, "N2 held after the fill");
        a.close(held.temperature(), 300, "the temperature after the fill");
        a.close(held.volume(), TestGasHandler.VOLUME, "its volume");

        Map<io.github.west3436.zas.api.gas.GasParcel, Double> none = Map.of();
        h.assertTrue(none.isEmpty(), "");
        Map<net.minecraft.resources.ResourceLocation, Double> want = Map.of(O2, 1.0, N2, 100.0);
        GasParcel wouldGive = handler.drain(want, IGasHandler.Action.SIMULATE);
        a.close(wouldGive.moles(O2), 1, "a simulated drain's O2");
        a.close(wouldGive.moles(N2), 6, "a simulated drain's N2: all there is");
        a.close(handler.contents().totalMoles(), 10, "what a simulated drain left");
        GasParcel gave = handler.drain(want, IGasHandler.Action.EXECUTE);
        h.assertValueEqual(gave.moles(), wouldGive.moles(), "what the drain gave");
        a.close(gave.temperature(), 300, "what the drain gave: its temperature");
        a.close(handler.contents().moles(O2), 3, "O2 after the drain");
        a.close(handler.contents().moles(N2), 0, "N2 after the drain");

        tank.capacity = 5;
        GasParcel part = handler.fill(offer, IGasHandler.Action.SIMULATE);
        a.close(part.totalMoles(), 2, "a simulated part of a fill");
        a.close(handler.contents().totalMoles(), 3, "what it held after simulating part of a fill");
        h.assertValueEqual(
                handler.fill(offer, IGasHandler.Action.EXECUTE).moles(), part.moles(), "the part of the fill it took");
        a.close(handler.contents().totalMoles(), 5, "what it held after part of a fill");
        h.succeed();
    }

    // ---- API-025: connecting -------------------------------------------------------------------

    /**
     * A pipe grows an arm to a handler placed beside it, and that arm counts: with one to a cap as
     * well, the pipe has no open end. The handler isn't a member of the network. Gas moves out to it
     * and back in from it, to an equal concentration in one atmos tick.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_025_a_pipe_connects_to_a_handler_and_gas_moves(GameTestHelper h) {
        Area a = new Area(h, 1101);
        a.clear(0, 0, 5, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        h.assertFalse(arm(a, 2, 1, 1, Direction.EAST), "an arm to air");
        h.assertTrue(pipe.isOpen(), "the pipe with one arm has no open end");

        TestGasHandler.Entity tank = handler(a, 3, 1, 1);
        h.assertTrue(arm(a, 2, 1, 1, Direction.EAST), "no arm to the handler");
        h.assertFalse(arm(a, 2, 1, 1, Direction.UP), "an arm to nothing");
        h.assertFalse(pipe.isOpen(), "the handler's arm doesn't count against an open end");
        double volume = PipeVolumes.PIPE * 2;
        a.close(pipe.network().volume(), volume, "the network's volume: no handler in it");
        double share = TestGasHandler.VOLUME / (volume + TestGasHandler.VOLUME);

        // Out: it goes to an equal concentration, then stops.
        pipe.network().gas().add(new GasParcel(Map.of(N2, 12.0), 400));
        a.step();
        a.close(tank.tank.moles(N2), 12 * share, "N2 in the handler");
        a.close(pipe.network().gas().moles(N2), 12 * (1 - share), "N2 in the pipes");
        a.close(tank.tank.temperature(), 400, "the gas's temperature in the handler");
        h.assertValueEqual(tank.fills, 1, "fills");
        a.step();
        h.assertValueEqual(tank.fills, 1, "fills once settled");

        // In: a gas the pipes don't have comes in the same way, and the N2 stays settled.
        tank.tank.add(new GasParcel(Map.of(O2, 6.0), 400));
        a.step();
        a.close(pipe.network().gas().moles(O2), 6 * (1 - share), "O2 in the pipes");
        a.close(tank.tank.moles(O2), 6 * share, "O2 left in the handler");
        a.close(pipe.network().gas().moles(N2) + tank.tank.moles(N2), 12, "N2 in all");
        h.assertValueEqual(tank.drains, 1, "drains");

        // A pipe placed beside a handler connects as well.
        PipeTests.pipe(a, 3, 2, 1);
        h.assertTrue(arm(a, 3, 2, 1, Direction.DOWN), "no arm to the handler from a pipe placed beside it");
        h.succeed();
    }

    /** A pipe placed by a player gets its arm, and a device lines up with a handler as with a pipe. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_025_a_placed_device_lines_up_with_a_handler(GameTestHelper h) {
        Area a = new Area(h, 1102);
        a.clear(0, 0, 8, 4);
        handler(a, 3, 2, 2);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(-90); // east
        player.setYHeadRot(-90);
        player.setXRot(60); // and steeply down, so with nothing around a pump would point down

        BlockPos beside = a.abs(4, 2, 2);
        ItemStack pipes = new ItemStack(AtmosItems.PIPE.get());
        BlockState pipe = AtmosBlocks.PIPE.get().getStateForPlacement(context(player, pipes, beside));
        h.assertTrue(pipe.getValue(PipeBlock.CONNECTIONS.get(Direction.WEST)), "a placed pipe has no arm to the handler");
        h.assertFalse(pipe.getValue(PipeBlock.CONNECTIONS.get(Direction.EAST)), "a placed pipe has an arm to air");

        ItemStack pumps = new ItemStack(AtmosItems.PRESSURE_PUMP.get());
        BlockState pump = AtmosBlocks.PRESSURE_PUMP.get().getStateForPlacement(context(player, pumps, beside));
        h.assertValueEqual(pump.getValue(InlineDeviceBlock.FACING), Direction.EAST, "a placed pump's way");

        ItemStack valves = new ItemStack(AtmosItems.MANUAL_VALVE.get());
        BlockState valve = AtmosBlocks.MANUAL_VALVE.get().getStateForPlacement(context(player, valves, beside));
        h.assertValueEqual(valve.getValue(ValveBlock.AXIS), Direction.Axis.X, "a placed valve's axis");
        h.succeed();
    }

    private static BlockPlaceContext context(Player player, ItemStack stack, BlockPos at) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
    }

    /**
     * Every kind of device with a pipe connection exchanges gas with a handler on it, and only there:
     * a cap on the face it points at, a connector port on its bottom, a vent, scrubber or passive vent
     * on its back, an open valve on a flange, and a pump, filter or mixer on each port, off or not.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_025_every_pipe_device_connects_to_a_handler(GameTestHelper h) {
        Area a = new Area(h, 1103);
        a.clear(-1, -1, 63, 7);
        List<Runnable> checks = new ArrayList<>();
        BlockState cap = AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.UP);
        BlockState openValve = AtmosBlocks.MANUAL_VALVE
                .get()
                .defaultBlockState()
                .setValue(ValveBlock.AXIS, Direction.Axis.Y)
                .setValue(ValveBlock.OPEN, true);
        Map<String, BlockState> onTop = new java.util.LinkedHashMap<>();
        onTop.put("a cap", cap);
        onTop.put("a vent", GasDeviceTests.vent(Direction.DOWN));
        onTop.put("a scrubber", ScrubberTests.scrubber(Direction.DOWN));
        onTop.put("an open valve", openValve);
        int x = 0;
        for (Map.Entry<String, BlockState> device : onTop.entrySet()) {
            int at = x;
            TestGasHandler.Entity tank = handler(a, at, 3, 2);
            a.place(at, 2, 2, device.getValue());
            PipeNodeEntity node = a.entity(at, 2, 2);
            tank.tank.add(gas(4, 0));
            checks.add(() -> {
                h.assertTrue(tank.drains > 0, device.getKey() + " didn't drain the handler above");
                a.close(
                        tank.tank.moles(O2) + node.network().gas().moles(O2),
                        4,
                        "O2 between " + device.getKey() + " and the handler");
                h.assertTrue(node.network().gas().moles(O2) > 0, device.getKey() + " got no gas");
            });
            x += 2;
        }

        // A connector port's way is down, not up.
        TestGasHandler.Entity below = handler(a, x, 1, 2);
        a.place(x, 2, 2, GasDeviceTests.port());
        TestGasHandler.Entity above = handler(a, x, 3, 2);
        below.tank.add(gas(4, 0));
        above.tank.add(gas(4, 0));
        checks.add(() -> {
            h.assertTrue(below.drains > 0, "a connector port didn't drain the handler below");
            h.assertValueEqual(above.drains, 0, "a connector port's top drains");
        });
        x += 2;

        // A passive vent exchanges with the room in its own step too, so only the handler's side is exact.
        TestGasHandler.Entity passive = handler(a, x, 3, 2);
        a.place(
                x,
                2,
                2,
                AtmosBlocks.PASSIVE_VENT
                        .get()
                        .defaultBlockState()
                        .setValue(io.github.west3436.zas.atmos.vent.PassiveVentBlock.FACING, Direction.DOWN));
        passive.tank.add(gas(4, 0));
        checks.add(() -> h.assertTrue(passive.drains > 0, "a passive vent didn't drain the handler above"));
        x += 2;

        // A pump, filter and mixer are off, and each port still has its own exchange.
        for (BlockState inline : List.of(
                AtmosBlocks.PRESSURE_PUMP
                        .get()
                        .defaultBlockState()
                        .setValue(InlineDeviceBlock.FACING, Direction.EAST),
                AtmosBlocks.FILTER.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST),
                AtmosBlocks.MIXER.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST))) {
            int at = x + 1;
            a.place(at, 2, 2, inline);
            InlineDeviceEntity device = a.entity(at, 2, 2);
            InlineDeviceBlock block = (InlineDeviceBlock) inline.getBlock();
            String name = inline.getBlock().getName().getString();
            List<Direction> faces = block.portFaces(inline);
            for (int i = 0; i < faces.size(); i++) {
                Direction face = faces.get(i);
                BlockPos beside = a.abs(at, 2, 2).relative(face);
                TestGasHandler.Entity tank = handler(
                        a, beside.getX() - a.origin.getX(), beside.getY() - a.origin.getY(), beside.getZ() - a.origin.getZ());
                PipePort port = device.port(i);
                double moles = i + 1.0; // each its own, so a mix-up between ports shows
                tank.tank.add(gas(moles, 0));
                checks.add(() -> {
                    h.assertFalse(device.running(), name + " is running");
                    h.assertTrue(tank.drains > 0, name + " didn't drain the handler on its " + face + " port");
                    a.close(
                            tank.tank.moles(O2) + port.network().gas().moles(O2),
                            moles,
                            "O2 between " + name + "'s " + face + " port and its handler");
                });
            }
            x += 4;
        }

        a.step();
        checks.forEach(Runnable::run);
        h.succeed();
    }

    /** A canister connects only to a connector port, and to no handler (CONT-083). A shut valve keeps its gas. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_025_a_canister_and_a_shut_valve_move_no_gas(GameTestHelper h) {
        Area a = new Area(h, 1104);
        a.clear(0, 0, 6, 4);

        TestGasHandler.Entity underCanister = handler(a, 1, 1, 1);
        a.place(1, 2, 1, GasDeviceTests.canister());
        PipeNodeEntity canister = a.entity(1, 2, 1);
        underCanister.tank.add(gas(4, 0));
        h.assertFalse(
                GasHandlers.connects(a.level, a.abs(1, 2, 1), a.get(1, 2, 1), Direction.DOWN),
                "a canister connects to a handler");

        BlockState shut = AtmosBlocks.MANUAL_VALVE
                .get()
                .defaultBlockState()
                .setValue(ValveBlock.AXIS, Direction.Axis.X)
                .setValue(ValveBlock.OPEN, false);
        a.place(3, 1, 1, shut);
        PipeNodeEntity valve = a.entity(3, 1, 1);
        TestGasHandler.Entity beyond = handler(a, 4, 1, 1);
        beyond.tank.add(gas(4, 0));
        valve.network().gas().add(new GasParcel(Map.of(N2, 4.0), T));
        h.assertTrue(
                GasHandlers.connects(a.level, a.abs(3, 1, 1), a.get(3, 1, 1), Direction.EAST),
                "a shut valve's flange doesn't connect to a handler");

        a.step();
        h.assertValueEqual(underCanister.drains, 0, "a canister's drains");
        h.assertTrue(canister.network().gas().isEmpty(), "a canister took gas");
        h.assertValueEqual(beyond.drains + beyond.fills, 0, "calls through a shut valve");
        a.close(beyond.tank.moles(O2), 4, "the handler's O2 beyond a shut valve");
        a.close(valve.network().gas().moles(N2), 4, "the valve's N2");
        h.succeed();
    }

    /** A handler that offers the capability on one side connects there and nowhere else. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_025_a_sided_handler_connects_on_its_side(GameTestHelper h) {
        Area a = new Area(h, 1105);
        a.clear(0, 0, 8, 3);
        a.place(3, 1, 1, TestGasHandler.block().defaultBlockState());
        TestGasHandler.Entity tank = a.entity(3, 1, 1);
        tank.onlySide = Direction.WEST;
        PipeTests.pipe(a, 2, 1, 1);
        PipeTests.pipe(a, 4, 1, 1);
        h.assertTrue(arm(a, 2, 1, 1, Direction.EAST), "no arm to the handler's own side");
        h.assertFalse(arm(a, 4, 1, 1, Direction.WEST), "an arm to a side it doesn't offer");
        h.succeed();
    }

    /**
     * An arm follows the capability, not just the block: taken away with the block still there, the
     * arm goes at the next step and the pipe has an open end again, and the arm comes back with it.
     * The block removed, the arm goes at once, and nothing more moves.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_025_an_arm_follows_the_capability(GameTestHelper h) {
        Area a = new Area(h, 1106);
        a.clear(0, 0, 5, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        TestGasHandler.Entity tank = handler(a, 3, 1, 1);
        h.assertTrue(arm(a, 2, 1, 1, Direction.EAST), "no arm to start with");

        tank.exposed = false;
        a.level.invalidateCapabilities(a.abs(3, 1, 1));
        a.step();
        h.assertFalse(arm(a, 2, 1, 1, Direction.EAST), "the arm outlived the capability");
        h.assertTrue(pipe.isOpen(), "no open end without the capability");
        h.assertTrue(arm(a, 2, 1, 1, Direction.WEST), "the arm to the cap went too");

        tank.exposed = true;
        a.level.invalidateCapabilities(a.abs(3, 1, 1));
        a.step();
        h.assertTrue(arm(a, 2, 1, 1, Direction.EAST), "the arm didn't return with the capability");
        h.assertFalse(pipe.isOpen(), "an open end with the capability back");

        pipe.network().gas().add(new GasParcel(Map.of(N2, 12.0), T));
        a.step();
        int fills = tank.fills;
        h.assertTrue(fills > 0, "no transfer with the arm back");
        a.close(pipe.network().gas().moles(N2) + tank.tank.moles(N2), 12, "N2 before the handler goes");
        double left = pipe.network().gas().moles(N2);

        a.set(3, 1, 1, AIR);
        h.assertFalse(arm(a, 2, 1, 1, Direction.EAST), "the arm outlived the block");
        h.assertTrue(pipe.isOpen(), "no open end without the block");
        a.close(pipe.network().gas().moles(N2), left, "N2 in the pipes as the handler goes");
        h.assertValueEqual(tank.fills, fills, "fills once it has gone");
        h.succeed();
    }

    // ---- API-026: transfers --------------------------------------------------------------------

    /**
     * Left to the game's own ticks, every call a pipe's step makes into a handler, in both directions,
     * is on the server thread. Asked to run a transfer on another thread, ZAS³ refuses before it
     * touches the handler.
     */
    @GameTest(template = "empty", batch = REAL_TIME_BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void api_026_transfers_run_on_the_server_thread(GameTestHelper h) {
        Area a = new Area(h, 1107);
        a.forceLoad(true);
        a.clear(0, 0, 5, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        TestGasHandler.Entity tank = handler(a, 3, 1, 1);
        pipe.network().gas().add(new GasParcel(Map.of(N2, 12.0), T));
        tank.tank.add(new GasParcel(Map.of(O2, 12.0), T));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(tank.fills > 0 && tank.drains > 0, "no transfer each way yet"))
                .thenExecute(() -> {
                    Thread server = a.level.getServer().getRunningThread();
                    h.assertTrue(Thread.currentThread() == server, "the test isn't on the server thread");
                    h.assertFalse(tank.threads.isEmpty(), "no calls recorded");
                    for (Thread t : tank.threads) {
                        h.assertTrue(t == server, "a call on " + t.getName());
                    }

                    int calls = tank.threads.size();
                    AtomicReference<Throwable> thrown = new AtomicReference<>();
                    Thread other = new Thread(() -> {
                        try {
                            GasHandlers.exchange(
                                    a.level,
                                    pipe.getBlockPos(),
                                    pipe.getBlockState(),
                                    Direction.EAST,
                                    new GasHandlers.Buffer(() -> new GasStore(1), gas -> {}, () -> false));
                        } catch (Throwable t) {
                            thrown.set(t);
                        }
                    });
                    other.start();
                    try {
                        other.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    h.assertTrue(thrown.get() instanceof IllegalStateException, "off the thread: " + thrown.get());
                    h.assertValueEqual(tank.threads.size(), calls, "calls made off the thread");
                    a.clear(0, 0, 5, 3);
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    /**
     * With the invariant checker on, gas goes from a sealed room through a pipe's open end into a
     * handler, and the handler's goes the other way. The total of each gas over all three holds at
     * every step, and the checker finds nothing unexplained (CORE-073).
     */
    @GameTest(template = "empty", batch = CHECKER_BATCH)
    public static void api_026_gas_is_conserved_both_ways_with_the_checker_on(GameTestHelper h) {
        boolean was = ZasServerConfig.CONSERVATION_CHECKER.get();
        ZasServerConfig.CONSERVATION_CHECKER.set(true);
        try {
            Area a = new Area(h, 1108);
            a.box(0, 0, 4, 4);
            a.fill(2, 2, 2, Map.of(O2, 30.0));
            TestGasHandler.Entity tank = handler(a, 2, 1, 2);
            tank.tank.add(new GasParcel(Map.of(N2, 20.0), T));
            a.tick();
            PipeTests.pipe(a, 2, 2, 2);
            PipeBlockEntity pipe = a.entity(2, 2, 2);
            h.assertTrue(arm(a, 2, 2, 2, Direction.DOWN), "no arm to the handler");
            h.assertTrue(pipe.isOpen(), "a pipe with only a handler's arm has no open end");
            a.tick();
            double o2 = a.zone(2, 3, 2).mixture().moles(O2);
            double n2 = 20;
            for (int i = 0; i < 12; i++) {
                a.step();
                ConservationReport report = a.service().dimensionFor(a.level).lastConservation();
                h.assertTrue(report != null, "the checker didn't run");
                h.assertTrue(report.conserved(), "unexplained: " + report.violations());
                GasMixture zone = a.zone(2, 3, 2).mixture();
                a.close(
                        zone.moles(O2) + pipe.network().gas().moles(O2) + tank.tank.moles(O2),
                        o2,
                        "O2 in all at step " + i);
                a.close(
                        zone.moles(N2) + pipe.network().gas().moles(N2) + tank.tank.moles(N2),
                        n2,
                        "N2 in all at step " + i);
            }
            h.assertTrue(tank.tank.moles(O2) > 0, "no room O2 reached the handler");
            h.assertTrue(a.zone(2, 3, 2).mixture().moles(N2) > 0, "no handler N2 reached the room");
        } finally {
            ZasServerConfig.CONSERVATION_CHECKER.set(was);
        }
        h.succeed();
    }

    /** A handler that refuses leaves its gas and the network's as they were, though it's asked each time. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_026_a_handler_that_refuses_changes_nothing(GameTestHelper h) {
        Area a = new Area(h, 1109);
        a.clear(0, 0, 5, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        TestGasHandler.Entity tank = handler(a, 3, 1, 1);
        tank.refusesFill = true;
        tank.refusesDrain = true;
        pipe.network().gas().add(new GasParcel(Map.of(N2, 12.0), 400));
        tank.tank.add(new GasParcel(Map.of(O2, 5.0), 300));
        for (int i = 1; i <= 3; i++) {
            a.step();
            h.assertValueEqual(tank.fills, i, "fills asked for");
            h.assertValueEqual(tank.drains, i, "drains asked for");
            a.close(pipe.network().gas().moles(N2), 12, "the network's N2");
            a.close(pipe.network().gas().temperature(), 400, "the network's temperature");
            a.close(tank.tank.moles(O2), 5, "the handler's O2");
            a.close(tank.tank.totalMoles(), 5, "the handler's gas");
        }
        h.succeed();
    }

    /**
     * A handler that takes part of a transfer leaves the rest in the network, and one that says it took
     * more than it did can't take more than it was offered.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_026_a_handler_that_takes_part_leaves_the_rest(GameTestHelper h) {
        Area a = new Area(h, 1110);
        a.clear(0, 0, 9, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        TestGasHandler.Entity tank = handler(a, 3, 1, 1);
        tank.capacity = 1;
        pipe.network().gas().add(new GasParcel(Map.of(N2, 12.0), 400));
        a.step();
        a.close(tank.tank.moles(N2), 1, "what it took");
        a.close(pipe.network().gas().moles(N2), 11, "the rest");
        a.close(pipe.network().gas().temperature(), 400, "the network's temperature");
        a.step();
        a.close(tank.tank.moles(N2), 1, "what it took when full");
        a.close(pipe.network().gas().moles(N2), 11, "the rest when it's full");

        PipeBlockEntity other = capped(a, 6, 1, 1);
        TestGasHandler.Entity liar = handler(a, 7, 1, 1);
        liar.overstates = 100;
        other.network().gas().add(new GasParcel(Map.of(N2, 12.0), T));
        double out = 12 * TestGasHandler.VOLUME / (PipeVolumes.PIPE * 2 + TestGasHandler.VOLUME);
        a.step();
        a.close(liar.tank.moles(N2), out, "what it really took");
        a.close(other.network().gas().moles(N2), 12 - out, "what's left: none below what it was offered");
        h.succeed();
    }

    /** A handler that throws loses no gas: all it was offered is back in the network. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_026_a_handler_that_throws_loses_no_gas(GameTestHelper h) {
        Area a = new Area(h, 1111);
        a.clear(0, 0, 5, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        TestGasHandler.Entity tank = handler(a, 3, 1, 1);
        tank.duringFill = () -> {
            throw new IllegalStateException("the test handler failing");
        };
        pipe.network().gas().add(new GasParcel(Map.of(N2, 12.0), T));
        try {
            pipe.step();
        } catch (IllegalStateException expected) {
            // Nothing catches it yet (NFR-013), and the gas mustn't depend on that.
        }
        h.assertValueEqual(tank.fills, 1, "the fill wasn't asked for");
        a.close(pipe.network().gas().moles(N2), 12, "the network's N2 after the handler threw");
        h.succeed();
    }

    /**
     * A handler that removes the pipe from inside a fill: the pipe's share and what the handler left of
     * the offer are released into the room, the handler keeps what it took, and it isn't asked to drain.
     * Nothing is lost or doubled, and the checker agrees.
     */
    @GameTest(template = "empty", batch = CHECKER_BATCH)
    public static void api_026_a_handler_that_removes_the_pipe_mid_fill_loses_no_gas(GameTestHelper h) {
        boolean was = ZasServerConfig.CONSERVATION_CHECKER.get();
        ZasServerConfig.CONSERVATION_CHECKER.set(true);
        try {
            Area a = new Area(h, 1112);
            a.box(0, 0, 4, 4);
            a.fill(2, 3, 2, Map.of(N2, 30.0));
            TestGasHandler.Entity tank = handler(a, 2, 1, 2);
            PipeTests.pipe(a, 2, 2, 2);
            a.tick();
            PipeBlockEntity pipe = a.entity(2, 2, 2);
            pipe.network().gas().add(new GasParcel(Map.of(N2, 12.0), T));
            tank.tank.add(new GasParcel(Map.of(O2, 2.0), T));
            tank.capacity = 3; // takes 1 mol of N2 of what it's offered
            tank.duringFill = () -> a.set(2, 2, 2, AIR);
            double n2 = a.zone(2, 3, 2).mixture().moles(N2) + 12;

            pipe.step();
            h.assertTrue(pipe.isRemoved(), "the pipe is still there");
            h.assertValueEqual(tank.drains, 0, "drains after the pipe went");
            a.close(tank.tank.moles(N2), 1, "what the handler took");
            a.close(tank.tank.moles(O2), 2, "the handler's O2");

            a.tick();
            a.tick();
            ConservationReport report = a.service().dimensionFor(a.level).lastConservation();
            h.assertTrue(report != null, "the checker didn't run");
            h.assertTrue(report.conserved(), "unexplained: " + report.violations());
            a.close(a.zone(2, 3, 2).mixture().moles(N2) + tank.tank.moles(N2), n2, "N2 in the room and the handler");
        } finally {
            ZasServerConfig.CONSERVATION_CHECKER.set(was);
        }
        h.succeed();
    }

    /** A handler that removes a device's port from inside a fill: the same, for a pump's gas. */
    @GameTest(template = "empty", batch = CHECKER_BATCH)
    public static void api_026_a_handler_that_removes_a_pump_mid_fill_loses_no_gas(GameTestHelper h) {
        Area a = new Area(h, 1113);
        a.box(0, 0, 6, 4);
        a.fill(3, 3, 2, Map.of(N2, 30.0));
        TestGasHandler.Entity tank = handler(a, 4, 2, 2);
        a.place(
                3,
                2,
                2,
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        a.tick();
        InlineDeviceEntity pump = a.entity(3, 2, 2);
        pump.port(1).network().gas().add(new GasParcel(Map.of(N2, 12.0), T));
        tank.capacity = 1;
        tank.duringFill = () -> a.set(3, 2, 2, AIR);
        double n2 = a.zone(3, 3, 2).mixture().moles(N2) + 12;

        pump.step();
        h.assertTrue(pump.isRemoved(), "the pump is still there");
        a.close(tank.tank.moles(N2), 1, "what the handler took");
        a.tick();
        a.tick();
        a.close(a.zone(3, 3, 2).mixture().moles(N2) + tank.tank.moles(N2), n2, "N2 in the room and the handler");
        h.succeed();
    }

    /**
     * A handler's chunk leaves full status while the pipe's stays. Nothing asks for it, so it stays
     * away; the pipe keeps its arm, so it has no open end, and keeps its gas. When the chunk returns,
     * transfers resume.
     */
    @GameTest(template = "empty", batch = CHUNK_BATCH, timeoutTicks = 800)
    public static void api_026_a_handler_in_a_chunk_that_is_away_is_left_alone(GameTestHelper h) {
        Area a = new Area(h, 1114);
        ChunkPos chunkA = new ChunkPos(a.abs(0, 0, 0));
        ChunkPos chunkB = new ChunkPos(a.abs(16, 0, 0));
        hold(a, chunkA, true);
        hold(a, chunkB, true);
        a.clear(13, 1, 17, 3);
        PipeBlockEntity pipe = capped(a, 15, 1, 2);
        TestGasHandler.Entity tank = handler(a, 16, 1, 2);
        pipe.network().gas().add(new GasParcel(Map.of(N2, 12.0), T));
        double[] held = new double[2];
        int[] calls = new int[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(tank.fills > 0, "no transfer before the chunk left"))
                .thenExecute(() -> hold(a, chunkB, false))
                .thenWaitUntil(() -> h.assertFalse(full(a, chunkB), "chunk B is still loaded"))
                .thenExecute(() -> {
                    held[0] = pipe.network().gas().moles(N2);
                    calls[0] = tank.threads.size();
                })
                .thenIdle(60) // three atmos ticks with chunk B away
                .thenExecute(() -> {
                    h.assertFalse(full(a, chunkB), "chunk B came back by itself");
                    h.assertTrue(arm(a, 15, 1, 2, Direction.EAST), "the arm went with the chunk");
                    h.assertFalse(pipe.isOpen(), "an open end with the handler's chunk away");
                    a.close(pipe.network().gas().moles(N2), held[0], "the network's N2 with chunk B away");
                    h.assertValueEqual(tank.threads.size(), calls[0], "calls with chunk B away");
                    hold(a, chunkB, true);
                })
                .thenWaitUntil(() -> h.assertTrue(full(a, chunkB), "chunk B hasn't returned"))
                .thenExecute(() -> pipe.network().gas().add(new GasParcel(Map.of(O2, 12.0), T)))
                .thenWaitUntil(() -> h.assertTrue(tank.tank.moles(O2) > 0, "no transfer after chunk B returned"))
                .thenExecute(() -> {
                    a.close(pipe.network().gas().moles(N2) + tank.tank.moles(N2), 12, "N2 in all");
                    a.close(pipe.network().gas().moles(O2) + tank.tank.moles(O2), 12, "O2 in all");
                    a.clear(13, 1, 17, 3);
                    hold(a, chunkA, false);
                    hold(a, chunkB, false);
                })
                .thenSucceed();
    }

    private static void hold(Area a, ChunkPos chunk, boolean on) {
        if (on) {
            a.level.getChunkSource().addRegionTicket(HOLD, chunk, 0, chunk);
        } else {
            a.level.getChunkSource().removeRegionTicket(HOLD, chunk, 0, chunk);
        }
    }

    private static boolean full(Area a, ChunkPos chunk) {
        return a.level.getChunkSource().hasChunk(chunk.x, chunk.z);
    }

    /** {@link Block} is here for its javadoc's sake only until a test needs it. */
    @SuppressWarnings("unused")
    private static final Class<Block> BLOCK = Block.class;
}
