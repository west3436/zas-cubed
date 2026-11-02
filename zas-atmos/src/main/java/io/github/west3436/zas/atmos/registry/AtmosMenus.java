package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.power.GeneratorMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The content mod's menu types, one per device GUI. The server validates every setting a GUI
 * sends (CONT-074).
 */
public final class AtmosMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ZasAtmosMod.MOD_ID);

    public static final Supplier<MenuType<ControllerMenu>> AREA_AIR_CONTROLLER =
            MENUS.register("area_air_controller", () -> IMenuTypeExtension.create(ControllerMenu::new));

    public static final Supplier<MenuType<GeneratorMenu>> GENERATOR =
            MENUS.register("generator", () -> IMenuTypeExtension.create(GeneratorMenu::new));

    private AtmosMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
