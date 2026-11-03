package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;

/**
 * A stand-in for the data-driven reactions (FIRE-001, FIRE-003, #41) until they land: FIRE-003's
 * three fuels burning with O₂ to H₂O and CO₂, with their flammability limits in air and their
 * autoignition points. Hotspots use it through {@link Combustion}; the reactions replace it.
 *
 * <p>A fuel is within its limits when its mole fraction of the whole mixture is. A parcel burns
 * each such fuel in turn, as far as the parcel's O₂ goes, and the heat goes into the whole
 * mixture. Nothing burns where the fire oxygen threshold fails (FIRE-030).
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

    private final int[] fuel;
    private final int oxygen;
    private final int water;
    private final int carbonDioxide;

    /** @param registry the gases; fuels whose products it lacks are left out */
    public SimpleCombustion(GasRegistry registry) {
        this.fuel = new int[FUELS.length];
        this.oxygen = registry.indexOf(FireSettings.OXYGEN);
        this.water = registry.indexOf("zas:water_vapor");
        this.carbonDioxide = registry.indexOf("zas:carbon_dioxide");
        for (int i = 0; i < FUELS.length; i++) {
            int f = registry.indexOf(FUELS[i].id());
            boolean products =
                    (FUELS[i].water() == 0 || water >= 0) && (FUELS[i].carbonDioxide() == 0 || carbonDioxide >= 0);
            fuel[i] = products ? f : -1;
        }
    }

    private boolean within(int i, GasMixture m) {
        int f = fuel[i];
        if (f < 0 || m.isEmpty()) {
            return false;
        }
        double share = m.moles(f) / m.totalMoles();
        return share >= FUELS[i].lower() && share <= FUELS[i].upper();
    }

    @Override
    public boolean flammable(GasMixture mixture, FireSettings fire) {
        if (!fire.sustains(mixture)) {
            return false;
        }
        for (int i = 0; i < FUELS.length; i++) {
            if (within(i, mixture)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double autoignition(GasMixture mixture, FireSettings fire) {
        double lowest = Double.POSITIVE_INFINITY;
        if (!fire.sustains(mixture)) {
            return lowest;
        }
        for (int i = 0; i < FUELS.length; i++) {
            if (within(i, mixture)) {
                lowest = Math.min(lowest, FUELS[i].autoignition());
            }
        }
        return lowest;
    }

    @Override
    public double[] burn(GasMixture mixture, double litres, FireSettings fire) {
        double[] change = new double[mixture.registry().size()];
        if (!flammable(mixture, fire)) {
            return change;
        }
        double share = Math.min(1, litres / mixture.volume());
        double o2 = mixture.moles(oxygen) * share;
        boolean[] burns = new boolean[FUELS.length];
        for (int i = 0; i < FUELS.length; i++) {
            burns[i] = within(i, mixture);
        }
        double heat = 0;
        for (int i = 0; i < FUELS.length && o2 > 0; i++) {
            if (!burns[i]) {
                continue;
            }
            Fuel f = FUELS[i];
            double burned = Math.min(mixture.moles(fuel[i]) * share, o2 / f.oxygen());
            if (!(burned > 0)) {
                continue;
            }
            double usedFuel = mixture.removeGas(fuel[i], burned);
            double usedO2 = mixture.removeGas(oxygen, usedFuel * f.oxygen());
            o2 -= usedO2;
            change[fuel[i]] -= usedFuel;
            change[oxygen] -= usedO2;
            if (f.water() > 0 && mixture.addMoles(water, usedFuel * f.water())) {
                change[water] += usedFuel * f.water();
            }
            if (f.carbonDioxide() > 0 && mixture.addMoles(carbonDioxide, usedFuel * f.carbonDioxide())) {
                change[carbonDioxide] += usedFuel * f.carbonDioxide();
            }
            heat += usedFuel * f.heat();
        }
        if (heat > 0) {
            mixture.addThermalEnergy(heat);
        }
        return change;
    }
}
