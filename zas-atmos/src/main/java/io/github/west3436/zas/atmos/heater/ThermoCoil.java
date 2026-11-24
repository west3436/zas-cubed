package io.github.west3436.zas.atmos.heater;

import io.github.west3436.ss13.device.DeviceTicks;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.power.DevicePower;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The heater built into a vent, or the air conditioner built into a scrubber (CONT-045, CONT-166,
 * CONT-167): what an area air controller's thermostat moves heat through. It has the standalone
 * machine's power and cost ({@link HeaterMath}): at most {@code [power] heater_watts} or {@code
 * air_conditioner_watts} a second, drawing {@code [power] heater} or {@code air_conditioner} FE/t at
 * full power and in proportion below it, rounded up to whole FE/t.
 *
 * <p>Each atmos tick the controller powering the device orders the heat its share of the zone needs
 * ({@link #order}) from what is left after paying its parts, and takes the order's cost ({@link
 * #draw()}) from its store as it orders. At its own step the device moves what was ordered, at its
 * air point, through API-013's heat mutation, and the order is used up: one order, paid once, moves
 * heat once. A controller doesn't order again while an order is waiting. An order not used within
 * {@link DevicePower#LEASE_TICKS} game ticks lapses.
 *
 * <p>One heat mutation is outstanding at a time ({@link #ready()}), so a step reads its zone after
 * the last one landed. Server thread only.
 */
public final class ThermoCoil {
    /** Which way a coil moves heat. */
    public enum Kind {
        /** Into the zone: a vent's heater. */
        HEATS,
        /** Out of the zone, and out of the world: a scrubber's air conditioner. */
        COOLS
    }

    private final Kind kind;
    private final SourceId source;
    private final DoubleSupplier maxWatts;
    private final IntSupplier fullDraw;
    private final LongSupplier clock;
    private double ordered;
    private long orderedUntil = Long.MIN_VALUE;
    private double watts;
    private double sent;
    private CompletableFuture<HeatResult> pending;

    /**
     * @param kind which way it moves heat
     * @param source who moves it, in the conservation ledger
     * @param maxWatts the most it moves, W
     * @param fullDraw what it draws at full power, FE/t
     * @param clock the current game tick, as {@link DeviceTicks#now}
     */
    public ThermoCoil(Kind kind, SourceId source, DoubleSupplier maxWatts, IntSupplier fullDraw, LongSupplier clock) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.source = Objects.requireNonNull(source, "source");
        this.maxWatts = Objects.requireNonNull(maxWatts, "maxWatts");
        this.fullDraw = Objects.requireNonNull(fullDraw, "fullDraw");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A vent's heater: the heater's power and draw (CONT-087). */
    public static ThermoCoil heater(SourceId source, LongSupplier clock) {
        return new ThermoCoil(
                Kind.HEATS,
                source,
                () -> AtmosServerConfig.get(AtmosServerConfig.HEATER_WATTS),
                () -> AtmosServerConfig.get(AtmosServerConfig.HEATER_DRAW),
                clock);
    }

    /** A scrubber's air conditioner: the air conditioner's power and draw (CONT-087). */
    public static ThermoCoil airConditioner(SourceId source, LongSupplier clock) {
        return new ThermoCoil(
                Kind.COOLS,
                source,
                () -> AtmosServerConfig.get(AtmosServerConfig.AIR_CONDITIONER_WATTS),
                () -> AtmosServerConfig.get(AtmosServerConfig.AIR_CONDITIONER_DRAW),
                clock);
    }

    /** Which way it moves heat. */
    public Kind kind() {
        return kind;
    }

    /** The most heat it moves in one atmos tick, J. */
    public double mostPerStep() {
        return Math.max(0, maxWatts.getAsDouble()) * DeviceTicks.SECONDS;
    }

    /**
     * A controller's order for this atmos tick: move this much heat at the next step.
     *
     * @param joules the heat, J; clamped to 0 to {@link #mostPerStep()}
     */
    public void order(double joules) {
        ordered = Double.isNaN(joules) ? 0 : Math.max(0, Math.min(mostPerStep(), joules));
        orderedUntil = clock.getAsLong() + DevicePower.LEASE_TICKS;
    }

    /** The heat ordered and not yet moved, J; 0 once the order lapses. */
    public double ordered() {
        return clock.getAsLong() < orderedUntil ? ordered : 0;
    }

    /** What the order costs, FE/t: the standalone machine's cost of that heat, spread over the atmos tick. */
    public int draw() {
        int fe = HeaterMath.energy(
                ordered(), DeviceTicks.SECONDS, maxWatts.getAsDouble(), fullDraw.getAsInt(), DeviceTicks.PERIOD);
        return (fe + DeviceTicks.PERIOD - 1) / DeviceTicks.PERIOD;
    }

    /**
     * The most heat an atmos tick's worth of FE pays for: whole FE/t of the full draw's share of the
     * full power, so that {@link #draw()} of it is within the FE.
     *
     * @param fe the FE for one atmos tick
     * @return the heat, J, from 0 to {@link #mostPerStep()}
     */
    public double joulesFor(long fe) {
        int full = fullDraw.getAsInt();
        if (full <= 0) {
            return mostPerStep(); // free heat at a draw of 0, as the heater's
        }
        long perTick = Math.max(0, fe) / DeviceTicks.PERIOD;
        if (perTick >= full) {
            return mostPerStep();
        }
        // A hair under the share, so rounding the cost up lands on perTick and not above it.
        return Math.max(0, mostPerStep() * perTick / full * (1 - 1e-9));
    }

    /** Whether its last heat mutation has landed, so the device may step. */
    public boolean ready() {
        return pending == null || pending.isDone();
    }

    /**
     * The heat it has sent and that hasn't landed yet, J: what a reading of its zone doesn't show
     * yet, so a thermostat doesn't order it again.
     */
    public double inFlight() {
        return ready() ? 0 : sent;
    }

    /**
     * The device's step: moves what was ordered, and paid for, at a point, and uses the order up.
     *
     * @param level the level
     * @param point where the heat goes, or comes from: the device's air point
     * @return the heat moved, as a power in W
     */
    public double step(ServerLevel level, Vec3 point) {
        double joules = ordered();
        ordered = 0;
        watts = 0;
        if (joules > 0) {
            pending = kind == Kind.HEATS
                    ? HeatMutations.add(level, point, joules, source)
                    : HeatMutations.remove(level, point, joules, source);
            sent = joules;
            watts = joules / DeviceTicks.SECONDS;
        }
        return watts;
    }

    /** The device moves no heat this step, being off or idle: the order goes too. */
    public void idle() {
        ordered = 0;
        watts = 0;
    }

    /** The heat it moved at its last step, as a power in W. */
    public double watts() {
        return watts;
    }
}
