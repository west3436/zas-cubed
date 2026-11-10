package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.capability.IGasHandler.Action;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.PortedBlock;
import io.github.west3436.zas.atmos.pipe.GasHandlers;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import io.github.west3436.zas.atmos.pipe.PipeGasHandler;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.radiator.HeatExchangerBlock;
import io.github.west3436.zas.atmos.radiator.RadiatorAdaptorBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.valve.ValveBlock;
import io.github.west3436.zas.atmos.vent.PassiveVentBlock;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Pipe parts as gas handlers themselves ({@link PipeGasHandler}, API-021): what another mod's pipe or
 * machine finds on a part's face, the fill's limit, the drain, and that gas is never counted twice,
 * neither between two of ZAS³'s own parts nor at a joint where both sides move gas (API-025, API-026).
 * Every test drives the device steps itself, so they share a batch of their own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeGasHandlerTests {
    private static final String BATCH = "zas_atmos_pipe_gas_handler";
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    private PipeGasHandlerTests() {}

    private static IGasHandler on(Area a, int x, int y, int z, Direction face) {
        return a.level.getCapability(IGasHandler.BLOCK, a.abs(x, y, z), face);
    }

    private static Set<Direction> offered(Area a, int x, int y, int z) {
        Set<Direction> faces = EnumSet.noneOf(Direction.class);
        for (Direction d : Direction.values()) {
            if (on(a, x, y, z, d) != null) {
                faces.add(d);
            }
        }
        return faces;
    }

    /** A cap at x - 1 and a pipe at x: a closed network of two parts. */
    private static PipeBlockEntity capped(Area a, int x, int y, int z) {
        PipeTests.cap(a, x - 1, y, z, Direction.EAST);
        PipeTests.pipe(a, x, y, z);
        return a.entity(x, y, z);
    }

    // ---- What a face offers --------------------------------------------------------------------

    /**
     * A pipe offers its network on every face but one that meets a pipe part, and on no face in
     * particular it offers nothing. What it shows is the whole network's gas, temperature and volume,
     * the same from any face and from any pipe of the network.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_021_a_pipe_offers_its_network_on_every_face(GameTestHelper h) {
        Area a = new Area(h, 1120);
        a.clear(0, 0, 6, 3);
        PipeTests.cap(a, 1, 1, 1, Direction.EAST);
        PipeTests.pipe(a, 2, 1, 1);
        PipeTests.pipe(a, 3, 1, 1);
        PipeBlockEntity pipe = a.entity(3, 1, 1);
        h.assertValueEqual(
                offered(a, 3, 1, 1), EnumSet.complementOf(EnumSet.of(Direction.WEST)), "the end pipe's faces");
        h.assertValueEqual(
                offered(a, 2, 1, 1),
                EnumSet.of(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH),
                "the middle pipe's faces");
        h.assertValueEqual(offered(a, 1, 1, 1), EnumSet.noneOf(Direction.class), "the faces of a cap on a pipe");
        h.assertTrue(
                a.level.getCapability(IGasHandler.BLOCK, a.abs(3, 1, 1), null) == null, "a pipe offers one on no face");

        pipe.network().gas().add(new GasParcel(Map.of(O2, 3.0, N2, 5.0), 350));
        double volume = PipeVolumes.PIPE * 3;
        a.close(pipe.network().volume(), volume, "the network's volume");
        for (IGasHandler handler : new IGasHandler[] {on(a, 3, 1, 1, Direction.EAST), on(a, 2, 1, 1, Direction.UP)}) {
            GasMixture seen = handler.contents();
            a.close(seen.moles(O2), 3, "the O2 a face shows");
            a.close(seen.moles(N2), 5, "the N2 a face shows");
            a.close(seen.temperature(), 350, "the temperature a face shows");
            a.close(seen.volume(), volume, "the volume a face shows");
        }

        // A pipe placed beside them joins the network, and gas put in on its face is in all of it.
        PipeTests.pipe(a, 3, 2, 1);
        h.assertTrue(on(a, 3, 1, 1, Direction.UP) == null, "a face that now meets a pipe still offers one");
        GasParcel took = on(a, 3, 2, 1, Direction.UP).fill(new GasParcel(Map.of(O2, 1.0), 350), Action.EXECUTE);
        a.close(took.moles(O2), 1, "what the new pipe's face took");
        a.close(pipe.network().gas().moles(O2), 4, "the network's O2");
        a.close(on(a, 2, 1, 1, Direction.DOWN).contents().volume(), PipeVolumes.PIPE * 4, "the volume it shows now");
        h.succeed();
    }

    /**
     * Each part offers it where a pipe would join it and nowhere else: a cap where it points, a vent,
     * scrubber, passive vent and heat exchanger at the back, a valve on its two flanges, a radiator
     * adaptor on its pipe end, a pump, filter, mixer and electrolyzer on each port. A connector port,
     * a canister and radiator pipe offer none. Every pipe part the mod registers is covered.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_021_every_pipe_part_offers_it_where_a_pipe_joins(GameTestHelper h) {
        Area a = new Area(h, 1121);
        a.clear(-1, -1, 80, 5);
        Map<BlockState, Set<Direction>> expected = new LinkedHashMap<>();
        expected.put(AtmosBlocks.PIPE.get().defaultBlockState(), EnumSet.allOf(Direction.class));
        expected.put(
                AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.UP),
                EnumSet.of(Direction.UP));
        expected.put(GasDeviceTests.port(), EnumSet.noneOf(Direction.class));
        expected.put(GasDeviceTests.canister(), EnumSet.noneOf(Direction.class));
        expected.put(GasDeviceTests.vent(Direction.DOWN), EnumSet.of(Direction.UP));
        expected.put(ScrubberTests.scrubber(Direction.DOWN), EnumSet.of(Direction.UP));
        expected.put(
                AtmosBlocks.PASSIVE_VENT.get().defaultBlockState().setValue(PassiveVentBlock.FACING, Direction.DOWN),
                EnumSet.of(Direction.UP));
        expected.put(
                AtmosBlocks.HEAT_EXCHANGER.get().defaultBlockState().setValue(HeatExchangerBlock.FACING, Direction.UP),
                EnumSet.of(Direction.DOWN));
        expected.put(AtmosBlocks.RADIATOR_PIPE.get().defaultBlockState(), EnumSet.noneOf(Direction.class));
        expected.put(
                AtmosBlocks.RADIATOR_ADAPTOR
                        .get()
                        .defaultBlockState()
                        .setValue(RadiatorAdaptorBlock.FACING, Direction.EAST),
                EnumSet.of(Direction.WEST));
        for (Block valve : new Block[] {AtmosBlocks.MANUAL_VALVE.get(), AtmosBlocks.REDSTONE_VALVE.get()}) {
            expected.put(
                    valve.defaultBlockState().setValue(ValveBlock.AXIS, Direction.Axis.X),
                    EnumSet.of(Direction.EAST, Direction.WEST));
        }
        for (Block ported : new Block[] {
            AtmosBlocks.PRESSURE_PUMP.get(),
            AtmosBlocks.VOLUME_PUMP.get(),
            AtmosBlocks.FILTER.get(),
            AtmosBlocks.MIXER.get(),
            AtmosBlocks.ELECTROLYZER.get()
        }) {
            BlockState state = ported.defaultBlockState();
            Set<Direction> ports = EnumSet.copyOf(((PortedBlock) ported).portFaces(state));
            h.assertTrue(ports.size() >= 2, ported.getName().getString() + " has fewer than two ports");
            expected.put(state, ports);
        }

        int x = 0;
        for (Map.Entry<BlockState, Set<Direction>> part : expected.entrySet()) {
            a.place(x, 2, 2, part.getKey());
            h.assertValueEqual(
                    offered(a, x, 2, 2),
                    part.getValue(),
                    "the faces of " + part.getKey().getBlock().getName().getString());
            x += 3;
        }
        // None is left out: every pipe part the mod registers has been through the loop above.
        Set<Block> seen = new HashSet<>();
        expected.keySet().forEach(state -> seen.add(state.getBlock()));
        for (DeferredHolder<Block, ? extends Block> entry : AtmosBlocks.BLOCKS.getEntries()) {
            if (entry.get() instanceof PipeConnector) {
                h.assertTrue(seen.contains(entry.get()), entry.getId() + " isn't covered by this test");
            }
        }
        h.succeed();
    }

    /**
     * A device's port face shows that port's own network and fills it alone. A shut valve still offers
     * the capability, as its pipes keep their arms, but shows, takes and gives nothing until it opens,
     * and the handler a block got while it was shut works once it has.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_021_a_port_face_is_its_own_network_and_a_shut_valve_passes_none(GameTestHelper h) {
        Area a = new Area(h, 1122);
        a.clear(0, 0, 10, 3);
        BlockState pump =
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST);
        a.place(2, 1, 1, pump);
        InlineDeviceEntity device = a.entity(2, 1, 1);
        List<Direction> faces = ((PortedBlock) pump.getBlock()).portFaces(pump);
        for (int i = 0; i < faces.size(); i++) {
            IGasHandler port = on(a, 2, 1, 1, faces.get(i));
            GasParcel took = port.fill(new GasParcel(Map.of(O2, i + 1.0), T), Action.EXECUTE);
            a.close(took.moles(O2), i + 1.0, "what the " + faces.get(i) + " port took");
        }
        for (int i = 0; i < faces.size(); i++) {
            a.close(device.port(i).network().gas().moles(O2), i + 1.0, "O2 behind the " + faces.get(i) + " port");
            a.close(on(a, 2, 1, 1, faces.get(i)).contents().volume(), PipeVolumes.PORT, "the volume a port shows");
        }

        BlockState shut = AtmosBlocks.MANUAL_VALVE
                .get()
                .defaultBlockState()
                .setValue(ValveBlock.AXIS, Direction.Axis.X)
                .setValue(ValveBlock.OPEN, false);
        a.place(6, 1, 1, shut);
        PipeNodeEntity valve = a.entity(6, 1, 1);
        valve.network().gas().add(new GasParcel(Map.of(N2, 4.0), T));
        IGasHandler flange = on(a, 6, 1, 1, Direction.EAST);
        h.assertTrue(flange != null, "a shut valve's flange offers none");
        h.assertTrue(flange.contents().isEmpty(), "a shut valve shows its gas");
        h.assertTrue(
                flange.fill(new GasParcel(Map.of(O2, 1.0), T), Action.EXECUTE).isEmpty(), "a shut valve took gas");
        h.assertTrue(flange.drain(Map.of(N2, 1.0), Action.EXECUTE).isEmpty(), "a shut valve gave gas");
        a.close(valve.network().gas().totalMoles(), 4, "the shut valve's gas");

        a.set(6, 1, 1, shut.setValue(ValveBlock.OPEN, true));
        a.close(flange.contents().moles(N2), 4, "what the valve shows once open");
        a.close(flange.drain(Map.of(N2, 1.0), Action.EXECUTE).moles(N2), 1, "what the open valve gave");
        a.close(valve.network().gas().moles(N2), 3, "the valve's gas after the drain");

        // A part that has gone holds nothing, whoever still has its handler.
        a.set(6, 1, 1, AIR);
        h.assertTrue(flange.contents().isEmpty(), "a removed valve shows gas");
        h.assertTrue(
                flange.fill(new GasParcel(Map.of(O2, 1.0), T), Action.EXECUTE).isEmpty(), "a removed valve took");
        h.assertTrue(flange.drain(Map.of(N2, 1.0), Action.EXECUTE).isEmpty(), "a removed valve gave gas");
        h.succeed();
    }

    // ---- Fill and drain ------------------------------------------------------------------------

    /**
     * A fill stops at a pump's highest pressure: under it the whole parcel goes in, and one that would
     * pass it goes in only as far as the limit, each gas by the same part, whatever its temperature.
     * At the limit nothing more goes in. Simulating answers what executing does and changes nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_021_a_pipes_fill_stops_at_a_pumps_limit(GameTestHelper h) {
        Area a = new Area(h, 1123);
        a.clear(0, 0, 8, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        GasStore gas = pipe.network().gas();
        IGasHandler handler = on(a, 2, 1, 1, Direction.EAST);

        GasParcel small = new GasParcel(Map.of(O2, 2.0, N2, 6.0), 320);
        h.assertValueEqual(handler.fill(small, Action.SIMULATE).moles(), small.moles(), "a small fill simulated");
        h.assertTrue(gas.isEmpty(), "a simulated fill changed the network");
        h.assertValueEqual(handler.fill(small, Action.EXECUTE).moles(), small.moles(), "a small fill");
        a.close(gas.totalMoles(), 8, "the network after a small fill");
        a.close(gas.temperature(), 320, "its temperature");

        // Hotter than the network and far more than fits: it mixes in as far as the limit.
        GasParcel flood = new GasParcel(Map.of(O2, 1e6, N2, 3e6), 600);
        GasParcel would = handler.fill(flood, Action.SIMULATE);
        a.close(gas.totalMoles(), 8, "the network after simulating a flood");
        GasParcel took = handler.fill(flood, Action.EXECUTE);
        h.assertValueEqual(took.moles(), would.moles(), "what the flood's fill took");
        h.assertTrue(took.totalMoles() > 0 && took.totalMoles() < flood.totalMoles(), "the part of the flood");
        a.close(took.moles(N2) / took.moles(O2), 3, "the part's mix");
        a.close(gas.totalMoles(), 8 + took.totalMoles(), "the network holds what the fill says it took");
        a.close(gas.pressure(), PipeGasHandler.FILL_LIMIT_KPA, "the pressure a flood leaves");

        h.assertTrue(handler.fill(small, Action.SIMULATE).isEmpty(), "a fill at the limit, simulated");
        h.assertTrue(handler.fill(small, Action.EXECUTE).isEmpty(), "a fill at the limit");
        a.close(gas.pressure(), PipeGasHandler.FILL_LIMIT_KPA, "the pressure after a refused fill");

        // Over the limit already, as a fire in the pipe leaves it: nothing goes in, and nothing comes out unasked.
        gas.setTemperature(gas.temperature() * 1.5);
        double held = gas.totalMoles();
        h.assertTrue(handler.fill(small, Action.EXECUTE).isEmpty(), "a fill over the limit");
        a.close(gas.totalMoles(), held, "the network over the limit");
        h.succeed();
    }

    /**
     * A drain gives what was asked for of each gas, or all there is, at the network's temperature, and
     * nothing of a gas the network lacks. Simulating answers what executing does and changes nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_021_a_pipe_drains_what_it_holds(GameTestHelper h) {
        Area a = new Area(h, 1124);
        a.clear(0, 0, 8, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        GasStore gas = pipe.network().gas();
        gas.add(new GasParcel(Map.of(O2, 4.0, N2, 6.0), 310));
        IGasHandler handler = on(a, 2, 1, 1, Direction.UP);
        Map<ResourceLocation, Double> want = Map.of(O2, 1.5, N2, 100.0, CO2, 2.0);

        GasParcel would = handler.drain(want, Action.SIMULATE);
        a.close(would.moles(O2), 1.5, "a simulated drain's O2");
        a.close(would.moles(N2), 6, "a simulated drain's N2: all there is");
        a.close(would.moles(CO2), 0, "a simulated drain's CO2");
        a.close(gas.totalMoles(), 10, "the network after a simulated drain");
        GasParcel gave = handler.drain(want, Action.EXECUTE);
        h.assertValueEqual(gave.moles(), would.moles(), "what the drain gave");
        a.close(gave.temperature(), 310, "the temperature of what it gave");
        a.close(gas.moles(O2), 2.5, "the network's O2 after the drain");
        a.close(gas.moles(N2), 0, "the network's N2 after the drain");
        h.assertTrue(handler.drain(Map.of(N2, 1.0), Action.EXECUTE).isEmpty(), "a drain of a gas that has gone");
        h.succeed();
    }

    // ---- Never counted twice -------------------------------------------------------------------

    /**
     * Two of ZAS³'s own parts are never each other's handlers, though both provide the capability:
     * neither offers one toward the other, a pipe counts no handler there, and a transfer asked for on
     * that face moves nothing. Their gas is one network's, and stepping leaves every mole where it was.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_025_pipe_parts_are_never_each_others_handlers(GameTestHelper h) {
        Area a = new Area(h, 1125);
        a.clear(0, 0, 10, 3);
        PipeTests.cap(a, 1, 1, 1, Direction.EAST);
        PipeTests.pipe(a, 2, 1, 1);
        PipeTests.pipe(a, 3, 1, 1);
        BlockState pump =
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST);
        a.place(4, 1, 1, pump);
        PipeTests.cap(a, 5, 1, 1, Direction.WEST);
        PipeBlockEntity pipe = a.entity(2, 1, 1);
        InlineDeviceEntity device = a.entity(4, 1, 1);
        pipe.network().gas().add(new GasParcel(Map.of(O2, 3.0, N2, 9.0), T));
        device.port(1).network().gas().add(new GasParcel(Map.of(N2, 2.0), T));

        BlockPos[] parts = {a.abs(2, 1, 1), a.abs(3, 1, 1), a.abs(4, 1, 1)};
        for (BlockPos pos : parts) {
            for (Direction face : new Direction[] {Direction.EAST, Direction.WEST}) {
                BlockState state = a.level.getBlockState(pos);
                h.assertTrue(
                        a.level.getCapability(IGasHandler.BLOCK, pos, face) == null,
                        state.getBlock().getName().getString() + " offers one toward a pipe part on " + face);
                h.assertFalse(
                        GasHandlers.connects(a.level, pos, state, face),
                        state.getBlock().getName().getString() + " counts a pipe part as a handler on " + face);
            }
        }

        // Asked outright, a transfer toward a pipe part moves nothing either way.
        GasStore mine = new GasStore(70);
        mine.add(new GasParcel(Map.of(O2, 50.0), T));
        GasHandlers.exchange(
                a.level,
                parts[0],
                a.level.getBlockState(parts[0]),
                Direction.EAST,
                new GasHandlers.Buffer(() -> mine, mine::add, () -> false));
        a.close(mine.totalMoles(), 50, "the store a transfer was asked of");

        double volume = pipe.network().volume();
        for (int i = 0; i < 4; i++) {
            a.step();
            a.close(pipe.network().gas().moles(O2), 3, "the pipes' O2 at step " + i);
            a.close(pipe.network().gas().moles(N2), 9, "the pipes' N2 at step " + i);
            a.close(device.port(1).network().gas().moles(N2), 2, "the pump's far port at step " + i);
            a.close(pipe.network().volume(), volume, "the network's volume at step " + i);
        }
        h.assertValueEqual(pipe.network().members().size(), 4, "the network's members: cap, two pipes, a port");
        h.succeed();
    }

    /**
     * Both sides move gas across one joint: the pipe evens out with the block beside it each atmos
     * tick, while that block pulls from the pipe and pushes into it through the pipe's own handler, as
     * a pump of another mod would, between the pipe's steps and from inside each of the pipe's calls.
     * Every mole of each gas stays in one of the two.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_026_a_joint_moved_from_both_sides_keeps_every_mole(GameTestHelper h) {
        Area a = new Area(h, 1126);
        a.clear(0, 0, 6, 3);
        PipeBlockEntity pipe = capped(a, 2, 1, 1);
        a.place(3, 1, 1, TestGasHandler.block().defaultBlockState());
        TestGasHandler.Entity tank = a.entity(3, 1, 1);
        h.assertFalse(pipe.isOpen(), "a pipe between a cap and another mod's block has an open end");
        IGasHandler pipeSide = on(a, 2, 1, 1, Direction.EAST);
        h.assertTrue(pipeSide != null, "the pipe offers none toward the block");
        GasStore gas = pipe.network().gas();
        gas.add(new GasParcel(Map.of(N2, 40.0), 350));
        tank.tank.add(new GasParcel(Map.of(O2, 30.0), 300));

        Runnable pull = () -> tank.tank.add(pipeSide.drain(Map.of(N2, 1.5, O2, 0.25), Action.EXECUTE));
        Runnable push = () -> {
            GasParcel offer = tank.tank.take(Map.of(O2, 2.0, N2, 0.5));
            GasParcel took = pipeSide.fill(offer, Action.EXECUTE);
            Map<ResourceLocation, Double> back = new TreeMap<>();
            offer.moles().forEach((id, n) -> back.put(id, n - took.moles(id)));
            tank.tank.add(new GasParcel(back, offer.temperature()));
        };
        tank.duringContents = pull;
        tank.duringFill = push;
        tank.duringDrain = pull;
        for (int i = 0; i < 10; i++) {
            a.step();
            push.run();
            pull.run();
            a.close(gas.moles(N2) + tank.tank.moles(N2), 40, "N2 in both at step " + i);
            a.close(gas.moles(O2) + tank.tank.moles(O2), 30, "O2 in both at step " + i);
        }
        h.assertTrue(tank.fills > 0 && tank.drains > 0, "the pipe never moved gas itself");
        h.assertTrue(gas.moles(O2) > 0 && tank.tank.moles(N2) > 0, "no gas crossed");
        h.succeed();
    }
}
