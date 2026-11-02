package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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

    /** The tab's contents, in order: pipes first, then devices. */
    private static void fill(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output out) {
        out.accept(AtmosItems.PIPE.get());
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
