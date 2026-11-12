package io.github.west3436.zas.compat.slots.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Accessories adapter on a server with an Accessories it supports (COMPAT-041, COMPAT-004): the parts that
 * need nothing of the content mod, so they also run with the content off (CONT-155). {@link
 * Scenarios} has what each test checks.
 *
 * <p>The build turns this namespace on only for a run that loads such an Accessories. The class names
 * nothing of Accessories', so it loads in every run.
 */
@GameTestHolder(AccessoriesTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AccessoriesTests {
    /** The namespace of these tests. */
    public static final String NAMESPACE = "zas_accessories";

    private AccessoriesTests() {}

    /** zas starts the adapter for a supported Accessories, and the adapter registers its slots (COMPAT-002). */
    @GameTest(template = "empty")
    public static void compat_002_a_supported_accessories_starts_the_adapter(GameTestHelper h) {
        Scenarios.adapterStarted(h, new AccessoriesKit());
        h.succeed();
    }

    /** What an entity wears in Accessories slots is given once, in Accessories' order, with each slot's kind (COMPAT-041). */
    @GameTest(template = "empty")
    public static void compat_041_what_is_worn_in_accessories_slots_is_given_once_and_in_order(GameTestHelper h) {
        Scenarios.givesWhatIsWorn(h, new AccessoriesKit());
        h.succeed();
    }

    /** The tags and the player's slots are there with the content, and not without it (COMPAT-043). */
    @GameTest(template = "empty")
    public static void compat_043_the_accessories_slot_data_comes_with_the_content(GameTestHelper h) {
        Scenarios.dataFollowsTheContent(h, new AccessoriesKit());
        h.succeed();
    }
}
