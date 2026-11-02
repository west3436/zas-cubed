package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.config.HazardConfig;
import io.github.west3436.zas.life.BreathState;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.CheckBudget;
import io.github.west3436.zas.life.EnvironmentHazards;
import io.github.west3436.zas.life.HazardType;
import io.github.west3436.zas.life.LifeDamage;
import io.github.west3436.zas.life.Shielding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The pressure and temperature hazards (LIFE-010, LIFE-011, LIFE-014, LIFE-015, LIFE-016), the
 * damage types (LIFE-030, LIFE-035), the hazard settings (LIFE-031, LIFE-036), the creative and
 * spectator exemption (LIFE-037), and the checks' stagger and cap (LIFE-033, LIFE-038).
 *
 * <p>Like {@code BreathingTests}, each test builds sealed rooms in its own area ({@link Rooms},
 * areas 240 and up) whose chunks don't tick, and drives the checks itself with {@link
 * Breathers#checkAt}, {@link Breathers#tickAt} and {@link Breathers#exposeForTest}. Zombies stand
 * in for the unprotected entity: they don't breathe, so only pressure and temperature harm them.
 * Each test runs within one game tick and puts back the settings it changes.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HazardTests {
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");

    private HazardTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    private static final class Lab implements AutoCloseable {
        final Rooms r;
        final GameTestHelper h;
        final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> restore = new ArrayList<>();

        Lab(GameTestHelper h, int index) {
            this.h = h;
            this.r = new Rooms(h, index);
        }

        /**
         * Builds a sealed room from x0 to x0 + 4, forms its zone, and fills it with partial
         * pressures in kPa at a temperature; an empty map leaves it a vacuum.
         */
        void room(int x0, Map<ResourceLocation, Double> kPa, double temperature) {
            r.clear(x0, x0 + 5);
            r.shell(x0, x0 + 4);
            r.settle(GasMutations.remove(
                    r.level,
                    r.point(x0 + 2, 2, 2),
                    Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                    Rooms.source("hazards")));
            if (!kPa.isEmpty()) {
                Map<ResourceLocation, Double> moles = new HashMap<>();
                kPa.forEach((gas, p) -> moles.put(gas, p * 27_000 / (GasMixture.GAS_CONSTANT * temperature)));
                r.settle(GasMutations.add(
                        r.level, r.point(x0 + 2, 2, 2), new GasParcel(moles, temperature), Rooms.source("hazards")));
            }
        }

        <T extends Mob> T mob(EntityType<T> type, int x0) {
            T e = type.create(r.level);
            h.assertTrue(e != null, "couldn't create a " + type);
            e.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
            e.setNoAi(true);
            r.level.addFreshEntity(e);
            entities.add(e);
            return e;
        }

        FakePlayer player(int x0) {
            FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_hazards"));
            p.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
            return p;
        }

        /** Switches a hazard on or off until the test ends. */
        void enabled(HazardType t, boolean on) {
            boolean before = HazardConfig.ENABLED.get(t).get();
            restore.add(() -> HazardConfig.ENABLED.get(t).set(before));
            HazardConfig.ENABLED.get(t).set(on);
        }

        /** Sets a hazard's scale until the test ends. */
        void scale(HazardType t, double scale) {
            double before = HazardConfig.SCALE.get(t).get();
            restore.add(() -> HazardConfig.SCALE.get(t).set(before));
            HazardConfig.SCALE.get(t).set(scale);
        }

        /** Sets the per-dimension overrides until the test ends. */
        void overrides(List<String> entries) {
            List<? extends String> before = HazardConfig.DIMENSION_OVERRIDES.get();
            restore.add(() -> HazardConfig.DIMENSION_OVERRIDES.set(before));
            HazardConfig.DIMENSION_OVERRIDES.set(entries);
        }

        @Override
        public void close() {
            entities.forEach(Entity::discard);
            for (int i = restore.size() - 1; i >= 0; i--) {
                restore.get(i).run();
            }
        }
    }

    /** A cell of nitrogen at a pressure in kPa and a temperature. */
    private static GasMixture at(double kPa, double temperature) {
        if (kPa == 0) {
            return new GasMixture(Map.of(), temperature, StandardAir.CELL_VOLUME);
        }
        double moles = kPa * StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * temperature);
        return new GasMixture(Map.of(StandardAir.NITROGEN, moles), temperature, StandardAir.CELL_VOLUME);
    }

    /** Two checks 2 s apart; the second charges 2 s. */
    private static void twoSeconds(LivingEntity e, long t) {
        Breathers.checkAt(e, t);
        Breathers.checkAt(e, t + 40);
    }

    private static float lost(LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    // ---- LIFE-015, LIFE-016: who takes them --------------------------------------------

    /**
     * In a vacuum zone a zombie, which doesn't breathe, takes decompression damage; an iron golem,
     * in {@code zas:atmosphere_immune}, takes none and isn't checked at all (LIFE-015).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_015_zombie_takes_vacuum_damage_and_an_immune_mob_doesnt(GameTestHelper h) {
        try (Lab lab = new Lab(h, 240)) {
            lab.room(0, Map.of(), StandardAir.TEMPERATURE);
            Zombie zombie = lab.mob(EntityType.ZOMBIE, 0);
            IronGolem golem = lab.mob(EntityType.IRON_GOLEM, 0);
            long t = lab.r.level.getGameTime();
            twoSeconds(zombie, t);
            twoSeconds(golem, t);
            EnvironmentHazards.Exposure x = BreathingRules.assess(zombie).exposure();
            h.assertTrue(x != null, "no exposure for the zombie");
            close(h, x.decompression(), 1, 0, "the zombie's decompression");
            close(h, lost(zombie), 2 * (x.decompression() + x.frostbite()), 1e-4, "the zombie's damage in 2 s");
            h.assertTrue(zombie.getLastDamageSource() != null, "the zombie wasn't hurt");
            h.assertValueEqual(golem.getHealth(), golem.getMaxHealth(), "the golem's health");
            Breathability zombieVerdict = Breathing.of(zombie);
            h.assertValueEqual(zombieVerdict.reason(), BreathReason.DOES_NOT_BREATHE, "the zombie's reason");
            h.assertTrue(
                    zombieVerdict.hazards().contains(Hazard.LOW_PRESSURE), "the zombie's hazards: " + zombieVerdict);
            Breathability golemVerdict = Breathing.of(golem);
            h.assertTrue(golemVerdict.hazards().isEmpty(), "the golem's hazards: " + golemVerdict);
            h.assertTrue(golemVerdict.protection().contains(Protection.IMMUNE), "the golem's protection");
            IronGolem fresh = lab.mob(EntityType.IRON_GOLEM, 0);
            fresh.baseTick();
            h.assertTrue(fresh.getExistingDataOrNull(Breathers.STATE) == null, "an immune mob was checked");
        }
        h.succeed();
    }

    /** The shipped {@code zas:atmosphere_immune} holds the bosses and the golems (LIFE-016). */
    @GameTest(template = "empty")
    public static void life_016_default_immune_tag(GameTestHelper h) {
        for (EntityType<?> type :
                List.of(EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM)) {
            h.assertTrue(type.is(EnvironmentHazards.IMMUNE), type + " isn't immune");
        }
        for (EntityType<?> type : List.of(EntityType.ZOMBIE, EntityType.PIG, EntityType.PLAYER, EntityType.VILLAGER)) {
            h.assertFalse(type.is(EnvironmentHazards.IMMUNE), type + " is immune");
        }
        h.succeed();
    }

    // ---- LIFE-010, LIFE-014: pressure --------------------------------------------------

    /** At 15 kPa an unprotected zombie takes 1 HP/s of decompression; at 25 kPa none (LIFE-010). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_010_low_pressure_deals_1_hp_per_second(GameTestHelper h) {
        try (Lab lab = new Lab(h, 241)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 15.0), StandardAir.TEMPERATURE);
            lab.room(6, Map.of(StandardAir.NITROGEN, 25.0), StandardAir.TEMPERATURE);
            Zombie low = lab.mob(EntityType.ZOMBIE, 0);
            Zombie fine = lab.mob(EntityType.ZOMBIE, 6);
            long t = lab.r.level.getGameTime();
            twoSeconds(low, t);
            twoSeconds(fine, t);
            close(h, lost(low), 2, 1e-4, "damage in 2 s at 15 kPa");
            h.assertTrue(low.getLastDamageSource().is(LifeDamage.DECOMPRESSION), "the damage type");
            close(h, lost(fine), 0, 0, "damage in 2 s at 25 kPa");
            h.assertTrue(Breathing.of(low).hazards().contains(Hazard.LOW_PRESSURE), "the verdict at 15 kPa");
            h.assertTrue(Breathing.of(fine).isProtected(), "the verdict at 25 kPa");
        }
        h.succeed();
    }

    /**
     * Barotrauma: 1 HP/s at 825 kPa and the 2 HP/s cap at 1,650 kPa; nothing just under 550 kPa
     * (LIFE-014).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_014_high_pressure(GameTestHelper h) {
        try (Lab lab = new Lab(h, 242)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 825.0), StandardAir.TEMPERATURE);
            lab.room(6, Map.of(StandardAir.NITROGEN, 1650.0), StandardAir.TEMPERATURE);
            lab.room(12, Map.of(StandardAir.NITROGEN, 545.0), StandardAir.TEMPERATURE);
            Zombie at825 = lab.mob(EntityType.ZOMBIE, 0);
            Zombie at1650 = lab.mob(EntityType.ZOMBIE, 6);
            Zombie at545 = lab.mob(EntityType.ZOMBIE, 12);
            long t = lab.r.level.getGameTime();
            for (Zombie z : List.of(at825, at1650, at545)) {
                twoSeconds(z, t);
            }
            close(h, lost(at825), 2, 1e-3, "damage in 2 s at 825 kPa");
            h.assertTrue(at825.getLastDamageSource().is(LifeDamage.BAROTRAUMA), "the damage type");
            close(h, lost(at1650), 4, 1e-4, "damage in 2 s at 1650 kPa");
            close(h, lost(at545), 0, 0, "damage in 2 s at 545 kPa");
            h.assertTrue(Breathing.of(at825).hazards().contains(Hazard.HIGH_PRESSURE), "the verdict at 825 kPa");
            close(h, EnvironmentHazards.barotrauma(549.99), 0, 0, "just under 550 kPa");
            close(h, EnvironmentHazards.barotrauma(550), 0, 0, "at 550 kPa");
            close(h, EnvironmentHazards.barotrauma(5000), 2, 0, "at 5000 kPa");
        }
        h.succeed();
    }

    // ---- LIFE-011: temperature ---------------------------------------------------------

    /** Each tier of frostbite and burns, at 1 atm, with the default comfort band (LIFE-011). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_011_temperature_tiers(GameTestHelper h) {
        try (Lab lab = new Lab(h, 243)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 101.325), StandardAir.TEMPERATURE);
            Zombie zombie = lab.mob(EntityType.ZOMBIE, 0);
            Map<Double, double[]> rows = new LinkedHashMap<>(); // T -> {frostbite, burns}
            rows.put(293.15, new double[] {0, 0});
            rows.put(220.0, new double[] {0, 0});
            rows.put(219.0, new double[] {0.5, 0});
            rows.put(200.0, new double[] {0.5, 0});
            rows.put(199.0, new double[] {1, 0});
            rows.put(120.0, new double[] {1, 0});
            rows.put(119.0, new double[] {2, 0});
            rows.put(2.7, new double[] {2, 0});
            rows.put(360.0, new double[] {0, 0});
            rows.put(361.0, new double[] {0, 1});
            rows.put(400.0, new double[] {0, 1});
            rows.put(401.0, new double[] {0, 2});
            rows.put(1000.0, new double[] {0, 2});
            rows.put(1001.0, new double[] {0, 4});
            rows.forEach((temperature, expected) -> {
                EnvironmentHazards.Exposure x =
                        EnvironmentHazards.exposure(Level.OVERWORLD, at(101.325, temperature), Shielding.NONE);
                close(h, x.frostbite(), expected[0], 0, "frostbite at " + temperature + " K");
                close(h, x.burns(), expected[1], 0, "burns at " + temperature + " K");
                close(h, x.decompression() + x.barotrauma(), 0, 0, "pressure damage at 1 atm");
            });
            Breathers.exposeForTest(zombie, at(101.325, 150), 2);
            close(h, lost(zombie), 2, 1e-4, "2 s at 150 K");
            h.assertTrue(zombie.getLastDamageSource().is(LifeDamage.FROSTBITE), "the cold's damage type");
            zombie.setHealth(zombie.getMaxHealth());
            Breathers.exposeForTest(zombie, at(101.325, 450), 2);
            close(h, lost(zombie), 4, 1e-4, "2 s at 450 K");
            h.assertTrue(zombie.getLastDamageSource().is(LifeDamage.BURNS), "the heat's damage type");
        }
        h.succeed();
    }

    /** A room of air at 450 K burns an unprotected zombie at 2 HP/s, by its own checks (LIFE-011). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_011_a_hot_room_burns(GameTestHelper h) {
        try (Lab lab = new Lab(h, 244)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 80.0, StandardAir.OXYGEN, 21.0), 450);
            Zombie zombie = lab.mob(EntityType.ZOMBIE, 0);
            twoSeconds(zombie, lab.r.level.getGameTime());
            close(h, lost(zombie), 4, 1e-3, "damage in 2 s at 450 K");
            h.assertTrue(zombie.getLastDamageSource().is(LifeDamage.BURNS), "the damage type");
            h.assertTrue(Breathing.of(zombie).hazards().contains(Hazard.HEAT), "the verdict");
        }
        h.succeed();
    }

    /**
     * An unprotected entity in a 2.7 K vacuum takes 3 HP/s: 1 of decompression and 2 of frostbite
     * (LIFE-011, LIFE-010).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_011_unprotected_in_vacuum_takes_3_hp_per_second(GameTestHelper h) {
        try (Lab lab = new Lab(h, 245)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 101.325), StandardAir.TEMPERATURE);
            Zombie zombie = lab.mob(EntityType.ZOMBIE, 0);
            EnvironmentHazards.Exposure x = Breathers.exposeForTest(zombie, at(0, 2.7), 1);
            close(h, x.decompression(), 1, 0, "decompression");
            close(h, x.frostbite(), 2, 0, "frostbite");
            close(h, lost(zombie), 3, 1e-4, "damage in 1 s");
        }
        h.succeed();
    }

    // ---- LIFE-030, LIFE-035: damage types ----------------------------------------------

    /** Each hazard has its own damage type with its own death message (LIFE-030). */
    @GameTest(template = "empty")
    public static void life_030_damage_types(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (HazardType t : HazardType.values()) {
            ResourceKey<DamageType> key = t.damageType();
            h.assertValueEqual(key.location().getPath(), t.id(), "the damage type of " + t);
            DamageType type = registry.get(key);
            h.assertTrue(type != null, "no damage type " + key.location());
            h.assertValueEqual(type.msgId(), "zas." + t.id(), "the message of " + key.location());
        }
        h.succeed();
    }

    /**
     * Every atmospheric damage type but airflow impact ignores armor: a zombie in diamond armor
     * takes what a bare one does (LIFE-035).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_035_armor_bypass(GameTestHelper h) {
        try (Lab lab = new Lab(h, 246)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 101.325), StandardAir.TEMPERATURE);
            for (HazardType t : HazardType.values()) {
                Zombie bare = lab.mob(EntityType.ZOMBIE, 0);
                Zombie armored = lab.mob(EntityType.ZOMBIE, 0);
                armored.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                armored.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                armored.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                for (Zombie z : List.of(bare, armored)) {
                    z.tick(); // equipment's attributes apply on a tick
                }
                h.assertTrue(
                        armored.getArmorValue() >= bare.getArmorValue() + 20,
                        "the armored zombie's armor: " + armored.getArmorValue());
                for (Zombie z : List.of(bare, armored)) {
                    z.hurt(LifeDamage.source(z, t.damageType()), 6);
                }
                boolean bypasses = LifeDamage.source(bare, t.damageType()).is(DamageTypeTags.BYPASSES_ARMOR);
                if (t == HazardType.AIRFLOW_IMPACT) {
                    h.assertFalse(bypasses, "airflow impact bypasses armor");
                    h.assertTrue(lost(armored) < lost(bare), "armor doesn't reduce airflow impact");
                } else {
                    h.assertTrue(bypasses, t + " doesn't bypass armor");
                    close(h, lost(armored), lost(bare), 1e-4, t + " with armor");
                    close(h, lost(bare), 6, 1e-4, t + " without armor");
                }
            }
        }
        h.succeed();
    }

    // ---- LIFE-031, LIFE-036: settings --------------------------------------------------

    /** Each pressure and temperature hazard can be turned off and scaled (LIFE-031). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_031_pressure_and_temperature_hazards_turn_off_and_scale(GameTestHelper h) {
        try (Lab lab = new Lab(h, 247)) {
            Map<HazardType, GasMixture> where = Map.of(
                    HazardType.DECOMPRESSION, at(0, StandardAir.TEMPERATURE),
                    HazardType.BAROTRAUMA, at(825, StandardAir.TEMPERATURE),
                    HazardType.FROSTBITE, at(101.325, 150),
                    HazardType.BURNS, at(101.325, 370));
            where.forEach((t, around) -> {
                double base = EnvironmentHazards.exposure(Level.OVERWORLD, around, Shielding.NONE)
                        .rate(t);
                h.assertTrue(base > 0, t + " doesn't harm");
                lab.scale(t, 2.5);
                close(
                        h,
                        EnvironmentHazards.exposure(Level.OVERWORLD, around, Shielding.NONE)
                                .rate(t),
                        base * 2.5,
                        1e-9,
                        t + " scaled by 2.5");
                lab.enabled(t, false);
                h.assertValueEqual(
                        EnvironmentHazards.exposure(Level.OVERWORLD, around, Shielding.NONE)
                                .rate(t),
                        0.0,
                        t + " turned off");
            });
            lab.scale(HazardType.AIRFLOW_IMPACT, 0.5);
            close(h, HazardConfig.scale(Level.OVERWORLD, HazardType.AIRFLOW_IMPACT), 0.5, 0, "airflow impact's scale");
            lab.enabled(HazardType.AIRFLOW_IMPACT, false);
            close(h, HazardConfig.scale(Level.OVERWORLD, HazardType.AIRFLOW_IMPACT), 0, 0, "airflow impact off");
        }
        h.succeed();
    }

    /**
     * Toxic gas: 0.5 kPa of CO deals 0.5 HP/s and Impaired (LIFE-041); scaled by 2 it deals 1 HP/s
     * with the same effects, and off it deals nothing and isn't a hazard (LIFE-031).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_031_toxic_gas_turns_off_and_scales(GameTestHelper h) {
        try (Lab lab = new Lab(h, 248)) {
            Map<ResourceLocation, Double> air = new LinkedHashMap<>();
            air.put(StandardAir.NITROGEN, 80.0);
            air.put(StandardAir.OXYGEN, 21.0);
            air.put(CO, 0.5);
            lab.room(0, air, StandardAir.TEMPERATURE);
            long t = lab.r.level.getGameTime();
            lab.scale(HazardType.TOXIC_GAS, 2);
            Pig scaled = lab.mob(EntityType.PIG, 0);
            twoSeconds(scaled, t);
            close(h, lost(scaled), 2, 1e-3, "2 s of CO scaled by 2");
            h.assertFalse(scaled.getActiveEffects().isEmpty(), "no effects when scaled");
            lab.enabled(HazardType.TOXIC_GAS, false);
            Pig off = lab.mob(EntityType.PIG, 0);
            twoSeconds(off, t);
            close(h, lost(off), 0, 0, "2 s of CO turned off");
            h.assertTrue(off.getActiveEffects().isEmpty(), "effects while off");
            h.assertFalse(Breathing.of(off).hazards().contains(Hazard.TOXIC_GAS), "toxic gas listed while off");
        }
        h.succeed();
    }

    /**
     * Hypoxia: turned off, a breath with no O2 is breathable and the meter doesn't drain; scaled by
     * 0.5, an empty meter deals 1 HP/s (LIFE-031).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_031_hypoxia_turns_off_and_scales(GameTestHelper h) {
        try (Lab lab = new Lab(h, 249)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 101.325), StandardAir.TEMPERATURE);
            lab.scale(HazardType.HYPOXIA, 0.5);
            Pig scaled = lab.mob(EntityType.PIG, 0);
            for (int i = 0; i < 320; i++) {
                scaled.baseTick();
            }
            h.assertValueEqual(scaled.getAirSupply(), 0, "the meter after 16 s");
            close(h, lost(scaled), 1, 1e-4, "an empty meter's damage in 1 s scaled by 0.5");
            h.assertTrue(scaled.getLastDamageSource().is(LifeDamage.HYPOXIA), "the damage type");
            lab.enabled(HazardType.HYPOXIA, false);
            Pig off = lab.mob(EntityType.PIG, 0);
            for (int i = 0; i < 20; i++) {
                off.baseTick();
            }
            h.assertValueEqual(off.getAirSupply(), off.getMaxAirSupply(), "the meter with hypoxia off");
            h.assertValueEqual(Breathing.of(off).reason(), BreathReason.BREATHABLE, "the reason with hypoxia off");
        }
        h.succeed();
    }

    /**
     * An override turns decompression off and halves burns in the Overworld, while the Nether keeps
     * them; malformed entries are refused (LIFE-036).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_036_per_dimension_overrides(GameTestHelper h) {
        try (Lab lab = new Lab(h, 250)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 101.325), StandardAir.TEMPERATURE);
            lab.overrides(List.of("minecraft:overworld decompression off", "minecraft:overworld burns 0.5"));
            Zombie zombie = lab.mob(EntityType.ZOMBIE, 0);
            EnvironmentHazards.Exposure vacuum = Breathers.exposeForTest(zombie, at(0, StandardAir.TEMPERATURE), 2);
            close(h, vacuum.decompression(), 0, 0, "decompression in the Overworld");
            close(h, lost(zombie), 0, 0, "damage in an Overworld vacuum");
            close(
                    h,
                    EnvironmentHazards.exposure(Level.OVERWORLD, at(101.325, 450), Shielding.NONE)
                            .burns(),
                    1,
                    0,
                    "burns at 450 K in the Overworld");
            EnvironmentHazards.Exposure nether =
                    EnvironmentHazards.exposure(Level.NETHER, at(0, StandardAir.TEMPERATURE), Shielding.NONE);
            close(h, nether.decompression(), 1, 0, "decompression in the Nether");
            close(
                    h,
                    EnvironmentHazards.exposure(Level.NETHER, at(101.325, 450), Shielding.NONE)
                            .burns(),
                    2,
                    0,
                    "burns at 450 K in the Nether");
            h.assertTrue(HazardConfig.enabled(Level.OVERWORLD, HazardType.BURNS), "burns are on in the Overworld");
            for (String bad : List.of(
                    "minecraft:overworld",
                    "minecraft:overworld lava off",
                    "minecraft:overworld burns maybe",
                    "minecraft:overworld burns -1",
                    "Not A Dimension burns off",
                    "minecraft:overworld burns off extra")) {
                h.assertTrue(HazardConfig.parse(bad).isEmpty(), "accepted \"" + bad + "\"");
            }
        }
        h.succeed();
    }

    // ---- LIFE-037: creative and spectator ----------------------------------------------

    /**
     * Creative and spectator players in a vacuum are exempt: no hazards, no exposure to charge, and
     * a full meter. A survival player there has the hazards (LIFE-037). (Fake players can't be hurt
     * at all, so the test checks what the checks would charge rather than their health.)
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_037_creative_and_spectator_are_exempt(GameTestHelper h) {
        try (Lab lab = new Lab(h, 251)) {
            lab.room(0, Map.of(), StandardAir.TEMPERATURE);
            FakePlayer survival = lab.player(0);
            FakePlayer creative = lab.player(0);
            creative.setGameMode(GameType.CREATIVE);
            FakePlayer spectator = lab.player(0);
            spectator.setGameMode(GameType.SPECTATOR);
            BreathingRules.Assessment s = BreathingRules.assess(survival);
            h.assertTrue(s.exposure() != null, "no exposure for the survival player");
            h.assertTrue(s.verdict().hazards().contains(Hazard.LOW_PRESSURE), "the survival player's hazards");
            for (FakePlayer p : List.of(creative, spectator)) {
                BreathingRules.Assessment a = BreathingRules.assess(p);
                h.assertValueEqual(a.verdict().reason(), BreathReason.EXEMPT, p.gameMode.getGameModeForPlayer() + "");
                h.assertTrue(a.exposure() == null, "an exposure for " + p.gameMode.getGameModeForPlayer());
                h.assertTrue(a.verdict().hazards().isEmpty(), "hazards for " + p.gameMode.getGameModeForPlayer());
                h.assertTrue(a.verdict().protection().contains(Protection.EXEMPT), "not marked exempt");
                for (int i = 0; i < 20; i++) {
                    p.baseTick();
                }
                h.assertValueEqual(p.getAirSupply(), p.getMaxAirSupply(), "air in a vacuum");
            }
        }
        h.succeed();
    }

    // ---- LIFE-033, LIFE-038: cadence and cap -------------------------------------------

    /**
     * 100 breathers' checks spread evenly over the 2 s interval: each is checked once per
     * interval, and no tick checks more than 3 (LIFE-033).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_033_checks_spread_across_the_interval(GameTestHelper h) {
        try (Lab lab = new Lab(h, 252)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 80.0, StandardAir.OXYGEN, 21.0), StandardAir.TEMPERATURE);
            List<Pig> pigs = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                pigs.add(lab.mob(EntityType.PIG, 0));
            }
            CheckBudget budget = new CheckBudget(() -> 1000);
            long t0 = lab.r.level.getGameTime();
            for (Pig p : pigs) {
                Breathers.tickAt(p, t0, budget);
            }
            int interval = BreathingConfig.checkIntervalTicks();
            Map<Pig, Integer> checks = new HashMap<>();
            int most = 0;
            for (long t = t0 + 1; t <= t0 + interval; t++) {
                int thisTick = 0;
                for (Pig p : pigs) {
                    if (Breathers.tickAt(p, t, budget).lastCheck() == t) {
                        checks.merge(p, 1, Integer::sum);
                        thisTick++;
                    }
                }
                most = Math.max(most, thisTick);
            }
            h.assertValueEqual(checks.size(), 100, "breathers checked in one interval");
            h.assertTrue(
                    checks.values().stream().allMatch(n -> n == 1), "a breather checked twice: " + checks.values());
            h.assertTrue(most <= 3, "a tick ran " + most + " checks");
        }
        h.succeed();
    }

    /**
     * With a cap of 5, 20 breathers due at once are checked 5 a tick over 4 ticks, and the rest
     * wait without losing their check; the cap is configurable, 100 by default (LIFE-038).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_038_checks_per_tick_are_capped(GameTestHelper h) {
        try (Lab lab = new Lab(h, 253)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 80.0, StandardAir.OXYGEN, 21.0), StandardAir.TEMPERATURE);
            h.assertValueEqual(BreathingConfig.maxChecksPerTick(), 100, "the default cap");
            List<Pig> pigs = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                pigs.add(lab.mob(EntityType.PIG, 0));
            }
            CheckBudget budget = new CheckBudget(() -> 5);
            long t0 = lab.r.level.getGameTime();
            List<Integer> perTick = new ArrayList<>();
            for (long t = t0; t < t0 + 8; t++) {
                int n = 0;
                for (Pig p : pigs) {
                    BreathState s = Breathers.tickAt(p, t, budget);
                    if (s.lastCheck() == t) {
                        n++;
                    }
                }
                perTick.add(n);
                h.assertTrue(budget.used(t) <= 5, "the budget at " + (t - t0) + ": " + budget.used(t));
            }
            h.assertValueEqual(perTick.subList(0, 4), List.of(5, 5, 5, 5), "checks per tick");
            h.assertTrue(
                    pigs.stream()
                            .allMatch(p -> p.getData(Breathers.STATE).verdict() != null
                                    && !p.getData(Breathers.STATE).deferred()),
                    "a breather still waits");
        }
        h.succeed();
    }
}
