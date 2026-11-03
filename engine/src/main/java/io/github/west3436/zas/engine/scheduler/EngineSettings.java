package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.engine.reaction.ReactionSet;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import java.util.Objects;

/**
 * The config a {@link Batch} carries, so a config change applies from a batch boundary
 * (architecture §2.3).
 *
 * @param exchange exchange settings; their period must be the atmos period in seconds, so every
 *     rate is per second (CORE-046)
 * @param checkConservation whether the invariant checker compares totals this tick (CORE-073)
 * @param heat conduction and radiation settings (HEAT); they use the exchange's period
 * @param airflowKpa the least pressure difference, kPa, at which an edge's openings are exported
 *     with the tick's result for airflow (FLOW-001); {@link Double#POSITIVE_INFINITY} exports none
 * @param fire the fire oxygen threshold (FIRE-030)
 * @param reactions the server's reactions (FIRE-001), compiled against the engine's gases
 */
public record EngineSettings(
        ExchangeSettings exchange,
        boolean checkConservation,
        HeatSettings heat,
        double airflowKpa,
        FireSettings fire,
        ReactionSet reactions) {
    /**
     * The default exchange, heat and fire threshold at a 1 s period, with the checker, airflow and
     * reactions off.
     */
    public static final EngineSettings DEFAULT = new EngineSettings(
            ExchangeSettings.DEFAULT,
            false,
            HeatSettings.DEFAULT,
            Double.POSITIVE_INFINITY,
            FireSettings.DEFAULT,
            ReactionSet.EMPTY);

    public EngineSettings {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(heat, "heat");
        Objects.requireNonNull(fire, "fire");
        Objects.requireNonNull(reactions, "reactions");
        if (Double.isNaN(airflowKpa) || airflowKpa < 0) {
            throw new IllegalArgumentException("airflowKpa must be >= 0: " + airflowKpa);
        }
    }

    /** Settings with reactions off. */
    public EngineSettings(
            ExchangeSettings exchange,
            boolean checkConservation,
            HeatSettings heat,
            double airflowKpa,
            FireSettings fire) {
        this(exchange, checkConservation, heat, airflowKpa, fire, ReactionSet.EMPTY);
    }

    /** Settings with the default fire threshold and reactions off. */
    public EngineSettings(ExchangeSettings exchange, boolean checkConservation, HeatSettings heat, double airflowKpa) {
        this(exchange, checkConservation, heat, airflowKpa, FireSettings.DEFAULT);
    }

    /** Settings with the default heat and fire threshold, and airflow and reactions off. */
    public EngineSettings(ExchangeSettings exchange, boolean checkConservation) {
        this(exchange, checkConservation, HeatSettings.DEFAULT, Double.POSITIVE_INFINITY);
    }

    /** Settings with the default fire threshold, and airflow and reactions off. */
    public EngineSettings(ExchangeSettings exchange, boolean checkConservation, HeatSettings heat) {
        this(exchange, checkConservation, heat, Double.POSITIVE_INFINITY);
    }

    /** These settings for an atmos period of {@code periodTicks} game ticks of 50 ms. */
    public EngineSettings withPeriodTicks(int periodTicks) {
        return new EngineSettings(
                exchange.withPeriod(SchedulerSettings.seconds(periodTicks)),
                checkConservation,
                heat,
                airflowKpa,
                fire,
                reactions);
    }

    public EngineSettings withConservationCheck(boolean on) {
        return new EngineSettings(exchange, on, heat, airflowKpa, fire, reactions);
    }

    public EngineSettings withHeat(HeatSettings settings) {
        return new EngineSettings(exchange, checkConservation, settings, airflowKpa, fire, reactions);
    }

    /** These settings exporting the openings of edges at or above {@code kpa} (FLOW-001). */
    public EngineSettings withAirflow(double kpa) {
        return new EngineSettings(exchange, checkConservation, heat, kpa, fire, reactions);
    }

    /** These settings with a fire oxygen threshold (FIRE-030). */
    public EngineSettings withFire(FireSettings settings) {
        return new EngineSettings(exchange, checkConservation, heat, airflowKpa, settings, reactions);
    }

    /** These settings with a set of reactions (FIRE-001). */
    public EngineSettings withReactions(ReactionSet set) {
        return new EngineSettings(exchange, checkConservation, heat, airflowKpa, fire, set);
    }
}
