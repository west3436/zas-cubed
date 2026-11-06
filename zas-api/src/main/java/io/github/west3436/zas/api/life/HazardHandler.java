package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.ServerThread;

/**
 * A hazard of your own that a gas brings, such as radiation from a radioactive gas you define
 * (LIFE-032). Register one for a gas or a gas tag with {@link HazardHandlers#register}.
 *
 * <p><b>When it runs.</b> ZAS³ calls it at each breath check of a living entity, which is every 2 s
 * by default (LIFE-007), once for each gas it's registered for in the entity's breath or in the
 * atmosphere at its eyes. So a filter or a tank doesn't stop a handler being called: {@link
 * HazardExposure} tells it how much of the gas is around the entity and how much it breathes, and it
 * decides what each means. Nothing else calls it, so a query never runs it.
 *
 * <p><b>Who for.</b> Players, entities in {@code zas:breathers}, and any other living entity that
 * pressure and temperature can harm. Not for creative and spectator players (LIFE-037). Not for a check
 * that charges no time, as an entity's first does, or holds because the atmosphere at its eyes isn't
 * known yet (LIFE-048), and not once the entity has died.
 *
 * <p><b>What it does</b> is up to it: ZAS³ deals nothing on its behalf. Its damage is the handler's own,
 * so {@code [hazards]} settings and {@link io.github.west3436.zas.api.event.HazardTickEvent} don't
 * reach it, and any setting for turning it off or scaling it is yours to add. Scale what it does by
 * {@link HazardExposure#seconds()}, so that the check interval changes smoothness rather than speed.
 *
 * <p><b>Keep it cheap.</b> Checks run under a per-tick cap on the server thread, and yours runs for
 * every entity near the gas.
 *
 * <p><b>If it throws</b> (NFR-013). ZAS³ catches it and skips the handler for the rest of that check,
 * so what it did before throwing stands, and the other handlers still run. ZAS³ calls it again at the
 * next check, and logs the fault at most once a minute.
 */
@FunctionalInterface
public interface HazardHandler {
    /**
     * Applies the hazard to an entity for one check.
     *
     * <p>Thread: server thread only; ZAS³ calls it there.
     *
     * @param exposure the entity, the gas, and how much of it reaches the entity
     */
    @ServerThread
    void apply(HazardExposure exposure);
}
