package io.github.west3436.zas.engine.gas;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A gas's optional phase data (CORE-017, D-58): its transition temperatures at 101.325 kPa and
 * the latent heats of those transitions. Nothing in R1 reads it; it is carried so that phase
 * changes (HEAT-018) can come later without a format break.
 *
 * <p>Every value is optional. A substance that sublimes at 101.325 kPa, such as CO₂, has no liquid
 * phase at that pressure, so it has a sublimation point instead of a melting and a boiling point.
 *
 * @param meltingPoint the melting point at 101.325 kPa, in K
 * @param boilingPoint the boiling point at 101.325 kPa, in K
 * @param sublimationPoint the sublimation point at 101.325 kPa, in K, of a substance with no liquid
 *     phase at that pressure
 * @param latentHeatFusion the latent heat of fusion, in J/mol
 * @param latentHeatVaporization the latent heat of vaporization, in J/mol
 * @param latentHeatSublimation the latent heat of sublimation, in J/mol
 */
public record GasPhaseData(
        OptionalDouble meltingPoint,
        OptionalDouble boilingPoint,
        OptionalDouble sublimationPoint,
        OptionalDouble latentHeatFusion,
        OptionalDouble latentHeatVaporization,
        OptionalDouble latentHeatSublimation) {

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException if no value is given, a value isn't finite and positive, a
     *     sublimation point comes with a melting or boiling point, or the melting point isn't below
     *     the boiling point
     */
    public GasPhaseData {
        positive("meltingPoint", meltingPoint);
        positive("boilingPoint", boilingPoint);
        positive("sublimationPoint", sublimationPoint);
        positive("latentHeatFusion", latentHeatFusion);
        positive("latentHeatVaporization", latentHeatVaporization);
        positive("latentHeatSublimation", latentHeatSublimation);
        if (meltingPoint.isEmpty()
                && boilingPoint.isEmpty()
                && sublimationPoint.isEmpty()
                && latentHeatFusion.isEmpty()
                && latentHeatVaporization.isEmpty()
                && latentHeatSublimation.isEmpty()) {
            throw new IllegalArgumentException("phase data needs at least one value");
        }
        if (sublimationPoint.isPresent() && (meltingPoint.isPresent() || boilingPoint.isPresent())) {
            throw new IllegalArgumentException(
                    "a gas that sublimes at 101.325 kPa has no melting or boiling point there");
        }
        if (meltingPoint.isPresent()
                && boilingPoint.isPresent()
                && !(meltingPoint.getAsDouble() < boilingPoint.getAsDouble())) {
            throw new IllegalArgumentException("meltingPoint " + meltingPoint.getAsDouble()
                    + " K must be below boilingPoint " + boilingPoint.getAsDouble() + " K");
        }
    }

    /** Phase data with a melting and a boiling point and both their latent heats. */
    public static GasPhaseData liquidRange(double melting, double boiling, double fusion, double vaporization) {
        return new GasPhaseData(
                OptionalDouble.of(melting),
                OptionalDouble.of(boiling),
                OptionalDouble.empty(),
                OptionalDouble.of(fusion),
                OptionalDouble.of(vaporization),
                OptionalDouble.empty());
    }

    /** Phase data of a substance that sublimes at 101.325 kPa. */
    public static GasPhaseData subliming(double sublimation, double latentHeat) {
        return new GasPhaseData(
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.of(sublimation),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.of(latentHeat));
    }

    private static void positive(String name, OptionalDouble value) {
        Objects.requireNonNull(value, name);
        if (value.isPresent() && (!Double.isFinite(value.getAsDouble()) || value.getAsDouble() <= 0)) {
            throw new IllegalArgumentException(name + " must be finite and positive, got " + value.getAsDouble());
        }
    }
}
