package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;

/**
 * A stand-in for the data-driven reactions (FIRE-001, FIRE-003, #41) until they land: FIRE-003's
 * three fuels burning with O₂ to H₂O and CO₂, their flammability limits in air and autoignition
 * points, and FIRE-030's oxygen threshold. Hotspots use it through {@link Combustion}; the
 * reactions replace it.
 *
 * <p>A fuel is within its limits when its mole fraction of the whole mixture is. A parcel burns
 * each such fuel in turn, as far as the parcel's O₂ goes, and the heat goes into the whole
 * mixture.
 */
public final class SimpleCombustion implements Combustion {
    /** One fuel: what it burns to, per mol of fuel. */
    private record Fuel(
            String id,
            double lower,
            double upper,
            double autoignition,
            double heat,
            double oxygen,
            double water,
            double carbonDioxide) {}

    private static final Fuel[] FUELS = {
        new Fuel("zas:hydrogen", 0.04, 0.75, 858, 242_000, 0.5, 1, 0),
        new Fuel("zas:methane", 0.05, 0.15, 810, 802_000, 2, 2, 1),
        new Fuel("zas:carbon_monoxide", 0.125, 0.74, 882, 283_000, 0.5, 0, 1),
    };

    private final double minOxygenFraction;
    private final double minOxygenKpa;
    private final int[] fuel;
    private final int oxygen;
    private final int water;
    private final int carbonDioxide;

    /**
     * @param registry the gases; fuels or products it lacks are left out
     * @param minOxygenFraction the least O₂ mole fraction a fire needs (FIRE-030)
     * @param minOxygenKpa the least O₂ partial pressure a fire needs, kPa (FIRE-030)
     */
    public SimpleCombustion(GasRegistry registry, double minOxygenFraction, double minOxygenKpa) {
        this.minOxygenFraction = minOxygenFraction;
        this.minOxygenKpa = minOxygenKpa;
        this.fuel = new int[FUELS.length];
        for (int i = 0; i < FUELS.length; i++) {
            fuel[i] = registry.indexOf(FUELS[i].id());
        }
        this.oxygen = registry.indexOf("zas:oxygen");
        this.water = registry.indexOf("zas:water_vapor");
        this.carbonDioxide = registry.indexOf("zas:carbon_dioxide");
    }

    /** With FIRE-030's defaults: an O₂ fraction of 0.14 and 10 kPa. */
    public SimpleCombustion(GasRegistry registry) {
        this(registry, 0.14, 10);
    }

    private boolean oxygenEnough(GasMixture m) {
        if (oxygen < 0 || m.totalMoles() <= 0) {
            return false;
        }
        return m.moles(oxygen) / m.totalMoles() >= minOxygenFraction && m.partialPressure(oxygen) >= minOxygenKpa;
    }

    private boolean burns(int i, GasMixture m) {
        int f = fuel[i];
        if (f < 0 || (FUELS[i].water() > 0 && water < 0) || (FUELS[i].carbonDioxide() > 0 && carbonDioxide < 0)) {
            return false;
        }
        double share = m.moles(f) / m.totalMoles();
        return share >= FUELS[i].lower() && share <= FUELS[i].upper();
    }

    @Override
    public boolean flammable(GasMixture mixture) {
        if (!oxygenEnough(mixture)) {
            return false;
        }
        for (int i = 0; i < FUELS.length; i++) {
            if (burns(i, mixture)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double autoignition(GasMixture mixture) {
        double lowest = Double.POSITIVE_INFINITY;
        if (!oxygenEnough(mixture)) {
            return lowest;
        }
        for (int i = 0; i < FUELS.length; i++) {
            if (burns(i, mixture)) {
                lowest = Math.min(lowest, FUELS[i].autoignition());
            }
        }
        return lowest;
    }

    @Override
    public double[] burn(GasMixture mixture, double litres) {
        double[] change = new double[mixture.registry().size()];
        if (!flammable(mixture)) {
            return change;
        }
        double share = Math.min(1, litres / mixture.volume());
        double o2 = mixture.moles(oxygen) * share;
        boolean[] within = new boolean[FUELS.length];
        for (int i = 0; i < FUELS.length; i++) {
            within[i] = burns(i, mixture);
        }
        double heat = 0;
        for (int i = 0; i < FUELS.length && o2 > 0; i++) {
            if (!within[i]) {
                continue;
            }
            Fuel f = FUELS[i];
            double burned = Math.min(mixture.moles(fuel[i]) * share, o2 / f.oxygen());
            if (!(burned > 0)) {
                continue;
            }
            double usedO2 = Math.min(burned * f.oxygen(), mixture.moles(oxygen));
            o2 -= usedO2;
            change[fuel[i]] -= mixture.removeGas(fuel[i], burned);
            change[oxygen] -= mixture.removeGas(oxygen, usedO2);
            if (f.water() > 0) {
                mixture.addMoles(water, burned * f.water());
                change[water] += burned * f.water();
            }
            if (f.carbonDioxide() > 0) {
                mixture.addMoles(carbonDioxide, burned * f.carbonDioxide());
                change[carbonDioxide] += burned * f.carbonDioxide();
            }
            heat += burned * f.heat();
        }
        if (heat > 0) {
            mixture.addThermalEnergy(heat);
        }
        return change;
    }
}
