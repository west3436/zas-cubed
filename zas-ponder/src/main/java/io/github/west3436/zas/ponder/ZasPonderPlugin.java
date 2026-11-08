package io.github.west3436.zas.ponder;

import io.github.west3436.zas.ponder.scene.Stage;
import io.github.west3436.zas.ponder.scenes.AreaAirControllerScenes;
import io.github.west3436.zas.ponder.scenes.FireScenes;
import io.github.west3436.zas.ponder.scenes.HeaterScenes;
import io.github.west3436.zas.ponder.scenes.PortScenes;
import io.github.west3436.zas.ponder.scenes.PumpScenes;
import io.github.west3436.zas.ponder.scenes.VentScenes;
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
    private static final List<String> ITEMS = List.of(
            "connector_port",
            "pressure_pump",
            "volume_pump",
            // Room air and fire.
            "vent",
            "scrubber",
            "passive_vent",
            "area_air_controller",
            "heater",
            "air_conditioner",
            "fire_alarm",
            "firelock",
            "igniter");

    @Override
    public String getModId() {
        return ZasPonderMod.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(atmos("connector_port")).addStoryBoard(Stage.MEDIUM, PortScenes::connectorPort, TAG);
        helper.forComponents(atmos("pressure_pump")).addStoryBoard(Stage.MEDIUM, PumpScenes::pressure, TAG);
        helper.forComponents(atmos("volume_pump")).addStoryBoard(Stage.MEDIUM, PumpScenes::volume, TAG);

        // Room air and fire.
        helper.forComponents(atmos("vent")).addStoryBoard(Stage.MEDIUM, VentScenes::vent, TAG);
        helper.forComponents(atmos("scrubber")).addStoryBoard(Stage.MEDIUM, VentScenes::scrubber, TAG);
        helper.forComponents(atmos("passive_vent")).addStoryBoard(Stage.MEDIUM, VentScenes::passive, TAG);
        helper.forComponents(atmos("area_air_controller"))
                .addStoryBoard(Stage.MEDIUM, AreaAirControllerScenes::controller, TAG);
        helper.forComponents(atmos("heater")).addStoryBoard(Stage.SMALL, HeaterScenes::heater, TAG);
        helper.forComponents(atmos("air_conditioner")).addStoryBoard(Stage.SMALL, HeaterScenes::airConditioner, TAG);
        helper.forComponents(atmos("fire_alarm")).addStoryBoard(Stage.MEDIUM, FireScenes::fireAlarm, TAG);
        helper.forComponents(atmos("firelock")).addStoryBoard(Stage.MEDIUM, FireScenes::firelock, TAG);
        helper.forComponents(atmos("igniter")).addStoryBoard(Stage.MEDIUM, FireScenes::igniter, TAG);
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
