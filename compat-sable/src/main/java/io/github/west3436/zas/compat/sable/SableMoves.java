package io.github.west3436.zas.compat.sable;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.relocation.Destination;
import io.github.west3436.zas.api.relocation.RelocationOutcome;
import io.github.west3436.zas.api.relocation.RelocationResult;
import io.github.west3436.zas.api.relocation.RelocationTicket;
import io.github.west3436.zas.api.relocation.Relocations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Rooms carried along when Sable moves blocks (COMPAT-100): into a sub-level's plot when it is
 * assembled, from plot to plot when one splits, and back into the world when it is taken apart.
 *
 * <p>Each move goes through ZAS³'s relocation API (API-065): begun with the box of the blocks
 * before the first one changes, completed with where Sable puts them once the last is in place, so
 * every room wholly inside the box arrives with its gas, its temperature and its pins, and nothing
 * breaches or vents on the way. The destination is read off Sable's own transform: where it puts
 * the first block, and how it turns the blocks next to it. A move that isn't a quarter turn about the
 * vertical, which Sable never makes, carries nothing, and the rooms react to the blocks leaving as to
 * any change.
 *
 * <p>Server thread. A move off it carries nothing, since the relocation API is server-thread only.
 */
public final class SableMoves {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Who moves the blocks, as the relocation API is told. */
    public static final SourceId MOVER = SourceId.of("zas", "sable");

    /** A completed move whose rooms haven't arrived yet: its source level and box. Server thread. */
    private record InFlight(ServerLevel level, BoundingBox box, CompletableFuture<RelocationResult> done) {}

    private static final List<InFlight> IN_FLIGHT = new ArrayList<>();

    private SableMoves() {}

    /**
     * Runs Sable's move of {@code blocks} by {@code transform}, carrying the rooms inside them along.
     *
     * @param level the level the blocks leave
     * @param transform where Sable puts them
     * @param blocks the blocks
     * @param move Sable's own move
     */
    public static void around(
            ServerLevel level,
            SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks,
            Runnable move) {
        BoundingBox box = level.getServer().isSameThread() ? boxOf(blocks) : null;
        Destination destination = box == null ? null : destination(transform, box);
        if (destination == null) {
            move.run();
            return;
        }
        RelocationTicket ticket = Relocations.begin(level, box, MOVER);
        try {
            move.run();
        } catch (RuntimeException | Error e) {
            Relocations.cancel(ticket);
            throw e;
        }
        CompletableFuture<RelocationResult> done = Relocations.complete(ticket, destination);
        if (!done.isDone()) {
            InFlight flight = new InFlight(level, box, done);
            IN_FLIGHT.add(flight);
            done.whenComplete((result, failure) -> IN_FLIGHT.remove(flight));
        }
        done.thenAccept(result -> {
            if (result.outcome() == RelocationOutcome.UNLOADED) {
                // The ticket stays open after this answer, holding the rooms paused until it times
                // out; the blocks have moved already, so let them react now instead.
                Relocations.cancel(ticket);
            }
            if (result.outcome() != RelocationOutcome.MOVED && result.outcome() != RelocationOutcome.ABSENT) {
                LOGGER.debug("Sable moved the blocks of {} without their air: {}", box, result.outcome());
            }
        });
    }

    /**
     * Whether the rooms of every move out of the given chunks have arrived where they went: a future
     * that completes when they have, or null if none is on its way. Server thread.
     *
     * @param level the level the moves left
     * @param chunks the chunks
     */
    static @Nullable CompletableFuture<Void> settled(ServerLevel level, Collection<ChunkPos> chunks) {
        List<CompletableFuture<RelocationResult>> waiting = new ArrayList<>();
        for (InFlight flight : IN_FLIGHT) {
            if (flight.level() != level || flight.done().isDone()) {
                continue;
            }
            for (ChunkPos chunk : chunks) {
                if (flight.box()
                        .intersects(
                                chunk.getMinBlockX(),
                                chunk.getMinBlockZ(),
                                chunk.getMaxBlockX(),
                                chunk.getMaxBlockZ())) {
                    waiting.add(flight.done());
                    break;
                }
            }
        }
        return waiting.isEmpty() ? null : CompletableFuture.allOf(waiting.toArray(CompletableFuture[]::new));
    }

    /** The box around some blocks, or null for none. */
    static @Nullable BoundingBox boxOf(Iterable<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (BlockPos p : blocks) {
            any = true;
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        return any ? new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    /**
     * Where Sable's transform puts a box of blocks, as a relocation destination: the box's lowest
     * corner, where the transform puts it, and the quarter turn the transform makes. Null if the
     * transform isn't a quarter turn about the vertical, or the box's far corner doesn't go where the
     * destination puts it.
     */
    static @Nullable Destination destination(SubLevelAssemblyHelper.AssemblyTransform transform, BoundingBox box) {
        BlockPos pivot = new BlockPos(box.minX(), box.minY(), box.minZ());
        BlockPos target = transform.apply(pivot);
        Rotation rotation = turn(
                transform.apply(pivot.east()).subtract(target),
                transform.apply(pivot.south()).subtract(target),
                transform.apply(pivot.above()).subtract(target));
        if (rotation == null) {
            return null;
        }
        Destination destination = new Destination(transform.getLevel(), pivot, target, rotation);
        BlockPos far = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
        return destination.map(far).equals(transform.apply(far)) ? destination : null;
    }

    /**
     * The quarter turn that takes east, south and up to the given steps, or null if none does.
     * Turns are {@link BlockPos#rotate}'s, as {@link Destination} applies them.
     */
    static @Nullable Rotation turn(BlockPos east, BlockPos south, BlockPos up) {
        if (!up.equals(new BlockPos(0, 1, 0))) {
            return null;
        }
        for (Rotation r : Rotation.values()) {
            if (east.equals(new BlockPos(1, 0, 0).rotate(r)) && south.equals(new BlockPos(0, 0, 1).rotate(r))) {
                return r;
            }
        }
        return null;
    }
}
