package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The content mod's client entry point: block and item tints, device GUIs and item model properties. */
@Mod(value = ZasAtmosMod.MOD_ID, dist = Dist.CLIENT)
public final class ZasAtmosClient {
    public ZasAtmosClient(IEventBus modBus) {
        modBus.addListener(ZasAtmosClient::blockColors);
        modBus.addListener(ZasAtmosClient::itemColors);
        modBus.addListener(ZasAtmosClient::screens);
        modBus.addListener(ZasAtmosClient::setup);
    }

    /** Dyed pipes and caps are tinted with their dye's colour (CONT-114); tint index 0 is the whole pipe. */
    private static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> tintIndex == 0 && state.hasProperty(PipeColor.PROPERTY)
                        ? 0xFF000000 | state.getValue(PipeColor.PROPERTY).tint()
                        : -1,
                AtmosBlocks.PIPE.get(),
                AtmosBlocks.PIPE_CAP.get());
    }

    /** Dyed pipe and cap items are tinted like the blocks they place (CONT-114). */
    private static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) ->
                        tintIndex == 0 ? 0xFF000000 | PipeItem.color(stack).tint() : -1,
                AtmosItems.PIPE.get(),
                AtmosItems.PIPE_CAP.get());
    }

    /** The device GUIs (CONT-074). */
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(AtmosMenus.CANISTER.get(), CanisterScreen::new);
        event.register(AtmosMenus.VENT.get(), VentScreen::new);
        event.register(AtmosMenus.AREA_AIR_CONTROLLER.get(), ControllerScreen::new);
        event.register(AtmosMenus.GENERATOR.get(), GeneratorScreen::new);
        event.register(AtmosMenus.HEATER.get(), HeaterScreen::new);
        event.register(AtmosMenus.RECHARGER.get(), RechargerScreen::new);
    }

    /**
     * A canister item shows its paint: {@code zas_atmos:color} is the colour's index over 32, which
     * {@code models/item/canister.json}'s overrides map to each colour's model. A battery shows its
     * charge: {@code zas_atmos:charge} is 0 empty, 0.5 part charged and 1 full, which each battery's
     * item model maps to its {@code _partial} and {@code _full} models.
     */
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    AtmosItems.CANISTER.get(), ZasAtmosMod.id("color"), (stack, level, entity, seed) -> {
                        BlockItemStateProperties state = stack.get(DataComponents.BLOCK_STATE);
                        CanisterColor color = state == null ? null : state.get(CanisterColor.PROPERTY);
                        return color == null ? 0 : color.ordinal() / 32f;
                    });
            AtmosItems.batteries()
                    .forEach(b ->
                            ItemProperties.register(b.get(), ZasAtmosMod.id("charge"), (stack, level, entity, seed) -> {
                                float charge = BatteryItem.charge(stack);
                                return charge <= 0 ? 0 : charge >= 1 ? 1 : 0.5f;
                            }));
        });
    }
}
