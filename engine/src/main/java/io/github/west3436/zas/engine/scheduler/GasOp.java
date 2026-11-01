package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import java.util.Objects;

/**
 * One queued gas operation (architecture §2.3, §3.2): an addition or removal of gas at a block
 * position, carried in a {@link Batch} and applied in phase 3 of an atmos tick (CORE-040).
 *
 * <p>Plain, immutable data. Moles are indexed by the gas registry the engine runs with, which is
 * fixed for the server's lifetime.
 *
 * @param seq the dimension-wide sequence number, assigned when the operation was queued; every
 *     operation is processed in sequence order, exactly once (CORE-048, §3.5)
 * @param kind addition or removal
 * @param block the target block position ({@link SectionKeys#block})
 * @param moles for an addition, the moles offered per gas; for a removal, the moles wanted; copied
 * @param temperature for an addition, the gas's temperature in K; ignored for a removal
 * @param category the ledger category the change is recorded under (CORE-036)
 * @param triggersZone whether the operation may create a zone when its target is enclosed and
 *     unzoned (CORE-051, CORE-055); breathing, for one, doesn't (LIFE-005)
 * @param source who queued it, for logs
 * @param sub the sub-cell of the position the operation acts on, as resolved from an exact point
 *     (API-015, GEO-004), or -1 for the position's largest cell. If the position no longer has
 *     that many cells when the operation applies, it acts on the largest. Not saved: an operation
 *     restored from a chunk acts on the largest cell.
 */
public record GasOp(
        long seq,
        Kind kind,
        long block,
        double[] moles,
        double temperature,
        LedgerCategory category,
        boolean triggersZone,
        String source,
        int sub) {

    /** What an operation does. */
    public enum Kind {
        ADD,
        REMOVE
    }

    /** An operation on a position's largest cell. */
    public GasOp(
            long seq,
            Kind kind,
            long block,
            double[] moles,
            double temperature,
            LedgerCategory category,
            boolean triggersZone,
            String source) {
        this(seq, kind, block, moles, temperature, category, triggersZone, source, -1);
    }

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if an amount or the temperature is negative or not finite
     */
    public GasOp {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(source, "source");
        moles = moles.clone();
        for (double n : moles) {
            if (!Double.isFinite(n) || n < 0) {
                throw new IllegalArgumentException("moles must be finite and >= 0: " + n);
            }
        }
        if (!Double.isFinite(temperature) || temperature < 0) {
            throw new IllegalArgumentException("temperature must be finite and >= 0 K: " + temperature);
        }
        if (sub < -1) {
            throw new IllegalArgumentException("sub-cell must be -1 or >= 0: " + sub);
        }
    }

    /** An API addition (CORE-055: API calls trigger zones). */
    public static GasOp add(long seq, long block, double[] moles, double temperature, String source) {
        return new GasOp(seq, Kind.ADD, block, moles, temperature, LedgerCategory.API, true, source);
    }

    /** An API removal. */
    public static GasOp remove(long seq, long block, double[] moles, String source) {
        return new GasOp(seq, Kind.REMOVE, block, moles, 0, LedgerCategory.API, true, source);
    }

    @Override
    public double[] moles() {
        return moles.clone();
    }

    /** The moles of one gas, without copying. */
    public double moles(int gas) {
        return moles[gas];
    }

    /** The number of gases in {@link #moles()}. */
    public int gasCount() {
        return moles.length;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GasOp g
                && seq == g.seq
                && kind == g.kind
                && block == g.block
                && java.util.Arrays.equals(moles, g.moles)
                && Double.compare(temperature, g.temperature) == 0
                && category == g.category
                && triggersZone == g.triggersZone
                && source.equals(g.source)
                && sub == g.sub;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(seq) * 31 + Long.hashCode(block);
    }

    @Override
    public String toString() {
        return "GasOp#" + seq + "[" + kind + " at " + SectionKeys.blockX(block) + "," + SectionKeys.blockY(block) + ","
                + SectionKeys.blockZ(block) + (sub >= 0 ? " sub-cell " + sub : "") + ", "
                + java.util.Arrays.toString(moles) + ", " + source + "]";
    }
}
