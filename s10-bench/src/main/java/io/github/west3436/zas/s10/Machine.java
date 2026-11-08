package io.github.west3436.zas.s10;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The machine the bench runs on, and the processors it may use. */
final class Machine {
    private static String affinity = "not set";

    private Machine() {}

    /**
     * Pins this process to the processors of a hex mask ({@code zas.s10.affinity}), as the owner's rig
     * runs it: logical CPUs 0 to 11, six cores with hyperthreading, like D-40's 6-core, 12-thread parts.
     * The JVM is also told it has that many processors (Gradle passes {@code -XX:ActiveProcessorCount}),
     * so its thread pools are sized for them.
     */
    static void pin() {
        String mask = System.getProperty("zas.s10.affinity", "none").trim();
        if (mask.isEmpty() || mask.equalsIgnoreCase("none")) {
            affinity = "left alone";
            return;
        }
        long pid = ProcessHandle.current().pid();
        List<String> command = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            command.addAll(List.of(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "(Get-Process -Id " + pid + ").ProcessorAffinity = 0x" + mask + "; (Get-Process -Id " + pid
                            + ").ProcessorAffinity"));
        } else {
            command.addAll(List.of("taskset", "-a", "-p", mask, String.valueOf(pid)));
        }
        affinity = "0x" + mask + ": " + run(command);
    }

    static String describe() {
        String cpu = cpuName();
        long heap = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        List<String> gcs = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcs.add(gc.getName());
        }
        return String.format(
                "%s; %d processors as the JVM sees them; affinity %s; Java %s (%s); heap %,d MiB; collectors %s; %s"
                        + " %s; commit %s",
                cpu,
                Runtime.getRuntime().availableProcessors(),
                affinity,
                System.getProperty("java.version"),
                System.getProperty("java.vm.name"),
                heap,
                String.join(", ", gcs),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("zas.s10.commit", "unknown"));
    }

    private static String cpuName() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            String name =
                    run(List.of("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Processor).Name"));
            if (!name.isBlank() && !name.startsWith("failed")) {
                return name.trim();
            }
        }
        String id = System.getenv("PROCESSOR_IDENTIFIER");
        return id == null ? System.getProperty("os.arch") : id;
    }

    private static String run(List<String> command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "failed: timed out";
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return p.exitValue() == 0 ? out : "failed (" + p.exitValue() + "): " + out;
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "failed: interrupted";
        }
    }
}
