package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockCycle;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.heater.ThermoMachineBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What Jade's server sends for the devices that read a room (COMPAT-030): the pressure or the
 * temperature each last read, never the room's gases (COMPAT-032), and nothing until it has read.
 *
 * <p>These drive the framework's atmos ticks themselves, so they have a batch of their own.
 */
@GameTestHolder(JadeReadoutTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class JadeRoomReadoutTests {
    private static final String BATCH = "zas_jade_rooms";

    /** The most atmos ticks a test waits for the air around a new device to be known. */
    private static final int TRIES = 8;

    private JadeRoomReadoutTests() {}

    /** A reading of one row with no gases listed. */
    private static Reading.Row only(GameTestHelper h, Reading reading, String what) {
        h.assertValueEqual(reading.rows().size(), 1, what + "'s rows");
        h.assertTrue(reading.shares().isEmpty(), what + " lists the room's gases");
        Reading.Row row = reading.rows().get(0);
        h.assertValueEqual(row.lead(), Reading.Lead.BODY, what + "'s lead");
        return row;
    }

    /** An air sensor reads the pressure where it is, and no temperature. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_an_air_sensor_reads_its_pressure(GameTestHelper h) {
        Spot s = new Spot(h, 20);
        s.clear();
        s.set(1, 1, 0, Spot.STONE);
        AirSensorBlockEntity sensor = s.place(
                1,
                1,
                1,
                AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.SOUTH),
                AirSensorBlockEntity.class);
        BlockPos pos = sensor.getBlockPos();
        h.assertTrue(JadeProbe.asks(s.level, pos), "a client wouldn't ask about an air sensor");
        h.assertTrue(JadeProbe.silent(s.level, pos), "a sensor that has read nothing has a reading");
        for (int i = 0; i < TRIES && Double.isNaN(sensor.pressure()); i++) {
            s.tick();
            sensor.step();
        }
        h.assertFalse(Double.isNaN(sensor.pressure()), "the sensor read nothing in " + TRIES + " atmos ticks");
        Reading.Row row = only(h, JadeProbe.reading(s.level, pos), "the air sensor");
        h.assertTrue(Spot.close(row.kpa(), sensor.pressure()), "the sensor's kPa: " + row.kpa());
        h.assertFalse(row.hasTemperature(), "the air sensor has a temperature");
        s.clear();
        h.succeed();
    }

    /** An airlock controller reads its chamber's pressure, from the sensor linked for it. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_an_airlock_controller_reads_its_chamber(GameTestHelper h) {
        Spot s = new Spot(h, 21);
        s.clear();
        s.set(1, 1, 0, Spot.STONE);
        s.set(3, 1, 0, Spot.STONE);
        AirlockControllerBlockEntity controller = s.place(
                1,
                1,
                1,
                AtmosBlocks.AIRLOCK_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AirlockControllerBlock.FACING, Direction.SOUTH),
                AirlockControllerBlockEntity.class);
        AirSensorBlockEntity sensor = s.place(
                3,
                1,
                1,
                AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.SOUTH),
                AirSensorBlockEntity.class);
        BlockPos pos = controller.getBlockPos();
        h.assertTrue(JadeProbe.asks(s.level, pos), "a client wouldn't ask about an airlock controller");
        controller.step();
        h.assertTrue(JadeProbe.silent(s.level, pos), "a controller with no chamber sensor has a reading");

        controller.arm(AirlockControllerBlockEntity.Slot.sensor(AirlockCycle.CHAMBER));
        controller.toggleLink(s.level, sensor.getBlockPos());
        for (int i = 0; i < TRIES && Double.isNaN(controller.chamberKpa()); i++) {
            s.tick();
            sensor.step();
            controller.step();
        }
        h.assertFalse(Double.isNaN(controller.chamberKpa()), "the controller read no chamber pressure");
        Reading.Row row = only(h, JadeProbe.reading(s.level, pos), "the airlock controller");
        h.assertTrue(Spot.close(row.kpa(), controller.chamberKpa()), "the chamber's kPa: " + row.kpa());
        h.assertFalse(row.hasTemperature(), "the airlock controller has a temperature");
        s.clear();
        h.succeed();
    }

    /** An area air controller reads its room's pressure and temperature, but only while it has power. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_an_area_air_controller_reads_its_room_while_powered(GameTestHelper h) {
        Spot s = new Spot(h, 22);
        s.clear();
        s.set(1, 2, 0, Spot.STONE);
        AreaAirControllerBlockEntity controller = s.place(
                1,
                2,
                1,
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, Direction.SOUTH),
                AreaAirControllerBlockEntity.class);
        BlockPos pos = controller.getBlockPos();
        h.assertTrue(JadeProbe.asks(s.level, pos), "a client wouldn't ask about an area air controller");
        s.tick();
        controller.step();
        h.assertFalse(controller.powered(), "the controller has power with an empty buffer");
        h.assertTrue(JadeProbe.silent(s.level, pos), "a controller without power has a reading");

        controller.buffer().fill(1_000_000);
        for (int i = 0; i < TRIES && controller.values().length == 0; i++) {
            s.tick();
            controller.step();
        }
        h.assertTrue(controller.powered(), "the controller has no power with a full buffer");
        double[] values = controller.values();
        h.assertTrue(values.length >= 2, "the powered controller read nothing in " + TRIES + " atmos ticks");
        Reading.Row row = only(h, JadeProbe.reading(s.level, pos), "the area air controller");
        h.assertTrue(Spot.close(row.kpa(), values[0]), "the room's kPa: " + row.kpa());
        h.assertTrue(Spot.close(row.kelvin(), values[1]), "the room's K: " + row.kelvin());
        h.assertTrue(row.kpa() > 50 && row.kelvin() > 200, "not the Overworld's air: " + row);
        s.clear();
        h.succeed();
    }

    /** A heater and an air conditioner read their room's temperature while they are on, and no pressure. */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_030_a_heater_and_an_air_conditioner_read_their_room_while_on(GameTestHelper h) {
        Spot s = new Spot(h, 23);
        s.clear();
        s.room();
        List<Block> blocks = List.of(AtmosBlocks.HEATER.get(), AtmosBlocks.AIR_CONDITIONER.get());
        ThermoMachineBlockEntity[] machines = new ThermoMachineBlockEntity[blocks.size()];
        for (int i = 0; i < machines.length; i++) {
            machines[i] = s.place(1 + 2 * i, 1, 2, blocks.get(i).defaultBlockState(), ThermoMachineBlockEntity.class);
        }
        // A room is a zone once it holds gas of its own.
        CompletableFuture<MutationResult> added = GasMutations.add(
                s.level,
                s.point(2, 2, 2),
                new GasParcel(Map.of(Spot.N2, 80.0, Spot.O2, 20.0), 301),
                SourceId.of(ZasAtmosMod.MOD_ID, "gametest"));
        for (int i = 0; i < TRIES && !added.isDone(); i++) {
            s.tick();
        }
        h.assertTrue(
                added.isDone() && Atmospheres.zoneAt(s.level, s.abs(2, 2, 2)).isPresent(), "the room is no zone");
        double room = Atmospheres.zoneAt(s.level, s.abs(2, 2, 2))
                .orElseThrow()
                .mixture()
                .temperature();

        for (int i = 0; i < machines.length; i++) {
            ThermoMachineBlockEntity machine = machines[i];
            BlockPos pos = machine.getBlockPos();
            String what = "the " + blocks.get(i).getName().getString();
            h.assertTrue(JadeProbe.asks(s.level, pos), "a client wouldn't ask about " + what);
            machine.step();
            h.assertTrue(JadeProbe.silent(s.level, pos), what + " has a reading while it is off");

            machine.setOn(true);
            machine.step();
            h.assertFalse(Double.isNaN(machine.roomTemperature()), what + " read no room");
            Reading.Row row = only(h, JadeProbe.reading(s.level, pos), what);
            h.assertFalse(row.hasPressure(), what + " has a pressure");
            h.assertTrue(Spot.close(row.kelvin(), machine.roomTemperature()), what + "'s K: " + row.kelvin());
            h.assertTrue(
                    Math.abs(row.kelvin() - room) < 1, what + " reads " + row.kelvin() + " K in a room at " + room);
            machine.setOn(false);
        }
        s.clear();
        h.succeed();
    }
}
