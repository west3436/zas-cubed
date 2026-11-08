package io.github.west3436.zas.atmos.tool;

import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.device.DeviceData;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
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
 * controller alarms (CONT-043). Used on a central atmospherics controller, it moves the selected
 * controller to that one, if it is in range (CONT-118). Use it on nothing while sneaking to clear the
 * selection. The selection is kept on the stack, as the controller's position and dimension.
 *
 * <p>It selects an airlock controller too, and then links airlocks, air sensors and vents to it
 * (CONT-138). Used sneaking on an airlock controller, it links that controller to the selected area
 * air controller as a part instead (CONT-141). With an area air controller selected, it links a
 * firelock fitted to an airlock or a door (CONT-137).
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
        boolean sneaking = player != null && player.isShiftKeyDown();
        if (be instanceof AreaAirControllerBlockEntity || (be instanceof AirlockControllerBlockEntity && !sneaking)) {
            // Sneaking, an airlock controller is a part to link to an area air controller (CONT-141).
            if (level instanceof ServerLevel) {
                select(context.getItemInHand(), level, context.getClickedPos());
                tell(
                        player,
                        Component.translatable(
                                be instanceof AirlockControllerBlockEntity
                                        ? "item.zas_atmos.multitool.selected_airlock"
                                        : "item.zas_atmos.multitool.selected",
                                pos(context.getClickedPos())));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockState state = level.getBlockState(context.getClickedPos());
        if (state.getBlock() instanceof CentralControllerBlock) {
            // Any of the console's four blocks (CONT-118).
            CentralControllerBlockEntity central = CentralControllerBlock.entity(level, context.getClickedPos(), state);
            if (central != null && level instanceof ServerLevel server) {
                tell(player, move(context.getItemInHand(), server, central));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel server
                && selectedBlock(context.getItemInHand(), server) instanceof AirlockControllerBlockEntity) {
            // Linking to an airlock controller: an airlock by its origin, a sensor or a vent (CONT-138).
            boolean airlock = state.getBlock() instanceof AirlockBlock;
            if (!airlock && !(be instanceof AirSensorBlockEntity) && !(be instanceof VentBlockEntity)) {
                return InteractionResult.PASS;
            }
            BlockPos part = airlock ? AirlockBlock.origin(state, context.getClickedPos()) : context.getClickedPos();
            tell(player, link(context.getItemInHand(), server, part));
            return InteractionResult.SUCCESS;
        }
        if (state.getBlock() instanceof AirlockBlock) {
            // A firelock fitted to an airlock, from any of its blocks: the link is its origin (CONT-137).
            if (level instanceof ServerLevel server) {
                tell(
                        player,
                        state.getValue(AirlockBlock.FIRELOCK).fitted()
                                ? link(
                                        context.getItemInHand(),
                                        server,
                                        AirlockBlock.origin(state, context.getClickedPos()))
                                : Component.translatable("item.zas_atmos.multitool.no_firelock"));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getBlock() instanceof FirelockDoorBlock) {
            // A firelock fitted to a door, from either half: the link is its lower half (CONT-137).
            if (level instanceof ServerLevel server) {
                tell(
                        player,
                        link(context.getItemInHand(), server, FirelockDoorBlock.lower(state, context.getClickedPos())));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
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

    /** The selected controller's block entity, if it is loaded in this level. */
    private static BlockEntity selectedBlock(ItemStack stack, ServerLevel level) {
        Optional<BlockPos> at = selected(stack, level);
        return at.isPresent() && level.isLoaded(at.get()) ? level.getBlockEntity(at.get()) : null;
    }

    /**
     * Links the part at a position to the selected controller, or unlinks it: to an area air
     * controller, a powered part or a firelock (CONT-042, CONT-043); to an airlock controller, an
     * airlock's origin, an air sensor or a vent (CONT-138).
     *
     * @return what to tell the player
     */
    public static Component link(ItemStack stack, ServerLevel level, BlockPos part) {
        Optional<BlockPos> at = selected(stack, level);
        if (at.isEmpty()) {
            return Component.translatable("item.zas_atmos.multitool.no_controller");
        }
        BlockEntity selected = level.isLoaded(at.get()) ? level.getBlockEntity(at.get()) : null;
        if (selected instanceof AirlockControllerBlockEntity airlockController) {
            return switch (airlockController.toggleLink(level, part)) {
                case LINKED ->
                    Component.translatable("item.zas_atmos.multitool.linked_airlock", pos(part), pos(at.get()));
                case UNLINKED ->
                    Component.translatable("item.zas_atmos.multitool.unlinked_airlock", pos(part), pos(at.get()));
                case AIRLOCKS_FULL -> Component.translatable("item.zas_atmos.multitool.airlocks_full");
                case SENSOR_FULL -> Component.translatable("item.zas_atmos.multitool.sensor_full");
                case NOT_LINKABLE -> Component.translatable("item.zas_atmos.multitool.not_linkable");
            };
        }
        if (!(selected instanceof AreaAirControllerBlockEntity controller)) {
            return Component.translatable("item.zas_atmos.multitool.controller_gone", pos(at.get()));
        }
        boolean linked = controller.toggleLink(part);
        return Component.translatable(
                linked ? "item.zas_atmos.multitool.linked" : "item.zas_atmos.multitool.unlinked",
                pos(part),
                pos(at.get()));
    }

    /**
     * Moves the selected area air controller to a central controller, from whichever had it, if it is
     * in that one's range (CONT-118).
     *
     * @return what to tell the player
     */
    public static Component move(ItemStack stack, ServerLevel level, CentralControllerBlockEntity central) {
        Optional<BlockPos> at = selected(stack, level);
        if (at.isEmpty()) {
            return Component.translatable("item.zas_atmos.multitool.no_controller");
        }
        if (!level.isLoaded(at.get()) || !(level.getBlockEntity(at.get()) instanceof AreaAirControllerBlockEntity)) {
            return Component.translatable("item.zas_atmos.multitool.controller_gone", pos(at.get()));
        }
        if (!central.take(at.get())) {
            return Component.translatable("item.zas_atmos.multitool.out_of_range", pos(at.get()));
        }
        return Component.translatable("item.zas_atmos.multitool.moved", pos(at.get()), pos(central.getBlockPos()));
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
