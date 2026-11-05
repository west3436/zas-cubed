package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.battery.RechargerBlock;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.device.DeviceActivity;
import io.github.west3436.zas.atmos.device.DeviceActivityPayload.Change;
import io.github.west3436.zas.atmos.device.DeviceActivitySync;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.heater.AirConditionerBlock;
import io.github.west3436.zas.atmos.heater.AirConditionerBlockEntity;
import io.github.west3436.zas.atmos.heater.AirConditionerMath;
import io.github.west3436.zas.atmos.heater.HeaterBlock;
import io.github.west3436.zas.atmos.heater.HeaterBlockEntity;
import io.github.west3436.zas.atmos.heater.HeaterMath;
import io.github.west3436.zas.atmos.heater.ThermoStatus;
import io.github.west3436.zas.atmos.portable.PortableBlock;
import io.github.west3436.zas.atmos.portable.PortableDevice;
import io.github.west3436.zas.atmos.portable.PortablePumpBlockEntity;
import io.github.west3436.zas.atmos.portable.PortableScrubberBlockEntity;
import io.github.west3436.zas.atmos.portable.PortableStatus;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.gametest.TestPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * What devices play and report as they work (CONT-127). The sounds are what the server plays; what a
 * client hears of them can't be tested here. Sites 800 to 819.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeviceSoundTests {
    private static final String BATCH = "zas_atmos_device_sounds";
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    private DeviceSoundTests() {}

    /** Takes what a block played: exactly these events, at volume 1 and pitch 1 on the Blocks slider. */
    @SafeVarargs
    static void played(
            GameTestHelper h, BlockPos block, String when, DeferredHolder<SoundEvent, SoundEvent>... events) {
        heard(h, Vec3.atCenterOf(block), SoundSource.BLOCKS, when, events);
    }

    /** Takes what played at a point: exactly these events, at volume 1 and pitch 1 on this slider. */
    @SafeVarargs
    static void heard(
            GameTestHelper h,
            Vec3 at,
            SoundSource slider,
            String when,
            DeferredHolder<SoundEvent, SoundEvent>... events) {
        List<SoundLog.Played> sounds = SoundLog.take(at);
        h.assertTrue(sounds.size() == events.length, when + ": played " + sounds);
        for (int i = 0; i < events.length; i++) {
            SoundLog.Played sound = sounds.get(i);
            h.assertTrue(
                    sound.id().equals(events[i].getId())
                            && sound.channel() == slider
                            && sound.volume() == 1
                            && sound.pitch() == 1,
                    when + ": played " + sound + ", not " + events[i].getId());
        }
    }

    /** What a player would be told at game tick {@code now}: nothing is an empty list. */
    private static List<Change> told(FakePlayer player, long now) {
        List<Change> changes = new ArrayList<>();
        DeviceActivitySync.syncAt(player, now, payload -> changes.addAll(payload.changes()));
        return changes;
    }

    private static List<Change> one(AtmosDevice device, int level) {
        return List.of(new Change(device.getBlockPos().asLong(), level));
    }

    private static FakePlayer player(Site s, String name) {
        FakePlayer p = TestPlayers.hurtable(s.level, name);
        p.moveTo(Vec3.atCenterOf(s.abs(2, 2, 2)));
        return p;
    }

    private static HeaterBlockEntity heater(Site s, int x, int y, int z) {
        return s.place(
                x,
                y,
                z,
                AtmosBlocks.HEATER.get().defaultBlockState().setValue(HeaterBlock.FACING, Direction.NORTH),
                HeaterBlockEntity.class);
    }

    private static AirConditionerBlockEntity airConditioner(Site s, int x, int y, int z) {
        return s.place(
                x,
                y,
                z,
                AtmosBlocks.AIR_CONDITIONER
                        .get()
                        .defaultBlockState()
                        .setValue(AirConditionerBlock.FACING, Direction.NORTH),
                AirConditionerBlockEntity.class);
    }

    private static PortablePumpBlockEntity pump(Site s, int x, int y, int z) {
        return s.place(
                x,
                y,
                z,
                AtmosBlocks.PORTABLE_PUMP.get().defaultBlockState().setValue(PortableBlock.FACING, Direction.NORTH),
                PortablePumpBlockEntity.class);
    }

    private static PortableScrubberBlockEntity scrubber(Site s, int x, int y, int z) {
        return s.place(
                x,
                y,
                z,
                AtmosBlocks.PORTABLE_SCRUBBER.get().defaultBlockState().setValue(PortableBlock.FACING, Direction.NORTH),
                PortableScrubberBlockEntity.class);
    }

    private static RechargerBlockEntity recharger(Site s, int x, int y, int z) {
        return s.place(
                x,
                y,
                z,
                AtmosBlocks.RECHARGER.get().defaultBlockState().setValue(RechargerBlock.FACING, Direction.NORTH),
                RechargerBlockEntity.class);
    }

    private static void switches(
            Site s,
            PortableDevice device,
            DeferredHolder<SoundEvent, SoundEvent> on,
            DeferredHolder<SoundEvent, SoundEvent> off) {
        BlockPos at = device.getBlockPos();
        String name = device.getBlockState().getBlock().toString();
        device.setOn(false);
        played(s.h, at, name + ", off and left off");
        device.setOn(true);
        device.setOn(true);
        played(s.h, at, name + ", switched on twice", on);
        device.setOn(false);
        device.setOn(false);
        played(s.h, at, name + ", switched off twice", off);
    }

    /** A machine's switch plays its own sound once each way, and nothing if nothing changed (CONT-127). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_127_a_machine_switch_plays_once_each_way(GameTestHelper h) {
        SoundLog.listen();
        Site s = new Site(h, 800);
        try {
            s.clear();
            switches(s, heater(s, 0, 1, 0), AtmosSounds.HEATER_ON, AtmosSounds.HEATER_OFF);
            switches(s, airConditioner(s, 2, 1, 0), AtmosSounds.AIR_CONDITIONER_ON, AtmosSounds.AIR_CONDITIONER_OFF);
            switches(s, pump(s, 4, 1, 0), AtmosSounds.PORTABLE_PUMP_ON, AtmosSounds.PORTABLE_PUMP_OFF);
            switches(s, scrubber(s, 6, 1, 0), AtmosSounds.PORTABLE_SCRUBBER_ON, AtmosSounds.PORTABLE_SCRUBBER_OFF);
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * A recharger sounds as it starts charging, as it fills, and as it stops short, once each. A step
     * that stays as it was is silent, and so is one with no charging on either side of it (CONT-127).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_127_a_recharger_plays_as_it_starts_fills_and_stops(GameTestHelper h) {
        SoundLog.listen();
        Site s = new Site(h, 801);
        try {
            BatteryTests.clear(s);
            RechargerBlockEntity r = recharger(s, 2, 1, 2);
            r.power().setTestPowered(true);
            BlockPos at = r.getBlockPos();

            s.cycles(2, r);
            played(h, at, "with no battery");

            BatteryTests.swap(r.battery(), new ItemStack(AtmosItems.BATTERY.get()));
            s.cycles(2, r);
            h.assertValueEqual(r.status(), RechargerBlockEntity.Status.CHARGING, "with an empty battery");
            played(h, at, "as it starts charging", AtmosSounds.RECHARGER_ON);

            BatteryTests.swap(r.battery(), BatteryTests.charged(AtmosItems.BATTERY.get(), 100_000 - 100));
            s.cycle(r);
            played(h, at, "topping up");
            s.cycles(2, r);
            h.assertValueEqual(r.status(), RechargerBlockEntity.Status.FULL, "with a full battery");
            played(h, at, "as it fills", AtmosSounds.RECHARGER_DONE);

            BatteryTests.swap(r.battery(), ItemStack.EMPTY);
            s.cycles(2, r);
            h.assertValueEqual(r.status(), RechargerBlockEntity.Status.NO_BATTERY, "the full battery taken out");
            played(h, at, "with a full battery taken out");

            BatteryTests.swap(r.battery(), new ItemStack(AtmosItems.BATTERY.get()));
            s.cycles(2, r);
            played(h, at, "charging again", AtmosSounds.RECHARGER_ON);
            r.power().setTestPowered(false);
            s.cycles(2, r);
            h.assertValueEqual(r.status(), RechargerBlockEntity.Status.UNPOWERED, "with its power gone");
            played(h, at, "with its power gone", AtmosSounds.RECHARGER_OFF);
            r.power().setTestPowered(true);
            s.cycles(2, r);
            played(h, at, "with its power back", AtmosSounds.RECHARGER_ON);
            BatteryTests.swap(r.battery(), ItemStack.EMPTY);
            s.cycles(2, r);
            played(h, at, "with a part-charged battery taken out", AtmosSounds.RECHARGER_OFF);
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * A generator plays as it lights and as it goes out, once each, not between one piece of fuel and
     * the next (CONT-127).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_127_a_generator_plays_as_it_lights_and_goes_out(GameTestHelper h) {
        SoundLog.listen();
        Site s = new Site(h, 802);
        try {
            s.clear();
            GeneratorBlockEntity g =
                    s.place(2, 1, 2, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            BlockPos at = g.getBlockPos();
            g.serverTick();
            played(h, at, "with no fuel");

            g.fuel().insertItem(0, new ItemStack(Items.STICK, 2), false);
            g.serverTick();
            played(h, at, "as it lights", AtmosSounds.GENERATOR_ON);
            for (int i = 0; i < 150; i++) {
                g.energy().extractEnergy(Integer.MAX_VALUE, false);
                g.serverTick();
            }
            h.assertTrue(g.fuel().getStackInSlot(0).isEmpty(), "the second stick hasn't started");
            played(h, at, "from one stick to the next");
            for (int i = 0; i < 100; i++) {
                g.energy().extractEnergy(Integer.MAX_VALUE, false);
                g.serverTick();
            }
            played(h, at, "as it goes out", AtmosSounds.GENERATOR_OFF);
            g.serverTick();
            played(h, at, "after it went out");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * A heater reads as working only while it heats: past its setpoint it falls idle after device
     * activity's hold, and switched off at once (CONT-127, DATA-017). The air conditioner shares the
     * code.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_127_a_heater_reports_working_only_while_it_heats(GameTestHelper h) {
        Site s = new Site(h, 803);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            HeaterBlockEntity heater = heater(s, 2, 1, 2);
            BatteryTests.formZone(s, 2, 2, 2);
            BatteryTests.swap(heater.battery(), AtmosItems.BATTERY.get().full());
            FakePlayer p = player(s, "zas_heater_sounds");
            long t = AtmosTicks.now(s.level);

            s.cycles(2, heater);
            h.assertValueEqual(heater.status(), ThermoStatus.OFF, "the new heater");
            h.assertValueEqual(told(p, t), List.of(), "off, it reports nothing");

            heater.setSetpoint(HeaterMath.MAX_SETPOINT);
            heater.setOn(true);
            for (int i = 0; i < 6 && heater.status() != ThermoStatus.HEATING; i++) {
                s.cycle(heater);
            }
            h.assertValueEqual(heater.status(), ThermoStatus.HEATING, "with a room and a battery");
            h.assertValueEqual(told(p, t), one(heater, DeviceActivity.WORKING), "while it heats");

            heater.setOn(false);
            s.cycle(heater);
            h.assertValueEqual(told(p, t + AtmosTicks.PERIOD), one(heater, DeviceActivity.IDLE), "switched off");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * A portable scrubber's level follows the moles it moves, and it falls idle at once when switched
     * off (CONT-127, DATA-017). The pump shares the code.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_127_a_portable_reports_what_it_moves(GameTestHelper h) {
        Site s = new Site(h, 804);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            PortableScrubberBlockEntity scrubber = scrubber(s, 2, 1, 2);
            BatteryTests.formZone(s, 2, 2, 2);
            s.addKpa(2, 2, 2, CO2, 2);
            BatteryTests.swap(scrubber.battery(), AtmosItems.BATTERY.get().full());
            FakePlayer p = player(s, "zas_portable_sounds");
            long t = AtmosTicks.now(s.level);

            s.cycles(2, scrubber);
            h.assertValueEqual(told(p, t), List.of(), "off, it reports nothing");

            scrubber.setOn(true);
            for (int i = 0; i < 6 && scrubber.status() != PortableStatus.WORKING; i++) {
                s.cycle(scrubber);
            }
            h.assertValueEqual(scrubber.status(), PortableStatus.WORKING, "with CO2 to scrub");
            List<Change> working = told(p, t);
            h.assertValueEqual(working.size(), 1, "what a working scrubber reported");
            h.assertTrue(working.get(0).level() != DeviceActivity.IDLE, "a working scrubber read as idle");

            scrubber.setOn(false);
            s.tick(); // the step's change finishes first
            s.cycle(scrubber);
            h.assertValueEqual(told(p, t + AtmosTicks.PERIOD), one(scrubber, DeviceActivity.IDLE), "switched off");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /** A recharger reads as working while it charges and idle at its next step after (CONT-127). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_127_a_recharger_reports_working_only_while_it_charges(GameTestHelper h) {
        Site s = new Site(h, 805);
        try {
            BatteryTests.clear(s);
            s.clear();
            RechargerBlockEntity r = recharger(s, 2, 1, 2);
            r.power().setTestPowered(true);
            FakePlayer p = player(s, "zas_recharger_sounds");
            long t = AtmosTicks.now(s.level);

            s.cycle(r);
            h.assertValueEqual(told(p, t), List.of(), "with no battery");

            BatteryTests.swap(r.battery(), new ItemStack(AtmosItems.BATTERY.get()));
            s.cycle(r);
            h.assertValueEqual(r.status(), RechargerBlockEntity.Status.CHARGING, "with an empty battery");
            h.assertValueEqual(told(p, t), one(r, DeviceActivity.WORKING), "while it charges");

            BatteryTests.swap(r.battery(), ItemStack.EMPTY);
            s.cycle(r);
            h.assertValueEqual(told(p, t + AtmosTicks.PERIOD), one(r, DeviceActivity.IDLE), "with no battery again");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    // ---- CONT-018, CONT-019, DATA-017: a portable's switch shows at once -----------------------------

    private static boolean lit(Site s, PortableDevice device) {
        return s.level.getBlockState(device.getBlockPos()).getValue(PortableBlock.LIT);
    }

    /** A room from x0 with a portable at x0 + 2 on a full battery, past its cell's wait. */
    private static <T extends PortableDevice> T inRoom(Site s, int x0, IntFunction<T> place) {
        s.room(x0, x0 + 4);
        T device = place.apply(x0 + 2);
        BatteryTests.formZone(s, x0 + 2, 2, 2);
        BatteryTests.swap(device.battery(), AtmosItems.BATTERY.get().full());
        s.cycles(CellWatch.SETTLE_STEPS + 1, device);
        return device;
    }

    /**
     * Switches a portable with work to do on and off, in the room at x. Its block and its level follow
     * the switch at once, its charge and its room wait for its step, and that step agrees with what
     * the switch showed, so it doesn't go dark or change its level a moment later.
     */
    private static void followsItsSwitch(Site s, PortableDevice device, int x) {
        GameTestHelper h = s.h;
        String name = device.getBlockState().getBlock().toString();
        FakePlayer p = player(s, "zas_switch");
        long t = AtmosTicks.now(s.level);
        int charge = device.energy();
        GasMixture air = s.zone(x, 2, 2).mixture();

        device.setOn(true);
        h.assertTrue(lit(s, device), name + " isn't lit once switched on");
        List<Change> on = told(p, t);
        h.assertValueEqual(on.size(), 1, name + ": what it reported once switched on");
        h.assertTrue(on.get(0).level() != DeviceActivity.IDLE, name + " reads as idle once switched on");
        s.tick(); // anything it had asked of its room would land here
        h.assertValueEqual(device.energy(), charge, name + ": the charge after its switch");
        GasMixture after = s.zone(x, 2, 2).mixture();
        s.close(after.totalMoles(), air.totalMoles(), 1e-9, name + ": the room's moles after its switch");
        s.close(after.temperature(), air.temperature(), 1e-9, name + ": the room's temperature after its switch");

        s.cycle(device);
        h.assertTrue(lit(s, device), name + " went dark at its first step");
        h.assertTrue(device.energy() < charge, name + " paid nothing at its first step");
        h.assertValueEqual(told(p, t + AtmosTicks.PERIOD), List.of(), name + ": what its first step changed");

        device.setOn(false);
        h.assertFalse(lit(s, device), name + " is still lit once switched off");
        h.assertValueEqual(
                told(p, t + 2 * AtmosTicks.PERIOD), one(device, DeviceActivity.IDLE), name + " switched off");
        s.tick(); // the first step's change finishes
        charge = device.energy();
        s.cycle(device);
        h.assertFalse(lit(s, device), name + " lit up at its step while off");
        h.assertValueEqual(device.energy(), charge, name + ": the charge at its step while off");
        h.assertValueEqual(told(p, t + 3 * AtmosTicks.PERIOD), List.of(), name + ": what its step while off changed");
    }

    /**
     * A heater's and an air conditioner's block and level follow their switches at once, and their
     * heat and charge wait for their steps (CONT-019, DATA-017).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_019_a_thermomachine_follows_its_switch_at_once(GameTestHelper h) {
        Site s = new Site(h, 813);
        try {
            BatteryTests.clear(s);
            HeaterBlockEntity heater = inRoom(s, 0, x -> heater(s, x, 1, 2));
            heater.setSetpoint(HeaterMath.MAX_SETPOINT);
            followsItsSwitch(s, heater, 2);
            AirConditionerBlockEntity ac = inRoom(s, 6, x -> airConditioner(s, x, 1, 2));
            ac.setSetpoint(AirConditionerMath.MIN_SETPOINT);
            followsItsSwitch(s, ac, 8);
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * A portable pump's and scrubber's block and level follow their switches at once, and their gas
     * and charge wait for their steps. On a held tank it has no cell to wait for, so it shows at once
     * even just placed in open air (CONT-018, DATA-017).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_a_portable_follows_its_switch_at_once(GameTestHelper h) {
        Site s = new Site(h, 814);
        try {
            BatteryTests.clear(s);
            PortablePumpBlockEntity pump = inRoom(s, 0, x -> pump(s, x, 1, 2));
            pump.setSiphon(true);
            pump.setTarget(300);
            followsItsSwitch(s, pump, 2);

            PortableScrubberBlockEntity scrubber = inRoom(s, 6, x -> scrubber(s, x, 1, 2));
            s.addKpa(8, 2, 2, CO2, 2);
            followsItsSwitch(s, scrubber, 8);

            PortableScrubberBlockEntity onTank = scrubber(s, 14, 1, 2);
            BatteryTests.swap(onTank.battery(), AtmosItems.BATTERY.get().full());
            ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
            GasTankItem.setGas(tank, new GasParcel(Map.of(CO2, 1.0), 293.15));
            onTank.tank().insertItem(0, tank, false);
            onTank.setOn(true);
            h.assertTrue(lit(s, onTank), "a scrubber on a tank isn't lit once switched on");
            s.close(GasTankItem.contents(onTank.tank().tank()).moles(CO2), 1, 1e-9, "the tank after its switch");
            h.assertValueEqual(onTank.energy(), 100_000, "the charge after its switch on a tank");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /** Switches on what has nothing to do: its block stays dark and it reads as idle, then and at its step. */
    private static void staysDark(Site s, PortableDevice device, String when) {
        FakePlayer p = player(s, "zas_dark_switch");
        long t = AtmosTicks.now(s.level);
        device.setOn(true);
        s.h.assertFalse(lit(s, device), when + ": lit once switched on");
        s.h.assertValueEqual(told(p, t), List.of(), when + ": what it reported once switched on");
        s.cycle(device);
        s.h.assertFalse(lit(s, device), when + ": lit at its step");
        s.h.assertValueEqual(told(p, t + AtmosTicks.PERIOD), List.of(), when + ": what it reported at its step");
        device.setOn(false);
    }

    /**
     * A portable switched on with nothing to do, no charge or no room doesn't light for a moment. Its
     * status says each is the case (CONT-018).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_a_portable_that_wouldnt_work_stays_dark_when_switched_on(GameTestHelper h) {
        Site s = new Site(h, 815);
        try {
            BatteryTests.clear(s);
            PortableScrubberBlockEntity scrubber = inRoom(s, 0, x -> scrubber(s, x, 1, 2));
            s.addKpa(2, 2, 2, CO2, 2);
            s.removeGas(2, 2, 2, CO2);
            staysDark(s, scrubber, "a scrubber in clean air");
            h.assertValueEqual(scrubber.status(), PortableStatus.NOTHING_TO_MOVE, "the scrubber in clean air");
            s.addKpa(2, 2, 2, CO2, 2);
            BatteryTests.swap(scrubber.battery(), new ItemStack(AtmosItems.BATTERY.get()));
            staysDark(s, scrubber, "a scrubber on an empty battery");
            h.assertValueEqual(scrubber.status(), PortableStatus.BATTERY_EMPTY, "the scrubber on an empty battery");
            BatteryTests.swap(scrubber.battery(), ItemStack.EMPTY);
            staysDark(s, scrubber, "a scrubber with no battery");
            h.assertValueEqual(scrubber.status(), PortableStatus.NO_BATTERY, "the scrubber with no battery");

            PortablePumpBlockEntity pump = inRoom(s, 6, x -> pump(s, x, 1, 2));
            staysDark(s, pump, "a pump with an empty store");
            h.assertValueEqual(pump.status(), PortableStatus.NOTHING_TO_MOVE, "the pump with an empty store");

            HeaterBlockEntity heater = inRoom(s, 12, x -> heater(s, x, 1, 2));
            heater.setSetpoint(HeaterMath.MIN_SETPOINT);
            staysDark(s, heater, "a heater past its setpoint");
            h.assertValueEqual(heater.status(), ThermoStatus.AT_SETPOINT, "the heater past its setpoint");
            heater.setSetpoint(HeaterMath.MAX_SETPOINT);
            BatteryTests.swap(heater.battery(), new ItemStack(AtmosItems.BATTERY.get()));
            staysDark(s, heater, "a heater on an empty battery");
            h.assertValueEqual(heater.status(), ThermoStatus.BATTERY_EMPTY, "the heater on an empty battery");
            BatteryTests.swap(heater.battery(), ItemStack.EMPTY);
            staysDark(s, heater, "a heater with no battery");
            h.assertValueEqual(heater.status(), ThermoStatus.NO_BATTERY, "the heater with no battery");

            // In open air, outside the rooms.
            PortableScrubberBlockEntity outdoors = scrubber(s, 2, 1, 6);
            BatteryTests.swap(outdoors.battery(), AtmosItems.BATTERY.get().full());
            s.cycles(CellWatch.SETTLE_STEPS + 1, outdoors);
            staysDark(s, outdoors, "a scrubber in open air");
            h.assertValueEqual(outdoors.status(), PortableStatus.NO_ROOM, "the scrubber in open air");
            HeaterBlockEntity cold = heater(s, 8, 1, 6);
            cold.setSetpoint(HeaterMath.MAX_SETPOINT);
            BatteryTests.swap(cold.battery(), AtmosItems.BATTERY.get().full());
            staysDark(s, cold, "a heater in open air");
            h.assertValueEqual(cold.status(), ThermoStatus.NO_ROOM, "the heater in open air");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * Flipping its switch doesn't shorten or lengthen a portable's wait for its cell. It stays dark
     * until the step that would work, and lights at once from then on, so a portable that has stood
     * off for a while shows at once as well (CONT-018).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_a_switch_leaves_a_portables_wait_for_its_cell_alone(GameTestHelper h) {
        Site s = new Site(h, 816);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            PortableScrubberBlockEntity scrubber = scrubber(s, 2, 1, 2);
            BatteryTests.formZone(s, 2, 2, 2);
            s.addKpa(2, 2, 2, CO2, 2);
            BatteryTests.swap(scrubber.battery(), AtmosItems.BATTERY.get().full());
            for (int i = 0; i < CellWatch.SETTLE_STEPS; i++) {
                flips(scrubber, true);
                h.assertFalse(lit(s, scrubber), "lit once switched on while its cell settles");
                s.cycle(scrubber);
                h.assertValueEqual(scrubber.status(), PortableStatus.WAITING, "while its cell settles");
                h.assertFalse(lit(s, scrubber), "lit at a step while its cell settles");
            }
            flips(scrubber, true);
            h.assertTrue(lit(s, scrubber), "not lit once switched on with its cell settled");
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.WORKING, "the step after its cell settled");
            h.assertTrue(lit(s, scrubber), "not lit at its first working step");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /** Flips a switch ten times between steps, leaving it on or off as asked. */
    private static void flips(PortableDevice device, boolean on) {
        for (int i = 0; i < 10; i++) {
            device.setOn(!device.on());
        }
        device.setOn(on);
    }

    /**
     * A switch gets a portable no extra work. Over the same steps, a scrubber flipped between steps
     * and on at each of them moves what one left on moves and pays the same, and one off at each of
     * them moves nothing and pays nothing, however often it was on in between. The pump shares the
     * code (CONT-018).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_toggling_a_portable_moves_no_more_than_leaving_it_on(GameTestHelper h) {
        Site s = new Site(h, 817);
        try {
            BatteryTests.clear(s);
            PortableScrubberBlockEntity left = inRoom(s, 0, x -> scrubber(s, x, 1, 2));
            PortableScrubberBlockEntity flipped = inRoom(s, 6, x -> scrubber(s, x, 1, 2));
            PortableScrubberBlockEntity offAtSteps = inRoom(s, 12, x -> scrubber(s, x, 1, 2));
            for (int x : new int[] {2, 8, 14}) {
                s.addKpa(x, 2, 2, CO2, 2);
            }
            left.setOn(true);
            for (int i = 0; i < 5; i++) {
                flips(flipped, true);
                flips(offAtSteps, false);
                s.cycle(left, flipped, offAtSteps);
            }
            double moved = left.store().moles(CO2);
            h.assertTrue(moved > 0, "left on, it moved nothing");
            h.assertTrue(left.energy() < 100_000, "left on, it paid nothing");
            s.close(flipped.store().moles(CO2), moved, 1e-6, "what the flipped scrubber moved");
            h.assertValueEqual(flipped.energy(), left.energy(), "what the flipped scrubber paid");
            s.close(offAtSteps.store().totalMoles(), 0, 0, "what the scrubber off at its steps moved");
            h.assertValueEqual(offAtSteps.energy(), 100_000, "what the scrubber off at its steps paid");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * A switch gets a thermomachine no extra work, as for a scrubber above. The air conditioner shares
     * the code (CONT-019).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_019_toggling_a_thermomachine_moves_no_more_than_leaving_it_on(GameTestHelper h) {
        Site s = new Site(h, 818);
        try {
            BatteryTests.clear(s);
            HeaterBlockEntity left = inRoom(s, 0, x -> heater(s, x, 1, 2));
            HeaterBlockEntity flipped = inRoom(s, 6, x -> heater(s, x, 1, 2));
            HeaterBlockEntity offAtSteps = inRoom(s, 12, x -> heater(s, x, 1, 2));
            for (HeaterBlockEntity heater : List.of(left, flipped, offAtSteps)) {
                heater.setSetpoint(HeaterMath.MAX_SETPOINT);
            }
            double start = s.zone(2, 2, 2).mixture().temperature();
            left.setOn(true);
            for (int i = 0; i < 5; i++) {
                flips(flipped, true);
                flips(offAtSteps, false);
                s.cycle(left, flipped, offAtSteps);
            }
            double heated = s.zone(2, 2, 2).mixture().temperature();
            h.assertTrue(heated > start, "left on, it heated nothing");
            h.assertTrue(left.energy() < 100_000, "left on, it paid nothing");
            s.close(s.zone(8, 2, 2).mixture().temperature(), heated, 1e-3, "what the flipped heater heated");
            h.assertValueEqual(flipped.energy(), left.energy(), "what the flipped heater paid");
            s.close(s.zone(14, 2, 2).mixture().temperature(), start, 1e-3, "what the heater off at its steps heated");
            h.assertValueEqual(offAtSteps.energy(), 100_000, "what the heater off at its steps paid");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }
}
