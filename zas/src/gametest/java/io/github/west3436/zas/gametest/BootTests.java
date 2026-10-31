package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootTests {
    private BootTests() {}

    /** zas loads on a dedicated server (NFR-020, NFR-023). */
    @GameTest(template = "empty")
    public static void nfr_023_zas_loads_on_dedicated_server(GameTestHelper h) {
        h.assertTrue(FMLEnvironment.dist == Dist.DEDICATED_SERVER, "not a dedicated server");
        h.assertTrue(ModList.get().isLoaded(ZasMod.MOD_ID), "zas is not loaded");
        h.succeed();
    }
}
