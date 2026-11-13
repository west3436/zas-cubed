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
import io.github.west3436.zas.atmos.label.Labels;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
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
 * <p>What it says names a part or a controller by its label, if a player gave it one ({@link Labels}),
 * and by its position if not.
 *
 * <p>It selects an airlock controller too, and then links airlocks and air sensors into its slots,
 * and vents, to it (CONT-138). Used sneaking on an airlock controller, it links that controller to the selected area
 * air controller as a part instead (CONT-141). With an area air controller selected, it links a
 * firelock fitted to an airlock or a door (CONT-137).
 *
 * <p>It acts before the block's own use, but only where it has something to do: a controller to
 * select, a part or firelock the selected controller links, or a central controller to move the
 * selected one to. Anywhere else the block gets the click, as it would from an empty hand: with
 * nothing to link, a part's window opens, a sensor is pressed and a door opens or closes, and the
 * controller already selected opens its own window. A sneak-click skips the block's use, so then the
 * multitool says why it has nothing to do, such as no controller being selected.
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

    /** Ahead of the block's own use: acts only where it has something to do. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return use(context, true);
    }

    /** Once the block's own use has passed, or a sneak-click skipped it: acts, or says why it can't. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return use(context, false);
    }

    /**
     * A use on a block: selects a controller, links or unlinks a part, or moves the selected controller
     * to a central controller. It decides from the stack and the blocks, which the client knows too.
     *
     * @param first whether the block's own use is still to come; if so, it passes where it has nothing
     *     to do, so the block gets the click
     * @return {@link InteractionResult#PASS} if it took no part
     */
    private static InteractionResult use(UseOnContext context, boolean first) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        Player player = context.getPlayer();
        boolean sneaking = player != null && player.isShiftKeyDown();
        Optional<BlockPos> at = selected(stack, level);
        if (be instanceof AreaAirControllerBlockEntity || (be instanceof AirlockControllerBlockEntity && !sneaking)) {
            // Sneaking, an airlock controller is a part to link to an area air controller (CONT-141).
            // The controller already selected has nothing to do, so its window opens.
            if (first && at.filter(pos::equals).isPresent()) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel) {
                select(stack, level, pos);
                tell(
                        player,
                        Component.translatable(
                                be instanceof AirlockControllerBlockEntity
                                        ? "item.zas_atmos.multitool.selected_airlock"
                                        : "item.zas_atmos.multitool.selected",
                                where(level, pos)));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockEntity selected = at.isPresent() && level.isLoaded(at.get()) ? level.getBlockEntity(at.get()) : null;
        if (state.getBlock() instanceof CentralControllerBlock) {
            // Any of the console's four blocks (CONT-118).
            CentralControllerBlockEntity central = CentralControllerBlock.entity(level, pos, state);
            if (central == null) {
                return InteractionResult.PASS;
            }
            if (!(selected instanceof AreaAirControllerBlockEntity)) {
                return nothingToDo(context, first, noController(at));
            }
            if (level instanceof ServerLevel server) {
                tell(player, move(stack, server, central));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (selected instanceof AirlockControllerBlockEntity) {
            // Linking to an airlock controller: an airlock by its origin, a sensor or a vent (CONT-138).
            boolean airlock = state.getBlock() instanceof AirlockBlock;
            if (!airlock && !(be instanceof AirSensorBlockEntity) && !(be instanceof VentBlockEntity)) {
                return InteractionResult.PASS;
            }
            return linkPart(context, airlock ? AirlockBlock.origin(state, pos) : pos);
        }
        // Linking to an area air controller: a powered part or a firelock (CONT-042, CONT-043).
        BlockPos part;
        if (state.getBlock() instanceof AirlockBlock) {
            // A firelock fitted to an airlock, from any of its blocks: the link is its origin (CONT-137).
            if (!state.getValue(AirlockBlock.FIRELOCK).fitted()) {
                return nothingToDo(context, first, Component.translatable("item.zas_atmos.multitool.no_firelock"));
            }
            part = AirlockBlock.origin(state, pos);
        } else if (state.getBlock() instanceof FirelockDoorBlock) {
            // A firelock fitted to a door, from either half: the link is its lower half (CONT-137).
            part = FirelockDoorBlock.lower(state, pos);
        } else if (state.getBlock() instanceof FirelockBlock) {
            // Either half: the firelock is its lower half (CONT-043).
            part = FirelockBlock.lower(state, pos);
        } else if (be instanceof PoweredDevice) {
            part = pos;
        } else {
            return InteractionResult.PASS;
        }
        if (!(selected instanceof AreaAirControllerBlockEntity)) {
            return nothingToDo(context, first, noController(at));
        }
        return linkPart(context, part);
    }

    /** Links or unlinks a part, by the position its link is kept at, and says which. */
    private static InteractionResult linkPart(UseOnContext context, BlockPos part) {
        if (context.getLevel() instanceof ServerLevel server) {
            tell(context.getPlayer(), link(context.getItemInHand(), server, part));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /**
     * A use with nothing to do: before the block's own use it passes, so the block gets the click;
     * after, it says why.
     */
    private static InteractionResult nothingToDo(UseOnContext context, boolean first, Component why) {
        if (first) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            tell(context.getPlayer(), why);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** Why there is no area air controller to link to: none is selected, or it's gone. */
    private static Component noController(Optional<BlockPos> at) {
        return at.isEmpty()
                ? Component.translatable("item.zas_atmos.multitool.no_controller")
                : Component.translatable("item.zas_atmos.multitool.controller_gone", pos(at.get()));
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
     * Links the part at a position to the selected controller, or unlinks it: to an area air
     * controller, a powered part or a firelock (CONT-042, CONT-043); to an airlock controller, an
     * airlock's origin, an air sensor or a vent (CONT-138).
     *
     * @return what to tell the player
     */
    public static Component link(ItemStack stack, ServerLevel level, BlockPos part) {
        Optional<BlockPos> at = selected(stack, level);
        BlockEntity selected = at.isPresent() && level.isLoaded(at.get()) ? level.getBlockEntity(at.get()) : null;
        if (selected instanceof AirlockControllerBlockEntity airlockController) {
            return switch (airlockController.toggleLink(level, part)) {
                case LINKED -> {
                    AirlockControllerBlockEntity.Slot slot = airlockController.slotOf(part);
                    yield slot == null
                            ? Component.translatable(
                                    "item.zas_atmos.multitool.linked_airlock",
                                    Labels.named(level, part),
                                    where(level, at.get()))
                            : Component.translatable(
                                    "item.zas_atmos.multitool.linked_slot", slotName(slot), Labels.named(level, part));
                }
                case UNLINKED ->
                    Component.translatable(
                            "item.zas_atmos.multitool.unlinked_airlock",
                            Labels.named(level, part),
                            where(level, at.get()));
                case AIRLOCKS_FULL -> Component.translatable("item.zas_atmos.multitool.airlocks_full");
                case SENSOR_FULL -> Component.translatable("item.zas_atmos.multitool.sensor_full");
                case WRONG_KIND -> {
                    AtmosSounds.play(level, part, AtmosSounds.AIRLOCK_DENIED);
                    AirlockControllerBlockEntity.Slot armed = airlockController.armed();
                    yield Component.translatable(
                            armed != null && armed.door()
                                    ? "item.zas_atmos.multitool.wants_airlock"
                                    : "item.zas_atmos.multitool.wants_sensor",
                            armed == null ? "" : slotName(armed));
                }
                case NOT_LINKABLE -> Component.translatable("item.zas_atmos.multitool.not_linkable");
            };
        }
        if (!(selected instanceof AreaAirControllerBlockEntity controller)) {
            return noController(at);
        }
        boolean linked = controller.toggleLink(part);
        return Component.translatable(
                linked ? "item.zas_atmos.multitool.linked" : "item.zas_atmos.multitool.unlinked",
                Labels.named(level, part),
                where(level, at.get()));
    }

    /**
     * Moves the selected area air controller to a central controller, from whichever had it, if it is
     * in that one's range (CONT-118).
     *
     * @return what to tell the player
     */
    public static Component move(ItemStack stack, ServerLevel level, CentralControllerBlockEntity central) {
        Optional<BlockPos> at = selected(stack, level);
        if (at.isEmpty()
                || !level.isLoaded(at.get())
                || !(level.getBlockEntity(at.get()) instanceof AreaAirControllerBlockEntity)) {
            return noController(at);
        }
        if (!central.take(at.get())) {
            return Component.translatable("item.zas_atmos.multitool.out_of_range", where(level, at.get()));
        }
        return Component.translatable(
                "item.zas_atmos.multitool.moved", where(level, at.get()), where(level, central.getBlockPos()));
    }

    /** An airlock controller's slot by its name in the controller's window. */
    private static Component slotName(AirlockControllerBlockEntity.Slot slot) {
        return Component.translatable("gui.zas_atmos.airlock_controller." + slot.key());
    }

    private static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /**
     * How a message says which controller it means: by its label, or without one by where it is, as
     * these messages always have.
     */
    private static Component where(Level level, BlockPos pos) {
        String label = Labels.of(level, pos);
        return label.isEmpty()
                ? Component.translatable("item.zas_atmos.multitool.at", pos(pos))
                : Component.literal(label);
    }

    private static void tell(Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }
}
