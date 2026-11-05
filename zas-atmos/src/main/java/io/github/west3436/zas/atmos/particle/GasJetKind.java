package io.github.west3436.zas.atmos.particle;

/** What a {@link GasJet} is, which says what its amount means and how it is sent. */
public enum GasJetKind {
    /** An extinguisher's spray (CLIENT-018). Its amount is how far it reaches from its nozzle, in blocks. */
    SPRAY(32, false),
    /** A tank leaking (CLIENT-018). Its amount is the moles it let out this atmos tick. */
    LEAK(32, true),
    /** A tank or canister rupturing, or a pipe bursting (CLIENT-018). Its amount is the moles it let out. */
    BURST(32, false),
    /**
     * An open pipe end moving gas (CLIENT-019). Its amount is the net moles per second: positive out of
     * the pipe, negative into it. Its direction is the way the end looks out, whichever way the gas goes.
     */
    OPEN_END(16, true);

    private final double range;
    private final boolean flowing;

    GasJetKind(double range, boolean flowing) {
        this.range = range;
        this.flowing = flowing;
    }

    /** How near a player must be to be sent one, in blocks. */
    public double range() {
        return range;
    }

    /**
     * Whether a block sends it every atmos tick for as long as gas flows, so that a player's share of
     * them is capped ({@link SendLimiter}). A burst marks one event, and a spray lasts only while a
     * player holds it down, so neither is.
     */
    public boolean flowing() {
        return flowing;
    }
}
