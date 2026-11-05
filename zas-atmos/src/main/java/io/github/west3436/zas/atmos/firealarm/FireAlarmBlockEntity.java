package io.github.west3436.zas.atmos.firealarm;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The fire alarm's work, once per atmos tick (CONT-044, architecture §11.6). While powered, it
 * watches the air at its own cell, in front of the wall: it is triggered while its zone has a
 * hotspot ({@link Fires#isBurning}, FIRE-019) or the air there is above {@link #TRIGGER_KELVIN}.
 * An area air controller that covers it, as it covers any part, reads a triggered alarm as a
 * danger-2 reading, so its firelocks, redstone and panel follow as for its own danger 2. It is a
 * life-support part (CONT-064) drawing {@code [power] fire_alarm} FE/t (CONT-065).
 *
 * <p>A client plays its alarm from its block state ({@link FireAlarmVoice}, CONT-128). All the server
 * plays is the click when it resets.
 */
public class FireAlarmBlockEntity extends AtmosDevice implements PoweredDevice {
    /** vg's fire alarm trips above 473 K (CONT-044). */
    public static final double TRIGGER_KELVIN = 473.0;

    private final DevicePower power = new DevicePower(
            PowerPriority.LIFE_SUPPORT, () -> AtmosServerConfig.get(AtmosServerConfig.FIRE_ALARM_DRAW), this::now);
    private boolean triggered;

    public FireAlarmBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.FIRE_ALARM.get(), pos, state);
    }

    @Override
    public DevicePower power() {
        return power;
    }

    /** The point it watches: the middle of its own block, in front of the wall. */
    @Override
    public Vec3 airPoint() {
        return Vec3.atCenterOf(getBlockPos());
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        if (!power.isPowered()) {
            triggered = false;
            setStatus(level, FireAlarmStatus.UNPOWERED);
            return;
        }
        AtmosphereSample sample = Atmospheres.at(level, airPoint());
        if (sample.state() != AtmosphereState.UNRESOLVED) {
            boolean fire = (sample.state() == AtmosphereState.ZONED || sample.state() == AtmosphereState.PAUSED)
                    && sample.zone().map(zone -> Fires.isBurning(level, zone)).orElse(false);
            triggered = fire || sample.temperature() > TRIGGER_KELVIN;
        }
        setStatus(level, triggered ? FireAlarmStatus.ALARM : FireAlarmStatus.NORMAL);
    }

    /** Whether it is powered and saw a fire or heat at its last step. */
    public boolean triggered() {
        return triggered && power.isPowered();
    }

    private void setStatus(ServerLevel level, FireAlarmStatus status) {
        BlockState state = getBlockState();
        if (state.hasProperty(FireAlarmBlock.STATUS) && state.getValue(FireAlarmBlock.STATUS) != status) {
            if (state.getValue(FireAlarmBlock.STATUS) == FireAlarmStatus.ALARM && status == FireAlarmStatus.NORMAL) {
                level.playSound(null, getBlockPos(), AtmosSounds.FIRE_ALARM_RESET.get(), SoundSource.BLOCKS);
            }
            level.setBlock(getBlockPos(), state.setValue(FireAlarmBlock.STATUS, status), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        power.save(tag);
        tag.putBoolean("Triggered", triggered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        power.load(tag);
        triggered = tag.getBoolean("Triggered");
    }
}
