package io.github.west3436.zas.compat.adastra.gametest;

import earth.terrarium.adastra.api.systems.OxygenApi;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ad Astra's per-block environment effects stay out of zones (SPACE-014), and its torches can be
 * placed in a zone whose air lets fire burn (SPACE-015).
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class EnvironmentTests {
    /**
     * Chunk ticks enough for Ad Astra's environment tick to reach every block of a section: it picks
     * 20 of the section's 4,096 positions a tick, so the chance it misses one in 4,000 ticks is e⁻¹⁹.
     */
    private static final int ENOUGH = 4000;

    private EnvironmentTests() {}

    /**
     * Fire, water and plants in a pressurised room on the Moon, and the same in the open beside it,
     * through 4,000 of Ad Astra's environment ticks. In the open Ad Astra does what it does alone:
     * at the Moon's −173 °C it freezes the water, and at a temperate position without oxygen it
     * breaks the torch and the fire, kills the grass and puts the campfire out. In the room, where
     * Ad Astra by itself sees the same lack of oxygen, all of them are left as they are, the grass
     * of the floor included: a zone touches them, and ZAS³'s own rules govern there.
     */
    @GameTest(template = "empty")
    public static void space_014_ad_astras_environment_effects_stay_out_of_a_zone(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 30)) {
            lab.room(0, Lab.AIR);
            BlockState water = Blocks.WATER.defaultBlockState();
            BlockState torch = Blocks.TORCH.defaultBlockState();
            BlockState fire = Blocks.FIRE.defaultBlockState();
            BlockState campfire = Blocks.CAMPFIRE.defaultBlockState();
            BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
            BlockState sapling = Blocks.OAK_SAPLING.defaultBlockState();
            BlockState stone = Blocks.STONE.defaultBlockState();

            // In the room: water, then a sapling on a grass block of the floor, a torch, a fire and a campfire.
            lab.set(1, 1, 1, water);
            lab.set(2, 0, 2, grass);
            lab.set(2, 1, 2, sapling);
            lab.set(3, 1, 3, torch);
            lab.set(3, 1, 1, fire);
            lab.set(1, 1, 3, campfire);
            // In the open, on a stone floor two blocks clear of the room: the same.
            for (BlockPos p : BlockPos.betweenClosed(lab.abs(7, 0, 0), lab.abs(15, 0, 4))) {
                lab.level.setBlockAndUpdate(p, stone);
            }
            lab.set(8, 1, 1, water);
            lab.set(10, 0, 2, grass);
            lab.set(12, 1, 3, torch);
            lab.set(12, 1, 1, fire);
            lab.set(14, 1, 3, campfire);
            // Ad Astra acts on water by the Moon's cold. On the rest it acts only at a temperate
            // position, as a planet between −20 and 70 °C would be all over.
            lab.temperature(
                    20,
                    lab.abs(2, 0, 2),
                    lab.abs(2, 1, 2),
                    lab.abs(3, 1, 3),
                    lab.abs(3, 1, 1),
                    lab.abs(1, 1, 3),
                    lab.abs(10, 0, 2),
                    lab.abs(12, 1, 3),
                    lab.abs(12, 1, 1),
                    lab.abs(14, 1, 3));
            h.assertFalse(OxygenApi.API.hasOxygen(lab.level, lab.abs(3, 1, 3)), "Ad Astra found oxygen in the room");

            lab.tickChunk(ENOUGH);

            h.assertValueEqual(state(lab, 8, 1, 1).getBlock(), Blocks.ICE, "the water in the open");
            h.assertValueEqual(state(lab, 10, 0, 2).getBlock(), Blocks.DIRT, "the grass in the open");
            h.assertTrue(state(lab, 12, 1, 3).isAir(), "the torch in the open is still there");
            h.assertTrue(state(lab, 12, 1, 1).isAir(), "the fire in the open is still there");
            h.assertFalse(state(lab, 14, 1, 3).getValue(CampfireBlock.LIT), "the campfire in the open is still lit");

            h.assertValueEqual(state(lab, 1, 1, 1), water, "the water in the room");
            h.assertValueEqual(state(lab, 2, 0, 2), grass, "the grass of the room's floor");
            h.assertValueEqual(state(lab, 2, 1, 2), sapling, "the sapling in the room");
            h.assertValueEqual(state(lab, 3, 1, 3), torch, "the torch in the room");
            h.assertValueEqual(state(lab, 3, 1, 1).getBlock(), Blocks.FIRE, "the fire in the room");
            h.assertValueEqual(state(lab, 1, 1, 3), campfire, "the campfire in the room");
        }
        h.succeed();
    }

    /**
     * A player places a torch and a lantern in a room of air on the Moon, where Ad Astra by itself
     * finds no oxygen and refuses them. In a room of nitrogen, where fire can't burn, and in the
     * open, the torch is still refused. Afterwards Ad Astra finds no more oxygen at the torch than
     * it did before.
     */
    @GameTest(template = "empty")
    public static void space_015_a_torch_places_in_a_zone_fire_can_burn_in_and_not_in_vacuum(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 31)) {
            lab.room(0, Lab.AIR);
            lab.room(8, Map.of(Lab.N2, 101.325));
            lab.set(2, 8, 2, Blocks.STONE.defaultBlockState());
            FakePlayer player = lab.player(lab.floor(0));

            BlockState inAir = lab.place(player, new ItemStack(Items.TORCH), lab.abs(2, 0, 2), Direction.UP);
            h.assertValueEqual(inAir.getBlock(), Blocks.TORCH, "placed in the room of air");
            h.assertFalse(
                    OxygenApi.API.hasOxygen(lab.level, lab.abs(2, 1, 2)),
                    "Ad Astra was left finding oxygen where the torch went");
            BlockState lantern = lab.place(player, new ItemStack(Items.LANTERN), lab.abs(1, 0, 1), Direction.UP);
            h.assertValueEqual(lantern.getBlock(), Blocks.LANTERN, "a lantern placed in the room of air");

            BlockState inNitrogen = lab.place(player, new ItemStack(Items.TORCH), lab.abs(10, 0, 2), Direction.UP);
            h.assertTrue(inNitrogen.isAir(), "a torch went into a room of nitrogen: " + inNitrogen);
            BlockState inVacuum = lab.place(player, new ItemStack(Items.TORCH), lab.abs(2, 8, 2), Direction.UP);
            h.assertTrue(inVacuum.isAir(), "a torch went into the open on the Moon: " + inVacuum);
        }
        h.succeed();
    }

    /**
     * The oxygen the adapter lends Ad Astra for a placement comes back even when the placement event
     * never ends, here because another mod's listener throws: by the next tick Ad Astra finds none
     * at the position again, so nothing of it reaches Ad Astra's saved data.
     */
    @GameTest(template = "empty")
    public static void space_015_oxygen_lent_for_a_placement_that_never_ends_is_taken_back(GameTestHelper h) {
        Consumer<BlockEvent.EntityPlaceEvent> faulty = event -> {
            throw new IllegalStateException("a GameTest's faulty placement listener");
        };
        ServerLevel moon;
        BlockPos torch;
        try (Lab lab = Lab.onTheMoon(h, 32)) {
            lab.room(0, Lab.AIR);
            FakePlayer player = lab.player(lab.floor(0));
            moon = lab.level;
            torch = lab.abs(2, 1, 2);
            boolean threw = false;
            NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, faulty);
            try {
                lab.place(player, new ItemStack(Items.TORCH), lab.abs(2, 0, 2), Direction.UP);
            } catch (IllegalStateException expected) {
                threw = true;
            } finally {
                NeoForge.EVENT_BUS.unregister(faulty);
            }
            h.assertTrue(threw, "the faulty listener didn't stop the placement event");
            h.assertTrue(OxygenApi.API.hasOxygen(moon, torch), "nothing was left lent, so this shows nothing");
        }
        h.runAfterDelay(1, () -> {
            h.assertFalse(OxygenApi.API.hasOxygen(moon, torch), "Ad Astra still finds oxygen where the event threw");
            h.succeed();
        });
    }

    private static BlockState state(Lab lab, int x, int y, int z) {
        return lab.level.getBlockState(lab.abs(x, y, z));
    }
}
