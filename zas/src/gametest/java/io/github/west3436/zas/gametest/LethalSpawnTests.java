package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.HazardConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.life.HazardType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Natural spawns where the atmosphere would kill a breathing mob (LIFE-034).
 *
 * <p>The natural spawner needs a player in the level to spawn near, which GameTests don't have. So
 * these call the position check it ends with, {@link EventHooks#checkSpawnPosition}, for {@link
 * MobSpawnType#NATURAL}, and run the spawner chunk generation uses for real, which needs no player.
 * Each test runs within one game tick, and restores the End's ambient before it returns. They have
 * a batch of their own because they call {@code tickNow}.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LethalSpawnTests {
    private static final String BATCH = "life_034";

    /** The area in the End, away from the islands and from the other tests. */
    private static final BlockPos END_AREA = new BlockPos(-28672, 100, -28672);

    /** The monsters that joined a level while {@link #recording}. */
    private static final List<Monster> SPAWNED = new ArrayList<>();

    private static boolean recording;
    private static boolean listening;

    private LethalSpawnTests() {}

    /** The End, with a test's ambient for as long as the body runs. */
    private static void inEnd(GameTestHelper h, Ambient ambient, Consumer<ServerLevel> body) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambients.overrideForTest(Level.END, ambient);
        try {
            body.accept(end);
        } finally {
            Ambients.overrideForTest(Level.END, null);
        }
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerLevel level, Vec3 at) {
        T mob = type.create(level);
        h.assertTrue(mob != null, "couldn't create a " + type);
        mob.moveTo(at);
        return mob;
    }

    private static boolean natural(Mob mob, ServerLevel level) {
        return EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL);
    }

    /** A biome whose only creature is the spider, which spawns on stone in the End's dark, at nearly every try. */
    private static Holder<Biome> spiders() {
        MobSpawnSettings spawns = new MobSpawnSettings.Builder()
                .creatureGenerationProbability(0.9F)
                .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.SPIDER, 1, 4, 4))
                .build();
        BiomeSpecialEffects effects = new BiomeSpecialEffects.Builder()
                .fogColor(0)
                .waterColor(0)
                .waterFogColor(0)
                .skyColor(0)
                .build();
        return Holder.direct(new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.5F)
                .downfall(0.5F)
                .specialEffects(effects)
                .mobSpawnSettings(spawns)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build());
    }

    /**
     * How many monsters chunk generation's spawner puts in the area's chunk, which it then discards.
     * They join the level in a chunk that isn't ticking, where a search of the level can't see them,
     * so this counts them as they join.
     */
    private static int chunkGenerationSpawns(ServerLevel end, Rooms r) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
                if (recording && e.getEntity() instanceof Monster monster) {
                    SPAWNED.add(monster);
                }
            });
        }
        SPAWNED.clear();
        recording = true;
        try {
            NaturalSpawner.spawnMobsForChunkGeneration(end, spiders(), r.chunk(0), RandomSource.create(34));
        } finally {
            recording = false;
        }
        SPAWNED.forEach(Monster::discard);
        return SPAWNED.size();
    }

    /**
     * Breathing mobs don't spawn naturally in a vacuum dimension (LIFE-034): the natural spawner's
     * check refuses them, the spawner of chunk generation puts none there, and the check answers on
     * chunk generation's worker thread too. With air in the same place, the check and the spawner let
     * them in. A mob that doesn't breathe, and a spawn that isn't natural, aren't stopped.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void life_034_breathing_mobs_don_t_spawn_naturally_in_a_vacuum_dimension(GameTestHelper h) {
        Ambient vacuum = Ambients.ambient(ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum")));
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        inEnd(h, vacuum, end -> {
            Rooms r = new Rooms(h, end, END_AREA);
            r.clear(0, 0, 15, 15);
            for (BlockPos p : BlockPos.betweenClosed(r.abs(0, 0, 0), r.abs(15, 0, 15))) {
                end.setBlockAndUpdate(p, Rooms.STONE);
            }
            Vec3 at = Vec3.atBottomCenterOf(r.abs(8, 1, 8));

            for (EntityType<? extends Mob> type : List.of(EntityType.PIG, EntityType.COW, EntityType.SPIDER)) {
                Mob m = mob(h, type, end, at);
                h.assertFalse(natural(m, end), type + " spawns naturally in a vacuum");
                h.assertFalse(
                        EventHooks.checkSpawnPosition(m, end, MobSpawnType.CHUNK_GENERATION),
                        type + " spawns at chunk generation in a vacuum");
                h.assertTrue(
                        EventHooks.checkSpawnPosition(m, end, MobSpawnType.SPAWN_EGG),
                        type + " from a spawn egg was stopped");
            }
            h.assertTrue(natural(mob(h, EntityType.ZOMBIE, end, at), end), "a zombie doesn't breathe, but was stopped");

            Mob offThread = mob(h, EntityType.PIG, end, at);
            boolean allowed = CompletableFuture.supplyAsync(
                            () -> EventHooks.checkSpawnPosition(offThread, end, MobSpawnType.CHUNK_GENERATION),
                            Util.backgroundExecutor())
                    .join();
            h.assertFalse(allowed, "a pig spawns on chunk generation's worker thread");

            h.assertValueEqual(chunkGenerationSpawns(end, r), 0, "spiders chunk generation spawned in a vacuum");
        });
        inEnd(h, Ambient.STANDARD_AIR, end -> {
            Rooms r = new Rooms(h, end, END_AREA);
            Vec3 at = Vec3.atBottomCenterOf(r.abs(8, 1, 8));
            h.assertTrue(natural(mob(h, EntityType.PIG, end, at), end), "a pig was stopped in air");
            h.assertTrue(chunkGenerationSpawns(end, r) > 0, "chunk generation spawned no spiders in air");
        });
        h.succeed();
    }

    /**
     * The atmosphere at the spot decides, not the dimension's (LIFE-034): in sealed rooms in the
     * overworld, a pig is refused in a vacuum, in air too thin in O2, in nitrogen, in a gas that
     * deals damage and at a pressure that does. It may spawn in air, and in a gas that only puts it
     * to sleep. With hypoxia off, nitrogen doesn't stop it. A check another listener already decided
     * stands.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void life_034_the_atmosphere_at_the_spot_decides(GameTestHelper h) {
        Rooms r = new Rooms(h, 279);
        ResourceLocation co = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
        ResourceLocation n2o = ResourceLocation.fromNamespaceAndPath("zas", "nitrous_oxide");
        double o2 = StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION;
        double n2 = StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION;
        r.clear(0, 40);

        room(r, 0, Map.of(Rooms.O2, o2, Rooms.N2, n2));
        h.assertTrue(natural(mob(h, EntityType.PIG, r.level, foot(r, 0)), r.level), "a pig was stopped in air");

        room(r, 6, Map.of());
        h.assertFalse(natural(mob(h, EntityType.PIG, r.level, foot(r, 6)), r.level), "a pig spawns in a vacuum room");

        room(r, 12, Map.of(Rooms.O2, 10.0, Rooms.N2, n2));
        h.assertFalse(natural(mob(h, EntityType.PIG, r.level, foot(r, 12)), r.level), "a pig spawns in thin O2");

        room(r, 18, Map.of(Rooms.N2, StandardAir.PRESSURE));
        Mob inNitrogen = mob(h, EntityType.PIG, r.level, foot(r, 18));
        h.assertFalse(natural(inNitrogen, r.level), "a pig spawns in nitrogen");
        boolean hypoxia = HazardConfig.ENABLED.get(HazardType.HYPOXIA).get();
        HazardConfig.ENABLED.get(HazardType.HYPOXIA).set(false);
        try {
            h.assertTrue(natural(inNitrogen, r.level), "with hypoxia off, nitrogen stopped a pig");
        } finally {
            HazardConfig.ENABLED.get(HazardType.HYPOXIA).set(hypoxia);
        }

        room(r, 24, Map.of(Rooms.O2, o2, Rooms.N2, n2, co, 2.0));
        h.assertFalse(natural(mob(h, EntityType.PIG, r.level, foot(r, 24)), r.level), "a pig spawns in CO");

        room(r, 30, Map.of(Rooms.O2, o2, Rooms.N2, n2, n2o, 6.0));
        h.assertTrue(natural(mob(h, EntityType.PIG, r.level, foot(r, 30)), r.level), "sleeping gas stopped a pig");

        room(r, 36, Map.of(Rooms.O2, o2 * 6, Rooms.N2, n2 * 6));
        Mob squeezed = mob(h, EntityType.PIG, r.level, foot(r, 36));
        h.assertFalse(natural(squeezed, r.level), "a pig spawns at 6 atm");

        MobSpawnEvent.PositionCheck decided =
                new MobSpawnEvent.PositionCheck(squeezed, r.level, MobSpawnType.NATURAL, null);
        decided.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
        NeoForge.EVENT_BUS.post(decided);
        h.assertValueEqual(
                decided.getResult(), MobSpawnEvent.PositionCheck.Result.SUCCEED, "another listener's decision");
        h.succeed();
    }

    /** The middle of the floor of the room from x0, at the level of a mob's feet. */
    private static Vec3 foot(Rooms r, int x0) {
        return Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2));
    }

    /** A sealed room from x0 to x0 + 4 holding these partial pressures in kPa at 293.15 K. */
    private static void room(Rooms r, int x0, Map<ResourceLocation, Double> kPa) {
        r.shell(x0, x0 + 4);
        r.settle(GasMutations.remove(
                r.level,
                r.point(x0 + 2, 2, 2),
                Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                Rooms.source("life_034")));
        if (!kPa.isEmpty()) {
            Map<ResourceLocation, Double> moles = new HashMap<>();
            kPa.forEach((gas, p) -> moles.put(gas, p * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE)));
            r.settle(GasMutations.add(
                    r.level,
                    r.point(x0 + 2, 2, 2),
                    new GasParcel(moles, StandardAir.TEMPERATURE),
                    Rooms.source("life_034")));
        }
    }
}
