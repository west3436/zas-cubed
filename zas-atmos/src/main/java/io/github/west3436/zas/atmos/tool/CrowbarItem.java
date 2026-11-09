package io.github.west3436.zas.atmos.tool;

import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.firelock.FirelockLogic;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The crowbar forces a firelock open or shut, even when it is locked down (CONT-094): a standalone
 * one, or one fitted to an airlock or a door (CONT-137). While a controller covering it is at danger
 * 2, a firelock forced open closes again after 5 s, as one opened by hand does. It doesn't move an
 * airlock's bolts (CONT-136).
 */
public class CrowbarItem extends Item {
    public CrowbarItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        BlockPos holder = state.getBlock() instanceof AirlockBlock
                ? AirlockBlock.origin(state, context.getClickedPos())
                : state.getBlock() instanceof FirelockDoorBlock
                        ? FirelockDoorBlock.lower(state, context.getClickedPos())
                        : null;
        if (holder != null) {
            // Only a fitted firelock: the crowbar doesn't move an airlock's bolts or a door (CONT-136, CONT-137).
            if (state.getBlock() instanceof AirlockBlock
                    && !state.getValue(AirlockBlock.FIRELOCK).fitted()) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel server
                    && server.getBlockEntity(holder) instanceof FirelockLogic.Holder h
                    && h.hasFirelock()) {
                FirelockLogic.Door door = h.firelockDoor();
                boolean open = !door.isOpen();
                door.setOpen(server, open);
                said(server, door.linkPos(), context.getPlayer(), open);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(state.getBlock() instanceof FirelockBlock)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            force(server, FirelockBlock.lower(state, context.getClickedPos()), context.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Forces the firelock whose lower half is at {@code lower}: opens it if it is closed, locked
     * down or not, and closes it if it is open.
     *
     * @return true if it is now open
     */
    public static boolean force(ServerLevel level, BlockPos lower, Player player) {
        boolean open = !level.getBlockState(lower).getValue(FirelockBlock.OPEN);
        FirelockBlock.setOpen(level, lower, open, player);
        said(level, lower, player, open);
        return open;
    }

    /** The bang of a forced firelock, and what the player is told. */
    private static void said(ServerLevel level, BlockPos at, Player player, boolean open) {
        level.playSound(null, at, AtmosSounds.FIRELOCK_FORCE.get(), SoundSource.BLOCKS);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable(
                            open ? "item.zas_atmos.crowbar.forced_open" : "item.zas_atmos.crowbar.forced_shut"),
                    true);
        }
    }
}
