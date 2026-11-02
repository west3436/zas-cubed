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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
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
     * out when its fuel runs out; its slot takes only coal and charcoal (CONT-061, CONT-066,
     * CONT-074).
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

    private static boolean lit(Site s) {
        BlockState state = s.level.getBlockState(s.abs(2, 1, 2));
        return state.is(AtmosBlocks.GENERATOR.get()) && state.getValue(GeneratorBlock.LIT);
    }

    /**
     * Under Hardcore, lighting the generator in a sealed room forms a zone there, as a lit furnace
     * does; unlit, or lit under Normal, it forms none (CORE-056, CONT-067). The generator is in
     * {@code #zas:combustion} and its {@code lit} states are the burning ones.
     */
    @GameTest(template = "empty", batch = "zas_atmos_core_056")
    public static void core_056_lit_generator_forms_a_zone_under_hardcore(GameTestHelper h) {
        Site s = new Site(h, 10);
        AtmospherePreset before = ZasServerConfig.preset();
        try {
            BlockState unlit = AtmosBlocks.GENERATOR.get().defaultBlockState();
            h.assertTrue(TriggerStates.isCombustion(unlit.setValue(GeneratorBlock.LIT, true)), "lit isn't combustion");
            h.assertFalse(TriggerStates.isCombustion(unlit), "unlit is combustion");
            s.clear();
            s.room(0, 4);
            s.room(6, 10);
            s.ticks(2);

            ZasServerConfig.setPreset(AtmospherePreset.NORMAL);
            GeneratorBlockEntity normal = generator(s, 8, 1, 2);
            normal.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            normal.serverTick();
            s.ticks(3);
            h.assertValueEqual(
                    Atmospheres.at(s.level, s.abs(8, 2, 2)).state(), AtmosphereState.AMBIENT, "lit under Normal");

            ZasServerConfig.setPreset(AtmospherePreset.HARDCORE);
            GeneratorBlockEntity g = generator(s, 2, 1, 2);
            s.ticks(3);
            h.assertValueEqual(
                    Atmospheres.at(s.level, s.abs(2, 2, 2)).state(), AtmosphereState.AMBIENT, "unlit under Hardcore");
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            g.serverTick();
            s.ticks(3);
            h.assertTrue(s.zoned(2, 2, 2), "lit under Hardcore formed no zone");
            h.assertValueEqual(s.zone(2, 2, 2).cellCount(), 27, "cells of the lit generator's room");
            h.succeed();
        } finally {
            ZasServerConfig.setPreset(before);
            s.clear();
        }
    }
}
