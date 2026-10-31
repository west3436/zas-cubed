package io.github.west3436.zas.engine.scheduler;

/** Prints the determinism scenario's hashes, for the cross-JVM check (CORE-072). */
public final class DeterminismMain {
    private DeterminismMain() {}

    public static void main(String[] args) {
        System.out.println(DeterminismTest.scenario(null));
    }
}
