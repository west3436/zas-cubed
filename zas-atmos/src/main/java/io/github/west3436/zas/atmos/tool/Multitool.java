package io.github.west3436.zas.atmos.tool;

import io.github.west3436.ss13.api.label.Labels;
import io.github.west3436.ss13.api.tool.Linkable;
import io.github.west3436.ss13.tool.MultitoolItem;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.igniter.IgniterBlock;
import io.github.west3436.zas.atmos.power.PoweredPart;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.atmos.turbine.CompressorBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

/**
 * What SS13 Framework's multitool links among this mod's blocks, as their {@link Linkable}
 * capability (CONT-042, CONT-068): it selects a controller, then links a part or a firelock to it,
 * or unlinks it if it is linked. A linked part is powered and commanded; a linked firelock closes
 * when the controller alarms (CONT-043).
 *
 * <ul>
 *   <li>An <b>area air controller</b> is a source. It links a powered part, a firelock on its own and
 *       one fitted to a door, each by its lower half, and a firelock fitted to an airlock, by the
 *       airlock's origin (CONT-137); an airlock with no firelock it refuses, saying so. Used on a
 *       central atmospherics controller, any of its four blocks, the multitool moves the selected
 *       controller to that one, if it is in range (CONT-118).
 *   <li>An <b>airlock controller</b> is a source too, linking airlocks, by their origin, and air
 *       sensors into its slots, and vents to it (CONT-138). Sneaking it is a target instead: a part an
 *       area air controller links, as any powered device is (CONT-141).
 *   <li>A <b>gas turbine controller</b> is a source, linking a turbine, a compressor and an igniter
 *       (CONT-190).
 *   <li>Everything a source links is a target: powered devices, firelocks, firelock doors, airlocks,
 *       air sensors, vents, the turbine's parts and the central controller.
 * </ul>
 *
 * <p>What the multitool says names a part or a controller by its label, if a player gave it one
 * ({@link Labels}), and by its position if not. A turbine controller and an airlock controller keep
 * their links' positions, as do a turbine and a compressor their controller's, so each follows a
 * mover that moves them ({@link Linkable#rebaseAll}, CONT-156).
 */
public final class Multitool {
    private Multitool() {}

    /** Registers the capability on every block of this mod; {@link #of} answers which role each plays. */
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Linkable.BLOCK,
                (level, pos, state, be, context) -> of(state, be),
                AtmosBlocks.BLOCKS.getEntries().stream()
                        .map(DeferredHolder::get)
                        .toArray(Block[]::new));
    }

    /** The role a block plays in the multitool's links, or null if it plays none. */
    static @Nullable Linkable of(BlockState state, @Nullable BlockEntity be) {
        if (be instanceof AreaAirControllerBlockEntity) {
            return AREA_AIR_CONTROLLER;
        }
        if (be instanceof AirlockControllerBlockEntity) {
            return AIRLOCK_CONTROLLER;
        }
        if (be instanceof TurbineControllerBlockEntity) {
            return TURBINE_CONTROLLER;
        }
        Block block = state.getBlock();
        if (block instanceof CentralControllerBlock) {
            return CENTRAL_CONTROLLER;
        }
        if (block instanceof AirlockBlock) {
            return AIRLOCK;
        }
        if (block instanceof FirelockDoorBlock) {
            return FIRELOCK_DOOR;
        }
        if (block instanceof FirelockBlock) {
            return FIRELOCK;
        }
        if (be instanceof TurbineBlockEntity || be instanceof CompressorBlockEntity) {
            return TURBINE_PART;
        }
        if (be instanceof PoweredPart
                || be instanceof AirSensorBlockEntity
                || be instanceof VentBlockEntity
                || block instanceof IgniterBlock) {
            return PART;
        }
        return null;
    }

    // ---- Sources -----------------------------------------------------------------------------

    /** An area air controller: powered parts and firelocks, and the central controller it moves to. */
    private static final Linkable AREA_AIR_CONTROLLER = new Linkable() {
        @Override
        public Optional<BlockPos> source(UseOnContext use) {
            return Optional.of(use.getClickedPos());
        }

        @Override
        public Component selected(ServerLevel level, BlockPos source) {
            return Component.translatable("item.zas_atmos.multitool.selected", where(level, source));
        }

        @Override
        public boolean accepts(Level level, BlockPos source, BlockPos target) {
            BlockState state = level.getBlockState(target);
            if (state.getBlock() instanceof AirlockBlock) {
                return state.getValue(AirlockBlock.FIRELOCK).fitted();
            }
            return state.getBlock() instanceof FirelockBlock
                    || state.getBlock() instanceof FirelockDoorBlock
                    || state.getBlock() instanceof CentralControllerBlock
                    || level.getBlockEntity(target) instanceof PoweredPart;
        }

        @Override
        public Component refusal(ServerLevel level, BlockPos source, BlockPos target) {
            return Component.translatable(
                    level.getBlockState(target).getBlock() instanceof AirlockBlock
                            ? "item.zas_atmos.multitool.no_firelock"
                            : "item.zas_atmos.multitool.not_linkable_area");
        }

        @Override
        public Component link(ServerLevel level, BlockPos source, BlockPos target, @Nullable Player player) {
            if (level.getBlockState(target).getBlock() instanceof CentralControllerBlock
                    && level.getBlockEntity(target) instanceof CentralControllerBlockEntity central) {
                return move(level, source, central);
            }
            if (!(level.getBlockEntity(source) instanceof AreaAirControllerBlockEntity controller)) {
                return refusal(level, source, target);
            }
            boolean linked = controller.toggleLink(target);
            return Component.translatable(
                    linked ? "item.zas_atmos.multitool.linked" : "item.zas_atmos.multitool.unlinked",
                    Labels.named(level, target),
                    where(level, source));
        }

        @Override
        public void rebase(ServerLevel level, BlockPos pos, UnaryOperator<@Nullable BlockPos> rebase) {
            if (level.getBlockEntity(pos) instanceof AreaAirControllerBlockEntity controller) {
                controller.rebase(rebase);
            }
        }
    };

    /**
     * An airlock controller: airlocks, by their origin, and sensors into its slots, and vents. Sneaking,
     * it isn't a source but a part an area air controller links (CONT-141).
     */
    private static final Linkable AIRLOCK_CONTROLLER = new Linkable() {
        @Override
        public Optional<BlockPos> source(UseOnContext use) {
            Player player = use.getPlayer();
            return player != null && player.isShiftKeyDown() ? Optional.empty() : Optional.of(use.getClickedPos());
        }

        @Override
        public Component selected(ServerLevel level, BlockPos source) {
            return Component.translatable("item.zas_atmos.multitool.selected_airlock", where(level, source));
        }

        @Override
        public boolean accepts(Level level, BlockPos source, BlockPos target) {
            BlockEntity be = level.getBlockEntity(target);
            return level.getBlockState(target).getBlock() instanceof AirlockBlock
                    || be instanceof AirSensorBlockEntity
                    || be instanceof VentBlockEntity;
        }

        @Override
        public Component refusal(ServerLevel level, BlockPos source, BlockPos target) {
            return Component.translatable("item.zas_atmos.multitool.not_linkable");
        }

        @Override
        public Component link(ServerLevel level, BlockPos source, BlockPos target, @Nullable Player player) {
            if (!(level.getBlockEntity(source) instanceof AirlockControllerBlockEntity controller)) {
                return refusal(level, source, target);
            }
            return switch (controller.toggleLink(level, target)) {
                case LINKED -> {
                    AirlockControllerBlockEntity.Slot slot = controller.slotOf(target);
                    yield slot == null
                            ? Component.translatable(
                                    "item.zas_atmos.multitool.linked_airlock",
                                    Labels.named(level, target),
                                    where(level, source))
                            : Component.translatable(
                                    "item.zas_atmos.multitool.linked_slot",
                                    slotName(slot),
                                    Labels.named(level, target));
                }
                case UNLINKED ->
                    Component.translatable(
                            "item.zas_atmos.multitool.unlinked_airlock",
                            Labels.named(level, target),
                            where(level, source));
                case AIRLOCKS_FULL -> Component.translatable("item.zas_atmos.multitool.airlocks_full");
                case SENSOR_FULL -> Component.translatable("item.zas_atmos.multitool.sensor_full");
                case WRONG_KIND -> {
                    AtmosSounds.play(level, target, AtmosSounds.AIRLOCK_DENIED);
                    AirlockControllerBlockEntity.Slot armed = controller.armed();
                    yield Component.translatable(
                            armed != null && armed.door()
                                    ? "item.zas_atmos.multitool.wants_airlock"
                                    : "item.zas_atmos.multitool.wants_sensor",
                            armed == null ? "" : slotName(armed));
                }
                case NOT_LINKABLE -> refusal(level, source, target);
            };
        }

        @Override
        public void rebase(ServerLevel level, BlockPos pos, UnaryOperator<@Nullable BlockPos> rebase) {
            if (level.getBlockEntity(pos) instanceof AirlockControllerBlockEntity controller) {
                controller.rebase(rebase);
            }
        }
    };

    /** A gas turbine controller: a turbine, a compressor and an igniter (CONT-190). */
    private static final Linkable TURBINE_CONTROLLER = new Linkable() {
        @Override
        public Optional<BlockPos> source(UseOnContext use) {
            return Optional.of(use.getClickedPos());
        }

        @Override
        public Component selected(ServerLevel level, BlockPos source) {
            return Component.translatable("item.zas_atmos.multitool.selected_turbine", where(level, source));
        }

        @Override
        public boolean accepts(Level level, BlockPos source, BlockPos target) {
            return TurbineControllerBlockEntity.partAt(level, target) != null;
        }

        @Override
        public Component refusal(ServerLevel level, BlockPos source, BlockPos target) {
            return Component.translatable("item.zas_atmos.multitool.not_linkable_turbine");
        }

        @Override
        public Component link(ServerLevel level, BlockPos source, BlockPos target, @Nullable Player player) {
            if (!(level.getBlockEntity(source) instanceof TurbineControllerBlockEntity controller)) {
                return refusal(level, source, target);
            }
            return switch (controller.toggleLink(level, target)) {
                case LINKED ->
                    Component.translatable(
                            "item.zas_atmos.multitool.linked_turbine",
                            Labels.named(level, target),
                            where(level, source));
                case UNLINKED ->
                    Component.translatable(
                            "item.zas_atmos.multitool.unlinked_turbine",
                            Labels.named(level, target),
                            where(level, source));
                case NOT_LINKABLE -> refusal(level, source, target);
            };
        }

        @Override
        public void rebase(ServerLevel level, BlockPos pos, UnaryOperator<@Nullable BlockPos> rebase) {
            if (level.getBlockEntity(pos) instanceof TurbineControllerBlockEntity controller) {
                controller.rebase(rebase);
            }
        }
    };

    // ---- Targets -----------------------------------------------------------------------------

    /** A part linked where it is clicked: a powered device, an air sensor, a vent or an igniter. */
    private static final Linkable PART = new Linkable() {};

    /** A turbine or a compressor, which keeps its controller's position as that keeps its (CONT-190). */
    private static final Linkable TURBINE_PART = new Linkable() {
        @Override
        public void rebase(ServerLevel level, BlockPos pos, UnaryOperator<@Nullable BlockPos> rebase) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TurbineBlockEntity turbine) {
                turbine.rebase(rebase);
            } else if (be instanceof CompressorBlockEntity compressor) {
                compressor.rebase(rebase);
            }
        }
    };

    /** A firelock on its own, from either half: its link is its lower half (CONT-043). */
    private static final Linkable FIRELOCK = new Linkable() {
        @Override
        public Optional<BlockPos> target(UseOnContext use) {
            return Optional.of(FirelockBlock.lower(stateOf(use), use.getClickedPos()));
        }
    };

    /** A firelock fitted to a door, from either half: its link is the lower half (CONT-137). */
    private static final Linkable FIRELOCK_DOOR = new Linkable() {
        @Override
        public Optional<BlockPos> target(UseOnContext use) {
            return Optional.of(FirelockDoorBlock.lower(stateOf(use), use.getClickedPos()));
        }
    };

    /** An airlock, from any of its blocks: its link is its origin (CONT-137, CONT-138). */
    private static final Linkable AIRLOCK = new Linkable() {
        @Override
        public Optional<BlockPos> target(UseOnContext use) {
            return Optional.of(AirlockBlock.origin(stateOf(use), use.getClickedPos()));
        }
    };

    /** The central atmospherics controller, from any of its four blocks: its lower left one (CONT-118). */
    private static final Linkable CENTRAL_CONTROLLER = new Linkable() {
        @Override
        public Optional<BlockPos> target(UseOnContext use) {
            CentralControllerBlockEntity central =
                    CentralControllerBlock.entity(use.getLevel(), use.getClickedPos(), stateOf(use));
            return central == null ? Optional.empty() : Optional.of(central.getBlockPos());
        }
    };

    // ---- What the multitool does, from its stack --------------------------------------------

    /**
     * What the multitool does to a target, from what its stack has selected, without the checks of a
     * click: the selected source links the target, or unlinks it, and says what it did. For tests and
     * scenes; a player's click goes through SS13 Framework's multitool.
     *
     * @param stack the multitool
     * @param level the level
     * @param target where the target's link is kept, such as an airlock's origin
     * @return what the multitool would say
     */
    public static Component link(ItemStack stack, ServerLevel level, BlockPos target) {
        Optional<BlockPos> at = MultitoolItem.source(stack, level);
        Linkable source = at.filter(level::isLoaded)
                .map(p -> level.getCapability(Linkable.BLOCK, p))
                .orElse(null);
        if (source == null) {
            return at.isEmpty()
                    ? Component.translatable("item.ss13_framework.multitool.no_source")
                    : Component.translatable(
                            "item.ss13_framework.multitool.source_gone",
                            at.get().toShortString());
        }
        return source.link(level, at.get(), target, null);
    }

    /**
     * Moves the area air controller at {@code controller} to a central controller, from whichever had
     * it, if it is in that one's range (CONT-118).
     *
     * @return what to tell the player
     */
    private static Component move(ServerLevel level, BlockPos controller, CentralControllerBlockEntity central) {
        if (!central.take(controller)) {
            return Component.translatable("item.zas_atmos.multitool.out_of_range", where(level, controller));
        }
        return Component.translatable(
                "item.zas_atmos.multitool.moved", where(level, controller), where(level, central.getBlockPos()));
    }

    private static BlockState stateOf(UseOnContext use) {
        return use.getLevel().getBlockState(use.getClickedPos());
    }

    /** An airlock controller's slot by its name in the controller's window. */
    private static Component slotName(AirlockControllerBlockEntity.Slot slot) {
        return Component.translatable("gui.zas_atmos.airlock_controller." + slot.key());
    }

    /**
     * How a message says which controller it means: by its label, or without one by where it is, as
     * these messages always have.
     */
    private static Component where(Level level, BlockPos pos) {
        String label = Labels.of(level, pos);
        return label.isEmpty()
                ? Component.translatable(
                        "item.zas_atmos.multitool.at", pos.getX() + " " + pos.getY() + " " + pos.getZ())
                : Component.literal(label);
    }
}
