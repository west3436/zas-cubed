package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * A data pack that overrides one of the content mod's recipes, for {@link RecipeTests} (CONT-059).
 * It lives in the GameTest source set at {@code testpacks/recipe_override}, so it's never in the mod
 * jar, and it's added only on the GameTest server, so dev clients and servers keep the real recipe.
 * It replaces {@code zas_atmos:pipe_cap} with one that makes {@value #PIPE_CAP_COUNT} caps instead
 * of 1.
 */
@EventBusSubscriber(modid = ZasAtmosMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class RecipeTestPack {
    /** How many pipe caps the overriding recipe makes. */
    public static final int PIPE_CAP_COUNT = 4;

    private RecipeTestPack() {}

    @SubscribeEvent
    static void addPack(AddPackFindersEvent event) {
        if (!GameTestHooks.isGametestServer() || !ZasAtmosMod.contentEnabled()) {
            return;
        }
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(ZasAtmosMod.MOD_ID, "testpacks/recipe_override"),
                PackType.SERVER_DATA,
                Component.literal("ZAS³ recipe override test"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP);
    }
}
