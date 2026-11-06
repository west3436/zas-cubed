package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.capability.ILifeSupport;
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
import io.github.west3436.zas.config.ProtectionConfig;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.EnvironmentHazards;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.Protections;
import io.github.west3436.zas.life.Respiration;
import io.github.west3436.zas.life.Shielding;
import io.github.west3436.zas.life.WornProtection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Worn and built-in protection (LIFE-020, LIFE-024 to LIFE-027) and the life-support capability
 * (API-022).
 *
 * <p>The capability comes from {@link TestLifeSupport}'s test item and tagged zombies; the data
 * from the GameTest pack's {@code data/zas_gametest/zas/protection/} files and the shipped vanilla
 * armor file. Like {@code HazardTests}, the tests that need rooms build them in their own areas
 * ({@link Rooms}, areas 260 and up), and each runs within one game tick.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectionTests {
    private static final EquipmentSlot[] ARMOR = ProtectionConfig.SLOTS;
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
    private static final ResourceLocation N2O = ResourceLocation.fromNamespaceAndPath("zas", "nitrous_oxide");

    private ProtectionTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    private static final class Lab implements AutoCloseable {
        final Rooms r;
        final GameTestHelper h;
        final List<Entity> entities = new ArrayList<>();

        Lab(GameTestHelper h, int index) {
            this.h = h;
            this.r = new Rooms(h, index);
        }

        /** A sealed room from x0 to x0 + 4 of nitrogen at a pressure in kPa and a temperature. */
        void room(int x0, double kPa, double temperature) {
            r.clear(x0, x0 + 5);
            r.shell(x0, x0 + 4);
            r.settle(GasMutations.remove(
                    r.level,
                    r.point(x0 + 2, 2, 2),
                    Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                    Rooms.source("protection")));
            if (kPa > 0) {
                Map<ResourceLocation, Double> moles = new HashMap<>();
                moles.put(StandardAir.NITROGEN, kPa * 27_000 / (GasMixture.GAS_CONSTANT * temperature));
                r.settle(GasMutations.add(
                        r.level, r.point(x0 + 2, 2, 2), new GasParcel(moles, temperature), Rooms.source("protection")));
            }
        }

        /** A sealed room from x0 to x0 + 4 holding these moles at 293.15 K. */
        void room(int x0, Map<ResourceLocation, Double> moles) {
            r.clear(x0, x0 + 5);
            r.shell(x0, x0 + 4);
            r.settle(GasMutations.remove(
                    r.level,
                    r.point(x0 + 2, 2, 2),
                    Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                    Rooms.source("protection")));
            r.settle(GasMutations.add(
                    r.level,
                    r.point(x0 + 2, 2, 2),
                    new GasParcel(moles, StandardAir.TEMPERATURE),
                    Rooms.source("protection")));
        }

        Zombie zombie(int x0) {
            return spawn(EntityType.ZOMBIE, x0);
        }

        Pig pig(int x0) {
            return spawn(EntityType.PIG, x0);
        }

        private <T extends Mob> T spawn(EntityType<T> type, int x0) {
            T e = type.create(r.level);
            h.assertTrue(e != null, "couldn't create a " + type);
            e.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
            e.setNoAi(true);
            r.level.addFreshEntity(e);
            entities.add(e);
            return e;
        }

        FakePlayer player(int x0) {
            FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_protection"));
            p.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
            return p;
        }

        /** A player that damage reaches, for a test of what harms it ({@link TestPlayers}). */
        FakePlayer hurtable(int x0) {
            FakePlayer p = TestPlayers.hurtable(r.level, "zas_protection");
            p.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
            return p;
        }

        @Override
        public void close() {
            entities.forEach(Entity::discard);
        }
    }

    /** Puts an item in each of the given armor slots. */
    private static void wear(LivingEntity e, Item item, EquipmentSlot... slots) {
        for (EquipmentSlot slot : slots) {
            e.setItemSlot(slot, new ItemStack(item));
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

    /** An entity's exposure to a mixture with its protection now, in the Overworld. */
    private static EnvironmentHazards.Exposure exposure(LivingEntity e, GasMixture around) {
        return EnvironmentHazards.exposure(Level.OVERWORLD, around, Shielding.of(e));
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    private static ILifeSupport rated(double minP, double maxP) {
        return new ILifeSupport() {
            @Override
            public double minPressure() {
                return minP;
            }

            @Override
            public double maxPressure() {
                return maxP;
            }
        };
    }

    // ---- LIFE-024: coverage weights -----------------------------------------------------

    /** The armor slots cover 0.3, 0.4, 0.2 and 0.1 of the body by default, 1 in all (LIFE-024). */
    @GameTest(template = "empty")
    public static void life_024_coverage_weights(GameTestHelper h) {
        Map<EquipmentSlot, Double> expected = Map.of(
                EquipmentSlot.HEAD, 0.3, EquipmentSlot.CHEST, 0.4, EquipmentSlot.LEGS, 0.2, EquipmentSlot.FEET, 0.1);
        double sum = 0;
        for (EquipmentSlot slot : ARMOR) {
            close(h, ProtectionConfig.coverage(slot), expected.get(slot), 0, slot + "'s coverage");
            close(h, ProtectionConfig.DEFAULT_COVERAGE.get(slot), expected.get(slot), 0, slot + "'s default");
            sum += ProtectionConfig.DEFAULT_COVERAGE.get(slot);
        }
        close(h, sum, 1, 1e-12, "the defaults' sum");
        close(h, ProtectionConfig.coverage(EquipmentSlot.MAINHAND), 0, 0, "the main hand's coverage");
        h.succeed();
    }

    // ---- LIFE-020: pressure protection ----------------------------------------------------

    /**
     * (1 − c⁵) of the difference gets through: all of it bare, none fully covered, 83 % without a
     * helmet; a piece counts on the side of 1 atm it is rated for (LIFE-020).
     */
    @GameTest(template = "empty")
    public static void life_020_pressure_formula(GameTestHelper h) {
        close(h, WornProtection.pressureFactorFor(0), 1, 0, "bare");
        close(h, WornProtection.pressureFactorFor(1), 0, 0, "fully covered");
        close(h, WornProtection.pressureFactorFor(0.7), 1 - Math.pow(0.7, 5), 1e-12, "without a helmet");
        close(h, WornProtection.pressureFactorFor(0.7), 0.83193, 1e-5, "without a helmet, about 83 %");
        close(h, WornProtection.pressureFactorFor(0.5), 1 - 0.03125, 1e-12, "half covered");
        ILifeSupport vacuumOnly = rated(0, StandardAir.PRESSURE);
        h.assertTrue(WornProtection.coversPressure(vacuumOnly, 0), "a vacuum rating covers vacuum");
        h.assertTrue(WornProtection.coversPressure(vacuumOnly, 50), "a vacuum rating covers 50 kPa");
        h.assertFalse(WornProtection.coversPressure(vacuumOnly, 200), "a vacuum rating covers 200 kPa");
        h.assertFalse(WornProtection.coversPressure(vacuumOnly, StandardAir.PRESSURE), "anything covers 1 atm");
        ILifeSupport to30 = rated(30, 600);
        h.assertFalse(WornProtection.coversPressure(to30, 20), "a 30 kPa rating covers 20 kPa");
        h.assertTrue(WornProtection.coversPressure(to30, 600), "a 600 kPa rating covers 600 kPa");
        h.assertFalse(WornProtection.coversPressure(to30, 601), "a 600 kPa rating covers 601 kPa");
        h.assertFalse(WornProtection.coversPressure(new ILifeSupport() {}, 50), "an unrated piece covers 50 kPa");
        h.succeed();
    }

    /**
     * In a vacuum, a player fully suited in vacuum-rated pieces takes no decompression and its
     * verdict lists pressure protection; without the helmet, 83 % of the difference reaches it, an
     * effective 17 kPa, so it still does (LIFE-020).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_020_suited_and_helmetless_players_in_vacuum(GameTestHelper h) {
        try (Lab lab = new Lab(h, 260)) {
            lab.room(0, 0, StandardAir.TEMPERATURE);
            FakePlayer suited = lab.player(0);
            FakePlayer helmetless = lab.player(0);
            wear(suited, TestLifeSupport.item(), ARMOR);
            wear(helmetless, TestLifeSupport.item(), EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
            EnvironmentHazards.Exposure s = BreathingRules.assess(suited).exposure();
            h.assertTrue(s != null, "no exposure for the suited player");
            close(h, s.effectivePressure(), StandardAir.PRESSURE, 1e-9, "the suited player's effective pressure");
            close(h, s.decompression(), 0, 0, "the suited player's decompression");
            Breathability v = Breathing.of(suited);
            h.assertTrue(v.protection().contains(Protection.PRESSURE), "the suited player's protection: " + v);
            h.assertFalse(v.hazards().contains(Hazard.LOW_PRESSURE), "the suited player's hazards: " + v);
            EnvironmentHazards.Exposure x = BreathingRules.assess(helmetless).exposure();
            close(h, x.effectivePressure(), StandardAir.PRESSURE * Math.pow(0.7, 5), 1e-6, "helmetless, effective");
            close(h, x.decompression(), 1, 0, "the helmetless player's decompression");
            Breathability w = Breathing.of(helmetless);
            h.assertTrue(w.protection().contains(Protection.PRESSURE), "the helmetless player's protection: " + w);
            h.assertTrue(w.hazards().contains(Hazard.LOW_PRESSURE), "the helmetless player's hazards: " + w);
        }
        h.succeed();
    }

    // ---- LIFE-025: thermal protection -------------------------------------------------------

    /**
     * At 450 K, burns of 2 HP/s are scaled by (1 − c): none in a full suit, 40 % with the chest and
     * legs covered, 70 % with only the helmet; a verdict lists thermal protection (LIFE-025).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_025_partial_suits_at_450_k(GameTestHelper h) {
        try (Lab lab = new Lab(h, 261)) {
            lab.room(0, StandardAir.PRESSURE, 450);
            Zombie full = lab.zombie(0);
            Zombie torso = lab.zombie(0);
            Zombie helmet = lab.zombie(0);
            Zombie bare = lab.zombie(0);
            wear(full, TestLifeSupport.item(), ARMOR);
            wear(torso, TestLifeSupport.item(), EquipmentSlot.CHEST, EquipmentSlot.LEGS);
            wear(helmet, TestLifeSupport.item(), EquipmentSlot.HEAD);
            close(h, BreathingRules.assess(bare).exposure().burns(), 2, 1e-9, "bare");
            close(h, BreathingRules.assess(full).exposure().burns(), 0, 1e-9, "a full suit");
            close(h, BreathingRules.assess(torso).exposure().burns(), 2 * 0.4, 1e-9, "chest and legs");
            close(h, BreathingRules.assess(helmet).exposure().burns(), 2 * 0.7, 1e-9, "a helmet");
            Breathability v = Breathing.of(torso);
            h.assertTrue(v.protection().contains(Protection.THERMAL), "protection: " + v);
            h.assertTrue(v.hazards().contains(Hazard.HEAT), "hazards: " + v);
            h.assertFalse(Breathing.of(full).hazards().contains(Hazard.HEAT), "a full suit's hazards");
            h.assertFalse(Breathing.of(bare).protection().contains(Protection.THERMAL), "bare protection");
        }
        h.succeed();
    }

    // ---- LIFE-027: vanilla armor --------------------------------------------------------------

    /**
     * Full diamond armor stops burns at 380 K, not at 450 K or in the cold, gives no pressure
     * protection and no seal, so it can't be used with internals (LIFE-027).
     */
    @GameTest(template = "empty")
    public static void life_027_vanilla_armor_protects_against_heat_only(GameTestHelper h) {
        try (Lab lab = new Lab(h, 262)) {
            Zombie bare = lab.zombie(0);
            Zombie diamond = lab.zombie(0);
            diamond.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            diamond.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            diamond.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            diamond.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            close(h, exposure(bare, at(StandardAir.PRESSURE, 380)).burns(), 1, 0, "bare at 380 K");
            close(h, exposure(diamond, at(StandardAir.PRESSURE, 380)).burns(), 0, 1e-12, "diamond at 380 K");
            close(h, exposure(diamond, at(StandardAir.PRESSURE, 450)).burns(), 2, 0, "diamond at 450 K");
            close(h, exposure(diamond, at(StandardAir.PRESSURE, 150)).frostbite(), 1, 0, "diamond at 150 K");
            close(h, exposure(diamond, at(0, StandardAir.TEMPERATURE)).decompression(), 1, 0, "diamond in vacuum");
            close(
                    h,
                    exposure(diamond, at(600, StandardAir.TEMPERATURE)).barotrauma(),
                    exposure(bare, at(600, StandardAir.TEMPERATURE)).barotrauma(),
                    0,
                    "diamond at 600 kPa");
            h.assertFalse(WornProtection.sealedBreathing(diamond), "a diamond helmet seals the breathing");
            for (Item armor : List.of(
                    Items.LEATHER_BOOTS,
                    Items.CHAINMAIL_HELMET,
                    Items.IRON_LEGGINGS,
                    Items.GOLDEN_CHESTPLATE,
                    Items.NETHERITE_HELMET,
                    Items.TURTLE_HELMET)) {
                ILifeSupport s = Protections.of(new ItemStack(armor));
                h.assertTrue(s != null, armor + " has no protection");
                close(h, s.maxTemperature(), 400, 0, armor + "'s highest temperature");
                close(h, s.minTemperature(), StandardAir.TEMPERATURE, 0, armor + "'s lowest temperature");
                close(h, s.minPressure(), StandardAir.PRESSURE, 0, armor + "'s lowest pressure");
                close(h, s.maxPressure(), StandardAir.PRESSURE, 0, armor + "'s highest pressure");
            }
        }
        h.succeed();
    }

    // ---- LIFE-026, API-022: where protection comes from ----------------------------------------

    /**
     * An item's capability, data naming it, and data naming a tag it is in each give protection,
     * in that order; an entity's own capability covers its whole body; an invalid file is skipped
     * and a second claim on an item is logged (LIFE-026, API-022).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_026_capability_and_data_items_protect(GameTestHelper h) {
        ILifeSupport capability = Protections.of(new ItemStack(TestLifeSupport.item()));
        h.assertTrue(capability != null, "the capability");
        close(h, capability.maxPressure(), TestLifeSupport.SUIT_PIECE.maxPressure(), 0, "the capability's rating");
        ILifeSupport pumpkin = Protections.of(new ItemStack(Items.CARVED_PUMPKIN));
        h.assertTrue(pumpkin != null, "the carved pumpkin's data");
        close(h, pumpkin.minPressure(), 0, 0, "the pumpkin's lowest pressure");
        close(h, pumpkin.maxPressure(), StandardAir.PRESSURE, 0, "the pumpkin's highest pressure: the first file");
        h.assertTrue(pumpkin.sealedBreathing(), "the pumpkin seals");
        ILifeSupport skull = Protections.of(new ItemStack(Items.SKELETON_SKULL));
        h.assertTrue(skull != null, "the skull's data, from its tag");
        close(h, skull.minTemperature(), 100, 0, "the skull's lowest temperature");
        close(h, skull.maxTemperature(), 500, 0, "the skull's highest temperature");
        ILifeSupport elytra = Protections.of(new ItemStack(Items.ELYTRA));
        h.assertTrue(elytra != null, "the elytra's data");
        close(h, elytra.airflowResistance(), 0.75, 0, "the elytra's file naming it beats its tag's");
        close(h, elytra.maxTemperature(), StandardAir.TEMPERATURE, 0, "the elytra gets nothing from its tag");
        h.assertTrue(Protections.of(new ItemStack(Items.STICK)) == null, "the invalid file gave the stick protection");
        h.assertTrue(Protections.of(ItemStack.EMPTY) == null, "an empty slot has protection");
        h.assertTrue(
                DatapackErrors.recent().stream()
                        .anyMatch(p -> p.kind().equals("protection")
                                && p.skipped()
                                && p.file().endsWith("invalid_min_pressure.json")
                                && p.message().startsWith("min_pressure_kpa: ")),
                "the invalid file wasn't skipped naming its field: " + DatapackErrors.recent());
        h.assertTrue(
                DatapackErrors.recent().stream()
                        .anyMatch(p -> p.kind().equals("protection")
                                && !p.skipped()
                                && p.file().endsWith("data_zz_duplicate.json")),
                "the second claim wasn't logged: " + DatapackErrors.recent());
        try (Lab lab = new Lab(h, 263)) {
            lab.room(0, 0, StandardAir.TEMPERATURE);
            Zombie own = lab.zombie(0);
            own.addTag(TestLifeSupport.ENTITY_TAG);
            Zombie bare = lab.zombie(0);
            Zombie pumpkinHead = lab.zombie(0);
            pumpkinHead.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            close(h, BreathingRules.assess(own).exposure().decompression(), 0, 0, "its own life support in vacuum");
            close(h, BreathingRules.assess(bare).exposure().decompression(), 1, 0, "bare in vacuum");
            close(
                    h,
                    BreathingRules.assess(pumpkinHead).exposure().effectivePressure(),
                    StandardAir.PRESSURE * Math.pow(0.3, 5),
                    1e-9,
                    "a data helmet's effective pressure in vacuum");
            h.assertTrue(WornProtection.sealedBreathing(own), "its own life support seals");
            h.assertTrue(WornProtection.sealedBreathing(pumpkinHead), "the data helmet seals");
            h.assertFalse(WornProtection.sealedBreathing(bare), "bare is sealed");
            Zombie chestSeal = lab.zombie(0);
            chestSeal.setItemSlot(EquipmentSlot.CHEST, new ItemStack(TestLifeSupport.item()));
            h.assertFalse(WornProtection.sealedBreathing(chestSeal), "a seal in the chest slot counts");
        }
        h.succeed();
    }

    /**
     * An entity's airflow resistance is the highest of its worn pieces' and its own, for airflow to
     * read (FLOW-012, API-022).
     */
    @GameTest(template = "empty")
    public static void api_022_airflow_resistance_is_the_highest_worn(GameTestHelper h) {
        try (Lab lab = new Lab(h, 264)) {
            Zombie bare = lab.zombie(0);
            Zombie own = lab.zombie(0);
            own.addTag(TestLifeSupport.ENTITY_TAG);
            Zombie suited = lab.zombie(0);
            suited.addTag(TestLifeSupport.ENTITY_TAG);
            suited.setItemSlot(EquipmentSlot.FEET, new ItemStack(TestLifeSupport.item()));
            Zombie elytra = lab.zombie(0);
            elytra.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            elytra.setItemSlot(EquipmentSlot.FEET, new ItemStack(TestLifeSupport.item()));
            close(h, WornProtection.airflowResistance(bare), 0, 0, "bare");
            close(h, WornProtection.airflowResistance(own), 0.25, 0, "its own");
            close(h, WornProtection.airflowResistance(suited), 0.5, 0, "its own and a worn piece");
            close(h, WornProtection.airflowResistance(elytra), 0.75, 0, "two worn pieces");
        }
        h.succeed();
    }

    // ---- API-022, CONT-088: gas filtering ----------------------------------------------------------

    /** Moles for a partial pressure in kPa in a Lab's 27,000 L room at 293.15 K. */
    private static double molesAt(double kPa) {
        return kPa * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
    }

    /**
     * A room of air with gas in it at these partial pressures: 2 kPa of CO, which makes a breather
     * unconscious and deals 2 HP/s (LIFE-041); 2 kPa of N₂O, which incapacitates it; and 3 kPa of CO₂,
     * which is below its first threshold and isn't in {@code #zas:toxic}.
     */
    private static Map<ResourceLocation, Double> foulAir() {
        return Map.of(
                StandardAir.OXYGEN,
                molesAt(21),
                StandardAir.NITROGEN,
                molesAt(79),
                CO,
                molesAt(2),
                N2O,
                molesAt(2),
                Respiration.CARBON_DIOXIDE,
                molesAt(3));
    }

    /** Runs a check, then another 2 s later, which charges the 2 s between them. */
    private static void breatheTwoSeconds(LivingEntity e) {
        long now = e.level().getGameTime();
        Breathers.checkAt(e, now);
        Breathers.checkAt(e, now + 40);
    }

    /**
     * A filter in the head slot takes CO and N₂O, the {@code #zas:toxic} gases, out of what its
     * wearer breathes and nothing else, so neither harms it, and the room keeps them; in the chest
     * slot it does nothing, and a bare player beside it is harmed (API-022, CONT-088).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_022_a_filter_keeps_toxic_gases_out_of_the_breath(GameTestHelper h) {
        try (Lab lab = new Lab(h, 265)) {
            lab.room(0, foulAir());
            FakePlayer masked = lab.hurtable(0);
            masked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
            FakePlayer chest = lab.hurtable(0);
            chest.setItemSlot(EquipmentSlot.CHEST, new ItemStack(TestLifeSupport.item()));
            FakePlayer bare = lab.hurtable(0);

            GasMixture room = lab.r.zone(2, 2, 2).mixture();
            Breathability v = Breathing.of(masked);
            GasMixture breath = v.breath().orElseThrow();
            h.assertTrue(v.protection().contains(Protection.GAS_FILTERING), "masked protection: " + v);
            h.assertFalse(v.hazards().contains(Hazard.TOXIC_GAS), "masked hazards: " + v);
            h.assertValueEqual(v.reason(), BreathReason.BREATHABLE, "the masked player's reason");
            close(h, breath.moles(CO), 0, 0, "CO in the masked breath");
            close(h, breath.moles(N2O), 0, 0, "N2O in the masked breath");
            for (ResourceLocation gas : List.of(StandardAir.OXYGEN, StandardAir.NITROGEN, Respiration.CARBON_DIOXIDE)) {
                close(h, breath.moles(gas), room.moles(gas), 1e-9, gas + " in the masked breath");
                close(
                        h,
                        breath.partialPressure(gas),
                        room.partialPressure(gas),
                        1e-9,
                        gas + "'s partial pressure in the masked breath");
            }

            for (FakePlayer unfiltered : List.of(chest, bare)) {
                Breathability u = Breathing.of(unfiltered);
                h.assertFalse(u.protection().contains(Protection.GAS_FILTERING), "unfiltered protection: " + u);
                h.assertTrue(u.hazards().contains(Hazard.TOXIC_GAS), "unfiltered hazards: " + u);
                close(h, u.breath().orElseThrow().moles(CO), room.moles(CO), 1e-9, "CO in an unfiltered breath");
            }
            h.assertFalse(WornProtection.gasFiltering(chest), "a filter in the chest slot counts");

            breatheTwoSeconds(masked);
            breatheTwoSeconds(chest);
            breatheTwoSeconds(bare);
            close(h, masked.getHealth(), masked.getMaxHealth(), 0, "the masked player's health");
            h.assertTrue(masked.getActiveEffects().isEmpty(), "effects on the masked player");
            close(h, bare.getHealth(), bare.getMaxHealth() - 4, 1e-6, "the bare player's health: 2 HP/s for 2 s");
            h.assertTrue(bare.hasEffect(MobEffects.BLINDNESS), "the bare player isn't unconscious");
            close(h, chest.getHealth(), chest.getMaxHealth() - 4, 1e-6, "health with the filter in the chest slot");

            lab.r.ticks(2);
            GasMixture after = lab.r.zone(2, 2, 2).mixture();
            close(h, after.moles(CO), room.moles(CO), 1e-9, "CO left in the room");
            close(h, after.moles(N2O), room.moles(N2O), 1e-9, "N2O left in the room");
        }
        h.succeed();
    }

    /**
     * A filter supplies no O₂ (CONT-088): in a room of nitrogen and CO, its wearer is spared the CO
     * and still can't breathe, so its air drains at the full rate (LIFE-003).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_022_a_filter_supplies_no_oxygen(GameTestHelper h) {
        try (Lab lab = new Lab(h, 266)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, molesAt(100), CO, molesAt(2)));
            FakePlayer masked = lab.hurtable(0);
            masked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
            BreathingRules.Assessment a = BreathingRules.assess(masked);
            h.assertValueEqual(a.verdict().reason(), BreathReason.LOW_OXYGEN, "the masked player's reason");
            close(h, a.drain(), 1, 0, "its air's drain");
            h.assertFalse(a.verdict().hazards().contains(Hazard.TOXIC_GAS), "its hazards: " + a.verdict());
            close(h, a.verdict().breath().orElseThrow().moles(CO), 0, 0, "CO in its breath");
            breatheTwoSeconds(masked);
            close(h, masked.getHealth(), masked.getMaxHealth(), 0, "its health: nothing but hypoxia harms it");
        }
        h.succeed();
    }

    /**
     * A filter works on a tank's breath too, and only there: its wearer on internals breathes the
     * tank's gas without its CO, while the tank's draw and the exhale (LIFE-043, LIFE-045) carry the
     * CO on into the room. A seal alone leaves the CO in the breath (API-022, CONT-088).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_022_a_filter_works_on_a_tanks_breath(GameTestHelper h) {
        try (Lab lab = new Lab(h, 267)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, molesAt(100)));
            Map<ResourceLocation, Double> sour = Map.of(StandardAir.OXYGEN, 0.9, CO, 0.1);
            FakePlayer masked = lab.hurtable(0);
            masked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
            masked.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(sour, 21.3));
            Internals.set(masked, true);
            FakePlayer sealed = lab.hurtable(0);
            sealed.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            sealed.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(sour, 21.3));
            Internals.set(sealed, true);

            Breathability v = Breathing.of(masked);
            h.assertValueEqual(v.source(), BreathSource.INTERNALS, "the masked player's source");
            h.assertTrue(v.protection().contains(Protection.SEALED_BREATHING), "protection: " + v);
            h.assertTrue(v.protection().contains(Protection.GAS_FILTERING), "protection: " + v);
            h.assertFalse(v.hazards().contains(Hazard.TOXIC_GAS), "hazards: " + v);
            GasMixture breath = v.breath().orElseThrow();
            close(h, breath.moles(CO), 0, 0, "CO in the filtered breath");
            close(h, breath.partialPressure(StandardAir.OXYGEN), 21.3 * 0.9, 1e-9, "O2 in the filtered breath");
            Breathability s = Breathing.of(sealed);
            h.assertTrue(s.hazards().contains(Hazard.TOXIC_GAS), "a seal alone filtered the tank: " + s);
            h.assertFalse(s.protection().contains(Protection.GAS_FILTERING), "a seal alone: " + s);

            double before = lab.r.zone(2, 2, 2).mixture().moles(CO);
            breatheTwoSeconds(masked);
            double left = TestLifeSupport.contents(masked.getOffhandItem()).moles(CO);
            h.assertTrue(left < 0.1, "the tank's draw left its CO behind: " + left);
            close(h, masked.getHealth(), masked.getMaxHealth(), 0, "the masked player's health");
            lab.r.ticks(2);
            close(h, lab.r.zone(2, 2, 2).mixture().moles(CO) - before, 0.1 - left, 1e-9, "CO exhaled into the room");
        }
        h.succeed();
    }

    /** An item given gas filtering by data, with no capability, filters as one with it does (LIFE-026). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_026_a_data_item_filters_the_breath(GameTestHelper h) {
        try (Lab lab = new Lab(h, 268)) {
            lab.room(0, foulAir());
            ILifeSupport head = Protections.of(new ItemStack(Items.CREEPER_HEAD));
            h.assertTrue(head != null && head.gasFiltering(), "the creeper head's data");
            FakePlayer masked = lab.hurtable(0);
            masked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CREEPER_HEAD));
            h.assertTrue(WornProtection.gasFiltering(masked), "the data item in the head slot doesn't filter");
            Breathability v = Breathing.of(masked);
            h.assertTrue(v.protection().contains(Protection.GAS_FILTERING), "protection: " + v);
            h.assertFalse(v.hazards().contains(Hazard.TOXIC_GAS), "hazards: " + v);
            close(h, v.breath().orElseThrow().moles(CO), 0, 0, "CO in the breath");
        }
        h.succeed();
    }

    /**
     * API-022's plan for a test entity: a pig given every part by its own capability has each
     * honoured. Pressure, thermal protection and airflow resistance as a worn piece's; sealed
     * breathing and a tank of its own let it breathe in a vacuum, and draw on the tank; and the filter
     * spares it a foul room that harms a bare pig beside it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_022_a_test_entity_gets_every_part(GameTestHelper h) {
        try (Lab lab = new Lab(h, 269)) {
            lab.room(0, foulAir());
            lab.room(6, 0, StandardAir.TEMPERATURE);

            // In the foul room: the filter, against a bare pig.
            Pig own = lab.pig(0);
            own.addTag(TestLifeSupport.EVERY_PART_TAG);
            Pig bare = lab.pig(0);
            Breathability v = Breathing.of(own);
            h.assertTrue(v.protection().contains(Protection.GAS_FILTERING), "the pig's protection: " + v);
            h.assertFalse(v.hazards().contains(Hazard.TOXIC_GAS), "the pig's hazards: " + v);
            close(h, v.breath().orElseThrow().moles(CO), 0, 0, "CO in the pig's breath");
            h.assertTrue(Breathing.of(bare).hazards().contains(Hazard.TOXIC_GAS), "the bare pig's hazards");
            breatheTwoSeconds(own);
            breatheTwoSeconds(bare);
            close(h, own.getHealth(), own.getMaxHealth(), 0, "the pig's health");
            h.assertTrue(bare.getHealth() < bare.getMaxHealth(), "the foul room didn't harm the bare pig");

            // Pressure and temperature, each side of 1 atm and 293.15 K.
            close(h, exposure(own, at(0, StandardAir.TEMPERATURE)).decompression(), 0, 0, "decompression");
            close(
                    h,
                    exposure(bare, at(0, StandardAir.TEMPERATURE)).decompression(),
                    1,
                    0,
                    "a bare pig's decompression");
            close(h, exposure(own, at(600, StandardAir.TEMPERATURE)).barotrauma(), 0, 0, "barotrauma");
            h.assertTrue(exposure(bare, at(600, StandardAir.TEMPERATURE)).barotrauma() > 0, "a bare pig's barotrauma");
            close(h, exposure(own, at(StandardAir.PRESSURE, 450)).burns(), 0, 0, "burns");
            close(h, exposure(bare, at(StandardAir.PRESSURE, 450)).burns(), 2, 0, "a bare pig's burns");
            close(h, exposure(own, at(StandardAir.PRESSURE, 150)).frostbite(), 0, 0, "frostbite");
            close(h, exposure(bare, at(StandardAir.PRESSURE, 150)).frostbite(), 1, 0, "a bare pig's frostbite");

            // Airflow resistance (flow_012_resistance_scales_the_push checks a mob's own moves it less).
            close(h, WornProtection.airflowResistance(own), 0.5, 0, "the pig's airflow resistance");

            // In the vacuum: its seal and its own tank.
            TestLifeSupport.PIG_TANK.refill();
            Pig suited = lab.pig(6);
            suited.addTag(TestLifeSupport.EVERY_PART_TAG);
            Internals.set(suited, true);
            Pig gasping = lab.pig(6);
            Breathability t = Breathing.of(suited);
            h.assertValueEqual(t.source(), BreathSource.INTERNALS, "the suited pig's source");
            h.assertValueEqual(t.reason(), BreathReason.BREATHABLE, "the suited pig's reason");
            h.assertTrue(t.protection().contains(Protection.SEALED_BREATHING), "its protection: " + t);
            h.assertTrue(t.protection().contains(Protection.PRESSURE), "its protection: " + t);
            close(h, t.breath().orElseThrow().partialPressure(StandardAir.OXYGEN), 21.3, 1e-9, "its breath's O2");
            h.assertValueEqual(Breathing.of(gasping).reason(), BreathReason.LOW_OXYGEN, "a bare pig in the vacuum");
            breatheTwoSeconds(suited);
            close(
                    h,
                    1 - TestLifeSupport.PIG_TANK.contents().totalMoles(),
                    Internals.drawRate(21.3, StandardAir.TEMPERATURE) * 2,
                    1e-12,
                    "its draw on its own tank");
        }
        h.succeed();
    }
}
