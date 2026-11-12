package io.github.west3436.zas.atmos.compat.cc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterMenu;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The peripherals as a real computer has them (COMPAT-020, COMPAT-021): a CC: Tweaked computer
 * beside a device, or on a wired modem beside it, running a Lua program. These show that CC: Tweaked
 * takes every method as written, and that a read and a write arrive as Lua has them. The checks of
 * every value and every refusal are in {@link CcReadTests} and {@link CcWriteTests}, which call the
 * peripherals' methods directly.
 */
@GameTestHolder(CcComputerTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CcComputerTests {
    static final String NAMESPACE = "zas_cc";
    private static final String BATCH = "zas_cc_computers";

    /** A program that waits for a peripheral of a type, since a modem may join it a tick later. */
    static final String FIND = """
            local function find(kind)
              while true do
                local found = peripheral.find(kind)
                if found then
                  return found
                end
                sleep(0.05)
              end
            end
            """;

    private CcComputerTests() {}

    /** Ends a test: its spot cleared and its chunks released. */
    private static void done(Spot s) {
        Devices.clear(s);
        s.forceLoad(false);
    }

    /**
     * Three computers, with every device between them: each lists the peripherals around it, by
     * type, with their methods. Every type is there, with every method, so CC: Tweaked took each
     * one's arguments and results as written.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Computers.TIMEOUT_TICKS)
    public static void compat_020_a_computer_finds_every_device_with_every_method(GameTestHelper h) {
        Spot s = new Spot(h, 0);
        s.forceLoad(true);
        Devices.clear(s);
        // A computer's six neighbours don't touch each other, so no device joins another.
        int[][] sides = {{-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        List<String> types = new ArrayList<>(Devices.SURFACE.keySet());
        // The wall panels and the console take more room than a block: west, east and south are free.
        List<String> wide =
                List.of("zas_area_air_controller", "zas_airlock_controller", "zas_central_atmos_controller");
        types.removeAll(wide);
        List<Computers.Computer> computers = new ArrayList<>();
        String program = """
                local out = {}
                for _, name in ipairs(peripheral.getNames()) do
                  local methods = peripheral.getMethods(name)
                  table.sort(methods)
                  out[peripheral.getType(name)] = methods
                end
                put("out", json(out))
                """;
        for (int c = 0; c * sides.length < types.size(); c++) {
            int x = 3 + 6 * c;
            for (int i = 0; i < sides.length && c * sides.length + i < types.size(); i++) {
                Devices.place(s, types.get(c * sides.length + i), x + sides[i][0], 2 + sides[i][1], 3 + sides[i][2]);
            }
            computers.add(Computers.start(s, x, 2, 3, program));
        }
        // The last computer: a wall panel above it and one east of it, each on stone to its north, and
        // the console south of it.
        int x = 3 + 6 * computers.size();
        Devices.place(s, wide.get(0), x + 1, 2, 3);
        Devices.place(s, wide.get(1), x, 3, 3);
        Devices.place(s, wide.get(2), x, 2, 4);
        computers.add(Computers.start(s, x, 2, 3, program));

        h.startSequence()
                .thenWaitUntil(() -> {
                    Map<String, List<String>> found = new TreeMap<>();
                    for (Computers.Computer computer : computers) {
                        JsonObject out = computer.json("out").getAsJsonObject();
                        for (String type : out.keySet()) {
                            List<String> methods = new ArrayList<>();
                            out.get(type).getAsJsonArray().forEach(m -> methods.add(m.getAsString()));
                            found.put(type, methods);
                        }
                    }
                    Map<String, List<String>> expected = new TreeMap<>();
                    Devices.SURFACE.forEach((type, methods) ->
                            expected.put(type, methods.stream().sorted().toList()));
                    h.assertValueEqual(found, expected, "the peripherals the computers found");
                })
                .thenExecute(() -> done(s))
                .thenSucceed();
    }

    /**
     * A computer beside a canister reads it: what Lua gets is what the canister's window shows, and
     * what the gas in it comes to.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Computers.TIMEOUT_TICKS)
    public static void compat_020_a_computer_reads_a_canister_as_its_window_shows_it(GameTestHelper h) {
        Spot s = new Spot(h, 2);
        s.forceLoad(true);
        Devices.clear(s);
        CanisterBlockEntity canister =
                s.place(1, 1, 1, AtmosBlocks.CANISTER.get().defaultBlockState(), CanisterBlockEntity.class);
        canister.network().gas().add(new GasParcel(Map.of(Spot.O2, 30.0, Spot.N2, 10.0), Spot.T));
        canister.setReleasePressure(250);
        Computers.Computer computer = Computers.start(s, 2, 1, 1, FIND + """
                        local c = find("zas_canister")
                        put("out", json({
                          type = peripheral.getType(c),
                          pressure = c.getPressure(),
                          amount = c.getAmount(),
                          contents = c.getContents(),
                          onPort = c.isOnPort(),
                          valveOpen = c.isValveOpen(),
                          releasePressure = c.getReleasePressure(),
                        }))
                        """);
        h.startSequence()
                .thenWaitUntil(() -> {
                    JsonObject out = computer.json("out").getAsJsonObject();
                    double pressure = GasMixture.GAS_CONSTANT * Spot.T * 40 / canister.nodeVolume();
                    h.assertValueEqual(out.get("type").getAsString(), "zas_canister", "its type");
                    s.close(out.get("pressure").getAsDouble(), pressure, "its pressure, kPa");
                    s.close(out.get("amount").getAsDouble(), 40, "its amount, mol");
                    JsonObject contents = out.getAsJsonObject("contents");
                    h.assertValueEqual(contents.size(), 2, "the gases in it");
                    s.close(contents.get(Spot.O2.toString()).getAsDouble(), 30, "its oxygen, mol");
                    s.close(contents.get(Spot.N2.toString()).getAsDouble(), 10, "its nitrogen, mol");
                    h.assertFalse(out.get("onPort").getAsBoolean(), "on a port");
                    h.assertFalse(out.get("valveOpen").getAsBoolean(), "its valve");
                    s.close(out.get("releasePressure").getAsDouble(), 250, "its release pressure, kPa");

                    // The window's own numbers, which are whole.
                    Player player = h.makeMockPlayer(GameType.SURVIVAL);
                    CanisterMenu window = (CanisterMenu) canister.createMenu(1, player.getInventory(), player);
                    h.assertValueEqual(
                            window.value(CanisterMenu.DATA_PRESSURE),
                            (int) Math.round(out.get("pressure").getAsDouble()),
                            "the window's pressure");
                    h.assertValueEqual(
                            window.value(CanisterMenu.DATA_MOLES),
                            (int) Math.round(out.get("amount").getAsDouble()),
                            "the window's amount");
                    h.assertValueEqual(
                            window.value(CanisterMenu.DATA_RELEASE),
                            (int) Math.round(out.get("releasePressure").getAsDouble()),
                            "the window's release pressure");
                    h.assertValueEqual(
                            window.value(CanisterMenu.DATA_OPEN) != 0,
                            out.get("valveOpen").getAsBoolean(),
                            "the window's valve");
                })
                .thenExecute(() -> done(s))
                .thenSucceed();
    }

    /**
     * A computer beside a pressure pump sets it: the pump takes a target in its range and its
     * switch, and a target outside the range, or one that is no number, is a Lua error that leaves
     * the pump as it was.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Computers.TIMEOUT_TICKS)
    public static void compat_021_a_computer_sets_a_pump_and_is_refused_outside_its_range(GameTestHelper h) {
        Spot s = new Spot(h, 4);
        s.forceLoad(true);
        Devices.clear(s);
        PressurePumpBlockEntity pump =
                s.place(1, 1, 1, AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState(), PressurePumpBlockEntity.class);
        double highest = pump.clamp(Double.MAX_VALUE);
        Computers.Computer computer = Computers.start(s, 2, 1, 1, FIND + """
                        local p = find("zas_pressure_pump")
                        local out = {}
                        out.before = p.getTargetPressure()
                        out.set = call(p.setTargetPressure, 250.5)
                        out.after = p.getTargetPressure()
                        out.switch = call(p.setOn, true)
                        out.on = p.isOn()
                        out.tooHigh = call(p.setTargetPressure, 1e9)
                        out.negative = call(p.setTargetPressure, -1)
                        out.text = call(p.setTargetPressure, "lots")
                        out.none = call(p.setTargetPressure)
                        out.kept = p.getTargetPressure()
                        put("out", json(out))
                        """);
        double before = pump.setting();
        h.startSequence()
                .thenWaitUntil(() -> {
                    JsonObject out = computer.json("out").getAsJsonObject();
                    s.close(out.get("before").getAsDouble(), before, "the target it read first");
                    h.assertTrue(ok(out, "set"), "setting 250.5 kPa was refused: " + out.get("set"));
                    s.close(out.get("after").getAsDouble(), 250.5, "the target it read back");
                    h.assertTrue(ok(out, "switch"), "switching it on was refused: " + out.get("switch"));
                    h.assertTrue(out.get("on").getAsBoolean(), "it read the pump as off");
                    String range = "target pressure must be 0 to " + Lua.number(highest) + " kPa";
                    h.assertTrue(error(out, "tooHigh").contains(range), "too high: " + out.get("tooHigh"));
                    h.assertTrue(error(out, "negative").contains(range), "negative: " + out.get("negative"));
                    h.assertTrue(error(out, "text").contains("number expected"), "text: " + out.get("text"));
                    h.assertTrue(error(out, "none").contains("number expected"), "nothing: " + out.get("none"));
                    s.close(out.get("kept").getAsDouble(), 250.5, "the target after the refusals");
                    // And the pump itself.
                    s.close(pump.setting(), 250.5, "the pump's target");
                    h.assertTrue(pump.on(), "the pump is off");
                    s.close(pump.portPressure(PumpBlockEntity.INPUT), 0, "its input's pressure");
                })
                .thenExecute(() -> done(s))
                .thenSucceed();
    }

    /**
     * A computer reaches a vent through a wired modem block between them: the vent has a name on the
     * network that starts with its type, and reads and sets as it does beside the computer.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Computers.TIMEOUT_TICKS)
    public static void compat_020_a_computer_reaches_a_device_through_a_wired_modem(GameTestHelper h) {
        Spot s = new Spot(h, 6);
        s.forceLoad(true);
        Devices.clear(s);
        VentBlockEntity vent = s.place(1, 1, 1, AtmosBlocks.VENT.get().defaultBlockState(), VentBlockEntity.class);
        Computers.modem(s, 2, 1, 1);
        List<Computers.Computer> computer = new ArrayList<>();
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    Computers.switchOn(s, 2, 1, 1);
                    computer.add(Computers.start(s, 3, 1, 1, FIND + """
                                    local v = find("zas_vent")
                                    local name = peripheral.getName(v)
                                    local out = { name = name, type = peripheral.getType(name), mode = v.getMode() }
                                    out.set = call(v.setMode, "siphon")
                                    out.bound = call(v.setExternalBound, 80)
                                    out.wrong = call(v.setMode, "sideways")
                                    out.read = v.getMode()
                                    put("out", json(out))
                                    """));
                })
                .thenWaitUntil(() -> {
                    JsonObject out = computer.get(0).json("out").getAsJsonObject();
                    String name = out.get("name").getAsString();
                    h.assertTrue(name.startsWith("zas_vent_"), "its name on the network: " + name);
                    h.assertValueEqual(out.get("type").getAsString(), "zas_vent", "its type");
                    h.assertValueEqual(out.get("mode").getAsString(), "off", "the mode it read first");
                    h.assertTrue(ok(out, "set") && ok(out, "bound"), "a setting was refused: " + out);
                    h.assertTrue(
                            error(out, "wrong").contains("mode must be one of: off, blow, siphon"),
                            "a mode there isn't: " + out.get("wrong"));
                    h.assertValueEqual(out.get("read").getAsString(), "siphon", "the mode it read back");
                    h.assertValueEqual(vent.settings().mode(), VentMode.SIPHON, "the vent's mode");
                    s.close(vent.settings().external(), 80, "the vent's external bound");
                })
                .thenExecute(() -> done(s))
                .thenSucceed();
    }

    /**
     * A device broken while a computer has it is detached, as CC: Tweaked does for any peripheral:
     * the computer hears it go and gets nothing from it after, a call that was already on its way to
     * the server is an error, and the area air controller's events no longer know the computer.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Computers.TIMEOUT_TICKS)
    public static void compat_020_a_device_broken_is_detached_from_its_computer(GameTestHelper h) {
        Spot s = new Spot(h, 8);
        s.forceLoad(true);
        Devices.clear(s);
        AreaAirControllerBlockEntity controller = Devices.areaAirController(s, 1, 1, 1);
        Computers.Computer computer = Computers.start(s, 2, 1, 1, FIND + """
                        local c = find("zas_area_air_controller")
                        local name = peripheral.getName(c)
                        put("ready", name)
                        while true do
                          local _, side = os.pullEvent("peripheral_detach")
                          if side == name then
                            break
                          end
                        end
                        put("out", json({ present = peripheral.isPresent(name), late = call(c.getStatus) }))
                        """);
        BlockPos at = s.abs(1, 1, 1);
        h.startSequence()
                .thenWaitUntil(() -> {
                    computer.read("ready");
                    h.assertValueEqual(ZoneEvents.attached(controller), 1, "the computers on the controller");
                })
                .thenExecute(() -> {
                    // What the computer was given a moment ago, called after the block went.
                    AreaAirControllerPeripheral held = s.peripheral(1, 1, 1, AreaAirControllerPeripheral.class);
                    s.level.removeBlock(at, false);
                    h.assertValueEqual(
                            s.refused(held::getStatus, "a call on a broken device"), "the device is gone", "");
                    h.assertTrue(s.peripheral(at, Direction.EAST) == null, "air is a peripheral");
                })
                .thenWaitUntil(() -> {
                    JsonObject out = computer.json("out").getAsJsonObject();
                    h.assertFalse(out.get("present").getAsBoolean(), "the computer still has the controller");
                    // CC: Tweaked answers a call on a peripheral that is gone with nothing, and no error.
                    h.assertFalse(out.getAsJsonObject("late").has("value"), "the detached controller still answered");
                    h.assertValueEqual(ZoneEvents.attached(controller), 0, "the computers on the broken controller");
                })
                .thenExecute(() -> done(s))
                .thenSucceed();
    }

    /** Whether a call the program recorded went through. */
    static boolean ok(JsonObject out, String key) {
        return out.getAsJsonObject(key).get("ok").getAsBoolean();
    }

    /** The error of a call the program recorded. */
    static String error(JsonObject out, String key) {
        JsonObject call = out.getAsJsonObject(key);
        if (call.get("ok").getAsBoolean()) {
            throw new GameTestAssertException(key + " was no Lua error: " + call);
        }
        JsonElement error = call.get("error");
        return error == null ? "" : error.getAsString();
    }
}
