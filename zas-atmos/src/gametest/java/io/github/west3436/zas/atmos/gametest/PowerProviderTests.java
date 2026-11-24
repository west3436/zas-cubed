package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.ss13.api.area.AreaStore;
import io.github.west3436.ss13.api.area.Areas;
import io.github.west3436.ss13.api.power.AreaPower;
import io.github.west3436.ss13.api.power.PowerFeed;
import io.github.west3436.ss13.api.power.PowerProvider;
import io.github.west3436.ss13.device.DeviceTicks;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The area air controller as a power provider under SS13 Framework's contract (#539), with a
 * stand-in for NT Engineering's APC ({@link TestApc}). Like {@link PowerChargeTests}, each test builds
 * its room and forms its zone within one game tick, then lets the game step the devices in real time
 * and reads the stores at every game tick with a {@link ChargeLog}.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerProviderTests {
    private static final String BATCH = "zas_atmos_power_providers";

    /** Game ticks: the test waits for nothing but the clock, about twenty atmos ticks. */
    private static final int TIMEOUT = 800;

    /**
     * How long a test waits after it changes who powers what. A provider that takes a device over in
     * a step another has paid for finds it {@link PowerFeed.Outcome#PAID_ALREADY} and pays from its
     * next step on, so the change shows by its second step: up to three periods after the change,
     * wherever its steps fall.
     */
    private static final int SETTLE = 3 * DeviceTicks.PERIOD;

    /** The atmos ticks each phase watches. */
    private static final int WATCH = 3;

    /** Where the stand-in APC stands, outside the room: its area is what it powers, not where it is. */
    private static final int[] APC_AT = {8, 1, 2};

    private PowerProviderTests() {}

    private static int ventBill() {
        return AtmosServerConfig.get(AtmosServerConfig.VENT_DRAW) * DeviceTicks.PERIOD;
    }

    /**
     * The Phase B gate (#539): a stand-in APC, of {@link PowerProvider#MAINS} precedence, takes over
     * powering a ZAS³ vent its area air controller powers and controls. While the APC holds it, the
     * APC pays for the vent and the controller pays only its own draw, goes on ordering the vent, lists
     * it as powered by another, and leaves it out of what it asks its central controller for. When
     * the APC is removed, the controller powers the vent again at its next step. The vent has power
     * at every game tick throughout, and no step of it is paid for twice.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void an_apc_takes_over_a_vent_its_controller_still_controls(GameTestHelper h) {
        Site s = PowerChargeTests.site(h, 3200);
        ResourceLocation room = ZasAtmosMod.id("power_provider_test_room");
        AreaStore areas = Areas.store(s.level);
        s.room(0, 4);
        AreaAirControllerBlockEntity c = s.controller(2);
        VentBlockEntity vent = s.place(
                2,
                1,
                2,
                AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.UP),
                VentBlockEntity.class);
        c.buffer().set(10_000);
        PowerChargeTests.formZone(h, s, c, vent);
        areas.remove(room);
        areas.define(room, "Power provider test room");
        areas.assign(room, List.of(vent.getBlockPos(), s.abs(APC_AT[0], APC_AT[1], APC_AT[2])));

        int own = PowerChargeTests.own();
        int[] without = new int[1];
        Runnable eachTick = () -> {
            if (!vent.power().isPowered()) {
                without[0]++;
            }
        };
        TestApc.Apc[] apc = new TestApc.Apc[1];
        ChargeLog before = new ChargeLog(
                h, "the controller's buffer before the APC", () -> c.buffer().getEnergyStored());
        ChargeLog during = new ChargeLog(
                h, "the controller's buffer under the APC", () -> c.buffer().getEnergyStored());
        ChargeLog apcLog = new ChargeLog(h, "the APC's buffer", () -> apc[0].store.getEnergyStored());
        ChargeLog after = new ChargeLog(
                h, "the controller's buffer after the APC", () -> c.buffer().getEnergyStored());

        GameTestSequence seq = h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            h.assertTrue(vent.power().source() == c, "the controller doesn't power the vent");
            h.assertTrue(vent.power().claimant() == c, "the controller doesn't hold the vent");
            h.assertValueEqual(vent.controller(), Optional.of(c.getBlockPos()), "the vent's controller");
            h.assertTrue(vent.power().draw() > 0, "the vent the controller ordered on draws nothing");
        });
        ChargeLog.watch(seq, h, WATCH, eachTick, before);
        seq.thenExecute(() -> {
                    s.set(APC_AT[0], APC_AT[1], APC_AT[2], TestApc.block().defaultBlockState());
                    apc[0] = s.entity(APC_AT[0], APC_AT[1], APC_AT[2], TestApc.Apc.class);
                    apc[0].area = room;
                })
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    h.assertTrue(
                            vent.power().source() == apc[0],
                            "the APC didn't take over the vent: " + vent.power().source() + ", "
                                    + apc[0].outcome(vent));
                    h.assertTrue(vent.power().claimant() == apc[0], "the APC doesn't hold the vent");
                    h.assertTrue(
                            AreaPower.provider(s.level, room).orElse(null) == apc[0], "the APC doesn't hold its area");
                    h.assertValueEqual(
                            apc[0].outcome(vent), PowerFeed.Outcome.SUPPLIED, "the APC's outcome for the vent");
                    h.assertValueEqual(vent.controller(), Optional.of(c.getBlockPos()), "the vent's controller");
                    AreaAirControllerBlockEntity.Part listed = c.parts().get(0);
                    h.assertTrue(
                            listed.pos().equals(vent.getBlockPos()) && listed.byAnother() && listed.powered(),
                            "the controller doesn't list the vent as powered by another: " + listed);
                    h.assertValueEqual(c.demand(), (long) own, "what the controller asks its central controller for");
                });
        ChargeLog.watch(seq, h, WATCH, eachTick, during, apcLog);
        seq.thenExecute(() -> {
                    h.assertValueEqual(vent.controller(), Optional.of(c.getBlockPos()), "the vent's controller");
                    h.assertTrue(vent.controlled(), "the vent isn't controlled while the APC powers it");
                    s.set(APC_AT[0], APC_AT[1], APC_AT[2], Blocks.AIR.defaultBlockState());
                    h.assertTrue(vent.power().claimant() == null, "a removed APC still holds the vent");
                    h.assertTrue(AreaPower.provider(s.level, room).isEmpty(), "a removed APC still holds its area");
                })
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    h.assertTrue(
                            vent.power().source() == c,
                            "the vent didn't fall back to its controller: "
                                    + vent.power().source());
                    h.assertTrue(vent.power().claimant() == c, "the controller doesn't hold the vent again");
                    AreaAirControllerBlockEntity.Part listed = c.parts().get(0);
                    h.assertTrue(
                            !listed.byAnother() && listed.powered(),
                            "the controller doesn't list the vent as its own: " + listed);
                    h.assertValueEqual(c.demand(), (long) own + ventBill(), "what the controller asks for again");
                });
        ChargeLog.watch(seq, h, WATCH, eachTick, after);
        PowerChargeTests.finish(seq, s, () -> {
            areas.remove(room);
            before.assertChargedEach(own + ventBill(), WATCH);
            during.assertChargedEach(own, WATCH);
            apcLog.assertChargedEach(ventBill(), WATCH);
            after.assertChargedEach(own + ventBill(), WATCH);
            h.assertValueEqual(without[0], 0, "game ticks the vent went without power");
        });
    }
}
