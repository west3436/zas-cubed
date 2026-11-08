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
import java.util.Arrays;
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
 * architecture §11.18).
 *
 * <p><b>Links.</b> The multitool links at most two airlocks, at most three air sensors and any number
 * of vents ({@link #toggleLink}). A link whose loaded block is no longer of its kind is dropped.
 *
 * <p><b>Sensors.</b> Each step it works out each linked sensor's role from the zones around its
 * airlocks: the chamber's, or beyond airlock 1 or 2 ({@link AirlockCycle#role}). The chamber is the
 * zone both airlocks border ({@link AirlockCycle#frontIsChamber}). The first sensor found in a role
 * has it. It reads the chamber at the chamber's sensor and each side at that side's sensor.
 *
 * <p><b>A cycle</b> runs toward one of its two airlocks, and needs both airlocks, the chamber's
 * sensor and that side's. It starts with both airlocks closed and bolted. Each step it runs its vents
 * for {@link AirlockCycle#next}: siphoning down to that side's pressure, or blowing up to it, into or
 * out of the vents' own pipe network. Once the chamber is within 5.07 kPa of it, it unbolts and opens
 * that airlock, leaving the other bolted, and is idle again. A cycle that loses an airlock or a
 * sensor it needs stops, leaving the airlocks as they are. Pressing a linked sensor cycles away from
 * the side the airlock is accessing ({@link #pressSensor}).
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
        /** Three sensors are linked already. */
        SENSOR_FULL,
        /** It's not an airlock, a sensor or a vent. */
        NOT_LINKABLE
    }

    private final DevicePower power = new DevicePower(PowerPriority.LIFE_SUPPORT, this::currentDraw, this::now);
    /** How many air sensors it links: the chamber's and one beyond each airlock. */
    public static final int MAX_SENSORS = 3;

    private final BlockPos[] airlocks = new BlockPos[2];
    /** The linked sensors, in the order they were linked. */
    private final List<BlockPos> sensors = new ArrayList<>();
    /** The sensor in each role at its last step, by {@link AirlockCycle#SIDE_1}, {@code SIDE_2} and {@code CHAMBER}. */
    private final BlockPos[] roles = new BlockPos[3];

    private final Set<BlockPos> vents = new LinkedHashSet<>();
    /** The airlock it's cycling toward, 0 or 1, or −1 while idle. */
    private int cycling = -1;
    /** The airlock opened last, 0 or 1, or −1 if neither has been since it was linked. */
    private int lastOpened = -1;

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
            if (!hasParts(cycling)) {
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
        if (cycling >= 0) {
            for (BlockPos p : sensors) {
                if (level.isLoaded(p) && level.getBlockEntity(p) instanceof AirSensorBlockEntity s) {
                    s.markCycling(level);
                }
            }
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
        sensors.removeIf(p -> level.isLoaded(p) && !(level.getBlockEntity(p) instanceof AirSensorBlockEntity));
        vents.removeIf(p -> level.isLoaded(p) && !(level.getBlockEntity(p) instanceof VentBlockEntity));
    }

    private static boolean isAirlock(ServerLevel level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        return state.getBlock() instanceof AirlockBlock && AirlockBlock.isOrigin(state);
    }

    /** Works out the sensors' roles, then reads the chamber and each side at its sensor, NaN where it can't. */
    private void read(ServerLevel level) {
        assignRoles(level);
        chamberKpa = pressure(level, roles[AirlockCycle.CHAMBER]);
        beyondKpa[0] = pressure(level, roles[AirlockCycle.SIDE_1]);
        beyondKpa[1] = pressure(level, roles[AirlockCycle.SIDE_2]);
    }

    private static double pressure(ServerLevel level, BlockPos sensor) {
        if (sensor == null || !level.isLoaded(sensor)) {
            return Double.NaN;
        }
        AtmosphereSample sample = Atmospheres.at(level, Vec3.atCenterOf(sensor));
        return sample.state() == AtmosphereState.UNRESOLVED ? Double.NaN : sample.pressure();
    }

    /**
     * Gives each linked sensor its role from the zones on either side of each airlock. With an
     * airlock not linked there are none; with one not loaded, the roles stay as they were.
     */
    private void assignRoles(ServerLevel level) {
        if (airlocks[0] == null || airlocks[1] == null) {
            Arrays.fill(roles, null);
            return;
        }
        for (BlockPos a : airlocks) {
            if (!level.isLoaded(a) || !isAirlock(level, a)) {
                for (int r = 0; r < roles.length; r++) {
                    if (roles[r] != null && !sensors.contains(roles[r])) {
                        roles[r] = null;
                    }
                }
                return;
            }
        }
        BlockPos[] front = new BlockPos[2];
        BlockPos[] back = new BlockPos[2];
        long[] frontZone = new long[2];
        long[] backZone = new long[2];
        for (int i = 0; i < 2; i++) {
            Direction facing = level.getBlockState(airlocks[i]).getValue(AirlockBlock.FACING);
            front[i] = airlocks[i].relative(facing);
            back[i] = airlocks[i].relative(facing.getOpposite());
            frontZone[i] = zone(Atmospheres.at(level, front[i]));
            backZone[i] = zone(Atmospheres.at(level, back[i]));
        }
        Vec3[] chamberCell = new Vec3[2];
        Vec3[] farCell = new Vec3[2];
        long[] chamberZones = new long[2];
        long[] farZones = new long[2];
        for (int i = 0; i < 2; i++) {
            int j = 1 - i;
            Vec3 other = Vec3.atCenterOf(airlocks[j]);
            boolean f = AirlockCycle.frontIsChamber(
                    frontZone[i],
                    backZone[i],
                    frontZone[j],
                    backZone[j],
                    other.distanceToSqr(Vec3.atCenterOf(front[i])),
                    other.distanceToSqr(Vec3.atCenterOf(back[i])));
            chamberCell[i] = Vec3.atCenterOf(f ? front[i] : back[i]);
            farCell[i] = Vec3.atCenterOf(f ? back[i] : front[i]);
            chamberZones[i] = f ? frontZone[i] : backZone[i];
            farZones[i] = f ? backZone[i] : frontZone[i];
        }
        Arrays.fill(roles, null);
        for (BlockPos s : sensors) {
            if (!level.isLoaded(s)) {
                continue;
            }
            Vec3 p = Vec3.atCenterOf(s);
            double[] past = {
                p.distanceToSqr(chamberCell[0]) - p.distanceToSqr(farCell[0]),
                p.distanceToSqr(chamberCell[1]) - p.distanceToSqr(farCell[1])
            };
            int role = AirlockCycle.role(zone(Atmospheres.at(level, p)), chamberZones, farZones, past);
            if (roles[role] == null) {
                roles[role] = s;
            }
        }
    }

    private static long zone(AtmosphereSample sample) {
        return sample.state() == AtmosphereState.ZONED || sample.state() == AtmosphereState.PAUSED
                ? sample.zone().map(ZoneId::value).orElse(-1L)
                : -1L;
    }

    /** Whether it has both airlocks, the chamber's sensor and the sensor beyond airlock {@code toward}. */
    private boolean hasParts(int toward) {
        return airlocks[0] != null
                && airlocks[1] != null
                && roles[AirlockCycle.CHAMBER] != null
                && roles[toward] != null;
    }

    /** One step of a cycle that has its airlocks and sensors, and power. */
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
            lastOpened = airlocks[0].equals(toward) ? 0 : 1;
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
     *     airlock, the chamber's sensor or the sensor beyond {@code airlock}, or if {@code airlock}
     *     isn't one of its two
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
        if (!(level instanceof ServerLevel server) || index < 0 || index > 1 || cycling >= 0 || !power.isPowered()) {
            return false;
        }
        read(server); // the sensors' roles as of now
        if (!hasParts(index)) {
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
     * A press of the linked sensor at {@code sensor} (CONT-140): a cycle toward the airlock opposite
     * the side it's accessing, which is the side whose airlock alone is unbolted, or else the airlock
     * opened last. With neither, it cycles away from the pressed sensor's side, or toward airlock 1
     * from the chamber's sensor ({@link AirlockCycle#pressTarget}).
     *
     * @return false, changing nothing, if the sensor isn't linked or the cycle is refused
     */
    public boolean pressSensor(BlockPos sensor) {
        if (!(level instanceof ServerLevel server)
                || !sensors.contains(sensor)
                || airlocks[0] == null
                || airlocks[1] == null) {
            return false;
        }
        read(server);
        int role = AirlockCycle.NONE;
        for (int r = 0; r < roles.length; r++) {
            if (sensor.equals(roles[r])) {
                role = r;
            }
        }
        boolean[] bolted = {bolted(server, airlocks[0]), bolted(server, airlocks[1])};
        return requestCycle(AirlockCycle.pressTarget(bolted, lastOpened, role));
    }

    private static boolean bolted(ServerLevel level, BlockPos airlock) {
        return level.isLoaded(airlock)
                && isAirlock(level, airlock)
                && level.getBlockState(airlock).getValue(AirlockBlock.BOLTED);
    }

    /** Called when the linked airlock whose origin is at {@code airlock} opens, for {@link #pressSensor}. */
    public void opened(BlockPos airlock) {
        for (int i = 0; i < 2; i++) {
            if (airlock.equals(airlocks[i]) && lastOpened != i) {
                lastOpened = i;
                setChanged();
            }
        }
    }

    /** The loaded controller that links the air sensor at {@code sensor}, if any. */
    public static Optional<AirlockControllerBlockEntity> linkingSensor(ServerLevel level, BlockPos sensor) {
        for (AtmosDevice d : DeviceTicker.loaded(level)) {
            if (d instanceof AirlockControllerBlockEntity c && !c.isRemoved() && c.sensors.contains(sensor)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
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
     * airlock unbolts it and stops a cycle, and unlinking a sensor a cycle needs stops it.
     */
    public LinkResult toggleLink(ServerLevel level, BlockPos pos) {
        pos = pos.immutable();
        for (int i = 0; i < 2; i++) {
            if (pos.equals(airlocks[i])) {
                airlocks[i] = null;
                if (lastOpened == i) {
                    lastOpened = -1;
                }
                stop();
                AirlockBlock.setBolted(level, pos, false);
                setChanged();
                return LinkResult.UNLINKED;
            }
        }
        if (sensors.remove(pos)) {
            if (cycling >= 0 && (pos.equals(roles[AirlockCycle.CHAMBER]) || pos.equals(roles[cycling]))) {
                stop();
            }
            for (int r = 0; r < roles.length; r++) {
                if (pos.equals(roles[r])) {
                    roles[r] = null;
                }
            }
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
            if (sensors.size() >= MAX_SENSORS) {
                return LinkResult.SENSOR_FULL;
            }
            sensors.add(pos);
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

    /** The linked sensors, in the order they were linked. */
    public List<BlockPos> sensors() {
        return List.copyOf(sensors);
    }

    /**
     * The sensor in a role at its last step, or null: {@link AirlockCycle#CHAMBER}, or {@link
     * AirlockCycle#SIDE_1} or {@code SIDE_2}, beyond airlock 1 or 2.
     */
    public BlockPos sensor(int role) {
        return roles[role];
    }

    /** The airlock opened last, 0 or 1, or −1 if neither has been since it was linked. */
    public int lastOpened() {
        return lastOpened;
    }

    /** The linked vents. */
    public Set<BlockPos> linkedVents() {
        return Set.copyOf(vents);
    }

    /** The chamber's pressure at its last step, kPa; NaN if it had no reading. */
    public double chamberKpa() {
        return chamberKpa;
    }

    /** The pressure beyond airlock 0 or 1 at its sensor at its last step, kPa; NaN if it had no reading. */
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
        tag.putLongArray("Sensors", sensors.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Vents", vents.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("Cycling", cycling);
        tag.putInt("LastOpened", lastOpened);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        power.load(tag);
        for (int i = 0; i < 2; i++) {
            airlocks[i] = tag.contains("Airlock" + i) ? BlockPos.of(tag.getLong("Airlock" + i)) : null;
        }
        sensors.clear();
        for (long p : tag.getLongArray("Sensors")) {
            if (sensors.size() < MAX_SENSORS) {
                sensors.add(BlockPos.of(p));
            }
        }
        if (tag.contains("Sensor") && sensors.isEmpty()) {
            sensors.add(BlockPos.of(tag.getLong("Sensor"))); // saved when it linked one sensor
        }
        Arrays.fill(roles, null);
        vents.clear();
        for (long p : tag.getLongArray("Vents")) {
            vents.add(BlockPos.of(p));
        }
        cycling = tag.contains("Cycling") ? Math.max(-1, Math.min(1, tag.getInt("Cycling"))) : -1;
        lastOpened = tag.contains("LastOpened") ? Math.max(-1, Math.min(1, tag.getInt("LastOpened"))) : -1;
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
