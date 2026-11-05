package io.github.west3436.zas.client;

import io.github.west3436.zas.api.client.ClientSounds;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Fades one-shot sounds for a listener in thin air (CLIENT-016), at the one place every sound passes
 * through on its way to the engine. A server plays a sound for everyone in range, and only each
 * client knows the air around its own player, so a device can play its event as it always would.
 *
 * <p>It acts on the namespaces mods have {@linkplain ClientSounds#fadeInThinAir given it}, so vanilla's
 * sounds stay as they are. A ticking sound keeps its own volume every tick, as a loop does, and is
 * left to fade by itself.
 *
 * <p>The factor is read once, as the sound starts: a one-shot is too short for it to matter.
 */
final class ThinAirOneShots {
    private ThinAirOneShots() {}

    /** Installs the listener. Called once, from the client entry point. */
    static void register() {
        NeoForge.EVENT_BUS.addListener((PlaySoundEvent e) -> e.setSound(faded(e.getSound(), ClientSounds.thinAir())));
    }

    /**
     * What to play in place of a sound.
     *
     * @param sound the sound, or null if a listener has already dropped it
     * @param factor the {@linkplain ClientSounds#thinAir thin-air factor}
     * @return the sound itself if it doesn't fade, null if it can't be heard, and otherwise the sound
     *     at a share of its volume
     */
    @Nullable
    static SoundInstance faded(@Nullable SoundInstance sound, double factor) {
        // The engine plays a queued sound again through the same event, so a sound fades only once.
        if (sound == null
                || factor >= 1
                || sound instanceof Faded
                || sound instanceof TickableSoundInstance
                || !ClientSounds.fadesInThinAir(sound.getLocation().getNamespace())) {
            return sound;
        }
        return factor <= 0 ? null : new Faded(sound, (float) factor);
    }

    /** A sound at a share of its volume, and in every other way the same sound. */
    private static final class Faded implements SoundInstance {
        private final SoundInstance sound;
        private final float factor;

        Faded(SoundInstance sound, float factor) {
            this.sound = sound;
            this.factor = factor;
        }

        @Override
        public float getVolume() {
            return sound.getVolume() * factor;
        }

        @Override
        public ResourceLocation getLocation() {
            return sound.getLocation();
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return sound.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return sound.getSound();
        }

        @Override
        public SoundSource getSource() {
            return sound.getSource();
        }

        @Override
        public boolean isLooping() {
            return sound.isLooping();
        }

        @Override
        public boolean isRelative() {
            return sound.isRelative();
        }

        @Override
        public int getDelay() {
            return sound.getDelay();
        }

        @Override
        public float getPitch() {
            return sound.getPitch();
        }

        @Override
        public double getX() {
            return sound.getX();
        }

        @Override
        public double getY() {
            return sound.getY();
        }

        @Override
        public double getZ() {
            return sound.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return sound.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return sound.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return sound.canPlaySound();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound stream, boolean looping) {
            return sound.getStream(buffers, stream, looping);
        }
    }
}
