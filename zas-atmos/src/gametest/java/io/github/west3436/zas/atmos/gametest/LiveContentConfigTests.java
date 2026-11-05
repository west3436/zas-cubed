package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.gametest.LiveConfigTests;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The content mod's server config under OPS-016: every key of it is live. */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiveContentConfigTests {
    private LiveContentConfigTests() {}

    /** Fails when a key gains the mark, as a reminder to mark it in docs/config.md too. */
    @GameTest(template = "empty", batch = "ops_016_live_content_config")
    public static void ops_016_no_content_setting_is_restart_only(GameTestHelper h) {
        h.assertValueEqual(LiveConfigTests.restartOnly(AtmosServerConfig.SPEC), Set.of(), "the content mod's config");
        h.succeed();
    }
}
