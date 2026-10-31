package io.github.west3436.zas.engine.scheduler;

import java.util.Arrays;

/**
 * An exchange component of one atmos tick (architecture §3.3): zones whose gas changed this tick
 * and that are linked by edges between them, together with zones that merged into them or
 * dissolved. Ambient edges don't link zones, because the ambient never changes (CORE-038).
 *
 * <p>The server thread discards a component when the lifecycle generation of any chunk it spans
 * changed between batch close and apply (CORE-070). The whole component is then restored,
 * which keeps conservation exact, and its operations run again (CORE-074).
 *
 * @param index the component's index in {@link Result#components()}
 * @param zoneIds the zones it restores, as they were before this tick's gas operations, ascending
 * @param chunks the chunks it spans, ascending
 * @param opSeqs the operations applied to its zones, ascending
 */
public record Component(int index, long[] zoneIds, long[] chunks, long[] opSeqs) {
    public Component {
        zoneIds = zoneIds.clone();
        chunks = chunks.clone();
        opSeqs = opSeqs.clone();
    }

    @Override
    public long[] zoneIds() {
        return zoneIds.clone();
    }

    @Override
    public long[] chunks() {
        return chunks.clone();
    }

    @Override
    public long[] opSeqs() {
        return opSeqs.clone();
    }

    /** Whether an operation belongs to this component. */
    public boolean hasOp(long seq) {
        return Arrays.binarySearch(opSeqs, seq) >= 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Component c
                && index == c.index
                && Arrays.equals(zoneIds, c.zoneIds)
                && Arrays.equals(chunks, c.chunks)
                && Arrays.equals(opSeqs, c.opSeqs);
    }

    @Override
    public int hashCode() {
        return index * 31 + Arrays.hashCode(zoneIds);
    }

    @Override
    public String toString() {
        return "Component#" + index + "[zones " + Arrays.toString(zoneIds) + ", " + chunks.length + " chunks, ops "
                + Arrays.toString(opSeqs) + "]";
    }
}
