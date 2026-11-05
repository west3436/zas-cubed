package io.github.west3436.zas.atmos.particle;

/** What a {@link GasJet} is, which says what its amount means and how far away it is sent. */
public enum GasJetKind {
    /** An extinguisher's spray (CLIENT-018). Its amount is how far it reaches from its nozzle, in blocks. */
    SPRAY(32);

    private final double range;

    GasJetKind(double range) {
        this.range = range;
    }

    /** How near a player must be to be sent one, in blocks. */
    public double range() {
        return range;
    }
}
