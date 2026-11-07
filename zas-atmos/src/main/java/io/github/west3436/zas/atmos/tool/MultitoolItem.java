package io.github.west3436.zas.atmos.tool;

import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.DeviceData;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The multitool links parts to an area air controller by hand (CONT-042, CONT-068): use it on a
 * controller to select it, then on a part or a firelock to link it to that controller, or unlink
 * it if it is linked. A linked part is powered and commanded; a linked firelock closes when the
 * controller alarms (CONT-043). Use it on nothing while sneaking to clear the selection. The
 * selection is kept on the stack, as the controller's position and dimension.
 */
public class MultitoolItem extends Item {
    private static final String CONTROLLER = "Controller";
    private static final String DIMENSION = "Dimension";

    /**
     * What brings a saved selection forward, oldest first, for {@link DeviceData#migrate(CompoundTag,
     * List)} (DATA-012). Version 0 is a selection saved before versions were added, and its fields are
     * those of version 1. It's brought forward each time it's read, and written only when one is made.
     */
    private static final List<Consumer<CompoundTag>> FORMAT_STEPS = List.of(tag -> {});

    public MultitoolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        Player player = context.getPlayer();
        if (be instanceof AreaAirControllerBlockEntity) {
            if (level instanceof ServerLevel) {
                select(context.getItemInHand(), level, context.getClickedPos());
                tell(player, Component.translatable("item.zas_atmos.multitool.selected", pos(context.getClickedPos())));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockState state = level.getBlockState(context.getClickedPos());
        if (state.getBlock() instanceof FirelockBlock) {
            // Either half: the firelock is its lower half (CONT-043).
            if (level instanceof ServerLevel server) {
                BlockPos lower = FirelockBlock.lower(state, context.getClickedPos());
                tell(player, link(context.getItemInHand(), server, lower));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (be instanceof PoweredDevice) {
            if (level instanceof ServerLevel server) {
                tell(player, link(context.getItemInHand(), server, context.getClickedPos()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && selected(stack, level).isPresent()) {
            if (!level.isClientSide) {
                stack.remove(DataComponents.CUSTOM_DATA);
                tell(player, Component.translatable("item.zas_atmos.multitool.cleared"));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** Selects the controller at a position. */
    public static void select(ItemStack stack, Level level, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DeviceData.VERSION_KEY, FORMAT_STEPS.size());
        tag.putLong(CONTROLLER, pos.asLong());
        tag.putString(DIMENSION, level.dimension().location().toString());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    /** The selected controller's position, if it is in this level. */
    public static Optional<BlockPos> selected(ItemStack stack, Level level) {
        CompoundTag tag =
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        DeviceData.migrate(tag, FORMAT_STEPS);
        if (!tag.contains(CONTROLLER)
                || !level.dimension().location().toString().equals(tag.getString(DIMENSION))) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(tag.getLong(CONTROLLER)));
    }

    /**
     * Links the part at a position to the selected controller, or unlinks it.
     *
     * @return what to tell the player
     */
    public static Component link(ItemStack stack, ServerLevel level, BlockPos part) {
        Optional<BlockPos> at = selected(stack, level);
        if (at.isEmpty()) {
            return Component.translatable("item.zas_atmos.multitool.no_controller");
        }
        if (!level.isLoaded(at.get())
                || !(level.getBlockEntity(at.get()) instanceof AreaAirControllerBlockEntity controller)) {
            return Component.translatable("item.zas_atmos.multitool.controller_gone", pos(at.get()));
        }
        boolean linked = controller.toggleLink(part);
        return Component.translatable(
                linked ? "item.zas_atmos.multitool.linked" : "item.zas_atmos.multitool.unlinked",
                pos(part),
                pos(at.get()));
    }

    private static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static void tell(Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }
}
