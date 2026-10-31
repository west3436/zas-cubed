package io.github.west3436.zas.engine.scheduler;

import java.util.Arrays;

/**
 * Gas that entered or left the simulation through the open air (CORE-058): an addition released
 * to the ambient (CORE-053) or a removal the ambient supplied (CORE-057). The server thread turns
 * it into an API event when the result is applied (API-032).
 *
 * @param seq the operation's sequence number
 * @param block the operation's block position
 * @param added true for an addition, false for a removal
 * @param moles the moles per gas that went to, or came from, the ambient
 * @param temperature their temperature in K
 */
public record OpenAirEvent(long seq, long block, boolean added, double[] moles, double temperature) {
    public OpenAirEvent {
        moles = moles.clone();
    }

    @Override
    public double[] moles() {
        return moles.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OpenAirEvent e
                && seq == e.seq
                && block == e.block
                && added == e.added
                && Arrays.equals(moles, e.moles)
                && Double.compare(temperature, e.temperature) == 0;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(seq);
    }

    @Override
    public String toString() {
        return "OpenAirEvent#" + seq + "[" + (added ? "added " : "removed ") + Arrays.toString(moles) + "]";
    }
}
