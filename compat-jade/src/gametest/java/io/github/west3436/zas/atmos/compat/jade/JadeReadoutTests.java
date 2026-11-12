package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlockEntity;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gauge.GaugeBlock;
import io.github.west3436.zas.atmos.gauge.GaugeBlockEntity;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.portable.PortableGasDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import io.github.west3436.zas.atmos.tank.LargeTankBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What Jade's server sends for each kind of device that holds gas (COMPAT-030, COMPAT-031), and
 * that it sends nothing for plain air, for other blocks and for devices with nothing to read
 * (COMPAT-032), on a server with Jade loaded. How the client draws it is checked by eye.
 *
 * <p>Each test builds at its own {@link Spot} and runs within one game tick, so no device steps in
 * between: the gas is where the test put it.
 */
@GameTestHolder(JadeReadoutTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class JadeReadoutTests {
    static final String NAMESPACE = "zas_jade";
    private static final String BATCH = "zas_jade_readout";

    private JadeReadoutTests() {}

    // ---- Helpers ---------------------------------------------------------------------------

    private static void row(
            GameTestHelper h, Reading.Row row, Reading.Lead lead, String id, double kpa, double kelvin, String what) {
        h.assertValueEqual(row.lead(), lead, what + "'s lead");
        h.assertValueEqual(row.id(), id, what + "'s id");
        same(h, row.kpa(), kpa, what + "'s kPa");
        same(h, row.kelvin(), kelvin, what + "'s K");
    }

    /** NaN, for no value, is the same as NaN. */
    private static void same(GameTestHelper h, double actual, double expected, String what) {
        boolean same = Double.isNaN(expected) ? Double.isNaN(actual) : Spot.close(actual, expected);
        h.assertTrue(same, what + ": expected " + expected + ", got " + actual);
    }

    /**
     * Checks a reading's shares against a body's moles: every gas of half a percent or more, in the
     * order a gas's id sorts in, which is the device windows' order.
     */
    private static void shares(GameTestHelper h, Reading reading, Map<ResourceLocation, Double> moles, String what) {
        double total = moles.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Reading.Share> expected = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Double> gas : new TreeMap<>(moles).entrySet()) {
            if (gas.getValue() / total >= 0.005) {
                expected.add(new Reading.Share(gas.getKey().toString(), (float) (gas.getValue() / total)));
            }
        }
        h.assertValueEqual(reading.shares().size(), expected.size(), what + "'s gases listed");
        for (int i = 0; i < expected.size(); i++) {
            Reading.Share share = reading.shares().get(i);
            h.assertValueEqual(share.gas(), expected.get(i).gas(), what + "'s gas " + i);
            same(h, share.fraction(), expected.get(i).fraction(), what + "'s share of " + share.gas());
        }
    }

    /** A reading of one body of gas: one row with the pressure icon, and the gases. */
    private static void body(
            GameTestHelper h,
            Reading reading,
            double kpa,
            double kelvin,
            Map<ResourceLocation, Double> moles,
            String what) {
        h.assertValueEqual(reading.rows().size(), 1, what + "'s rows");
        row(h, reading.rows().get(0), Reading.Lead.BODY, "", kpa, kelvin, what);
        shares(h, reading, moles, what);
    }

    private static ItemStack tank(Item item, Map<ResourceLocation, Double> moles) {
        ItemStack tank = new ItemStack(item);
        GasTankItem.setGas(tank, new GasParcel(moles, Spot.T));
        return tank;
    }

    private static String id(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem())
                .toString();
    }

    // ---- COMPAT-030 and COMPAT-031: one body of gas ----------------------------------------------

    /** Three joined pipes are one network: each reads the network's pressure, temperature and gases. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_a_pipe_reads_as_its_network(GameTestHelper h) {
        Spot s = new Spot(h, 0);
        s.clear();
        PipeBlockEntity[] pipes = new PipeBlockEntity[3];
        for (int x = 0; x < 3; x++) {
            pipes[x] = s.place(x, 1, 1, AtmosBlocks.PIPE.get().defaultBlockState(), PipeBlockEntity.class);
        }
        Map<ResourceLocation, Double> moles = Map.of(Spot.N2, 30.0, Spot.O2, 4.0);
        pipes[0].network().gas().add(new GasParcel(moles, 310));
        h.assertTrue(pipes[2].network() == pipes[0].network(), "the three pipes aren't one network");
        double volume = 3 * pipes[0].nodeVolume();
        double kpa = 34.0 * GasMixture.GAS_CONSTANT * 310 / volume;
        for (PipeBlockEntity pipe : pipes) {
            h.assertTrue(JadeProbe.asks(s.level, pipe.getBlockPos()), "a client wouldn't ask about a pipe");
            body(h, JadeProbe.reading(s.level, pipe.getBlockPos()), kpa, 310, moles, "the pipe");
        }
        // Reading built nothing and moved nothing.
        h.assertTrue(Spot.close(pipes[0].network().gas().totalMoles(), 34.0), "reading changed the network's gas");
        s.clear();
        h.succeed();
    }

    /** A pipe whose network hasn't been built reads its own gas, and reading doesn't build one. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_reading_a_pipe_builds_no_network(GameTestHelper h) {
        Spot s = new Spot(h, 1);
        s.clear();
        PipeBlockEntity pipe = s.place(1, 1, 1, AtmosBlocks.PIPE.get().defaultBlockState(), PipeBlockEntity.class);
        Map<ResourceLocation, Double> moles = Map.of(Spot.O2, 2.0);
        pipe.stored().set(new GasParcel(moles, 250));
        boolean hadNetwork = pipe.currentNetwork() != null;
        double kpa = 2.0 * GasMixture.GAS_CONSTANT * 250 / pipe.nodeVolume();
        body(h, JadeProbe.reading(s.level, pipe.getBlockPos()), kpa, 250, moles, "the lone pipe");
        h.assertValueEqual(pipe.currentNetwork() != null, hadNetwork, "whether the pipe has a network after a reading");
        s.clear();
        h.succeed();
    }

    /** A canister with three gases and a trace: the three are listed with their shares, the trace isn't. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_031_a_canister_lists_its_gases_without_the_trace(GameTestHelper h) {
        Spot s = new Spot(h, 2);
        s.clear();
        CanisterBlockEntity can =
                s.place(1, 1, 1, AtmosBlocks.CANISTER.get().defaultBlockState(), CanisterBlockEntity.class);
        Map<ResourceLocation, Double> moles = Map.of(Spot.N2, 120.0, Spot.O2, 30.0, Spot.CO2, 10.0, Spot.H2, 0.5);
        can.network().gas().add(new GasParcel(moles, Spot.T));
        Reading reading = JadeProbe.reading(s.level, can.getBlockPos());
        body(h, reading, can.network().gas().pressure(), Spot.T, moles, "the canister");
        h.assertValueEqual(
                reading.shares().stream().map(Reading.Share::gas).toList(),
                List.of("zas:carbon_dioxide", "zas:nitrogen", "zas:oxygen"),
                "the canister's gases");
        s.clear();
        h.succeed();
    }

    /** A large tank reads its gas from either half, though only its lower half has a block entity. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_a_large_tank_reads_its_gas_from_either_half(GameTestHelper h) {
        Spot s = new Spot(h, 12);
        s.clear();
        BlockState lower = AtmosBlocks.LARGE_GAS_TANK.get().defaultBlockState();
        // The upper half first: the lower one goes at once with nothing above it.
        s.set(1, 2, 1, lower.setValue(LargeTankBlock.HALF, DoubleBlockHalf.UPPER));
        LargeTankBlockEntity tank = s.place(1, 1, 1, lower, LargeTankBlockEntity.class);
        Map<ResourceLocation, Double> moles = Map.of(Spot.N2, 900.0, Spot.O2, 100.0);
        tank.network().gas().add(new GasParcel(moles, 300));
        double kpa = 1000.0 * GasMixture.GAS_CONSTANT * 300 / tank.nodeVolume();
        for (BlockPos half : List.of(s.abs(1, 1, 1), s.abs(1, 2, 1))) {
            h.assertTrue(JadeProbe.asks(s.level, half), "a client wouldn't ask about a large tank's half");
            body(h, JadeProbe.reading(s.level, half), kpa, 300, moles, "the large tank");
        }
        s.clear();
        h.succeed();
    }

    /** Every device that is one node of a pipe network reads its gas as a pipe does. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_every_device_on_a_network_reads_its_gas(GameTestHelper h) {
        Spot s = new Spot(h, 3);
        s.clear();
        List<Block> blocks = List.of(
                AtmosBlocks.PIPE_CAP.get(),
                AtmosBlocks.CONNECTOR_PORT.get(),
                AtmosBlocks.MANUAL_VALVE.get(),
                AtmosBlocks.REDSTONE_VALVE.get(),
                AtmosBlocks.VENT.get(),
                AtmosBlocks.SCRUBBER.get(),
                AtmosBlocks.PASSIVE_VENT.get(),
                AtmosBlocks.RADIATOR_PIPE.get(),
                AtmosBlocks.RADIATOR_ADAPTOR.get(),
                AtmosBlocks.HEAT_EXCHANGER.get());
        Map<ResourceLocation, Double> moles = Map.of(Spot.N2, 3.0, Spot.CO2, 1.0);
        for (int i = 0; i < blocks.size(); i++) {
            // Two blocks apart, so none joins the next.
            PipeNodeEntity node = s.place(2 * i, 1, 1, blocks.get(i).defaultBlockState(), PipeNodeEntity.class);
            String what = "the " + blocks.get(i).getName().getString();
            node.network().gas().add(new GasParcel(moles, 330));
            GasStore gas = node.network().gas();
            h.assertTrue(JadeProbe.asks(s.level, node.getBlockPos()), "a client wouldn't ask about " + what);
            body(h, JadeProbe.reading(s.level, node.getBlockPos()), gas.pressure(), gas.temperature(), moles, what);
        }
        s.clear();
        h.succeed();
    }

    /** A vacuum has a pressure, 0, and neither a temperature nor gases. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_an_empty_pipe_reads_zero_and_no_temperature(GameTestHelper h) {
        Spot s = new Spot(h, 4);
        s.clear();
        PipeBlockEntity pipe = s.place(1, 1, 1, AtmosBlocks.PIPE.get().defaultBlockState(), PipeBlockEntity.class);
        body(h, JadeProbe.reading(s.level, pipe.getBlockPos()), 0, Double.NaN, Map.of(), "the empty pipe");
        s.clear();
        h.succeed();
    }

    /** A portable pump and a portable scrubber read the gas in their own store. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_a_portable_pump_and_scrubber_read_their_store(GameTestHelper h) {
        Spot s = new Spot(h, 5);
        s.clear();
        List<Block> blocks = List.of(AtmosBlocks.PORTABLE_PUMP.get(), AtmosBlocks.PORTABLE_SCRUBBER.get());
        Map<ResourceLocation, Double> moles = Map.of(Spot.CO2, 60.0, Spot.N2, 40.0);
        for (int i = 0; i < blocks.size(); i++) {
            PortableGasDevice device = s.place(2 * i, 1, 1, blocks.get(i).defaultBlockState(), PortableGasDevice.class);
            String what = "the " + blocks.get(i).getName().getString();
            device.store().set(new GasParcel(moles, 280));
            h.assertTrue(JadeProbe.asks(s.level, device.getBlockPos()), "a client wouldn't ask about " + what);
            body(
                    h,
                    JadeProbe.reading(s.level, device.getBlockPos()),
                    device.store().pressure(),
                    280,
                    moles,
                    what);
        }
        s.clear();
        h.succeed();
    }

    // ---- A gauge (#488) ------------------------------------------------------------------------

    /**
     * A gauge reads what it is on as one body of gas, with its gases: a pipe's network, a canister's
     * own gas, and the port of a pump it is beside. Reading it moves no gas.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_gauge_reads_what_it_is_on(GameTestHelper h) {
        Spot s = new Spot(h, 30);
        s.clear();
        // On a wall facing north: on the block south of it.
        BlockState gauge = AtmosBlocks.GAUGE.get().defaultBlockState();
        Map<ResourceLocation, Double> moles = Map.of(Spot.N2, 30.0, Spot.O2, 4.0);

        PipeBlockEntity pipe = s.place(1, 1, 2, AtmosBlocks.PIPE.get().defaultBlockState(), PipeBlockEntity.class);
        GaugeBlockEntity onPipe = s.place(1, 1, 1, gauge, GaugeBlockEntity.class);
        pipe.network().gas().add(new GasParcel(moles, 310));
        GasStore gas = pipe.network().gas();
        h.assertTrue(JadeProbe.asks(s.level, onPipe.getBlockPos()), "a client wouldn't ask about a gauge");
        body(h, JadeProbe.reading(s.level, onPipe.getBlockPos()), gas.pressure(), 310, moles, "the pipe's gauge");
        h.assertTrue(Spot.close(gas.totalMoles(), 34.0), "reading the gauge changed the pipe's gas");

        CanisterBlockEntity canister =
                s.place(4, 1, 2, AtmosBlocks.CANISTER.get().defaultBlockState(), CanisterBlockEntity.class);
        GaugeBlockEntity onCanister = s.place(4, 1, 1, gauge, GaugeBlockEntity.class);
        canister.network().gas().add(new GasParcel(moles, 300));
        double kpa = 34.0 * GasMixture.GAS_CONSTANT * 300 / canister.nodeVolume();
        body(h, JadeProbe.reading(s.level, onCanister.getBlockPos()), kpa, 300, moles, "the canister's gauge");

        InlineDeviceEntity pump =
                s.place(7, 1, 2, AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState(), InlineDeviceEntity.class);
        GaugeBlockEntity onPump = s.place(7, 1, 1, gauge, GaugeBlockEntity.class);
        for (int port = 0; port < 2; port++) {
            pump.port(port).network().gas().add(new GasParcel(Map.of(Spot.O2, 1.0 + port), 280 + 10 * port));
        }
        int port = GaugeBlock.reads(s.level, onPump.getBlockPos(), onPump.getBlockState())
                .port();
        h.assertTrue(port >= 0, "the pump's gauge reads no port");
        GasStore portGas = pump.port(port).network().gas();
        body(
                h,
                JadeProbe.reading(s.level, onPump.getBlockPos()),
                portGas.pressure(),
                280 + 10 * port,
                Map.of(Spot.O2, 1.0 + port),
                "the pump's gauge");
        s.clear();
        h.succeed();
    }

    // ---- COMPAT-030: several ports -------------------------------------------------------------

    /** A pump reads its inlet and its outlet, a filter and a mixer their side port too; none lists gases. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_a_pump_filter_and_mixer_read_each_port(GameTestHelper h) {
        Spot s = new Spot(h, 6);
        s.clear();
        List<Block> blocks = List.of(
                AtmosBlocks.PRESSURE_PUMP.get(),
                AtmosBlocks.VOLUME_PUMP.get(),
                AtmosBlocks.FILTER.get(),
                AtmosBlocks.MIXER.get());
        Reading.Lead[] leads = {Reading.Lead.IN, Reading.Lead.OUT, Reading.Lead.SIDE};
        for (int i = 0; i < blocks.size(); i++) {
            // Three blocks apart, so no port faces another device's.
            InlineDeviceEntity device =
                    s.place(3 * i, 1, 3, blocks.get(i).defaultBlockState(), InlineDeviceEntity.class);
            String what = "the " + blocks.get(i).getName().getString();
            int ports = i < 2 ? 2 : 3;
            for (int port = 0; port < ports; port++) {
                // Each port its own amount and temperature, so a row can't pass for another.
                device.port(port).network().gas().add(new GasParcel(Map.of(Spot.O2, 1.0 + port), 280 + 10 * port));
            }
            h.assertTrue(JadeProbe.asks(s.level, device.getBlockPos()), "a client wouldn't ask about " + what);
            Reading reading = JadeProbe.reading(s.level, device.getBlockPos());
            h.assertValueEqual(reading.rows().size(), ports, what + "'s rows");
            for (int port = 0; port < ports; port++) {
                GasStore gas = device.port(port).network().gas();
                row(
                        h,
                        reading.rows().get(port),
                        leads[port],
                        "",
                        gas.pressure(),
                        280 + 10 * port,
                        what + "'s port " + port);
            }
            h.assertTrue(reading.shares().isEmpty(), what + " lists gases");
        }
        s.clear();
        h.succeed();
    }

    /** An electrolyzer's two outlets each read under the gas they give; an empty one reads 0. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_an_electrolyzer_reads_each_outlet_by_its_gas(GameTestHelper h) {
        Spot s = new Spot(h, 7);
        s.clear();
        ElectrolyzerBlockEntity device =
                s.place(1, 1, 3, AtmosBlocks.ELECTROLYZER.get().defaultBlockState(), ElectrolyzerBlockEntity.class);
        device.hydrogen().network().gas().add(new GasParcel(Map.of(Spot.H2, 2.0), 300));
        Reading reading = JadeProbe.reading(s.level, device.getBlockPos());
        h.assertValueEqual(reading.rows().size(), 2, "the electrolyzer's rows");
        row(h, reading.rows().get(0), Reading.Lead.GAS, "zas:oxygen", 0, Double.NaN, "the oxygen outlet");
        double kpa = device.hydrogen().network().gas().pressure();
        row(h, reading.rows().get(1), Reading.Lead.GAS, "zas:hydrogen", kpa, 300, "the hydrogen outlet");
        h.assertTrue(reading.shares().isEmpty(), "the electrolyzer lists gases");
        s.clear();
        h.succeed();
    }

    // ---- COMPAT-031: tanks --------------------------------------------------------------------

    /** A tank holder reads the tank a click takes out, the last one put in, and nothing when empty. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_031_a_tank_holder_reads_the_tank_a_click_takes(GameTestHelper h) {
        Spot s = new Spot(h, 8);
        s.clear();
        TankHolderBlockEntity holder =
                s.place(1, 1, 1, AtmosBlocks.TANK_HOLDER.get().defaultBlockState(), TankHolderBlockEntity.class);
        BlockPos pos = holder.getBlockPos();
        h.assertTrue(JadeProbe.asks(s.level, pos), "a client wouldn't ask about a tank holder");
        h.assertTrue(JadeProbe.silent(s.level, pos), "an empty tank holder has a reading");

        Map<ResourceLocation, Double> oxygen = Map.of(Spot.O2, 5.0);
        Map<ResourceLocation, Double> air = Map.of(Spot.N2, 4.0, Spot.O2, 1.0);
        ItemStack first = tank(AtmosItems.OXYGEN_TANK.get(), oxygen);
        ItemStack second = tank(AtmosItems.AIR_TANK.get(), air);
        double firstKpa = GasTankItem.contents(first).pressure();
        double secondKpa = GasTankItem.contents(second).pressure();
        h.assertTrue(holder.put(first.copy()) && holder.put(second.copy()), "the holder refused a tank");

        Reading reading = JadeProbe.reading(s.level, pos);
        h.assertValueEqual(reading.rows().size(), 1, "the holder's rows with two tanks");
        row(h, reading.rows().get(0), Reading.Lead.ITEM, id(second), secondKpa, Spot.T, "the air tank");
        shares(h, reading, air, "the air tank");

        h.assertTrue(holder.takeLast().is(AtmosItems.AIR_TANK.get()), "a click didn't take the air tank");
        reading = JadeProbe.reading(s.level, pos);
        row(h, reading.rows().get(0), Reading.Lead.ITEM, id(first), firstKpa, Spot.T, "the oxygen tank");
        shares(h, reading, oxygen, "the oxygen tank");

        holder.takeLast();
        h.assertTrue(JadeProbe.silent(s.level, pos), "the emptied tank holder has a reading");
        s.clear();
        h.succeed();
    }

    /** A tank transfer valve reads each tank it holds, and lists the gases while it holds one. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_031_a_transfer_valve_reads_each_tank(GameTestHelper h) {
        Spot s = new Spot(h, 9);
        s.clear();
        TransferValveBlockEntity valve = s.place(
                1, 1, 1, AtmosBlocks.TANK_TRANSFER_VALVE.get().defaultBlockState(), TransferValveBlockEntity.class);
        BlockPos pos = valve.getBlockPos();
        h.assertTrue(JadeProbe.asks(s.level, pos), "a client wouldn't ask about a transfer valve");
        h.assertTrue(JadeProbe.silent(s.level, pos), "a transfer valve with no tank has a reading");

        Map<ResourceLocation, Double> oxygen = Map.of(Spot.O2, 5.0);
        Map<ResourceLocation, Double> hydrogen = Map.of(Spot.H2, 3.0);
        ItemStack first = tank(AtmosItems.OXYGEN_TANK.get(), oxygen);
        ItemStack second = tank(AtmosItems.EMPTY_TANK.get(), hydrogen);
        double firstKpa = GasTankItem.contents(first).pressure();
        double secondKpa = GasTankItem.contents(second).pressure();

        h.assertTrue(valve.fit(first.copy()), "the valve refused its first tank");
        Reading reading = JadeProbe.reading(s.level, pos);
        h.assertValueEqual(reading.rows().size(), 1, "the valve's rows with one tank");
        row(h, reading.rows().get(0), Reading.Lead.ITEM, id(first), firstKpa, Spot.T, "the valve's first tank");
        shares(h, reading, oxygen, "the valve's lone tank");

        h.assertTrue(valve.fit(second.copy()), "the valve refused its second tank");
        reading = JadeProbe.reading(s.level, pos);
        h.assertValueEqual(reading.rows().size(), 2, "the valve's rows with two tanks");
        row(h, reading.rows().get(0), Reading.Lead.ITEM, id(first), firstKpa, Spot.T, "the valve's first tank");
        row(h, reading.rows().get(1), Reading.Lead.ITEM, id(second), secondKpa, Spot.T, "the valve's second tank");
        h.assertTrue(reading.shares().isEmpty(), "a valve with two tanks lists gases");
        s.clear();
        h.succeed();
    }

    // ---- COMPAT-032: devices only --------------------------------------------------------------

    /** Jade holds no part of the adapter for plain air, a plain block or another mod's block entity. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_032_plain_air_and_other_blocks_get_no_reading(GameTestHelper h) {
        Spot s = new Spot(h, 10);
        s.clear();
        s.set(1, 1, 1, Blocks.STONE.defaultBlockState());
        s.set(3, 1, 1, Blocks.CHEST.defaultBlockState());
        s.set(5, 1, 1, Blocks.FURNACE.defaultBlockState());
        s.set(7, 1, 1, AtmosBlocks.STATION_WALL.get().defaultBlockState());
        s.set(9, 1, 1, AtmosBlocks.REINFORCED_GLASS_BLOCK.get().defaultBlockState());
        for (int x : new int[] {0, 1, 3, 5, 7, 9}) {
            BlockPos pos = s.abs(x, 1, 1);
            String what = s.level.getBlockState(pos).getBlock().getName().getString();
            BlockEntity be = s.level.getBlockEntity(pos);
            h.assertFalse(JadeProbe.registered(s.level.getBlockState(pos), be), "Jade holds the readout for " + what);
            h.assertFalse(JadeProbe.asks(s.level, pos), "a client would ask about " + what);
            h.assertTrue(JadeProbe.silent(s.level, pos), "the server sends a reading for " + what);
            h.assertFalse(DeviceReadings.reads(be), what + " counts as a device with a reading");
            h.assertTrue(DeviceReadings.of(be) == null, what + " has a reading");
        }
        s.clear();
        h.succeed();
    }

    /**
     * The devices that hold no gas and read no room have nothing to show: a client asks nothing about
     * them, and the server would answer nothing if it did.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_032_devices_with_nothing_to_read_send_nothing(GameTestHelper h) {
        Spot s = new Spot(h, 11);
        s.clear();
        List<Block> blocks = List.of(
                AtmosBlocks.CENTRAL_ATMOS_CONTROLLER.get(),
                AtmosBlocks.GENERATOR.get(),
                AtmosBlocks.RECHARGER.get(),
                AtmosBlocks.IGNITER.get(),
                AtmosBlocks.FIRELOCK_DOOR.get());
        for (int i = 0; i < blocks.size(); i++) {
            s.set(2 * i, 1, 1, blocks.get(i).defaultBlockState());
            BlockPos pos = s.abs(2 * i, 1, 1);
            String what = "the " + blocks.get(i).getName().getString();
            h.assertTrue(s.level.getBlockEntity(pos) != null, what + " has no block entity");
            h.assertFalse(JadeProbe.asks(s.level, pos), "a client would ask about " + what);
            h.assertTrue(JadeProbe.silent(s.level, pos), "the server sends a reading for " + what);
        }
        s.clear();
        h.succeed();
    }
}
