package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.power.GeneratorBlock;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.power.GeneratorMenu;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.change.TriggerStates;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The generator in a real level (CONT-061, CONT-066, CONT-067, CORE-056). The burn is driven by
 * calling the generator's game tick directly, since a block entity in a test area far from any
 * player doesn't tick on its own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GeneratorTests {
    private GeneratorTests() {}

    private static GeneratorBlockEntity generator(Site s, int x, int y, int z) {
        return s.place(x, y, z, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
    }

    /**
     * The generator makes 40 FE/t while it burns, one coal lasts 80 s (1,600 game ticks), and it goes
     * out when its fuel runs out; its slot takes fuel and not dirt (CONT-061, CONT-066, CONT-074).
     */
    @GameTest(template = "empty", batch = "zas_atmos_controller")
    public static void cont_061_generator_makes_40_fe_per_tick_and_a_coal_lasts_80_s(GameTestHelper h) {
        Site s = new Site(h, 9);
        try {
            s.clear();
            GeneratorBlockEntity g = generator(s, 2, 1, 2);
            h.assertTrue(g.fuel().insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1, "dirt is fuel");
            h.assertTrue(
                    g.fuel().insertItem(0, new ItemStack(Items.CHARCOAL), true).isEmpty(), "charcoal isn't fuel");
            g.fuel().insertItem(0, new ItemStack(Items.COAL, 2), false);
            g.serverTick();
            h.assertTrue(lit(s), "unlit with coal in it");
            h.assertValueEqual(g.fuel().getStackInSlot(0).getCount(), 1, "coal after lighting");
            int before = g.energy().getEnergyStored();
            for (int i = 0; i < 20; i++) {
                g.serverTick();
            }
            h.assertValueEqual(g.energy().getEnergyStored() - before, 800, "FE made in 20 game ticks");

            // Run the first coal out, drawing the FE off so the store never fills.
            for (int t = 22; t <= 1600; t++) {
                g.energy().extractEnergy(Integer.MAX_VALUE, false);
                g.serverTick();
            }
            h.assertValueEqual(g.fuel().getStackInSlot(0).getCount(), 1, "the second coal started before 80 s");
            h.assertValueEqual(g.burnLeft(), 1, "game ticks left at 80 s less one");
            g.energy().extractEnergy(Integer.MAX_VALUE, false);
            g.serverTick();
            h.assertTrue(g.fuel().getStackInSlot(0).isEmpty(), "the second coal didn't start at 80 s");
            h.assertTrue(lit(s), "went out between coals");
            for (int t = 0; t < 1600; t++) {
                g.serverTick();
            }
            h.assertFalse(lit(s), "still lit with no fuel");
            h.assertValueEqual(g.energy().getEnergyStored(), g.energy().getMaxEnergyStored(), "the store isn't full");

            // A full store takes no new fuel.
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            g.serverTick();
            h.assertFalse(lit(s), "lit with a full store");

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            GeneratorMenu menu = new GeneratorMenu(0, player.getInventory(), g, new SimpleContainerData(4));
            h.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)), "the menu's slot takes dirt");
            h.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.CHARCOAL)), "the menu's slot refuses charcoal");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * Any furnace fuel burns, for 80 s times its furnace burn time over coal's: a coal block 800 s, a
     * stick 5 s. A lava bucket isn't fuel, since the slot has nowhere for its empty bucket: the slot
     * refuses it, and one put there by other means isn't burnt (CONT-066, d-155-fuel).
     */
    @GameTest(template = "empty", batch = "zas_atmos_controller")
    public static void cont_066_generator_burns_any_furnace_fuel_by_its_burn_time(GameTestHelper h) {
        Site s = new Site(h, 14);
        try {
            s.clear();
            for (var item : List.of(
                    Items.COAL,
                    Items.CHARCOAL,
                    Items.COAL_BLOCK,
                    Items.STICK,
                    Items.OAK_PLANKS,
                    Items.BLAZE_ROD,
                    Items.DRIED_KELP_BLOCK)) {
                h.assertTrue(GeneratorBlockEntity.isFuel(new ItemStack(item)), item + " isn't fuel");
            }
            for (var item : List.of(Items.LAVA_BUCKET, Items.BUCKET, Items.DIRT, Items.AIR)) {
                h.assertFalse(GeneratorBlockEntity.isFuel(new ItemStack(item)), item + " is fuel");
            }
            h.assertValueEqual(GeneratorBlockEntity.burnTicks(new ItemStack(Items.COAL)), 1600, "a coal's ticks");
            h.assertValueEqual(
                    GeneratorBlockEntity.burnTicks(new ItemStack(Items.COAL_BLOCK)), 16_000, "a coal block's ticks");
            h.assertValueEqual(GeneratorBlockEntity.burnTicks(new ItemStack(Items.STICK)), 100, "a stick's ticks");
            h.assertValueEqual(
                    GeneratorBlockEntity.burnTicks(new ItemStack(Items.BLAZE_ROD)), 2400, "a blaze rod's ticks");

            GeneratorBlockEntity g = generator(s, 2, 1, 2);
            g.fuel().insertItem(0, new ItemStack(Items.COAL_BLOCK), false);
            g.serverTick();
            h.assertTrue(lit(s), "unlit with a coal block in it");
            h.assertValueEqual(g.burnLeft(), 16_000, "a coal block burns for 800 s");
            for (int t = 0; t < 16_000; t++) {
                g.energy().extractEnergy(Integer.MAX_VALUE, false);
                g.serverTick();
            }
            h.assertFalse(lit(s), "still lit after 800 s");

            g.fuel().insertItem(0, new ItemStack(Items.STICK), false);
            g.serverTick();
            h.assertValueEqual(g.burnLeft(), 100, "a stick burns for 5 s");
            for (int t = 0; t < 100; t++) {
                g.energy().extractEnergy(Integer.MAX_VALUE, false);
                g.serverTick();
            }
            h.assertFalse(lit(s), "still lit after a stick's 5 s");

            h.assertValueEqual(
                    g.fuel()
                            .insertItem(0, new ItemStack(Items.LAVA_BUCKET), false)
                            .getCount(),
                    1,
                    "the slot took a lava bucket");
            g.fuel().setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));
            g.serverTick();
            h.assertFalse(lit(s), "burnt a lava bucket");
            h.assertTrue(g.fuel().getStackInSlot(0).is(Items.LAVA_BUCKET), "the lava bucket went");

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            GeneratorMenu menu = new GeneratorMenu(0, player.getInventory(), g, new SimpleContainerData(4));
            h.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.OAK_PLANKS)), "the menu's slot refuses planks");
            h.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.LAVA_BUCKET)), "the menu's slot takes lava");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * A running generator never touches the air (D-35, CONT-067): in a sealed zone it uses no O₂,
     * makes no CO₂ and adds no heat, and the burning step doesn't track it. Conduction is off, so
     * nothing else moves the temperature.
     */
    @GameTest(template = "empty", batch = "zas_atmos_controller")
    public static void cont_067_running_generator_changes_no_gas(GameTestHelper h) {
        Site s = new Site(h, 60);
        ZasServerConfig.overrideHeatForTest(io.github.west3436.zas.engine.heat.HeatSettings.OFF);
        try {
            s.clear();
            s.room(0, 4);
            GeneratorBlockEntity g = generator(s, 2, 1, 2);
            // Forms the room's zone, which takes in the ambient air, with some more O2.
            s.settle(io.github.west3436.zas.api.mutation.GasMutations.add(
                    s.level,
                    s.point(2, 2, 2),
                    new io.github.west3436.zas.api.gas.GasParcel(
                            java.util.Map.of(io.github.west3436.zas.api.gas.StandardAir.OXYGEN, 10.0), 293.15),
                    Site.SOURCE));
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            g.serverTick();
            h.assertTrue(lit(s), "unlit with coal in it");
            h.assertFalse(
                    io.github.west3436.zas.fire.Burners.tracks(s.level, s.abs(2, 1, 2)), "the burning step tracks it");
            var before = s.zone(2, 2, 2);
            var sent = io.github.west3436.zas.fire.Burners.burn(s.level, 10, p -> p.equals(s.abs(2, 1, 2)));
            h.assertTrue(sent.isEmpty(), "the generator burned the zone's O2");
            for (int i = 0; i < 3; i++) {
                for (int t = 0; t < 20; t++) {
                    g.serverTick();
                }
                s.tick();
            }
            h.assertTrue(lit(s), "the generator went out");
            var after = s.zone(2, 2, 2);
            h.assertValueEqual(after.mixture().moles(), before.mixture().moles(), "the zone's gas");
            s.close(after.mixture().temperature(), before.mixture().temperature(), 1e-9, "the zone's temperature");
            h.succeed();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
            s.clear();
        }
    }

    /**
     * A generator runs in a vacuum (D-35, CONT-067): in the End with the test datapack's vacuum
     * ambient, a coal lights it and it makes 40 FE per game tick.
     */
    @GameTest(template = "empty", batch = "zas_atmos_controller")
    public static void cont_067_generator_runs_in_a_vacuum(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum")));
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Site s = new Site(h, end, 61);
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            s.clear();
            s.set(2, 0, 2, Site.STONE);
            GeneratorBlockEntity g = generator(s, 2, 1, 2);
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            g.serverTick();
            h.assertTrue(lit(s), "unlit with coal in it, in a vacuum");
            int left = g.burnLeft();
            int energy = g.energy().getEnergyStored();
            for (int t = 0; t < 20; t++) {
                g.serverTick();
            }
            h.assertValueEqual(g.burnLeft(), left - 20, "burn time in a vacuum");
            h.assertValueEqual(g.energy().getEnergyStored(), energy + 20 * 40, "FE made in a vacuum");
            h.assertTrue(lit(s), "the generator went out in a vacuum");
            h.succeed();
        } finally {
            s.clear();
            Ambients.overrideForTest(Level.END, null);
        }
    }

    private static boolean lit(Site s) {
        BlockState state = s.level.getBlockState(s.abs(2, 1, 2));
        return state.is(AtmosBlocks.GENERATOR.get()) && state.getValue(GeneratorBlock.LIT);
    }

    /**
     * Lighting the generator in a sealed room forms no zone, even under Hardcore (CORE-056,
     * CONT-067): it never touches the air, so it isn't in {@code #zas:combustion}.
     */
    @GameTest(template = "empty", batch = "zas_atmos_core_056")
    public static void core_056_lit_generator_forms_no_zone_under_hardcore(GameTestHelper h) {
        Site s = new Site(h, 10);
        AtmospherePreset before = ZasServerConfig.preset();
        try {
            BlockState unlit = AtmosBlocks.GENERATOR.get().defaultBlockState();
            h.assertFalse(TriggerStates.isCombustion(unlit.setValue(GeneratorBlock.LIT, true)), "lit is combustion");
            h.assertFalse(TriggerStates.isCombustion(unlit), "unlit is combustion");
            s.clear();
            s.room(0, 4);
            s.ticks(2);

            ZasServerConfig.setPreset(AtmospherePreset.HARDCORE);
            GeneratorBlockEntity g = generator(s, 2, 1, 2);
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            g.serverTick();
            h.assertTrue(lit(s), "unlit with coal in it");
            s.ticks(3);
            h.assertValueEqual(
                    Atmospheres.at(s.level, s.abs(2, 2, 2)).state(), AtmosphereState.AMBIENT, "lit under Hardcore");
            h.succeed();
        } finally {
            ZasServerConfig.setPreset(before);
            s.clear();
        }
    }
}
