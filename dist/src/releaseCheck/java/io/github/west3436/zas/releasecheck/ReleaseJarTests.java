package io.github.west3436.zas.releasecheck;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforgespi.language.IModFileInfo;

/**
 * Checks that run inside the release jar on a dedicated server (CONT-052, CONT-155). Gradle runs them
 * twice: {@code runReleaseCheckGameTestServer} with the content on and {@code
 * runContentOffGameTestServer} with it off, as {@code zas.releaseCheck.contentOff} says.
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class ReleaseJarTests {
    private static final String CONTENT = ZasAtmosMod.MOD_ID;

    /** The content's data pack, under the id NeoForge gives a mod's built-in pack. */
    private static final String CONTENT_PACK = "mod/" + ZasAtmosMod.id(ZasAtmosMod.CONTENT_PACK);

    /** A framework tag that the content adds its doors to, and that holds vanilla's doors either way. */
    private static final TagKey<Block> ZONE_BOUNDARY =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("zas", "zone_boundary"));

    private ReleaseJarTests() {}

    /** zas and zas_atmos load from one jar (CONT-052, D-88). */
    @GameTest(template = "empty")
    public static void cont_052_both_mods_load_from_the_release_jar(GameTestHelper h) {
        h.assertTrue(FMLEnvironment.dist == Dist.DEDICATED_SERVER, "not a dedicated server");
        IModFileInfo zas = ModList.get().getModFileById("zas");
        IModFileInfo content = ModList.get().getModFileById(CONTENT);
        h.assertTrue(zas != null, "zas isn't loaded");
        h.assertTrue(content != null, "zas_atmos isn't loaded");
        h.assertTrue(
                zas == content,
                "zas and zas_atmos load from different files: " + zas.getFile().getFilePath() + " and "
                        + content.getFile().getFilePath());
        h.succeed();
    }

    /**
     * The content's registry entries, recipes, loot tables and data pack are there only while its
     * startup config has it on, and the framework's own data loads either way (CONT-155).
     */
    @GameTest(template = "empty")
    public static void cont_155_the_content_loads_only_while_its_config_has_it_on(GameTestHelper h) {
        boolean on = !Boolean.getBoolean("zas.releaseCheck.contentOff");
        h.assertValueEqual(ZasAtmosMod.contentEnabled(), on, "whether the content is on");
        MinecraftServer server = h.getLevel().getServer();

        long entries = 0;
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            entries += registry.keySet().stream()
                    .filter(ReleaseJarTests::isContent)
                    .count();
        }
        long recipes = server.getRecipeManager().getRecipes().stream()
                .filter(recipe -> isContent(recipe.id()))
                .count();
        long lootTables = server.reloadableRegistries().getKeys(Registries.LOOT_TABLE).stream()
                .filter(ReleaseJarTests::isContent)
                .count();
        long contentBoundaries = BuiltInRegistries.BLOCK
                .getTag(ZONE_BOUNDARY)
                .map(set -> set.stream()
                        .filter(block ->
                                isContent(block.unwrapKey().orElseThrow().location()))
                        .count())
                .orElse(0L);

        h.assertTrue(Blocks.OAK_DOOR.defaultBlockState().is(ZONE_BOUNDARY), "zas's own zone boundary tag didn't load");
        if (on) {
            h.assertTrue(entries > 0, "the content registered nothing");
            h.assertTrue(recipes > 0, "the content has no recipes");
            h.assertTrue(lootTables > 0, "the content has no loot tables");
            h.assertTrue(contentBoundaries > 0, "the content's doors aren't zone boundaries");
            h.assertTrue(server.getPackRepository().getSelectedIds().contains(CONTENT_PACK), CONTENT_PACK + " is off");
        } else {
            h.assertValueEqual(entries, 0L, "registry entries in zas_atmos");
            h.assertValueEqual(recipes, 0L, "recipes in zas_atmos");
            h.assertValueEqual(lootTables, 0L, "loot tables in zas_atmos");
            h.assertValueEqual(contentBoundaries, 0L, "zas_atmos blocks in #zas:zone_boundary");
            h.assertFalse(
                    server.getPackRepository().getAvailableIds().contains(CONTENT_PACK), CONTENT_PACK + " is offered");
        }
        h.succeed();
    }

    private static boolean isContent(ResourceLocation id) {
        return id.getNamespace().equals(CONTENT);
    }
}
