/**
 * Enclosure detection (GEO-020 to GEO-028; architecture §4.3): the cell topology over section
 * snapshots ({@link io.github.west3436.zas.engine.classify.SectionTopology}, with ids from {@link
 * io.github.west3436.zas.engine.classify.CellIds}), the bounded fill ({@link
 * io.github.west3436.zas.engine.classify.EnclosureFill}), turning enclosed regions into zones
 * ({@link io.github.west3436.zas.engine.classify.EnclosureZones}), breach resolution ({@link
 * io.github.west3436.zas.engine.classify.BreachResolver}) and the operations held while a
 * classification is pending ({@link io.github.west3436.zas.engine.classify.PendingClassifications}).
 *
 * <p>Everything here is pure engine logic on read-only views; the scheduler owns the section
 * mirror and decides when to classify.
 */
package io.github.west3436.zas.engine.classify;
