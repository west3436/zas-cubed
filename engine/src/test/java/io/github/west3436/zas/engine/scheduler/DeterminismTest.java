package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Same inputs, same outcome: threaded or inline, run to run, JVM to JVM (CORE-044, CORE-071, CORE-072). */
class DeterminismTest {

    /**
     * A scenario with zone creation, breaches, venting, merges, removals, open-air operations and
     * a rejected component. Returns the engine's state hash after every tick, then the final
     * published table's hash.
     */
    static List<Long> scenario(ExecutorService executor) {
        Sim sim = new Sim(new TestWorld(Ambient.STANDARD_AIR), executor, EnclosureClassifier.FACTORY);
        Random random = new Random(42);
        int[] xs = {1, 5, 9, 13, 17, 21};
        for (int x : xs) {
            sim.world.room(x, 64, 1, x + 2, 66, 3);
        }
        List<Long> hashes = new ArrayList<>();
        for (int t = 0; t < 40; t++) {
            for (int i = 0; i < 3; i++) {
                int x = xs[random.nextInt(xs.length)] + random.nextInt(3);
                if (random.nextInt(4) == 0) {
                    sim.remove(x, 65, 2, random.nextDouble() * 20, random.nextDouble() * 5);
                } else {
                    sim.add(x, 65, 2, random.nextDouble() * 50, random.nextDouble() * 50);
                }
            }
            sim.add(40, 80, 40, 1, 1); // open air
            if (random.nextInt(5) == 0) {
                int wall = 4 + 4 * random.nextInt(xs.length - 1);
                sim.world.set(wall, 65, 2, random.nextBoolean() ? TestWorld.AIR : TestWorld.SOLID);
            }
            if (t == 20) {
                sim.world.set(xs[0] - 1, 65, 2, TestWorld.AIR); // a breach to the open
            }
            boolean bounce = t % 7 == 3;
            sim.betweenCloseAndApply = s -> {
                if (bounce) {
                    long chunk = SectionKeys.chunk(0, 0);
                    s.world.unload(chunk);
                    s.pipeline.chunkUnloaded(chunk);
                    s.world.load(chunk);
                    s.pipeline.chunkLoaded(chunk);
                }
            };
            sim.step();
            hashes.add(sim.engine.stateHash());
        }
        long h = 17;
        for (ZoneView z : sim.snapshot().zones()) {
            h = h * 31 + z.hashCode();
            h = h * 31 + Double.hashCode(z.temperature());
        }
        hashes.add(h);
        return hashes;
    }

    /** The same batches threaded and single-threaded give the same states, exactly (CORE-071). */
    @Test
    void core_071_threaded_matches_single_threaded() throws InterruptedException {
        ExecutorService workers = AtmosWorkers.create(2);
        try {
            List<Long> threaded = scenario(workers);
            List<Long> inline = scenario(null);
            assertEquals(inline, threaded);
        } finally {
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    /** Running the scenario twice gives the same results, exactly (CORE-044). */
    @Test
    void core_044_runs_are_identical() {
        assertEquals(scenario(null), scenario(null));
    }

    /**
     * A second JVM, with its own hash seeds and identity hash codes, gets the same results, so no
     * outcome depends on hash order (CORE-072, CORE-044).
     */
    @Test
    void core_072_results_independent_of_hash_order() throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process p = new ProcessBuilder(
                        java,
                        "-XX:+UnlockExperimentalVMOptions",
                        "-XX:hashCode=2", // a different identity hash scheme from this JVM's
                        "-cp",
                        System.getProperty("java.class.path"),
                        DeterminismMain.class.getName())
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), out);
        assertEquals(scenario(null).toString(), out.lines().reduce((a, b) -> b).orElse(""));
    }
}
