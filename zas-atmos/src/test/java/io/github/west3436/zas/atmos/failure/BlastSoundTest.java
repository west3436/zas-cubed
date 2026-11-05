package io.github.west3436.zas.atmos.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Which sound a blast plays for a listener at a distance (FIRE-037). */
class BlastSoundTest {
    @Test
    void fire_037_a_big_blast_is_near_then_far_then_nothing() {
        assertEquals(BlastSound.NEAR, BlastSound.heard(4, 0));
        assertEquals(BlastSound.NEAR, BlastSound.heard(4, 32));
        assertEquals(BlastSound.FAR, BlastSound.heard(4, 32.01));
        assertEquals(BlastSound.FAR, BlastSound.heard(4, 255.9));
        assertNull(BlastSound.heard(4, 256));
        assertNull(BlastSound.heard(4, 10_000));
    }

    @Test
    void fire_037_a_small_blast_is_near_then_far_then_nothing() {
        assertEquals(BlastSound.SMALL, BlastSound.heard(1, 0));
        assertEquals(BlastSound.SMALL, BlastSound.heard(1, 8));
        assertEquals(BlastSound.SMALL_FAR, BlastSound.heard(1, 8.01));
        assertEquals(BlastSound.SMALL_FAR, BlastSound.heard(1, 127.9));
        assertNull(BlastSound.heard(1, 128));
    }

    /** Power decides big or small, at the line vanilla uses for its large explosion particles. */
    @Test
    void fire_037_power_of_2_is_big() {
        assertEquals(BlastSound.SMALL, BlastSound.heard(0, 1));
        assertEquals(BlastSound.SMALL, BlastSound.heard(1.99, 1));
        assertEquals(BlastSound.NEAR, BlastSound.heard(2, 1));
        assertEquals(BlastSound.NEAR, BlastSound.heard(16, 1));
    }

    /** Only the big near sound is louder than 1, because that is what gives it 64 blocks' range. */
    @Test
    void fire_037_the_volumes() {
        assertEquals(4, BlastSound.NEAR.volume());
        assertEquals(1, BlastSound.SMALL.volume());
        assertEquals(1, BlastSound.FAR.volume());
        assertEquals(1, BlastSound.SMALL_FAR.volume());
    }

    /**
     * The near sound stops where it still has half its gain, so a listener just past it doesn't jump
     * from almost nothing to the far sound. The gains are the engine's straight lines to 0 at each
     * sound's range, with the volumes in {@code sounds.json}, and the far file is 2.2 dB below the near
     * files in its loudest 0.4 s (measured from the shipped oggs, FIRE-037's note).
     */
    @Test
    void fire_037_near_and_far_meet_within_3_db() {
        double farFile = Math.pow(10, -2.2 / 20);
        double big =
                stepDb(1 - BlastSound.NEAR_REACH / 64, farFile * (1 - BlastSound.NEAR_REACH / BlastSound.FAR_REACH));
        double small = stepDb(
                0.6 * (1 - BlastSound.SMALL_REACH / 16),
                0.5 * farFile * (1 - BlastSound.SMALL_REACH / BlastSound.SMALL_FAR_REACH));
        assertEquals(0, big, 3, "a big blast's step");
        assertEquals(0, small, 3, "a small blast's step");
    }

    private static double stepDb(double near, double far) {
        return 20 * Math.log10(far / near);
    }
}
