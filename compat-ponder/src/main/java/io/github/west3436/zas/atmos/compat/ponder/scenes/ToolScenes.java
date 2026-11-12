package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.canister.CanisterBlock;
import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Gas;
import io.github.west3436.zas.atmos.compat.ponder.scene.Hand;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * The handheld tools, each used on the blocks it acts on: the multitool (CONT-042), the crowbar
 * (CONT-094), the fire extinguisher (CONT-037), the analyzer (CONT-034) and the wrench (CONT-035,
 * CONT-091).
 */
public final class ToolScenes {
    private ToolScenes() {}

    /** A multitool picks a controller, then links a vent in the next room to it, and unlinks it. */
    public static void multitool(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "multitool");

        // A low wall between two rooms, with a controller on this side and a vent on the far one.
        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (int z = 0; z <= 6; z++) {
            wall.put(util.grid().at(3, 1, z), AtmosBlocks.STATION_WALL.get().defaultBlockState());
        }
        Build.revealAll(scene, util, wall, Direction.DOWN);
        BlockPos controller = util.grid().at(2, 1, 3);
        Build.reveal(
                scene,
                util,
                controller,
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, Direction.WEST)
                        .setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE),
                Direction.EAST);
        BlockPos vent = util.grid().at(5, 0, 3);
        Build.sink(
                scene, util, vent, AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.UP));

        ItemStack multitool = new ItemStack(AtmosItems.MULTITOOL.get());
        Vec3 front = util.vector().blockSurface(controller, Direction.WEST);
        Hand.use(scene, util, controller, Direction.WEST, multitool);
        scene.overlay().showOutline(PonderPalette.GREEN, "picked", util.select().position(controller), 200);
        Say.at(scene, front, "multitool.pick");

        // Linked, the vent runs on the controller over the wall.
        Hand.use(scene, util, vent, Direction.UP, multitool);
        Vec3 over = Vec3.atCenterOf(controller.east()).add(0, 1, 0);
        int linked = 2 * (Say.LINE + 10);
        scene.overlay().showLine(PonderPalette.GREEN, front, over, linked);
        scene.overlay().showLine(PonderPalette.GREEN, over, util.vector().topOf(vent), linked);
        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, VentMode.BLOW));
        Gas.puff(scene, util, vent, Direction.UP, linked);
        Say.at(scene, util.vector().topOf(vent), "multitool.link");
        Say.at(scene, util.vector().topOf(vent), "multitool.runs");

        Hand.use(scene, util, vent, Direction.UP, multitool);
        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, VentMode.OFF));
        Say.at(scene, util.vector().topOf(vent), "multitool.unlink");
        scene.markAsFinished();
    }

    /** A crowbar forces a locked-down firelock open, and shut again. */
    public static void crowbar(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "crowbar");

        // A wall along z with a firelock in it, its passage running east to west.
        BlockPos lower = util.grid().at(2, 1, 2);
        Selection firelock = util.select().fromTo(lower, lower.above());
        BlockState closed = AtmosBlocks.FIRELOCK
                .get()
                .defaultBlockState()
                .setValue(FirelockBlock.FACING, Direction.WEST)
                .setValue(FirelockBlock.OPEN, false)
                .setValue(FirelockBlock.LOCKED, true);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int z = 0; z <= 4; z++) {
            for (int y = 1; y <= 2; y++) {
                BlockPos pos = util.grid().at(2, y, z);
                blocks.put(
                        pos,
                        z == 2
                                ? closed.setValue(
                                        FirelockBlock.HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                                : AtmosBlocks.STATION_WALL.get().defaultBlockState());
            }
        }
        Build.revealAll(scene, util, blocks, Direction.DOWN);

        ItemStack crowbar = new ItemStack(AtmosItems.CROWBAR.get());
        Hand.use(scene, util, lower, Direction.WEST, crowbar);
        Build.change(scene, firelock, s -> s.setValue(FirelockBlock.OPEN, true));
        Say.at(scene, util.vector().blockSurface(lower.above(), Direction.WEST), "crowbar.open");
        Say.at(scene, util.vector().blockSurface(lower.above(), Direction.WEST), "crowbar.locked");

        Hand.use(scene, util, lower, Direction.WEST, crowbar);
        Build.change(scene, firelock, s -> s.setValue(FirelockBlock.OPEN, false));
        Say.at(scene, util.vector().blockSurface(lower.above(), Direction.WEST), "crowbar.shut");
        scene.markAsFinished();
    }

    /** An extinguisher sprays out fires a few blocks off, and refills at water. */
    public static void fireExtinguisher(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "fire_extinguisher");

        List<BlockPos> fires = List.of(
                util.grid().at(1, 1, 1), util.grid().at(1, 1, 2), util.grid().at(2, 1, 1));
        Map<BlockPos, BlockState> burning = new LinkedHashMap<>();
        for (BlockPos fire : fires) {
            burning.put(fire, Blocks.FIRE.defaultBlockState());
        }
        Build.revealAll(scene, util, burning, Direction.DOWN);

        // Sprayed from where a player would hold it, it puts the fires out one by one.
        Vec3 nozzle = new Vec3(4.5, 1.6, 3.5);
        ItemStack extinguisher = new ItemStack(AtmosItems.FIRE_EXTINGUISHER.get());
        Hand.useAt(scene, nozzle, extinguisher);
        int spraying = fires.size() * SPRAY;
        Say.over(scene, spraying + Say.LINE, util.vector().topOf(fires.get(0)), "fire_extinguisher.spray");
        for (BlockPos fire : fires) {
            Vec3 at = Vec3.atCenterOf(fire);
            Vec3 way = at.subtract(nozzle).normalize().scale(at.distanceTo(nozzle) / 10);
            scene.effects()
                    .emitParticles(nozzle, scene.effects().simpleParticleEmitter(ParticleTypes.POOF, way), 2, SPRAY);
            scene.idle(SPRAY);
            Build.place(scene, fire, Blocks.AIR.defaultBlockState());
            scene.effects()
                    .emitParticles(
                            at, scene.effects().simpleParticleEmitter(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 3, 2);
        }
        scene.idle(Say.LINE);

        // A pool of water let into the floor.
        BlockPos water = util.grid().at(3, 0, 1);
        Build.place(scene, water, Blocks.WATER.defaultBlockState());
        scene.idle(10);
        Hand.use(scene, util, water, Direction.UP, extinguisher);
        Say.at(scene, util.vector().topOf(water), "fire_extinguisher.refill");
        scene.markAsFinished();
    }

    /** The ticks the extinguisher sprays at each fire. */
    private static final int SPRAY = 20;

    /** An analyzer reads the air where it's used, or a pipe's or a canister's gas. */
    public static void analyzer(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "atmos_analyzer");

        // The room's two far walls; the near sides are left open to see in.
        Map<BlockPos, BlockState> walls = new LinkedHashMap<>();
        for (int i = 0; i <= 4; i++) {
            for (int y = 1; y <= 2; y++) {
                walls.put(
                        util.grid().at(4, y, i), AtmosBlocks.STATION_WALL.get().defaultBlockState());
                walls.put(
                        util.grid().at(i, y, 4), AtmosBlocks.STATION_WALL.get().defaultBlockState());
            }
        }
        Build.revealAll(scene, util, walls, Direction.DOWN);

        Vec3 player = new Vec3(2, 1.7, 2);
        ItemStack analyzer = new ItemStack(AtmosItems.ATMOS_ANALYZER.get());
        Hand.useAt(scene, player, analyzer);
        scene.overlay()
                .showOutline(PonderPalette.GREEN, "zone", util.select().fromTo(0, 1, 0, 3, 2, 3), 2 * (Say.LINE + 10));
        Say.at(scene, player, "atmos_analyzer.zone");
        Say.at(scene, player, "reading.air", Gas.pressure(101.325), Readout.temperature(293.15));

        // A capped pipe, and a canister.
        BlockPos pipe = util.grid().at(1, 1, 1);
        BlockPos canister = util.grid().at(3, 1, 3);
        Map<BlockPos, BlockState> parts = new LinkedHashMap<>();
        parts.put(pipe.west(), PumpScenes.cap(Direction.EAST));
        parts.put(pipe, AtmosBlocks.PIPE.get().defaultBlockState());
        parts.put(pipe.east(), PumpScenes.cap(Direction.WEST));
        parts.put(
                canister,
                AtmosBlocks.CANISTER.get().defaultBlockState().setValue(CanisterBlock.FACING, Direction.NORTH));
        Build.revealAll(scene, util, parts, Direction.DOWN);
        Hand.use(scene, util, pipe, Direction.UP, analyzer);
        scene.idle(20);
        Hand.use(scene, util, canister, Direction.UP, analyzer);
        Say.at(scene, util.vector().topOf(canister), "atmos_analyzer.target");

        Say.plain(scene, "atmos_analyzer.hud");
        scene.markAsFinished();
    }

    /** A wrench turns a pump, takes a cap off, and leaves on a part under pressure. */
    public static void wrench(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "wrench");

        BlockPos pump = util.grid().at(2, 1, 2);
        BlockPos pipe = util.grid().at(1, 1, 2);
        BlockPos cap = util.grid().at(4, 1, 2);
        Map<BlockPos, BlockState> run = new LinkedHashMap<>();
        run.put(util.grid().at(0, 1, 2), PumpScenes.cap(Direction.EAST));
        run.put(pipe, AtmosBlocks.PIPE.get().defaultBlockState());
        run.put(
                pump,
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        run.put(util.grid().at(3, 1, 2), AtmosBlocks.PIPE.get().defaultBlockState());
        run.put(cap, PumpScenes.cap(Direction.WEST));
        Build.revealAll(scene, util, run, Direction.DOWN);

        // Two uses turn the pump round, while the line is up.
        ItemStack wrench = new ItemStack(AtmosItems.WRENCH.get());
        List<Direction> turns = List.of(Direction.SOUTH, Direction.WEST);
        Say.over(
                scene,
                turns.size() * (Hand.PAUSE + TURN) + Say.LINE,
                util.vector().topOf(pump),
                "wrench.turn");
        for (Direction to : turns) {
            Hand.use(scene, util, pump, Direction.UP, wrench);
            Build.change(scene, pump, s -> s.setValue(InlineDeviceBlock.FACING, to));
            scene.idle(TURN);
        }
        scene.idle(Say.LINE);

        Hand.sneakUse(scene, util, cap, Direction.UP, wrench);
        Build.remove(scene, util, cap);
        Say.at(scene, util.vector().blockSurface(cap.west(), Direction.EAST), "wrench.remove");

        Hand.sneakUse(scene, util, pipe, Direction.UP, wrench);
        scene.overlay().showOutline(PonderPalette.RED, "held", util.select().position(pipe), Say.LINE);
        Say.at(scene, util.vector().topOf(pipe), "wrench.pressure");
        scene.markAsFinished();
    }

    /** The ticks a wrench's turn stays before the next. */
    private static final int TURN = 25;
}
