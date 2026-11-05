package io.github.west3436.zas.atmos.mixer;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A mixer's block entity (CONT-077): gas comes in at the back and the side, each at its set fraction
 * in whole percent, and leaves by the front, up to its target, at most 100 L/s from each input at
 * that input's conditions. New, it is off and mixes half and half.
 */
public class MixerBlockEntity extends SidePortEntity {
    /** A new mixer's back input's share, in percent. */
    public static final int DEFAULT_BACK_PERCENT = 50;

    private static final String BACK_PERCENT = "BackPercent";
    private static final int[] STEPS = {-10, -1, 1, 10};

    private int backPercent = DEFAULT_BACK_PERCENT;

    public MixerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.MIXER.get(), pos, state);
    }

    /** The back input's share, in percent; the side input's is the rest. */
    public int backPercent() {
        return backPercent;
    }

    /** Sets the back input's share, clamped to 0–100 %. Server side. */
    public void setBackPercent(int percent) {
        int next = Mth.clamp(percent, 0, 100);
        if (next != backPercent) {
            backPercent = next;
            setChanged();
        }
    }

    /** Applies one of the GUI's steps, from the largest down to the largest up. */
    void stepBackPercent(int index) {
        setBackPercent(backPercent + STEPS[index]);
    }

    @Override
    protected InlineStatus work(ServerLevel level) {
        PipeNetwork[] networks = networks();
        if (oneNetwork(networks)) {
            return InlineStatus.SAME_NETWORK;
        }
        GasStore back = networks[BACK].gas();
        GasStore side = networks[SIDE].gas();
        GasStore out = networks[FRONT].gas();
        MixerMath.Flow flow = MixerMath.moles(back, side, out, backPercent / 100.0, target(), AtmosTicks.SECONDS);
        if (flow.total() < PipeBlockEntity.MIN_FLOW) {
            boolean dry = (backPercent > 0 && runsDry(back)) || (backPercent < 100 && runsDry(side));
            return dry ? InlineStatus.INPUT_EMPTY : InlineStatus.TARGET_REACHED;
        }
        MixerMath.move(flow, back, side, out);
        return InlineStatus.MOVING;
    }

    @Override
    protected int drawWhenOn() {
        return AtmosServerConfig.get(AtmosServerConfig.MIXER_DRAW);
    }

    @Override
    protected int dataCount() {
        return MixerMenu.DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return index == MixerMenu.DATA_BACK_PERCENT ? backPercent : super.dataValue(index);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(BACK_PERCENT, backPercent);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(BACK_PERCENT)) {
            backPercent = Mth.clamp(tag.getInt(BACK_PERCENT), 0, 100);
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MixerMenu(id, this);
    }
}
