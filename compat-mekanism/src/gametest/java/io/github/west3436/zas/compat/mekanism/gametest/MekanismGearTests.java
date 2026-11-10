package io.github.west3436.zas.compat.mekanism.gametest;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.compat.mekanism.MekanismGear;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.HudLevels;
import io.github.west3436.zas.life.EnvironmentHazards;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.LifeDamage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mekanism.api.MekanismAPITags;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.MekaSuitAbsorption;
import mekanism.common.CommonPlayerTickHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Mekanism's breathing gear on a server that has Mekanism (MEK-030 to MEK-034), and the hazards of
 * the gases the adapter defines. Players wear Mekanism's real items and live through real player
 * ticks, so Mekanism's own tick handler and ZAS³'s breathing both run, in the order they do in
 * play.
 *
 * <p>Each test has its own area ({@link GearLab}) and runs within one game tick.
 */
@GameTestHolder(MekanismFoundationTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismGearTests {
    private static final String BATCH = "mek_gear";

    /** Long enough for a full air meter to run out and suffocation to start: 300 ticks and then 20. */
    private static final int SUFFOCATING = 400;

    private static final ResourceLocation CHLORINE = ResourceLocation.fromNamespaceAndPath("zas", "chlorine");
    private static final ResourceLocation HYDROGEN_CHLORIDE =
            ResourceLocation.fromNamespaceAndPath("zas", "hydrogen_chloride");
    private static final ResourceLocation SULFUR_DIOXIDE =
            ResourceLocation.fromNamespaceAndPath("zas", "sulfur_dioxide");

    /** ZAS³'s damage types that go past armor, which is every one but the airflow's. */
    private static final List<ResourceKey<DamageType>> PAST_ARMOR = List.of(
            LifeDamage.HYPOXIA,
            LifeDamage.TOXIC_GAS,
            LifeDamage.DECOMPRESSION,
            LifeDamage.BAROTRAUMA,
            LifeDamage.FROSTBITE,
            LifeDamage.BURNS);

    private MekanismGearTests() {}

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }

    /** Hurts a player with one of ZAS³'s damage types and says how much health it lost. */
    private static float hurt(FakePlayer player, ResourceKey<DamageType> type, float amount) {
        float before = player.getHealth();
        player.invulnerableTime = 0;
        player.hurt(LifeDamage.source(player, type), amount);
        return before - player.getHealth();
    }

    // ---- MEK-030: scuba gear -------------------------------------------------------------------

    /**
     * A scuba mask and a switched-on tank holding oxygen keep a player breathing in a vacuum
     * (MEK-030). The public verdict says it breathes from its internals, sealed and filtered; the
     * meter stays full through 400 ticks; nothing suffocates it; and the tank loses 1 mB a tick,
     * which is what Mekanism takes from any running tank, and no more. The HUD shows, with its
     * internals indicator on though the player's own switch is off.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_030_scuba_gear_breathes_in_a_vacuum_at_no_extra_cost(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 0)) {
            lab.room(0, Map.of());
            FakePlayer p = lab.player(lab.floor(0, 0));
            GearLab.wearScuba(p, 1000, true);
            ItemStack tank = p.getItemBySlot(EquipmentSlot.CHEST);
            h.assertTrue(CommonPlayerTickHandler.isScubaMaskOn(p, tank), "Mekanism doesn't find the scuba gear on");
            h.assertTrue(MekanismGear.scubaRunning(p), "the adapter doesn't find the scuba gear running");
            h.assertFalse(Internals.on(p), "the player's own internals switch is on");

            Breathability verdict = Breathing.of(p);
            h.assertValueEqual(verdict.reason(), BreathReason.BREATHABLE, "the verdict");
            h.assertValueEqual(verdict.source(), BreathSource.INTERNALS, "the breath's source");
            h.assertTrue(verdict.protection().contains(Protection.SEALED_BREATHING), "no SEALED_BREATHING");
            h.assertTrue(verdict.protection().contains(Protection.GAS_FILTERING), "no GAS_FILTERING");
            close(h, verdict.breath().orElseThrow().partialPressure(GearLab.O2), 21.3, 1e-6, "the breath's O2, kPa");

            GearLab.breathe(p, SUFFOCATING);

            h.assertValueEqual(p.getAirSupply(), p.getMaxAirSupply(), "the air meter");
            h.assertValueEqual(GearLab.oxygenIn(tank), 1000L - SUFFOCATING, "mB left in the tank");
            close(h, GearLab.taken(p, LifeDamage.HYPOXIA), 0, 0, "health lost to hypoxia");
            h.assertTrue(HudLevels.triggered(p), "sealed breathing doesn't show the HUD");
            h.assertValueEqual(HudLevels.internals(p), AtmosHudPayload.INTERNALS_ON, "the HUD's internals");
        }
        h.succeed();
    }

    /**
     * Without a running set the vacuum suffocates (MEK-030): with the tank switched off, with an
     * empty tank switched on, with the tank but no mask, and with the mask but no tank. Each time
     * Mekanism finds the gear off too, the verdict is too little oxygen from the atmosphere, the
     * meter runs out, hypoxia follows, and a tank that holds oxygen keeps every mB.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_030_scuba_gear_off_or_empty_suffocates_in_a_vacuum(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 1)) {
            lab.room(0, Map.of());
            record Case(String name, ItemStack head, ItemStack chest, long oxygen) {}
            List<Case> cases = List.of(
                    new Case("switched off", GearLab.mekanism("scuba_mask"), GearLab.scubaTank(1000, false), 1000),
                    new Case("empty", GearLab.mekanism("scuba_mask"), GearLab.scubaTank(0, true), 0),
                    new Case("no mask", ItemStack.EMPTY, GearLab.scubaTank(1000, true), 1000),
                    new Case("no tank", GearLab.mekanism("scuba_mask"), ItemStack.EMPTY, -1));
            for (Case c : cases) {
                FakePlayer p = lab.player(lab.floor(0, 0));
                p.setItemSlot(EquipmentSlot.HEAD, c.head());
                p.setItemSlot(EquipmentSlot.CHEST, c.chest());
                h.assertFalse(CommonPlayerTickHandler.isScubaMaskOn(p, c.chest()), c.name() + ": Mekanism finds it on");
                h.assertFalse(MekanismGear.scubaRunning(p), c.name() + ": the adapter finds it running");
                h.assertTrue(MekanismGear.of(p) == null, c.name() + ": the gear gives life support");

                Breathability verdict = Breathing.of(p);
                h.assertValueEqual(verdict.reason(), BreathReason.LOW_OXYGEN, c.name() + ": the verdict");
                h.assertValueEqual(verdict.source(), BreathSource.ATMOSPHERE, c.name() + ": the breath's source");

                GearLab.breathe(p, SUFFOCATING);

                h.assertTrue(p.getAirSupply() <= 0, c.name() + ": the air meter is at " + p.getAirSupply());
                h.assertTrue(GearLab.taken(p, LifeDamage.HYPOXIA) > 0, c.name() + ": no hypoxia");
                if (c.oxygen() >= 0) {
                    h.assertValueEqual(GearLab.oxygenIn(c.chest()), c.oxygen(), c.name() + ": mB left in the tank");
                }
            }
        }
        h.succeed();
    }

    /**
     * A tank that runs dry stops supplying (MEK-030): 60 mB last 60 ticks, through which the meter
     * stays full; from the next check on the verdict is too little oxygen, and the player suffocates.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_030_a_tank_that_runs_dry_stops_supplying(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 2)) {
            lab.room(0, GearLab.NITROGEN);
            FakePlayer p = lab.player(lab.floor(0, 0));
            GearLab.wearScuba(p, 60, true);
            ItemStack tank = p.getItemBySlot(EquipmentSlot.CHEST);

            GearLab.breathe(p, 60);
            h.assertValueEqual(GearLab.oxygenIn(tank), 0L, "mB left after 60 ticks");
            h.assertValueEqual(p.getAirSupply(), p.getMaxAirSupply(), "the air meter while it lasted");
            h.assertFalse(MekanismGear.scubaRunning(p), "an empty tank still runs");

            h.assertValueEqual(Breathing.of(p).reason(), BreathReason.LOW_OXYGEN, "the verdict once dry");
            GearLab.breathe(p, SUFFOCATING);
            h.assertTrue(GearLab.taken(p, LifeDamage.HYPOXIA) > 0, "no hypoxia once dry");
        }
        h.succeed();
    }

    /**
     * The tank loses the same 1 mB a tick wherever the wearer is (MEK-030): in a room with nothing
     * to breathe, in a room of air, and under water, where ZAS³ keeps the meter full for a player
     * breathing from its internals, so Mekanism has nothing to top up. The meter stays full in all
     * three, and nothing drowns or suffocates the wearer. Without the gear the same water drowns.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_030_the_tank_loses_only_mekanisms_own_drain(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 3)) {
            lab.room(0, 0, GearLab.NITROGEN, StandardAir.TEMPERATURE);
            lab.room(8, 0, GearLab.AIR, StandardAir.TEMPERATURE);
            lab.room(0, 8, GearLab.AIR, StandardAir.TEMPERATURE);
            lab.flood(0, 8);
            Map<String, FakePlayer> wearers = new HashMap<>();
            wearers.put("nothing to breathe", lab.player(lab.floor(0, 0)));
            wearers.put("air", lab.player(lab.floor(8, 0)));
            wearers.put("under water", lab.player(lab.floor(0, 8)));
            wearers.forEach((where, p) -> {
                GearLab.wearScuba(p, 1000, true);
                GearLab.breathe(p, 200);
                h.assertValueEqual(
                        GearLab.oxygenIn(p.getItemBySlot(EquipmentSlot.CHEST)), 800L, where + ": mB left in the tank");
                h.assertValueEqual(p.getAirSupply(), p.getMaxAirSupply(), where + ": the air meter");
                h.assertValueEqual(GearLab.taken(p), List.of(), where + ": damage taken");
            });
            h.assertValueEqual(
                    Breathing.of(wearers.get("air")).source(), BreathSource.INTERNALS, "in air, the breath's source");
            h.assertValueEqual(
                    Breathing.of(wearers.get("under water")).source(),
                    BreathSource.INTERNALS,
                    "under water, the breath's source");

            FakePlayer bare = lab.player(lab.floor(0, 8));
            GearLab.breathe(bare, SUFFOCATING);
            h.assertTrue(
                    GearLab.taken(bare, DamageTypes.DROWN) > 0, "the water doesn't drown a player without the gear");
        }
        h.succeed();
    }

    // ---- MEK-031: what scuba gear protects against -----------------------------------------------

    /**
     * Scuba gear protects against no pressure or temperature unless the setting says so (MEK-031).
     * By default a wearer in a vacuum is exposed to its low pressure and is hurt by decompression,
     * while still breathing. With {@code scuba_protects} on, read at the next check with no
     * restart, the mask and tank protect the whole body: no pressure hazard, and no harm. They do
     * so switched off too, when the wearer has nothing to breathe.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_031_scuba_gear_protects_only_when_set(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 4)) {
            lab.room(0, Map.of());
            h.assertFalse(MekanismSettings.scubaProtects(), "scuba_protects is on by default");
            lab.afterwards(() -> MekanismSettings.SCUBA_PROTECTS.set(MekanismSettings.DEFAULT_SCUBA_PROTECTS));

            FakePlayer p = lab.player(lab.floor(0, 0));
            GearLab.wearScuba(p, 1000, true);
            Breathability exposed = Breathing.of(p);
            h.assertValueEqual(exposed.reason(), BreathReason.BREATHABLE, "the verdict by default");
            h.assertTrue(exposed.hazards().contains(Hazard.LOW_PRESSURE), "no low pressure hazard by default");
            h.assertFalse(exposed.protection().contains(Protection.PRESSURE), "pressure protection by default");
            GearLab.check(p, 3);
            h.assertTrue(GearLab.taken(p, LifeDamage.DECOMPRESSION) > 0, "no decompression by default");

            MekanismSettings.SCUBA_PROTECTS.set(true);
            FakePlayer q = lab.player(lab.floor(0, 0));
            GearLab.wearScuba(q, 1000, true);
            Breathability covered = Breathing.of(q);
            h.assertValueEqual(covered.reason(), BreathReason.BREATHABLE, "the verdict when set");
            h.assertFalse(covered.hazards().contains(Hazard.LOW_PRESSURE), "a low pressure hazard when set");
            h.assertTrue(covered.protection().contains(Protection.PRESSURE), "no pressure protection when set");
            GearLab.check(q, 3);
            h.assertValueEqual(GearLab.taken(q), List.of(), "damage taken when set");

            FakePlayer off = lab.player(lab.floor(0, 0));
            GearLab.wearScuba(off, 1000, false);
            Breathability worn = Breathing.of(off);
            h.assertValueEqual(worn.reason(), BreathReason.LOW_OXYGEN, "switched off, the verdict");
            h.assertFalse(worn.hazards().contains(Hazard.LOW_PRESSURE), "switched off, a low pressure hazard");
            h.assertFalse(worn.protection().contains(Protection.SEALED_BREATHING), "switched off, it seals");
        }
        h.succeed();
    }

    // ---- MEK-032: the MekaSuit -------------------------------------------------------------------

    /**
     * A whole MekaSuit protects against pressure and temperature (MEK-032): in a vacuum, in air at
     * 1,000 kPa and in air at 600 K, a player without it is hurt by decompression, barotrauma and
     * burns, and a player in it takes no damage at all and is told it is protected. Its helmet
     * seals the breathing, so internals work with it.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_032_a_whole_mekasuit_protects_in_vacuum_high_pressure_and_heat(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 5)) {
            lab.room(0, 0, Map.of(), StandardAir.TEMPERATURE);
            lab.room(8, 0, scaled(GearLab.AIR, 1000 / StandardAir.PRESSURE), StandardAir.TEMPERATURE);
            lab.room(0, 8, GearLab.AIR, 600);
            record Case(String name, int x0, int z0, ResourceKey<DamageType> harm, Hazard hazard, Protection by) {}
            List<Case> cases = List.of(
                    new Case("vacuum", 0, 0, LifeDamage.DECOMPRESSION, Hazard.LOW_PRESSURE, Protection.PRESSURE),
                    new Case("1,000 kPa", 8, 0, LifeDamage.BAROTRAUMA, Hazard.HIGH_PRESSURE, Protection.PRESSURE),
                    new Case("600 K", 0, 8, LifeDamage.BURNS, Hazard.HEAT, Protection.THERMAL));
            for (Case c : cases) {
                FakePlayer bare = lab.player(lab.floor(c.x0(), c.z0()));
                h.assertTrue(
                        Breathing.of(bare).hazards().contains(c.hazard()), c.name() + ": no hazard without a suit");
                GearLab.check(bare, 3);
                h.assertTrue(GearLab.taken(bare, c.harm()) > 0, c.name() + ": no harm without a suit");

                FakePlayer suited = lab.player(lab.floor(c.x0(), c.z0()));
                GearLab.wearMekaSuit(suited);
                Breathability verdict = Breathing.of(suited);
                h.assertFalse(verdict.hazards().contains(c.hazard()), c.name() + ": a hazard in the suit");
                h.assertTrue(verdict.protection().contains(c.by()), c.name() + ": the suit isn't the protection");
                GearLab.check(suited, 3);
                h.assertValueEqual(GearLab.taken(suited), List.of(), c.name() + ": damage taken in the suit");
            }

            FakePlayer suited = lab.player(lab.floor(0, 0));
            GearLab.wearMekaSuit(suited);
            h.assertTrue(HudLevels.triggered(suited), "the helmet doesn't seal the breathing");
        }
        h.succeed();
    }

    /**
     * A MekaSuit one piece short doesn't protect the whole body (MEK-032, LIFE-020, LIFE-025). In
     * air at 600 K each missing piece lets burns through. In a vacuum the helmet's and the body
     * armor's shares matter most: without either, decompression gets through.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_032_a_mekasuit_one_piece_short_is_exposed(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 6)) {
            lab.room(0, 0, Map.of(), StandardAir.TEMPERATURE);
            lab.room(0, 8, GearLab.AIR, 600);
            for (EquipmentSlot missing :
                    List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                FakePlayer hot = lab.player(lab.floor(0, 8));
                GearLab.wearMekaSuit(hot);
                hot.setItemSlot(missing, ItemStack.EMPTY);
                h.assertTrue(Breathing.of(hot).hazards().contains(Hazard.HEAT), missing + " missing: no heat hazard");
                GearLab.check(hot, 3);
                h.assertTrue(GearLab.taken(hot, LifeDamage.BURNS) > 0, missing + " missing: no burns");
            }
            for (EquipmentSlot missing : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST)) {
                FakePlayer airless = lab.player(lab.floor(0, 0));
                GearLab.wearMekaSuit(airless);
                airless.setItemSlot(missing, ItemStack.EMPTY);
                GearLab.check(airless, 3);
                h.assertTrue(
                        GearLab.taken(airless, LifeDamage.DECOMPRESSION) > 0, missing + " missing: no decompression");
            }
        }
        h.succeed();
    }

    /**
     * The Electrolytic Breathing Unit does what Mekanism makes it do and nothing more (MEK-032,
     * Q-20). It makes air from water, so in a vacuum a whole MekaSuit with one installed still
     * suffocates: the meter runs out and hypoxia follows, though the suit keeps the vacuum's other
     * harm away. Under water the same suit keeps the meter up and uses energy, which shows the unit
     * was working all along; a suit without one drowns there.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_032_the_breathing_unit_gives_no_air_in_a_vacuum(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 7)) {
            lab.room(0, 0, Map.of(), StandardAir.TEMPERATURE);
            lab.room(0, 8, GearLab.AIR, StandardAir.TEMPERATURE);
            lab.flood(0, 8);

            FakePlayer airless = lab.player(lab.floor(0, 0));
            GearLab.wearMekaSuit(airless);
            lab.install(airless.getItemBySlot(EquipmentSlot.HEAD), "electrolytic_breathing_unit");
            Breathability verdict = Breathing.of(airless);
            h.assertValueEqual(verdict.reason(), BreathReason.LOW_OXYGEN, "in a vacuum, the verdict");
            h.assertValueEqual(verdict.source(), BreathSource.ATMOSPHERE, "in a vacuum, the breath's source");
            GearLab.breathe(airless, SUFFOCATING);
            h.assertTrue(airless.getAirSupply() <= 0, "in a vacuum the air meter is at " + airless.getAirSupply());
            h.assertTrue(GearLab.taken(airless, LifeDamage.HYPOXIA) > 0, "in a vacuum, no hypoxia");
            close(h, GearLab.taken(airless, LifeDamage.DECOMPRESSION), 0, 0, "in a vacuum, decompression");

            FakePlayer diver = lab.player(lab.floor(0, 8));
            GearLab.wearMekaSuit(diver);
            ItemStack helmet = diver.getItemBySlot(EquipmentSlot.HEAD);
            lab.install(helmet, "electrolytic_breathing_unit");
            long before = GearLab.energy(helmet);
            GearLab.breathe(diver, SUFFOCATING);
            h.assertTrue(
                    diver.getAirSupply() >= diver.getMaxAirSupply() - 1,
                    "under water the air meter is at " + diver.getAirSupply());
            h.assertValueEqual(GearLab.taken(diver), List.of(), "under water, damage taken");
            h.assertTrue(GearLab.energy(diver.getItemBySlot(EquipmentSlot.HEAD)) < before, "the unit used no energy");

            FakePlayer without = lab.player(lab.floor(0, 8));
            GearLab.wearMekaSuit(without);
            GearLab.breathe(without, SUFFOCATING);
            h.assertTrue(without.getAirSupply() <= 0, "a suit without the unit kept its air under water");
        }
        h.succeed();
    }

    // ---- MEK-033: damage types -------------------------------------------------------------------

    /**
     * Every damage type of ZAS³'s is tagged for Mekanism on purpose (MEK-033). Hypoxia and the
     * pressure and temperature types go past armor and aren't among the types the MekaSuit always
     * absorbs, so a charged MekaSuit takes nothing off them, and uses no energy on them: protection
     * from pressure and temperature is the life-support model's, once. Toxic gas goes past armor
     * too, and is the one type Mekanism's gas mask and purification unit prevent. The airflow's
     * impact is absorbed whole, by an entry of its own in Mekanism's absorption data, as flying
     * into a wall is.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_033_each_damage_type_against_a_mekasuit(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 8)) {
            FakePlayer p = lab.player(lab.floor(0, 0));
            GearLab.wearMekaSuit(p);
            for (ResourceKey<DamageType> type : PAST_ARMOR) {
                DamageSource source = LifeDamage.source(p, type);
                h.assertTrue(source.is(DamageTypeTags.BYPASSES_ARMOR), type.location() + " doesn't go past armor");
                h.assertFalse(
                        source.is(MekanismAPITags.DamageTypes.MEKASUIT_ALWAYS_SUPPORTED),
                        type.location() + " is always absorbed");
                h.assertValueEqual(
                        source.is(MekanismAPITags.DamageTypes.IS_PREVENTABLE_MAGIC),
                        type == LifeDamage.TOXIC_GAS,
                        type.location() + " is prevented by a gas mask");
                h.assertTrue(absorption(p, type) == null, type.location() + " has an absorption entry");

                long energy = suitEnergy(p);
                close(h, hurt(p, type, 4), 4, 1e-4, "health lost to " + type.location() + " in a MekaSuit");
                h.assertValueEqual(suitEnergy(p), energy, "the suit's energy after " + type.location());
            }

            DamageSource impact = LifeDamage.source(p, LifeDamage.AIRFLOW_IMPACT);
            h.assertFalse(impact.is(DamageTypeTags.BYPASSES_ARMOR), "the airflow's impact goes past armor");
            MekaSuitAbsorption entry = absorption(p, LifeDamage.AIRFLOW_IMPACT);
            h.assertTrue(entry != null, "the airflow's impact has no absorption entry");
            close(h, entry.absorption(), 1, 0, "the airflow's impact, absorbed");
            long energy = suitEnergy(p);
            close(h, hurt(p, LifeDamage.AIRFLOW_IMPACT, 4), 0, 0, "health lost to the airflow's impact");
            h.assertTrue(suitEnergy(p) < energy, "absorbing the impact used no energy");

            // Without a suit it hurts, less only what the plain armor value takes off: here, nothing.
            FakePlayer bare = lab.player(lab.floor(0, 0));
            close(h, hurt(bare, LifeDamage.AIRFLOW_IMPACT, 4), 4, 1e-4, "health lost to the impact, bare");
        }
        h.succeed();
    }

    /**
     * Mekanism's own prevention of toxic gas damage reaches ZAS³'s (MEK-033). A running scuba set
     * cancels it, as it cancels magic damage, and a purification unit absorbs it for energy; neither
     * touches hypoxia or decompression. A scuba set that isn't running prevents nothing.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_033_scuba_gear_and_the_purification_unit_prevent_toxic_gas_damage(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 9)) {
            FakePlayer diver = lab.player(lab.floor(0, 0));
            GearLab.wearScuba(diver, 1000, true);
            close(h, hurt(diver, LifeDamage.TOXIC_GAS, 4), 0, 0, "toxic gas, scuba gear running");
            close(h, hurt(diver, LifeDamage.HYPOXIA, 4), 4, 1e-4, "hypoxia, scuba gear running");
            close(h, hurt(diver, LifeDamage.DECOMPRESSION, 4), 4, 1e-4, "decompression, scuba gear running");

            FakePlayer off = lab.player(lab.floor(0, 0));
            GearLab.wearScuba(off, 1000, false);
            close(h, hurt(off, LifeDamage.TOXIC_GAS, 4), 4, 1e-4, "toxic gas, scuba gear switched off");

            FakePlayer suited = lab.player(lab.floor(0, 0));
            GearLab.wearMekaSuit(suited);
            ItemStack helmet = suited.getItemBySlot(EquipmentSlot.HEAD);
            lab.install(helmet, "inhalation_purification_unit");
            long energy = suitEnergy(suited);
            close(h, hurt(suited, LifeDamage.TOXIC_GAS, 4), 0, 0, "toxic gas, purification unit");
            h.assertTrue(suitEnergy(suited) < energy, "the purification unit used no energy");
            close(h, hurt(suited, LifeDamage.HYPOXIA, 4), 4, 1e-4, "hypoxia, purification unit");
        }
        h.succeed();
    }

    /** Mekanism's robit, a machine, is among the entities that the atmosphere doesn't harm. */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_033_the_robit_is_immune_to_the_atmosphere(GameTestHelper h) {
        EntityType<?> robit =
                BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("mekanism", "robit"));
        h.assertTrue(robit != EntityType.PIG, "Mekanism has no robit");
        h.assertTrue(robit.is(EnvironmentHazards.IMMUNE), "the robit isn't in zas:atmosphere_immune");
        h.assertTrue(EntityType.IRON_GOLEM.is(EnvironmentHazards.IMMUNE), "the adapter's tag replaced ZAS³'s");
        h.succeed();
    }

    // ---- MEK-034: the Inhalation Purification Unit -----------------------------------------------

    /**
     * An Inhalation Purification Unit filters ZAS³'s toxic gases and supplies no oxygen (MEK-034).
     * In air with chlorine far over its worst threshold, a MekaSuit helmet with the unit installed,
     * enabled and charged keeps the gas out of the breath: the verdict has no toxic gas hazard and
     * names the filter, the breath is still the room's, and checks do no harm. Switched off, with no
     * energy, or with no unit, the same helmet lets the gas through. In a room with nothing to
     * breathe the unit changes nothing: too little oxygen.
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void mek_034_the_purification_unit_filters_toxic_gas_and_supplies_no_oxygen(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 10)) {
            Map<ResourceLocation, Double> poisoned = new HashMap<>(GearLab.AIR);
            poisoned.put(CHLORINE, 0.1);
            lab.room(0, 0, poisoned, StandardAir.TEMPERATURE);
            lab.room(8, 0, GearLab.NITROGEN, StandardAir.TEMPERATURE);
            h.assertTrue(GasTags.contains(GasTags.TOXIC, CHLORINE), "chlorine isn't a toxic gas");

            FakePlayer p = lab.player(lab.floor(0, 0));
            p.setItemSlot(EquipmentSlot.HEAD, GearLab.charged("mekasuit_helmet"));
            ItemStack helmet = p.getItemBySlot(EquipmentSlot.HEAD);
            h.assertFalse(MekanismGear.purifies(helmet), "a helmet with no unit filters");
            h.assertTrue(Breathing.of(p).hazards().contains(Hazard.TOXIC_GAS), "the chlorine isn't a hazard");

            lab.install(helmet, "inhalation_purification_unit");
            h.assertTrue(MekanismGear.purifies(helmet), "the unit doesn't filter");
            Breathability verdict = Breathing.of(p);
            h.assertFalse(verdict.hazards().contains(Hazard.TOXIC_GAS), "the chlorine reached the breath");
            h.assertTrue(verdict.protection().contains(Protection.GAS_FILTERING), "no GAS_FILTERING");
            h.assertValueEqual(verdict.source(), BreathSource.ATMOSPHERE, "the breath's source");
            close(h, verdict.breath().orElseThrow().partialPressure(CHLORINE), 0, 0, "chlorine in the breath, kPa");
            GearLab.check(p, 3);
            h.assertValueEqual(GearLab.taken(p), List.of(), "damage taken behind the unit");

            GearLab.toggle(p, helmet, "inhalation_purification_unit");
            h.assertFalse(MekanismGear.purifies(helmet), "a unit switched off filters");
            h.assertTrue(Breathing.of(p).hazards().contains(Hazard.TOXIC_GAS), "switched off, no hazard");

            FakePlayer flat = lab.player(lab.floor(0, 0));
            flat.setItemSlot(EquipmentSlot.HEAD, GearLab.mekanism("mekasuit_helmet"));
            lab.install(flat.getItemBySlot(EquipmentSlot.HEAD), "inhalation_purification_unit");
            h.assertFalse(
                    MekanismGear.purifies(flat.getItemBySlot(EquipmentSlot.HEAD)), "a unit with no energy filters");
            h.assertTrue(Breathing.of(flat).hazards().contains(Hazard.TOXIC_GAS), "with no energy, no hazard");

            FakePlayer airless = lab.player(lab.floor(8, 0));
            airless.setItemSlot(EquipmentSlot.HEAD, GearLab.charged("mekasuit_helmet"));
            lab.install(airless.getItemBySlot(EquipmentSlot.HEAD), "inhalation_purification_unit");
            Breathability starved = Breathing.of(airless);
            h.assertValueEqual(starved.reason(), BreathReason.LOW_OXYGEN, "with nothing to breathe, the verdict");
            h.assertValueEqual(starved.source(), BreathSource.ATMOSPHERE, "with nothing to breathe, the source");
        }
        h.succeed();
    }

    // ---- The new gases' hazards (LIFE-004, LIFE-041) ----------------------------------------------

    /** Chlorine: warning at 0.0001 kPa, impaired and 0.5 HP/s at 0.001 kPa, unconscious and 2 HP/s at 0.01 kPa. */
    @GameTest(batch = BATCH, template = "empty")
    public static void life_004_chlorine_harms_at_its_thresholds_and_gear_prevents_it(GameTestHelper h) {
        gasHazard(h, 11, CHLORINE, 0.0001, 0.001, 0.01);
    }

    /** Hydrogen chloride: warning at 0.0005 kPa, impaired and 0.5 HP/s at 0.005 kPa, unconscious and 2 HP/s at 0.05 kPa. */
    @GameTest(batch = BATCH, template = "empty")
    public static void life_004_hydrogen_chloride_harms_at_its_thresholds_and_gear_prevents_it(GameTestHelper h) {
        gasHazard(h, 12, HYDROGEN_CHLORIDE, 0.0005, 0.005, 0.05);
    }

    /** Sulfur dioxide: warning at 0.0005 kPa, impaired and 0.5 HP/s at 0.01 kPa, unconscious and 2 HP/s at 0.1 kPa. */
    @GameTest(batch = BATCH, template = "empty")
    public static void life_004_sulfur_dioxide_harms_at_its_thresholds_and_gear_prevents_it(GameTestHelper h) {
        gasHazard(h, 13, SULFUR_DIOXIDE, 0.0005, 0.01, 0.1);
    }

    // ---- A sealed room keeps the new gases (CORE-052) ---------------------------------------------

    /**
     * Sealed rooms of air nobody is in, each holding one of the adapter's toxic gases at twice the
     * partial pressure that hurts, keep their zones and their gas well past {@code [atmos]
     * sealed_zone_dissolve_seconds}. By the engine's tolerances their air is the open air's: these
     * gases hurt from a five-hundredth or less of what the tolerances let through. So such a room
     * used to turn back into open air a minute after it was left, and its gas was gone (CORE-052,
     * LIFE-004).
     */
    @GameTest(batch = BATCH, template = "empty")
    public static void core_052_a_sealed_room_holding_a_toxic_gas_keeps_its_zone(GameTestHelper h) {
        try (GearLab lab = new GearLab(h, 14)) {
            double configured = ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.get();
            lab.afterwards(() -> ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(configured));
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(3.0);

            // Each gas with the partial pressure it hurts from, in kPa, in a room of its own.
            List<Map.Entry<ResourceLocation, Double>> gases = List.of(
                    Map.entry(CHLORINE, 0.001), Map.entry(HYDROGEN_CHLORIDE, 0.005), Map.entry(SULFUR_DIOXIDE, 0.01));
            List<ZoneId> zones = new ArrayList<>();
            for (int i = 0; i < gases.size(); i++) {
                Map<ResourceLocation, Double> air = new HashMap<>(GearLab.AIR);
                air.put(gases.get(i).getKey(), 2 * gases.get(i).getValue());
                lab.room(5 * i, air);
                zones.add(lab.air(5 * i, 0).zone().orElseThrow());
            }

            lab.atmosTicks(12);

            for (int i = 0; i < gases.size(); i++) {
                ResourceLocation gas = gases.get(i).getKey();
                double kPa = 2 * gases.get(i).getValue();
                AtmosphereSample room = lab.air(5 * i, 0);
                h.assertValueEqual(room.state(), AtmosphereState.ZONED, "the room of " + gas);
                h.assertValueEqual(room.zone().orElseThrow(), zones.get(i), "the zone of the room of " + gas);
                close(h, room.mixture().partialPressure(gas), kPa, 1e-6 * kPa, gas + " still in its room, kPa");
            }
        }
        h.succeed();
    }

    /**
     * One gas's profile in a room of air, with the gas added in three steps, each a fifth over a
     * threshold: nothing harms at the warning; at the second the breath is a hazard, the player is
     * impaired and loses 0.5 HP a second; at the third it is blinded and loses 2 HP a second. Then,
     * at that worst level, running scuba gear and a purification unit each keep a player from all
     * of it.
     */
    private static void gasHazard(
            GameTestHelper h, int area, ResourceLocation gas, double warning, double impaired, double unconscious) {
        try (GearLab lab = new GearLab(h, area)) {
            lab.room(0, GearLab.AIR);

            lab.add(0, 0, Map.of(gas, 1.2 * warning), StandardAir.TEMPERATURE);
            close(h, lab.air(0, 0).mixture().partialPressure(gas), 1.2 * warning, 0.02 * warning, "the gas, kPa");
            FakePlayer warned = lab.player(lab.floor(0, 0));
            h.assertFalse(Breathing.of(warned).hazards().contains(Hazard.TOXIC_GAS), "a hazard at the warning");
            GearLab.check(warned, 3);
            h.assertValueEqual(GearLab.taken(warned), List.of(), "damage taken at the warning");
            h.assertTrue(warned.getActiveEffects().isEmpty(), "effects at the warning: " + warned.getActiveEffects());

            lab.add(0, 0, Map.of(gas, 1.2 * (impaired - warning)), StandardAir.TEMPERATURE);
            FakePlayer hurt = lab.player(lab.floor(0, 0));
            h.assertTrue(Breathing.of(hurt).hazards().contains(Hazard.TOXIC_GAS), "no hazard at the second threshold");
            GearLab.check(hurt, 3);
            // Three checks, 2 s apart: the first counts no time, so 4 s at 0.5 HP/s.
            close(h, GearLab.taken(hurt, LifeDamage.TOXIC_GAS), 2, 1e-3, "health lost in 4 s at the second threshold");
            h.assertTrue(hurt.hasEffect(MobEffects.CONFUSION), "not impaired at the second threshold");
            h.assertFalse(hurt.hasEffect(MobEffects.BLINDNESS), "blinded at the second threshold");

            lab.add(0, 0, Map.of(gas, 1.2 * (unconscious - impaired)), StandardAir.TEMPERATURE);
            FakePlayer down = lab.player(lab.floor(0, 0));
            GearLab.check(down, 3);
            close(h, GearLab.taken(down, LifeDamage.TOXIC_GAS), 8, 1e-3, "health lost in 4 s at the third threshold");
            h.assertTrue(down.hasEffect(MobEffects.BLINDNESS), "not blinded at the third threshold");

            FakePlayer diver = lab.player(lab.floor(0, 0));
            GearLab.wearScuba(diver, 1000, true);
            h.assertFalse(Breathing.of(diver).hazards().contains(Hazard.TOXIC_GAS), "a hazard with scuba gear running");
            GearLab.check(diver, 3);
            h.assertValueEqual(GearLab.taken(diver), List.of(), "damage taken with scuba gear running");
            h.assertTrue(diver.getActiveEffects().isEmpty(), "effects with scuba gear running");

            FakePlayer suited = lab.player(lab.floor(0, 0));
            suited.setItemSlot(EquipmentSlot.HEAD, GearLab.charged("mekasuit_helmet"));
            lab.install(suited.getItemBySlot(EquipmentSlot.HEAD), "inhalation_purification_unit");
            h.assertFalse(
                    Breathing.of(suited).hazards().contains(Hazard.TOXIC_GAS), "a hazard with a purification unit");
            GearLab.check(suited, 3);
            h.assertValueEqual(GearLab.taken(suited), List.of(), "damage taken with a purification unit");
            h.assertTrue(suited.getActiveEffects().isEmpty(), "effects with a purification unit");
        }
        h.succeed();
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private static Map<ResourceLocation, Double> scaled(Map<ResourceLocation, Double> kPa, double by) {
        Map<ResourceLocation, Double> out = new HashMap<>();
        kPa.forEach((gas, p) -> out.put(gas, p * by));
        return out;
    }

    /** A damage type's entry in Mekanism's absorption data, or null if it has none. */
    private static MekaSuitAbsorption absorption(FakePlayer player, ResourceKey<DamageType> type) {
        return IMekanismDataMapTypes.INSTANCE.getMekaSuitAbsorption(
                player.registryAccess(), LifeDamage.source(player, type).typeHolder());
    }

    /** The energy in the four armor pieces an entity wears. */
    private static long suitEnergy(FakePlayer player) {
        long energy = 0;
        for (ItemStack piece : player.getArmorSlots()) {
            energy += GearLab.energy(piece);
        }
        return energy;
    }
}
