package io.github.west3436.zas;

import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.engine.Engine;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class S0Tests {
    private S0Tests() {}

    @GameTest(template = "empty")
    public static void s0_engine_and_api_present(GameTestHelper h) {
        h.assertTrue("engine-ok".equals(Engine.hello()), "engine classes not reachable");
        h.assertTrue(ZasApi.isPresent(), "zas did not install its service");
        h.assertTrue("zas".equals(ZasApi.get().implementationName()), "wrong implementation");
        double p = ZasApi.get().pressureKPa(h.getLevel(), BlockPos.ZERO);
        h.assertTrue(Math.abs(p - 101.4) < 0.1, "unexpected pressure " + p);
        h.succeed();
    }
}
