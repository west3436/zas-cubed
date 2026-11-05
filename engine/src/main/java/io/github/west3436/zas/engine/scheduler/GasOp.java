package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import java.util.Objects;

/**
 * One queued gas operation (architecture §2.3, §3.2): an addition or removal of gas at a block
 * position, carried in a {@link Batch} and applied in phase 3 of an atmos tick (CORE-040). A pin
 * or an unpin (API-017, API-050) travels the same way and moves no gas, and so does an addition or
 * removal of heat (API-013), which moves only energy.
 *
 * <p>Plain, immutable data. Moles are indexed by the gas registry the engine runs with, which is
 * fixed for the server's lifetime.
 *
 * @param seq the dimension-wide sequence number, assigned when the operation was queued; every
 *     operation is processed in sequence order, exactly once (CORE-048, §3.5)
 * @param kind addition or removal
 * @param block the target block position ({@link SectionKeys#block})
 * @param moles for an addition, the moles offered per gas; for a removal, the moles wanted; for a
 *     pin or unpin, none; copied
 * @param temperature for an addition, the gas's temperature in K; ignored for a removal
 * @param category the ledger category the change is recorded under (CORE-036)
 * @param triggersZone whether the operation may create a zone when its target is enclosed and
 *     unzoned (CORE-051, CORE-055); breathing, for one, doesn't (LIFE-005)
 * @param source who queued it, for logs; for a pin or unpin, the pin's owner
 * @param sub the sub-cell of the position the operation acts on, as resolved from an exact point
 *     (API-015, GEO-004), or -1 for the position's largest cell. If the position no longer has
 *     that many cells when the operation applies, it acts on the largest. Not saved: an operation
 *     restored from a chunk acts on the largest cell.
 * @param energy for an addition or removal of heat, the joules offered or wanted; otherwise 0
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
        int sub,
        double energy) {

    /** What an operation does. */
    public enum Kind {
        ADD,
        REMOVE,
        /**
         * Pins the zone at the target under the source as owner (API-017, API-050), forming one if
         * the target is enclosed and unzoned. It moves no gas, and it is applied before the undo
         * point, as zones formed by classification are, so a rejected component never undoes it.
         */
        PIN,
        /** Removes the source's pins from every zone in memory (API-017). Its target is unused. */
        UNPIN,
        /** Adds {@link #energy()} joules of heat at the target (API-013); it moves no gas. */
        ADD_HEAT,
        /** Removes up to {@link #energy()} joules of heat at the target (API-013); it moves no gas. */
        REMOVE_HEAT,
        /**
         * Ignites a flammable zone in the target's cell or a neighbouring cell (FIRE-014): a hotspot
         * starts there, in the hotspot phase. It moves no gas itself and never forms a zone.
         */
        IGNITE,
        /**
         * Resets the zone at the target to the ambient by operator command (OPS-013): its cells
         * become unzoned, and its gas is removed and attributed to the source. It asks for no
         * amounts and never forms a zone. It is applied ahead of the other operations and the undo
         * point, so what they do sees the cells unzoned and a rejected component never undoes it.
         */
        RESET;

        /** Whether the kind is a pin or an unpin, which moves no gas. */
        public boolean isPin() {
            return this == PIN || this == UNPIN;
        }

        /** Whether the kind adds or removes heat, which moves no gas. */
        public boolean isHeat() {
            return this == ADD_HEAT || this == REMOVE_HEAT;
        }
    }

    /** An operation that moves no heat. */
    public GasOp(
            long seq,
            Kind kind,
            long block,
            double[] moles,
            double temperature,
            LedgerCategory category,
            boolean triggersZone,
            String source,
            int sub) {
        this(seq, kind, block, moles, temperature, category, triggersZone, source, sub, 0);
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
        this(seq, kind, block, moles, temperature, category, triggersZone, source, -1, 0);
    }

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if an amount, the temperature or the energy is negative or
     *     not finite
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
        if (!Double.isFinite(energy) || energy < 0) {
            throw new IllegalArgumentException("energy must be finite and >= 0 J: " + energy);
        }
    }

    /** An API addition (CORE-055: API calls trigger zones). */
    public static GasOp add(long seq, long block, double[] moles, double temperature, String source) {
        return new GasOp(seq, Kind.ADD, block, moles, temperature, LedgerCategory.API, true, source);
    }

    /** A pin at a position's cell {@code sub} (-1 for its largest), under {@code owner} (API-050). */
    public static GasOp pin(long seq, long block, int sub, String owner) {
        return new GasOp(seq, Kind.PIN, block, new double[0], 0, LedgerCategory.API, true, owner, sub);
    }

    /** An unpin of every zone {@code owner} pins (API-017). */
    public static GasOp unpin(long seq, String owner) {
        return new GasOp(seq, Kind.UNPIN, 0, new double[0], 0, LedgerCategory.API, false, owner, -1);
    }

    /** An ignition at a position by {@code source} (FIRE-014). */
    public static GasOp ignite(long seq, long block, String source) {
        return new GasOp(seq, Kind.IGNITE, block, new double[0], 0, LedgerCategory.REACTION, false, source, -1);
    }

    /** A reset of the zone at a position's cell {@code sub} (-1 for its largest), by {@code source} (OPS-013). */
    public static GasOp reset(long seq, long block, int sub, String source) {
        return new GasOp(seq, Kind.RESET, block, new double[0], 0, LedgerCategory.OPERATOR, false, source, sub);
    }

    /** An API removal. */
    public static GasOp remove(long seq, long block, double[] moles, String source) {
        return new GasOp(seq, Kind.REMOVE, block, moles, 0, LedgerCategory.API, true, source);
    }

    /**
     * An addition ({@code add}) or removal of {@code joules} of heat at a position's cell {@code
     * sub} (-1 for its largest), as an API call (API-013): it may form a zone as {@code
     * triggersZone} says (CORE-055).
     */
    public static GasOp heat(
            long seq, long block, int sub, boolean add, double joules, boolean triggersZone, String source) {
        return new GasOp(
                seq,
                add ? Kind.ADD_HEAT : Kind.REMOVE_HEAT,
                block,
                new double[0],
                0,
                LedgerCategory.API,
                triggersZone,
                source,
                sub,
                joules);
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
                && sub == g.sub
                && Double.compare(energy, g.energy) == 0;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(seq) * 31 + Long.hashCode(block);
    }

    @Override
    public String toString() {
        if (kind == Kind.UNPIN) {
            return "GasOp#" + seq + "[UNPIN " + source + "]";
        }
        if (kind == Kind.IGNITE) {
            return "GasOp#" + seq + "[IGNITE at " + SectionKeys.blockX(block) + "," + SectionKeys.blockY(block) + ","
                    + SectionKeys.blockZ(block) + ", " + source + "]";
        }
        if (kind.isHeat()) {
            return "GasOp#" + seq + "[" + kind + " at " + SectionKeys.blockX(block) + "," + SectionKeys.blockY(block)
                    + "," + SectionKeys.blockZ(block) + (sub >= 0 ? " sub-cell " + sub : "") + ", " + energy + " J, "
                    + source + "]";
        }
        return "GasOp#" + seq + "[" + kind + " at " + SectionKeys.blockX(block) + "," + SectionKeys.blockY(block) + ","
                + SectionKeys.blockZ(block) + (sub >= 0 ? " sub-cell " + sub : "") + ", "
                + java.util.Arrays.toString(moles) + ", " + source + "]";
    }
}
