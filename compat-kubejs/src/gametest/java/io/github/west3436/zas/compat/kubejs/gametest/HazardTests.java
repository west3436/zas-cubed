package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.api.life.HazardExposure;
import io.github.west3436.zas.api.life.HazardHandlers;
import io.github.west3436.zas.compat.kubejs.gametest.Probe.Exposed;
import io.github.west3436.zas.life.Breathers;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hazards a pack's server script registers (COMPAT-052), and the guard around one that throws
 * (NFR-013). The run's {@code hazard.js} and {@code hazard_throws.js} register theirs as the
 * server starts, for gases only this run's data has, so each test's entity is the only one they
 * are called for. A test drives its entity's breath checks itself.
 */
@GameTestHolder(EventTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class HazardTests {
    private static final ResourceLocation SCRIPT_GAS = Lab.id("script_gas");
    private static final ResourceLocation TAGGED_GAS = Lab.id("tagged_gas");
    private static final ResourceLocation SECOND_TAGGED_GAS = Lab.id("second_tagged_gas");
    private static final ResourceLocation FAULTY_GAS = Lab.id("faulty_gas");

    /** What ZAS³ logs a handler's fault under, and where. */
    private static final String ZAS = "io.github.west3436.zas.life.HandledHazards";

    private static final String FAULT = "Hazard handler zas_kubejs:a_broken threw; skipped for this check";

    private HazardTests() {}

    /** Two checks 2 s apart; the first charges nothing, and the second charges 2 s. */
    static void twoSeconds(LivingEntity entity, long t) {
        Breathers.checkAt(entity, t);
        Breathers.checkAt(entity, t + 40);
    }

    static boolean registered(String path) {
        return HazardHandlers.registered().stream().anyMatch(r -> r.id().equals(Lab.id(path)));
    }

    /**
     * A script's handler for a gas is registered under the id the script gave, and called at a
     * breath check of an entity in that gas, on the server thread, with the entity, the gas, the
     * pressures and the seconds. What it does to the entity is done. An entity in clean air isn't
     * its business.
     */
    @GameTest(template = "empty")
    public static void compat_052_a_script_registers_a_hazard_for_a_gas(GameTestHelper h) {
        try (Lab lab = new Lab(h, 20)) {
            h.assertTrue(registered("choking"), "the script's handler isn't registered under its id");
            lab.airRoom(0, Map.of(SCRIPT_GAS, 1.0));
            lab.airRoom(6, Map.of());
            Pig pig = lab.mob(EntityType.PIG, 0);
            Pig clean = lab.mob(EntityType.PIG, 6);
            long t = lab.level.getGameTime();
            twoSeconds(pig, t);
            twoSeconds(clean, t);

            List<Exposed> calls = Probe.exposed("choking", pig);
            h.assertValueEqual(calls.size(), 1, "the calls to the script's handler");
            h.assertTrue(calls.get(0).serverThread(), "it wasn't called on the server thread");
            HazardExposure exposure = calls.get(0).exposure();
            h.assertValueEqual(exposure.gas(), SCRIPT_GAS, "the gas");
            lab.close(exposure.seconds(), 2, 0, "the seconds");
            lab.close(exposure.surroundingPressure(), 1, 1e-6, "the pressure around the pig");
            lab.close(exposure.breathedPressure(), 1, 1e-6, "the pressure the pig breathes");
            lab.close(pig.getMaxHealth() - pig.getHealth(), 3, 1e-4, "the harm the script did the pig");

            h.assertValueEqual(Probe.exposed("choking", clean).size(), 0, "the calls for the pig in clean air");
            lab.close(clean.getMaxHealth() - clean.getHealth(), 0, 0, "the harm done the pig in clean air");
        }
        h.succeed();
    }

    /**
     * A script's handler for a gas tag, written {@code '#namespace:path'}, is called once for each
     * of the tag's gases around the entity, and for no other gas.
     */
    @GameTest(template = "empty")
    public static void compat_052_a_script_registers_a_hazard_for_a_gas_tag(GameTestHelper h) {
        try (Lab lab = new Lab(h, 21)) {
            lab.airRoom(0, Map.of(TAGGED_GAS, 0.5, SECOND_TAGGED_GAS, 0.5, SCRIPT_GAS, 0.5));
            Pig pig = lab.mob(EntityType.PIG, 0);
            twoSeconds(pig, lab.level.getGameTime());

            List<ResourceLocation> gases = Probe.exposed("tagged", pig).stream()
                    .map(e -> e.exposure().gas())
                    .toList();
            h.assertValueEqual(gases, List.of(SECOND_TAGGED_GAS, TAGGED_GAS), "the gases it was called for");
        }
        h.succeed();
    }

    /**
     * A script's handler that throws is contained as a mod's is: it is skipped for the rest of
     * that check, the handler after it still runs, and it is called again at the next check. The
     * fault is logged twice, each at most once a minute: by ZAS³ under the handler's id, and in
     * KubeJS's console for server scripts with the script's file and line. A mistake in the script
     * is contained the same way as a throw of its own, and the handler counts again once it stops.
     */
    @GameTest(template = "empty")
    public static void nfr_013_a_script_hazard_handler_that_throws_is_contained(GameTestHelper h) {
        try (Lab lab = new Lab(h, 22);
                FaultLog log = new FaultLog()) {
            lab.restoring(() -> Probe.mode("hazard", ""));
            lab.airRoom(0, Map.of(FAULTY_GAS, 1.0));
            Pig pig = lab.mob(EntityType.PIG, 0);
            long t = lab.level.getGameTime();
            Breathers.checkAt(pig, t);
            h.assertValueEqual(Probe.exposed("a_broken", pig).size(), 0, "the calls at a check that charges nothing");

            FaultLog.aMinutePasses();
            Probe.mode("hazard", "throw");
            for (int i = 1; i <= 3; i++) {
                Breathers.checkAt(pig, t + 40L * i);
                h.assertValueEqual(Probe.exposed("a_broken", pig).size(), i, "the calls to the one that throws");
                h.assertValueEqual(Probe.exposed("b_working", pig).size(), i, "the calls to the one after it");
            }
            h.assertTrue(pig.isAlive(), "the pig died of a script's fault");
            h.assertValueEqual(log.by(ZAS, FAULT).size(), 1, "errors ZAS³ logged: " + log.all());
            List<String> console = log.by(FaultLog.KUBEJS, FAULT);
            h.assertValueEqual(console.size(), 1, "errors in KubeJS's console: " + log.all());
            h.assertTrue(
                    console.get(0).contains("hazard_throws.js#")
                            && console.get(0).contains("the hazard script is broken"),
                    "the console's line doesn't name the script and what it threw: " + console.get(0));

            FaultLog.aMinutePasses();
            Probe.mode("hazard", "mistake");
            Breathers.checkAt(pig, t + 160);
            h.assertValueEqual(Probe.exposed("a_broken", pig).size(), 4, "the calls to it a minute later");
            h.assertValueEqual(Probe.exposed("b_working", pig).size(), 4, "the calls to the one after a mistake");
            h.assertValueEqual(log.by(ZAS, FAULT).size(), 2, "errors ZAS³ logged a minute later: " + log.all());
            h.assertValueEqual(
                    log.by(FaultLog.KUBEJS, FAULT).size(),
                    2,
                    "errors in KubeJS's console a minute later: " + log.all());

            Probe.mode("hazard", "");
            Breathers.checkAt(pig, t + 200);
            h.assertValueEqual(Probe.exposed("a_broken", pig).size(), 5, "the calls to it once it stops throwing");
            h.assertValueEqual(log.by(ZAS, FAULT).size(), 2, "errors ZAS³ logged once it stops: " + log.all());
        }
        h.succeed();
    }
}
