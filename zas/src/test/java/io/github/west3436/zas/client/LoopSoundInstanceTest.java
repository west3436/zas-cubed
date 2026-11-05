package io.github.west3436.zas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.sound.LoopBook;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;

/**
 * The sound engine's end of a voice (CLIENT-015): it plays as a loop, with the engine's attenuation
 * off, copies its voice, and stops by itself when its voice is released or nobody tends it. A stand-in
 * event, since no sound is ever played here: the game isn't bootstrapped.
 */
class LoopSoundInstanceTest {
    private static final SoundEvent STAND_IN =
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("zas_test", "stand_in"));

    private final LoopBook<String> book = new LoopBook<>();

    private LoopBook.Voice<String> voice() {
        book.request("device", "hum", 3, 4, 5, 1, 1, 8);
        book.tick(0, 0, 0, 1, (x, y, z) -> true);
        return book.voices().iterator().next();
    }

    @Test
    void client_015_it_is_a_silent_starting_loop_with_no_attenuation() {
        LoopSoundInstance s = new LoopSoundInstance(voice(), STAND_IN, SoundSource.BLOCKS);
        assertTrue(s.isLooping());
        assertEquals(0, s.getDelay());
        assertEquals(SoundInstance.Attenuation.NONE, s.getAttenuation());
        assertFalse(s.isRelative());
        assertTrue(s.canStartSilent());
        assertEquals(SoundSource.BLOCKS, s.getSource());
        assertFalse(s.isStopped());
    }

    @Test
    void client_015_it_follows_its_voice() {
        LoopSoundInstance s = new LoopSoundInstance(voice(), STAND_IN, SoundSource.BLOCKS);
        assertEquals(3, s.getX());
        assertEquals(4, s.getY());
        assertEquals(5, s.getZ());
        book.request("device", "hum", 1, 2, 3, 1, 1, 8);
        book.tick(0, 0, 0, 1, (x, y, z) -> true);
        s.tick();
        assertEquals(1, s.getX());
        assertEquals(2, s.getY());
        assertEquals(3, s.getZ());
    }

    @Test
    void client_015_it_stops_when_its_voice_is_released() {
        LoopSoundInstance s = new LoopSoundInstance(voice(), STAND_IN, SoundSource.BLOCKS);
        book.clear();
        s.tick();
        assertTrue(s.isStopped());
        assertFalse(s.isLooping(), "a stopped sound must not loop");
    }

    @Test
    void client_015_it_stops_by_itself_if_nobody_tends_it() {
        LoopSoundInstance s = new LoopSoundInstance(voice(), STAND_IN, SoundSource.BLOCKS);
        for (int t = 0; t < 40; t++) {
            s.tick();
            assertFalse(s.isStopped(), "stopped early, at tick " + t);
        }
        s.tick();
        assertTrue(s.isStopped(), "it played on untended");
    }

    @Test
    void client_015_tending_it_keeps_it_going() {
        LoopSoundInstance s = new LoopSoundInstance(voice(), STAND_IN, SoundSource.BLOCKS);
        for (int t = 0; t < 500; t++) {
            s.tended();
            s.tick();
        }
        assertFalse(s.isStopped());
    }

    @Test
    void client_015_finish_stops_it() {
        LoopSoundInstance s = new LoopSoundInstance(voice(), STAND_IN, SoundSource.BLOCKS);
        s.finish();
        assertTrue(s.isStopped());
    }
}
