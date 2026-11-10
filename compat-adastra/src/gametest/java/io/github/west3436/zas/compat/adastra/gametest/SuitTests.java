package io.github.west3436.zas.compat.adastra.gametest;

import earth.terrarium.adastra.api.planets.Planet;
import earth.terrarium.adastra.api.systems.OxygenApi;
import io.github.west3436.zas.api.capability.IInternalSupply;
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
import io.github.west3436.zas.compat.adastra.gametest.Lab.Suit;
import io.github.west3436.zas.life.Protections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ad Astra's suits as ZAS³ life support: the protection the adapter's data pack gives them
 * (SPACE-022), their oxygen as the tank internals breathe from (SPACE-023), and the two together
 * on the Moon (S-06).
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class SuitTests {
    /** The adapter's data pack, under the id NeoForge gives a mod's built-in pack. */
    private static final String PACK = "mod/zas:datapacks/ad_astra";

    private static final ResourceLocation HYPOXIA = zas("hypoxia");
    private static final ResourceLocation BAROTRAUMA = zas("barotrauma");
    private static final ResourceLocation DECOMPRESSION = zas("decompression");
    private static final ResourceLocation FROSTBITE = zas("frostbite");
    private static final ResourceLocation BURNS = zas("burns");

    /** What a full store counts as, in moles of O₂ (SPACE-023). */
    private static final double FULL = 17.5;

    /** What internals draw each second from a tank delivering 21.3 kPa at 293.15 K, in moles (LIFE-043). */
    private static final double DRAW_PER_SECOND = 21.3 * 0.0625 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);

    private SuitTests() {}

    private static ResourceLocation zas(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas", path);
    }

    private static FakePlayer wearing(Lab lab, Vec3 at, Suit suit, int oxygen) {
        FakePlayer player = lab.player(at);
        suit.wornBy(player, oxygen);
        return player;
    }

    private static IInternalSupply supply(GameTestHelper h, ItemStack chest) {
        IInternalSupply supply = chest.getCapability(IInternalSupply.ITEM);
        h.assertTrue(supply != null, chest + " isn't an internal supply");
        return supply;
    }

    // ---- SPACE-022 ----------------------------------------------------------------------------

    /**
     * The data the adapter ships for the twelve suit pieces, as ZAS³ loaded it: the space suit is
     * rated from vacuum to 1 atm and from 0 to 500 K, the netherite suit and the jet suit for every
     * pressure and temperature, the helmets seal the breathing, and nothing filters gas or resists
     * airflow. It is data: none of the pieces has life support in code.
     */
    @GameTest(template = "empty")
    public static void space_022_the_suits_ratings_are_the_adapters_data(GameTestHelper h) {
        h.assertTrue(
                h.getLevel().getServer().getPackRepository().getSelectedIds().contains(PACK),
                "the adapter's data pack isn't on");
        for (Suit suit : Suit.values()) {
            boolean heavy = suit != Suit.SPACE;
            for (String path : suit.pieces()) {
                ItemStack piece = Lab.adAstra(path);
                h.assertTrue(piece.getCapability(ILifeSupport.ITEM) == null, path + " has life support in code");
                ILifeSupport rated = Protections.of(piece);
                h.assertTrue(rated != null, "no protection data names " + path);
                h.assertValueEqual(rated.minPressure(), 0.0, path + ": lowest pressure, kPa");
                h.assertValueEqual(
                        rated.maxPressure(), heavy ? 1e9 : StandardAir.PRESSURE, path + ": highest pressure, kPa");
                h.assertValueEqual(rated.minTemperature(), 0.0, path + ": lowest temperature, K");
                h.assertValueEqual(rated.maxTemperature(), heavy ? 1e9 : 500.0, path + ": highest temperature, K");
                h.assertValueEqual(rated.sealedBreathing(), path.equals(suit.head), path + ": seals the breathing");
                h.assertFalse(rated.gasFiltering(), path + " filters gas");
                h.assertValueEqual(rated.airflowResistance(), 0.0, path + ": airflow resistance");
                h.assertTrue(rated.internalSupply().isEmpty(), path + "'s data holds a supply");
            }
        }
        h.succeed();
    }

    /**
     * In vacuum, on the Moon at 100.15 K, each whole suit keeps out the low pressure and the cold:
     * the verdict lists neither hazard and names the pressure and thermal protection, and with
     * internals off nothing harms the wearer but the lack of oxygen. A player in nothing has both
     * hazards and takes both kinds of damage.
     */
    @GameTest(template = "empty")
    public static void space_022_each_suit_protects_in_vacuum(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 70)) {
            FakePlayer bare = lab.player(lab.open(10));
            h.assertValueEqual(
                    Breathing.of(bare).hazards(),
                    Set.of(Hazard.LOW_PRESSURE, Hazard.COLD),
                    "the hazards without a suit");
            Lab.check(bare, 5);
            h.assertTrue(
                    Lab.dealt(bare).containsAll(Set.of(DECOMPRESSION, FROSTBITE)),
                    "damage dealt without a suit: " + Lab.dealt(bare));

            for (Suit suit : Suit.values()) {
                FakePlayer player = wearing(lab, lab.open(10), suit, 0);
                Breathability verdict = Breathing.of(player);
                h.assertValueEqual(verdict.hazards(), Set.of(), suit + ": hazards in vacuum");
                h.assertTrue(
                        verdict.protection().containsAll(Set.of(Protection.PRESSURE, Protection.THERMAL)),
                        suit + ": protection in vacuum: " + verdict.protection());
                h.assertValueEqual(verdict.reason(), BreathReason.LOW_OXYGEN, suit + ": the verdict, internals off");
                Lab.check(player, 5);
                h.assertTrue(
                        Set.of(HYPOXIA).containsAll(Lab.dealt(player)),
                        suit + ": damage dealt in vacuum: " + Lab.dealt(player));
            }
        }
        h.succeed();
    }

    /**
     * At 600 kPa, in a sealed room, the space suit has no rating: its wearer has the high-pressure
     * hazard and takes barotrauma. The netherite suit and the jet suit are rated for it, and their
     * wearers take nothing.
     */
    @GameTest(template = "empty")
    public static void space_022_only_the_heavy_suits_protect_at_600_kpa(GameTestHelper h) {
        try (Lab lab = Lab.onTheOverworld(h, 71)) {
            lab.room(0, Map.of(Lab.N2, 474.0, Lab.O2, 126.0));
            h.assertTrue(
                    Math.abs(lab.sample(2, 2, 2).pressure() - 600) < 1e-6,
                    "the room's pressure: " + lab.sample(2, 2, 2).pressure());

            FakePlayer soft = wearing(lab, lab.floor(0), Suit.SPACE, 0);
            Breathability verdict = Breathing.of(soft);
            h.assertValueEqual(verdict.hazards(), Set.of(Hazard.HIGH_PRESSURE), "SPACE: hazards at 600 kPa");
            h.assertFalse(verdict.protection().contains(Protection.PRESSURE), "SPACE is rated for 600 kPa");
            Lab.check(soft, 10);
            h.assertValueEqual(Set.copyOf(Lab.dealt(soft)), Set.of(BAROTRAUMA), "SPACE: damage dealt at 600 kPa");

            for (Suit suit : List.of(Suit.NETHERITE, Suit.JET)) {
                FakePlayer player = wearing(lab, lab.floor(0), suit, 0);
                verdict = Breathing.of(player);
                h.assertValueEqual(verdict.hazards(), Set.of(), suit + ": hazards at 600 kPa");
                h.assertTrue(verdict.protection().contains(Protection.PRESSURE), suit + " isn't rated for 600 kPa");
                Lab.check(player, 10);
                h.assertValueEqual(Lab.dealt(player), List.of(), suit + ": damage dealt at 600 kPa");
            }
        }
        h.succeed();
    }

    /**
     * At 737 K, in the open on Venus, the space suit's 500 K isn't enough: its wearer has the heat
     * hazard and burns, though the vacuum is kept out. The netherite suit and the jet suit keep both
     * out.
     */
    @GameTest(template = "empty")
    public static void space_022_only_the_heavy_suits_protect_at_737_k(GameTestHelper h) {
        try (Lab lab = Lab.onPlanet(h, Planet.VENUS, 72)) {
            double kelvin = lab.sample(0, 20, 0).temperature();
            h.assertTrue(Math.abs(kelvin - 737.15) < 1e-6, "Venus's temperature: " + kelvin);

            FakePlayer soft = wearing(lab, lab.open(10), Suit.SPACE, 0);
            Breathability verdict = Breathing.of(soft);
            h.assertValueEqual(verdict.hazards(), Set.of(Hazard.HEAT), "SPACE: hazards at 737 K");
            h.assertTrue(verdict.protection().contains(Protection.PRESSURE), "SPACE isn't rated for vacuum");
            h.assertFalse(verdict.protection().contains(Protection.THERMAL), "SPACE is rated for 737 K");
            Lab.check(soft, 3);
            h.assertTrue(Lab.dealt(soft).contains(BURNS), "SPACE: no burns at 737 K: " + Lab.dealt(soft));

            for (Suit suit : List.of(Suit.NETHERITE, Suit.JET)) {
                FakePlayer player = wearing(lab, lab.open(10), suit, 0);
                verdict = Breathing.of(player);
                h.assertValueEqual(verdict.hazards(), Set.of(), suit + ": hazards at 737 K");
                h.assertTrue(
                        verdict.protection().containsAll(Set.of(Protection.PRESSURE, Protection.THERMAL)),
                        suit + ": protection at 737 K: " + verdict.protection());
                Lab.check(player, 3);
                h.assertTrue(
                        Set.of(HYPOXIA).containsAll(Lab.dealt(player)),
                        suit + ": damage dealt at 737 K: " + Lab.dealt(player));
            }
        }
        h.succeed();
    }

    // ---- SPACE-023 ----------------------------------------------------------------------------

    /**
     * Internals draw from a full Ad Astra suit: on the Moon, with internals on, the wearer of each
     * suit breathes its oxygen at 21.3 kPa, and a minute of checks takes a minute's draw out of the
     * store, in whole mB taken ahead of use. Nothing harms the wearer, Ad Astra's check agrees, and
     * Ad Astra's own drain, which runs where its check finds no oxygen, takes nothing in a minute
     * of wearing.
     */
    @GameTest(template = "empty")
    public static void space_023_internals_draw_from_a_full_ad_astra_suit(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 73)) {
            for (Suit suit : Suit.values()) {
                FakePlayer player = wearing(lab, lab.open(10), suit, suit.capacity);
                h.assertValueEqual(
                        Breathing.of(player).source(), BreathSource.ATMOSPHERE, suit + ": the breath, internals off");
                Lab.internals(player, true);

                Breathability verdict = Breathing.of(player);
                h.assertValueEqual(verdict.source(), BreathSource.INTERNALS, suit + ": what it breathes from");
                h.assertValueEqual(verdict.reason(), BreathReason.BREATHABLE, suit + ": the verdict");
                h.assertTrue(verdict.isProtected(), suit + ": hazards on internals: " + verdict.hazards());
                GasMixture breath = verdict.breath().orElseThrow();
                close(h, breath.pressure(), 21.3, suit + ": the breath's pressure, kPa");
                close(h, breath.partialPressure(Lab.O2), 21.3, suit + ": the breath's O2, kPa");
                h.assertTrue(OxygenApi.API.hasOxygen(player), suit + ": Ad Astra's check found no oxygen");

                // 31 checks, 2 s apart: a minute's draw.
                Lab.check(player, 31);
                h.assertValueEqual(Lab.dealt(player), List.of(), suit + ": damage dealt on internals");
                double drawn = DRAW_PER_SECOND * 60;
                ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
                close(h, supply(h, chest).contents().totalMoles(), FULL - drawn, suit + ": moles left after a minute");
                int taken = (int) Math.ceil(drawn / (FULL / suit.capacity));
                h.assertValueEqual(Lab.oxygenIn(chest), suit.capacity - taken, suit + ": mB left after a minute");

                Lab.wear(player, 1200);
                h.assertValueEqual(
                        Lab.oxygenIn(player.getItemBySlot(EquipmentSlot.CHEST)),
                        suit.capacity - taken,
                        suit + ": mB left after a minute of wearing");
            }
        }
        h.succeed();
    }

    /**
     * A store is one ZAS³ O₂ tank scaled by how full it is: a full one of any size gives 17.5 mol of
     * O₂ at 293.15 K in 70 L, delivered at 21.3 kPa, and half a store gives 8.75 mol. Drawing takes
     * the oxygen out of Ad Astra's store. A store too low to deliver 21.3 kPa isn't offered as a tank,
     * so internals look on to the offhand and the hotbar; the other three pieces never are one.
     */
    @GameTest(template = "empty")
    public static void space_023_half_a_store_gives_8_75_mol(GameTestHelper h) {
        for (Suit suit : Suit.values()) {
            IInternalSupply full = supply(h, Lab.suitHolding(suit.chest, suit.capacity));
            GasMixture contents = full.contents();
            close(h, contents.moles(Lab.O2), FULL, suit + ": a full store's O2, mol");
            close(h, contents.totalMoles(), FULL, suit + ": a full store's gas, mol");
            close(h, contents.temperature(), StandardAir.TEMPERATURE, suit + ": its temperature, K");
            close(h, contents.volume(), 70, suit + ": its volume, L");
            close(h, full.distributionPressure(), 21.3, suit + ": its distribution pressure, kPa");

            IInternalSupply half = supply(h, Lab.suitHolding(suit.chest, suit.capacity / 2));
            close(h, half.contents().totalMoles(), FULL / 2, suit + ": half a store, mol");

            for (String other : List.of(suit.head, suit.legs, suit.feet)) {
                h.assertTrue(Lab.adAstra(other).getCapability(IInternalSupply.ITEM) == null, other + " is a tank");
            }
            h.assertTrue(
                    Lab.adAstra(suit.chest).getCapability(IInternalSupply.ITEM) == null, "an empty store is a tank");
        }

        // Drawing: 1 mol from half a space suit is 58 mB of its 500, taken whole, and the rest is all of it.
        ItemStack chest = Lab.suitHolding(Suit.SPACE.chest, 500);
        IInternalSupply store = supply(h, chest);
        GasParcel mole = store.draw(1.0);
        close(h, mole.moles(Lab.O2), 1.0, "the O2 drawn, mol");
        close(h, mole.temperature(), StandardAir.TEMPERATURE, "its temperature, K");
        h.assertValueEqual(Lab.oxygenIn(chest), 442, "mB left after drawing 1 mol");
        close(h, store.contents().totalMoles(), 7.75, "moles left after drawing 1 mol");
        GasParcel rest = store.draw(100);
        close(h, rest.totalMoles(), 7.75, "the rest drawn, mol");
        h.assertValueEqual(Lab.oxygenIn(chest), 0, "mB left after drawing the rest");
        h.assertTrue(store.contents().isEmpty(), "the store still holds " + store.contents());
        h.assertTrue(store.draw(1.0).isEmpty(), "an empty store gave gas");

        // 21.3 kPa in 70 L at 293.15 K is 0.61 mol: 35 mB of a space suit's 1,000.
        h.assertTrue(
                Lab.suitHolding(Suit.SPACE.chest, 34).getCapability(IInternalSupply.ITEM) == null,
                "a store that can't deliver 21.3 kPa is a tank");
        h.assertTrue(
                Lab.suitHolding(Suit.SPACE.chest, 36).getCapability(IInternalSupply.ITEM) != null,
                "a store that can deliver 21.3 kPa isn't a tank");
        h.succeed();
    }

    // ---- S-06 ---------------------------------------------------------------------------------

    /**
     * S-06, start to end. On the Moon a sealed room of ZAS³ air with no oxygen distributor is
     * breathable: a player in nothing stands in it for a minute unharmed, and Ad Astra's check
     * agrees though Ad Astra by itself finds no oxygen there. The player puts on a space suit, switches
     * internals on and goes outside: the suit is the life support, nothing harms the wearer for a
     * minute, and Ad Astra's check still agrees.
     */
    @GameTest(template = "empty")
    public static void s_06_a_room_on_the_moon_is_breathable_and_a_suit_is_life_support_outside(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 74)) {
            lab.room(0, Lab.AIR);
            FakePlayer player = lab.player(lab.floor(0));
            h.assertFalse(
                    OxygenApi.API.hasOxygen(lab.level, BlockPos.containing(player.getEyePosition())),
                    "Ad Astra by itself found oxygen in a room on the Moon");
            Breathability inside = Breathing.of(player);
            h.assertValueEqual(inside.reason(), BreathReason.BREATHABLE, "the verdict in the room");
            h.assertValueEqual(inside.source(), BreathSource.ATMOSPHERE, "what it breathes in the room");
            h.assertTrue(inside.isProtected(), "hazards in the room: " + inside.hazards());
            h.assertTrue(OxygenApi.API.hasOxygen(player), "Ad Astra's check found no oxygen in the room");
            Lab.check(player, 31);
            h.assertValueEqual(Lab.dealt(player), List.of(), "damage dealt in the room");

            Suit.SPACE.wornBy(player, 1000);
            Lab.internals(player, true);
            player.moveTo(lab.open(10));
            Breathability outside = Breathing.of(player);
            h.assertValueEqual(outside.reason(), BreathReason.BREATHABLE, "the verdict outside");
            h.assertValueEqual(outside.source(), BreathSource.INTERNALS, "what it breathes outside");
            h.assertTrue(outside.isProtected(), "hazards outside: " + outside.hazards());
            h.assertTrue(OxygenApi.API.hasOxygen(player), "Ad Astra's check found no oxygen in the suit");
            Lab.check(player, 31);
            Lab.wear(player, 1200);
            h.assertValueEqual(Lab.dealt(player), List.of(), "damage dealt outside");
            h.assertValueEqual(player.getHealth(), player.getMaxHealth(), "health");

            // Take the helmet off and the vacuum is back.
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            Breathability open = Breathing.of(player);
            h.assertValueEqual(open.reason(), BreathReason.LOW_OXYGEN, "the verdict without the helmet");
            h.assertFalse(OxygenApi.API.hasOxygen(player), "Ad Astra's check found oxygen without the helmet");
        }
        h.succeed();
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)),
                what + ": " + actual + ", not " + expected);
    }
}
