package io.github.west3436.zas.compat.cosmonautics.gametest;

import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.cosmonautics.CosmonauticsCompat;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * With a Cosmonautics older than the adapter supports, the adapter stays off (COMPAT-002): the game
 * starts, and Cosmonautics' own answer stands even in a sealed room.
 */
@GameTestHolder(CosmonauticsOffTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CosmonauticsOffTests {
    /** The namespace of the GameTest run with an unsupported Cosmonautics. */
    public static final String NAMESPACE = "zas_cosmonautics_off";

    private CosmonauticsOffTests() {}

    /**
     * The adapter's row is unsupported and its entry class never started; in a sealed room of air
     * on the Moon a pig can't breathe, as Cosmonautics says.
     */
    @GameTest(template = "empty")
    public static void compat_002_the_adapter_stays_off_with_an_older_cosmonautics(GameTestHelper h) {
        Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals(CosmonauticsCompat.MOD_ID))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Cosmonautics");
        h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
        h.assertFalse(Adapters.isActive(CosmonauticsCompat.MOD_ID), "zas says the adapter is on");
        h.assertFalse(CosmonauticsCompat.isActive(), "the adapter started");
        try (Site site = Site.onTheMoon(h, 7)) {
            site.room(0, Map.of(Site.N2, 79.0, Site.O2, 21.0));
            Pig pig = site.mob(EntityType.PIG, site.floor(0));
            CosmonauticsTests.check(pig);
            h.assertFalse(CosmonauticsTests.breathes(pig), "a pig breathes in a room on the Moon with the adapter off");
        }
        h.succeed();
    }
}
