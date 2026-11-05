package io.github.west3436.zas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.sound.LoopBook;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Which one-shot sounds fade for a listener in thin air, and by how much (CLIENT-016). Stand-in sounds,
 * since nothing is ever played here: that a real sound comes through the event is the Manual part.
 */
class ThinAirOneShotsTest {
    private static final String OURS = "zas_test_fades";

    @BeforeAll
    static void giveTheNamespace() {
        ClientSounds.fadeInThinAir(OURS);
    }

    @Test
    void client_016_a_one_shot_plays_at_its_share_of_the_volume() {
        SoundInstance faded = ThinAirOneShots.faded(new StandIn(OURS), 0.5);
        assertNotNull(faded);
        assertEquals(0.4f * 0.5f, faded.getVolume());
    }

    @Test
    void client_016_a_loud_one_shot_keeps_its_share_above_full_volume() {
        // A near blast plays at volume 4 for its range, and the engine works out the range from this.
        StandIn blast = new StandIn(OURS);
        blast.volume = 4;
        assertEquals(1f, ThinAirOneShots.faded(blast, 0.25).getVolume());
    }

    @Test
    void client_016_a_vacuum_drops_the_sound() {
        assertNull(ThinAirOneShots.faded(new StandIn(OURS), 0));
    }

    @Test
    void client_016_normal_air_leaves_the_sound_as_it_was() {
        StandIn sound = new StandIn(OURS);
        assertSame(sound, ThinAirOneShots.faded(sound, 1));
    }

    @Test
    void client_016_vanilla_sounds_do_not_fade() {
        StandIn vanilla = new StandIn("minecraft");
        assertSame(vanilla, ThinAirOneShots.faded(vanilla, 0.5));
        assertSame(vanilla, ThinAirOneShots.faded(vanilla, 0));
    }

    @Test
    void client_016_a_mod_that_did_not_ask_does_not_fade() {
        StandIn other = new StandIn("zas_test_someone_else");
        assertSame(other, ThinAirOneShots.faded(other, 0.5));
    }

    /** A loop's own volume already has the factor in it, every tick. */
    @Test
    void client_016_a_loop_is_left_to_fade_by_itself() {
        LoopBook<String> book = new LoopBook<>();
        book.request("device", "hum", 0, 0, 0, 1, 1, 8);
        book.tick(0, 0, 0, 1, (x, y, z) -> true);
        LoopSoundInstance loop = new LoopSoundInstance(
                book.voices().iterator().next(),
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OURS, "hum")),
                SoundSource.BLOCKS);
        assertSame(loop, ThinAirOneShots.faded(loop, 0.5));
        assertSame(loop, ThinAirOneShots.faded(loop, 0));
    }

    /** The engine plays a queued sound again through the same event. */
    @Test
    void client_016_a_sound_fades_only_once() {
        SoundInstance once = ThinAirOneShots.faded(new StandIn(OURS), 0.5);
        assertSame(once, ThinAirOneShots.faded(once, 0.5));
    }

    @Test
    void client_016_no_sound_stays_no_sound() {
        assertNull(ThinAirOneShots.faded(null, 0.5));
    }

    @Test
    void client_016_a_faded_sound_is_otherwise_the_same_sound() {
        StandIn sound = new StandIn(OURS);
        SoundInstance faded = ThinAirOneShots.faded(sound, 0.5);
        assertEquals(sound.getLocation(), faded.getLocation());
        assertNull(faded.getSound());
        assertTrue(sound.askedForSound, "asking for its sound didn't reach the sound");
        assertEquals(SoundSource.BLOCKS, faded.getSource());
        assertTrue(faded.isLooping());
        assertTrue(faded.isRelative());
        assertEquals(7, faded.getDelay());
        assertEquals(1.5f, faded.getPitch());
        assertEquals(1, faded.getX());
        assertEquals(2, faded.getY());
        assertEquals(3, faded.getZ());
        assertEquals(SoundInstance.Attenuation.NONE, faded.getAttenuation());
        assertTrue(faded.canStartSilent());
        assertFalse(faded.canPlaySound());
        assertNull(faded.resolve(null));
        assertTrue(sound.resolved, "resolving it didn't reach the sound");
    }

    /** Answers nothing a default would, so that a getter that isn't passed on shows. */
    private static final class StandIn implements SoundInstance {
        final ResourceLocation location;
        float volume = 0.4f;
        boolean resolved;
        boolean askedForSound;

        StandIn(String namespace) {
            location = ResourceLocation.fromNamespaceAndPath(namespace, "stand_in");
        }

        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            resolved = true;
            return null;
        }

        @Override
        public Sound getSound() {
            askedForSound = true;
            return null; // a real one needs the game bootstrapped
        }

        @Override
        public SoundSource getSource() {
            return SoundSource.BLOCKS;
        }

        @Override
        public boolean isLooping() {
            return true;
        }

        @Override
        public boolean isRelative() {
            return true;
        }

        @Override
        public int getDelay() {
            return 7;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Override
        public float getPitch() {
            return 1.5f;
        }

        @Override
        public double getX() {
            return 1;
        }

        @Override
        public double getY() {
            return 2;
        }

        @Override
        public double getZ() {
            return 3;
        }

        @Override
        public Attenuation getAttenuation() {
            return Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean canPlaySound() {
            return false;
        }
    }
}
