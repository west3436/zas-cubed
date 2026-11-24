package io.github.west3436.zas.atmos.tool;

import io.github.west3436.ss13.api.tool.Pryable;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.firelock.FirelockLogic;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

/**
 * How SS13 Framework's crowbar forces this mod's firelocks open or shut, even when they are locked
 * down (CONT-094), as their {@link Pryable} capability: a standalone one, or one fitted to an airlock
 * or a door (CONT-137). While a controller covering it is at danger 2, a firelock forced open closes
 * again after 5 s, as one opened by hand does. It doesn't move an airlock's bolts (CONT-136): an
 * airlock with no firelock fitted leaves the crowbar nothing to pry.
 *
 * <p>The blocks let a crowbar, any item in {@link io.github.west3436.ss13.api.tool.ToolTags#CROWBARS},
 * through their own use without opening or closing by hand, so the crowbar acts after it.
 */
public final class Crowbar {
    private Crowbar() {}

    /** Registers the capability on the firelock, the firelock fitted to a door and the airlocks. */
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Pryable.BLOCK,
                (level, pos, state, be, context) -> FIRELOCK,
                AtmosBlocks.BLOCKS.getEntries().stream()
                        .map(DeferredHolder::get)
                        .filter(block -> block instanceof FirelockBlock
                                || block instanceof FirelockDoorBlock
                                || block instanceof AirlockBlock)
                        .toArray(Block[]::new));
    }

    /** A firelock, on its own or fitted to a door or an airlock, from any of its blocks. */
    private static final Pryable FIRELOCK = new Pryable() {
        /** Only a fitted firelock: the crowbar doesn't move an airlock's bolts (CONT-136). */
        @Override
        public boolean canPry(UseOnContext use) {
            BlockState state = use.getLevel().getBlockState(use.getClickedPos());
            return !(state.getBlock() instanceof AirlockBlock)
                    || state.getValue(AirlockBlock.FIRELOCK).fitted();
        }

        @Override
        public void pry(ServerLevel level, BlockPos pos, UseOnContext use) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FirelockBlock) {
                force(level, FirelockBlock.lower(state, pos), use.getPlayer());
                return;
            }
            BlockPos holder = state.getBlock() instanceof AirlockBlock
                    ? AirlockBlock.origin(state, pos)
                    : FirelockDoorBlock.lower(state, pos);
            if (level.getBlockEntity(holder) instanceof FirelockLogic.Holder h && h.hasFirelock()) {
                FirelockLogic.Door door = h.firelockDoor();
                boolean open = !door.isOpen();
                door.setOpen(level, open);
                said(level, door.linkPos(), use.getPlayer(), open);
            }
        }
    };

    /**
     * Forces the firelock whose lower half is at {@code lower}: opens it if it is closed, locked
     * down or not, and closes it if it is open.
     *
     * @return true if it is now open
     */
    public static boolean force(ServerLevel level, BlockPos lower, @Nullable Player player) {
        boolean open = !level.getBlockState(lower).getValue(FirelockBlock.OPEN);
        FirelockBlock.setOpen(level, lower, open, player);
        said(level, lower, player, open);
        return open;
    }

    /** The bang of a forced firelock, and what the player is told. */
    private static void said(ServerLevel level, BlockPos at, @Nullable Player player, boolean open) {
        level.playSound(null, at, AtmosSounds.FIRELOCK_FORCE.get(), SoundSource.BLOCKS);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable(
                            open ? "item.zas_atmos.crowbar.forced_open" : "item.zas_atmos.crowbar.forced_shut"),
                    true);
        }
    }
}
