package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.controller.ScrubberOrder;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlock;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMath;
import io.github.west3436.zas.atmos.scrubber.ScrubberMenu;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.scrubber.ScrubberStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The scrubber in the world (CONT-015, CONT-065, CONT-074, CONT-079, CONT-080, CONT-081). Like
 * {@link GasDeviceTests}, each builds in its own {@link Area} and drives device steps and atmos ticks
 * itself, in a batch of its own. Scrubbers run on test power (architecture §11.3).
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScrubberTests {
    static final String BATCH = "zas_atmos_scrubbers";

    private static final ResourceLocation CO2 = ControllerPreset.CO2;
    private static final ResourceLocation CO = ControllerPreset.CO;
    private static final ResourceLocation N2O = ControllerPreset.N2O;
    private static final ResourceLocation H2 = ControllerPreset.H2;

    /**
     * What goes into the room, on top of the ambient air a zone forms with: what a scrubber takes by
     * default, and what it leaves.
     */
    private static final Map<ResourceLocation, Double> MIXED =
            Map.of(N2, 800.0, O2, 200.0, CO2, 30.0, CO, 6.0, N2O, 4.0, H2, 2.0);

    private ScrubberTests() {}

    static BlockState scrubber(Direction facing) {
        return AtmosBlocks.SCRUBBER.get().defaultBlockState().setValue(ScrubberBlock.FACING, facing);
    }

    /** A sealed 3×3×3 room of mixed air, with a test-powered scrubber in its west wall, facing in. */
    private static ScrubberBlockEntity rig(Area a) {
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, MIXED);
        a.place(0, 2, 2, scrubber(Direction.EAST));
        a.tick();
        ScrubberBlockEntity s = a.entity(0, 2, 2);
        s.power().setTestPowered(true);
        return s;
    }

    /** Steps until the scrubber has read the air in front, at most 8 times. */
    private static void settle(Area a, ScrubberBlockEntity s) {
        for (int i = 0; i < 8 && (s.status() == ScrubberStatus.OFF || s.status() == ScrubberStatus.WAITING); i++) {
            a.step();
        }
    }

    /**
     * One more step on a settled scrubber: each gas it keeps falls by the sampled share of the room,
     * each other gas stays, and its network gets just what left.
     */
    private static void checkStep(
            GameTestHelper h,
            Area a,
            ScrubberBlockEntity s,
            double litres,
            Predicate<ResourceLocation> kept,
            String what) {
        GasMixture before = a.zone(2, 2, 2).mixture();
        double netBefore = s.network().gas().totalMoles();
        h.assertTrue(before.volume() == 27_000, "the room's volume: " + before.volume());
        double keep = 1 - litres / before.volume();
        a.step();
        GasMixture after = a.zone(2, 2, 2).mixture();
        double left = 0;
        for (Map.Entry<ResourceLocation, Double> e : before.moles().entrySet()) {
            ResourceLocation gas = e.getKey();
            boolean taken = kept.test(gas);
            a.close(after.moles(gas), taken ? e.getValue() * keep : e.getValue(), what + ": " + gas + " in the room");
            left += e.getValue() - after.moles(gas);
        }
        h.assertTrue(left > 0, what + ": no gas left the room");
        a.close(s.network().gas().totalMoles(), netBefore + left, what + ": the network holds what left the room");
    }

    /**
     * CONT-015: a scrubber removes CO₂ and CO from a room, and leaves O₂ and N₂. With its default
     * selection, CO₂ goes by name and CO and N₂O through {@code #zas:toxic}, and H₂ stays.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_015_a_scrubber_removes_co2_and_co_and_leaves_o2_and_n2(GameTestHelper h) {
        Area a = new Area(h, 700);
        ScrubberBlockEntity s = rig(a);
        h.assertValueEqual(s.selection(), GasSelection.DEFAULT, "a new scrubber's selection");
        s.command(ScrubberMode.SCRUB, s.selection());
        GasMixture start = a.zone(2, 2, 2).mixture();
        for (int i = 0; i < 40; i++) {
            a.step();
        }
        GasMixture room = a.zone(2, 2, 2).mixture();
        a.close(room.moles(O2), start.moles(O2), "O2 in the room");
        a.close(room.moles(N2), start.moles(N2), "N2 in the room");
        a.close(room.moles(H2), start.moles(H2), "H2 in the room");
        h.assertTrue(room.moles(CO2) < 30 * 0.6, "CO2 in the room: " + room.moles(CO2));
        h.assertTrue(room.moles(CO) < 6 * 0.6, "CO in the room: " + room.moles(CO));
        h.assertTrue(room.moles(N2O) < 4 * 0.6, "N2O in the room: " + room.moles(N2O));

        Map<ResourceLocation, Double> net = s.network().gas().moles();
        a.close(net.get(CO2) + room.moles(CO2), 30, "the CO2 in the room and the network");
        a.close(net.get(CO) + room.moles(CO), 6, "the CO in the room and the network");
        h.assertValueEqual(java.util.Set.copyOf(net.keySet()), java.util.Set.of(CO2, CO, N2O), "the network's gases");
        a.close(s.network().volume(), PipeVolumes.PORT, "a lone scrubber's network");
        h.assertValueEqual(a.get(0, 2, 2).getValue(ScrubberMode.PROPERTY), ScrubberMode.SCRUB, "the block state");
        h.assertValueEqual(s.status(), ScrubberStatus.MOVING, "the status");
        h.succeed();
    }

    /** CONT-015: a pipe joins a scrubber only on its back, whichever way it faces. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_015_a_pipe_joins_a_scrubber_only_on_its_back(GameTestHelper h) {
        Area a = new Area(h, 701);
        for (Direction facing : Direction.values()) {
            a.clear(0, 0, 6, 6);
            a.place(3, 3, 3, scrubber(facing));
            for (Direction d : Direction.values()) {
                BlockPos p = new BlockPos(3, 3, 3).relative(d);
                a.place(p.getX(), p.getY(), p.getZ(), GasDeviceTests.pipe());
            }
            for (Direction d : Direction.values()) {
                BlockPos p = new BlockPos(3, 3, 3).relative(d);
                boolean expect = d == facing.getOpposite();
                h.assertTrue(
                        a.get(p.getX(), p.getY(), p.getZ()).getValue(PipeBlock.CONNECTIONS.get(d.getOpposite()))
                                == expect,
                        "scrubber facing " + facing + ": pipe on its " + d + " side joined " + !expect);
            }
        }
        h.succeed();
    }

    /** CONT-079: scrubbing keeps only the selected gases of its 500 L sample. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_079_scrubbing_keeps_only_the_selected_gases(GameTestHelper h) {
        Area a = new Area(h, 702);
        ScrubberBlockEntity s = rig(a);
        GasSelection selection = GasSelection.of(List.of(CO2), List.of(GasTags.TOXIC));
        s.command(ScrubberMode.SCRUB, selection);
        settle(a, s);
        checkStep(h, a, s, ScrubberMath.SAMPLE_LITRES, selection, "scrubbing");
        h.assertValueEqual(a.get(0, 2, 2).getValue(ScrubberMode.PROPERTY), ScrubberMode.SCRUB, "the block state");

        // Another selection takes other gases: O₂ by name, the flammable H₂ and CO through a tag.
        GasSelection other = GasSelection.of(List.of(O2), List.of(GasTags.FLAMMABLE));
        s.command(ScrubberMode.SCRUB, other);
        checkStep(h, a, s, ScrubberMath.SAMPLE_LITRES, other, "another selection");
        h.assertTrue(other.test(H2) && other.test(CO) && !other.test(CO2), "the tag's members");
        h.succeed();
    }

    /** CONT-079: siphon keeps its whole 500 L sample. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_079_siphon_keeps_its_whole_sample(GameTestHelper h) {
        Area a = new Area(h, 703);
        ScrubberBlockEntity s = rig(a);
        s.command(ScrubberMode.SIPHON, GasSelection.DEFAULT);
        settle(a, s);
        checkStep(h, a, s, ScrubberMath.SAMPLE_LITRES, ScrubberMath.EVERY_GAS, "siphon");
        h.assertValueEqual(a.get(0, 2, 2).getValue(ScrubberMode.PROPERTY), ScrubberMode.SIPHON, "the block state");
        h.succeed();
    }

    /** CONT-079: panic is siphon at the panic rate, 1,000 L a second. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_079_panic_is_siphon_at_the_panic_rate(GameTestHelper h) {
        Area a = new Area(h, 704);
        ScrubberBlockEntity s = rig(a);
        s.command(ScrubberMode.PANIC, GasSelection.DEFAULT);
        settle(a, s);
        checkStep(h, a, s, ScrubberMath.PANIC_LITRES, ScrubberMath.EVERY_GAS, "panic");
        h.assertValueEqual(a.get(0, 2, 2).getValue(ScrubberMode.PROPERTY), ScrubberMode.PANIC, "the block state");
        h.succeed();
    }

    /**
     * CONT-081: a scrubber stops once its buffer reaches 50 atm, takes up again when the buffer falls,
     * and its last sample stops at 50 atm. Its network is its buffer: a scrubber's 200 L is a member
     * of it, as any device's is (architecture §11.4).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_081_a_scrubber_stops_at_50_atm_in_its_buffer(GameTestHelper h) {
        Area a = new Area(h, 705);
        ScrubberBlockEntity s = rig(a);
        s.command(ScrubberMode.PANIC, GasSelection.DEFAULT);
        settle(a, s);
        double room = a.zone(2, 2, 2).mixture().totalMoles();

        double full = ScrubberMath.STALL_KPA * PipeVolumes.PORT / (GasMixture.GAS_CONSTANT * T);
        s.network().gas().drain();
        s.network().gas().add(new GasParcel(Map.of(N2, full), T));
        a.step();
        a.step();
        h.assertValueEqual(s.status(), ScrubberStatus.STALLED, "at 50 atm");
        a.close(a.zone(2, 2, 2).mixture().totalMoles(), room, "the room with the scrubber stalled");

        // Just under 50 atm, it takes what reaches 50 atm, then stops again.
        s.network().gas().takeFraction(0.001);
        double under = s.networkPressure();
        h.assertTrue(under < ScrubberMath.STALL_KPA, "the buffer is at " + under + " kPa");
        a.step();
        a.step();
        h.assertTrue(a.zone(2, 2, 2).mixture().totalMoles() < room, "the scrubber didn't take up again");
        h.assertTrue(
                Math.abs(s.networkPressure() - ScrubberMath.STALL_KPA) < 0.01,
                "the buffer went to " + s.networkPressure() + " kPa");
        h.assertValueEqual(s.status(), ScrubberStatus.STALLED, "back at 50 atm");
        h.succeed();
    }

    /**
     * CONT-060, CONT-065: an unpowered scrubber does nothing and shows off; one that runs draws 15 FE/t,
     * and 30 in panic, and one that is off draws nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_065_a_scrubber_draws_only_while_on_and_twice_as_much_in_panic(GameTestHelper h) {
        Area a = new Area(h, 706);
        ScrubberBlockEntity s = rig(a);
        s.power().setTestPowered(false);
        double room = a.zone(2, 2, 2).mixture().totalMoles();
        s.command(ScrubberMode.SIPHON, GasSelection.DEFAULT);
        for (int i = 0; i < 6; i++) {
            a.step();
        }
        a.close(a.zone(2, 2, 2).mixture().totalMoles(), room, "the room with an unpowered scrubber");
        h.assertValueEqual(a.get(0, 2, 2).getValue(ScrubberMode.PROPERTY), ScrubberMode.OFF, "an unpowered scrubber");
        h.assertValueEqual(s.status(), ScrubberStatus.OFF, "an unpowered scrubber's status");

        int draw = AtmosServerConfig.get(AtmosServerConfig.SCRUBBER_DRAW);
        int panic = AtmosServerConfig.get(AtmosServerConfig.SCRUBBER_PANIC_DRAW);
        h.assertValueEqual(draw, 15, "the default draw");
        h.assertValueEqual(panic, 30, "the default draw in panic");
        h.assertValueEqual(s.power().draw(), draw, "siphoning");
        s.command(ScrubberMode.SCRUB, GasSelection.DEFAULT);
        h.assertValueEqual(s.power().draw(), draw, "scrubbing");
        s.command(ScrubberMode.PANIC, GasSelection.DEFAULT);
        h.assertValueEqual(s.power().draw(), panic, "in panic");
        s.command(ScrubberMode.OFF, GasSelection.DEFAULT);
        h.assertValueEqual(s.power().draw(), 0, "off");
        h.succeed();
    }

    /** CONT-015: a scrubber idle for want of a selected gas says so, and takes nothing. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_015_an_idle_scrubber_says_why(GameTestHelper h) {
        Area a = new Area(h, 707);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 800.0, O2, 200.0));
        double start = a.zone(2, 2, 2).mixture().totalMoles();
        a.place(0, 2, 2, scrubber(Direction.EAST));
        a.tick();
        ScrubberBlockEntity s = a.entity(0, 2, 2);
        s.power().setTestPowered(true);
        h.assertValueEqual(s.status(), ScrubberStatus.OFF, "a new scrubber's status");
        s.command(ScrubberMode.SCRUB, GasSelection.DEFAULT);
        settle(a, s);
        h.assertValueEqual(s.status(), ScrubberStatus.NOTHING, "with nothing to scrub");
        a.close(a.zone(2, 2, 2).mixture().totalMoles(), start, "the room");
        a.close(s.network().gas().totalMoles(), 0, "the network");
        h.succeed();
    }

    /** CONT-015: a scrubber keeps its mode and selection when it is saved and loaded. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_015_a_scrubber_keeps_its_settings(GameTestHelper h) {
        Area a = new Area(h, 708);
        a.clear(0, 0, 6, 4);
        a.place(1, 1, 2, scrubber(Direction.UP));
        a.place(4, 1, 2, scrubber(Direction.UP));
        ScrubberBlockEntity from = a.entity(1, 1, 2);
        ScrubberBlockEntity to = a.entity(4, 1, 2);
        GasSelection selection = GasSelection.of(List.of(O2), List.of(GasTags.FLAMMABLE));
        from.command(ScrubberMode.SIPHON, selection);
        to.loadWithComponents(from.saveWithoutMetadata(a.level.registryAccess()), a.level.registryAccess());
        h.assertValueEqual(to.mode(), ScrubberMode.SIPHON, "the mode");
        h.assertValueEqual(to.selection(), selection, "the selection");
        h.succeed();
    }

    /**
     * CONT-074, CONT-015: a scrubber's GUI checks its buttons and sets the mode and selection. While a
     * controller orders the scrubber it refuses every button and shows the controller and the order,
     * and it takes settings again once the orders stop. The controller is stood in for by calling
     * {@code scrubberOrder} as a controller does.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void cont_074_a_scrubbers_gui_is_checked_and_a_controller_wins(GameTestHelper h) {
        Area a = new Area(h, 709);
        a.forceLoad(true); // it waits in real time
        a.clear(0, 0, 6, 4);
        a.place(2, 1, 2, scrubber(Direction.UP));
        ScrubberBlockEntity s = a.entity(2, 1, 2);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ScrubberMenu menu = (ScrubberMenu) s.createMenu(1, player.getInventory(), player);

        // Buttons outside the GUI are refused.
        h.assertFalse(menu.clickMenuButton(player, ScrubberMenu.LAST_BUTTON + 1), "an unknown button was accepted");
        h.assertFalse(menu.clickMenuButton(player, -1), "a negative button was accepted");

        // Free: it shows and takes settings. The default selection is CO₂ and the toxic tag.
        h.assertFalse(menu.controlled(), "a new scrubber is controlled");
        int co2 = ScrubberMenu.GASES.indexOf(CO2);
        int oxygen = ScrubberMenu.GASES.indexOf(O2);
        int toxic = ScrubberMenu.GASES.size() + ScrubberMenu.TAGS.indexOf(GasTags.TOXIC);
        for (int option = 0; option < ScrubberMenu.OPTIONS; option++) {
            h.assertValueEqual(menu.selected(option), option == co2 || option == toxic, "option " + option);
        }
        for (ScrubberMode next : List.of(ScrubberMode.SCRUB, ScrubberMode.SIPHON, ScrubberMode.PANIC)) {
            h.assertTrue(
                    menu.clickMenuButton(player, ScrubberMenu.SET_MODE + next.ordinal()),
                    "the free scrubber refused its mode");
            h.assertValueEqual(s.mode(), next, "the mode");
            h.assertValueEqual(menu.mode(), next, "the GUI's mode");
        }
        h.assertTrue(menu.clickMenuButton(player, ScrubberMenu.FIRST_OPTION + oxygen), "the toggle was refused");
        h.assertTrue(s.selection().gases().contains(O2), "the toggle didn't select oxygen");
        h.assertTrue(menu.selected(oxygen), "the GUI doesn't show oxygen selected");
        h.assertTrue(menu.clickMenuButton(player, ScrubberMenu.FIRST_OPTION + toxic), "the tag's toggle was refused");
        h.assertFalse(s.selection().tags().contains(GasTags.TOXIC), "the toggle didn't take away the tag");

        // Controlled: the order wins, and the GUI says whose it is.
        BlockPos controller = new BlockPos(-70_000, -30, 123_456); // beyond a short, to check the halves
        s.scrubberOrder(ScrubberOrder.scrub(ControllerPreset.SCRUB_GASES, ControllerPreset.SCRUB_TAGS)
                .from(controller));
        h.assertTrue(s.controlled(), "the ordered scrubber isn't controlled");
        h.assertValueEqual(s.mode(), ScrubberMode.SCRUB, "the ordered mode");
        h.assertValueEqual(s.selection(), GasSelection.DEFAULT, "the ordered selection");
        h.assertValueEqual(s.controller(), Optional.of(controller), "the scrubber's controller");
        for (int id = ScrubberMenu.SET_MODE; id <= ScrubberMenu.LAST_BUTTON; id++) {
            h.assertFalse(menu.clickMenuButton(player, id), "the controlled scrubber took button " + id);
        }
        h.assertValueEqual(s.mode(), ScrubberMode.SCRUB, "the controlled mode");
        h.assertTrue(menu.controlled(), "the GUI doesn't show the scrubber controlled");
        h.assertValueEqual(menu.controller(), Optional.of(controller), "the GUI's controller");
        var tag = s.saveWithoutMetadata(a.level.registryAccess()); // what /data get block shows
        h.assertTrue(tag.getBoolean("Controlled"), "the scrubber's data doesn't show it controlled: " + tag);

        // Panic and off follow the order and keep the selection; an order names no controller, once.
        s.scrubberOrder(ScrubberOrder.PANIC);
        h.assertValueEqual(s.mode(), ScrubberMode.PANIC, "ordered to panic");
        h.assertValueEqual(s.selection(), GasSelection.DEFAULT, "the selection in panic");
        s.scrubberOrder(ScrubberOrder.OFF.from(controller));
        h.assertValueEqual(s.mode(), ScrubberMode.OFF, "ordered off");

        // Once the orders stop, the scrubber is the player's again.
        h.runAfterDelay(DevicePower.LEASE_TICKS + 1, () -> {
            h.assertFalse(s.controlled(), "the scrubber stayed controlled with no orders");
            h.assertTrue(menu.controller().isEmpty(), "the GUI still shows a controller");
            h.assertFalse(
                    s.saveWithoutMetadata(a.level.registryAccess()).contains("Controlled"),
                    "the freed scrubber's data shows it controlled");
            h.assertTrue(
                    menu.clickMenuButton(player, ScrubberMenu.SET_MODE + ScrubberMode.SCRUB.ordinal()),
                    "the freed scrubber refused its GUI");
            h.assertValueEqual(s.mode(), ScrubberMode.SCRUB, "the freed scrubber's mode");
            a.forceLoad(false);
            h.succeed();
        });
    }

    /**
     * CONT-015: in an open cell a scrubber takes from the ambient and forms no zone, as a siphoning
     * vent does.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_015_a_scrubber_in_an_open_cell_takes_from_the_ambient(GameTestHelper h) {
        Area a = new Area(h, 710);
        a.clear(0, 0, 4, 4);
        a.place(2, 1, 2, scrubber(Direction.NORTH));
        ScrubberBlockEntity s = a.entity(2, 1, 2);
        s.power().setTestPowered(true);
        s.command(ScrubberMode.SIPHON, GasSelection.DEFAULT);
        for (int i = 0; i <= CellWatch.SETTLE_STEPS; i++) {
            a.step();
        }
        h.assertTrue(s.network().gas().totalMoles() > 0, "the scrubber took nothing");
        h.assertTrue(Atmospheres.zoneAt(a.level, a.abs(2, 1, 1)).isEmpty(), "scrubbing formed a zone");
        h.succeed();
    }
}
