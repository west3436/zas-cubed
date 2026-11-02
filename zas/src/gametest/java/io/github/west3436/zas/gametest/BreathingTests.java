package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.life.BreathState;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.EffectLevel;
import io.github.west3436.zas.life.HazardProfile;
import io.github.west3436.zas.life.HazardProfiles;
import io.github.west3436.zas.life.LifeDamage;
import io.github.west3436.zas.life.Respiration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Breathing (LIFE-001 to LIFE-009, LIFE-040 to LIFE-042) and NeoForge's breathing event
 * (SPACE-002).
 *
 * <p>Each test builds sealed 3 × 3 × 3 rooms in its own area ({@link Rooms}, areas 200 and up) and
 * puts entities in them. The areas' chunks aren't ticking, so nothing happens to an entity unless
 * the test makes it: {@link LivingEntity#baseTick()} runs one tick of NeoForge's breathing hook,
 * and {@link Breathers#tickAt} and {@link Breathers#checkAt} drive the breath checks at chosen game
 * times. Each test runs within one game tick.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BreathingTests {
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
    private static final ResourceLocation N2O = ResourceLocation.fromNamespaceAndPath("zas", "nitrous_oxide");
    private static final ResourceLocation TEST_GAS = ResourceLocation.fromNamespaceAndPath("zas_gametest", "test_gas");

    /** Standard air's partial pressures, in kPa. */
    private static final Map<ResourceLocation, Double> AIR = Map.of(
            StandardAir.OXYGEN, StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION,
            StandardAir.NITROGEN, StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION);

    private BreathingTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    /** A test's rooms and the entities it put in them, which it discards when done. */
    private static final class Lab implements AutoCloseable {
        final Rooms r;
        final GameTestHelper h;
        final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> restore = new ArrayList<>();

        Lab(GameTestHelper h, int index) {
            this.h = h;
            this.r = new Rooms(h, index);
        }

        Lab(GameTestHelper h, Rooms r) {
            this.h = h;
            this.r = r;
        }

        /**
         * Builds a sealed room from x0 to x0 + 4, forms its zone, and fills it with partial
         * pressures in kPa at 293.15 K; an empty map leaves it a vacuum.
         */
        ZoneInfo room(int x0, Map<ResourceLocation, Double> kPa) {
            r.clear(x0, x0 + 5);
            r.shell(x0, x0 + 4);
            r.settle(GasMutations.remove(
                    r.level,
                    r.point(x0 + 2, 2, 2),
                    Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                    Rooms.source("life")));
            if (!kPa.isEmpty()) {
                Map<ResourceLocation, Double> moles = new HashMap<>();
                kPa.forEach(
                        (gas, p) -> moles.put(gas, p * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE)));
                r.settle(GasMutations.add(
                        r.level,
                        r.point(x0 + 2, 2, 2),
                        new GasParcel(moles, StandardAir.TEMPERATURE),
                        Rooms.source("life")));
            }
            return r.zone(x0 + 2, 2, 2);
        }

        /** A mob standing on the floor of the room from x0, at its middle. */
        <T extends Mob> T mob(EntityType<T> type, int x0) {
            return mob(type, r.level, Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
        }

        <T extends Mob> T mob(EntityType<T> type, ServerLevel level, Vec3 at) {
            T e = type.create(level);
            h.assertTrue(e != null, "couldn't create a " + type);
            e.moveTo(at);
            e.setNoAi(true);
            level.addFreshEntity(e);
            entities.add(e);
            return e;
        }

        /** A survival player standing in the room from x0; not added to the level. */
        FakePlayer player(int x0) {
            FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_life"));
            p.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
            return p;
        }

        /** Sets the breath check interval until the test ends. */
        void interval(int ticks) {
            int before = BreathingConfig.CHECK_INTERVAL_TICKS.get();
            restore.add(() -> BreathingConfig.CHECK_INTERVAL_TICKS.set(before));
            BreathingConfig.CHECK_INTERVAL_TICKS.set(ticks);
        }

        /** Sets the respiration multiplier until the test ends. */
        void respiration(double multiplier) {
            double before = BreathingConfig.RESPIRATION_MULTIPLIER.get();
            restore.add(() -> BreathingConfig.RESPIRATION_MULTIPLIER.set(before));
            BreathingConfig.RESPIRATION_MULTIPLIER.set(multiplier);
        }

        /** Puts a hazard profile in place of a gas's own until the test ends. */
        void profile(ResourceLocation gas, HazardProfile profile) {
            Optional<HazardProfile> before = HazardProfiles.of(gas);
            restore.add(() -> HazardProfiles.putForTest(gas, before.orElse(null)));
            HazardProfiles.putForTest(gas, profile);
        }

        @Override
        public void close() {
            entities.forEach(Entity::discard);
            for (int i = restore.size() - 1; i >= 0; i--) {
                restore.get(i).run();
            }
        }
    }

    /** Runs the breathing hook {@code n} times, as {@code n} ticks of the entity. */
    private static void ticks(LivingEntity e, int n) {
        for (int i = 0; i < n; i++) {
            e.baseTick();
        }
    }

    /** The first game time at or after {@code from} that is the entity's tick of the interval. */
    private static long aligned(long from, LivingEntity e, int interval) {
        return from + Math.floorMod(-(from + e.getId()), interval);
    }

    /** An entity's effects, as amplifier by effect. */
    private static Map<Holder<MobEffect>, Integer> effects(LivingEntity e) {
        Map<Holder<MobEffect>, Integer> m = new HashMap<>();
        for (MobEffectInstance i : e.getActiveEffects()) {
            m.put(i.getEffect(), i.getAmplifier());
        }
        return m;
    }

    /** The effects of effect levels together, the highest amplifier of each. */
    private static Map<Holder<MobEffect>, Integer> expected(EffectLevel... levels) {
        Map<Holder<MobEffect>, Integer> m = new HashMap<>();
        for (EffectLevel l : levels) {
            l.effects().forEach(fx -> m.merge(fx.effect(), fx.amplifier(), Math::max));
        }
        return m;
    }

    /** A cell of a gas at a partial pressure in kPa, at 293.15 K. */
    private static GasMixture breath(ResourceLocation gas, double kPa) {
        double moles = kPa * StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
        return new GasMixture(Map.of(gas, moles), StandardAir.TEMPERATURE, StandardAir.CELL_VOLUME);
    }

    private static Map<ResourceLocation, Double> airWith(ResourceLocation gas, double kPa) {
        Map<ResourceLocation, Double> m = new LinkedHashMap<>(AIR);
        m.put(gas, kPa);
        return m;
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    // ---- LIFE-001, LIFE-006: who breathes ---------------------------------------------

    /** In a vacuum, a player and a tagged mob lose air; an untagged mob doesn't (LIFE-001). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_001_player_and_breather_lose_air_in_vacuum(GameTestHelper h) {
        try (Lab lab = new Lab(h, 200)) {
            lab.room(0, Map.of());
            Pig pig = lab.mob(EntityType.PIG, 0);
            Zombie zombie = lab.mob(EntityType.ZOMBIE, 0);
            FakePlayer player = lab.player(0);
            for (LivingEntity e : List.of(pig, zombie, player)) {
                ticks(e, 20);
            }
            h.assertValueEqual(pig.getAirSupply(), 280, "the pig's air after 1 s");
            h.assertValueEqual(player.getAirSupply(), 280, "the player's air after 1 s");
            h.assertValueEqual(zombie.getAirSupply(), zombie.getMaxAirSupply(), "the zombie's air after 1 s");
            h.assertValueEqual(Breathing.of(zombie).reason(), BreathReason.DOES_NOT_BREATHE, "the zombie");
        }
        h.succeed();
    }

    /** The shipped {@code zas:breathers} tag: air-breathing mobs, without the excluded groups (LIFE-006). */
    @GameTest(template = "empty")
    public static void life_006_default_breathers(GameTestHelper h) {
        for (EntityType<?> type : List.of(
                EntityType.PIG,
                EntityType.COW,
                EntityType.SHEEP,
                EntityType.CHICKEN,
                EntityType.VILLAGER,
                EntityType.WOLF,
                EntityType.CREEPER,
                EntityType.SPIDER,
                EntityType.PILLAGER,
                EntityType.FROG)) {
            h.assertTrue(type.is(BreathingRules.BREATHERS), type + " isn't in zas:breathers");
        }
        for (EntityType<?> type : List.of(
                // undead
                EntityType.ZOMBIE,
                EntityType.SKELETON,
                EntityType.PHANTOM,
                EntityType.WITHER,
                // golems
                EntityType.IRON_GOLEM,
                EntityType.SNOW_GOLEM,
                // aquatic
                EntityType.COD,
                EntityType.SQUID,
                EntityType.DOLPHIN,
                EntityType.AXOLOTL,
                EntityType.TURTLE,
                EntityType.GUARDIAN,
                // the Nether's and the End's natives
                EntityType.PIGLIN,
                EntityType.PIGLIN_BRUTE,
                EntityType.HOGLIN,
                EntityType.STRIDER,
                EntityType.GHAST,
                EntityType.BLAZE,
                EntityType.MAGMA_CUBE,
                EntityType.ENDERMAN,
                EntityType.SHULKER,
                EntityType.ENDERMITE,
                EntityType.ENDER_DRAGON,
                // not living
                EntityType.ARMOR_STAND)) {
            h.assertFalse(type.is(BreathingRules.BREATHERS), type + " is in zas:breathers");
        }
        h.succeed();
    }

    /**
     * A piglin isn't a breather, so the Hardcore Nether's 12 kPa of CO₂ leaves it alone; a pig, which
     * is, is incapacitated after 12 s and hurt after 60 s (LIFE-006, LIFE-041).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_006_piglin_survives_the_hardcore_nether(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "no Nether");
        AtmospherePreset before = ZasServerConfig.PRESET.get();
        Rooms r = new Rooms(h, nether, new BlockPos(-7680, 100, 8704));
        try (Lab lab = new Lab(h, r)) {
            ZasServerConfig.PRESET.set(AtmospherePreset.HARDCORE);
            r.clear(0, 6);
            Vec3 at = Vec3.atBottomCenterOf(r.abs(3, 1, 3));
            Piglin piglin = lab.mob(EntityType.PIGLIN, nether, at);
            Pig pig = lab.mob(EntityType.PIG, nether, at);
            AtmosphereSample air = Atmospheres.of(pig);
            h.assertValueEqual(air.state(), AtmosphereState.AMBIENT, "the Nether's open air");
            close(h, air.mixture().partialPressure(CO2), 12, 1e-6, "CO2 in the Hardcore Nether");
            long t0 = nether.getGameTime();
            for (int k = 0; k <= 31; k++) { // 62 s
                Breathers.checkAt(piglin, t0 + 40L * k);
                Breathers.checkAt(pig, t0 + 40L * k);
                if (k == 5) {
                    h.assertFalse(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the pig is slowed after 10 s");
                }
                if (k == 6) {
                    h.assertValueEqual(effects(pig), expected(EffectLevel.INCAPACITATED), "the pig after 12 s");
                }
                if (k == 29) {
                    h.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "the pig is hurt before 60 s");
                }
            }
            h.assertValueEqual(Breathing.of(piglin).reason(), BreathReason.DOES_NOT_BREATHE, "the piglin");
            h.assertTrue(piglin.getActiveEffects().isEmpty(), "the piglin's effects: " + piglin.getActiveEffects());
            h.assertValueEqual(piglin.getHealth(), piglin.getMaxHealth(), "the piglin's health");
            // 1 HP/s of toxic gas from 60 s on, for two checks.
            close(h, pig.getHealth(), pig.getMaxHealth() - 4, 1e-4, "the pig's health after 62 s");
            h.assertTrue(pig.getLastDamageSource().is(LifeDamage.TOXIC_GAS), "the pig's damage type");
        } finally {
            ZasServerConfig.PRESET.set(before);
        }
        h.succeed();
    }

    // ---- LIFE-002: the breath source --------------------------------------------------

    /**
     * A breather breathes the atmosphere at its eyes: a room's zone inside it, the ambient outside
     * (LIFE-002). Internals (LIFE-021) come with #36.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_002_breathes_the_atmosphere_at_its_eyes(GameTestHelper h) {
        try (Lab lab = new Lab(h, 201)) {
            ZoneInfo zone = lab.room(0, airWith(CO2, 2));
            Pig inside = lab.mob(EntityType.PIG, 0);
            Pig outside = lab.mob(EntityType.PIG, lab.r.level, Vec3.atBottomCenterOf(lab.r.abs(8, 1, 2)));
            Breathability in = Breathing.of(inside);
            h.assertValueEqual(in.source(), BreathSource.ATMOSPHERE, "inside: source");
            h.assertValueEqual(in.breath(), Optional.of(zone.mixture()), "inside: the zone's gas");
            Breathability out = Breathing.of(outside);
            h.assertValueEqual(out.source(), BreathSource.ATMOSPHERE, "outside: source");
            h.assertValueEqual(out.breath(), Optional.of(Atmospheres.of(outside).mixture()), "outside: the ambient");
            h.assertValueEqual(Atmospheres.of(outside).state(), AtmosphereState.AMBIENT, "outside is ambient");
        }
        h.succeed();
    }

    // ---- LIFE-007: cadence ------------------------------------------------------------

    /**
     * Hazards apply on each entity's breath check, every 2 s by default; with a 1 s interval the
     * damage per second is the same (LIFE-007). CO at 0.5 kPa deals 0.5 HP/s (LIFE-041).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_007_checks_every_2_s_and_damage_doesnt_depend_on_it(GameTestHelper h) {
        try (Lab lab = new Lab(h, 202)) {
            lab.room(0, airWith(CO, 0.5));
            Pig every2 = lab.mob(EntityType.PIG, 0);
            Pig every1 = lab.mob(EntityType.PIG, 0);
            long t0 = lab.r.level.getGameTime();

            h.assertValueEqual(BreathingConfig.checkIntervalTicks(), 40, "the default interval");
            List<Long> checks = new ArrayList<>();
            List<Long> hurt = new ArrayList<>();
            long start = aligned(t0, every2, 40);
            for (long t = start; t <= start + 160; t++) {
                float health = every2.getHealth();
                long last = Breathers.tickAt(every2, t).lastCheck();
                if (last == t) {
                    checks.add(t - start);
                }
                if (every2.getHealth() < health) {
                    hurt.add(t - start);
                }
            }
            h.assertValueEqual(checks, List.of(0L, 40L, 80L, 120L, 160L), "checks at a 2 s interval");
            h.assertValueEqual(hurt, List.of(40L, 80L, 120L, 160L), "damage at a 2 s interval");
            close(h, every2.getHealth(), every2.getMaxHealth() - 4 * 0.5 * 2, 1e-4, "8 s at 0.5 HP/s, every 2 s");

            lab.interval(20);
            checks.clear();
            start = aligned(t0, every1, 40);
            for (long t = start; t <= start + 160; t++) {
                if (Breathers.tickAt(every1, t).lastCheck() == t) {
                    checks.add(t - start);
                }
            }
            h.assertValueEqual(checks.size(), 9, "checks at a 1 s interval: " + checks);
            close(h, every1.getHealth(), every2.getHealth(), 1e-4, "8 s at 0.5 HP/s, every 1 s");
        }
        h.succeed();
    }

    // ---- LIFE-003, LIFE-008, LIFE-009: the air meter ----------------------------------

    /** The air meter empties in 15 s in a vacuum, as under water (LIFE-003). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_003_meter_empties_in_15_s_in_vacuum(GameTestHelper h) {
        try (Lab lab = new Lab(h, 203)) {
            lab.room(0, Map.of());
            Pig pig = lab.mob(EntityType.PIG, 0);
            ticks(pig, 299);
            h.assertValueEqual(pig.getAirSupply(), 1, "air after 299 ticks");
            ticks(pig, 1);
            h.assertValueEqual(pig.getAirSupply(), 0, "air after 15 s");
            h.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "health as the meter empties");
        }
        h.succeed();
    }

    /** At 8 kPa of O₂, half the minimum, the meter drains at half the rate: 30 s (LIFE-003). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_003_meter_empties_in_30_s_at_8_kpa(GameTestHelper h) {
        try (Lab lab = new Lab(h, 204)) {
            lab.room(0, Map.of(StandardAir.OXYGEN, 8.0, StandardAir.NITROGEN, 70.0));
            Pig pig = lab.mob(EntityType.PIG, 0);
            int ticks = 0;
            while (pig.getAirSupply() > 0 && ticks < 1000) {
                pig.baseTick();
                ticks++;
            }
            // The room's 8 kPa comes back from moles to within rounding, so allow a tick either way.
            h.assertTrue(Math.abs(ticks - 600) <= 1, "the meter emptied in " + ticks + " ticks, not 600");
            h.assertValueEqual(Breathing.of(pig).reason(), BreathReason.LOW_OXYGEN, "reason");
        }
        h.succeed();
    }

    /** An empty meter deals 2 HP/s of hypoxia damage, as drowning does (LIFE-008). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_008_empty_meter_deals_2_hp_per_second(GameTestHelper h) {
        try (Lab lab = new Lab(h, 205)) {
            lab.room(0, Map.of());
            Pig pig = lab.mob(EntityType.PIG, 0);
            ticks(pig, 300);
            h.assertValueEqual(pig.getAirSupply(), 0, "the meter after 15 s");
            float full = pig.getHealth();
            ticks(pig, 19);
            h.assertValueEqual(pig.getHealth(), full, "health before a second has passed");
            ticks(pig, 1);
            close(h, pig.getHealth(), full - 2, 1e-4, "health after 1 s");
            h.assertTrue(pig.getLastDamageSource().is(LifeDamage.HYPOXIA), "the damage type");
            ticks(pig, 20);
            close(h, pig.getHealth(), full - 4, 1e-4, "health after 2 s");
            h.assertValueEqual(pig.getAirSupply(), 0, "the meter stays empty");
        }
        h.succeed();
    }

    /** In breathable air the meter refills at the vanilla rate, 4 a tick (LIFE-009). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_009_meter_refills_at_the_vanilla_rate(GameTestHelper h) {
        try (Lab lab = new Lab(h, 206)) {
            lab.room(0, AIR);
            lab.room(6, Map.of());
            Pig breathing = lab.mob(EntityType.PIG, 0);
            Pig choking = lab.mob(EntityType.PIG, 6);
            ticks(breathing, 1);
            ticks(choking, 1);
            breathing.setAirSupply(0);
            choking.setAirSupply(100);
            ticks(breathing, 10);
            ticks(choking, 10);
            h.assertValueEqual(breathing.getAirSupply(), 40, "air refilled in 10 ticks");
            h.assertValueEqual(choking.getAirSupply(), 90, "air in a vacuum after 10 ticks");
            h.assertValueEqual(Breathing.of(breathing).reason(), BreathReason.BREATHABLE, "reason in the air");
        }
        h.succeed();
    }

    // ---- LIFE-004, LIFE-040, LIFE-041: gas hazards ------------------------------------

    /**
     * The test datapack's profile for {@code zas_gametest:test_gas} warns at 0.5 kPa and, once 2 kPa
     * has lasted 6 s, applies Glowing and 1 HP/s (LIFE-004). At 3 kPa it applies after 6 s; at 1
     * kPa never. Invalid profiles are skipped, naming the file and field (OPS-008).
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void life_004_profile_applies_at_its_threshold_after_its_duration(GameTestHelper h) {
        HazardProfile loaded = HazardProfiles.of(TEST_GAS)
                .orElseThrow(() -> new GameTestAssertException("the test datapack's profile for test_gas didn't load"));
        h.assertValueEqual(loaded.thresholds().size(), 2, "thresholds");
        HazardProfile.Threshold warning = loaded.thresholds().get(0);
        HazardProfile.Threshold harmful = loaded.thresholds().get(1);
        h.assertFalse(warning.harmful(), "the 0.5 kPa threshold is only a warning");
        h.assertValueEqual(harmful.partialPressure(), 2.0, "threshold");
        h.assertValueEqual(harmful.duration(), 6.0, "duration");
        h.assertValueEqual(harmful.damage(), 1.0, "damage");
        h.assertValueEqual(harmful.effects().size(), 1, "effects");
        h.assertTrue(harmful.effects().get(0).effect().is(MobEffects.GLOWING), "the effect");
        for (String file : List.of("invalid_negative_pressure", "invalid_effect")) {
            String path = "data/zas_gametest/zas/hazard_profile/" + file + ".json";
            h.assertTrue(
                    DatapackErrors.recent().stream()
                            .anyMatch(p -> p.kind().equals("hazard profile")
                                    && p.file().equals(path)
                                    && p.skipped()
                                    && p.message().startsWith("thresholds: ")),
                    path + " wasn't skipped naming its field: " + DatapackErrors.recent());
            h.assertTrue(
                    HazardProfiles.of(ResourceLocation.fromNamespaceAndPath("zas_gametest", file))
                            .isEmpty(),
                    file + " loaded");
        }

        try (Lab lab = new Lab(h, 207)) {
            lab.room(0, airWith(TEST_GAS, 3));
            lab.room(6, airWith(TEST_GAS, 1));
            Pig above = lab.mob(EntityType.PIG, 0);
            Pig below = lab.mob(EntityType.PIG, 6);
            long t0 = lab.r.level.getGameTime();
            for (int k = 0; k <= 4; k++) {
                Breathers.checkAt(above, t0 + 40L * k);
                Breathers.checkAt(below, t0 + 40L * k);
                if (k == 2) {
                    h.assertFalse(above.hasEffect(MobEffects.GLOWING), "glowing after 4 s");
                    h.assertValueEqual(above.getHealth(), above.getMaxHealth(), "hurt after 4 s");
                }
                if (k == 3) {
                    h.assertTrue(above.hasEffect(MobEffects.GLOWING), "not glowing after 6 s");
                    close(h, above.getHealth(), above.getMaxHealth() - 2, 1e-4, "health after 6 s");
                }
            }
            close(h, above.getHealth(), above.getMaxHealth() - 4, 1e-4, "health after 8 s");
            h.assertTrue(below.getActiveEffects().isEmpty(), "effects below the threshold");
            h.assertValueEqual(below.getHealth(), below.getMaxHealth(), "health below the threshold");
        }
        h.succeed();
    }

    /** Each effect level applies exactly its listed effects (LIFE-040). */
    @GameTest(template = "empty")
    public static void life_040_effect_levels_apply_their_effects(GameTestHelper h) {
        ResourceLocation gas = ResourceLocation.fromNamespaceAndPath("zas_gametest", "life_040");
        try (Lab lab = new Lab(h, 208)) {
            lab.r.clear(0, 6);
            Pig pig = lab.mob(EntityType.PIG, 0);
            Map<EffectLevel, Map<Holder<MobEffect>, Integer>> listed = Map.of(
                    EffectLevel.IMPAIRED, Map.of(MobEffects.CONFUSION, 0, MobEffects.WEAKNESS, 0),
                    EffectLevel.INCAPACITATED,
                            Map.of(MobEffects.MOVEMENT_SLOWDOWN, 3, MobEffects.DIG_SLOWDOWN, 2, MobEffects.WEAKNESS, 1),
                    EffectLevel.UNCONSCIOUS,
                            Map.of(
                                    MobEffects.MOVEMENT_SLOWDOWN, 3,
                                    MobEffects.DIG_SLOWDOWN, 2,
                                    MobEffects.WEAKNESS, 1,
                                    MobEffects.BLINDNESS, 0));
            for (EffectLevel level : EffectLevel.values()) {
                pig.removeAllEffects();
                lab.profile(
                        gas,
                        new HazardProfile(
                                List.of(new HazardProfile.Threshold(1, 0, Optional.of(level), List.of(), 0))));
                Breathers.breatheForTest(pig, breath(gas, 2), 2);
                h.assertValueEqual(effects(pig), listed.get(level), level.getSerializedName());
                h.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), level + " deals damage");
            }
        }
        h.succeed();
    }

    /** The shipped profiles hold LIFE-041's table. */
    @GameTest(template = "empty")
    public static void life_041_shipped_profiles_hold_the_table(GameTestHelper h) {
        checkProfile(
                h,
                CO2,
                List.of(
                        new HazardProfile.Threshold(5, 0, Optional.empty(), List.of(), 0),
                        new HazardProfile.Threshold(10, true, 12, Optional.of(EffectLevel.INCAPACITATED), List.of(), 0),
                        new HazardProfile.Threshold(
                                10, true, 60, Optional.of(EffectLevel.INCAPACITATED), List.of(), 1)));
        // "Above 10 kPa" is strict; the bare thresholds apply at their partial pressure.
        HazardProfile co2 = HazardProfiles.of(CO2).orElseThrow();
        h.assertFalse(co2.harmfulAt(10), "CO2 is harmful at exactly 10 kPa");
        h.assertTrue(co2.harmfulAt(10.001), "CO2 isn't harmful just above 10 kPa");
        HazardProfile n2o = HazardProfiles.of(N2O).orElseThrow();
        h.assertTrue(n2o.harmfulAt(0.15), "N2O isn't harmful at exactly 0.15 kPa");
        h.assertFalse(n2o.harmfulAt(0.1499), "N2O is harmful below 0.15 kPa");
        HazardProfile co = HazardProfiles.of(CO).orElseThrow();
        h.assertTrue(co.harmfulAt(0.12), "CO isn't harmful at exactly 0.12 kPa");
        checkProfile(
                h,
                N2O,
                List.of(
                        new HazardProfile.Threshold(
                                0.15, 0, Optional.empty(), List.of(new EffectLevel.Effect(MobEffects.CONFUSION, 0)), 0),
                        new HazardProfile.Threshold(1, 0, Optional.of(EffectLevel.INCAPACITATED), List.of(), 0),
                        new HazardProfile.Threshold(5, 0, Optional.of(EffectLevel.UNCONSCIOUS), List.of(), 0)));
        checkProfile(
                h,
                CO,
                List.of(
                        new HazardProfile.Threshold(0.005, 0, Optional.empty(), List.of(), 0),
                        new HazardProfile.Threshold(0.12, 0, Optional.of(EffectLevel.IMPAIRED), List.of(), 0.5),
                        new HazardProfile.Threshold(1.28, 0, Optional.of(EffectLevel.UNCONSCIOUS), List.of(), 2)));
        for (String other : List.of("oxygen", "nitrogen", "water_vapor", "hydrogen", "methane")) {
            h.assertTrue(
                    HazardProfiles.of(ResourceLocation.fromNamespaceAndPath("zas", other))
                            .isEmpty(),
                    other + " has a hazard profile");
        }
        h.succeed();
    }

    private static void checkProfile(GameTestHelper h, ResourceLocation gas, List<HazardProfile.Threshold> rows) {
        HazardProfile p =
                HazardProfiles.of(gas).orElseThrow(() -> new GameTestAssertException("no profile for " + gas));
        h.assertValueEqual(p.thresholds(), rows, gas + "'s thresholds");
    }

    /** CO₂: a warning at 5 kPa; above 10 kPa, Incapacitated after 12 s and 1 HP/s after 60 s (LIFE-041). */
    @GameTest(template = "empty")
    public static void life_041_carbon_dioxide_rows(GameTestHelper h) {
        try (Lab lab = new Lab(h, 209)) {
            lab.r.clear(0, 6);
            Pig warned = lab.mob(EntityType.PIG, 0);
            Pig exposed = lab.mob(EntityType.PIG, 0);
            for (int k = 1; k <= 30; k++) { // 2 s a breath
                Breathers.breatheForTest(warned, breath(CO2, 7), 2);
                Breathers.breatheForTest(exposed, breath(CO2, 11), 2);
                if (k == 5) {
                    h.assertTrue(exposed.getActiveEffects().isEmpty(), "effects after 10 s at 11 kPa");
                }
                if (k == 6) {
                    h.assertValueEqual(effects(exposed), expected(EffectLevel.INCAPACITATED), "after 12 s");
                }
                if (k == 29) {
                    h.assertValueEqual(exposed.getHealth(), exposed.getMaxHealth(), "damage before 60 s");
                }
            }
            close(h, exposed.getHealth(), exposed.getMaxHealth() - 2, 1e-4, "1 HP/s at 60 s");
            h.assertTrue(warned.getActiveEffects().isEmpty(), "effects at the 5 kPa warning");
            h.assertValueEqual(warned.getHealth(), warned.getMaxHealth(), "damage at the 5 kPa warning");
        }
        h.succeed();
    }

    /** N₂O: Nausea at 0.15 kPa, Incapacitated at 1 kPa, Unconscious at 5 kPa, never damage (LIFE-041). */
    @GameTest(template = "empty")
    public static void life_041_nitrous_oxide_rows(GameTestHelper h) {
        try (Lab lab = new Lab(h, 210)) {
            lab.r.clear(0, 6);
            Map<Double, Map<Holder<MobEffect>, Integer>> rows = new LinkedHashMap<>();
            rows.put(0.1, Map.of());
            rows.put(0.2, Map.of(MobEffects.CONFUSION, 0));
            Map<Holder<MobEffect>, Integer> incapacitated = new HashMap<>(expected(EffectLevel.INCAPACITATED));
            incapacitated.put(MobEffects.CONFUSION, 0);
            rows.put(1.5, incapacitated);
            Map<Holder<MobEffect>, Integer> unconscious = new HashMap<>(expected(EffectLevel.UNCONSCIOUS));
            unconscious.put(MobEffects.CONFUSION, 0);
            rows.put(6.0, unconscious);
            rows.forEach((kPa, effects) -> {
                Pig pig = lab.mob(EntityType.PIG, 0);
                Breathers.breatheForTest(pig, breath(N2O, kPa), 2);
                h.assertValueEqual(effects(pig), effects, "effects at " + kPa + " kPa");
                h.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "damage at " + kPa + " kPa");
            });
        }
        h.succeed();
    }

    /** CO: a warning at 0.005 kPa, Impaired and 0.5 HP/s at 0.12 kPa, Unconscious and 2 HP/s at 1.28 kPa (LIFE-041). */
    @GameTest(template = "empty")
    public static void life_041_carbon_monoxide_rows(GameTestHelper h) {
        try (Lab lab = new Lab(h, 211)) {
            lab.r.clear(0, 6);
            Pig warned = lab.mob(EntityType.PIG, 0);
            Pig impaired = lab.mob(EntityType.PIG, 0);
            Pig unconscious = lab.mob(EntityType.PIG, 0);
            Breathers.breatheForTest(warned, breath(CO, 0.01), 2);
            Breathers.breatheForTest(impaired, breath(CO, 0.2), 2);
            Breathers.breatheForTest(unconscious, breath(CO, 1.5), 2);
            h.assertTrue(warned.getActiveEffects().isEmpty(), "effects at the warning");
            h.assertValueEqual(warned.getHealth(), warned.getMaxHealth(), "damage at the warning");
            h.assertValueEqual(effects(impaired), expected(EffectLevel.IMPAIRED), "effects at 0.2 kPa");
            close(h, impaired.getHealth(), impaired.getMaxHealth() - 1, 1e-4, "0.5 HP/s for 2 s");
            h.assertValueEqual(
                    effects(unconscious),
                    expected(EffectLevel.IMPAIRED, EffectLevel.UNCONSCIOUS),
                    "effects at 1.5 kPa");
            close(h, unconscious.getHealth(), unconscious.getMaxHealth() - 4, 1e-4, "2 HP/s for 2 s");
            h.assertTrue(unconscious.getLastDamageSource().is(LifeDamage.TOXIC_GAS), "the damage type");
        }
        h.succeed();
    }

    // ---- LIFE-005, LIFE-042: respiration ----------------------------------------------

    /**
     * A breather in a zone takes O₂ from it and returns the same moles of CO₂; one in the open air
     * changes nothing (LIFE-005).
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void life_005_respiration_turns_o2_into_co2(GameTestHelper h) {
        try (Lab lab = new Lab(h, 212)) {
            ZoneInfo before = lab.room(0, AIR);
            lab.respiration(3600); // 1 mol/s
            Pig pig = lab.mob(EntityType.PIG, 0);
            Pig outside = lab.mob(EntityType.PIG, lab.r.level, Vec3.atBottomCenterOf(lab.r.abs(8, 1, 2)));
            long t0 = lab.r.level.getGameTime();
            Breathers.checkAt(pig, t0);
            Breathers.checkAt(outside, t0);
            Breathers.checkAt(pig, t0 + 40);
            Breathers.checkAt(outside, t0 + 40);
            Map<Long, CompletableFuture<GasParcel>> sent = Respiration.flush(lab.r.level);
            h.assertValueEqual(
                    sent.keySet(), Set.of(before.id().value()), "the zones drawn from (none for the open air)");
            GasParcel removed = lab.r.settle(sent.get(before.id().value()));
            close(h, removed.moles(StandardAir.OXYGEN), 2, 1e-9, "O2 breathed in 2 s");
            lab.r.ticks(2);
            ZoneInfo after = lab.r.zone(2, 2, 2);
            double usedO2 =
                    before.mixture().moles(StandardAir.OXYGEN) - after.mixture().moles(StandardAir.OXYGEN);
            close(h, usedO2, 2, 1e-9, "O2 the zone lost");
            close(h, after.mixture().moles(CO2), usedO2, 1e-9, "CO2 the zone gained");
            close(h, after.mixture().temperature(), before.mixture().temperature(), 1e-9, "temperature");
        }
        h.succeed();
    }

    /** Respiration runs at about 1 mol of O₂ per breather per hour, times the multiplier (LIFE-042). */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void life_042_respiration_rate(GameTestHelper h) {
        try (Lab lab = new Lab(h, 213)) {
            ZoneInfo before = lab.room(0, AIR);
            double multiplier = 360; // 0.1 mol/s per breather
            lab.respiration(multiplier);
            close(h, BreathingConfig.respirationMolesPerSecond() * 3600 / multiplier, 1, 1e-12, "mol/h at x1");
            List<Pig> pigs = List.of(lab.mob(EntityType.PIG, 0), lab.mob(EntityType.PIG, 0));
            long t0 = lab.r.level.getGameTime();
            for (int k = 0; k <= 5; k++) { // 10 s
                for (Pig pig : pigs) {
                    Breathers.checkAt(pig, t0 + 40L * k);
                }
            }
            Map<Long, CompletableFuture<GasParcel>> sent = Respiration.flush(lab.r.level);
            lab.r.settle(sent.get(before.id().value()));
            lab.r.ticks(2);
            ZoneInfo after = lab.r.zone(2, 2, 2);
            double perBreatherPerHour = (before.mixture().moles(StandardAir.OXYGEN)
                            - after.mixture().moles(StandardAir.OXYGEN))
                    / pigs.size()
                    / 10
                    * 3600
                    / multiplier;
            close(h, perBreatherPerHour, 1, 1e-9, "O2 per breather per hour");
        }
        h.succeed();
    }

    // ---- SPACE-002: NeoForge's breathing event ----------------------------------------

    private static final AtomicBoolean LISTENING = new AtomicBoolean();
    private static volatile LivingEntity watched;
    private static volatile LivingEntity forced;
    private static volatile Boolean seenAtLow;

    /** Another mod's listeners: one that says "can't breathe" at HIGH, one that reads at LOW. */
    private static void listen() {
        if (LISTENING.compareAndSet(false, true)) {
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingBreatheEvent.class, e -> {
                if (e.getEntity() == forced) {
                    e.setCanBreathe(false);
                }
            });
            NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LivingBreatheEvent.class, e -> {
                if (e.getEntity() == watched) {
                    seenAtLow = e.canBreathe();
                }
            });
        }
    }

    /** A listener at LOW priority sees ZAS³'s verdict (SPACE-002). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void space_002_low_priority_listener_sees_the_verdict(GameTestHelper h) {
        listen();
        try (Lab lab = new Lab(h, 214)) {
            lab.room(0, Map.of());
            lab.room(6, AIR);
            Pig choking = lab.mob(EntityType.PIG, 0);
            Pig breathing = lab.mob(EntityType.PIG, 6);
            watched = choking;
            seenAtLow = null;
            choking.baseTick();
            h.assertValueEqual(seenAtLow, Boolean.FALSE, "in a vacuum, at LOW");
            watched = breathing;
            seenAtLow = null;
            breathing.baseTick();
            h.assertValueEqual(seenAtLow, Boolean.TRUE, "in breathable air, at LOW");
        } finally {
            watched = null;
        }
        h.succeed();
    }

    /** Another mod's "can't breathe" survives ZAS³'s listener, even in breathable air (SPACE-002). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void space_002_another_mods_cant_breathe_survives(GameTestHelper h) {
        listen();
        try (Lab lab = new Lab(h, 215)) {
            lab.room(0, AIR);
            Pig pig = lab.mob(EntityType.PIG, 0);
            forced = pig;
            watched = pig;
            seenAtLow = null;
            ticks(pig, 10);
            h.assertValueEqual(seenAtLow, Boolean.FALSE, "at LOW");
            h.assertValueEqual(pig.getAirSupply(), pig.getMaxAirSupply() - 10, "air");
            h.assertValueEqual(Breathing.of(pig).reason(), BreathReason.BREATHABLE, "ZAS³'s own verdict");
        } finally {
            forced = null;
            watched = null;
        }
        h.succeed();
    }

    // ---- The breath check's state -----------------------------------------------------

    /**
     * With its eyes in water a breather follows vanilla's rule; when they leave the water, its
     * check runs again at once rather than at its next tick of the interval, so surfacing into a
     * vacuum doesn't refill its air (LIFE-007).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_007_check_runs_again_when_the_eyes_leave_water(GameTestHelper h) {
        try (Lab lab = new Lab(h, 216)) {
            lab.room(0, Map.of());
            Pig pig = lab.mob(EntityType.PIG, 0);
            lab.r.set(2, 1, 2, Blocks.WATER.defaultBlockState());
            pig.baseTick();
            BreathState state = pig.getData(Breathers.STATE);
            h.assertValueEqual(state.verdict().source(), BreathSource.FLUID, "in water");
            long checked = state.lastCheck();
            lab.r.set(2, 1, 2, Rooms.AIR);
            pig.baseTick();
            h.assertValueEqual(state.lastCheck(), checked, "both checks ran in this game tick");
            h.assertValueEqual(state.verdict().reason(), BreathReason.LOW_OXYGEN, "out of the water, in a vacuum");
            int air = pig.getAirSupply();
            pig.baseTick();
            h.assertValueEqual(pig.getAirSupply(), air - 1, "the meter drains after surfacing");
        }
        h.succeed();
    }
}
