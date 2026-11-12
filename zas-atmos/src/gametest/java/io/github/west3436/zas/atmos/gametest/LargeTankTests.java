package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.STONE;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosCreativeTab;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import io.github.west3436.zas.atmos.tank.LargeTankBlockEntity;
import io.github.west3436.zas.atmos.tool.WrenchItem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The large gas tank in the world: its volume, where pipes join it, filling and emptying it through
 * a pipe with no gas made or lost, its pressure limit, what breaking it does, its two halves, what
 * it saves, the wrench on it, and that it seals nothing. Each test builds in its own {@link Area}
 * and drives device steps and atmos ticks itself, within one game tick, in a batch of its own.
 *
 * <p>It follows the rules the other pipe parts have: a node's volume and share (CONT-003, CONT-007),
 * its gas into its cell when it goes (CONT-009), the wrench (CONT-035, CONT-091), and a canister's
 * rating (CONT-101, CONT-102). The analyzer on it is in {@link AnalyzerTests}, its gas handler in
 * {@link PipeGasHandlerTests}.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LargeTankTests {
    private static final String BATCH = "zas_atmos_large_tank";
    private static final double ATM = ContainerFailure.ATM_KPA;

    /** A gas the ambient doesn't hold, so only what a test puts in counts. */
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    private LargeTankTests() {}

    // ---- Helpers ----------------------------------------------------------------------------------

    static BlockState lower(Direction facing) {
        return AtmosBlocks.LARGE_GAS_TANK.get().defaultBlockState().setValue(LargeTankBlock.FACING, facing);
    }

    static BlockState upper(Direction facing) {
        return lower(facing).setValue(LargeTankBlock.HALF, DoubleBlockHalf.UPPER);
    }

    /**
     * Stands a whole tank with its lower half at a place. The upper half goes in first: the lower one
     * would go at once with nothing above it.
     */
    static LargeTankBlockEntity stand(Area a, int x, int y, int z, Direction facing) {
        a.set(x, y + 1, z, upper(facing));
        a.place(x, y, z, lower(facing));
        return a.entity(x, y, z);
    }

    /** Whether both halves stand at a place, facing one way. */
    private static boolean whole(Area a, int x, int y, int z) {
        BlockState below = a.get(x, y, z);
        return below.is(AtmosBlocks.LARGE_GAS_TANK.get())
                && a.get(x, y + 1, z) == upper(below.getValue(LargeTankBlock.FACING));
    }

    /** Whether nothing of a tank is left at a place. */
    private static boolean gone(Area a, int x, int y, int z) {
        return a.get(x, y, z).isAir() && a.get(x, y + 1, z).isAir();
    }

    /** The moles that make a pressure in a volume at a temperature. */
    private static double molesFor(double kPa, double volume, double kelvin) {
        return kPa * volume / (GasMixture.GAS_CONSTANT * kelvin);
    }

    private static PipeNetwork network(Area a, int x, int y, int z) {
        return a.<PipeNodeEntity>entity(x, y, z).network();
    }

    private static Set<Direction> offered(Area a, int x, int y, int z) {
        Set<Direction> faces = EnumSet.noneOf(Direction.class);
        for (Direction d : Direction.values()) {
            if (a.level.getCapability(IGasHandler.BLOCK, a.abs(x, y, z), d) != null) {
                faces.add(d);
            }
        }
        return faces;
    }

    /** A survival player the game's own paths take for a client, at a block of the area. */
    private static ServerPlayer player(Area a, int x, int y, int z) {
        return ListeningPlayer.client(a.level, Vec3.atBottomCenterOf(a.abs(x, y, z)), false)
                .player();
    }

    /** A survival player holding a wrench. */
    private static Player wrenching(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.WRENCH.get()));
        return player;
    }

    /** A click with the wrench on a face of a block, as the game makes it, sneaking or not. */
    private static InteractionResult wrench(Area a, Player player, int x, int y, int z, Direction face, boolean sneak) {
        BlockPos pos = a.abs(x, y, z);
        player.setShiftKeyDown(sneak);
        Vec3 hit = Vec3.atCenterOf(pos).relative(face, 0.5);
        return player.getMainHandItem()
                .onItemUseFirst(
                        new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false)));
    }

    private static boolean plainTank(ItemStack stack) {
        return ItemStack.matches(stack, new ItemStack(AtmosItems.LARGE_GAS_TANK.get()));
    }

    // ---- Its volume, and where pipes join it ------------------------------------------------------

    /**
     * A tank holds ten canisters' worth. Whichever way it faces, a pipe joins it behind its lower
     * half and nowhere else: not at its sides, its front or its top, and not on its upper half. It
     * offers its gas to another mod's pipe on that one face, and its upper half has no block entity.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_holds_ten_canisters_and_takes_a_pipe_only_behind_its_lower_half(GameTestHelper h) {
        Area a = new Area(h, 4850);
        a.clear(0, 0, 30, 6);
        h.assertValueEqual(PipeVolumes.LARGE_TANK, 10 * PipeVolumes.CANISTER, "its volume against a canister's");
        LargeTankBlock block = AtmosBlocks.LARGE_GAS_TANK.get();
        BlockState pipe = GasDeviceTests.pipe();
        int x = 3;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Direction back = facing.getOpposite();
            LargeTankBlockEntity tank = stand(a, x, 1, 3, facing);
            h.assertTrue(whole(a, x, 1, 3), facing + ": the tank doesn't stand whole");
            a.close(tank.nodeVolume(), 10_000, facing + ": its volume");
            a.close(tank.network().volume(), PipeVolumes.LARGE_TANK, facing + ": its network, alone");
            h.assertTrue(
                    a.level.getBlockEntity(a.abs(x, 2, 3)) == null, facing + ": the upper half has a block entity");
            for (Direction face : Direction.values()) {
                h.assertValueEqual(
                        block.pipeConnects(lower(facing), face, pipe),
                        face == back,
                        facing + ": a pipe on its " + face);
                h.assertFalse(
                        block.pipeConnects(upper(facing), face, pipe),
                        facing + ": a pipe joins its upper half on " + face);
            }
            h.assertValueEqual(offered(a, x, 1, 3), EnumSet.of(back), facing + ": the faces it offers its gas on");
            h.assertValueEqual(
                    offered(a, x, 2, 3), EnumSet.noneOf(Direction.class), facing + ": the faces of its upper half");

            // Pipes all round both halves, and one on top.
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int y = 1; y <= 2; y++) {
                    a.place(x + side.getStepX(), y, 3 + side.getStepZ(), pipe);
                }
            }
            a.place(x, 3, 3, pipe);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int y = 1; y <= 2; y++) {
                    boolean arm = GasDeviceTests.joined(
                            a.get(x + side.getStepX(), y, 3 + side.getStepZ()), side.getOpposite());
                    h.assertValueEqual(
                            arm, side == back && y == 1, facing + ": the pipe to its " + side + " at y = " + y);
                }
            }
            h.assertFalse(GasDeviceTests.joined(a.get(x, 3, 3), Direction.DOWN), facing + ": the pipe on top joins it");
            // The pipe behind it, and the pipe above that one, are its network; no other pipe is.
            PipeNetwork net = tank.network();
            a.close(net.volume(), PipeVolumes.LARGE_TANK + 2 * PipeVolumes.PIPE, facing + ": its network's volume");
            for (Direction side : Direction.Plane.HORIZONTAL) {
                boolean same = network(a, x + side.getStepX(), 1, 3 + side.getStepZ()) == net;
                h.assertValueEqual(same, side == back, facing + ": on the network of the pipe to its " + side);
            }
            x += 6;
        }
        a.clear(0, 0, 30, 6);
        h.succeed();
    }

    // ---- Filling and emptying ---------------------------------------------------------------------

    /**
     * A pump between two tanks empties one into the other through their pipes: the second fills to
     * the pump's target, the first falls by as much, and the moles and the thermal energy are what
     * they were. Each tank holds its part of its network's gas by volume.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_fills_and_empties_through_a_pipe_and_no_gas_is_made_or_lost(GameTestHelper h) {
        Area a = new Area(h, 4851);
        a.clear(0, 0, 8, 4);
        LargeTankBlockEntity from = stand(a, 1, 1, 2, Direction.WEST); // its back to the east
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.place(
                3,
                1,
                2,
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        a.place(4, 1, 2, GasDeviceTests.pipe());
        LargeTankBlockEntity to = stand(a, 5, 1, 2, Direction.EAST); // its back to the west
        PressurePumpBlockEntity pump = a.entity(3, 1, 2);
        pump.power().setTestPowered(true);

        double run = PipeVolumes.LARGE_TANK + PipeVolumes.PIPE + PipeVolumes.PORT;
        double part = PipeVolumes.LARGE_TANK / run;
        h.assertTrue(pump.input().network() == from.network(), "the first tank isn't on the pump's input");
        h.assertTrue(pump.output().network() == to.network(), "the second tank isn't on the pump's output");
        GasStore in = pump.input().network().gas();
        GasStore out = pump.output().network().gas();
        a.close(in.volume(), run, "the input network's volume");
        a.close(out.volume(), run, "the output network's volume");

        in.add(new GasParcel(Map.of(N2, molesFor(2_000, run, T), O2, 50.0), T));
        double moles = in.totalMoles();
        double energy = in.thermalEnergy();
        double full = in.pressure();
        a.close(from.share().totalMoles(), moles * part, "what the full tank holds");
        h.assertTrue(to.share().isEmpty(), "the empty tank holds " + to.share());

        pump.setOn(true);
        pump.set(500);
        int steps = 0;
        while (pump.status() != InlineStatus.TARGET_REACHED && steps < 400) {
            pump.step();
            steps++;
        }
        h.assertValueEqual(pump.status(), InlineStatus.TARGET_REACHED, "the pump's status after " + steps + " steps");
        a.close(out.pressure(), 500, "the second tank's pressure");
        h.assertTrue(in.pressure() < full - 400, "the first tank fell only to " + in.pressure() + " kPa");
        a.close(in.totalMoles() + out.totalMoles(), moles, "the moles");
        double after = in.thermalEnergy() + out.thermalEnergy();
        h.assertTrue(Math.abs(after - energy) <= 1e-9 * energy, "the thermal energy: " + energy + " became " + after);
        a.close(to.share().totalMoles(), out.totalMoles() * part, "what the filled tank holds");
        a.close(from.share().totalMoles(), in.totalMoles() * part, "what the emptied tank holds");
        a.close(to.share().moles(O2) / to.share().moles(N2), 50.0 / molesFor(2_000, run, T), "the mix that came over");
        a.clear(0, 0, 8, 4);
        h.succeed();
    }

    // ---- Its pressure limit: a canister's ---------------------------------------------------------

    /**
     * Just below a canister's rating a tank holds; just above it, it ruptures: both halves go, its gas
     * goes into its cell, and it drops nothing and makes no explosion (CONT-101).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_101_a_large_tank_ruptures_just_above_90_atm_and_not_below(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 4852);
        GasDeviceTests.room(a);
        double room = a.zone(1, 1, 1).mixture().totalMoles();
        LargeTankBlockEntity tank = stand(a, 2, 1, 2, Direction.NORTH);
        a.tick();
        double below = molesFor(9_119, PipeVolumes.LARGE_TANK, T);
        tank.network().gas().add(new GasParcel(Map.of(N2, below), T));
        a.step();
        h.assertTrue(whole(a, 2, 1, 2), "a tank at 9,119 kPa ruptured");
        a.close(a.zone(1, 1, 1).mixture().totalMoles(), room, "the room's gas with the tank whole");

        double raised = molesFor(9_121, PipeVolumes.LARGE_TANK, T);
        tank.network().gas().add(new GasParcel(Map.of(N2, raised - below), T));
        DropLog.watch();
        a.step();
        h.assertTrue(gone(a, 2, 1, 2), "a tank at 9,121 kPa held: " + a.get(2, 1, 2) + ", " + a.get(2, 2, 2));
        h.assertTrue(DropLog.take().isEmpty(), "a ruptured tank dropped something");
        a.tick();
        a.close(a.zone(1, 1, 1).mixture().totalMoles(), room + raised, "the room's gas after the rupture");
        h.assertTrue(Blasts.take(a.point(2, 1, 2)).isEmpty(), "a rupturing tank exploded");
        h.succeed();
    }

    /**
     * A tank in a network fails at the network's pressure. Its share goes into its cell, and the rest
     * stays in the pipes, which stand: this is below the 100 atm at which a pipe would burst first.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_101_a_large_tank_in_a_network_ruptures_with_its_share(GameTestHelper h) {
        Area a = new Area(h, 4853);
        GasDeviceTests.room(a);
        double room = a.zone(1, 1, 1).mixture().totalMoles();
        LargeTankBlockEntity tank = stand(a, 2, 1, 1, Direction.NORTH); // its back to the south
        a.place(2, 1, 2, GasDeviceTests.pipe());
        PipeTests.cap(a, 2, 1, 3, Direction.NORTH);
        a.tick();
        PipeNetwork net = tank.network();
        a.close(net.volume(), PipeVolumes.LARGE_TANK + 2 * PipeVolumes.PIPE, "the network's volume");
        double total = molesFor(9_200, net.volume(), T);
        double share = total * PipeVolumes.LARGE_TANK / net.volume();
        net.gas().add(new GasParcel(Map.of(N2, total), T));
        a.step();
        h.assertTrue(gone(a, 2, 1, 1), "the tank held above its rating");
        h.assertTrue(a.get(2, 1, 2).is(AtmosBlocks.PIPE.get()), "the pipe went with the tank");
        a.tick();
        a.close(a.zone(1, 1, 1).mixture().totalMoles(), room + share, "the room's gas: the tank's share");
        a.close(network(a, 2, 1, 2).gas().totalMoles(), total - share, "what the pipes kept");
        h.succeed();
    }

    /**
     * Raised past 135 atm in one tick, a tank goes straight to the explosion, with a canister's power
     * for its pressure, and its gas goes into its cell (CONT-102).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_102_a_large_tank_raised_past_135_atm_explodes_with_a_canisters_power(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 4854);
        GasDeviceTests.room(a);
        double room = a.zone(1, 1, 1).mixture().totalMoles();
        LargeTankBlockEntity tank = stand(a, 2, 1, 2, Direction.NORTH);
        a.tick();
        double start = molesFor(5_000, PipeVolumes.LARGE_TANK, T);
        tank.network().gas().add(new GasParcel(Map.of(N2, start), T));
        a.step();
        h.assertTrue(whole(a, 2, 1, 2), "a tank at 5,000 kPa failed");

        double raised = molesFor(ContainerFailure.CANISTER_EXPLODE_KPA + 25 * ATM, PipeVolumes.LARGE_TANK, T);
        tank.network().gas().add(new GasParcel(Map.of(N2, raised - start), T));
        a.step();
        h.assertTrue(gone(a, 2, 1, 2), "the tank is still there");
        var blasts = Blasts.take(a.point(2, 1, 2));
        h.assertValueEqual(blasts.size(), 1, "explosions");
        h.assertTrue(
                Math.abs(blasts.get(0).radius() - 2.5) < 1e-4,
                "the explosion's power: " + blasts.get(0).radius());
        h.assertTrue(
                blasts.get(0).center().distanceTo(a.point(2, 1, 2)) < 1e-6,
                "the explosion is at " + blasts.get(0).center() + ", not at its lower half");
        a.tick();
        a.close(a.zone(1, 1, 1).mixture().totalMoles(), room + raised, "the room's gas after the explosion");
        h.succeed();
    }

    /** A blast from above reaches only the upper half, which the lower one is behind: the tank ruptures. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_101_a_blast_reaching_a_large_tanks_upper_half_ruptures_it(GameTestHelper h) {
        caughtInABlast(h, new Area(h, 4855), true);
    }

    /** A blast beside its lower half ruptures it too. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_101_a_blast_reaching_a_large_tanks_lower_half_ruptures_it(GameTestHelper h) {
        caughtInABlast(h, new Area(h, 4856), false);
    }

    /** A blast that keeps blocks, as a container's does under Normal: the tank goes whole, its gas into its cell. */
    private static void caughtInABlast(GameTestHelper h, Area a, boolean above) {
        GasDeviceTests.room(a);
        LargeTankBlockEntity tank = stand(a, 2, 1, 2, Direction.NORTH);
        a.tick();
        tank.network().gas().add(new GasParcel(Map.of(CO2, 800.0), T));
        Vec3 at = above ? a.point(2, 3, 2) : a.point(1, 1, 2);
        DropLog.watch();
        a.level.explode(null, at.x, at.y, at.z, 4f, Level.ExplosionInteraction.NONE);
        h.assertTrue(gone(a, 2, 1, 2), "the blast left " + a.get(2, 1, 2) + " and " + a.get(2, 2, 2));
        h.assertTrue(DropLog.take().isEmpty(), "a tank caught in a blast dropped something");
        a.tick();
        a.close(a.zone(1, 1, 1).mixture().moles(CO2), 800.0, "the room's CO2 after the blast");
        h.succeed();
    }

    // ---- Breaking it ------------------------------------------------------------------------------

    /**
     * Mined with a pickaxe by either half, a tank goes whole and drops one item, which carries no gas;
     * by hand, or in creative, it goes whole and drops nothing. Every time its gas goes into its room,
     * as a pipe's does (CONT-009).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_009_a_broken_large_tank_drops_empty_and_its_gas_goes_into_its_room(GameTestHelper h) {
        Area a = new Area(h, 4857);
        GasDeviceTests.room(a);
        ServerPlayer p = player(a, 1, 1, 1);
        double held = 0;
        for (boolean top : new boolean[] {false, true}) {
            String half = top ? "upper" : "lower";
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            List<ItemStack> mined = broken(h, a, p, top, "mined by its " + half + " half");
            h.assertTrue(
                    mined.size() == 1 && plainTank(mined.get(0)),
                    "mined by its " + half + " half, it dropped " + mined);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            List<ItemStack> byHand = broken(h, a, p, top, "broken by hand by its " + half + " half");
            h.assertTrue(byHand.isEmpty(), "broken by hand by its " + half + " half, it dropped " + byHand);
            held += 80;
            a.close(a.zone(1, 1, 1).mixture().moles(CO2), held, "the room's CO2 after its " + half + " half");
        }
        p.setGameMode(GameType.CREATIVE);
        for (boolean top : new boolean[] {false, true}) {
            String what = "broken in creative by its " + (top ? "upper" : "lower") + " half";
            List<ItemStack> dropped = broken(h, a, p, top, what);
            h.assertTrue(dropped.isEmpty(), what + ", it dropped " + dropped);
            held += 40;
            a.close(a.zone(1, 1, 1).mixture().moles(CO2), held, "the room's CO2, " + what);
        }
        h.succeed();
    }

    /** Stands a tank holding 40 mol of CO2 in the room, has the player break one half, and returns what dropped. */
    private static List<ItemStack> broken(GameTestHelper h, Area a, ServerPlayer p, boolean top, String what) {
        LargeTankBlockEntity tank = stand(a, 2, 1, 2, Direction.NORTH);
        a.tick();
        tank.network().gas().add(new GasParcel(Map.of(CO2, 40.0), T));
        DropLog.watch();
        h.assertTrue(p.gameMode.destroyBlock(a.abs(2, top ? 2 : 1, 2)), what + ": it didn't break");
        List<ItemStack> dropped = DropLog.take();
        h.assertTrue(gone(a, 2, 1, 2), what + ", it left " + a.get(2, 1, 2) + " and " + a.get(2, 2, 2));
        a.tick();
        return dropped;
    }

    // ---- Its two halves ---------------------------------------------------------------------------

    /**
     * Placed from its item, a tank stands whole with its front toward the player and its pipe face
     * away, whichever way they look, and takes the item. With no room above, it isn't placed.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_is_placed_whole_facing_the_player_and_needs_the_block_above(GameTestHelper h) {
        Area a = new Area(h, 4858);
        a.clear(0, 0, 12, 6);
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 6; z++) {
                a.set(x, 0, z, STONE);
            }
        }
        ServerPlayer p = player(a, 1, 1, 1);
        int x = 2;
        for (Direction looks : Direction.Plane.HORIZONTAL) {
            p.moveTo(p.getX(), p.getY(), p.getZ(), looks.toYRot(), 0);
            ItemStack item = new ItemStack(AtmosItems.LARGE_GAS_TANK.get());
            h.assertTrue(placeOnFloor(a, p, item, x, 3).consumesAction(), "looking " + looks + ", it wasn't placed");
            h.assertTrue(item.isEmpty(), "looking " + looks + ", placing it didn't take the item");
            h.assertValueEqual(a.get(x, 1, 3), lower(looks.getOpposite()), "looking " + looks + ", its lower half");
            h.assertValueEqual(a.get(x, 2, 3), upper(looks.getOpposite()), "looking " + looks + ", its upper half");
            h.assertTrue(
                    a.level.getBlockEntity(a.abs(x, 1, 3)) instanceof LargeTankBlockEntity,
                    "looking " + looks + ", its lower half has no tank's block entity");
            h.assertTrue(
                    AtmosBlocks.LARGE_GAS_TANK.get().pipeConnects(a.get(x, 1, 3), looks, GasDeviceTests.pipe()),
                    "looking " + looks + ", its pipe face isn't away from the player");
            x += 2;
        }

        // Under a block, there is no room for it.
        a.set(x, 2, 3, STONE);
        ItemStack item = new ItemStack(AtmosItems.LARGE_GAS_TANK.get());
        h.assertFalse(placeOnFloor(a, p, item, x, 3).consumesAction(), "it was placed under a block");
        h.assertTrue(a.get(x, 1, 3).isAir(), "under a block, it left " + a.get(x, 1, 3));
        h.assertValueEqual(item.getCount(), 1, "the items left after it wasn't placed");
        a.clear(0, 0, 12, 6);
        h.succeed();
    }

    /** Uses a stack on the top of the floor at (x, 0, z), as a right-click there does. */
    private static InteractionResult placeOnFloor(Area a, ServerPlayer p, ItemStack stack, int x, int z) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos on = a.abs(x, 0, z);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(on).add(0, 0.5, 0), Direction.UP, on, false);
        return p.gameMode.useItemOn(p, a.level, stack, InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Half a tank never stays: with either half taken away or replaced, as a command does it, the
     * other goes too. A piston moves neither half, and a structure turned or mirrored keeps the two
     * halves facing one way.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_never_leaves_half_a_tank(GameTestHelper h) {
        Area a = new Area(h, 4859);
        a.clear(0, 0, 4, 4);

        stand(a, 2, 1, 2, Direction.EAST);
        DropLog.watch();
        a.set(2, 2, 2, AIR);
        h.assertTrue(gone(a, 2, 1, 2), "with its upper half taken away, it left " + a.get(2, 1, 2));
        List<ItemStack> dropped = DropLog.take();
        h.assertTrue(dropped.size() == 1 && plainTank(dropped.get(0)), "its lower half dropped " + dropped);

        stand(a, 2, 1, 2, Direction.EAST);
        DropLog.watch();
        a.set(2, 1, 2, AIR);
        h.assertTrue(gone(a, 2, 1, 2), "with its lower half taken away, it left " + a.get(2, 2, 2));
        h.assertTrue(DropLog.take().isEmpty(), "its upper half dropped something");

        stand(a, 2, 1, 2, Direction.EAST);
        DropLog.watch();
        a.set(2, 2, 2, STONE);
        h.assertTrue(a.get(2, 1, 2).isAir(), "with its upper half replaced, it left " + a.get(2, 1, 2));
        DropLog.take();
        a.set(2, 2, 2, AIR);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (BlockState half : List.of(lower(facing), upper(facing))) {
                h.assertValueEqual(half.getPistonPushReaction(), PushReaction.BLOCK, "a piston on " + half);
                for (Rotation turn : Rotation.values()) {
                    h.assertValueEqual(
                            half.rotate(turn),
                            half.setValue(LargeTankBlock.FACING, turn.rotate(facing)),
                            half + " turned " + turn);
                }
                for (Mirror mirror : Mirror.values()) {
                    h.assertValueEqual(
                            half.mirror(mirror),
                            half.setValue(LargeTankBlock.FACING, mirror.mirror(facing)),
                            half + " mirrored " + mirror);
                }
            }
        }
        h.succeed();
    }

    // ---- What it saves ----------------------------------------------------------------------------

    /**
     * A tank saves its share of its network's gas by volume (CONT-003), and reads it back with its
     * temperature, through bytes as a chunk's save goes. Each half's state reads back as it was.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_saves_and_loads_its_share(GameTestHelper h) throws IOException {
        Area a = new Area(h, 4860);
        a.clear(0, 0, 4, 6);
        LargeTankBlockEntity tank = stand(a, 2, 1, 1, Direction.NORTH); // its back to the south
        a.place(2, 1, 2, GasDeviceTests.pipe());
        PipeTests.cap(a, 2, 1, 3, Direction.NORTH);
        PipeNetwork net = tank.network();
        double part = PipeVolumes.LARGE_TANK / net.volume();
        a.close(net.volume(), PipeVolumes.LARGE_TANK + 2 * PipeVolumes.PIPE, "the network's volume");
        net.gas().add(new GasParcel(Map.of(N2, 4_056.0, CO2, 507.0), 310.0));

        HolderLookup.Provider registries = a.level.registryAccess();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tank.saveWithFullMetadata(registries), bytes);
        CompoundTag read =
                NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
        BlockEntity loaded = BlockEntity.loadStatic(a.abs(2, 1, 1), a.get(2, 1, 1), read, registries);
        h.assertTrue(loaded instanceof LargeTankBlockEntity, "it read back as " + loaded);
        LargeTankBlockEntity again = (LargeTankBlockEntity) loaded;
        a.close(again.share().moles(N2), 4_056.0 * part, "the N2 it read back");
        a.close(again.share().moles(CO2), 507.0 * part, "the CO2 it read back");
        a.close(again.share().temperature(), 310.0, "the temperature it read back");
        a.close(again.nodeVolume(), PipeVolumes.LARGE_TANK, "the volume it read back with");

        var blocks = a.level.holderLookup(Registries.BLOCK);
        for (BlockState half : List.of(a.get(2, 1, 1), a.get(2, 2, 1))) {
            h.assertValueEqual(
                    NbtUtils.readBlockState(blocks, NbtUtils.writeBlockState(half)), half, "the state read back");
        }
        a.clear(0, 0, 4, 6);
        h.succeed();
    }

    // ---- The wrench -------------------------------------------------------------------------------

    /**
     * A wrench turns a tank by either half: both halves turn, the pipe behind it lets go, the pipe at
     * its new back joins, and the tank takes its share with it. Sneaking, it takes the tank down as
     * one item, by either half (CONT-035).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_035_a_wrench_turns_a_large_tank_and_takes_it_down_by_either_half(GameTestHelper h) {
        Area a = new Area(h, 4861);
        a.clear(0, 0, 6, 6);
        LargeTankBlockEntity tank = stand(a, 3, 1, 3, Direction.NORTH); // its back to the south
        a.place(3, 1, 4, GasDeviceTests.pipe());
        a.place(2, 1, 3, GasDeviceTests.pipe()); // to its west, where its back comes after a turn
        PipeNetwork net = tank.network();
        h.assertTrue(net == network(a, 3, 1, 4), "the tank isn't on the pipe behind it");
        h.assertTrue(net != network(a, 2, 1, 3), "the tank is on the pipe beside it");
        net.gas().add(new GasParcel(Map.of(CO2, 507.0), T));
        double share = 507.0 * PipeVolumes.LARGE_TANK / net.volume();
        Player player = wrenching(h);

        // Clicked on a side of its upper half, it turns a quarter clockwise as seen from above: to face east.
        h.assertTrue(wrench(a, player, 3, 2, 3, Direction.EAST, false).consumesAction(), "the tank didn't turn");
        h.assertValueEqual(a.get(3, 1, 3), lower(Direction.EAST), "its lower half after the turn");
        h.assertValueEqual(a.get(3, 2, 3), upper(Direction.EAST), "its upper half after the turn");
        h.assertFalse(GasDeviceTests.joined(a.get(3, 1, 4), Direction.NORTH), "the pipe it turned from still joins it");
        h.assertTrue(GasDeviceTests.joined(a.get(2, 1, 3), Direction.EAST), "the pipe at its new back doesn't join it");
        h.assertTrue(tank.network() == network(a, 2, 1, 3), "the tank isn't on the pipe at its new back");
        a.close(tank.network().gas().moles(CO2), share, "the tank took its share with it");
        a.close(network(a, 3, 1, 4).gas().moles(CO2), 507.0 - share, "what the pipe it left kept");

        // Clicked on its lower half, the same: to face south, its back to no pipe.
        h.assertTrue(wrench(a, player, 3, 1, 3, Direction.NORTH, false).consumesAction(), "the tank didn't turn again");
        h.assertValueEqual(a.get(3, 1, 3), lower(Direction.SOUTH), "its lower half after the second turn");
        h.assertValueEqual(a.get(3, 2, 3), upper(Direction.SOUTH), "its upper half after the second turn");
        a.close(tank.network().volume(), PipeVolumes.LARGE_TANK, "its network, off both pipes");
        a.close(
                tank.network().gas().moles(CO2),
                share * PipeVolumes.LARGE_TANK / (PipeVolumes.LARGE_TANK + PipeVolumes.PIPE),
                "the tank's share after the second turn");

        // Sneaking, by its upper half: the tank comes down as one item.
        DropLog.watch();
        h.assertTrue(wrench(a, player, 3, 2, 3, Direction.UP, true).consumesAction(), "the tank's removal");
        h.assertTrue(gone(a, 3, 1, 3), "the wrench left " + a.get(3, 1, 3) + " and " + a.get(3, 2, 3));
        List<ItemStack> dropped = DropLog.take();
        h.assertTrue(dropped.size() == 1 && plainTank(dropped.get(0)), "taken down, it dropped " + dropped);

        // And by its lower half.
        stand(a, 3, 1, 3, Direction.NORTH);
        DropLog.watch();
        h.assertTrue(wrench(a, player, 3, 1, 3, Direction.WEST, true).consumesAction(), "the second tank's removal");
        h.assertTrue(gone(a, 3, 1, 3), "the wrench left " + a.get(3, 1, 3) + " and " + a.get(3, 2, 3));
        dropped = DropLog.take();
        h.assertTrue(dropped.size() == 1 && plainTank(dropped.get(0)), "taken down again, it dropped " + dropped);

        a.clear(0, 0, 6, 6);
        player.discard();
        h.succeed();
    }

    /**
     * A tank holding 3 atm over its room's pressure doesn't come off by either half, and the player
     * is told it's pressurized; under 2 atm over, it does, and its gas goes into the room (CONT-091).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_091_a_large_tank_3_atm_over_ambient_stays_and_under_2_atm_comes_off(GameTestHelper h) {
        Area a = new Area(h, 4862);
        GasDeviceTests.room(a);
        LargeTankBlockEntity tank = stand(a, 2, 1, 2, Direction.NORTH);
        a.tick();
        BlockPos at = a.abs(2, 1, 2);
        double ambient = a.zone(1, 1, 1).mixture().pressure();
        GasStore gas = tank.network().gas();
        gas.add(new GasParcel(Map.of(CO2, molesFor(ambient + 3 * ATM, PipeVolumes.LARGE_TANK, T)), T));
        a.close(WrenchItem.overAmbient(a.level, at), 3 * ATM, "the tank's pressure over the room's");
        double held = gas.totalMoles();
        Player player = wrenching(h);

        for (int y = 1; y <= 2; y++) {
            wrench(a, player, 2, y, 2, Direction.EAST, true);
            h.assertTrue(whole(a, 2, 1, 2), "the tank came off at 3 atm over, clicked at y = " + y);
        }
        Optional<Component> refusal = WrenchItem.remove(a.level, at, player, player.getMainHandItem());
        h.assertTrue(whole(a, 2, 1, 2), "the call removed the tank at 3 atm over");
        h.assertTrue(
                refusal.isPresent()
                        && refusal.get().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("item.zas_atmos.wrench.pressurized"),
                "the refusal: " + refusal);
        a.close(gas.totalMoles(), held, "what the tank holds after the refusals");

        gas.takeFraction(1 - (ambient + 1.99 * ATM) / gas.pressure());
        double left = gas.totalMoles();
        DropLog.watch();
        wrench(a, player, 2, 2, 2, Direction.EAST, true);
        h.assertTrue(gone(a, 2, 1, 2), "the tank didn't come off under 2 atm over");
        List<ItemStack> dropped = DropLog.take();
        h.assertTrue(dropped.size() == 1 && plainTank(dropped.get(0)), "taken down, it dropped " + dropped);
        a.tick();
        a.close(a.zone(1, 1, 1).mixture().moles(CO2), left, "the room's CO2 after it came off");
        player.discard();
        h.succeed();
    }

    // ---- The room round it ------------------------------------------------------------------------

    /**
     * A tank seals nothing, as a canister doesn't: one that fills a corridor from wall to wall and
     * floor to ceiling leaves it one room, the tank's own two cells among its ten.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_seals_no_face_so_a_corridor_it_fills_stays_one_room(GameTestHelper h) {
        Area a = new Area(h, 4863);
        a.box(0, 0, 6, 2);
        for (int x = 1; x <= 5; x++) {
            a.set(x, 3, 1, STONE); // the corridor is 5 long, 2 high and 1 wide
        }
        stand(a, 3, 1, 1, Direction.NORTH);
        a.fill(1, 1, 1, Map.of(N2, 10.0));
        var zone = a.zone(1, 1, 1);
        h.assertValueEqual(zone.cellCount(), 10, "the cells of the corridor's room");
        for (int[] cell : new int[][] {{5, 1, 1}, {5, 2, 1}, {3, 1, 1}, {3, 2, 1}}) {
            h.assertValueEqual(
                    a.zone(cell[0], cell[1], cell[2]).id(), zone.id(), "the room at " + cell[0] + ", " + cell[1]);
        }
        h.succeed();
    }

    // ---- Getting one ------------------------------------------------------------------------------

    /**
     * Six plasteel sheets round a pipe, a space and a machine frame craft one tank, and the creative
     * tab lists it once, empty.
     */
    @GameTest(template = "empty")
    public static void large_tank_is_crafted_from_plasteel_a_pipe_and_a_machine_frame(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ItemStack sheet = new ItemStack(AtmosItems.PLASTEEL_SHEET.get());
        CraftingInput input = CraftingInput.of(
                3,
                3,
                List.of(
                        sheet,
                        new ItemStack(AtmosItems.PIPE.get()),
                        sheet,
                        sheet,
                        ItemStack.EMPTY,
                        sheet,
                        sheet,
                        new ItemStack(AtmosItems.MACHINE_FRAME.get()),
                        sheet));
        RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElse(null);
        h.assertTrue(recipe != null, "nothing crafts from the tank's pattern");
        ItemStack out = recipe.value().assemble(input, level.registryAccess());
        h.assertTrue(plainTank(out), "the pattern makes " + out);

        CreativeModeTab tab = AtmosCreativeTab.TAB.get();
        tab.buildContents(
                new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess()));
        long listed =
                tab.getDisplayItems().stream().filter(LargeTankTests::plainTank).count();
        h.assertValueEqual(listed, 1L, "tanks in the creative tab");
        h.succeed();
    }
}
