package io.github.west3436.zas.compat.slots.gametest;

import io.github.west3436.zas.compat.curios.CuriosCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Curios adapter on a server whose Curios is older than it supports (COMPAT-002): it stays
 * off, and the server runs on. No Curios below the oldest
 * supported one exists for this Minecraft, so the run loads an old build of Accessories'
 * compatibility layer, which says it is one.
 *
 * <p>These are in a namespace of their own, which the build turns on only for the run that loads
 * such a Curios; that run leaves {@link CuriosTests} out.
 */
@GameTestHolder(CuriosOffTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CuriosOffTests {
    /** The namespace of the tests for an unsupported Curios. */
    public static final String NAMESPACE = "zas_curios_off";

    private CuriosOffTests() {}

    /** zas leaves the adapter off, and no slots of Curios' are registered. */
    @GameTest(template = "empty")
    public static void compat_002_an_unsupported_curios_leaves_the_adapter_off(GameTestHelper h) {
        Scenarios.adapterOff(h, Slots.CURIOS, CuriosCompat.ID, CuriosCompat.isActive());
        h.succeed();
    }
}
