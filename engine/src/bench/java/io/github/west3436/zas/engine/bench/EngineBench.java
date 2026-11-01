package io.github.west3436.zas.engine.bench;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The headless engine benchmarks (architecture §8): {@code ./gradlew :engine:bench}. Prints
 * Markdown, the tables recorded in {@code docs/research/benchmarks.md}.
 *
 * <p>Arguments: case names to run ({@code fill}, {@code zones}, {@code s10}, {@code scaling},
 * {@code changes}, {@code toggle}; all by default), and {@code --quick} for short runs that only check the
 * harness works. Every number is wall-clock time from {@link System#nanoTime()} on one JVM, after
 * warm-up; there is no JMH, so treat differences under about 10 % as noise.
 */
public final class EngineBench {
    private EngineBench() {}

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        boolean quick = false;
        List<String> cases = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--quick")) {
                quick = true;
            } else {
                cases.add(a);
            }
        }
        if (cases.isEmpty()) {
            cases = List.of("fill", "zones", "s10", "scaling", "changes", "toggle");
        }
        environment(quick);
        long start = System.nanoTime();
        for (String c : cases) {
            switch (c) {
                case "fill" -> FillBench.run(quick);
                case "zones" -> ZoneBench.run(quick);
                case "s10" -> StationBench.run(quick);
                case "scaling" -> ScalingBench.run(quick);
                case "changes" -> ChangeBench.run(quick);
                case "toggle" -> DoorBench.run(quick);
                default ->
                    throw new IllegalArgumentException(
                            "unknown case " + c + "; expected fill, zones, s10, scaling, changes or toggle");
            }
        }
        System.out.printf("_All cases took %.0f s._%n", (System.nanoTime() - start) / 1e9);
    }

    private static void environment(boolean quick) {
        Runtime rt = Runtime.getRuntime();
        String cpu = System.getenv("PROCESSOR_IDENTIFIER");
        System.out.println("# Engine benchmarks" + (quick ? " (quick)" : ""));
        System.out.println();
        System.out.println("- JVM: " + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.runtime.version") + " (" + System.getProperty("java.vm.vendor") + ")");
        System.out.println("- OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch"));
        System.out.println(
                "- CPU: " + (cpu != null ? cpu : "unknown") + ", " + rt.availableProcessors() + " hardware threads");
        System.out.printf("- Max heap: %.1f GiB%n", rt.maxMemory() / (1024.0 * 1024 * 1024));
        System.out.println("- JVM arguments: "
                + String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()));
        System.out.println();
    }

    static void section(String title) {
        System.out.println("## " + title);
        System.out.println();
    }

    /** Heap in use after a few collections, in bytes. */
    static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            best = Math.min(best, rt.totalMemory() - rt.freeMemory());
        }
        return best;
    }
}
