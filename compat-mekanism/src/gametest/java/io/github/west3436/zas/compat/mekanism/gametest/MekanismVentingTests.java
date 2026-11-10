package io.github.west3436.zas.compat.mekanism.gametest;

import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.HYDROGEN;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.LITHIUM;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.NUCLEAR_WASTE;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.OXYGEN;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.WATER_VAPOR;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.mol;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.run;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.compat.mekanism.ChemicalGases;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import io.github.west3436.zas.compat.mekanism.Venting;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.radiation.IRadiationManager;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.network.ChemicalNetwork;
import mekanism.common.content.tank.TankMultiblockData;
import mekanism.common.lib.math.voxel.VoxelCuboid;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.TileEntityChemicalTank.GasMode;
import mekanism.common.tile.factory.TileEntityItemStackChemicalToItemStackFactory;
import mekanism.common.tile.machine.TileEntityElectrolyticSeparator;
import mekanism.common.tile.machine.TileEntityMetallurgicInfuser;
import mekanism.common.tile.machine.TileEntityPurificationChamber;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import mekanism.common.tile.transmitter.TileEntityPressurizedTube;
import mekanism.generators.common.content.turbine.TurbineMultiblockData;
import mekanism.generators.common.content.turbine.TurbineMultiblockData.VentData;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Venting into rooms (MEK-020): each place where Mekanism deletes a chemical releases it as gas
 * instead, where it is deleted, when the chemical map makes it one.
 *
 * <p>Each hook into Mekanism has a test of its own here (MEK-003), which drives the code of
 * Mekanism's that the hook is in and fails if the hook didn't apply: the room would gain nothing.
 * Each checks that gas is conserved, that the moles the room gains are the mB Mekanism deleted
 * times the unit factor. The build runs them with the newest Mekanism and the oldest supported.
 *
 * <p>The cap is {@link MekanismVentingCapTests}' and radiation {@link MekanismVentingRadiationTests}'.
 */
@GameTestHolder(MekanismFoundationTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismVentingTests {
    private static final String MEK_OXYGEN = "mekanism:oxygen";
    private static final String MEK_HYDROGEN = "mekanism:hydrogen";
    private static final String MEK_STEAM = "mekanism:steam";

    /** What the venting sent to the open air, for the tests of a machine with no room beside it. */
    private static final List<OpenAirGasEvent> OPEN_AIR = new CopyOnWriteArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener((OpenAirGasEvent event) -> {
            if (event.source().equals(Venting.SOURCE)) {
                OPEN_AIR.add(event);
            }
        });
    }

    private MekanismVentingTests() {}

    private static BasicChemicalTank unchecked(GameTestHelper h, IChemicalTank tank) {
        h.assertTrue(tank instanceof BasicChemicalTank, "the tank isn't one of Mekanism's basic tanks: " + tank);
        return (BasicChemicalTank) tank;
    }

    /**
     * Calls a machine's {@code dumpChemical}, a method of Mekanism's computer integration, which
     * calls it by reflection as well.
     */
    private static void dumpChemical(TileEntityAdvancedElectricMachine machine) {
        try {
            Method dump = TileEntityAdvancedElectricMachine.class.getDeclaredMethod("dumpChemical");
            dump.setAccessible(true);
            dump.invoke(machine);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("the machine's dumpChemical couldn't be called: " + e);
        }
    }

    /** The oxygen the venting sent to the open air at a block, in mol. */
    private static double openAirOxygen(BlockPos pos) {
        return OPEN_AIR.stream()
                .filter(event -> event.added() && event.pos().equals(pos))
                .mapToDouble(event -> event.gas().moles(OXYGEN))
                .sum();
    }

    // ---- Chemical tank ------------------------------------------------------------------

    /**
     * A chemical tank set to Dumping sheds a four-hundredth of its size each tick, and the room it
     * stands in gains exactly that as gas. Idle, it sheds nothing and the room gains nothing.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_chemical_tank_set_to_dump_releases_what_it_sheds(GameTestHelper h) {
        VentArea a = new VentArea(h, 0);
        try {
            a.room();
            TileEntityChemicalTank tank =
                    a.place(2, 1, 2, "mekanism:basic_chemical_tank", TileEntityChemicalTank.class);
            a.form(2, 3, 2);
            tank.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 10_000));
            double before = a.moles(2, 3, 2, OXYGEN);

            run(tank, 3);
            a.tick();
            h.assertValueEqual(tank.getChemicalTank().getStored(), 10_000L, "an idle tank's contents");
            a.close(a.moles(2, 3, 2, OXYGEN), before, "the room's oxygen beside an idle tank");

            tank.dumping = GasMode.DUMPING;
            run(tank, 3);
            a.tick();
            long shed = 10_000 - tank.getChemicalTank().getStored();
            h.assertValueEqual(shed, 3 * (tank.getTier().getStorage() / 400), "what three ticks of dumping shed");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(shed), "the oxygen the room gained");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * A chemical tank set to Dumping Excess sheds what it holds above the level Mekanism keeps, at
     * most its output rate each tick, and the room gains exactly that. At the level, it sheds
     * nothing more.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_chemical_tank_dumping_excess_releases_what_it_sheds(GameTestHelper h) {
        VentArea a = new VentArea(h, 1);
        try {
            a.room();
            TileEntityChemicalTank tank =
                    a.place(2, 1, 2, "mekanism:basic_chemical_tank", TileEntityChemicalTank.class);
            a.form(2, 3, 2);
            long full = tank.getChemicalTank().getCapacity();
            tank.getChemicalTank().setStack(a.stack(MEK_OXYGEN, full));
            double before = a.moles(2, 3, 2, OXYGEN);

            tank.dumping = GasMode.DUMPING_EXCESS;
            run(tank, 2);
            a.tick();
            long shed = full - tank.getChemicalTank().getStored();
            h.assertValueEqual(shed, 2 * tank.getTier().getOutput(), "what two ticks of dumping excess shed");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(shed), "the oxygen the room gained");

            // Until nothing is in excess: then the tank keeps the rest and the room gains no more.
            run(tank, 200);
            a.tick();
            long kept = tank.getChemicalTank().getStored();
            h.assertTrue(kept > 0 && kept < full - shed, "the tank kept " + kept + " of " + full);
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(full - kept), "the oxygen the room gained in all");
            run(tank, 5);
            a.tick();
            h.assertValueEqual(tank.getChemicalTank().getStored(), kept, "the tank at the level it keeps");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(full - kept), "the room's oxygen once nothing is in excess");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    // ---- Electrolytic separator ---------------------------------------------------------

    /**
     * An electrolytic separator sheds from each output on its own: the oxygen of one set to
     * Dumping, a little each tick, and the hydrogen of one set to Dumping Excess, down to the level
     * Mekanism keeps. The room gains exactly both. An idle output sheds nothing.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_an_electrolytic_separator_releases_what_its_outputs_dump(GameTestHelper h) {
        VentArea a = new VentArea(h, 2);
        try {
            a.room();
            TileEntityElectrolyticSeparator separator =
                    a.place(2, 1, 2, "mekanism:electrolytic_separator", TileEntityElectrolyticSeparator.class);
            a.form(2, 3, 2);
            long full = separator.leftTank.getCapacity();
            separator.leftTank.setStack(a.stack(MEK_HYDROGEN, full));
            separator.rightTank.setStack(a.stack(MEK_OXYGEN, 2_000));
            double oxygen = a.moles(2, 3, 2, OXYGEN);
            double hydrogen = a.moles(2, 3, 2, HYDROGEN);

            run(separator, 3);
            a.tick();
            h.assertValueEqual(separator.leftTank.getStored(), full, "an idle output's hydrogen");
            h.assertValueEqual(separator.rightTank.getStored(), 2_000L, "an idle output's oxygen");
            a.close(a.moles(2, 3, 2, OXYGEN), oxygen, "the room's oxygen beside an idle separator");

            // The oxygen side, Dumping: the first of the two calls the hook wraps.
            separator.dumpRight = GasMode.DUMPING;
            run(separator, 5);
            a.tick();
            long oxygenShed = 2_000 - separator.rightTank.getStored();
            h.assertTrue(oxygenShed > 0 && oxygenShed < 2_000, "five ticks of dumping shed " + oxygenShed + " mB");
            h.assertValueEqual(separator.leftTank.getStored(), full, "the hydrogen of the output left idle");
            a.close(a.moles(2, 3, 2, OXYGEN) - oxygen, mol(oxygenShed), "the oxygen the room gained");
            a.close(a.moles(2, 3, 2, HYDROGEN), hydrogen, "the room's hydrogen, none being dumped");

            // The hydrogen side, Dumping Excess: the second.
            separator.dumpRight = GasMode.IDLE;
            separator.dumpLeft = GasMode.DUMPING_EXCESS;
            run(separator, 1);
            a.tick();
            long hydrogenShed = full - separator.leftTank.getStored();
            h.assertTrue(hydrogenShed > 0 && hydrogenShed < full, "dumping excess shed " + hydrogenShed + " mB");
            a.close(a.moles(2, 3, 2, HYDROGEN) - hydrogen, mol(hydrogenShed), "the hydrogen the room gained");
            a.close(a.moles(2, 3, 2, OXYGEN) - oxygen, mol(oxygenShed), "the room's oxygen, no more being dumped");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    // ---- Industrial turbine -------------------------------------------------------------

    /**
     * An industrial turbine releases the steam it dumps, and only that: the steam it uses to make
     * power is not released. It leaves from the block outside a turbine vent, on that vent's side of
     * the shell, and with several vents from the one whose turn the tick is.
     *
     * <p>The turbine here is one casing block in the wall between two rooms, whose multiblock is
     * told it is formed, with the size, blades, coil and vents of a real one, through the same
     * public fields and methods Mekanism's structure check fills in. Its tick is Mekanism's own.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_an_industrial_turbine_releases_dumped_steam_outside_a_vent(GameTestHelper h) {
        VentArea a = new VentArea(h, 3);
        TurbineMultiblockData turbine = null;
        try {
            // Two rooms, west and east of a wall at x = 4, with the casing in the middle of the wall.
            a.box(8, 4, 4);
            a.stone(4, 1, 1, 4, 3, 3);
            TileEntityTurbineCasing casing =
                    a.place(4, 2, 2, "mekanismgenerators:turbine_casing", TileEntityTurbineCasing.class);
            BlockPos shell = casing.getBlockPos();
            a.form(2, 2, 2);
            a.form(6, 2, 2);
            h.assertFalse(a.zone(2, 2, 2).id().equals(a.zone(6, 2, 2).id()), "the two rooms are one zone");

            turbine = casing.getDefaultData();
            turbine.setShape(new VoxelCuboid(a.abs(2, 0, 0), a.abs(6, 4, 4)));
            turbine.setFormedForce(true);
            turbine.lowerVolume = 2;
            turbine.blades = 2;
            turbine.coils = 1;
            turbine.updateVentData(List.of(new VentData(shell, Direction.EAST)));
            long capacity = turbine.getSteamCapacity();
            h.assertTrue(capacity >= 100_000, "the turbine holds only " + capacity + " mB of steam");
            turbine.chemicalTank.setStack(a.stack(MEK_STEAM, 100_000));
            double west = a.moles(2, 2, 2, WATER_VAPOR);
            double east = a.moles(6, 2, 2, WATER_VAPOR);

            // Idle, it only uses steam: the first of the three calls in its tick, which isn't wrapped.
            turbine.dumpMode = GasMode.IDLE;
            turbine.tick(a.level);
            a.tick();
            h.assertTrue(turbine.clientFlow > 0, "the turbine used no steam");
            h.assertValueEqual(turbine.chemicalTank.getStored(), 100_000 - turbine.clientFlow, "the steam left");
            a.close(a.moles(2, 2, 2, WATER_VAPOR), west, "the west room's water vapour, nothing being dumped");
            a.close(a.moles(6, 2, 2, WATER_VAPOR), east, "the east room's water vapour, nothing being dumped");

            // Dumping, while it also uses steam: only the dumped part leaves, out of the east side.
            long held = turbine.chemicalTank.getStored();
            turbine.dumpMode = GasMode.DUMPING;
            turbine.tick(a.level);
            a.tick();
            long dumped = held - turbine.chemicalTank.getStored() - turbine.clientFlow;
            h.assertTrue(turbine.clientFlow > 0 && dumped > 0, "the turbine dumped " + dumped + " mB");
            a.close(a.moles(6, 2, 2, WATER_VAPOR) - east, mol(dumped), "the water vapour the east room gained");
            a.close(a.moles(2, 2, 2, WATER_VAPOR), west, "the west room's water vapour, its side having no vent");

            // Dumping Excess, with a vent on each side and no blades, so no steam is used.
            turbine.blades = 0;
            turbine.updateVentData(List.of(new VentData(shell, Direction.WEST), new VentData(shell, Direction.EAST)));
            turbine.chemicalTank.setStack(a.stack(MEK_STEAM, capacity));
            turbine.dumpMode = GasMode.DUMPING_EXCESS;
            turbine.tick(a.level);
            a.tick();
            long excess = capacity - turbine.chemicalTank.getStored();
            h.assertValueEqual(turbine.clientFlow, 0L, "the steam a turbine without blades uses");
            h.assertTrue(excess > 0 && excess < capacity, "dumping excess shed " + excess + " mB");
            boolean westsTurn = Math.floorMod(a.level.getGameTime(), 2L) == 0;
            a.close(
                    a.moles(2, 2, 2, WATER_VAPOR) - west,
                    westsTurn ? mol(excess) : 0,
                    "the water vapour the west room gained");
            a.close(
                    a.moles(6, 2, 2, WATER_VAPOR) - east,
                    mol(dumped) + (westsTurn ? 0 : mol(excess)),
                    "the water vapour the east room gained in all");
        } finally {
            if (turbine != null) {
                turbine.chemicalTank.setEmpty();
                turbine.setFormedForce(false);
            }
            a.clear();
        }
        h.succeed();
    }

    // ---- Dump buttons -------------------------------------------------------------------

    /** The dump button of a factory with a chemical tank empties the tank into the room. */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_factorys_dump_button_releases_its_tank(GameTestHelper h) {
        VentArea a = new VentArea(h, 4);
        try {
            a.room();
            TileEntityItemStackChemicalToItemStackFactory factory = a.place(
                    2, 1, 2, "mekanism:basic_purifying_factory", TileEntityItemStackChemicalToItemStackFactory.class);
            a.form(2, 3, 2);
            factory.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 600));
            h.assertValueEqual(factory.getChemicalTank().getStored(), 600L, "the oxygen in the factory");
            double before = a.moles(2, 3, 2, OXYGEN);

            factory.dump();
            a.tick();
            h.assertTrue(factory.getChemicalTank().isEmpty(), "the factory's tank after its dump button");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(600), "the oxygen the room gained");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * The dump button of a metallurgic infuser empties its tank into the room when what it holds
     * is a gas, and deletes it as before when it isn't, as no infuse type is. Mekanism's recipes put
     * only infuse types in an infuser, so the oxygen is put there past the tank's own check, as a
     * pack's recipe could.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_metallurgic_infusers_dump_button_releases_a_mapped_chemical(GameTestHelper h) {
        VentArea a = new VentArea(h, 5);
        try {
            a.room();
            TileEntityMetallurgicInfuser infuser =
                    a.place(2, 1, 2, "mekanism:metallurgic_infuser", TileEntityMetallurgicInfuser.class);
            a.form(2, 3, 2);
            unchecked(h, infuser.infusionTank).setStackUnchecked(a.stack(MEK_OXYGEN, 800));
            double before = a.moles(2, 3, 2, OXYGEN);

            infuser.dump();
            a.tick();
            h.assertTrue(infuser.infusionTank.isEmpty(), "the infuser's tank after its dump button");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(800), "the oxygen the room gained");

            double total = a.total(2, 3, 2);
            infuser.infusionTank.setStack(a.stack("mekanism:carbon", 500));
            infuser.dump();
            a.tick();
            h.assertTrue(infuser.infusionTank.isEmpty(), "the infuser's tank after dumping carbon");
            a.close(a.total(2, 3, 2), total, "the room's gas after carbon was dumped");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * A machine with one chemical tank and no dump button, here a purification chamber, has a
     * dump method that a computer calls. It empties the tank into the room too.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_machines_dump_method_releases_its_tank(GameTestHelper h) {
        VentArea a = new VentArea(h, 6);
        try {
            a.room();
            TileEntityPurificationChamber chamber =
                    a.place(2, 1, 2, "mekanism:purification_chamber", TileEntityPurificationChamber.class);
            a.form(2, 3, 2);
            chamber.chemicalTank.setStack(a.stack(MEK_OXYGEN, 200));
            double before = a.moles(2, 3, 2, OXYGEN);

            dumpChemical(chamber);
            a.tick();
            h.assertTrue(chamber.chemicalTank.isEmpty(), "the chamber's tank after its dump method");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(200), "the oxygen the room gained");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    // ---- Gauge dropper ------------------------------------------------------------------

    /**
     * A player who sneak-clicks a tank's gauge with a gauge dropper empties the tank, and the room
     * the machine stands in gains what it held.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_gauge_dropper_dumping_a_tank_releases_it(GameTestHelper h) {
        VentArea a = new VentArea(h, 7);
        try {
            a.room();
            TileEntityChemicalTank tank =
                    a.place(2, 1, 2, "mekanism:basic_chemical_tank", TileEntityChemicalTank.class);
            a.form(2, 3, 2);
            tank.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 5_000));
            double before = a.moles(2, 3, 2, OXYGEN);

            a.dropperDump(a.player(1, 1, 1), tank.getBlockPos());
            a.tick();
            h.assertTrue(tank.getChemicalTank().isEmpty(), "the tank after the dropper dumped it");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(5_000), "the oxygen the room gained");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * For a multiblock, Mekanism names the middle of the structure, which is inside its shell. The
     * gas leaves instead from outside the shell block whose window the player has open.
     *
     * <p>The multiblock is a 3 by 3 by 3 of stone with one dynamic tank block in its east face,
     * whose multiblock is told it is formed with that shape. Its middle has stone on every side, so
     * gas released there would be lost, and the room east of it would gain nothing.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_gauge_dropper_dumping_a_multiblock_releases_outside_its_shell(GameTestHelper h) {
        VentArea a = new VentArea(h, 8);
        TankMultiblockData multiblock = null;
        try {
            a.box(6, 4, 4);
            a.stone(1, 1, 1, 3, 3, 3);
            TileEntityDynamicTank open = a.place(3, 2, 2, "mekanism:dynamic_tank", TileEntityDynamicTank.class);
            a.form(5, 2, 2);
            multiblock = open.getDefaultData();
            multiblock.setShape(new VoxelCuboid(a.abs(1, 1, 1), a.abs(3, 3, 3)));
            multiblock.setFormedForce(true);
            multiblock.mergedTank.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 6_000));
            h.assertValueEqual(multiblock.getBounds().getCenter(), a.abs(2, 2, 2), "the middle Mekanism names");
            double before = a.moles(5, 2, 2, OXYGEN);

            a.dropperDump(a.player(5, 1, 2), open.getBlockPos());
            a.tick();
            h.assertTrue(multiblock.mergedTank.getChemicalTank().isEmpty(), "the multiblock after the dropper");
            a.close(a.moles(5, 2, 2, OXYGEN) - before, mol(6_000), "the oxygen the room outside the shell gained");
        } finally {
            if (multiblock != null) {
                multiblock.mergedTank.getChemicalTank().setEmpty();
                multiblock.setFormedForce(false);
            }
            a.clear();
        }
        h.succeed();
    }

    /**
     * A player who sneak-uses a gauge dropper in the hand empties the dropper itself, and what it
     * held is released at the player's head.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_gauge_dropper_emptied_in_the_hand_releases_at_the_player(GameTestHelper h) {
        VentArea a = new VentArea(h, 9);
        FakePlayer player = a.player(2, 1, 2);
        try {
            a.room();
            a.form(2, 3, 2);
            ItemStack dropper = a.dropper();
            IChemicalHandler held = Capabilities.CHEMICAL.getCapability(dropper);
            h.assertTrue(held != null, "the gauge dropper holds no chemicals");
            held.setChemicalInTank(0, a.stack(MEK_OXYGEN, 2_000));
            h.assertValueEqual(held.getChemicalInTank(0).getAmount(), 2_000L, "the oxygen in the dropper");
            double before = a.moles(2, 3, 2, OXYGEN);

            // Used without sneaking, the dropper keeps what it holds.
            player.setItemInHand(InteractionHand.MAIN_HAND, dropper);
            dropper.use(a.level, player, InteractionHand.MAIN_HAND);
            a.tick();
            h.assertValueEqual(held.getChemicalInTank(0).getAmount(), 2_000L, "the dropper, used without sneaking");
            a.close(a.moles(2, 3, 2, OXYGEN), before, "the room's oxygen, the dropper not emptied");

            player.setShiftKeyDown(true);
            dropper.use(a.level, player, InteractionHand.MAIN_HAND);
            a.tick();
            h.assertTrue(
                    Capabilities.CHEMICAL
                            .getCapability(dropper)
                            .getChemicalInTank(0)
                            .isEmpty(),
                    "the dropper after a sneak-use");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(2_000), "the oxygen the room gained");
        } finally {
            a.player(2, 1, 2);
            a.clear();
        }
        h.succeed();
    }

    // ---- Broken tubes -------------------------------------------------------------------

    /**
     * Breaking the last tube of a network releases all the network held, where the tube was.
     * Breaking one tube of two releases what the tube left can't hold, and that tube keeps the rest.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_broken_tube_releases_what_its_network_loses(GameTestHelper h) {
        VentArea a = new VentArea(h, 10);
        try {
            a.room();
            a.form(2, 3, 2);

            // One tube alone.
            TileEntityPressurizedTube only = a.tube(1, 1, 1);
            a.networkTick();
            ChemicalNetwork network = only.getTransmitter().getTransmitterNetwork();
            h.assertTrue(network != null, "the tube got no network");
            network.chemicalTank.setStack(a.stack(MEK_OXYGEN, 3_000));
            a.tick();
            double before = a.moles(2, 3, 2, OXYGEN);
            a.level.removeBlock(only.getBlockPos(), false);
            a.networkTick();
            a.tick();
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(3_000), "the oxygen the room gained from a lone tube");

            // Two tubes, one above the other, and the upper one broken.
            TileEntityPressurizedTube lower = a.tube(3, 1, 3);
            TileEntityPressurizedTube upper = a.tube(3, 2, 3);
            a.networkTick();
            ChemicalNetwork both = lower.getTransmitter().getTransmitterNetwork();
            h.assertTrue(
                    both != null && both == upper.getTransmitter().getTransmitterNetwork(), "two tubes, one network");
            long each = lower.getTransmitter().getCapacity();
            h.assertValueEqual(both.getCapacity(), 2 * each, "what two tubes hold");
            long held = 2 * each - 1_000;
            both.chemicalTank.setStack(a.stack(MEK_OXYGEN, held));
            a.tick();
            before = a.moles(2, 3, 2, OXYGEN);
            a.level.removeBlock(upper.getBlockPos(), false);
            a.networkTick();
            a.networkTick();
            a.tick();
            ChemicalNetwork left = lower.getTransmitter().getTransmitterNetwork();
            h.assertTrue(left != null && left != both, "the tube left got no network of its own");
            h.assertValueEqual(left.chemicalTank.getStored(), each, "what the tube left kept");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(held - each), "the oxygen the room gained from one of two");
            left.chemicalTank.setEmpty();
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * Mekanism also runs its code for a network's lost chemical when it saves a network that holds
     * more than its tubes do, which a lowered capacity setting can cause. Nothing has left the
     * network then, so nothing is released. That the code ran shows in the radiation Mekanism
     * applies there for a radioactive chemical.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_a_tube_network_saved_over_its_capacity_releases_nothing(GameTestHelper h) {
        VentArea a = new VentArea(h, 11);
        BlockPos pos = a.abs(2, 1, 2);
        try {
            h.assertTrue(IRadiationManager.INSTANCE.isRadiationEnabled(), "Mekanism's radiation is switched off");
            h.assertTrue(ChemicalGases.mapping(a.chemical(NUCLEAR_WASTE)).isPresent(), "the tests' data maps no waste");
            a.room();
            a.form(2, 3, 2);
            TileEntityPressurizedTube tube = a.tube(2, 1, 2);
            a.networkTick();
            ChemicalNetwork network = tube.getTransmitter().getTransmitterNetwork();
            h.assertTrue(network != null, "the tube got no network");
            long over = network.getCapacity() + 2_000;
            network.chemicalTank.setStack(a.stack(NUCLEAR_WASTE, over));
            a.tick();
            double total = a.total(2, 3, 2);

            network.validateSaveShares(tube.getTransmitter());
            a.tick();
            a.close(a.radiation(pos), a.stack(NUCLEAR_WASTE, 2_000).getRadioactivity(), "Mekanism's radiation");
            h.assertValueEqual(network.chemicalTank.getStored(), over, "what the network still holds");
            a.close(a.total(2, 3, 2), total, "the room's gas after the network was saved");
            network.chemicalTank.setEmpty();
        } finally {
            a.clear();
            a.scrub(pos);
        }
        h.succeed();
    }

    // ---- What is and isn't released, and where ------------------------------------------

    /** A chemical with no mapping is deleted as it is without ZAS³: the room gains nothing. */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_an_unmapped_chemical_is_voided_as_before(GameTestHelper h) {
        VentArea a = new VentArea(h, 12);
        try {
            h.assertTrue(ChemicalGases.mapping(a.chemical(LITHIUM)).isEmpty(), "lithium is mapped to a gas");
            a.room();
            TileEntityChemicalTank tank =
                    a.place(2, 1, 2, "mekanism:basic_chemical_tank", TileEntityChemicalTank.class);
            a.form(2, 3, 2);
            tank.getChemicalTank().setStack(a.stack(LITHIUM, 10_000));
            double total = a.total(2, 3, 2);

            tank.dumping = GasMode.DUMPING;
            run(tank, 3);
            a.tick();
            h.assertValueEqual(
                    tank.getChemicalTank().getStored(),
                    10_000 - 3 * (tank.getTier().getStorage() / 400),
                    "the lithium left after three ticks of dumping");
            a.close(a.total(2, 3, 2), total, "the room's gas after lithium was dumped");

            a.dropperDump(a.player(1, 1, 1), tank.getBlockPos());
            a.tick();
            h.assertTrue(tank.getChemicalTank().isEmpty(), "the tank after the dropper dumped it");
            a.close(a.total(2, 3, 2), total, "the room's gas after a dropper dumped lithium");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * With the venting switched off in the server config, Mekanism deletes what it voids, as
     * without ZAS³, and the room gains nothing. Switched back on, the next tick's is released.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_switched_off_the_venting_leaves_voiding_to_mekanism(GameTestHelper h) {
        VentArea a = new VentArea(h, 13);
        try {
            h.assertTrue(MekanismSettings.ventingEnabled(), "the venting is off by default");
            a.room();
            TileEntityChemicalTank tank =
                    a.place(2, 1, 2, "mekanism:basic_chemical_tank", TileEntityChemicalTank.class);
            a.form(2, 3, 2);
            tank.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 10_000));
            tank.dumping = GasMode.DUMPING;
            long each = tank.getTier().getStorage() / 400;
            double before = a.moles(2, 3, 2, OXYGEN);

            // No test runs between the change and its undoing, since both are in this one call.
            try {
                MekanismSettings.VENTING_ENABLED.set(false);
                run(tank, 3);
                a.tick();
            } finally {
                MekanismSettings.VENTING_ENABLED.set(true);
            }
            h.assertValueEqual(tank.getChemicalTank().getStored(), 10_000 - 3 * each, "the oxygen left in the tank");
            a.close(a.moles(2, 3, 2, OXYGEN), before, "the room's oxygen with the venting off");

            run(tank, 1);
            a.tick();
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(each), "the oxygen the room gained once it was on again");
        } finally {
            a.clear();
        }
        h.succeed();
    }

    /**
     * A machine with no room beside it. A tank standing in the open releases to the open air
     * (CORE-053), and so does a machine with no free face that is a full block, here a purification
     * chamber with stone on all six sides: it has nowhere to release to, and the gas is lost as it
     * was when Mekanism deleted it. Neither makes a zone.
     *
     * <p>A walled-in machine that isn't a full block, as a factory isn't, releases into the gap its
     * own shape leaves in its block, which is then a small sealed space.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_020_with_no_room_beside_it_a_machine_releases_to_the_open_air(GameTestHelper h) {
        VentArea a = new VentArea(h, 14);
        try {
            a.clear();
            // In the open.
            TileEntityChemicalTank tank =
                    a.place(1, 1, 1, "mekanism:basic_chemical_tank", TileEntityChemicalTank.class);
            a.tick();
            tank.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 10_000));
            tank.dumping = GasMode.DUMPING;
            run(tank, 3);
            a.tick();
            long shed = 10_000 - tank.getChemicalTank().getStored();
            h.assertTrue(shed > 0, "the tank shed nothing");
            h.assertTrue(Atmospheres.zoneAt(a.level, tank.getBlockPos()).isEmpty(), "a zone formed in the open air");
            a.close(openAirOxygen(tank.getBlockPos()), mol(shed), "the oxygen the open air took from the tank");

            // A full block with stone on all six sides.
            a.stone(4, 0, 0, 6, 2, 2);
            TileEntityPurificationChamber chamber =
                    a.place(5, 1, 1, "mekanism:purification_chamber", TileEntityPurificationChamber.class);
            a.tick();
            chamber.chemicalTank.setStack(a.stack(MEK_OXYGEN, 200));
            dumpChemical(chamber);
            a.tick();
            h.assertTrue(chamber.chemicalTank.isEmpty(), "the walled-in chamber's tank after its dump method");
            h.assertTrue(Atmospheres.zoneAt(a.level, chamber.getBlockPos()).isEmpty(), "a zone formed in the chamber");
            for (Direction side : Direction.values()) {
                BlockPos beside = chamber.getBlockPos().relative(side);
                h.assertTrue(
                        Atmospheres.zoneAt(a.level, beside).isEmpty(), "a zone formed " + side + " of the chamber");
            }
            a.close(openAirOxygen(chamber.getBlockPos()), mol(200), "the oxygen the open air took from the chamber");

            // Not a full block, with stone on all six sides: the gap around it is its room.
            a.stone(8, 0, 0, 10, 2, 2);
            TileEntityItemStackChemicalToItemStackFactory factory = a.place(
                    9, 1, 1, "mekanism:basic_purifying_factory", TileEntityItemStackChemicalToItemStackFactory.class);
            a.tick();
            a.form(9, 1, 1);
            double before = a.moles(9, 1, 1, OXYGEN);
            factory.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 600));
            factory.dump();
            a.tick();
            h.assertTrue(factory.getChemicalTank().isEmpty(), "the walled-in factory's tank after its dump button");
            a.close(a.moles(9, 1, 1, OXYGEN) - before, mol(600), "the oxygen in the gap around the factory");
            a.close(openAirOxygen(factory.getBlockPos()), 0, "the oxygen the open air took from the factory");
        } finally {
            a.clear();
        }
        h.succeed();
    }
}
