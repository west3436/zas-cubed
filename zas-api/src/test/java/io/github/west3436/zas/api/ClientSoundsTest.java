package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.api.client.LoopRequest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;

/** Without ZAS³ a listener hears normally and loops play nothing (API-060, API-061, D-17). */
class ClientSoundsTest {
    private static final SoundEvent SOUND =
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("test", "hum"));

    private static LoopRequest request(double x, float volume, float pitch, double range) {
        return new LoopRequest("source", SOUND, SoundSource.BLOCKS, x, 0, 0, volume, pitch, range);
    }

    @Test
    void api_060_a_listener_hears_normally_without_zas() {
        assertEquals(1, ClientSounds.thinAir());
    }

    @Test
    void api_060_a_mod_gives_its_own_namespace() {
        assertFalse(ClientSounds.fadesInThinAir("zas_test_given"));
        ClientSounds.fadeInThinAir("zas_test_given");
        assertTrue(ClientSounds.fadesInThinAir("zas_test_given"));
        assertFalse(ClientSounds.fadesInThinAir("minecraft"));
        assertThrows(NullPointerException.class, () -> ClientSounds.fadeInThinAir(null));
    }

    @Test
    void api_061_a_loop_without_zas_does_nothing() {
        assertDoesNotThrow(() -> ClientSounds.loop(request(0, 1, 1, 8)));
        assertThrows(NullPointerException.class, () -> ClientSounds.loop(null));
    }

    @Test
    void api_061_a_request_clamps_its_volume_and_pitch() {
        LoopRequest loud = request(0, 3, 9, 8);
        assertEquals(1f, loud.volume());
        assertEquals(2f, loud.pitch());
        LoopRequest quiet = request(0, -1, 0, 8);
        assertEquals(0f, quiet.volume());
        assertEquals(0.5f, quiet.pitch());
    }

    @Test
    void api_061_a_request_refuses_what_it_cant_play() {
        assertThrows(IllegalArgumentException.class, () -> request(Double.NaN, 1, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> request(Double.POSITIVE_INFINITY, 1, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> request(0, Float.NaN, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> request(0, 1, Float.NaN, 8));
        assertThrows(IllegalArgumentException.class, () -> request(0, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> request(0, 1, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> request(0, 1, 1, Double.POSITIVE_INFINITY));
        assertThrows(
                NullPointerException.class, () -> new LoopRequest(null, SOUND, SoundSource.BLOCKS, 0, 0, 0, 1, 1, 8));
    }
}
