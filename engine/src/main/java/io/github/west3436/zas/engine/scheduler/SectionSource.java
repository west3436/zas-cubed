package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;

/**
 * A section copied on the server thread, decoded on the atmos worker (architecture §3.4). The
 * platform's capture keeps a private copy of the block states, so decoding never touches the live
 * world (CORE-049).
 */
public interface SectionSource {
    /** The section key ({@link SectionKeys}). */
    long key();

    /** Decodes the copy into geometry ids. Called on the worker, once per batch. */
    SectionSnapshot decode();

    /** A source that is already decoded. */
    static SectionSource of(SectionSnapshot snapshot) {
        return new SectionSource() {
            @Override
            public long key() {
                return snapshot.key();
            }

            @Override
            public SectionSnapshot decode() {
                return snapshot;
            }
        };
    }
}
