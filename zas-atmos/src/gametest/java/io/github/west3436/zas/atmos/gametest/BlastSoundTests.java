package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.failure.BlastSound;
import io.github.west3436.zas.atmos.failure.ContainerBlast;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Kind;
import io.github.west3436.zas.atmos.failure.TankFailure;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the server tells players when something explodes (FIRE-037): vg's sounds for a gas container's
 * blast, one per listener by distance, and vanilla's for anything else. Areas 870 to 874.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlastSoundTests {
    private static final String BATCH = "zas_atmos_blast_sound";

    private BlastSoundTests() {}

    private static ListeningPlayer listener(Area a, Vec3 at, double distance) {
        return ListeningPlayer.at(a.level, at.add(distance, 0, 0));
    }

    /** The one sound a listener was sent, of the event expected, at the volume expected. */
    private static void heard(GameTestHelper h, ListeningPlayer l, SoundEvent event, float volume, String who) {
        List<ClientboundSoundPacket> sounds = l.sounds();
        h.assertValueEqual(sounds.size(), 1, who + ": sounds sent");
        h.assertValueEqual(sounds.get(0).getSound().value(), event, who + ": the sound");
        h.assertValueEqual(sounds.get(0).getVolume(), volume, who + ": the volume");
        h.assertValueEqual(sounds.get(0).getPitch(), 1f, who + ": the pitch");
        h.assertValueEqual(sounds.get(0).getSource(), SoundSource.BLOCKS, who + ": the slider");
    }

    /**
     * A big blast: each listener gets one sound, the near one close by and the far one beyond it, and
     * one too far away gets nothing. The explosion itself, which a client would otherwise play
     * vanilla's sound for, carries the empty sound to everyone it reaches.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void fire_037_a_big_blast_plays_near_or_far_by_distance(GameTestHelper h) {
        Area a = new Area(h, 870);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);
        ListeningPlayer close = listener(a, at, 10);
        ListeningPlayer edge = listener(a, at, BlastSound.NEAR_REACH);
        ListeningPlayer past = listener(a, at, BlastSound.NEAR_REACH + 1);
        ListeningPlayer far = listener(a, at, 200);
        ListeningPlayer beyond = listener(a, at, BlastSound.FAR_REACH + 1);
        ListeningPlayer.during(
                a.level,
                List.of(close, edge, past, far, beyond),
                () -> ContainerBlast.explode(a.level, at, BlastSound.BIG_POWER, null));
        heard(h, close, AtmosSounds.BLAST_NEAR.value(), 4f, "at 10 blocks");
        heard(h, edge, AtmosSounds.BLAST_NEAR.value(), 4f, "at the edge of near");
        heard(h, past, AtmosSounds.BLAST_FAR.value(), 1f, "just past near");
        heard(h, far, AtmosSounds.BLAST_FAR.value(), 1f, "at 200 blocks");
        h.assertTrue(beyond.sounds().isEmpty(), "a listener past the far range was sent a sound");
        h.assertValueEqual(close.sounds().get(0).getSeed(), far.sounds().get(0).getSeed(), "the seeds");
        h.assertValueEqual(close.sounds().get(0).getX(), at.x, "where it is");
        for (ListeningPlayer l : List.of(close, edge, past)) {
            List<ClientboundExplodePacket> blasts = l.explosions();
            h.assertValueEqual(blasts.size(), 1, "explosions sent");
            h.assertValueEqual(blasts.get(0).getExplosionSound().value(), SoundEvents.EMPTY, "the explosion's sound");
        }
        a.tick();
        h.succeed();
    }

    /** A small blast plays the small sounds, near and far, with their shorter distances. */
    @GameTest(template = "empty", batch = BATCH)
    public static void fire_037_a_small_blast_plays_the_small_sounds(GameTestHelper h) {
        Area a = new Area(h, 871);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);
        ListeningPlayer close = listener(a, at, BlastSound.SMALL_REACH);
        ListeningPlayer far = listener(a, at, BlastSound.SMALL_REACH + 1);
        ListeningPlayer beyond = listener(a, at, BlastSound.SMALL_FAR_REACH + 1);
        ListeningPlayer.during(
                a.level,
                List.of(close, far, beyond),
                () -> ContainerBlast.explode(a.level, at, BlastSound.BIG_POWER - 0.1, null));
        heard(h, close, AtmosSounds.BLAST_SMALL.value(), 1f, "close by");
        heard(h, far, AtmosSounds.BLAST_SMALL_FAR.value(), 1f, "past near");
        h.assertTrue(beyond.sounds().isEmpty(), "a listener past the far range was sent a sound");
        a.tick();
        h.succeed();
    }

    /** A tank's failure plays through the same call, sized by its power: 1 at 60 atm, 5 at 100 atm. */
    @GameTest(template = "empty", batch = BATCH)
    public static void fire_037_an_exploding_tank_plays_for_its_power(GameTestHelper h) {
        Area a = new Area(h, 872);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);
        ListeningPlayer small = listener(a, at, 1);
        ListeningPlayer.during(
                a.level,
                List.of(small),
                () -> h.assertValueEqual(
                        TankFailure.check(a.level, at, tank(60), null).kind(), Kind.EXPLODE, "at 60 atm"));
        heard(h, small, AtmosSounds.BLAST_SMALL.value(), 1f, "at 60 atm");
        ListeningPlayer big = listener(a, at, 1);
        ListeningPlayer.during(
                a.level,
                List.of(big),
                () -> h.assertValueEqual(
                        TankFailure.check(a.level, at, tank(100), null).kind(), Kind.EXPLODE, "at 100 atm"));
        heard(h, big, AtmosSounds.BLAST_NEAR.value(), 4f, "at 100 atm");
        a.tick();
        h.succeed();
    }

    /** A real TNT block's explosion keeps vanilla's sound and sends none of ours. */
    @GameTest(template = "empty", batch = BATCH)
    public static void fire_037_tnt_keeps_vanillas_sound(GameTestHelper h) {
        Area a = new Area(h, 873);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);
        ListeningPlayer close = listener(a, at, 10);
        ListeningPlayer far = listener(a, at, 100);
        PrimedTnt tnt = new PrimedTnt(a.level, at.x, at.y, at.z, null);
        tnt.setFuse(0);
        ListeningPlayer.during(a.level, List.of(close, far), tnt::tick);
        h.assertTrue(close.sounds().isEmpty(), "TNT sent a sound of its own");
        h.assertTrue(far.sounds().isEmpty(), "TNT sent a far sound");
        List<ClientboundExplodePacket> blasts = close.explosions();
        h.assertValueEqual(blasts.size(), 1, "explosions sent");
        h.assertValueEqual(
                blasts.get(0).getExplosionSound().value(),
                SoundEvents.GENERIC_EXPLODE.value(),
                "the explosion's sound");
        h.assertTrue(far.explosions().isEmpty(), "an explosion reached a listener past vanilla's 64 blocks");
        a.tick();
        a.clear(-2, -2, 8, 8);
        h.succeed();
    }

    /** The events' own ranges agree with how far each sound is played, and the near sound has 64 blocks. */
    @GameTest(template = "empty", batch = BATCH)
    public static void fire_037_the_ranges_agree_with_the_events(GameTestHelper h) {
        h.assertValueEqual(AtmosSounds.BLAST_NEAR.value().getRange(BlastSound.NEAR.volume()), 64f, "blast.near");
        h.assertValueEqual(AtmosSounds.BLAST_SMALL.value().getRange(BlastSound.SMALL.volume()), 16f, "blast.small");
        h.assertValueEqual(
                AtmosSounds.BLAST_FAR.value().getRange(BlastSound.FAR.volume()),
                (float) BlastSound.FAR_REACH,
                "blast.far");
        h.assertValueEqual(
                AtmosSounds.BLAST_SMALL_FAR.value().getRange(BlastSound.SMALL_FAR.volume()),
                (float) BlastSound.SMALL_FAR_REACH,
                "blast.small_far");
        h.succeed();
    }

    /** A 70 L tank of N2 at a pressure. */
    private static ItemStack tank(double atm) {
        ItemStack stack = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(stack, GasTankItem.fill(Map.of(Area.N2, 1.0), atm * ContainerFailure.ATM_KPA, 70));
        return stack;
    }
}
