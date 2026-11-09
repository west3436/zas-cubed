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
import java.util.Locale;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * An airlock controller's work, once per atmos tick (CONT-138 to CONT-141, CONT-152, CONT-153,
 * architecture §11.18).
 *
 * <p><b>Slots.</b> It has five, which its window shows: the inner and outer doors, which take
 * airlocks, and the inner, outer and chamber sensors, which take air sensors ({@link Slot}). The
 * multitool fills them ({@link #toggleLink}). With no slot armed, a link goes to the slot guessed
 * from the zones around it ({@link AirlockCycle#doorGuess}, {@link AirlockCycle#sensorGuess}), or
 * to the first empty slot of its kind where there's no guess or that slot is taken. A slot armed in
 * the window takes the next airlock or sensor linked instead, and refuses the other kind; a device
 * already in a slot moves to it, swapping with what was there, which is how the player corrects a
 * guess. The guess is made once, at the link: later changes to the zones never move a device. Vents
 * link besides, any number. A link whose loaded block is no longer of its kind is dropped.
 *
 * <p><b>A cycle</b> runs toward the inner or outer door, and needs both doors, the chamber sensor and
 * that side's sensor. It starts with both airlocks closed and bolted. Each step it runs its vents
 * for {@link AirlockCycle#next}: siphoning down to that side's pressure, or blowing up to it, into or
 * out of the vents' own pipe network. Once the chamber is within 5.07 kPa of it, it unbolts and opens
 * that airlock, leaving the other bolted, and is idle again. A cycle that loses a door or a sensor it
 * needs stops, leaving the airlocks as they are. Pressing a linked sensor cycles away from the side
 * the airlock is accessing ({@link #pressSensor}).
 *
 * <p><b>Power</b> (CONT-141). It is a part of an area air controller, in its zone or linked, as any
 * powered part is. It draws {@code [power] airlock_controller} and what its vents draw, and while it
 * is powered it powers them. Unpowered, a cycle waits. Its vents take its orders alone, off between
 * cycles (CONT-152).
 */
public class AirlockControllerBlockEntity extends AtmosDevice implements PoweredDevice, MenuProvider {
    /** Its slots, in the window's order. */
    public enum Slot {
        INNER_DOOR(true, "Airlock0"),
        OUTER_DOOR(true, "Airlock1"),
        INNER_SENSOR(false, "InnerSensor"),
        OUTER_SENSOR(false, "OuterSensor"),
        CHAMBER_SENSOR(false, "ChamberSensor");

        private final boolean door;
        private final String tag;

        Slot(boolean door, String tag) {
            this.door = door;
            this.tag = tag;
        }

        /** Whether it takes an airlock; otherwise it takes an air sensor. */
        public boolean door() {
            return door;
        }

        /** Its name in the window's text keys. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The door slot for {@link AirlockCycle#INNER} or {@code OUTER}. */
        public static Slot door(int side) {
            return values()[INNER_DOOR.ordinal() + side];
        }

        /** The sensor slot for {@link AirlockCycle#INNER}, {@code OUTER} or {@code CHAMBER}. */
        public static Slot sensor(int role) {
            return values()[INNER_SENSOR.ordinal() + role];
        }

        /** The slot by its place in the window's order, or null. */
        public static @Nullable Slot of(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
        }
    }

    /** What linking a block with the multitool did. */
    public enum LinkResult {
        LINKED,
        UNLINKED,
        /** No slot was armed and both doors are filled. */
        AIRLOCKS_FULL,
        /** No slot was armed and all three sensors are filled. */
        SENSOR_FULL,
        /** The armed slot takes the other kind: an airlock for a door, an air sensor for a sensor. */
        WRONG_KIND,
        /** It's not an airlock, a sensor or a vent. */
        NOT_LINKABLE
    }

    private final DevicePower power = new DevicePower(PowerPriority.LIFE_SUPPORT, this::currentDraw, this::now);
    /** What fills each slot, by {@link Slot#ordinal}. A door's is the airlock's origin. */
    private final BlockPos[] slots = new BlockPos[Slot.values().length];
    /**
     * By {@link Slot#ordinal}, each slot filled by a guess or by the player, as against one filled as
     * the first empty slot, which a later guess may take ({@link #toggleLink}).
     */
    private int sure;
    /** The slot the next link fills, or null. */
    private Slot armed;

    private final Set<BlockPos> vents = new LinkedHashSet<>();
    /** The door it's cycling toward, {@link AirlockCycle#INNER} or {@code OUTER}, or −1 while idle. */
    private int cycling = -1;
    /** The door opened last, {@link AirlockCycle#INNER} or {@code OUTER}, or −1 if neither has been. */
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
            for (Slot s : Slot.values()) {
                BlockPos p = slots[s.ordinal()];
                if (!s.door()
                        && p != null
                        && level.isLoaded(p)
                        && level.getBlockEntity(p) instanceof AirSensorBlockEntity sensor) {
                    sensor.markCycling(level);
                }
            }
        }
        show(level);
        setChanged();
    }

    /** Empties each slot whose loaded block is no longer of its kind, and drops such vents. */
    private void prune(ServerLevel level) {
        for (Slot s : Slot.values()) {
            BlockPos p = slots[s.ordinal()];
            if (p != null && level.isLoaded(p) && !fits(level, s, p)) {
                empty(s);
            }
        }
        vents.removeIf(p -> level.isLoaded(p) && !(level.getBlockEntity(p) instanceof VentBlockEntity));
    }

    private static boolean fits(ServerLevel level, Slot slot, BlockPos pos) {
        return slot.door() ? isAirlock(level, pos) : level.getBlockEntity(pos) instanceof AirSensorBlockEntity;
    }

    private static boolean isAirlock(ServerLevel level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        return state.getBlock() instanceof AirlockBlock && AirlockBlock.isOrigin(state);
    }

    /** Reads the chamber and each side at its sensor, NaN where it can't. */
    private void read(ServerLevel level) {
        chamberKpa = pressure(level, sensor(AirlockCycle.CHAMBER));
        beyondKpa[0] = pressure(level, sensor(AirlockCycle.INNER));
        beyondKpa[1] = pressure(level, sensor(AirlockCycle.OUTER));
    }

    private static double pressure(ServerLevel level, BlockPos sensor) {
        if (sensor == null || !level.isLoaded(sensor)) {
            return Double.NaN;
        }
        AtmosphereSample sample = Atmospheres.at(level, Vec3.atCenterOf(sensor));
        return sample.state() == AtmosphereState.UNRESOLVED ? Double.NaN : sample.pressure();
    }

    /** Whether it has both doors, the chamber sensor and the sensor on side {@code toward}. */
    private boolean hasParts(int toward) {
        return slots[Slot.INNER_DOOR.ordinal()] != null
                && slots[Slot.OUTER_DOOR.ordinal()] != null
                && sensor(AirlockCycle.CHAMBER) != null
                && sensor(toward) != null;
    }

    /** One step of a cycle that has its doors and sensors, and power. */
    private void advance(ServerLevel level) {
        closeAndBolt(level);
        if (Double.isNaN(chamberKpa) || Double.isNaN(beyondKpa[cycling])) {
            step = null; // waiting for a reading
            return;
        }
        step = AirlockCycle.next(chamberKpa, beyondKpa[cycling]);
        if (step == AirlockCycle.Step.DONE) {
            int done = cycling;
            BlockPos toward = slots[Slot.door(done).ordinal()];
            cycling = -1;
            step = null;
            lastOpened = done;
            if (level.isLoaded(toward)) {
                AirlockBlock.setBolted(level, toward, false);
                AirlockBlock.openIfFree(level, toward);
            }
        }
    }

    private void closeAndBolt(ServerLevel level) {
        for (BlockPos a : airlocks()) {
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
     * Starts a cycle toward the door whose airlock's origin is at {@code airlock}: both airlocks close
     * and bolt now, and the vents run from its next step.
     *
     * @return false, changing nothing, if it is already cycling, has no power, or lacks either door,
     *     the chamber sensor or the sensor on that door's side, or if {@code airlock} isn't one of its
     *     doors
     */
    public boolean requestCycle(BlockPos airlock) {
        Slot slot = slotOf(airlock);
        return slot != null && slot.door() && requestCycle(slot.ordinal() - Slot.INNER_DOOR.ordinal());
    }

    /** {@link #requestCycle(BlockPos)} by the door: {@link AirlockCycle#INNER} or {@code OUTER}. */
    public boolean requestCycle(int side) {
        if (!(level instanceof ServerLevel server)
                || side < AirlockCycle.INNER
                || side > AirlockCycle.OUTER
                || cycling >= 0
                || !power.isPowered()
                || !hasParts(side)) {
            return false;
        }
        read(server);
        cycling = side;
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
     * A press of the linked sensor at {@code sensor} (CONT-140): a cycle toward the door opposite the
     * side it's accessing, which is the side whose airlock alone is unbolted, or else the door opened
     * last. With neither, it cycles away from the pressed sensor's side, or toward the inner door from
     * the chamber sensor ({@link AirlockCycle#pressTarget}).
     *
     * @return false, changing nothing, if the sensor isn't in a slot or the cycle is refused
     */
    public boolean pressSensor(BlockPos sensor) {
        Slot slot = slotOf(sensor);
        if (!(level instanceof ServerLevel server) || slot == null || slot.door()) {
            return false;
        }
        BlockPos[] doors = airlocks();
        boolean[] bolted = {bolted(server, doors[0]), bolted(server, doors[1])};
        return requestCycle(AirlockCycle.pressTarget(bolted, lastOpened, slot.ordinal() - Slot.INNER_SENSOR.ordinal()));
    }

    private static boolean bolted(ServerLevel level, BlockPos airlock) {
        return airlock != null
                && level.isLoaded(airlock)
                && isAirlock(level, airlock)
                && level.getBlockState(airlock).getValue(AirlockBlock.BOLTED);
    }

    /** Called when the linked airlock whose origin is at {@code airlock} opens, for {@link #pressSensor}. */
    public void opened(BlockPos airlock) {
        Slot slot = slotOf(airlock);
        if (slot != null && slot.door() && lastOpened != slot.ordinal()) {
            lastOpened = slot.ordinal();
            setChanged();
        }
    }

    /** The loaded controller that has the air sensor at {@code sensor} in a slot, if any. */
    public static Optional<AirlockControllerBlockEntity> linkingSensor(ServerLevel level, BlockPos sensor) {
        for (AtmosDevice d : DeviceTicker.loaded(level)) {
            if (d instanceof AirlockControllerBlockEntity c && !c.isRemoved()) {
                Slot s = c.slotOf(sensor);
                if (s != null && !s.door()) {
                    return Optional.of(c);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The loaded controller that has the airlock whose origin is at {@code airlock} as a door, if any.
     * A controller has an airlock once, so the first found is the only one.
     */
    public static Optional<AirlockControllerBlockEntity> linking(ServerLevel level, BlockPos airlock) {
        for (AtmosDevice d : DeviceTicker.loaded(level)) {
            if (d instanceof AirlockControllerBlockEntity c && !c.isRemoved()) {
                Slot s = c.slotOf(airlock);
                if (s != null && s.door()) {
                    return Optional.of(c);
                }
            }
        }
        return Optional.empty();
    }

    // ---- Slots and links (CONT-138, CONT-153) -----------------------------------------------

    /**
     * Arms a slot, so the next airlock or sensor linked fills it; arming the armed slot disarms it.
     *
     * @return true, as the window's button always does something
     */
    public boolean arm(Slot slot) {
        armed = armed == slot ? null : slot;
        setChanged();
        return true;
    }

    /**
     * Empties a slot. Emptying a door unbolts its airlock and stops a cycle; emptying a sensor a
     * cycle reads stops it.
     *
     * @return false if it was empty
     */
    public boolean clear(Slot slot) {
        BlockPos was = slots[slot.ordinal()];
        if (was == null) {
            return false;
        }
        empty(slot);
        if (slot.door()) {
            stop();
            if (level instanceof ServerLevel server && server.isLoaded(was)) {
                AirlockBlock.setBolted(server, was, false);
            }
        } else if (cycling >= 0 && (slot == Slot.CHAMBER_SENSOR || slot == Slot.sensor(cycling))) {
            stop();
        }
        setChanged();
        return true;
    }

    private void empty(Slot slot) {
        slots[slot.ordinal()] = null;
        sure &= ~(1 << slot.ordinal());
        if (slot.door() && lastOpened == slot.ordinal()) {
            lastOpened = -1;
        }
    }

    private void put(Slot slot, BlockPos pos, boolean isSure) {
        slots[slot.ordinal()] = pos;
        sure = isSure ? sure | 1 << slot.ordinal() : sure & ~(1 << slot.ordinal());
    }

    /** Swaps what fills two slots of one kind, either of which may be empty, and stops a cycle. */
    private void swap(Slot a, Slot b, boolean isSure) {
        BlockPos at = slots[a.ordinal()];
        BlockPos bt = slots[b.ordinal()];
        put(a, bt, isSure && bt != null);
        put(b, at, isSure && at != null);
        if (a.door() && lastOpened >= 0) {
            lastOpened = 1 - lastOpened; // the doors are slots 0 and 1
        }
        stop();
    }

    /**
     * Links or unlinks a block: an airlock, by its origin; an air sensor; or a vent.
     *
     * <ul>
     *   <li>With a slot armed, an airlock or a sensor goes to it and the slot disarms. One already in
     *       another slot swaps with what's in the armed slot; one that wasn't linked replaces it. An
     *       armed slot refuses the other kind and stays armed.
     *   <li>With none armed, one in a slot already is unlinked. Otherwise it goes to the slot guessed
     *       for it, if that's empty or was filled as the first empty slot (which then moves to the
     *       first empty slot of its kind), or else to the first empty slot of its kind.
     *   <li>Unlinking a door unbolts its airlock and stops a cycle, and unlinking a sensor a cycle
     *       reads stops it. Moving a device stops a cycle.
     * </ul>
     */
    public LinkResult toggleLink(ServerLevel level, BlockPos pos) {
        pos = pos.immutable();
        Slot in = slotOf(pos);
        boolean airlock = isAirlock(level, pos);
        boolean sensor = !airlock && level.getBlockEntity(pos) instanceof AirSensorBlockEntity;
        if (armed != null && (airlock || sensor)) {
            if (armed.door() != airlock) {
                return LinkResult.WRONG_KIND;
            }
            Slot target = armed;
            armed = null;
            if (in == null) {
                clear(target);
                put(target, pos, true);
            } else if (in != target) {
                swap(in, target, true);
            } else {
                put(target, pos, true);
            }
            setChanged();
            return LinkResult.LINKED;
        }
        if (in != null) {
            clear(in);
            return LinkResult.UNLINKED;
        }
        if (vents.remove(pos)) {
            setChanged();
            return LinkResult.UNLINKED;
        }
        if (airlock || sensor) {
            Slot free = firstEmpty(airlock);
            if (free == null) {
                return airlock ? LinkResult.AIRLOCKS_FULL : LinkResult.SENSOR_FULL;
            }
            Slot guess = guess(level, pos, airlock);
            if (guess != null && slots[guess.ordinal()] != null && !isSure(guess)) {
                swap(guess, free, false); // what was put there with no guess makes way
            }
            if (guess != null && slots[guess.ordinal()] == null) {
                put(guess, pos, true);
            } else {
                put(firstEmpty(airlock), pos, false);
            }
            setChanged();
            return LinkResult.LINKED;
        }
        if (level.getBlockEntity(pos) instanceof VentBlockEntity) {
            vents.add(pos);
            setChanged();
            return LinkResult.LINKED;
        }
        return LinkResult.NOT_LINKABLE;
    }

    private boolean isSure(Slot slot) {
        return (sure & 1 << slot.ordinal()) != 0;
    }

    private @Nullable Slot firstEmpty(boolean door) {
        for (Slot s : Slot.values()) {
            if (s.door() == door && slots[s.ordinal()] == null) {
                return s;
            }
        }
        return null;
    }

    /**
     * The slot guessed for the airlock or sensor at {@code pos}, from the zones around the doors
     * already linked and around it (CONT-138), or null if there's no guess.
     */
    private @Nullable Slot guess(ServerLevel level, BlockPos pos, boolean airlock) {
        BlockPos[] doors = airlocks();
        if (airlock) {
            BlockPos other = doors[0] != null ? doors[0] : doors[1];
            Sides mine = Sides.of(level, pos);
            Sides theirs = Sides.of(level, other);
            int mySide = mine.chamberSide(theirs);
            int theirSide = theirs.chamberSide(mine);
            int door = AirlockCycle.doorGuess(
                    mine.far(mySide), mine.farKpa(mySide), theirs.far(theirSide), theirs.farKpa(theirSide));
            return door == AirlockCycle.NONE ? null : Slot.door(door);
        }
        Sides inner = Sides.of(level, doors[0]);
        Sides outer = Sides.of(level, doors[1]);
        int innerSide = inner.chamberSide(outer);
        int outerSide = outer.chamberSide(inner);
        int role = AirlockCycle.sensorGuess(
                key(Atmospheres.at(level, Vec3.atCenterOf(pos))),
                new long[] {inner.chamber(innerSide), outer.chamber(outerSide)},
                new long[] {inner.far(innerSide), outer.far(outerSide)});
        return role == AirlockCycle.NONE ? null : Slot.sensor(role);
    }

    /** A place's key for the guess: its zone's id, open, or unknown ({@link AirlockCycle}). */
    private static long key(AtmosphereSample sample) {
        return switch (sample.state()) {
            case ZONED, PAUSED -> sample.zone().map(ZoneId::value).orElse(AirlockCycle.UNKNOWN);
            case AMBIENT -> AirlockCycle.OPEN;
            case UNRESOLVED -> AirlockCycle.UNKNOWN;
        };
    }

    /** What's in front of and behind an airlock, for the guess. */
    private record Sides(long front, long back, double frontKpa, double backKpa) {
        static final Sides NONE = new Sides(AirlockCycle.UNKNOWN, AirlockCycle.UNKNOWN, Double.NaN, Double.NaN);

        /** The cells in front of and behind the airlock whose origin is at {@code origin}, if there's one. */
        static Sides of(ServerLevel level, @Nullable BlockPos origin) {
            if (origin == null || !level.isLoaded(origin) || !isAirlock(level, origin)) {
                return NONE;
            }
            Direction facing = level.getBlockState(origin).getValue(AirlockBlock.FACING);
            AtmosphereSample front = Atmospheres.at(level, Vec3.atCenterOf(origin.relative(facing)));
            AtmosphereSample back = Atmospheres.at(level, Vec3.atCenterOf(origin.relative(facing.getOpposite())));
            return new Sides(key(front), key(back), front.pressure(), back.pressure());
        }

        int chamberSide(Sides other) {
            return AirlockCycle.chamberSide(front, back, other.front, other.back);
        }

        long chamber(int side) {
            return side == AirlockCycle.FRONT ? front : side == AirlockCycle.BACK ? back : AirlockCycle.UNKNOWN;
        }

        long far(int side) {
            return side == AirlockCycle.FRONT ? back : side == AirlockCycle.BACK ? front : AirlockCycle.UNKNOWN;
        }

        double farKpa(int side) {
            return side == AirlockCycle.FRONT ? backKpa : side == AirlockCycle.BACK ? frontKpa : Double.NaN;
        }
    }

    /** Called by the block when the controller is removed: unbolts its airlocks. */
    void removedFromWorld(ServerLevel level) {
        cycling = -1;
        for (BlockPos a : airlocks()) {
            if (a != null && level.isLoaded(a)) {
                AirlockBlock.setBolted(level, a, false);
            }
        }
    }

    // ---- For the window and tests -------------------------------------------------------

    /** The door it's cycling toward, {@link AirlockCycle#INNER} or {@code OUTER}, or −1 while idle. */
    public int cycling() {
        return cycling;
    }

    /** What its vents did at its last step: null while idle or waiting for a reading. */
    public AirlockCycle.Step cycleStep() {
        return step;
    }

    /** What fills a slot, or null. */
    public @Nullable BlockPos slot(Slot slot) {
        return slots[slot.ordinal()];
    }

    /** The slot {@code pos} fills, or null. */
    public @Nullable Slot slotOf(BlockPos pos) {
        for (Slot s : Slot.values()) {
            if (pos.equals(slots[s.ordinal()])) {
                return s;
            }
        }
        return null;
    }

    /** The armed slot, or null. */
    public @Nullable Slot armed() {
        return armed;
    }

    /** The inner and outer doors' airlocks' origins, either of which may be null. */
    public BlockPos[] airlocks() {
        return new BlockPos[] {slots[Slot.INNER_DOOR.ordinal()], slots[Slot.OUTER_DOOR.ordinal()]};
    }

    /**
     * The sensor in the slot for {@link AirlockCycle#INNER}, {@code OUTER} or {@code CHAMBER}, or
     * null.
     */
    public @Nullable BlockPos sensor(int role) {
        return slots[Slot.sensor(role).ordinal()];
    }

    /** The door opened last, {@link AirlockCycle#INNER} or {@code OUTER}, or −1 if neither has been. */
    public int lastOpened() {
        return lastOpened;
    }

    /** The linked vents. */
    public Set<BlockPos> linkedVents() {
        return Set.copyOf(vents);
    }

    /** The chamber's pressure at its sensor at its last step, kPa; NaN if it had no reading. */
    public double chamberKpa() {
        return chamberKpa;
    }

    /** The pressure past the inner (0) or outer (1) door at its sensor at its last step, kPa; NaN if none. */
    public double beyondKpa(int side) {
        return beyondKpa[side];
    }

    // ---- Saving and the menu ------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        power.save(tag);
        for (Slot s : Slot.values()) {
            if (slots[s.ordinal()] != null) {
                tag.putLong(s.tag, slots[s.ordinal()].asLong());
            }
        }
        tag.putInt("Sure", sure);
        tag.putInt("Armed", armed == null ? -1 : armed.ordinal());
        tag.putLongArray("Vents", vents.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("Cycling", cycling);
        tag.putInt("LastOpened", lastOpened);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        power.load(tag);
        for (Slot s : Slot.values()) {
            slots[s.ordinal()] = tag.contains(s.tag) ? BlockPos.of(tag.getLong(s.tag)) : null;
        }
        if (slots[Slot.CHAMBER_SENSOR.ordinal()] == null && tag.contains("Sensor")) {
            // Saved when a controller had one sensor, the chamber's.
            slots[Slot.CHAMBER_SENSOR.ordinal()] = BlockPos.of(tag.getLong("Sensor"));
        }
        sure = tag.getInt("Sure");
        armed = Slot.of(tag.contains("Armed") ? tag.getInt("Armed") : -1);
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
