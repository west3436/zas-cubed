package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlock;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlockEntity;
import io.github.west3436.zas.atmos.firealarm.FireAlarmStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.heat.HeatSettings;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fire alarm (CONT-044). Each test builds two sealed rooms: room A, at x 0 to 4, holds a
 * controller on its north wall, powered by the test FE block behind it; room B, at x 6 to 10, holds
 * a fire alarm on its north wall, linked to that controller with the multitool, so the
 * controller's own readings stay safe and only the alarm can raise it. Heat is off, so a room's
 * temperature moves only by the heat a test adds.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireAlarmTests {
    private static final String BATCH = "zas_atmos_controller";

    private FireAlarmTests() {}

    private record Rooms(AreaAirControllerBlockEntity controller, FireAlarmBlockEntity alarm) {}

    private static Rooms build(Site s) {
        s.clear();
        s.room(0, 4);
        s.set(2, 2, 0, TestParts.battery().defaultBlockState());
        AreaAirControllerBlockEntity c = s.controller(2);
        s.room(6, 10);
        BlockState state =
                AtmosBlocks.FIRE_ALARM.get().defaultBlockState().setValue(FireAlarmBlock.FACING, Direction.SOUTH);
        s.h.assertTrue(state.canSurvive(s.level, s.abs(8, 2, 1)), "the alarm has no wall behind it");
        FireAlarmBlockEntity alarm = s.place(8, 2, 1, state, FireAlarmBlockEntity.class);
        c.toggleLink(alarm.getBlockPos());
        // Forms room B's zone, which takes in the ambient air.
        s.settle(GasMutations.add(
                s.level, s.point(8, 2, 2), new GasParcel(Map.of(StandardAir.NITROGEN, 1.0), 293.15), Site.SOURCE));
        return new Rooms(c, alarm);
    }

    /** Heats room B to {@code kelvin}. */
    private static void heatTo(Site s, double kelvin) {
        var zone = s.zone(8, 2, 2);
        double joules = (kelvin - zone.mixture().temperature())
                * GasProperties.heatCapacity(zone.mixture().moles());
        var future = joules >= 0
                ? HeatMutations.add(s.level, s.point(8, 2, 2), joules, Site.SOURCE)
                : HeatMutations.remove(s.level, s.point(8, 2, 2), -joules, Site.SOURCE);
        s.settle(future);
    }

    private static FireAlarmStatus status(FireAlarmBlockEntity alarm) {
        return alarm.getBlockState().getValue(FireAlarmBlock.STATUS);
    }

    private static void run(GameTestHelper h, Site s, Runnable checks) {
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            checks.run();
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
            s.clear();
        }
        h.succeed();
    }

    /**
     * A powered fire alarm whose room is above 473 K raises its controller to danger 2, as the
     * controller's own danger 2 would: its panel, its redstone and its firelocks' alarm follow. At
     * 470 K it doesn't. Once the room cools the alarm stops at once, and the controller drops back
     * after CONT-046's 10 readings (CONT-044).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_044_fire_alarm_raises_danger_2_above_473_k(GameTestHelper h) {
        Site s = new Site(h, 62);
        run(h, s, () -> {
            Rooms r = build(s);
            AreaAirControllerBlockEntity c = r.controller();
            FireAlarmBlockEntity alarm = r.alarm();
            s.cycles(2, c, alarm);
            h.assertTrue(alarm.power().isPowered(), "the linked alarm has no power");
            h.assertValueEqual(status(alarm), FireAlarmStatus.NORMAL, "the alarm in a cool room");
            h.assertValueEqual(c.danger(), 0, "the controller with a cool alarm");

            heatTo(s, 470);
            s.cycles(2, c, alarm);
            h.assertFalse(alarm.triggered(), "the alarm tripped at 470 K");
            h.assertValueEqual(c.danger(), 0, "the controller with the alarm at 470 K");

            heatTo(s, 500);
            s.cycles(2, c, alarm);
            h.assertTrue(alarm.triggered(), "the alarm didn't trip at 500 K");
            h.assertValueEqual(status(alarm), FireAlarmStatus.ALARM, "the alarm's model at 500 K");
            h.assertTrue(c.fire(), "the controller didn't see the alarm");
            h.assertValueEqual(c.danger(), 2, "the controller with the alarm tripped");
            h.assertTrue(c.alarming(), "the controller isn't alarming its firelocks");
            h.assertValueEqual(c.status(), ControllerStatus.DANGER2, "the controller's panel");
            h.assertValueEqual(s.level.getDirectSignalTo(s.abs(2, 2, 0)), 15, "the controller's redstone");

            heatTo(s, 293.15);
            s.cycle(c, alarm);
            h.assertFalse(alarm.triggered(), "the alarm still tripped once cool");
            h.assertValueEqual(status(alarm), FireAlarmStatus.NORMAL, "the alarm's model once cool");
            s.cycles(9, c, alarm);
            h.assertValueEqual(c.danger(), 2, "the controller fell before 10 lower readings");
            s.cycle(c, alarm);
            h.assertFalse(c.fire(), "the controller still sees a fire");
            h.assertValueEqual(c.danger(), 0, "the controller after 10 lower readings");
        });
    }

    /**
     * A powered fire alarm whose zone has a hotspot raises its controller to danger 2, though the
     * room is far below 473 K: a vanilla fire on netherrack in room B is a hotspot (FIRE-019,
     * {@code Fires.isBurning}). Once the fire is out the alarm stops (CONT-044).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_044_fire_alarm_raises_danger_2_for_a_hotspot(GameTestHelper h) {
        Site s = new Site(h, 64);
        run(h, s, () -> {
            Rooms r = build(s);
            AreaAirControllerBlockEntity c = r.controller();
            FireAlarmBlockEntity alarm = r.alarm();
            s.cycles(2, c, alarm);
            h.assertValueEqual(c.danger(), 0, "the controller before the fire");

            s.set(8, 0, 2, Blocks.NETHERRACK.defaultBlockState());
            s.set(8, 1, 2, Blocks.FIRE.defaultBlockState());
            s.tick();
            var zone = s.zone(8, 2, 2);
            h.assertTrue(Fires.isBurning(s.level, zone.id()), "the room with a fire isn't burning");
            h.assertTrue(
                    zone.mixture().temperature() < 400,
                    "the room is hot: " + zone.mixture().temperature());
            s.cycles(2, c, alarm);
            h.assertTrue(alarm.triggered(), "the alarm didn't trip for the hotspot");
            h.assertValueEqual(status(alarm), FireAlarmStatus.ALARM, "the alarm's model with a fire");
            h.assertTrue(c.fire(), "the controller didn't see the alarm");
            h.assertValueEqual(c.danger(), 2, "the controller with a fire");
            h.assertTrue(c.alarming(), "the controller isn't alarming its firelocks");

            s.set(8, 1, 2, Site.AIR);
            s.tick();
            h.assertFalse(Fires.isBurning(s.level, zone.id()), "the room still burns once the fire is out");
            s.cycle(c, alarm);
            h.assertFalse(alarm.triggered(), "the alarm still tripped once the fire is out");
        });
    }

    /**
     * A fire alarm no controller powers shows unpowered and raises nothing, even above 473 K: once
     * unlinked, its lease runs out and the controller falls back (CONT-044, CONT-064).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_044_an_unpowered_fire_alarm_does_nothing(GameTestHelper h) {
        Site s = new Site(h, 63);
        run(h, s, () -> {
            Rooms r = build(s);
            AreaAirControllerBlockEntity c = r.controller();
            FireAlarmBlockEntity alarm = r.alarm();
            heatTo(s, 500);
            c.toggleLink(alarm.getBlockPos());
            s.cycles(3, c, alarm);
            h.assertFalse(alarm.power().isPowered(), "the unlinked alarm has power");
            h.assertValueEqual(status(alarm), FireAlarmStatus.UNPOWERED, "the unpowered alarm's model");
            h.assertFalse(alarm.triggered(), "the unpowered alarm tripped");
            h.assertValueEqual(c.danger(), 0, "the controller with an unpowered alarm");
        });
    }
}
