package io.github.west3436.zas.atmos.particle;

import io.github.west3436.ss13.device.DeviceActivity;
import io.github.west3436.zas.api.relocation.Relocations;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.vent.VentMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What a client shows for a vent or a scrubber (CLIENT-019), from what the server already tells it: how
 * hard it is working, in the device activity table (DATA-018), and what it is doing, in its block state.
 * So these particles need no sync of their own.
 *
 * <p>Nothing shows below {@link #MIN_LEVEL}, so a trickle stays quiet. A vent's way follows its mode,
 * since a vent can siphon as well as blow. A scrubber only ever draws gas in.
 */
public final class DeviceJets {
    /**
     * How far out of a block's middle its particles start, in blocks: just in front of its grille, which
     * is the whole face of a full block.
     */
    public static final double MOUTH = 0.55;

    /**
     * The lowest activity level that shows. A vent's or scrubber's loop starts here too, so its air is
     * heard only while it is seen.
     */
    public static final int MIN_LEVEL = DeviceActivity.WORKING;

    private DeviceJets() {}

    /**
     * Where a device's jet starts, and which way it points, where the world shows the device: on a
     * Sable ship, at the grille on the ship, the way the ship turns it (COMPAT-101, API-068). A device
     * where it is gives its mouth, {@link #MOUTH} out along its facing, and the facing itself.
     *
     * @param mouth where the jet starts
     * @param way the facing as the world shows it, one block long
     */
    public record Placed(Vec3 mouth, Vec3 way) {}

    /**
     * Places a device's jet (see {@link Placed}).
     *
     * @param level the device's level, on its own thread
     * @param pos its block, where the level keeps it
     * @param facing the way its grille faces in the level
     */
    public static Placed place(Level level, BlockPos pos, Direction facing) {
        Vec3 centre = Vec3.atCenterOf(pos);
        Vec3 step = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vec3 mouth = Relocations.shownAt(level, centre.add(step.scale(MOUTH)));
        Vec3 shownCentre = Relocations.shownAt(level, centre);
        Vec3 way = shownCentre.equals(centre)
                ? step
                : Relocations.shownAt(level, centre.add(step)).subtract(shownCentre);
        return new Placed(mouth, way);
    }

    /**
     * The amount of a vent's {@link GasJetKind#DEVICE} jet.
     *
     * @param mode what its block state says it is doing
     * @param level its activity level
     * @return its level, positive blowing out and negative drawing in; 0 for no jet
     */
    public static double vent(VentMode mode, int level) {
        if (level < MIN_LEVEL) {
            return 0;
        }
        return switch (mode) {
            case OFF -> 0;
            case BLOW -> level;
            case SIPHON -> -level;
        };
    }

    /**
     * The amount of a scrubber's {@link GasJetKind#DEVICE} jet.
     *
     * @param mode what its block state says it is doing
     * @param level its activity level
     * @return its level, negative as it draws in; 0 for no jet
     */
    public static double scrubber(ScrubberMode mode, int level) {
        if (level < MIN_LEVEL) {
            return 0;
        }
        return switch (mode) {
            case OFF -> 0;
            case SCRUB, SIPHON, PANIC -> -level;
        };
    }
}
