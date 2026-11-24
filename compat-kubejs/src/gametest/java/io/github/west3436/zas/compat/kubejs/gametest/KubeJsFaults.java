package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.api.ThirdPartyFaults;
import java.time.Duration;

/**
 * What the checks that a script's fault is logged where it should be, and at most once a minute
 * (NFR-013), need beside SS13 Framework's test kit's {@code FaultLog}: the name KubeJS logs under, and
 * the limit's clock, which the kit can't name.
 */
final class KubeJsFaults {
    /** The name KubeJS's console for server scripts logs under. */
    static final String KUBEJS = "KubeJS Server";

    private KubeJsFaults() {}

    /** Moves the limit's clock on, so whatever it remembers has had its minute. */
    static void aMinutePasses() {
        ThirdPartyFaults.advanceClockForTest(Duration.ofMinutes(1));
    }
}
