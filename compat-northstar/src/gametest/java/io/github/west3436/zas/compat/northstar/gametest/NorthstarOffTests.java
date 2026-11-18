package io.github.west3436.zas.compat.northstar.gametest;

import com.lightning.northstar.world.oxygen.NorthstarOxygen;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.northstar.NorthstarCompat;
import io.github.west3436.zas.compat.northstar.NorthstarVersions;
import io.github.west3436.zas.compat.northstar.mixin.NorthstarMixinPlugin;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * With a Northstar older than the adapter supports, the adapter stays off (COMPAT-002): the game
 * starts, its mixins don't apply, and Northstar finds no oxygen in a sealed room of air on its Moon.
 */
@GameTestHolder(NorthstarOffTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class NorthstarOffTests {
    /** The namespace of the GameTest run with an unsupported Northstar. */
    public static final String NAMESPACE = "zas_northstar_off";

    private NorthstarOffTests() {}

    @GameTest(template = "empty")
    public static void compat_002_the_adapter_stays_off_with_an_older_northstar(GameTestHelper h) {
        Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals(NorthstarVersions.MOD_ID))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Northstar");
        h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
        h.assertFalse(Adapters.isActive(NorthstarVersions.MOD_ID), "zas says the adapter is on");
        h.assertFalse(NorthstarCompat.isActive(), "the adapter started");
        h.assertTrue(NorthstarMixinPlugin.loaded(), "the adapter's mixin config wasn't loaded with Northstar");
        h.assertFalse(NorthstarMixinPlugin.applies(), "the adapter's mixins apply to an unsupported Northstar");
        try (Site site = Site.on(h, Site.MOON, 0)) {
            site.room(0, Site.AIR);
            h.assertFalse(
                    NorthstarOxygen.hasOxygen(site.level, site.middle(0)),
                    "Northstar finds oxygen in a ZAS³ room with the adapter off");
        }
        h.succeed();
    }
}
