package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.gas.GasConstants;
import java.util.Arrays;

/**
 * An immutable copy of one zone as of an atmos tick, for the published {@link AtmosSnapshot}
 * (API-011, API-046). Moles are indexed by the snapshot's gas registry.
 *
 * @param id the zone's id (CORE-026)
 * @param volume its gas volume in L (CORE-027)
 * @param cellCount its number of cells
 * @param moles its moles per gas
 * @param temperature its temperature in K
 * @param zoneEdges its number of edges to other zones
 * @param ambientEdges its number of edges to the ambient: 0 or 1; 1 means breached
 * @param openFaces the open cell faces across all its edges (CORE-037)
 * @param paused whether it is paused (GEO-030); always false until paused zones exist (#27)
 */
public record ZoneView(
        long id,
        double volume,
        int cellCount,
        double[] moles,
        double temperature,
        int zoneEdges,
        int ambientEdges,
        int openFaces,
        boolean paused) {

    public ZoneView {
        moles = moles.clone();
    }

    @Override
    public double[] moles() {
        return moles.clone();
    }

    /** The moles of one gas, without copying. */
    public double moles(int gas) {
        return moles[gas];
    }

    /** The number of gases. */
    public int gasCount() {
        return moles.length;
    }

    /** The total moles. */
    public double totalMoles() {
        double total = 0;
        for (double n : moles) {
            total += n;
        }
        return total;
    }

    /** The pressure in kPa: p = nRT/V. */
    public double pressure() {
        return totalMoles() * GasConstants.R * temperature / volume;
    }

    /** Whether the zone is open to the ambient. */
    public boolean breached() {
        return ambientEdges > 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ZoneView z
                && id == z.id
                && Double.compare(volume, z.volume) == 0
                && cellCount == z.cellCount
                && Arrays.equals(moles, z.moles)
                && Double.compare(temperature, z.temperature) == 0
                && zoneEdges == z.zoneEdges
                && ambientEdges == z.ambientEdges
                && openFaces == z.openFaces
                && paused == z.paused;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id) * 31 + Arrays.hashCode(moles);
    }

    @Override
    public String toString() {
        return "ZoneView#" + id + "[" + cellCount + " cells, " + volume + " L, " + Arrays.toString(moles) + " mol, "
                + temperature + " K, " + zoneEdges + " zone edges, " + ambientEdges + " ambient edges, " + openFaces
                + " open faces" + (paused ? ", paused" : "") + "]";
    }
}
