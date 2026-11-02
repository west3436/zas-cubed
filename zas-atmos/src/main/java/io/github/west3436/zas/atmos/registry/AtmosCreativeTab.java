package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterColor;
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
     * tools, then the crafting components.
     */
    private static void fill(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output out) {
        out.accept(AtmosItems.PIPE.get());
        out.accept(AtmosItems.PIPE_CAP.get());
        out.accept(AtmosItems.CONNECTOR_PORT.get());
        out.accept(AtmosItems.VENT.get());
        out.accept(AtmosItems.CANISTER.get());
        FILLED.forEach((gas, color) ->
                out.accept(AtmosItems.CANISTER.get().filled(ResourceLocation.fromNamespaceAndPath("zas", gas), color)));
        out.accept(AtmosItems.AREA_AIR_CONTROLLER.get());
        out.accept(AtmosItems.GENERATOR.get());
        out.accept(AtmosItems.HEATER.get());
        out.accept(AtmosItems.MULTITOOL.get());
        AtmosItems.components().forEach(c -> out.accept(c.get()));
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
