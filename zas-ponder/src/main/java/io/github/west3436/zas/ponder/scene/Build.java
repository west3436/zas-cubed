package io.github.west3436.zas.ponder.scene;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a scene on its floor. A scene's world starts as a bare floor ({@link Stage}), so blocks are
 * placed out of sight and then shown. Ponder places blocks without telling their neighbours, so
 * {@link #settle} joins pipes, ports and the like to what's beside them, as the game does when a block
 * is placed.
 */
public final class Build {
    private Build() {}

    /**
     * Places a block out of sight, to be shown later. A block placed where one is already shown, such
     * as in the floor, shows at once.
     */
    public static void place(SceneBuilder scene, BlockPos pos, BlockState state) {
        scene.world().setBlock(pos, state, false);
    }

    /**
     * Places a block, joins everything to its neighbours, and shows it dropping in from above. It waits
     * until the block lands, since a block still coming in can't be {@link #lift lifted}.
     *
     * @param scene the scene
     * @param util the scene's helpers
     * @param pos where
     * @param state what
     */
    public static void drop(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, BlockState state) {
        reveal(scene, util, pos, state, Direction.DOWN);
    }

    /**
     * Places a block, joins everything to its neighbours, shows it coming in from one side, and waits
     * until it's in.
     *
     * @param from the way it comes from, such as {@link Direction#DOWN} to drop it from above
     */
    public static void reveal(
            SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, BlockState state, Direction from) {
        place(scene, pos, state);
        settle(scene);
        scene.world().showSection(util.select().position(pos), from);
        scene.idle(SHOW);
    }

    /** The ticks Ponder takes to show a section. */
    public static final int SHOW = 15;

    /**
     * Places several blocks, joins everything to its neighbours, shows them coming in together from
     * one side, and waits until they're in.
     *
     * @param scene the scene
     * @param util the scene's helpers
     * @param blocks where and what, in any order
     * @param from the way they come from, such as {@link Direction#DOWN} to drop them from above
     */
    public static void revealAll(
            SceneBuilder scene, SceneBuildingUtil util, Map<BlockPos, BlockState> blocks, Direction from) {
        Selection all = null;
        for (Map.Entry<BlockPos, BlockState> block : blocks.entrySet()) {
            place(scene, block.getKey(), block.getValue());
            Selection one = util.select().position(block.getKey());
            all = all == null ? one : all.add(one);
        }
        settle(scene);
        scene.world().showSection(all, from);
        scene.idle(SHOW);
    }

    /**
     * Changes a shown block's state, as a player's use, a wrench or a signal would, and joins
     * everything to its neighbours again: a valve opening, a device turning on, a part turning.
     *
     * @param scene the scene
     * @param pos the block
     * @param change what it becomes, from what it is
     */
    public static void change(SceneBuilder scene, BlockPos pos, UnaryOperator<BlockState> change) {
        scene.world().modifyBlock(pos, change, false);
        settle(scene);
    }

    /**
     * Changes several shown blocks' states at once, and joins everything to its neighbours again: both
     * halves of a two-block block, such as a firelock closing, which would each take back the other's
     * old state if changed one at a time.
     *
     * @param scene the scene
     * @param blocks the blocks
     * @param change what each becomes, from what it is
     */
    public static void change(SceneBuilder scene, Selection blocks, UnaryOperator<BlockState> change) {
        scene.world().modifyBlocks(blocks, change, false);
        settle(scene);
    }

    /**
     * Takes shown blocks away together, as a wrench or a player would, lifting them out of sight, and
     * joins their neighbours to what's left: a pipe beside a gap gets an open end.
     *
     * @param scene the scene
     * @param util the scene's helpers
     * @param positions the blocks
     */
    public static void remove(SceneBuilder scene, SceneBuildingUtil util, BlockPos... positions) {
        Selection all = null;
        for (BlockPos pos : positions) {
            Selection one = util.select().position(pos);
            all = all == null ? one : all.add(one);
        }
        scene.world().hideSection(all, Direction.UP);
        scene.idle(SHOW);
        for (BlockPos pos : positions) {
            place(scene, pos, Blocks.AIR.defaultBlockState());
        }
        settle(scene);
    }

    /**
     * Lifts a shown block a little way out of place, to show what joins it from below or behind, and
     * waits while it moves. {@link #lower} puts it back.
     *
     * @param scene the scene
     * @param util the scene's helpers
     * @param pos the block
     * @param away the way it moves, such as {@link Direction#UP}
     * @return the lifted block, for {@link #lower}
     */
    public static Lifted lift(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Direction away) {
        ElementLink<WorldSectionElement> link =
                scene.world().makeSectionIndependent(util.select().position(pos));
        Vec3 offset = Vec3.atLowerCornerOf(away.getNormal()).scale(LIFT);
        scene.world().moveSection(link, offset, MOVE);
        scene.idle(MOVE + 5);
        return new Lifted(link, offset);
    }

    /** Puts a {@link #lift lifted} block back, and waits while it moves. */
    public static void lower(SceneBuilder scene, Lifted lifted) {
        scene.world().moveSection(lifted.link(), lifted.offset().reverse(), MOVE);
        scene.idle(MOVE + 5);
    }

    /** A block {@link #lift} moved, and how far. */
    public record Lifted(ElementLink<WorldSectionElement> link, Vec3 offset) {}

    /** How far {@link #lift} moves a block, in blocks. */
    private static final double LIFT = 0.75;

    /** The ticks {@link #lift} and {@link #lower} take. */
    private static final int MOVE = 10;

    /**
     * Sets every block in the scene's world to the shape its neighbours give it, as the game does when
     * a neighbour changes: a pipe's arms, a port's collar, a wall's sides. It runs where it is in the
     * scene, so call it after placing and before showing.
     */
    public static void settle(SceneBuilder scene) {
        scene.addInstruction(ponder -> {
            PonderLevel level = ponder.getWorld();
            Map<BlockPos, BlockState> blocks = level.getBlockMap();
            for (BlockPos pos : new ArrayList<>(blocks.keySet())) {
                BlockState state = blocks.get(pos);
                BlockState settled = Block.updateFromNeighbourShapes(state, level, pos);
                if (settled != state) {
                    level.setBlock(pos, settled, 0);
                }
            }
            ponder.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw);
        });
    }
}
