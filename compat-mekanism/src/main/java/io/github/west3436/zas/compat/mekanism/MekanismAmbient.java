package io.github.west3436.zas.compat.mekanism;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import mekanism.api.heat.IMekanismHeatHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What stands round a Mekanism heat block, for the heat Mekanism has it lose to its environment
 * (MEK-041, the server's {@code [heat] ambient_from_rooms}, off unless set).
 *
 * <p>Each tick a Mekanism heat block loses heat through each of its six sides to an ambient
 * temperature it once read from the biome, through its own insulation and Mekanism's figure for
 * air, and that heat goes nowhere. With the setting on, the adapter's two hooks into Mekanism ask
 * this class instead:
 *
 * <ul>
 *   <li><b>The temperature</b> ({@link #temperature}). A side that faces a cell of a zone reads
 *       that zone's temperature. A side that faces an air block in the open reads the dimension's
 *       ambient as ZAS³ has it, which on another planet isn't what its biome says. Any other side
 *       keeps Mekanism's own: one against a block, in a vacuum or gas too thin to carry heat
 *       (HEAT-008), or where the atmosphere isn't resolved.
 *   <li><b>The heat</b> ({@link #environment}). What the block loses through a side that faces a
 *       zone is kept count of here and reaches that zone at the adapter's next step. What a block
 *       colder than its zone would gain through such a side is held back, asked of the zone at the
 *       next step, and handed to the block when the zone delivers it. Heat through any other side
 *       is left as Mekanism has it: to the open air, which nothing warms.
 * </ul>
 *
 * <p><b>Not between two heat blocks.</b> Mekanism also uses a side's ambient temperature for the
 * heat it passes to a heat block on that side. A side with a heat block across it keeps Mekanism's
 * own, so heat between two blocks moves exactly as it does without the adapter, and both read the
 * same.
 *
 * <p><b>No more than the ceiling.</b> One body, which is one capacitor as in {@link MekanismHeat},
 * passes its zones at most {@code [heat] max_body_watts} this way, and the exchange has what that
 * leaves. What a hot block loses above it is lost as it is without the adapter; a cold block gains
 * no more than it.
 *
 * <p><b>How fresh.</b> A side is read once for each step of the adapter, which is once an atmos
 * tick, and a zone's temperature changes only then, so a reading is never more than one atmos tick
 * old. Mekanism reads the biome once and keeps it.
 *
 * <p>The exchange of {@link MekanismHeat} (MEK-040) runs beside this: two ways out of the block,
 * each joule through one of them.
 *
 * <p>Nothing here names a class of Mekanism's outside its API. Server thread.
 */
public final class MekanismAmbient {
    /** Nothing ZAS³ answers for is on this side: Mekanism's own ambient. */
    private static final byte OWN = 0;

    /** An air block in the open, whose ambient has gas. */
    private static final byte OPEN = 1;

    /** A cell of a zone. */
    private static final byte ROOM = 2;

    private static final Direction[] SIDES = Direction.values();

    /** What is known of each level. Server thread only. */
    private static final Map<ServerLevel, Rooms> LEVELS = new WeakHashMap<>();

    private MekanismAmbient() {}

    /** One level's heat blocks that have asked. */
    private static final class Rooms {
        final Map<BlockPos, Sides> blocks = new HashMap<>();

        /**
         * What each body has passed its zones since the last step, in J, whichever way: what its
         * ceiling is held against.
         */
        final Map<Object, double[]> passed = new IdentityHashMap<>();

        /** How many steps the adapter has made: a reading is good until the next. */
        long step;
    }

    /** What one block's six sides face, and the heat on its way through them. */
    private static final class Sides {
        /** The step each side was read at, or -1. */
        final long[] read = new long[SIDES.length];

        final byte[] faces = new byte[SIDES.length];
        final double[] temperature = new double[SIDES.length];

        /**
         * The heat through each side since the last step, in J: above 0 out of the block into its
         * zone, below 0 asked of the zone.
         */
        final double[] joules = new double[SIDES.length];

        /** The block, and its heat handler, for handing it what a zone delivers. */
        BlockEntity owner;

        IMekanismHeatHandler handler;

        /** The last step the block asked at. */
        long used;

        Sides() {
            Arrays.fill(read, -1);
        }
    }

    /**
     * Heat that went through one side of a block since the last step.
     *
     * @param owner the block
     * @param side the side
     * @param joules the heat, in J: above 0 lost by the block to the zone on that side, below 0
     *     asked of that zone for the block
     * @param handler the block's heat handler
     */
    record Moved(BlockEntity owner, Direction side, double joules, IMekanismHeatHandler handler) {}

    /**
     * The ambient temperature on one side of a heat block.
     *
     * @param level the level
     * @param pos the block
     * @param side the side
     * @param mekanisms what Mekanism has there, from the biome
     * @return the temperature of the zone the side faces, or of the open air it faces, in K; else
     *     {@code mekanisms}
     */
    public static double temperature(ServerLevel level, BlockPos pos, Direction side, double mekanisms) {
        Rooms rooms = LEVELS.computeIfAbsent(level, l -> new Rooms());
        Sides sides = sides(rooms, pos);
        read(level, rooms, sides, pos, side);
        return sides.faces[side.ordinal()] == OWN ? mekanisms : sides.temperature[side.ordinal()];
    }

    /**
     * Heat a block is about to exchange with its environment through one side, as Mekanism has
     * worked it out.
     *
     * @param level the level
     * @param owner the block
     * @param side the side
     * @param heat the heat Mekanism is about to hand the block, in its units: below 0 a loss
     * @param handler the block's heat handler
     * @return the heat to hand the block: {@code heat}, or 0 for a gain that a zone has to deliver
     *     first
     */
    public static double environment(
            ServerLevel level, BlockEntity owner, Direction side, double heat, IMekanismHeatHandler handler) {
        int period = Atmospheres.periodTicks();
        if (heat == 0 || !Double.isFinite(heat) || period <= 0) {
            return heat;
        }
        BlockPos pos = owner.getBlockPos();
        Rooms rooms = LEVELS.computeIfAbsent(level, l -> new Rooms());
        Sides sides = sides(rooms, pos);
        read(level, rooms, sides, pos, side);
        int index = side.ordinal();
        if (sides.faces[index] != ROOM) {
            return heat;
        }
        sides.owner = owner;
        sides.handler = handler;
        double joules = Math.abs(heat) * MekanismSettings.joulesPerHeatUnit();
        double most = MekanismSettings.maxBodyWatts();
        if (most > 0) {
            double[] passed = rooms.passed.computeIfAbsent(MekanismHeat.body(owner, side), body -> new double[1]);
            joules = Math.min(joules, Math.max(0, most * period / 20.0 - passed[0]));
            passed[0] += joules;
        }
        if (heat < 0) {
            // The block loses all of it, as it does without the adapter; the zone gets this much.
            sides.joules[index] += joules;
            return heat;
        }
        // The block gains nothing now: its zone is asked for this much at the next step.
        sides.joules[index] -= joules;
        return 0;
    }

    /**
     * Whether any block of a level has asked, so that the adapter has to step for it.
     *
     * @param level the level
     * @return true if it has
     */
    static boolean has(ServerLevel level) {
        Rooms rooms = LEVELS.get(level);
        return rooms != null && !rooms.blocks.isEmpty();
    }

    /**
     * The heat that went through blocks' sides to and from zones since the last step, which this
     * forgets. Every reading is taken again after it. A block that has asked nothing for two steps
     * is forgotten too.
     *
     * @param level the level
     * @return the heat, a side at a time
     */
    static List<Moved> take(ServerLevel level) {
        Rooms rooms = LEVELS.get(level);
        if (rooms == null) {
            return List.of();
        }
        List<Moved> moved = new ArrayList<>();
        Iterator<Sides> blocks = rooms.blocks.values().iterator();
        while (blocks.hasNext()) {
            Sides sides = blocks.next();
            for (Direction side : SIDES) {
                double joules = sides.joules[side.ordinal()];
                if (joules != 0) {
                    moved.add(new Moved(sides.owner, side, joules, sides.handler));
                    sides.joules[side.ordinal()] = 0;
                }
            }
            if (sides.used + 2 <= rooms.step) {
                blocks.remove();
            }
        }
        rooms.passed.clear();
        rooms.step++;
        return moved;
    }

    /**
     * Keeps heat for the next step, because its zone sits this one out.
     *
     * @param level the level
     * @param moved the heat
     */
    static void keep(ServerLevel level, Moved moved) {
        Rooms rooms = LEVELS.computeIfAbsent(level, l -> new Rooms());
        Sides sides = sides(rooms, moved.owner().getBlockPos());
        sides.owner = moved.owner();
        sides.handler = moved.handler();
        sides.joules[moved.side().ordinal()] += moved.joules();
    }

    /** The server has stopped: nothing is kept for the next one. */
    static void forget() {
        LEVELS.clear();
    }

    private static Sides sides(Rooms rooms, BlockPos pos) {
        Sides sides = rooms.blocks.get(pos);
        if (sides == null) {
            sides = new Sides();
            rooms.blocks.put(pos.immutable(), sides);
        }
        sides.used = rooms.step;
        return sides;
    }

    /** Reads what a side faces, if it hasn't been read since the adapter's last step. */
    private static void read(ServerLevel level, Rooms rooms, Sides sides, BlockPos pos, Direction side) {
        int index = side.ordinal();
        if (sides.read[index] == rooms.step) {
            return;
        }
        sides.read[index] = rooms.step;
        sides.faces[index] = OWN;
        BlockPos next = pos.relative(side);
        if (!level.isLoaded(next)) {
            return;
        }
        boolean air = level.getBlockState(next).isAir();
        if (!air && level.getCapability(MekanismHeat.HEAT, next, side.getOpposite()) != null) {
            return; // a heat block: between the two, heat moves as Mekanism has it
        }
        AtmosphereSample there = Atmospheres.at(level, next);
        byte faces = OWN;
        GasMixture gas = null;
        if (there.state() == AtmosphereState.ZONED) {
            // The cell that touches the middle of the face, as the exchange has it.
            AtmosphereSample cell = Atmospheres.at(level, MekanismHeat.across(pos, side));
            if (cell.state() == AtmosphereState.ZONED) {
                faces = ROOM;
                gas = cell.mixture();
            }
        } else if (there.state() == AtmosphereState.AMBIENT && air) {
            faces = OPEN;
            gas = there.mixture();
        }
        if (gas == null
                || HeatFlow.thin(gas.totalMoles(), gas.volume(), Atmospheres.nearVacuumDensity())
                || !Double.isFinite(gas.temperature())) {
            return;
        }
        sides.faces[index] = faces;
        sides.temperature[index] = gas.temperature();
    }
}
