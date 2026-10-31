package io.github.west3436.zas.example.gametest;

import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.example.ExampleAddonMod;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * S-12, API without ZAS³ (API-048, D-17): this server runs the example addon, which nests the API,
 * and no {@code zas}. The API's fallback must answer every query with ambient standard air and no
 * zone, complete every mutation at once as {@link MutationOutcome#ABSENT}, and throw nothing.
 */
@GameTestHolder(ExampleAddonMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WithoutZasTests {
    private static final SourceId SOURCE = SourceId.of(ExampleAddonMod.MOD_ID, "s_12");
    private static final double EPS = 1e-9;

    private WithoutZasTests() {}

    /** The addon loaded and zas didn't, so the API reports no implementation (API-040). */
    @GameTest(template = "empty")
    public static void s_12_api_absent_without_zas(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded(ExampleAddonMod.MOD_ID), "the example addon is not loaded");
        h.assertFalse(ModList.get().isLoaded("zas"), "zas is loaded; S-12 needs it absent");
        h.assertFalse(ZasApi.isPresent(), "ZasApi.isPresent() is true without zas");
        h.assertFalse(ZasApi.provider().isReal(), "the provider claims to be real");
        h.succeed();
    }

    /** Every point, entity and dimension reads ambient, breathable standard air, in no zone. */
    @GameTest(template = "empty")
    public static void s_12_queries_return_standard_air(GameTestHelper h) {
        ServerLevel overworld = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "no nether level");
        List<AtmosphereSample> samples = List.of(
                Atmospheres.at(overworld, pos),
                Atmospheres.at(overworld, Vec3.atCenterOf(pos)),
                Atmospheres.at(overworld, BlockPos.ZERO),
                Atmospheres.of(pig),
                Atmospheres.at(nether, pos));
        for (AtmosphereSample s : samples) {
            assertStandardAir(h, s);
        }
        h.assertTrue(Atmospheres.zoneAt(overworld, pos).isEmpty(), "zoneAt(pos) found a zone");
        h.assertTrue(Atmospheres.zoneAt(overworld, Vec3.atCenterOf(pos)).isEmpty(), "zoneAt(point) found a zone");
        h.assertTrue(Atmospheres.zone(overworld, new ZoneId(1)).isEmpty(), "zone(id) found a zone");
        h.succeed();
    }

    /** Adding and removing gas complete at once as ABSENT, moving nothing. */
    @GameTest(template = "empty")
    public static void s_12_mutations_complete_absent(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 point = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        CompletableFuture<MutationResult> add =
                GasMutations.add(level, point, new GasParcel(Map.of(StandardAir.OXYGEN, 5.0), 300), SOURCE);
        CompletableFuture<MutationResult> remove =
                GasMutations.remove(level, point, Map.of(StandardAir.NITROGEN, 2.0), SOURCE);
        for (CompletableFuture<MutationResult> f : List.of(add, remove)) {
            h.assertTrue(f.isDone(), "a fallback mutation didn't complete at once");
            h.assertFalse(f.isCompletedExceptionally(), "a fallback mutation failed");
            MutationResult r = f.join();
            h.assertValueEqual(r.outcome(), MutationOutcome.ABSENT, "outcome");
            h.assertTrue(r.moved().isEmpty(), "the fallback moved gas: " + r.moved());
        }
        h.succeed();
    }

    private static void assertStandardAir(GameTestHelper h, AtmosphereSample s) {
        h.assertValueEqual(s.state(), AtmosphereState.AMBIENT, "state");
        h.assertTrue(s.zone().isEmpty(), "an ambient sample has a zone");
        h.assertTrue(Math.abs(s.pressure() - StandardAir.PRESSURE) < 1e-6, "pressure " + s.pressure());
        h.assertTrue(Math.abs(s.temperature() - StandardAir.TEMPERATURE) < EPS, "temperature " + s.temperature());
        Double o2 = s.composition().get(StandardAir.OXYGEN);
        h.assertTrue(o2 != null && Math.abs(o2 - StandardAir.OXYGEN_FRACTION) < EPS, "O2 fraction " + o2);
        Double n2 = s.composition().get(StandardAir.NITROGEN);
        h.assertTrue(n2 != null && Math.abs(n2 - StandardAir.NITROGEN_FRACTION) < EPS, "N2 fraction " + n2);
        // Breathable: the O2 partial pressure of sea-level air, about 21.3 kPa.
        Double po2 = s.partialPressures().get(StandardAir.OXYGEN);
        h.assertTrue(
                po2 != null && Math.abs(po2 - StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION) < 1e-6,
                "O2 partial pressure " + po2);
    }
}
