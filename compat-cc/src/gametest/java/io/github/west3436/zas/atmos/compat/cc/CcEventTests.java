package io.github.west3436.zas.atmos.compat.cc;

import com.google.gson.JsonObject;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.fire.IgnitionOutcome;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.engine.scheduler.HotspotSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a computer on an area air controller hears of its zone (COMPAT-022): {@code
 * zas_zone_breached}, {@code zas_zone_sealed}, {@code zas_fire_ignited} and {@code
 * zas_fire_extinguished}, each with the name the controller is attached by and the x, y and z of the
 * event's block. The breaches and fires are real ones: a roof block taken out and put back, and
 * hydrogen lit and taken away.
 */
@GameTestHolder(CcComputerTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CcEventTests {
    private static final String BATCH = "zas_cc_events";

    /** For the test that asks for atmos ticks itself: the ones with real computers wait on the server's. */
    private static final String DIRECT_BATCH = "zas_cc_events_direct";

    private static final SourceId SOURCE = SourceId.of("zas_atmos", "cc_gametest");

    /** Every shipped gas, a lot of it: removing this empties a room. */
    private static final Map<ResourceLocation, Double> EVERYTHING = Map.of(
            Spot.N2,
            1e6,
            Spot.O2,
            1e6,
            Spot.H2,
            1e6,
            Spot.CO2,
            1e6,
            gas("water_vapor"),
            1e6,
            gas("carbon_monoxide"),
            1e6,
            gas("methane"),
            1e6,
            gas("nitrous_oxide"),
            1e6);

    private CcEventTests() {}

    private static ResourceLocation gas(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas", path);
    }

    /** One step of each controller, then one atmos tick. */
    private static void cycles(Spot s, int n, AtmosDevice... devices) {
        for (int i = 0; i < n; i++) {
            for (AtmosDevice device : devices) {
                if (!device.isRemoved()) {
                    device.step();
                }
            }
            s.tick();
        }
    }

    private static <T> T settle(Spot s, CompletableFuture<T> future) {
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            s.tick();
        }
        s.h.assertTrue(future.isDone(), "the mutation didn't complete within 8 atmos ticks");
        return future.join();
    }

    /**
     * Runs a test's body with fires that don't spread, so a fire is where the test lit it. The body
     * must run within the call, so no other test's atmos tick sees the setting.
     */
    private static void withoutSpread(Runnable body) {
        FireConfig.overrideHotspotsForTest(new HotspotSettings(0));
        try {
            body.run();
        } finally {
            FireConfig.overrideHotspotsForTest(null);
        }
    }

    /** Hydrogen to a tenth of an atmosphere in the room whose first corner is at x0: enough to burn in its air. */
    private static GasParcel hydrogen(Spot s, int x0) {
        ZoneInfo zone = Atmospheres.zoneAt(s.level, s.abs(x0 + 2, 2, 2))
                .orElseThrow(() -> new GameTestAssertException("the room has no zone"));
        return new GasParcel(
                Map.of(Spot.H2, 0.1 * 101.325 * zone.volume() / (GasMixture.GAS_CONSTANT * Spot.T)), Spot.T);
    }

    /** Whether an event's block is inside the room whose first corner is at x0. */
    private static boolean inRoom(Spot s, int x0, int x, int y, int z) {
        BlockPos low = s.abs(x0 + 1, 1, 1);
        BlockPos high = s.abs(x0 + 3, 3, 3);
        return x >= low.getX()
                && x <= high.getX()
                && y >= low.getY()
                && y <= high.getY()
                && z >= low.getZ()
                && z <= high.getZ();
    }

    /** Checks the last event a fake computer got, and how many it has had. */
    private static void heard(Spot s, Devices.FakeComputer computer, int count, String event, int x0) {
        s.h.assertValueEqual(computer.events.size(), count, "the events " + computer.getAttachmentName() + " got");
        List<Object> last = computer.events.get(count - 1);
        s.h.assertValueEqual(last.size(), 5, "what came with " + last);
        s.h.assertValueEqual(last.get(0), event, "the event");
        s.h.assertValueEqual(last.get(1), computer.getAttachmentName(), "the name the controller is attached by");
        s.h.assertTrue(
                inRoom(s, x0, (Integer) last.get(2), (Integer) last.get(3), (Integer) last.get(4)),
                "the event's block isn't in the room: " + last);
    }

    /**
     * Two rooms, each with a controller, and computers that only record: an event goes to every
     * computer on the controller of the zone it names and to no other, with the name each has the
     * controller by. A computer that detached hears no more, and a controller that is gone is
     * forgotten at the next event.
     */
    @GameTest(template = "empty", batch = DIRECT_BATCH)
    public static void compat_022_an_event_goes_to_the_computers_on_that_zones_controller(GameTestHelper h) {
        Spot s = new Spot(h, 20);
        s.load();
        withoutSpread(() -> {
            Devices.clear(s);
            s.room(0);
            s.room(6);
            AreaAirControllerBlockEntity a = Devices.areaAirController(s, 2, 2, 1);
            AreaAirControllerBlockEntity b = Devices.areaAirController(s, 8, 2, 1);
            cycles(s, 3, a, b);
            h.assertTrue(a.lastPin() == PinOutcome.ZONE, "the first room is " + a.lastPin());
            h.assertTrue(b.lastPin() == PinOutcome.ZONE, "the second room is " + b.lastPin());

            Devices.FakeComputer left = new Devices.FakeComputer("left");
            Devices.FakeComputer wired = new Devices.FakeComputer("zas_area_air_controller_7");
            Devices.FakeComputer other = new Devices.FakeComputer("top");
            // CC: Tweaked asks for a peripheral each time, so each computer has one of its own.
            s.peripheral(2, 2, 1, AreaAirControllerPeripheral.class).attach(left);
            s.peripheral(2, 2, 1, AreaAirControllerPeripheral.class).attach(wired);
            s.peripheral(8, 2, 1, AreaAirControllerPeripheral.class).attach(other);
            h.assertValueEqual(ZoneEvents.attached(a), 2, "the computers on the first controller");
            h.assertValueEqual(ZoneEvents.attached(b), 1, "the computers on the second controller");
            cycles(s, 2, a, b);
            h.assertValueEqual(left.events.size() + wired.events.size() + other.events.size(), 0, "events so far");

            // A breach of the first room, and its sealing.
            s.set(2, 4, 2, Spot.AIR);
            cycles(s, 12, a, b);
            heard(s, left, 1, ZoneEvents.BREACHED, 0);
            heard(s, wired, 1, ZoneEvents.BREACHED, 0);
            s.set(2, 4, 2, Spot.STONE);
            cycles(s, 4, a, b);
            heard(s, left, 2, ZoneEvents.SEALED, 0);
            heard(s, wired, 2, ZoneEvents.SEALED, 0);

            // One computer detaches, through another peripheral of the same controller.
            s.peripheral(2, 2, 1, AreaAirControllerPeripheral.class).detach(wired);
            h.assertValueEqual(ZoneEvents.attached(a), 1, "the computers on the first controller, one gone");

            // A fire in the first room, and its end.
            settle(s, GasMutations.add(s.level, s.point(2, 2, 2), hydrogen(s, 0), SOURCE));
            h.assertValueEqual(
                    settle(s, Fires.ignite(s.level, s.abs(2, 2, 2), SOURCE)).outcome(),
                    IgnitionOutcome.IGNITED,
                    "lighting the hydrogen");
            cycles(s, 2, a, b);
            heard(s, left, 3, ZoneEvents.IGNITED, 0);
            BlockPos lit = s.abs(2, 2, 2);
            List<Object> where = List.of(lit.getX(), lit.getY(), lit.getZ());
            h.assertValueEqual(left.events.get(2).subList(2, 5), where, "the fire's block");
            settle(s, GasMutations.remove(s.level, s.point(2, 2, 2), EVERYTHING, SOURCE));
            cycles(s, 3, a, b);
            heard(s, left, 4, ZoneEvents.EXTINGUISHED, 0);
            h.assertValueEqual(wired.events.size(), 2, "the events the detached computer got");
            h.assertValueEqual(other.events.size(), 0, "the events the other room's computer got");

            // The first controller goes with nobody detaching its computer; the second room is breached.
            s.level.removeBlock(s.abs(2, 2, 1), false);
            s.set(8, 4, 2, Spot.AIR);
            cycles(s, 12, a, b);
            heard(s, other, 1, ZoneEvents.BREACHED, 6);
            h.assertValueEqual(left.events.size(), 4, "the events the gone controller's computer got");
            h.assertValueEqual(ZoneEvents.attached(a), 0, "the computers kept for a controller that is gone");
            s.peripheral(8, 2, 1, AreaAirControllerPeripheral.class).detach(other);
            h.assertValueEqual(ZoneEvents.attached(b), 0, "the computers on the second controller, detached");
            Devices.clear(s);
        });
        h.succeed();
    }

    /**
     * A real computer beside an area air controller, waiting for events: it hears its room breached
     * and sealed, then lit and put out, each once and in that order, with the side the controller is
     * on and a block of the room.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Computers.TIMEOUT_TICKS)
    public static void compat_022_a_computer_hears_its_zone_breached_sealed_lit_and_put_out(GameTestHelper h) {
        Spot s = new Spot(h, 24);
        s.forceLoad(true);
        Devices.clear(s);
        s.room(0);
        AreaAirControllerBlockEntity controller = Devices.areaAirController(s, 2, 2, 1);
        List<Computers.Computer> computer = new ArrayList<>();
        List<CompletableFuture<?>> asked = new ArrayList<>();
        String program = CcComputerTests.FIND + """
                local c = find("zas_area_air_controller")
                put("ready", peripheral.getName(c))
                local n = 0
                while n < 4 do
                  local e = table.pack(os.pullEvent())
                  if type(e[1]) == "string" and e[1]:sub(1, 4) == "zas_" then
                    n = n + 1
                    put("event" .. n, json({ event = e[1], from = e[2], x = e[3], y = e[4], z = e[5], count = e.n }))
                  end
                end
                """;
        h.startSequence()
                // The controller forms its zone at its first step.
                .thenWaitUntil(() -> h.assertTrue(controller.lastPin() == PinOutcome.ZONE, "the room isn't sealed yet"))
                .thenExecute(() -> computer.add(Computers.start(s, 1, 2, 1, program)))
                .thenWaitUntil(() -> {
                    computer.get(0).read("ready");
                    h.assertValueEqual(ZoneEvents.attached(controller), 1, "the computers on the controller");
                })
                .thenExecute(() -> s.set(2, 4, 2, Spot.AIR))
                .thenWaitUntil(() -> heard(s, computer.get(0), 1, ZoneEvents.BREACHED))
                .thenExecute(() -> s.set(2, 4, 2, Spot.STONE))
                .thenWaitUntil(() -> heard(s, computer.get(0), 2, ZoneEvents.SEALED))
                .thenExecute(() -> asked.add(GasMutations.add(s.level, s.point(2, 2, 2), hydrogen(s, 0), SOURCE)))
                .thenWaitUntil(() -> h.assertTrue(asked.get(0).isDone(), "the hydrogen isn't in yet"))
                .thenExecute(() -> asked.add(Fires.ignite(s.level, s.abs(2, 2, 2), SOURCE)))
                .thenWaitUntil(() -> heard(s, computer.get(0), 3, ZoneEvents.IGNITED))
                .thenExecute(() -> asked.add(GasMutations.remove(s.level, s.point(2, 2, 2), EVERYTHING, SOURCE)))
                .thenWaitUntil(() -> heard(s, computer.get(0), 4, ZoneEvents.EXTINGUISHED))
                .thenExecute(() -> {
                    Devices.clear(s);
                    s.forceLoad(false);
                })
                .thenSucceed();
    }

    /** Checks the nth event a real computer wrote down. */
    private static void heard(Spot s, Computers.Computer computer, int n, String event) {
        JsonObject heard = computer.json("event" + n).getAsJsonObject();
        s.h.assertValueEqual(heard.get("event").getAsString(), event, "event " + n);
        s.h.assertValueEqual(heard.get("from").getAsString(), computer.read("ready"), "the side it came from");
        s.h.assertValueEqual(heard.get("count").getAsInt(), 5, "what came with the event");
        s.h.assertTrue(
                inRoom(
                        s,
                        0,
                        heard.get("x").getAsInt(),
                        heard.get("y").getAsInt(),
                        heard.get("z").getAsInt()),
                "the event's block isn't in the room: " + heard);
    }

    /**
     * A computer and a controller in a chunk that unloads and loads again: unloading it detaches the
     * computer, so nothing is kept for a controller that isn't there, and loading it starts the
     * computer again, which is attached to the controller that was loaded with it.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Computers.TIMEOUT_TICKS)
    public static void compat_022_a_chunk_unloading_detaches_its_computer_and_loading_attaches_it_again(
            GameTestHelper h) {
        // Far from every other spot, so no other test's chunks keep this one loaded.
        Spot s = new Spot(h, 70);
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        s.level.setChunkForced(cx, cz, true);
        Devices.clear(s);
        AreaAirControllerBlockEntity controller = Devices.areaAirController(s, 2, 1, 2);
        Computers.Computer computer = Computers.start(s, 3, 1, 2, CcComputerTests.FIND + """
                        local c = find("zas_area_air_controller")
                        local starts = 1
                        if fs.exists("starts") then
                          local f = fs.open("starts", "r")
                          starts = tonumber(f.readAll()) + 1
                          f.close()
                        end
                        local f = fs.open("starts", "w")
                        f.write(tostring(starts))
                        f.close()
                        put("ready" .. starts, c.getMode())
                        """);
        BlockPos at = s.abs(2, 1, 2);
        h.startSequence()
                .thenWaitUntil(() -> {
                    computer.read("ready1");
                    h.assertValueEqual(ZoneEvents.attached(controller), 1, "the computers on the controller");
                })
                .thenExecute(() -> s.level.setChunkForced(cx, cz, false))
                .thenWaitUntil(() -> {
                    h.assertFalse(s.level.hasChunk(cx, cz), "the chunk is still loaded");
                    h.assertTrue(controller.isRemoved(), "the controller wasn't unloaded with its chunk");
                    h.assertValueEqual(ZoneEvents.attached(controller), 0, "the computers kept for it");
                })
                .thenExecute(() -> s.level.setChunkForced(cx, cz, true))
                .thenWaitUntil(() -> {
                    h.assertTrue(s.level.hasChunk(cx, cz), "the chunk isn't loaded again");
                    computer.read("ready2");
                    BlockEntity loaded = s.level.getBlockEntity(at);
                    h.assertTrue(
                            loaded instanceof AreaAirControllerBlockEntity && loaded != controller,
                            "the controller wasn't loaded again: " + loaded);
                    h.assertValueEqual(
                            ZoneEvents.attached((AreaAirControllerBlockEntity) loaded),
                            1,
                            "the computers on the controller loaded again");
                    h.assertValueEqual(ZoneEvents.attached(controller), 0, "the computers kept for the old one");
                })
                .thenExecute(() -> {
                    Devices.clear(s);
                    s.level.setChunkForced(cx, cz, false);
                })
                .thenSucceed();
    }
}
