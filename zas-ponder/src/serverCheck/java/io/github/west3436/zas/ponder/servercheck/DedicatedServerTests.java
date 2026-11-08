package io.github.west3436.zas.ponder.servercheck;

import io.github.west3436.zas.ponder.ZasPonderMod;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * CLIENT-025: a dedicated server runs with the Ponder addon installed. The server loads the built
 * addon, with the Ponder and Flywheel it nests, beside the built {@code zas} and {@code zas_atmos}, and
 * these tests run inside it. A server that got this far has loaded every mod and its world; the test
 * checks it is a dedicated server and that each mod is there.
 */
@GameTestHolder(ZasPonderMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DedicatedServerTests {
    private DedicatedServerTests() {}

    @GameTest(template = "empty")
    public static void client_025_a_dedicated_server_runs_with_the_ponder_addon(GameTestHelper h) {
        h.assertTrue(FMLEnvironment.dist == Dist.DEDICATED_SERVER, "not a dedicated server");
        for (String mod : List.of(ZasPonderMod.MOD_ID, "ponder", "flywheel", "zas_atmos", "zas")) {
            h.assertTrue(ModList.get().isLoaded(mod), mod + " isn't loaded");
        }
        h.succeed();
    }
}
