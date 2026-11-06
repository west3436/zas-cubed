package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import io.github.west3436.zas.atmos.pump.VolumePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A real volume pump on real power (CONT-060, CONT-065, CONT-069). {@code PumpTests} covers the
 * pressure pump the same way. This has a batch of its own because it waits in real time for a lease
 * to run out.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VolumePumpPowerTests {
    /** A pipe, a cap and the port's buffer: the volume on each side of the pump. */
    private static final double RUN = 2 * PipeVolumes.PIPE + PipeVolumes.PORT;

    private VolumePumpPowerTests() {}

    /** Places a pipe with its connections worked out from its neighbours. */
    private static void pipe(Site s, int x, int y, int z) {
        BlockPos pos = s.abs(x, y, z);
        BlockState state =
                Block.updateFromNeighbourShapes(AtmosBlocks.PIPE.get().defaultBlockState(), s.level, pos);
        s.place(x, y, z, state, Object.class);
    }

    private static void cap(Site s, int x, int y, int z, Direction facing) {
        s.place(
                x,
                y,
                z,
                AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing),
                Object.class);
    }

    private static GasStore out(VolumePumpBlockEntity pump) {
        return pump.output().network().gas();
    }

    /** Burns a generator for some game ticks, as its ticker would. */
    private static void burn(GeneratorBlockEntity g, int ticks) {
        for (int i = 0; i < ticks; i++) {
            g.serverTick();
        }
    }

    /**
     * A real volume pump between two networks, under a real controller that a real generator powers:
     * with no controller it has no power, even on and beside a burning generator (CONT-069). Under the
     * controller it is in the gas-handling group (CONT-064), the controller pays 20 FE/t for it while
     * it is on, with nothing drawn while it is off (CONT-065), and it pumps. When the generator goes
     * and the buffer is spent, its lease runs out and it stops, still switched on (CONT-060).
     */
    @GameTest(template = "empty", batch = "zas_atmos_volume_pump_power", timeoutTicks = 400)
    public static void cont_060_a_real_volume_pump_stops_when_its_power_is_cut(GameTestHelper h) {
        Site s = new Site(h, 960);
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        // The test waits in real time, so its chunks must stay loaded.
        s.level.setChunkForced(cx, cz, true);
        s.level.setChunkForced(cx + 1, cz, true);
        Runnable cleanup = () -> {
            s.clear();
            s.level.setChunkForced(cx, cz, false);
            s.level.setChunkForced(cx + 1, cz, false);
        };
        VolumePumpBlockEntity pump;
        AreaAirControllerBlockEntity c;
        try {
            s.clear();
            // A 7 × 3 × 3 room, holding the pump's line and a burning generator beside it.
            s.room(0, 8);
            BlockState state = AtmosBlocks.VOLUME_PUMP
                    .get()
                    .defaultBlockState()
                    .setValue(InlineDeviceBlock.FACING, Direction.EAST);
            cap(s, 2, 2, 2, Direction.EAST);
            pipe(s, 3, 2, 2);
            pump = s.place(4, 2, 2, state, VolumePumpBlockEntity.class);
            pipe(s, 5, 2, 2);
            cap(s, 6, 2, 2, Direction.WEST);
            h.assertTrue(pump.input().network() != pump.output().network(), "the pump joined its two networks");
            GeneratorBlockEntity beside =
                    s.place(4, 2, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            beside.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            burn(beside, 20);
            h.assertTrue(beside.energy().getEnergyStored() > 0, "the generator holds no FE");
            s.ticks(2);
            s.settle(GasMutations.add(
                    s.level,
                    s.point(4, 3, 3),
                    new GasParcel(Map.of(StandardAir.NITROGEN, 1.0), StandardAir.TEMPERATURE),
                    Site.SOURCE));

            h.assertValueEqual(pump.power().priority(), PowerPriority.GAS_HANDLING, "the pump's group");
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.VOLUME_PUMP_DRAW), 20, "the pump's draw");
            h.assertValueEqual(pump.power().draw(), 0, "the draw of a pump that's off");
            h.assertValueEqual(pump.power().drawPerAtmosTick(), 0L, "the bill of a pump that's off");

            // Plenty of gas for the seconds below, and well short of a burst.
            pump.input()
                    .network()
                    .gas()
                    .add(new GasParcel(Map.of(StandardAir.NITROGEN, 300.0), StandardAir.TEMPERATURE));
            pump.setOn(true);
            s.close(pump.setting(), PumpFlow.MAX_RATE, 1e-9, "a new pump's rate");
            h.assertValueEqual(pump.power().draw(), 20, "the draw of a pump that's on");
            h.assertValueEqual(
                    pump.power().drawPerAtmosTick(), 20L * AtmosTicks.PERIOD, "the bill of a pump that's on");

            // No controller: no power, on and beside a burning generator.
            s.cycles(3, pump);
            h.assertFalse(pump.power().isPowered(), "the pump has power with no controller");
            h.assertFalse(pump.running(), "the pump runs with no controller");
            s.close(out(pump).totalMoles(), 0, 1e-12, "the output with no controller");

            // A controller in its room, with a generator below it that has burned enough. It covers
            // the pump by its zone, and pays for itself and the pump, 25 FE/t, for one atmos tick.
            GeneratorBlockEntity g =
                    s.place(1, 1, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            g.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            burn(g, 40);
            int fe = g.energy().getEnergyStored();
            c = s.controller(1);
            s.cycle(c, pump);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertTrue(pump.power().isPowered(), "the controller didn't power its pump");
            h.assertTrue(pump.power().source() == c, "the pump's power isn't the controller's");
            int left = g.energy().getEnergyStored() + c.buffer().getEnergyStored();
            h.assertValueEqual(fe - left, 25 * AtmosTicks.PERIOD, "the FE for the controller and the pump");

            // It pumps.
            h.assertTrue(pump.running(), "the powered pump isn't running");
            h.assertValueEqual(pump.status(), InlineStatus.MOVING, "the powered pump's status");
            h.assertTrue(
                    s.level.getBlockState(pump.getBlockPos()).getValue(InlineDeviceBlock.ON),
                    "the powered pump's block state");
            s.close(out(pump).totalMoles(), 300.0 * PumpFlow.MAX_RATE / RUN, 1e-9, "the powered pump's first second");
            s.cycles(2, c, pump);

            // CONT-060: cut the power. Both generators go, and the buffer is spent.
            s.set(1, 1, 1, Site.AIR);
            s.set(4, 2, 1, Site.AIR);
            c.buffer().set(0);
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        VolumePumpBlockEntity p = pump;
        AreaAirControllerBlockEntity controller = c;
        h.runAfterDelay(DevicePower.LEASE_TICKS + 2L * AtmosTicks.PERIOD, () -> {
            try {
                h.assertFalse(controller.powered(), "the controller has power with no generator");
                h.assertFalse(p.power().isPowered(), "the pump's lease outlived its controller's power");
                h.assertFalse(p.running(), "the unpowered pump runs");
                h.assertTrue(p.on(), "cutting the power switched the pump off");
                p.step();
                double held = out(p).totalMoles();
                h.assertTrue(held >= 1, "the pump moved nothing before its power was cut");
                h.assertValueEqual(p.status(), InlineStatus.OFF, "the unpowered pump's status");
                h.assertFalse(
                        s.level.getBlockState(p.getBlockPos()).getValue(InlineDeviceBlock.ON),
                        "the unpowered pump's block state");
                s.cycles(4, p);
                s.close(out(p).totalMoles(), held, 1e-12, "the output with the power cut");
            } finally {
                cleanup.run();
            }
            h.succeed();
        });
    }
}
