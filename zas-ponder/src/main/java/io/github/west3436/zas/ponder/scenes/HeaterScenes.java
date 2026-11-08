package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.portable.PortableBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.ponder.scene.Build;
import io.github.west3436.zas.ponder.scene.Hand;
import io.github.west3436.zas.ponder.scene.Say;
import io.github.west3436.zas.ponder.scene.Stage;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The heater and the air conditioner (CONT-019, D-73): portables on a battery that hold the room they
 * stand in at a set temperature, with no pipe and no controller.
 */
public final class HeaterScenes {
    private HeaterScenes() {}

    /** A heater warms its room to its set temperature. */
    public static void heater(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "heater");
        portable(scene, util, AtmosBlocks.HEATER.get(), "heater.heats");
    }

    /** An air conditioner cools its room to its set temperature. */
    public static void airConditioner(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "air_conditioner");
        portable(scene, util, AtmosBlocks.AIR_CONDITIONER.get(), "air_conditioner.cools");
    }

    /**
     * A portable dropped on the floor: its window sets its temperature and takes its battery, then it
     * works until its room reaches that temperature.
     *
     * @param works the line saying what it does to its room
     */
    private static void portable(SceneBuilder scene, SceneBuildingUtil util, Block block, String works) {
        BlockPos portable = util.grid().at(1, 1, 1);
        Build.drop(scene, util, portable, block.defaultBlockState().setValue(PortableBlock.FACING, Direction.NORTH));

        Hand.use(scene, util, portable, Direction.UP, ItemStack.EMPTY);
        Say.at(scene, util.vector().topOf(portable), "portable.setpoint");

        Hand.show(scene, util, portable, Direction.UP, new ItemStack(AtmosItems.BATTERY.get()));
        Say.at(scene, util.vector().topOf(portable), "portable.battery");

        Build.change(scene, portable, s -> s.setValue(PortableBlock.LIT, true));
        Say.at(scene, util.vector().blockSurface(portable, Direction.NORTH), works);

        Build.change(scene, portable, s -> s.setValue(PortableBlock.LIT, false));
        Say.at(scene, util.vector().topOf(portable), "portable.stops");
        scene.markAsFinished();
    }
}
