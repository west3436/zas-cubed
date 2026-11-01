package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The API as installed by {@code zas}: the ambient cases and the threading
 * contract. The zoned cases of API-010, API-011, API-012 and API-016 are in
 * {@link AtmosTests}; the paused and unresolved ones are in
 * {@link PausedZoneTests}.
 *
 * <p>The breathability helper (API-014) is interim until LIFE (#34 to #36)
 * and the life-support capability (API-022): {@code zas} answers with
 * vanilla's verdict, as the fallback does. The {@code api_014_interim_} tests
 * pin that down, and change when LIFE's verdict lands.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApiTests {
    private static final SourceId SOURCE = SourceId.of(ZasMod.MOD_ID, "gametest");

    private ApiTests() {}

    /** With zas installed, the API reports a real implementation (API-040). */
    @GameTest(template = "empty")
    public static void api_040_present_with_zas(GameTestHelper h) {
        h.assertTrue(ZasApi.isPresent(), "ZasApi.isPresent() is false with zas loaded");
        h.succeed();
    }

    /** An ambient point returns every field of the ambient (API-010). */
    @GameTest(template = "empty")
    public static void api_010_ambient_point_returns_every_field(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        AtmosphereSample sample = Atmospheres.at(level, pos);
        h.assertValueEqual(sample.state(), AtmosphereState.AMBIENT, "state");
        h.assertTrue(sample.zone().isEmpty(), "an ambient point has no zone");
        h.assertTrue(Math.abs(sample.pressure() - StandardAir.PRESSURE) < 1e-6, "pressure " + sample.pressure());
        h.assertTrue(Math.abs(sample.temperature() - StandardAir.TEMPERATURE) < 1e-9, "temperature");
        h.assertTrue(
                Math.abs(sample.composition().get(StandardAir.OXYGEN) - StandardAir.OXYGEN_FRACTION) < 1e-9,
                "O2 fraction");
        double partials = sample.partialPressures().values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        h.assertTrue(Math.abs(partials - sample.pressure()) < 1e-6, "partial pressures don't sum to the total");
        h.assertValueEqual(Atmospheres.at(level, Vec3.atCenterOf(pos)), sample, "exact point");
        h.assertTrue(Atmospheres.zoneAt(level, pos).isEmpty(), "an ambient point is in no zone (API-011)");
        h.succeed();
    }

    /** A query about an entity uses its eye position (API-015). */
    @GameTest(template = "empty")
    public static void api_015_entity_query_uses_eye_position(GameTestHelper h) {
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        h.assertValueEqual(Atmospheres.of(pig), Atmospheres.at(h.getLevel(), pig.getEyePosition()), "entity sample");
        h.succeed();
    }

    /** A mutation from another thread is refused (API-045). */
    @GameTest(template = "empty")
    public static void api_045_mutation_off_server_thread_is_refused(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 point = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread other = new Thread(
                () -> {
                    try {
                        GasMutations.add(level, point, new GasParcel(Map.of(StandardAir.OXYGEN, 1.0), 300), SOURCE);
                    } catch (Throwable t) {
                        thrown.set(t);
                    }
                },
                "zas-api-045");
        other.start();
        try {
            other.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        h.assertTrue(thrown.get() instanceof IllegalStateException, "expected IllegalStateException, got " + thrown);
        h.succeed();
    }

    /**
     * Adding gas in the open air returns a future that completes on the server
     * thread after the tick that applies it, with everything taken by the
     * ambient (API-012, API-016).
     */
    @GameTest(template = "empty")
    public static void api_016_ambient_add_completes_on_server_thread(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 point = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        GasParcel gas = new GasParcel(Map.of(StandardAir.OXYGEN, 2.5), 310);
        long calledAt = h.getTick();
        CompletableFuture<MutationResult> future = GasMutations.add(level, point, gas, SOURCE);
        h.assertFalse(future.isDone(), "the future completed before the applying tick");
        AtomicBoolean onServerThread = new AtomicBoolean();
        future.thenRun(() -> onServerThread.set(level.getServer().isSameThread()));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone(), "not done yet");
            h.assertTrue(h.getTick() > calledAt, "completed in the calling tick");
            h.assertTrue(onServerThread.get(), "completed off the server thread");
            MutationResult r = future.join();
            h.assertValueEqual(r.outcome(), MutationOutcome.AMBIENT, "outcome");
            h.assertValueEqual(r.moved(), gas, "moved");
        });
    }

    /**
     * Removing gas in the open air delivers the gases the ambient holds, and
     * none it lacks (API-012, API-016).
     */
    @GameTest(template = "empty")
    public static void api_012_ambient_remove_delivers_ambient_gases(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 point = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        ResourceLocation helium = ResourceLocation.fromNamespaceAndPath("zas", "gametest_absent");
        CompletableFuture<MutationResult> future =
                GasMutations.remove(level, point, Map.of(StandardAir.NITROGEN, 4.0, helium, 1.0), SOURCE);
        h.assertFalse(future.isDone(), "the future completed before the applying tick");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone(), "not done yet");
            MutationResult r = future.join();
            h.assertValueEqual(r.outcome(), MutationOutcome.AMBIENT, "outcome");
            h.assertValueEqual(r.moved().moles(StandardAir.NITROGEN), 4.0, "N2 delivered");
            h.assertValueEqual(r.moved().moles(helium), 0.0, "a gas the ambient lacks");
            h.assertValueEqual(r.moved().temperature(), StandardAir.TEMPERATURE, "temperature");
        });
    }

    // ---- Breathability (API-014): interim, vanilla's verdict until LIFE --------------------

    /** In the open air, zas answers with vanilla's verdict: the pig breathes the atmosphere. */
    @GameTest(template = "empty")
    public static void api_014_interim_answers_vanilla_in_air(GameTestHelper h) {
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        Breathability b = Breathing.of(pig);
        h.assertValueEqual(b, Breathing.vanilla(pig), "the interim verdict is vanilla's");
        h.assertValueEqual(b.reason(), BreathReason.BREATHABLE, "reason");
        h.assertValueEqual(b.source(), BreathSource.ATMOSPHERE, "source");
        h.assertTrue(b.canBreathe(), "the pig can't breathe in the open air");
        h.assertTrue(b.isProtected(), "the pig isn't protected in the open air");
        h.succeed();
    }

    /** With its eyes in water, zas keeps vanilla's verdict: a pig drowns (LIFE-022). */
    @GameTest(template = "empty")
    public static void api_014_interim_answers_vanilla_underwater(GameTestHelper h) {
        // A stone basin on a floor at y = 0, with a column of water two deep at (1, 1, 1).
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(2, 2, 2))) {
            boolean water = p.getY() > 0 && p.getX() == 1 && p.getZ() == 1;
            h.setBlock(p, water ? Blocks.WATER : Blocks.STONE);
        }
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
        h.succeedWhen(() -> {
            assertEyesInWater(h, pig);
            Breathability b = Breathing.of(pig);
            h.assertValueEqual(b, Breathing.vanilla(pig), "the interim verdict is vanilla's");
            h.assertValueEqual(b.reason(), BreathReason.DROWNING, "reason");
            h.assertValueEqual(b.source(), BreathSource.FLUID, "source");
            h.assertFalse(b.canBreathe(), "a submerged pig can breathe");
        });
    }

    /**
     * Interim until LIFE (#34): in a vacuum zone, zas still answers with vanilla's verdict, which
     * never looks at the atmosphere, so the pig is told it can breathe. LIFE's verdict is
     * {@link BreathReason#LOW_OXYGEN} there; this test changes with it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_014_interim_ignores_a_vacuum_zone(GameTestHelper h) {
        Rooms r = new Rooms(h, 80);
        ServerLevel level = r.level;
        r.clear(0, 6);
        r.shell(0, 4); // a sealed 3 × 3 × 3 room
        r.settle(GasMutations.remove(
                level,
                r.point(2, 2, 2),
                Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                Rooms.source("api_014")));
        Pig pig = EntityType.PIG.create(level);
        h.assertTrue(pig != null, "no pig");
        pig.moveTo(Vec3.atBottomCenterOf(r.abs(2, 1, 2)));
        level.addFreshEntity(pig);
        try {
            AtmosphereSample at = Atmospheres.of(pig);
            h.assertValueEqual(at.state(), AtmosphereState.ZONED, "the pig's eyes aren't in the room's zone");
            h.assertTrue(at.pressure() < 1e-6, "the room isn't a vacuum: " + at.pressure() + " kPa");
            Breathability b = Breathing.of(pig);
            h.assertValueEqual(b, Breathing.vanilla(pig), "the interim verdict is vanilla's");
            h.assertValueEqual(b.reason(), BreathReason.BREATHABLE, "reason");
            h.assertTrue(b.isProtected(), "the interim verdict reports a hazard");
        } finally {
            pig.discard();
        }
        h.succeed();
    }

    /** The breathability helper is refused off the server thread for a server-side entity. */
    @GameTest(template = "empty")
    public static void api_014_breathing_off_server_thread_is_refused(GameTestHelper h) {
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        AtomicReference<Throwable> ofThrew = new AtomicReference<>();
        AtomicReference<Throwable> vanillaThrew = new AtomicReference<>();
        Thread other = new Thread(
                () -> {
                    ofThrew.set(thrownBy(() -> Breathing.of(pig)));
                    vanillaThrew.set(thrownBy(() -> Breathing.vanilla(pig)));
                },
                "zas-api-014");
        other.start();
        try {
            other.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        h.assertTrue(ofThrew.get() instanceof IllegalStateException, "Breathing.of: got " + ofThrew);
        h.assertTrue(vanillaThrew.get() instanceof IllegalStateException, "Breathing.vanilla: got " + vanillaThrew);
        h.succeed();
    }

    private static Throwable thrownBy(Runnable call) {
        try {
            call.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private static void assertEyesInWater(GameTestHelper h, LivingEntity e) {
        h.assertTrue(
                e.getEyeInFluidType() == NeoForgeMod.WATER_TYPE.value(),
                e.getName().getString() + "'s eyes aren't in water yet");
    }
}
