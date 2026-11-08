package io.github.west3436.zas.atmos.airlock;

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

/**
 * An airlock controller's window (CONT-153, CONT-074): whether it's cycling and toward which
 * airlock, its power, what's linked, and the pressure at the chamber's sensor and at the sensor
 * beyond each airlock. The client sends only button ids: cycle toward airlock 1 or 2, or stop. The
 * server checks each, and {@link AirlockControllerBlockEntity#requestCycle(int)} refuses a cycle
 * toward an airlock that isn't linked, as it does one with no power, without the sensors it needs,
 * or one already running.
 */
public class AirlockControllerMenu extends AbstractContainerMenu {
    /** Cycle toward airlock 1 (id 0) or 2 (id 1). */
    public static final int CYCLE = 0;

    /** Stop a cycle. */
    public static final int STOP = 2;

    /** 0 while idle, else 1 + the airlock it's cycling toward. */
    public static final int DATA_CYCLING = 0;

    public static final int DATA_POWERED = 1;
    /**
     * Bit 0 and bit 1: each airlock is linked; bit 2: the chamber has a sensor; bits 3 and 4: the
     * side beyond airlock 1 and 2 has one.
     */
    public static final int DATA_LINKS = 2;

    public static final int DATA_VENTS = 3;
    /** The chamber's pressure in hundredths of a kPa, in two halves, or −1 with no reading. */
    public static final int DATA_CHAMBER = 4;
    /** The pressure beyond airlock 1, then 2, as {@link #DATA_CHAMBER}. */
    public static final int DATA_BEYOND = 6;
    /** What its vents are doing: 0 nothing, 1 siphoning, 2 blowing. */
    public static final int DATA_STEP = 10;
    /** Each airlock's look: bit 0 airlock 1 open, bit 1 airlock 1 bolted, bits 2 and 3 airlock 2's. */
    public static final int DATA_DOORS = 11;

    public static final int DATA_COUNT = 12;

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
                    case DATA_LINKS ->
                        (controller.airlocks()[0] != null ? 1 : 0)
                                | (controller.airlocks()[1] != null ? 2 : 0)
                                | (controller.sensor(AirlockCycle.CHAMBER) != null ? 4 : 0)
                                | (controller.sensor(AirlockCycle.SIDE_1) != null ? 8 : 0)
                                | (controller.sensor(AirlockCycle.SIDE_2) != null ? 16 : 0);
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
                    default -> 0;
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

    /** Airlock {@code i}'s open and bolted bits, or 0 if it isn't linked or loaded. */
    private static int door(AirlockControllerBlockEntity controller, int i) {
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
            default -> false;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.AIRLOCK_CONTROLLER.get());
    }
}
