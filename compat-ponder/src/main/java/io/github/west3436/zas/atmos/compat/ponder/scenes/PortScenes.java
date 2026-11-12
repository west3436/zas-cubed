package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.canister.CanisterBlock;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Gas;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** The connector port (CONT-083). */
public final class PortScenes {
    private PortScenes() {}

    /** A pipe joins the port from below, and a canister stands on it to join the pipes. */
    public static void connectorPort(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "connector_port");

        BlockPos port = util.grid().at(2, 1, 1);
        BlockPos canister = port.above();
        BlockPos vent = util.grid().at(2, 0, 3);
        BlockPos dip = util.grid().at(2, 0, 2);
        List<BlockPos> run = List.of(port.below(), dip, dip.below(), vent.below());

        // The pipe runs in the floor, then dips under the vent's place in it.
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState().setValue(PipeColor.PROPERTY, PipeColor.BLUE);
        Build.place(scene, port.below(), pipe);
        Build.place(scene, dip, pipe);
        Map<BlockPos, BlockState> under = new LinkedHashMap<>();
        under.put(dip.below(), pipe);
        under.put(vent.below(), pipe);
        Build.under(scene, util, under);
        scene.idle(Build.SHOW);

        Build.drop(scene, util, port, AtmosBlocks.CONNECTOR_PORT.get().defaultBlockState());
        Build.Lifted lifted = Build.lift(scene, util, port, Direction.UP);
        scene.overlay().showOutline(PonderPalette.GREEN, "below", util.select().position(port.below()), Say.LINE);
        Say.at(scene, util.vector().topOf(port.below()), "connector_port.pipe_below");
        Build.lower(scene, lifted);

        Build.drop(
                scene,
                util,
                canister,
                AtmosBlocks.CANISTER
                        .get()
                        .defaultBlockState()
                        .setValue(CanisterBlock.FACING, Direction.NORTH)
                        .setValue(CanisterColor.PROPERTY, CanisterColor.BLUE));
        Say.at(scene, util.vector().topOf(port), "connector_port.canister_on_top");

        // A vent in the floor at the pipe's other end, powered by an area air controller on the wall
        // beside it.
        Build.sink(
                scene, util, vent, AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.UP));
        BlockState wall = AtmosBlocks.STATION_WALL.get().defaultBlockState();
        for (BlockPos pos :
                BlockPos.betweenClosed(util.grid().at(1, 1, 4), util.grid().at(3, 2, 4))) {
            Build.place(scene, pos.immutable(), wall);
        }
        scene.world().showSection(util.select().fromTo(1, 1, 4, 3, 2, 4), Direction.DOWN);
        scene.idle(Build.SHOW);
        // Off to one side, so the vent's puffs don't hide it.
        BlockPos controller = util.grid().at(1, 2, 3);
        Build.reveal(
                scene,
                util,
                controller,
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, Direction.NORTH)
                        .setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE),
                Direction.SOUTH);
        Say.at(scene, util.vector().blockSurface(controller, Direction.NORTH), "connector_port.vent_power");

        // Gas leaves the canister, runs through the pipes and out of the vent while its line is up.
        Say.at(scene, util.vector().topOf(canister), "reading.oxygen", Gas.pressure(1013.25));
        int flowing = Say.LINE + 40;
        scene.world().modifyBlock(vent, s -> s.setValue(VentMode.PROPERTY, VentMode.BLOW), false);
        Gas.puff(scene, util, vent, Direction.UP, flowing);
        Say.over(scene, flowing, util.vector().topOf(vent), "connector_port.gas_flows");
        Gas.flow(scene, flowing, List.of(canister, port, run.get(0), run.get(1), run.get(2), run.get(3), vent));
        scene.markAsFinished();
    }
}
