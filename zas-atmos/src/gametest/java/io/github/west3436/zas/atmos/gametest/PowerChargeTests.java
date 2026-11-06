package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a controller charges across several atmos ticks (CONT-062, CONT-064, CONT-065, CONT-068).
 * {@link ControllerTests} steps its devices within one game tick, where the game clock stands still,
 * so it sees only the first charge. Here each test builds its rooms and forms its zones that way, then
 * lets the game step them in real time and reads the stores at every game tick with a {@link
 * ChargeLog}.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerChargeTests {
    private static final String BATCH = "zas_atmos_power_charges";

    /** Game ticks: no test here waits for anything but the clock, for about ten atmos ticks. */
    private static final int TIMEOUT = 600;

    /** How long a test waits after its last driven step: see {@link ChargeLog}. */
    private static final int SETTLE = 2 * AtmosTicks.PERIOD;

    /** The atmos ticks most tests watch. */
    private static final int WATCH = 5;

    private static final Runnable NOTHING = () -> {};

    private PowerChargeTests() {}

    /** The controller's own bill for one atmos tick. */
    private static int own() {
        return AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
    }

    private static int bill(TestParts.Kind kind) {
        return kind.draw * AtmosTicks.PERIOD;
    }

    /** A test's site, its chunks force-loaded because it waits in real time, and cleared. */
    private static Site site(GameTestHelper h, int index) {
        Site s = new Site(h, index);
        forced(s, true);
        s.clear();
        return s;
    }

    private static void forced(Site s, boolean on) {
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        s.level.setChunkForced(cx, cz, on);
        s.level.setChunkForced(cx + 1, cz, on);
    }

    /** Runs a test's last checks, then releases its site even if they fail. */
    private static void finish(GameTestSequence seq, Site s, Runnable checks) {
        seq.thenExecute(() -> {
                    try {
                        checks.run();
                    } finally {
                        s.clear();
                        forced(s, false);
                    }
                })
                .thenSucceed();
    }

    private static TestParts.Vent vent(Site s, int x) {
        return s.place(x, 1, 3, TestParts.Kind.VENT.block().defaultBlockState(), TestParts.Vent.class);
    }

    /** Steps a controller until it covers a part in its zone, which takes a step to pin the zone. */
    private static void formZone(GameTestHelper h, Site s, AreaAirControllerBlockEntity c, TestParts.Part part) {
        for (int i = 0; i < 4 && !part.power().isPowered(); i++) {
            s.cycle(c);
        }
        h.assertTrue(part.power().isPowered(), "the controller didn't cover the part in its zone");
    }

    /**
     * An FE block behind a controller pays what the controller spends each atmos tick, once: after the
     * first pull fills the buffer, each later one tops it up by that atmos tick's bill. The buffer is
     * back to full less that bill each time (CONT-062, CONT-065).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void cont_062_the_buffer_refills_from_its_fe_block_every_atmos_tick(GameTestHelper h) {
        Site s = site(h, 3100);
        s.room(0, 4);
        s.set(2, 2, 0, TestParts.battery().defaultBlockState());
        AreaAirControllerBlockEntity c = s.controller(2);
        TestParts.Vent vent = vent(s, 2);
        TestParts.Battery battery = s.entity(2, 2, 0, TestParts.Battery.class);
        formZone(h, s, c, vent);
        int bill = own() + bill(TestParts.Kind.VENT);
        ChargeLog log = new ChargeLog(h, "the FE block", () -> battery.store.getEnergyStored());
        GameTestSequence seq = h.startSequence().thenIdle(SETTLE);
        ChargeLog.watch(seq, h, WATCH, NOTHING, log);
        finish(seq, s, () -> {
            log.assertChargedEach(bill, WATCH);
            h.assertValueEqual(c.buffer().getEnergyStored(), 10_000 - bill, "the buffer after its last step");
        });
    }

    /**
     * Each atmos tick a controller pays its own draw and each part's, once, and a part it covers both
     * by its zone and by a link costs no more. No part goes without power at any game tick, and each
     * runs at each of its steps (CONT-065, CONT-068).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void cont_068_a_controller_charges_each_part_once_every_atmos_tick(GameTestHelper h) {
        Site s = site(h, 3101);
        s.room(0, 4);
        AreaAirControllerBlockEntity c = s.controller(2);
        TestParts.Vent vent = vent(s, 2);
        s.room(6, 10);
        TestParts.Part pump = s.place(8, 1, 2, TestParts.Kind.PUMP.block().defaultBlockState(), TestParts.Part.class);
        c.buffer().set(10_000);
        formZone(h, s, c, vent);
        ItemStack tool = new ItemStack(AtmosItems.MULTITOOL.get());
        MultitoolItem.select(tool, s.level, c.getBlockPos());
        MultitoolItem.link(tool, s.level, vent.getBlockPos());
        MultitoolItem.link(tool, s.level, pump.getBlockPos());
        h.assertValueEqual(c.links().size(), 2, "the controller's links");

        ChargeLog log = new ChargeLog(h, "the buffer", () -> c.buffer().getEnergyStored());
        int[] without = new int[1];
        int[] steps = new int[2];
        GameTestSequence seq = h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            steps[0] = vent.poweredSteps;
            steps[1] = pump.poweredSteps;
        });
        ChargeLog.watch(
                seq,
                h,
                WATCH,
                () -> {
                    if (!vent.power().isPowered() || !pump.power().isPowered()) {
                        without[0]++;
                    }
                },
                log);
        finish(seq, s, () -> {
            log.assertChargedEach(own() + bill(TestParts.Kind.VENT) + bill(TestParts.Kind.PUMP), WATCH);
            h.assertValueEqual(without[0], 0, "game ticks with a part unpowered");
            h.assertTrue(vent.power().source() == c, "the vent's power isn't the controller's");
            h.assertTrue(pump.power().source() == c, "the pump's power isn't the controller's");
            h.assertValueEqual(vent.poweredSteps - steps[0], WATCH, "the vent's steps with power");
            h.assertValueEqual(pump.poweredSteps - steps[1], WATCH, "the pump's steps with power");
        });
    }

    /**
     * A part two controllers cover is paid for once each atmos tick: one covers it by its zone, the
     * other by a link, and only the first to step pays. Each pays its own draw (CONT-068).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void cont_068_a_part_two_controllers_cover_is_charged_once(GameTestHelper h) {
        Site s = site(h, 3102);
        s.room(0, 4);
        AreaAirControllerBlockEntity zone = s.controller(2);
        TestParts.Vent vent = vent(s, 2);
        s.room(6, 10);
        AreaAirControllerBlockEntity linked = s.controller(8);
        zone.buffer().set(10_000);
        linked.buffer().set(10_000);
        formZone(h, s, zone, vent);
        ItemStack tool = new ItemStack(AtmosItems.MULTITOOL.get());
        MultitoolItem.select(tool, s.level, linked.getBlockPos());
        MultitoolItem.link(tool, s.level, vent.getBlockPos());
        s.cycle(linked);
        h.assertValueEqual(linked.parts().size(), 1, "the parts the linked controller covers");

        ChargeLog zoneLog = new ChargeLog(
                h, "the zone controller's buffer", () -> zone.buffer().getEnergyStored());
        ChargeLog linkedLog = new ChargeLog(
                h, "the linked controller's buffer", () -> linked.buffer().getEnergyStored());
        GameTestSequence seq = h.startSequence().thenIdle(SETTLE);
        ChargeLog.watch(seq, h, WATCH, NOTHING, zoneLog, linkedLog);
        finish(seq, s, () -> {
            boolean zonePays = vent.power().source() == zone;
            h.assertTrue(zonePays || vent.power().source() == linked, "neither controller powers the vent");
            int paying = own() + bill(TestParts.Kind.VENT);
            zoneLog.assertChargedEach(zonePays ? paying : own(), WATCH);
            linkedLog.assertChargedEach(zonePays ? own() : paying, WATCH);
        });
    }

    /**
     * As a buffer with nothing to fill it runs out, the charges go on at their full amount while they
     * fit. The next pays only the controller's own draw, since the part's no longer fits, and after
     * that there are none. The part's lease runs out one lease after its last charge, and what's left
     * in the buffer stays there (CONT-060, CONT-064).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void cont_064_charges_stop_as_the_buffer_runs_out(GameTestHelper h) {
        Site s = site(h, 3103);
        s.room(0, 4);
        AreaAirControllerBlockEntity c = s.controller(2);
        TestParts.Vent vent = vent(s, 2);
        c.buffer().set(10_000);
        formZone(h, s, c, vent);
        int own = own();
        int full = own + bill(TestParts.Kind.VENT);
        int left = own / 2;
        ChargeLog log = new ChargeLog(h, "the buffer", () -> c.buffer().getEnergyStored());
        long[] unpowered = {-1};
        int[] steps = new int[1];
        GameTestSequence seq = h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            c.buffer().set(3 * full + own + left);
            steps[0] = vent.poweredSteps;
        });
        ChargeLog.watch(
                seq,
                h,
                4 + DevicePower.LEASE_TICKS / AtmosTicks.PERIOD,
                () -> {
                    if (unpowered[0] < 0 && !vent.power().isPowered()) {
                        unpowered[0] = h.getLevel().getServer().getTickCount();
                    }
                },
                log);
        finish(seq, s, () -> {
            log.assertCharged(full, full, full, own);
            h.assertValueEqual(c.buffer().getEnergyStored(), left, "what's left in the buffer");
            h.assertFalse(c.powered(), "the controller has power with too little for its own draw");
            h.assertTrue(unpowered[0] >= 0, "the vent never lost power");
            // The charge was read on the game tick after it was made, one game tick into its lease.
            long lastFull = log.charges().get(2).tick();
            h.assertValueEqual(unpowered[0] - lastFull, DevicePower.LEASE_TICKS - 1L, "game ticks the last lease ran");
            h.assertTrue(vent.poweredSteps - steps[0] >= 3, "the vent didn't run on what it was charged");
        });
    }

    /**
     * Each charge follows what a part draws at the time: a part that stops drawing costs nothing from
     * the next atmos tick and keeps its lease, and when it draws again it costs its full bill from the
     * next one (CONT-065).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void cont_065_each_charge_follows_what_a_part_draws(GameTestHelper h) {
        Site s = site(h, 3104);
        s.room(0, 4);
        AreaAirControllerBlockEntity c = s.controller(2);
        TestParts.Vent vent = vent(s, 2);
        c.buffer().set(10_000);
        formZone(h, s, c, vent);
        int own = own();
        int full = own + bill(TestParts.Kind.VENT);
        ChargeLog log = new ChargeLog(h, "the buffer", () -> c.buffer().getEnergyStored());
        boolean[] lost = new boolean[1];
        Runnable watchLease = () -> lost[0] |= !vent.power().isPowered();
        GameTestSequence seq = h.startSequence().thenIdle(SETTLE);
        ChargeLog.watch(seq, h, 2, watchLease, log);
        seq.thenExecute(() -> vent.draw = 0);
        ChargeLog.watch(seq, h, 2, watchLease, log);
        seq.thenExecute(() -> vent.draw = TestParts.Kind.VENT.draw);
        ChargeLog.watch(seq, h, 2, watchLease, log);
        finish(seq, s, () -> {
            log.assertCharged(full, full, own, own, full, full);
            h.assertFalse(lost[0], "the part lost its lease while it drew nothing");
        });
    }
}
