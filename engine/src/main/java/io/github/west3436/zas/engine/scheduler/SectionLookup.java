package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;

/**
 * Read access to the worker's section mirror (architecture §4.3), for the cell topology and
 * classification. Answers reflect the mirror as of the current point in the atmos tick. Worker
 * thread only.
 */
public interface SectionLookup {
    /** The mirrored snapshot of a section ({@link SectionKeys}), or null if the mirror lacks it. */
    SectionSnapshot section(long key);

    /**
     * Whether a section is known to be unloaded: its chunk unloaded, or it was asked for and
     * couldn't be captured, and no snapshot of it arrived since. A fill reaching such a section
     * is unresolved (GEO-024); one reaching a section that is merely missing asks for it.
     */
    boolean isUnloaded(long key);

    /** The lowest section y of the dimension. */
    int minSectionY();

    /** The highest section y of the dimension. */
    int maxSectionY();
}
