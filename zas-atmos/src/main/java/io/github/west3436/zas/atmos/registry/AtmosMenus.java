package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterMenu;
import io.github.west3436.zas.atmos.vent.VentMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The content mod's menu types, one per device GUI. The server validates every setting a GUI
 * sends (CONT-074).
 */
public final class AtmosMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ZasAtmosMod.MOD_ID);

    /** A canister's valve and release pressure (CONT-084). */
    public static final Supplier<MenuType<CanisterMenu>> CANISTER =
            MENUS.register("canister", () -> new MenuType<>(CanisterMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** A vent's mode and bounds (CONT-014). */
    public static final Supplier<MenuType<VentMenu>> VENT =
            MENUS.register("vent", () -> new MenuType<>(VentMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private AtmosMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
