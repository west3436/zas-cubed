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
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.config.HazardConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.life.BreathState;
import io.github.west3436.zas.life.BreathSyncPayload;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.ClientBreath;
import io.github.west3436.zas.life.EffectLevel;
import io.github.west3436.zas.life.HazardProfile;
import io.github.west3436.zas.life.HazardProfiles;
import io.github.west3436.zas.life.HazardType;
import io.github.west3436.zas.life.LifeDamage;
import io.github.west3436.zas.life.Respiration;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
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

        /** A survival player standing in the room from x0 that damage reaches ({@link TestPlayers}); not added to the level. */
        FakePlayer hurtable(int x0) {
            FakePlayer p = TestPlayers.hurtable(r.level, "zas_life");
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

        /** Turns a hazard off until the test ends (LIFE-031). */
        void off(HazardType hazard) {
            boolean before = HazardConfig.ENABLED.get(hazard).get();
            restore.add(() -> HazardConfig.ENABLED.get(hazard).set(before));
            HazardConfig.ENABLED.get(hazard).set(false);
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

    // ---- LIFE-048: an unresolved atmosphere -------------------------------------------

    /**
     * While the atmosphere at the eyes is unresolved, a breath check holds the state: the last
     * verdict and its air drain stay, and nothing is charged, though the last-known mixture is the
     * breathable ambient. An entity with no verdict yet waits for the atmosphere to resolve
     * (LIFE-048). The unresolved region is one held at a breach because it reaches an unloaded
     * chunk (GEO-026); CO at 0.5 kPa deals 0.5 HP/s (LIFE-041).
     *
     * <p>{@code /zas life}'s damage line says what is charged: CO's rate in the room, and nothing
     * while held, even if the last-known mixture would burn.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_048_unresolved_atmosphere_holds_the_breath_state(GameTestHelper h) {
        try (Lab lab = new Lab(h, 217)) {
            lab.off(HazardType.DECOMPRESSION); // room A holds only 0.5 kPa
            Rooms r = lab.r;
            r.clear(0, 23);
            r.shell(2, 9); // A, in the west chunk: 54 cells of CO alone
            r.shell(9, 21); // B, unzoned, across both chunks, sharing the wall at x = 9
            r.settle(GasMutations.remove(
                    r.level,
                    r.point(5, 2, 2),
                    Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                    Rooms.source("life")));
            double moles = 0.5 * 54_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
            r.settle(GasMutations.add(
                    r.level,
                    r.point(5, 2, 2),
                    new GasParcel(Map.of(CO, moles), StandardAir.TEMPERATURE),
                    Rooms.source("life")));
            Pig pig = lab.mob(EntityType.PIG, r.level, Vec3.atBottomCenterOf(r.abs(5, 1, 2)));
            long t = r.level.getGameTime();
            Breathability before = Breathers.checkAt(pig, t).verdict();
            h.assertValueEqual(before.reason(), BreathReason.LOW_OXYGEN, "the verdict in A");
            Breathers.checkAt(pig, t + 40);
            float health = pig.getHealth();
            close(h, pig.getMaxHealth() - health, 1, 1e-4, "damage from 2 s of CO");
            h.assertValueEqual(line(pig, DAMAGE), DAMAGE + "toxic_gas 0.5", "the damage /zas life reports in A");

            ChunkPos east = new ChunkPos(r.abs(16, 0, 0));
            byte[] saved = r.dimension().simulateUnload(east);
            try {
                r.tick();
                r.set(9, 2, 2, Blocks.AIR.defaultBlockState());
                r.tick();
                Vec3 inB = Vec3.atBottomCenterOf(r.abs(12, 1, 2));
                pig.moveTo(inB);
                h.assertValueEqual(Atmospheres.of(pig).state(), AtmosphereState.UNRESOLVED, "B's loaded part");
                h.assertValueEqual(
                        BreathingRules.assess(pig).verdict().reason(),
                        BreathReason.BREATHABLE,
                        "the last-known ambient");
                BreathState held = Breathers.checkAt(pig, t + 80);
                h.assertValueEqual(held.verdict(), before, "the held verdict");
                h.assertValueEqual(held.lastCheck(), t + 80, "the held check's time");
                h.assertValueEqual(Breathing.of(pig), before, "the API's verdict");
                h.assertValueEqual(pig.getHealth(), health, "health after a held check");
                // A held check charges nothing, whatever the last-known mixture would deal: with a
                // comfort band that ends below the ambient's 293.15 K, the guess burns.
                double comfortMax = HazardConfig.COMFORT_MAX.get();
                HazardConfig.COMFORT_MAX.set(280.0);
                try {
                    h.assertTrue(BreathingRules.assess(pig).exposure().burns() > 0, "the guess doesn't burn");
                    h.assertValueEqual(line(pig, DAMAGE), DAMAGE + "-", "the damage /zas life reports while held");
                } finally {
                    HazardConfig.COMFORT_MAX.set(comfortMax);
                }
                int air = pig.getAirSupply();
                ticks(pig, 20);
                h.assertValueEqual(pig.getAirSupply(), air - 20, "air while held");

                Pig fresh = lab.mob(EntityType.PIG, r.level, inB);
                BreathState waiting = Breathers.tickAt(fresh, t + 80);
                h.assertTrue(waiting.verdict() == null, "a verdict from the last-known ambient");
                h.assertValueEqual(waiting.lastCheck(), -1L, "the waiting check's time");
                h.assertValueEqual(Breathers.tickAt(fresh, t + 81).lastCheck(), -1L, "the next tick's check");
            } finally {
                r.dimension().simulateLoad(east, saved);
            }
            r.ticks(3);
            h.assertValueEqual(Atmospheres.of(pig).state(), AtmosphereState.ZONED, "B once the chunk loads");
            BreathState resolved = Breathers.checkAt(pig, t + 120);
            h.assertValueEqual(resolved.verdict(), BreathingRules.assess(pig).verdict(), "the verdict in the zone");
            h.assertValueEqual(resolved.lastCheck(), t + 120, "the check's time");
            Pig fresh = (Pig) lab.entities.get(lab.entities.size() - 1);
            h.assertTrue(Breathers.tickAt(fresh, t + 121).verdict() != null, "no verdict once resolved");
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

    /**
     * Nausea and blindness from a gas keep the ticks the client needs to show them, at any check
     * interval (LIFE-040): it wobbles the view only while nausea has more than 60 left, and draws
     * blindness's fog back over its last 20. A tick before the next check both have more than that,
     * and a check a second late still finds them so. The other effects end a second after the next
     * check, as before.
     */
    @GameTest(template = "empty")
    public static void life_040_nausea_and_blindness_keep_the_ticks_the_client_needs(GameTestHelper h) {
        List<Holder<MobEffect>> others =
                List.of(MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN);
        Map<Holder<MobEffect>, Integer> leads = Map.of(MobEffects.CONFUSION, 60, MobEffects.BLINDNESS, 20);
        try (Lab lab = new Lab(h, 224)) {
            lab.room(0, airWith(CO, 1.5));
            long now = lab.r.level.getGameTime();
            for (int interval : new int[] {40, 10, 200}) {
                lab.interval(interval);
                for (boolean checked : new boolean[] {true, false}) {
                    Pig pig = lab.mob(EntityType.PIG, 0);
                    String at = (checked ? "a check" : "a profile applied") + " at an interval of " + interval;
                    if (checked) {
                        Breathers.checkAt(pig, now);
                    } else {
                        Breathers.breatheForTest(pig, breath(CO, 1.5), 0);
                    }
                    h.assertValueEqual(
                            effects(pig), expected(EffectLevel.IMPAIRED, EffectLevel.UNCONSCIOUS), "effects, " + at);
                    for (Holder<MobEffect> other : others) {
                        h.assertValueEqual(
                                pig.getEffect(other).getDuration(),
                                interval + 20,
                                other.getRegisteredName() + "'s ticks, " + at);
                    }
                    leads.forEach((effect, lead) -> h.assertValueEqual(
                            pig.getEffect(effect).getDuration(),
                            interval + 20 + lead,
                            effect.getRegisteredName() + "'s ticks, " + at));
                    if (!checked) {
                        continue;
                    }
                    // No other check is due at this game time, so these ticks only run the effects down.
                    ticks(pig, interval - 1);
                    leads.forEach((effect, lead) -> {
                        int left = pig.getEffect(effect).getDuration();
                        h.assertTrue(
                                left > lead,
                                effect.getRegisteredName() + " has " + left + " ticks a tick before the next check, "
                                        + at);
                    });
                    ticks(pig, 20);
                    leads.forEach((effect, lead) -> {
                        int left = pig.getEffect(effect).getDuration();
                        h.assertTrue(
                                left > lead,
                                effect.getRegisteredName() + " has " + left + " ticks 19 after the next check was "
                                        + "due, " + at);
                    });
                }
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

    // ---- LIFE-004, LIFE-008: the damage /zas life reports ------------------------------

    /** The start of {@code /zas life}'s hazards line. */
    private static final String HAZARDS = "  Hazards: ";

    /** The start of {@code /zas life}'s damage line. */
    private static final String DAMAGE = "  Damage per second: ";

    /** The line {@code /zas life} says of an entity that starts with {@code start}. */
    private static String line(LivingEntity e, String start) {
        List<String> said = SpaceQueryTests.life(e);
        return said.stream()
                .filter(l -> l.startsWith(start))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no line starting '" + start + "' in " + said));
    }

    /**
     * In the room from x0, whose CO deals {@code rate} HP/s: a bare player's lines give the hazard
     * and that rate, and 2 s of checks take that much health. A player with a filter on its head,
     * as a gas mask is, and one breathing from an always-on tank behind a seal, as running scuba
     * gear is, have nothing on the damage line and lose no health.
     */
    private static void carbonMonoxideRoom(GameTestHelper h, Lab lab, int x0, String rate, double lostIn2s) {
        FakePlayer bare = lab.hurtable(x0);
        FakePlayer masked = lab.hurtable(x0);
        masked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CREEPER_HEAD));
        FakePlayer tanked = lab.hurtable(x0);
        tanked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
        tanked.setItemInHand(
                InteractionHand.OFF_HAND, TestLifeSupport.alwaysOnTank(Map.of(StandardAir.OXYGEN, 1.0), 21.3));
        String at = ", where CO deals " + rate + " HP/s";

        h.assertValueEqual(line(bare, HAZARDS), HAZARDS + "toxic gas; protection: -", "bare, the hazards" + at);
        h.assertValueEqual(line(bare, DAMAGE), DAMAGE + "toxic_gas " + rate, "bare, the damage" + at);
        h.assertValueEqual(line(masked, HAZARDS), HAZARDS + "-; protection: gas filtering", "masked, the hazards" + at);
        h.assertValueEqual(line(masked, DAMAGE), DAMAGE + "-", "masked, the damage" + at);
        h.assertValueEqual(Breathing.of(tanked).source(), BreathSource.INTERNALS, "on a tank, the breath" + at);
        h.assertValueEqual(line(tanked, DAMAGE), DAMAGE + "-", "on a tank, the damage" + at);

        for (FakePlayer p : List.of(bare, masked, tanked)) {
            long now = p.level().getGameTime();
            Breathers.checkAt(p, now);
            Breathers.checkAt(p, now + 40);
        }
        close(h, bare.getMaxHealth() - bare.getHealth(), lostIn2s, 1e-4, "bare, the health 2 s took" + at);
        close(h, masked.getHealth(), masked.getMaxHealth(), 0, "masked, the health" + at);
        close(h, tanked.getHealth(), tanked.getMaxHealth(), 0, "on a tank, the health" + at);
        h.assertValueEqual(line(bare, DAMAGE), DAMAGE + "toxic_gas " + rate, "bare, the damage after 2 s" + at);
        h.assertValueEqual(line(masked, DAMAGE), DAMAGE + "-", "masked, the damage after 2 s" + at);
    }

    /**
     * {@code /zas life}'s damage line gives the toxic gas damage the checks charge (LIFE-004). In air
     * with CO at 0.2 kPa, where it impairs and deals 0.5 HP/s, and at 1.5 kPa, where it knocks out
     * and deals 2 HP/s (LIFE-041), a bare player's line gives that rate and loses health at it; a
     * filtered breath and a tank's have no damage. The line used to be built from pressure and
     * temperature alone, so it said "-" under "Hazards: toxic gas" while health fell.
     *
     * <p>The line follows the rules, not the threshold alone. CO2 above 10 kPa is a hazard at once but
     * deals its 1 HP/s only once sustained for 60 s: the line is empty at 58 s and gives the rate at
     * 60 s, with the check that first takes health. With toxic gas off nothing is charged, and the
     * line is empty (LIFE-031).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_004_the_life_command_reports_toxic_gas_damage(GameTestHelper h) {
        try (Lab lab = new Lab(h, 222)) {
            lab.room(0, airWith(CO, 0.2));
            lab.room(6, airWith(CO, 1.5));
            lab.room(12, airWith(CO2, 12));
            carbonMonoxideRoom(h, lab, 0, "0.5", 1);
            carbonMonoxideRoom(h, lab, 6, "2", 4);

            FakePlayer slow = lab.hurtable(12);
            long t = slow.level().getGameTime();
            for (int check = 0; check <= 29; check++) {
                Breathers.checkAt(slow, t + 40L * check);
            }
            h.assertValueEqual(line(slow, HAZARDS), HAZARDS + "toxic gas; protection: -", "58 s into CO2, the hazards");
            h.assertValueEqual(line(slow, DAMAGE), DAMAGE + "-", "58 s into CO2, the damage");
            close(h, slow.getHealth(), slow.getMaxHealth(), 0, "58 s into CO2, the health");
            Breathers.checkAt(slow, t + 40L * 30);
            h.assertValueEqual(line(slow, DAMAGE), DAMAGE + "toxic_gas 1", "60 s into CO2, the damage");
            close(h, slow.getMaxHealth() - slow.getHealth(), 2, 1e-4, "the health the check at 60 s took");

            FakePlayer bare = lab.hurtable(6);
            lab.off(HazardType.TOXIC_GAS);
            h.assertValueEqual(line(bare, HAZARDS), HAZARDS + "-; protection: -", "toxic gas off, the hazards");
            h.assertValueEqual(line(bare, DAMAGE), DAMAGE + "-", "toxic gas off, the damage");
        }
        h.succeed();
    }

    /**
     * {@code /zas life}'s damage line gives the hypoxia damage an empty air meter deals (LIFE-008).
     * In nitrogen at one atmosphere, where nothing but the lack of O2 harms it, a pig can't breathe
     * from the start, but its line is empty while its meter drains and it loses no health. Once the
     * meter is empty the line gives 2 HP/s, and the next second takes 2 HP. The line used to stay
     * empty. A player breathing from a tank there isn't short of O2, and its line stays empty.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_008_the_life_command_reports_hypoxia_damage(GameTestHelper h) {
        try (Lab lab = new Lab(h, 223)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, StandardAir.PRESSURE));
            Pig pig = lab.mob(EntityType.PIG, 0);
            FakePlayer tanked = lab.hurtable(0);
            tanked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
            tanked.setItemInHand(
                    InteractionHand.OFF_HAND, TestLifeSupport.alwaysOnTank(Map.of(StandardAir.OXYGEN, 1.0), 21.3));

            ticks(pig, 100);
            List<String> draining = SpaceQueryTests.life(pig);
            h.assertTrue(draining.get(0).contains(": low oxygen, "), "the pig's verdict: " + draining);
            h.assertTrue(pig.getAirSupply() > 0, "the meter is empty after 5 s");
            h.assertValueEqual(line(pig, DAMAGE), DAMAGE + "-", "while the meter drains, the damage");
            h.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "while the meter drains, the health");

            ticks(pig, 200);
            h.assertValueEqual(pig.getAirSupply(), 0, "the meter after 15 s");
            h.assertValueEqual(line(pig, DAMAGE), DAMAGE + "hypoxia 2", "with the meter empty, the damage");
            float before = pig.getHealth();
            ticks(pig, 20);
            close(h, before - pig.getHealth(), 2, 1e-4, "the health the next second took");
            h.assertValueEqual(line(pig, DAMAGE), DAMAGE + "hypoxia 2", "a second later, the damage");

            ticks(tanked, 320);
            List<String> breathing = SpaceQueryTests.life(tanked);
            h.assertTrue(
                    breathing.get(0).contains(": breathable, breathing from internals"), "on a tank: " + breathing);
            h.assertValueEqual(tanked.getAirSupply(), tanked.getMaxAirSupply(), "on a tank, the meter");
            h.assertValueEqual(line(tanked, DAMAGE), DAMAGE + "-", "on a tank, the damage");
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
            // Sends the draws of other tests' breathers in the overworld, so the flush below holds
            // only this test's: nothing else runs between the two.
            Respiration.flush(lab.r.level);
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

    // ---- LIFE-022: under water ---------------------------------------------------------

    /**
     * A player under water in a zone of breathable air drowns as in vanilla (LIFE-022): the zone's
     * air doesn't reach it, it loses 1 air a tick, and 20 ticks after its air runs out it takes 2 HP
     * of drowning damage and its meter restarts from 0.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_022_a_submerged_player_in_a_zone_drowns(GameTestHelper h) {
        try (Lab lab = new Lab(h, 221)) {
            lab.room(0, AIR);
            lab.r.set(2, 1, 2, Blocks.WATER.defaultBlockState());
            lab.r.set(2, 2, 2, Blocks.WATER.defaultBlockState());
            lab.r.ticks(2);
            FakePlayer p = TestPlayers.hurtable(lab.r.level, "zas_life_022");
            p.moveTo(Vec3.atBottomCenterOf(lab.r.abs(2, 1, 2)));
            float full = p.getHealth();
            int air = p.getMaxAirSupply();

            ticks(p, 1);
            h.assertValueEqual(Atmospheres.of(p).state(), AtmosphereState.ZONED, "the atmosphere at its eyes");
            h.assertTrue(
                    Atmospheres.of(p).partialPressures().get(StandardAir.OXYGEN) > BreathingConfig.oxygenMinimum(),
                    "the zone's air isn't breathable");
            BreathState state = p.getData(Breathers.STATE);
            h.assertValueEqual(state.verdict().reason(), BreathReason.DROWNING, "the verdict's reason");
            h.assertValueEqual(state.verdict().source(), BreathSource.FLUID, "the verdict's source");
            h.assertValueEqual(p.getAirSupply(), air - 1, "air after a tick");

            ticks(p, air - 1 + 19);
            h.assertValueEqual(p.getAirSupply(), -19, "air a tick before it drowns");
            h.assertValueEqual(p.getHealth(), full, "health before it drowns");

            ticks(p, 1);
            h.assertValueEqual(p.getAirSupply(), 0, "air once it drowns");
            close(h, p.getHealth(), full - 2, 1e-4, "health once it drowns");
            h.assertTrue(p.getLastDamageSource().is(DamageTypes.DROWN), "the damage type");
        }
        h.succeed();
    }

    // ---- LIFE-023: the client breathing sync -------------------------------------------

    /**
     * The synced state carries the verdict without its breath gas, and the drain only while the
     * player is short of O₂; it survives the network codec unchanged (LIFE-023).
     */
    @GameTest(template = "empty")
    public static void life_023_sync_payload_round_trips(GameTestHelper h) {
        Breathability verdict = Breathability.builder(BreathReason.LOW_OXYGEN, BreathSource.ATMOSPHERE)
                .breath(breath(StandardAir.OXYGEN, 8))
                .hazard(Hazard.TOXIC_GAS)
                .hazard(Hazard.COLD)
                .protection(Protection.PRESSURE)
                .protection(Protection.IMMUNE)
                .build();
        BreathSyncPayload hypoxic = BreathSyncPayload.of(verdict, 0.5);
        h.assertTrue(hypoxic.verdict().breath().isEmpty(), "the breath gas isn't synced");
        h.assertValueEqual(hypoxic.verdict().hazards(), Set.of(Hazard.TOXIC_GAS, Hazard.COLD), "hazards");
        h.assertValueEqual(
                hypoxic.verdict().protection(), Set.of(Protection.PRESSURE, Protection.IMMUNE), "protection");
        h.assertTrue(hypoxic.hypoxic(), "short of O2");
        BreathSyncPayload breathing =
                BreathSyncPayload.of(Breathability.of(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE), 0.5);
        h.assertValueEqual(breathing.drain(), 0.0, "no drain in breathable air");
        h.assertValueEqual(BreathSyncPayload.of(null, 1), BreathSyncPayload.NONE, "no verdict");
        for (BreathSyncPayload p : List.of(hypoxic, breathing, BreathSyncPayload.NONE)) {
            ByteBuf buf = Unpooled.buffer();
            BreathSyncPayload.STREAM_CODEC.encode(buf, p);
            h.assertValueEqual(BreathSyncPayload.STREAM_CODEC.decode(buf), p, "after the codec");
            h.assertValueEqual(buf.readableBytes(), 0, "bytes left over");
        }
        h.succeed();
    }

    /**
     * A player's state is synced at its first check, again when it changes, and not while it
     * stays the same; changes go at most once every 5 ticks, 4 a second (LIFE-023, DATA-005).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_023_player_state_syncs_on_change_at_most_4_a_second(GameTestHelper h) {
        try (Lab lab = new Lab(h, 218)) {
            lab.room(0, Map.of());
            lab.room(6, AIR);
            h.assertValueEqual(new BreathState().syncDue(0), BreathSyncPayload.NONE, "a fresh state syncs at once");
            FakePlayer player = lab.player(0);
            BreathState state = Breathers.tickAt(player, 1000);
            BreathSyncPayload first = state.sent();
            h.assertTrue(first != null && first.hypoxic(), "synced at the first check: " + first);
            h.assertValueEqual(first.drain(), 1.0, "the drain in a vacuum");
            h.assertTrue(state.syncDue(1001) == null, "nothing new to sync");
            player.moveTo(Vec3.atBottomCenterOf(lab.r.abs(8, 1, 2)));
            // At the same game time, so the check counts no seconds and leaves no respiration draw
            // pending for another test's flush.
            Breathers.checkAt(player, 1000);
            h.assertValueEqual(state.verdict().reason(), BreathReason.BREATHABLE, "in the air room");
            h.assertTrue(state.syncDue(1002) == null, "2 ticks after the last sync");
            h.assertTrue(state.syncDue(1004) == null, "4 ticks after the last sync");
            BreathSyncPayload second = state.syncDue(1005);
            h.assertValueEqual(second, BreathSyncPayload.of(Breathers.verdict(player), 0), "5 ticks after");
            h.assertTrue(second.verdict().breath().isEmpty(), "no breath gas");
            h.assertTrue(state.syncDue(1006) == null, "nothing new after");
        }
        h.succeed();
    }

    /**
     * Fed the server's synced state, the client's breathing hook drains the air bar tick for tick
     * as the server drains the meter, in a vacuum and at 8 kPa of O₂, and holds it at empty without
     * drowning, so the bar doesn't jitter (LIFE-023).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_023_client_drains_the_air_bar_as_the_server_does(GameTestHelper h) {
        try (Lab lab = new Lab(h, 219)) {
            lab.room(0, Map.of());
            lab.room(6, Map.of(StandardAir.OXYGEN, 8.0, StandardAir.NITROGEN, 70.0));
            matchClient(h, lab.mob(EntityType.PIG, 0), lab.mob(EntityType.PIG, 12), 340, "a vacuum");
            matchClient(h, lab.mob(EntityType.PIG, 6), lab.mob(EntityType.PIG, 18), 640, "8 kPa of O2");
        }
        h.succeed();
    }

    /** Ticks a server-side breather and a stand-in for its client copy, comparing their air each tick. */
    private static void matchClient(GameTestHelper h, Pig server, Pig client, int ticks, String where) {
        ClientBreath cb = new ClientBreath();
        float health = client.getHealth();
        for (int t = 0; t < ticks; t++) {
            server.baseTick();
            if (t == 0) {
                cb.accept(server.getData(Breathers.STATE).syncState());
            }
            clientTick(cb, client);
            if (client.getAirSupply() != server.getAirSupply()) {
                throw new GameTestAssertException("in " + where + " at tick " + t + " the client's air is "
                        + client.getAirSupply() + ", the server's " + server.getAirSupply());
            }
        }
        h.assertTrue(server.getAirSupply() <= 0, "the meter emptied in " + where);
        h.assertTrue(server.getHealth() < server.getMaxHealth(), "the server dealt hypoxia in " + where);
        h.assertValueEqual(client.getHealth(), health, "the client deals no damage in " + where);
    }

    /** What NeoForge's breathing hook does on the client for an entity whose eyes are out of fluids. */
    private static void clientTick(ClientBreath cb, LivingEntity e) {
        LivingBreatheEvent b = new LivingBreatheEvent(e, true, 1, 4);
        cb.breathe(b, false);
        if (b.canBreathe()) {
            e.setAirSupply(Math.min(e.getAirSupply() + b.getRefillAirAmount(), e.getMaxAirSupply()));
        } else {
            e.setAirSupply(e.getAirSupply() - b.getConsumeAirAmount());
        }
        if (e.getAirSupply() <= 0) {
            LivingDrownEvent d = new LivingDrownEvent(e);
            cb.drown(d, false);
            if (!d.isCanceled() && d.isDrowning()) {
                e.setAirSupply(0);
            }
        }
    }

    /**
     * The client leaves the hook to vanilla before any sync, in breathable air and with the eyes
     * in a fluid, and its verdict is the server's only while the eyes are where the server last
     * checked them (LIFE-023, API-014).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_023_client_leaves_air_and_fluids_to_vanilla(GameTestHelper h) {
        try (Lab lab = new Lab(h, 220)) {
            lab.room(0, Map.of());
            Pig pig = lab.mob(EntityType.PIG, 0);
            ClientBreath cb = new ClientBreath();
            h.assertTrue(!cb.breathe(new LivingBreatheEvent(pig, true, 1, 4), false), "before any sync");
            h.assertValueEqual(cb.verdict(pig), Breathing.vanilla(pig), "the verdict before any sync");
            cb.accept(BreathSyncPayload.of(Breathability.of(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE), 0));
            h.assertTrue(!cb.breathe(new LivingBreatheEvent(pig, true, 1, 4), false), "in breathable air");
            Breathability hypoxic = Breathability.of(BreathReason.LOW_OXYGEN, BreathSource.ATMOSPHERE);
            cb.accept(BreathSyncPayload.of(hypoxic, 1));
            LivingBreatheEvent wet = new LivingBreatheEvent(pig, false, 1, 0);
            h.assertTrue(!cb.breathe(wet, true), "with the eyes in water");
            h.assertValueEqual(wet.getConsumeAirAmount(), 1, "vanilla's drain in water");
            pig.setAirSupply(-20);
            LivingDrownEvent drowning = new LivingDrownEvent(pig);
            cb.drown(drowning, true);
            h.assertTrue(!drowning.isCanceled(), "drowning in water stays vanilla's");
            h.assertValueEqual(cb.verdict(pig), hypoxic, "the server's verdict, out of water");
            lab.r.set(2, 1, 2, Blocks.WATER.defaultBlockState());
            lab.r.set(2, 2, 2, Blocks.WATER.defaultBlockState());
            pig.baseTick();
            h.assertValueEqual(cb.verdict(pig).source(), BreathSource.FLUID, "vanilla's verdict once in water");
            cb.clear();
            h.assertValueEqual(cb.state(), BreathSyncPayload.NONE, "cleared");
        }
        h.succeed();
    }
}
