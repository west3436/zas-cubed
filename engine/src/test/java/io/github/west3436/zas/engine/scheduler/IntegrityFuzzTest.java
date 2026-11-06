package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The integrity fuzz's engine half (NFR-010, CORE-036, CORE-063, CORE-073, GEO-032). One seed runs by
 * default. {@code -Pfuzz.seed=S} starts from another, which is how a failure replays, {@code
 * -Pfuzz.seeds=N} runs N seeds from it, as the nightly run does, and {@code -Pfuzz.ops=N} shortens a run.
 */
class IntegrityFuzzTest {
    private static long property(String name, long fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : Long.parseLong(value.trim());
    }

    @TestFactory
    List<DynamicTest> nfr_010_integrity_under_fuzzing() {
        long first = property("zas.fuzz.seed", 1);
        long count = property("zas.fuzz.seeds", 1);
        int ops = (int) property("zas.fuzz.ops", IntegrityFuzz.DEFAULT_OPS);
        List<DynamicTest> tests = new ArrayList<>();
        for (long seed = first; seed < first + count; seed++) {
            long s = seed;
            tests.add(DynamicTest.dynamicTest("seed " + s, () -> {
                IntegrityFuzz fuzz = new IntegrityFuzz(s, ops);
                long start = System.nanoTime();
                fuzz.run();
                long millis = (System.nanoTime() - start) / 1_000_000;
                System.out.println("NFR-010: seed " + s + ", " + ops + " operations, " + millis + " ms: " + fuzz.stats);
                if (ops >= IntegrityFuzz.DEFAULT_OPS) {
                    exercised(fuzz.stats);
                }
            }));
        }
        return tests;
    }

    /** A run that never got to these would pass without testing them. */
    private static void exercised(IntegrityFuzz.Stats s) {
        assertTrue(s.blockEdits > 1000, "block edits: " + s);
        assertTrue(s.gasOps > 1000, "gas operations: " + s);
        assertTrue(s.unloads > 100, "unloads: " + s);
        assertTrue(s.reloads > 100, "reloads: " + s);
        assertTrue(s.saveLoads > 10, "save/load cycles: " + s);
        assertTrue(s.rejectedComponents > 0, "rejected components: " + s);
        assertTrue(s.mostZones > 3, "zones: " + s);
        assertTrue(s.mostPaused > 0, "paused zones: " + s);
        assertTrue(s.checkpoints >= 10, "audits: " + s);
    }

    @Test
    void nfr_010_a_seed_replays_exactly() {
        assertEquals(new IntegrityFuzz(5, 1500).run(), new IntegrityFuzz(5, 1500).run());
    }

    /** What every failure must say: the seed, the operation and how to replay it. */
    private static void reports(AssertionError failure, long seed, int atOrAfter, String what) {
        String message = failure.getMessage();
        assertTrue(message.contains("seed " + seed + " failed at operation "), message);
        assertTrue(message.contains("-Pfuzz.seed=" + seed), message);
        String rest = message.substring(message.indexOf("operation ") + "operation ".length());
        int op = Integer.parseInt(rest.substring(0, rest.indexOf(' ')));
        assertTrue(op >= atOrAfter, "operation " + op + " is before the change at " + atOrAfter);
        assertTrue(message.contains(what), message);
    }

    /** Gas no category explains fails on CORE-073's report, at the operation's tick. */
    @Test
    void nfr_010_gas_made_behind_the_ledger_fails() {
        AssertionError failure = assertThrows(
                AssertionError.class, () -> new IntegrityFuzz(2, 3000).sabotage(400, f -> f.conjure(50)).run());
        reports(failure, 2, 400, "conservation");
    }

    /**
     * Shares that come back doubled are explained by the storage category, so CORE-073 says nothing:
     * only the independent total can catch them.
     */
    @Test
    void nfr_010_gas_made_by_a_reload_fails_on_the_independent_total() {
        AssertionError failure = assertThrows(
                AssertionError.class,
                () -> new IntegrityFuzz(3, 3000).sabotage(400, IntegrityFuzz::doubleSavedShares).run());
        reports(failure, 3, 400, "the zones and the saved shares hold");
    }

    /** A block change the engine never heard of fails the next audit. */
    @Test
    void nfr_010_a_lost_block_change_fails_the_audit() {
        AssertionError failure = assertThrows(
                AssertionError.class,
                () -> new IntegrityFuzz(4, 1200).sabotage(900, IntegrityFuzz::loseBlockChange).run());
        reports(failure, 4, 900, "the audit found");
    }
}
