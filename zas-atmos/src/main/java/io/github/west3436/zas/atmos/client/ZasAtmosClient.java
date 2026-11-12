package io.github.west3436.zas.atmos.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.compat.PonderGate;
import io.github.west3436.zas.atmos.gauge.GaugeReading;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.MagneticBootsItem;
import io.github.west3436.zas.atmos.heater.AirConditionerMenu;
import io.github.west3436.zas.atmos.heater.HeaterMenu;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.particle.DeviceJets;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.radiator.HeatExchangerBlock;
import io.github.west3436.zas.atmos.radiator.HeatExchangerTint;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The content mod's client entry point: block and item tints, device GUIs, item model properties, the
 * magnetic boots' keybind, the station blocks' connected textures with Fusion and the Ponder scenes
 * with Ponder.
 */
@Mod(value = ZasAtmosMod.MOD_ID, dist = Dist.CLIENT)
public final class ZasAtmosClient {
    /**
     * The key that switches the magnetic boots the player wears (CONT-033): B by default, beside the
     * internals key under "Atmospherics".
     */
    public static final KeyMapping TOGGLE_MAGNETIC_BOOTS = new KeyMapping(
            "key.zas_atmos.toggle_magnetic_boots",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.zas");

    public ZasAtmosClient(IEventBus modBus) {
        // FML builds the common entry point first, so the startup config is read (CONT-155).
        if (!ZasAtmosMod.contentEnabled()) {
            return;
        }
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(TOGGLE_MAGNETIC_BOOTS));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (TOGGLE_MAGNETIC_BOOTS.consumeClick()) {
                if (Minecraft.getInstance().getConnection() != null) {
                    PacketDistributor.sendToServer(MagneticBootsItem.TogglePayload.INSTANCE);
                }
            }
        });
        modBus.addListener(ZasAtmosClient::blockColors);
        modBus.addListener(ZasAtmosClient::itemColors);
        modBus.addListener(ZasAtmosClient::screens);
        modBus.addListener(ZasAtmosClient::setup);
        modBus.addListener(ZasAtmosClient::transferValveColors);
        modBus.addListener(ZasAtmosClient::transferValveModels);
        modBus.addListener(ZasAtmosClient::transferValveRenderer);
        modBus.addListener(ZasAtmosClient::gaugeRenderer);
        modBus.addListener(ConnectedTextures::addPack);
        modBus.addListener(FirelockDoorModel::wrap);
        // The Ponder scenes, on a client that has Ponder: Create brings it, and so does the ZAS³ Ponder
        // addon. Ponder gathers its plugins' scenes once every mod has set up, so this is in time.
        modBus.addListener((FMLClientSetupEvent e) -> PonderGate.start());
        GasTankItem.settingsScreen = hand -> Minecraft.getInstance()
                .setScreen(new TankScreen(hand, Component.translatable("gui.zas_atmos.gas_tank.title")));
        GasJetParticles.register(NeoForge.EVENT_BUS);
        DeviceLoops.register(NeoForge.EVENT_BUS);
        ClientSounds.fadeInThinAir(ZasAtmosMod.MOD_ID);

        // Pipe devices' running sounds (CONT-125).
        DeviceLoops.add(AtmosBlocks.PRESSURE_PUMP, AtmosSounds.PUMP_RUN);
        DeviceLoops.add(AtmosBlocks.VOLUME_PUMP, AtmosSounds.VOLUME_PUMP_RUN);
        DeviceLoops.add(AtmosBlocks.FILTER, AtmosSounds.FILTER_RUN);
        DeviceLoops.add(AtmosBlocks.MIXER, AtmosSounds.MIXER_RUN);
        DeviceLoops.add(AtmosBlocks.VENT, AtmosSounds.VENT_RUN, DeviceJets.MIN_LEVEL);
        DeviceLoops.add(AtmosBlocks.PASSIVE_VENT, AtmosSounds.PASSIVE_VENT_RUN);
        DeviceLoops.add(AtmosBlocks.SCRUBBER, AtmosSounds.SCRUBBER_RUN, DeviceJets.MIN_LEVEL);
        DeviceLoops.add(AtmosBlocks.CANISTER, AtmosSounds.CANISTER_RUN);
        DeviceLoops.add(AtmosBlocks.PIPE, AtmosSounds.PIPE_LEAK);

        // Running machines' loops (CONT-127). The generator's isn't here: it plays from its own ticker.
        DeviceLoops.add(AtmosBlocks.HEATER, AtmosSounds.HEATER_RUN);
        DeviceLoops.add(AtmosBlocks.AIR_CONDITIONER, AtmosSounds.AIR_CONDITIONER_RUN);
        DeviceLoops.add(AtmosBlocks.PORTABLE_PUMP, AtmosSounds.PORTABLE_PUMP_RUN);
        DeviceLoops.add(AtmosBlocks.PORTABLE_SCRUBBER, AtmosSounds.PORTABLE_SCRUBBER_RUN);
        DeviceLoops.add(AtmosBlocks.RECHARGER, AtmosSounds.RECHARGER_RUN);

        // The fire alarm's sound, one per client (CONT-128).
        FireAlarmSounds.register(NeoForge.EVENT_BUS);
    }

    /** Dyed pipes and caps are tinted with their dye's colour (CONT-114); tint index 0 is the whole pipe. */
    private static void blockColors(RegisterColorHandlersEvent.Block event) {
        BlockColor dye = (state, level, pos, tintIndex) -> tintIndex == 0 && state.hasProperty(PipeColor.PROPERTY)
                ? 0xFF000000 | state.getValue(PipeColor.PROPERTY).tint()
                : -1;
        event.register(dye, AtmosBlocks.PIPE_CAP.get());
        // A pipe in a casing too: its end in the casing's face is the pipe's (#487).
        event.register(dye, AtmosBlocks.pipes());
        // A large gas tank's shell: grey undyed, or its dye's colour.
        event.register(
                (state, level, pos, tintIndex) ->
                        tintIndex == 0 ? 0xFF000000 | LargeTankBlock.tint(state.getValue(PipeColor.PROPERTY)) : -1,
                AtmosBlocks.LARGE_GAS_TANK.get());
        // A heat exchanger's coil and pipe show its gas's temperature band (CONT-148).
        event.register(
                (state, level, pos, tintIndex) -> tintIndex == 0
                        ? 0xFF000000 | HeatExchangerTint.colour(state.getValue(HeatExchangerBlock.HEAT))
                        : -1,
                AtmosBlocks.HEAT_EXCHANGER.get());
        // A gauge's arc for a gas's share is in the gas's colour (#488).
        event.register(
                (state, level, pos, tintIndex) -> {
                    var gas = state.getValue(GaugeReading.PROPERTY).gas();
                    return tintIndex == 1 && gas != null ? 0xFF000000 | GasParts.colour(gas.toString()) : -1;
                },
                AtmosBlocks.GAUGE.get());

        // A tank holder's tanks are tinted by their kinds: tint index n is the tank in place n (CONT-151).
        event.register(
                (state, level, pos, tintIndex) -> level != null
                                && pos != null
                                && level.getBlockEntity(pos) instanceof TankHolderBlockEntity holder
                        ? holder.tint(tintIndex - 1)
                        : -1,
                AtmosBlocks.TANK_HOLDER.get());

        // A placed TTV's tanks are tinted as the item's are: index 1 its first or lone tank, 2 its second (D-86).
        event.register(
                (state, level, pos, tintIndex) -> level != null
                                && pos != null
                                && level.getBlockEntity(pos) instanceof TransferValveBlockEntity ttv
                        ? ttv.tint(tintIndex)
                        : -1,
                AtmosBlocks.TANK_TRANSFER_VALVE.get());
    }

    /** Dyed pipe and cap items are tinted like the blocks they place (CONT-114). */
    private static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) ->
                        tintIndex == 0 ? 0xFF000000 | PipeItem.color(stack).tint() : -1,
                AtmosItems.PIPE.get(),
                AtmosItems.PIPE_CAP.get());
        event.register(
                (stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | LargeTankBlock.tint(PipeItem.color(stack)) : -1,
                AtmosItems.LARGE_GAS_TANK.get());
        // A heat exchanger's item shows copper (CONT-148).
        event.register(
                (stack, tintIndex) ->
                        tintIndex == 0 ? 0xFF000000 | HeatExchangerTint.colour(HeatExchangerTint.COPPER) : -1,
                AtmosItems.HEAT_EXCHANGER.get());
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

    /** A gauge's needle (#488). */
    private static void gaugeRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AtmosBlockEntities.GAUGE.get(), GaugeRenderer::new);
    }

    /** The device GUIs (CONT-074). */
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(AtmosMenus.CANISTER.get(), CanisterScreen::new);
        event.register(AtmosMenus.VENT.get(), VentScreen::new);
        event.register(AtmosMenus.SCRUBBER.get(), ScrubberScreen::new);
        event.register(AtmosMenus.AREA_AIR_CONTROLLER.get(), ControllerScreen::new);
        event.register(AtmosMenus.CENTRAL_ATMOS_CONTROLLER.get(), CentralScreen::new);
        event.register(AtmosMenus.GENERATOR.get(), GeneratorScreen::new);
        event.register(AtmosMenus.HEATER.get(), ThermoMachineScreen<HeaterMenu>::new);
        event.register(AtmosMenus.AIR_CONDITIONER.get(), ThermoMachineScreen<AirConditionerMenu>::new);
        event.register(AtmosMenus.PORTABLE_SCRUBBER.get(), PortableScrubberScreen::new);
        event.register(AtmosMenus.PORTABLE_PUMP.get(), PortablePumpScreen::new);
        event.register(AtmosMenus.PRESSURE_PUMP.get(), PumpScreen::pressure);
        event.register(AtmosMenus.VOLUME_PUMP.get(), PumpScreen::volume);
        event.register(AtmosMenus.FILTER.get(), FilterScreen::new);
        event.register(AtmosMenus.MIXER.get(), MixerScreen::new);
        event.register(AtmosMenus.ELECTROLYZER.get(), ElectrolyzerScreen::new);
        event.register(AtmosMenus.TANK_TRANSFER_VALVE.get(), TransferValveScreen::new);
        event.register(AtmosMenus.ANALYZER.get(), AnalyzerScreen::new);
        event.register(AtmosMenus.AIRLOCK_CONTROLLER.get(), AirlockControllerScreen::new);
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
            // Magnetic boots show whether they're on: zas_atmos:on is 1 or 0, which
            // models/item/magnetic_boots.json maps to its _on model (CONT-033).
            ItemProperties.register(
                    AtmosItems.MAGNETIC_BOOTS.get(),
                    ZasAtmosMod.id("on"),
                    (stack, level, entity, seed) -> MagneticBootsItem.on(stack) ? 1 : 0);
        });
    }
}
