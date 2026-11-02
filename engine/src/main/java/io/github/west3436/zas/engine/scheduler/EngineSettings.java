package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.zone.ExchangeSettings;
import java.util.Objects;

/**
 * The config a {@link Batch} carries, so a config change applies from a batch boundary
 * (architecture §2.3).
 *
 * @param exchange exchange settings; their period must be the atmos period in seconds, so every
 *     rate is per second (CORE-046)
 * @param checkConservation whether the invariant checker compares totals this tick (CORE-073)
 * @param airflowKpa the least pressure difference, kPa, at which an edge's openings are exported
 *     with the tick's result for airflow (FLOW-001); {@link Double#POSITIVE_INFINITY} exports none
 */
public record EngineSettings(ExchangeSettings exchange, boolean checkConservation, double airflowKpa) {
    /** The default exchange at a 1 s period, with the checker and airflow off. */
    public static final EngineSettings DEFAULT =
            new EngineSettings(ExchangeSettings.DEFAULT, false, Double.POSITIVE_INFINITY);

    public EngineSettings {
        Objects.requireNonNull(exchange, "exchange");
        if (Double.isNaN(airflowKpa) || airflowKpa < 0) {
            throw new IllegalArgumentException("airflowKpa must be >= 0: " + airflowKpa);
        }
    }

    /** These settings with airflow off. */
    public EngineSettings(ExchangeSettings exchange, boolean checkConservation) {
        this(exchange, checkConservation, Double.POSITIVE_INFINITY);
    }

    /** These settings for an atmos period of {@code periodTicks} game ticks of 50 ms. */
    public EngineSettings withPeriodTicks(int periodTicks) {
        return new EngineSettings(
                exchange.withPeriod(SchedulerSettings.seconds(periodTicks)), checkConservation, airflowKpa);
    }

    public EngineSettings withConservationCheck(boolean on) {
        return new EngineSettings(exchange, on, airflowKpa);
    }

    /** These settings exporting the openings of edges at or above {@code kpa} (FLOW-001). */
    public EngineSettings withAirflow(double kpa) {
        return new EngineSettings(exchange, checkConservation, kpa);
    }
}
