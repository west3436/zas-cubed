package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.fire.IgnitionOutcome;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.compat.kubejs.ScriptAtmospheres;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * ZAS³'s calls in a pack's server script (COMPAT-051). The run's {@code calls.js} hands {@link
 * Probe} functions that call {@code Atmospheres}, {@code Breathing}, {@code GasMutations}, {@code
 * HeatMutations}, {@code ZonePins} and {@code Fires} as a script writes them, and note what came
 * back; the tests call the functions and compare the notes with what the same call gives a mod.
 */
@GameTestHolder(EventTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CallTests {
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    private CallTests() {}

    /**
     * What a script calls {@code Atmospheres} stands in for the API's class, so it has each of its
     * calls: one added to the API has to be added there too.
     */
    @GameTest(template = "empty")
    public static void compat_051_the_atmospheres_a_script_gets_has_every_call_of_the_apis(GameTestHelper h) {
        Set<String> scripts = new TreeSet<>();
        for (Method method : ScriptAtmospheres.class.getMethods()) {
            if (method.getDeclaringClass() == ScriptAtmospheres.class) {
                scripts.add(method.getName());
            }
        }
        Set<String> api = new TreeSet<>();
        for (Method method : Atmospheres.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                api.add(method.getName());
            }
        }
        h.assertValueEqual(scripts, api, "the calls a script has on Atmospheres");
        h.succeed();
    }

    /**
     * A script reads the air at a block, at a point and at an entity, a zone, an entity's breath
     * and a zone's fires, and gets what a mod gets.
     */
    @GameTest(template = "empty")
    public static void compat_051_a_script_reads_through_each_entry_point(GameTestHelper h) {
        try (Lab lab = new Lab(h, 10)) {
            ZoneInfo zone = lab.room(0, Lab.AIR);
            Pig pig = lab.mob(EntityType.PIG, 0);
            BlockPos inside = lab.abs(2, 2, 2);
            BlockPos open = lab.abs(2, 20, 2);
            Probe.forget("read.");
            Probe.task("read").run(lab.level, inside, open, pig);

            AtmosphereSample sample = Atmospheres.at(lab.level, inside);
            h.assertValueEqual(Probe.noted("read.state"), AtmosphereState.ZONED, "Atmospheres.at's state");
            lab.close(Probe.number("read.pressure"), sample.pressure(), 1e-9, "its pressure");
            lab.close(Probe.number("read.pressure"), StandardAir.PRESSURE, 1e-6, "the room's pressure");
            lab.close(Probe.number("read.temperature"), sample.temperature(), 1e-9, "its temperature");
            lab.close(
                    Probe.number("read.oxygen"),
                    StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION,
                    1e-6,
                    "its oxygen, asked for by the id as text");
            h.assertValueEqual(Probe.noted("read.zoned"), true, "its zone");
            h.assertValueEqual(Probe.noted("read.open.state"), AtmosphereState.AMBIENT, "the state in the open");
            h.assertValueEqual(Probe.noted("read.point.state"), AtmosphereState.ZONED, "the state at a point");
            h.assertValueEqual(Probe.noted("read.array.state"), AtmosphereState.ZONED, "the state at [x, y, z]");
            h.assertValueEqual(
                    Probe.noted("read.array.open.state"),
                    AtmosphereState.AMBIENT,
                    "the state at [x, y, z] in the open");
            h.assertTrue(
                    Probe.noted("read.text").toString().contains("takes a block position, a Vec3d or [x, y, z]"),
                    "what Atmospheres.at says of a place it can't read: " + Probe.noted("read.text"));
            lab.close(
                    Probe.number("read.entity.pressure"),
                    Atmospheres.of(pig).pressure(),
                    1e-9,
                    "Atmospheres.of an entity");

            lab.close(Probe.number("read.zone.volume"), zone.volume(), 1e-9, "Atmospheres.zoneAt's volume");
            h.assertValueEqual(Probe.noted("read.zone.enclosed"), true, "whether it is enclosed");
            h.assertValueEqual(Probe.noted("read.zone.same"), true, "Atmospheres.zone by the id");
            h.assertValueEqual(Probe.noted("read.zone.point"), true, "Atmospheres.zoneAt a point");
            h.assertValueEqual(Probe.noted("read.zone.open"), false, "Atmospheres.zoneAt in the open");
            h.assertValueEqual(
                    Probe.noted("read.containerExplosionsBreakBlocks"),
                    Atmospheres.containerExplosionsBreakBlocks(),
                    "Atmospheres.containerExplosionsBreakBlocks");
            h.assertValueEqual(
                    Probe.noted("read.zoneTouches"),
                    Atmospheres.zoneTouches(lab.level, inside),
                    "Atmospheres.zoneTouches");
            lab.close(Probe.number("read.periodTicks"), Atmospheres.periodTicks(), 0, "Atmospheres.periodTicks");
            h.assertValueEqual(Probe.noted("read.preset"), Atmospheres.preset(), "Atmospheres.preset");
            lab.close(
                    Probe.number("read.nearVacuumDensity"),
                    Atmospheres.nearVacuumDensity(),
                    0,
                    "Atmospheres.nearVacuumDensity");

            h.assertValueEqual(Probe.noted("read.breathing.canBreathe"), true, "Breathing.of: can it breathe");
            h.assertValueEqual(Probe.noted("read.breathing.reason"), BreathReason.BREATHABLE, "the reason");
            h.assertValueEqual(Probe.noted("read.breathing.source"), BreathSource.ATMOSPHERE, "the source");
            h.assertValueEqual(Probe.noted("read.breathing.vanilla"), true, "Breathing.vanilla");
            lab.close(
                    Probe.number("read.breathing.oxygenMinimum"),
                    Breathing.oxygenMinimum(),
                    0,
                    "Breathing.oxygenMinimum");

            h.assertValueEqual(Probe.noted("read.fires.canBurnIn"), true, "Fires.canBurnIn the room's air");
            h.assertValueEqual(Probe.noted("read.fires.isBurning"), false, "Fires.isBurning");
            lab.close(Probe.number("read.fires.hotspots"), 0, 0, "Fires.hotspots");
            h.assertValueEqual(Probe.noted("read.fires.isHotspot"), false, "Fires.isHotspot");
            lab.close(Probe.number("read.fires.extinguish"), 0, 0, "Fires.extinguish");
        }
        h.succeed();
    }

    /**
     * A script adds and removes gas and heat, pins a zone and lights a fire, and each answers it at
     * the atmos tick that makes the change, as it answers a mod. What the script wrote as text, as
     * an array and as an object reached ZAS³ as an id, a point and a parcel of gas.
     */
    @GameTest(template = "empty")
    public static void compat_051_a_script_changes_through_each_entry_point(GameTestHelper h) {
        try (Lab lab = new Lab(h, 11)) {
            lab.room(0, Lab.AIR);
            Pig pig = lab.mob(EntityType.PIG, 0);
            BlockPos inside = lab.abs(2, 2, 2);
            BlockPos open = lab.abs(2, 20, 2);
            double before = lab.zone(2, 2, 2).mixture().moles(CO2);
            Probe.forget("change.");
            Probe.forget("unpin.");
            Probe.task("change").run(lab.level, inside, open, pig);
            lab.ticks(4);

            h.assertValueEqual(Probe.noted("change.add.outcome"), MutationOutcome.ZONE, "GasMutations.add");
            lab.close(Probe.number("change.add.moved"), 3, 1e-9, "the gas it added, from a GasParcel");
            lab.close(Probe.number("change.add.object.moved"), 2, 1e-9, "the gas it added, from an object");
            lab.close(Probe.number("change.remove.moved"), 1, 1e-9, "GasMutations.remove");
            lab.close(lab.zone(2, 2, 2).mixture().moles(CO2) - before, 2, 1e-9, "the room's carbon dioxide");

            h.assertValueEqual(Probe.noted("change.heat.add.outcome"), MutationOutcome.ZONE, "HeatMutations.add");
            lab.close(Probe.number("change.heat.add.joules"), 5000, 1e-6, "the heat it added");
            lab.close(Probe.number("change.heat.remove.joules"), 2000, 1e-6, "HeatMutations.remove");

            h.assertValueEqual(Probe.noted("change.pin.outcome"), PinOutcome.ZONE, "ZonePins.pin");
            h.assertValueEqual(Probe.noted("change.pin.zoned"), true, "the zone it pinned");
            h.assertValueEqual(Probe.noted("change.pin.open.outcome"), PinOutcome.OPEN, "ZonePins.pin in the open");

            h.assertValueEqual(
                    Probe.noted("change.ignite.outcome"),
                    IgnitionOutcome.NOT_FLAMMABLE,
                    "Fires.ignite where nothing burns");
            h.assertValueEqual(Probe.noted("change.ignite.burning"), false, "whether it burns");
            h.assertFalse(Fires.isBurning(lab.level, lab.zone(2, 2, 2).id()), "the room is on fire");

            Probe.task("unpin").run(lab.level, inside, open, pig);
            lab.ticks(2);
            h.assertValueEqual(Probe.noted("unpin.done"), true, "ZonePins.unpin");
        }
        h.succeed();
    }

    /**
     * A change asked for from another thread fails as it does for a mod: with an exception that
     * names the call and the thread, and nothing done.
     */
    @GameTest(template = "empty")
    public static void compat_051_a_change_from_another_thread_fails_clearly(GameTestHelper h) {
        try (Lab lab = new Lab(h, 12)) {
            lab.room(0, Lab.AIR);
            Pig pig = lab.mob(EntityType.PIG, 0);
            BlockPos inside = lab.abs(2, 2, 2);
            double before = lab.zone(2, 2, 2).mixture().totalMoles();
            Probe.Task task = Probe.task("offThread");
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread other = new Thread(
                    () -> {
                        try {
                            task.run(lab.level, inside, lab.abs(2, 20, 2), pig);
                        } catch (Throwable t) {
                            thrown.set(t);
                        }
                    },
                    "zas-kubejs-gametest");
            other.start();
            try {
                other.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            h.assertFalse(other.isAlive(), "the script's call from another thread never returned");
            h.assertTrue(thrown.get() != null, "the script's call from another thread threw nothing");
            StringBuilder said = new StringBuilder();
            for (Throwable t = thrown.get(); t != null; t = t.getCause()) {
                said.append(t).append('\n');
            }
            h.assertTrue(
                    said.toString().contains("GasMutations.add may only be called on the server thread")
                            && said.toString().contains("zas-kubejs-gametest"),
                    "what it threw doesn't name the call and the thread: " + said);
            lab.ticks(2);
            lab.close(lab.zone(2, 2, 2).mixture().totalMoles(), before, 1e-9, "the room's gas afterwards");
        }
        h.succeed();
    }
}
