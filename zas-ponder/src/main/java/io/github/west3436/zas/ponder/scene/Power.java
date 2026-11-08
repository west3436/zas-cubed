package io.github.west3436.zas.ponder.scene;

import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What powers a device: an area air controller in its room, or one a multitool links it to (D-68,
 * CONT-068, CONT-069). A part has no power of its own, and a player can't see this from the block, so
 * every powered device's scene shows it (CLIENT-026).
 */
public final class Power {
    private Power() {}

    /**
     * Puts up a station wall with an area air controller on it, turns the device on, and says the
     * controller powers it.
     *
     * @param scene the scene
     * @param util the scene's helpers
     * @param controller where the controller goes, in the air in front of the wall
     * @param facing the way its panel faces; the wall is behind it, from the floor up
     * @param device the device it powers
     * @param on the device's state once it's powered, from its state before, such as {@code ON} set
     */
    public static void controller(
            SceneBuilder scene,
            SceneBuildingUtil util,
            BlockPos controller,
            Direction facing,
            BlockPos device,
            UnaryOperator<BlockState> on) {
        BlockPos behind = controller.relative(facing.getOpposite());
        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (int y = 1; y <= behind.getY(); y++) {
            wall.put(behind.atY(y), AtmosBlocks.STATION_WALL.get().defaultBlockState());
        }
        Build.revealAll(scene, util, wall, Direction.DOWN);
        Build.reveal(
                scene,
                util,
                controller,
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, facing)
                        .setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE),
                facing.getOpposite());
        Build.change(scene, device, on);
        Say.at(scene, panel(util, controller, facing), "power.controller");
    }

    /**
     * The front of a panel on a wall, such as an area air controller's or a fire alarm's. The panel is
     * thin and sits at the back of its block, so its block's face is in the air in front of it.
     *
     * @param util the scene's helpers
     * @param pos the panel's block
     * @param facing the way the panel faces
     * @return a point on the panel's front
     */
    public static Vec3 panel(SceneBuildingUtil util, BlockPos pos, Direction facing) {
        Vec3 back = Vec3.atLowerCornerOf(facing.getOpposite().getNormal());
        return util.vector().centerOf(pos).add(0, 0.1, 0).add(back.scale(0.375));
    }

    /**
     * Shows a multitool used on the device, and says it links the device to a controller in another
     * room (CONT-042).
     *
     * @param scene the scene
     * @param util the scene's helpers
     * @param device the device
     */
    public static void multitool(SceneBuilder scene, SceneBuildingUtil util, BlockPos device) {
        Hand.use(scene, util, device, Direction.UP, new ItemStack(AtmosItems.MULTITOOL.get()));
        Say.at(scene, util.vector().topOf(device), "power.multitool");
    }
}
