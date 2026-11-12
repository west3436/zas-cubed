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
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.port.ConnectorPortBlock;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosCreativeTab;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tank.GasVessels;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import io.github.west3436.zas.atmos.tank.LargeTankBlock.Part;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The large gas tank in the world: its volume, where pipes and a connector port join it, filling and
 * emptying it through a pipe with no gas made or lost, its pressure limit, what breaking it does, its
 * two halves side by side, dyeing it, what it saves, the wrench on it, and that it seals nothing. Each
 * test builds in its own {@link Area} and drives device steps and atmos ticks itself, within one game
 * tick, in a batch of its own.
 *
 * <p>It follows the rules the other pipe parts have: a node's volume and share (CONT-003, CONT-007),
 * its gas into its cell when it goes (CONT-009), the wrench (CONT-035, CONT-091), a canister's rating
 * (CONT-101, CONT-102) and a pipe's dye (CONT-114). The analyzer on it is in {@link AnalyzerTests},
 * its gas handler in {@link PipeGasHandlerTests}.
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

    /** A tank's inlet half, its end half the way it faces. */
    static BlockState inlet(Direction facing) {
        return AtmosBlocks.LARGE_GAS_TANK.get().defaultBlockState().setValue(LargeTankBlock.FACING, facing);
    }

    static BlockState end(Direction facing) {
        return inlet(facing).setValue(LargeTankBlock.PART, Part.END);
    }

    /**
     * Lays a whole tank with its inlet half at a place and its end half next to it the way it faces.
     * The end half goes in first: the inlet one would go at once with nothing beside it.
     */
    static LargeTankBlockEntity lay(Area a, int x, int y, int z, Direction facing) {
        a.set(x + facing.getStepX(), y, z + facing.getStepZ(), end(facing));
        a.place(x, y, z, inlet(facing));
        return a.entity(x, y, z);
    }

    /** Whether a whole tank lies with its inlet half at a place, in one colour. */
    private static boolean whole(Area a, int x, int y, int z) {
        BlockState in = a.get(x, y, z);
        if (!in.is(AtmosBlocks.LARGE_GAS_TANK.get()) || in.getValue(LargeTankBlock.PART) != Part.INLET) {
            return false;
        }
        Direction facing = in.getValue(LargeTankBlock.FACING);
        return a.get(x + facing.getStepX(), y, z + facing.getStepZ()) == in.setValue(LargeTankBlock.PART, Part.END);
    }

    /** Whether nothing of a tank lying from a place one way is left. */
    private static boolean gone(Area a, int x, int y, int z, Direction facing) {
        return a.get(x, y, z).isAir()
                && a.get(x + facing.getStepX(), y, z + facing.getStepZ()).isAir();
    }

    /** The stacks dropped since {@link WrenchTests#watchDrops}, taken out of the level. */
    private static List<ItemStack> drops() {
        return WrenchTests.takeDrops().stream().map(ItemEntity::getItem).toList();
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

    /** Uses a stack on a face of a block, as a right-click there does. */
    private static InteractionResult use(Area a, ServerPlayer p, ItemStack stack, int x, int y, int z, Direction face) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos on = a.abs(x, y, z);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(on).relative(face, 0.5), face, on, false);
        return p.gameMode.useItemOn(p, a.level, stack, InteractionHand.MAIN_HAND, hit);
    }

    private static boolean plainTank(ItemStack stack) {
        return ItemStack.matches(stack, new ItemStack(AtmosItems.LARGE_GAS_TANK.get()));
    }

    private static boolean tankOf(ItemStack stack, PipeColor color) {
        return ItemStack.matches(stack, PipeItem.of(AtmosItems.LARGE_GAS_TANK.get(), color, 1));
    }

    // ---- Its volume, and where pipes join it ------------------------------------------------------

    /**
     * A tank holds ten canisters' worth. Whichever way it lies, a pipe joins it under its inlet half
     * and nowhere else: not at its sides, its ends or its top, and not under its end half. Each pipe
     * is tried alone, so only the tank can join it. It offers its gas to another mod's pipe on that one
     * face, and its end half has no block entity.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_holds_ten_canisters_and_takes_a_pipe_only_under_its_inlet_half(GameTestHelper h) {
        Area a = new Area(h, 4850);
        a.clear(0, 0, 30, 6);
        h.assertValueEqual(PipeVolumes.LARGE_TANK, 10 * PipeVolumes.CANISTER, "its volume against a canister's");
        LargeTankBlock block = AtmosBlocks.LARGE_GAS_TANK.get();
        BlockState pipe = GasDeviceTests.pipe();
        int x = 3;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            LargeTankBlockEntity tank = lay(a, x, 2, 3, facing);
            int ex = x + facing.getStepX();
            int ez = 3 + facing.getStepZ();
            h.assertTrue(whole(a, x, 2, 3), facing + ": the tank doesn't lie whole");
            a.close(tank.nodeVolume(), 10_000, facing + ": its volume");
            a.close(tank.network().volume(), PipeVolumes.LARGE_TANK, facing + ": its network, alone");
            h.assertTrue(
                    a.level.getBlockEntity(a.abs(ex, 2, ez)) == null, facing + ": the end half has a block entity");
            for (Direction face : Direction.values()) {
                h.assertValueEqual(
                        block.pipeConnects(inlet(facing), face, pipe),
                        face == Direction.DOWN,
                        facing + ": a pipe on its inlet half's " + face);
                h.assertFalse(
                        block.pipeConnects(end(facing), face, pipe), facing + ": a pipe joins its end half on " + face);
            }
            h.assertValueEqual(
                    offered(a, x, 2, 3), EnumSet.of(Direction.DOWN), facing + ": the faces it offers gas on");
            h.assertValueEqual(
                    offered(a, ex, 2, ez), EnumSet.noneOf(Direction.class), facing + ": the faces of its end half");

            // One pipe at a time beside each face of each half, but where the halves meet.
            for (boolean atInlet : new boolean[] {true, false}) {
                int hx = atInlet ? x : ex;
                int hz = atInlet ? 3 : ez;
                for (Direction face : Direction.values()) {
                    if (face == (atInlet ? facing : facing.getOpposite())) {
                        continue;
                    }
                    int px = hx + face.getStepX();
                    int py = 2 + face.getStepY();
                    int pz = hz + face.getStepZ();
                    String where = facing + ": a pipe by the " + (atInlet ? "inlet" : "end") + " half's " + face;
                    a.place(px, py, pz, pipe);
                    boolean joins = atInlet && face == Direction.DOWN;
                    h.assertValueEqual(GasDeviceTests.joined(a.get(px, py, pz), face.getOpposite()), joins, where);
                    a.close(
                            tank.network().volume(),
                            PipeVolumes.LARGE_TANK + (joins ? PipeVolumes.PIPE : 0),
                            where + ": the tank's network");
                    a.set(px, py, pz, AIR);
                }
            }
            x += 6;
        }
        a.clear(0, 0, 30, 6);
        h.succeed();
    }

    /**
     * A tank whose inlet half lies on a connector port joins the port's network, as a canister on it
     * does: the port shows its collar, and the tank, the port and the pipe under the port share one
     * gas. Placed from its item on the port's top, it joins it too. A port under its end half stays
     * bare and alone.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_lies_on_a_connector_port_by_its_inlet_half(GameTestHelper h) {
        Area a = new Area(h, 4865);
        a.clear(0, 0, 8, 6);
        a.place(2, 1, 3, GasDeviceTests.pipe());
        a.place(2, 2, 3, GasDeviceTests.port());
        LargeTankBlockEntity tank = lay(a, 2, 3, 3, Direction.NORTH);
        h.assertTrue(a.get(2, 2, 3).getValue(ConnectorPortBlock.CONNECTED), "the port doesn't show the tank");
        PipeNetwork net = tank.network();
        a.close(net.volume(), PipeVolumes.LARGE_TANK + PipeVolumes.PORT + PipeVolumes.PIPE, "the network's volume");
        h.assertTrue(network(a, 2, 1, 3) == net, "the pipe under the port isn't on the tank's network");
        net.gas().add(new GasParcel(Map.of(N2, 1_000.0), T));
        a.close(tank.share().moles(N2), 1_000.0 * PipeVolumes.LARGE_TANK / net.volume(), "the tank's share");

        // Placed by a player on the port's top.
        a.set(2, 3, 3, AIR);
        h.assertTrue(gone(a, 2, 3, 3, Direction.NORTH), "taking the inlet half away left its end half");
        h.assertFalse(a.get(2, 2, 3).getValue(ConnectorPortBlock.CONNECTED), "the port still shows a tank");
        ServerPlayer p = player(a, 2, 3, 6);
        p.moveTo(p.getX(), p.getY(), p.getZ(), Direction.NORTH.toYRot(), 0);
        ItemStack item = new ItemStack(AtmosItems.LARGE_GAS_TANK.get());
        h.assertTrue(use(a, p, item, 2, 2, 3, Direction.UP).consumesAction(), "it wasn't placed on the port");
        h.assertTrue(whole(a, 2, 3, 3), "placed on the port, it isn't whole: " + a.get(2, 3, 3));
        h.assertTrue(a.get(2, 2, 3).getValue(ConnectorPortBlock.CONNECTED), "placed on it, the port doesn't show it");
        LargeTankBlockEntity placed = a.entity(2, 3, 3);
        a.close(
                placed.network().volume(),
                PipeVolumes.LARGE_TANK + PipeVolumes.PORT + PipeVolumes.PIPE,
                "placed on the port, its network's volume");

        // A port under the end half.
        a.place(5, 2, 2, GasDeviceTests.port());
        LargeTankBlockEntity other = lay(a, 5, 3, 3, Direction.NORTH); // its end half above the port
        h.assertFalse(a.get(5, 2, 2).getValue(ConnectorPortBlock.CONNECTED), "a port under the end half shows it");
        a.close(other.network().volume(), PipeVolumes.LARGE_TANK, "the tank over a port by its end half");
        a.clear(0, 0, 8, 6);
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
        LargeTankBlockEntity from = lay(a, 1, 2, 2, Direction.NORTH);
        a.place(1, 1, 2, GasDeviceTests.pipe());
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.place(
                3,
                1,
                2,
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        a.place(4, 1, 2, GasDeviceTests.pipe());
        a.place(5, 1, 2, GasDeviceTests.pipe());
        LargeTankBlockEntity to = lay(a, 5, 2, 2, Direction.NORTH);
        PressurePumpBlockEntity pump = a.entity(3, 1, 2);
        pump.power().setTestPowered(true);

        double run = PipeVolumes.LARGE_TANK + 2 * PipeVolumes.PIPE + PipeVolumes.PORT;
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
        LargeTankBlockEntity tank = lay(a, 2, 1, 2, Direction.NORTH);
        a.tick();
        double below = molesFor(9_119, PipeVolumes.LARGE_TANK, T);
        tank.network().gas().add(new GasParcel(Map.of(N2, below), T));
        a.step();
        h.assertTrue(whole(a, 2, 1, 2), "a tank at 9,119 kPa ruptured");
        a.close(a.zone(1, 1, 1).mixture().totalMoles(), room, "the room's gas with the tank whole");

        double raised = molesFor(9_121, PipeVolumes.LARGE_TANK, T);
        tank.network().gas().add(new GasParcel(Map.of(N2, raised - below), T));
        WrenchTests.watchDrops();
        a.step();
        h.assertTrue(
                gone(a, 2, 1, 2, Direction.NORTH),
                "a tank at 9,121 kPa held: " + a.get(2, 1, 2) + ", " + a.get(2, 1, 1));
        h.assertTrue(drops().isEmpty(), "a ruptured tank dropped something");
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
        LargeTankBlockEntity tank = lay(a, 2, 2, 2, Direction.NORTH);
        a.place(2, 1, 2, GasDeviceTests.pipe());
        PipeTests.cap(a, 2, 1, 3, Direction.NORTH);
        a.tick();
        PipeNetwork net = tank.network();
        a.close(net.volume(), PipeVolumes.LARGE_TANK + 2 * PipeVolumes.PIPE, "the network's volume");
        double total = molesFor(9_200, net.volume(), T);
        double share = total * PipeVolumes.LARGE_TANK / net.volume();
        net.gas().add(new GasParcel(Map.of(N2, total), T));
        a.step();
        h.assertTrue(gone(a, 2, 2, 2, Direction.NORTH), "the tank held above its rating");
        h.assertTrue(a.get(2, 1, 2).is(AtmosBlocks.PIPE.get()), "the pipe went with the tank");
        a.tick();
        a.close(a.zone(1, 1, 1).mixture().totalMoles(), room + share, "the room's gas: the tank's share");
        a.close(network(a, 2, 1, 2).gas().totalMoles(), total - share, "what the pipes kept");
        h.succeed();
    }

    /**
     * Raised past 135 atm in one tick, a tank goes straight to the explosion, with a canister's power
     * for its pressure, at its inlet half, and its gas goes into its cell (CONT-102).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_102_a_large_tank_raised_past_135_atm_explodes_with_a_canisters_power(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 4854);
        GasDeviceTests.room(a);
        double room = a.zone(1, 1, 1).mixture().totalMoles();
        LargeTankBlockEntity tank = lay(a, 2, 1, 2, Direction.NORTH);
        a.tick();
        double start = molesFor(5_000, PipeVolumes.LARGE_TANK, T);
        tank.network().gas().add(new GasParcel(Map.of(N2, start), T));
        a.step();
        h.assertTrue(whole(a, 2, 1, 2), "a tank at 5,000 kPa failed");

        double raised = molesFor(ContainerFailure.CANISTER_EXPLODE_KPA + 25 * ATM, PipeVolumes.LARGE_TANK, T);
        tank.network().gas().add(new GasParcel(Map.of(N2, raised - start), T));
        a.step();
        h.assertTrue(gone(a, 2, 1, 2, Direction.NORTH), "the tank is still there");
        var blasts = Blasts.take(a.point(2, 1, 2));
        h.assertValueEqual(blasts.size(), 1, "explosions");
        h.assertTrue(
                Math.abs(blasts.get(0).radius() - 2.5) < 1e-4,
                "the explosion's power: " + blasts.get(0).radius());
        h.assertTrue(
                blasts.get(0).center().distanceTo(a.point(2, 1, 2)) < 1e-6,
                "the explosion is at " + blasts.get(0).center() + ", not at its inlet half");
        a.tick();
        a.close(a.zone(1, 1, 1).mixture().totalMoles(), room + raised, "the room's gas after the explosion");
        h.succeed();
    }

    /** A blast beside its end half ruptures it: the end half stands for the tank. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_101_a_blast_beside_a_large_tanks_end_half_ruptures_it(GameTestHelper h) {
        caughtInABlast(h, new Area(h, 4855), true);
    }

    /** A blast beside its inlet half ruptures it too. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_101_a_blast_beside_a_large_tanks_inlet_half_ruptures_it(GameTestHelper h) {
        caughtInABlast(h, new Area(h, 4856), false);
    }

    /** A blast that keeps blocks, as a container's does under Normal: the tank goes whole, its gas into its cell. */
    private static void caughtInABlast(GameTestHelper h, Area a, boolean byEnd) {
        GasDeviceTests.room(a);
        LargeTankBlockEntity tank = lay(a, 2, 1, 2, Direction.NORTH); // its end half at (2, 1, 1)
        a.tick();
        h.assertTrue(GasVessels.at(a.level, a.abs(2, 1, 1)) == tank, "the end half doesn't stand for the tank");
        tank.network().gas().add(new GasParcel(Map.of(CO2, 800.0), T));
        Vec3 at = byEnd ? a.point(3, 1, 1) : a.point(3, 1, 3);
        WrenchTests.watchDrops();
        a.level.explode(null, at.x, at.y, at.z, 4f, Level.ExplosionInteraction.NONE);
        h.assertTrue(gone(a, 2, 1, 2, Direction.NORTH), "the blast left " + a.get(2, 1, 2) + " and " + a.get(2, 1, 1));
        h.assertTrue(drops().isEmpty(), "a tank caught in a blast dropped something");
        a.tick();
        a.close(a.zone(1, 1, 1).mixture().moles(CO2), 800.0, "the room's CO2 after the blast");
        h.succeed();
    }

    // ---- Breaking it ------------------------------------------------------------------------------

    /**
     * Mined with a pickaxe by either half, a tank goes whole and drops one item, which carries no gas;
     * by hand, or in creative, it goes whole and drops nothing. Every time its gas goes into its room,
     * as a pipe's does (CONT-009): the owner's choice.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_009_a_broken_large_tank_drops_empty_and_its_gas_goes_into_its_room(GameTestHelper h) {
        Area a = new Area(h, 4857);
        GasDeviceTests.room(a);
        ServerPlayer p = player(a, 1, 1, 3);
        double held = 0;
        for (boolean byEnd : new boolean[] {false, true}) {
            String half = byEnd ? "end" : "inlet";
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            List<ItemStack> mined = broken(h, a, p, byEnd, "mined by its " + half + " half");
            h.assertTrue(
                    mined.size() == 1 && plainTank(mined.get(0)),
                    "mined by its " + half + " half, it dropped " + mined);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            List<ItemStack> byHand = broken(h, a, p, byEnd, "broken by hand by its " + half + " half");
            h.assertTrue(byHand.isEmpty(), "broken by hand by its " + half + " half, it dropped " + byHand);
            held += 80;
            a.close(a.zone(1, 1, 1).mixture().moles(CO2), held, "the room's CO2 after its " + half + " half");
        }
        p.setGameMode(GameType.CREATIVE);
        for (boolean byEnd : new boolean[] {false, true}) {
            String what = "broken in creative by its " + (byEnd ? "end" : "inlet") + " half";
            List<ItemStack> dropped = broken(h, a, p, byEnd, what);
            h.assertTrue(dropped.isEmpty(), what + ", it dropped " + dropped);
            held += 40;
            a.close(a.zone(1, 1, 1).mixture().moles(CO2), held, "the room's CO2, " + what);
        }
        h.succeed();
    }

    /** Lays a tank holding 40 mol of CO2 in the room, has the player break one half, and returns what dropped. */
    private static List<ItemStack> broken(GameTestHelper h, Area a, ServerPlayer p, boolean byEnd, String what) {
        LargeTankBlockEntity tank = lay(a, 2, 1, 2, Direction.NORTH);
        a.tick();
        tank.network().gas().add(new GasParcel(Map.of(CO2, 40.0), T));
        WrenchTests.watchDrops();
        h.assertTrue(p.gameMode.destroyBlock(a.abs(2, 1, byEnd ? 1 : 2)), what + ": it didn't break");
        List<ItemStack> dropped = drops();
        h.assertTrue(
                gone(a, 2, 1, 2, Direction.NORTH), what + ", it left " + a.get(2, 1, 2) + " and " + a.get(2, 1, 1));
        a.tick();
        return dropped;
    }

    // ---- Its two halves ---------------------------------------------------------------------------

    /**
     * Placed from its item, a tank lies whole along the player's look: its inlet half where they
     * clicked, its end half past it, whichever way they look, and takes the item. With a block past
     * it, it isn't placed.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_is_placed_whole_along_the_players_look_and_needs_the_block_past_it(GameTestHelper h) {
        Area a = new Area(h, 4858);
        a.clear(0, 0, 22, 8);
        for (int x = 0; x <= 22; x++) {
            for (int z = 0; z <= 8; z++) {
                a.set(x, 0, z, STONE);
            }
        }
        ServerPlayer p = player(a, 1, 1, 1);
        int x = 2;
        for (Direction looks : Direction.Plane.HORIZONTAL) {
            p.moveTo(p.getX(), p.getY(), p.getZ(), looks.toYRot(), 0);
            ItemStack item = new ItemStack(AtmosItems.LARGE_GAS_TANK.get());
            h.assertTrue(use(a, p, item, x, 0, 4, Direction.UP).consumesAction(), "looking " + looks + ", not placed");
            h.assertTrue(item.isEmpty(), "looking " + looks + ", placing it didn't take the item");
            h.assertValueEqual(a.get(x, 1, 4), inlet(looks), "looking " + looks + ", its inlet half");
            h.assertValueEqual(
                    a.get(x + looks.getStepX(), 1, 4 + looks.getStepZ()), end(looks), "looking " + looks + ", its end");
            h.assertTrue(
                    a.level.getBlockEntity(a.abs(x, 1, 4)) instanceof LargeTankBlockEntity,
                    "looking " + looks + ", its inlet half has no tank's block entity");
            x += 4;
        }

        // A block past it: there is no room for it.
        p.moveTo(p.getX(), p.getY(), p.getZ(), Direction.EAST.toYRot(), 0);
        a.set(x + 1, 1, 4, STONE);
        ItemStack item = new ItemStack(AtmosItems.LARGE_GAS_TANK.get());
        h.assertFalse(use(a, p, item, x, 0, 4, Direction.UP).consumesAction(), "it was placed against a block");
        h.assertTrue(a.get(x, 1, 4).isAir(), "against a block, it left " + a.get(x, 1, 4));
        h.assertValueEqual(item.getCount(), 1, "the items left after it wasn't placed");
        a.clear(0, 0, 22, 8);
        h.succeed();
    }

    /**
     * Half a tank never stays: with either half taken away or replaced, as a command does it, the
     * other goes too, and only the inlet half drops the item. Half a tank turned another way from its
     * other half goes as well. A piston moves neither half, and a structure turned or mirrored keeps the
     * two halves lying one way.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_never_leaves_half_a_tank(GameTestHelper h) {
        Area a = new Area(h, 4859);
        a.clear(0, 0, 4, 4);

        lay(a, 2, 1, 2, Direction.EAST); // its end half at (3, 1, 2)
        WrenchTests.watchDrops();
        a.set(3, 1, 2, AIR);
        h.assertTrue(gone(a, 2, 1, 2, Direction.EAST), "with its end half taken away, it left " + a.get(2, 1, 2));
        List<ItemStack> dropped = drops();
        h.assertTrue(dropped.size() == 1 && plainTank(dropped.get(0)), "its inlet half dropped " + dropped);

        lay(a, 2, 1, 2, Direction.EAST);
        WrenchTests.watchDrops();
        a.set(2, 1, 2, AIR);
        h.assertTrue(gone(a, 2, 1, 2, Direction.EAST), "with its inlet half taken away, it left " + a.get(3, 1, 2));
        h.assertTrue(drops().isEmpty(), "its end half dropped something");

        lay(a, 2, 1, 2, Direction.EAST);
        WrenchTests.watchDrops();
        a.set(3, 1, 2, STONE);
        h.assertTrue(a.get(2, 1, 2).isAir(), "with its end half replaced, it left " + a.get(2, 1, 2));
        drops();
        a.set(3, 1, 2, AIR);

        lay(a, 2, 1, 2, Direction.EAST);
        WrenchTests.watchDrops();
        a.set(3, 1, 2, end(Direction.NORTH)); // an end half lying another way
        h.assertTrue(a.get(2, 1, 2).isAir(), "beside an end half lying north, it left " + a.get(2, 1, 2));
        drops();
        a.clear(0, 0, 4, 4);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (BlockState half : List.of(inlet(facing), end(facing))) {
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

    // ---- Its colour -------------------------------------------------------------------------------

    /**
     * A tank starts grey. A dye used on either half colours both and is used up; the same dye again
     * does nothing. Mined, a dyed tank drops an item of its colour, which places a tank of that colour
     * whole; picking it gives one too. The undyed tank is drawn grey, a dyed one in its dye's colour.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_114_a_large_tank_is_dyed_as_a_pipe_is_and_its_item_keeps_the_colour(GameTestHelper h) {
        Area a = new Area(h, 4866);
        a.clear(0, 0, 8, 6);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 6; z++) {
                a.set(x, 0, z, STONE);
            }
        }
        h.assertValueEqual(LargeTankBlock.tint(PipeColor.NONE), LargeTankBlock.GREY, "an undyed tank's tint");
        h.assertValueEqual(
                LargeTankBlock.tint(PipeColor.RED),
                DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF,
                "a red tank's tint");
        lay(a, 2, 1, 2, Direction.EAST); // its end half at (3, 1, 2)
        h.assertValueEqual(a.get(2, 1, 2).getValue(PipeColor.PROPERTY), PipeColor.NONE, "a new tank's colour");
        ServerPlayer p = player(a, 2, 1, 4);

        ItemStack dye = new ItemStack(Items.RED_DYE, 2);
        h.assertTrue(use(a, p, dye, 3, 1, 2, Direction.UP).consumesAction(), "the dye on its end half did nothing");
        h.assertTrue(
                whole(a, 2, 1, 2), "dyed, it isn't whole in one colour: " + a.get(2, 1, 2) + ", " + a.get(3, 1, 2));
        h.assertValueEqual(a.get(2, 1, 2).getValue(PipeColor.PROPERTY), PipeColor.RED, "its colour after the dye");
        h.assertValueEqual(dye.getCount(), 1, "the dye left after one use");
        use(a, p, dye, 2, 1, 2, Direction.UP);
        h.assertValueEqual(dye.getCount(), 1, "the dye left after it was used on a tank its colour");

        ItemStack picked = a.get(2, 1, 2)
                .getCloneItemStack(
                        new BlockHitResult(a.point(2, 1, 2), Direction.UP, a.abs(2, 1, 2), false),
                        a.level,
                        a.abs(2, 1, 2),
                        p);
        h.assertTrue(tankOf(picked, PipeColor.RED), "picking a red tank gave " + picked);

        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        WrenchTests.watchDrops();
        h.assertTrue(p.gameMode.destroyBlock(a.abs(3, 1, 2)), "the red tank didn't break");
        List<ItemStack> dropped = drops();
        h.assertTrue(dropped.size() == 1 && tankOf(dropped.get(0), PipeColor.RED), "the red tank dropped " + dropped);
        h.assertTrue(
                dropped.get(0).getHoverName().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("item.zas_atmos.dyed"),
                "the red tank's item is named " + dropped.get(0).getHoverName());

        p.moveTo(p.getX(), p.getY(), p.getZ(), Direction.SOUTH.toYRot(), 0);
        ItemStack red = dropped.get(0).copy();
        h.assertTrue(use(a, p, red, 5, 0, 2, Direction.UP).consumesAction(), "the red item wasn't placed");
        h.assertTrue(whole(a, 5, 1, 2), "placed, the red tank isn't whole in one colour");
        h.assertValueEqual(a.get(5, 1, 2).getValue(PipeColor.PROPERTY), PipeColor.RED, "the placed tank's colour");
        h.assertValueEqual(a.get(5, 1, 2).getValue(LargeTankBlock.FACING), Direction.SOUTH, "the placed tank's way");
        a.clear(0, 0, 8, 6);
        h.succeed();
    }

    // ---- What it saves ----------------------------------------------------------------------------

    /**
     * A tank saves its share of its network's gas by volume (CONT-003), and reads it back with its
     * temperature, through bytes as a chunk's save goes. Each half's state, its colour among them,
     * reads back as it was.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_saves_and_loads_its_share_and_its_colour(GameTestHelper h) throws IOException {
        Area a = new Area(h, 4860);
        a.clear(0, 0, 4, 6);
        LargeTankBlockEntity tank = lay(a, 2, 2, 2, Direction.NORTH);
        a.set(2, 2, 2, a.get(2, 2, 2).setValue(PipeColor.PROPERTY, PipeColor.CYAN));
        h.assertTrue(whole(a, 2, 2, 2), "dyed cyan, it isn't whole in one colour");
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
        BlockEntity loaded = BlockEntity.loadStatic(a.abs(2, 2, 2), a.get(2, 2, 2), read, registries);
        h.assertTrue(loaded instanceof LargeTankBlockEntity, "it read back as " + loaded);
        LargeTankBlockEntity again = (LargeTankBlockEntity) loaded;
        a.close(again.share().moles(N2), 4_056.0 * part, "the N2 it read back");
        a.close(again.share().moles(CO2), 507.0 * part, "the CO2 it read back");
        a.close(again.share().temperature(), 310.0, "the temperature it read back");
        a.close(again.nodeVolume(), PipeVolumes.LARGE_TANK, "the volume it read back with");

        var blocks = a.level.holderLookup(Registries.BLOCK);
        for (BlockState half : List.of(a.get(2, 2, 2), a.get(2, 2, 1))) {
            h.assertValueEqual(half.getValue(PipeColor.PROPERTY), PipeColor.CYAN, "a half's colour");
            h.assertValueEqual(
                    NbtUtils.readBlockState(blocks, NbtUtils.writeBlockState(half)), half, "the state read back");
        }
        a.clear(0, 0, 4, 6);
        h.succeed();
    }

    // ---- The wrench -------------------------------------------------------------------------------

    /**
     * A wrench doesn't turn a tank, whose pipe joins it from below whichever way it lies: used on
     * either half, nothing changes. Sneaking, it takes the tank down as one item, by either half, and
     * a dyed tank's item keeps its colour (CONT-035).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_035_a_wrench_takes_a_large_tank_down_by_either_half_and_doesnt_turn_it(GameTestHelper h) {
        Area a = new Area(h, 4861);
        a.clear(0, 0, 6, 6);
        LargeTankBlockEntity tank = lay(a, 3, 2, 3, Direction.NORTH); // its end half at (3, 2, 2)
        a.place(3, 1, 3, GasDeviceTests.pipe());
        PipeNetwork net = tank.network();
        h.assertTrue(net == network(a, 3, 1, 3), "the tank isn't on the pipe under it");
        Player player = wrenching(h);

        for (int z : new int[] {3, 2}) {
            for (Direction face : Direction.values()) {
                h.assertFalse(
                        wrench(a, player, 3, 2, z, face, false).consumesAction(), "the wrench on " + face + " acted");
            }
        }
        h.assertTrue(whole(a, 3, 2, 3), "the wrench changed the tank");
        h.assertValueEqual(a.get(3, 2, 3), inlet(Direction.NORTH), "its inlet half after the wrench");

        // Sneaking, by its end half: the tank comes down as one item.
        WrenchTests.watchDrops();
        h.assertTrue(wrench(a, player, 3, 2, 2, Direction.UP, true).consumesAction(), "the tank's removal");
        h.assertTrue(gone(a, 3, 2, 3, Direction.NORTH), "the wrench left " + a.get(3, 2, 3) + " and " + a.get(3, 2, 2));
        List<ItemStack> dropped = drops();
        h.assertTrue(dropped.size() == 1 && plainTank(dropped.get(0)), "taken down, it dropped " + dropped);

        // And by its inlet half, dyed.
        lay(a, 3, 2, 3, Direction.NORTH);
        a.set(3, 2, 3, a.get(3, 2, 3).setValue(PipeColor.PROPERTY, PipeColor.LIME));
        WrenchTests.watchDrops();
        h.assertTrue(wrench(a, player, 3, 2, 3, Direction.WEST, true).consumesAction(), "the second tank's removal");
        h.assertTrue(gone(a, 3, 2, 3, Direction.NORTH), "the wrench left " + a.get(3, 2, 3) + " and " + a.get(3, 2, 2));
        dropped = drops();
        h.assertTrue(
                dropped.size() == 1 && tankOf(dropped.get(0), PipeColor.LIME),
                "taken down dyed, it dropped " + dropped);

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
        LargeTankBlockEntity tank = lay(a, 2, 1, 2, Direction.NORTH);
        a.tick();
        BlockPos at = a.abs(2, 1, 2);
        double ambient = a.zone(1, 1, 1).mixture().pressure();
        GasStore gas = tank.network().gas();
        gas.add(new GasParcel(Map.of(CO2, molesFor(ambient + 3 * ATM, PipeVolumes.LARGE_TANK, T)), T));
        a.close(WrenchItem.overAmbient(a.level, at), 3 * ATM, "the tank's pressure over the room's");
        double held = gas.totalMoles();
        Player player = wrenching(h);

        for (int z = 1; z <= 2; z++) {
            wrench(a, player, 2, 1, z, Direction.EAST, true);
            h.assertTrue(whole(a, 2, 1, 2), "the tank came off at 3 atm over, clicked at z = " + z);
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
        WrenchTests.watchDrops();
        wrench(a, player, 2, 1, 1, Direction.EAST, true);
        h.assertTrue(gone(a, 2, 1, 2, Direction.NORTH), "the tank didn't come off under 2 atm over");
        List<ItemStack> dropped = drops();
        h.assertTrue(dropped.size() == 1 && plainTank(dropped.get(0)), "taken down, it dropped " + dropped);
        a.tick();
        a.close(a.zone(1, 1, 1).mixture().moles(CO2), left, "the room's CO2 after it came off");
        player.discard();
        h.succeed();
    }

    // ---- The room round it ------------------------------------------------------------------------

    /**
     * A tank seals nothing, as a canister doesn't: one lying along a corridor 1 wide leaves it one
     * room, the tank's own two cells among its ten.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void large_tank_seals_no_face_so_a_corridor_it_fills_stays_one_room(GameTestHelper h) {
        Area a = new Area(h, 4863);
        a.box(0, 0, 6, 2);
        for (int x = 1; x <= 5; x++) {
            a.set(x, 3, 1, STONE); // the corridor is 5 long, 2 high and 1 wide
        }
        lay(a, 2, 1, 1, Direction.EAST);
        a.fill(1, 1, 1, Map.of(N2, 10.0));
        var zone = a.zone(1, 1, 1);
        h.assertValueEqual(zone.cellCount(), 10, "the cells of the corridor's room");
        for (int[] cell : new int[][] {{5, 1, 1}, {2, 1, 1}, {3, 1, 1}, {3, 2, 1}}) {
            h.assertValueEqual(
                    a.zone(cell[0], cell[1], cell[2]).id(), zone.id(), "the room at " + cell[0] + ", " + cell[1]);
        }
        h.succeed();
    }

    // ---- Getting one ------------------------------------------------------------------------------

    /**
     * Six plasteel sheets round a pipe, a space and a machine frame craft one grey tank, and the
     * creative tab lists it once, grey and empty.
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
