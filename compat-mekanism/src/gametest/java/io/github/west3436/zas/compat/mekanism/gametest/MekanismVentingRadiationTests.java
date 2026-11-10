package io.github.west3436.zas.compat.mekanism.gametest;

import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.CARBON_DIOXIDE;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.NUCLEAR_WASTE;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.PLUTONIUM;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.mol;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.run;

import io.github.west3436.zas.compat.mekanism.ChemicalGases;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.radiation.IRadiationManager;
import mekanism.common.content.network.ChemicalNetwork;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.TileEntityChemicalTank.GasMode;
import mekanism.common.tile.transmitter.TileEntityPressurizedTube;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Radioactive chemicals and the venting (MEK-023). None is a gas by default, so by default Mekanism
 * deletes and radiates them as it does without ZAS³. These tests' data maps nuclear waste to carbon
 * dioxide, as a datapack may, and its radiation is then applied exactly once: by Mekanism on the
 * two paths where Mekanism radiates what it voids, and by the venting, through Mekanism's own
 * radiation manager, on the paths where Mekanism voids silently.
 *
 * <p>The radiation is read back as the magnitude of Mekanism's radiation source at the block, which
 * a second application would double.
 */
@GameTestHolder(MekanismFoundationTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismVentingRadiationTests {
    private static final String TANK = "mekanism:basic_chemical_tank";

    private MekanismVentingRadiationTests() {}

    private static void assumeRadiation(GameTestHelper h, VentArea a) {
        h.assertTrue(IRadiationManager.INSTANCE.isRadiationEnabled(), "Mekanism's radiation is switched off");
        h.assertTrue(a.chemical(NUCLEAR_WASTE).value().isRadioactive(), "nuclear waste isn't radioactive");
        h.assertTrue(ChemicalGases.mapping(a.chemical(NUCLEAR_WASTE)).isPresent(), "the tests' data maps no waste");
        h.assertTrue(a.chemical(PLUTONIUM).value().isRadioactive(), "plutonium isn't radioactive");
        h.assertTrue(ChemicalGases.mapping(a.chemical(PLUTONIUM)).isEmpty(), "plutonium is mapped to a gas");
    }

    /** Fills a tank past its own check, which refuses radioactive chemicals while radiation is on. */
    private static void force(GameTestHelper h, TileEntityChemicalTank tank, VentArea a, String chemical, long amount) {
        h.assertTrue(tank.getChemicalTank() instanceof BasicChemicalTank, "the tank isn't a basic tank");
        ((BasicChemicalTank) tank.getChemicalTank()).setStackUnchecked(a.stack(chemical, amount));
    }

    /** A tube's network holding a chemical, with the tube broken and Mekanism's tick end run. */
    private static void breakTubeHolding(GameTestHelper h, VentArea a, int x, int y, int z, String chemical) {
        TileEntityPressurizedTube tube = a.tube(x, y, z);
        a.networkTick();
        ChemicalNetwork network = tube.getTransmitter().getTransmitterNetwork();
        h.assertTrue(network != null, "the tube got no network");
        network.chemicalTank.setStack(a.stack(chemical, 2_000));
        a.level.removeBlock(tube.getBlockPos(), false);
        a.networkTick();
        a.tick();
    }

    /**
     * A broken tube is one of the two paths where Mekanism radiates what it voids. The room gains
     * the mapped waste as gas and the radiation is Mekanism's alone, applied once, for all the
     * tube lost even when the cap lets only part of it out as gas. A radioactive chemical with no
     * mapping, as every one is by default, gives the room nothing and radiates as it always did.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_023_a_broken_tube_radiates_once_and_by_mekanism(GameTestHelper h) {
        VentArea a = new VentArea(h, 30);
        BlockPos mapped = a.abs(1, 1, 1);
        BlockPos capped = a.abs(3, 1, 1);
        BlockPos unmapped = a.abs(3, 1, 3);
        long cap = MekanismSettings.VENTING_MAX_PER_SECOND.get();
        try {
            assumeRadiation(h, a);
            a.room();
            a.form(2, 3, 2);
            double once = a.stack(NUCLEAR_WASTE, 2_000).getRadioactivity();
            h.assertTrue(once > 0, "2,000 mB of waste radiate nothing");

            double before = a.moles(2, 3, 2, CARBON_DIOXIDE);
            breakTubeHolding(h, a, 1, 1, 1, NUCLEAR_WASTE);
            a.close(a.moles(2, 3, 2, CARBON_DIOXIDE) - before, mol(2_000), "the gas the room gained from the waste");
            a.close(a.radiation(mapped), once, "the radiation where the tube was");

            // Under a cap, only part becomes gas. Mekanism still radiates all the tube lost, once.
            // No test runs between the change and its undoing, since both are in this one call.
            MekanismSettings.VENTING_MAX_PER_SECOND.set(500L);
            breakTubeHolding(h, a, 3, 1, 1, NUCLEAR_WASTE);
            MekanismSettings.VENTING_MAX_PER_SECOND.set(cap);
            a.close(
                    a.moles(2, 3, 2, CARBON_DIOXIDE) - before,
                    mol(2_000 + 500),
                    "the gas the room gained from the capped tube");
            a.close(a.radiation(capped), once, "the radiation where the capped tube was");

            // Not mapped, the default: no gas, and Mekanism's radiation as without ZAS³.
            double total = a.total(2, 3, 2);
            breakTubeHolding(h, a, 3, 1, 3, PLUTONIUM);
            a.close(a.total(2, 3, 2), total, "the room's gas after a tube of plutonium broke");
            a.close(a.radiation(unmapped), a.stack(PLUTONIUM, 2_000).getRadioactivity(), "the plutonium's radiation");
        } finally {
            MekanismSettings.VENTING_MAX_PER_SECOND.set(cap);
            a.clear();
            a.scrub(mapped);
            a.scrub(capped);
            a.scrub(unmapped);
        }
        h.succeed();
    }

    /**
     * A gauge dropper emptying a tank is the other path where Mekanism radiates what it voids, just
     * before it empties the tank. The room gains the waste as gas, and the radiation at the tank is
     * that one application.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_023_a_gauge_dropper_dump_radiates_once_and_by_mekanism(GameTestHelper h) {
        VentArea a = new VentArea(h, 31);
        BlockPos pos = a.abs(2, 1, 2);
        try {
            assumeRadiation(h, a);
            a.room();
            TileEntityChemicalTank tank = a.place(2, 1, 2, TANK, TileEntityChemicalTank.class);
            a.form(2, 3, 2);
            force(h, tank, a, NUCLEAR_WASTE, 3_000);
            double before = a.moles(2, 3, 2, CARBON_DIOXIDE);

            a.dropperDump(a.player(1, 1, 1), pos);
            a.tick();
            h.assertTrue(tank.getChemicalTank().isEmpty(), "the tank after the dropper dumped it");
            a.close(a.moles(2, 3, 2, CARBON_DIOXIDE) - before, mol(3_000), "the gas the room gained from the waste");
            a.close(a.radiation(pos), a.stack(NUCLEAR_WASTE, 3_000).getRadioactivity(), "the radiation at the tank");
        } finally {
            a.clear();
            a.scrub(pos);
        }
        h.succeed();
    }

    /**
     * Where Mekanism voids silently, as a tank set to Dumping does, the venting applies the
     * radiation of what it releases, once, through Mekanism's radiation manager, so mapped waste
     * never reaches a room without it. What the cap leaves deleted, and what is deleted with the
     * venting switched off, is deleted as silently as Mekanism always did.
     *
     * <p>Such a tank refuses radioactive chemicals while Mekanism's radiation is on, so the waste is
     * put there past its check: it could only be there from a time radiation was off.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_023_a_silent_dump_radiates_once_for_what_it_releases(GameTestHelper h) {
        VentArea a = new VentArea(h, 32);
        BlockPos free = a.abs(1, 1, 1);
        BlockPos capped = a.abs(3, 1, 1);
        BlockPos off = a.abs(3, 1, 3);
        long cap = MekanismSettings.VENTING_MAX_PER_SECOND.get();
        TileEntityChemicalTank[] tanks = new TileEntityChemicalTank[3];
        try {
            assumeRadiation(h, a);
            a.room();
            tanks[0] = a.place(1, 1, 1, TANK, TileEntityChemicalTank.class);
            tanks[1] = a.place(3, 1, 1, TANK, TileEntityChemicalTank.class);
            tanks[2] = a.place(3, 1, 3, TANK, TileEntityChemicalTank.class);
            a.form(2, 3, 2);
            for (TileEntityChemicalTank tank : tanks) {
                force(h, tank, a, NUCLEAR_WASTE, 10_000);
                tank.dumping = GasMode.DUMPING;
            }
            long each = tanks[0].getTier().getStorage() / 400;
            double before = a.moles(2, 3, 2, CARBON_DIOXIDE);

            // Three ticks, three releases: the radiation of each, and no more.
            run(tanks[0], 3);
            a.tick();
            h.assertValueEqual(tanks[0].getChemicalTank().getStored(), 10_000 - 3 * each, "the first tank's waste");
            a.close(a.moles(2, 3, 2, CARBON_DIOXIDE) - before, mol(3 * each), "the gas the room gained");
            a.close(
                    a.radiation(free),
                    a.stack(NUCLEAR_WASTE, 3 * each).getRadioactivity(),
                    "the radiation at the first tank");

            // Under a cap of 300 mB, three ticks shed 480: 300 as gas with its radiation, 180 deleted.
            // No test runs between the changes and their undoing, since all are in this one call.
            MekanismSettings.VENTING_MAX_PER_SECOND.set(300L);
            run(tanks[1], 3);
            MekanismSettings.VENTING_MAX_PER_SECOND.set(cap);
            a.tick();
            h.assertValueEqual(tanks[1].getChemicalTank().getStored(), 10_000 - 3 * each, "the second tank's waste");
            a.close(a.moles(2, 3, 2, CARBON_DIOXIDE) - before, mol(3 * each + 300), "the gas with a capped tank's");
            a.close(a.radiation(capped), a.stack(NUCLEAR_WASTE, 300).getRadioactivity(), "the capped tank's radiation");

            // Switched off: nothing released, so nothing radiated, as Mekanism does by itself.
            MekanismSettings.VENTING_ENABLED.set(false);
            run(tanks[2], 3);
            MekanismSettings.VENTING_ENABLED.set(true);
            a.tick();
            h.assertValueEqual(tanks[2].getChemicalTank().getStored(), 10_000 - 3 * each, "the third tank's waste");
            a.close(a.moles(2, 3, 2, CARBON_DIOXIDE) - before, mol(3 * each + 300), "the gas with the venting off");
            a.close(a.radiation(off), 0, "the radiation at a tank dumping with the venting off");
        } finally {
            MekanismSettings.VENTING_ENABLED.set(true);
            MekanismSettings.VENTING_MAX_PER_SECOND.set(cap);
            // Emptied first: Mekanism radiates what a block holds when it is removed.
            for (TileEntityChemicalTank tank : tanks) {
                if (tank != null) {
                    tank.getChemicalTank().setEmpty();
                }
            }
            a.clear();
            a.scrub(free);
            a.scrub(capped);
            a.scrub(off);
        }
        h.succeed();
    }
}
