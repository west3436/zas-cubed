package io.github.west3436.zas.api.relocation;

/**
 * What a step of a move did (API-065). Every outcome but {@link #BEGUN}, {@link #MOVED} and {@link
 * #CANCELLED} means nothing changed: the zones are where they were and go on as they would have.
 *
 * @since 1.2.0
 */
public enum RelocationOutcome {
    /** {@link Relocations#begin}: the zones wholly inside the box are suspended for the move. */
    BEGUN,
    /**
     * {@link Relocations#complete}: the zones are at the destination, with their gas, temperature and
     * pins.
     */
    MOVED,
    /** {@link Relocations#cancel}: the zones carry on where they are. */
    CANCELLED,
    /**
     * A chunk the box, the destination or a zone in the box spans isn't loaded. At {@link
     * Relocations#begin} the ticket is over. At {@link Relocations#complete} it stays open, so complete
     * again once the chunks are loaded, or cancel.
     */
    UNLOADED,
    /**
     * {@link Relocations#begin}: a zone in the box is paused (GEO-030), or the whole dimension is
     * (OPS-005). The ticket is over. A {@link Relocations#complete} called before the begin was
     * applied answers this too, as do the two below.
     */
    PAUSED,
    /** {@link Relocations#begin}: a zone in the box is already being moved. The ticket is over. */
    BUSY,
    /**
     * {@link Relocations#begin}: the zones in the box have more cells than the server allows in one
     * move. The ticket is over.
     */
    TOO_LARGE,
    /**
     * The ticket was not completed in time, and was cancelled: its zones carry on where they are.
     * {@link Relocations#complete} and {@link Relocations#cancel} answer this after the server's
     * time limit.
     */
    TIMED_OUT,
    /**
     * {@link Relocations#complete}, {@link Relocations#cancel}: the ticket is over already. It was
     * completed or cancelled, its begin was refused (its {@link RelocationTicket#begun()} says why),
     * or the server stopped or rebuilt the dimension's simulation since.
     */
    ENDED,
    /** ZAS³ is not installed: nothing happened (API-009, D-17). */
    ABSENT
}
