package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.fire.CarbonCombustion;
import io.github.west3436.zas.engine.scheduler.FireIntent;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.fire.Burners;
import io.github.west3436.zas.hotspot.FireIntents;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Vanilla fire and fuel blocks (FIRE-020 to FIRE-027, FIRE-030, FIRE-005, FIRE-024, HEAT-007): the
 * fire oxygen threshold puts fire out and pauses fuel blocks, and burning blocks use O₂ and make
 * CO₂, CO and heat at their fuel profile's rate.
 *
 * <p>Each room is a sealed 3 × 3 × 3 stone room in an area of its own, with its gas set by removing
 * the air and adding a mixture. Burning is driven with {@link Burners#burn(ServerLevel, double,
 * java.util.function.Predicate)} for the test's own area, and fire and fuel-block ticks are called
 * directly, so every check runs within the test's game tick.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireTests {
    static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "carbon_dioxide");
    static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "carbon_monoxide");
    static final BlockState NETHERRACK = Blocks.NETHERRACK.defaultBlockState();
    static final BlockState FIRE = Blocks.FIRE.defaultBlockState();

    /** Air: 21 % O₂ at 1 atm. */
    static final Map<ResourceLocation, Double> AIR = Map.of(N2, 80.04675, O2, 21.27825);

    /** Holds a chunk at full status; lapses by itself, after longer than the test using it runs. */
    private static final TicketType<ChunkPos> FULL =
            TicketType.create("zas_gametest_fire_full", Comparator.comparingLong(ChunkPos::toLong), 400);

    private FireTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    /** The test's own area, apart from every other test's. */
    private static Rooms rooms(GameTestHelper h, int index) {
        return new Rooms(h, h.getLevel(), new BlockPos(-12288 - 256 * index, 100, 12288));
    }

    /**
     * Builds a sealed room from x0 to x0 + 4 with {@code floor} under the middle of the floor and
     * {@code inside} on it at (x0 + 2, 1, 2), forms its zone and fills it with partial pressures in
     * kPa at 293.15 K; an empty map leaves it a vacuum.
     */
    private static ZoneInfo room(
            Rooms r, int x0, BlockState floor, BlockState inside, Map<ResourceLocation, Double> kPa) {
        r.clear(x0, x0 + 5);
        r.shell(x0, x0 + 4);
        r.set(x0 + 2, 0, 2, floor);
        Vec3 at = r.point(x0 + 2, 3, 2);
        r.settle(GasMutations.remove(r.level, at, Map.of(N2, 1e6, O2, 1e6, CO2, 1e6, CO, 1e6), Rooms.source("fire")));
        double volume = r.zone(x0 + 2, 3, 2).volume();
        if (!kPa.isEmpty()) {
            Map<ResourceLocation, Double> moles = new HashMap<>();
            kPa.forEach((gas, p) -> moles.put(gas, p * volume / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE)));
            r.settle(
                    GasMutations.add(r.level, at, new GasParcel(moles, StandardAir.TEMPERATURE), Rooms.source("fire")));
        }
        r.set(x0 + 2, 1, 2, inside);
        r.tick();
        return r.zone(x0 + 2, 3, 2);
    }

    /** Burns the area's burning blocks for {@code seconds} and lands the products: their zone's burn. */
    private static CarbonCombustion.Burn burn(Rooms r, int x0, double seconds) {
        ZoneInfo z = r.zone(x0 + 2, 3, 2);
        Map<Long, CompletableFuture<CarbonCombustion.Burn>> sent =
                Burners.burn(r.level, seconds, p -> inArea(r, p, x0));
        CompletableFuture<CarbonCombustion.Burn> mine = sent.get(z.id().value());
        if (mine == null) {
            throw new GameTestAssertException("nothing burned in the room at " + x0);
        }
        CarbonCombustion.Burn done = r.settle(mine);
        r.tick();
        return done;
    }

    private static boolean inArea(Rooms r, BlockPos p, int x0) {
        int x = p.getX() - r.origin.getX();
        int y = p.getY() - r.origin.getY();
        int z = p.getZ() - r.origin.getZ();
        return x >= x0 && x <= x0 + 4 && y >= 0 && y <= 4 && z >= 0 && z <= 4;
    }

    private static void near(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }

    private static BlockState lit(Block furnace) {
        return furnace.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true);
    }

    /** Runs a body with conduction and radiation off, so temperatures move only by what burns. */
    private static void withoutHeat(Runnable body) {
        Rooms.withoutHeat(body);
    }

    // ---- FIRE-030, FIRE-020: fire needs oxygen ------------------------------------------

    /**
     * FIRE-030: a fire goes out where the O₂ fraction is below 0.14 though its partial pressure is
     * high (13 % at 1 atm), and where the partial pressure is below 10 kPa though the fraction is
     * high (pure O₂ at 9 kPa); in air it burns on. Each case is checked by the fire's own tick (M2)
     * and by the burning step, which also covers soul fire.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_030_fire_goes_out_below_each_limit(GameTestHelper h) {
        Rooms r = rooms(h, 0);
        room(r, 0, NETHERRACK, FIRE, AIR);
        room(r, 6, NETHERRACK, FIRE, Map.of(N2, 88.2, O2, 13.2));
        room(r, 12, NETHERRACK, FIRE, Map.of(O2, 9.0));
        room(r, 18, Blocks.SOUL_SOIL.defaultBlockState(), Blocks.SOUL_FIRE.defaultBlockState(), Map.of(O2, 9.0));
        room(r, 24, NETHERRACK, FIRE, Map.of(O2, 11.0));
        // M2: the fire's tick.
        for (int x0 : new int[] {0, 6, 12, 24}) {
            BlockPos at = r.abs(x0 + 2, 1, 2);
            r.level.getBlockState(at).tick(r.level, at, r.level.random);
        }
        h.assertTrue(r.level.getBlockState(r.abs(2, 1, 2)).is(Blocks.FIRE), "the fire in air went out");
        h.assertTrue(r.level.getBlockState(r.abs(8, 1, 2)).isAir(), "the fire at 13 % O2 burns on");
        h.assertTrue(r.level.getBlockState(r.abs(14, 1, 2)).isAir(), "the fire in O2 at 9 kPa burns on");
        h.assertTrue(r.level.getBlockState(r.abs(26, 1, 2)).is(Blocks.FIRE), "the fire in O2 at 11 kPa went out");
        // The burning step: soul fire never ticks.
        h.assertTrue(Burners.tracks(r.level, r.abs(20, 1, 2)), "the soul fire isn't tracked");
        Burners.burn(r.level, 1, p -> inArea(r, p, 18));
        h.assertTrue(r.level.getBlockState(r.abs(20, 1, 2)).isAir(), "the soul fire in O2 at 9 kPa burns on");
        h.assertFalse(Burners.tracks(r.level, r.abs(20, 1, 2)), "the soul fire is still tracked");
        h.succeed();
    }

    /**
     * FIRE-020: flint and steel can't light a fire in a vacuum dimension (the End with the test
     * datapack's vacuum ambient, outside any zone), and lights one in the Overworld's open air.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_020_flint_and_steel_fails_in_a_vacuum_dimension(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum")));
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            BlockPos inEnd = new BlockPos(-12288, 100, 12544);
            h.assertFalse(strike(end, inEnd), "flint and steel lit a fire in a vacuum");
            h.assertTrue(end.getBlockState(inEnd.above()).isAir(), "a fire is in the vacuum");
            h.assertFalse(BaseFireBlock.canBePlacedAt(end, inEnd.above(), Direction.NORTH), "fire can go in a vacuum");
        } finally {
            Ambients.overrideForTest(Level.END, null);
        }
        Rooms r = rooms(h, 1);
        r.clear(0, 4);
        BlockPos open = r.abs(2, 0, 2);
        h.assertTrue(strike(r.level, open), "flint and steel lit nothing in open air");
        h.assertTrue(r.level.getBlockState(open.above()).is(Blocks.FIRE), "no fire in open air");
        r.set(2, 1, 2, Rooms.AIR);
        h.succeed();
    }

    /** Puts a stone block at {@code pos} and strikes flint and steel on its top; whether a fire was lit. */
    private static boolean strike(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Rooms.STONE);
        level.setBlockAndUpdate(pos.above(), Rooms.AIR);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "zas_fire"));
        player.moveTo(Vec3.atCenterOf(pos.above(2)));
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, flint);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        flint.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return level.getBlockState(pos.above()).getBlock() instanceof BaseFireBlock;
    }

    /** FIRE-020: a fire goes out in a sealed room once its O₂ is gone, and doesn't burn its neighbour (M4). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_020_fire_goes_out_in_a_sealed_room(GameTestHelper h) {
        Rooms r = rooms(h, 2);
        room(r, 0, NETHERRACK, FIRE, AIR);
        BlockPos fire = r.abs(2, 1, 2);
        r.level.getBlockState(fire).tick(r.level, fire, r.level.random);
        h.assertTrue(r.level.getBlockState(fire).is(Blocks.FIRE), "the fire went out in air");
        r.settle(GasMutations.remove(r.level, r.point(2, 3, 2), Map.of(O2, 1e6), Rooms.source("fire")));
        h.assertFalse(BaseFireBlock.canBePlacedAt(r.level, r.abs(2, 2, 2), Direction.NORTH), "fire can be lit");
        r.level.getBlockState(fire).tick(r.level, fire, r.level.random);
        h.assertTrue(r.level.getBlockState(fire).isAir(), "the fire burns on without O2");
        h.succeed();
    }

    // ---- FIRE-025, FIRE-021, HEAT-007: fuel blocks burn their profile -------------------

    /**
     * FIRE-021, FIRE-025, HEAT-007: a lit furnace in a zone burns 5 kW of carbon: in 10 s it uses
     * 0.127 mol of O₂ and makes as much CO₂, and adds 50 kJ of heat; a blast furnace burns 10 kW, a
     * campfire 5 kW.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_021_lit_fuel_blocks_use_o2_at_their_profile(GameTestHelper h) {
        withoutHeat(() -> {
            Rooms r = rooms(h, 3);
            Object[][] cases = {
                {lit(Blocks.FURNACE), 5000.0, "a furnace"},
                {lit(Blocks.BLAST_FURNACE), 10_000.0, "a blast furnace"},
                {lit(Blocks.SMOKER), 5000.0, "a smoker"},
                {Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 5000.0, "a campfire"},
            };
            for (int i = 0; i < cases.length; i++) {
                int x0 = 6 * i;
                String what = (String) cases[i][2];
                double watts = (Double) cases[i][1];
                ZoneInfo before = room(r, x0, Rooms.STONE, (BlockState) cases[i][0], AIR);
                CarbonCombustion.Burn done = burn(r, x0, 10);
                ZoneInfo after = r.zone(x0 + 2, 3, 2);
                double carbon = watts * 10 / 394_000;
                near(h, done.oxygen(), carbon, 1e-12, what + ": O2 burned");
                near(h, before.mixture().moles(O2) - after.mixture().moles(O2), carbon, 1e-9, what + ": O2 used");
                near(h, after.mixture().moles(CO2), carbon, 1e-9, what + ": CO2 made");
                near(h, after.mixture().moles(CO), 0, 1e-12, what + ": CO made");
                // HEAT-007: the products come at the zone's temperature, then the heat raises it by Q / C.
                double c = GasProperties.heatCapacity(after.mixture().moles());
                near(
                        h,
                        after.mixture().temperature() - before.mixture().temperature(),
                        watts * 10 / c,
                        1e-6,
                        what + ": temperature rise");
            }
        });
        h.succeed();
    }

    /** FIRE-005: a sealed room at an O₂ fraction of 0.16 with a lit furnace builds up CO, half the carbon. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_005_furnace_in_low_oxygen_makes_co(GameTestHelper h) {
        Rooms r = rooms(h, 4);
        double total = 101.325;
        room(r, 0, Rooms.STONE, lit(Blocks.FURNACE), Map.of(N2, 0.84 * total, O2, 0.16 * total));
        ZoneInfo before = r.zone(2, 3, 2);
        CarbonCombustion.Burn done = burn(r, 0, 10);
        ZoneInfo after = r.zone(2, 3, 2);
        double carbon = 5000.0 * 10 / 394_000;
        // The fraction is read from the room before the burn: 0.16 to within the rounding of its moles.
        double share = (0.18 - before.mixture().composition().get(O2)) / 0.04;
        near(h, share, 0.5, 1e-9, "the CO share at 16 % O2");
        near(h, after.mixture().moles(CO), carbon * share, 1e-9, "CO made");
        near(h, after.mixture().moles(CO2), carbon * (1 - share), 1e-9, "CO2 made");
        near(h, before.mixture().moles(O2) - after.mixture().moles(O2), carbon * (1 - share / 2), 1e-9, "O2 used");
        near(h, done.heat(), carbon * (394_000 - share * 283_000), 1e-6, "heat released");
        h.succeed();
    }

    // ---- FIRE-027: fuel blocks pause without oxygen --------------------------------------

    /**
     * FIRE-027: a burning furnace in a zone that loses its O₂ keeps its burn time, fuel and cooking
     * progress, and resumes when O₂ returns; while it can't burn it uses no O₂.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_027_furnace_pauses_without_oxygen(GameTestHelper h) {
        Rooms r = rooms(h, 5);
        room(r, 0, Rooms.STONE, Blocks.FURNACE.defaultBlockState(), AIR);
        BlockPos at = r.abs(2, 1, 2);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) r.level.getBlockEntity(at);
        h.assertTrue(furnace != null, "no furnace");
        furnace.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        furnace.setItem(1, new ItemStack(Items.COAL, 4));
        tickFurnace(r, at, furnace, 5);
        h.assertValueEqual(furnace.getItem(1).getCount(), 3, "coal after lighting");
        CompoundTag lit = state(r, furnace);
        h.assertTrue(lit.getInt("BurnTime") > 0, "the furnace didn't light");

        r.settle(GasMutations.remove(r.level, r.point(2, 3, 2), Map.of(O2, 1e6), Rooms.source("fire")));
        tickFurnace(r, at, furnace, 40);
        CompoundTag paused = state(r, furnace);
        h.assertValueEqual(paused.getInt("BurnTime"), lit.getInt("BurnTime"), "burn time while paused");
        h.assertValueEqual(paused.getInt("CookTime"), lit.getInt("CookTime"), "cooking progress while paused");
        h.assertValueEqual(furnace.getItem(1).getCount(), 3, "coal while paused");
        h.assertTrue(r.level.getBlockState(at).getValue(AbstractFurnaceBlock.LIT), "the furnace went out");
        h.assertTrue(Burners.burn(r.level, 10, p -> inArea(r, p, 0)).isEmpty(), "the paused furnace burned O2");

        double moles = 21.27825 * r.zone(2, 3, 2).volume() / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
        r.settle(GasMutations.add(
                r.level,
                r.point(2, 3, 2),
                new GasParcel(Map.of(O2, moles), StandardAir.TEMPERATURE),
                Rooms.source("fire")));
        tickFurnace(r, at, furnace, 10);
        CompoundTag resumed = state(r, furnace);
        h.assertValueEqual(resumed.getInt("BurnTime"), lit.getInt("BurnTime") - 10, "burn time after O2 returned");
        h.assertValueEqual(resumed.getInt("CookTime"), lit.getInt("CookTime") + 10, "cooking after O2 returned");
        h.succeed();
    }

    /** FIRE-027, M6: a lit campfire in a zone without O₂ goes out on its next tick; one in air burns on. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_027_campfire_goes_out_without_oxygen(GameTestHelper h) {
        Rooms r = rooms(h, 6);
        BlockState campfire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        room(r, 0, Rooms.STONE, campfire, AIR);
        room(r, 6, Rooms.STONE, campfire, Map.of(N2, 101.325));
        for (int x0 : new int[] {0, 6}) {
            BlockPos at = r.abs(x0 + 2, 1, 2);
            CampfireBlockEntity be = (CampfireBlockEntity) r.level.getBlockEntity(at);
            h.assertTrue(be != null, "no campfire");
            CampfireBlockEntity.cookTick(r.level, at, r.level.getBlockState(at), be);
        }
        h.assertTrue(r.level.getBlockState(r.abs(2, 1, 2)).getValue(CampfireBlock.LIT), "the campfire in air went out");
        h.assertFalse(r.level.getBlockState(r.abs(8, 1, 2)).getValue(CampfireBlock.LIT), "the campfire in N2 burns on");
        h.succeed();
    }

    private static void tickFurnace(Rooms r, BlockPos at, AbstractFurnaceBlockEntity furnace, int ticks) {
        for (int i = 0; i < ticks; i++) {
            AbstractFurnaceBlockEntity.serverTick(r.level, at, r.level.getBlockState(at), furnace);
        }
    }

    private static CompoundTag state(Rooms r, AbstractFurnaceBlockEntity furnace) {
        return furnace.saveWithoutMetadata(r.level.registryAccess());
    }

    // ---- FIRE-022: burning blocks ----------------------------------------------------------

    /**
     * FIRE-022: a fire block in a zone burns its profile, 10 kW of carbon; on a block in the test
     * datapack's {@code #zas_gametest:fire_022_fuel} (hay), the tag's profile, 20 kW, overrides it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_022_fire_burns_its_profile_and_a_tag_overrides_it(GameTestHelper h) {
        Rooms r = rooms(h, 7);
        double[] watts = {10_000, 20_000};
        BlockState[] floors = {NETHERRACK, Blocks.HAY_BLOCK.defaultBlockState()};
        for (int i = 0; i < 2; i++) {
            int x0 = 6 * i;
            ZoneInfo before = room(r, x0, floors[i], FIRE, AIR);
            burn(r, x0, 10);
            ZoneInfo after = r.zone(x0 + 2, 3, 2);
            double carbon = watts[i] * 10 / 394_000;
            near(h, before.mixture().moles(O2) - after.mixture().moles(O2), carbon, 1e-9, "O2 used on " + floors[i]);
            near(h, after.mixture().moles(CO2), carbon, 1e-9, "CO2 made on " + floors[i]);
        }
        h.succeed();
    }

    // ---- FIRE-024: a sealed fire suffocates itself (Hardcore) ---------------------------------

    /** How long a room must stay without fire to count as out: far longer than a gas fire takes to light. */
    private static final int FIRE_024_QUIET_TICKS = 20;

    /**
     * FIRE-024: under Hardcore, a fire lit in a sealed, unzoned room forms a zone holding the room's
     * air (CORE-056), burns its O₂ down, and goes out once the O₂ fraction falls below 0.14; the room
     * keeps the CO₂ and CO it made. A room this small, burned in these 30 s steps, is hot by then, so
     * its CO can light by itself as a gas fire (FIRE-035), at most once. That burns down too and leaves
     * the room with no fire of either kind. While it does, wool in the walls lights no vanilla fire
     * (FIRE-033, FIRE-034).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_024_sealed_fire_forms_a_zone_and_goes_out_under_hardcore(GameTestHelper h) {
        Rooms r = rooms(h, 8);
        // A room of two cells: the fire on the netherrack floor and the air above it.
        r.clear(0, 4);
        for (BlockPos p : BlockPos.betweenClosed(r.abs(0, 0, 0), r.abs(2, 3, 2))) {
            r.level.setBlockAndUpdate(p, Rooms.STONE);
        }
        r.set(1, 0, 1, NETHERRACK);
        r.set(1, 2, 1, Rooms.AIR);
        r.set(1, 1, 1, Rooms.AIR);
        r.ticks(2);
        h.assertFalse(r.zoned(1, 2, 1), "the room is zoned before the fire");
        AtmospherePreset preset = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(AtmospherePreset.HARDCORE);
            r.set(1, 1, 1, FIRE);
            for (int i = 0; i < 4 && !r.zoned(1, 2, 1); i++) {
                r.tick();
            }
        } finally {
            ZasServerConfig.setPreset(preset);
        }
        h.assertTrue(r.zoned(1, 2, 1), "the fire formed no zone under Hardcore");
        ZoneInfo formed = r.zone(1, 2, 1);
        h.assertValueEqual(formed.cellCount(), 2, "cells of the fire's room");
        BlockPos fire = r.abs(1, 1, 1);
        int burns = 0;
        while (r.level.getBlockState(fire).is(Blocks.FIRE) && burns < 40) {
            Map<Long, CompletableFuture<CarbonCombustion.Burn>> sent = Burners.burn(r.level, 30, p -> p.equals(fire));
            for (CompletableFuture<CarbonCombustion.Burn> f : sent.values()) {
                r.settle(f);
            }
            r.tick();
            burns++;
        }
        h.assertFalse(r.level.getBlockState(fire).is(Blocks.FIRE), "the fire still burns after " + burns + " burns");
        ZoneInfo after = r.zone(1, 2, 1);
        double fraction = after.mixture().composition().get(O2);
        h.assertTrue(fraction < 0.14, "the fire went out at an O2 fraction of " + fraction);
        h.assertTrue(fraction > 0.12, "the fire burned on far below the threshold: " + fraction);
        h.assertTrue(after.mixture().moles(CO2) > 0, "no CO2 made");
        h.assertTrue(after.mixture().moles(CO) > 0, "no CO made as the O2 fell");

        // Wait for any gas fire from the CO to light, which may already have happened, and to burn down:
        // until both cells have held no fire for a run of ticks. Whether the CO is within its limit when
        // the vanilla fire goes out isn't certain, so a run may light none.
        BlockPos above = r.abs(1, 2, 1);
        r.set(0, 1, 1, Blocks.WHITE_WOOL.defaultBlockState());
        r.set(0, 2, 1, Blocks.WHITE_WOOL.defaultBlockState());
        boolean burning = HotspotTests.gasFire(r, fire) || HotspotTests.gasFire(r, above);
        int lightings = burning ? 1 : 0;
        int quiet = 0;
        int ticks = 0;
        while (quiet < FIRE_024_QUIET_TICKS && ticks < 400) {
            r.tick();
            ticks++;
            h.assertFalse(
                    r.level.getBlockState(fire).getBlock() instanceof BaseFireBlock
                            || r.level.getBlockState(above).getBlock() instanceof BaseFireBlock,
                    "a vanilla fire lit below 14 % O2, " + ticks + " ticks on: "
                            + r.zone(1, 2, 1).mixture());
            boolean now = HotspotTests.gasFire(r, fire) || HotspotTests.gasFire(r, above);
            lightings += now && !burning ? 1 : 0;
            burning = now;
            quiet = now ? 0 : quiet + 1;
        }
        ZoneInfo end = r.zone(1, 2, 1);
        h.assertTrue(
                quiet >= FIRE_024_QUIET_TICKS, "a gas fire still burns after " + ticks + " ticks: " + end.mixture());
        h.assertTrue(lightings <= 1, "the room's CO lit " + lightings + " times, ending at " + end.mixture());
        h.assertTrue(r.level.getBlockState(fire).isAir(), "the fire's cell holds a fire or a block");
        h.assertTrue(r.level.getBlockState(above).isAir(), "the cell above holds a fire or a block");
        double left = end.mixture().composition().get(O2);
        h.assertTrue(left < 0.14, "the O2 fraction rose to " + left);
        h.assertTrue(left > 0.12, "the gas fire burned on far below the threshold: " + left);
        h.assertTrue(end.mixture().moles(CO2) > 0, "no CO2 left");
        h.assertTrue(end.mixture().moles(CO) > 0, "no CO left");
        h.succeed();
    }

    // ---- GEO-034: fire doesn't load chunks ----------------------------------------------

    /**
     * GEO-034: a tracked fire over a chunk that isn't at full status waits, and neither it nor a fire
     * beside that chunk nor a fire intent in it pulls the chunk back up. A chunk two away from a held
     * one is at ticket level 35: still there and still tracked, but not full. A ticket from reading
     * it through the level would make it 33. Once the chunks are full again, the fires burn.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_034_a_fire_waits_while_its_chunk_isnt_full(GameTestHelper h) {
        Rooms r = rooms(h, 9);
        ServerChunkCache chunks = r.level.getChunkSource();
        ChunkPos west = r.chunk(0);
        ChunkPos middle = r.chunk(16);
        ChunkPos east = r.chunk(32);
        List<ChunkPos> leaving = List.of(west, middle);
        leaving.forEach(c -> chunks.addRegionTicket(FULL, c, 0, c));
        chunks.addRegionTicket(FULL, east, 0, east);
        // A fire in the west chunk, and one in the east chunk's first column with air below it, so what
        // it burns on is looked for on all sides. Its west wall is in the middle chunk.
        room(r, 2, NETHERRACK, FIRE, AIR);
        room(r, 31, Rooms.STONE, Rooms.AIR, AIR);
        r.set(33, 2, 2, Blocks.OAK_PLANKS.defaultBlockState());
        r.set(32, 2, 2, FIRE);
        r.tick();
        BlockPos inWest = r.abs(4, 1, 2);
        BlockPos besideMiddle = r.abs(32, 2, 2);
        h.assertTrue(Burners.tracks(r.level, inWest), "the west fire isn't tracked");
        h.assertTrue(Burners.tracks(r.level, besideMiddle), "the east fire isn't tracked");
        leaving.forEach(c -> chunks.removeRegionTicket(FULL, c, 0, c));
        List<FireIntent> place =
                List.of(new FireIntent(FireIntent.Kind.PLACE, r.abs(4, 2, 2).asLong(), 0));
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertValueEqual(ticketLevel(chunks, middle), 34, "the middle chunk's ticket level");
                    h.assertValueEqual(ticketLevel(chunks, west), 35, "the west chunk's ticket level");
                })
                .thenExecute(() -> {
                    h.assertTrue(Burners.burn(r.level, 10, p -> inArea(r, p, 2)).isEmpty(), "the west fire burned");
                    h.assertTrue(
                            Burners.burn(r.level, 10, p -> inArea(r, p, 31)).isEmpty(), "the east fire burned");
                    h.assertValueEqual(FireIntents.apply(r.level, place, c -> true, b -> false), 0, "fires placed");
                    h.assertValueEqual(ticketLevel(chunks, middle), 34, "the middle chunk's level after burning");
                    h.assertValueEqual(ticketLevel(chunks, west), 35, "the west chunk's level after burning");
                    h.assertTrue(Burners.tracks(r.level, inWest), "the west fire counted as gone");
                    h.assertTrue(Burners.tracks(r.level, besideMiddle), "the east fire counted as gone");
                    leaving.forEach(c -> chunks.addRegionTicket(FULL, c, 0, c));
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(chunks.getChunkNow(west.x, west.z) != null, "the west chunk isn't back");
                    h.assertTrue(chunks.getChunkNow(middle.x, middle.z) != null, "the middle chunk isn't back");
                    h.assertFalse(
                            Burners.burn(r.level, 1, p -> inArea(r, p, 2)).isEmpty(), "the west fire didn't burn");
                    h.assertFalse(
                            Burners.burn(r.level, 1, p -> inArea(r, p, 31)).isEmpty(), "the east fire didn't burn");
                })
                .thenExecute(() -> {
                    h.assertValueEqual(FireIntents.apply(r.level, place, c -> true, b -> false), 1, "fires placed");
                    leaving.forEach(c -> chunks.removeRegionTicket(FULL, c, 0, c));
                    chunks.removeRegionTicket(FULL, east, 0, east);
                })
                .thenSucceed();
    }

    private static int ticketLevel(ServerChunkCache chunks, ChunkPos chunk) {
        return chunks.chunkMap.getVisibleChunkIfPresent(chunk.toLong()).getTicketLevel();
    }
}
