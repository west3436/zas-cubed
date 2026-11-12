package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.canister.CanisterBlock;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Gas;
import io.github.west3436.zas.atmos.compat.ponder.scene.Hand;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The canister (CONT-017, CONT-084, CONT-039). The connector port's scene shows one on a port. */
public final class CanisterScenes {
    private CanisterScenes() {}

    /** Its release valve, a tank filled from it, a dye, and keeping its gas when mined. */
    public static void canister(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "canister");

        BlockPos canister = util.grid().at(2, 1, 2);
        Direction valve = Direction.NORTH;
        Build.drop(
                scene,
                util,
                canister,
                AtmosBlocks.CANISTER.get().defaultBlockState().setValue(CanisterBlock.FACING, valve));

        Hand.use(scene, util, canister, valve, ItemStack.EMPTY);
        Say.at(scene, util.vector().blockSurface(canister, valve), "canister.use");

        // Its valve opens, and gas leaves by it while the line is up.
        Build.change(scene, canister, s -> s.setValue(CanisterBlock.OPEN, true));
        int releasing = Say.LINE + 20;
        Gas.puff(scene, util, canister, valve, releasing);
        Say.over(scene, releasing, util.vector().blockSurface(canister, valve), "canister.open");
        scene.idle(releasing);
        Build.change(scene, canister, s -> s.setValue(CanisterBlock.OPEN, false));
        scene.idle(10);

        Hand.use(scene, util, canister, Direction.UP, new ItemStack(AtmosItems.EMPTY_TANK.get()));
        Say.at(scene, util.vector().topOf(canister), "canister.tank");

        Hand.use(scene, util, canister, Direction.UP, new ItemStack(Items.BLUE_DYE));
        Build.change(scene, canister, s -> s.setValue(CanisterColor.PROPERTY, CanisterColor.BLUE));
        Say.at(scene, util.vector().topOf(canister), "canister.dye");

        Say.at(scene, util.vector().topOf(canister), "canister.mined");
        scene.markAsFinished();
    }
}
