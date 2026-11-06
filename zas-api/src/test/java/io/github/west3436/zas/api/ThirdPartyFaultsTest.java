package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The limit on logging faults in third-party code: once per source per minute, in a record that
 * can't grow without bound (API-062, NFR-013). The GameTests named {@code nfr_013_} check it on each
 * kind of third-party code.
 */
class ThirdPartyFaultsTest {
    private static final long MINUTE = 60;

    private long now;

    private ThirdPartyFaults.Limit limit(int mostSources) {
        return new ThirdPartyFaults.Limit(() -> now, MINUTE, mostSources);
    }

    @Test
    void nfr_013_a_source_is_allowed_once_a_minute() {
        ThirdPartyFaults.Limit limit = limit(8);
        assertTrue(limit.allows("a"), "its first fault");
        assertFalse(limit.allows("a"), "again at once");
        now = MINUTE - 1;
        assertFalse(limit.allows("a"), "just inside the minute");
        now = MINUTE;
        assertTrue(limit.allows("a"), "when the minute is up");
        assertFalse(limit.allows("a"), "and the minute starts again");
    }

    @Test
    void nfr_013_each_source_has_its_own_minute() {
        ThirdPartyFaults.Limit limit = limit(8);
        assertTrue(limit.allows("a"));
        now = 30;
        assertTrue(limit.allows("b"), "another source isn't held back by the first");
        now = MINUTE;
        assertTrue(limit.allows("a"), "the first source's minute is up");
        assertFalse(limit.allows("b"), "the second's isn't");
    }

    @Test
    void nfr_013_the_record_forgets_sources_and_stays_bounded() {
        ThirdPartyFaults.Limit limit = limit(2);
        assertTrue(limit.allows("a"));
        assertTrue(limit.allows("b"));
        assertFalse(limit.allows("c"), "there's no room within the minute, and a logged source stays limited");
        assertFalse(limit.allows("a"), "being full doesn't forget a source early");
        assertEquals(2, limit.sources());

        now = MINUTE;
        assertTrue(limit.allows("c"), "there's room once the minute is up");
        assertEquals(1, limit.sources(), "the old sources are forgotten");

        for (int i = 0; i < 100; i++) {
            now += MINUTE;
            assertTrue(limit.allows("source " + i));
            assertEquals(1, limit.sources(), "sources that have stopped failing don't pile up");
        }
    }

    @Test
    void api_062_the_shared_limit_follows_its_clock() {
        String source = "ThirdPartyFaultsTest source";
        assertTrue(ThirdPartyFaults.shouldLog(source));
        assertFalse(ThirdPartyFaults.shouldLog(source));
        ThirdPartyFaults.advanceClockForTest(java.time.Duration.ofMinutes(1));
        assertTrue(ThirdPartyFaults.shouldLog(source));
    }
}
