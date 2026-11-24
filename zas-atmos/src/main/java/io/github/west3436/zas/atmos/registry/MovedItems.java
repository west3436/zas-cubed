package io.github.west3436.zas.atmos.registry;

import io.github.west3436.ss13.api.FrameworkApi;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Items that were this mod's and are now SS13 Framework's: the crafting components, after vg's stock
 * parts (D-72), and the multitool, the wrench and the crowbar, whose behaviour on this mod's blocks
 * is still this mod's ({@link io.github.west3436.zas.atmos.tool.ToolProviders}). The framework registers them, with their recipes, models, textures and names, so
 * every mod that builds from them shares one of each. Each old {@code zas_atmos:} id is an alias of
 * the framework's, so a world saved on 1.x, its chests and its players' inventories keep every one.
 *
 * <p>The aliases are registered even with the content off (CONT-155): they register no item of this
 * mod, and the items they name are the framework's, which loads either way.
 */
public final class MovedItems {
    /** Registers no items of its own: it only holds the aliases. */
    private static final DeferredRegister.Items ALIASES = DeferredRegister.createItems(ZasAtmosMod.MOD_ID);

    /** The crafting components that moved to the framework, with the same path in its namespace. */
    public static final List<String> TO_FRAMEWORK = List.of(
            "cable_coil",
            "circuit_board",
            "electronics",
            "machine_frame",
            "capacitor",
            "advanced_capacitor",
            "super_capacitor",
            "scanning_module",
            "micro_manipulator",
            "micro_laser",
            "console_screen",
            "igniter_assembly",
            "timer_assembly",
            "ansible_crystal",
            "subspace_ansible",
            "subspace_transmitter",
            "bluespace_crystal",
            "plasteel_sheet",
            "reinforced_glass");

    /** The tools that moved to the framework (#540), with the same path in its namespace. */
    public static final List<String> TOOLS = List.of("multitool", "wrench", "crowbar");

    private MovedItems() {}

    /** The framework's id of an item that moved there. */
    public static ResourceLocation framework(String path) {
        return ResourceLocation.fromNamespaceAndPath(FrameworkApi.MOD_ID, path);
    }

    public static void register(IEventBus modBus) {
        TO_FRAMEWORK.forEach(path -> ALIASES.addAlias(ZasAtmosMod.id(path), framework(path)));
        TOOLS.forEach(path -> ALIASES.addAlias(ZasAtmosMod.id(path), framework(path)));
        ALIASES.register(modBus);
    }
}
