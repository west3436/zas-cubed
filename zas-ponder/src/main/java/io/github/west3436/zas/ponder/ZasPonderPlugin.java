package io.github.west3436.zas.ponder;

import io.github.west3436.zas.ponder.scene.Stage;
import io.github.west3436.zas.ponder.scenes.AreaAirControllerScenes;
import io.github.west3436.zas.ponder.scenes.CanisterScenes;
import io.github.west3436.zas.ponder.scenes.CentralControllerScenes;
import io.github.west3436.zas.ponder.scenes.ElectrolyzerScenes;
import io.github.west3436.zas.ponder.scenes.FireScenes;
import io.github.west3436.zas.ponder.scenes.HeaterScenes;
import io.github.west3436.zas.ponder.scenes.PipeScenes;
import io.github.west3436.zas.ponder.scenes.PortScenes;
import io.github.west3436.zas.ponder.scenes.PortableScenes;
import io.github.west3436.zas.ponder.scenes.PowerScenes;
import io.github.west3436.zas.ponder.scenes.PumpScenes;
import io.github.west3436.zas.ponder.scenes.SidePortScenes;
import io.github.west3436.zas.ponder.scenes.ToolScenes;
import io.github.west3436.zas.ponder.scenes.TransferValveScenes;
import io.github.west3436.zas.ponder.scenes.ValveScenes;
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
            "pipe",
            "pipe_cap",
            "connector_port",
            "manual_valve",
            "redstone_valve",
            "pressure_pump",
            "volume_pump",
            "filter",
            "filter_mirrored",
            "mixer",
            "mixer_mirrored",
            // Room air and fire.
            "vent",
            "scrubber",
            "passive_vent",
            "area_air_controller",
            "heater",
            "air_conditioner",
            "fire_alarm",
            "firelock",
            "igniter",
            // Portables, power, the electrolyzer, the central controller and the handheld tools.
            "canister",
            "portable_scrubber",
            "portable_pump",
            "generator",
            "recharger",
            "electrolyzer",
            "central_atmos_controller",
            "multitool",
            "crowbar",
            "fire_extinguisher",
            "tank_transfer_valve",
            "atmos_analyzer",
            "wrench");

    @Override
    public String getModId() {
        return ZasPonderMod.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(atmos("pipe"), atmos("pipe_cap")).addStoryBoard(Stage.MEDIUM, PipeScenes::pipes, TAG);
        helper.forComponents(atmos("connector_port")).addStoryBoard(Stage.MEDIUM, PortScenes::connectorPort, TAG);
        helper.forComponents(atmos("manual_valve")).addStoryBoard(Stage.MEDIUM, ValveScenes::manual, TAG);
        helper.forComponents(atmos("redstone_valve")).addStoryBoard(Stage.MEDIUM, ValveScenes::redstone, TAG);
        helper.forComponents(atmos("pressure_pump")).addStoryBoard(Stage.MEDIUM, PumpScenes::pressure, TAG);
        helper.forComponents(atmos("volume_pump")).addStoryBoard(Stage.MEDIUM, PumpScenes::volume, TAG);
        // A mirrored item places the same block, with its side port on the left (D-77).
        helper.forComponents(atmos("filter"), atmos("filter_mirrored"))
                .addStoryBoard(Stage.MEDIUM, SidePortScenes::filter, TAG);
        helper.forComponents(atmos("mixer"), atmos("mixer_mirrored"))
                .addStoryBoard(Stage.MEDIUM, SidePortScenes::mixer, TAG);

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

        // Portables, power, the electrolyzer, the central controller and the handheld tools.
        helper.forComponents(atmos("canister")).addStoryBoard(Stage.MEDIUM, CanisterScenes::canister, TAG);
        helper.forComponents(atmos("portable_scrubber")).addStoryBoard(Stage.MEDIUM, PortableScenes::scrubber, TAG);
        helper.forComponents(atmos("portable_pump")).addStoryBoard(Stage.MEDIUM, PortableScenes::pump, TAG);
        helper.forComponents(atmos("generator")).addStoryBoard(Stage.MEDIUM, PowerScenes::generator, TAG);
        helper.forComponents(atmos("recharger")).addStoryBoard(Stage.MEDIUM, PowerScenes::recharger, TAG);
        helper.forComponents(atmos("electrolyzer")).addStoryBoard(Stage.MEDIUM, ElectrolyzerScenes::electrolyzer, TAG);
        helper.forComponents(atmos("central_atmos_controller"))
                .addStoryBoard(Stage.LARGE, CentralControllerScenes::central, TAG);
        helper.forComponents(atmos("multitool")).addStoryBoard(Stage.LARGE, ToolScenes::multitool, TAG);
        helper.forComponents(atmos("crowbar")).addStoryBoard(Stage.MEDIUM, ToolScenes::crowbar, TAG);
        helper.forComponents(atmos("fire_extinguisher")).addStoryBoard(Stage.MEDIUM, ToolScenes::fireExtinguisher, TAG);
        helper.forComponents(atmos("tank_transfer_valve"))
                .addStoryBoard(Stage.SMALL, TransferValveScenes::transferValve, TAG);
        helper.forComponents(atmos("atmos_analyzer")).addStoryBoard(Stage.MEDIUM, ToolScenes::analyzer, TAG);
        helper.forComponents(atmos("wrench")).addStoryBoard(Stage.MEDIUM, ToolScenes::wrench, TAG);
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
