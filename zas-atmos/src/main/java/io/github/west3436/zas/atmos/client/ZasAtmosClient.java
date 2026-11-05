package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.heater.AirConditionerMenu;
import io.github.west3436.zas.atmos.heater.HeaterMenu;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.portable.PortableScrubberMenu;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The content mod's client entry point: block and item tints, device GUIs and item model properties. */
@Mod(value = ZasAtmosMod.MOD_ID, dist = Dist.CLIENT)
public final class ZasAtmosClient {
    public ZasAtmosClient(IEventBus modBus) {
        modBus.addListener(ZasAtmosClient::blockColors);
        modBus.addListener(ZasAtmosClient::itemColors);
        modBus.addListener(ZasAtmosClient::screens);
        modBus.addListener(ZasAtmosClient::setup);
        modBus.addListener(ZasAtmosClient::transferValveColors);
        modBus.addListener(ZasAtmosClient::transferValveModels);
        modBus.addListener(ZasAtmosClient::transferValveRenderer);
        TransferValveItem.settingsScreen = hand -> Minecraft.getInstance()
                .setScreen(new TransferValveScreen(hand, Component.translatable("item.zas_atmos.tank_transfer_valve")));
        GasTankItem.settingsScreen = hand -> Minecraft.getInstance()
                .setScreen(new TankScreen(hand, Component.translatable("gui.zas_atmos.gas_tank.title")));
        GasJetParticles.register(NeoForge.EVENT_BUS);
        ClientSounds.fadeInThinAir(ZasAtmosMod.MOD_ID);
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

    /** A TTV item's tanks are tinted by their kinds: layer 1 its first tank, or a lone tank, and layer 2 its second. */
    private static void transferValveColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> {
                    TransferValve valve = TransferValveItem.valve(stack);
                    return switch (tintIndex) {
                        case 1 ->
                            0xFF000000 | TransferValveTint.of(valve.first().isEmpty() ? valve.second() : valve.first());
                        case 2 -> 0xFF000000 | TransferValveTint.of(valve.second());
                        default -> -1;
                    };
                },
                AtmosItems.TANK_TRANSFER_VALVE.get());
    }

    /** A primed TTV's models have no block state to load them, so the game is told of each. */
    private static void transferValveModels(ModelEvent.RegisterAdditional event) {
        for (var byOpen : PrimedTransferValveRenderer.MODELS) {
            for (var model : byOpen) {
                event.register(model);
            }
        }
    }

    private static void transferValveRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AtmosEntities.PRIMED_TANK_TRANSFER_VALVE.get(), PrimedTransferValveRenderer::new);
    }

    /** The device GUIs (CONT-074). */
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(AtmosMenus.CANISTER.get(), CanisterScreen::new);
        event.register(AtmosMenus.VENT.get(), VentScreen::new);
        event.register(AtmosMenus.SCRUBBER.get(), ScrubberScreen::new);
        event.register(AtmosMenus.AREA_AIR_CONTROLLER.get(), ControllerScreen::new);
        event.register(AtmosMenus.GENERATOR.get(), GeneratorScreen::new);
        event.register(AtmosMenus.HEATER.get(), ThermoMachineScreen<HeaterMenu>::new);
        event.register(AtmosMenus.AIR_CONDITIONER.get(), ThermoMachineScreen<AirConditionerMenu>::new);
        event.register(AtmosMenus.PORTABLE_SCRUBBER.get(), PortableGasScreen<PortableScrubberMenu>::new);
        event.register(AtmosMenus.PORTABLE_PUMP.get(), PortablePumpScreen::new);
        event.register(AtmosMenus.RECHARGER.get(), RechargerScreen::new);
        event.register(AtmosMenus.PRESSURE_PUMP.get(), PumpScreen::pressure);
        event.register(AtmosMenus.VOLUME_PUMP.get(), PumpScreen::volume);
        event.register(AtmosMenus.FILTER.get(), FilterScreen::new);
        event.register(AtmosMenus.MIXER.get(), MixerScreen::new);
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
            // A TTV shows its tanks: zas_atmos:tanks is 0, 0.5 or 1 for no tanks, one or two, which
            // models/item/tank_transfer_valve.json maps to its _one and _two models.
            ItemProperties.register(
                    AtmosItems.TANK_TRANSFER_VALVE.get(),
                    ZasAtmosMod.id("tanks"),
                    (stack, level, entity, seed) -> TransferValveItem.tankCount(stack) / 2f);
        });
    }
}
