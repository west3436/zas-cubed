package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.filter.FilterMenu;
import io.github.west3436.zas.atmos.filter.FilterOption;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.inline.SidePortBlock;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
import io.github.west3436.zas.atmos.inline.SidePortMenu;
import io.github.west3436.zas.atmos.mixer.MixerBlockEntity;
import io.github.west3436.zas.atmos.mixer.MixerMenu;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The filter and the mixer in the world (CONT-013, CONT-077, CONT-060, CONT-065, CONT-074): a real
 * device among three pipe networks, each run capped so nothing leaks. Each test builds in its own
 * {@link Site} and steps its devices itself, within one game tick. The power tests have batches of
 * their own because they wait in real time for a lease to run out.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FilterMixerTests {
    private static final String BATCH = "zas_atmos_filter_mixer";
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath("zas", "water_vapor");
    private static final double T = StandardAir.TEMPERATURE;

    /** A port's run: a pipe, a cap and the port's buffer, once. */
    private static final double RUN = 2 * PipeVolumes.PIPE + PipeVolumes.PORT;

    private FilterMixerTests() {}

    private static BlockState filterState(Direction facing) {
        return AtmosBlocks.FILTER.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, facing);
    }

    private static BlockState mixerState(Direction facing) {
        return AtmosBlocks.MIXER.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, facing);
    }

    private static void pipe(Site s, int x, int y, int z) {
        s.place(
                x,
                y,
                z,
                Block.updateFromNeighbourShapes(AtmosBlocks.PIPE.get().defaultBlockState(), s.level, s.abs(x, y, z)),
                Object.class);
    }

    private static void cap(Site s, int x, int y, int z, Direction facing) {
        s.place(
                x,
                y,
                z,
                AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing),
                Object.class);
    }

    /**
     * A device at (x, 2, 2) facing east, so its side port is south, with a pipe and a cap on each
     * port: along x at the back and front, and south then down at the side.
     */
    private static <T extends SidePortEntity> T line(Site s, int x, BlockState state, Class<T> type) {
        cap(s, x - 2, 2, 2, Direction.EAST);
        pipe(s, x - 1, 2, 2);
        T device = s.place(x, 2, 2, state, type);
        pipe(s, x + 1, 2, 2);
        cap(s, x + 2, 2, 2, Direction.WEST);
        cap(s, x, 1, 3, Direction.UP);
        pipe(s, x, 2, 3);
        return device;
    }

    private static FilterBlockEntity filter(Site s, int x) {
        return line(s, x, filterState(Direction.EAST), FilterBlockEntity.class);
    }

    private static MixerBlockEntity mixer(Site s, int x) {
        return line(s, x, mixerState(Direction.EAST), MixerBlockEntity.class);
    }

    private static GasStore back(SidePortEntity device) {
        return device.port(SidePortEntity.BACK).network().gas();
    }

    private static GasStore front(SidePortEntity device) {
        return device.port(SidePortEntity.FRONT).network().gas();
    }

    private static GasStore side(SidePortEntity device) {
        return device.port(SidePortEntity.SIDE).network().gas();
    }

    private static void assertSeparate(GameTestHelper h, SidePortEntity device) {
        h.assertTrue(back(device) != front(device), "the device joined its back and front");
        h.assertTrue(back(device) != side(device), "the device joined its back and side");
        h.assertTrue(front(device) != side(device), "the device joined its front and side");
    }

    private static double moles(SidePortEntity device) {
        return back(device).totalMoles()
                + front(device).totalMoles()
                + side(device).totalMoles();
    }

    private static double energy(SidePortEntity device) {
        return back(device).thermalEnergy()
                + front(device).thermalEnergy()
                + side(device).thermalEnergy();
    }

    private static boolean litUp(Site s, SidePortEntity device) {
        return s.level.getBlockState(device.getBlockPos()).getValue(InlineDeviceBlock.ON);
    }

    /**
     * A filter keeps its three networks apart. While on and powered it takes an even slice of its
     * input, at most 100 L/s at input conditions, sending the selected gases to its side outlet and the
     * rest to its output, until an outlet is at the target. Moles and thermal energy are conserved, and
     * it says what it is doing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_013_a_real_filter_sends_what_it_selects_to_its_side_outlet(GameTestHelper h) {
        Site s = new Site(h, 620);
        s.clear();
        FilterBlockEntity f = filter(s, 4);
        f.power().setTestPowered(true);
        assertSeparate(h, f);
        s.close(back(f).volume(), RUN, 1e-9, "the input's volume");
        s.close(front(f).volume(), RUN, 1e-9, "the output's volume");
        s.close(side(f).volume(), RUN, 1e-9, "the side outlet's volume");

        // Off, it does nothing, powered or not.
        back(f).add(new GasParcel(Map.of(N2, 200.0, CO2, 100.0), 600));
        front(f).add(new GasParcel(Map.of(O2, 1.0), T));
        double moles = moles(f);
        double energy = energy(f);
        f.step();
        s.close(side(f).totalMoles(), 0, 1e-12, "the side outlet with the filter off");
        s.close(front(f).totalMoles(), 1, 1e-12, "the output with the filter off");
        h.assertValueEqual(f.status(), InlineStatus.OFF, "the status while off");
        h.assertFalse(litUp(s, f), "the state while off");

        // On, far from a high target: 100 L at the input's conditions, split by what it selects.
        f.setOn(true);
        f.setTarget(PumpFlow.MAX_TARGET);
        double slice = PumpFlow.MAX_RATE / RUN;
        f.step();
        s.close(side(f).moles(CO2), 100 * slice, 1e-9, "the CO₂ at the side outlet");
        s.close(side(f).moles(N2), 0, 1e-12, "the nitrogen at the side outlet");
        s.close(front(f).moles(N2), 200 * slice, 1e-9, "the nitrogen at the output");
        s.close(front(f).moles(CO2), 0, 1e-12, "the CO₂ at the output");
        h.assertValueEqual(f.status(), InlineStatus.MOVING, "the status while moving");
        h.assertTrue(litUp(s, f), "the state while on");

        // A low target: the output has more coming, and it stops there exactly.
        double target = front(f).pressure() + 50;
        f.setTarget(target);
        for (int i = 0; i < 20; i++) {
            f.step();
        }
        s.close(front(f).pressure(), target, 1e-6, "the output at its target");
        h.assertTrue(side(f).pressure() < target, "the side outlet passed the target");
        h.assertValueEqual(f.status(), InlineStatus.TARGET_REACHED, "the status at the target");

        // It stays there, and a target below the outlets moves nothing back.
        double held = front(f).totalMoles();
        f.setTarget(1);
        f.step();
        s.close(front(f).totalMoles(), held, 1e-12, "the output above its target");

        // Selecting all the input sends the rest to the side outlet, which then has its own target.
        f.setSelection(GasSelection.of(List.of(N2, CO2), List.of()));
        target = front(f).pressure() + 20;
        f.setTarget(target);
        for (int i = 0; i < 20; i++) {
            f.step();
        }
        s.close(side(f).pressure(), target, 1e-6, "the side outlet at its target");
        s.close(front(f).totalMoles(), held, 1e-12, "the output while everything goes to the side");
        h.assertValueEqual(f.status(), InlineStatus.SIDE_TARGET_REACHED, "the status at the side's target");
        s.close(moles(f), moles, 1e-9, "the moles");
        s.close(energy(f), energy, 1e-4, "the thermal energy");

        // With the input drained, it says so.
        f.setTarget(PumpFlow.MAX_TARGET);
        for (int i = 0; i < 60; i++) {
            f.step();
        }
        h.assertTrue(back(f).totalMoles() < 1e-3, "the input didn't drain: " + back(f).totalMoles());
        h.assertValueEqual(f.status(), InlineStatus.INPUT_EMPTY, "the status with no gas");
        s.clear();
        h.succeed();
    }

    /** A new filter selects CO₂ and the toxic gases, and a selection can name gases and gas tags. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_013_a_real_filter_selects_by_gas_and_by_gas_tag(GameTestHelper h) {
        Site s = new Site(h, 621);
        s.clear();
        FilterBlockEntity f = filter(s, 4);
        f.power().setTestPowered(true);
        f.setOn(true);
        f.setTarget(PumpFlow.MAX_TARGET);
        double slice = PumpFlow.MAX_RATE / RUN;

        // New: CO₂ by name and carbon monoxide by #zas:toxic go to the side, the nitrogen doesn't.
        back(f).add(new GasParcel(Map.of(N2, 30.0, CO2, 20.0, CO, 10.0), T));
        f.step();
        s.close(side(f).moles(CO2), 20 * slice, 1e-9, "the CO₂ at the side outlet");
        s.close(side(f).moles(CO), 10 * slice, 1e-9, "the carbon monoxide at the side outlet");
        s.close(front(f).moles(N2), 30 * slice, 1e-9, "the nitrogen at the output");

        // A tag selects its gases, and nothing else does then.
        f.setSelection(GasSelection.of(List.of(), List.of(GasTags.FLAMMABLE)));
        back(f).takeFraction(1);
        front(f).takeFraction(1);
        side(f).takeFraction(1);
        back(f).add(new GasParcel(Map.of(H2, 10.0, N2, 20.0, CO2, 30.0), T));
        f.step();
        s.close(side(f).moles(H2), 10 * slice, 1e-9, "the hydrogen at the side outlet");
        s.close(side(f).moles(CO2), 0, 1e-12, "the CO₂ at the side outlet");
        s.close(front(f).moles(N2), 20 * slice, 1e-9, "the nitrogen at the output");
        s.close(front(f).moles(CO2), 30 * slice, 1e-9, "the CO₂ at the output");
        s.close(front(f).moles(H2), 0, 1e-12, "the hydrogen at the output");
        s.clear();
        h.succeed();
    }

    /**
     * A mixer keeps its three networks apart. While on and powered it adds gas from its back and side
     * inputs in their fractions, up to its target. When an input can't keep up, both scale together, so
     * the fractions hold. An input it isn't asked for can be empty, and one it is asked for stops it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_077_a_real_mixer_adds_both_inputs_in_their_fractions(GameTestHelper h) {
        Site s = new Site(h, 622);
        s.clear();
        MixerBlockEntity m = mixer(s, 4);
        m.power().setTestPowered(true);
        assertSeparate(h, m);
        h.assertValueEqual(m.backPercent(), 50, "a new mixer's back share");

        // Off, it does nothing.
        back(m).add(new GasParcel(Map.of(O2, 300.0), T));
        side(m).add(new GasParcel(Map.of(N2, 300.0), T));
        double moles = moles(m);
        double energy = energy(m);
        m.step();
        s.close(front(m).totalMoles(), 0, 1e-12, "the output with the mixer off");
        h.assertValueEqual(m.status(), InlineStatus.OFF, "the status while off");

        // Far from a high target: the side input, asked for three quarters, gives 100 L at its
        // conditions, and the back input scales down with it.
        m.setOn(true);
        m.setTarget(PumpFlow.MAX_TARGET);
        m.setBackPercent(25);
        double litres = 300 * PumpFlow.MAX_RATE / RUN;
        m.step();
        s.close(front(m).moles(N2), litres, 1e-9, "the side input's first second");
        s.close(front(m).moles(O2), litres / 3, 1e-9, "the back input's first second");
        h.assertValueEqual(m.status(), InlineStatus.MOVING, "the status while moving");
        h.assertTrue(litUp(s, m), "the state while on");

        // A low target: it stops there exactly, with the gas it adds in the fractions.
        double o2 = front(m).moles(O2);
        double n2 = front(m).moles(N2);
        double target = front(m).pressure() + 50;
        m.setTarget(target);
        for (int i = 0; i < 20; i++) {
            m.step();
        }
        s.close(front(m).pressure(), target, 1e-6, "the output at its target");
        s.close((front(m).moles(N2) - n2) / (front(m).moles(O2) - o2), 3, 1e-6, "the fractions near the target");
        h.assertValueEqual(m.status(), InlineStatus.TARGET_REACHED, "the status at the target");
        s.close(moles(m), moles, 1e-9, "the moles");
        s.close(energy(m), energy, 1e-4, "the thermal energy");

        // An input with a fraction of 0 isn't needed.
        side(m).takeFraction(1);
        m.setTarget(PumpFlow.MAX_TARGET);
        m.setBackPercent(100);
        o2 = front(m).moles(O2);
        m.step();
        h.assertTrue(front(m).moles(O2) > o2, "all from the back moved nothing with the side empty");
        h.assertValueEqual(m.status(), InlineStatus.MOVING, "the status with all from the back");

        // An empty input with a fraction above 0 stops it, whatever the other holds.
        m.setBackPercent(50);
        double held = front(m).totalMoles();
        m.step();
        s.close(front(m).totalMoles(), held, 1e-12, "the output with an input empty");
        h.assertValueEqual(m.status(), InlineStatus.INPUT_EMPTY, "the status with an input empty");
        s.clear();
        h.succeed();
    }

    /**
     * Pipes join a device only at its three ports, in all six facings and with its side port on either
     * side, and its shape reaches the edges of the block only there. Rotating or mirroring it in a
     * structure takes its side port with it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_013_cont_077_pipes_join_a_device_only_at_its_ports(GameTestHelper h) {
        Site s = new Site(h, 623);
        s.clear();
        for (BlockState device : List.of(filterState(Direction.NORTH), mixerState(Direction.NORTH))) {
            SidePortBlock block = (SidePortBlock) device.getBlock();
            for (Direction facing : Direction.values()) {
                for (boolean mirrored : new boolean[] {false, true}) {
                    BlockState state = device.setValue(InlineDeviceBlock.FACING, facing)
                            .setValue(SidePortBlock.MIRRORED, mirrored);
                    Direction right = facing.getAxis().isVertical() ? Direction.EAST : facing.getClockWise();
                    Direction sideFace = mirrored ? right.getOpposite() : right;
                    List<Direction> ports = block.portFaces(state);
                    h.assertValueEqual(
                            ports,
                            List.of(facing.getOpposite(), facing, sideFace),
                            facing + " " + mirrored + ": the ports");

                    BlockPos at = s.abs(4, 3, 3);
                    s.level.setBlockAndUpdate(at, state);
                    var shape = state.getShape(s.level, BlockPos.ZERO, CollisionContext.empty());
                    for (Direction d : Direction.values()) {
                        BlockPos beside = at.relative(d);
                        s.level.setBlockAndUpdate(
                                beside,
                                Block.updateFromNeighbourShapes(
                                        AtmosBlocks.PIPE.get().defaultBlockState(), s.level, beside));
                        boolean connected =
                                s.level.getBlockState(beside).getValue(PipeBlock.CONNECTIONS.get(d.getOpposite()));
                        h.assertValueEqual(
                                connected, ports.contains(d), facing + " " + mirrored + ": a pipe to the " + d);
                        s.level.setBlockAndUpdate(beside, Site.AIR);

                        boolean edge = d.getAxisDirection() == Direction.AxisDirection.POSITIVE
                                ? shape.max(d.getAxis()) == 1
                                : shape.min(d.getAxis()) == 0;
                        h.assertValueEqual(
                                edge, ports.contains(d), facing + " " + mirrored + ": the shape to the " + d);
                    }
                    s.level.setBlockAndUpdate(at, Site.AIR);
                }
            }

            // A structure takes its side port with it, in every horizontal facing.
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (boolean mirrored : new boolean[] {false, true}) {
                    BlockState state = device.setValue(InlineDeviceBlock.FACING, facing)
                            .setValue(SidePortBlock.MIRRORED, mirrored);
                    Direction was = SidePortBlock.sideFace(state);
                    for (Rotation r : Rotation.values()) {
                        BlockState turned = state.rotate(r);
                        h.assertValueEqual(
                                turned.getValue(InlineDeviceBlock.FACING), r.rotate(facing), "rotated facing");
                        h.assertValueEqual(SidePortBlock.sideFace(turned), r.rotate(was), r + ": the side port");
                    }
                    for (Mirror m : Mirror.values()) {
                        BlockState flipped = state.mirror(m);
                        h.assertValueEqual(
                                flipped.getValue(InlineDeviceBlock.FACING), m.mirror(facing), "mirrored facing");
                        h.assertValueEqual(SidePortBlock.sideFace(flipped), m.mirror(was), m + ": the side port");
                    }
                }
            }
        }
        s.clear();
        h.succeed();
    }

    /**
     * Flipping a device moves its side port to the other side: the port keeps its share of the gas and
     * joins the pipe there, and the pipe it left keeps its own share and lets go of it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_013_flipping_moves_the_side_port_and_keeps_its_gas(GameTestHelper h) {
        Site s = new Site(h, 624);
        s.clear();
        FilterBlockEntity f = filter(s, 4);
        // A run ready on the other side, which it doesn't join yet.
        cap(s, 4, 1, 1, Direction.UP);
        pipe(s, 4, 2, 1);
        h.assertFalse(
                s.level.getBlockState(s.abs(4, 2, 1)).getValue(PipeBlock.CONNECTIONS.get(Direction.SOUTH)),
                "joined early");
        side(f).add(new GasParcel(Map.of(N2, 34.0), T));
        double perLitre = 34.0 / RUN;

        f.flip();
        BlockState state = s.level.getBlockState(f.getBlockPos());
        h.assertTrue(state.getValue(SidePortBlock.MIRRORED), "the flip didn't change the block's state");
        h.assertValueEqual(SidePortBlock.sideFace(state), Direction.NORTH, "the side port's face after a flip");
        h.assertTrue(
                s.level.getBlockState(s.abs(4, 2, 1)).getValue(PipeBlock.CONNECTIONS.get(Direction.SOUTH)),
                "the new pipe didn't join");
        h.assertFalse(
                s.level.getBlockState(s.abs(4, 2, 3)).getValue(PipeBlock.CONNECTIONS.get(Direction.NORTH)),
                "the old pipe didn't let go");
        PipeNodeEntity left = s.entity(4, 2, 3, PipeNodeEntity.class);
        s.close(side(f).volume(), RUN, 1e-9, "the side port's new network's volume");
        s.close(side(f).totalMoles(), perLitre * PipeVolumes.PORT, 1e-9, "the side port's share");
        s.close(left.network().gas().totalMoles(), perLitre * 2 * PipeVolumes.PIPE, 1e-9, "the old run's share");

        // Back again, the two shares are one network.
        f.flip();
        h.assertFalse(s.level.getBlockState(f.getBlockPos()).getValue(SidePortBlock.MIRRORED), "the second flip");
        s.close(side(f).volume(), RUN, 1e-9, "the side port's old network's volume");
        s.close(
                side(f).moles(N2)
                        + s.entity(4, 2, 1, PipeNodeEntity.class)
                                .network()
                                .gas()
                                .moles(N2),
                34,
                1e-9,
                "the moles");
        s.clear();
        h.succeed();
    }

    /**
     * Pipes joining all three ports leave a device nothing to do. Pipes joining only two don't stop it,
     * and no gas is lost or made however they share a network.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_013_cont_077_ports_sharing_a_network_lose_no_gas(GameTestHelper h) {
        Site s = new Site(h, 625);
        s.clear();

        // All three joined.
        FilterBlockEntity all = s.place(4, 2, 2, filterState(Direction.EAST), FilterBlockEntity.class);
        for (int[] p : new int[][] {{3, 2, 2}, {3, 2, 3}, {4, 2, 3}, {5, 2, 3}, {5, 2, 2}}) {
            pipe(s, p[0], p[1], p[2]);
        }
        all.power().setTestPowered(true);
        all.setOn(true);
        all.setTarget(PumpFlow.MAX_TARGET);
        h.assertTrue(back(all) == front(all) && back(all) == side(all), "the pipes didn't join all three ports");
        back(all).add(new GasParcel(Map.of(N2, 10.0, CO2, 10.0), T));
        all.step();
        h.assertValueEqual(all.status(), InlineStatus.SAME_NETWORK, "the status with all three joined");
        s.close(back(all).totalMoles(), 20, 1e-12, "the one network's moles");
        s.clear();

        // A filter's two outlets joined: it still empties its input, into their one network.
        FilterBlockEntity outlets = s.place(4, 2, 2, filterState(Direction.EAST), FilterBlockEntity.class);
        cap(s, 2, 2, 2, Direction.EAST);
        pipe(s, 3, 2, 2);
        for (int[] p : new int[][] {{5, 2, 2}, {5, 2, 3}, {4, 2, 3}}) {
            pipe(s, p[0], p[1], p[2]);
        }
        outlets.power().setTestPowered(true);
        outlets.setOn(true);
        outlets.setTarget(PumpFlow.MAX_TARGET);
        h.assertTrue(front(outlets) == side(outlets), "the pipes didn't join the outlets");
        h.assertTrue(back(outlets) != side(outlets), "the pipes joined the input to the outlets");
        back(outlets).add(new GasParcel(Map.of(N2, 10.0, CO2, 10.0), T));
        double energy = back(outlets).thermalEnergy();
        outlets.step();
        h.assertValueEqual(outlets.status(), InlineStatus.MOVING, "the status with the outlets joined");
        h.assertTrue(side(outlets).totalMoles() > 0, "nothing moved with the outlets joined");
        s.close(back(outlets).totalMoles() + side(outlets).totalMoles(), 20, 1e-9, "the moles with the outlets joined");
        s.close(
                back(outlets).thermalEnergy() + side(outlets).thermalEnergy(),
                energy,
                1e-6,
                "the energy with the outlets joined");
        s.clear();

        // A filter's input joined to its side outlet: the selected gas stays in the loop.
        FilterBlockEntity loop = s.place(4, 2, 2, filterState(Direction.EAST), FilterBlockEntity.class);
        cap(s, 6, 2, 2, Direction.WEST);
        pipe(s, 5, 2, 2);
        for (int[] p : new int[][] {{3, 2, 2}, {3, 2, 3}, {4, 2, 3}}) {
            pipe(s, p[0], p[1], p[2]);
        }
        loop.power().setTestPowered(true);
        loop.setOn(true);
        loop.setTarget(PumpFlow.MAX_TARGET);
        h.assertTrue(back(loop) == side(loop), "the pipes didn't join the input to the side outlet");
        back(loop).add(new GasParcel(Map.of(N2, 10.0, CO2, 10.0), T));
        loop.step();
        s.close(back(loop).moles(CO2), 10, 1e-9, "the CO₂ looping");
        h.assertTrue(front(loop).moles(N2) > 0, "the nitrogen stayed in the loop");
        s.close(back(loop).moles(N2) + front(loop).moles(N2), 10, 1e-9, "the nitrogen");
        s.clear();

        // A mixer's two inputs joined: it takes both shares from the one network.
        MixerBlockEntity inputs = s.place(4, 2, 2, mixerState(Direction.EAST), MixerBlockEntity.class);
        cap(s, 6, 2, 2, Direction.WEST);
        pipe(s, 5, 2, 2);
        for (int[] p : new int[][] {{3, 2, 2}, {3, 2, 3}, {4, 2, 3}}) {
            pipe(s, p[0], p[1], p[2]);
        }
        inputs.power().setTestPowered(true);
        inputs.setOn(true);
        inputs.setTarget(PumpFlow.MAX_TARGET);
        h.assertTrue(back(inputs) == side(inputs), "the pipes didn't join the inputs");
        back(inputs).add(new GasParcel(Map.of(N2, 20.0), T));
        inputs.step();
        h.assertValueEqual(inputs.status(), InlineStatus.MOVING, "the status with the inputs joined");
        h.assertTrue(front(inputs).totalMoles() > 0, "nothing moved with the inputs joined");
        s.close(back(inputs).totalMoles() + front(inputs).totalMoles(), 20, 1e-9, "the moles with the inputs joined");
        s.clear();
        h.succeed();
    }

    /**
     * A filter's GUI: its switch, target and flip act on the server, a target stays in 0–4,500 kPa, and
     * it offers what it should. A button toggles only what the server's own list holds. The client's
     * menu shows that list, and its slots say what's on, but it can't act.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_074_a_filter_gui_validates_on_the_server(GameTestHelper h) {
        Site s = new Site(h, 626);
        s.clear();
        FilterBlockEntity f = filter(s, 4);
        back(f).add(new GasParcel(Map.of(WATER, 1.0), T));
        Player hand = h.makeMockPlayer(GameType.SURVIVAL);
        FilterMenu menu = (FilterMenu) f.createMenu(1, hand.getInventory(), hand);

        h.assertTrue(menu.clickMenuButton(hand, InlineDeviceMenu.TOGGLE), "the switch was refused");
        h.assertTrue(f.on(), "the switch didn't switch");
        h.assertValueEqual(menu.value(InlineDeviceMenu.DATA_ON), 1, "the switch's slot");

        // Steps stay in range, and reach the client's slot.
        s.close(f.target(), SidePortEntity.DEFAULT_TARGET, 1e-9, "a new filter's target");
        h.assertValueEqual(menu.value(SidePortMenu.DATA_TARGET), 101, "the target's slot");
        f.setTarget(4_480);
        h.assertTrue(menu.clickMenuButton(hand, SidePortMenu.LAST_BUTTON), "the step up was refused");
        s.close(f.target(), 4_500, 1e-9, "a step up past the limit");
        f.setTarget(30);
        h.assertTrue(menu.clickMenuButton(hand, SidePortMenu.STEP_DOWN_BIG), "the step down was refused");
        s.close(f.target(), 0, 1e-9, "a step down past the limit");
        f.setTarget(9_999);
        s.close(f.target(), 4_500, 1e-9, "a target set too high");
        f.setTarget(Double.NaN);
        s.close(f.target(), 0, 1e-9, "a target that isn't a number");
        int[] steps = {-100, -10, 10, 100};
        f.setTarget(1_000);
        for (int i = 0; i < steps.length; i++) {
            h.assertTrue(menu.clickMenuButton(hand, SidePortMenu.STEP_DOWN_BIG + i), "step " + i + " was refused");
            s.close(f.target(), 1_000 + steps[i], 1e-9, "step " + i);
            f.setTarget(1_000);
        }

        // Its button flips the side port.
        h.assertTrue(menu.clickMenuButton(hand, SidePortMenu.FLIP), "the flip was refused");
        h.assertTrue(s.level.getBlockState(f.getBlockPos()).getValue(SidePortBlock.MIRRORED), "the flip didn't flip");

        // It offers the shipped tags, their gases, standard air and what's in the line.
        List<FilterOption> options = menu.options();
        for (FilterOption o : List.of(
                new FilterOption(GasTags.FLAMMABLE.id(), true),
                new FilterOption(GasTags.OXIDIZER.id(), true),
                new FilterOption(GasTags.TOXIC.id(), true),
                new FilterOption(N2, false),
                new FilterOption(O2, false),
                new FilterOption(H2, false),
                new FilterOption(CO2, false),
                new FilterOption(WATER, false))) {
            h.assertTrue(options.contains(o), "the GUI doesn't offer " + o);
        }

        // Its entries toggle.
        int nitrogen = options.indexOf(new FilterOption(N2, false));
        int flammable = options.indexOf(new FilterOption(GasTags.FLAMMABLE.id(), true));
        int co2 = options.indexOf(new FilterOption(CO2, false));
        h.assertFalse(menu.selected(nitrogen), "nitrogen is on when new");
        h.assertTrue(menu.selected(co2), "CO₂ is off when new");
        h.assertTrue(menu.clickMenuButton(hand, FilterMenu.OPTION + nitrogen), "the nitrogen entry was refused");
        h.assertTrue(menu.selected(nitrogen), "the nitrogen entry didn't turn on");
        h.assertTrue(f.selection().test(N2), "the filter doesn't select nitrogen");
        h.assertTrue(menu.clickMenuButton(hand, FilterMenu.OPTION + nitrogen), "the nitrogen entry was refused");
        h.assertFalse(menu.selected(nitrogen), "the nitrogen entry didn't turn off");
        h.assertFalse(f.selection().test(N2), "the filter still selects nitrogen");
        h.assertTrue(menu.clickMenuButton(hand, FilterMenu.OPTION + flammable), "the flammable entry was refused");
        h.assertTrue(f.selection().test(H2), "the filter doesn't select hydrogen by its tag");
        h.assertFalse(menu.selected(options.indexOf(new FilterOption(H2, false))), "hydrogen's own entry turned on");

        // Anything else is refused, and changes nothing.
        GasSelection selection = f.selection();
        for (int id : new int[] {-1, FilterMenu.OPTION + options.size(), Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            h.assertFalse(menu.clickMenuButton(hand, id), "button " + id + " wasn't refused");
        }
        h.assertValueEqual(f.selection(), selection, "the selection after refused buttons");

        // The client's menu gets the same list, shows the slots it's sent, and acts on nothing.
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), s.level.registryAccess());
        FilterMenu.OPTIONS.encode(buf, options);
        FilterMenu client = new FilterMenu(2, hand.getInventory(), buf);
        h.assertValueEqual(client.options(), options, "the client's list");
        h.assertFalse(client.selected(nitrogen), "a client's entry before its slot arrives");
        client.setData(SidePortMenu.DATA_COUNT + nitrogen, 1);
        h.assertTrue(client.selected(nitrogen), "a client's entry after its slot arrives");
        h.assertFalse(client.clickMenuButton(hand, FilterMenu.OPTION + nitrogen), "the client's menu acted");

        // The list is rebuilt the next time the GUI opens: water goes with the gas.
        back(f).takeFraction(1);
        h.assertFalse(f.offered().contains(new FilterOption(WATER, false)), "water with none in the line");

        // What it selected stays on offer, though nothing else would offer it.
        ResourceLocation pack = ResourceLocation.fromNamespaceAndPath("pack", "krypton");
        f.setSelection(GasSelection.of(List.of(pack), List.of()));
        h.assertTrue(f.offered().contains(new FilterOption(pack, false)), "a selected gas isn't on offer");
        s.clear();
        h.succeed();
    }

    /** A mixer's GUI: its steps stay in range, reach the client's slots, and it refuses anything else. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_074_a_mixer_gui_validates_on_the_server(GameTestHelper h) {
        Site s = new Site(h, 627);
        s.clear();
        MixerBlockEntity m = mixer(s, 4);
        Player hand = h.makeMockPlayer(GameType.SURVIVAL);
        MixerMenu menu = (MixerMenu) m.createMenu(1, hand.getInventory(), hand);
        h.assertValueEqual(menu.value(MixerMenu.DATA_BACK_PERCENT), 50, "the fraction's slot when new");

        int[] steps = {-10, -1, 1, 10};
        for (int i = 0; i < steps.length; i++) {
            m.setBackPercent(50);
            h.assertTrue(menu.clickMenuButton(hand, MixerMenu.MIX_DOWN_BIG + i), "fraction step " + i + " was refused");
            h.assertValueEqual(m.backPercent(), 50 + steps[i], "fraction step " + i);
            h.assertValueEqual(
                    menu.value(MixerMenu.DATA_BACK_PERCENT), 50 + steps[i], "fraction step " + i + "'s slot");
        }
        m.setBackPercent(95);
        h.assertTrue(menu.clickMenuButton(hand, MixerMenu.LAST_MIX_BUTTON), "the big step up was refused");
        h.assertValueEqual(m.backPercent(), 100, "a step up past 100 %");
        m.setBackPercent(5);
        h.assertTrue(menu.clickMenuButton(hand, MixerMenu.MIX_DOWN_BIG), "the big step down was refused");
        h.assertValueEqual(m.backPercent(), 0, "a step down past 0 %");
        m.setBackPercent(500);
        h.assertValueEqual(m.backPercent(), 100, "a fraction set too high");

        // The side port's buttons still work.
        h.assertTrue(menu.clickMenuButton(hand, SidePortMenu.FLIP), "the flip was refused");
        h.assertTrue(s.level.getBlockState(m.getBlockPos()).getValue(SidePortBlock.MIRRORED), "the flip didn't flip");
        h.assertTrue(menu.clickMenuButton(hand, SidePortMenu.STEP_DOWN_BIG), "the target's step was refused");

        for (int id : new int[] {-1, MixerMenu.LAST_MIX_BUTTON + 1, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            h.assertFalse(menu.clickMenuButton(hand, id), "button " + id + " wasn't refused");
        }
        MixerMenu client = new MixerMenu(2);
        h.assertFalse(client.clickMenuButton(hand, MixerMenu.MIX_DOWN_BIG), "the client's menu acted");
        s.clear();
        h.succeed();
    }

    /**
     * Saved, a device keeps its gas, switch and settings, and bad saved settings are clamped. Broken, it
     * drops itself, and it needs a pickaxe as a pump does.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_013_cont_077_a_device_saves_its_settings(GameTestHelper h) {
        Site s = new Site(h, 628);
        s.clear();
        FilterBlockEntity f = filter(s, 4);
        back(f).add(new GasParcel(Map.of(N2, 3.4), T));
        f.setOn(true);
        f.setTarget(777);
        GasSelection selection = GasSelection.of(List.of(O2), List.of(GasTags.FLAMMABLE));
        f.setSelection(selection);
        CompoundTag saved = f.saveWithoutMetadata(s.level.registryAccess());
        h.assertValueEqual(saved.getList("Ports", 10).size(), 3, "the filter's ports saved");
        FilterBlockEntity copy = new FilterBlockEntity(f.getBlockPos(), f.getBlockState());
        copy.loadWithComponents(saved, s.level.registryAccess());
        s.close(
                copy.port(SidePortEntity.BACK).stored().totalMoles(),
                3.4 * PipeVolumes.PORT / RUN,
                1e-9,
                "the back port's share");
        h.assertTrue(copy.on(), "the filter's switch wasn't saved");
        s.close(copy.target(), 777, 1e-9, "the filter's target wasn't saved");
        h.assertValueEqual(copy.selection(), selection, "the filter's selection wasn't saved");
        saved.putDouble("Target", 99_999);
        copy.loadWithComponents(saved, s.level.registryAccess());
        s.close(copy.target(), 4_500, 1e-9, "a saved target too high");
        saved.remove("Selection");
        copy.loadWithComponents(saved, s.level.registryAccess());
        h.assertValueEqual(copy.selection(), GasSelection.DEFAULT, "a save with no selection");
        s.clear();

        MixerBlockEntity m = mixer(s, 4);
        m.setOn(true);
        m.setTarget(888);
        m.setBackPercent(30);
        saved = m.saveWithoutMetadata(s.level.registryAccess());
        h.assertValueEqual(saved.getList("Ports", 10).size(), 3, "the mixer's ports saved");
        MixerBlockEntity again = new MixerBlockEntity(m.getBlockPos(), m.getBlockState());
        again.loadWithComponents(saved, s.level.registryAccess());
        h.assertTrue(again.on(), "the mixer's switch wasn't saved");
        s.close(again.target(), 888, 1e-9, "the mixer's target wasn't saved");
        h.assertValueEqual(again.backPercent(), 30, "the mixer's fraction wasn't saved");
        saved.putInt("BackPercent", 500);
        again.loadWithComponents(saved, s.level.registryAccess());
        h.assertValueEqual(again.backPercent(), 100, "a saved fraction too high");
        s.clear();

        assertDropsItself(h, s, filterState(Direction.EAST), AtmosItems.FILTER.get());
        assertDropsItself(h, s, mixerState(Direction.EAST), AtmosItems.MIXER.get());
        h.succeed();
    }

    private static void assertDropsItself(GameTestHelper h, Site s, BlockState state, Item item) {
        BlockPos pos = s.abs(4, 2, 2);
        s.level.setBlockAndUpdate(pos, state);
        List<ItemStack> drops = Block.getDrops(state, s.level, pos, s.level.getBlockEntity(pos));
        h.assertTrue(drops.size() == 1 && drops.get(0).is(item), state.getBlock() + " dropped " + drops);
        h.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), state.getBlock() + " isn't for a pickaxe");
        s.level.setBlockAndUpdate(pos, Site.AIR);
    }

    /** Burns a generator for some game ticks, as its ticker would. */
    private static void burn(GeneratorBlockEntity g, int ticks) {
        for (int i = 0; i < ticks; i++) {
            g.serverTick();
        }
    }

    /**
     * A real filter on a real controller: it draws 20 FE/t while on and nothing while off (CONT-065),
     * in the gas-handling group (CONT-064). With no controller, or its power cut, it doesn't run, and
     * stays switched on (CONT-060).
     */
    @GameTest(template = "empty", batch = "zas_atmos_filter_power", timeoutTicks = 400)
    public static void cont_060_a_real_filter_runs_on_a_controller_and_stops_when_its_power_is_cut(GameTestHelper h) {
        h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.FILTER_DRAW), 20, "the filter's draw");
        assertPowered(h, 629, filterState(Direction.EAST), FilterBlockEntity.class);
    }

    /** The same for a real mixer. */
    @GameTest(template = "empty", batch = "zas_atmos_mixer_power", timeoutTicks = 400)
    public static void cont_060_a_real_mixer_runs_on_a_controller_and_stops_when_its_power_is_cut(GameTestHelper h) {
        h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.MIXER_DRAW), 20, "the mixer's draw");
        assertPowered(h, 630, mixerState(Direction.EAST), MixerBlockEntity.class);
    }

    private static <T extends SidePortEntity> void assertPowered(
            GameTestHelper h, int index, BlockState state, Class<T> type) {
        Site s = new Site(h, index);
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
        T device;
        AreaAirControllerBlockEntity c;
        try {
            s.clear();
            // A 7 × 3 × 3 room, holding the device's lines.
            s.room(0, 8);
            device = line(s, 4, state, type);
            s.ticks(2);
            s.settle(GasMutations.add(s.level, s.point(4, 3, 3), new GasParcel(Map.of(N2, 1.0), T), Site.SOURCE));

            h.assertValueEqual(device.power().priority(), PowerPriority.GAS_HANDLING, "the device's group");
            h.assertValueEqual(device.power().draw(), 0, "the draw of a device that's off");
            h.assertValueEqual(device.power().drawPerAtmosTick(), 0L, "the bill of a device that's off");

            // Plenty of gas for the seconds below, far from the target, and well short of a burst.
            back(device).add(new GasParcel(Map.of(N2, 300.0), T));
            side(device).add(new GasParcel(Map.of(N2, 50.0), T));
            device.setTarget(PumpFlow.MAX_TARGET);
            device.setOn(true);
            h.assertValueEqual(device.power().draw(), 20, "the draw of a device that's on");
            h.assertValueEqual(
                    device.power().drawPerAtmosTick(), 20L * AtmosTicks.PERIOD, "the bill of a device that's on");

            // No controller: no power.
            s.cycles(3, device);
            h.assertFalse(device.power().isPowered(), "the device has power with no controller");
            h.assertFalse(device.running(), "the device runs with no controller");
            s.close(front(device).totalMoles(), 0, 1e-12, "the output with no controller");

            // A controller in its room, with a generator below it that has burned enough. It covers the
            // device by its zone, and pays for itself and the device, 25 FE/t, for one atmos tick.
            GeneratorBlockEntity g =
                    s.place(1, 1, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            burn(g, 40);
            int fe = g.energy().getEnergyStored();
            c = s.controller(1);
            s.cycle(c, device);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertTrue(device.power().isPowered(), "the controller didn't power its device");
            h.assertTrue(device.power().source() == c, "the device's power isn't the controller's");
            int left = g.energy().getEnergyStored() + c.buffer().getEnergyStored();
            h.assertValueEqual(fe - left, 25 * AtmosTicks.PERIOD, "the FE for the controller and the device");

            // It runs.
            h.assertTrue(device.running(), "the powered device isn't running");
            h.assertValueEqual(device.status(), InlineStatus.MOVING, "the powered device's status");
            h.assertTrue(litUp(s, device), "the powered device's block state");
            h.assertTrue(front(device).totalMoles() > 0, "the powered device moved nothing");
            s.cycles(2, c, device);

            // CONT-060: cut the power. The generator goes, and the buffer is spent.
            s.set(1, 1, 1, Site.AIR);
            c.buffer().set(0);
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        T d = device;
        AreaAirControllerBlockEntity controller = c;
        h.runAfterDelay(DevicePower.LEASE_TICKS + 2L * AtmosTicks.PERIOD, () -> {
            try {
                h.assertFalse(controller.powered(), "the controller has power with no generator");
                h.assertFalse(d.power().isPowered(), "the device's lease outlived its controller's power");
                h.assertFalse(d.running(), "the unpowered device runs");
                h.assertTrue(d.on(), "cutting the power switched the device off");
                d.step();
                double held = front(d).totalMoles();
                h.assertValueEqual(d.status(), InlineStatus.OFF, "the unpowered device's status");
                h.assertFalse(litUp(s, d), "the unpowered device's block state");
                s.cycles(4, d);
                s.close(front(d).totalMoles(), held, 1e-12, "the output with the power cut");
            } finally {
                cleanup.run();
            }
            h.succeed();
        });
    }
}
