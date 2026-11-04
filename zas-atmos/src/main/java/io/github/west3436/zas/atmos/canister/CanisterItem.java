package io.github.west3436.zas.atmos.canister;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.ContainerReactions;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** A canister's item. It carries the gas of a mined canister, or a creative fill (CONT-086), and lists it. */
public class CanisterItem extends BlockItem {
    /** A creative canister's pressure, kPa: 45 atm, half the rating. */
    public static final double FILL_KPA = 45 * 101.325;

    /** A creative canister's temperature, K. */
    public static final double FILL_K = 293.15;

    public CanisterItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * A creative canister of one gas at 45 atm and 293.15 K, painted in that gas's colour.
     *
     * @param gas the gas
     * @param color its paint
     * @return the stack
     */
    public ItemStack filled(ResourceLocation gas, CanisterColor color) {
        ItemStack stack = new ItemStack(this);
        double moles = FILL_KPA * PipeVolumes.CANISTER / (GasMixture.GAS_CONSTANT * FILL_K);
        stack.set(AtmosComponents.GAS.get(), new GasParcel(Map.of(gas, moles), FILL_K));
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(CanisterColor.PROPERTY, color));
        return stack;
    }

    /** A mined canister's gas goes on reacting while it is carried (CONT-100). */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel server && AtmosTicks.due(server, entity)) {
            ContainerReactions.react(stack, PipeVolumes.CANISTER);
        }
    }

    /** A mined canister's gas goes on reacting while it lies dropped (CONT-100). */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel server && AtmosTicks.due(server, entity)) {
            ContainerReactions.react(stack, PipeVolumes.CANISTER);
        }
        return false;
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
                        String.format(Locale.ROOT, "%.0f", GasCodecs.pressure(gas, PipeVolumes.CANISTER)),
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
