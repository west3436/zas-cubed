package io.github.west3436.zas.atmos.airlock;

import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity.Slot;
import io.github.west3436.zas.atmos.label.Labelled;
import io.github.west3436.zas.atmos.label.LabelledMenu;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * An airlock controller's window (CONT-138, CONT-153, CONT-074): whether it's cycling and toward
 * which door, its power, its five slots and the armed one, and the pressure at the chamber sensor and
 * at the sensor past each door. The client sends only button ids: cycle toward the inner or outer
 * door, stop, arm a slot or clear one. The server checks each, and {@link
 * AirlockControllerBlockEntity#requestCycle(int)} refuses a cycle toward a door that isn't set, as it
 * does one with no power, without the sensors it needs, or one already running.
 */
public class AirlockControllerMenu extends AbstractContainerMenu implements LabelledMenu {
    private static final int SLOTS = Slot.values().length;

    /** Cycle toward the inner door (id 0) or the outer door (id 1). */
    public static final int CYCLE = 0;

    /** Stop a cycle. */
    public static final int STOP = 2;

    /** Arm a slot, or disarm it if it's armed: 3 + its place in {@link Slot}'s order. */
    public static final int ARM = 3;

    /** Clear a slot: 8 + its place in {@link Slot}'s order. */
    public static final int CLEAR = ARM + SLOTS;

    /** 0 while idle, else 1 + the door it's cycling toward. */
    public static final int DATA_CYCLING = 0;

    public static final int DATA_POWERED = 1;
    /** Bit i: slot i, in {@link Slot}'s order, is filled. */
    public static final int DATA_LINKS = 2;

    public static final int DATA_VENTS = 3;
    /** The chamber's pressure in hundredths of a kPa, in two halves, or −1 with no reading. */
    public static final int DATA_CHAMBER = 4;
    /** The pressure past the inner door, then the outer, as {@link #DATA_CHAMBER}. */
    public static final int DATA_BEYOND = 6;
    /** What its vents are doing: 0 nothing, 1 siphoning, 2 blowing. */
    public static final int DATA_STEP = 10;
    /** Each door's look: bit 0 the inner door open, bit 1 it bolted, bits 2 and 3 the outer door's. */
    public static final int DATA_DOORS = 11;
    /** 0 with no slot armed, else 1 + the armed slot's place in {@link Slot}'s order. */
    public static final int DATA_ARMED = 12;
    /** What fills each slot, in {@link Slot}'s order, as four shorts of {@link BlockPos#asLong}. */
    public static final int DATA_SLOTS = 13;

    public static final int DATA_COUNT = DATA_SLOTS + 4 * SLOTS;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final AirlockControllerBlockEntity controller;

    /** The client's menu. */
    public AirlockControllerMenu(int id, Inventory inventory) {
        super(AtmosMenus.AIRLOCK_CONTROLLER.get(), id);
        this.data = new SimpleContainerData(DATA_COUNT);
        this.access = ContainerLevelAccess.NULL;
        this.controller = null;
        addDataSlots(data);
    }

    /** The server's menu, on a controller. */
    public AirlockControllerMenu(int id, AirlockControllerBlockEntity controller, BlockPos pos) {
        super(AtmosMenus.AIRLOCK_CONTROLLER.get(), id);
        this.controller = controller;
        this.access = ContainerLevelAccess.create(controller.getLevel(), pos);
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_CYCLING -> controller.cycling() + 1;
                    case DATA_POWERED -> controller.power().isPowered() ? 1 : 0;
                    case DATA_LINKS -> links(controller);
                    case DATA_VENTS ->
                        Math.min(Short.MAX_VALUE, controller.linkedVents().size());
                    case DATA_CHAMBER, DATA_CHAMBER + 1 -> half(controller.chamberKpa(), index - DATA_CHAMBER);
                    case DATA_BEYOND, DATA_BEYOND + 1 -> half(controller.beyondKpa(0), index - DATA_BEYOND);
                    case DATA_BEYOND + 2, DATA_BEYOND + 3 -> half(controller.beyondKpa(1), index - DATA_BEYOND - 2);
                    case DATA_STEP ->
                        controller.cycleStep() == AirlockCycle.Step.SIPHON
                                ? 1
                                : controller.cycleStep() == AirlockCycle.Step.BLOW ? 2 : 0;
                    case DATA_DOORS -> door(controller, 0) | door(controller, 1) << 2;
                    case DATA_ARMED ->
                        controller.armed() == null ? 0 : controller.armed().ordinal() + 1;
                    default -> index >= DATA_SLOTS && index < DATA_COUNT ? slotPart(controller, index - DATA_SLOTS) : 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(data);
    }

    private static int links(AirlockControllerBlockEntity controller) {
        int bits = 0;
        for (Slot s : Slot.values()) {
            bits |= controller.slot(s) != null ? 1 << s.ordinal() : 0;
        }
        return bits;
    }

    /** One of the four shorts of what fills a slot: {@code part} is 4 × the slot + which short. */
    private static int slotPart(AirlockControllerBlockEntity controller, int part) {
        BlockPos at = controller.slot(Slot.values()[part / 4]);
        return at == null ? 0 : (short) (at.asLong() >> 16 * (part % 4));
    }

    /** Door {@code i}'s look as the window shows it: bit 0 open, bit 1 bolted, or 0 if it isn't set or loaded. */
    public static int door(AirlockControllerBlockEntity controller, int i) {
        BlockPos at = controller.airlocks()[i];
        Level level = controller.getLevel();
        if (at == null || level == null || !level.isLoaded(at)) {
            return 0;
        }
        BlockState state = level.getBlockState(at);
        if (!(state.getBlock() instanceof AirlockBlock)) {
            return 0;
        }
        return (state.getValue(AirlockBlock.OPEN) ? 1 : 0) | (state.getValue(AirlockBlock.BOLTED) ? 2 : 0);
    }

    /** One 16-bit half of a pressure in hundredths of a kPa, or of −1 for no reading: menu data syncs as shorts. */
    private static int half(double kpa, int which) {
        int v = Double.isNaN(kpa) ? -1 : (int) Math.min(Integer.MAX_VALUE, Math.round(Math.max(0, kpa) * 100));
        return which == 0 ? (short) v : (short) (v >> 16);
    }

    /** A synced pressure, kPa, from its two halves at {@code index}; NaN with no reading. */
    public double pressure(int index) {
        int v = (data.get(index + 1) << 16) | (data.get(index) & 0xFFFF);
        return v < 0 ? Double.NaN : v / 100.0;
    }

    /** Whether a slot is filled. */
    public boolean filled(Slot slot) {
        return (data.get(DATA_LINKS) & 1 << slot.ordinal()) != 0;
    }

    /** What fills a slot, or null. */
    public @Nullable BlockPos slotPos(Slot slot) {
        if (!filled(slot)) {
            return null;
        }
        long v = 0;
        for (int i = 0; i < 4; i++) {
            v |= (data.get(DATA_SLOTS + 4 * slot.ordinal() + i) & 0xFFFFL) << 16 * i;
        }
        return BlockPos.of(v);
    }

    /** The armed slot, or null. */
    public @Nullable Slot armed() {
        return Slot.of(data.get(DATA_ARMED) - 1);
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /**
     * Applies a button: the server's half of the window. Unknown ids are refused, and the controller
     * refuses a cycle it can't start.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (controller == null || controller.isRemoved()) {
            return false;
        }
        return switch (id) {
            case CYCLE, CYCLE + 1 -> controller.requestCycle(id - CYCLE);
            case STOP -> controller.stop();
            default -> {
                Slot slot = Slot.of(id - ARM);
                if (slot != null) {
                    yield controller.arm(slot);
                }
                slot = Slot.of(id - CLEAR);
                yield slot != null && controller.clear(slot);
            }
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** The device whose label the title bar sets. */
    @Override
    public @Nullable Labelled labelled() {
        return controller;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.AIRLOCK_CONTROLLER.get());
    }
}
