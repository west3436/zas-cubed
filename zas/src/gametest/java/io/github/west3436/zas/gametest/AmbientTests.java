package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Per-dimension ambients, using the test ambients in this source set's {@code
 * data/zas_gametest/zas/ambient}.
 *
 * <p>The GameTest server creates only the vanilla dimensions; it ignores datapack dimensions.
 * So the test dimensions ({@code zas_gametest:ambient_test}, {@code vacuum}, and so on) have
 * ambient definitions but no level, and are checked through {@link Ambients}, the lookup the
 * API and the scheduler use. The API end to end is checked in the real Nether and End, whose
 * shipped ambients differ from the Overworld's.
 *
 * <p>Tests that change the preset or the excluded dimensions restore them, so they don't leak
 * into other tests.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmbientTests {
    private static final double EPS = 1e-9;
    private static final BlockPos OPEN = new BlockPos(0, 100, 0);
    /** Away from the End's main island and its dragon fight. */
    private static final BlockPos ROOM = new BlockPos(512, 100, 512);

    private static final SourceId SOURCE = SourceId.of(ZasMod.MOD_ID, "gametest");
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation TEST_GAS = ResourceLocation.fromNamespaceAndPath("zas_gametest", "test_gas");

    private AmbientTests() {}

    /**
     * A test dimension gets its ambient from a datapack (ENV-001), and queries return the
     * dimension's ambient.
     */
    @GameTest(template = "empty")
    public static void env_001_datapack_ambient_answers_queries(GameTestHelper h) {
        Ambient ambient = Ambients.ambient(key("ambient_test"), AtmospherePreset.NORMAL);
        GasMixture cell = Ambients.cell(ambient);
        close(h, cell.temperature(), 250, "temperature");
        close(h, cell.pressure(), 75, "pressure");
        close(h, cell.partialPressures().get(O2), 50, "O2 partial pressure");
        // A gas from another datapack works in an ambient.
        close(h, cell.partialPressures().get(TEST_GAS), 25, "test gas partial pressure");

        // Queries return the dimension's ambient, not the Overworld's.
        ServerLevel nether = level(h, Level.NETHER);
        withPreset(h, AtmospherePreset.NORMAL, () -> {
            AtmosphereSample sample = Atmospheres.at(nether, OPEN);
            h.assertValueEqual(sample.state(), AtmosphereState.AMBIENT, "state");
            h.assertValueEqual(sample.mixture(), Ambients.cell(Ambients.ambient(Level.NETHER)), "Nether sample");
            close(h, sample.temperature(), 313.15, "Nether temperature");
            h.assertValueEqual(Atmospheres.at(nether, Vec3.atCenterOf(OPEN)), sample, "point query");
        });
        h.succeed();
    }

    /** The active preset's variant applies, and switching the preset switches it (ENV-010). */
    @GameTest(template = "empty")
    public static void env_010_preset_selects_variant(GameTestHelper h) {
        ResourceKey<Level> test = key("ambient_test");
        ServerLevel end = level(h, Level.END);
        withPreset(h, AtmospherePreset.HARDCORE, () -> {
            // The Hardcore variant is given in moles per 1,000 L: one cell holds exactly that.
            GasMixture cell = Ambients.cell(Ambients.ambient(test));
            close(h, cell.temperature(), 400, "Hardcore temperature");
            close(h, cell.moles(N2), 20, "Hardcore N2 moles per cell");
            close(h, cell.moles(O2), 0, "Hardcore O2 moles");
            AtmosphereSample sample = Atmospheres.at(end, OPEN);
            close(h, sample.pressure(), 40, "Hardcore End pressure");
            close(h, sample.temperature(), 263.15, "Hardcore End temperature");
        });
        withPreset(h, AtmospherePreset.NORMAL, () -> {
            GasMixture cell = Ambients.cell(Ambients.ambient(test));
            close(h, cell.temperature(), 250, "Normal temperature");
            close(h, cell.partialPressures().get(O2), 50, "Normal O2 partial pressure");
            AtmosphereSample sample = Atmospheres.at(end, OPEN);
            close(h, sample.pressure(), StandardAir.PRESSURE, "Normal End pressure");
            close(h, sample.temperature(), 293.15, "Normal End temperature");
        });
        h.succeed();
    }

    /** A dimension with no ambient from any source gets standard air under both presets (ENV-011). */
    @GameTest(template = "empty")
    public static void env_011_undefined_dimension_gets_standard_air(GameTestHelper h) {
        ResourceKey<Level> undefined = key("undefined");
        h.assertTrue(Ambients.definition(undefined).isEmpty(), "zas_gametest:undefined has a definition");
        for (AtmospherePreset preset : AtmospherePreset.values()) {
            GasMixture cell = Ambients.cell(Ambients.ambient(undefined, preset));
            close(h, cell.pressure(), StandardAir.PRESSURE, preset + " pressure");
            close(h, cell.temperature(), StandardAir.TEMPERATURE, preset + " temperature");
            close(h, cell.composition().get(N2), StandardAir.NITROGEN_FRACTION, preset + " N2 fraction");
            close(h, cell.composition().get(O2), StandardAir.OXYGEN_FRACTION, preset + " O2 fraction");
            close(
                    h,
                    cell.totalMoles(),
                    StandardAir.mixture(StandardAir.CELL_VOLUME).totalMoles(),
                    preset + " moles per cell");
        }
        h.succeed();
    }

    /** The shipped Normal ambients of the vanilla dimensions (ENV-002). */
    @GameTest(template = "empty")
    public static void env_002_normal_vanilla_defaults(GameTestHelper h) {
        AtmospherePreset normal = AtmospherePreset.NORMAL;
        checkDefined(h, Level.OVERWORLD);
        checkStandardAir(h, Ambients.ambient(Level.OVERWORLD, normal), 293.15, "Overworld");
        checkDefined(h, Level.NETHER);
        checkStandardAir(h, Ambients.ambient(Level.NETHER, normal), 313.15, "Nether");
        checkDefined(h, Level.END);
        checkStandardAir(h, Ambients.ambient(Level.END, normal), 293.15, "End");
        h.succeed();
    }

    /** The shipped Hardcore ambients of the vanilla dimensions (ENV-012). */
    @GameTest(template = "empty")
    public static void env_012_hardcore_vanilla_defaults(GameTestHelper h) {
        AtmospherePreset hardcore = AtmospherePreset.HARDCORE;
        checkStandardAir(h, Ambients.ambient(Level.OVERWORLD, hardcore), 293.15, "Overworld");

        Ambient nether = Ambients.ambient(Level.NETHER, hardcore);
        close(h, nether.temperature(), 340, "Nether temperature");
        close(h, nether.partialPressure(CO2.toString()), 12, "Nether CO2");
        close(h, nether.partialPressure(O2.toString()), 18, "Nether O2");
        close(h, nether.partialPressure(N2.toString()), 71.325, "Nether N2");
        close(h, nether.pressure(), 101.325, "Nether pressure");

        Ambient end = Ambients.ambient(Level.END, hardcore);
        close(h, end.temperature(), 263.15, "End temperature");
        close(h, end.partialPressure(O2.toString()), 8.4, "End O2");
        close(h, end.partialPressure(N2.toString()), 31.6, "End N2");
        close(h, end.pressure(), 40, "End pressure");
        h.succeed();
    }

    /**
     * Open cells in a vacuum dimension report 0 kPa at 2.7 K, or at the temperature the
     * definition gives (ENV-003).
     */
    @GameTest(template = "empty")
    public static void env_003_vacuum_dimension_reports_zero_pressure(GameTestHelper h) {
        for (AtmospherePreset preset : AtmospherePreset.values()) {
            Ambient ambient = Ambients.ambient(key("vacuum"), preset);
            h.assertTrue(ambient.isVacuum(), preset + " vacuum holds gas: " + ambient);
            GasMixture cell = Ambients.cell(ambient);
            h.assertValueEqual(cell.pressure(), 0.0, preset + " pressure");
            h.assertValueEqual(cell.moles(), Map.of(), preset + " moles");
            close(h, cell.temperature(), Ambient.COSMIC_BACKGROUND, preset + " temperature");
        }
        GasMixture hot = Ambients.cell(Ambients.ambient(key("hot_vacuum"), AtmospherePreset.NORMAL));
        h.assertValueEqual(hot.pressure(), 0.0, "declared-temperature vacuum pressure");
        close(h, hot.temperature(), 737, "declared vacuum temperature");
        h.succeed();
    }

    /**
     * A datapack or the config can exclude a dimension (ENV-007), and an excluded dimension
     * keeps its ambient (ENV-018). Gas vented in a sealed room there goes to the ambient and no
     * zone forms. The room is built in the End, excluded by the config for the test.
     *
     * <p>TODO(#23): until the scheduler exists no dimension has zones, so the vent check only
     * becomes meaningful once it skips excluded dimensions.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void env_007_excluded_dimension_forms_no_zone(GameTestHelper h) {
        h.assertTrue(Ambients.isExcluded(key("excluded")), "the datapack doesn't exclude zas_gametest:excluded");
        h.assertTrue(Ambients.ambient(key("excluded")).isVacuum(), "zas_gametest:excluded lost its vacuum ambient");
        h.assertFalse(Ambients.isExcluded(key("ambient_test")), "zas_gametest:ambient_test is excluded");

        // The config excludes a dimension too.
        ServerLevel end = level(h, Level.END);
        h.assertTrue(ZasServerConfig.SPEC.isLoaded(), "the server config isn't loaded");
        List<? extends String> before = ZasServerConfig.EXCLUDED_DIMENSIONS.get();
        h.assertFalse(Ambients.isExcluded(Level.END), "the End is excluded before the test");
        ZasServerConfig.EXCLUDED_DIMENSIONS.set(List.of(Level.END.location().toString()));
        h.assertTrue(Ambients.isExcluded(Level.END), "the config doesn't exclude the End");

        // Excluded, it keeps its ambient.
        h.assertValueEqual(
                Atmospheres.at(end, OPEN).mixture(), Ambients.cell(Ambients.ambient(Level.END)), "End sample");

        // A sealed 3×3×3 room of stone around ROOM.
        for (BlockPos pos : BlockPos.betweenClosed(ROOM.offset(-2, -2, -2), ROOM.offset(2, 2, 2))) {
            boolean shell = Math.abs(pos.getX() - ROOM.getX()) == 2
                    || Math.abs(pos.getY() - ROOM.getY()) == 2
                    || Math.abs(pos.getZ() - ROOM.getZ()) == 2;
            end.setBlockAndUpdate(pos, shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        Vec3 inside = Vec3.atCenterOf(ROOM);
        CompletableFuture<MutationResult> vent =
                GasMutations.add(end, inside, new GasParcel(Map.of(O2, 10.0), 293.15), SOURCE);
        // Un-exclude once the vent has been applied, whatever the outcome.
        vent.whenComplete((result, error) -> ZasServerConfig.EXCLUDED_DIMENSIONS.set(before));
        h.succeedWhen(() -> {
            h.assertTrue(vent.isDone(), "the vent hasn't completed");
            h.assertValueEqual(vent.join().outcome(), MutationOutcome.AMBIENT, "vent outcome");
            h.assertTrue(Atmospheres.zoneAt(end, inside).isEmpty(), "a zone formed in an excluded dimension");
        });
    }

    /** Invalid ambient files are skipped, leaving their dimensions on the fallback (OPS-008). */
    @GameTest(template = "empty")
    public static void ops_008_invalid_ambient_files_are_skipped(GameTestHelper h) {
        for (String path : List.of(
                "invalid_no_temperature",
                "invalid_two_compositions",
                "invalid_unknown_gas",
                "invalid_negative_pressure")) {
            ResourceKey<Level> dimension = key(path);
            h.assertTrue(Ambients.definition(dimension).isEmpty(), path + " loaded");
            for (AtmospherePreset preset : AtmospherePreset.values()) {
                h.assertValueEqual(
                        Ambients.ambient(dimension, preset), Ambient.STANDARD_AIR, path + " " + preset + " ambient");
            }
        }
        // The valid files beside them still loaded.
        h.assertTrue(Ambients.definition(key("ambient_test")).isPresent(), "ambient_test didn't load");
        h.succeed();
    }

    // ---- Helpers ------------------------------------------------------------

    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", path));
    }

    private static ServerLevel level(GameTestHelper h, ResourceKey<Level> dimension) {
        ServerLevel level = h.getLevel().getServer().getLevel(dimension);
        if (level == null) {
            throw new GameTestAssertException("dimension " + dimension.location() + " doesn't exist");
        }
        return level;
    }

    /** Runs checks under a preset, then restores the configured one. */
    private static void withPreset(GameTestHelper h, AtmospherePreset preset, Runnable checks) {
        h.assertTrue(ZasServerConfig.SPEC.isLoaded(), "the server config isn't loaded");
        AtmospherePreset before = ZasServerConfig.PRESET.get();
        try {
            ZasServerConfig.PRESET.set(preset);
            h.assertValueEqual(ZasServerConfig.preset(), preset, "active preset");
            checks.run();
        } finally {
            ZasServerConfig.PRESET.set(before);
        }
    }

    private static void checkDefined(GameTestHelper h, ResourceKey<Level> dimension) {
        h.assertTrue(Ambients.definition(dimension).isPresent(), "zas ships no ambient for " + dimension.location());
    }

    private static void checkStandardAir(GameTestHelper h, Ambient ambient, double temperature, String name) {
        close(h, ambient.temperature(), temperature, name + " temperature");
        close(h, ambient.pressure(), StandardAir.PRESSURE, name + " pressure");
        close(
                h,
                ambient.partialPressure(O2.toString()) / ambient.pressure(),
                StandardAir.OXYGEN_FRACTION,
                name + " O2 fraction");
        close(
                h,
                ambient.partialPressure(N2.toString()) / ambient.pressure(),
                StandardAir.NITROGEN_FRACTION,
                name + " N2 fraction");
    }

    private static void close(GameTestHelper h, Double actual, double expected, String what) {
        h.assertTrue(
                actual != null && Math.abs(actual - expected) < EPS,
                what + ": expected " + expected + ", got " + actual);
    }
}
