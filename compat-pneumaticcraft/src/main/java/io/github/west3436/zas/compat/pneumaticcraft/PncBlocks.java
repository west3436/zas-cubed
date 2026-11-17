package io.github.west3436.zas.compat.pneumaticcraft;

import io.github.west3436.zas.api.ServerThread;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerAdapter;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The blocks of a level that hold PneumaticCraft's air or heat, as the leaks (COMPAT-012) and the
 * heat exchange (COMPAT-013) need them. They are learned as their chunk loads and as a block with a
 * block entity is set, never by searching the world: a chunk's block entities are looked at once
 * when it loads, and a set block costs one flag read unless it has a block entity, when it and the
 * block entities next to it are looked at. Each part then visits only the blocks learned.
 *
 * <p>A block is learned through PneumaticCraft's capabilities, so any block that exposes its machine
 * air handler ({@code PNCCapabilities.AIR_HANDLER_MACHINE}) or its heat exchanger ({@code
 * PNCCapabilities.HEAT_EXCHANGER_BLOCK}), on a side or on none, is one, whoever made it. A heat
 * exchanger that is an adapter onto another mod's heat ({@link IHeatExchangerAdapter}), such as the
 * one PneumaticCraft puts on Mekanism's heat blocks, isn't: that mod's heat is that mod's adapter's
 * to exchange, and counting it here would count it twice.
 *
 * <p>Server thread.
 */
public final class PncBlocks {
    private static final Direction[] SIDES = Direction.values();

    /** What is known of each level. */
    private static final Map<ServerLevel, Known> LEVELS = new WeakHashMap<>();

    private PncBlocks() {}

    /** What is known of one level. */
    private static final class Known {
        /** The blocks with machine air handlers, each with its handlers. */
        final Map<BlockPos, Air> air = new LinkedHashMap<>();

        /** The blocks with heat exchangers of PneumaticCraft's own. */
        final Map<BlockPos, BlockEntity> heat = new LinkedHashMap<>();

        /** Positions whose block entity hasn't been looked at yet. */
        final Set<BlockPos> waiting = new HashSet<>();
    }

    /**
     * A block with machine air handlers: the block entity, and each distinct handler it exposes on
     * any side or on none. A Vacuum Pump has two.
     *
     * @param owner the block entity
     * @param handlers its handlers
     */
    record Air(BlockEntity owner, List<IAirHandlerMachine> handlers) {}

    private static Known known(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, l -> new Known());
    }

    /**
     * A chunk has loaded: its block entities are looked at when the blocks are next asked for.
     *
     * @param event the load
     */
    static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            Map<BlockPos, BlockEntity> entities = chunk.getBlockEntities();
            if (!entities.isEmpty()) {
                known(level).waiting.addAll(entities.keySet());
            }
        }
    }

    /**
     * A block was set: if it has a block entity, it is looked at when the blocks are next asked for.
     * So are the block entities next to it, since what a block exposes can change with its
     * neighbours: a tube offers its air handler only on the sides it can connect on.
     *
     * @param event the change
     */
    static void blockChanged(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().hasBlockEntity() && event.getLevel() instanceof ServerLevel level) {
            Set<BlockPos> waiting = known(level).waiting;
            BlockPos pos = event.getPos();
            waiting.add(pos.immutable());
            for (Direction side : SIDES) {
                BlockPos next = pos.relative(side);
                if (level.isLoaded(next) && level.getBlockState(next).hasBlockEntity()) {
                    waiting.add(next);
                }
            }
        }
    }

    /** The server has stopped: nothing is kept for the next one. */
    static void forget() {
        LEVELS.clear();
    }

    /**
     * A level's blocks with machine air handlers, once the positions waiting have been looked at.
     * Blocks gone or unloaded are dropped as they are found; one whose chunk loads again is learned
     * again.
     *
     * @param level the level
     * @return the blocks, in the order learned
     */
    @ServerThread
    static List<Air> air(ServerLevel level) {
        Known known = LEVELS.get(level);
        if (known == null) {
            return List.of();
        }
        admit(level, known);
        List<Air> found = new ArrayList<>(known.air.size());
        Iterator<Map.Entry<BlockPos, Air>> it = known.air.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Air> entry = it.next();
            if (entry.getValue().owner().isRemoved() || !level.isLoaded(entry.getKey())) {
                it.remove();
            } else {
                found.add(entry.getValue());
            }
        }
        return found;
    }

    /**
     * A level's blocks with heat exchangers of PneumaticCraft's own, once the positions waiting have
     * been looked at.
     *
     * @param level the level
     * @return the blocks' block entities, in the order learned
     */
    @ServerThread
    static List<BlockEntity> heat(ServerLevel level) {
        Known known = LEVELS.get(level);
        if (known == null) {
            return List.of();
        }
        admit(level, known);
        List<BlockEntity> found = new ArrayList<>(known.heat.size());
        Iterator<Map.Entry<BlockPos, BlockEntity>> it = known.heat.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, BlockEntity> entry = it.next();
            BlockEntity entity = entry.getValue();
            if (entity.isRemoved() || !level.isLoaded(entry.getKey())) {
                it.remove();
            } else {
                found.add(entity);
            }
        }
        return found;
    }

    /**
     * Whether a position is among a level's blocks of either kind, once the positions waiting have
     * been looked at.
     *
     * @param level the level
     * @param pos the block's position
     * @return true if the block there takes part in the leaks or the heat exchange
     */
    @ServerThread
    public static boolean knows(ServerLevel level, BlockPos pos) {
        Known known = LEVELS.get(level);
        if (known == null) {
            return false;
        }
        admit(level, known);
        return known.air.containsKey(pos) || known.heat.containsKey(pos);
    }

    /** Looks at the positions waiting: those whose block entity exposes air or heat join the level's blocks. */
    private static void admit(ServerLevel level, Known known) {
        if (known.waiting.isEmpty()) {
            return;
        }
        for (BlockPos pos : known.waiting) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity == null || entity.isRemoved()) {
                known.air.remove(pos);
                known.heat.remove(pos);
                continue;
            }
            List<IAirHandlerMachine> handlers = handlers(level, pos, entity);
            if (handlers.isEmpty()) {
                known.air.remove(pos);
            } else {
                known.air.put(pos, new Air(entity, handlers));
            }
            if (exposesHeat(level, pos, entity)) {
                known.heat.put(pos, entity);
            } else {
                known.heat.remove(pos);
            }
        }
        known.waiting.clear();
    }

    /** Each distinct machine air handler a block entity exposes, on no side and on each side. */
    private static List<IAirHandlerMachine> handlers(ServerLevel level, BlockPos pos, BlockEntity entity) {
        BlockState state = entity.getBlockState();
        Map<IAirHandlerMachine, Boolean> found = new IdentityHashMap<>();
        List<IAirHandlerMachine> handlers = new ArrayList<>(1);
        add(level.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE, pos, state, entity, null), found, handlers);
        for (Direction side : SIDES) {
            add(level.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE, pos, state, entity, side), found, handlers);
        }
        return handlers.isEmpty() ? List.of() : List.copyOf(handlers);
    }

    private static void add(
            @Nullable IAirHandlerMachine handler, Map<IAirHandlerMachine, Boolean> found, List<IAirHandlerMachine> to) {
        if (handler != null && found.put(handler, Boolean.TRUE) == null) {
            to.add(handler);
        }
    }

    /** Whether a block entity exposes a heat exchanger of PneumaticCraft's own, on no side or on any. */
    private static boolean exposesHeat(ServerLevel level, BlockPos pos, BlockEntity entity) {
        BlockState state = entity.getBlockState();
        if (own(level.getCapability(PNCCapabilities.HEAT_EXCHANGER_BLOCK, pos, state, entity, null))) {
            return true;
        }
        for (Direction side : SIDES) {
            if (own(level.getCapability(PNCCapabilities.HEAT_EXCHANGER_BLOCK, pos, state, entity, side))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a heat exchanger is PneumaticCraft's own heat, not an adapter onto another mod's.
     *
     * @param logic the exchanger, or null
     * @return true for one of PneumaticCraft's own
     */
    static boolean own(@Nullable IHeatExchangerLogic logic) {
        return logic != null && !(logic instanceof IHeatExchangerAdapter);
    }
}
