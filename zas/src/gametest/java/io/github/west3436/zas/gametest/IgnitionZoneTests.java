package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryOverride.Target;
import io.github.west3436.zas.hotspot.HotspotBlocks;
import io.github.west3436.zas.ignition.IgnitionSource;
import io.github.west3436.zas.ignition.IgnitionSources;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
 * Ignition sources end to end (FIRE-014, FIRE-015, API-053): each lights a flammable zone, through
 * the geometry table's flag for standing sources and the engine's ignite call for one-off ones.
 *
 * <p>Each case is a sealed 3 × 3 × 3 stone room of 10 % hydrogen in air at 20 °C, below
 * hydrogen's autoignition point, built with {@code HotspotTests}' helpers in an area of the test's
 * own; a room has caught fire once its zone holds water vapour. Each test leaves its rooms with
 * nothing to burn. Tests that change the server's sources run in a batch of their own and put them
 * back, and the geometry table with them, before they end.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IgnitionZoneTests {
    private static final String OVERRIDES = "fire_015_zone_overrides";
    private static final ResourceLocation TORCH = id("torch");
    private static final ResourceLocation REDSTONE = id("redstone_spark");

    /** Atmos ticks a room gets to catch fire. */
    private static final int TICKS = 6;

    private IgnitionZoneTests() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, path);
    }

    /** The test's own area, apart from every other test's; chunk-aligned. */
    private static Rooms rooms(GameTestHelper h, int index) {
        return new Rooms(h, h.getLevel(), new BlockPos(12288 + 256 * index, 100, -14336));
    }

    /** Where a case acts: room {@code x0}'s middle column, x0 + 2, z 2. */
    private record Spot(Rooms r, int x0) {
        BlockPos at(int dx, int y, int dz) {
            return r.abs(x0 + 2 + dx, y, 2 + dz);
        }

        /** Sets the block on the middle of the floor. */
        void floor(BlockState state) {
            r.level.setBlockAndUpdate(at(0, 1, 0), state);
        }

        /** Sets the middle floor block itself, with stone under it so the room stays sealed. */
        void under(BlockState state) {
            r.level.setBlockAndUpdate(at(0, -1, 0), Rooms.STONE);
            r.level.setBlockAndUpdate(at(0, 0, 0), state);
        }
    }

    /** One room: its name, and what to do in it once it is flammable. */
    private record Case(String name, Consumer<Spot> light) {}

    /** The flammable room at area x {@code 6 * i}: interior x + 1 to x + 3. */
    private static int x0(int i) {
        return 6 * i;
    }

    private static void build(Rooms r, int rooms) {
        r.clear(0, 6 * rooms);
        for (int i = 0; i < rooms; i++) {
            HotspotTests.hydrogenRoom(r, x0(i));
        }
    }

    private static boolean burning(Rooms r, int i) {
        ZoneInfo z = r.zone(x0(i) + 2, 3, 2);
        return z.mixture().moles(HotspotTests.H2O) > 0;
    }

    /** Ticks until every room burns, at most {@link #TICKS} times. */
    private static void tickUntilBurning(Rooms r, int rooms) {
        for (int t = 0; t < TICKS; t++) {
            boolean all = true;
            for (int i = 0; i < rooms; i++) {
                all &= burning(r, i);
            }
            if (all) {
                return;
            }
            r.tick();
        }
    }

    private static void emptyAll(Rooms r, int rooms) {
        for (int i = 0; i < rooms; i++) {
            HotspotTests.empty(r, x0(i));
        }
    }

    /** Builds a room per case, lights each, and checks each burns as expected. */
    private static void run(GameTestHelper h, Rooms r, List<Case> cases, boolean expectBurning) {
        build(r, cases.size());
        for (int i = 0; i < cases.size(); i++) {
            cases.get(i).light().accept(new Spot(r, x0(i)));
        }
        tickUntilBurning(r, cases.size());
        for (int i = 0; i < cases.size(); i++) {
            h.assertValueEqual(
                    burning(r, i), expectBurning, cases.get(i).name() + (expectBurning ? " lit" : " didn't light"));
        }
        emptyAll(r, cases.size());
    }

    // ---- FIRE-014: the default sources ----------------------------------------------------

    /**
     * FIRE-014: a torch, a soul torch, a lit campfire, fire and lava each ignite the flammable zone
     * they are in; an unlit campfire, a lantern and a room with nothing in it don't, as 20 °C is far
     * below hydrogen's autoignition point.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_014_standing_sources_ignite_a_flammable_zone(GameTestHelper h) {
        BlockState campfire = Blocks.CAMPFIRE.defaultBlockState();
        run(
                h,
                rooms(h, 0),
                List.of(
                        new Case("a torch", s -> s.floor(Blocks.TORCH.defaultBlockState())),
                        new Case("a soul torch", s -> s.floor(Blocks.SOUL_TORCH.defaultBlockState())),
                        new Case("a lit campfire", s -> s.floor(campfire.setValue(CampfireBlock.LIT, true))),
                        new Case("fire", s -> {
                            s.under(Blocks.NETHERRACK.defaultBlockState());
                            s.floor(Blocks.FIRE.defaultBlockState());
                        }),
                        new Case("lava in the floor", s -> s.under(Blocks.LAVA.defaultBlockState()))),
                true);
        run(
                h,
                rooms(h, 1),
                List.of(
                        new Case("an unlit campfire", s -> s.floor(campfire.setValue(CampfireBlock.LIT, false))),
                        new Case("a lantern", s -> s.floor(Blocks.LANTERN.defaultBlockState())),
                        new Case("nothing", s -> {})),
                false);
        h.succeed();
    }

    /** FIRE-014: a gas fire isn't a standing source: it is a hotspot already, and spreads by chance. */
    @GameTest(template = "empty")
    public static void fire_014_gas_fire_is_not_a_standing_source(GameTestHelper h) {
        BlockState gasFire = HotspotBlocks.GAS_FIRE.get().defaultBlockState();
        h.assertTrue(gasFire.is(BlockTags.FIRE), "gas fire is in #minecraft:fire");
        h.assertFalse(IgnitionSources.isStanding(gasFire), "a gas fire is a standing source");
        h.assertFalse(
                Geometries.table().geometry(gasFire).hasFlag(Geometry.FLAG_IGNITION_SOURCE),
                "a gas fire is flagged as an ignition source");
        h.assertTrue(
                Geometries.table().geometry(Blocks.TORCH.defaultBlockState()).hasFlag(Geometry.FLAG_IGNITION_SOURCE),
                "a torch isn't flagged as an ignition source");
        h.succeed();
    }

    /**
     * FIRE-014: flint and steel struck under the ceiling, where it can't place a fire, lightning and
     * an explosion each ignite the flammable zone they happen in.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_014_one_off_sources_ignite_a_flammable_zone(GameTestHelper h) {
        run(
                h,
                rooms(h, 2),
                List.of(
                        new Case("flint and steel", s -> {
                            FakePlayer player = TestPlayers.hurtable(s.r().level, "zas_fire_014_flint");
                            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
                            player.setItemInHand(InteractionHand.MAIN_HAND, flint);
                            BlockPos ceiling = s.at(0, 4, 0);
                            BlockHitResult hit = new BlockHitResult(
                                    Vec3.atCenterOf(ceiling).add(0, -0.5, 0), Direction.DOWN, ceiling, false);
                            player.gameMode.useItemOn(player, s.r().level, flint, InteractionHand.MAIN_HAND, hit);
                        }),
                        new Case("lightning", s -> {
                            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(s.r().level);
                            bolt.moveTo(Vec3.atBottomCenterOf(s.at(0, 1, 0)));
                            bolt.setVisualOnly(true);
                            s.r().level.addFreshEntity(bolt);
                        }),
                        new Case("an explosion", s -> {
                            Vec3 c = Vec3.atCenterOf(s.at(0, 2, 0));
                            s.r().level.explode(null, c.x, c.y, c.z, 0.5f, Level.ExplosionInteraction.NONE);
                        })),
                true);
        h.succeed();
    }

    // ---- API-053: the tag -----------------------------------------------------------------

    /**
     * API-053: a block in {@code #zas:ignition_sources} ignites a flammable zone, one with a {@code
     * lit} property only while lit: the GameTest pack's crying obsidian and redstone ore.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_053_tagged_block_ignites_a_flammable_zone(GameTestHelper h) {
        BlockState ore = Blocks.REDSTONE_ORE.defaultBlockState();
        run(
                h,
                rooms(h, 3),
                List.of(
                        new Case("crying obsidian", s -> s.floor(Blocks.CRYING_OBSIDIAN.defaultBlockState())),
                        new Case("lit redstone ore", s -> s.floor(ore.setValue(RedStoneOreBlock.LIT, true)))),
                true);
        run(
                h,
                rooms(h, 4),
                List.of(new Case("unlit redstone ore", s -> s.floor(ore.setValue(RedStoneOreBlock.LIT, false)))),
                false);
        h.succeed();
    }

    // ---- FIRE-015: a datapack's sources -----------------------------------------------------

    /**
     * FIRE-015: a datapack that makes glowstone a source and turns torches off: after the reload,
     * glowstone lights a flammable zone and a torch doesn't; with the datapack gone, a torch does
     * again.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = OVERRIDES)
    public static void fire_015_datapack_adds_and_removes_sources_in_a_zone(GameTestHelper h) {
        IgnitionSource glowstone = new IgnitionSource(
                Optional.of(IgnitionSource.Type.BLOCK),
                true,
                List.of(new Target(ResourceLocation.withDefaultNamespace("glowstone"), false)),
                Optional.empty(),
                List.of(),
                List.of());
        try {
            IgnitionSources.overrideForTest(Map.of(TORCH, IgnitionSource.disabled(), id("test_glowstone"), glowstone));
            Geometries.rebuildForTest();
            run(
                    h,
                    rooms(h, 5),
                    List.of(new Case("added glowstone", s -> s.floor(Blocks.GLOWSTONE.defaultBlockState()))),
                    true);
            run(
                    h,
                    rooms(h, 6),
                    List.of(new Case("a removed torch", s -> s.floor(Blocks.TORCH.defaultBlockState()))),
                    false);
        } finally {
            IgnitionSources.overrideForTest(Map.of());
            Geometries.rebuildForTest();
        }
        run(
                h,
                rooms(h, 7),
                List.of(new Case("a torch, the datapack gone", s -> s.floor(Blocks.TORCH.defaultBlockState()))),
                true);
        h.succeed();
    }

    /**
     * FIRE-015: by default a lever thrown in a flammable zone lights nothing; with the redstone spark
     * turned on by a datapack, it does. A spark is sent at the end of the game tick, so this test
     * runs over three.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = OVERRIDES)
    public static void fire_015_redstone_ignites_only_when_enabled(GameTestHelper h) {
        Rooms off = rooms(h, 8);
        Rooms on = rooms(h, 9);
        BlockState lever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR);
        build(off, 1);
        build(on, 1);
        off.holdLoaded(List.of(off.chunk(0)));
        on.holdLoaded(List.of(on.chunk(0)));
        Spot offSpot = new Spot(off, 0);
        Spot onSpot = new Spot(on, 0);
        offSpot.floor(lever);
        onSpot.floor(lever);
        offSpot.floor(lever.setValue(LeverBlock.POWERED, true));
        h.runAfterDelay(1, () -> {
            tickUntilBurning(off, 1);
            h.assertFalse(burning(off, 0), "a lever thrown with the redstone spark off lit the room");
            IgnitionSource shipped = IgnitionSources.sources().get(REDSTONE);
            IgnitionSources.overrideForTest(Map.of(
                    REDSTONE,
                    new IgnitionSource(shipped.type(), true, shipped.blocks(), shipped.state(), List.of(), List.of())));
            onSpot.floor(lever.setValue(LeverBlock.POWERED, true));
            h.runAfterDelay(1, () -> {
                try {
                    tickUntilBurning(on, 1);
                    h.assertTrue(burning(on, 0), "a lever thrown with the redstone spark on didn't light the room");
                } finally {
                    IgnitionSources.overrideForTest(Map.of());
                    emptyAll(off, 1);
                    emptyAll(on, 1);
                }
                h.succeed();
            });
        });
    }
}
