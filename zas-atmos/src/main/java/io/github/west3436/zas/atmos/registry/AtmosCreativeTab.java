package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The "ZAS³ Atmospherics" creative tab, which lists every block and item of the content mod. */
public final class AtmosCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZasAtmosMod.MOD_ID);

    public static final Supplier<CreativeModeTab> TAB = TABS.register(
            "atmospherics",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.zas_atmos"))
                    .icon(() -> new ItemStack(AtmosItems.PIPE.get()))
                    .displayItems(AtmosCreativeTab::fill)
                    .build());

    private AtmosCreativeTab() {}

    /**
     * The creative canisters, one per built-in gas (D-42), each in its gas's colour (texture
     * procurement §6.4). They exist only here (CONT-086).
     */
    public static final Map<String, CanisterColor> FILLED = filled();

    private static Map<String, CanisterColor> filled() {
        Map<String, CanisterColor> m = new LinkedHashMap<>();
        m.put("oxygen", CanisterColor.BLUE);
        m.put("nitrogen", CanisterColor.RED);
        m.put("carbon_dioxide", CanisterColor.BLACK);
        m.put("nitrous_oxide", CanisterColor.RED_WHITE);
        m.put("hydrogen", CanisterColor.ORANGE);
        m.put("water_vapor", CanisterColor.WHITE);
        m.put("methane", CanisterColor.BROWN);
        m.put("carbon_monoxide", CanisterColor.PURPLE);
        return Collections.unmodifiableMap(m);
    }

    /**
     * The tab's contents, in order: pipes first, then devices, then the creative canisters, then the
     * station blocks, then the tools, then the breathing gear, then the crafting components.
     */
    private static void fill(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output out) {
        out.accept(AtmosItems.PIPE.get());
        out.accept(AtmosItems.PIPE_CAP.get());
        out.accept(AtmosItems.MANUAL_VALVE.get());
        out.accept(AtmosItems.REDSTONE_VALVE.get());
        out.accept(AtmosItems.CONNECTOR_PORT.get());
        out.accept(AtmosItems.VENT.get());
        out.accept(AtmosItems.PRESSURE_PUMP.get());
        out.accept(AtmosItems.VOLUME_PUMP.get());
        out.accept(AtmosItems.FILTER.get());
        out.accept(AtmosItems.FILTER_MIRRORED.get());
        out.accept(AtmosItems.MIXER.get());
        out.accept(AtmosItems.MIXER_MIRRORED.get());
        out.accept(AtmosItems.SCRUBBER.get());
        out.accept(AtmosItems.PASSIVE_VENT.get());
        out.accept(AtmosItems.CANISTER.get());
        FILLED.forEach((gas, color) ->
                out.accept(AtmosItems.CANISTER.get().filled(ResourceLocation.fromNamespaceAndPath("zas", gas), color)));
        out.accept(AtmosItems.AREA_AIR_CONTROLLER.get());
        out.accept(AtmosItems.FIRE_ALARM.get());
        out.accept(AtmosItems.GENERATOR.get());
        out.accept(AtmosItems.HEATER.get());
        out.accept(AtmosItems.AIR_CONDITIONER.get());
        out.accept(AtmosItems.PORTABLE_SCRUBBER.get());
        out.accept(AtmosItems.PORTABLE_PUMP.get());
        out.accept(AtmosItems.FIRELOCK.get());
        out.accept(AtmosItems.RECHARGER.get());
        out.accept(AtmosItems.IGNITER.get());
        AtmosItems.batteries().forEach(b -> {
            out.accept(b.get());
            out.accept(b.get().full());
        });
        AtmosItems.stationBlocks().forEach(b -> out.accept(b.get()));
        out.accept(AtmosItems.MULTITOOL.get());
        out.accept(AtmosItems.CROWBAR.get());
        out.accept(AtmosItems.FIRE_EXTINGUISHER.get());
        out.accept(AtmosItems.TANK_TRANSFER_VALVE.get());
        out.accept(AtmosItems.OXYGEN_TANK.get());
        out.accept(AtmosItems.AIR_TANK.get());
        out.accept(AtmosItems.EMPTY_TANK.get());
        ItemStack emergency = new ItemStack(AtmosItems.EMERGENCY_OXYGEN_TANK.get());
        GasTankItem.setGas(emergency, AtmosItems.emergencyFill());
        out.accept(emergency);
        out.accept(AtmosItems.EMERGENCY_OXYGEN_TANK.get());
        out.accept(AtmosItems.BREATH_MASK.get());
        out.accept(AtmosItems.SOFTSUIT_HELMET.get());
        out.accept(AtmosItems.SOFTSUIT_CHESTPLATE.get());
        out.accept(AtmosItems.SOFTSUIT_LEGGINGS.get());
        out.accept(AtmosItems.SOFTSUIT_BOOTS.get());
        AtmosItems.components().forEach(c -> out.accept(c.get()));
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
