package io.github.west3436.zas.compat.mekanism.gametest;

import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.CARBON_DIOXIDE;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.LITHIUM;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.NITROGEN;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.NUCLEAR_WASTE;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.OXYGEN;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.mol;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import mekanism.api.Action;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.radiation.IRadiationManager;
import mekanism.common.content.network.ChemicalNetwork;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.transmitter.TileEntityPressurizedTube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Venting on purpose (MEK-022): a passive vent on a pressurized tube, or against a machine's
 * output, lets Mekanism's chemicals out into the room as gas. No block of the adapter's is needed:
 * the passive vent offers gas at its pipe face, the adapter gives it a view of that as Mekanism's
 * chemical handler (MEK-013), and the vent shares what arrives with the room in front of it.
 *
 * <p>Each test builds a sealed stone room holding a real chemical tank, a real tube where there is
 * one, and the content mod's passive vent, placed by id, and leaves them to the game: the tank
 * ejects, the tube carries, the vent steps. The room is read through the public API and the vent's
 * network through its gas handler. Gas is conserved: every mole of what the tank held is in the
 * room or in the vent's network, at the unit factor (MEK-002).
 *
 * <p>The tests are in the venting's batch because they run atmos ticks themselves. They pass over
 * when the content is switched off, since there is no passive vent then.
 */
@GameTestHolder(MekanismFoundationTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismVentRouteTests {
    private static final String MEK_OXYGEN = "mekanism:oxygen";
    private static final String TANK = "mekanism:basic_chemical_tank";
    private static final String TUBE = "mekanism:basic_pressurized_tube";

    /** The content mod's passive vent, by id: the adapter and its tests name none of its classes. */
    private static final ResourceLocation PASSIVE_VENT = ResourceLocation.parse("zas_atmos:passive_vent");

    /** The pressure difference a passive vent rests within, in kPa (CONT-016). */
    private static final double REST_KPA = 0.5;

    /**
     * How long a test waits for the game to tick a chunk it has asked it to keep. The test server
     * runs game ticks as fast as it can while a chunk starts ticking in real time, so this is far
     * more ticks than a passing test uses.
     */
    private static final int CHUNK_WAIT = 12_000;

    private MekanismVentRouteTests() {}

    /** Passes a test over if the content is switched off, and says whether it did. */
    private static boolean passedOver(GameTestHelper h) {
        if (BuiltInRegistries.BLOCK.containsKey(PASSIVE_VENT)) {
            return false;
        }
        h.succeed();
        return true;
    }

    private static <V extends Comparable<V>> BlockState facing(BlockState state, Direction way) {
        @SuppressWarnings("unchecked")
        Property<V> property =
                (Property<V>) state.getBlock().getStateDefinition().getProperty("facing");
        if (property == null) {
            throw new GameTestAssertException(state + " has no facing");
        }
        @SuppressWarnings("unchecked")
        V value = (V) way;
        return state.setValue(property, value);
    }

    /** Places a passive vent as its item would, its grille facing one way and its pipe face the other. */
    private static BlockPos vent(VentArea a, int x, int y, int z, Direction grille) {
        BlockPos pos = a.abs(x, y, z);
        BlockState state = facing(BuiltInRegistries.BLOCK.get(PASSIVE_VENT).defaultBlockState(), grille);
        BlockState shaped = Block.updateFromNeighbourShapes(state, a.level, pos);
        a.level.setBlockAndUpdate(pos, shaped);
        shaped.getBlock().setPlacedBy(a.level, pos, shaped, null, new ItemStack(shaped.getBlock()));
        return pos;
    }

    /** A Basic Chemical Tank whose front gives and ejects, as one a player has just placed does. */
    private static TileEntityChemicalTank tank(VentArea a, int x, int y, int z, Direction front) {
        a.set(x, y, z, facing(a.block(TANK), front));
        BlockEntity entity = a.level.getBlockEntity(a.abs(x, y, z));
        if (!(entity instanceof TileEntityChemicalTank tank)) {
            throw new GameTestAssertException("the tank's block entity is " + entity);
        }
        a.h.assertValueEqual(tank.getDirection(), front, "the way the tank faces");
        ConfigInfo sides = tank.configComponent.getConfig(TransmissionType.CHEMICAL);
        a.h.assertTrue(sides != null, "the tank has no chemical sides");
        sides.setDataType(DataType.OUTPUT, RelativeSide.FRONT);
        sides.setEjecting(true);
        return tank;
    }

    /** What a vent's network holds, read at its pipe face. */
    private static GasMixture network(VentArea a, BlockPos vent, Direction pipeFace) {
        IGasHandler handler = a.level.getCapability(IGasHandler.BLOCK, vent, pipeFace);
        a.h.assertTrue(handler != null, "the vent offers no gas handler at its pipe face");
        return handler.contents();
    }

    /** The view of a vent's pipe face that a tube or a machine there finds. */
    private static IChemicalHandler view(VentArea a, BlockPos vent, Direction pipeFace) {
        IChemicalHandler handler = a.level.getCapability(Bench.CHEMICAL, vent, pipeFace);
        a.h.assertTrue(handler != null, "the vent offers no chemical handler at its pipe face");
        return handler;
    }

    private static long inTube(TileEntityPressurizedTube tube) {
        ChemicalNetwork network = tube.getTransmitter().getTransmitterNetwork();
        return network == null ? 0 : network.chemicalTank.getStored();
    }

    /**
     * A tank of oxygen, a passive vent, and between them a tube or nothing, left to the game until
     * the tank and the tube are empty and the vent is at rest. The room has then gained nearly all
     * the tank held, the vent's own 400 L hold the rest at the room's pressure, and the two add up
     * to what the tank held, at the unit factor. The room's nitrogen, which the vent also shares
     * with its network, is all still in the room or the network.
     */
    private static void route(GameTestHelper h, int index, boolean withTube) {
        VentArea a = new VentArea(h, index);
        a.box(8, 4, 4);
        a.keepTicking();
        TileEntityChemicalTank tank = tank(a, 1, 1, 2, Direction.EAST);
        TileEntityPressurizedTube tube = withTube ? a.place(2, 1, 2, TUBE, TileEntityPressurizedTube.class) : null;
        BlockPos vent = vent(a, withTube ? 3 : 2, 1, 2, Direction.EAST);
        a.form(6, 2, 2);
        // The test waits, and the room's air is close to the open air's.
        a.pin(6, 2, 2);
        long amount = 8_000;
        double[] oxygen = new double[2];
        double[] nitrogen = new double[1];
        h.startSequence()
                // The game has the chunk ticking, the tube has joined, and the vent has let the room's air in.
                .thenWaitUntil(() -> {
                    a.tick();
                    assertAtRest(a, vent);
                    if (tube != null) {
                        h.assertTrue(joins(tube, Direction.WEST), "the tube draws no connection to the tank");
                        h.assertTrue(joins(tube, Direction.EAST), "the tube draws no connection to the vent");
                    }
                })
                .thenExecute(() -> {
                    GasMixture room = a.zone(6, 2, 2).mixture();
                    GasMixture net = network(a, vent, Direction.WEST);
                    oxygen[0] = room.moles(OXYGEN);
                    oxygen[1] = net.moles(OXYGEN);
                    nitrogen[0] = room.moles(NITROGEN) + net.moles(NITROGEN);
                    tank.getChemicalTank().setStack(a.stack(MEK_OXYGEN, amount));
                })
                .thenWaitUntil(() -> {
                    a.tick();
                    h.assertTrue(
                            tank.getChemicalTank().isEmpty(),
                            "the tank still holds " + tank.getChemicalTank().getStored() + " mB");
                    h.assertTrue(tube == null || inTube(tube) == 0, "the tube still holds oxygen");
                    assertAtRest(a, vent);
                })
                .thenExecute(() -> {
                    GasMixture room = a.zone(6, 2, 2).mixture();
                    GasMixture net = network(a, vent, Direction.WEST);
                    double gained = room.moles(OXYGEN) - oxygen[0];
                    double kept = net.moles(OXYGEN) - oxygen[1];
                    a.close(gained + kept, mol(amount), "the oxygen the room and the vent's network gained");
                    h.assertTrue(
                            gained >= 0.98 * mol(amount),
                            "the room gained " + gained / mol(1) + " mB of the tank's " + amount);
                    a.close(
                            room.moles(NITROGEN) + net.moles(NITROGEN),
                            nitrogen[0],
                            "the nitrogen in the room and the vent's network");
                    a.clear();
                })
                .thenSucceed();
    }

    private static boolean joins(TileEntityPressurizedTube tube, Direction face) {
        return tube.getTransmitter().getConnectionType(face) != ConnectionType.NONE
                && tube.getTransmitter().getTransmitterNetwork() != null;
    }

    /** A passive vent moves nothing while its network and the room are within its threshold. */
    private static void assertAtRest(VentArea a, BlockPos vent) {
        double net = network(a, vent, Direction.WEST).pressure();
        double room = a.zone(6, 2, 2).mixture().pressure();
        a.h.assertTrue(
                Math.abs(net - room) <= REST_KPA,
                "the vent's network is at " + net + " kPa and the room at " + room + " kPa");
    }

    /**
     * On a tube: a chemical tank ejects into a pressurized tube, the tube joins the passive vent's
     * pipe face, and the room in front of the vent gains the oxygen.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH, timeoutTicks = CHUNK_WAIT)
    public static void mek_022_a_passive_vent_on_a_tube_lets_a_tanks_chemical_into_its_room(GameTestHelper h) {
        if (passedOver(h)) {
            return;
        }
        route(h, 60, true);
    }

    /**
     * Against a machine: a chemical tank's ejecting front against the passive vent's pipe face, with
     * no tube, and the room in front of the vent gains the oxygen.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH, timeoutTicks = CHUNK_WAIT)
    public static void mek_022_a_passive_vent_against_a_tanks_output_lets_its_chemical_into_its_room(GameTestHelper h) {
        if (passedOver(h)) {
            return;
        }
        route(h, 61, false);
    }

    /**
     * A chemical that is no gas doesn't get into a passive vent: asked or offered, the vent's view
     * hands all of it back, so it stays in the tube or the machine, and the vent's network holds
     * what it held.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_022_a_passive_vent_takes_no_chemical_that_is_not_a_gas(GameTestHelper h) {
        if (passedOver(h)) {
            return;
        }
        VentArea a = new VentArea(h, 62);
        try {
            a.room();
            BlockPos vent = vent(a, 1, 2, 2, Direction.EAST);
            a.form(3, 2, 2);
            IChemicalHandler view = view(a, vent, Direction.WEST);
            double held = network(a, vent, Direction.WEST).totalMoles();
            ChemicalStack lithium = a.stack(LITHIUM, 1_000);
            h.assertValueEqual(
                    view.insertChemical(lithium, Action.SIMULATE).getAmount(), 1_000L, "the lithium left, asked");
            h.assertValueEqual(
                    view.insertChemical(lithium, Action.EXECUTE).getAmount(), 1_000L, "the lithium left, offered");
            a.close(network(a, vent, Direction.WEST).totalMoles(), held, "the gas in the vent's network");
            for (Direction face : Direction.values()) {
                if (face != Direction.WEST) {
                    h.assertTrue(
                            a.level.getCapability(Bench.CHEMICAL, vent, face) == null,
                            "the vent offers a chemical handler on " + face + ", which is no pipe face");
                }
            }
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * A radioactive chemical that a pack mapped releases its radiation once, through Mekanism, at
     * the passive vent it enters: none when only asked, and for what entered, exactly what Mekanism
     * itself would release for that amount. Its gas is in the vent's network, at the unit factor,
     * and carries none.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_023_a_mapped_radioactive_chemical_radiates_once_as_it_enters_a_passive_vent(
            GameTestHelper h) {
        if (passedOver(h)) {
            return;
        }
        VentArea a = new VentArea(h, 63);
        try {
            IRadiationManager radiation = IRadiationManager.INSTANCE;
            h.assertTrue(radiation.isRadiationEnabled(), "Mekanism's radiation is off on this server");
            a.room();
            BlockPos vent = vent(a, 1, 2, 2, Direction.EAST);
            a.form(3, 2, 2);
            IChemicalHandler view = view(a, vent, Direction.WEST);
            ChemicalStack waste = a.stack(NUCLEAR_WASTE, 1_000);
            h.assertTrue(waste.isRadioactive(), "nuclear waste isn't radioactive");
            double carbonDioxide = network(a, vent, Direction.WEST).moles(CARBON_DIOXIDE);
            double before = radiation.getRadiationLevel(a.level, vent);

            h.assertTrue(view.insertChemical(waste, Action.SIMULATE).isEmpty(), "the waste would be refused");
            a.close(radiation.getRadiationLevel(a.level, vent), before, "the radiation after only asking");
            h.assertValueEqual(a.radiation(vent), 0.0, "the radiation source at the vent after only asking");

            h.assertTrue(view.insertChemical(waste, Action.EXECUTE).isEmpty(), "the waste was refused");
            a.close(
                    radiation.getRadiationLevel(a.level, vent) - before,
                    waste.getRadioactivity(),
                    "the radiation 1,000 mB of waste released");
            a.close(a.radiation(vent), waste.getRadioactivity(), "the radiation source at the vent");
            // The tests' data maps the waste to carbon dioxide.
            a.close(
                    network(a, vent, Direction.WEST).moles(CARBON_DIOXIDE) - carbonDioxide,
                    mol(1_000),
                    "the carbon dioxide the waste became");
            a.scrub(vent);
        } finally {
            a.clear();
        }
        h.succeed();
    }
}
