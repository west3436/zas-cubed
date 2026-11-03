package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.FireExtinguisherItem;
import io.github.west3436.zas.gametest.TestPlayers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fire extinguisher (CONT-037). Each test builds on a stone floor in its own site, places a
 * survival test player holding an extinguisher and aims it, then uses it as a held right click does:
 * {@code use} starts the use and each of the item's use ticks is called as the player's tick would.
 * Everything happens within one game tick, so no fire ticks in between.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExtinguisherTests {
    private static final String BATCH = "zas_atmos_extinguisher";
    private static final BlockState FIRE = Blocks.FIRE.defaultBlockState();

    private ExtinguisherTests() {}

    /** Clears the site and lays a stone floor at y = 0 under all of it. */
    private static void floor(Site s) {
        s.clear();
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 6; z++) {
                s.set(x, 0, z, Site.STONE);
            }
        }
    }

    /** A test player standing in cell (x, 1, z), holding a full extinguisher and aiming at {@code target}. */
    private static FakePlayer holder(Site s, String name, int x, int z, Vec3 target) {
        FakePlayer p = TestPlayers.hurtable(s.level, name);
        p.moveTo(Vec3.atBottomCenterOf(s.abs(x, 1, z)));
        p.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.FIRE_EXTINGUISHER.get()));
        return p;
    }

    /** One spray tick of the player's current use. */
    private static void sprayTick(FakePlayer p) {
        p.getUseItem().onUseTick(p.level(), p, p.getUseItemRemainingTicks());
    }

    private static boolean fire(Site s, int x, int y, int z) {
        return s.level.getBlockState(s.abs(x, y, z)).is(Blocks.FIRE);
    }

    /**
     * Held use sprays: the first spray puts out a fire on the floor 3 blocks in front of the player
     * it is aimed at, and uses one charge, while a fire in the same line 7 blocks out, beyond the
     * 5-block reach, keeps burning. In a second lane a glass wall 2 blocks out stops the spray
     * before a fire 4 blocks out.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_037_extinguisher_puts_out_fire_it_is_aimed_at(GameTestHelper h) {
        Site s = new Site(h, 70);
        try {
            floor(s);
            s.set(4, 1, 2, FIRE);
            s.set(8, 1, 2, FIRE);
            for (int y = 1; y <= 4; y++) {
                for (int z = 4; z <= 6; z++) {
                    s.set(3, y, z, Blocks.GLASS.defaultBlockState());
                }
            }
            s.set(5, 1, 5, FIRE);
            h.assertTrue(fire(s, 4, 1, 2) && fire(s, 8, 1, 2) && fire(s, 5, 1, 5), "the fires didn't light");

            FakePlayer p = holder(s, "zas_cont_037", 1, 2, Vec3.atBottomCenterOf(s.abs(4, 1, 2)));
            ItemStack stack = p.getMainHandItem();
            h.assertValueEqual(
                    FireExtinguisherItem.charges(stack), FireExtinguisherItem.CHARGES, "a new one's charges");
            InteractionResultHolder<ItemStack> result = stack.use(s.level, p, InteractionHand.MAIN_HAND);
            h.assertTrue(result.getResult().consumesAction(), "use didn't start a spray: " + result.getResult());
            h.assertTrue(p.isUsingItem(), "the player isn't using the extinguisher");
            sprayTick(p);
            h.assertFalse(fire(s, 4, 1, 2), "the fire it was aimed at still burns");
            h.assertTrue(fire(s, 8, 1, 2), "the fire beyond its reach went out");
            h.assertValueEqual(
                    FireExtinguisherItem.charges(stack), FireExtinguisherItem.CHARGES - 1, "charges after a spray");

            FakePlayer behind = holder(s, "zas_cont_037_wall", 1, 5, Vec3.atBottomCenterOf(s.abs(5, 1, 5)));
            behind.getMainHandItem().use(s.level, behind, InteractionHand.MAIN_HAND);
            sprayTick(behind);
            h.assertTrue(fire(s, 5, 1, 5), "the spray went through the glass");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * Aimed straight at a one-block stone wall 2.5 blocks from the eye, level and due east, the spray
     * stops at the wall: a fire on the floor right behind it keeps burning. The look is exact, so the
     * line meets the wall's face at a sample point, which is the case where a box around the wall's
     * own cell would reach behind it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_037_extinguisher_stops_at_a_wall(GameTestHelper h) {
        Site s = new Site(h, 73);
        try {
            floor(s);
            for (int y = 1; y <= 4; y++) {
                for (int z = 0; z <= 6; z++) {
                    s.set(4, y, z, Site.STONE);
                }
            }
            s.set(5, 1, 2, FIRE);
            h.assertTrue(fire(s, 5, 1, 2), "the fire didn't light");

            // Due east and level, set as angles so the look is exactly +x.
            FakePlayer p = holder(s, "zas_cont_037_wall_hit", 1, 2, Vec3.ZERO);
            p.setYRot(-90);
            p.setXRot(0);
            ItemStack stack = p.getMainHandItem();
            stack.use(s.level, p, InteractionHand.MAIN_HAND);
            h.assertTrue(p.isUsingItem(), "the player isn't using the extinguisher");
            sprayTick(p);
            h.assertValueEqual(
                    FireExtinguisherItem.charges(stack), FireExtinguisherItem.CHARGES - 1, "charges after a spray");
            h.assertTrue(fire(s, 5, 1, 2), "the spray reached the fire behind the wall");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * It sprays every 4 ticks of a held use, a charge each; when the last charge is gone the use
     * stops with the next spray, and using it again does nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_037_extinguisher_runs_dry(GameTestHelper h) {
        Site s = new Site(h, 71);
        try {
            floor(s);
            FakePlayer p = holder(s, "zas_cont_037_dry", 1, 2, Vec3.atCenterOf(s.abs(6, 2, 2)));
            ItemStack stack = p.getMainHandItem();
            FireExtinguisherItem.setCharges(stack, 2);
            stack.use(s.level, p, InteractionHand.MAIN_HAND);
            int start = p.getUseItemRemainingTicks();
            for (int t = 0; t < FireExtinguisherItem.SPRAY_INTERVAL; t++) {
                p.getUseItem().onUseTick(s.level, p, start - t);
            }
            h.assertValueEqual(FireExtinguisherItem.charges(stack), 1, "charges after one spray interval");
            p.getUseItem().onUseTick(s.level, p, start - FireExtinguisherItem.SPRAY_INTERVAL);
            h.assertValueEqual(FireExtinguisherItem.charges(stack), 0, "charges after two sprays");
            h.assertTrue(p.isUsingItem(), "it stopped before it was dry");
            p.getUseItem().onUseTick(s.level, p, start - 2 * FireExtinguisherItem.SPRAY_INTERVAL);
            h.assertFalse(p.isUsingItem(), "it kept spraying while dry");
            InteractionResultHolder<ItemStack> again = stack.use(s.level, p, InteractionHand.MAIN_HAND);
            h.assertFalse(again.getResult().consumesAction(), "an empty extinguisher started a spray");
            h.assertFalse(p.isUsingItem(), "an empty extinguisher is in use");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /** Used on a water source block, a part-used extinguisher fills to full and the water stays. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_037_extinguisher_refills_from_water(GameTestHelper h) {
        Site s = new Site(h, 72);
        try {
            floor(s);
            s.set(4, 0, 2, Blocks.WATER.defaultBlockState());
            FakePlayer p =
                    holder(s, "zas_cont_037_refill", 1, 2, s.point(4, 0, 2).add(0, 0.2, 0));
            ItemStack stack = p.getMainHandItem();
            FireExtinguisherItem.setCharges(stack, 3);
            stack.use(s.level, p, InteractionHand.MAIN_HAND);
            h.assertValueEqual(
                    FireExtinguisherItem.charges(stack), FireExtinguisherItem.CHARGES, "charges after a refill");
            h.assertFalse(p.isUsingItem(), "refilling started a spray");
            h.assertTrue(s.level.getFluidState(s.abs(4, 0, 2)).isSource(), "the water source went with the refill");
            h.succeed();
        } finally {
            s.clear();
        }
    }
}
