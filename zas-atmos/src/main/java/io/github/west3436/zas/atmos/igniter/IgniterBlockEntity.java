package io.github.west3436.zas.atmos.igniter;

import io.github.west3436.ss13.api.label.Labelled;
import io.github.west3436.ss13.api.power.DevicePower;
import io.github.west3436.ss13.api.power.PowerChannel;
import io.github.west3436.ss13.api.power.PowerPriority;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.power.PoweredPart;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The igniter's block entity (CONT-105): an area air controller's part, powered like a vent through
 * its zone or a link, in the last priority group (CONT-064). While it gets a redstone signal it draws
 * {@code [power] igniter} FE/t (20); at each step it sparks ({@link IgniterBlock#LIT}) if it has a
 * signal and power paid for, and stops if either is gone. It never touches gas itself: the
 * framework ignites a flammable mixture beside a lit igniter (API-053).
 *
 * <p>A gas turbine controller it is linked to can also spark it on command ({@link #spark}, CONT-191):
 * lit for {@value #COMMAND_STEPS} atmos ticks whatever its signal and power, as a single spark that
 * draws nothing.
 */
public class IgniterBlockEntity extends AtmosDevice implements PoweredPart, Labelled {
    /** Atmos ticks between its spark sounds while it stays lit, 3 s (CONT-127). */
    public static final int SPARK_STEPS = 3;

    private final DevicePower power =
            new DevicePower(PowerChannel.EQUIPMENT, PowerPriority.NORMAL, this::currentDraw, this::now);
    private int sparkIn;

    /** Atmos ticks a commanded spark keeps it lit. */
    public static final int COMMAND_STEPS = 2;

    /** Atmos ticks left of a commanded spark. */
    private int commanded;

    public IgniterBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.IGNITER.get(), pos, state);
    }

    /** Whether it gets a redstone signal. */
    public boolean signal() {
        BlockState state = getBlockState();
        return state.hasProperty(IgniterBlock.POWERED) && state.getValue(IgniterBlock.POWERED);
    }

    /** Whether it is sparking. */
    public boolean lit() {
        BlockState state = getBlockState();
        return state.hasProperty(IgniterBlock.LIT) && state.getValue(IgniterBlock.LIT);
    }

    /**
     * Sparks it once, on command from a gas turbine controller (CONT-191): it is lit at once and for
     * {@value #COMMAND_STEPS} atmos ticks, then goes back to following its signal. Server side.
     */
    public void spark() {
        commanded = COMMAND_STEPS;
        if (level instanceof ServerLevel server) {
            BlockState state = getBlockState();
            if (state.hasProperty(IgniterBlock.LIT) && !state.getValue(IgniterBlock.LIT)) {
                server.setBlock(worldPosition, state.setValue(IgniterBlock.LIT, true), Block.UPDATE_CLIENTS);
            }
            sparkIn = SPARK_STEPS;
            server.playSound(null, worldPosition, AtmosSounds.IGNITER_SPARK.get(), SoundSource.BLOCKS);
        }
    }

    private int currentDraw() {
        return signal() ? AtmosServerConfig.get(AtmosServerConfig.IGNITER_DRAW) : 0;
    }

    @Override
    public DevicePower power() {
        return power;
    }

    /** Its own cell: the plate lies in its room. */
    @Override
    public Vec3 airPoint() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        boolean ordered = commanded > 0;
        if (ordered) {
            commanded--;
        }
        boolean spark = (signal() && paid()) || ordered;
        BlockState state = getBlockState();
        boolean lighting = false;
        if (state.hasProperty(IgniterBlock.LIT) && state.getValue(IgniterBlock.LIT) != spark) {
            level.setBlock(worldPosition, state.setValue(IgniterBlock.LIT, spark), Block.UPDATE_CLIENTS);
            lighting = spark;
        }
        if (spark && (lighting || --sparkIn <= 0)) {
            sparkIn = SPARK_STEPS;
            level.playSound(null, worldPosition, AtmosSounds.IGNITER_SPARK.get(), SoundSource.BLOCKS);
        }
    }

    /** Whether it has power to spark with: paid for, or any power at all when it is set to draw nothing. */
    private boolean paid() {
        return power.paidFor() || (AtmosServerConfig.get(AtmosServerConfig.IGNITER_DRAW) == 0 && power.isPowered());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        power.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        power.load(tag);
    }
}
