package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.STONE;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterMenu;
import io.github.west3436.zas.atmos.controller.VentOrder;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceActivity;
import io.github.west3436.zas.atmos.device.DeviceActivityPayload;
import io.github.west3436.zas.atmos.device.DeviceActivitySync;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
import io.github.west3436.zas.atmos.mixer.MixerBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.pump.VolumePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.valve.ValveBlock;
import io.github.west3436.zas.atmos.vent.PassiveVentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentSettings;
import io.github.west3436.zas.gametest.TestPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The sounds of pipe devices, as far as a server can tell: which devices read as moving gas, so that
 * a client plays their loops (CONT-125), and what each one plays when it changes (CONT-126). Each
 * builds in its own {@link Area} and runs within one game tick. What these sound like, and that a
 * client plays a loop from the level, can't be checked here.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeDeviceSoundTests {
    private static final String BATCH = "zas_atmos_pipe_device_sounds";
    private static final BlockState REDSTONE = Blocks.REDSTONE_BLOCK.defaultBlockState();

    static {
        SoundLog.listen();
    }

    private PipeDeviceSoundTests() {}

    /** A device's level now: what a new player beside it would be told, and idle if it isn't told. */
    private static int level(Area a, BlockPos pos) {
        FakePlayer p = TestPlayers.hurtable(a.level, "zas_device_sounds");
        p.moveTo(Vec3.atCenterOf(pos));
        List<DeviceActivityPayload> sent = new ArrayList<>();
        DeviceActivitySync.syncAt(p, AtmosTicks.now(a.level), sent::add);
        int level = DeviceActivity.IDLE;
        for (DeviceActivityPayload payload : sent) {
            for (DeviceActivityPayload.Change change : payload.changes()) {
                if (change.pos() == pos.asLong()) {
                    level = change.level();
                }
            }
        }
        return level;
    }

    private static void reads(GameTestHelper h, Area a, BlockPos pos, int expected, String what) {
        h.assertValueEqual(level(a, pos), expected, what);
    }

    /** The one sound a block has played since it was last asked. */
    private static void plays(
            GameTestHelper h, BlockPos pos, DeferredHolder<SoundEvent, SoundEvent> event, String what) {
        List<SoundLog.Played> played = SoundLog.take(Vec3.atCenterOf(pos));
        h.assertValueEqual(played.size(), 1, "sounds from " + what);
        h.assertTrue(played.get(0).is(event), what + " played " + played.get(0));
    }

    private static void silent(GameTestHelper h, BlockPos pos, String what) {
        h.assertValueEqual(SoundLog.take(Vec3.atCenterOf(pos)), List.of(), "sounds from " + what);
    }

    /** An inline device at (2, 2, 2) facing east, on test power, with a capped run on each port. */
    private static <T extends InlineDeviceEntity> T inline(Area a, Block block) {
        a.clear(-1, 0, 6, 5);
        PipeTests.cap(a, 0, 2, 2, Direction.EAST);
        PipeTests.pipe(a, 1, 2, 2);
        a.place(2, 2, 2, block.defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        PipeTests.pipe(a, 3, 2, 2);
        PipeTests.cap(a, 4, 2, 2, Direction.WEST);
        T device = a.entity(2, 2, 2);
        if (device instanceof SidePortEntity) {
            PipeTests.cap(a, 2, 1, 3, Direction.UP);
            PipeTests.pipe(a, 2, 2, 3);
        }
        device.power().setTestPowered(true);
        return device;
    }

    private static void add(GasStore gas, double moles) {
        gas.add(new GasParcel(Map.of(N2, moles), T));
    }

    private static void use(Area a, BlockPos pos, Player player) {
        a.level
                .getBlockState(pos)
                .useWithoutItem(a.level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    // ---- CONT-125: what reads as moving gas --------------------------------------------------------

    /**
     * A pressure pump reads by the moles it moves, not by being on or powered. A run of 340 L, so 100
     * L/s is 0.29 of the input a step. Off or without power it reads idle at once, whatever it holds.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_125_a_pressure_pump_reads_by_the_gas_it_moves(GameTestHelper h) {
        Area a = new Area(h, 900);
        PressurePumpBlockEntity pump = inline(a, AtmosBlocks.PRESSURE_PUMP.get());
        BlockPos pos = pump.getBlockPos();
        GasStore in = pump.input().network().gas();
        pump.set(4_500);
        add(in, 300);

        pump.step();
        reads(h, a, pos, DeviceActivity.IDLE, "off, with gas and power");

        pump.setOn(true);
        pump.step();
        h.assertValueEqual(pump.status(), InlineStatus.MOVING, "the pump's status");
        reads(h, a, pos, DeviceActivity.HARD, "moving 88 mol");

        pump.setOn(false);
        pump.step();
        reads(h, a, pos, DeviceActivity.IDLE, "switched off, the step it stops");

        pump.power().setTestPowered(false);
        pump.setOn(true);
        pump.step();
        reads(h, a, pos, DeviceActivity.IDLE, "on with no power");

        pump.power().setTestPowered(true);
        pump.step();
        reads(h, a, pos, DeviceActivity.HARD, "powered again");

        // On and powered, with nothing it may move: at its target.
        pump.set(0);
        pump.step();
        pump.step();
        h.assertValueEqual(pump.status(), InlineStatus.TARGET_REACHED, "the pump's status at its target");
        reads(h, a, pos, DeviceActivity.IDLE, "at its target");

        // The level follows the moles.
        pump.set(4_500);
        in.drain();
        add(in, 0.5);
        pump.step();
        reads(h, a, pos, DeviceActivity.TRICKLE, "moving 0.15 mol");
        add(in, 10);
        pump.step();
        reads(h, a, pos, DeviceActivity.WORKING, "moving 3 mol");

        // On and powered, with nothing to move: its input is empty.
        in.drain();
        pump.step();
        pump.step();
        h.assertValueEqual(pump.status(), InlineStatus.INPUT_EMPTY, "the pump's status with no input");
        reads(h, a, pos, DeviceActivity.IDLE, "with no input");
        h.succeed();
    }

    /** A volume pump set to no rate moves nothing, on and powered, and reads idle. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_125_a_volume_pump_reads_by_the_gas_it_moves(GameTestHelper h) {
        Area a = new Area(h, 901);
        VolumePumpBlockEntity pump = inline(a, AtmosBlocks.VOLUME_PUMP.get());
        BlockPos pos = pump.getBlockPos();
        add(pump.input().network().gas(), 300);
        pump.setOn(true);
        pump.set(0);
        pump.step();
        h.assertValueEqual(pump.status(), InlineStatus.RATE_ZERO, "the pump's status");
        reads(h, a, pos, DeviceActivity.IDLE, "on at no rate");
        pump.set(100);
        pump.step();
        reads(h, a, pos, DeviceActivity.HARD, "moving 88 mol");
        pump.setOn(false);
        pump.step();
        reads(h, a, pos, DeviceActivity.IDLE, "switched off");
        h.succeed();
    }

    /** A filter and a mixer read by what they move, and idle at their target or with nothing to move. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_125_a_filter_and_a_mixer_read_by_the_gas_they_move(GameTestHelper h) {
        Area fa = new Area(h, 902);
        FilterBlockEntity filter = inline(fa, AtmosBlocks.FILTER.get());
        BlockPos fp = filter.getBlockPos();
        filter.setOn(true);
        filter.setTarget(4_500);
        filter.step();
        reads(h, fa, fp, DeviceActivity.IDLE, "a filter with nothing to move");
        add(filter.port(SidePortEntity.BACK).network().gas(), 300);
        filter.step();
        h.assertValueEqual(filter.status(), InlineStatus.MOVING, "the filter's status");
        reads(h, fa, fp, DeviceActivity.HARD, "a filter moving 88 mol");
        filter.setTarget(0);
        filter.step();
        filter.step();
        reads(h, fa, fp, DeviceActivity.IDLE, "a filter at its target");
        filter.setTarget(4_500);
        filter.step();
        filter.setOn(false);
        filter.step();
        reads(h, fa, fp, DeviceActivity.IDLE, "a filter switched off");

        Area ma = new Area(h, 903);
        MixerBlockEntity mixer = inline(ma, AtmosBlocks.MIXER.get());
        BlockPos mp = mixer.getBlockPos();
        mixer.setOn(true);
        mixer.setTarget(4_500);
        mixer.step();
        reads(h, ma, mp, DeviceActivity.IDLE, "a mixer with nothing to move");
        add(mixer.port(SidePortEntity.BACK).network().gas(), 300);
        add(mixer.port(SidePortEntity.SIDE).network().gas(), 300);
        mixer.step();
        h.assertValueEqual(mixer.status(), InlineStatus.MOVING, "the mixer's status");
        reads(h, ma, mp, DeviceActivity.HARD, "a mixer moving gas");
        mixer.setTarget(0);
        mixer.step();
        mixer.step();
        reads(h, ma, mp, DeviceActivity.IDLE, "a mixer at its target");
        mixer.setTarget(4_500);
        mixer.step();
        mixer.setOn(false);
        mixer.step();
        reads(h, ma, mp, DeviceActivity.IDLE, "a mixer switched off");
        h.succeed();
    }

    /** A passive vent reads by what it exchanges, and idle within its threshold. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_125_a_passive_vent_reads_by_the_gas_it_exchanges(GameTestHelper h) {
        Area a = new Area(h, 904);
        PassiveVentBlockEntity v = PassiveVentTests.rig(a);
        BlockPos pos = v.getBlockPos();
        double room = a.zone(2, 2, 2).mixture().pressure();
        PassiveVentTests.network(v, N2, room + PassiveVentBlockEntity.THRESHOLD_KPA / 2);
        for (int i = 0; i < PipeBlockEntity.SETTLE_STEPS + 2; i++) {
            a.step();
        }
        reads(h, a, pos, DeviceActivity.IDLE, "within its threshold");
        PassiveVentTests.network(v, N2, 300);
        a.step();
        reads(h, a, pos, DeviceActivity.HARD, "equalizing 300 kPa with a nearly empty room");
        h.succeed();
    }

    /** A canister reads by what it releases: nothing while shut, or once the room is at its release pressure. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_125_a_canister_reads_by_the_gas_it_releases(GameTestHelper h) {
        Area a = new Area(h, 905);
        GasDeviceTests.room(a);
        a.place(2, 1, 2, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 1, 2);
        BlockPos pos = can.getBlockPos();
        add(can.network().gas(), 1_500);
        can.setReleasePressure(120);
        for (int i = 0; i < PipeBlockEntity.SETTLE_STEPS + 2; i++) {
            a.step();
        }
        reads(h, a, pos, DeviceActivity.IDLE, "with its valve shut");
        can.setValveOpen(true);
        a.step(); // its block changed, so it doesn't trust its reading of the cell this step
        a.step();
        reads(h, a, pos, DeviceActivity.HARD, "filling the room");
        for (int i = 0; i < 16; i++) {
            a.step();
        }
        h.assertTrue(can.valveOpen(), "its valve shut by itself");
        h.assertTrue(
                Math.abs(a.zone(2, 2, 2).mixture().pressure() - 120) < 0.05, "the room isn't at the release pressure");
        reads(h, a, pos, DeviceActivity.IDLE, "with the room at its release pressure");
        h.succeed();
    }

    /**
     * An open pipe end reads by the gas pushed through it, so it hisses while it leaks and stops when
     * it has settled. Gases only passing each other at one pressure push nothing through. A pipe with
     * no open end reads idle at any pressure.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_125_an_open_pipe_end_reads_by_the_gas_pushed_through_it(GameTestHelper h) {
        Area a = new Area(h, 906);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 1_100.0));
        PipeTests.pipe(a, 2, 1, 2);
        a.tick();
        PipeBlockEntity pipe = a.entity(2, 1, 2);
        BlockPos pos = pipe.getBlockPos();
        h.assertTrue(pipe.isOpen(), "the lone pipe has no open end");
        for (int i = 0; i < PipeBlockEntity.SETTLE_STEPS; i++) {
            a.step();
        }

        // O2 and N2 at the room's pressure: both move, and nothing is pushed.
        GasStore gas = pipe.network().gas();
        gas.drain();
        double oxygen = a.zone(2, 2, 2).mixture().pressure() * gas.volume() / (GasMixture.GAS_CONSTANT * T);
        gas.add(new GasParcel(Map.of(O2, oxygen), T));
        a.step();
        gas = pipe.network().gas();
        h.assertTrue(gas.moles(O2) < oxygen - 0.05, "no O2 left the pipe");
        reads(h, a, pos, DeviceActivity.IDLE, "gases passing each other");

        // A lot more than the room: 16 mol a second.
        gas.drain();
        add(gas, 100);
        a.step();
        reads(h, a, pos, DeviceActivity.HARD, "leaking 100 mol");
        for (int i = 0; i < 60; i++) {
            a.step();
        }
        reads(h, a, pos, DeviceActivity.IDLE, "settled with the room");

        PipeTests.cap(a, 1, 3, 2, Direction.EAST);
        PipeTests.pipe(a, 2, 3, 2);
        PipeTests.cap(a, 3, 3, 2, Direction.WEST);
        PipeBlockEntity capped = a.entity(2, 3, 2);
        h.assertFalse(capped.isOpen(), "the capped pipe has an open end");
        add(capped.network().gas(), 100);
        a.step();
        reads(h, a, capped.getBlockPos(), DeviceActivity.IDLE, "with no open end");
        h.succeed();
    }

    // ---- CONT-126: what plays when a device changes ------------------------------------------------

    private static void switches(
            GameTestHelper h,
            Area a,
            Block block,
            DeferredHolder<SoundEvent, SoundEvent> on,
            DeferredHolder<SoundEvent, SoundEvent> off,
            String what) {
        InlineDeviceEntity d = inline(a, block);
        BlockPos pos = d.getBlockPos();
        silent(h, pos, what + " placed");
        d.setOn(true);
        plays(h, pos, on, what + " switched on");
        d.setOn(true);
        silent(h, pos, what + " switched on again");
        d.step();
        d.step();
        silent(h, pos, what + " stepping");
        d.setOn(false);
        plays(h, pos, off, what + " switched off");
        d.step();
        silent(h, pos, what + " stepping off");
    }

    /** Each inline device plays its own sound once per switch, and nothing as it steps. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_an_inline_device_plays_once_per_switch(GameTestHelper h) {
        switches(
                h,
                new Area(h, 907),
                AtmosBlocks.PRESSURE_PUMP.get(),
                AtmosSounds.PUMP_ON,
                AtmosSounds.PUMP_OFF,
                "a pressure pump");
        switches(
                h,
                new Area(h, 908),
                AtmosBlocks.VOLUME_PUMP.get(),
                AtmosSounds.VOLUME_PUMP_ON,
                AtmosSounds.VOLUME_PUMP_OFF,
                "a volume pump");
        switches(
                h,
                new Area(h, 909),
                AtmosBlocks.FILTER.get(),
                AtmosSounds.FILTER_ON,
                AtmosSounds.FILTER_OFF,
                "a filter");
        switches(h, new Area(h, 910), AtmosBlocks.MIXER.get(), AtmosSounds.MIXER_ON, AtmosSounds.MIXER_OFF, "a mixer");
        h.succeed();
    }

    /** Its GUI's switch is the same switch. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_the_gui_switch_plays_once(GameTestHelper h) {
        Area a = new Area(h, 911);
        PressurePumpBlockEntity pump = inline(a, AtmosBlocks.PRESSURE_PUMP.get());
        BlockPos pos = pump.getBlockPos();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = pump.createMenu(0, player.getInventory(), player);
        h.assertTrue(menu.clickMenuButton(player, InlineDeviceMenu.TOGGLE), "the switch was refused");
        plays(h, pos, AtmosSounds.PUMP_ON, "the pump switched on in its GUI");
        h.assertTrue(menu.clickMenuButton(player, InlineDeviceMenu.TOGGLE), "the switch was refused");
        plays(h, pos, AtmosSounds.PUMP_OFF, "the pump switched off in its GUI");
        h.succeed();
    }

    /**
     * The sound follows the switch and not the power, so a chunk loading, whose leases aren't saved,
     * makes no clicks, and nor does a blackout: the block state changes, and nothing plays.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_power_coming_and_going_plays_nothing(GameTestHelper h) {
        Area a = new Area(h, 912);
        PressurePumpBlockEntity pump = inline(a, AtmosBlocks.PRESSURE_PUMP.get());
        BlockPos pos = pump.getBlockPos();
        pump.setOn(true);
        pump.step();
        plays(h, pos, AtmosSounds.PUMP_ON, "the pump switched on");
        h.assertTrue(a.get(2, 2, 2).getValue(InlineDeviceBlock.ON), "the pump doesn't show it is running");

        pump.power().setTestPowered(false);
        pump.step();
        h.assertFalse(a.get(2, 2, 2).getValue(InlineDeviceBlock.ON), "the pump shows it is running with no power");
        silent(h, pos, "the pump losing power");

        pump.power().setTestPowered(true);
        pump.step();
        h.assertTrue(a.get(2, 2, 2).getValue(InlineDeviceBlock.ON), "the pump doesn't show it is running again");
        silent(h, pos, "the pump getting power back");

        CompoundTag saved = pump.saveWithoutMetadata(a.level.registryAccess());
        pump.loadWithComponents(saved, a.level.registryAccess());
        h.assertTrue(pump.on(), "the pump came back off");
        silent(h, pos, "the pump loading");
        h.succeed();
    }

    /** A vent plays from off to a mode and back, by hand or by a controller, and not between modes. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_a_vent_plays_when_it_goes_on_or_off(GameTestHelper h) {
        Area a = new Area(h, 913);
        a.clear(-1, 0, 4, 4);
        a.place(1, 2, 2, GasDeviceTests.vent(Direction.EAST));
        VentBlockEntity vent = a.entity(1, 2, 2);
        BlockPos pos = vent.getBlockPos();
        vent.command(VentSettings.blow(50));
        plays(h, pos, AtmosSounds.VENT_ON, "the vent going on");
        vent.command(VentSettings.blow(50));
        vent.command(VentSettings.blow(60));
        vent.command(VentSettings.siphon(0));
        silent(h, pos, "the vent changing its settings");
        vent.command(null);
        plays(h, pos, AtmosSounds.VENT_OFF, "the vent going off");

        vent.ventOrder(VentOrder.blow(100));
        plays(h, pos, AtmosSounds.VENT_ON, "a controller's order");
        vent.ventOrder(VentOrder.blow(100));
        silent(h, pos, "a controller's order, repeated");
        vent.ventOrder(VentOrder.OFF);
        plays(h, pos, AtmosSounds.VENT_OFF, "a controller's order to stop");
        vent.ventOrder(VentOrder.OFF);
        silent(h, pos, "a controller's order to stop, repeated");
        h.succeed();
    }

    /** A scrubber plays from off to a mode and back, and not between modes. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_a_scrubber_plays_when_it_goes_on_or_off(GameTestHelper h) {
        Area a = new Area(h, 914);
        a.clear(-1, 0, 4, 4);
        a.place(1, 2, 2, ScrubberTests.scrubber(Direction.EAST));
        ScrubberBlockEntity s = a.entity(1, 2, 2);
        BlockPos pos = s.getBlockPos();
        s.command(ScrubberMode.OFF, GasSelection.DEFAULT);
        silent(h, pos, "the scrubber told to stay off");
        s.command(ScrubberMode.SCRUB, GasSelection.DEFAULT);
        plays(h, pos, AtmosSounds.SCRUBBER_ON, "the scrubber going on");
        s.command(ScrubberMode.SCRUB, GasSelection.DEFAULT);
        s.command(ScrubberMode.PANIC, GasSelection.DEFAULT);
        s.command(ScrubberMode.SIPHON, GasSelection.DEFAULT);
        silent(h, pos, "the scrubber changing its mode");
        s.command(null, GasSelection.DEFAULT);
        plays(h, pos, AtmosSounds.SCRUBBER_OFF, "the scrubber going off");
        h.succeed();
    }

    /** A manual valve plays its own sound once per turn. Steps and a signal play nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_a_manual_valve_plays_once_per_turn(GameTestHelper h) {
        Area a = new Area(h, 915);
        a.clear(0, 0, 6, 4);
        a.place(3, 1, 1, AtmosBlocks.MANUAL_VALVE.get().defaultBlockState());
        BlockPos pos = a.abs(3, 1, 1);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        use(a, pos, player);
        h.assertTrue(a.get(3, 1, 1).getValue(ValveBlock.OPEN), "the valve didn't open");
        plays(h, pos, AtmosSounds.VALVE_OPEN, "the valve opening");
        a.step();
        silent(h, pos, "the valve joining its networks");
        use(a, pos, player);
        plays(h, pos, AtmosSounds.VALVE_CLOSE, "the valve shutting");
        a.set(3, 2, 1, REDSTONE);
        silent(h, pos, "the manual valve getting a signal");
        h.succeed();
    }

    /** A redstone valve plays once per change of position: a second signal or a hand plays nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_a_redstone_valve_plays_once_per_change(GameTestHelper h) {
        Area a = new Area(h, 916);
        a.clear(0, 0, 6, 4);
        a.place(3, 1, 1, AtmosBlocks.REDSTONE_VALVE.get().defaultBlockState());
        BlockPos pos = a.abs(3, 1, 1);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        use(a, pos, player);
        silent(h, pos, "a hand on a redstone valve");

        a.set(3, 2, 1, REDSTONE);
        plays(h, pos, AtmosSounds.REDSTONE_VALVE_OPEN, "the valve opening");
        a.set(3, 0, 1, REDSTONE);
        silent(h, pos, "a second signal");
        a.set(3, 0, 1, AIR);
        silent(h, pos, "losing the second signal");
        a.set(3, 2, 1, AIR);
        plays(h, pos, AtmosSounds.REDSTONE_VALVE_CLOSE, "the valve shutting");
        h.succeed();
    }

    /** A canister's valve plays once per turn, and its GUI's other buttons play nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_a_canister_valve_plays_once_per_turn(GameTestHelper h) {
        Area a = new Area(h, 917);
        a.clear(0, 0, 4, 4);
        a.place(2, 1, 2, GasDeviceTests.canister());
        CanisterBlockEntity can = a.entity(2, 1, 2);
        BlockPos pos = can.getBlockPos();
        can.setValveOpen(true);
        plays(h, pos, AtmosSounds.CANISTER_OPEN, "the canister opening");
        can.setValveOpen(true);
        silent(h, pos, "the canister told to open again");

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = can.createMenu(0, player.getInventory(), player);
        h.assertTrue(menu.clickMenuButton(player, CanisterMenu.RELEASE_UP_10), "the button was refused");
        silent(h, pos, "the canister's release pressure changing");
        h.assertTrue(menu.clickMenuButton(player, CanisterMenu.TOGGLE_VALVE), "the button was refused");
        plays(h, pos, AtmosSounds.CANISTER_CLOSE, "the canister shutting in its GUI");
        h.succeed();
    }

    /** A port plays as a canister comes and goes, at the port, and for nothing else above it. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_a_port_plays_as_a_canister_comes_and_goes(GameTestHelper h) {
        Area a = new Area(h, 918);
        a.clear(0, 0, 8, 4);
        a.place(2, 2, 2, GasDeviceTests.port());
        BlockPos pos = a.abs(2, 2, 2);
        silent(h, pos, "a port placed on its own");
        a.place(2, 3, 2, GasDeviceTests.canister());
        plays(h, pos, AtmosSounds.PORT_CONNECT, "a canister put on the port");
        silent(h, a.abs(2, 3, 2), "the canister put on the port");
        a.set(2, 3, 2, AIR);
        plays(h, pos, AtmosSounds.PORT_DISCONNECT, "a canister taken off the port");
        a.set(2, 3, 2, STONE);
        silent(h, pos, "stone put on the port");
        a.set(2, 3, 2, AIR);
        silent(h, pos, "stone taken off the port");

        // Placed under a canister, it attaches it; removed, it has nothing to say.
        a.place(6, 3, 2, GasDeviceTests.canister());
        a.place(6, 2, 2, GasDeviceTests.port());
        BlockPos under = a.abs(6, 2, 2);
        plays(h, under, AtmosSounds.PORT_CONNECT, "a port placed under a canister");
        a.set(6, 2, 2, AIR);
        silent(h, under, "a port removed from under a canister");
        h.succeed();
    }

    /** A burst plays once at the segment that went, and just below the threshold nothing plays. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_126_a_pipe_burst_plays_once(GameTestHelper h) {
        Area a = new Area(h, 919);
        a.box(0, 0, 8, 4);
        PipeNetwork net = PipeBurstTests.run(a);
        a.fill(4, 3, 3, Map.of(N2, 100.0));
        a.tick();
        double room = a.zone(4, 3, 3).mixture().pressure();
        PipeBurstTests.fillTo(net, room + PipeBurst.THRESHOLD_KPA - 1);
        a.step();
        for (int x = 2; x <= 6; x++) {
            silent(h, a.abs(x, 2, 2), "a segment just below the threshold");
        }

        PipeBurstTests.fillTo(net, room + PipeBurst.THRESHOLD_KPA + 1);
        a.step();
        int burst = 0;
        for (int x = 2; x <= 6; x++) {
            BlockPos pos = a.abs(x, 2, 2);
            if (a.level.getBlockEntity(pos) == null) {
                burst++;
                plays(h, pos, AtmosSounds.PIPE_BURST, "the segment that burst");
            } else {
                silent(h, pos, "a segment that didn't burst");
            }
        }
        h.assertValueEqual(burst, 1, "segments gone");
        h.succeed();
    }
}
