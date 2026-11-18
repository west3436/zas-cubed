package io.github.west3436.zas.compat.cosmonautics.gametest;

import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.life.Internals;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Breathing with Cosmonautics that needs the content mod's breath mask and oxygen tank, in a
 * namespace that {@code -Pcontent=off} leaves out (CONT-155).
 */
@GameTestHolder(CosmonauticsContentTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CosmonauticsContentTests {
    /** The namespace of the adapter's GameTests that need the content. */
    public static final String NAMESPACE = "zas_cosmonautics_content";

    private CosmonauticsContentTests() {}

    /**
     * In the open on the Moon, where Cosmonautics finds no air, a player on ZAS³'s internals
     * breathes, as ZAS³'s verdict finds it does. With its internals off, it doesn't (S-06).
     */
    @GameTest(template = "empty")
    public static void space_012_internals_give_a_breath_in_the_open(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 6)) {
            FakePlayer player = site.player(site.open(8));
            player.setItemSlot(EquipmentSlot.HEAD, Site.content("breath_mask"));
            player.setItemInHand(InteractionHand.OFF_HAND, Site.content("oxygen_tank"));
            CosmonauticsTests.check(player);
            h.assertFalse(CosmonauticsTests.breathes(player), "a player with its internals off breathes in the open");
            Internals.set(player, true);
            CosmonauticsTests.check(player);
            h.assertValueEqual(Breathing.of(player).source(), BreathSource.INTERNALS, "what the player breathes");
            h.assertFalse(
                    CosmonauticsTests.cosmonautics(player),
                    "Cosmonautics by itself let the player on internals breathe");
            h.assertTrue(CosmonauticsTests.breathes(player), "a player on internals can't breathe in the open");
        }
        h.succeed();
    }
}
