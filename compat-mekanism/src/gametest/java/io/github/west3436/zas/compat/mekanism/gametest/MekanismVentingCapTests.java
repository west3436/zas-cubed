package io.github.west3436.zas.compat.mekanism.gametest;

import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.OXYGEN;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.mol;
import static io.github.west3436.zas.compat.mekanism.gametest.VentArea.run;

import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import io.github.west3436.zas.compat.mekanism.VentBudget;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.TileEntityChemicalTank.GasMode;
import mekanism.common.tile.factory.TileEntityItemStackChemicalToItemStackFactory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The venting's cap (MEK-021): each source releases at most so much in a second, a server setting,
 * and what is above it is deleted as Mekanism does, or released as well if a second setting says
 * so.
 *
 * <p>A test here runs within one game tick, so within one second of the cap's count, and changes
 * the settings back before it returns: no other test runs in between.
 */
@GameTestHolder(MekanismFoundationTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismVentingCapTests {
    private static final String MEK_OXYGEN = "mekanism:oxygen";
    private static final String TANK = "mekanism:basic_chemical_tank";

    private MekanismVentingCapTests() {}

    /**
     * Above the cap, what a source voids is deleted, by default. A tank that sheds 1,600 mB in a
     * second under a cap of 1,000 releases 1,000, whether the cap is reached within one tick's
     * amount or between two, and nothing more that second. Another source beside it has a cap of
     * its own, as has a dump button, whose one large amount is cut to the cap at once.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_021_above_the_cap_a_source_voids_the_rest_by_default(GameTestHelper h) {
        VentArea a = new VentArea(h, 20);
        long cap = MekanismSettings.VENTING_MAX_PER_SECOND.get();
        try {
            h.assertValueEqual(cap, VentBudget.DEFAULT_MAX_PER_SECOND, "the cap on a new server");
            h.assertValueEqual(MekanismSettings.ventingMaxPerSecond(), 20_000L, "the default cap, in mB a second");
            h.assertFalse(MekanismSettings.ventingReleasesAboveCap(), "what is above the cap is released by default");
            a.room();
            TileEntityChemicalTank first = a.place(1, 1, 1, TANK, TileEntityChemicalTank.class);
            TileEntityChemicalTank second = a.place(3, 1, 3, TANK, TileEntityChemicalTank.class);
            TileEntityItemStackChemicalToItemStackFactory factory = a.place(
                    3, 1, 1, "mekanism:basic_purifying_factory", TileEntityItemStackChemicalToItemStackFactory.class);
            a.form(2, 3, 2);
            first.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 10_000));
            second.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 10_000));
            factory.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 600));
            long each = first.getTier().getStorage() / 400;
            h.assertValueEqual(each, 160L, "what a basic tank sheds each tick");
            double before = a.moles(2, 3, 2, OXYGEN);

            // No test runs between the change and its undoing, since both are in this one call.
            MekanismSettings.VENTING_MAX_PER_SECOND.set(1_000L);

            // Ten ticks shed 1,600 mB. The seventh reaches the cap part-way: 40 of its 160 leave.
            first.dumping = GasMode.DUMPING;
            run(first, 10);
            a.tick();
            h.assertValueEqual(first.getChemicalTank().getStored(), 10_000 - 10 * each, "the first tank's oxygen");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(1_000), "the oxygen the room gained from the first tank");

            // The same second, the same tank: all of it is deleted now.
            run(first, 2);
            a.tick();
            h.assertValueEqual(
                    first.getChemicalTank().getStored(), 10_000 - 12 * each, "the first tank, still dumping");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(1_000), "the room's oxygen, the first tank's cap used up");

            // The tank beside it has used none of its own.
            second.dumping = GasMode.DUMPING;
            run(second, 2);
            a.tick();
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(1_000 + 2 * each), "the room's oxygen with a second source");

            // One large amount at once, from a third source, under a smaller cap.
            MekanismSettings.VENTING_MAX_PER_SECOND.set(250L);
            factory.dump();
            a.tick();
            h.assertTrue(factory.getChemicalTank().isEmpty(), "the factory's tank after its dump button");
            a.close(
                    a.moles(2, 3, 2, OXYGEN) - before,
                    mol(1_000 + 2 * each + 250),
                    "the room's oxygen with a dump button's 600 mB cut to 250");

            // A cap of nothing releases nothing.
            MekanismSettings.VENTING_MAX_PER_SECOND.set(0L);
            run(second, 3);
            a.tick();
            h.assertValueEqual(second.getChemicalTank().getStored(), 10_000 - 5 * each, "the second tank's oxygen");
            a.close(
                    a.moles(2, 3, 2, OXYGEN) - before,
                    mol(1_000 + 2 * each + 250),
                    "the room's oxygen under a cap of nothing");
        } finally {
            MekanismSettings.VENTING_MAX_PER_SECOND.set(cap);
            a.clear();
        }
        h.succeed();
    }

    /**
     * With {@code release_above_cap} on, a source releases all it voids, however far above the cap,
     * so nothing is deleted. Switched off again, the cap holds again from the next amount.
     */
    @GameTest(template = "empty", batch = VentArea.BATCH)
    public static void mek_021_with_release_above_cap_on_a_source_releases_all_of_it(GameTestHelper h) {
        VentArea a = new VentArea(h, 21);
        long cap = MekanismSettings.VENTING_MAX_PER_SECOND.get();
        try {
            a.room();
            TileEntityChemicalTank tank = a.place(2, 1, 2, TANK, TileEntityChemicalTank.class);
            a.form(2, 3, 2);
            tank.getChemicalTank().setStack(a.stack(MEK_OXYGEN, 10_000));
            tank.dumping = GasMode.DUMPING;
            long each = tank.getTier().getStorage() / 400;
            double before = a.moles(2, 3, 2, OXYGEN);

            // No test runs between the changes and their undoing, since all are in this one call.
            MekanismSettings.VENTING_MAX_PER_SECOND.set(1_000L);
            MekanismSettings.VENTING_RELEASE_ABOVE_CAP.set(true);
            run(tank, 10);
            a.tick();
            h.assertValueEqual(tank.getChemicalTank().getStored(), 10_000 - 10 * each, "the tank's oxygen");
            a.close(a.moles(2, 3, 2, OXYGEN) - before, mol(10 * each), "the oxygen the room gained, all 1,600 mB");

            // What was released above the cap wasn't counted against it: the cap holds from here.
            MekanismSettings.VENTING_RELEASE_ABOVE_CAP.set(false);
            run(tank, 10);
            a.tick();
            h.assertValueEqual(tank.getChemicalTank().getStored(), 10_000 - 20 * each, "the tank's oxygen");
            a.close(
                    a.moles(2, 3, 2, OXYGEN) - before,
                    mol(10 * each + 1_000),
                    "the room's oxygen, the second ten ticks under the cap");
        } finally {
            MekanismSettings.VENTING_RELEASE_ABOVE_CAP.set(VentBudget.DEFAULT_RELEASE_ABOVE_CAP);
            MekanismSettings.VENTING_MAX_PER_SECOND.set(cap);
            a.clear();
        }
        h.succeed();
    }
}
