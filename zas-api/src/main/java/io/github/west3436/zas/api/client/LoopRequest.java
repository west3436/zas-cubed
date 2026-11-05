package io.github.west3436.zas.api.client;

import io.github.west3436.zas.api.AnyThread;
import java.util.Objects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * "This source wants this sound here, with this volume and pitch": what a mod asks {@link
 * ClientSounds#loop} for. A source and a sound together name one loop, so asking for another sound
 * from the same source fades the first out once its request lapses.
 *
 * <p>The volume falls with distance as vanilla's does, in a straight line from the source to its
 * range, so the sound's {@code attenuation_distance} in {@code sounds.json} isn't used. Give a loop
 * a mono sound, or it won't pan, and don't mark it {@code stream}: streamed sounds share a pool of 8
 * channels with the music.
 *
 * @param source who wants the sound, compared with {@code equals}: something immutable that stays
 *     the same between requests, such as a block's {@code BlockPos}
 * @param sound the sound
 * @param channel the slider it follows, such as {@link SoundSource#BLOCKS}
 * @param x where it is
 * @param y where it is
 * @param z where it is
 * @param volume its volume at the source, clamped to 0 to 1
 * @param pitch its pitch, clamped to 0.5 to 2, the range the game plays
 * @param range how far away it can be heard, in blocks: about 8 for a device
 */
@AnyThread
public record LoopRequest(
        Object source,
        SoundEvent sound,
        SoundSource channel,
        double x,
        double y,
        double z,
        float volume,
        float pitch,
        double range) {
    /**
     * Checks the arguments.
     *
     * @throws NullPointerException if a reference is null
     * @throws IllegalArgumentException if the position, volume or pitch isn't a number, or the range
     *     isn't above 0 and finite
     */
    public LoopRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(channel, "channel");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("position not finite: " + x + ", " + y + ", " + z);
        }
        if (Float.isNaN(volume) || Float.isNaN(pitch)) {
            throw new IllegalArgumentException("volume or pitch not a number: " + volume + ", " + pitch);
        }
        if (!(range > 0) || Double.isInfinite(range)) {
            throw new IllegalArgumentException("range not above 0 and finite: " + range);
        }
        volume = Math.max(0, Math.min(1, volume));
        pitch = Math.max(0.5f, Math.min(2, pitch));
    }
}
