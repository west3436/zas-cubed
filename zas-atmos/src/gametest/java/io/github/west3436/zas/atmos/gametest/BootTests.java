package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootTests {
    private BootTests() {}

    /** zas_atmos loads alongside zas on a dedicated server (NFR-020, NFR-023). */
    @GameTest(template = "empty")
    public static void nfr023_zasAtmosLoadsOnDedicatedServer(GameTestHelper h) {
        h.assertTrue(FMLEnvironment.dist == Dist.DEDICATED_SERVER, "not a dedicated server");
        h.assertTrue(ModList.get().isLoaded("zas"), "zas is not loaded");
        h.assertTrue(ModList.get().isLoaded(ZasAtmosMod.MOD_ID), "zas_atmos is not loaded");
        h.succeed();
    }
}
