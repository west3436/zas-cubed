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
 */
public record EngineSettings(ExchangeSettings exchange, boolean checkConservation, HeatSettings heat) {
    /** The default exchange and heat at a 1 s period, with the checker off. */
    public static final EngineSettings DEFAULT =
            new EngineSettings(ExchangeSettings.DEFAULT, false, HeatSettings.DEFAULT);

    public EngineSettings {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(heat, "heat");
    }

    /** Settings with the default heat. */
    public EngineSettings(ExchangeSettings exchange, boolean checkConservation) {
        this(exchange, checkConservation, HeatSettings.DEFAULT);
    }

    /** These settings for an atmos period of {@code periodTicks} game ticks of 50 ms. */
    public EngineSettings withPeriodTicks(int periodTicks) {
        return new EngineSettings(exchange.withPeriod(SchedulerSettings.seconds(periodTicks)), checkConservation, heat);
    }

    public EngineSettings withConservationCheck(boolean on) {
        return new EngineSettings(exchange, on, heat);
    }

    public EngineSettings withHeat(HeatSettings settings) {
        return new EngineSettings(exchange, checkConservation, settings);
    }
}
