package io.github.west3436.zas.ponder.scene;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import java.util.ArrayList;
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
     * Puffs of gas drawn into a block's face, like a siphoning vent's or a scrubber's. They come in
     * low over the face from its four sides and meet over its middle, since puffs coming straight
     * down onto a face look like gas leaving it.
     *
     * @param scene the scene
     * @param util the scene's helpers
     * @param pos the block
     * @param face the face the gas goes in by
     * @param cycles how many puffs, about one a tick
     */
    public static void draw(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Direction face, int cycles) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        // The four ways across the face, toward its sides.
        List<Vec3> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (side.getAxis() != face.getAxis()) {
                sides.add(Vec3.atLowerCornerOf(side.getNormal()));
            }
        }
        scene.effects()
                .emitParticles(
                        util.vector().blockSurface(pos, face).add(normal.scale(DRAW_OVER)),
                        (level, x, y, z) -> {
                            // POOF rises as it fades, which looks like gas leaving, so this is CLOUD.
                            Vec3 side = sides.get(level.random.nextInt(sides.size()));
                            Vec3 spread = side.cross(normal).scale((level.random.nextDouble() - 0.5) * 0.6);
                            Vec3 at = side.scale(DRAW_FROM).add(spread).add(x, y, z);
                            Vec3 in = side.scale(-DRAW_SPEED).add(normal.scale(-DRAW_SPEED / 8));
                            level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, in.x, in.y, in.z);
                        },
                        1,
                        cycles);
    }

    /** How far over a face {@link #draw}'s puffs move, in blocks. */
    private static final double DRAW_OVER = 0.2;

    /** How far out from a face's middle {@link #draw} starts its puffs, in blocks. */
    private static final double DRAW_FROM = 0.75;

    /**
     * How fast {@link #draw}'s puffs move in, in blocks a tick. A CLOUD slows by 4 % a tick, so one
     * that lasts travels 25 times this: to about the face's middle.
     */
    private static final double DRAW_SPEED = 0.03;

    /**
     * Outlines gliding through blocks one after another, again and again, as gas moves through pipes.
     * Several paths run side by side, such as a mixer's two inputs, each starting together. The scene
     * waits while they run, so show their line first with {@link Say#over}.
     *
     * @param scene the scene
     * @param ticks how long it runs
     * @param paths the blocks of each path in order, each next to the last
     */
    @SafeVarargs
    public static void flow(SceneBuilder scene, int ticks, List<BlockPos>... paths) {
        int longest = 0;
        for (List<BlockPos> path : paths) {
            longest = Math.max(longest, path.size());
        }
        int done = 0;
        while (done < ticks) {
            // New outlines each pass, so each starts again at its first block, not sliding back to it.
            Object[] slots = new Object[paths.length];
            for (int p = 0; p < paths.length; p++) {
                slots[p] = new Object();
            }
            for (int i = 0; i < longest && done < ticks; i++) {
                int stay = i == longest - 1 ? STEP + REST : STEP;
                for (int p = 0; p < paths.length; p++) {
                    List<BlockPos> path = paths[p];
                    // A shorter path waits on its last block for the longest.
                    BlockPos at = path.get(Math.min(i, path.size() - 1));
                    scene.overlay().chaseBoundingBoxOutline(FLOW, slots[p], new AABB(at).inflate(1 / 32d), stay);
                }
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
