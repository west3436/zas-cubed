package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.life.HazardHandler;
import io.github.west3436.zas.api.life.HazardHandlers;
import io.github.west3436.zas.api.mutation.GasMutations;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a reload of the scripts does to what they registered (COMPAT-052, COMPAT-053): the adapter
 * removes it all just before server scripts load, so afterwards exactly what the scripts register
 * now is registered. Nothing is left from a script that no longer registers it, nothing fails as a
 * duplicate, and nothing is called twice. Both of a pack maker's reloads are run: KubeJS's own
 * command, and a datapack reload, which loads server scripts again too (twice over, as KubeJS
 * has it).
 *
 * <p>The run's {@code hazard.js} registers one handler only while a test wants it, as a script
 * that was edited between two reloads. Each script's last line counts that it ran to its end.
 * These are in batches of their own: a reload runs the server's queued work while it waits.
 */
@GameTestHolder(EventTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class ReloadTests {
    private static final List<String> SCRIPTS =
            List.of("events.js", "calls.js", "hazard.js", "hazard_throws.js", "ambient.js", "ambient_throws.js");

    private ReloadTests() {}

    private static Map<String, Integer> loads() {
        Map<String, Integer> loads = new LinkedHashMap<>();
        SCRIPTS.forEach(script -> loads.put(script, Probe.loads(script)));
        return loads;
    }

    /**
     * Checks that every script ran to its end again, and gives the new counts. Not that it ran
     * once: a datapack reload has KubeJS load its server scripts twice.
     */
    private static Map<String, Integer> ranAgain(GameTestHelper h, Map<String, Integer> before, String when) {
        Map<String, Integer> now = loads();
        for (String script : SCRIPTS) {
            h.assertTrue(
                    now.get(script) > before.get(script),
                    script + " didn't run to its end " + when + ": " + before + " before, " + now + " now");
        }
        return now;
    }

    private static HazardHandler handler(String path) {
        return HazardHandlers.registered().stream()
                .filter(r -> r.id().equals(Lab.id(path)))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no handler " + path + " is registered"))
                .handler();
    }

    /** The handlers scripts have registered, by id, in the order they run. */
    private static List<String> handlers() {
        return HazardHandlers.registered().stream()
                .filter(r -> r.id().getNamespace().equals("zas_kubejs"))
                .map(r -> r.id().getPath())
                .toList();
    }

    private static List<String> providers(ResourceKey<Level> dimension) {
        return AmbientProviders.registered(dimension).stream()
                .map(r -> r.id().getPath() + " " + r.priority())
                .toList();
    }

    /**
     * Checks a reload: once with the script no longer registering its extra handler, and once with
     * it registering it again.
     */
    private static void reloads(GameTestHelper h, int area, Runnable reload) {
        try (Lab lab = new Lab(h, area)) {
            // A failure half way leaves the scripts as they were for the other tests.
            lab.restoring(() -> {
                Probe.mode("extra", "");
                if (!handlers().contains("extra")) {
                    reload.run();
                }
            });
            h.assertValueEqual(
                    handlers(), List.of("a_broken", "b_working", "choking", "extra", "tagged"), "handlers at first");
            HazardHandler before = handler("choking");
            Map<String, Integer> loads = loads();

            Probe.mode("extra", "off");
            reload.run();
            loads = ranAgain(h, loads, "after one reload");
            h.assertValueEqual(
                    handlers(),
                    List.of("a_broken", "b_working", "choking", "tagged"),
                    "handlers once the script registers one fewer");
            h.assertTrue(handler("choking") != before, "the handler is still the one from before the reload");
            h.assertValueEqual(providers(Level.END), List.of("end_air 5", "end_fallback 0"), "the End's providers");
            h.assertValueEqual(providers(Level.NETHER), List.of("nether_air 0"), "the Nether's providers");

            // Once each, not once for each time the scripts loaded.
            lab.clear(0, 5);
            lab.settle(GasMutations.add(
                    lab.level, lab.point(2, 20, 2), new GasParcel(Map.of(Lab.O2, 1.0), 293.15), Lab.SOURCE));
            Probe.one(lab, "openAirGas");
            lab.airRoom(0, Map.of(Lab.id("script_gas"), 1.0));
            Pig pig = lab.mob(EntityType.PIG, 0);
            HazardTests.twoSeconds(pig, lab.level.getGameTime());
            h.assertValueEqual(Probe.exposed("choking", pig).size(), 1, "the calls to a handler after a reload");
            h.assertValueEqual(Probe.exposed("extra", pig).size(), 0, "the calls to the handler that is gone");
            lab.close(pig.getMaxHealth() - pig.getHealth(), 3, 1e-4, "the harm done after a reload");

            Probe.mode("extra", "");
            reload.run();
            ranAgain(h, loads, "after two reloads");
            h.assertValueEqual(
                    handlers(),
                    List.of("a_broken", "b_working", "choking", "extra", "tagged"),
                    "handlers once the script registers it again");
        }
    }

    /** {@code /kubejs reload server-scripts} leaves what the scripts register now. */
    @GameTest(template = "empty", batch = "zas_kubejs_reload_scripts")
    public static void compat_052_reloading_the_scripts_leaves_what_they_register_now(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        reloads(
                h,
                30,
                () -> server.getCommands()
                        .performPrefixedCommand(server.createCommandSourceStack(), "kubejs reload server-scripts"));
        h.succeed();
    }

    /** {@code /reload} leaves what the scripts register now. */
    @GameTest(template = "empty", batch = "zas_kubejs_reload_datapacks", timeoutTicks = 400)
    public static void compat_053_reloading_the_datapacks_leaves_what_the_scripts_register_now(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        reloads(
                h,
                31,
                () -> server.reloadResources(server.getPackRepository().getSelectedIds())
                        .join());
        h.succeed();
    }
}
