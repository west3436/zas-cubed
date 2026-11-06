package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentSettings;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Devices in an open cell with atmos ticks driven within one game tick. What they move to or from the
 * ambient finishes in the atmos tick that runs it, so they move gas at every step, in chunks the
 * worker hasn't been told of. Each move used to hold a device for six atmos ticks while its cell was
 * classified (#158); GEO-021 ended that.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OpenCellTests {
    private static final String BATCH = "zas_atmos_open_cells";

    private OpenCellTests() {}

    /** Steps a new device through its wait for its cell to settle, up to its first move of gas. */
    private static void settle(Area a) {
        for (int i = 0; i < CellWatch.SETTLE_STEPS; i++) {
            a.step();
        }
    }

    /** CONT-014: a blowing vent's network falls by the vent's share at each step, and no zone forms. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_014_a_vent_in_an_open_cell_blows_at_every_step(GameTestHelper h) {
        Area a = new Area(h, 2600);
        a.clear(0, 0, 4, 4);
        a.place(2, 1, 2, GasDeviceTests.vent(Direction.NORTH));
        a.place(2, 1, 3, GasDeviceTests.pipe());
        a.place(2, 1, 4, AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.NORTH));
        a.tick();
        VentBlockEntity vent = a.entity(2, 1, 2);
        vent.power().setTestPowered(true);
        PipeNetwork net = vent.network();
        net.gas().add(new GasParcel(Map.of(N2, 100.0), T));
        vent.command(VentSettings.blow(500));
        settle(a);
        double keep = 1 - 0.5 * PipeVolumes.VENT / net.volume();
        for (int step = 1; step <= 3; step++) {
            a.step();
            a.close(net.gas().totalMoles(), 100.0 * Math.pow(keep, step), "the network's moles after step " + step);
            h.assertTrue(Atmospheres.zoneAt(a.level, a.abs(2, 1, 1)).isEmpty(), "venting formed a zone");
        }
        h.succeed();
    }

    /** CONT-015: a siphoning scrubber's network gains gas at each step, and no zone forms. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_015_a_scrubber_in_an_open_cell_takes_at_every_step(GameTestHelper h) {
        Area a = new Area(h, 2601);
        a.clear(0, 0, 4, 4);
        a.place(2, 1, 2, ScrubberTests.scrubber(Direction.NORTH));
        ScrubberBlockEntity s = a.entity(2, 1, 2);
        s.power().setTestPowered(true);
        s.command(ScrubberMode.SIPHON, GasSelection.DEFAULT);
        settle(a);
        double held = s.network().gas().totalMoles();
        h.assertValueEqual(held, 0.0, "the network before the scrubber's first step");
        for (int step = 1; step <= 3; step++) {
            a.step();
            double now = s.network().gas().totalMoles();
            h.assertTrue(now > held, "the scrubber took nothing at step " + step);
            h.assertTrue(Atmospheres.zoneAt(a.level, a.abs(2, 1, 1)).isEmpty(), "scrubbing formed a zone");
            held = now;
        }
        h.succeed();
    }
}
