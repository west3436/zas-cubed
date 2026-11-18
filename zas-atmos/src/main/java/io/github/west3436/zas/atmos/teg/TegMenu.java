package io.github.west3436.zas.atmos.teg;

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
 * A TEG core's window (CONT-159): its switch and state, each stream's temperatures in and out and its
 * two networks' pressures, what it makes and what it has stored. The client sends only the switch.
 * Menu data syncs as shorts, so the store's FE comes in two halves.
 */
public class TegMenu extends AbstractContainerMenu {
    /** Switches it on or off. */
    public static final int TOGGLE = 0;

    public static final int DATA_ON = 0;
    public static final int DATA_STATUS = 1;

    /** What it makes, FE/t, rounded and capped at 32,767. */
    public static final int DATA_POWER = 2;

    public static final int DATA_STORED_HIGH = 3;
    public static final int DATA_STORED_LOW = 4;
    public static final int DATA_CAPACITY_HIGH = 5;
    public static final int DATA_CAPACITY_LOW = 6;

    /** Hot in, hot out, cold in, cold out, K, rounded. */
    public static final int DATA_TEMPERATURE = 7;

    /** Hot inlet, hot outlet, cold inlet, cold outlet networks, kPa, rounded. */
    public static final int DATA_PRESSURE = DATA_TEMPERATURE + 4;

    public static final int DATA_COUNT = DATA_PRESSURE + 4;

    private final ContainerData data;
    private final @Nullable TegBlockEntity core;
    private final ContainerLevelAccess access;

    /** The client's. */
    public TegMenu(int id, Inventory inventory) {
        this(id, null, new SimpleContainerData(DATA_COUNT));
    }

    /** The server's, on a core. */
    public TegMenu(int id, @Nullable TegBlockEntity core, ContainerData data) {
        super(AtmosMenus.TEG.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.core = core;
        this.access = core == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(core.getLevel(), core.getBlockPos());
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

    public TegStatus status() {
        return TegStatus.of(data.get(DATA_STATUS));
    }

    /** What it makes, FE/t. */
    public int power() {
        return data.get(DATA_POWER);
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

    /** A stream's temperature, K: side 0 hot, 1 cold. */
    public int temperature(int side, boolean in) {
        return data.get(DATA_TEMPERATURE + 2 * side + (in ? 0 : 1));
    }

    /** A side's network's pressure, kPa: side 0 hot, 1 cold. */
    public int pressure(int side, boolean in) {
        return data.get(DATA_PRESSURE + 2 * side + (in ? 0 : 1));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (core == null || core.isRemoved() || id != TOGGLE) {
            return false;
        }
        core.setOn(!core.on());
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return core == null || stillValid(access, player, core.getBlockState().getBlock());
    }
}
