package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.fire.BlockIgnitionSettings;
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
 * @param reactions the server's reactions (FIRE-001), compiled against the engine's gases; hotspots
 *     burn with them (FIRE-011)
 * @param hotspots hotspot spread (FIRE-016)
 * @param blockIgnition how hot gas and gas fires set blocks alight (FIRE-033, FIRE-034)
 * @param dimensionPaused whether the whole dimension is paused (OPS-005): every zone behaves as a
 *     paused zone, and nothing is classified, so no zone forms, until a batch says otherwise
 */
public record EngineSettings(
        ExchangeSettings exchange,
        boolean checkConservation,
        HeatSettings heat,
        double airflowKpa,
        FireSettings fire,
        ReactionSet reactions,
        HotspotSettings hotspots,
        BlockIgnitionSettings blockIgnition,
        boolean dimensionPaused) {
    /**
     * The default exchange, heat, fire threshold, hotspot spread and block ignition at a 1 s period,
     * with the checker, airflow and reactions off, and the dimension running.
     */
    public static final EngineSettings DEFAULT = new EngineSettings(
            ExchangeSettings.DEFAULT,
            false,
            HeatSettings.DEFAULT,
            Double.POSITIVE_INFINITY,
            FireSettings.DEFAULT,
            ReactionSet.EMPTY,
            HotspotSettings.DEFAULT,
            BlockIgnitionSettings.DEFAULT);

    public EngineSettings {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(heat, "heat");
        Objects.requireNonNull(fire, "fire");
        Objects.requireNonNull(reactions, "reactions");
        Objects.requireNonNull(hotspots, "hotspots");
        Objects.requireNonNull(blockIgnition, "blockIgnition");
        if (Double.isNaN(airflowKpa) || airflowKpa < 0) {
            throw new IllegalArgumentException("airflowKpa must be >= 0: " + airflowKpa);
        }
    }

    /** Settings for a dimension that is running. */
    public EngineSettings(
            ExchangeSettings exchange,
            boolean checkConservation,
            HeatSettings heat,
            double airflowKpa,
            FireSettings fire,
            ReactionSet reactions,
            HotspotSettings hotspots,
            BlockIgnitionSettings blockIgnition) {
        this(exchange, checkConservation, heat, airflowKpa, fire, reactions, hotspots, blockIgnition, false);
    }

    /** Settings with the default block ignition. */
    public EngineSettings(
            ExchangeSettings exchange,
            boolean checkConservation,
            HeatSettings heat,
            double airflowKpa,
            FireSettings fire,
            ReactionSet reactions,
            HotspotSettings hotspots) {
        this(exchange, checkConservation, heat, airflowKpa, fire, reactions, hotspots, BlockIgnitionSettings.DEFAULT);
    }

    /** Settings with the default hotspot spread. */
    public EngineSettings(
            ExchangeSettings exchange,
            boolean checkConservation,
            HeatSettings heat,
            double airflowKpa,
            FireSettings fire,
            ReactionSet reactions) {
        this(exchange, checkConservation, heat, airflowKpa, fire, reactions, HotspotSettings.DEFAULT);
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
                reactions,
                hotspots,
                blockIgnition,
                dimensionPaused);
    }

    public EngineSettings withConservationCheck(boolean on) {
        return new EngineSettings(
                exchange, on, heat, airflowKpa, fire, reactions, hotspots, blockIgnition, dimensionPaused);
    }

    public EngineSettings withHeat(HeatSettings settings) {
        return new EngineSettings(
                exchange,
                checkConservation,
                settings,
                airflowKpa,
                fire,
                reactions,
                hotspots,
                blockIgnition,
                dimensionPaused);
    }

    /** These settings exporting the openings of edges at or above {@code kpa} (FLOW-001). */
    public EngineSettings withAirflow(double kpa) {
        return new EngineSettings(
                exchange, checkConservation, heat, kpa, fire, reactions, hotspots, blockIgnition, dimensionPaused);
    }

    /** These settings with a fire oxygen threshold (FIRE-030). */
    public EngineSettings withFire(FireSettings settings) {
        return new EngineSettings(
                exchange,
                checkConservation,
                heat,
                airflowKpa,
                settings,
                reactions,
                hotspots,
                blockIgnition,
                dimensionPaused);
    }

    /** These settings with a set of reactions (FIRE-001). */
    public EngineSettings withReactions(ReactionSet set) {
        return new EngineSettings(
                exchange, checkConservation, heat, airflowKpa, fire, set, hotspots, blockIgnition, dimensionPaused);
    }

    /** These settings with hotspot spread settings (FIRE-016). */
    public EngineSettings withHotspots(HotspotSettings settings) {
        return new EngineSettings(
                exchange,
                checkConservation,
                heat,
                airflowKpa,
                fire,
                reactions,
                settings,
                blockIgnition,
                dimensionPaused);
    }

    /** These settings with block ignition settings (FIRE-033, FIRE-034). */
    public EngineSettings withBlockIgnition(BlockIgnitionSettings settings) {
        return new EngineSettings(
                exchange, checkConservation, heat, airflowKpa, fire, reactions, hotspots, settings, dimensionPaused);
    }

    /** These settings with the whole dimension paused or running (OPS-005). */
    public EngineSettings withDimensionPaused(boolean paused) {
        return new EngineSettings(
                exchange, checkConservation, heat, airflowKpa, fire, reactions, hotspots, blockIgnition, paused);
    }
}
