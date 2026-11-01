package io.github.west3436.zas.example.gametest;

import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.example.ExampleAddonMod;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * S-12, API without ZAS³ (API-048, D-17): this server runs the example addon, which nests the API,
 * and no {@code zas}. The API's fallback must answer every query with ambient standard air and no
 * zone, complete every mutation at once as {@link MutationOutcome#ABSENT}, give vanilla's
 * breathing verdict (API-009, API-014), and throw nothing.
 */
@GameTestHolder(ExampleAddonMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WithoutZasTests {
    private static final SourceId SOURCE = SourceId.of(ExampleAddonMod.MOD_ID, "s_12");
    private static final double EPS = 1e-9;

    /** The bottoms of the two water columns {@link #waterColumns} builds. */
    private static final BlockPos LEFT = new BlockPos(1, 1, 1);

    private static final BlockPos RIGHT = new BlockPos(3, 1, 1);

    private WithoutZasTests() {}

    /** The addon loaded and zas didn't, so the API reports no implementation (API-040). */
    @GameTest(template = "empty")
    public static void s_12_api_absent_without_zas(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded(ExampleAddonMod.MOD_ID), "the example addon is not loaded");
        h.assertFalse(ModList.get().isLoaded("zas"), "zas is loaded; S-12 needs it absent");
        h.assertFalse(ZasApi.isPresent(), "ZasApi.isPresent() is true without zas");
        h.assertFalse(ZasApi.provider().isReal(), "the provider claims to be real");
        h.succeed();
    }

    /** Every point, entity and dimension reads ambient, breathable standard air, in no zone. */
    @GameTest(template = "empty")
    public static void s_12_queries_return_standard_air(GameTestHelper h) {
        ServerLevel overworld = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "no nether level");
        List<AtmosphereSample> samples = List.of(
                Atmospheres.at(overworld, pos),
                Atmospheres.at(overworld, Vec3.atCenterOf(pos)),
                Atmospheres.at(overworld, BlockPos.ZERO),
                Atmospheres.of(pig),
                Atmospheres.at(nether, pos));
        for (AtmosphereSample s : samples) {
            assertStandardAir(h, s);
        }
        h.assertTrue(Atmospheres.zoneAt(overworld, pos).isEmpty(), "zoneAt(pos) found a zone");
        h.assertTrue(Atmospheres.zoneAt(overworld, Vec3.atCenterOf(pos)).isEmpty(), "zoneAt(point) found a zone");
        h.assertTrue(Atmospheres.zone(overworld, new ZoneId(1)).isEmpty(), "zone(id) found a zone");
        h.succeed();
    }

    /** Adding and removing gas complete at once as ABSENT, moving nothing. */
    @GameTest(template = "empty")
    public static void s_12_mutations_complete_absent(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 point = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        CompletableFuture<MutationResult> add =
                GasMutations.add(level, point, new GasParcel(Map.of(StandardAir.OXYGEN, 5.0), 300), SOURCE);
        CompletableFuture<MutationResult> remove =
                GasMutations.remove(level, point, Map.of(StandardAir.NITROGEN, 2.0), SOURCE);
        for (CompletableFuture<MutationResult> f : List.of(add, remove)) {
            h.assertTrue(f.isDone(), "a fallback mutation didn't complete at once");
            h.assertFalse(f.isCompletedExceptionally(), "a fallback mutation failed");
            MutationResult r = f.join();
            h.assertValueEqual(r.outcome(), MutationOutcome.ABSENT, "outcome");
            h.assertTrue(r.moved().isEmpty(), "the fallback moved gas: " + r.moved());
        }
        h.succeed();
    }

    /**
     * In air the fallback gives vanilla's verdict: a pig and a player breathe the atmosphere, with
     * no breath gas, no hazard and no protection, so they are protected (API-009, API-014).
     */
    @GameTest(template = "empty")
    public static void api_014_fallback_breathes_in_air(GameTestHelper h) {
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        for (LivingEntity e : List.of(pig, player)) {
            Breathability b = Breathing.of(e);
            h.assertValueEqual(b, Breathing.vanilla(e), "the fallback's verdict is vanilla's");
            assertVanillaVerdict(h, b, BreathReason.BREATHABLE, BreathSource.ATMOSPHERE);
        }
        h.succeed();
    }

    /**
     * Underwater still counts (API-009): with its eyes in water a pig drowns, and a zombie, whose
     * type can breathe under water, doesn't.
     */
    @GameTest(template = "empty")
    public static void api_014_fallback_underwater_still_counts(GameTestHelper h) {
        waterColumns(h);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, LEFT);
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, RIGHT);
        h.succeedWhen(() -> {
            assertEyesInWater(h, pig);
            assertEyesInWater(h, zombie);
            Breathability drowning = Breathing.of(pig);
            h.assertValueEqual(drowning, Breathing.vanilla(pig), "the fallback's verdict is vanilla's");
            assertVanillaVerdict(h, drowning, BreathReason.DROWNING, BreathSource.FLUID);
            assertVanillaVerdict(h, Breathing.of(zombie), BreathReason.FLUID_BREATHING, BreathSource.FLUID);
        });
    }

    /**
     * Underwater, Water Breathing keeps a pig from drowning, and so does creative mode for a
     * player, while a survival player drowns (API-009).
     */
    @GameTest(template = "empty")
    public static void api_014_fallback_water_breathing_and_creative_mode(GameTestHelper h) {
        waterColumns(h);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, LEFT);
        pig.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 1200));
        assertVanillaVerdict(
                h, Breathing.of(submergedPlayer(h, GameType.CREATIVE)), BreathReason.EXEMPT, BreathSource.FLUID);
        assertVanillaVerdict(
                h, Breathing.of(submergedPlayer(h, GameType.SURVIVAL)), BreathReason.DROWNING, BreathSource.FLUID);
        h.succeedWhen(() -> {
            assertEyesInWater(h, pig);
            assertVanillaVerdict(h, Breathing.of(pig), BreathReason.FLUID_BREATHING, BreathSource.FLUID);
        });
    }

    /** Stone on a floor at y = 0, with two water columns two deep at {@link #LEFT} and {@link #RIGHT}. */
    private static void waterColumns(GameTestHelper h) {
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(4, 2, 2))) {
            boolean water = p.getY() > 0 && p.getZ() == 1 && (p.getX() == 1 || p.getX() == 3);
            h.setBlock(p, water ? Blocks.WATER : Blocks.STONE);
        }
    }

    /**
     * A mock player in a game mode, standing in the {@link #RIGHT} column. Mock players aren't
     * ticked, so it is ticked once here to see the water at its eyes.
     */
    private static Player submergedPlayer(GameTestHelper h, GameType mode) {
        Player player = h.makeMockPlayer(mode);
        mode.updatePlayerAbilities(player.getAbilities());
        player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(RIGHT)));
        player.baseTick();
        assertEyesInWater(h, player);
        return player;
    }

    /**
     * Checks an entity's eyes are in water. A test force-loads only its structure's chunks, so an
     * entity standing in the next chunk isn't ticked and never sees the water at its eyes; it is
     * ticked once here if it hasn't yet.
     */
    private static void assertEyesInWater(GameTestHelper h, LivingEntity e) {
        if (e.getEyeInFluidType() != NeoForgeMod.WATER_TYPE.value()) {
            e.baseTick();
        }
        h.assertTrue(
                e.getEyeInFluidType() == NeoForgeMod.WATER_TYPE.value(),
                e.getName().getString() + "'s eyes aren't in water yet");
    }

    /** Checks a vanilla verdict: its reason and source, and no breath gas, hazard or protection. */
    private static void assertVanillaVerdict(
            GameTestHelper h, Breathability b, BreathReason reason, BreathSource source) {
        h.assertValueEqual(b.reason(), reason, "reason");
        h.assertValueEqual(b.source(), source, "source");
        h.assertValueEqual(b.canBreathe(), reason.canBreathe(), "can breathe");
        h.assertTrue(b.breath().isEmpty(), "vanilla's verdict carries a breath gas: " + b);
        h.assertTrue(b.hazards().isEmpty() && b.isProtected(), "vanilla's verdict has a hazard: " + b);
        h.assertTrue(b.protection().isEmpty(), "vanilla's verdict has protection: " + b);
    }

    private static void assertStandardAir(GameTestHelper h, AtmosphereSample s) {
        h.assertValueEqual(s.state(), AtmosphereState.AMBIENT, "state");
        h.assertTrue(s.zone().isEmpty(), "an ambient sample has a zone");
        h.assertTrue(Math.abs(s.pressure() - StandardAir.PRESSURE) < 1e-6, "pressure " + s.pressure());
        h.assertTrue(Math.abs(s.temperature() - StandardAir.TEMPERATURE) < EPS, "temperature " + s.temperature());
        Double o2 = s.composition().get(StandardAir.OXYGEN);
        h.assertTrue(o2 != null && Math.abs(o2 - StandardAir.OXYGEN_FRACTION) < EPS, "O2 fraction " + o2);
        Double n2 = s.composition().get(StandardAir.NITROGEN);
        h.assertTrue(n2 != null && Math.abs(n2 - StandardAir.NITROGEN_FRACTION) < EPS, "N2 fraction " + n2);
        // Breathable: the O2 partial pressure of sea-level air, about 21.3 kPa.
        Double po2 = s.partialPressures().get(StandardAir.OXYGEN);
        h.assertTrue(
                po2 != null && Math.abs(po2 - StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION) < 1e-6,
                "O2 partial pressure " + po2);
    }
}
