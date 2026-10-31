package io.github.west3436.zas.api.atmosphere;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.gas.GasMixture;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The atmosphere at one point, as of the last applied atmos tick (API-010,
 * API-046). It is an immutable snapshot: it never changes after it is
 * returned, whatever later ticks do. Every method may be called from any
 * thread.
 *
 * @param state what kind of gas the point holds
 * @param zone the zone, present only when {@code state} is
 *     {@link AtmosphereState#ZONED} or {@link AtmosphereState#PAUSED}
 * @param mixture the gas: the zone's mixture, or the ambient's for one cell
 */
@AnyThread
public record AtmosphereSample(AtmosphereState state, Optional<ZoneId> zone, GasMixture mixture) {
    /**
     * Checks the arguments.
     *
     * @throws IllegalArgumentException if a zone is given for an ambient or
     *     unresolved point, or missing for a zoned or paused one
     * @throws NullPointerException if an argument is null
     */
    public AtmosphereSample {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(mixture, "mixture");
        boolean zoned = state == AtmosphereState.ZONED || state == AtmosphereState.PAUSED;
        if (zoned != zone.isPresent()) {
            throw new IllegalArgumentException(
                    "a " + state + " sample " + (zoned ? "needs" : "can't have") + " a zone id");
        }
    }

    /**
     * A sample of an ambient point.
     *
     * <p>Thread: any.
     *
     * @param mixture the ambient gas for one cell
     * @return a sample with state {@link AtmosphereState#AMBIENT} and no zone
     */
    @AnyThread
    public static AtmosphereSample ambient(GasMixture mixture) {
        return new AtmosphereSample(AtmosphereState.AMBIENT, Optional.empty(), mixture);
    }

    /**
     * The composition: each gas's mole fraction.
     *
     * <p>Thread: any.
     *
     * @return an unmodifiable map from gas id to fraction
     */
    @AnyThread
    public Map<ResourceLocation, Double> composition() {
        return mixture.composition();
    }

    /**
     * The partial pressure of every gas present.
     *
     * <p>Thread: any.
     *
     * @return an unmodifiable map from gas id to kPa
     */
    @AnyThread
    public Map<ResourceLocation, Double> partialPressures() {
        return mixture.partialPressures();
    }

    /**
     * The total pressure.
     *
     * <p>Thread: any.
     *
     * @return the pressure in kPa
     */
    @AnyThread
    public double pressure() {
        return mixture.pressure();
    }

    /**
     * The temperature.
     *
     * <p>Thread: any.
     *
     * @return the temperature in kelvin
     */
    @AnyThread
    public double temperature() {
        return mixture.temperature();
    }
}
