package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The atmosphere HUD's alert levels (CLIENT-008) and their sync to the player (DATA-014).
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
                0, 0, 0, 0, 101, AtmosHudPayload.NO_BREATH, 293, Optional.empty(), AtmosHudPayload.NO_BREATH, true);
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
     * partial pressure), and a value that isn't shown never counts.
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
        AtmosHudPayload hidden = new AtmosHudPayload(0, 0, 0, 0, 101, 21, 293, Optional.of(CO2), 0.04f);
        h.assertValueEqual(hidden, AtmosHudPayload.NONE, "values with no alert are dropped");
        h.assertTrue(base.differsFrom(null), "the first state is always sent");
        h.succeed();
    }

    /** The payload survives its stream codec. */
    @GameTest(batch = "hud", template = "empty")
    public static void data_014_payload_round_trips(GameTestHelper h) {
        for (AtmosHudPayload p : List.of(
                AtmosHudPayload.NONE,
                new AtmosHudPayload(-2, 2, 2, 2, 12.5f, 2.6f, 410, Optional.of(CO), 0.3f),
                new AtmosHudPayload(1, 0, -1, 0, 400, 0, 235, Optional.empty(), 0),
                new AtmosHudPayload(
                        0, 0, -1, 0, 101.3f, AtmosHudPayload.NO_BREATH, 230, Optional.empty(), 0.04f, true))) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            AtmosHudPayload.STREAM_CODEC.encode(buf, p);
            h.assertValueEqual(AtmosHudPayload.STREAM_CODEC.decode(buf), p, "round trip");
            h.assertValueEqual(buf.readableBytes(), 0, "bytes left over");
        }
        h.succeed();
    }
}
