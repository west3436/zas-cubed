package io.github.west3436.zas.atmos.airlock;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentSettings;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * An airlock controller's work, once per atmos tick (CONT-138 to CONT-141, CONT-152, CONT-153,
 * architecture §11.17).
 *
 * <p><b>Links.</b> The multitool links at most two airlocks, at most one air sensor and any number of
 * vents ({@link #toggleLink}). A link whose loaded block is no longer of its kind is dropped.
 *
 * <p><b>A cycle</b> runs toward one of its two airlocks. It starts with both airlocks closed and
 * bolted. Each step it reads the chamber at the air sensor and the pressure beyond that airlock, the
 * side of it that isn't the chamber's ({@link AirlockCycle#frontIsChamber}), and runs its vents for
 * {@link AirlockCycle#next}: siphoning down to that pressure, or blowing up to it, into or out of the
 * vents' own pipe network. Once the chamber is within 5.07 kPa of it, it unbolts and opens that
 * airlock, leaving the other bolted, and is idle again. A cycle with an airlock or the sensor unlinked
 * stops, leaving the airlocks as they are.
 *
 * <p><b>Power</b> (CONT-141). It is a part of an area air controller, in its zone or linked, as any
 * powered part is. It draws {@code [power] airlock_controller} and what its vents draw, and while it
 * is powered it powers them. Unpowered, a cycle waits. Its vents take its orders alone, off between
 * cycles (CONT-152).
 */
public class AirlockControllerBlockEntity extends AtmosDevice implements PoweredDevice, MenuProvider {
    /** What linking a block with the multitool did. */
    public enum LinkResult {
        LINKED,
        UNLINKED,
        /** Two airlocks are linked already. */
        AIRLOCKS_FULL,
        /** A sensor is linked already. */
        SENSOR_FULL,
        /** It's not an airlock, a sensor or a vent. */
        NOT_LINKABLE
    }

    private final DevicePower power = new DevicePower(PowerPriority.LIFE_SUPPORT, this::currentDraw, this::now);
    private final BlockPos[] airlocks = new BlockPos[2];
    private BlockPos sensor;
    private final Set<BlockPos> vents = new LinkedHashSet<>();
    /** The airlock it's cycling toward, 0 or 1, or −1 while idle. */
    private int cycling = -1;

    private AirlockCycle.Step step;
    private double chamberKpa = Double.NaN;
    private final double[] beyondKpa = {Double.NaN, Double.NaN};

    public AirlockControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.AIRLOCK_CONTROLLER.get(), pos, state);
    }

    /** What it draws now: its own draw and that of each loaded vent linked to it (CONT-141). */
    private int currentDraw() {
        int draw = AtmosServerConfig.get(AtmosServerConfig.AIRLOCK_CONTROLLER_DRAW);
        if (level != null) {
            for (VentBlockEntity v : vents(level)) {
                draw += v.power().draw();
            }
        }
        return draw;
    }

    @Override
    public DevicePower power() {
        return power;
    }

    /** The point whose zone it is in: the middle of its own block, in front of the wall. */
    @Override
    public Vec3 airPoint() {
        return Vec3.atCenterOf(getBlockPos());
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        prune(level);
        read(level);
        boolean powered = power.isPowered();
        if (cycling >= 0) {
            if (airlocks[0] == null || airlocks[1] == null || sensor == null) {
                stop();
            } else if (powered) {
                advance(level);
            }
        }
        VentSettings order = VentSettings.OFF;
        if (cycling >= 0 && powered && step != null && step != AirlockCycle.Step.DONE) {
            double target = beyondKpa[cycling];
            order = step == AirlockCycle.Step.SIPHON ? VentSettings.siphon(target) : VentSettings.blow(target);
        }
        List<VentBlockEntity> linked = vents(level);
        for (VentBlockEntity v : linked) {
            v.airlockOrder(getBlockPos(), order);
        }
        if (powered) {
            for (VentBlockEntity v : linked) {
                v.power().supply(this);
            }
        }
        if (cycling >= 0 && sensor != null && level.getBlockEntity(sensor) instanceof AirSensorBlockEntity s) {
            s.markCycling(level);
        }
        show(level);
        setChanged();
    }

    /** Drops each link whose loaded block is no longer of its kind. */
    private void prune(ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            if (airlocks[i] != null && level.isLoaded(airlocks[i]) && !isAirlock(level, airlocks[i])) {
                airlocks[i] = null;
            }
        }
        if (sensor != null
                && level.isLoaded(sensor)
                && !(level.getBlockEntity(sensor) instanceof AirSensorBlockEntity)) {
            sensor = null;
        }
        vents.removeIf(p -> level.isLoaded(p) && !(level.getBlockEntity(p) instanceof VentBlockEntity));
    }

    private static boolean isAirlock(ServerLevel level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        return state.getBlock() instanceof AirlockBlock && AirlockBlock.isOrigin(state);
    }

    /** Reads the chamber at the sensor and the pressure beyond each airlock, NaN where it can't. */
    private void read(ServerLevel level) {
        chamberKpa = Double.NaN;
        beyondKpa[0] = Double.NaN;
        beyondKpa[1] = Double.NaN;
        if (sensor == null || !level.isLoaded(sensor)) {
            return;
        }
        Vec3 point = Vec3.atCenterOf(sensor);
        AtmosphereSample chamber = Atmospheres.at(level, point);
        if (chamber.state() == AtmosphereState.UNRESOLVED) {
            return;
        }
        chamberKpa = chamber.pressure();
        for (int i = 0; i < 2; i++) {
            BlockPos a = airlocks[i];
            if (a == null || !level.isLoaded(a) || !isAirlock(level, a)) {
                continue;
            }
            Direction facing = level.getBlockState(a).getValue(AirlockBlock.FACING);
            BlockPos front = a.relative(facing);
            BlockPos back = a.relative(facing.getOpposite());
            AtmosphereSample f = Atmospheres.at(level, front);
            AtmosphereSample b = Atmospheres.at(level, back);
            boolean frontIsChamber = AirlockCycle.frontIsChamber(
                    zone(chamber),
                    zone(f),
                    zone(b),
                    point.distanceToSqr(Vec3.atCenterOf(front)),
                    point.distanceToSqr(Vec3.atCenterOf(back)));
            AtmosphereSample beyond = frontIsChamber ? b : f;
            if (beyond.state() != AtmosphereState.UNRESOLVED) {
                beyondKpa[i] = beyond.pressure();
            }
        }
    }

    private static long zone(AtmosphereSample sample) {
        return sample.state() == AtmosphereState.ZONED || sample.state() == AtmosphereState.PAUSED
                ? sample.zone().map(ZoneId::value).orElse(-1L)
                : -1L;
    }

    /** One step of a cycle that has its airlocks and sensor, and power. */
    private void advance(ServerLevel level) {
        closeAndBolt(level);
        if (Double.isNaN(chamberKpa) || Double.isNaN(beyondKpa[cycling])) {
            step = null; // waiting for a reading
            return;
        }
        step = AirlockCycle.next(chamberKpa, beyondKpa[cycling]);
        if (step == AirlockCycle.Step.DONE) {
            BlockPos toward = airlocks[cycling];
            cycling = -1;
            step = null;
            if (level.isLoaded(toward)) {
                AirlockBlock.setBolted(level, toward, false);
                AirlockBlock.openIfFree(level, toward);
            }
        }
    }

    private void closeAndBolt(ServerLevel level) {
        for (BlockPos a : airlocks) {
            if (a != null && level.isLoaded(a)) {
                AirlockBlock.setBolted(level, a, true);
            }
        }
    }

    private List<VentBlockEntity> vents(net.minecraft.world.level.Level level) {
        List<VentBlockEntity> out = new ArrayList<>();
        for (BlockPos p : vents) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof VentBlockEntity v) {
                out.add(v);
            }
        }
        return out;
    }

    private void show(ServerLevel level) {
        BlockState state = getBlockState();
        boolean on = cycling >= 0;
        if (state.hasProperty(AirlockControllerBlock.CYCLING) && state.getValue(AirlockControllerBlock.CYCLING) != on) {
            level.setBlock(getBlockPos(), state.setValue(AirlockControllerBlock.CYCLING, on), Block.UPDATE_CLIENTS);
        }
    }

    // ---- Asking for a cycle (CONT-140, CONT-153) ------------------------------------------

    /**
     * Starts a cycle toward the linked airlock whose origin is at {@code airlock}: both airlocks close
     * and bolt now, and the vents run from its next step.
     *
     * @return false, changing nothing, if it is already cycling, has no power, or lacks either
     *     airlock or the sensor, or if {@code airlock} isn't one of its two
     */
    public boolean requestCycle(BlockPos airlock) {
        for (int i = 0; i < 2; i++) {
            if (airlock.equals(airlocks[i])) {
                return requestCycle(i);
            }
        }
        return false;
    }

    /** {@link #requestCycle(BlockPos)} by the airlock's index, 0 or 1. */
    public boolean requestCycle(int index) {
        if (!(level instanceof ServerLevel server)
                || index < 0
                || index > 1
                || cycling >= 0
                || !power.isPowered()
                || airlocks[0] == null
                || airlocks[1] == null
                || sensor == null) {
            return false;
        }
        cycling = index;
        step = null;
        closeAndBolt(server);
        show(server);
        setChanged();
        return true;
    }

    /** Stops a cycle, leaving both airlocks bolted; its vents are off from its next step. */
    public boolean stop() {
        if (cycling < 0) {
            return false;
        }
        cycling = -1;
        step = null;
        setChanged();
        return true;
    }

    /**
     * The loaded controller that links the airlock whose origin is at {@code airlock}, if any. A
     * controller links an airlock once, so the first found is the only one.
     */
    public static Optional<AirlockControllerBlockEntity> linking(ServerLevel level, BlockPos airlock) {
        for (AtmosDevice d : DeviceTicker.loaded(level)) {
            if (d instanceof AirlockControllerBlockEntity c && !c.isRemoved() && c.links(airlock)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    private boolean links(BlockPos airlock) {
        return airlock.equals(airlocks[0]) || airlock.equals(airlocks[1]);
    }

    // ---- Linking (CONT-138) ---------------------------------------------------------------

    /**
     * Links or unlinks a block: an airlock, by its origin; an air sensor; or a vent. Unlinking an
     * airlock unbolts it, and unlinking an airlock or the sensor stops a cycle.
     */
    public LinkResult toggleLink(ServerLevel level, BlockPos pos) {
        pos = pos.immutable();
        for (int i = 0; i < 2; i++) {
            if (pos.equals(airlocks[i])) {
                airlocks[i] = null;
                stop();
                AirlockBlock.setBolted(level, pos, false);
                setChanged();
                return LinkResult.UNLINKED;
            }
        }
        if (pos.equals(sensor)) {
            sensor = null;
            stop();
            setChanged();
            return LinkResult.UNLINKED;
        }
        if (vents.remove(pos)) {
            setChanged();
            return LinkResult.UNLINKED;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (isAirlock(level, pos)) {
            for (int i = 0; i < 2; i++) {
                if (airlocks[i] == null) {
                    airlocks[i] = pos;
                    setChanged();
                    return LinkResult.LINKED;
                }
            }
            return LinkResult.AIRLOCKS_FULL;
        }
        if (be instanceof AirSensorBlockEntity) {
            if (sensor != null) {
                return LinkResult.SENSOR_FULL;
            }
            sensor = pos;
            setChanged();
            return LinkResult.LINKED;
        }
        if (be instanceof VentBlockEntity) {
            vents.add(pos);
            setChanged();
            return LinkResult.LINKED;
        }
        return LinkResult.NOT_LINKABLE;
    }

    /** Called by the block when the controller is removed: unbolts its airlocks. */
    void removedFromWorld(ServerLevel level) {
        cycling = -1;
        for (BlockPos a : airlocks) {
            if (a != null && level.isLoaded(a)) {
                AirlockBlock.setBolted(level, a, false);
            }
        }
    }

    // ---- For the window and tests -------------------------------------------------------

    /** The airlock it's cycling toward, 0 or 1, or −1 while idle. */
    public int cycling() {
        return cycling;
    }

    /** What its vents did at its last step: null while idle or waiting for a reading. */
    public AirlockCycle.Step cycleStep() {
        return step;
    }

    /** The linked airlocks' origins, either of which may be null. */
    public BlockPos[] airlocks() {
        return airlocks.clone();
    }

    /** The linked sensor, or null. */
    public BlockPos sensor() {
        return sensor;
    }

    /** The linked vents. */
    public Set<BlockPos> linkedVents() {
        return Set.copyOf(vents);
    }

    /** The chamber's pressure at its last step, kPa; NaN if it had no reading. */
    public double chamberKpa() {
        return chamberKpa;
    }

    /** The pressure beyond airlock 0 or 1 at its last step, kPa; NaN if it had no reading. */
    public double beyondKpa(int index) {
        return beyondKpa[index];
    }

    // ---- Saving and the menu ------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        power.save(tag);
        for (int i = 0; i < 2; i++) {
            if (airlocks[i] != null) {
                tag.putLong("Airlock" + i, airlocks[i].asLong());
            }
        }
        if (sensor != null) {
            tag.putLong("Sensor", sensor.asLong());
        }
        tag.putLongArray("Vents", vents.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("Cycling", cycling);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        power.load(tag);
        for (int i = 0; i < 2; i++) {
            airlocks[i] = tag.contains("Airlock" + i) ? BlockPos.of(tag.getLong("Airlock" + i)) : null;
        }
        sensor = tag.contains("Sensor") ? BlockPos.of(tag.getLong("Sensor")) : null;
        vents.clear();
        for (long p : tag.getLongArray("Vents")) {
            vents.add(BlockPos.of(p));
        }
        cycling = tag.contains("Cycling") ? Math.max(-1, Math.min(1, tag.getInt("Cycling"))) : -1;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.airlock_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AirlockControllerMenu(id, this, getBlockPos());
    }
}
