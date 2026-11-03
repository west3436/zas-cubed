package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.geometry.GeometryOverride.Target;
import io.github.west3436.zas.ignition.IgnitionEvents;
import io.github.west3436.zas.ignition.IgnitionEvents.Strike;
import io.github.west3436.zas.ignition.IgnitionSource;
import io.github.west3436.zas.ignition.IgnitionSources;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ignition sources (FIRE-014, FIRE-015): the shipped definitions, a datapack adding and removing
 * sources, and the one-off sources reaching the engine.
 *
 * <p>Every strike sent to the engine is recorded in {@link #STRIKES}; each test looks only for the
 * positions in its own structure. Tests that change the server's sources run in a batch of their
 * own and put them back before they end.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IgnitionTests {
    private static final String OVERRIDES = "fire_015_overrides";
    private static final List<Strike> STRIKES = Collections.synchronizedList(new ArrayList<>());

    private static final ResourceLocation TORCH = id("torch");
    private static final ResourceLocation REDSTONE = id("redstone_spark");

    static {
        IgnitionEvents.observeForTest(STRIKES::add);
    }

    private IgnitionTests() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, path);
    }

    /** The strikes so far at {@code pos} in {@code level}. */
    private static List<Strike> strikesAt(ServerLevel level, BlockPos pos) {
        synchronized (STRIKES) {
            return STRIKES.stream()
                    .filter(s -> s.level() == level && s.pos().equals(pos))
                    .toList();
        }
    }

    private static void struck(GameTestHelper h, BlockPos pos, ResourceLocation source, String what) {
        List<Strike> at = strikesAt(h.getLevel(), pos);
        h.assertTrue(
                at.stream().anyMatch(s -> s.source().equals(source)),
                what + ": expected a strike by " + source + " at " + pos + ", got " + at);
    }

    private static void notStruck(GameTestHelper h, BlockPos pos, String what) {
        List<Strike> at = strikesAt(h.getLevel(), pos);
        h.assertTrue(at.isEmpty(), what + ": expected no strike at " + pos + ", got " + at);
    }

    // ---- FIRE-015: the definitions ------------------------------------------------------

    /**
     * The shipped standing sources are fire, lava, lit campfires and torches; an unlit campfire,
     * redstone and ordinary blocks aren't (FIRE-014, FIRE-015).
     */
    @GameTest(template = "empty")
    public static void fire_015_shipped_standing_sources(GameTestHelper h) {
        BlockState lit = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        List<BlockState> standing = List.of(
                Blocks.FIRE.defaultBlockState(),
                Blocks.SOUL_FIRE.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(),
                Blocks.LAVA_CAULDRON.defaultBlockState(),
                lit,
                Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true),
                Blocks.TORCH.defaultBlockState(),
                Blocks.WALL_TORCH.defaultBlockState(),
                Blocks.SOUL_TORCH.defaultBlockState(),
                Blocks.SOUL_WALL_TORCH.defaultBlockState());
        for (BlockState s : standing) {
            h.assertTrue(IgnitionSources.isStanding(s), s + " should be a standing ignition source");
        }
        List<BlockState> not = List.of(
                lit.setValue(CampfireBlock.LIT, false),
                Blocks.REDSTONE_TORCH.defaultBlockState(),
                Blocks.REDSTONE_WIRE.defaultBlockState(),
                Blocks.LEVER.defaultBlockState().setValue(LeverBlock.POWERED, true),
                Blocks.MAGMA_BLOCK.defaultBlockState(),
                Blocks.LANTERN.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                Blocks.STONE.defaultBlockState());
        for (BlockState s : not) {
            h.assertFalse(IgnitionSources.isStanding(s), s + " shouldn't be a standing ignition source");
        }
        IgnitionSource redstone = IgnitionSources.sources().get(REDSTONE);
        h.assertTrue(redstone != null && !redstone.enabled(), "the redstone spark should be shipped, off");
        h.succeed();
    }

    /**
     * A datapack adds a source and removes a shipped one: here a file making glowstone a source,
     * and one turning torches off (FIRE-015).
     */
    @GameTest(template = "empty", batch = OVERRIDES)
    public static void fire_015_datapack_adds_and_removes_sources(GameTestHelper h) {
        try {
            IgnitionSources.overrideForTest(Map.of(
                    TORCH,
                    IgnitionSource.disabled(),
                    id("test_glowstone"),
                    blocks("glowstone"),
                    id("test_furnace"),
                    blocks("furnace")));
            h.assertTrue(IgnitionSources.isStanding(Blocks.GLOWSTONE.defaultBlockState()), "added: glowstone");
            // A block with a lit property, named without a state, counts only while lit.
            BlockState furnace = Blocks.FURNACE.defaultBlockState();
            h.assertTrue(
                    IgnitionSources.isStanding(furnace.setValue(AbstractFurnaceBlock.LIT, true)), "added: lit furnace");
            h.assertFalse(
                    IgnitionSources.isStanding(furnace.setValue(AbstractFurnaceBlock.LIT, false)), "an unlit furnace");
            h.assertFalse(IgnitionSources.isStanding(Blocks.TORCH.defaultBlockState()), "removed: torch");
            h.assertFalse(IgnitionSources.isStanding(Blocks.WALL_TORCH.defaultBlockState()), "removed: wall torch");
            h.assertTrue(IgnitionSources.isStanding(Blocks.LAVA.defaultBlockState()), "kept: lava");
        } finally {
            IgnitionSources.overrideForTest(Map.of());
        }
        h.assertTrue(IgnitionSources.isStanding(Blocks.TORCH.defaultBlockState()), "torch back after the override");
        h.assertFalse(IgnitionSources.isStanding(Blocks.GLOWSTONE.defaultBlockState()), "glowstone gone again");
        h.succeed();
    }

    /** A block source naming these vanilla blocks, with no state. */
    private static IgnitionSource blocks(String... ids) {
        return new IgnitionSource(
                Optional.of(IgnitionSource.Type.BLOCK),
                true,
                java.util.Arrays.stream(ids)
                        .map(i -> new Target(ResourceLocation.withDefaultNamespace(i), false))
                        .toList(),
                Optional.empty(),
                List.of(),
                List.of());
    }

    /**
     * Blocks in {@code #zas:ignition_sources} are standing sources, those with a {@code lit}
     * property only while lit: here the GameTest pack's redstone ore and crying obsidian (API-053).
     */
    @GameTest(template = "empty")
    public static void api_053_tagged_blocks_are_standing_sources(GameTestHelper h) {
        BlockState ore = Blocks.REDSTONE_ORE.defaultBlockState();
        h.assertTrue(IgnitionSources.isStanding(ore.setValue(RedStoneOreBlock.LIT, true)), "lit redstone ore");
        h.assertFalse(IgnitionSources.isStanding(ore.setValue(RedStoneOreBlock.LIT, false)), "unlit redstone ore");
        h.assertTrue(IgnitionSources.isStanding(Blocks.CRYING_OBSIDIAN.defaultBlockState()), "crying obsidian");
        h.assertFalse(IgnitionSources.isStanding(Blocks.OBSIDIAN.defaultBlockState()), "obsidian, untagged");
        h.succeed();
    }

    /**
     * By default a lever thrown strikes nothing; with the redstone spark turned on by a datapack, it
     * strikes at the lever (FIRE-015).
     */
    @GameTest(template = "empty", batch = OVERRIDES)
    public static void fire_015_redstone_strikes_only_when_enabled(GameTestHelper h) {
        BlockPos off = new BlockPos(1, 2, 1);
        BlockPos on = new BlockPos(3, 2, 1);
        BlockState lever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR);
        h.setBlock(off.below(), Blocks.STONE);
        h.setBlock(on.below(), Blocks.STONE);
        h.setBlock(off, lever);
        h.setBlock(on, lever);
        // Off by default: throwing the first lever is no spark.
        h.setBlock(off, lever.setValue(LeverBlock.POWERED, true));
        h.runAfterDelay(1, () -> {
            notStruck(h, h.absolutePos(off), "a lever thrown with the redstone spark off");
            IgnitionSource shipped = IgnitionSources.sources().get(REDSTONE);
            IgnitionSources.overrideForTest(Map.of(
                    REDSTONE,
                    new IgnitionSource(shipped.type(), true, shipped.blocks(), shipped.state(), List.of(), List.of())));
            try {
                h.setBlock(on, lever.setValue(LeverBlock.POWERED, true));
            } finally {
                h.runAfterDelay(1, () -> {
                    IgnitionSources.overrideForTest(Map.of());
                    struck(h, h.absolutePos(on), REDSTONE, "a lever thrown with the redstone spark on");
                    h.succeed();
                });
            }
        });
    }

    // ---- FIRE-014: one-off sources ------------------------------------------------------

    /** Flint and steel used on a block's top strikes the cell above it (FIRE-014). */
    @GameTest(template = "empty")
    public static void fire_014_flint_and_steel_strikes_the_side_used(GameTestHelper h) {
        BlockPos floor = new BlockPos(1, 1, 1);
        h.setBlock(floor, Blocks.STONE);
        ServerLevel level = h.getLevel();
        FakePlayer player = TestPlayers.hurtable(level, "zas_fire_014_flint");
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, flint);
        BlockPos abs = h.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        player.gameMode.useItemOn(player, level, flint, InteractionHand.MAIN_HAND, hit);
        struck(h, abs.above(), id("flint_and_steel"), "flint and steel on a block's top");
        notStruck(h, abs, "flint and steel: the block used on");
        h.succeed();
    }

    /** Lightning strikes its cell (FIRE-014). */
    @GameTest(template = "empty")
    public static void fire_014_lightning_strikes_its_cell(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(h.getLevel());
        h.assertTrue(bolt != null, "a lightning bolt");
        bolt.moveTo(Vec3.atBottomCenterOf(at));
        bolt.setVisualOnly(true);
        h.getLevel().addFreshEntity(bolt);
        struck(h, at, id("lightning"), "lightning");
        h.succeed();
    }

    /**
     * An explosion strikes its centre; a burst that only triggers blocks, such as a wind charge's,
     * strikes nothing (FIRE-014).
     */
    @GameTest(template = "empty")
    public static void fire_014_explosion_strikes_its_centre(GameTestHelper h) {
        BlockPos blast = h.absolutePos(new BlockPos(1, 2, 1));
        BlockPos burst = h.absolutePos(new BlockPos(3, 2, 3));
        ServerLevel level = h.getLevel();
        Vec3 c = Vec3.atCenterOf(blast);
        level.explode(null, c.x, c.y, c.z, 0.5f, Level.ExplosionInteraction.NONE);
        Vec3 w = Vec3.atCenterOf(burst);
        level.explode(null, w.x, w.y, w.z, 0.5f, Level.ExplosionInteraction.TRIGGER);
        struck(h, blast, id("explosion"), "an explosion");
        notStruck(h, burst, "a wind burst");
        h.succeed();
    }
}
