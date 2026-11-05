package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.battery.RechargerBlock;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceActivity;
import io.github.west3436.zas.atmos.device.DeviceActivityPayload.Change;
import io.github.west3436.zas.atmos.device.DeviceActivitySync;
import io.github.west3436.zas.atmos.heater.AirConditionerBlock;
import io.github.west3436.zas.atmos.heater.AirConditionerBlockEntity;
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
     * activity's hold, and switched off at its next step (CONT-127, DATA-017). The air conditioner
     * shares the code.
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
     * A portable scrubber's level follows the moles it moves, and it falls idle at its next step when
     * switched off (CONT-127, DATA-017). The pump shares the code.
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
}
