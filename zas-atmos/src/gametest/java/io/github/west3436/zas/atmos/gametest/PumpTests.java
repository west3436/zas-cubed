package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import io.github.west3436.zas.atmos.pump.PumpMenu;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The pressure pump in the world (CONT-011, CONT-076, CONT-060, CONT-065, CONT-074): a real pump
 * between two pipe networks, each run capped so nothing leaks. Each test builds in its own {@link
 * Site} and steps its devices itself, within one game tick, and has a batch of its own: the power
 * test because it waits in real time for a lease to run out.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PumpTests {
    private static final String BATCH = "zas_atmos_pumps";
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final double T = StandardAir.TEMPERATURE;
    private static final double R = GasMixture.GAS_CONSTANT;

    /** The output's volume: a pipe, a cap and the port's buffer, once. */
    private static final double RUN = 2 * PipeVolumes.PIPE + PipeVolumes.PORT;

    private PumpTests() {}

    private static BlockState pumpState(Direction facing) {
        return AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, facing);
    }

    /** A pipe's state as placed at a position, its connections worked out from its neighbours. */
    private static BlockState pipeAt(Site s, BlockPos pos) {
        return Block.updateFromNeighbourShapes(AtmosBlocks.PIPE.get().defaultBlockState(), s.level, pos);
    }

    private static void pipe(Site s, int x, int y, int z) {
        s.place(x, y, z, pipeAt(s, s.abs(x, y, z)), Object.class);
    }

    private static void cap(Site s, int x, int y, int z, Direction facing) {
        s.place(
                x,
                y,
                z,
                AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing),
                Object.class);
    }

    /** A pump at (x, 2, 2) facing east, with a pipe and a cap on each side, from x − 2 to x + 2. */
    private static PressurePumpBlockEntity line(Site s, int x) {
        cap(s, x - 2, 2, 2, Direction.EAST);
        pipe(s, x - 1, 2, 2);
        PressurePumpBlockEntity pump = s.place(x, 2, 2, pumpState(Direction.EAST), PressurePumpBlockEntity.class);
        pipe(s, x + 1, 2, 2);
        cap(s, x + 2, 2, 2, Direction.WEST);
        return pump;
    }

    private static GasStore in(PumpBlockEntity pump) {
        return pump.input().network().gas();
    }

    private static GasStore out(PumpBlockEntity pump) {
        return pump.output().network().gas();
    }

    /** Burns a generator for some game ticks, as its ticker would. */
    private static void burn(GeneratorBlockEntity g, int ticks) {
        for (int i = 0; i < ticks; i++) {
            g.serverTick();
        }
    }

    /**
     * A pump keeps its two networks apart and moves gas from its input to its output, at most 100 L/s
     * at input conditions (CONT-076), until the output is at the target (CONT-011), counting the
     * output's buffer once. Moles and thermal energy are conserved, and it says what it is doing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_011_a_real_pump_brings_its_output_to_its_target(GameTestHelper h) {
        Site s = new Site(h, 550);
        s.clear();
        PressurePumpBlockEntity pump = line(s, 4);
        pump.power().setTestPowered(true);
        h.assertTrue(pump.input().network() != pump.output().network(), "the pump joined its two networks");
        s.close(in(pump).volume(), RUN, 1e-9, "the input network's volume");
        s.close(out(pump).volume(), RUN, 1e-9, "the output network's volume: the buffer counts once");

        // Off, it does nothing, powered or not.
        in(pump).add(new GasParcel(Map.of(N2, 300.0), 600));
        out(pump).add(new GasParcel(Map.of(O2, 1.0), T));
        double moles = in(pump).totalMoles() + out(pump).totalMoles();
        double energy = in(pump).thermalEnergy() + out(pump).thermalEnergy();
        pump.step();
        s.close(out(pump).totalMoles(), 1.0, 1e-9, "the output with the pump off");
        h.assertValueEqual(pump.status(), InlineStatus.OFF, "the status while off");
        h.assertFalse(s.level.getBlockState(pump.getBlockPos()).getValue(InlineDeviceBlock.ON), "the state while off");

        // On, far from a high target: 100 L at the input's conditions, no more.
        pump.setOn(true);
        pump.set(4_500);
        double expected = in(pump).totalMoles() * PumpFlow.MAX_RATE / RUN;
        double before = out(pump).totalMoles();
        pump.step();
        s.close(out(pump).totalMoles() - before, expected, 1e-9, "the first second's moles");
        h.assertValueEqual(pump.status(), InlineStatus.MOVING, "the status while moving");
        h.assertTrue(s.level.getBlockState(pump.getBlockPos()).getValue(InlineDeviceBlock.ON), "the state while on");

        // A low target: it stops there.
        double target = out(pump).pressure() + 50;
        pump.set(target);
        for (int i = 0; i < 20; i++) {
            pump.step();
        }
        s.close(out(pump).pressure(), target, 1e-9, "the output at its target");
        h.assertValueEqual(pump.status(), InlineStatus.TARGET_REACHED, "the status at the target");
        s.close(in(pump).totalMoles() + out(pump).totalMoles(), moles, 1e-9, "the moles");
        s.close(in(pump).thermalEnergy() + out(pump).thermalEnergy(), energy, 1e-4, "the thermal energy");

        // It stays there, and a target below the output moves nothing back.
        double held = out(pump).totalMoles();
        pump.set(1);
        pump.step();
        s.close(out(pump).totalMoles(), held, 1e-12, "the output above its target");

        // With the input drained, it says so.
        pump.set(4_500);
        for (int i = 0; i < 60; i++) {
            pump.step();
        }
        h.assertTrue(in(pump).totalMoles() < 1e-3, "the input didn't drain: " + in(pump).totalMoles());
        h.assertValueEqual(pump.status(), InlineStatus.INPUT_EMPTY, "the status with no gas");
        s.clear();
        h.succeed();
    }

    /**
     * Pipes join a pump only at its two ends, whichever way it faces, and each end is its own node. A
     * pipe from end to end joins its ports into one network, and it then moves nothing. Removed, it
     * releases each port's share into its cell, and each port saves its share.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_011_pipes_join_a_pump_only_at_its_ends(GameTestHelper h) {
        Site s = new Site(h, 551);
        s.clear();
        for (Direction facing : Direction.values()) {
            BlockPos at = s.abs(4, 3, 3);
            s.level.setBlockAndUpdate(at, pumpState(facing));
            for (Direction d : Direction.values()) {
                BlockPos beside = at.relative(d);
                s.level.setBlockAndUpdate(beside, pipeAt(s, beside));
                boolean connected = s.level.getBlockState(beside).getValue(PipeBlock.CONNECTIONS.get(d.getOpposite()));
                h.assertValueEqual(connected, d.getAxis() == facing.getAxis(), facing + ": a pipe to the " + d);
                s.level.setBlockAndUpdate(beside, Site.AIR);
            }
            s.level.setBlockAndUpdate(at, Site.AIR);
        }

        // The shape turns with the pump: its flanges reach the block's edges along its axis only.
        for (Direction facing : Direction.values()) {
            var shape = pumpState(facing).getShape(s.level, BlockPos.ZERO, CollisionContext.empty());
            for (Direction.Axis axis : Direction.Axis.values()) {
                boolean along = axis == facing.getAxis();
                s.close(shape.min(axis), along ? 0 : 0.25, 1e-9, facing + ": the shape's low " + axis);
                s.close(shape.max(axis), along ? 1 : 0.75, 1e-9, facing + ": the shape's high " + axis);
            }
        }

        // A pipe around it joins its ends: one network, so there is nothing to pump.
        PressurePumpBlockEntity pump = s.place(4, 2, 2, pumpState(Direction.EAST), PressurePumpBlockEntity.class);
        for (int[] p : new int[][] {{3, 2, 2}, {3, 3, 2}, {4, 3, 2}, {5, 3, 2}, {5, 2, 2}}) {
            pipe(s, p[0], p[1], p[2]);
        }
        pump.power().setTestPowered(true);
        pump.setOn(true);
        h.assertTrue(pump.input().network() == pump.output().network(), "the loop isn't one network");
        s.close(in(pump).volume(), 2 * PipeVolumes.PORT + 5 * PipeVolumes.PIPE, 1e-9, "the loop's volume");
        in(pump).add(new GasParcel(Map.of(N2, 100.0), T));
        pump.set(4_500);
        pump.step();
        s.close(in(pump).totalMoles(), 100.0, 1e-9, "the loop's gas");
        h.assertValueEqual(pump.status(), InlineStatus.SAME_NETWORK, "the status on a loop");

        // Break the loop, with the pump off so it moves nothing: each port's share is by volume, as a
        // network's split is (CONT-003).
        double share = 100.0 * PipeVolumes.PORT / in(pump).volume();
        pump.setOn(false);
        s.set(4, 3, 2, Site.AIR);
        pump.step();
        pump.setOn(true);
        h.assertTrue(pump.input().network() != pump.output().network(), "the broken loop is still one network");
        double perLitre = 100.0 / (2 * PipeVolumes.PORT + 5 * PipeVolumes.PIPE);
        s.close(in(pump).totalMoles(), perLitre * (PipeVolumes.PORT + 2 * PipeVolumes.PIPE), 1e-9, "the input's share");
        s.close(
                out(pump).totalMoles(),
                perLitre * (PipeVolumes.PORT + 2 * PipeVolumes.PIPE),
                1e-9,
                "the output's share");

        // Saved, each port keeps its own share.
        CompoundTag saved = pump.saveWithoutMetadata(s.level.registryAccess());
        h.assertValueEqual(saved.getList("Ports", 10).size(), 2, "the ports saved");
        PressurePumpBlockEntity copy = new PressurePumpBlockEntity(pump.getBlockPos(), pump.getBlockState());
        copy.loadWithComponents(saved, s.level.registryAccess());
        s.close(copy.input().stored().totalMoles(), share, 1e-9, "the input's saved share");
        s.close(copy.output().stored().totalMoles(), share, 1e-9, "the output's saved share");
        h.assertTrue(copy.on(), "the switch wasn't saved");
        s.close(copy.setting(), 4_500, 1e-9, "the target wasn't saved");

        // Removed in a room, it releases each port's share into its cell (CONT-009).
        s.clear();
        s.room(0, 4);
        s.settle(GasMutations.add(s.level, s.point(2, 2, 2), new GasParcel(Map.of(N2, 1.0), T), Site.SOURCE));
        double room = s.zone(2, 2, 2).mixture().moles(O2);
        PressurePumpBlockEntity lone = s.place(2, 2, 2, pumpState(Direction.EAST), PressurePumpBlockEntity.class);
        in(lone).add(new GasParcel(Map.of(O2, 3.0), T));
        out(lone).add(new GasParcel(Map.of(O2, 4.0), T));
        s.set(2, 2, 2, Site.AIR);
        s.ticks(1);
        s.close(s.zone(2, 2, 2).mixture().moles(O2) - room, 7.0, 1e-9, "the shares released into the room");
        s.clear();
        h.succeed();
    }

    /**
     * Placed, a pump's gas goes the way the player looks. Looking down at a run on the floor, it lines
     * up with the run instead of pointing into the floor, the way along the run they look.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_011_a_placed_pump_lines_up_with_its_pipes(GameTestHelper h) {
        Site s = new Site(h, 552);
        s.clear();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos at = s.abs(4, 2, 2);
        ItemStack stack = new ItemStack(AtmosItems.PRESSURE_PUMP.get());

        // No pipes: it goes the way they look, whichever that is. 60 degrees down is down.
        look(player, -90, 0); // east
        h.assertValueEqual(placed(player, stack, at), Direction.EAST, "looking east");
        look(player, -90, 60);
        h.assertValueEqual(placed(player, stack, at), Direction.DOWN, "looking steeply down");

        // A run along x beside it: it lines up, the way along the run they look.
        pipe(s, 3, 2, 2);
        h.assertValueEqual(placed(player, stack, at), Direction.EAST, "looking steeply down and east");
        look(player, 90, 60); // west
        h.assertValueEqual(placed(player, stack, at), Direction.WEST, "looking steeply down and west");
        s.clear();
        h.succeed();
    }

    /** Turns a player's head, which is what placing reads. */
    private static void look(Player player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
    }

    private static Direction placed(Player player, ItemStack stack, BlockPos at) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        return AtmosBlocks.PRESSURE_PUMP.get().getStateForPlacement(context).getValue(InlineDeviceBlock.FACING);
    }

    /** The GUI's buttons switch the pump and step its target, clamped; unknown ids are refused (CONT-074). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_074_a_pumps_gui_is_checked_on_the_server(GameTestHelper h) {
        Site s = new Site(h, 553);
        s.clear();
        PressurePumpBlockEntity pump = line(s, 4);
        Player hand = h.makeMockPlayer(GameType.SURVIVAL);
        PumpMenu menu = (PumpMenu) pump.createMenu(1, hand.getInventory(), hand);

        h.assertFalse(menu.on(), "a new pump is on");
        s.close(pump.setting(), PressurePumpBlockEntity.DEFAULT_TARGET, 1e-9, "a new pump's target");
        h.assertValueEqual(menu.value(PumpMenu.DATA_SETTING), 101, "the GUI's target");

        h.assertTrue(menu.clickMenuButton(hand, InlineDeviceMenu.TOGGLE), "the switch was refused");
        h.assertTrue(pump.on(), "the switch didn't switch it on");
        h.assertTrue(menu.on(), "the GUI doesn't show it on");

        for (int id : new int[] {-1, PumpMenu.LAST_BUTTON + 1, 99}) {
            h.assertFalse(menu.clickMenuButton(hand, id), "button " + id + " wasn't refused");
        }
        s.close(pump.setting(), PressurePumpBlockEntity.DEFAULT_TARGET, 1e-9, "the target after refused buttons");

        // The four steps, then past each end of the range.
        double[] steps = {-100, -10, 10, 100};
        pump.set(1_000);
        for (int i = 0; i < steps.length; i++) {
            double before = pump.setting();
            h.assertTrue(menu.clickMenuButton(hand, PumpMenu.STEP_DOWN_BIG + i), "step " + i + " was refused");
            s.close(pump.setting(), before + steps[i], 1e-9, "step " + i);
        }
        pump.set(50);
        h.assertTrue(menu.clickMenuButton(hand, PumpMenu.STEP_DOWN_BIG), "the step down was refused");
        s.close(pump.setting(), 0, 1e-9, "the target stepped below 0");
        pump.set(4_450);
        h.assertTrue(menu.clickMenuButton(hand, PumpMenu.LAST_BUTTON), "the step up was refused");
        s.close(pump.setting(), PumpFlow.MAX_TARGET, 1e-9, "the target stepped above 4,500 kPa");
        h.assertValueEqual(menu.value(PumpMenu.DATA_SETTING), 4_500, "the GUI's target at the top");

        // The GUI shows the pressure at each end.
        in(pump).add(new GasParcel(Map.of(N2, 300 * RUN / (R * T)), T));
        out(pump).add(new GasParcel(Map.of(N2, 40 * RUN / (R * T)), T));
        h.assertValueEqual(menu.pressure(PumpBlockEntity.INPUT), 300, "the GUI's input");
        h.assertValueEqual(menu.pressure(PumpBlockEntity.OUTPUT), 40, "the GUI's output");
        s.clear();
        h.succeed();
    }

    /**
     * A network of ports alone reacts, with no pipe to run it (CONT-100), even with the pumps off. Two
     * pumps end to end make one, of two ports and 400 L, and it reacts once per atmos tick
     * though two members step: a 375 L parcel burns each time, so if each ran it, the hydrogen would
     * be gone sooner. A lone pump's open end is a network of one port. A port among pipes takes turns
     * with them, and the network still reacts once.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_100_a_network_of_ports_alone_reacts_once_per_atmos_tick(GameTestHelper h) {
        Site s = new Site(h, 555);
        s.clear();
        PressurePumpBlockEntity first = s.place(2, 2, 2, pumpState(Direction.EAST), PressurePumpBlockEntity.class);
        PressurePumpBlockEntity second = s.place(3, 2, 2, pumpState(Direction.EAST), PressurePumpBlockEntity.class);
        h.assertTrue(
                first.output().network() == second.input().network(), "two pumps' facing ports aren't one network");
        h.assertFalse(first.input().network() == first.output().network(), "a pump joined its own ports");
        s.close(out(first).volume(), 2 * PipeVolumes.PORT, 1e-9, "the network of two ports");

        GasStore between = out(first);
        between.add(flammable());
        for (int round = 1; round <= 2; round++) {
            GasMixture before = between.mixture();
            first.step();
            second.step();
            assertReacted(h, s, between, before, "two ports, round " + round);
        }

        // A lone end: one port, one reaction per step.
        GasStore end = in(first);
        end.add(flammable());
        GasMixture lone = end.mixture();
        first.step();
        assertReacted(h, s, end, lone, "one port");

        // A port among pipes: each member stepping in turn reacts the network once. It holds more than
        // the parcel, so some is left for the second round.
        s.clear();
        PressurePumpBlockEntity pump = s.place(4, 2, 2, pumpState(Direction.EAST), PressurePumpBlockEntity.class);
        for (int x = 5; x <= 7; x++) {
            pipe(s, x, 2, 2);
        }
        GasStore mixed = out(pump);
        s.close(mixed.volume(), PipeVolumes.PORT + 3 * PipeVolumes.PIPE, 1e-9, "the network of a port and pipes");
        mixed.add(flammable());
        for (int round = 1; round <= 2; round++) {
            GasMixture before = mixed.mixture();
            pump.step();
            for (int x = 5; x <= 7; x++) {
                s.entity(x, 2, 2, PipeNodeEntity.class).step();
            }
            assertReacted(h, s, mixed, before, "a port and pipes, round " + round);
        }
        s.clear();
        h.succeed();
    }

    /** Hydrogen above its autoignition point, with oxygen to spare. */
    private static GasParcel flammable() {
        return new GasParcel(Map.of(H2, 5.0, O2, 10.0, N2, 15.0), 900);
    }

    /** The store after one atmos tick is exactly what the API says one reaction leaves. */
    private static void assertReacted(GameTestHelper h, Site s, GasStore gas, GasMixture before, String what) {
        GasMixture expected = Reactions.applyStandalone(before, 1, false);
        h.assertTrue(expected != before, what + ": the API says nothing reacts, so the test proves nothing");
        s.close(gas.temperature(), expected.temperature(), 1e-9, what + ": temperature");
        for (ResourceLocation id : expected.moles().keySet()) {
            s.close(gas.moles(id), expected.moles(id), 1e-9, what + ": " + id);
        }
    }

    /**
     * A real pump between two networks, under a real controller that a real generator powers: with no
     * controller it has no power, even on and beside a burning generator (CONT-069). Under the
     * controller, in its zone with no link, it is in the gas-handling group (CONT-064) and the
     * controller pays 20 FE/t for it while it is on, with nothing drawn while it is off (CONT-065),
     * and it pumps. When the generator goes and the buffer is spent, its lease runs out and it stops
     * (CONT-060).
     */
    @GameTest(template = "empty", batch = "zas_atmos_pump_power", timeoutTicks = 400)
    public static void cont_060_a_real_pump_runs_on_a_controller_and_stops_when_its_power_is_cut(GameTestHelper h) {
        Site s = new Site(h, 554);
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        // The test waits in real time, so its chunks must stay loaded.
        s.level.setChunkForced(cx, cz, true);
        s.level.setChunkForced(cx + 1, cz, true);
        Runnable cleanup = () -> {
            s.clear();
            s.level.setChunkForced(cx, cz, false);
            s.level.setChunkForced(cx + 1, cz, false);
        };
        PressurePumpBlockEntity pump;
        AreaAirControllerBlockEntity c;
        try {
            s.clear();
            // A 7 × 3 × 3 room, holding the pump's line and a burning generator beside it.
            s.room(0, 8);
            pump = line(s, 4);
            GeneratorBlockEntity beside =
                    s.place(4, 2, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            beside.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            burn(beside, 20);
            h.assertTrue(beside.energy().getEnergyStored() > 0, "the generator holds no FE");
            s.ticks(2);
            s.settle(GasMutations.add(s.level, s.point(4, 3, 3), new GasParcel(Map.of(N2, 1.0), T), Site.SOURCE));

            h.assertValueEqual(pump.power().priority(), PowerPriority.GAS_HANDLING, "the pump's group");
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.PRESSURE_PUMP_DRAW), 20, "the pump's draw");
            h.assertValueEqual(pump.power().draw(), 0, "the draw of a pump that's off");
            h.assertValueEqual(pump.power().drawPerAtmosTick(), 0L, "the bill of a pump that's off");

            // Plenty of gas for the seconds below, far from the target, and well short of a burst.
            in(pump).add(new GasParcel(Map.of(N2, 300.0), T));
            pump.set(PumpFlow.MAX_TARGET);
            pump.setOn(true);
            h.assertValueEqual(pump.power().draw(), 20, "the draw of a pump that's on");
            h.assertValueEqual(
                    pump.power().drawPerAtmosTick(), 20L * AtmosTicks.PERIOD, "the bill of a pump that's on");

            // No controller: no power, on and beside a burning generator.
            s.cycles(3, pump);
            h.assertFalse(pump.power().isPowered(), "the pump has power with no controller");
            h.assertFalse(pump.running(), "the pump runs with no controller");
            s.close(out(pump).totalMoles(), 0, 1e-12, "the output with no controller");

            // A controller in its room, with a generator below it that has burned enough. It covers
            // the pump by its zone, and pays for itself and the pump, 25 FE/t, for one atmos tick.
            GeneratorBlockEntity g =
                    s.place(1, 1, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            burn(g, 40);
            int fe = g.energy().getEnergyStored();
            c = s.controller(1);
            s.cycle(c, pump);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertTrue(pump.power().isPowered(), "the controller didn't power its pump");
            h.assertTrue(pump.power().source() == c, "the pump's power isn't the controller's");
            int left = g.energy().getEnergyStored() + c.buffer().getEnergyStored();
            h.assertValueEqual(fe - left, 25 * AtmosTicks.PERIOD, "the FE for the controller and the pump");

            // It pumps.
            h.assertTrue(pump.running(), "the powered pump isn't running");
            h.assertValueEqual(pump.status(), InlineStatus.MOVING, "the powered pump's status");
            h.assertTrue(
                    s.level.getBlockState(pump.getBlockPos()).getValue(InlineDeviceBlock.ON),
                    "the powered pump's block state");
            s.close(out(pump).totalMoles(), 300.0 * PumpFlow.MAX_RATE / RUN, 1e-9, "the powered pump's first second");
            s.cycles(2, c, pump);

            // CONT-060: cut the power. Both generators go, and the buffer is spent.
            s.set(1, 1, 1, Site.AIR);
            s.set(4, 2, 1, Site.AIR);
            c.buffer().set(0);
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        PressurePumpBlockEntity p = pump;
        AreaAirControllerBlockEntity controller = c;
        h.runAfterDelay(DevicePower.LEASE_TICKS + 2L * AtmosTicks.PERIOD, () -> {
            try {
                h.assertFalse(controller.powered(), "the controller has power with no generator");
                h.assertFalse(p.power().isPowered(), "the pump's lease outlived its controller's power");
                h.assertFalse(p.running(), "the unpowered pump runs");
                h.assertTrue(p.on(), "cutting the power switched the pump off");
                p.step();
                double held = out(p).totalMoles();
                h.assertTrue(held >= 1, "the pump moved nothing before its power was cut");
                h.assertValueEqual(p.status(), InlineStatus.OFF, "the unpowered pump's status");
                h.assertFalse(
                        s.level.getBlockState(p.getBlockPos()).getValue(InlineDeviceBlock.ON),
                        "the unpowered pump's block state");
                s.cycles(4, p);
                s.close(out(p).totalMoles(), held, 1e-12, "the output with the power cut");
            } finally {
                cleanup.run();
            }
            h.succeed();
        });
    }
}
