package io.github.west3436.zas.engine.bench;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Locale;

/**
 * The benchmark regression check (NFR-034): {@code ./gradlew :engine:benchCheck}, which CI runs. It
 * scripts the S-10 station ({@link StationBench}) on the engine at the commit recorded in {@code
 * engine/s10-baseline.properties} and on this code, and fails if this code costs more than {@value
 * #TOLERANCE_PERCENT} % over the baseline.
 *
 * <p>The baseline is a commit, not a time, because CI's machines differ by far more than that (see
 * docs/research/benchmarks.md). So both engines run here, on this machine, in turn, each from its
 * own class loader so that neither shares the other's classes or their JIT profiles. Each pair
 * gives a ratio, and the result is the median over the pairs, after warm-up pairs that let the JIT
 * settle.
 */
public final class RegressionCheck {
    /** How much slower than the baseline still passes, in percent (NFR-034). */
    static final int TOLERANCE_PERCENT = 10;

    private static final int WARM_PAIRS = 10;
    private static final int PAIRS = 20;

    /** The engine's side of the script: 2 minutes of warm-up, then 10 of game time, as {@code s10}. */
    private static final int MINUTES = 10;

    private static final int WARM_MINUTES = 2;

    private RegressionCheck() {}

    /**
     * Runs the check.
     *
     * @param args the baseline's commit, its classes, and this code's classes as a class path
     * @throws ReflectiveOperationException if either engine's script doesn't have the shape {@link
     *     Script} expects
     */
    public static void main(String[] args) throws ReflectiveOperationException {
        Locale.setDefault(Locale.ROOT);
        Script baseline = new Script(args[1]);
        Script current = new Script(args[2]);
        double[] worker = new double[PAIRS];
        double[] server = new double[PAIRS];
        double[] baselineWorker = new double[PAIRS];
        double[] baselineServer = new double[PAIRS];
        long start = System.nanoTime();
        for (int i = -WARM_PAIRS; i < PAIRS; i++) {
            // By turns, so that neither always runs straight after the other.
            boolean baselineFirst = (i & 1) == 0;
            double[] was = baselineFirst ? baseline.run() : null;
            double[] now = current.run();
            was = baselineFirst ? was : baseline.run();
            if (i >= 0) {
                worker[i] = now[0] / was[0];
                server[i] = now[1] / was[1];
                baselineWorker[i] = was[0];
                baselineServer[i] = was[1];
            }
        }
        System.out.println("S-10 regression check (NFR-034) against " + args[0] + ": "
                + Math.round((System.nanoTime() - start) / 1e9) + " s, " + PAIRS + " pairs after " + WARM_PAIRS
                + " to warm up.");
        boolean workerOk = row("Worker thread, per atmos tick", worker, baselineWorker);
        boolean serverOk = row("Server thread, per atmos tick", server, baselineServer);
        if (!workerOk || !serverOk) {
            String message = "S-10 costs more than " + TOLERANCE_PERCENT
                    + " % over its recorded baseline (NFR-034). To accept that, see docs/research/benchmarks.md.";
            System.out.println((System.getenv("GITHUB_ACTIONS") != null ? "::error::" : "") + message);
            System.exit(1);
        }
    }

    private static boolean row(String what, double[] ratios, double[] baselineMs) {
        double change = (median(ratios) - 1) * 100;
        boolean ok = change <= TOLERANCE_PERCENT;
        System.out.printf(
                "%s: baseline %.3f ms, this code %+.1f %% (%s). Middle half of the pairs: %+.1f to %+.1f %%.%n",
                what,
                median(baselineMs),
                change,
                ok ? "pass" : "FAIL",
                (quantile(ratios, 0.25) - 1) * 100,
                (quantile(ratios, 0.75) - 1) * 100);
        return ok;
    }

    private static double median(double[] values) {
        return quantile(values, 0.5);
    }

    private static double quantile(double[] values, double q) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[(int) (sorted.length * q)];
    }

    /**
     * One engine's script. It's reached by reflection because the two engines' classes have the same
     * names and only one can be compiled against: a baseline commit keeps its shape for good, so
     * this can't call anything added after it.
     */
    private static final class Script {
        private final Method script;
        private final Field worker;
        private final Field server;
        private final Method meanMs;

        Script(String classPath) throws ReflectiveOperationException {
            URL[] urls = Arrays.stream(classPath.split(File.pathSeparator))
                    .map(Script::url)
                    .toArray(URL[]::new);
            ClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
            String pkg = RegressionCheck.class.getPackageName();
            Class<?> bench = loader.loadClass(pkg + ".StationBench");
            Class<?> run = loader.loadClass(pkg + ".StationBench$Run");
            Class<?> heap = loader.loadClass(pkg + ".StationBench$Heap");
            script = bench.getDeclaredMethod("script", int.class, boolean.class, int.class, int.class, heap);
            script.setAccessible(true);
            worker = run.getDeclaredField("worker");
            worker.setAccessible(true);
            server = run.getDeclaredField("server");
            server.setAccessible(true);
            meanMs = worker.getType().getDeclaredMethod("meanMs");
            meanMs.setAccessible(true);
        }

        private static URL url(String path) {
            try {
                return new File(path).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(path, e);
            }
        }

        /** Runs the script once, with device operations, at the default period: mean ms per atmos tick. */
        double[] run() throws ReflectiveOperationException {
            Object run = script.invoke(null, 20, true, MINUTES, WARM_MINUTES, null);
            return new double[] {(double) meanMs.invoke(worker.get(run)), (double) meanMs.invoke(server.get(run))};
        }
    }
}
