package io.github.west3436.zas.compat.pneumaticcraft.gametest;

import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.compat.pneumaticcraft.AirUnits;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.Protections;
import java.util.List;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pneumatic Armor as a suit (COMPAT-010) and its air as a tank for internals (COMPAT-011), on a
 * server with PneumaticCraft: the pieces are PneumaticCraft's own, their air is set and read
 * through PneumaticCraft's item air handler, and ZAS³'s own breathing check runs on a player who
 * wears them.
 */
@GameTestHolder(PncGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class PncArmorTests {
    /** The four pieces, head to feet. */
    private static final List<String> PIECES =
            List.of("pneumatic_helmet", "pneumatic_chestplate", "pneumatic_leggings", "pneumatic_boots");

    private static final List<EquipmentSlot> SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    /** The pressure a piece delivers at, in kPa: an air tank's. */
    private static final double DELIVERED = 101.3;

    private PncArmorTests() {}

    private static IAirHandlerItem air(GameTestHelper h, ItemStack piece) {
        IAirHandlerItem air = piece.getCapability(PNCCapabilities.AIR_HANDLER_ITEM);
        h.assertTrue(air != null, piece + " has no air handler");
        return air;
    }

    /** Dresses an entity in the whole set, the chestplate holding this much air in mL, the rest none. */
    private static void dress(Area area, LivingEntity entity, int chestAir) {
        for (int i = 0; i < PIECES.size(); i++) {
            ItemStack piece = area.item(PIECES.get(i));
            if (SLOTS.get(i) == EquipmentSlot.CHEST && chestAir > 0) {
                air(area.h, piece).addAir(chestAir);
            }
            entity.setItemSlot(SLOTS.get(i), piece);
        }
    }

    /**
     * Runs ZAS³'s breathing check on an entity {@code n} times, each one check interval of game time
     * after the last, without waiting for the time to pass.
     */
    private static void check(LivingEntity entity, int n) {
        long now = entity.level().getGameTime();
        int interval = BreathingConfig.checkIntervalTicks();
        for (int i = 1; i <= n; i++) {
            entity.invulnerableTime = 0;
            Breathers.checkAt(entity, now + (long) i * interval);
        }
    }

    private static void close(GameTestHelper h, double actual, double expected, double relative, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= relative * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    // ---- COMPAT-010 ---------------------------------------------------------------------------

    /**
     * The data the adapter ships for the four pieces, as ZAS³ loaded it, rates them as the softsuit:
     * from vacuum to 1 atm and from 0 to 500 K, the helmet seals the breathing, and nothing filters
     * gas or resists airflow. It is data: none of the pieces has life support in code.
     */
    @GameTest(template = "empty")
    public static void compat_010_pneumatic_armor_is_rated_as_the_softsuit(GameTestHelper h) {
        Area area = new Area(h, 1);
        for (String path : PIECES) {
            ItemStack piece = area.item(path);
            h.assertTrue(piece.getCapability(ILifeSupport.ITEM) == null, path + " has life support in code");
            ILifeSupport rated = Protections.of(piece);
            h.assertTrue(rated != null, "no protection data names " + path);
            h.assertValueEqual(rated.minPressure(), 0.0, path + ": lowest pressure, kPa");
            h.assertValueEqual(rated.maxPressure(), StandardAir.PRESSURE, path + ": highest pressure, kPa");
            h.assertValueEqual(rated.minTemperature(), 0.0, path + ": lowest temperature, K");
            h.assertValueEqual(rated.maxTemperature(), 500.0, path + ": highest temperature, K");
            h.assertValueEqual(
                    rated.sealedBreathing(), path.equals("pneumatic_helmet"), path + ": seals the breathing");
            h.assertFalse(rated.gasFiltering(), path + " filters gas");
            h.assertValueEqual(rated.airflowResistance(), 0.0, path + ": airflow resistance");
        }
        h.succeed();
    }

    /**
     * In a sealed room pumped to vacuum, a player in nothing has the low-pressure hazard; a player in
     * the whole set of Pneumatic Armor doesn't, and has the pressure and thermal protection. With a
     * full chestplate and internals on, the helmet seals the breath and the chestplate feeds it.
     */
    @GameTest(template = "empty", batch = PncHeatTests.BATCH)
    public static void compat_010_pneumatic_armor_keeps_out_a_vacuum(GameTestHelper h) {
        Area area = new Area(h, 2);
        try {
            area.room();
            area.form(2, 2, 2);
            area.empty(2, 2, 2);
            h.assertTrue(area.zone(2, 2, 2).mixture().pressure() < 1, "the room isn't a vacuum: " + area.zone(2, 2, 2));

            FakePlayer bare = area.player(2, 1, 2);
            h.assertTrue(
                    Breathing.of(bare).hazards().contains(Hazard.LOW_PRESSURE),
                    "a player in nothing is safe in vacuum: " + Breathing.of(bare));

            FakePlayer suited = area.player(2, 1, 2);
            dress(area, suited, 28_000 * 10);
            Internals.set(suited, true);
            Breathability verdict = Breathing.of(suited);
            h.assertFalse(verdict.hazards().contains(Hazard.LOW_PRESSURE), "the suit lets in the vacuum: " + verdict);
            h.assertTrue(
                    verdict.protection()
                            .containsAll(List.of(Protection.PRESSURE, Protection.THERMAL, Protection.SEALED_BREATHING)),
                    "the suit's protection: " + verdict.protection());
            h.assertValueEqual(verdict.source(), BreathSource.INTERNALS, "what the suited player breathes from");
            h.assertValueEqual(verdict.reason(), BreathReason.BREATHABLE, "the suited player's breath");
            bare.discard();
            suited.discard();
        } finally {
            area.clear();
        }
        h.succeed();
    }

    // ---- COMPAT-011 ---------------------------------------------------------------------------

    /**
     * Internals draw from a worn Pneumatic Chestplate: with the internals switch on, the wearer
     * breathes its air, standard air delivered at 101.3 kPa. A minute of checks takes a minute's draw
     * out of it, in whole mL taken ahead of use, so its pressure as PneumaticCraft reads it falls.
     * The wearer stands in a sealed room, so the air round them is known and the check acts.
     */
    @GameTest(template = "empty", batch = PncHeatTests.BATCH)
    public static void compat_011_internals_draw_from_a_worn_piece(GameTestHelper h) {
        Area area = new Area(h, 3);
        area.room();
        area.form(2, 2, 2);
        FakePlayer player = area.player(2, 1, 2);
        int full = 28_000 * 10;
        dress(area, player, full);
        Internals.set(player, true);
        try {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            IInternalSupply supply = chest.getCapability(IInternalSupply.ITEM);
            h.assertTrue(supply != null, "a full chestplate isn't a tank");
            double perMl = AirUnits.DEFAULT_MOLES_PER_ML;
            double fullMoles = full * perMl;
            GasMixture held = supply.contents();
            close(h, held.totalMoles(), fullMoles, 1e-9, "the moles a full chestplate holds");
            close(h, held.temperature(), StandardAir.TEMPERATURE, 1e-12, "its air's temperature");
            close(h, held.moles(StandardAir.OXYGEN) / held.totalMoles(), 0.21, 1e-12, "its air's share of O2");
            close(h, held.pressure(), 1000, 1e-6, "its air's pressure at 10 bar, kPa");
            float pressure = air(h, chest).getPressure();

            Breathability verdict = Breathing.of(player);
            h.assertValueEqual(verdict.source(), BreathSource.INTERNALS, "what the wearer breathes from");
            GasMixture breath = verdict.breath().orElseThrow();
            close(h, breath.pressure(), DELIVERED, 1e-9, "the breath's pressure, kPa");
            close(h, breath.partialPressure(StandardAir.OXYGEN), DELIVERED * 0.21, 1e-9, "the breath's O2, kPa");

            // 31 checks, a check interval apart: the first counts no time, and with the default
            // interval of 2 s the rest are a minute's draw.
            check(player, 31);
            double drawn = Internals.drawRate(DELIVERED, StandardAir.TEMPERATURE)
                    * 30
                    * BreathingConfig.checkIntervalTicks()
                    / 20.0;
            ItemStack after = player.getItemBySlot(EquipmentSlot.CHEST);
            close(h, supply(h, after).contents().totalMoles(), fullMoles - drawn, 1e-9, "moles left after a minute");
            int taken = (int) Math.ceil(drawn / perMl);
            int left = air(h, after).getAir();
            h.assertTrue(
                    Math.abs(left - (full - taken)) <= 1,
                    "mL left after a minute: expected " + (full - taken) + ", got " + left);
            h.assertTrue(air(h, after).getPressure() < pressure, "the chestplate's pressure didn't fall");
        } finally {
            player.discard();
            area.clear();
        }
        h.succeed();
    }

    /**
     * A piece too low to deliver isn't a tank: a chestplate at half a bar holds too little to give
     * 101.3 kPa, so a wearer with internals on breathes the air round it, and the chestplate keeps
     * every mL.
     */
    @GameTest(template = "empty", batch = PncHeatTests.BATCH)
    public static void compat_011_a_piece_below_one_bar_is_no_tank(GameTestHelper h) {
        Area area = new Area(h, 4);
        area.room();
        area.form(2, 2, 2);
        FakePlayer player = area.player(2, 1, 2);
        int half = 28_000 / 2;
        dress(area, player, half);
        Internals.set(player, true);
        try {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            h.assertTrue(chest.getCapability(IInternalSupply.ITEM) == null, "a chestplate at 0.5 bar is a tank");
            h.assertValueEqual(Breathing.of(player).source(), BreathSource.ATMOSPHERE, "what the wearer breathes");
            check(player, 5);
            h.assertValueEqual(air(h, player.getItemBySlot(EquipmentSlot.CHEST)).getAir(), half, "mL left");
        } finally {
            player.discard();
            area.clear();
        }
        h.succeed();
    }

    private static IInternalSupply supply(GameTestHelper h, ItemStack piece) {
        IInternalSupply supply = piece.getCapability(IInternalSupply.ITEM);
        h.assertTrue(supply != null, piece + " isn't a tank");
        return supply;
    }
}
