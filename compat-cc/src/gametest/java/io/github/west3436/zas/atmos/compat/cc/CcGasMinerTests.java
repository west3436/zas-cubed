package io.github.west3436.zas.atmos.compat.cc;

import io.github.west3436.ss13.device.DeviceTicker;
import io.github.west3436.zas.atmos.miner.GasMinerBlock;
import io.github.west3436.zas.atmos.miner.GasMinerBlockEntity;
import io.github.west3436.zas.atmos.miner.GasMinerStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A creative gas miner to a computer (CONT-188): it reads and sets what the miner's window does, over
 * the window's ranges, and refuses a gas the server doesn't define. Its spots are 5990 and up.
 */
@GameTestHolder(CcComputerTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CcGasMinerTests {
    private static final String BATCH = "zas_cc_gas_miner";

    private CcGasMinerTests() {}

    /**
     * CONT-188: a computer switches a miner on, sets its gas, target and temperature, and reads its
     * state and its room's pressure; the miner fills its room to the target the computer set. A target
     * or temperature beyond the window's range, and an unknown gas, are refused and change nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_188_a_computer_sets_a_gas_miner_as_its_window_does(GameTestHelper h) {
        Spot s = new Spot(h, 5990);
        s.clear();
        s.room(0);
        BlockState state =
                AtmosBlocks.GAS_MINER.get().defaultBlockState().setValue(GasMinerBlock.FACING, Direction.EAST);
        GasMinerBlockEntity miner = s.place(0, 2, 2, state, GasMinerBlockEntity.class);
        GasMinerPeripheral p = s.peripheral(0, 2, 2, GasMinerPeripheral.class);

        s.allowed(
                () -> {
                    h.assertFalse(p.isOn(), "a new miner is on");
                    h.assertValueEqual(p.getState(), "off", "a new miner's state");
                    h.assertTrue(p.getRoomPressure() == null, "an off miner read its room");
                    List<String> gases = p.getGases();
                    h.assertTrue(gases.contains(Spot.N2.toString()), "its gases: " + gases);
                    h.assertValueEqual(p.getGas(), GasMinerBlockEntity.DEFAULT_GAS.toString(), "its gas");
                    p.setGas(Spot.N2.toString());
                    p.setTargetPressure(180);
                    p.setTemperature(310);
                },
                "setting it");
        h.assertValueEqual(miner.gas(), Spot.N2, "the gas a computer set");
        s.close(miner.target(), 180, "the target a computer set");
        s.close(miner.temperature(), 310, "the temperature a computer set");

        h.assertTrue(
                s.refused(() -> p.setGas("zas:no_such_gas"), "an unknown gas").contains("no such gas"), "error");
        s.refused(() -> p.setGas("Not A Gas!"), "a name that is no id");
        h.assertValueEqual(miner.gas(), Spot.N2, "its gas after the refusals");
        double most = GasMinerBlockEntity.maxTarget();
        s.refused(() -> p.setTargetPressure(-1), "a target below 0");
        s.refused(() -> p.setTargetPressure(most + 1), "a target past its highest");
        s.refused(() -> p.setTargetPressure(Double.NaN), "a target of NaN");
        s.allowed(() -> p.setTargetPressure(most), "its highest target");
        s.allowed(() -> p.setTargetPressure(0), "its lowest target");
        s.refused(() -> p.setTemperature(GasMinerBlockEntity.MIN_TEMPERATURE - 1), "a temperature below its lowest");
        s.refused(() -> p.setTemperature(GasMinerBlockEntity.MAX_TEMPERATURE + 1), "a temperature past its highest");
        s.close(miner.temperature(), 310, "its temperature after the refusals");

        s.allowed(
                () -> {
                    p.setTargetPressure(180);
                    p.setOn(true);
                },
                "switching it on");
        h.assertTrue(miner.isOn(), "a computer didn't switch it on");
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < 60 && miner.status() != GasMinerStatus.AT_TARGET; i++) {
            DeviceTicker.stepNow(s.level);
            s.tick();
            if (i < 8) {
                trace.append(miner.status())
                        .append('@')
                        .append(miner.roomPressure())
                        .append(' ');
            }
        }
        h.assertValueEqual(miner.status(), GasMinerStatus.AT_TARGET, "its status after " + trace);
        s.allowed(
                () -> {
                    h.assertValueEqual(p.getState(), "at_target", "its state");
                    Double room = p.getRoomPressure();
                    // Its gas is warmer than the room, so the room settles a little under the target.
                    h.assertTrue(
                            room != null && Math.abs(room - 180) <= 180 * GasMinerBlockEntity.AT_TARGET_SHARE,
                            "its room's pressure: " + room);
                },
                "reading it");
        s.clear();
        h.succeed();
    }
}
