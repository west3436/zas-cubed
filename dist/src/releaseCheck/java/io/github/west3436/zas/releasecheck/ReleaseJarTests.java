package io.github.west3436.zas.releasecheck;

import io.github.west3436.zas.api.environment.AmbientSeeds;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.life.ExtraSlots;
import io.github.west3436.zas.api.life.LocalAtmospheres;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.compat.Adapters;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforgespi.language.IModFileInfo;

/**
 * Checks that run inside the release jar on a dedicated server (CONT-052, CONT-155, MEK-001,
 * COMPAT-001, SPACE-003). Gradle runs them twice: {@code runReleaseCheckGameTestServer} with the content on and {@code
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

    /** The Mekanism adapter's package. */
    private static final String MEKANISM_ADAPTER = "io.github.west3436.zas.compat.mekanism";

    /** The Jade adapter's package, which is part of zas_atmos. */
    private static final String JADE_ADAPTER = "io.github.west3436.zas.atmos.compat.jade";

    /** The Ad Astra adapter's package. */
    private static final String AD_ASTRA_ADAPTER = "io.github.west3436.zas.compat.adastra";

    /** The Curios adapter's package. */
    private static final String CURIOS_ADAPTER = "io.github.west3436.zas.compat.curios";

    /** The Accessories adapter's package. */
    private static final String ACCESSORIES_ADAPTER = "io.github.west3436.zas.compat.accessories";

    /** The entity types pressure and temperature don't harm, which the Ad Astra adapter's data pack adds to. */
    private static final TagKey<EntityType<?>> ATMOSPHERE_IMMUNE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("zas", "atmosphere_immune"));

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

    /**
     * The release jar holds the whole Mekanism adapter, and on a server without Mekanism none of it
     * loads (MEK-001, NFR-022): {@code zas} left the adapter off, Mixin was never handed its config,
     * and its data pack, its gases and its settings file aren't there.
     */
    @GameTest(template = "empty")
    public static void mek_001_nothing_of_the_mekanism_adapter_loads_without_mekanism(GameTestHelper h) {
        h.assertFalse(ModList.get().isLoaded("mekanism"), "Mekanism is on the release check's server");
        // It ships: these are looked up as files, which loads nothing.
        ClassLoader jar = ReleaseJarTests.class.getClassLoader();
        for (String file : new String[] {
            MEKANISM_ADAPTER.replace('.', '/') + "/MekanismCompat.class",
            "zas.mekanism.mixins.json",
            "datapacks/mekanism/pack.mcmeta",
            "datapacks/mekanism/data/zas/zas/gas/chlorine.json"
        }) {
            h.assertTrue(jar.getResource(file) != null, "the release jar has no " + file);
        }

        // zas went through its row and found no Mekanism, so it never loaded the entry class.
        Adapters.Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals("mekanism"))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Mekanism");
        h.assertValueEqual(status.row().entryClass(), MEKANISM_ADAPTER + ".MekanismCompat", "the row's entry class");
        h.assertValueEqual(status.state(), Adapters.State.ABSENT, "the Mekanism adapter's state");
        h.assertFalse(Adapters.isActive("mekanism"), "the Mekanism adapter is on");

        // What the adapter would have added.
        MinecraftServer server = h.getLevel().getServer();
        String pack = "mod/zas:datapacks/mekanism";
        h.assertFalse(server.getPackRepository().getAvailableIds().contains(pack), pack + " is offered");
        h.assertFalse(ModConfigs.getFileMap().containsKey("zas-mekanism-server.toml"), "its config file is registered");
        ResourceLocation chlorine = ResourceLocation.fromNamespaceAndPath("zas", "chlorine");
        h.assertFalse(GasProperties.isDefined(chlorine), "the adapter's gases are defined");
        h.assertFalse(GasTags.contains(GasTags.TOXIC, chlorine), "the adapter's gas tags loaded");
        h.assertTrue(
                GasProperties.isDefined(ResourceLocation.fromNamespaceAndPath("zas", "oxygen")),
                "zas's own gases aren't defined");

        // Last, the adapter's own word, from the two classes that name nothing of Mekanism's. Asking
        // loads them, which is why everything above was checked first.
        h.assertFalse(mekanismAdapterSays("MekanismCompat", "isActive"), "the adapter says it started");
        h.assertFalse(
                mekanismAdapterSays("mixin.MekanismMixinPlugin", "loaded"), "Mixin loaded the adapter's mixin config");
        h.succeed();
    }

    /** Calls a static yes-or-no method of the Mekanism adapter by name: nothing here compiles against it. */
    private static boolean mekanismAdapterSays(String className, String method) {
        try {
            return (boolean) Class.forName(MEKANISM_ADAPTER + "." + className)
                    .getMethod(method)
                    .invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The release jar's " + className + " has no " + method + "()", e);
        }
    }

    /**
     * The release jar holds the Jade adapter, and on a server without Jade none of it runs
     * (COMPAT-001, NFR-022): only Jade creates its plugin, and nothing in the two mods names it.
     */
    @GameTest(template = "empty")
    public static void compat_001_nothing_of_the_jade_adapter_runs_without_jade(GameTestHelper h) {
        h.assertFalse(ModList.get().isLoaded("jade"), "Jade is on the release check's server");
        // It ships: these are looked up as files, which loads nothing.
        ClassLoader jar = ReleaseJarTests.class.getClassLoader();
        for (String file : new String[] {
            JADE_ADAPTER.replace('.', '/') + "/ZasJadePlugin.class",
            "assets/zas_atmos/textures/gui/sprites/jade/pressure.png"
        }) {
            h.assertTrue(jar.getResource(file) != null, "the release jar has no " + file);
        }
        // The adapter's own word, from its one class that names nothing of Jade's.
        boolean started;
        try {
            started = (boolean) Class.forName(JADE_ADAPTER + ".JadeGate")
                    .getMethod("pluginStarted")
                    .invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The release jar's JadeGate has no pluginStarted()", e);
        }
        h.assertFalse(started, "the Jade plugin was created");
        h.succeed();
    }

    /**
     * The release jar holds the whole Ad Astra adapter, and on a server without Ad Astra none of it
     * loads (SPACE-003, NFR-022): {@code zas} left the adapter off, and its data pack, its settings
     * file, its planet seed and its distributor areas aren't there. A zombie, which the pack makes
     * immune through Ad Astra's tags, isn't.
     */
    @GameTest(template = "empty")
    public static void space_003_nothing_of_the_ad_astra_adapter_loads_without_ad_astra(GameTestHelper h) {
        h.assertFalse(ModList.get().isLoaded("ad_astra"), "Ad Astra is on the release check's server");
        // It ships: these are looked up as files, which loads nothing.
        ClassLoader jar = ReleaseJarTests.class.getClassLoader();
        for (String file : new String[] {
            AD_ASTRA_ADAPTER.replace('.', '/') + "/AdAstraCompat.class",
            "datapacks/ad_astra/pack.mcmeta",
            "datapacks/ad_astra/data/zas/tags/entity_type/atmosphere_immune.json",
            "datapacks/ad_astra/data/zas/zas/protection/ad_astra_space_suit.json"
        }) {
            h.assertTrue(jar.getResource(file) != null, "the release jar has no " + file);
        }

        // zas went through its row and found no Ad Astra, so it never loaded the entry class.
        Adapters.Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals("ad_astra"))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Ad Astra");
        h.assertValueEqual(status.row().entryClass(), AD_ASTRA_ADAPTER + ".AdAstraCompat", "the row's entry class");
        h.assertValueEqual(status.state(), Adapters.State.ABSENT, "the Ad Astra adapter's state");
        h.assertFalse(Adapters.isActive("ad_astra"), "the Ad Astra adapter is on");

        // What the adapter would have added.
        MinecraftServer server = h.getLevel().getServer();
        String pack = "mod/zas:datapacks/ad_astra";
        h.assertFalse(server.getPackRepository().getAvailableIds().contains(pack), pack + " is offered");
        h.assertFalse(ModConfigs.getFileMap().containsKey("zas-adastra-server.toml"), "its config file is registered");
        ResourceLocation seed = ResourceLocation.fromNamespaceAndPath("zas", "ad_astra_planets");
        ResourceLocation areas = ResourceLocation.fromNamespaceAndPath("zas", "ad_astra_distributors");
        h.assertFalse(
                AmbientSeeds.registered().stream().anyMatch(r -> r.id().equals(seed)), "its planet seed is registered");
        h.assertFalse(
                LocalAtmospheres.registered().stream().anyMatch(r -> r.id().equals(areas)),
                "its distributor areas are registered");
        h.assertFalse(EntityType.ZOMBIE.is(ATMOSPHERE_IMMUNE), "a zombie is immune to the atmosphere");
        h.assertTrue(EntityType.IRON_GOLEM.is(ATMOSPHERE_IMMUNE), "zas's own immune entity tag didn't load");
        h.succeed();
    }

    /**
     * The release jar holds the Curios and Accessories adapters, and on a server with neither mod
     * nothing of them loads (COMPAT-001, NFR-022): {@code zas} left both off, and no provider of
     * extra equipment slots is registered. The content's entries in the two mods' slot tags ship in
     * its data pack (COMPAT-043), where without those mods nothing reads them: they are there with
     * the content on, and not with it off (CONT-155).
     */
    @GameTest(template = "empty")
    public static void compat_001_nothing_of_the_curios_and_accessories_adapters_loads_without_them(GameTestHelper h) {
        ClassLoader jar = ReleaseJarTests.class.getClassLoader();
        String[][] adapters = {
            {"curios", CURIOS_ADAPTER + ".CuriosCompat"},
            {"accessories", ACCESSORIES_ADAPTER + ".AccessoriesCompat"}
        };
        for (String[] adapter : adapters) {
            String modId = adapter[0];
            String entry = adapter[1];
            h.assertFalse(ModList.get().isLoaded(modId), modId + " is on the release check's server");
            // It ships: looked up as a file, which loads nothing.
            String file = entry.replace('.', '/') + ".class";
            h.assertTrue(jar.getResource(file) != null, "the release jar has no " + file);

            // zas went through its row and found no such mod, so it never loaded the entry class.
            Adapters.Status status = Adapters.statuses().stream()
                    .filter(s -> s.row().modId().equals(modId))
                    .findFirst()
                    .orElse(null);
            h.assertTrue(status != null, "zas has no adapter row for " + modId);
            h.assertValueEqual(status.row().entryClass(), entry, "the row's entry class");
            h.assertValueEqual(status.state(), Adapters.State.ABSENT, "the " + modId + " adapter's state");
            h.assertFalse(Adapters.isActive(modId), "the " + modId + " adapter is on");
        }
        h.assertValueEqual(ExtraSlots.registered(), List.of(), "the providers of extra equipment slots");

        // The slot data, in the content's pack: files in the jar, and tags only while the content is on.
        for (String file : new String[] {
            "datapacks/content/data/curios/tags/item/back.json",
            "datapacks/content/data/curios/tags/item/head.json",
            "datapacks/content/data/accessories/tags/item/belt.json",
            "datapacks/content/data/accessories/tags/item/face.json",
            "datapacks/content/data/zas_atmos/curios/entities/gear.json"
        }) {
            h.assertTrue(jar.getResource(file) != null, "the release jar has no " + file);
        }
        boolean on = !Boolean.getBoolean("zas.releaseCheck.contentOff");
        for (String tag : new String[] {"curios:back", "curios:head", "accessories:belt", "accessories:face"}) {
            TagKey<Item> key = TagKey.create(Registries.ITEM, ResourceLocation.parse(tag));
            boolean filled = BuiltInRegistries.ITEM
                    .getTag(key)
                    .map(items -> items.size() > 0)
                    .orElse(false);
            h.assertValueEqual(filled, on, "#" + tag + " holds items");
        }

        // Last, each adapter's own word, from its one class that names nothing of its target's. Asking
        // loads that class, which is why everything above was checked first.
        for (String[] adapter : adapters) {
            boolean active;
            try {
                active = (boolean)
                        Class.forName(adapter[1]).getMethod("isActive").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("The release jar's " + adapter[1] + " has no isActive()", e);
            }
            h.assertFalse(active, "the " + adapter[0] + " adapter says it started");
        }
        h.succeed();
    }

    private static boolean isContent(ResourceLocation id) {
        return id.getNamespace().equals(CONTENT);
    }
}
