package io.github.west3436.zas.client;

import io.github.west3436.zas.sound.LoopBook;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * One looping sound in the game's sound engine: it copies its {@link LoopBook.Voice} every tick and
 * decides nothing itself.
 *
 * <p>Its attenuation is none, so {@code sounds.json}'s {@code attenuation_distance} doesn't matter:
 * the book's gain has already allowed for distance, and the engine still pans by position.
 *
 * <p>It stops by itself if {@link ClientLoops} stops tending it, so a loop can't outlive whatever
 * should be running it. The engine ticks it only while the game isn't paused, as it does the rest.
 */
final class LoopSoundInstance extends AbstractTickableSoundInstance {
    /** How many ticks it plays on without being tended: 2 seconds. */
    private static final int UNTENDED_TICKS = 40;

    private final LoopBook.Voice<?> voice;
    private int untended;

    LoopSoundInstance(LoopBook.Voice<?> voice, SoundEvent event, SoundSource channel) {
        super(event, channel, SoundInstance.createUnseededRandom());
        this.voice = voice;
        looping = true;
        delay = 0;
        attenuation = SoundInstance.Attenuation.NONE;
        relative = false;
        copy();
    }

    private void copy() {
        volume = (float) voice.gain();
        pitch = (float) voice.pitch();
        x = voice.x();
        y = voice.y();
        z = voice.z();
    }

    /** {@link ClientLoops} still knows this instance. */
    void tended() {
        untended = 0;
    }

    /** Tells the engine to stop it. */
    void finish() {
        stop();
    }

    @Override
    public void tick() {
        if (!voice.alive() || ++untended > UNTENDED_TICKS) {
            stop();
            return;
        }
        copy();
    }

    /** A voice starts at 0 and fades in, so the engine must not skip it for being silent. */
    @Override
    public boolean canStartSilent() {
        return true;
    }
}
