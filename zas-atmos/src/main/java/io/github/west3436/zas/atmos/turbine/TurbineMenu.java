package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.atmos.device.RedstoneControl;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A gas turbine's window (CONT-163): its switch and state, the shaft's speed, the chamber's pressure
 * and temperature, what its generator makes and what it has stored, and its redstone setting
 * (CONT-170). The client sends only the switch and the redstone setting's buttons. Menu data syncs as
 * shorts, so the store's FE comes in two halves.
 */
public class TurbineMenu extends AbstractContainerMenu {
    /** Switches it on or off. */
    public static final int TOGGLE = 0;

    public static final int DATA_ON = 0;
    public static final int DATA_STATUS = 1;
    public static final int DATA_RPM = 2;
    public static final int DATA_CHAMBER_PRESSURE = 3;
    public static final int DATA_CHAMBER_TEMPERATURE = 4;

    /** What it makes, FE/t, rounded and capped at 32,767. */
    public static final int DATA_POWER = 5;

    public static final int DATA_STORED_HIGH = 6;
    public static final int DATA_STORED_LOW = 7;
    public static final int DATA_CAPACITY_HIGH = 8;
    public static final int DATA_CAPACITY_LOW = 9;

    /** Its top speed, rpm, for the speed bar. */
    public static final int DATA_TOP_RPM = 10;

    /** Its {@linkplain RedstoneControl#data() redstone setting and signal} (CONT-170). */
    public static final int DATA_REDSTONE = 11;

    public static final int DATA_COUNT = 12;

    private final ContainerData data;
    private final @Nullable TurbineBlockEntity turbine;
    private final ContainerLevelAccess access;

    /** The client's. */
    public TurbineMenu(int id, Inventory inventory) {
        this(id, null, new SimpleContainerData(DATA_COUNT));
    }

    /** The server's, on a turbine. */
    public TurbineMenu(int id, @Nullable TurbineBlockEntity turbine, ContainerData data) {
        super(AtmosMenus.TURBINE.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.turbine = turbine;
        this.access = turbine == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(turbine.getLevel(), turbine.getBlockPos());
        addDataSlots(data);
    }

    /** A value as a short carries it: clamped. */
    static int shortOf(long value) {
        return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    public boolean on() {
        return data.get(DATA_ON) != 0;
    }

    public TurbineStatus status() {
        return TurbineStatus.of(data.get(DATA_STATUS));
    }

    /** Its redstone setting and signal, as synced: read with {@link RedstoneControl#mode(int)} and the rest. */
    public int redstone() {
        return data.get(DATA_REDSTONE);
    }

    /** Its stored FE. */
    public int energy() {
        return join(DATA_STORED_HIGH);
    }

    /** Its store's size, FE. */
    public int capacity() {
        return join(DATA_CAPACITY_HIGH);
    }

    private int join(int high) {
        return ((data.get(high) & 0xFFFF) << 16) | (data.get(high + 1) & 0xFFFF);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (turbine == null || turbine.isRemoved()) {
            return false;
        }
        if (RedstoneControl.isButton(id)) {
            return turbine.redstone().button(id);
        }
        if (id != TOGGLE) {
            return false;
        }
        turbine.setOn(!turbine.on());
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return turbine == null
                || stillValid(access, player, turbine.getBlockState().getBlock());
    }
}
