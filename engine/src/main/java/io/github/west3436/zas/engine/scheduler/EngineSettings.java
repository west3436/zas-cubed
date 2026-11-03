package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.fire.FireSettings;
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
 * @param fire the fire oxygen threshold (FIRE-030)
 * @param hotspots hotspot spread and chemistry (FIRE-011, FIRE-016)
 */
public record EngineSettings(
        ExchangeSettings exchange,
        boolean checkConservation,
        HeatSettings heat,
        double airflowKpa,
        FireSettings fire,
        HotspotSettings hotspots) {
    /**
     * The default exchange, heat and fire threshold at a 1 s period, with the checker and airflow
     * off, and nothing that burns.
     */
    public static final EngineSettings DEFAULT = new EngineSettings(
            ExchangeSettings.DEFAULT,
            false,
            HeatSettings.DEFAULT,
            Double.POSITIVE_INFINITY,
            FireSettings.DEFAULT,
            HotspotSettings.DEFAULT);

    public EngineSettings {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(heat, "heat");
        Objects.requireNonNull(fire, "fire");
        if (Double.isNaN(airflowKpa) || airflowKpa < 0) {
            throw new IllegalArgumentException("airflowKpa must be >= 0: " + airflowKpa);
        }
        Objects.requireNonNull(hotspots, "hotspots");
    }

    /** Settings with the default hotspots. */
    public EngineSettings(
            ExchangeSettings exchange,
            boolean checkConservation,
            HeatSettings heat,
            double airflowKpa,
            FireSettings fire) {
        this(exchange, checkConservation, heat, airflowKpa, fire, HotspotSettings.DEFAULT);
    }

    /** Settings with the default fire threshold and hotspots. */
    public EngineSettings(ExchangeSettings exchange, boolean checkConservation, HeatSettings heat, double airflowKpa) {
        this(exchange, checkConservation, heat, airflowKpa, FireSettings.DEFAULT);
    }

    /** Settings with the default heat, fire threshold and hotspots, and airflow off. */
    public EngineSettings(ExchangeSettings exchange, boolean checkConservation) {
        this(exchange, checkConservation, HeatSettings.DEFAULT, Double.POSITIVE_INFINITY);
    }

    /** Settings with the default fire threshold and hotspots, and airflow off. */
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
                hotspots);
    }

    public EngineSettings withConservationCheck(boolean on) {
        return new EngineSettings(exchange, on, heat, airflowKpa, fire, hotspots);
    }

    public EngineSettings withHeat(HeatSettings settings) {
        return new EngineSettings(exchange, checkConservation, settings, airflowKpa, fire, hotspots);
    }

    /** These settings exporting the openings of edges at or above {@code kpa} (FLOW-001). */
    public EngineSettings withAirflow(double kpa) {
        return new EngineSettings(exchange, checkConservation, heat, kpa, fire, hotspots);
    }

    /** These settings with a fire oxygen threshold (FIRE-030). */
    public EngineSettings withFire(FireSettings settings) {
        return new EngineSettings(exchange, checkConservation, heat, airflowKpa, settings, hotspots);
    }

    /** These settings with other hotspot settings (FIRE-011, FIRE-016). */
    public EngineSettings withHotspots(HotspotSettings settings) {
        return new EngineSettings(exchange, checkConservation, heat, airflowKpa, fire, settings);
    }
}
