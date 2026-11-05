package io.github.west3436.zas.client;

import io.github.west3436.zas.api.client.ClientSoundProvider;
import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.api.client.LoopRequest;
import io.github.west3436.zas.hud.ClientListenerAir;
import io.github.west3436.zas.sound.LoopBook;
import io.github.west3436.zas.sound.LoopPlayer;
import io.github.west3436.zas.sound.ThinAir;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Plays what the {@link LoopBook} says (CLIENT-015): the thin shell between its rules, with the
 * {@link LoopPlayer}'s bookkeeping, and the game's sound engine, and the API's provider (API-060,
 * API-061).
 *
 * <p>What it adds, because only it can see the game:
 *
 * <ul>
 *   <li>A new level, or none, drops every loop. That covers a change of dimension and leaving a
 *       world, and {@code LoggingOut} covers leaving a server.
 *   <li>A source in a chunk the client doesn't hold isn't a candidate, even if its caller keeps
 *       asking.
 *   <li>A loop whose slider is at 0 has no instance. The engine cuts such a sound off but goes on
 *       reporting it as active and ticking it, so a loop would stay silent when the slider came back
 *       up.
 *   <li>While the game is paused nothing here runs, and nor do the engine's ticks or the callers', so
 *       no lease runs out.
 * </ul>
 *
 * <p>Client thread only.
 */
final class ClientLoops implements ClientSoundProvider, LoopPlayer.Engine<ClientLoops.LoopSound, LoopSoundInstance> {
    private static final ClientLoops INSTANCE = new ClientLoops();
    private static final RandomSource WEIGHT_RANDOM = RandomSource.create();

    /**
     * A sound and its slider, compared by the sound's id: an event made twice for one id is one
     * sound.
     */
    record LoopSound(SoundEvent event, SoundSource channel) {
        @Override
        public boolean equals(Object o) {
            return o instanceof LoopSound s
                    && channel == s.channel
                    && event.getLocation().equals(s.event.getLocation());
        }

        @Override
        public int hashCode() {
            return 31 * event.getLocation().hashCode() + channel.hashCode();
        }
    }

    private final LoopBook<LoopSound> book = new LoopBook<>();
    private final LoopPlayer<LoopSound, LoopSoundInstance> player = new LoopPlayer<>(this);
    private ClientLevel level;

    private ClientLoops() {}

    /** Installs the provider and the listeners. Called once, from the client entry point. */
    static void register() {
        ClientSounds.install(INSTANCE);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> INSTANCE.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> INSTANCE.stopAll());
    }

    @Override
    public double thinAir() {
        return ThinAir.factor(ClientListenerAir.kPa());
    }

    @Override
    public void loop(LoopRequest r) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("loops are asked for on the client thread, not on "
                    + Thread.currentThread().getName());
        }
        // Dropped outside a level: the next level starts with nothing.
        if (level != null) {
            book.request(
                    r.source(),
                    new LoopSound(r.sound(), r.channel()),
                    r.x(),
                    r.y(),
                    r.z(),
                    r.volume(),
                    r.pitch(),
                    r.range(),
                    weight(r));
        }
    }

    /**
     * What the sound engine will multiply a loop's volume by, for choosing which loops play: the volume
     * its event has in {@code sounds.json}, and its slider, as the engine reads them. The master slider
     * is left out, since it applies to every loop alike.
     */
    private static double weight(LoopRequest r) {
        Minecraft mc = Minecraft.getInstance();
        double slider = r.channel() == SoundSource.MASTER ? 1 : mc.options.getSoundSourceVolume(r.channel());
        return slider * eventVolume(mc.getSoundManager().getSoundEvent(r.sound().getLocation()));
    }

    /**
     * The volume an event has in {@code sounds.json}. With several sounds of different volumes it is
     * always the same one's, so it doesn't change from one request to the next, though a loop may play
     * another. Every loop in this mod has one sound.
     *
     * @param event the event's sound data, or null if it has none, as before sounds have loaded
     * @return its volume, or 1 with no data, so that nothing goes silent for the lack of it
     */
    static double eventVolume(@Nullable WeighedSoundEvents event) {
        if (event == null) {
            return 1;
        }
        WEIGHT_RANDOM.setSeed(0);
        return event.getSound(WEIGHT_RANDOM).getVolume().sample(WEIGHT_RANDOM);
    }

    private void tick(Minecraft mc) {
        if (mc.isPaused()) {
            return;
        }
        if (mc.level != level) {
            stopAll();
            level = mc.level;
        }
        if (level == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 ear = camera.isInitialized() || mc.player == null ? camera.getPosition() : mc.player.getEyePosition();
        book.tick(
                ear.x,
                ear.y,
                ear.z,
                thinAir(),
                (x, y, z) -> level.getChunkSource()
                        .hasChunk(SectionPos.posToSectionCoord(x), SectionPos.posToSectionCoord(z)));
        player.tick(book.voices());
    }

    /** Stops everything at once: the level is going, so there is nothing to fade for. */
    private void stopAll() {
        book.clear();
        player.stopAll();
    }

    // ---- The sound engine -------------------------------------------------------------------

    private static SoundManager sounds() {
        return Minecraft.getInstance().getSoundManager();
    }

    @Override
    public LoopSoundInstance start(LoopBook.Voice<LoopSound> voice) {
        LoopSoundInstance instance = new LoopSoundInstance(
                voice, voice.sound().event(), voice.sound().channel());
        sounds().play(instance);
        return instance;
    }

    @Override
    public void stop(LoopSoundInstance sound) {
        sound.finish();
        sounds().stop(sound);
    }

    @Override
    public boolean active(LoopSoundInstance sound) {
        return sounds().isActive(sound);
    }

    @Override
    public void tend(LoopSoundInstance sound) {
        sound.tended();
    }

    @Override
    public boolean muted(LoopSound sound) {
        var options = Minecraft.getInstance().options;
        return options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || options.getSoundSourceVolume(sound.channel()) <= 0;
    }
}
