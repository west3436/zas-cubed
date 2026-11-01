package io.github.west3436.zas.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.PresetOption;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.config.ZoneTrigger;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The atmosphere preset and the options it sets (OPS-007, OPS-014, OPS-015).
 *
 * <p>The preset and the overrides are the whole server's, so these tests run in a batch of their
 * own, and each sets them and restores them within one game tick: it drives the atmos ticks it
 * needs with {@link io.github.west3436.zas.simulation.AtmosService#tickNow}. Commands run with
 * {@link ZasServerConfig#holdSavesForTest}, so they don't write the config file, whose reload
 * would drop other tests' unsaved values.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PresetTests {
    private static final String BATCH = "ops_preset";
    private static final double EPS = 1e-9;
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation O2 = StandardAir.OXYGEN;

    /**
     * The defaults the requirements give each option, Normal then Hardcore: written out here rather
     * than read from the declarations, so a wrong default fails.
     */
    private static final Map<String, List<Object>> DEFAULTS = new LinkedHashMap<>();

    static {
        // ENV-002, ENV-012 (D-19): the vanilla dimensions' ambients.
        DEFAULTS.put("ambient_variant", List.of(AtmospherePreset.NORMAL, AtmospherePreset.HARDCORE));
        // CORE-056, FIRE-024 (D-13): combustion is a Hardcore zone trigger.
        DEFAULTS.put("combustion_forms_zones", List.of(false, true));
        // CORE-056, CONT-053 (D-33): so is planting.
        DEFAULTS.put("planting_forms_zones", List.of(false, true));
        // FLOW-007, FLOW-016 (D-28): block pressure damage only in Hardcore.
        DEFAULTS.put("block_pressure_damage", List.of(false, true));
        // CONT-057 (D-61): mol/h at light 15.
        DEFAULTS.put("crop_photosynthesis_rate", List.of(0.125, 0.0625));
        DEFAULTS.put("leaf_photosynthesis_rate", List.of(0.0625, 0.03125));
        // CONT-107 (D-57): container explosions break blocks only in Hardcore.
        DEFAULTS.put("container_explosions_break_blocks", List.of(false, true));
    }

    private PresetTests() {}

    // ---- OPS-007 ------------------------------------------------------------------------

    /**
     * With no overrides, every preset-controlled option has the default its requirement gives under
     * each preset, and says it comes from the preset (OPS-007). The zone triggers follow (CORE-055,
     * CORE-056), and the ambient variant is the preset's (ENV-002, ENV-012).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void ops_007_option_defaults_follow_the_preset(GameTestHelper h) {
        requireNoOverrides(h);
        h.assertValueEqual(
                PresetOptions.all().stream().map(PresetOption::key).toList(),
                List.copyOf(DEFAULTS.keySet()),
                "the preset options");
        ServerLevel nether = level(h, Level.NETHER);
        BlockPos open = new BlockPos(0, 100, 0);
        nether.getChunk(open);
        for (AtmospherePreset preset : AtmospherePreset.values()) {
            withPreset(h, preset, () -> {
                int index = preset == AtmospherePreset.NORMAL ? 0 : 1;
                for (PresetOption<?> option : PresetOptions.all()) {
                    Object expected = DEFAULTS.get(option.key()).get(index);
                    String what = option.key() + " under " + preset;
                    h.assertValueEqual(option.get(), expected, what);
                    h.assertValueEqual(option.defaultFor(preset), expected, what + ", default");
                    h.assertValueEqual(option.source(), PresetOption.Source.PRESET, what + ", source");
                }
                boolean hardcore = preset == AtmospherePreset.HARDCORE;
                h.assertTrue(ZoneTrigger.API_CALLS.active(), "API calls don't form zones under " + preset);
                h.assertTrue(ZoneTrigger.BREACHES.active(), "breaches don't form zones under " + preset);
                h.assertValueEqual(ZoneTrigger.COMBUSTION.active(), hardcore, "combustion under " + preset);
                h.assertValueEqual(ZoneTrigger.PLANTING.active(), hardcore, "planting under " + preset);

                // The ambient variant applies: the Nether is 40 °C standard air under Normal, hot
                // and CO2-rich under Hardcore; the End's air is thin under Hardcore.
                Ambient netherAir = Ambients.ambient(Level.NETHER);
                close(h, netherAir.temperature(), hardcore ? 340 : 313.15, "Nether temperature under " + preset);
                close(h, netherAir.partialPressure(CO2.toString()), hardcore ? 12 : 0, "Nether CO2 under " + preset);
                close(
                        h,
                        Ambients.ambient(Level.END).pressure(),
                        hardcore ? 40 : StandardAir.PRESSURE,
                        "End pressure under " + preset);
                AtmosphereSample sample = Atmospheres.at(nether, open);
                close(h, sample.temperature(), netherAir.temperature(), "Nether sample under " + preset);
            });
        }
        h.succeed();
    }

    /**
     * Hardcore adds zone triggers to Normal's, so an API call still forms a zone in a sealed room
     * under Hardcore (OPS-007, CORE-055, CORE-056).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_007_hardcore_keeps_the_normal_zone_triggers(GameTestHelper h) {
        requireNoOverrides(h);
        Rooms r = new Rooms(h, h.getLevel(), new BlockPos(-8192, 100, 8192));
        r.clear(0, 0, 6, 6);
        r.box(0, 0, 4, 4);
        withPreset(h, AtmospherePreset.HARDCORE, () -> {
            MutationResult m =
                    r.settle(GasMutations.add(r.level, r.point(2, 2, 2), Rooms.oxygen(10), Rooms.source("ops_007")));
            h.assertValueEqual(m.outcome(), MutationOutcome.ZONE, "outcome under Hardcore");
            h.assertValueEqual(r.zone(2, 2, 2).cellCount(), 27, "cells");
        });
        h.succeed();
    }

    /**
     * A preset change reaches the simulation with the next atmos tick: a sealed Nether room zoned
     * under Hardcore holds Hardcore's CO2-rich Nether air, and one zoned under Normal holds none
     * (OPS-007, ENV-010).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_007_preset_ambient_reaches_the_simulation(GameTestHelper h) {
        requireNoOverrides(h);
        ServerLevel nether = level(h, Level.NETHER);
        Rooms r = new Rooms(h, nether, new BlockPos(-8192, 100, 8192));
        r.clear(0, 0, 12, 6);
        r.box(0, 0, 4, 4);
        r.box(8, 0, 12, 4);
        double hardcoreCo2 =
                Ambients.ambient(Level.NETHER, AtmospherePreset.HARDCORE).moles(CO2.toString(), 27_000);
        h.assertTrue(hardcoreCo2 > 0, "the Hardcore Nether has no CO2");
        withPreset(h, AtmospherePreset.HARDCORE, () -> {
            r.settle(GasMutations.add(nether, r.point(2, 2, 2), Rooms.oxygen(10), Rooms.source("ops_007")));
            close(h, r.zone(2, 2, 2).mixture().moles(CO2), hardcoreCo2, "CO2 of the room zoned under Hardcore");
        });
        withPreset(h, AtmospherePreset.NORMAL, () -> {
            r.settle(GasMutations.add(nether, r.point(10, 2, 2), Rooms.oxygen(10), Rooms.source("ops_007")));
            close(h, r.zone(10, 2, 2).mixture().moles(CO2), 0, "CO2 of the room zoned under Normal");
        });
        h.succeed();
    }

    // ---- OPS-014 ------------------------------------------------------------------------

    /**
     * Each option can be overridden on its own, and the override holds through preset changes,
     * even when it equals the active preset's default, until it is cleared (OPS-014). The ambient
     * and zone triggers follow the override.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_014_overrides_survive_a_preset_change(GameTestHelper h) {
        requireNoOverrides(h);
        withPreset(h, AtmospherePreset.NORMAL, () -> {
            try {
                // Under Normal, give every option its Hardcore default, except combustion, which
                // gets its Normal default: an override that matches the preset is still one.
                for (PresetOption<?> option : PresetOptions.all()) {
                    AtmospherePreset value = option == PresetOptions.COMBUSTION_FORMS_ZONES
                            ? AtmospherePreset.NORMAL
                            : AtmospherePreset.HARDCORE;
                    overrideWithDefault(option, value);
                    h.assertValueEqual(
                            option.source(), PresetOption.Source.OVERRIDE, option.key() + " source after override");
                }
                h.assertValueEqual(
                        PresetOptions.overridden(), PresetOptions.all().size(), "overridden");
                checkOverrides(h, "under Normal");

                ZasServerConfig.setPreset(AtmospherePreset.HARDCORE);
                checkOverrides(h, "after switching to Hardcore");
                ZasServerConfig.setPreset(AtmospherePreset.NORMAL);
                checkOverrides(h, "after switching back to Normal");

                // The wired options act on the override: the Nether stays Hardcore under Normal,
                // and combustion stays off under Hardcore.
                close(h, Ambients.ambient(Level.NETHER).temperature(), 340, "overridden Nether under Normal");
                ZasServerConfig.setPreset(AtmospherePreset.HARDCORE);
                h.assertFalse(ZoneTrigger.COMBUSTION.active(), "combustion forms zones despite its override");

                // Overriding one option leaves the others alone; clearing it brings back the preset.
                PresetOptions.AMBIENT_VARIANT.setOverride(AtmospherePreset.NORMAL);
                close(h, Ambients.ambient(Level.NETHER).temperature(), 313.15, "Nether overridden to Normal");
                h.assertTrue(PresetOptions.PLANTING_FORMS_ZONES.get(), "planting changed with ambient_variant");
                PresetOptions.AMBIENT_VARIANT.clearOverride();
                h.assertValueEqual(
                        PresetOptions.AMBIENT_VARIANT.source(), PresetOption.Source.PRESET, "source after clearing");
                h.assertValueEqual(
                        PresetOptions.AMBIENT_VARIANT.get(), AtmospherePreset.HARDCORE, "value after clearing");
                PresetOptions.COMBUSTION_FORMS_ZONES.clearOverride();
                h.assertTrue(ZoneTrigger.COMBUSTION.active(), "combustion after clearing, under Hardcore");
                h.assertValueEqual(
                        PresetOptions.overridden(), PresetOptions.all().size() - 2, "overridden");
            } finally {
                clearOverrides();
            }
        });

        // Values parse as the config file writes them, and as an operator would type them.
        h.assertValueEqual(PresetOption.BOOLEAN.parse(true), Optional.of(true), "boolean");
        h.assertValueEqual(PresetOption.BOOLEAN.parse("False"), Optional.of(false), "boolean text");
        h.assertValueEqual(PresetOption.BOOLEAN.parse("yes"), Optional.empty(), "not a boolean");
        PresetOption.Type<?> rate = PresetOptions.CROP_PHOTOSYNTHESIS_RATE.type();
        h.assertValueEqual(rate.parse(1), Optional.of(1.0), "a TOML integer");
        h.assertValueEqual(rate.parse("0.25"), Optional.of(0.25), "a number as text");
        h.assertValueEqual(rate.parse(-1.0), Optional.empty(), "a negative rate");
        PresetOption.Type<?> variant = PresetOptions.AMBIENT_VARIANT.type();
        h.assertValueEqual(variant.parse("hardcore"), Optional.of(AtmospherePreset.HARDCORE), "a variant");
        h.assertValueEqual(variant.parse("preset"), Optional.empty(), "\"preset\" isn't a variant");
        h.succeed();
    }

    /**
     * {@code /zas preset} switches the preset, overrides and clears options, and keeps overrides
     * across a switch; its report marks each value as from the preset or an override (OPS-014,
     * OPS-015).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void ops_014_preset_command_keeps_overrides(GameTestHelper h) {
        requireNoOverrides(h);
        Capture c = new Capture();
        CommandSourceStack op = stack(h, c);
        AtmospherePreset before = ZasServerConfig.preset();
        ZasServerConfig.holdSavesForTest(true);
        try {
            ZasServerConfig.setPreset(AtmospherePreset.NORMAL);
            run(op, "zas preset");
            String report = c.text();
            h.assertTrue(report.contains("Atmosphere preset: Normal, 0 of 7 options overridden"), report);
            h.assertTrue(report.contains("  Server config: "), report);
            h.assertTrue(
                    report.contains("  ambient_variant = NORMAL [preset] (Normal NORMAL, Hardcore HARDCORE)"), report);
            // The zone triggers are wired (CORE-056), so they don't wait; block damage still does.
            h.assertTrue(
                    report.contains("  combustion_forms_zones = false [preset] (Normal false, Hardcore true)\n"),
                    report);
            h.assertTrue(
                    report.contains("  planting_forms_zones = false [preset] (Normal false, Hardcore true)\n"), report);
            h.assertTrue(
                    report.contains("  block_pressure_damage = false [preset] (Normal false, Hardcore true), not used"
                            + " yet: waits for FLOW-007"),
                    report);
            h.assertTrue(
                    report.contains("  crop_photosynthesis_rate = 0.125 [preset] (Normal 0.125, Hardcore 0.0625)"),
                    report);
            h.assertTrue(report.contains("Zone triggers under both presets (CORE-055)"), report);

            c.clear();
            run(op, "zas preset override ambient_variant hardcore");
            h.assertTrue(c.text().contains("Overrode ambient_variant with HARDCORE under either preset"), c.text());
            h.assertTrue(c.text().contains("  ambient_variant = HARDCORE [override]"), c.text());
            h.assertValueEqual(PresetOptions.AMBIENT_VARIANT.get(), AtmospherePreset.HARDCORE, "after override");

            c.clear();
            run(op, "zas preset hardcore");
            h.assertTrue(c.text().contains("Switched the atmosphere preset to Hardcore; overrides kept: 1"), c.text());
            run(op, "zas preset normal");
            h.assertValueEqual(ZasServerConfig.preset(), AtmospherePreset.NORMAL, "preset");
            h.assertValueEqual(PresetOptions.AMBIENT_VARIANT.get(), AtmospherePreset.HARDCORE, "kept override");
            c.clear();
            run(op, "zas preset");
            h.assertTrue(c.text().contains("Atmosphere preset: Normal, 1 of 7 options overridden"), c.text());
            h.assertTrue(c.text().contains("  ambient_variant = HARDCORE [override]"), c.text());
            h.assertTrue(c.text().contains("  planting_forms_zones = false [preset]"), c.text());

            c.clear();
            run(op, "zas preset clear ambient_variant");
            h.assertTrue(c.text().contains("ambient_variant follows the preset again, now Normal"), c.text());
            h.assertValueEqual(PresetOptions.AMBIENT_VARIANT.source(), PresetOption.Source.PRESET, "cleared");

            run(op, "zas preset override leaf_photosynthesis_rate 0.5");
            h.assertValueEqual(PresetOptions.LEAF_PHOTOSYNTHESIS_RATE.get(), 0.5, "rate override");
            run(op, "zas preset clear leaf_photosynthesis_rate");

            refused(op, "zas preset override combustion_forms_zones maybe");
            refused(op, "zas preset override no_such_option true");
            refused(op, "zas preset override crop_photosynthesis_rate -1");
            h.assertValueEqual(PresetOptions.overridden(), 0, "overrides after refused commands");
        } finally {
            clearOverrides();
            ZasServerConfig.setPreset(before);
            ZasServerConfig.holdSavesForTest(false);
        }
        h.succeed();
    }

    // ---- Helpers ------------------------------------------------------------------------

    private static void requireNoOverrides(GameTestHelper h) {
        h.assertTrue(ZasServerConfig.SPEC.isLoaded(), "the server config isn't loaded");
        h.assertValueEqual(PresetOptions.overridden(), 0, "preset options overridden before the test");
    }

    /** Runs checks under a preset, then restores the configured one. */
    private static void withPreset(GameTestHelper h, AtmospherePreset preset, Runnable checks) {
        AtmospherePreset before = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(preset);
            h.assertValueEqual(ZasServerConfig.preset(), preset, "active preset");
            checks.run();
        } finally {
            ZasServerConfig.setPreset(before);
        }
    }

    private static void clearOverrides() {
        for (PresetOption<?> option : PresetOptions.all()) {
            option.clearOverride();
        }
    }

    private static <T> void overrideWithDefault(PresetOption<T> option, AtmospherePreset preset) {
        option.setOverride(option.defaultFor(preset));
    }

    /** Checks each option still has the override {@link #ops_014_overrides_survive_a_preset_change} gave it. */
    private static void checkOverrides(GameTestHelper h, String when) {
        for (PresetOption<?> option : PresetOptions.all()) {
            int index = option == PresetOptions.COMBUSTION_FORMS_ZONES ? 0 : 1;
            h.assertValueEqual(option.get(), DEFAULTS.get(option.key()).get(index), option.key() + " " + when);
            h.assertValueEqual(option.source(), PresetOption.Source.OVERRIDE, option.key() + " source " + when);
        }
    }

    private static ServerLevel level(GameTestHelper h, net.minecraft.resources.ResourceKey<Level> dimension) {
        ServerLevel level = h.getLevel().getServer().getLevel(dimension);
        if (level == null) {
            throw new GameTestAssertException("dimension " + dimension.location() + " doesn't exist");
        }
        return level;
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= EPS * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /** Collects what commands send to their source. */
    private static final class Capture implements CommandSource {
        final List<Component> messages = new CopyOnWriteArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        String text() {
            StringBuilder b = new StringBuilder();
            for (Component m : messages) {
                b.append(m.getString()).append('\n');
            }
            return b.toString();
        }

        void clear() {
            messages.clear();
        }
    }

    private static CommandSourceStack stack(GameTestHelper h, Capture capture) {
        return new CommandSourceStack(
                capture,
                Vec3.ZERO,
                Vec2.ZERO,
                h.getLevel(),
                2,
                "zas_test",
                Component.literal("zas_test"),
                h.getLevel().getServer(),
                null);
    }

    private static void run(CommandSourceStack source, String command) {
        try {
            source.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " failed: " + e.getMessage());
        }
    }

    private static void refused(CommandSourceStack source, String command) {
        try {
            source.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return;
        }
        throw new GameTestAssertException("/" + command + " succeeded");
    }
}
