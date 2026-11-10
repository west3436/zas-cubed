package io.github.west3436.zas.compat.adastra.gametest;

import earth.terrarium.adastra.api.systems.OxygenApi;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.compat.adastra.AdAstraConfig;
import io.github.west3436.zas.compat.adastra.AdAstraConfig.ModeSetting;
import io.github.west3436.zas.compat.adastra.Mode;
import io.github.west3436.zas.config.AtmospherePreset;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The adapter's two modes (SPACE-001), their default from the preset (SPACE-004), and what an Ad
 * Astra oxygen distributor's area is to ZAS³ in Cooperative mode (SPACE-017).
 *
 * <p>A distributor's area here is a box of positions given oxygen and 22 °C through Ad Astra's API,
 * which is all a distributor does to the positions it reaches.
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class ModeTests {
    private ModeTests() {}

    /** A box in the open on the Moon made a distributor's area, and where something in it stands. */
    private static Vec3 area(Lab lab) {
        lab.distribute(lab.abs(8, 8, 0), lab.abs(12, 13, 4));
        return lab.open(10);
    }

    /**
     * The mode is Cooperative under the Normal preset and Authoritative under Hardcore, and the
     * adapter's own setting overrides that either way. The setting ships as "follow the preset".
     */
    @GameTest(template = "empty")
    public static void space_004_the_default_mode_follows_the_preset_and_the_setting_overrides_it(GameTestHelper h) {
        try (Lab lab = Lab.onTheOverworld(h, 40)) {
            h.assertValueEqual(AdAstraConfig.MODE.getDefault(), ModeSetting.PRESET, "the shipped setting");
            lab.mode(ModeSetting.PRESET);
            lab.preset(AtmospherePreset.NORMAL);
            h.assertValueEqual(Mode.current(), Mode.COOPERATIVE, "under Normal");
            lab.preset(AtmospherePreset.HARDCORE);
            h.assertValueEqual(Mode.current(), Mode.AUTHORITATIVE, "under Hardcore");

            lab.mode(ModeSetting.COOPERATIVE);
            h.assertValueEqual(Mode.current(), Mode.COOPERATIVE, "set to cooperative, under Hardcore");
            lab.preset(AtmospherePreset.NORMAL);
            lab.mode(ModeSetting.AUTHORITATIVE);
            h.assertValueEqual(Mode.current(), Mode.AUTHORITATIVE, "set to authoritative, under Normal");
        }
        h.succeed();
    }

    /**
     * In Cooperative mode a player in a distributor's area, outside any zone, breathes standard air
     * at 295.15 K as far as ZAS³'s rules go: the verdict says so, Ad Astra's check agrees, and a
     * minute of checks deals no harm. Atmosphere queries still report the Moon's vacuum there.
     */
    @GameTest(template = "empty")
    public static void space_017_a_distributor_area_is_standard_air_to_the_rules_in_cooperative_mode(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 41)) {
            lab.mode(ModeSetting.COOPERATIVE);
            FakePlayer player = lab.player(area(lab));

            Breathability verdict = Breathing.of(player);
            h.assertValueEqual(verdict.reason(), BreathReason.BREATHABLE, "the verdict");
            h.assertValueEqual(verdict.source(), BreathSource.ATMOSPHERE, "what it breathes from");
            h.assertTrue(verdict.isProtected(), "hazards in the area: " + verdict.hazards());
            GasMixture breath = verdict.breath().orElseThrow();
            close(h, breath.pressure(), StandardAir.PRESSURE, "the breath's pressure, kPa");
            close(h, breath.temperature(), 295.15, "the breath's temperature, K");
            close(h, breath.partialPressure(Lab.O2), StandardAir.PRESSURE * 0.21, "the breath's O2, kPa");
            close(h, breath.partialPressure(Lab.N2), StandardAir.PRESSURE * 0.79, "the breath's N2, kPa");
            h.assertTrue(OxygenApi.API.hasOxygen(player), "Ad Astra's check found no oxygen in the area");

            Lab.check(player, 60);
            h.assertValueEqual(Lab.dealt(player), List.of(), "damage dealt in the area");
            h.assertValueEqual(player.getHealth(), player.getMaxHealth(), "health");

            AtmosphereSample asked = Atmospheres.of(player);
            h.assertValueEqual(asked.state(), AtmosphereState.AMBIENT, "the atmosphere query's state");
            h.assertTrue(asked.mixture().isEmpty(), "the atmosphere query reports gas: " + asked);
            close(h, asked.temperature(), Moon.TEMPERATURE, "the atmosphere query's temperature, K");
        }
        h.succeed();
    }

    /**
     * A distributor's area counts as breathable only in Cooperative mode. In Authoritative mode the
     * same player in the same area is in the Moon's vacuum: the verdict, Ad Astra's check and a
     * thing that isn't alive all say so. Inside a zone the zone governs in both modes: a room of
     * thin air in the area stays thin air.
     */
    @GameTest(template = "empty")
    public static void space_001_a_distributor_area_is_breathable_only_in_cooperative_mode(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 42)) {
            lab.room(0, Map.of(Lab.N2, 90.0, Lab.O2, 10.0));
            lab.distribute(lab.abs(0, 0, 0), lab.abs(4, 4, 4));
            Vec3 open = area(lab);
            FakePlayer player = lab.player(open);
            FakePlayer inRoom = lab.player(lab.floor(0));
            ItemEntity item = lab.add(new ItemEntity(lab.level, open.x, open.y, open.z, new ItemStack(Items.STONE)));

            lab.mode(ModeSetting.COOPERATIVE);
            h.assertValueEqual(Breathing.of(player).reason(), BreathReason.BREATHABLE, "cooperative, in the area");
            h.assertTrue(OxygenApi.API.hasOxygen(player), "cooperative: Ad Astra's check for the player");
            h.assertTrue(OxygenApi.API.hasOxygen(item), "cooperative: Ad Astra's check for an item");
            h.assertValueEqual(Breathing.of(inRoom).reason(), BreathReason.LOW_OXYGEN, "cooperative, in the room");

            lab.mode(ModeSetting.AUTHORITATIVE);
            Breathability verdict = Breathing.of(player);
            h.assertValueEqual(verdict.reason(), BreathReason.LOW_OXYGEN, "authoritative, in the area");
            h.assertFalse(verdict.isProtected(), "authoritative: no hazard in a vacuum");
            h.assertFalse(OxygenApi.API.hasOxygen(player), "authoritative: Ad Astra's check for the player");
            h.assertFalse(OxygenApi.API.hasOxygen(item), "authoritative: Ad Astra's check for an item");
            h.assertValueEqual(Breathing.of(inRoom).reason(), BreathReason.LOW_OXYGEN, "authoritative, in the room");
        }
        h.succeed();
    }

    /**
     * In both modes ZAS³ alone deals harm. A pig in a distributor's area lives through a minute of
     * its own whole ticks, which Ad Astra hooks, and a minute of ZAS³'s checks: in Cooperative mode
     * nothing hurts it, and in Authoritative mode only ZAS³'s damage types do.
     */
    @GameTest(template = "empty")
    public static void space_001_only_zas_deals_harm_in_both_modes(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 43)) {
            Vec3 open = area(lab);

            lab.mode(ModeSetting.COOPERATIVE);
            Pig spared = lab.mob(EntityType.PIG, open);
            Lab.live(spared, 1200);
            Lab.check(spared, 60);
            h.assertValueEqual(Lab.dealt(spared), List.of(), "cooperative: damage dealt in the area");

            lab.mode(ModeSetting.AUTHORITATIVE);
            Pig harmed = lab.mob(EntityType.PIG, open);
            harmed.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            harmed.setHealth(1000);
            Lab.live(harmed, 1200);
            Lab.check(harmed, 60);
            List<ResourceLocation> dealt = Lab.dealt(harmed);
            h.assertFalse(dealt.isEmpty(), "authoritative: nothing hurt a pig in vacuum");
            for (ResourceLocation type : dealt) {
                h.assertValueEqual(type.getNamespace(), "zas", "authoritative: the namespace of " + type);
            }
            h.assertTrue(
                    dealt.stream().anyMatch(type -> !type.getPath().equals("hypoxia")),
                    "authoritative: only hypoxia was dealt, so the checks' hazards never ran: " + dealt);
        }
        h.succeed();
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= 1e-6 * Math.abs(expected), what + ": " + actual + ", not " + expected);
    }
}
