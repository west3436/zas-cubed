package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.canister.CanisterMenu;
import io.github.west3436.zas.atmos.canister.CanisterRelease;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.port.ConnectorPortBlock;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMenu;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.atmos.vent.VentSettings;
import io.github.west3436.zas.atmos.vent.VentStatus;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The canister, connector port and vent in the world (CONT-014, CONT-017, CONT-074, CONT-078,
 * CONT-083, CONT-084). Like {@link PipeTests}, each builds in its own {@link Area} and drives device
 * steps and atmos ticks itself, in a batch of its own. Vents run on test power (architecture §11.3).
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasDeviceTests {
    static final String BATCH = "zas_atmos_devices";

    private GasDeviceTests() {}

    static BlockState port() {
        return AtmosBlocks.CONNECTOR_PORT.get().defaultBlockState();
    }

    static BlockState canister() {
        return AtmosBlocks.CANISTER.get().defaultBlockState();
    }

    static BlockState vent(Direction facing) {
        return AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, facing);
    }

    static BlockState pipe() {
        return AtmosBlocks.PIPE.get().defaultBlockState();
    }

    static boolean joined(BlockState pipe, Direction face) {
        return pipe.getValue(PipeBlock.CONNECTIONS.get(face));
    }

    /** A sealed 3×3×3 room (interior 1..3) with its zone formed; returns its pressure. */
    static double room(Area a) {
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(N2, 10.0));
        return a.zone(2, 2, 2).mixture().pressure();
    }

    /** Steps until the readings stop changing or the limit is reached; returns the readings, one a step. */
    static double[] run(Area a, int steps, java.util.function.DoubleSupplier reading) {
        double[] out = new double[steps];
        for (int i = 0; i < steps; i++) {
            a.step();
            out[i] = reading.getAsDouble();
        }
        return out;
    }

    /** The steps that moved the reading by more than a little. */
    static int moving(double[] readings, double start) {
        int n = 0;
        double last = start;
        for (double r : readings) {
            if (Math.abs(r - last) > 1e-3) {
                n++;
            }
            last = r;
        }
        return n;
    }

    /** CONT-083: a canister on a port joins its network, and a pipe joins the port only below. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_083_a_canister_on_a_port_joins_its_network(GameTestHelper h) {
        Area a = new Area(h, 20);
        a.clear(0, 0, 12, 4);
        a.place(2, 1, 2, pipe());
        a.place(2, 2, 2, port());
        a.place(2, 3, 2, canister());
        a.place(3, 2, 2, pipe()); // beside the port
        a.tick();

        h.assertTrue(a.get(2, 2, 2).getValue(ConnectorPortBlock.CONNECTED), "the port doesn't show the canister");
        h.assertTrue(joined(a.get(2, 1, 2), Direction.UP), "the pipe below doesn't join the port");
        h.assertFalse(joined(a.get(3, 2, 2), Direction.WEST), "a pipe beside the port joins it");
        PipeNetwork net = a.<PipeNodeEntity>entity(2, 1, 2).network();
        h.assertTrue(net.members().contains(a.<PipeNodeEntity>entity(2, 3, 2)), "the canister isn't in the network");
        a.close(net.volume(), PipeVolumes.PIPE + PipeVolumes.PORT + PipeVolumes.CANISTER, "network volume");

        // A pipe above a port doesn't join it; a canister on a pipe doesn't join the pipe.
        a.place(6, 2, 2, port());
        a.place(6, 3, 2, pipe());
        a.place(9, 1, 2, pipe());
        a.place(9, 2, 2, canister());
        h.assertFalse(joined(a.get(6, 3, 2), Direction.DOWN), "a pipe above the port joins it");
        h.assertFalse(a.get(6, 2, 2).getValue(ConnectorPortBlock.CONNECTED), "a pipe shows as a canister");
        h.assertFalse(joined(a.get(9, 1, 2), Direction.UP), "a canister joins a pipe directly");

        // Taking the canister away detaches it.
        a.set(2, 3, 2, AIR);
        h.assertFalse(a.get(2, 2, 2).getValue(ConnectorPortBlock.CONNECTED), "the port still shows a canister");
        h.succeed();
    }

    /** CONT-014: a pipe joins a vent only on its back. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_014_a_pipe_joins_a_vent_only_on_its_back(GameTestHelper h) {
        Area a = new Area(h, 21);
        a.clear(0, 0, 6, 6);
        for (Direction facing : Direction.values()) {
            a.clear(0, 0, 6, 6);
            a.place(3, 3, 3, vent(facing));
            for (Direction d : Direction.values()) {
                BlockPos p = new BlockPos(3, 3, 3).relative(d);
                a.place(p.getX(), p.getY(), p.getZ(), pipe());
            }
            for (Direction d : Direction.values()) {
                BlockPos p = new BlockPos(3, 3, 3).relative(d);
                boolean expect = d == facing.getOpposite();
                h.assertTrue(
                        joined(a.get(p.getX(), p.getY(), p.getZ()), d.getOpposite()) == expect,
                        "vent facing " + facing + ": pipe on its " + d + " side joined " + !expect);
            }
        }
        h.succeed();
    }

    /** Vent in the room's west wall, facing in, fed by a pipe, a port and a canister outside. */
    static VentBlockEntity ventRig(Area a) {
        a.place(0, 2, 2, vent(Direction.EAST));
        a.place(-1, 2, 2, pipe());
        a.place(-1, 3, 2, port());
        a.place(-1, 4, 2, canister());
        a.tick();
        VentBlockEntity vent = a.entity(0, 2, 2);
        vent.power().setTestPowered(true);
        return vent;
    }

    /** CONT-014, CONT-078: a vent fills a room to its external bound, over several atmos ticks. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_014_a_vent_fills_a_room_to_the_external_bound(GameTestHelper h) {
        Area a = new Area(h, 22);
        double start = room(a);
        VentBlockEntity vent = ventRig(a);
        PipeNetwork net = vent.network();
        a.close(net.volume(), PipeVolumes.VENT + PipeVolumes.PIPE + PipeVolumes.PORT + PipeVolumes.CANISTER, "volume");
        net.gas().add(new GasParcel(Map.of(N2, 1_500.0), T));
        double target = 150;
        vent.command(VentSettings.blow(target));

        double[] p = run(a, 30, () -> a.zone(2, 2, 2).mixture().pressure());
        double last = p[p.length - 1];
        h.assertTrue(Math.abs(last - target) < 0.05, "the room ended at " + last + " kPa, not " + target);
        double max = 0;
        for (double v : p) {
            max = Math.max(max, v);
        }
        h.assertTrue(max < target + 0.05, "the room went over the bound: " + max);
        int ticks = moving(p, start);
        h.assertTrue(ticks >= 4, "the room filled in " + ticks + " atmos ticks, not several (CONT-078)");
        h.assertTrue(a.get(0, 2, 2).getValue(VentMode.PROPERTY) == VentMode.BLOW, "the vent doesn't show blowing");
        h.succeed();
    }

    /** CONT-014: a vent siphons a room down to its external bound. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_014_a_vent_siphons_a_room_to_the_external_bound(GameTestHelper h) {
        Area a = new Area(h, 23);
        double start = room(a);
        VentBlockEntity vent = ventRig(a);
        double target = start - 5;
        vent.command(VentSettings.siphon(target));
        double before = a.zone(2, 2, 2).mixture().totalMoles();

        double[] p = run(a, 40, () -> a.zone(2, 2, 2).mixture().pressure());
        double last = p[p.length - 1];
        h.assertTrue(Math.abs(last - target) < 0.05, "the room ended at " + last + " kPa, not " + target);
        double moved = before - a.zone(2, 2, 2).mixture().totalMoles();
        a.close(vent.network().gas().totalMoles(), moved, "the network holds what left the room");
        h.assertTrue(moving(p, start) >= 4, "the room emptied in under four atmos ticks (CONT-078)");
        h.succeed();
    }

    /**
     * CONT-014: in an open cell a vent blows into the ambient, at its rate, and forms no zone. This
     * one runs in real time: an addition to the open air doesn't complete in atmos ticks run by
     * {@code tickNow} within one game tick.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void cont_014_a_vent_vents_to_the_ambient_in_an_open_cell(GameTestHelper h) {
        Area a = new Area(h, 24);
        a.forceLoad(true); // it waits in real time
        a.clear(0, 0, 4, 4);
        a.place(2, 1, 2, vent(Direction.NORTH));
        a.place(2, 1, 3, pipe());
        a.place(
                2,
                1,
                4,
                AtmosBlocks.PIPE_CAP
                        .get()
                        .defaultBlockState()
                        .setValue(io.github.west3436.zas.atmos.pipe.PipeCapBlock.FACING, Direction.NORTH));
        a.tick();
        VentBlockEntity vent = a.entity(2, 1, 2);
        vent.power().setTestPowered(true);
        PipeNetwork net = vent.network();
        net.gas().add(new GasParcel(Map.of(N2, 100.0), T));
        vent.command(VentSettings.blow(500));

        // Each atmos tick that moves gas sends half the vent's share: the network keeps
        // 1 - 0.5·400/540 of its gas. Three such ticks, and no zone in front.
        double keep = 1 - 0.5 * PipeVolumes.VENT / net.volume();
        double third = 100.0 * Math.pow(keep, 3);
        h.startSequence()
                .thenWaitUntil(() -> {
                    double n = vent.network().gas().totalMoles();
                    h.assertTrue(n <= third * (1 + 1e-9), "the network holds " + n + " mol");
                    h.assertTrue(Atmospheres.zoneAt(a.level, a.abs(2, 1, 1)).isEmpty(), "venting formed a zone");
                    double k = Math.log(n / 100.0) / Math.log(keep);
                    h.assertTrue(Math.abs(k - Math.rint(k)) < 1e-6, "the network fell by other than whole ticks: " + k);
                })
                .thenExecute(() -> a.forceLoad(false))
                .thenSucceed();
    }

    /** CONT-060: an unpowered vent does nothing, and shows off. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_014_an_unpowered_vent_does_nothing(GameTestHelper h) {
        Area a = new Area(h, 25);
        double start = room(a);
        VentBlockEntity vent = ventRig(a);
        vent.power().setTestPowered(false);
        vent.network().gas().add(new GasParcel(Map.of(N2, 1_500.0), T));
        vent.command(VentSettings.blow(150));
        run(a, 6, () -> 0);
        a.close(a.zone(2, 2, 2).mixture().pressure(), start, "the room's pressure");
        h.assertTrue(a.get(0, 2, 2).getValue(VentMode.PROPERTY) == VentMode.OFF, "an unpowered vent shows on");
        h.assertTrue(vent.power().draw() > 0, "a vent that is on draws nothing");
        vent.command(VentSettings.OFF);
        h.assertTrue(vent.power().draw() == 0, "a vent that is off draws power");
        h.succeed();
    }

    /** CONT-084: a canister's valve fills a room to the release pressure. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_084_a_canister_fills_a_room_to_the_release_pressure(GameTestHelper h) {
        Area a = new Area(h, 26);
        room(a);
        a.place(2, 1, 2, canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 1, 2);
        can.network().gas().add(new GasParcel(Map.of(N2, 1_500.0), T));
        can.setReleasePressure(120);
        can.setValveOpen(true);

        double[] p = run(a, 8, () -> a.zone(2, 2, 2).mixture().pressure());
        double last = p[p.length - 1];
        h.assertTrue(Math.abs(last - 120) < 0.05, "the room ended at " + last + " kPa, not 120");
        can.setValveOpen(false);
        double held = can.network().gas().totalMoles();
        run(a, 3, () -> 0);
        a.close(can.network().gas().totalMoles(), held, "a closed valve releases nothing");
        h.succeed();
    }

    /**
     * The canister among a block's drops. The drops are taken as {@code Block.dropResources} makes
     * them; item entities in a GameTest area far from the players can't be found again.
     */
    static ItemStack canisterIn(List<ItemStack> drops) {
        return drops.stream()
                .filter(s -> s.is(AtmosBlocks.CANISTER.get().asItem()))
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    /** CONT-017: a filled canister mined and placed again holds the same gas, and keeps its paint. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_017_a_mined_canister_keeps_its_contents(GameTestHelper h) {
        Area a = new Area(h, 27);
        a.clear(0, 0, 8, 4);
        GasParcel gas = new GasParcel(Map.of(O2, 1_234.5, N2, 10.0), 300.0);

        // Mined by a player with a pickaxe: the block is removed before the drops are made.
        BlockState painted = canister().setValue(CanisterColor.PROPERTY, CanisterColor.BLUE);
        a.place(2, 1, 2, painted);
        a.<CanisterBlockEntity>entity(2, 1, 2).network().gas().add(gas);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        BlockPos pos = a.abs(2, 1, 2);
        BlockState state = a.level.getBlockState(pos);
        BlockEntity be = a.level.getBlockEntity(pos);
        state.getBlock().playerWillDestroy(a.level, pos, state, player);
        a.level.removeBlock(pos, false);
        ItemStack item = canisterIn(Block.getDrops(state, a.level, pos, be, player, player.getMainHandItem()));
        h.assertTrue(
                gas.equals(item.get(AtmosComponents.GAS.get())),
                "the item " + item + " carries " + item.get(AtmosComponents.GAS.get()));
        var props = item.get(DataComponents.BLOCK_STATE);
        h.assertTrue(props != null && props.get(CanisterColor.PROPERTY) == CanisterColor.BLUE, "the paint is lost");

        // Placed again, the canister holds it.
        a.place(5, 1, 2, canister().setValue(CanisterColor.PROPERTY, CanisterColor.BLUE));
        CanisterBlockEntity again = a.entity(5, 1, 2);
        again.applyComponentsFromItemStack(item);
        a.close(again.network().gas().moles(O2), 1_234.5, "O2 after placing it again");
        a.close(again.network().gas().moles(N2), 10.0, "N2 after placing it again");

        // Dropped before removal, as by destroyBlock or an explosion: the same, and nothing released.
        BlockPos pos2 = a.abs(5, 1, 2);
        BlockState state2 = a.level.getBlockState(pos2);
        ItemStack second = canisterIn(Block.getDrops(state2, a.level, pos2, again));
        a.level.removeBlock(pos2, false);
        h.assertTrue(gas.equals(second.get(AtmosComponents.GAS.get())), "drops before removal lost the gas");
        a.close(again.stored().totalMoles(), gas.totalMoles(), "the removed canister kept its gas for the item");
        h.succeed();
    }

    /**
     * CONT-017 (owner's change of 2026-11-02): a canister broken in creative drops nothing, and its
     * gas is destroyed with it, not released into its room.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_017_a_canister_broken_in_creative_destroys_its_gas(GameTestHelper h) {
        Area a = new Area(h, 31);
        room(a);
        a.place(2, 1, 2, canister());
        a.tick();
        CanisterBlockEntity creative = a.entity(2, 1, 2);
        creative.network().gas().add(new GasParcel(Map.of(O2, 50.0), T));
        double o2 = a.zone(2, 2, 2).mixture().moles(O2);
        Player builder = h.makeMockPlayer(GameType.CREATIVE);
        BlockPos pos = a.abs(2, 1, 2);
        BlockState state = a.level.getBlockState(pos);
        state.getBlock().playerWillDestroy(a.level, pos, state, builder);
        a.level.removeBlock(pos, false);
        for (int i = 0; i < 3; i++) {
            a.tick();
        }
        a.close(creative.stored().totalMoles(), 0, "a canister broken in creative kept its gas");
        a.close(a.zone(2, 2, 2).mixture().moles(O2), o2, "the room's O2 after a canister was broken in creative");
        h.succeed();
    }

    /** CONT-017, CONT-009: a canister removed without dropping releases its gas into its room. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_017_a_canister_removed_without_a_drop_releases_its_gas(GameTestHelper h) {
        Area a = new Area(h, 28);
        room(a);
        a.place(2, 1, 2, canister());
        a.tick();
        a.<CanisterBlockEntity>entity(2, 1, 2).network().gas().add(new GasParcel(Map.of(O2, 50.0), T));
        double o2 = a.zone(2, 2, 2).mixture().moles(O2);
        a.set(2, 1, 2, AIR);
        a.tick();
        a.close(a.zone(2, 2, 2).mixture().moles(O2) - o2, 50.0, "the room gains the canister's gas");
        h.succeed();
    }

    /**
     * CONT-014, CONT-074: a vent's GUI shows its network's pressure, and why it moves nothing. As in
     * the S-01 round's test: a vent siphoning into a network with a full canister on its port, with
     * the internal bound below the network's pressure, is idle, and says the network is why.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_014_an_idle_vent_says_why(GameTestHelper h) {
        Area a = new Area(h, 32);
        room(a);
        VentBlockEntity vent = ventRig(a);
        PipeNetwork net = vent.network();
        net.gas().add(new GasParcel(Map.of(N2, 1_500.0), T));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VentMenu menu = (VentMenu) vent.createMenu(1, player.getInventory(), player);
        h.assertValueEqual(menu.status(), VentStatus.OFF, "an off vent's status");
        double kPa = vent.networkPressure();
        h.assertTrue(kPa > 2_000, "the network is at " + kPa + " kPa");
        h.assertValueEqual(menu.value(VentMenu.DATA_NETWORK), (int) Math.round(kPa), "the GUI's network pressure");

        // Siphoning up to 400 kPa into a network at over 2,000: idle, because of the network.
        double room = a.zone(2, 2, 2).mixture().totalMoles();
        vent.command(new VentSettings(VentMode.SIPHON, 0, 400));
        settle(a, vent);
        h.assertValueEqual(vent.status(), VentStatus.NETWORK_BOUND, "the siphoning vent's status");
        h.assertValueEqual(menu.status(), VentStatus.NETWORK_BOUND, "the GUI's status");
        a.close(a.zone(2, 2, 2).mixture().totalMoles(), room, "the room's moles while the vent is idle");

        // Raise the internal bound above the network: it siphons.
        vent.command(new VentSettings(VentMode.SIPHON, 0, VentSettings.MAX_KPA));
        a.step();
        h.assertValueEqual(vent.status(), VentStatus.MOVING, "the status with the bound raised");
        a.step();
        h.assertTrue(a.zone(2, 2, 2).mixture().totalMoles() < room, "the vent didn't siphon with the bound raised");

        // Blowing into a room above the external bound: idle, because of the room.
        vent.command(new VentSettings(VentMode.BLOW, 0, 0));
        settle(a, vent);
        h.assertValueEqual(menu.status(), VentStatus.ROOM_BOUND, "the GUI's status blowing past the external bound");

        // Blowing from an empty network: idle, because the network is empty.
        net = vent.network();
        net.gas().takeFraction(1.0);
        vent.command(VentSettings.blow(VentSettings.MAX_KPA));
        settle(a, vent);
        h.assertValueEqual(menu.status(), VentStatus.NETWORK_EMPTY, "the GUI's status with an empty network");
        h.assertValueEqual(menu.value(VentMenu.DATA_NETWORK), 0, "the GUI's network pressure when empty");

        vent.command(VentSettings.OFF);
        a.step();
        h.assertValueEqual(menu.status(), VentStatus.OFF, "an off vent's status");
        h.succeed();
    }

    /** Steps until the vent's port is free and it has read the air in front, at most 8 times. */
    private static void settle(Area a, VentBlockEntity vent) {
        for (int i = 0; i < 8; i++) {
            a.step();
            if (vent.readyToStep() && vent.status() != VentStatus.WAITING) {
                a.step(); // and once more on the new settings
                return;
            }
        }
    }

    /** CONT-074: GUI buttons are checked and settings clamped on the server. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_074_out_of_range_settings_are_clamped_or_refused(GameTestHelper h) {
        Area a = new Area(h, 29);
        a.clear(0, 0, 6, 4);
        a.place(2, 1, 2, canister());
        a.place(4, 1, 2, vent(Direction.UP));
        CanisterBlockEntity can = a.entity(2, 1, 2);
        VentBlockEntity vent = a.entity(4, 1, 2);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);

        CanisterMenu cm =
                new CanisterMenu(1, new SimpleContainerData(CanisterMenu.DATA_COUNT), ContainerLevelAccess.NULL, can);
        h.assertFalse(cm.clickMenuButton(player, 99), "an unknown canister button was accepted");
        h.assertFalse(cm.clickMenuButton(player, -1), "a negative canister button was accepted");
        for (int i = 0; i < 20; i++) {
            cm.clickMenuButton(player, CanisterMenu.RELEASE_UP_100);
        }
        a.close(can.releasePressure(), CanisterRelease.MAX_KPA, "release pressure, raised past the top");
        for (int i = 0; i < 20; i++) {
            cm.clickMenuButton(player, CanisterMenu.RELEASE_DOWN_100);
        }
        a.close(can.releasePressure(), CanisterRelease.MIN_KPA, "release pressure, lowered past the bottom");
        cm.clickMenuButton(player, CanisterMenu.TOGGLE_VALVE);
        h.assertTrue(can.valveOpen(), "the valve didn't open");

        VentMenu vm = new VentMenu(1, new SimpleContainerData(VentMenu.DATA_COUNT), ContainerLevelAccess.NULL, vent);
        h.assertFalse(vm.clickMenuButton(player, VentMenu.LAST_BUTTON + 1), "an unknown vent button was accepted");
        for (int i = 0; i < 60; i++) {
            vm.clickMenuButton(player, VentMenu.EXTERNAL_DOWN_100 + 3);
            vm.clickMenuButton(player, VentMenu.INTERNAL_DOWN_100);
        }
        a.close(vent.settings().external(), VentSettings.MAX_KPA, "external bound, raised past the top");
        a.close(vent.settings().internal(), 0, "internal bound, lowered past the bottom");
        vm.clickMenuButton(player, VentMenu.CYCLE_MODE);
        h.assertTrue(vent.settings().mode() == VentMode.BLOW, "the mode didn't cycle to blow");
        h.succeed();
    }

    /**
     * CONT-014, CONT-074: a vent no controller orders takes hand settings from its GUI; one a
     * controller orders refuses them, its GUI shows the controller and its order, and it takes hand
     * settings again once the orders stop. The controller is stood in for by calling the vent's
     * {@code control} as a controller does.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void cont_014_a_controlled_vent_refuses_hand_settings(GameTestHelper h) {
        Area a = new Area(h, 30);
        a.forceLoad(true); // it waits in real time
        a.clear(0, 0, 6, 4);
        a.place(2, 1, 2, vent(Direction.UP));
        VentBlockEntity vent = a.entity(2, 1, 2);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        VentMenu menu = (VentMenu) vent.createMenu(1, player.getInventory(), player);

        // Uncontrolled: the GUI sets it.
        h.assertFalse(vent.controlled(), "a new vent is controlled");
        h.assertValueEqual(menu.value(VentMenu.DATA_CONTROLLED), 0, "the GUI's control flag");
        h.assertTrue(menu.clickMenuButton(player, VentMenu.CYCLE_MODE), "the free vent refused a setting");
        h.assertValueEqual(vent.settings().mode(), VentMode.BLOW, "the free vent's mode");

        // Controlled: the order wins, and the GUI says whose it is.
        BlockPos controller = new BlockPos(-70_000, -30, 123_456); // beyond a short, to check the halves
        vent.control(controller, VentSettings.siphon(50));
        h.assertTrue(vent.controlled(), "the ordered vent isn't controlled");
        h.assertValueEqual(vent.controller(), java.util.Optional.of(controller), "the vent's controller");
        for (int id = VentMenu.CYCLE_MODE; id <= VentMenu.LAST_BUTTON; id++) {
            h.assertFalse(menu.clickMenuButton(player, id), "the controlled vent took button " + id);
        }
        h.assertValueEqual(vent.settings(), VentSettings.siphon(50), "the controlled vent's settings");
        h.assertValueEqual(menu.value(VentMenu.DATA_CONTROLLED), 1, "the GUI's control flag");
        h.assertTrue(menu.controlled(), "the GUI doesn't show the vent controlled");
        h.assertValueEqual(menu.controller(), java.util.Optional.of(controller), "the GUI's controller");
        h.assertValueEqual(menu.mode(), VentMode.SIPHON, "the GUI's order");
        var tag = vent.saveWithoutMetadata(a.level.registryAccess()); // what /data get block shows
        h.assertTrue(tag.getBoolean("Controlled"), "the vent's data doesn't show it controlled: " + tag);
        h.assertValueEqual(
                net.minecraft.nbt.NbtUtils.readBlockPos(tag, "Controller"),
                java.util.Optional.of(controller),
                "the vent's data's controller");
        vent.control(null, VentSettings.OFF);
        h.assertValueEqual(menu.value(VentMenu.DATA_CONTROLLED), 2, "an unknown controller's flag");
        h.assertTrue(menu.controller().isEmpty(), "the GUI shows an unknown controller's position");
        vent.control(controller, VentSettings.siphon(50));

        // Once the orders stop, the vent is the player's again.
        h.runAfterDelay(DevicePower.LEASE_TICKS + 1, () -> {
            h.assertFalse(vent.controlled(), "the vent stayed controlled with no orders");
            h.assertValueEqual(menu.value(VentMenu.DATA_CONTROLLED), 0, "the GUI's control flag after");
            h.assertFalse(
                    vent.saveWithoutMetadata(a.level.registryAccess()).contains("Controlled"),
                    "the freed vent's data shows it controlled");
            h.assertTrue(
                    menu.clickMenuButton(player, VentMenu.EXTERNAL_DOWN_100 + 2), "the freed vent refused a setting");
            a.close(vent.settings().external(), 60, "the freed vent's external bound");
            a.forceLoad(false);
            h.succeed();
        });
    }
}
