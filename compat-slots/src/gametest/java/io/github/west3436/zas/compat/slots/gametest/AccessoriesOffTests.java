package io.github.west3436.zas.compat.slots.gametest;

import io.github.west3436.zas.compat.accessories.AccessoriesCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Accessories adapter on a server whose Accessories is older than it supports (COMPAT-002): it stays
 * off, and the server runs on. The run loads the
 * last build of Accessories before the oldest supported one.
 *
 * <p>These are in a namespace of their own, which the build turns on only for the run that loads
 * such a Accessories; that run leaves {@link AccessoriesTests} out.
 */
@GameTestHolder(AccessoriesOffTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AccessoriesOffTests {
    /** The namespace of the tests for an unsupported Accessories. */
    public static final String NAMESPACE = "zas_accessories_off";

    private AccessoriesOffTests() {}

    /** zas leaves the adapter off, and no slots of Accessories' are registered. */
    @GameTest(template = "empty")
    public static void compat_002_an_unsupported_accessories_leaves_the_adapter_off(GameTestHelper h) {
        Scenarios.adapterOff(h, Slots.ACCESSORIES, AccessoriesCompat.ID, AccessoriesCompat.isActive());
        h.succeed();
    }
}
