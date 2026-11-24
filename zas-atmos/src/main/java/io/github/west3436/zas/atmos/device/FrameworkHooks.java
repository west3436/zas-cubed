package io.github.west3436.zas.atmos.device;

import io.github.west3436.ss13.api.device.BlockMover;
import io.github.west3436.ss13.api.device.BlockMovers;
import io.github.west3436.ss13.client.tgui.Readout;
import io.github.west3436.zas.api.client.DisplayUnits;
import io.github.west3436.zas.api.relocation.Relocations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What SS13 Framework's device base and window kit ask ZAS³: whether a block is being moved and where
 * the world shows it, from {@link Relocations}, and the units a reading is shown in, from {@link
 * DisplayUnits}.
 */
public final class FrameworkHooks {
    /** Answers the device base from {@link Relocations}, so a device on a moving ship waits (CONT-157). */
    private static final class RelocationMover implements BlockMover {
        @Override
        public boolean isMoving(ServerLevel level, BlockPos pos) {
            return Relocations.isMoving(level, pos);
        }

        @Override
        public Vec3 shownAt(Level level, Vec3 point) {
            return Relocations.shownAt(level, point);
        }
    }

    /** The player's units (CLIENT-006), for the windows' and tooltips' readings. */
    private static final class PlayerUnits implements Readout.Units {
        @Override
        public double pressure(double kpa) {
            return DisplayUnits.pressure(kpa);
        }

        @Override
        public String pressureSymbol() {
            return DisplayUnits.pressureSymbol();
        }

        @Override
        public double temperature(double kelvin) {
            return DisplayUnits.temperature(kelvin);
        }

        @Override
        public String temperatureSymbol() {
            return DisplayUnits.temperatureSymbol();
        }
    }

    private FrameworkHooks() {}

    /**
     * The units {@link #register} installs: {@link DisplayUnits}', whatever they are when asked. A
     * unit test that reads through {@link Readout} without the mod loaded installs these itself.
     *
     * @return the units
     */
    public static Readout.Units playerUnits() {
        return new PlayerUnits();
    }

    /** Installs both, on either side, whether the content is on or not. Called once, from the mod constructor. */
    public static void register() {
        BlockMovers.register(ZasAtmosMod.id("relocations"), new RelocationMover());
        Readout.useUnits(playerUnits());
    }
}
