package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforgespi.language.IModInfo;

@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootTests {
    /** All a GameTest server loads: the game, NeoForge and our own mods. */
    private static final Set<String> REQUIRED = Set.of("minecraft", "neoforge", "zas", "zas_atmos");

    private BootTests() {}

    /**
     * A GameTest server holds no mod but the ones ZAS³ needs, so this suite is the run with no
     * optional mod that NFR-022 asks for: its passing is ZAS³ running fully without one.
     */
    @GameTest(template = "empty")
    public static void nfr_022_runs_with_no_optional_mod(GameTestHelper h) {
        List<String> others = ModList.get().getMods().stream()
                .map(IModInfo::getModId)
                .filter(id -> !REQUIRED.contains(id))
                .sorted()
                .toList();
        h.assertTrue(others.isEmpty(), "the server has other mods: " + others);
        h.succeed();
    }

    /** zas loads on a dedicated server (NFR-020, NFR-023). */
    @GameTest(template = "empty")
    public static void nfr_023_zas_loads_on_dedicated_server(GameTestHelper h) {
        h.assertTrue(FMLEnvironment.dist == Dist.DEDICATED_SERVER, "not a dedicated server");
        h.assertTrue(ModList.get().isLoaded(ZasMod.MOD_ID), "zas is not loaded");
        h.succeed();
    }
}
