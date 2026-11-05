package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceActivity;
import io.github.west3436.zas.atmos.device.DeviceActivityPayload;
import io.github.west3436.zas.atmos.device.DeviceActivityPayload.Change;
import io.github.west3436.zas.atmos.device.DeviceActivitySync;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.particle.DeviceJets;
import io.github.west3436.zas.atmos.particle.GasJetKind;
import io.github.west3436.zas.atmos.particle.JetPlan;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlock;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.scrubber.ScrubberStatus;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.atmos.vent.VentSettings;
import io.github.west3436.zas.atmos.vent.VentStatus;
import io.github.west3436.zas.gametest.TestPlayers;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Telling clients how hard devices are working (DATA-017, DATA-018), and what a client shows for it
 * (CLIENT-019).
 *
 * <p>A fake player has no connection and isn't in the server's player list, so these ask {@link
 * DeviceActivitySync#syncAt} what it would send. Each runs within one game tick, and passes the sync
 * a later tick as the clock, so devices in other tests' areas, which are 256 blocks apart, don't step
 * in between.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeviceActivityTests {
    private static final String BATCH = "zas_atmos_activity";

    private DeviceActivityTests() {}

    /** A test device at (x, 1, z) of the area, moving this much a step. */
    private static TestDevice.Entity device(Area a, int x, int z, double moles) {
        a.place(x, 1, z, TestDevice.block().defaultBlockState());
        TestDevice.Entity d = a.entity(x, 1, z);
        d.moves = moles;
        return d;
    }

    private static void step(Area a) {
        DeviceTicker.stepNow(a.level);
    }

    private static FakePlayer player(Area a, int x, int z) {
        FakePlayer p = TestPlayers.hurtable(a.level, "zas_activity");
        p.moveTo(a.point(x, 1, z));
        return p;
    }

    /** What the sync sends a player at game tick {@code now}: nothing is an empty list. */
    private static List<Change> told(GameTestHelper h, FakePlayer p, long now) {
        List<DeviceActivityPayload> sent = new ArrayList<>();
        DeviceActivityPayload back = DeviceActivitySync.syncAt(p, now, sent::add);
        h.assertTrue(sent.size() <= 1, "more than one message at once");
        h.assertTrue(back == (sent.isEmpty() ? null : sent.get(0)), "the sync's answer isn't what it sent");
        return sent.isEmpty() ? List.of() : sent.get(0).changes();
    }

    private static List<Change> one(TestDevice.Entity d, int level) {
        return one(d.getBlockPos(), level);
    }

    private static List<Change> one(BlockPos pos, int level) {
        return List.of(new Change(pos.asLong(), level));
    }

    /** DATA-018: a player within 32 blocks is told, and one further away isn't. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_only_players_in_range_are_told(GameTestHelper h) {
        Area a = new Area(h, 760);
        TestDevice.Entity d = device(a, 0, 0, 8);
        step(a);
        long t = AtmosTicks.now(a.level);
        h.assertValueEqual(told(h, player(a, 8, 0), t), one(d, DeviceActivity.HARD), "8 blocks away");
        h.assertValueEqual(told(h, player(a, 32, 0), t), one(d, DeviceActivity.HARD), "32 blocks away");
        h.assertValueEqual(told(h, player(a, 33, 0), t), List.of(), "33 blocks away");
        h.assertValueEqual(told(h, player(a, 0, -40), t), List.of(), "40 blocks away");
        h.succeed();
    }

    /** DATA-018: however often it is asked, a player gets at most one message per atmos tick. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_at_most_once_per_atmos_tick(GameTestHelper h) {
        Area a = new Area(h, 761);
        TestDevice.Entity d = device(a, 0, 0, 8);
        step(a);
        FakePlayer p = player(a, 4, 0);
        long t = AtmosTicks.now(a.level);
        h.assertValueEqual(told(h, p, t), one(d, DeviceActivity.HARD), "the first");
        d.moves = 1.5;
        step(a);
        step(a);
        h.assertValueEqual(told(h, p, t), List.of(), "again in the same game tick");
        h.assertValueEqual(told(h, p, t + AtmosTicks.PERIOD - 1), List.of(), "before the next atmos tick");
        h.assertValueEqual(told(h, p, t + AtmosTicks.PERIOD), one(d, DeviceActivity.WORKING), "at the next");
        h.succeed();
    }

    /** DATA-018: a device still working at the same level, or a player already told, sends nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_nothing_is_sent_when_nothing_changed(GameTestHelper h) {
        Area a = new Area(h, 762);
        TestDevice.Entity d = device(a, 0, 0, 8);
        step(a);
        FakePlayer p = player(a, 4, 0);
        long t = AtmosTicks.now(a.level);
        h.assertValueEqual(told(h, p, t), one(d, DeviceActivity.HARD), "the first");
        for (int k = 1; k <= 3; k++) {
            d.moves = 3 + k; // moving, but within the level
            step(a);
            h.assertValueEqual(told(h, p, t + k * AtmosTicks.PERIOD), List.of(), "unchanged, " + k);
        }
        h.succeed();
    }

    /** DATA-017, DATA-018: a device that moves next to nothing reads as idle, so nobody is told of it. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_a_trickle_below_the_scale_sends_nothing(GameTestHelper h) {
        Area a = new Area(h, 763);
        device(a, 0, 0, 0);
        device(a, 2, 0, 0.01);
        TestDevice.Entity d = device(a, 4, 0, 0.06);
        step(a);
        h.assertValueEqual(
                told(h, player(a, 2, 2), AtmosTicks.now(a.level)),
                one(d, DeviceActivity.TRICKLE),
                "only the one that moved 0.06 mol");
        h.succeed();
    }

    /** DATA-018: a device that falls idle tells a player to forget it, after the hold, and only once. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_going_idle_is_told_once(GameTestHelper h) {
        Area a = new Area(h, 764);
        TestDevice.Entity d = device(a, 0, 0, 8);
        step(a);
        FakePlayer p = player(a, 4, 0);
        long t = AtmosTicks.now(a.level);
        told(h, p, t);
        d.moves = 0;
        step(a);
        h.assertValueEqual(told(h, p, t + AtmosTicks.PERIOD), List.of(), "after one step with nothing");
        step(a);
        h.assertValueEqual(told(h, p, t + 2 * AtmosTicks.PERIOD), one(d, DeviceActivity.IDLE), "after two");
        h.assertValueEqual(told(h, p, t + 3 * AtmosTicks.PERIOD), List.of(), "told once");
        h.succeed();
    }

    /** DATA-017: a device switched off doesn't wait out the hold. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_017_a_device_that_stops_reads_idle_at_its_next_step(GameTestHelper h) {
        Area a = new Area(h, 765);
        TestDevice.Entity d = device(a, 0, 0, 8);
        step(a);
        FakePlayer p = player(a, 4, 0);
        long t = AtmosTicks.now(a.level);
        told(h, p, t);
        d.moves = 0;
        d.stopping = true;
        step(a);
        h.assertValueEqual(told(h, p, t + AtmosTicks.PERIOD), one(d, DeviceActivity.IDLE), "after one step");
        h.succeed();
    }

    /** DATA-018: a device that is removed is forgotten. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_removing_a_device_is_told(GameTestHelper h) {
        Area a = new Area(h, 766);
        TestDevice.Entity d = device(a, 0, 0, 8);
        step(a);
        FakePlayer p = player(a, 4, 0);
        long t = AtmosTicks.now(a.level);
        told(h, p, t);
        BlockPos pos = d.getBlockPos();
        a.set(0, 1, 0, Area.AIR);
        h.assertValueEqual(told(h, p, t + AtmosTicks.PERIOD), one(pos, DeviceActivity.IDLE), "after removal");
        h.succeed();
    }

    /**
     * DATA-018: the server's lease. A device that stops stepping, with its port stuck or its chunk out
     * of full status, reads as idle after 3 s, whatever it last reported, and a player told of it
     * forgets it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_a_device_that_stops_stepping_is_let_go(GameTestHelper h) {
        Area a = new Area(h, 767);
        TestDevice.Entity d = device(a, 0, 0, 8);
        step(a);
        d.ready = false;
        FakePlayer p = player(a, 4, 0);
        long t = AtmosTicks.now(a.level);
        told(h, p, t);
        h.assertValueEqual(told(h, p, t + DeviceActivitySync.STALE_TICKS), List.of(), "on the lease's last tick");
        h.assertValueEqual(
                told(h, p, t + DeviceActivitySync.STALE_TICKS + 1), one(d, DeviceActivity.IDLE), "past its lease");
        h.assertValueEqual(
                told(h, player(a, 4, 0), t + DeviceActivitySync.STALE_TICKS + 1),
                List.of(),
                "a new player isn't told of it");
        d.ready = true;
        step(a);
        h.assertValueEqual(told(h, player(a, 4, 0), t), one(d, DeviceActivity.HARD), "stepping again");
        h.succeed();
    }

    /**
     * DATA-018: a player told of a device isn't told to forget it until 40 blocks, so walking along the
     * edge doesn't keep sending, and is told again on coming back.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_leaving_range_has_a_margin(GameTestHelper h) {
        Area a = new Area(h, 768);
        TestDevice.Entity d = device(a, 0, 0, 8);
        step(a);
        FakePlayer p = player(a, 30, 0);
        long t = AtmosTicks.now(a.level);
        h.assertValueEqual(told(h, p, t), one(d, DeviceActivity.HARD), "at 30 blocks");
        p.moveTo(a.point(38, 1, 0));
        h.assertValueEqual(told(h, p, t + AtmosTicks.PERIOD), List.of(), "at 38 blocks");
        p.moveTo(a.point(41, 1, 0));
        h.assertValueEqual(told(h, p, t + 2 * AtmosTicks.PERIOD), one(d, DeviceActivity.IDLE), "at 41 blocks");
        p.moveTo(a.point(31, 1, 0));
        h.assertValueEqual(told(h, p, t + 3 * AtmosTicks.PERIOD), one(d, DeviceActivity.HARD), "back at 31 blocks");
        h.succeed();
    }

    /**
     * DATA-018: a device in another dimension isn't told, at the same coordinates. Coming back is told
     * again, since a new dimension is a new level whose table on the client starts empty.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_only_the_players_own_dimension(GameTestHelper h) {
        Area a = new Area(h, 769);
        TestDevice.Entity d = device(a, 0, 0, 8);
        step(a);
        ServerLevel nether = a.level.getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "this server has no Nether");
        UUID id = UUID.randomUUID();
        FakePlayer here = new FakePlayer(a.level, new GameProfile(id, "zas_activity"));
        FakePlayer there = new FakePlayer(nether, new GameProfile(id, "zas_activity"));
        here.moveTo(a.point(4, 1, 0));
        there.moveTo(a.point(4, 1, 0));
        long t = AtmosTicks.now(a.level);
        h.assertValueEqual(told(h, here, t), one(d, DeviceActivity.HARD), "at home");
        h.assertValueEqual(told(h, there, t + AtmosTicks.PERIOD), List.of(), "in the Nether");
        h.assertValueEqual(told(h, here, t + 2 * AtmosTicks.PERIOD), one(d, DeviceActivity.HARD), "back again");
        h.succeed();
    }

    /** DATA-018: more than a message holds wait for the next, so there is a bound on what a player costs. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_a_large_station_takes_more_than_one_message(GameTestHelper h) {
        Area a = new Area(h, 770);
        int devices = DeviceActivityPayload.MAX_CHANGES + 17;
        for (int i = 0; i < devices; i++) {
            device(a, i % 12, i / 12, 8);
        }
        step(a);
        FakePlayer p = player(a, 6, 6);
        long t = AtmosTicks.now(a.level);
        h.assertValueEqual(told(h, p, t).size(), DeviceActivityPayload.MAX_CHANGES, "the first message");
        h.assertValueEqual(told(h, p, t + AtmosTicks.PERIOD).size(), 17, "the second message");
        h.assertValueEqual(told(h, p, t + 2 * AtmosTicks.PERIOD), List.of(), "then nothing");
        h.succeed();
    }

    /** DATA-018: the bytes, measured: 9 a change, and 21 a message with its channel's name. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_018_what_a_message_costs(GameTestHelper h) {
        FriendlyByteBuf name = new FriendlyByteBuf(Unpooled.buffer());
        ResourceLocation.STREAM_CODEC.encode(name, DeviceActivityPayload.TYPE.id());
        h.assertValueEqual(1 + name.readableBytes(), 20, "a packet's id and channel");
        for (int n : new int[] {0, 1, 10, DeviceActivityPayload.MAX_CHANGES}) {
            List<Change> changes = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                changes.add(new Change(new BlockPos(-30_000_000 + i, -64, 30_000_000).asLong(), 1 + i % 3));
            }
            DeviceActivityPayload payload = new DeviceActivityPayload(changes);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            DeviceActivityPayload.CODEC.encode(buf, payload);
            h.assertValueEqual(buf.readableBytes(), 1 + 9 * n, "bytes for " + n + " changes");
            h.assertValueEqual(DeviceActivityPayload.CODEC.decode(buf), payload, "the round trip of " + n);
        }
        h.succeed();
    }

    /** Steps until a vent has read the air in front and is moving gas, at most 8 times. */
    private static void settle(Area a, VentBlockEntity vent) {
        for (int i = 0; i < 8 && vent.status() != VentStatus.MOVING; i++) {
            a.step();
        }
    }

    /** DATA-017: a vent on a full line filling a vacuum reads as hard, and idle once the room is full. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_017_a_vent_filling_a_vacuum_reads_hard_until_the_room_is_full(GameTestHelper h) {
        Area a = new Area(h, 771);
        GasDeviceTests.room(a);
        a.settle(GasMutations.remove(
                a.level, a.point(2, 2, 2), a.zone(2, 2, 2).mixture().moles(), Area.SOURCE));
        h.assertTrue(a.zone(2, 2, 2).mixture().pressure() < 1e-6, "the room isn't a vacuum");
        VentBlockEntity vent = GasDeviceTests.ventRig(a);
        vent.network().gas().add(new GasParcel(Map.of(N2, 1_500.0), T));
        vent.command(VentSettings.blow(50));
        settle(a, vent);
        h.assertTrue(vent.status() == VentStatus.MOVING, "the vent isn't moving gas: " + vent.status());
        FakePlayer p = player(a, 2, 2);
        long t = AtmosTicks.now(a.level);
        h.assertValueEqual(told(h, p, t), one(vent.getBlockPos(), DeviceActivity.HARD), "filling");
        for (int i = 0; i < 60; i++) {
            a.step();
        }
        h.assertValueEqual(
                told(h, p, t + AtmosTicks.PERIOD), one(vent.getBlockPos(), DeviceActivity.IDLE), "the room is full");
        h.succeed();
    }

    /** DATA-017: a vent siphoning reports too, and one that loses its power goes idle at once. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_017_a_vent_siphoning_reads_hard_and_loses_it_with_its_power(GameTestHelper h) {
        Area a = new Area(h, 772);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 1_000.0));
        VentBlockEntity vent = GasDeviceTests.ventRig(a);
        vent.command(VentSettings.siphon(0));
        settle(a, vent);
        h.assertTrue(vent.status() == VentStatus.MOVING, "the vent isn't moving gas");
        FakePlayer p = player(a, 2, 2);
        long t = AtmosTicks.now(a.level);
        h.assertValueEqual(told(h, p, t), one(vent.getBlockPos(), DeviceActivity.HARD), "siphoning");
        vent.power().setTestPowered(false);
        a.step();
        h.assertTrue(vent.status() == VentStatus.OFF, "the vent still runs: " + vent.status());
        h.assertValueEqual(
                told(h, p, t + AtmosTicks.PERIOD), one(vent.getBlockPos(), DeviceActivity.IDLE), "without power");
        h.succeed();
    }

    private static ScrubberBlockEntity scrubberRig(Area a, Map<ResourceLocation, Double> air) {
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, air);
        a.place(0, 2, 2, ScrubberTests.scrubber(Direction.EAST));
        a.tick();
        ScrubberBlockEntity s = a.entity(0, 2, 2);
        s.power().setTestPowered(true);
        return s;
    }

    private static void settle(Area a, ScrubberBlockEntity s) {
        for (int i = 0; i < 8 && s.status() != ScrubberStatus.MOVING; i++) {
            a.step();
        }
    }

    /** DATA-017: a scrubber siphoning reads as hard, and one that is switched off goes idle at once. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_017_a_scrubber_siphoning_reads_hard_and_off_reads_idle(GameTestHelper h) {
        Area a = new Area(h, 773);
        ScrubberBlockEntity s = scrubberRig(a, Map.of(N2, 800.0, Area.O2, 200.0));
        s.command(ScrubberMode.SIPHON, GasSelection.DEFAULT);
        settle(a, s);
        h.assertTrue(s.status() == ScrubberStatus.MOVING, "the scrubber isn't moving gas");
        FakePlayer p = player(a, 2, 2);
        long t = AtmosTicks.now(a.level);
        h.assertValueEqual(told(h, p, t), one(s.getBlockPos(), DeviceActivity.HARD), "siphoning");
        s.command(ScrubberMode.OFF, GasSelection.DEFAULT);
        a.step();
        h.assertValueEqual(told(h, p, t + AtmosTicks.PERIOD), one(s.getBlockPos(), DeviceActivity.IDLE), "off");
        h.succeed();
    }

    /** DATA-017: a scrubber with nothing to take reads as idle, even while it is running. */
    @GameTest(template = "empty", batch = BATCH)
    public static void data_017_a_scrubber_with_nothing_to_take_reads_idle(GameTestHelper h) {
        Area a = new Area(h, 774);
        ScrubberBlockEntity s = scrubberRig(a, Map.of(N2, 800.0, Area.O2, 200.0));
        s.command(ScrubberMode.SCRUB, GasSelection.DEFAULT);
        for (int i = 0; i < 8; i++) {
            a.step();
        }
        h.assertTrue(s.status() == ScrubberStatus.NOTHING, "its status: " + s.status());
        h.assertValueEqual(told(h, player(a, 2, 2), AtmosTicks.now(a.level)), List.of(), "nothing to take");
        h.succeed();
    }

    // ---- CLIENT-019: what a client makes of what it is told ---------------------------------------

    /** The level a player is told of the one device working near it, or idle if it is told of none. */
    private static int toldLevel(Area a) {
        List<Change> changes = told(a.h, player(a, 2, 2), AtmosTicks.now(a.level));
        a.h.assertTrue(changes.size() <= 1, "told of more than one device: " + changes);
        return changes.isEmpty() ? DeviceActivity.IDLE : changes.get(0).level();
    }

    /** A vent filling a vacuum blows out of its grille at its hardest: the owner's first example. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_vent_filling_a_vacuum_shows_blowing_out_of_its_grille(GameTestHelper h) {
        Area a = new Area(h, 874);
        GasDeviceTests.room(a);
        a.settle(GasMutations.remove(
                a.level, a.point(2, 2, 2), a.zone(2, 2, 2).mixture().moles(), Area.SOURCE));
        VentBlockEntity vent = GasDeviceTests.ventRig(a);
        vent.network().gas().add(new GasParcel(Map.of(N2, 1_500.0), T));
        vent.command(VentSettings.blow(50));
        settle(a, vent);
        BlockState state = vent.getBlockState();
        h.assertValueEqual(state.getValue(VentBlock.FACING), Direction.EAST, "the way its grille looks");
        double amount = DeviceJets.vent(state.getValue(VentMode.PROPERTY), toldLevel(a));
        h.assertTrue(amount == DeviceActivity.HARD, "its jet's amount: " + amount);
        h.assertFalse(JetPlan.of(GasJetKind.DEVICE, amount).inward(), "it shows gas going in");
        h.succeed();
    }

    /** A vent can siphon as well as blow, and shows which: gas going in, and nothing once it is off. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_vent_siphoning_shows_drawing_in_until_it_is_off(GameTestHelper h) {
        Area a = new Area(h, 875);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 1_000.0));
        VentBlockEntity vent = GasDeviceTests.ventRig(a);
        vent.command(VentSettings.siphon(0));
        settle(a, vent);
        double amount = DeviceJets.vent(vent.getBlockState().getValue(VentMode.PROPERTY), toldLevel(a));
        h.assertTrue(amount == -DeviceActivity.HARD, "its jet's amount: " + amount);
        h.assertTrue(JetPlan.of(GasJetKind.DEVICE, amount).inward(), "it shows gas going out");

        vent.command(VentSettings.OFF);
        a.step();
        // The state says off at once, so it shows nothing even with the table's old level.
        h.assertValueEqual(vent.getBlockState().getValue(VentMode.PROPERTY), VentMode.OFF, "its state");
        h.assertTrue(
                DeviceJets.vent(vent.getBlockState().getValue(VentMode.PROPERTY), DeviceActivity.HARD) == 0,
                "a vent that is off shows a jet");
        h.succeed();
    }

    /** A vent with no power has its state say off too, whatever it was set to. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_vent_without_power_shows_nothing(GameTestHelper h) {
        Area a = new Area(h, 876);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 1_000.0));
        VentBlockEntity vent = GasDeviceTests.ventRig(a);
        vent.command(VentSettings.siphon(0));
        settle(a, vent);
        vent.power().setTestPowered(false);
        a.step();
        h.assertValueEqual(vent.getBlockState().getValue(VentMode.PROPERTY), VentMode.OFF, "its state");
        h.succeed();
    }

    /** A scrubber working hard draws in at its hardest: the owner's second example. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_scrubber_working_hard_shows_drawing_in(GameTestHelper h) {
        Area a = new Area(h, 877);
        ScrubberBlockEntity s = scrubberRig(a, Map.of(N2, 800.0, Area.O2, 200.0));
        s.command(ScrubberMode.PANIC, GasSelection.DEFAULT);
        settle(a, s);
        BlockState state = s.getBlockState();
        h.assertValueEqual(state.getValue(ScrubberBlock.FACING), Direction.EAST, "the way its grille looks");
        double amount = DeviceJets.scrubber(state.getValue(ScrubberMode.PROPERTY), toldLevel(a));
        h.assertTrue(amount == -DeviceActivity.HARD, "its jet's amount: " + amount);
        h.assertTrue(JetPlan.of(GasJetKind.DEVICE, amount).inward(), "it shows gas going out");
        h.succeed();
    }

    /** A scrubber on 10 kPa of CO2 is working, not working hard, so it shows the smaller jet. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_scrubber_on_a_fair_amount_shows_the_smaller_jet(GameTestHelper h) {
        Area a = new Area(h, 878);
        ScrubberBlockEntity s = scrubberRig(a, Map.of(N2, 800.0, Area.O2, 200.0, ControllerPreset.CO2, 110.0));
        s.command(ScrubberMode.SCRUB, GasSelection.DEFAULT);
        settle(a, s);
        double amount = DeviceJets.scrubber(s.getBlockState().getValue(ScrubberMode.PROPERTY), toldLevel(a));
        h.assertTrue(amount == -DeviceActivity.WORKING, "its jet's amount: " + amount);
        h.succeed();
    }

    /** A scrubber on 1 kPa of CO2 is really running, and only trickling, so it shows nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_019_a_scrubber_at_a_trickle_shows_nothing(GameTestHelper h) {
        Area a = new Area(h, 879);
        ScrubberBlockEntity s = scrubberRig(a, Map.of(N2, 800.0, Area.O2, 200.0, ControllerPreset.CO2, 11.0));
        s.command(ScrubberMode.SCRUB, GasSelection.DEFAULT);
        settle(a, s);
        h.assertTrue(s.status() == ScrubberStatus.MOVING, "the scrubber isn't moving gas: " + s.status());
        int level = toldLevel(a);
        h.assertValueEqual(level, DeviceActivity.TRICKLE, "its level");
        double amount = DeviceJets.scrubber(s.getBlockState().getValue(ScrubberMode.PROPERTY), level);
        h.assertTrue(amount == 0, "a trickle shows a jet: " + amount);
        h.succeed();
    }
}
