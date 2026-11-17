package io.github.west3436.zas.compat.pneumaticcraft.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.compat.pneumaticcraft.AirUnits;
import io.github.west3436.zas.compat.pneumaticcraft.PncLeaks;
import io.github.west3436.zas.compat.pneumaticcraft.PncSettings;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Air that a PneumaticCraft machine leaks into a sealed room fills the room (COMPAT-012). The
 * machine is PneumaticCraft's own Air Compressor, with no fuel and nothing connected to it, so it
 * leaks from its front and its air changes by its leak alone; the game ticks it, and the adapter
 * finds the leak, counts it and sends it as it does in play. Its own batch, since it leaves the
 * game to run for a while.
 */
@GameTestHolder(PncGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class PncLeakTests {
    private static final String BATCH = "zas_pneumaticcraft_leaks";

    /** The pressure the compressor is set to, in bar: below its danger pressure of 5. */
    private static final float PRESSURE = 4f;

    /** How long the counted leak runs, in game ticks. */
    private static final int LEAKING = 30;

    /** How long after the leak stops its air has surely landed: two atmos ticks and a margin. */
    private static int landing() {
        return 2 * Atmospheres.periodTicks() + 10;
    }

    private PncLeakTests() {}

    /**
     * An Air Compressor at 4 bar on the floor of a sealed room leaks from its front into the room.
     * From the atmos tick at which the adapter finds the leak, every mL the compressor loses reaches
     * the room as standard air at the server's moles a mL: the room's gas has grown by exactly that
     * once the leak is stopped and its last air has landed.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void compat_012_a_leak_fills_the_room(GameTestHelper h) {
        Area area = new Area(h, 30);
        area.room();
        area.place(2, 1, 2, "air_compressor");
        BlockPos pos = area.abs(2, 1, 2);
        area.form(2, 2, 2);
        area.pin(2, 2, 2);
        area.keepTicking();

        // The compressor's air at the tick its leak was found, the room's moles then, and when.
        double[] found = {Double.NaN, Double.NaN};
        long[] when = {-1, -1};
        int[] airAtStop = {-1};
        h.onEachTick(() -> {
            try {
                IAirHandlerMachine air = area.level.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE, pos, null);
                if (air == null) {
                    throw new GameTestAssertException("the compressor has no air handler");
                }
                long now = area.level.getGameTime();
                if (when[0] < 0 && air.getAir() == 0 && h.getTick() >= 3) {
                    // Loaded and ticking: give it its air.
                    air.addAir((int) (PRESSURE * air.getVolume()));
                    h.assertTrue(air.getPressure() > 3.9f, "the compressor's pressure: " + air.getPressure());
                    when[0] = 0;
                } else if (when[0] == 0 && PncLeaks.counting(area.level, air)) {
                    Direction side = air.getSideLeaking();
                    h.assertTrue(side != null && side.getAxis().isHorizontal(), "the compressor leaks to " + side);
                    h.assertValueEqual(PncLeaks.leaked(area.level, air), 0L, "what was counted as it was found");
                    found[0] = air.getAir();
                    found[1] = area.total(2, 2, 2);
                    when[0] = now;
                } else if (when[0] > 0 && when[1] < 0 && now >= when[0] + LEAKING) {
                    // Stop the leak: with no air, the compressor leaks nothing.
                    airAtStop[0] = air.getAir();
                    h.assertTrue(airAtStop[0] > 0, "the compressor ran out of air while leaking");
                    air.addAir(-airAtStop[0]);
                    when[1] = now;
                } else if (when[1] > 0 && now >= when[1] + landing()) {
                    double leaked = found[0] - airAtStop[0];
                    h.assertTrue(leaked > LEAKING * 100, "the compressor leaked only " + leaked + " mL");
                    double expected = found[1] + leaked * PncSettings.molesPerMillilitre();
                    double total = area.total(2, 2, 2);
                    h.assertTrue(
                            Math.abs(total - expected) <= 1e-6,
                            "the room's moles: expected " + expected + " (" + found[1] + " and " + leaked + " mL), got "
                                    + total);
                    h.assertValueEqual(PncLeaks.leaked(area.level, air), 0L, "what is still counted");
                    area.clear();
                    h.succeed();
                }
            } catch (RuntimeException e) {
                area.clear();
                throw e;
            }
        });
    }

    /** The moles one mL of PneumaticCraft's air is by default: 1 mL at 1 bar and 293.15 K. */
    @GameTest(template = "empty")
    public static void compat_012_a_millilitre_is_a_millilitre_at_one_bar(GameTestHelper h) {
        double expected = 100.0 * 0.001 / (8.314462618 * 293.15);
        h.assertTrue(
                Math.abs(AirUnits.DEFAULT_MOLES_PER_ML - expected) <= 1e-15,
                "the default moles a mL: " + AirUnits.DEFAULT_MOLES_PER_ML);
        h.assertTrue(
                Math.abs(PncSettings.molesPerMillilitre() - expected) <= 1e-15,
                "the server's moles a mL: " + PncSettings.molesPerMillilitre());
        h.succeed();
    }
}
