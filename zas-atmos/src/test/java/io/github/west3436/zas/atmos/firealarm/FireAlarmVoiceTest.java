package io.github.west3436.zas.atmos.firealarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.west3436.zas.atmos.firealarm.FireAlarmVoice.Request;
import org.junit.jupiter.api.Test;

/** What a client asks for of the fire alarms around it (CONT-128). The listener stands at the origin. */
class FireAlarmVoiceTest {
    private final FireAlarmVoice voice = new FireAlarmVoice();

    /** One game tick with an alarm 3 blocks east. */
    private Request alarm() {
        voice.report(3, 0, 0);
        return quiet();
    }

    /** One game tick with no alarm reporting. */
    private Request quiet() {
        return voice.tick(0, 0, 0);
    }

    @Test
    void cont_128_nothing_is_asked_for_with_no_alarm() {
        for (int i = 0; i < 1000; i++) {
            assertNull(quiet());
        }
    }

    @Test
    void cont_128_an_alarm_sounds_at_once_then_rests_and_comes_round_every_12_s() {
        for (int round = 0; round < 3; round++) {
            for (int t = 0; t < FireAlarmVoice.SOUND_TICKS; t++) {
                assertEquals(new Request(3, 0, 0, 1f), alarm(), "round " + round + ", tick " + t);
            }
            assertEquals(new Request(3, 0, 0, 0f), alarm(), "the end of round " + round);
            for (int t = FireAlarmVoice.SOUND_TICKS + 1; t < FireAlarmVoice.PERIOD_TICKS; t++) {
                assertNull(alarm(), "round " + round + ", resting at tick " + t);
            }
        }
    }

    @Test
    void cont_128_it_sounds_for_about_4_s_of_each_12() {
        assertEquals(70, FireAlarmVoice.SOUND_TICKS, "3.5 s, and half a second's fade");
        assertEquals(240, FireAlarmVoice.PERIOD_TICKS, "12 s");
    }

    @Test
    void cont_128_many_alarms_are_one_voice_at_the_nearest() {
        voice.report(30, 0, 0);
        voice.report(5, 0, 0);
        voice.report(0, 12, 0);
        assertEquals(new Request(5, 0, 0, 1f), quiet());
        voice.report(0, 12, 0);
        voice.report(0, 0, 2);
        voice.report(5, 0, 0);
        assertEquals(new Request(0, 0, 2, 1f), quiet(), "the nearest wherever it comes in the order");
    }

    @Test
    void cont_128_a_station_of_alarms_sounds_no_more_often_than_one() {
        int asked = 0;
        for (int t = 0; t < FireAlarmVoice.PERIOD_TICKS; t++) {
            for (int a = 0; a < 200; a++) {
                voice.report(a % 20, 0, a / 20);
            }
            Request request = quiet();
            if (request != null && request.volume() > 0) {
                asked++;
            }
        }
        assertEquals(FireAlarmVoice.SOUND_TICKS, asked);
    }

    @Test
    void cont_128_it_stops_at_the_first_tick_after_the_last_alarm_resets() {
        for (int t = 0; t < 20; t++) {
            assertNotNull(alarm());
        }
        assertEquals(new Request(3, 0, 0, 0f), quiet(), "the reset");
        for (int t = 0; t < 1000; t++) {
            assertNull(quiet(), "after the reset");
        }
    }

    @Test
    void cont_128_it_goes_on_while_another_alarm_is_still_going() {
        voice.report(3, 0, 0);
        voice.report(6, 0, 0);
        assertEquals(new Request(3, 0, 0, 1f), quiet());
        voice.report(6, 0, 0);
        assertEquals(new Request(6, 0, 0, 1f), quiet(), "the nearer one reset");
    }

    @Test
    void cont_128_a_flickering_alarm_keeps_its_rhythm() {
        for (int t = 0; t < 20; t++) {
            alarm();
        }
        quiet(); // it resets at tick 20, and stops
        for (int t = 21; t < FireAlarmVoice.SOUND_TICKS + 1; t++) {
            quiet();
        }
        // Back within the period, in what is now the rest: it waits for the round to come up.
        for (int t = FireAlarmVoice.SOUND_TICKS + 1; t < FireAlarmVoice.PERIOD_TICKS; t++) {
            assertNull(alarm(), "resting at tick " + t);
        }
        assertEquals(new Request(3, 0, 0, 1f), alarm(), "the next round");
    }

    @Test
    void cont_128_an_alarm_after_a_quiet_spell_sounds_at_once() {
        alarm();
        quiet();
        for (int t = 0; t < FireAlarmVoice.PERIOD_TICKS + 1; t++) {
            assertNull(quiet());
        }
        assertEquals(new Request(3, 0, 0, 1f), alarm());
    }

    @Test
    void cont_128_only_alarms_within_32_blocks_are_heard() {
        voice.report(32.5, 0, 0);
        assertNull(quiet(), "32.5 blocks away");
        voice.report(31.5, 0, 0);
        assertEquals(new Request(31.5, 0, 0, 1f), quiet(), "31.5 blocks away");
    }

    @Test
    void cont_128_walking_out_of_range_stops_it() {
        assertNotNull(alarm());
        voice.report(3, 0, 0);
        Request request = voice.tick(100, 0, 0); // measured from the origin: still heard this tick
        assertEquals(1f, request.volume());
        voice.report(3, 0, 0);
        assertEquals(0f, voice.tick(100, 0, 0).volume(), "now 97 blocks away");
        voice.report(3, 0, 0);
        assertNull(voice.tick(100, 0, 0), "and it stays stopped");
    }
}
