package io.github.west3436.zas.engine.scheduler;

import java.util.Arrays;

/**
 * The answer to "is this unzoned cell enclosed?" (GEO-020, GEO-024, architecture §4.3), from a
 * {@link Classifier}.
 */
public sealed interface Classification {
    /** Open to the ambient: operations there act on the ambient (CORE-053, CORE-057). */
    Classification OPEN = new Open();

    /**
     * Enclosed: a zone is created from these cells (CORE-051) and the waiting operations apply
     * to it (GEO-028).
     *
     * @param cells the enclosure's cells, connected by direct openings, none zoned
     */
    record Enclosed(long[] cells) implements Classification {
        public Enclosed {
            cells = cells.clone();
        }

        @Override
        public long[] cells() {
            return cells.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Enclosed e && Arrays.equals(cells, e.cells);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(cells);
        }

        @Override
        public String toString() {
            return "Enclosed[" + cells.length + " cells]";
        }
    }

    /** Open to the ambient. */
    record Open() implements Classification {}

    /**
     * The fill reached unloaded chunks (GEO-024): no zone forms, and operations there are
     * treated as at an unresolved region (GEO-036, GEO-038).
     *
     * @param missingSections the unloaded sections it reached, ascending
     */
    record Unresolved(long[] missingSections) implements Classification {
        public Unresolved {
            missingSections = missingSections.clone();
            Arrays.sort(missingSections);
        }

        @Override
        public long[] missingSections() {
            return missingSections.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Unresolved u && Arrays.equals(missingSections, u.missingSections);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(missingSections);
        }
    }

    /**
     * The fill reached loaded sections the mirror lacks: the scheduler asks for them in its
     * result, and the waiting operations stay buffered and are classified again next tick
     * (GEO-028, architecture §4.3).
     *
     * @param sections the sections to fetch, ascending
     */
    record NeedsSections(long[] sections) implements Classification {
        public NeedsSections {
            sections = sections.clone();
            Arrays.sort(sections);
        }

        @Override
        public long[] sections() {
            return sections.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof NeedsSections n && Arrays.equals(sections, n.sections);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(sections);
        }
    }
}
