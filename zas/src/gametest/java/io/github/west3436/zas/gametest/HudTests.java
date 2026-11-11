package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.HazardConfig;
import io.github.west3436.zas.config.ProtectionConfig;
import io.github.west3436.zas.hud.AtmosHud;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.HudLevels;
import io.github.west3436.zas.life.HazardProfile;
import io.github.west3436.zas.life.HazardProfiles;
import io.github.west3436.zas.life.HazardType;
import io.github.west3436.zas.life.Internals;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The atmosphere HUD's alert levels (CLIENT-008), what shows it (CLIENT-001), its internals
 * indicator (CLIENT-009) and their sync to the player (DATA-014).
 *
 * <p>The thresholds are checked on {@link HudLevels} directly, then in sealed rooms through {@link
 * AtmosHud#syncAt}, which records what would be sent. Like {@code HazardTests}, each room test
 * builds in its own area ({@link Rooms}, areas 300 and up) and runs within one game tick.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HudTests {
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");

    private HudTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    private static final class Lab implements AutoCloseable {
        final Rooms r;
        final GameTestHelper h;
        private final List<Runnable> restore = new ArrayList<>();

        Lab(GameTestHelper h, int index) {
            this.h = h;
            this.r = new Rooms(h, index);
        }

        /** A sealed room from x0 to x0 + 4 holding partial pressures in kPa at a temperature. */
        void room(int x0, Map<ResourceLocation, Double> kPa, double temperature) {
            r.clear(x0, x0 + 5);
            r.shell(x0, x0 + 4);
            r.settle(GasMutations.remove(
                    r.level,
                    r.point(x0 + 2, 2, 2),
                    Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                    Rooms.source("hud")));
            if (!kPa.isEmpty()) {
                Map<ResourceLocation, Double> moles = new HashMap<>();
                kPa.forEach((gas, p) -> moles.put(gas, p * 27_000 / (GasMixture.GAS_CONSTANT * temperature)));
                r.settle(GasMutations.add(
                        r.level, r.point(x0 + 2, 2, 2), new GasParcel(moles, temperature), Rooms.source("hud")));
            }
        }

        FakePlayer player(int x0) {
            FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_hud"));
            p.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
            return p;
        }

        /** Switches a hazard on or off until the test ends. */
        void enabled(HazardType t, boolean on) {
            boolean before = HazardConfig.ENABLED.get(t).get();
            restore.add(() -> HazardConfig.ENABLED.get(t).set(before));
            HazardConfig.ENABLED.get(t).set(on);
        }

        @Override
        public void close() {
            for (int i = restore.size() - 1; i >= 0; i--) {
                restore.get(i).run();
            }
        }
    }

    /** Air at a total pressure in kPa: 21 % O₂, the rest N₂. */
    private static Map<ResourceLocation, Double> air(double kPa) {
        Map<ResourceLocation, Double> m = new LinkedHashMap<>();
        m.put(StandardAir.OXYGEN, kPa * 0.21);
        m.put(StandardAir.NITROGEN, kPa * 0.79);
        return m;
    }

    /** The state the player would be sent now, as a first sync. */
    private static AtmosHudPayload synced(GameTestHelper h, FakePlayer p, String what) {
        List<AtmosHudPayload> sent = new ArrayList<>();
        AtmosHud.syncAt(p, p.level().getGameTime(), sent::add);
        h.assertValueEqual(sent.size(), 1, what + ": states sent");
        return sent.get(0);
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    /** The state a player's HUD should hold now, sent or not; the atmosphere at its eyes must be resolved. */
    private static AtmosHudPayload now(FakePlayer p) {
        return HudLevels.withIndicator(HudLevels.of(p), p);
    }

    /** Puts the test item that seals the breathing on a player's head. */
    private static void seal(FakePlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
    }

    // ---- CLIENT-008: thresholds ---------------------------------------------------------

    /**
     * Each reading's level either side of each threshold: pressure at 20, 50, 325 and 550 kPa, O₂
     * at 16 and 19.8 kPa, temperature at 220, 240, 340 and 360 K, and CO₂ and CO by their hazard
     * profiles.
     */
    @GameTest(batch = "hud", template = "empty")
    public static void client_008_levels_at_each_threshold(GameTestHelper h) {
        double[][] pressure = {
            {0, -2},
            {19.99, -2},
            {20, -1},
            {49.99, -1},
            {50, 0},
            {101.325, 0},
            {325, 0},
            {325.01, 1},
            {549.99, 1},
            {550, 2},
            {5000, 2}
        };
        for (double[] c : pressure) {
            h.assertValueEqual(HudLevels.pressure(c[0]), (int) c[1], "pressure at " + c[0] + " kPa");
        }
        double[][] oxygen = {{0, 2}, {15.99, 2}, {16, 1}, {19.79, 1}, {19.8, 0}, {21.3, 0}};
        for (double[] c : oxygen) {
            h.assertValueEqual(HudLevels.oxygen(c[0], 16), (int) c[1], "O2 at " + c[0] + " kPa");
        }
        double[][] temperature = {
            {2.7, -2},
            {219.9, -2},
            {220, -1},
            {239.9, -1},
            {240, 0},
            {293.15, 0},
            {340, 0},
            {340.1, 1},
            {360, 1},
            {360.1, 2},
            {1500, 2}
        };
        for (double[] c : temperature) {
            h.assertValueEqual(HudLevels.temperature(c[0], 220, 360), (int) c[1], "temperature at " + c[0] + " K");
        }
        HazardProfile co2 = HazardProfiles.all().get(CO2);
        HazardProfile co = HazardProfiles.all().get(CO);
        h.assertTrue(co2 != null && co != null, "the CO2 and CO hazard profiles are loaded");
        // CO2: a warning at 5 kPa, incapacitating only above 10 kPa.
        double[][] carbonDioxide = {{0, 0}, {4.99, 0}, {5, 1}, {10, 1}, {10.01, 2}};
        for (double[] c : carbonDioxide) {
            h.assertValueEqual(HudLevels.toxin(co2, c[0]), (int) c[1], "CO2 at " + c[0] + " kPa");
        }
        // CO: a warning at 0.005 kPa, damaging from 0.12 kPa.
        double[][] carbonMonoxide = {{0.004, 0}, {0.005, 1}, {0.119, 1}, {0.12, 2}};
        for (double[] c : carbonMonoxide) {
            h.assertValueEqual(HudLevels.toxin(co, c[0]), (int) c[1], "CO at " + c[0] + " kPa");
        }
        h.succeed();
    }

    /**
     * A player is sent each reading's level and value in rooms of thin air, vacuum, CO₂ and cold,
     * and nothing to show in normal air.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_008_rooms_sync_their_alerts(GameTestHelper h) {
        try (Lab lab = new Lab(h, 300)) {
            lab.room(0, air(StandardAir.PRESSURE), StandardAir.TEMPERATURE);
            lab.room(6, air(40), StandardAir.TEMPERATURE);
            lab.room(12, Map.of(), StandardAir.TEMPERATURE);
            Map<ResourceLocation, Double> stale = air(StandardAir.PRESSURE);
            stale.put(CO2, 6.0);
            lab.room(18, stale, StandardAir.TEMPERATURE);
            lab.room(24, air(StandardAir.PRESSURE), 230);

            h.assertValueEqual(synced(h, lab.player(0), "normal air"), AtmosHudPayload.NONE, "normal air");

            AtmosHudPayload thin = synced(h, lab.player(6), "40 kPa of air");
            h.assertValueEqual(thin.pressure(), -HudLevels.WARNING, "40 kPa: pressure");
            h.assertValueEqual(thin.oxygen(), HudLevels.DANGER, "40 kPa: O2 (8.4 kPa)");
            h.assertValueEqual(thin.temperature(), HudLevels.NONE, "40 kPa: temperature");
            close(h, thin.pressureKpa(), 40, 0.5, "40 kPa: pressure shown");
            close(h, thin.oxygenKpa(), 8.4, 0.1, "40 kPa: O2 shown");

            AtmosHudPayload vacuum = synced(h, lab.player(12), "vacuum");
            h.assertValueEqual(vacuum.pressure(), -HudLevels.DANGER, "vacuum: pressure");
            h.assertValueEqual(vacuum.oxygen(), HudLevels.DANGER, "vacuum: O2");
            h.assertValueEqual(vacuum.toxins(), HudLevels.NONE, "vacuum: toxins");

            AtmosHudPayload co2 = synced(h, lab.player(18), "6 kPa of CO2");
            h.assertValueEqual(co2.toxins(), HudLevels.WARNING, "CO2: toxins");
            h.assertValueEqual(co2.toxinGas(), Optional.of(CO2), "CO2: gas shown");
            close(h, co2.toxinKpa(), 6, 0.1, "CO2: partial pressure shown");
            h.assertValueEqual(co2.pressure(), HudLevels.NONE, "CO2: pressure");

            AtmosHudPayload cold = synced(h, lab.player(24), "230 K");
            h.assertValueEqual(cold.temperature(), -HudLevels.WARNING, "230 K: temperature");
            close(h, cold.temperatureK(), 230, 0.5, "230 K: temperature shown");
            h.assertValueEqual(cold.pressure(), HudLevels.NONE, "230 K: pressure");
        }
        h.succeed();
    }

    /**
     * Alerts judge what reaches the player: in 1 kPa of nitrogen at 100 K, a full suit leaves no pressure or
     * temperature alert, but without internals its breath still has no O₂; unsuited, all three
     * are dangers.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_008_suits_hide_what_does_not_reach_the_player(GameTestHelper h) {
        try (Lab lab = new Lab(h, 301)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 1.0), 100);
            FakePlayer suited = lab.player(0);
            for (EquipmentSlot slot : ProtectionConfig.SLOTS) {
                suited.setItemSlot(slot, new ItemStack(TestLifeSupport.item()));
            }
            AtmosHudPayload s = synced(h, suited, "suited");
            h.assertValueEqual(s.pressure(), HudLevels.NONE, "suited: pressure");
            h.assertValueEqual(s.temperature(), HudLevels.NONE, "suited: temperature");
            h.assertValueEqual(s.oxygen(), HudLevels.DANGER, "suited: O2");
            AtmosHudPayload bare = synced(h, lab.player(0), "unsuited");
            h.assertValueEqual(bare.pressure(), -HudLevels.DANGER, "unsuited: pressure");
            h.assertValueEqual(bare.temperature(), -HudLevels.DANGER, "unsuited: temperature");
            h.assertValueEqual(bare.oxygen(), HudLevels.DANGER, "unsuited: O2");
        }
        h.succeed();
    }

    /**
     * Alerts judge what reaches the player: a filter's wearer is shown no toxin alert for the CO it
     * keeps out of its breath, while the bare player beside it is (API-022).
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_008_a_filter_hides_the_gas_it_stops(GameTestHelper h) {
        try (Lab lab = new Lab(h, 304)) {
            Map<ResourceLocation, Double> kPa = air(StandardAir.PRESSURE);
            kPa.put(CO, 2.0);
            lab.room(0, kPa, StandardAir.TEMPERATURE);
            FakePlayer masked = lab.player(0);
            masked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
            h.assertValueEqual(synced(h, masked, "masked").toxins(), HudLevels.NONE, "masked: toxins");
            AtmosHudPayload bare = synced(h, lab.player(0), "bare");
            h.assertTrue(bare.toxins() != HudLevels.NONE, "bare: no toxin alert");
            h.assertValueEqual(bare.toxinGas(), Optional.of(CO), "bare: gas shown");
        }
        h.succeed();
    }

    /**
     * A hazard switched off has no alert (LIFE-031), and a creative player none at all (LIFE-037).
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_008_hazards_off_and_creative_have_no_alerts(GameTestHelper h) {
        try (Lab lab = new Lab(h, 302)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 1.0), 100);
            FakePlayer creative = lab.player(0);
            creative.setGameMode(GameType.CREATIVE);
            h.assertValueEqual(synced(h, creative, "creative"), AtmosHudPayload.NONE, "creative");
            lab.enabled(HazardType.DECOMPRESSION, false);
            lab.enabled(HazardType.FROSTBITE, false);
            AtmosHudPayload off = synced(h, lab.player(0), "decompression and frostbite off");
            h.assertValueEqual(off.pressure(), HudLevels.NONE, "decompression off: pressure");
            h.assertValueEqual(off.temperature(), HudLevels.NONE, "frostbite off: temperature");
            h.assertValueEqual(off.oxygen(), HudLevels.DANGER, "hypoxia still on: O2");
        }
        h.succeed();
    }

    // ---- CLIENT-008, CONT-034: every reading while an analyzer is held --------------------------

    /** Which of the four readings a state shows, top to bottom: pressure, O₂, toxins, temperature. */
    private static List<Boolean> shown(AtmosHudPayload s) {
        return List.of(
                s.shows(s.pressure(), s.pressureKpa()),
                s.shows(s.oxygen(), s.oxygenKpa()),
                s.shows(s.toxins(), s.toxinKpa()),
                s.shows(s.temperature(), s.temperatureK()));
    }

    /**
     * Holding an item in {@code #zas:shows_readings} in either hand shows all four readings, safe
     * ones with their values; with empty hands only those at warning or worse show. Here the item is a
     * clock, and {@code zas_atmos} adds its analyzer. In air at 230 K only the temperature has an
     * alert. A creative player holding one has its values and no alerts, and a spectator isn't shown
     * them. With no breath, O₂ and toxins have no value, so they don't show.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_008_a_held_analyzer_shows_every_reading(GameTestHelper h) {
        List<Boolean> all = List.of(true, true, true, true);
        try (Lab lab = new Lab(h, 320)) {
            lab.room(0, air(StandardAir.PRESSURE), 230);

            AtmosHudPayload bare = synced(h, lab.player(0), "empty hands");
            h.assertFalse(bare.readings(), "empty hands: every reading shown");
            h.assertValueEqual(shown(bare), List.of(false, false, false, true), "empty hands: readings shown");
            h.assertValueEqual(bare.pressureKpa(), 0f, "empty hands: a safe value sent");

            FakePlayer p = lab.player(0);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CLOCK));
            AtmosHudPayload held = synced(h, p, "main hand");
            h.assertValueEqual(shown(held), all, "main hand: readings shown");
            h.assertValueEqual(held.pressure(), HudLevels.NONE, "main hand: pressure");
            h.assertValueEqual(held.oxygen(), HudLevels.NONE, "main hand: O2");
            h.assertValueEqual(held.toxins(), HudLevels.NONE, "main hand: toxins");
            h.assertValueEqual(held.temperature(), -HudLevels.WARNING, "main hand: temperature");
            close(h, held.pressureKpa(), StandardAir.PRESSURE, 0.5, "main hand: pressure shown");
            close(h, held.oxygenKpa(), StandardAir.PRESSURE * 0.21, 0.1, "main hand: O2 shown");
            close(h, held.temperatureK(), 230, 0.5, "main hand: temperature shown");
            h.assertValueEqual(held.toxinGas(), Optional.empty(), "main hand: toxic gas shown");
            h.assertValueEqual(held.toxinKpa(), 0f, "main hand: toxins shown");

            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CLOCK));
            h.assertValueEqual(HudLevels.of(p), held, "off hand: the state");
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            AtmosHudPayload away = HudLevels.of(p);
            h.assertValueEqual(away, bare, "put away: the state");
            h.assertTrue(away.differsFrom(held), "putting it away sends nothing");

            FakePlayer creative = lab.player(0);
            creative.setGameMode(GameType.CREATIVE);
            creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CLOCK));
            AtmosHudPayload c = HudLevels.of(creative);
            h.assertValueEqual(shown(c), all, "creative: readings shown");
            h.assertFalse(c.any(), "creative: an alert");
            close(h, c.temperatureK(), 230, 0.5, "creative: temperature shown");
            creative.setGameMode(GameType.SPECTATOR);
            h.assertFalse(HudLevels.showsReadings(creative), "a spectator is shown every reading");
        }
        AtmosHudPayload breathless = new AtmosHudPayload(
                0,
                0,
                0,
                0,
                101,
                AtmosHudPayload.NO_BREATH,
                293,
                Optional.empty(),
                AtmosHudPayload.NO_BREATH,
                false,
                AtmosHudPayload.INTERNALS_OFF,
                AtmosHudPayload.NO_TANK,
                true);
        h.assertValueEqual(shown(breathless), List.of(true, false, false, true), "no breath: readings shown");
        h.succeed();
    }

    // ---- DATA-014: own-state sync -----------------------------------------------------------

    /**
     * A player is sent its state at once, then nothing while it doesn't change; a change waits
     * until 5 ticks after the last send, so at most 4 a second.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void data_014_hud_syncs_on_change_at_most_4_a_second(GameTestHelper h) {
        try (Lab lab = new Lab(h, 303)) {
            lab.room(0, Map.of(), StandardAir.TEMPERATURE);
            lab.room(6, air(StandardAir.PRESSURE), StandardAir.TEMPERATURE);
            FakePlayer p = lab.player(0);
            List<AtmosHudPayload> sent = new ArrayList<>();
            long t = p.level().getGameTime();
            AtmosHud.syncAt(p, t, sent::add);
            h.assertValueEqual(sent.size(), 1, "the first state");
            AtmosHud.syncAt(p, t + 5, sent::add);
            AtmosHud.syncAt(p, t + 10, sent::add);
            h.assertValueEqual(sent.size(), 1, "states sent while nothing changed");
            p.moveTo(Vec3.atBottomCenterOf(lab.r.abs(8, 1, 2)));
            AtmosHud.syncAt(p, t + 11, sent::add);
            h.assertValueEqual(sent.size(), 2, "a change 11 ticks after the last send");
            h.assertValueEqual(sent.get(1), AtmosHudPayload.NONE, "the state in normal air");
            p.moveTo(Vec3.atBottomCenterOf(lab.r.abs(2, 1, 2)));
            AtmosHud.syncAt(p, t + 12, sent::add);
            AtmosHud.syncAt(p, t + 15, sent::add);
            h.assertValueEqual(sent.size(), 2, "a change 1 and 4 ticks after the last send");
            AtmosHud.syncAt(p, t + 16, sent::add);
            h.assertValueEqual(sent.size(), 3, "the change 5 ticks after the last send");
            h.assertValueEqual(sent.get(2), sent.get(0), "the state back in the vacuum");
        }
        h.succeed();
    }

    /**
     * A shown value is resent when it changes by its display resolution (1 kPa, 1 K, 0.1 kPa
     * partial pressure), and a value that isn't shown never counts. A trace of a toxic gas at warning
     * or worse, which the HUD writes to two significant figures, is resent when its second figure
     * changes, and never for less than 0.00001 kPa. A safe trace, shown with the analyzer in hand,
     * is written to one decimal and resent at 0.1 kPa.
     */
    @GameTest(batch = "hud", template = "empty")
    public static void data_014_values_resend_at_their_resolution(GameTestHelper h) {
        AtmosHudPayload base = new AtmosHudPayload(-1, 1, -1, 1, 40, 18, 230, Optional.of(CO2), 6);
        h.assertFalse(base.differsFrom(base), "the same state");
        h.assertFalse(
                new AtmosHudPayload(-1, 1, -1, 1, 40.9f, 18.09f, 230.9f, Optional.of(CO2), 6.09f).differsFrom(base),
                "changes under the resolution");
        h.assertTrue(new AtmosHudPayload(-1, 1, -1, 1, 41, 18, 230, Optional.of(CO2), 6).differsFrom(base), "1 kPa");
        h.assertTrue(new AtmosHudPayload(-1, 1, -1, 1, 40, 18, 231, Optional.of(CO2), 6).differsFrom(base), "1 K");
        h.assertTrue(
                new AtmosHudPayload(-1, 1, -1, 1, 40, 18.1f, 230, Optional.of(CO2), 6).differsFrom(base),
                "0.1 kPa of O2");
        h.assertTrue(
                new AtmosHudPayload(-1, 1, -1, 1, 40, 18, 230, Optional.of(CO2), 6.1f).differsFrom(base),
                "0.1 kPa of CO2");
        h.assertTrue(
                new AtmosHudPayload(-2, 1, -1, 1, 40, 18, 230, Optional.of(CO2), 6).differsFrom(base),
                "a threshold crossed");
        AtmosHudPayload trace = toxin(0.002f);
        h.assertFalse(toxin(0.00196f).differsFrom(trace), "a trace, changed by under its second figure");
        h.assertTrue(toxin(0.0019f).differsFrom(trace), "0.0020 kPa of CO down to 0.0019");
        h.assertTrue(toxin(0.0021f).differsFrom(trace), "0.0020 kPa of CO up to 0.0021");
        h.assertFalse(toxin(0.000196f).differsFrom(toxin(0.0002f)), "a smaller trace, changed by under its second");
        h.assertTrue(toxin(0.00019f).differsFrom(toxin(0.0002f)), "0.00020 kPa of CO down to 0.00019");
        h.assertTrue(toxin(0.00093f).differsFrom(toxin(0.00102f)), "0.0010 kPa of CO down to 0.00093");
        h.assertTrue(toxin(0.095f).differsFrom(toxin(0.12f)), "0.1 kPa of CO down to a trace");
        h.assertFalse(toxin(0.19f).differsFrom(toxin(0.12f)), "no trace, changed by under 0.1 kPa");
        h.assertFalse(toxin(0.000024f).differsFrom(toxin(0.00002f)), "a trace, changed by under 0.00001 kPa");
        h.assertTrue(toxin(0.00003f).differsFrom(toxin(0.00002f)), "0.00002 kPa of CO up to 0.00003");
        AtmosHudPayload warning = new AtmosHudPayload(0, 0, 0, 1, 101, 21, 293, Optional.of(CO), 0.00094f);
        h.assertTrue(
                new AtmosHudPayload(0, 0, 0, 1, 101, 21, 293, Optional.of(CO), 0.00073f).differsFrom(warning),
                "a trace at warning, 0.00094 kPa down to 0.00073");
        AtmosHudPayload safe = safe(0.0002f);
        h.assertValueEqual(safe.toxinKpa(), 0.0002f, "a safe trace is kept while every reading is shown");
        h.assertFalse(safe(0.00062f).differsFrom(safe), "a safe trace of CO2, grown threefold");
        h.assertFalse(safe(0.09f).differsFrom(safe), "a safe trace, changed by under 0.1 kPa");
        h.assertTrue(safe(0.11f).differsFrom(safe), "a safe reading, changed by 0.1 kPa");
        AtmosHudPayload hidden = new AtmosHudPayload(0, 0, 0, 0, 101, 21, 293, Optional.of(CO2), 0.04f);
        h.assertValueEqual(hidden, AtmosHudPayload.NONE, "values with no alert are dropped");
        h.assertTrue(base.differsFrom(null), "the first state is always sent");
        h.succeed();
    }

    /** A state with no alert and every reading shown, as with the analyzer in hand: CO2 at a partial pressure. */
    private static AtmosHudPayload safe(float kPa) {
        return new AtmosHudPayload(
                0,
                0,
                0,
                0,
                101,
                21,
                293,
                Optional.of(CO2),
                kPa,
                false,
                AtmosHudPayload.INTERNALS_OFF,
                AtmosHudPayload.NO_TANK,
                true);
    }

    /** A state whose one alert is CO in the breath, at a partial pressure in kPa. */
    private static AtmosHudPayload toxin(float kPa) {
        return new AtmosHudPayload(0, 0, 0, 2, 101, 21, 293, Optional.of(CO), kPa);
    }

    /** The payload survives its stream codec. */
    @GameTest(batch = "hud", template = "empty")
    public static void data_014_payload_round_trips(GameTestHelper h) {
        for (AtmosHudPayload p : List.of(
                AtmosHudPayload.NONE,
                new AtmosHudPayload(-2, 2, 2, 2, 12.5f, 2.6f, 410, Optional.of(CO), 0.3f),
                new AtmosHudPayload(1, 0, -1, 0, 400, 0, 235, Optional.empty(), 0),
                new AtmosHudPayload(
                        0,
                        0,
                        -1,
                        0,
                        101.3f,
                        AtmosHudPayload.NO_BREATH,
                        230,
                        Optional.empty(),
                        0.04f,
                        true,
                        AtmosHudPayload.INTERNALS_ON,
                        250,
                        true))) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            AtmosHudPayload.STREAM_CODEC.encode(buf, p);
            h.assertValueEqual(AtmosHudPayload.STREAM_CODEC.decode(buf), p, "round trip");
            h.assertValueEqual(buf.readableBytes(), 0, "bytes left over");
        }
        h.succeed();
    }

    /**
     * A trigger, the internals and the tank's pressure send a state as a shown value does: the tank
     * at 1 kPa, the internals and the HUD showing or hiding at any change. A hidden HUD holds neither
     * internals nor a tank, and a trigger under an alert changes nothing shown.
     */
    @GameTest(batch = "hud", template = "empty")
    public static void data_014_the_indicator_resends_at_its_resolution(GameTestHelper h) {
        int on = AtmosHudPayload.INTERNALS_ON;
        AtmosHudPayload base = AtmosHudPayload.NONE.withIndicator(true, on, 200);
        h.assertFalse(base.differsFrom(base), "the same state");
        h.assertFalse(AtmosHudPayload.NONE.withIndicator(true, on, 200.9f).differsFrom(base), "under 1 kPa");
        h.assertTrue(AtmosHudPayload.NONE.withIndicator(true, on, 199).differsFrom(base), "1 kPa");
        h.assertTrue(
                AtmosHudPayload.NONE
                        .withIndicator(true, AtmosHudPayload.INTERNALS_OFF, 200)
                        .differsFrom(base),
                "internals off");
        h.assertTrue(
                AtmosHudPayload.NONE
                        .withIndicator(true, AtmosHudPayload.INTERNALS_FAILING, 200)
                        .differsFrom(base),
                "internals failing");
        h.assertTrue(
                AtmosHudPayload.NONE
                        .withIndicator(true, on, AtmosHudPayload.NO_TANK)
                        .differsFrom(base),
                "the tank gone");
        h.assertTrue(
                AtmosHudPayload.NONE
                        .withIndicator(true, on, 0)
                        .differsFrom(AtmosHudPayload.NONE.withIndicator(true, on, AtmosHudPayload.NO_TANK)),
                "an empty tank in place of none");
        h.assertTrue(AtmosHudPayload.NONE.differsFrom(base), "hidden");
        h.assertTrue(base.differsFrom(AtmosHudPayload.NONE), "shown");

        h.assertValueEqual(AtmosHudPayload.NONE.withIndicator(false, on, 200), AtmosHudPayload.NONE, "hidden");
        AtmosHudPayload alert = new AtmosHudPayload(-1, 0, 0, 0, 40, 0, 0, Optional.empty(), 0);
        h.assertFalse(alert.withIndicator(true, 0, -1).differsFrom(alert), "a trigger under an alert");
        h.assertValueEqual(alert.withIndicator(false, on, 200).tankKpa(), 200f, "the tank under an alert");
        h.succeed();
    }

    /**
     * A state with a tank draining slowly goes once for each 1 kPa, and a tank draining fast goes at
     * most every 5 ticks, 4 a second.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void data_014_a_draining_tank_syncs_at_most_4_a_second(GameTestHelper h) {
        try (Lab lab = new Lab(h, 305)) {
            lab.room(0, air(StandardAir.PRESSURE), StandardAir.TEMPERATURE);
            FakePlayer p = lab.player(0);
            seal(p);
            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(Map.of(StandardAir.OXYGEN, 1.0), 21.3));
            Internals.set(p, true);
            IInternalSupply tank = Internals.find(p).orElseThrow();
            double molesPerKpa = TestLifeSupport.TANK_VOLUME / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
            List<AtmosHudPayload> sent = new ArrayList<>();
            long t = p.level().getGameTime();
            AtmosHud.syncAt(p, t, sent::add);
            h.assertValueEqual(sent.size(), 1, "the first state");

            // 0.04 kPa a tick for 100 ticks: 4 kPa.
            for (int i = 1; i <= 100; i++) {
                tank.draw(0.04 * molesPerKpa);
                AtmosHud.syncAt(p, t + i, sent::add);
            }
            h.assertValueEqual(sent.size(), 5, "states sent while 4 kPa drained");
            for (int i = 1; i < sent.size(); i++) {
                close(h, sent.get(i - 1).tankKpa() - sent.get(i).tankKpa(), 1, 0.05, "the drop between states");
            }

            // 1 kPa a tick for 20 ticks.
            int before = sent.size();
            long last = t + 100;
            for (int i = 1; i <= 20; i++) {
                tank.draw(molesPerKpa);
                AtmosHudPayload out = AtmosHud.syncAt(p, last + i, sent::add);
                if (out != null) {
                    h.assertValueEqual(i % 5, 0, "the tick of a state sent");
                }
            }
            h.assertValueEqual(sent.size() - before, 4, "states sent in 20 ticks");
        }
        h.succeed();
    }

    // ---- CLIENT-001: what shows the HUD -----------------------------------------------------

    /**
     * In normal air, a head item that seals the breathing shows the HUD, by its capability or by
     * protection data. Nothing else on the head does, nor a sealing item elsewhere, and it raises no
     * alert. Taking it off sends the state that hides the HUD.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_001_a_sealed_head_shows_the_hud(GameTestHelper h) {
        try (Lab lab = new Lab(h, 306)) {
            lab.room(0, air(StandardAir.PRESSURE), StandardAir.TEMPERATURE);
            FakePlayer p = lab.player(0);
            h.assertValueEqual(now(p), AtmosHudPayload.NONE, "bare-headed");
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(TestLifeSupport.item()));
            h.assertFalse(now(p).shown(), "a sealing item on the chest");
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            h.assertFalse(now(p).shown(), "a helmet with no seal");
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            h.assertTrue(now(p).shown(), "a head sealed by protection data");
            seal(p);
            AtmosHudPayload sealed = now(p);
            h.assertTrue(sealed.shown(), "a head sealed by its capability");
            h.assertFalse(sealed.any(), "a sealed head in normal air raises an alert");

            List<AtmosHudPayload> sent = new ArrayList<>();
            long t = p.level().getGameTime();
            AtmosHud.syncAt(p, t, sent::add);
            h.assertValueEqual(sent, List.of(sealed), "the state with the head on");
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            AtmosHud.syncAt(p, t + 5, sent::add);
            h.assertValueEqual(sent, List.of(sealed, AtmosHudPayload.NONE), "the state with it off");
        }
        h.succeed();
    }

    /**
     * An item in {@code #zas:shows_hud}, a spyglass standing in for an analyzer, shows the HUD while
     * held in either hand. It doesn't elsewhere in the inventory or worn, and an item outside the tag
     * doesn't in the hand.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_001_a_held_tag_item_shows_the_hud(GameTestHelper h) {
        try (Lab lab = new Lab(h, 307)) {
            lab.room(0, air(StandardAir.PRESSURE), StandardAir.TEMPERATURE);
            FakePlayer p = lab.player(0);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            h.assertFalse(now(p).shown(), "a stick in the hand");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
            h.assertTrue(now(p).shown(), "in the main hand");
            h.assertFalse(now(p).any(), "held in normal air, an alert");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SPYGLASS));
            h.assertTrue(now(p).shown(), "in the offhand");
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            p.getInventory().setItem(1, new ItemStack(Items.SPYGLASS));
            h.assertFalse(now(p).shown(), "in a hotbar slot not selected");
            p.getInventory().setItem(1, ItemStack.EMPTY);
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SPYGLASS));
            h.assertFalse(now(p).shown(), "worn");
        }
        h.succeed();
    }

    /**
     * A creative player's sealed head shows the HUD in air that would alert another, still with no
     * alert (LIFE-037). A spectator's shows nothing.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_001_creative_is_shown_the_hud_and_a_spectator_is_not(GameTestHelper h) {
        try (Lab lab = new Lab(h, 308)) {
            lab.room(0, Map.of(StandardAir.NITROGEN, 1.0), 100);
            FakePlayer p = lab.player(0);
            seal(p);
            p.setGameMode(GameType.CREATIVE);
            AtmosHudPayload creative = now(p);
            h.assertTrue(creative.shown(), "creative, sealed");
            h.assertFalse(creative.any(), "creative, an alert");
            p.setGameMode(GameType.SPECTATOR);
            h.assertValueEqual(now(p), AtmosHudPayload.NONE, "a spectator, sealed");
        }
        h.succeed();
    }

    // ---- CLIENT-009: the internals indicator -----------------------------------------------

    /**
     * While the HUD is shown, the state holds what the internals do and the pressure of the first
     * tank found, on or off: off, on with a tank that delivers, and failing with no tank or one that
     * can't deliver. A hidden HUD holds neither, even with internals on and a tank.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_009_internals_and_the_tanks_pressure(GameTestHelper h) {
        try (Lab lab = new Lab(h, 309)) {
            lab.room(0, air(StandardAir.PRESSURE), StandardAir.TEMPERATURE);
            FakePlayer p = lab.player(0);
            seal(p);
            Map<ResourceLocation, Double> full = Map.of(StandardAir.OXYGEN, 1.0);
            double fullKpa = GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE / TestLifeSupport.TANK_VOLUME;

            AtmosHudPayload none = now(p);
            h.assertValueEqual(none.internals(), AtmosHudPayload.INTERNALS_OFF, "off, no tank");
            h.assertValueEqual(none.tankKpa(), AtmosHudPayload.NO_TANK, "off, no tank: pressure");
            Internals.set(p, true);
            none = now(p);
            h.assertValueEqual(none.internals(), AtmosHudPayload.INTERNALS_FAILING, "on, no tank");
            h.assertValueEqual(none.tankKpa(), AtmosHudPayload.NO_TANK, "on, no tank: pressure");

            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(full, 21.3));
            AtmosHudPayload on = now(p);
            h.assertValueEqual(on.internals(), AtmosHudPayload.INTERNALS_ON, "on");
            close(h, on.tankKpa(), fullKpa, 0.01, "on: pressure");
            Internals.set(p, false);
            AtmosHudPayload off = now(p);
            h.assertValueEqual(off.internals(), AtmosHudPayload.INTERNALS_OFF, "off");
            close(h, off.tankKpa(), fullKpa, 0.01, "off: pressure");
            Internals.set(p, true);

            p.setItemSlot(EquipmentSlot.CHEST, TestLifeSupport.tank(Map.of(StandardAir.OXYGEN, 0.5), 21.3));
            close(h, now(p).tankKpa(), fullKpa / 2, 0.01, "the first tank found (LIFE-028)");
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);

            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(full, fullKpa + 1));
            AtmosHudPayload failing = now(p);
            h.assertValueEqual(failing.internals(), AtmosHudPayload.INTERNALS_FAILING, "a tank below its setting");
            close(h, failing.tankKpa(), fullKpa, 0.01, "a tank below its setting: pressure");

            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(full, 21.3));
            h.assertValueEqual(now(p), AtmosHudPayload.NONE, "hidden, on, with a tank");
        }
        h.succeed();
    }

    /** An alert shows the indicator too: bare-headed in thin air, internals on fail with no seal, and the tank shows. */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_009_an_alert_shows_the_indicator(GameTestHelper h) {
        try (Lab lab = new Lab(h, 310)) {
            lab.room(0, air(40), StandardAir.TEMPERATURE);
            FakePlayer p = lab.player(0);
            Internals.set(p, true);
            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(Map.of(StandardAir.OXYGEN, 1.0), 21.3));
            AtmosHudPayload s = now(p);
            h.assertTrue(s.any(), "40 kPa has an alert");
            h.assertFalse(s.triggered(), "bare-headed, a trigger");
            h.assertValueEqual(s.internals(), AtmosHudPayload.INTERNALS_FAILING, "on, no seal");
            close(
                    h,
                    s.tankKpa(),
                    GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE / TestLifeSupport.TANK_VOLUME,
                    0.01,
                    "the tank");
        }
        h.succeed();
    }

    /**
     * While the atmosphere at the eyes is unresolved the last readings stand, and the internals still
     * reach the client. A player with nothing sent yet waits. The unresolved region is one held at a
     * breach because it reaches an unloaded chunk (GEO-026), as in LIFE-048's test.
     */
    @GameTest(batch = "hud", template = "empty", timeoutTicks = 200)
    public static void client_009_internals_update_while_the_atmosphere_is_unresolved(GameTestHelper h) {
        try (Lab lab = new Lab(h, 311)) {
            Rooms r = lab.r;
            r.clear(0, 23);
            r.shell(2, 9); // A, in the west chunk: a vacuum
            r.shell(9, 21); // B, unzoned, across both chunks, sharing the wall at x = 9
            r.settle(GasMutations.remove(
                    r.level,
                    r.point(5, 2, 2),
                    Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                    Rooms.source("hud")));
            FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_hud"));
            p.moveTo(Vec3.atBottomCenterOf(r.abs(5, 1, 2)));
            List<AtmosHudPayload> sent = new ArrayList<>();
            long t = p.level().getGameTime();
            AtmosHud.syncAt(p, t, sent::add);
            h.assertValueEqual(sent.size(), 1, "the state in the vacuum");
            AtmosHudPayload vacuum = sent.get(0);
            h.assertValueEqual(vacuum.pressure(), -HudLevels.DANGER, "the vacuum: pressure");
            h.assertValueEqual(vacuum.internals(), AtmosHudPayload.INTERNALS_OFF, "the vacuum: internals");

            ChunkPos east = new ChunkPos(r.abs(16, 0, 0));
            byte[] saved = r.dimension().simulateUnload(east);
            try {
                r.tick();
                r.set(9, 2, 2, Blocks.AIR.defaultBlockState());
                r.tick();
                Vec3 inB = Vec3.atBottomCenterOf(r.abs(12, 1, 2));
                p.moveTo(inB);
                h.assertValueEqual(Atmospheres.of(p).state(), AtmosphereState.UNRESOLVED, "B's loaded part");
                AtmosHud.syncAt(p, t + 5, sent::add);
                h.assertValueEqual(sent.size(), 1, "states sent with nothing changed");
                Internals.set(p, true);
                AtmosHud.syncAt(p, t + 10, sent::add);
                h.assertValueEqual(sent.size(), 2, "states sent with internals on");
                h.assertValueEqual(sent.get(1).internals(), AtmosHudPayload.INTERNALS_FAILING, "internals on");
                h.assertValueEqual(sent.get(1).pressure(), vacuum.pressure(), "the reading that stood");

                FakePlayer fresh = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_hud"));
                fresh.moveTo(inB);
                seal(fresh);
                h.assertTrue(AtmosHud.syncAt(fresh, t, sent::add) == null, "a state with nothing to go on");
            } finally {
                r.dimension().simulateLoad(east, saved);
            }
        }
        h.succeed();
    }

    /** The indicator survives the stream codec. */
    @GameTest(batch = "hud", template = "empty")
    public static void client_009_payload_round_trips(GameTestHelper h) {
        for (AtmosHudPayload p : List.of(
                AtmosHudPayload.NONE.withIndicator(true, AtmosHudPayload.INTERNALS_ON, 608.5f),
                AtmosHudPayload.NONE.withIndicator(true, AtmosHudPayload.INTERNALS_FAILING, AtmosHudPayload.NO_TANK),
                new AtmosHudPayload(-2, 2, 2, 2, 12.5f, 2.6f, 410, Optional.of(CO), 0.3f)
                        .withIndicator(false, AtmosHudPayload.INTERNALS_OFF, 12))) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            AtmosHudPayload.STREAM_CODEC.encode(buf, p);
            h.assertValueEqual(AtmosHudPayload.STREAM_CODEC.decode(buf), p, "round trip");
            h.assertValueEqual(buf.readableBytes(), 0, "bytes left over");
        }
        h.succeed();
    }
}
