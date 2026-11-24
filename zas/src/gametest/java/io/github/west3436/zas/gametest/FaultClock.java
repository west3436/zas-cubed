package io.github.west3436.zas.gametest;

import io.github.west3436.zas.api.ThirdPartyFaults;
import java.time.Duration;

/**
 * The clock of NFR-013's limit on logging a third party's fault, for tests that check a fault is
 * logged at most once a minute. They collect the errors with SS13 Framework's test kit's {@code
 * FaultLog}, which can't name {@code zas-api}. Shared by {@code zas}'s and {@code zas_atmos}'s
 * GameTests.
 */
public final class FaultClock {
    private FaultClock() {}

    /** Moves the limit's clock on, so whatever it remembers has had its minute. */
    public static void aMinutePasses() {
        ThirdPartyFaults.advanceClockForTest(Duration.ofMinutes(1));
    }
}
