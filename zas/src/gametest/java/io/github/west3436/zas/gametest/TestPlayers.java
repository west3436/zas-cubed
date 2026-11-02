package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Players for GameTests, shared by {@code zas}'s and {@code zas_atmos}'s.
 *
 * <p>NeoForge's {@link FakePlayer} can't be hurt. It is invulnerable to every damage source, and,
 * like every new {@link ServerPlayer}, it is spared most damage for its first 60 ticks, which its
 * tick never counts down because it does nothing. A test that checks a player takes harm, or takes
 * none, needs {@link #hurtable} instead.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TestPlayers {
    private TestPlayers() {}

    /**
     * A survival fake player in a level, not added to it, that damage reaches as it would reach a
     * real player: neither invulnerable nor spawn-protected. Its tick still does nothing, so a test
     * that hurts it more than once within 10 ticks sets {@code invulnerableTime} back to 0 itself,
     * as a real player's tick would count it down.
     */
    public static FakePlayer hurtable(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        try {
            Field spawn = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("can't end the test player's spawn protection: " + e);
        }
        return p;
    }

    /** The hurtable player takes damage that a plain fake player shrugs off. */
    @GameTest(template = "empty")
    public static void test_players_hurtable_player_takes_damage(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FakePlayer plain = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "zas_plain"));
        FakePlayer hurtable = hurtable(level, "zas_hurtable");
        plain.hurt(level.damageSources().generic(), 2);
        hurtable.hurt(level.damageSources().generic(), 2);
        h.assertValueEqual(plain.getHealth(), plain.getMaxHealth(), "the plain fake player's health");
        h.assertValueEqual(hurtable.getHealth(), hurtable.getMaxHealth() - 2, "the hurtable player's health");
        h.succeed();
    }
}
