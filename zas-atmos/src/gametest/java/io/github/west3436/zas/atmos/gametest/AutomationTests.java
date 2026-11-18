package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.STONE;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.device.Comparators;
import io.github.west3436.zas.atmos.device.RedstoneControl;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlockEntity;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerMath;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gauge.GaugeBlock;
import io.github.west3436.zas.atmos.gauge.GaugeBlockEntity;
import io.github.west3436.zas.atmos.gauge.GaugeReading;
import io.github.west3436.zas.atmos.heater.HeaterBlockEntity;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.portable.PortablePumpBlockEntity;
import io.github.west3436.zas.atmos.power.GeneratorBlock;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.scrubber.ScrubberMath;
import io.github.west3436.zas.atmos.tank.LargeTankBlockEntity;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMenu;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.atmos.vent.VentSettings;
import io.github.west3436.zas.atmos.vent.VentStatus;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * R2's automation (CONT-170 to CONT-178): a device's redstone setting, which only ever stops it; what
 * a comparator reads from the containers, on one scale; and the gauge and the air sensor as sensors.
 * Each test builds in its own {@link Area}, 5800 on, and steps the devices itself within one game
 * tick, but for the one that waits for a real comparator.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AutomationTests {
    private static final String BATCH = "zas_atmos_automation";
    private static final BlockState SIGNAL = Blocks.REDSTONE_BLOCK.defaultBlockState();

    private AutomationTests() {}

    // ---- Helpers ----------------------------------------------------------------------------------

    /** What a comparator reads from the block at a place. */
    private static int comparator(Area a, int x, int y, int z) {
        return a.get(x, y, z).getAnalogOutputSignal(a.level, a.abs(x, y, z));
    }

    /** Empties a gas store and fills it to a pressure with nitrogen. */
    private static void fillTo(GasStore gas, double kPa) {
        gas.drain();
        if (kPa > 0) {
            gas.add(new GasParcel(Map.of(N2, kPa * gas.volume() / (GasMixture.GAS_CONSTANT * T)), T));
        }
    }

    /** A vent facing north in the open, fed by a pipe and a cap to its south, powered, with nitrogen in its network. */
    private static VentBlockEntity openVent(Area a) {
        a.clear(0, 0, 6, 6);
        a.place(2, 1, 2, AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.NORTH));
        a.place(2, 1, 3, AtmosBlocks.PIPE.get().defaultBlockState());
        a.place(2, 1, 4, AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.NORTH));
        a.tick();
        VentBlockEntity vent = a.entity(2, 1, 2);
        vent.power().setTestPowered(true);
        vent.network().gas().add(new GasParcel(Map.of(N2, 100.0), T));
        return vent;
    }

    /** Steps enough for a vent in the open to move gas, and returns whether its network lost any. */
    private static boolean vents(Area a, VentBlockEntity vent) {
        PipeNetwork net = vent.network();
        double before = net.gas().totalMoles();
        for (int i = 0; i < CellWatch.SETTLE_STEPS + 3; i++) {
            a.step();
        }
        return net.gas().totalMoles() < before - 1e-9;
    }

    // ---- The redstone setting ---------------------------------------------------------------------

    /**
     * CONT-170, CONT-171: a pressure pump's redstone setting. Ignored, the default, it runs with a
     * signal or none and reads none; high, only with one; low, only without. Held, it shows off and
     * draws nothing. Its window's buttons set it, and its data carries the mode and the signal.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_170_each_redstone_mode_on_a_pump(GameTestHelper h) {
        Area a = new Area(h, 5800);
        a.clear(0, 0, 6, 6);
        a.place(2, 1, 2, AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState());
        PressurePumpBlockEntity pump = a.entity(2, 1, 2);
        pump.power().setTestPowered(true);
        pump.setOn(true);
        int draw = AtmosServerConfig.get(AtmosServerConfig.PRESSURE_PUMP_DRAW);
        h.assertTrue(draw > 0, "a pressure pump draws nothing");

        h.assertValueEqual(pump.redstone().mode(), RedstoneControl.Mode.IGNORED, "a new pump's mode");
        a.step();
        h.assertTrue(pump.running(), "an ignoring pump with no signal doesn't run");
        a.set(2, 2, 2, SIGNAL);
        a.step();
        h.assertTrue(pump.running(), "an ignoring pump with a signal doesn't run");
        h.assertFalse(pump.redstone().signal(), "an ignoring pump read its signal");
        h.assertValueEqual(pump.power().draw(), draw, "an ignoring pump's draw");

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        InlineDeviceMenu window = (InlineDeviceMenu) pump.createMenu(1, player.getInventory(), player);
        h.assertTrue(
                window.clickMenuButton(player, RedstoneControl.FIRST_BUTTON + RedstoneControl.Mode.HIGH.ordinal()),
                "the window's high button did nothing");
        h.assertValueEqual(pump.redstone().mode(), RedstoneControl.Mode.HIGH, "the mode its window set");
        a.step();
        h.assertTrue(pump.running(), "a high pump with a signal doesn't run");
        h.assertValueEqual(RedstoneControl.mode(window.redstone()), RedstoneControl.Mode.HIGH, "the window's mode");
        h.assertTrue(RedstoneControl.signal(window.redstone()), "the window shows no signal");
        h.assertFalse(RedstoneControl.holds(window.redstone()), "the window shows it held");

        a.set(2, 2, 2, Area.AIR);
        a.step();
        h.assertFalse(pump.running(), "a high pump with no signal runs");
        h.assertValueEqual(pump.status(), InlineStatus.OFF, "a held pump's status");
        h.assertFalse(a.get(2, 1, 2).getValue(InlineDeviceBlock.ON), "a held pump shows running");
        h.assertValueEqual(pump.power().draw(), 0, "a held pump's draw");
        h.assertTrue(pump.on(), "redstone switched the pump off");
        h.assertTrue(RedstoneControl.holds(window.redstone()), "the window doesn't show it held");

        h.assertTrue(
                window.clickMenuButton(player, RedstoneControl.FIRST_BUTTON + RedstoneControl.Mode.LOW.ordinal()),
                "the window's low button did nothing");
        a.step();
        h.assertTrue(pump.running(), "a low pump with no signal doesn't run");
        a.set(2, 2, 2, SIGNAL);
        a.step();
        h.assertFalse(pump.running(), "a low pump with a signal runs");
        h.assertValueEqual(pump.power().draw(), 0, "a held pump's draw");
        h.assertFalse(
                window.clickMenuButton(player, RedstoneControl.LAST_BUTTON + 1),
                "a button past the modes did something");
        h.assertValueEqual(pump.redstone().mode(), RedstoneControl.Mode.LOW, "the mode after a button past them");
        h.succeed();
    }

    /**
     * CONT-170: a vent's redstone setting, by the gas it moves. Ignoring, it vents with a signal;
     * high, it holds without one and vents with one; low, the other way round. Held, it moves
     * nothing and draws nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_170_each_redstone_mode_on_a_vent(GameTestHelper h) {
        Area a = new Area(h, 5801);
        VentBlockEntity vent = openVent(a);
        vent.setByHand(VentSettings.blow(500));
        a.set(1, 1, 2, SIGNAL);
        h.assertTrue(vents(a, vent), "an ignoring vent with a signal moved nothing");

        vent.redstone().set(RedstoneControl.Mode.HIGH);
        h.assertTrue(vents(a, vent), "a high vent with a signal moved nothing");
        a.set(1, 1, 2, Area.AIR);
        h.assertFalse(vents(a, vent), "a high vent with no signal moved gas");
        h.assertFalse(vent.running(), "a held vent runs");
        h.assertValueEqual(vent.status(), VentStatus.OFF, "a held vent's status");
        h.assertValueEqual(vent.power().draw(), 0, "a held vent's draw");
        h.assertValueEqual(vent.settings().mode(), VentMode.BLOW, "redstone changed the vent's mode");

        vent.redstone().set(RedstoneControl.Mode.LOW);
        h.assertTrue(vents(a, vent), "a low vent with no signal moved nothing");
        a.set(1, 1, 2, SIGNAL);
        h.assertFalse(vents(a, vent), "a low vent with a signal moved gas");
        vent.redstone().set(RedstoneControl.Mode.IGNORED);
        h.assertTrue(vents(a, vent), "a vent set back to ignoring moved nothing");
        h.succeed();
    }

    /**
     * CONT-172: redstone only ever stops a device. A controller's orders and a held vent meet as an
     * AND: the orders stand, the vent holds, and it runs them once the signal lets it. Its window
     * sets the redstone mode under the orders, which it refuses for its own settings. A switched-off
     * pump with the signal it waits for stays off.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_172_redstone_only_stops_a_device(GameTestHelper h) {
        Area a = new Area(h, 5802);
        VentBlockEntity vent = openVent(a);
        BlockPos controller = a.abs(5, 1, 5);
        vent.redstone().set(RedstoneControl.Mode.HIGH);
        vent.control(controller, VentSettings.blow(500));
        h.assertFalse(vents(a, vent), "a held vent ran its controller's orders");
        h.assertTrue(vent.controlled(), "redstone ended the controller's orders");
        h.assertValueEqual(vent.settings().mode(), VentMode.BLOW, "the ordered mode");

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VentMenu window = (VentMenu) vent.createMenu(1, player.getInventory(), player);
        window.clickMenuButton(player, VentMenu.SET_MODE + VentMode.OFF.ordinal());
        h.assertValueEqual(vent.settings().mode(), VentMode.BLOW, "a hand changed an ordered vent's mode");
        h.assertTrue(
                window.clickMenuButton(player, RedstoneControl.FIRST_BUTTON + RedstoneControl.Mode.IGNORED.ordinal()),
                "an ordered vent's window refused its redstone mode");
        h.assertTrue(vents(a, vent), "an ordered vent let go by redstone moved nothing");

        a.place(2, 1, 0, AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState());
        PressurePumpBlockEntity pump = a.entity(2, 1, 0);
        pump.power().setTestPowered(true);
        pump.redstone().set(RedstoneControl.Mode.HIGH);
        a.set(3, 1, 0, SIGNAL);
        a.step();
        h.assertFalse(pump.on() || pump.running(), "a signal switched a pump on");
        h.assertValueEqual(pump.power().draw(), 0, "a switched-off pump's draw");
        h.succeed();
    }

    /**
     * CONT-173: a setting at ignored saves nothing, so a device saved before it existed, or never
     * set, loads as ignoring; any other mode saves as its word and loads back.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_173_the_redstone_setting_saves_only_when_set(GameTestHelper h) {
        Area a = new Area(h, 5803);
        a.clear(0, 0, 6, 6);
        a.place(2, 1, 2, AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState());
        PressurePumpBlockEntity pump = a.entity(2, 1, 2);
        CompoundTag plain = pump.saveWithoutMetadata(a.level.registryAccess());
        h.assertFalse(plain.contains(RedstoneControl.KEY), "an ignoring pump saved a redstone mode");
        RedstoneControl loaded = new RedstoneControl(pump);
        loaded.load(plain);
        h.assertValueEqual(loaded.mode(), RedstoneControl.Mode.IGNORED, "a pump saved with none");

        pump.redstone().set(RedstoneControl.Mode.LOW);
        CompoundTag set = pump.saveWithoutMetadata(a.level.registryAccess());
        h.assertValueEqual(set.getString(RedstoneControl.KEY), "low", "the saved mode");
        loaded.load(set);
        h.assertValueEqual(loaded.mode(), RedstoneControl.Mode.LOW, "the mode loaded back");
        CompoundTag odd = new CompoundTag();
        odd.putString(RedstoneControl.KEY, "sideways");
        loaded.load(odd);
        h.assertValueEqual(loaded.mode(), RedstoneControl.Mode.IGNORED, "a mode there isn't, loaded");
        h.succeed();
    }

    /**
     * CONT-173: a generator held by redstone stops burning without losing what it was burning: its
     * burn, its fuel and its FE wait, and it goes on once let.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_173_a_held_generator_keeps_its_burn(GameTestHelper h) {
        Area a = new Area(h, 5804);
        a.clear(0, 0, 6, 6);
        a.place(2, 1, 2, AtmosBlocks.GENERATOR.get().defaultBlockState());
        GeneratorBlockEntity generator = a.entity(2, 1, 2);
        generator.fuel().setStackInSlot(0, new ItemStack(Items.COAL, 2));
        generator.redstone().set(RedstoneControl.Mode.HIGH);
        a.set(2, 2, 2, SIGNAL);
        generator.serverTick();
        generator.serverTick();
        int left = generator.burnLeft();
        int energy = generator.energy().getEnergyStored();
        h.assertTrue(left > 0 && energy > 0, "a let generator isn't burning");
        h.assertTrue(a.get(2, 1, 2).getValue(GeneratorBlock.LIT), "a burning generator isn't lit");

        a.set(2, 2, 2, Area.AIR);
        for (int i = 0; i < 5; i++) {
            generator.serverTick();
        }
        h.assertValueEqual(generator.burnLeft(), left, "a held generator's burn");
        h.assertValueEqual(generator.energy().getEnergyStored(), energy, "a held generator's FE");
        h.assertValueEqual(generator.fuel().getStackInSlot(0).getCount(), 1, "a held generator's fuel");
        h.assertFalse(a.get(2, 1, 2).getValue(GeneratorBlock.LIT), "a held generator is lit");

        a.set(2, 2, 2, SIGNAL);
        generator.serverTick();
        h.assertValueEqual(generator.burnLeft(), left - 1, "a generator let go");
        h.succeed();
    }

    // ---- Comparators ------------------------------------------------------------------------------

    /**
     * CONT-174: a canister and a large tank, from either half, read their pressure over their rating:
     * 0 empty, 1 for a breath of gas, 8 at half, 14 at 14/15, and 15 just short of the rating.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_174_a_canister_and_a_large_tank_read_their_pressure_over_their_rating(GameTestHelper h) {
        Area a = new Area(h, 5805);
        a.clear(0, 0, 8, 6);
        a.place(2, 1, 2, AtmosBlocks.CANISTER.get().defaultBlockState());
        CanisterBlockEntity canister = a.entity(2, 1, 2);
        LargeTankBlockEntity tank = LargeTankTests.stand(a, 5, 1, 2);
        double rating = ContainerFailure.CANISTER_RATED_KPA;
        double[] kpa = {0, 1, rating / 2, rating * 14 / 15 * (1 - 1e-6), rating * 0.99};
        int[] signal = {0, 1, 8, 14, 15};
        h.assertTrue(a.get(2, 1, 2).hasAnalogOutputSignal(), "a canister gives comparators nothing");
        h.assertTrue(a.get(5, 2, 2).hasAnalogOutputSignal(), "a tank's upper half gives comparators nothing");
        for (int i = 0; i < kpa.length; i++) {
            fillTo(canister.network().gas(), kpa[i]);
            fillTo(tank.network().gas(), kpa[i]);
            h.assertValueEqual(comparator(a, 2, 1, 2), signal[i], "the canister at " + kpa[i] + " kPa");
            h.assertValueEqual(comparator(a, 5, 1, 2), signal[i], "the tank's lower half at " + kpa[i] + " kPa");
            h.assertValueEqual(comparator(a, 5, 2, 2), signal[i], "the tank's upper half at " + kpa[i] + " kPa");
        }
        h.succeed();
    }

    /**
     * CONT-174: a real comparator behind a canister follows its pressure once the canister steps,
     * which tells the comparators around it when its signal changes.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_174_a_comparator_follows_a_canister(GameTestHelper h) {
        Area a = new Area(h, 5806);
        a.forceLoad(true);
        a.clear(0, 0, 6, 6);
        a.place(2, 1, 2, AtmosBlocks.CANISTER.get().defaultBlockState());
        CanisterBlockEntity canister = a.entity(2, 1, 2);
        a.set(3, 0, 2, STONE);
        a.set(3, 1, 2, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
        h.startSequence()
                .thenExecute(() -> {
                    fillTo(canister.network().gas(), ContainerFailure.CANISTER_RATED_KPA / 2);
                    a.step();
                })
                .thenWaitUntil(() -> h.assertValueEqual(output(a), 8, "the comparator at half"))
                .thenExecute(() -> {
                    fillTo(canister.network().gas(), 0);
                    a.step();
                })
                .thenWaitUntil(() -> h.assertValueEqual(output(a), 0, "the comparator, empty"))
                .thenExecute(() -> {
                    a.clear(0, 0, 6, 6);
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    private static int output(Area a) {
        return a.level.getBlockEntity(a.abs(3, 1, 2)) instanceof ComparatorBlockEntity c ? c.getOutputSignal() : -1;
    }

    /**
     * CONT-175: the other containers read how full they are on the same scale: a tank holder its
     * tanks over its places, a recharger and a heater their battery, a portable pump its store over
     * 50 atm, an electrolyzer its water and a generator its FE.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_175_the_other_containers_read_how_full_they_are(GameTestHelper h) {
        Area a = new Area(h, 5807);
        a.clear(0, 0, 16, 6);

        a.place(1, 1, 2, AtmosBlocks.TANK_HOLDER.get().defaultBlockState());
        TankHolderBlockEntity holder = a.entity(1, 1, 2);
        h.assertValueEqual(comparator(a, 1, 1, 2), 0, "an empty tank holder");
        h.assertTrue(holder.put(new ItemStack(AtmosItems.OXYGEN_TANK.get())), "the holder took no tank");
        h.assertValueEqual(comparator(a, 1, 1, 2), 2, "a holder with one tank of ten");
        for (int i = 1; i < TankHolderBlockEntity.PLACES; i++) {
            holder.put(new ItemStack(AtmosItems.AIR_TANK.get()));
        }
        h.assertValueEqual(comparator(a, 1, 1, 2), 15, "a full holder");

        ItemStack battery = new ItemStack(AtmosItems.BATTERY.get());
        int capacity = Batteries.energy(battery).getMaxEnergyStored();
        Batteries.energy(battery).receiveEnergy(capacity / 2, false);
        int half = Comparators.fill((double) (capacity / 2) / capacity);
        h.assertValueEqual(half, 8, "half a battery");
        a.place(3, 1, 2, AtmosBlocks.RECHARGER.get().defaultBlockState());
        RechargerBlockEntity recharger = a.entity(3, 1, 2);
        h.assertValueEqual(comparator(a, 3, 1, 2), 0, "an empty recharger");
        recharger.battery().setStackInSlot(0, battery.copy());
        h.assertValueEqual(comparator(a, 3, 1, 2), half, "a recharger with half a battery");

        a.place(5, 1, 2, AtmosBlocks.HEATER.get().defaultBlockState());
        HeaterBlockEntity heater = a.entity(5, 1, 2);
        h.assertValueEqual(comparator(a, 5, 1, 2), 0, "a heater with no battery");
        heater.battery().setStackInSlot(0, battery.copy());
        h.assertValueEqual(comparator(a, 5, 1, 2), half, "a heater with half a battery");

        a.place(7, 1, 2, AtmosBlocks.PORTABLE_PUMP.get().defaultBlockState());
        PortablePumpBlockEntity pump = a.entity(7, 1, 2);
        h.assertValueEqual(comparator(a, 7, 1, 2), 0, "an empty portable pump");
        fillTo(pump.store(), ScrubberMath.STALL_KPA / 2);
        h.assertValueEqual(comparator(a, 7, 1, 2), 8, "a portable pump at 25 atm");
        fillTo(pump.store(), ScrubberMath.STALL_KPA * 0.99);
        h.assertValueEqual(comparator(a, 7, 1, 2), 15, "a portable pump near 50 atm");

        a.place(9, 1, 2, AtmosBlocks.ELECTROLYZER.get().defaultBlockState());
        ElectrolyzerBlockEntity electrolyzer = a.entity(9, 1, 2);
        h.assertValueEqual(comparator(a, 9, 1, 2), 0, "a dry electrolyzer");
        electrolyzer
                .tank()
                .fill(new FluidStack(Fluids.WATER, ElectrolyzerMath.TANK_MB / 2), IFluidHandler.FluidAction.EXECUTE);
        h.assertValueEqual(comparator(a, 9, 1, 2), 8, "an electrolyzer half full of water");

        a.place(11, 1, 2, AtmosBlocks.GENERATOR.get().defaultBlockState());
        GeneratorBlockEntity generator = a.entity(11, 1, 2);
        h.assertValueEqual(comparator(a, 11, 1, 2), 0, "a generator with no FE");
        generator.energy().set(generator.energy().getMaxEnergyStored());
        h.assertValueEqual(comparator(a, 11, 1, 2), 15, "a full generator");
        h.succeed();
    }

    // ---- The gauge and the air sensor -------------------------------------------------------------

    /**
     * CONT-176, CONT-178: a gauge gives comparators where its needle stands, ⌊15 × along + ½⌋; turned
     * to another reading, it reads at once.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_176_a_gauge_reads_where_its_needle_stands(GameTestHelper h) {
        Area a = new Area(h, 5808);
        a.clear(0, 0, 6, 6);
        a.place(2, 1, 1, AtmosBlocks.CANISTER.get().defaultBlockState());
        CanisterBlockEntity canister = a.entity(2, 1, 1);
        a.place(
                2,
                1,
                2,
                AtmosBlocks.GAUGE
                        .get()
                        .defaultBlockState()
                        .setValue(GaugeBlock.FACE, AttachFace.WALL)
                        .setValue(GaugeBlock.FACING, Direction.SOUTH));
        GaugeBlockEntity gauge = a.entity(2, 1, 2);
        fillTo(canister.network().gas(), ContainerFailure.CANISTER_RATED_KPA / 2);
        gauge.step();
        // On a canister, half its rating is the end of the green band, 0.4 along.
        h.assertValueEqual(gauge.signal(), Comparators.dial(0.4), "the gauge's signal");
        h.assertValueEqual(comparator(a, 2, 1, 2), 6, "a comparator behind a gauge at half the rating");

        gauge.setReading(GaugeReading.OXYGEN);
        h.assertValueEqual(comparator(a, 2, 1, 2), 0, "the oxygen of pure nitrogen, at once");
        canister.network().gas().drain();
        canister.network().gas().add(new GasParcel(Map.of(N2, 10.0, O2, 10.0), T));
        gauge.step();
        h.assertValueEqual(comparator(a, 2, 1, 2), 8, "half oxygen");
        h.succeed();
    }

    /**
     * CONT-177: an air sensor no controller links reads the room in front of its wall for a
     * comparator: its pressure from none to 2 atm, its temperature to 1,000 K, a gas's share. A
     * sneaking use with an empty hand turns it to its next reading; a plain use doesn't. Its reading
     * is saved.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_177_an_air_sensor_reads_its_room_for_a_comparator(GameTestHelper h) {
        Area a = new Area(h, 5809);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 80.0, O2, 20.0));
        a.place(
                2,
                2,
                1,
                AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.SOUTH));
        AirSensorBlockEntity sensor = a.entity(2, 2, 1);
        h.assertValueEqual(comparator(a, 2, 2, 1), 0, "a sensor before its first step");
        a.step();
        double kpa = sensor.pressure();
        h.assertTrue(kpa > 0, "the sensor read no pressure");
        int pressure = Comparators.dial(kpa / GaugeReading.ROOM_TOP_KPA);
        h.assertTrue(pressure > 0, "the room's pressure reads nothing");
        h.assertValueEqual(comparator(a, 2, 2, 1), pressure, "the room's pressure");
        h.assertValueEqual(
                Comparators.dial(101.325 / GaugeReading.ROOM_TOP_KPA), 8, "one atmosphere on the sensor's scale");

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = a.abs(2, 2, 1);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
        a.get(2, 2, 1).useWithoutItem(a.level, player, hit);
        h.assertValueEqual(sensor.reading(), GaugeReading.PRESSURE, "a plain use turned the sensor");
        player.setShiftKeyDown(true);
        a.get(2, 2, 1).useWithoutItem(a.level, player, hit);
        h.assertValueEqual(sensor.reading(), GaugeReading.TEMPERATURE, "a sneaking use's reading");
        h.assertValueEqual(comparator(a, 2, 2, 1), Comparators.dial(T / GaugeReading.TOP_KELVIN), "its temperature");
        a.get(2, 2, 1).useWithoutItem(a.level, player, hit);
        h.assertValueEqual(sensor.reading(), GaugeReading.OXYGEN, "the next sneaking use's reading");
        double oxygen = sensor.value();
        h.assertTrue(oxygen > 0.19 && oxygen < 0.23, "the room's oxygen share: " + oxygen);
        h.assertValueEqual(comparator(a, 2, 2, 1), Comparators.dial(oxygen), "its oxygen's share");

        CompoundTag saved = sensor.saveWithoutMetadata(a.level.registryAccess());
        h.assertValueEqual(saved.getString("Reading"), "oxygen", "the saved reading");
        sensor.setReading(GaugeReading.PRESSURE);
        h.assertFalse(
                sensor.saveWithoutMetadata(a.level.registryAccess()).contains("Reading"),
                "a sensor reading pressure saved its reading");
        sensor.loadWithComponents(saved, a.level.registryAccess());
        h.assertValueEqual(sensor.reading(), GaugeReading.OXYGEN, "the reading loaded back");
        h.succeed();
    }
}
