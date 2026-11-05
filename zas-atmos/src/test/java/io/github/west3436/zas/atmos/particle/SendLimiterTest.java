package io.github.west3436.zas.atmos.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.atmos.device.AtmosTicks;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The cap on the flowing jets each player is sent (CLIENT-019, DATA-006). */
class SendLimiterTest {
    private static final UUID ONE = new UUID(0, 1);
    private static final UUID TWO = new UUID(0, 2);

    /** A jet on the wire: a packet ID, its channel's name and the payload. */
    private static final int PACKET_BYTES = 1 + 1 + "zas_atmos:gas_jet".length() + GasJetTest.PAYLOAD_BYTES;

    @Test
    void client_019_a_player_gets_up_to_the_cap_in_a_window() {
        SendLimiter limiter = new SendLimiter(2, 20);
        assertTrue(limiter.allow(ONE, 100));
        assertTrue(limiter.allow(ONE, 110));
        assertFalse(limiter.allow(ONE, 110));
        assertFalse(limiter.allow(ONE, 119));
    }

    @Test
    void client_019_each_player_has_their_own_cap() {
        SendLimiter limiter = new SendLimiter(1, 20);
        assertTrue(limiter.allow(ONE, 100));
        assertFalse(limiter.allow(ONE, 100));
        assertTrue(limiter.allow(TWO, 100));
    }

    @Test
    void client_019_the_next_window_starts_again() {
        SendLimiter limiter = new SendLimiter(1, 20);
        assertTrue(limiter.allow(ONE, 119));
        assertFalse(limiter.allow(ONE, 119));
        assertTrue(limiter.allow(ONE, 120));
        assertFalse(limiter.allow(ONE, 139));
        assertTrue(limiter.allow(ONE, 140));
    }

    @Test
    void client_019_ends_sharing_a_game_tick_all_send_under_the_cap() {
        // A device's phase comes from the order it was added, so any few of them can share one.
        SendLimiter limiter = new SendLimiter(GasJets.FLOWING_PER_SECOND, AtmosTicks.PERIOD);
        for (int end = 0; end < GasJets.FLOWING_PER_SECOND; end++) {
            assertTrue(limiter.allow(ONE, 100), "end " + end + " lost out with the cap not reached");
        }
        assertFalse(limiter.allow(ONE, 100));
    }

    @Test
    void data_006_a_large_station_stays_within_the_budget() {
        SendLimiter limiter = new SendLimiter(GasJets.FLOWING_PER_SECOND, AtmosTicks.PERIOD);
        int seconds = 10;
        int sent = 0;
        for (long tick = 0; tick < seconds * AtmosTicks.PERIOD; tick++) {
            for (int end = 0; end < 100; end++) {
                if (limiter.allow(ONE, tick)) {
                    sent++;
                }
            }
        }
        assertEquals(GasJets.FLOWING_PER_SECOND * seconds, sent);
        double bytesPerSecond = (double) sent * PACKET_BYTES / seconds;
        assertTrue(bytesPerSecond < 2048, bytesPerSecond + " bytes a second");
    }
}
