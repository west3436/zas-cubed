package io.github.west3436.zas.compat.mekanism.gametest;

import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.CARBON_DIOXIDE;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.HYDROGEN;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.LITHIUM;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.NUCLEAR_WASTE;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.OXYGEN;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.PLUTONIUM;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.mol;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.run;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.compat.mekanism.ChemicalGases;
import io.github.west3436.zas.compat.mekanism.ExhaustPort;
import io.github.west3436.zas.compat.mekanism.ExhaustPortBlock;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import mekanism.api.Action;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.radiation.IRadiationManager;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.network.ChemicalNetwork;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.TileEntityChemicalTank.GasMode;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.transmitter.TileEntityPressurizedTube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The exhaust port (MEK-022): a block that takes Mekanism's chemicals at its back and releases them
 * as gas in front of its grille, with none of the venting's mixins.
 *
 * <p>Each test builds a sealed stone room with a port in its wall or on its floor, offers the port
 * chemicals through Mekanism's capability, as a tube and a machine do, or through a real tube and a
 * real machine, and reads the room through the public API. Gas is conserved: the moles the room
 * gains are the mB the port took times the unit factor (MEK-002).
 *
 * <p>The tests are in the venting's batch because they run atmos ticks themselves. Each runs within
 * one game tick, so within one second of the cap's count.
 */
@GameTestHolder(MekanismFoundationTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismExhaustPortTests {
    private static final String MEK_OXYGEN = "mekanism:oxygen";
    private static final String MEK_HYDROGEN = "mekanism:hydrogen";
    private static final String TANK = "mekanism:basic_chemical_tank";

    /** The content mod's pipe and pipe cap, by id: the adapter and its tests name none of its classes. */
    private static final ResourceLocation PIPE = ResourceLocation.parse("zas_atmos:pipe");

    private static final ResourceLocation PIPE_CAP = ResourceLocation.parse("zas_atmos:pipe_cap");

    /** What the ports sent to the open air, in the order they sent it. */
    private static final List<OpenAirGasEvent> OPEN_AIR = new CopyOnWriteArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener((OpenAirGasEvent event) -> {
            if (event.source().equals(ExhaustPort.SOURCE)) {
                OPEN_AIR.add(event);
            }
        });
    }

    private MekanismExhaustPortTests() {}

    /** Places a port and returns its position. */
    private static BlockPos port(VentArea a, int x, int y, int z, Direction facing) {
        a.set(x, y, z, ExhaustPort.block().defaultBlockState().setValue(ExhaustPortBlock.FACING, facing));
        return a.abs(x, y, z);
    }

    /** The handler a tube or a machine behind a port finds. */
    private static IChemicalHandler back(GameTestHelper h, VentArea a, BlockPos pos, Direction facing) {
        IChemicalHandler handler = a.level.getCapability(ExhaustPort.CHEMICAL, pos, facing.getOpposite());
        h.assertTrue(handler != null, "the port offers no chemical handler at its back");
        return handler;
    }

    /** What a port takes of an offer: the amount offered less what it hands back. */
    private static long taken(IChemicalHandler handler, ChemicalStack offered, Action action) {
        return offered.getAmount() - handler.insertChemical(offered, action).getAmount();
    }

    // ---- The content mod's pipes, by id, as another mod has them -------------------------------

    /** Places a pipe part as its item would: shaped by its neighbours, then told it was placed. */
    private static void part(VentArea a, int x, int y, int z, BlockState state) {
        BlockPos pos = a.abs(x, y, z);
        BlockState shaped = Block.updateFromNeighbourShapes(state, a.level, pos);
        a.level.setBlockAndUpdate(pos, shaped);
        shaped.getBlock().setPlacedBy(a.level, pos, shaped, null, new ItemStack(shaped.getBlock()));
    }

    private static void pipe(VentArea a, int x, int y, int z) {
        part(a, x, y, z, BuiltInRegistries.BLOCK.get(PIPE).defaultBlockState());
    }

    /** A pipe cap, closing the pipe it faces. */
    private static void cap(VentArea a, int x, int y, int z, Direction facing) {
        part(
                a,
                x,
                y,
                z,
                BuiltInRegistries.BLOCK
                        .get(PIPE_CAP)
                        .defaultBlockState()
                        .setValue(BlockStateProperties.FACING, facing));
    }

    private static BooleanProperty armOn(Direction face) {
        return switch (face) {
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case EAST -> BlockStateProperties.EAST;
            case WEST -> BlockStateProperties.WEST;
            case UP -> BlockStateProperties.UP;
            case DOWN -> BlockStateProperties.DOWN;
        };
    }

    /** The faces a pipe has grown an arm on. */
    private static Set<Direction> arms(VentArea a, int x, int y, int z) {
        BlockState state = a.level.getBlockState(a.abs(x, y, z));
        Set<Direction> arms = EnumSet.noneOf(Direction.class);
        for (Direction face : Direction.values()) {
            if (state.getValue(armOn(face))) {
                arms.add(face);
            }
        }
        return arms;
    }

    /**
     * Gives a pipe an arm by hand and tells no neighbour, as one that had joined a gas handler
     * there would have it. The pipe takes it back at its next step if it finds no handler.
     */
    private static void setArm(VentArea a, int x, int y, int z, Direction face) {
        BlockPos pos = a.abs(x, y, z);
        a.level.setBlock(
                pos,
                a.level.getBlockState(pos).setValue(armOn(face), true),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** A pipe's network, asked through a face where nothing is connected. */
    private static IGasHandler network(GameTestHelper h, VentArea a, int x, int y, int z, Direction face) {
        IGasHandler handler = a.level.getCapability(IGasHandler.BLOCK, a.abs(x, y, z), face);
        h.assertTrue(handler != null, "the pipe at " + x + ", " + y + ", " + z + " offers no gas handler");
        return handler;
    }

    private static double openAirOxygen(BlockPos pos) {
        return OPEN_AIR.stream()
                .filter(event -> event.added() && event.pos().equals(pos))
                .mapToDouble(event -> event.gas().moles(OXYGEN))
                .sum();
    }

    /**
     * A port in a room's wall releases into the room what it is given at its back, mole for mole,
     * and at once. Asking what it would take releases nothing. It holds nothing and gives nothing
     * back, its other faces offer no handler, and it is no gas handler of ZAS³'s, so no pipe can
     * draw from it. Being a full block, it leaves the room sealed.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_022_an_exhaust_port_releases_what_it_is_given_into_its_room(GameTestHelper h) {
        VentArea a = new VentArea(h, 40);
        try {
            a.room();
            BlockPos pos = port(a, 0, 2, 2, Direction.EAST);
            a.form(2, 2, 2);
            h.assertFalse(Atmospheres.holdsGas(a.level, pos), "the port leaves room for gas, so the wall leaks");
            IChemicalHandler handler = back(h, a, pos, Direction.EAST);
            double oxygen = a.moles(2, 2, 2, OXYGEN);
            double hydrogen = a.moles(2, 2, 2, HYDROGEN);

            // What it would take, which releases nothing.
            h.assertTrue(handler.isValid(0, a.stack(MEK_OXYGEN, 1)), "oxygen isn't valid for the port");
            h.assertValueEqual(
                    taken(handler, a.stack(MEK_OXYGEN, 1_000), Action.SIMULATE), 1_000L, "oxygen it would take");
            a.tick();
            a.close(a.moles(2, 2, 2, OXYGEN), oxygen, "the room's oxygen after the port was only asked");

            // What it takes is in the room at the next atmos tick.
            h.assertValueEqual(taken(handler, a.stack(MEK_OXYGEN, 1_000), Action.EXECUTE), 1_000L, "oxygen it took");
            h.assertValueEqual(
                    taken(handler, a.stack(MEK_HYDROGEN, 250), Action.EXECUTE), 250L, "hydrogen it took, by tank");
            h.assertTrue(
                    handler.insertChemical(0, a.stack(MEK_HYDROGEN, 150), Action.EXECUTE)
                            .isEmpty(),
                    "the port handed hydrogen back");
            a.tick();
            a.close(a.moles(2, 2, 2, OXYGEN) - oxygen, mol(1_000), "the oxygen the room gained");
            a.close(a.moles(2, 2, 2, HYDROGEN) - hydrogen, mol(400), "the hydrogen the room gained");

            // It holds nothing, and nothing comes back out of it.
            h.assertValueEqual(handler.getChemicalTanks(), 1, "the port's tanks");
            h.assertTrue(handler.getChemicalInTank(0).isEmpty(), "the port holds a chemical");
            h.assertTrue(handler.extractChemical(0, 1_000, Action.EXECUTE).isEmpty(), "the port gave a chemical back");
            h.assertTrue(handler.extractChemical(1_000, Action.EXECUTE).isEmpty(), "the port gave a chemical back");
            h.assertTrue(
                    handler.extractChemical(a.stack(MEK_OXYGEN, 1_000), Action.EXECUTE)
                            .isEmpty(),
                    "the port gave oxygen back");
            h.assertTrue(
                    handler.insertChemical(1, a.stack(MEK_OXYGEN, 10), Action.EXECUTE)
                                    .getAmount()
                            == 10,
                    "the port took oxygen into a tank it doesn't have");

            // Only the back is a chemical handler, and no face is a gas handler.
            h.assertTrue(a.level.getCapability(ExhaustPort.CHEMICAL, pos, null) == null, "a handler for no side");
            for (Direction side : Direction.values()) {
                if (side != Direction.WEST) {
                    h.assertTrue(
                            a.level.getCapability(ExhaustPort.CHEMICAL, pos, side) == null,
                            "a chemical handler on the port's " + side + " face");
                }
                h.assertTrue(
                        a.level.getCapability(IGasHandler.BLOCK, pos, side) == null,
                        "a gas handler on the port's " + side + " face");
            }
            a.tick();
            a.close(a.moles(2, 2, 2, OXYGEN) - oxygen, mol(1_000), "the room's oxygen at the end");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * A chemical the map doesn't make a gas is refused, asked or offered, whole: lithium stays where
     * it was and the room gains nothing.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_022_an_exhaust_port_refuses_a_chemical_that_is_not_a_gas(GameTestHelper h) {
        VentArea a = new VentArea(h, 41);
        try {
            a.room();
            BlockPos pos = port(a, 0, 2, 2, Direction.EAST);
            a.form(2, 2, 2);
            IChemicalHandler handler = back(h, a, pos, Direction.EAST);
            h.assertTrue(ChemicalGases.mapping(a.chemical(LITHIUM)).isEmpty(), "lithium is mapped to a gas");
            double before = a.total(2, 2, 2);

            h.assertFalse(handler.isValid(0, a.stack(LITHIUM, 1)), "lithium is valid for the port");
            h.assertValueEqual(taken(handler, a.stack(LITHIUM, 500), Action.SIMULATE), 0L, "lithium it would take");
            h.assertValueEqual(taken(handler, a.stack(LITHIUM, 500), Action.EXECUTE), 0L, "lithium it took");
            h.assertTrue(
                    handler.insertChemical(ChemicalStack.EMPTY, Action.EXECUTE).isEmpty(), "nothing, offered");
            a.tick();
            a.close(a.total(2, 2, 2), before, "the room's gas after the port was offered lithium");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * At the end of a real tube: the tube joins the port's back, its network empties into the port
     * at Mekanism's own tick, and the room gains it all. An unmapped chemical stays in the tube.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_022_a_tube_empties_into_an_exhaust_port(GameTestHelper h) {
        VentArea a = new VentArea(h, 42);
        ChemicalNetwork network = null;
        try {
            h.assertTrue(
                    ExhaustPort.CHEMICAL == Capabilities.CHEMICAL.block(),
                    "the port's capability isn't the one Mekanism's tubes ask for");
            a.room();
            BlockPos pos = port(a, 0, 2, 2, Direction.EAST);
            a.form(2, 2, 2);
            TileEntityPressurizedTube tube = a.tube(-1, 2, 2);
            a.networkTick();
            network = tube.getTransmitter().getTransmitterNetwork();
            h.assertTrue(network != null, "the tube got no network");
            h.assertTrue(network.hasAcceptor(pos), "the tube didn't join the port");
            h.assertValueEqual(network.getAcceptorCount(), 1, "what the tube's network feeds");
            double oxygen = a.moles(2, 2, 2, OXYGEN);

            network.chemicalTank.setStack(a.stack(MEK_OXYGEN, 3_000));
            a.networkTick();
            h.assertTrue(network.chemicalTank.isEmpty(), "the tube still holds " + network.chemicalTank.getStack());
            a.tick();
            a.close(a.moles(2, 2, 2, OXYGEN) - oxygen, mol(3_000), "the oxygen the room gained from the tube");

            double total = a.total(2, 2, 2);
            network.chemicalTank.setStack(a.stack(LITHIUM, 1_000));
            a.networkTick();
            h.assertValueEqual(network.chemicalTank.getStored(), 1_000L, "the lithium left in the tube");
            a.tick();
            a.close(a.total(2, 2, 2), total, "the room's gas with lithium in the tube");
        } finally {
            if (network != null) {
                network.chemicalTank.setEmpty();
            }
            a.clear();
        }
        h.succeed();
    }

    /**
     * Beside a real machine: a chemical tank set to eject through its front sends its oxygen into a
     * port on that face, at the tank's own rate, and the room gains what the tank lost. With a full
     * block against the port's grille the port takes nothing, so the tank keeps its oxygen.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_022_a_machine_ejects_into_an_exhaust_port(GameTestHelper h) {
        VentArea a = new VentArea(h, 43);
        try {
            a.box(6, 4, 6);
            TileEntityChemicalTank tank = a.place(3, 1, 3, TANK, TileEntityChemicalTank.class);
            Direction front = tank.getDirection();
            h.assertTrue(front.getAxis().isHorizontal(), "the tank faces " + front);
            BlockPos beside = tank.getBlockPos().relative(front);
            BlockPos outlet = beside.relative(front);
            a.level.setBlockAndUpdate(
                    beside, ExhaustPort.block().defaultBlockState().setValue(ExhaustPortBlock.FACING, front));
            a.form(3, 3, 3);
            ConfigInfo sides = tank.configComponent.getConfig(TransmissionType.CHEMICAL);
            h.assertTrue(sides != null, "the tank has no chemical sides");
            sides.setDataType(DataType.OUTPUT, RelativeSide.FRONT);
            sides.setEjecting(true);
            tank.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 5_000));
            double oxygen = a.moles(3, 3, 3, OXYGEN);

            run(tank, 1);
            long ejected = 5_000 - tank.getChemicalTank().getStored();
            h.assertTrue(ejected > 0, "the tank ejected nothing into the port");
            a.tick();
            a.close(a.moles(3, 3, 3, OXYGEN) - oxygen, mol(ejected), "the oxygen the room gained from the tank");

            // Blocked: nothing leaves the tank, and nothing comes out round the block.
            a.level.setBlockAndUpdate(outlet, Blocks.STONE.defaultBlockState());
            a.tick();
            long held = tank.getChemicalTank().getStored();
            oxygen = a.moles(3, 3, 3, OXYGEN);
            run(tank, 3);
            h.assertValueEqual(tank.getChemicalTank().getStored(), held, "the tank's oxygen with the port blocked");
            a.tick();
            a.close(a.moles(3, 3, 3, OXYGEN), oxygen, "the room's oxygen with the port blocked");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * A port takes nothing while a full block is against its grille, asked or offered, and nothing
     * reaches the room round that block. With a block that leaves room for gas there, a slab, it
     * takes again, and so with the block gone. Facing the open air, it releases there.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_022_a_blocked_exhaust_port_takes_nothing(GameTestHelper h) {
        VentArea a = new VentArea(h, 44);
        try {
            a.room();
            BlockPos pos = port(a, 0, 2, 2, Direction.EAST);
            a.set(1, 2, 2, Blocks.STONE.defaultBlockState());
            a.form(2, 2, 2);
            IChemicalHandler handler = back(h, a, pos, Direction.EAST);
            double oxygen = a.moles(2, 2, 2, OXYGEN);

            h.assertValueEqual(taken(handler, a.stack(MEK_OXYGEN, 1_000), Action.SIMULATE), 0L, "blocked, asked");
            h.assertValueEqual(taken(handler, a.stack(MEK_OXYGEN, 1_000), Action.EXECUTE), 0L, "blocked, offered");
            a.tick();
            a.close(a.moles(2, 2, 2, OXYGEN), oxygen, "the room's oxygen with the port blocked");

            // A slab leaves half the block to the room. (A space that opens brings its own air, so
            // the room's oxygen is read again after each change.)
            a.set(1, 2, 2, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            a.tick();
            oxygen = a.moles(2, 2, 2, OXYGEN);
            h.assertValueEqual(taken(handler, a.stack(MEK_OXYGEN, 1_000), Action.EXECUTE), 1_000L, "behind a slab");
            a.tick();
            a.close(a.moles(2, 2, 2, OXYGEN) - oxygen, mol(1_000), "the oxygen the room gained past a slab");

            a.set(1, 2, 2, Blocks.AIR.defaultBlockState());
            a.tick();
            oxygen = a.moles(2, 2, 2, OXYGEN);
            h.assertValueEqual(taken(handler, a.stack(MEK_OXYGEN, 500), Action.EXECUTE), 500L, "with the block gone");
            a.tick();
            a.close(a.moles(2, 2, 2, OXYGEN) - oxygen, mol(500), "the oxygen the room gained with the block gone");

            // In the room's far wall, facing out: the open air takes it.
            BlockPos outer = port(a, 4, 2, 2, Direction.EAST);
            a.tick();
            oxygen = a.moles(2, 2, 2, OXYGEN);
            IChemicalHandler outward = back(h, a, outer, Direction.EAST);
            h.assertValueEqual(taken(outward, a.stack(MEK_OXYGEN, 700), Action.EXECUTE), 700L, "facing the open air");
            // The open air's share is reported a tick or two after the tick that applies it.
            for (int i = 0; i < 8 && openAirOxygen(a.abs(5, 2, 2)) == 0; i++) {
                a.tick();
            }
            a.close(openAirOxygen(a.abs(5, 2, 2)), mol(700), "the oxygen the open air took");
            a.close(a.moles(2, 2, 2, OXYGEN), oxygen, "the room's oxygen, the other port facing out");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * The venting's cap holds for a port as for any source (MEK-021), with one difference: what is
     * above it isn't deleted but left with whoever offered it. A port says so before it takes, and
     * asking uses none of the cap. Each port has its own, and with {@code release_above_cap} on a
     * port takes all it is offered.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_022_an_exhaust_port_takes_no_more_than_the_cap_and_leaves_the_rest(GameTestHelper h) {
        VentArea a = new VentArea(h, 45);
        long cap = MekanismSettings.VENTING_MAX_PER_SECOND.get();
        try {
            a.room();
            BlockPos first = port(a, 0, 2, 2, Direction.EAST);
            BlockPos second = port(a, 2, 0, 2, Direction.UP);
            a.form(2, 2, 2);
            IChemicalHandler one = back(h, a, first, Direction.EAST);
            IChemicalHandler two = back(h, a, second, Direction.UP);
            h.assertValueEqual(one.getChemicalTankCapacity(0), cap, "the port's capacity, the default cap");
            double oxygen = a.moles(2, 2, 2, OXYGEN);

            // No test runs between the changes and their undoing, since all are in this one call.
            MekanismSettings.VENTING_MAX_PER_SECOND.set(1_000L);
            h.assertValueEqual(one.getChemicalTankCapacity(0), 1_000L, "the port's capacity");
            h.assertValueEqual(taken(one, a.stack(MEK_OXYGEN, 1_600), Action.SIMULATE), 1_000L, "asked, above the cap");
            h.assertValueEqual(taken(one, a.stack(MEK_OXYGEN, 1_600), Action.SIMULATE), 1_000L, "asked again");
            h.assertValueEqual(taken(one, a.stack(MEK_OXYGEN, 600), Action.EXECUTE), 600L, "under the cap");
            h.assertValueEqual(taken(one, a.stack(MEK_OXYGEN, 1_600), Action.EXECUTE), 400L, "reaching the cap");
            h.assertValueEqual(taken(one, a.stack(MEK_OXYGEN, 500), Action.SIMULATE), 0L, "asked, the cap used up");
            h.assertValueEqual(taken(one, a.stack(MEK_OXYGEN, 500), Action.EXECUTE), 0L, "the cap used up");

            // The port in the floor has used none of its own.
            h.assertValueEqual(taken(two, a.stack(MEK_OXYGEN, 300), Action.EXECUTE), 300L, "the second port");
            a.tick();
            a.close(a.moles(2, 2, 2, OXYGEN) - oxygen, mol(1_300), "the oxygen the room gained under the cap");

            // Released above the cap: all of it, and none of it counted.
            MekanismSettings.VENTING_RELEASE_ABOVE_CAP.set(true);
            h.assertValueEqual(one.getChemicalTankCapacity(0), Long.MAX_VALUE, "the port's capacity, uncapped");
            h.assertValueEqual(taken(one, a.stack(MEK_OXYGEN, 5_000), Action.EXECUTE), 5_000L, "above the cap");
            MekanismSettings.VENTING_RELEASE_ABOVE_CAP.set(false);
            h.assertValueEqual(taken(two, a.stack(MEK_OXYGEN, 900), Action.EXECUTE), 700L, "the second port's rest");

            // A cap of nothing takes nothing.
            MekanismSettings.VENTING_MAX_PER_SECOND.set(0L);
            h.assertValueEqual(taken(two, a.stack(MEK_OXYGEN, 900), Action.EXECUTE), 0L, "a cap of nothing");
            a.tick();
            a.close(a.moles(2, 2, 2, OXYGEN) - oxygen, mol(1_300 + 5_000 + 700), "the oxygen the room gained in all");
        } finally {
            MekanismSettings.VENTING_RELEASE_ABOVE_CAP.set(false);
            MekanismSettings.VENTING_MAX_PER_SECOND.set(cap);
            a.clear();
        }
        h.succeed();
    }

    /**
     * The port doesn't depend on the venting's hooks: with {@code [venting] enabled} off, a tank set
     * to dump releases nothing, and a port in the same room releases all it is given. (That its
     * classes name none of the hooks' code is {@code AdapterReferencesTest}'s to check.)
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_022_an_exhaust_port_works_with_the_venting_switched_off(GameTestHelper h) {
        VentArea a = new VentArea(h, 46);
        try {
            a.room();
            BlockPos pos = port(a, 0, 2, 2, Direction.EAST);
            TileEntityChemicalTank tank = a.place(3, 1, 3, TANK, TileEntityChemicalTank.class);
            a.form(2, 2, 2);
            IChemicalHandler handler = back(h, a, pos, Direction.EAST);
            tank.getChemicalTank().setStack(a.stack(MEK_HYDROGEN, 10_000));
            tank.dumping = GasMode.DUMPING;
            double oxygen = a.moles(2, 2, 2, OXYGEN);
            double hydrogen = a.moles(2, 2, 2, HYDROGEN);

            // No test runs between the change and its undoing, since both are in this one call.
            MekanismSettings.VENTING_ENABLED.set(false);
            run(tank, 3);
            h.assertTrue(tank.getChemicalTank().getStored() < 10_000, "the tank dumped nothing");
            h.assertValueEqual(taken(handler, a.stack(MEK_OXYGEN, 2_000), Action.EXECUTE), 2_000L, "oxygen it took");
            MekanismSettings.VENTING_ENABLED.set(true);
            a.tick();
            a.close(a.moles(2, 2, 2, HYDROGEN), hydrogen, "the room's hydrogen, the hooks switched off");
            a.close(a.moles(2, 2, 2, OXYGEN) - oxygen, mol(2_000), "the oxygen the room gained from the port");
        } finally {
            MekanismSettings.VENTING_ENABLED.set(true);
            a.clear();
        }
        h.succeed();
    }

    /**
     * A radioactive chemical that a datapack maps radiates once, at the port, through Mekanism's
     * radiation manager, for what the port takes (MEK-023): asking radiates nothing, and what the
     * cap leaves in the tube hasn't left Mekanism, so it radiates nothing either. One that isn't
     * mapped, as none is by default, is refused like any other.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_023_an_exhaust_port_radiates_once_for_what_it_takes(GameTestHelper h) {
        VentArea a = new VentArea(h, 47);
        BlockPos free = a.abs(0, 2, 2);
        BlockPos capped = a.abs(2, 0, 2);
        BlockPos unmapped = a.abs(2, 2, 0);
        long cap = MekanismSettings.VENTING_MAX_PER_SECOND.get();
        try {
            h.assertTrue(IRadiationManager.INSTANCE.isRadiationEnabled(), "Mekanism's radiation is switched off");
            h.assertTrue(a.chemical(NUCLEAR_WASTE).value().isRadioactive(), "nuclear waste isn't radioactive");
            h.assertTrue(ChemicalGases.mapping(a.chemical(NUCLEAR_WASTE)).isPresent(), "the tests' data maps no waste");
            h.assertTrue(ChemicalGases.mapping(a.chemical(PLUTONIUM)).isEmpty(), "plutonium is mapped to a gas");
            a.room();
            port(a, 0, 2, 2, Direction.EAST);
            port(a, 2, 0, 2, Direction.UP);
            port(a, 2, 2, 0, Direction.SOUTH);
            a.form(2, 2, 2);
            double before = a.moles(2, 2, 2, CARBON_DIOXIDE);

            IChemicalHandler one = back(h, a, free, Direction.EAST);
            h.assertValueEqual(taken(one, a.stack(NUCLEAR_WASTE, 2_000), Action.SIMULATE), 2_000L, "waste, asked");
            a.close(a.radiation(free), 0, "the radiation at a port that was only asked");
            h.assertValueEqual(taken(one, a.stack(NUCLEAR_WASTE, 2_000), Action.EXECUTE), 2_000L, "waste it took");
            a.tick();
            a.close(a.moles(2, 2, 2, CARBON_DIOXIDE) - before, mol(2_000), "the gas the room gained from the waste");
            a.close(a.radiation(free), a.stack(NUCLEAR_WASTE, 2_000).getRadioactivity(), "the radiation at the port");

            // Under a cap: it takes 500 and radiates 500's worth. The rest is still in Mekanism.
            // No test runs between the change and its undoing, since both are in this one call.
            MekanismSettings.VENTING_MAX_PER_SECOND.set(500L);
            IChemicalHandler two = back(h, a, capped, Direction.UP);
            h.assertValueEqual(taken(two, a.stack(NUCLEAR_WASTE, 2_000), Action.EXECUTE), 500L, "waste, capped");
            MekanismSettings.VENTING_MAX_PER_SECOND.set(cap);
            a.tick();
            a.close(
                    a.moles(2, 2, 2, CARBON_DIOXIDE) - before,
                    mol(2_500),
                    "the gas the room gained, with a capped port");
            a.close(
                    a.radiation(capped),
                    a.stack(NUCLEAR_WASTE, 500).getRadioactivity(),
                    "the radiation at the capped port");

            // Not mapped, the default: refused, so nothing is released and nothing radiates.
            IChemicalHandler three = back(h, a, unmapped, Direction.SOUTH);
            double total = a.total(2, 2, 2);
            h.assertValueEqual(taken(three, a.stack(PLUTONIUM, 2_000), Action.EXECUTE), 0L, "plutonium it took");
            a.tick();
            a.close(a.total(2, 2, 2), total, "the room's gas after a port was offered plutonium");
            a.close(a.radiation(unmapped), 0, "the radiation at a port that refused plutonium");
        } finally {
            MekanismSettings.VENTING_MAX_PER_SECOND.set(cap);
            a.clear();
            a.scrub(free);
            a.scrub(capped);
            a.scrub(unmapped);
        }
        h.succeed();
    }

    /**
     * A pipe beside a port neither joins it nor feeds it: the port is for Mekanism's machinery, and
     * a pipe has vents of its own. The port offers no gas handler on any face, so a pipe behind it
     * and a pipe on top of it grow no arm to it, whichever of the two is placed first.
     *
     * <p>Then the game runs the pipes for five of their steps. Each was given an arm to the port by
     * hand, as a pipe that had joined a gas handler there would have, and takes it back at its
     * first step, having asked the port and found none. The pipes keep all their gas, none of it is
     * in the room, and the port still takes chemicals at its back.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH, timeoutTicks = 400)
    public static void mek_022_a_pipe_beside_an_exhaust_port_neither_joins_nor_feeds_it(GameTestHelper h) {
        if (!BuiltInRegistries.BLOCK.containsKey(PIPE)) {
            // The content is switched off on this server, so there are no pipes.
            h.succeed();
            return;
        }
        VentArea a = new VentArea(h, 49);
        boolean left = false;
        try {
            a.box(7, 5, 5);
            a.keepTicking();
            // One pipe behind the port, placed before it, closed by a cap at its west and one on top.
            cap(a, 2, 1, 2, Direction.EAST);
            pipe(a, 3, 1, 2);
            cap(a, 3, 2, 2, Direction.DOWN);
            BlockPos pos = port(a, 4, 1, 2, Direction.EAST);
            // One on top of the port, placed after it, closed by a cap on top and one at its east.
            pipe(a, 4, 2, 2);
            cap(a, 4, 3, 2, Direction.DOWN);
            cap(a, 5, 2, 2, Direction.WEST);
            a.form(5, 1, 2);

            for (Direction side : Direction.values()) {
                h.assertTrue(
                        a.level.getCapability(IGasHandler.BLOCK, pos, side) == null,
                        "a gas handler on the port's " + side + " face");
            }
            h.assertTrue(a.level.getCapability(IGasHandler.BLOCK, pos, null) == null, "a gas handler for no side");
            h.assertValueEqual(
                    arms(a, 3, 1, 2), EnumSet.of(Direction.WEST, Direction.UP), "the arms of the pipe behind the port");
            h.assertValueEqual(
                    arms(a, 4, 2, 2), EnumSet.of(Direction.UP, Direction.EAST), "the arms of the pipe on the port");

            IGasHandler behind = network(h, a, 3, 1, 2, Direction.NORTH);
            IGasHandler onTop = network(h, a, 4, 2, 2, Direction.NORTH);
            GasParcel carbonDioxide = new GasParcel(Map.of(CARBON_DIOXIDE, 5.0), StandardAir.TEMPERATURE);
            GasParcel hydrogen = new GasParcel(Map.of(HYDROGEN, 5.0), StandardAir.TEMPERATURE);
            a.close(
                    behind.fill(carbonDioxide, IGasHandler.Action.EXECUTE).moles(CARBON_DIOXIDE),
                    5,
                    "the carbon dioxide the pipe behind the port took");
            a.close(
                    onTop.fill(hydrogen, IGasHandler.Action.EXECUTE).moles(HYDROGEN),
                    5,
                    "the hydrogen the pipe on the port took");
            double roomCarbonDioxide = a.moles(5, 1, 2, CARBON_DIOXIDE);
            double roomHydrogen = a.moles(5, 1, 2, HYDROGEN);

            setArm(a, 3, 1, 2, Direction.EAST);
            setArm(a, 4, 2, 2, Direction.DOWN);

            // A pipe steps once in 20 game ticks.
            left = true;
            h.runAtTickTime(110, () -> {
                try {
                    h.assertValueEqual(
                            arms(a, 3, 1, 2),
                            EnumSet.of(Direction.WEST, Direction.UP),
                            "the arms of the pipe behind the port, after its steps");
                    h.assertValueEqual(
                            arms(a, 4, 2, 2),
                            EnumSet.of(Direction.UP, Direction.EAST),
                            "the arms of the pipe on the port, after its steps");
                    a.close(
                            network(h, a, 3, 1, 2, Direction.NORTH).contents().moles(CARBON_DIOXIDE),
                            5,
                            "the carbon dioxide in the pipe behind the port");
                    a.close(
                            network(h, a, 4, 2, 2, Direction.NORTH).contents().moles(HYDROGEN),
                            5,
                            "the hydrogen in the pipe on the port");
                    a.tick();
                    a.close(a.moles(5, 1, 2, CARBON_DIOXIDE), roomCarbonDioxide, "the room's carbon dioxide");
                    a.close(a.moles(5, 1, 2, HYDROGEN), roomHydrogen, "the room's hydrogen");
                    for (Direction side : Direction.values()) {
                        h.assertTrue(
                                a.level.getCapability(IGasHandler.BLOCK, pos, side) == null,
                                "a gas handler on the port's " + side + " face, after the pipes' steps");
                    }

                    // The port is still Mekanism's to use, with a pipe where a tube would be.
                    double oxygen = a.moles(5, 1, 2, OXYGEN);
                    IChemicalHandler handler = back(h, a, pos, Direction.EAST);
                    h.assertValueEqual(taken(handler, a.stack(MEK_OXYGEN, 500), Action.EXECUTE), 500L, "beside a pipe");
                    a.tick();
                    a.close(a.moles(5, 1, 2, OXYGEN) - oxygen, mol(500), "the oxygen the room gained");
                } finally {
                    a.clear();
                }
                h.succeed();
            });
        } finally {
            if (!left) {
                a.clear();
            }
        }
    }

    /**
     * The port is a block of the mod the adapter ships in, with an item, a recipe that makes it
     * from a pressurized tube, itself as its drop and a pickaxe as its tool, all of which exist
     * because Mekanism is installed. Whichever of the six ways it faces, its back and no other face
     * takes chemicals, and placed it faces whoever places it.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_022_the_exhaust_port_is_a_block_with_an_item_a_recipe_and_a_drop(GameTestHelper h) {
        VentArea a = new VentArea(h, 48);
        try {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("zas", "exhaust_port");
            Block block = ExhaustPort.block();
            h.assertValueEqual(BuiltInRegistries.BLOCK.getKey(block), id, "the block's id");
            h.assertValueEqual(BuiltInRegistries.ITEM.getKey(ExhaustPort.item()), id, "the item's id");
            h.assertTrue(Block.byItem(ExhaustPort.item()) == block, "the item doesn't place the block");

            Optional<RecipeHolder<?>> recipe = a.level.getRecipeManager().byKey(id);
            h.assertTrue(recipe.isPresent(), "no recipe " + id);
            h.assertTrue(
                    recipe.get().value().getResultItem(a.level.registryAccess()).is(ExhaustPort.item()),
                    "the recipe doesn't make the port");
            ItemStack tube = new ItemStack(
                    BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:basic_pressurized_tube")));
            h.assertFalse(tube.isEmpty(), "Mekanism registers no basic pressurized tube");
            h.assertTrue(
                    recipe.get().value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(tube)),
                    "the recipe uses no pressurized tube");

            a.clear();
            BlockPos pos = port(a, 2, 2, 2, Direction.NORTH);
            BlockState state = a.level.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, a.level, pos, null);
            h.assertTrue(drops.size() == 1 && drops.get(0).is(ExhaustPort.item()), "the port drops " + drops);
            h.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), "the port isn't mined with a pickaxe");
            h.assertTrue(state.requiresCorrectToolForDrops(), "the port drops without a tool");
            h.assertTrue(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), "a pickaxe isn't its tool");
            h.assertFalse(new ItemStack(Items.IRON_SHOVEL).isCorrectToolForDrops(state), "a shovel is its tool");

            for (Direction facing : Direction.values()) {
                port(a, 2, 2, 2, facing);
                for (Direction side : Direction.values()) {
                    boolean offered = a.level.getCapability(ExhaustPort.CHEMICAL, pos, side) != null;
                    h.assertValueEqual(
                            offered, side == facing.getOpposite(), "facing " + facing + ", a handler on " + side);
                }
            }

            // Placed, its grille faces the player: the way they look, turned round.
            FakePlayer player = a.player(2, 2, 4);
            for (float[] look : new float[][] {{0, 0}, {90, 0}, {180, 0}, {-90, 0}, {0, 90}, {0, -90}}) {
                player.setYRot(look[0]);
                player.setXRot(look[1]);
                BlockPlaceContext context = new BlockPlaceContext(
                        player,
                        InteractionHand.MAIN_HAND,
                        new ItemStack(ExhaustPort.item()),
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                BlockState placed = block.getStateForPlacement(context);
                h.assertTrue(placed != null, "the port can't be placed");
                h.assertValueEqual(
                        placed.getValue(ExhaustPortBlock.FACING),
                        Direction.orderedByNearest(player)[0].getOpposite(),
                        "the facing of a port placed looking " + look[0] + ", " + look[1]);
            }
        } finally {
            a.clear();
        }
        h.succeed();
    }
}
