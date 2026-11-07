package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlock;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlockEntity;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerMath;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerMenu;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.inline.SidePortBlock;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.scrubber.ScrubberStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The water electrolyzer in the world (CONT-050, CONT-055, CONT-056, CONT-060, CONT-065). Each test
 * builds in its own {@link Site} and steps its devices itself, within one game tick, on test power. The
 * power test has a batch of its own because it waits in real time for a lease to run out.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectrolyzerTests {
    private static final String BATCH = "zas_atmos_electrolyzer";
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation H2 = ElectrolyzerBlockEntity.H2;
    private static final double T = StandardAir.TEMPERATURE;

    /** An outlet's run in {@link #capped}: the port's buffer, a pipe and a cap. */
    private static final double RUN = PipeVolumes.PORT + 2 * PipeVolumes.PIPE;

    private ElectrolyzerTests() {}

    private static BlockState electrolyzer(Direction facing) {
        return AtmosBlocks.ELECTROLYZER.get().defaultBlockState().setValue(ElectrolyzerBlock.FACING, facing);
    }

    private static ElectrolyzerBlockEntity place(Site s, int x, int y, int z, Direction facing) {
        return s.place(x, y, z, electrolyzer(facing), ElectrolyzerBlockEntity.class);
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

    /** A connector port on a pipe, with an empty canister on it (CONT-083, CONT-017). */
    private static CanisterBlockEntity canisterOnPipe(Site s, int x, int y, int z) {
        pipe(s, x, y, z);
        s.place(x, y + 1, z, AtmosBlocks.CONNECTOR_PORT.get().defaultBlockState(), Object.class);
        return s.place(x, y + 2, z, AtmosBlocks.CANISTER.get().defaultBlockState(), CanisterBlockEntity.class);
    }

    /** An electrolyzer facing north at (4, y, 2), each outlet capped: O₂ east, H₂ west. */
    private static ElectrolyzerBlockEntity capped(Site s, int y) {
        cap(s, 2, y, 2, Direction.EAST);
        pipe(s, 3, y, 2);
        ElectrolyzerBlockEntity e = place(s, 4, y, 2, Direction.NORTH);
        pipe(s, 5, y, 2);
        cap(s, 6, y, 2, Direction.WEST);
        return e;
    }

    private static GasStore o2(ElectrolyzerBlockEntity e) {
        return e.oxygen().network().gas();
    }

    private static GasStore h2(ElectrolyzerBlockEntity e) {
        return e.hydrogen().network().gas();
    }

    private static void fill(ElectrolyzerBlockEntity e, int mb) {
        e.tank().fill(new FluidStack(Fluids.WATER, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    private static boolean lit(Site s, ElectrolyzerBlockEntity e) {
        return s.level.getBlockState(e.getBlockPos()).getValue(ElectrolyzerBlock.ON);
    }

    private static int shownLevel(Site s, ElectrolyzerBlockEntity e) {
        return s.level.getBlockState(e.getBlockPos()).getValue(ElectrolyzerBlock.WATER);
    }

    // ---- CONT-055 -------------------------------------------------------------------------------

    /**
     * CONT-055: on, powered and with water, it makes 1 mol of O₂ and 2 mol of H₂ each second, at
     * 20 °C, the O₂ into its left outlet's network and the H₂ into its right's. It makes nothing
     * while off. In a sealed room, the room keeps every mole it had: no gas leaves it but by its
     * outlets.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_055_it_makes_1_mol_of_o2_and_2_mol_of_h2_a_second(GameTestHelper h) {
        Site s = new Site(h, 1550);
        try {
            s.clear();
            s.room(0, 8); // interior x 1–7, y 1–3, z 1–3
            ElectrolyzerBlockEntity e = capped(s, 1);
            s.settle(GasMutations.add(s.level, s.point(4, 2, 2), new GasParcel(Map.of(N2, 300.0), T), Site.SOURCE));
            double room = s.zone(4, 2, 2).mixture().totalMoles();
            e.power().setTestPowered(true);
            fill(e, ElectrolyzerMath.TANK_MB);
            h.assertTrue(o2(e) != h2(e), "its outlets share a network");
            s.close(o2(e).volume(), RUN, 1e-9, "the O2 outlet's volume");
            s.close(h2(e).volume(), RUN, 1e-9, "the H2 outlet's volume");

            // Off, it makes nothing.
            s.cycle(e);
            h.assertValueEqual(e.status(), InlineStatus.OFF, "the status while off");
            s.close(o2(e).totalMoles() + h2(e).totalMoles(), 0, 0, "the gas made while off");

            e.setOn(true);
            int seconds = 5;
            for (int i = 1; i <= seconds; i++) {
                s.cycle(e);
                h.assertValueEqual(e.status(), InlineStatus.MOVING, "the status, second " + i);
                s.close(o2(e).moles(O2), i * ElectrolyzerMath.O2_PER_SECOND, 1e-9, "the O2 after " + i + " s");
                s.close(h2(e).moles(H2), i * ElectrolyzerMath.H2_PER_SECOND, 1e-9, "the H2 after " + i + " s");
            }
            s.close(ElectrolyzerMath.O2_PER_SECOND, 1, 0, "the O2 rate, mol/s");
            s.close(ElectrolyzerMath.H2_PER_SECOND, 2, 0, "the H2 rate, mol/s");
            h.assertValueEqual(o2(e).moles().keySet(), Set.of(O2), "the gases in the O2 outlet");
            h.assertValueEqual(h2(e).moles().keySet(), Set.of(H2), "the gases in the H2 outlet");
            s.close(o2(e).temperature(), T, 1e-9, "the O2's temperature");
            s.close(h2(e).temperature(), T, 1e-9, "the H2's temperature");
            s.close(s.zone(4, 2, 2).mixture().totalMoles(), room, 1e-9, "the room's moles");
            h.assertTrue(lit(s, e), "its block doesn't show it making gas");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * CONT-055: facing its front, O₂ leaves only by its left outlet and H₂ only by its right, whichever
     * way it faces. With a pipe on each of its six sides, only the two at its outlets join it, and
     * each gets only its own gas.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_055_each_gas_leaves_only_by_its_own_port(GameTestHelper h) {
        Site s = new Site(h, 1551);
        try {
            BlockPos at = new BlockPos(4, 3, 3);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                s.clear();
                ElectrolyzerBlockEntity e = place(s, at.getX(), at.getY(), at.getZ(), facing);
                for (Direction d : Direction.values()) {
                    BlockPos p = at.relative(d);
                    pipe(s, p.getX(), p.getY(), p.getZ());
                }
                // Facing its front is looking the other way: its left is the front's clockwise turn.
                Direction left = facing.getOpposite().getCounterClockWise();
                Direction right = facing.getOpposite().getClockWise();
                h.assertValueEqual(
                        ElectrolyzerBlock.oxygenFace(e.getBlockState()), left, "the O2 face, facing " + facing);
                h.assertValueEqual(
                        ElectrolyzerBlock.hydrogenFace(e.getBlockState()), right, "the H2 face, facing " + facing);
                for (Direction d : Direction.values()) {
                    BlockPos p = at.relative(d);
                    boolean joined = s.level
                            .getBlockState(s.abs(p.getX(), p.getY(), p.getZ()))
                            .getValue(PipeBlock.CONNECTIONS.get(d.getOpposite()));
                    h.assertValueEqual(joined, d == left || d == right, "facing " + facing + ", the pipe on its " + d);
                }

                e.power().setTestPowered(true);
                fill(e, ElectrolyzerMath.BUCKET_MB);
                e.setOn(true);
                e.step();
                h.assertValueEqual(e.status(), InlineStatus.MOVING, "the status, facing " + facing);
                for (Direction d : Direction.values()) {
                    BlockPos p = at.relative(d);
                    GasStore gas = s.entity(p.getX(), p.getY(), p.getZ(), PipeBlockEntity.class)
                            .network()
                            .gas();
                    String where = "facing " + facing + ", the pipe on its " + d;
                    if (d == left) {
                        h.assertValueEqual(gas.moles().keySet(), Set.of(O2), where + ": its gases");
                        s.close(gas.moles(O2), ElectrolyzerMath.O2_PER_SECOND, 1e-9, where + ": its O2");
                    } else if (d == right) {
                        h.assertValueEqual(gas.moles().keySet(), Set.of(H2), where + ": its gases");
                        s.close(gas.moles(H2), ElectrolyzerMath.H2_PER_SECOND, 1e-9, where + ": its H2");
                    } else {
                        s.close(gas.totalMoles(), 0, 0, where + ": its gas");
                    }
                }
            }
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * It makes nothing while an outlet's network is at 4,500 kPa, the most a pump fills one to, and
     * goes on once the gas is taken away. Its block doesn't show it making gas meanwhile.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_055_it_stops_while_an_outlet_is_full(GameTestHelper h) {
        Site s = new Site(h, 1552);
        try {
            s.clear();
            ElectrolyzerBlockEntity e = capped(s, 2);
            e.power().setTestPowered(true);
            fill(e, ElectrolyzerMath.BUCKET_MB);
            e.setOn(true);
            GasStore h2 = h2(e);
            double full = PumpFlow.MAX_TARGET * h2.volume() / (GasMixture.GAS_CONSTANT * T) * 1.001;
            h2.add(new GasParcel(Map.of(H2, full), T));
            h.assertTrue(
                    h2.pressure() >= ElectrolyzerMath.MAX_OUTPUT_KPA, "the H2 outlet isn't full: " + h2.pressure());
            double water = e.tank().water();
            e.step();
            h.assertValueEqual(e.status(), InlineStatus.OUTPUT_FULL, "the status with its H2 outlet full");
            s.close(o2(e).totalMoles(), 0, 0, "the O2 made with the H2 outlet full");
            s.close(e.tank().water(), water, 0, "the water used with the H2 outlet full");
            h.assertFalse(lit(s, e), "its block shows it making gas with its outlet full");

            h2.drain();
            e.step();
            h.assertValueEqual(e.status(), InlineStatus.MOVING, "the status once the H2 is taken");
            h.assertTrue(lit(s, e), "its block doesn't show it making gas");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    // ---- CONT-056 -------------------------------------------------------------------------------

    /**
     * CONT-056: water buckets fill the 4-bucket tank, from any side, each leaving an empty bucket, and
     * a fifth is refused. A fluid capability fills it too, up to the tank, and takes nothing back out.
     * Its block shows one level per bucket it has begun to fill, and the water is saved.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_056_buckets_and_a_fluid_capability_fill_the_tank(GameTestHelper h) {
        Site s = new Site(h, 1553);
        try {
            s.clear();
            ElectrolyzerBlockEntity e = place(s, 4, 2, 2, Direction.NORTH);
            BlockPos pos = e.getBlockPos();
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            List<Direction> sides = List.of(Direction.NORTH, Direction.EAST, Direction.UP, Direction.SOUTH);
            for (int i = 1; i <= 4; i++) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), sides.get(i - 1), pos, false);
                s.level
                        .getBlockState(pos)
                        .useItemOn(player.getMainHandItem(), s.level, player, InteractionHand.MAIN_HAND, hit);
                s.close(e.tank().water(), i * 1000, 0, "the water after " + i + " buckets");
                h.assertTrue(
                        player.getMainHandItem().is(Items.BUCKET), "bucket " + i + " left " + player.getMainHandItem());
                h.assertValueEqual(shownLevel(s, e), i, "the level shown after " + i + " buckets");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            s.level
                    .getBlockState(pos)
                    .useItemOn(
                            player.getMainHandItem(),
                            s.level,
                            player,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            s.close(e.tank().water(), ElectrolyzerMath.TANK_MB, 0, "the water after a fifth bucket");
            h.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "the full tank took a fifth bucket");

            // Through the fluid capability on its back: a second electrolyzer, filled in parts.
            ElectrolyzerBlockEntity f = place(s, 8, 2, 2, Direction.EAST);
            IFluidHandler back = s.level.getCapability(
                    Capabilities.FluidHandler.BLOCK, f.getBlockPos(), ElectrolyzerBlock.inletFace(f.getBlockState()));
            h.assertTrue(back != null, "no fluid capability on its back");
            h.assertValueEqual(back.getTankCapacity(0), ElectrolyzerMath.TANK_MB, "the tank's capacity");
            h.assertValueEqual(
                    back.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE), 0, "lava taken");
            h.assertValueEqual(
                    back.fill(new FluidStack(Fluids.WATER, 2500), IFluidHandler.FluidAction.SIMULATE),
                    2500,
                    "simulated");
            s.close(f.tank().water(), 0, 0, "the water after a simulated fill");
            h.assertValueEqual(
                    back.fill(new FluidStack(Fluids.WATER, 2500), IFluidHandler.FluidAction.EXECUTE), 2500, "filled");
            h.assertValueEqual(shownLevel(s, f), 3, "the level shown at 2,500 mB");
            h.assertValueEqual(
                    back.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE),
                    1500,
                    "filled past the tank");
            s.close(f.tank().water(), ElectrolyzerMath.TANK_MB, 0, "the water through the capability");
            h.assertValueEqual(
                    back.getFluidInTank(0).getAmount(), ElectrolyzerMath.TANK_MB, "what the capability shows");
            h.assertTrue(back.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "water drained from it");
            h.assertTrue(
                    back.drain(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE)
                            .isEmpty(),
                    "water drained from it by type");

            // Saved and loaded, the water stays.
            ElectrolyzerBlockEntity half = place(s, 12, 2, 2, Direction.NORTH);
            fill(half, 2345);
            CompoundTag saved = half.saveCustomOnly(s.level.registryAccess());
            ElectrolyzerBlockEntity loaded = new ElectrolyzerBlockEntity(BlockPos.ZERO, electrolyzer(Direction.NORTH));
            loaded.loadCustomOnly(saved, s.level.registryAccess());
            s.close(loaded.tank().water(), 2345, 0, "the water saved and loaded");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * CONT-056: the fluid capability is offered on its back, where its water inlet is, and on no other
     * face, whichever way it faces.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_056_the_fluid_capability_is_on_the_back_only(GameTestHelper h) {
        Site s = new Site(h, 1554);
        try {
            s.clear();
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                ElectrolyzerBlockEntity e = place(s, 4, 2, 2, facing);
                for (Direction side : Direction.values()) {
                    IFluidHandler cap = s.level.getCapability(Capabilities.FluidHandler.BLOCK, e.getBlockPos(), side);
                    boolean back = side == facing.getOpposite();
                    h.assertValueEqual(cap != null, back, "facing " + facing + ", the capability on its " + side);
                    if (back) {
                        h.assertTrue(cap == e.tank(), "facing " + facing + ": the capability isn't its tank");
                    }
                }
                h.assertTrue(
                        s.level.getCapability(Capabilities.FluidHandler.BLOCK, e.getBlockPos(), null) == null,
                        "facing " + facing + ": the capability with no side");
                s.set(4, 2, 2, Site.AIR);
            }
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * CONT-056: it splits 2 mol of water a second, as many moles as the H₂ it makes, so a bucket lasts
     * about 7.7 h. With too little left for a second it makes what the water pays for, then stops with
     * no water, and its block shows the tank empty and stops showing it making gas.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_056_the_water_used_per_second(GameTestHelper h) {
        Site s = new Site(h, 1555);
        try {
            s.clear();
            ElectrolyzerBlockEntity e = capped(s, 2);
            e.power().setTestPowered(true);
            fill(e, 1);
            e.setOn(true);
            double perSecond = ElectrolyzerMath.H2_PER_SECOND / ElectrolyzerMath.WATER_MOLES_PER_MB;
            s.close(perSecond, 0.036030, 1e-6, "the water split a second, mB");

            int seconds = (int) Math.floor(1 / perSecond); // 27 whole seconds in 1 mB
            for (int i = 1; i <= seconds; i++) {
                e.step();
                s.close(1 - e.tank().water(), i * perSecond, 1e-9, "the water used after " + i + " s");
            }
            double used = 1 - e.tank().water();
            s.close(
                    used * ElectrolyzerMath.WATER_MOLES_PER_MB,
                    h2(e).moles(H2),
                    1e-9,
                    "the water's moles and the H2's");
            double hours = ElectrolyzerMath.BUCKET_MB / (used / seconds) / 3600;
            h.assertTrue(Math.abs(hours - 7.7) < 0.05, "a bucket lasts " + hours + " h");
            h.assertValueEqual(shownLevel(s, e), 1, "the level shown with water left");

            // The last of it: part of a second's gas, then none.
            double left = e.tank().water();
            double o2 = o2(e).moles(O2);
            e.step();
            h.assertValueEqual(e.status(), InlineStatus.MOVING, "the status on the last of the water");
            s.close(o2(e).moles(O2) - o2, left / perSecond * ElectrolyzerMath.O2_PER_SECOND, 1e-9, "the last O2");
            s.close(e.tank().water(), 0, 0, "the water left");
            e.step();
            h.assertValueEqual(e.status(), InlineStatus.NO_WATER, "the status with no water");
            h.assertValueEqual(shownLevel(s, e), 0, "the level shown when empty");
            h.assertFalse(lit(s, e), "its block shows it making gas with no water");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /** CONT-074: its window has its switch and nothing else; the server refuses any other button. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_074_its_window_only_switches_it(GameTestHelper h) {
        Site s = new Site(h, 1556);
        try {
            s.clear();
            ElectrolyzerBlockEntity e = place(s, 4, 2, 2, Direction.NORTH);
            fill(e, 1500);
            ElectrolyzerMenu menu = new ElectrolyzerMenu(1, e);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            h.assertTrue(menu.clickMenuButton(player, InlineDeviceMenu.TOGGLE), "the switch was refused");
            h.assertTrue(e.on(), "the switch didn't switch it on");
            for (int id : new int[] {-1, 1, 2, 99}) {
                h.assertFalse(menu.clickMenuButton(player, id), "button " + id + " was taken");
            }
            h.assertTrue(e.on(), "a refused button changed it");
            ElectrolyzerMenu client = new ElectrolyzerMenu(2);
            h.assertValueEqual(e.data().getCount(), ElectrolyzerMenu.DATA_COUNT, "its data slots");
            h.assertValueEqual(e.data().get(ElectrolyzerMenu.DATA_WATER), 1500, "the water it syncs");
            h.assertValueEqual(client.water(), 0, "a new client menu's water");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    // ---- CONT-060, CONT-064, CONT-065, CONT-069 ---------------------------------------------------

    /** Burns a generator for some game ticks, as its ticker would. */
    private static void burn(GeneratorBlockEntity g, int ticks) {
        for (int i = 0; i < ticks; i++) {
            g.serverTick();
        }
    }

    /**
     * A real electrolyzer under a real controller that a real generator powers: with no controller it
     * has no power, even on and beside a burning generator (CONT-069). Under the controller it is in
     * the last power group, gas production (CONT-064), the controller pays 80 FE/t for it while it is
     * on, with nothing drawn while it is off (CONT-065), and it makes gas. When the generator goes and
     * the buffer is spent, its lease runs out and it stops, still switched on (CONT-060).
     */
    @GameTest(template = "empty", batch = "zas_atmos_electrolyzer_power", timeoutTicks = 400)
    public static void cont_060_an_electrolyzer_stops_when_its_power_is_cut(GameTestHelper h) {
        Site s = new Site(h, 1557);
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
        ElectrolyzerBlockEntity e;
        AreaAirControllerBlockEntity c;
        try {
            s.clear();
            // A 7 × 3 × 3 room, holding the electrolyzer and a burning generator beside it.
            s.room(0, 8);
            e = place(s, 5, 1, 2, Direction.SOUTH);
            fill(e, ElectrolyzerMath.TANK_MB);
            GeneratorBlockEntity beside =
                    s.place(5, 1, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            beside.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            burn(beside, 20);
            h.assertTrue(beside.energy().getEnergyStored() > 0, "the generator holds no FE");
            s.ticks(2);
            s.settle(GasMutations.add(s.level, s.point(4, 3, 3), new GasParcel(Map.of(N2, 1.0), T), Site.SOURCE));

            h.assertValueEqual(e.power().priority(), PowerPriority.AUXILIARY, "its group");
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.ELECTROLYZER_DRAW), 80, "its draw");
            h.assertValueEqual(e.power().draw(), 0, "the draw of one that's off");
            h.assertValueEqual(e.power().drawPerAtmosTick(), 0L, "the bill of one that's off");
            e.setOn(true);
            h.assertValueEqual(e.power().draw(), 80, "the draw of one that's on");
            h.assertValueEqual(e.power().drawPerAtmosTick(), 80L * AtmosTicks.PERIOD, "the bill of one that's on");

            // No controller: no power, on and beside a burning generator.
            s.cycles(3, e);
            h.assertFalse(e.power().isPowered(), "it has power with no controller");
            h.assertFalse(e.running(), "it runs with no controller");
            s.close(o2(e).totalMoles() + h2(e).totalMoles(), 0, 0, "the gas made with no controller");

            // A controller in its room, with a generator below it that has burned enough. It covers
            // the electrolyzer by its zone, and pays for itself and it, 85 FE/t, for one atmos tick.
            GeneratorBlockEntity g =
                    s.place(1, 1, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            burn(g, 130); // enough for three atmos ticks
            int fe = g.energy().getEnergyStored();
            c = s.controller(1);
            s.cycle(c, e);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertTrue(e.power().isPowered(), "the controller didn't power it");
            h.assertTrue(e.power().source() == c, "its power isn't the controller's");
            int left = g.energy().getEnergyStored() + c.buffer().getEnergyStored();
            h.assertValueEqual(fe - left, 85 * AtmosTicks.PERIOD, "the FE for the controller and the electrolyzer");

            // It makes gas.
            h.assertTrue(e.running(), "the powered electrolyzer isn't running");
            h.assertValueEqual(e.status(), InlineStatus.MOVING, "the powered electrolyzer's status");
            h.assertTrue(lit(s, e), "the powered electrolyzer's block state");
            s.close(o2(e).moles(O2), ElectrolyzerMath.O2_PER_SECOND, 1e-9, "the O2 of its first second");
            s.close(h2(e).moles(H2), ElectrolyzerMath.H2_PER_SECOND, 1e-9, "the H2 of its first second");
            s.cycles(2, c, e);

            // CONT-060: cut the power. Both generators go, and the buffer is spent.
            s.set(1, 1, 1, Site.AIR);
            s.set(5, 1, 1, Site.AIR);
            c.buffer().set(0);
        } catch (RuntimeException | Error x) {
            cleanup.run();
            throw x;
        }
        ElectrolyzerBlockEntity electrolyzer = e;
        AreaAirControllerBlockEntity controller = c;
        h.runAfterDelay(DevicePower.LEASE_TICKS + 2L * AtmosTicks.PERIOD, () -> {
            try {
                h.assertFalse(controller.powered(), "the controller has power with no generator");
                h.assertFalse(electrolyzer.power().isPowered(), "its lease outlived its controller's power");
                h.assertFalse(electrolyzer.running(), "the unpowered electrolyzer runs");
                h.assertTrue(electrolyzer.on(), "cutting the power switched it off");
                electrolyzer.step();
                double o2 = o2(electrolyzer).totalMoles();
                double h2 = h2(electrolyzer).totalMoles();
                double water = electrolyzer.tank().water();
                h.assertTrue(o2 >= 3 - 1e-9, "it made too little before its power was cut: " + o2);
                h.assertValueEqual(electrolyzer.status(), InlineStatus.OFF, "the unpowered electrolyzer's status");
                h.assertFalse(lit(s, electrolyzer), "the unpowered electrolyzer's block state");
                s.cycles(4, electrolyzer);
                s.close(o2(electrolyzer).totalMoles(), o2, 0, "the O2 with the power cut");
                s.close(h2(electrolyzer).totalMoles(), h2, 0, "the H2 with the power cut");
                s.close(electrolyzer.tank().water(), water, 0, "the water used with the power cut");
            } finally {
                cleanup.run();
            }
            h.succeed();
        });
    }

    // ---- CONT-050 -------------------------------------------------------------------------------

    /** The mods a matrix run adds, which are about speed and have no gas or power (NFR-025). */
    private static final Set<String> MATRIX_MODS = Set.of("modernfix", "ferritecore", "lithium");

    /**
     * CONT-050: with only ZAS³ installed, gas comes by each R1 route. The electrolyzer fills a
     * canister with O₂. A scrubber takes a room's O₂ and N₂ into its pipe, and a filter splits them:
     * the O₂ it selects into one canister at its side outlet, the N₂ into another at its output.
     * The devices run on test power; {@code cont_060_an_electrolyzer_stops_when_its_power_is_cut}
     * runs one on a generator and a controller.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_050_gas_by_each_route(GameTestHelper h) {
        Set<String> mods = new HashSet<>();
        ModList.get().forEachModInOrder(m -> mods.add(m.getModId()));
        Set<String> allowed = new HashSet<>(Set.of("minecraft", "neoforge", "zas", ZasAtmosMod.MOD_ID));
        if (Boolean.getBoolean("zas.compatMatrix")) {
            allowed.addAll(MATRIX_MODS);
        }
        mods.removeAll(allowed);
        h.assertValueEqual(mods, Set.of(), "mods other than ZAS³");

        Site s = new Site(h, 1558);
        try {
            s.clear();
            // The electrolyzer, facing south: its O₂ outlet is west, into a pipe under a canister.
            ElectrolyzerBlockEntity e = place(s, 13, 1, 2, Direction.SOUTH);
            CanisterBlockEntity fromWater = canisterOnPipe(s, 12, 1, 2);
            h.assertTrue(e.oxygen().network().members().contains(fromWater), "the canister isn't on its O2 outlet");
            e.power().setTestPowered(true);
            fill(e, ElectrolyzerMath.BUCKET_MB);
            e.setOn(true);

            // A sealed room of air, x 6–10, with a scrubber in its west wall, facing in. Behind it, a
            // pipe to a filter facing west: its output, west, and its side outlet, north, each under
            // a canister.
            s.room(6, 10);
            double air = 1000;
            s.settle(GasMutations.add(
                    s.level,
                    s.point(8, 2, 2),
                    new GasParcel(
                            Map.of(
                                    N2, air * (1 - StandardAir.OXYGEN_FRACTION),
                                    O2, air * StandardAir.OXYGEN_FRACTION),
                            T),
                    Site.SOURCE));
            ScrubberBlockEntity scrubber =
                    s.place(6, 2, 2, ScrubberTests.scrubber(Direction.EAST), ScrubberBlockEntity.class);
            s.tick();
            pipe(s, 5, 2, 2);
            BlockState filterState =
                    AtmosBlocks.FILTER.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.WEST);
            FilterBlockEntity filter = s.place(4, 2, 2, filterState, FilterBlockEntity.class);
            h.assertValueEqual(SidePortBlock.sideFace(filterState), Direction.NORTH, "the filter's side outlet");
            CanisterBlockEntity nitrogen = canisterOnPipe(s, 3, 2, 2);
            CanisterBlockEntity oxygen = canisterOnPipe(s, 4, 2, 1);
            h.assertTrue(
                    filter.port(SidePortEntity.FRONT).network().members().contains(nitrogen),
                    "the N2 canister isn't on the filter's output");
            h.assertTrue(
                    filter.port(SidePortEntity.SIDE).network().members().contains(oxygen),
                    "the O2 canister isn't on the filter's side outlet");
            h.assertTrue(
                    scrubber.network().members().contains(filter.port(SidePortEntity.BACK)),
                    "the scrubber's pipe isn't the filter's input");

            scrubber.power().setTestPowered(true);
            scrubber.command(ScrubberMode.SCRUB, GasSelection.of(List.of(O2, N2), List.of()));
            filter.power().setTestPowered(true);
            filter.setSelection(GasSelection.of(List.of(O2), List.of()));
            filter.setTarget(PumpFlow.MAX_TARGET);
            filter.setOn(true);
            for (int i = 0; i < 20; i++) {
                s.cycle(scrubber, filter, e);
            }
            h.assertValueEqual(scrubber.status(), ScrubberStatus.MOVING, "the scrubber's status");
            h.assertValueEqual(filter.status(), InlineStatus.MOVING, "the filter's status");

            assertHolds(h, fromWater, O2, "the electrolyzer's canister");
            assertHolds(h, oxygen, O2, "the filter's O2 canister");
            assertHolds(h, nitrogen, N2, "the filter's N2 canister");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /** A canister holds a mole or more of one gas, and nothing else. */
    private static void assertHolds(GameTestHelper h, CanisterBlockEntity canister, ResourceLocation gas, String what) {
        GasParcel share = canister.share();
        h.assertTrue(share.moles(gas) >= 1, what + ": " + share.moles(gas) + " mol of " + gas);
        h.assertTrue(share.totalMoles() - share.moles(gas) <= 1e-12, what + " holds other gases: " + share.moles());
    }
}
