package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.STONE;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.device.Comparators;
import io.github.west3436.zas.atmos.device.RedstoneControl;
import io.github.west3436.zas.atmos.device.RedstoneControlled;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.miner.GasMinerBlock;
import io.github.west3436.zas.atmos.miner.GasMinerBlockEntity;
import io.github.west3436.zas.atmos.miner.GasMinerMenu;
import io.github.west3436.zas.atmos.miner.GasMinerStatus;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.teg.TegBlock;
import io.github.west3436.zas.atmos.teg.TegBlockEntity;
import io.github.west3436.zas.atmos.teg.TegMenu;
import io.github.west3436.zas.atmos.teg.TegStatus;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineControllerMenu;
import io.github.west3436.zas.atmos.turbine.TurbineStatus;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.heat.HeatSettings;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The redstone setting and comparator output of the round's new devices (CONT-170, CONT-173,
 * CONT-175): the TEG core, the gas turbine's controller and the gas miner. Circulators, the turbine
 * and its compressor have neither: the controller has the engine's. Each test builds in its own {@link Area}, 6000 on, and steps the devices itself, but for
 * the one that waits for a real comparator.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NewDeviceAutomationTests {
    private static final String BATCH = "zas_atmos_new_device_automation";
    private static final BlockState SIGNAL = Blocks.REDSTONE_BLOCK.defaultBlockState();

    /** The TEG's west (left) and east (right) circulators' x, as in {@link TegTests}. */
    private static final int LEFT = 3;

    private static final int RIGHT = 5;

    private NewDeviceAutomationTests() {}

    // ---- Helpers ----------------------------------------------------------------------------------

    /** What a comparator reads from the block at a place. */
    private static int comparator(Area a, int x, int y, int z) {
        return a.get(x, y, z).getAnalogOutputSignal(a.level, a.abs(x, y, z));
    }

    /** A window's button that sets a redstone mode. */
    private static int button(RedstoneControl.Mode mode) {
        return RedstoneControl.FIRST_BUTTON + mode.ordinal();
    }

    /** A TEG core at (4, 2, 4) facing north, a circulator on each side passing gas south, as {@link TegTests} builds. */
    private static TegBlockEntity teg(Area a) {
        a.clear(0, 0, 8, 8);
        a.place(4, 2, 4, AtmosBlocks.TEG.get().defaultBlockState().setValue(TegBlock.FACING, Direction.NORTH));
        for (int x : new int[] {LEFT, RIGHT}) {
            PipeTests.cap(a, x, 2, 2, Direction.SOUTH);
            PipeTests.pipe(a, x, 2, 3);
            a.place(
                    x,
                    2,
                    4,
                    AtmosBlocks.CIRCULATOR
                            .get()
                            .defaultBlockState()
                            .setValue(InlineDeviceBlock.FACING, Direction.SOUTH));
            PipeTests.pipe(a, x, 2, 5);
            PipeTests.cap(a, x, 2, 6, Direction.NORTH);
        }
        return a.entity(4, 2, 4);
    }

    private static PipeNetwork in(Area a, int x) {
        return PipeTests.network(a, x, 2, 3);
    }

    private static PipeNetwork out(Area a, int x) {
        return PipeTests.network(a, x, 2, 5);
    }

    /** Empties the TEG's four networks and puts a hot stream at the left inlet and a cold one at the right. */
    private static void streams(Area a) {
        for (int x : new int[] {LEFT, RIGHT}) {
            in(a, x).gas().drain();
            out(a, x).gas().drain();
        }
        in(a, LEFT).gas().add(new GasParcel(Map.of(N2, 20.0), 600));
        in(a, RIGHT).gas().add(new GasParcel(Map.of(N2, 20.0), 250));
    }

    /** The TEG's work so far, J: its FE and what it carries toward the next. */
    private static double work(TegBlockEntity teg) {
        return teg.energy().getEnergyStored() * AtmosServerConfig.get(AtmosServerConfig.JOULES_PER_FE)
                + teg.workCarry();
    }

    /** A sealed 3 × 3 × 3 room of ambient air with a miner in its west wall facing in, as {@link GasMinerTests} builds. */
    private static GasMinerBlockEntity miner(Area a) {
        a.box(0, 0, 4, 4);
        a.place(
                0,
                2,
                2,
                AtmosBlocks.GAS_MINER.get().defaultBlockState().setValue(GasMinerBlock.FACING, Direction.EAST));
        a.fill(2, 2, 2, Map.of(N2, 1.0)); // forms the room's zone
        return a.entity(0, 2, 2);
    }

    private static double room(Area a) {
        return a.zone(2, 2, 2).mixture().pressure();
    }

    // ---- The redstone setting ---------------------------------------------------------------------

    /**
     * CONT-170, CONT-171: a TEG core's redstone setting. Ignoring, the default, it runs with a signal
     * as it always has. Held, it makes no FE and passes no work: its circulators pass their gas on
     * their own, at the temperature it came in at, and its front goes dark. Let go, it runs again.
     * Its window's buttons set the mode, and its data carries the mode and the signal.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_170_a_held_teg_makes_no_fe_and_its_circulators_pass_gas_unchanged(GameTestHelper h) {
        Area a = new Area(h, 6000);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            TegBlockEntity teg = teg(a);
            a.set(4, 3, 4, SIGNAL);
            streams(a);
            teg.setOn(true);
            h.assertValueEqual(teg.redstone().mode(), RedstoneControl.Mode.IGNORED, "a new core's mode");
            a.step();
            h.assertValueEqual(teg.status(), TegStatus.RUNNING, "an ignoring core with a signal");
            h.assertTrue(work(teg) > 0, "an ignoring core with a signal made no FE");
            h.assertFalse(teg.redstone().signal(), "an ignoring core read its signal");

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            TegMenu window = (TegMenu) teg.createMenu(1, player.getInventory(), player);
            a.set(4, 3, 4, Area.AIR);
            h.assertTrue(window.clickMenuButton(player, button(RedstoneControl.Mode.HIGH)), "the high button");
            h.assertValueEqual(teg.redstone().mode(), RedstoneControl.Mode.HIGH, "the mode its window set");
            h.assertTrue(RedstoneControl.holds(window.redstone()), "the window doesn't show it held");
            h.assertFalse(teg.drives(), "a held core drives its circulators");
            streams(a);
            double made = work(teg);
            for (int i = 0; i < 3; i++) {
                a.step();
            }
            a.close(work(teg), made, "a held core's work");
            h.assertValueEqual(teg.status(), TegStatus.OFF, "a held core's status");
            h.assertValueEqual(teg.powerPerTick(), 0.0, "a held core's output");
            h.assertValueEqual(a.get(4, 2, 4).getValue(TegBlock.OUTPUT), 0, "a held core's front");
            h.assertTrue(teg.on(), "redstone switched the core off");
            for (int x : new int[] {LEFT, RIGHT}) {
                h.assertTrue(
                        out(a, x).gas().totalMoles() > 1, "a held core's circulator at x " + x + " passed nothing");
            }
            a.close(out(a, LEFT).gas().temperature(), 600, "the hot stream past a held core");
            a.close(out(a, RIGHT).gas().temperature(), 250, "the cold stream past a held core");

            h.assertTrue(window.clickMenuButton(player, button(RedstoneControl.Mode.LOW)), "the low button");
            streams(a);
            a.step();
            h.assertValueEqual(teg.status(), TegStatus.RUNNING, "a low core with no signal");
            h.assertTrue(work(teg) > made, "a core let go made no FE");
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
        h.succeed();
    }

    /**
     * CONT-170, CONT-171: a gas turbine's redstone setting, read at its controller. Held, the engine is
     * as if switched off: the turbine lets nothing out of its chamber, the compressor releases nothing
     * from its feed, and the spinning shaft runs down to a stop. High with the signal, it runs, and
     * ignoring it runs too. The controller's window sets the mode; the turbine has no setting.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_170_a_held_turbine_moves_no_gas_and_spins_down(GameTestHelper h) {
        Area a = new Area(h, 6001);
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            TurbineRig r = TurbineRig.build(a).link(false).burn(0.5, false);
            h.assertFalse(r.turbine instanceof RedstoneControlled, "the turbine has a redstone setting");
            a.set(6, 6, 2, SIGNAL); // on the controller
            r.feed(20, 20);
            r.pressurise(300);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            TurbineControllerMenu window =
                    (TurbineControllerMenu) r.controller.createMenu(1, player.getInventory(), player);
            h.assertTrue(window.clickMenuButton(player, button(RedstoneControl.Mode.LOW)), "the low button");
            h.assertTrue(RedstoneControl.holds(window.redstone()), "the window doesn't show it held");
            r.turbine.setShaftEnergy(AtmosServerConfig.get(AtmosServerConfig.TURBINE_SPIN_ENERGY_KJ) * 1000 / 4);
            double chamber = r.chamber().mixture().totalMoles();
            double exhaust = r.exhaust().mixture().totalMoles();
            double fed = r.feedGas().totalMoles();
            double last = r.turbine.shaftEnergy();
            int i = 0;
            for (; i < 300 && r.turbine.rpm() > 0; i++) {
                r.step();
                h.assertTrue(r.turbine.shaftEnergy() <= last, "a held turbine's shaft sped up at round " + i);
                last = r.turbine.shaftEnergy();
                h.assertValueEqual(r.turbine.turbineWork(), 0.0, "a held turbine's work at round " + i);
                h.assertValueEqual(r.turbine.compressorWork(), 0.0, "a held compressor's work at round " + i);
                h.assertValueEqual(r.compressor.lastFuel(), 0.0, "a held compressor's fuel at round " + i);
            }
            h.assertValueEqual(r.turbine.rpm(), 0.0, "a held turbine's shaft after " + i + " rounds");
            h.assertValueEqual(r.turbine.status(), TurbineStatus.OFF, "a held turbine's status");
            h.assertValueEqual(r.controller.status(), TurbineStatus.OFF, "a held controller's status");
            h.assertTrue(r.controller.on(), "redstone switched the controller off");
            h.assertTrue(r.turbine.held().isEmpty(), "a held turbine holds gas on its way");
            a.close(r.chamber().mixture().totalMoles(), chamber, "the chamber's moles past a held turbine");
            a.close(r.exhaust().mixture().totalMoles(), exhaust, "the exhaust's moles past a held turbine");
            a.close(r.feedGas().totalMoles(), fed, "the feed of a held compressor");
            h.assertValueEqual(r.spin(8), 0, "a held turbine's blades");

            for (RedstoneControl.Mode mode : List.of(RedstoneControl.Mode.HIGH, RedstoneControl.Mode.IGNORED)) {
                r.controller.redstone().set(mode);
                r.pressurise(300);
                double exhaustBefore = r.exhaust().mixture().totalMoles();
                for (int j = 0; j < CellWatch.SETTLE_STEPS + 3; j++) {
                    r.step();
                }
                h.assertTrue(
                        r.exhaust().mixture().totalMoles() > exhaustBefore + 1e-6,
                        "a turbine " + mode.word() + " with a signal let nothing out");
                h.assertTrue(
                        r.controller.status() != TurbineStatus.OFF,
                        "a controller " + mode.word() + " with a signal is off");
            }
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
        h.succeed();
    }

    /**
     * CONT-170, CONT-171: a gas miner's redstone setting. Held, it adds nothing, its lamp goes out and
     * its window says so, but it still reads its room. High with the signal it fills its room to its
     * target, and its window's buttons set the mode past its gas buttons.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_170_a_held_gas_miner_adds_nothing(GameTestHelper h) {
        Area a = new Area(h, 6002);
        GasMinerBlockEntity m = miner(a);
        m.setGas(N2);
        m.setTarget(250);
        a.set(-1, 2, 2, SIGNAL);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        GasMinerMenu window = (GasMinerMenu) m.createMenu(1, player.getInventory(), player);
        h.assertTrue(window.clickMenuButton(player, button(RedstoneControl.Mode.LOW)), "the low button");
        h.assertValueEqual(m.redstone().mode(), RedstoneControl.Mode.LOW, "the mode its window set");
        m.setOn(true);
        h.assertFalse(a.get(0, 2, 2).getValue(GasMinerBlock.ON), "a held miner's lamp is lit");
        for (int i = 0; i < CellWatch.SETTLE_STEPS + 3; i++) {
            a.step();
        }
        double held = room(a);
        for (int i = 0; i < 10; i++) {
            a.step();
        }
        a.close(room(a), held, "the room of a held miner");
        h.assertTrue(held < 200, "the room filled under a held miner: " + held + " kPa");
        h.assertValueEqual(m.status(), GasMinerStatus.OFF, "a held miner's status");
        a.close(m.roomPressure(), held, "a held miner's reading of its room");
        h.assertTrue(RedstoneControl.holds(window.redstone()), "the window doesn't show it held");
        h.assertTrue(m.isOn(), "redstone switched the miner off");

        h.assertTrue(window.clickMenuButton(player, button(RedstoneControl.Mode.HIGH)), "the high button");
        for (int i = 0; i < 60 && m.status() != GasMinerStatus.AT_TARGET; i++) {
            a.step();
        }
        h.assertValueEqual(m.status(), GasMinerStatus.AT_TARGET, "a high miner with a signal");
        h.assertTrue(a.get(0, 2, 2).getValue(GasMinerBlock.ON), "a running miner's lamp is out");
        h.assertTrue(Math.abs(room(a) - 250) < 0.5, "the room's pressure: " + room(a));
        h.assertValueEqual(GasMinerMenu.gasButton(0), GasMinerMenu.GAS, "the first gas's button");
        int past = RedstoneControl.FIRST_BUTTON - GasMinerMenu.GAS;
        h.assertValueEqual(
                GasMinerMenu.gasButton(past), RedstoneControl.LAST_BUTTON + 1, "a gas button past the redstone ones");
        h.succeed();
    }

    /**
     * CONT-173: the new devices save their redstone setting only when it is set, and load it back; a
     * device saved without one loads as ignoring.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_173_the_new_devices_save_their_redstone_setting_only_when_set(GameTestHelper h) {
        Area a = new Area(h, 6003);
        a.clear(0, 0, 8, 4);
        a.place(1, 1, 1, AtmosBlocks.TEG.get().defaultBlockState());
        a.place(3, 1, 1, AtmosBlocks.TURBINE_CONTROLLER.get().defaultBlockState());
        a.place(5, 1, 1, AtmosBlocks.GAS_MINER.get().defaultBlockState());
        for (int x : new int[] {1, 3, 5}) {
            BlockEntity device = a.level.getBlockEntity(a.abs(x, 1, 1));
            RedstoneControl control = ((RedstoneControlled) device).redstone();
            String what = device.getClass().getSimpleName();
            CompoundTag plain = device.saveWithoutMetadata(a.level.registryAccess());
            h.assertFalse(plain.contains(RedstoneControl.KEY), what + " saved a redstone mode while ignoring");
            control.set(RedstoneControl.Mode.HIGH);
            CompoundTag set = device.saveWithoutMetadata(a.level.registryAccess());
            h.assertValueEqual(set.getString(RedstoneControl.KEY), "high", what + "'s saved mode");
            control.set(RedstoneControl.Mode.IGNORED);
            device.loadWithComponents(set, a.level.registryAccess());
            h.assertValueEqual(control.mode(), RedstoneControl.Mode.HIGH, what + "'s mode loaded back");
            device.loadWithComponents(plain, a.level.registryAccess());
            h.assertValueEqual(control.mode(), RedstoneControl.Mode.IGNORED, what + " loaded from a plain save");
        }
        h.succeed();
    }

    // ---- What a comparator reads ------------------------------------------------------------------

    /**
     * CONT-175: a TEG core and a gas turbine's controller read their FE store's fill on the containers'
     * scale; the turbine, the compressor and a circulator, which hold nothing, read nothing. A gas miner
     * reads its room's pressure over its target, 15 at it, and nothing while switched off.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_175_the_new_devices_comparator_readings(GameTestHelper h) {
        Area a = new Area(h, 6004);
        a.clear(0, 0, 12, 4);
        a.place(1, 1, 1, AtmosBlocks.TEG.get().defaultBlockState());
        a.place(3, 1, 1, AtmosBlocks.TURBINE_CONTROLLER.get().defaultBlockState());
        a.place(5, 1, 1, TurbineRig.part(true));
        a.place(7, 1, 1, AtmosBlocks.CIRCULATOR.get().defaultBlockState());
        a.place(9, 1, 1, TurbineRig.part(false));
        TegBlockEntity teg = a.entity(1, 1, 1);
        TurbineControllerBlockEntity t = a.entity(3, 1, 1);
        h.assertValueEqual(comparator(a, 1, 1, 1), 0, "an empty TEG core");
        h.assertValueEqual(comparator(a, 3, 1, 1), 0, "an empty turbine controller");
        int half = Comparators.fill(0.5);
        teg.energy().set(teg.energy().getMaxEnergyStored() / 2);
        t.energy().set(t.energy().getMaxEnergyStored() / 2);
        h.assertValueEqual(comparator(a, 1, 1, 1), half, "a TEG core half full");
        h.assertValueEqual(comparator(a, 3, 1, 1), half, "a turbine controller half full");
        teg.energy().set(1);
        t.energy().set(1);
        h.assertValueEqual(comparator(a, 1, 1, 1), 1, "a TEG core with 1 FE");
        h.assertValueEqual(comparator(a, 3, 1, 1), 1, "a turbine controller with 1 FE");
        teg.energy().set(teg.energy().getMaxEnergyStored());
        t.energy().set(t.energy().getMaxEnergyStored());
        h.assertValueEqual(comparator(a, 1, 1, 1), 15, "a full TEG core");
        h.assertValueEqual(comparator(a, 3, 1, 1), 15, "a full turbine controller");
        h.assertFalse(a.get(5, 1, 1).hasAnalogOutputSignal(), "a compressor gives a comparator reading");
        h.assertFalse(a.get(7, 1, 1).hasAnalogOutputSignal(), "a circulator gives a comparator reading");
        h.assertFalse(a.get(9, 1, 1).hasAnalogOutputSignal(), "a turbine gives a comparator reading");

        Area b = new Area(h, 6005);
        GasMinerBlockEntity m = miner(b);
        m.setGas(N2);
        m.setTarget(250);
        h.assertValueEqual(comparator(b, 0, 2, 2), 0, "a switched-off miner");
        m.setOn(true);
        for (int i = 0; i < CellWatch.SETTLE_STEPS + 1 && Double.isNaN(m.roomPressure()); i++) {
            b.step();
        }
        double share = m.roomPressure() / 250;
        h.assertTrue(share > 0.3 && share < 0.6, "the room's first share of its target: " + share);
        h.assertValueEqual(comparator(b, 0, 2, 2), Comparators.fill(share), "a miner's room on its way");
        for (int i = 0; i < 60 && m.status() != GasMinerStatus.AT_TARGET; i++) {
            b.step();
        }
        h.assertValueEqual(m.status(), GasMinerStatus.AT_TARGET, "the miner's state");
        h.assertValueEqual(comparator(b, 0, 2, 2), 15, "a miner's room at its target");
        m.setTarget(500);
        b.step();
        h.assertValueEqual(comparator(b, 0, 2, 2), Comparators.fill(m.roomPressure() / 500), "a raised target");
        h.assertTrue(comparator(b, 0, 2, 2) < 15, "a miner below a raised target reads full");
        m.setOn(false);
        b.step();
        h.assertValueEqual(comparator(b, 0, 2, 2), 0, "a miner switched off");
        h.succeed();
    }

    /**
     * CONT-175: a real comparator behind a gas miner follows its room as the miner steps, which tells
     * the comparators around it when its signal changes; held by redstone, it still reads its room.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_175_a_comparator_follows_a_gas_miner(GameTestHelper h) {
        Area a = new Area(h, 6006);
        a.forceLoad(true);
        GasMinerBlockEntity m = miner(a);
        m.setGas(N2);
        m.setTarget(250);
        a.set(-1, 1, 2, STONE);
        a.set(-1, 2, 2, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.EAST));
        BlockPos at = a.abs(-1, 2, 2);
        h.startSequence()
                .thenExecute(() -> {
                    m.setOn(true);
                    for (int i = 0; i < 60 && m.status() != GasMinerStatus.AT_TARGET; i++) {
                        a.step();
                    }
                })
                .thenWaitUntil(() -> h.assertValueEqual(output(a, at), 15, "the comparator at the target"))
                .thenExecute(() -> {
                    // Held, so it reads its room without filling it toward the raised target.
                    m.redstone().set(RedstoneControl.Mode.HIGH);
                    m.setTarget(GasMinerBlockEntity.maxTarget());
                    a.step();
                })
                .thenWaitUntil(() -> h.assertValueEqual(output(a, at), 1, "the comparator below a raised target"))
                .thenExecute(() -> {
                    a.clear(-1, -1, 5, 5);
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    private static int output(Area a, BlockPos at) {
        return a.level.getBlockEntity(at) instanceof ComparatorBlockEntity c ? c.getOutputSignal() : -1;
    }
}
