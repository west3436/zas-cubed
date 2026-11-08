package io.github.west3436.zas.ponder.scene;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import java.util.List;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gas, shown without the simulation, which doesn't run in a scene's world: puffs where gas leaves a
 * device, a line chasing along the pipes it moves through, and readings in the player's units.
 */
public final class Gas {
    /** The colour of moving gas in a scene. */
    public static final PonderPalette FLOW = PonderPalette.INPUT;

    private Gas() {}

    /**
     * Puffs of gas leaving a block's face, like a running vent's (CLIENT-019).
     *
     * @param scene the scene
     * @param util the scene's helpers
     * @param pos the block
     * @param face the face the gas leaves by
     * @param cycles how many puffs, about one every two ticks
     */
    public static void puff(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Direction face, int cycles) {
        Vec3 out = Vec3.atLowerCornerOf(face.getNormal()).scale(0.08);
        scene.effects()
                .emitParticles(
                        util.vector().blockSurface(pos, face),
                        scene.effects().simpleParticleEmitter(ParticleTypes.POOF, out),
                        1,
                        cycles);
    }

    /**
     * An outline gliding through blocks one after another, again and again, as gas moves through
     * pipes. The scene waits while it runs, so show its line first with {@link Say#over}.
     *
     * @param scene the scene
     * @param ticks how long it runs
     * @param path the blocks in order, each next to the last
     */
    public static void flow(SceneBuilder scene, int ticks, List<BlockPos> path) {
        int done = 0;
        while (done < ticks) {
            // A new outline each pass, so it starts again at the first block, not sliding back to it.
            Object slot = new Object();
            for (int i = 0; i < path.size() && done < ticks; i++) {
                int stay = i == path.size() - 1 ? STEP + REST : STEP;
                scene.overlay().chaseBoundingBoxOutline(FLOW, slot, new AABB(path.get(i)).inflate(1 / 32d), stay);
                scene.idle(stay);
                done += stay;
            }
        }
    }

    /** The ticks {@link #flow} takes over each block. */
    public static final int STEP = 4;

    /** The ticks {@link #flow} rests on the last block before it starts again. */
    private static final int REST = 10;

    /** A pressure in kPa, in the player's units (CLIENT-006), for a line's {@code %s}. */
    public static String pressure(double kpa) {
        return Readout.pressure(kpa);
    }
}
