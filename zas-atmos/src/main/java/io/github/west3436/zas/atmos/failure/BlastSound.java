package io.github.west3436.zas.atmos.failure;

import org.jetbrains.annotations.Nullable;

/**
 * Which sound a container's explosion plays for a listener (FIRE-037). A listener hears one sound,
 * so the near and far versions of a blast never play over each other. It is plain arithmetic, so it
 * is tested without the game; {@link ContainerBlast} sends the sound.
 *
 * <p>The near sound stops at half its audible range, not at the end of it. Its gain falls in a
 * straight line to 0 at that range, while the far sound's has hardly started to fall, so a listener
 * just inside the near sound's range would hear almost nothing and one just outside would hear the
 * far sound at about 0.6. At half the range the two are within 3 dB: see FIRE-037's note.
 */
public enum BlastSound {
    /** {@code blast.near}. Volume 4 gives it 64 blocks' range, as vanilla's explosion has. */
    NEAR(4),
    /** {@code blast.small}. Volume 1 gives it 16 blocks' range. */
    SMALL(1),
    /** {@code blast.far}. Its range of 256 is in {@code sounds.json}. */
    FAR(1),
    /** {@code blast.small_far}. Its range of 128 is in {@code sounds.json}. */
    SMALL_FAR(1);

    /** The least power of a big blast: where vanilla draws the line for its large explosion particles. */
    public static final double BIG_POWER = 2;

    /** How far a big blast plays its near sound, in blocks: half of its 64. */
    public static final double NEAR_REACH = 32;

    /** How far a big blast is heard at all, in blocks: where {@code blast.far} falls to nothing. */
    public static final double FAR_REACH = 256;

    /** How far a small blast plays its near sound, in blocks: half of its 16. */
    public static final double SMALL_REACH = 8;

    /** How far a small blast is heard at all, in blocks: where {@code blast.small_far} falls to nothing. */
    public static final double SMALL_FAR_REACH = 128;

    private final float volume;

    BlastSound(float volume) {
        this.volume = volume;
    }

    /** The volume to play it at. */
    public float volume() {
        return volume;
    }

    /**
     * What a listener hears.
     *
     * @param power the explosion's power
     * @param distance how far the listener is from it, in blocks
     * @return the sound, or null if the blast is too far away to hear
     */
    public static @Nullable BlastSound heard(double power, double distance) {
        boolean big = power >= BIG_POWER;
        if (distance <= (big ? NEAR_REACH : SMALL_REACH)) {
            return big ? NEAR : SMALL;
        }
        if (distance < (big ? FAR_REACH : SMALL_FAR_REACH)) {
            return big ? FAR : SMALL_FAR;
        }
        return null;
    }
}
