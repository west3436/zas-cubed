package io.github.west3436.zas.ponder;

import io.github.west3436.zas.ponder.scene.Stage;
import io.github.west3436.zas.ponder.scenes.PortScenes;
import java.util.List;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers the scenes, each under the item it explains, and lists them all under one ZAS³ tag in
 * Ponder's index (CLIENT-023). The tag's name and description are
 * {@code zas_ponder.ponder.tag.atmospherics} and its {@code .description} in {@code en_us.json}.
 */
final class ZasPonderPlugin implements PonderPlugin {
    private static final ResourceLocation TAG = ZasPonderMod.id("atmospherics");

    /** Every item with a scene, in the index's order. */
    private static final List<String> ITEMS = List.of("connector_port");

    @Override
    public String getModId() {
        return ZasPonderMod.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(atmos("connector_port")).addStoryBoard(Stage.MEDIUM, PortScenes::connectorPort, TAG);
    }

    /**
     * The tag's icon shows beside every scene's title, so it's the pipe, which most scenes use. A
     * canister item would show yellow, as an empty one is.
     */
    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag("atmospherics")
                .addToIndex()
                .item(BuiltInRegistries.ITEM.get(atmos("pipe")), true, false)
                .register();
        var tag = helper.addToTag(TAG);
        ITEMS.forEach(item -> tag.add(atmos(item)));
    }

    private static ResourceLocation atmos(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas_atmos", path);
    }
}
