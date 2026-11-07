package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.battery.RechargerBlock;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlock;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlockEntity;
import io.github.west3436.zas.atmos.firealarm.FireAlarmStatus;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The parts an area air controller powers whose power cut had no test of its own: the igniter, the
 * recharger and the fire alarm (CONT-060). The vent, scrubber, pumps, filter, mixer and electrolyzer
 * have theirs beside their other tests. This has a batch of its own because it waits in real time
 * for a lease to run out.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerCutTests {
    private PowerCutTests() {}

    /** Burns a generator for some game ticks, as its ticker would. */
    private static void burn(GeneratorBlockEntity g, int ticks) {
        for (int i = 0; i < ticks; i++) {
            g.serverTick();
        }
    }

    private static FireAlarmStatus status(FireAlarmBlockEntity alarm) {
        return alarm.getBlockState().getValue(FireAlarmBlock.STATUS);
    }

    /**
     * An igniter with a signal, a recharger with an empty battery and a fire alarm, in a room whose
     * controller a real generator powers: each works, the igniter sparking, the recharger charging
     * and the alarm watching. When the generator goes and the buffer is spent, their leases run out
     * and each stops: the igniter goes out though its signal stays, the recharger charges nothing
     * more, and the alarm shows unpowered (CONT-060).
     */
    @GameTest(template = "empty", batch = "zas_atmos_power_cut", timeoutTicks = 400)
    public static void cont_060_an_igniter_a_recharger_and_a_fire_alarm_stop_when_their_power_is_cut(GameTestHelper h) {
        Site s = new Site(h, 5060);
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        // The test waits in real time, so its chunks must stay loaded.
        s.level.setChunkForced(cx, cz, true);
        s.level.setChunkForced(cx + 1, cz, true);
        Runnable cleanup = () -> {
            BatteryTests.clear(s);
            s.level.setChunkForced(cx, cz, false);
            s.level.setChunkForced(cx + 1, cz, false);
        };
        AreaAirControllerBlockEntity c;
        IgniterBlockEntity igniter;
        RechargerBlockEntity recharger;
        FireAlarmBlockEntity alarm;
        try {
            BatteryTests.clear(s);
            // A 7 × 3 × 3 room. Its controller is on the north wall above a generator that has
            // burned to its full store, so it pays for every part from the generator's FE.
            s.room(0, 8);
            GeneratorBlockEntity g =
                    s.place(1, 1, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            burn(g, 200);
            s.settle(GasMutations.add(
                    s.level,
                    s.point(4, 2, 2),
                    new GasParcel(Map.of(StandardAir.NITROGEN, 1.0), StandardAir.TEMPERATURE),
                    Site.SOURCE));
            c = s.controller(1);

            igniter = s.place(3, 1, 2, AtmosBlocks.IGNITER.get().defaultBlockState(), IgniterBlockEntity.class);
            s.set(3, 1, 3, Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertTrue(igniter.signal(), "the igniter has no signal");
            recharger = s.place(
                    6,
                    1,
                    2,
                    AtmosBlocks.RECHARGER.get().defaultBlockState().setValue(RechargerBlock.FACING, Direction.NORTH),
                    RechargerBlockEntity.class);
            BatteryTests.swap(recharger.battery(), new ItemStack(AtmosItems.BATTERY.get()));
            BlockState alarmState =
                    AtmosBlocks.FIRE_ALARM.get().defaultBlockState().setValue(FireAlarmBlock.FACING, Direction.SOUTH);
            h.assertTrue(alarmState.canSurvive(s.level, s.abs(4, 2, 1)), "the alarm has no wall behind it");
            alarm = s.place(4, 2, 1, alarmState, FireAlarmBlockEntity.class);

            // The controller covers them by its zone, which takes a step to pin.
            for (int i = 0;
                    i < 4
                            && !(igniter.power().isPowered()
                                    && recharger.power().isPowered()
                                    && alarm.power().isPowered());
                    i++) {
                s.cycle(c, igniter, recharger, alarm);
            }
            s.cycle(c, igniter, recharger, alarm);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertTrue(igniter.power().source() == c, "the controller doesn't power the igniter");
            h.assertTrue(recharger.power().source() == c, "the controller doesn't power the recharger");
            h.assertTrue(alarm.power().source() == c, "the controller doesn't power the fire alarm");
            h.assertTrue(igniter.lit(), "the powered igniter doesn't spark");
            h.assertValueEqual(
                    recharger.status(), RechargerBlockEntity.Status.CHARGING, "the powered recharger's status");
            h.assertTrue(recharger.energy() > 0, "the powered recharger charged nothing");
            h.assertValueEqual(status(alarm), FireAlarmStatus.NORMAL, "the powered alarm's status");

            // CONT-060: cut the power. The generator goes, and the buffer is spent.
            s.set(1, 1, 1, Site.AIR);
            c.buffer().set(0);
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        AreaAirControllerBlockEntity controller = c;
        IgniterBlockEntity ig = igniter;
        RechargerBlockEntity r = recharger;
        FireAlarmBlockEntity fa = alarm;
        h.runAfterDelay(DevicePower.LEASE_TICKS + 2L * AtmosTicks.PERIOD, () -> {
            try {
                controller.step();
                h.assertFalse(controller.powered(), "the controller has power with no generator");
                h.assertValueEqual(controller.status(), ControllerStatus.UNPOWERED, "the controller's status");
                h.assertFalse(ig.power().isPowered(), "the igniter's lease outlived its controller's power");
                h.assertFalse(r.power().isPowered(), "the recharger's lease outlived its controller's power");
                h.assertFalse(fa.power().isPowered(), "the alarm's lease outlived its controller's power");

                ig.step();
                h.assertTrue(ig.signal(), "the igniter lost its signal");
                h.assertFalse(ig.lit(), "the unpowered igniter sparks");

                r.step();
                int held = r.energy();
                h.assertValueEqual(r.status(), RechargerBlockEntity.Status.UNPOWERED, "the unpowered recharger");
                r.step();
                h.assertValueEqual(r.energy(), held, "the battery in an unpowered recharger");

                fa.step();
                h.assertValueEqual(status(fa), FireAlarmStatus.UNPOWERED, "the unpowered alarm's status");
                h.assertFalse(fa.triggered(), "the unpowered alarm is triggered");
            } finally {
                cleanup.run();
            }
            h.succeed();
        });
    }
}
