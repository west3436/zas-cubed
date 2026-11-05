package io.github.west3436.zas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The volume a loop's event has in {@code sounds.json}, which decides what the loop ranks at
 * (CLIENT-015). These use the game's own classes for sound data, built by hand: no client is running.
 * An event with an empty sound list isn't here, because reading one needs the game bootstrapped; the
 * game's empty sound has a volume of 1.
 */
class ClientLoopsTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas_test", "stand_in");

    private static Sound sound(float volume) {
        // Not ConstantFloat, which needs the game's registries: the game isn't bootstrapped here.
        return new Sound(ID, random -> volume, random -> 1f, 1, Sound.Type.FILE, false, false, 16);
    }

    private static WeighedSoundEvents event(float... volumes) {
        WeighedSoundEvents event = new WeighedSoundEvents(ID, null);
        for (float volume : volumes) {
            event.addSound(sound(volume));
        }
        return event;
    }

    @Test
    void client_015_an_event_with_no_sound_data_weighs_1_so_nothing_goes_silent() {
        assertEquals(1, ClientLoops.eventVolume(null));
    }

    @Test
    void client_015_an_event_weighs_its_volume_in_sounds_json() {
        assertEquals(0.35, ClientLoops.eventVolume(event(0.35f)), 1e-6);
        assertEquals(1, ClientLoops.eventVolume(event(1f)), 1e-6);
    }

    /** A weight that changed from one request to the next would make loops swap places for no reason. */
    @Test
    void client_015_an_event_with_several_sounds_always_gives_the_same_answer() {
        WeighedSoundEvents event = event(0.2f, 0.5f, 0.9f);
        double first = ClientLoops.eventVolume(event);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, ClientLoops.eventVolume(event));
        }
    }
}
