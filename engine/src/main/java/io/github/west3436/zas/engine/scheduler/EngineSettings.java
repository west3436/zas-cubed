package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.heat.HeatSettings;
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
 */
public record EngineSettings(
        ExchangeSettings exchange, boolean checkConservation, HeatSettings heat, double airflowKpa) {
    /** The default exchange and heat at a 1 s period, with the checker and airflow off. */
    public static final EngineSettings DEFAULT =
            new EngineSettings(ExchangeSettings.DEFAULT, false, HeatSettings.DEFAULT, Double.POSITIVE_INFINITY);

    public EngineSettings {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(heat, "heat");
        if (Double.isNaN(airflowKpa) || airflowKpa < 0) {
            throw new IllegalArgumentException("airflowKpa must be >= 0: " + airflowKpa);
        }
    }

    /** Settings with the default heat and airflow off. */
    public EngineSettings(ExchangeSettings exchange, boolean checkConservation) {
        this(exchange, checkConservation, HeatSettings.DEFAULT, Double.POSITIVE_INFINITY);
    }

    /** Settings with airflow off. */
    public EngineSettings(ExchangeSettings exchange, boolean checkConservation, HeatSettings heat) {
        this(exchange, checkConservation, heat, Double.POSITIVE_INFINITY);
    }

    /** These settings for an atmos period of {@code periodTicks} game ticks of 50 ms. */
    public EngineSettings withPeriodTicks(int periodTicks) {
        return new EngineSettings(
                exchange.withPeriod(SchedulerSettings.seconds(periodTicks)), checkConservation, heat, airflowKpa);
    }

    public EngineSettings withConservationCheck(boolean on) {
        return new EngineSettings(exchange, on, heat, airflowKpa);
    }

    public EngineSettings withHeat(HeatSettings settings) {
        return new EngineSettings(exchange, checkConservation, settings, airflowKpa);
    }

    /** These settings exporting the openings of edges at or above {@code kpa} (FLOW-001). */
    public EngineSettings withAirflow(double kpa) {
        return new EngineSettings(exchange, checkConservation, heat, kpa);
    }
}
