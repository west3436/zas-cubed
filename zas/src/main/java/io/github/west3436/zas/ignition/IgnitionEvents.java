package io.github.west3436.zas.ignition;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The one-off ignition sources (FIRE-014, FIRE-015): each, when it happens, asks the engine to
 * ignite the mixture at its position or a neighbouring cell of its zone. The engine decides whether
 * there is anything flammable there; nothing here reads gas.
 *
 * <ul>
 *   <li><b>Item use</b> ({@code item_use}, flint and steel): the item used on a block, once the
 *       block hasn't taken the click itself, ignites the cell on the side clicked.
 *   <li><b>Entities</b> ({@code entity}, lightning): an entity of a source type joining a level,
 *       other than loaded from disk, ignites its cell.
 *   <li><b>Explosions</b> ({@code explosion}): an explosion ignites its centre, except a burst that
 *       only triggers blocks, such as a wind charge's.
 *   <li><b>Sparks</b> ({@code spark}, the redstone spark, off by default): the change hook queues
 *       every write to a spark state, on any thread, and they are sent at the end of the server
 *       tick. With no spark source on, the hook pays one read per write.
 * </ul>
 *
 * <p>Detection is change-driven: nothing scans the world. A position whose last ignition hasn't
 * been applied yet isn't sent again, so an explosion chain or a redstone clock queues at most one
 * ignition per position per atmos tick. Standing sources (fire, lava, torches, lit campfires) aren't
 * here: the engine finds them through the geometry table ({@link IgnitionSources}).
 *
 * <p>Server thread, except {@link #onBlockWrite}.
 */
public final class IgnitionEvents {
    /** An ignition sent to the engine: where, and which source. */
    public record Strike(ServerLevel level, BlockPos pos, ResourceLocation source) {}

    private record QueuedSpark(ServerLevel level, long pos, ResourceLocation source) {}

    private static final ConcurrentLinkedQueue<QueuedSpark> SPARKS = new ConcurrentLinkedQueue<>();
    private static final Map<ServerLevel, Set<Long>> IN_FLIGHT = new IdentityHashMap<>();
    private static volatile @Nullable Consumer<Strike> observer;

    private IgnitionEvents() {}

    /** Registers the event listeners on the game event bus. */
    public static void register(IEventBus gameBus) {
        // Lowest, so a use or an explosion another mod cancels never ignites.
        gameBus.addListener(EventPriority.LOWEST, IgnitionEvents::onUseItemOnBlock);
        gameBus.addListener(EventPriority.LOWEST, IgnitionEvents::onEntityJoin);
        gameBus.addListener(EventPriority.LOWEST, IgnitionEvents::onExplosion);
        gameBus.addListener(IgnitionEvents::onServerTick);
        gameBus.addListener(IgnitionEvents::onLevelUnload);
        gameBus.addListener(IgnitionEvents::onServerStopped);
    }

    /**
     * Sees every strike sent to the engine, or nothing with {@code null}: for GameTests, which run
     * on the server thread and so see them in order.
     */
    public static void observeForTest(@Nullable Consumer<Strike> watcher) {
        observer = watcher;
    }

    /**
     * Called by the change hook for every write into a server chunk that changed the stored state:
     * queues a spark if the new state is one. Any thread.
     */
    public static void onBlockWrite(ServerLevel level, BlockPos pos, BlockState now) {
        ResourceLocation source = IgnitionSources.spark(Block.getId(now));
        if (source != null) {
            SPARKS.add(new QueuedSpark(level, pos.asLong(), source));
        }
    }

    /** Sends one ignition, unless the last one at that position is still waiting to be applied. */
    static void strike(ServerLevel level, BlockPos pos, ResourceLocation source) {
        BlockPos at = pos.immutable();
        Set<Long> waiting = IN_FLIGHT.computeIfAbsent(level, l -> new HashSet<>());
        long key = at.asLong();
        if (!waiting.add(key)) {
            return;
        }
        Consumer<Strike> watcher = observer;
        if (watcher != null) {
            watcher.accept(new Strike(level, at, source));
        }
        EngineLink.ignite(level, at, source.toString()).whenComplete((r, e) -> {
            Set<Long> s = IN_FLIGHT.get(level);
            if (s != null) {
                s.remove(key);
            }
        });
    }

    private static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        if (event.isCanceled()
                || event.getUsePhase() != UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation source = IgnitionSources.item(event.getItemStack().getItem());
        if (source != null) {
            strike(level, event.getPos().relative(event.getFace()), source);
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation source = IgnitionSources.entity(event.getEntity().getType());
        if (source != null) {
            strike(level, event.getEntity().blockPosition(), source);
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        Explosion explosion = event.getExplosion();
        if (!(event.getLevel() instanceof ServerLevel level)
                || explosion.getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK) {
            return;
        }
        ResourceLocation source = IgnitionSources.explosion();
        if (source != null) {
            strike(level, BlockPos.containing(explosion.center()), source);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        QueuedSpark s;
        while ((s = SPARKS.poll()) != null) {
            strike(s.level(), BlockPos.of(s.pos()), s.source());
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SPARKS.removeIf(s -> s.level() == level);
            IN_FLIGHT.remove(level);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SPARKS.clear();
        IN_FLIGHT.clear();
    }
}
