package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.api.event.BlocksMovedEvent;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.api.relocation.Destination;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.turbine.CompressorBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.IEventBus;

/**
 * Devices a mover has moved with their blocks (CONT-156, API-067): each link to a block that moved
 * with it follows that block; a link to one that stayed is kept within a dimension and dropped across
 * dimensions. A turbine controller and its turbine and compressor, which keep each other's places,
 * do the same (CONT-190). An area air controller gives up its old position's pins, which its zone carried along,
 * and pins again from where it is; it keeps its central controller as the links do, and a central
 * controller keeps the area air controllers it had in the same way. Pipes, devices and portable
 * devices keep their gas while they are moved ({@link
 * io.github.west3436.zas.api.relocation.Relocations#isMoving}), since the mover carries it in their
 * data. A part the mover turns faces where it turned it, its pipe faces, a pipe's arms and a side
 * port with it, so it still joins the pipes that moved with it ({@link
 * io.github.west3436.zas.atmos.device.BlockTurns}).
 */
public final class MovedDevices {
    private MovedDevices() {}

    /** Registers the listener. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(MovedDevices::moved);
    }

    private static void moved(BlocksMovedEvent e) {
        ServerLevel from = e.from();
        Destination d = e.destination();
        ServerLevel to = d.level();
        boolean same = from == to;
        UnaryOperator<BlockPos> rebase = p -> e.moved(p) ? e.map(p) : same ? p : null;
        List<BlockEntity> found = blockEntities(to, d.map(e.box()));
        CentralClaims fromClaims = CentralClaims.of(from);
        CentralClaims toClaims = CentralClaims.of(to);
        for (BlockEntity be : found) {
            BlockPos now = be.getBlockPos();
            BlockPos was = unmap(d, now);
            if (be instanceof AirlockControllerBlockEntity airlock) {
                airlock.rebase(rebase);
            } else if (be instanceof TurbineControllerBlockEntity turbineController) {
                turbineController.rebase(rebase);
            } else if (be instanceof TurbineBlockEntity turbine) {
                turbine.rebase(rebase);
            } else if (be instanceof CompressorBlockEntity compressor) {
                compressor.rebase(rebase);
            } else if (be instanceof AreaAirControllerBlockEntity controller) {
                controller.rebase(rebase);
                ZonePins.unpin(from, AreaAirControllerBlockEntity.owner(was));
                Optional<BlockPos> central = fromClaims.centralOf(was);
                if (central.isPresent()) {
                    ZonePriority priority = fromClaims.priority(was);
                    fromClaims.release(was);
                    BlockPos c = rebase.apply(central.get());
                    if (c != null) {
                        toClaims.move(now, c);
                        toClaims.setPriority(now, priority);
                    }
                }
            }
        }
        for (BlockEntity be : found) {
            if (be instanceof CentralControllerBlockEntity) {
                BlockPos now = be.getBlockPos();
                BlockPos was = unmap(d, now);
                CentralChunks.hold(from, was, false);
                for (BlockPos controller : fromClaims.claimedBy(was)) {
                    ZonePriority priority = fromClaims.priority(controller);
                    fromClaims.release(controller);
                    if (same) {
                        toClaims.move(controller, now);
                        toClaims.setPriority(controller, priority);
                    }
                }
            }
        }
    }

    /** The block entities inside a box, as loaded. */
    private static List<BlockEntity> blockEntities(ServerLevel level, BoundingBox box) {
        List<BlockEntity> out = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (box.isInside(be.getBlockPos())) {
                        out.add(be);
                    }
                }
            }
        }
        return out;
    }

    /** Where a block a move put at {@code pos} was before it. */
    static BlockPos unmap(Destination d, BlockPos pos) {
        Rotation back = switch (d.rotation()) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> d.rotation();
        };
        return d.pivot().offset(pos.subtract(d.target()).rotate(back));
    }
}
