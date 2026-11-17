package io.github.west3436.zas.compat.nuclearcraft.gametest;

import igentuman.nr.api.shielding.ArmorProtectionRegistry;
import io.github.west3436.zas.atmos.fluid.FluidGases;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the adapter's data does with the content on: NuclearCraft's gas fluids are ZAS³'s gases
 * (COMPAT-152), and Nuclear Radiation counts ZAS³'s masks and hardsuit helmet as keeping its gas
 * out (COMPAT-155). Without the content, neither the fluid bridge nor the masks exist, and these
 * tests don't run.
 */
@GameTestHolder(NcContentTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class NcContentTests {
    /** The namespace of the tests that need the content. */
    public static final String NAMESPACE = "zas_nuclearcraft_content";

    private static final ResourceLocation WATER_VAPOR = NcGateTests.zas("water_vapor");

    private NcContentTests() {}

    private static Fluid fluid(GameTestHelper h, String path) {
        Fluid fluid = BuiltInRegistries.FLUID.get(Area.nc(path));
        h.assertTrue(fluid != Fluids.EMPTY, "NuclearCraft registers no fluid " + path);
        return fluid;
    }

    private static FluidGases.Mapped mapped(GameTestHelper h, String path) {
        FluidGases.Mapped mapped = FluidGases.mapping(fluid(h, path)).orElse(null);
        h.assertTrue(mapped != null, "nuclearcraft:" + path + " isn't mapped");
        return mapped;
    }

    private static List<Fluid> leaving(ResourceLocation gas, double temperature) {
        return FluidGases.leavingAs(gas, temperature).stream()
                .map(FluidGases.Mapped::fluid)
                .toList();
    }

    /**
     * Each of NuclearCraft's gas fluids, its own gases and those it shares with Mekanism, is the ZAS³
     * gas of its name; hot helium is helium.
     */
    @GameTest(template = "empty")
    public static void compat_152_nuclearcrafts_gas_fluids_are_zas_gases(GameTestHelper h) {
        for (String gas : NcGateTests.OWN_GASES) {
            h.assertValueEqual(mapped(h, gas).gas(), NcGateTests.zas(gas), "nuclearcraft:" + gas + "'s gas");
            h.assertTrue(
                    leaving(NcGateTests.zas(gas), 2000).contains(fluid(h, gas)),
                    "zas:" + gas + " can't leave as nuclearcraft:" + gas);
        }
        for (String gas : NcGateTests.SHARED_GASES) {
            h.assertValueEqual(mapped(h, gas).gas(), NcGateTests.zas(gas), "nuclearcraft:" + gas + "'s gas");
        }
        h.assertValueEqual(mapped(h, "hot_helium").gas(), NcGateTests.zas("helium"), "hot helium's gas");
        h.succeed();
    }

    /**
     * Each of NuclearCraft's steams is water vapour at its own temperature, but never below boiling.
     * Water vapour goes back into a tank as high-pressure steam once it is as hot as that is, and
     * never as spent steam. Hot helium is helium at its own temperature, and helium leaves as it
     * only once that hot.
     */
    @GameTest(template = "empty")
    public static void compat_152_steams_are_water_vapour_at_their_temperatures(GameTestHelper h) {
        for (String steam :
                List.of("steam", "high_pressure_steam", "exhaust_steam", "low_pressure_steam", "low_quality_steam")) {
            FluidGases.Mapped mapped = mapped(h, steam);
            h.assertValueEqual(mapped.gas(), WATER_VAPOR, steam + "'s gas");
            Fluid fluid = fluid(h, steam);
            double own = fluid.getFluidType().getTemperature();
            double at = mapped.gas(new FluidStack(fluid, 1000), 1000).temperature();
            h.assertValueEqual(at, Math.max(own, 373.15), steam + "'s gas temperature");
        }
        double hp = fluid(h, "high_pressure_steam").getFluidType().getTemperature();
        h.assertTrue(hp > 373.15, "high-pressure steam is " + hp + " K");
        List<Fluid> hot = leaving(WATER_VAPOR, hp);
        h.assertTrue(
                hot.contains(fluid(h, "high_pressure_steam")), "hot water vapour can't leave as high-pressure steam");
        for (String spent : List.of("exhaust_steam", "low_pressure_steam", "low_quality_steam")) {
            h.assertFalse(leaving(WATER_VAPOR, 5000).contains(fluid(h, spent)), "water vapour leaves as " + spent);
        }
        h.assertFalse(
                leaving(WATER_VAPOR, 373.15).contains(fluid(h, "high_pressure_steam")),
                "water vapour at boiling leaves as high-pressure steam");

        Fluid hotHelium = fluid(h, "hot_helium");
        double heliumHot = hotHelium.getFluidType().getTemperature();
        h.assertValueEqual(
                mapped(h, "hot_helium")
                        .gas(new FluidStack(hotHelium, 1000), 1000)
                        .temperature(),
                heliumHot,
                "hot helium's gas temperature");
        h.assertFalse(leaving(NcGateTests.zas("helium"), 300).contains(hotHelium), "cool helium leaves as hot helium");
        h.assertTrue(leaving(NcGateTests.zas("helium"), heliumHot).contains(hotHelium), "hot helium can't go back");
        h.succeed();
    }

    /**
     * Nuclear Radiation reads the content's data: ZAS³'s breath mask, gas mask and hardsuit helmet
     * keep its gas out, and give no shielding from its radiation.
     */
    @GameTest(template = "empty")
    public static void compat_155_nuclear_radiation_counts_zas_masks_as_gas_protection(GameTestHelper h) {
        for (Map.Entry<String, Boolean> piece : Map.of(
                        "breath_mask", true, "gas_mask", true, "hardsuit_helmet", true, "softsuit_helmet", false)
                .entrySet()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("zas_atmos", piece.getKey()));
            h.assertFalse(item == Items.AIR, "the content has no " + piece.getKey());
            ArmorProtectionRegistry.Protection protection = ArmorProtectionRegistry.get(new ItemStack(item));
            h.assertValueEqual(protection.protectsFromGas(), piece.getValue(), piece.getKey() + " keeps gas out");
            h.assertValueEqual(
                    protection.xray() + protection.alpha() + protection.beta() + protection.neutron(),
                    0.0,
                    piece.getKey() + "'s radiation shielding");
        }
        h.succeed();
    }
}
