package io.github.west3436.s0consumer;

import io.github.west3436.zas.api.ZasApi;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("s0consumer")
@PrefixGameTestTemplate(false)
public final class S0ConsumerTests {
    private S0ConsumerTests() {}

    /** Without zas (s0.expectPresent=false) this is the S-12 shape: fallback, no crash. */
    @GameTest(template = "empty")
    public static void s0_consumer_sees_expected_api(GameTestHelper h) {
        boolean expectPresent = Boolean.parseBoolean(System.getProperty("s0.expectPresent", "true"));
        h.assertTrue(ZasApi.isPresent() == expectPresent, "isPresent=" + ZasApi.isPresent() + " expected " + expectPresent);
        String impl = ZasApi.get().implementationName();
        h.assertTrue(impl.equals(expectPresent ? "zas" : "fallback"), "impl " + impl);
        double p = ZasApi.get().pressureKPa(h.getLevel(), BlockPos.ZERO);
        h.assertTrue(p > 100 && p < 102, "pressure " + p);
        h.succeed();
    }
}
