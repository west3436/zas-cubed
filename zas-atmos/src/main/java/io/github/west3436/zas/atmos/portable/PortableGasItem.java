package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** A portable pump's or scrubber's item (CONT-018). It carries the gas of its store when mined, and lists it. */
public class PortableGasItem extends BlockItem {
    public PortableGasItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        GasParcel gas = stack.get(AtmosComponents.GAS.get());
        if (gas == null || gas.isEmpty()) {
            tooltip.add(Component.translatable("item.zas_atmos.canister.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable(
                        "item.zas_atmos.canister.pressure",
                        String.format(Locale.ROOT, "%.0f", GasCodecs.pressure(gas, PortableGasDevice.STORE_LITRES)),
                        String.format(Locale.ROOT, "%.1f", gas.temperature()))
                .withStyle(ChatFormatting.GRAY));
        gas.moles()
                .forEach((id, n) -> tooltip.add(Component.translatable(
                                "item.zas_atmos.canister.gas",
                                GasCodecs.name(id),
                                String.format(Locale.ROOT, "%.1f", n))
                        .withStyle(ChatFormatting.GRAY)));
    }
}
