package io.github.west3436.zas.compat.pneumaticcraft;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Air that a PneumaticCraft machine or tube leaks into a sealed room fills the room (COMPAT-012),
 * as standard air at 293.15 K, so many moles a mL ({@link PncSettings#molesPerMillilitre}).
 *
 * <p><b>Finding leaks.</b> PneumaticCraft's machine air handler has no leak event, but its API says
 * which side a handler leaks from ({@code IAirHandlerMachine.getSideLeaking}) and how much it leaks
 * a tick: {@code pressure × 40 + 20} mL. So the adapter polls, in two tiers.
 *
 * <ul>
 *   <li>Once an atmos tick, each block with an air handler that a zone touches ({@link
 *       Atmospheres#zoneTouches}, a lookup that builds nothing) is asked whether it leaks. The
 *       blocks are the ones {@link PncBlocks} has learned, never a search of the world, and a block
 *       no zone touches costs that one lookup.
 *   <li>Each game tick, before the level's blocks tick, each handler found leaking is asked its side,
 *       its air and its pressure, and the leak that tick's own tick will make is added up: exactly
 *       what PneumaticCraft takes out, as it reads the same values in the same arithmetic. A block
 *       whose chunk doesn't tick leaks nothing, and nothing is counted for it.
 *   <li>Once an atmos tick, what each handler leaked goes into the zone of the cell its leaking side
 *       faces, in one gas mutation. Into open air, into a solid block or into nothing loaded it is
 *       left to PneumaticCraft, which deletes it, as it does without ZAS³.
 * </ul>
 *
 * <p><b>Cost.</b> A tube network of N blocks costs N lookups an atmos tick (once a second by
 * default), and only those near a zone are asked anything more; each handler that leaks costs
 * three calls a game tick, and one mutation a second. A base of 5,000 tubes in sealed rooms with
 * 50 open ends is about 10,000 calls a second, a few microseconds a game tick, against NFR-001's
 * 2 ms.
 *
 * <p><b>Not counted.</b> A leak that starts between two atmos ticks is counted from the next one, up
 * to one atmos tick late. A handler venting through a Security Upgrade or a Safety Tube Module
 * vents through a side the API doesn't show, so its air is lost as without ZAS³. A handler below
 * 0 bar, such as a Vacuum Pump's, leaks air in, which PneumaticCraft makes from nothing; the room
 * is left as it is.
 *
 * <p>Server thread.
 */
public final class PncLeaks {
    /** Who the adapter's leak mutations are made by. */
    public static final SourceId SOURCE = SourceId.of("zas", "pneumaticcraft_leak");

    /** The handlers found leaking, in each level, with what each has leaked since it was last emptied. */
    private static final Map<ServerLevel, Map<IAirHandlerMachine, Leak>> LEVELS = new WeakHashMap<>();

    private PncLeaks() {}

    /** One leaking handler and what it has leaked since its air last went to a zone. */
    private static final class Leak {
        final BlockEntity owner;
        final IAirHandlerMachine handler;

        /** The side it last leaked from. */
        Direction side;

        /** The air it has leaked since, in mL. */
        long leaked;

        Leak(BlockEntity owner, IAirHandlerMachine handler, Direction side) {
            this.owner = owner;
            this.handler = handler;
            this.side = side;
        }
    }

    /**
     * What one atmos tick sent to a zone from one handler's leak.
     *
     * @param from the block that leaked
     * @param millilitres the air, in PneumaticCraft's mL
     * @param result what ZAS³ made of it, complete once the atmos tick that applies it has been
     */
    public record Sent(BlockPos from, long millilitres, CompletableFuture<MutationResult> result) {}

    /** Before a level's blocks tick: what each leaking handler's tick will leak is counted. */
    static void beforeTick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            count(level);
        }
    }

    /** After a level has ticked: once an atmos tick, the leaks go to their zones and leaks are looked for. */
    static void afterTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            int period = Atmospheres.periodTicks();
            if (period > 0 && level.getGameTime() % period == 0) {
                step(level);
            }
        }
    }

    /** The server has stopped: nothing is kept for the next one. */
    static void forget() {
        LEVELS.clear();
    }

    /**
     * Counts what each handler found leaking leaks in the tick about to run. Called before the
     * level's blocks tick; GameTests call it in place of the event.
     *
     * @param level the level
     */
    @ServerThread
    public static void count(ServerLevel level) {
        Map<IAirHandlerMachine, Leak> leaks = LEVELS.get(level);
        if (leaks == null || leaks.isEmpty() || !level.tickRateManager().runsNormally()) {
            return;
        }
        for (Leak leak : leaks.values()) {
            Direction side = leak.handler.getSideLeaking();
            BlockPos pos = leak.owner.getBlockPos();
            if (side == null || leak.owner.isRemoved() || !level.shouldTickBlocksAt(pos)) {
                continue;
            }
            leak.side = side;
            leak.leaked += AirUnits.leakPerTick(leak.handler.getPressure(), leak.handler.getAir());
        }
    }

    /**
     * Sends what was leaked to the zones the leaks face, and looks for leaks again. {@link #afterTick}
     * calls it once an atmos tick; GameTests call it directly.
     *
     * @param level the level
     * @return what went to zones, a mutation for each handler that leaked into one
     */
    @ServerThread
    public static List<Sent> step(ServerLevel level) {
        if (!PncSettings.leaksFillRooms()) {
            // Switched off: what was counted is left to PneumaticCraft, and nothing more is counted.
            LEVELS.remove(level);
            return List.of();
        }
        Map<IAirHandlerMachine, Leak> leaks = LEVELS.get(level);
        List<Sent> sent = new ArrayList<>();
        if (leaks != null) {
            double perMl = PncSettings.molesPerMillilitre();
            Iterator<Leak> it = leaks.values().iterator();
            while (it.hasNext()) {
                Leak leak = it.next();
                long leaked = leak.leaked;
                leak.leaked = 0;
                if (leaked > 0 && perMl > 0 && !leak.owner.isRemoved()) {
                    Sent one = send(level, leak.owner.getBlockPos(), leak.side, leaked, perMl);
                    if (one != null) {
                        sent.add(one);
                    }
                }
                if (leak.owner.isRemoved() || leak.handler.getSideLeaking() == null) {
                    it.remove();
                }
            }
        }
        look(level);
        return sent;
    }

    /** Sends a leak's air to the zone of the cell its side faces, if one does. */
    private static @Nullable Sent send(ServerLevel level, BlockPos pos, Direction side, long leaked, double perMl) {
        BlockPos into = pos.relative(side);
        if (!level.isLoaded(into) || Atmospheres.at(level, into).state() != AtmosphereState.ZONED) {
            return null;
        }
        Vec3 point = across(pos, side);
        return new Sent(pos, leaked, GasMutations.add(level, point, AirUnits.standardAir(leaked * perMl), SOURCE));
    }

    /** Looks for handlers that leak, among the blocks a zone touches. */
    private static void look(ServerLevel level) {
        Map<IAirHandlerMachine, Leak> leaks = LEVELS.get(level);
        for (PncBlocks.Air air : PncBlocks.air(level)) {
            BlockEntity owner = air.owner();
            if (!Atmospheres.zoneTouches(level, owner.getBlockPos())) {
                continue;
            }
            for (IAirHandlerMachine handler : air.handlers()) {
                Direction side = handler.getSideLeaking();
                if (side != null && handler.getAir() > 0) {
                    if (leaks == null) {
                        leaks = LEVELS.computeIfAbsent(level, l -> new IdentityHashMap<>());
                    }
                    leaks.computeIfAbsent(handler, h -> new Leak(owner, h, side));
                }
            }
        }
    }

    /**
     * Whether a handler is among those being counted as leaking.
     *
     * @param level the level
     * @param handler the handler
     * @return true if its leaks are being counted
     */
    @ServerThread
    public static boolean counting(ServerLevel level, IAirHandlerMachine handler) {
        Map<IAirHandlerMachine, Leak> leaks = LEVELS.get(level);
        return leaks != null && leaks.containsKey(handler);
    }

    /**
     * What a handler has leaked since its air last went to a zone.
     *
     * @param level the level
     * @param handler the handler
     * @return the air in mL; 0 if it isn't being counted
     */
    @ServerThread
    public static long leaked(ServerLevel level, IAirHandlerMachine handler) {
        Map<IAirHandlerMachine, Leak> leaks = LEVELS.get(level);
        Leak leak = leaks == null ? null : leaks.get(handler);
        return leak == null ? 0 : leak.leaked;
    }

    /** A point just across one side of a block: in the cell that touches the middle of that face. */
    static Vec3 across(BlockPos pos, Direction side) {
        double reach = 0.5 + 1.0 / 32;
        return new Vec3(
                pos.getX() + 0.5 + side.getStepX() * reach,
                pos.getY() + 0.5 + side.getStepY() * reach,
                pos.getZ() + 0.5 + side.getStepZ() * reach);
    }
}
