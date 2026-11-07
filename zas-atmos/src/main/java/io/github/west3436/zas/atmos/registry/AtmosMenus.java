package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterMenu;
import io.github.west3436.zas.atmos.controller.CentralMenu;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerMenu;
import io.github.west3436.zas.atmos.filter.FilterMenu;
import io.github.west3436.zas.atmos.heater.AirConditionerMenu;
import io.github.west3436.zas.atmos.heater.HeaterMenu;
import io.github.west3436.zas.atmos.mixer.MixerMenu;
import io.github.west3436.zas.atmos.portable.PortablePumpMenu;
import io.github.west3436.zas.atmos.portable.PortableScrubberMenu;
import io.github.west3436.zas.atmos.power.GeneratorMenu;
import io.github.west3436.zas.atmos.pump.PumpMenu;
import io.github.west3436.zas.atmos.scrubber.ScrubberMenu;
import io.github.west3436.zas.atmos.ttv.TransferValveMenu;
import io.github.west3436.zas.atmos.vent.VentMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
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

    /** A canister's valve and release pressure (CONT-084). */
    public static final Supplier<MenuType<CanisterMenu>> CANISTER =
            MENUS.register("canister", () -> new MenuType<>(CanisterMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** A vent's mode and bounds (CONT-014). */
    public static final Supplier<MenuType<VentMenu>> VENT =
            MENUS.register("vent", () -> new MenuType<>(VentMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<ControllerMenu>> AREA_AIR_CONTROLLER =
            MENUS.register("area_air_controller", () -> IMenuTypeExtension.create(ControllerMenu::new));

    /** A central controller's range, its area air controllers' priorities, and opening theirs (CONT-123). */
    public static final Supplier<MenuType<CentralMenu>> CENTRAL_ATMOS_CONTROLLER =
            MENUS.register("central_atmos_controller", () -> IMenuTypeExtension.create(CentralMenu::new));

    public static final Supplier<MenuType<GeneratorMenu>> GENERATOR =
            MENUS.register("generator", () -> IMenuTypeExtension.create(GeneratorMenu::new));

    /** A heater's switch, setpoint and battery (CONT-019). */
    public static final Supplier<MenuType<HeaterMenu>> HEATER =
            MENUS.register("heater", () -> new MenuType<>(HeaterMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** An air conditioner's switch, setpoint and battery (CONT-019). */
    public static final Supplier<MenuType<AirConditionerMenu>> AIR_CONDITIONER = MENUS.register(
            "air_conditioner", () -> new MenuType<>(AirConditionerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** A portable scrubber's switch, battery and held tank (CONT-018). */
    public static final Supplier<MenuType<PortableScrubberMenu>> PORTABLE_SCRUBBER = MENUS.register(
            "portable_scrubber", () -> new MenuType<>(PortableScrubberMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** A portable pump's switch, direction, target, battery and held tank (CONT-018). */
    public static final Supplier<MenuType<PortablePumpMenu>> PORTABLE_PUMP =
            MENUS.register("portable_pump", () -> new MenuType<>(PortablePumpMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** A pressure pump's switch and target (CONT-011). */
    public static final Supplier<MenuType<PumpMenu>> PRESSURE_PUMP = MENUS.register(
            "pressure_pump",
            () -> new MenuType<>(
                    (id, inventory) -> new PumpMenu(AtmosMenus.PRESSURE_PUMP.get(), id), FeatureFlags.DEFAULT_FLAGS));

    /** A volume pump's switch and rate (CONT-012). */
    public static final Supplier<MenuType<PumpMenu>> VOLUME_PUMP = MENUS.register(
            "volume_pump",
            () -> new MenuType<>(
                    (id, inventory) -> new PumpMenu(AtmosMenus.VOLUME_PUMP.get(), id), FeatureFlags.DEFAULT_FLAGS));

    /** A filter's switch, target, side port and what it selects (CONT-013). */
    public static final Supplier<MenuType<FilterMenu>> FILTER =
            MENUS.register("filter", () -> IMenuTypeExtension.create(FilterMenu::new));

    /** A mixer's switch, target, side port and fractions (CONT-077). */
    public static final Supplier<MenuType<MixerMenu>> MIXER = MENUS.register(
            "mixer", () -> new MenuType<>((id, inventory) -> new MixerMenu(id), FeatureFlags.DEFAULT_FLAGS));

    /** A scrubber's mode and gas selection (CONT-015, CONT-079). */
    public static final Supplier<MenuType<ScrubberMenu>> SCRUBBER =
            MENUS.register("scrubber", () -> new MenuType<>(ScrubberMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** A held tank transfer valve's tanks, valve and timer (CONT-103, CONT-104). */
    public static final Supplier<MenuType<TransferValveMenu>> TANK_TRANSFER_VALVE =
            MENUS.register("tank_transfer_valve", () -> IMenuTypeExtension.create(TransferValveMenu::new));

    /** The electrolyzer's switch, outlets and water (CONT-055, CONT-056). */
    public static final Supplier<MenuType<ElectrolyzerMenu>> ELECTROLYZER = MENUS.register(
            "electrolyzer",
            () -> new MenuType<>((id, inventory) -> new ElectrolyzerMenu(id), FeatureFlags.DEFAULT_FLAGS));

    private AtmosMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
