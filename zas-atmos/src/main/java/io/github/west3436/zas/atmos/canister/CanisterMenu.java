package io.github.west3436.zas.atmos.canister;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * A canister's GUI (CONT-074, CONT-084): its pressure and contents, its valve, and its release
 * pressure. The client sends only button ids; the server checks each id and clamps the release
 * pressure to 10–1,013 kPa.
 */
public class CanisterMenu extends AbstractContainerMenu {
    /** Opens or closes the valve. */
    public static final int TOGGLE_VALVE = 0;

    /** Change the release pressure by −100, −10, +10 and +100 kPa. */
    public static final int RELEASE_DOWN_100 = 1;

    public static final int RELEASE_DOWN_10 = 2;
    public static final int RELEASE_UP_10 = 3;
    public static final int RELEASE_UP_100 = 4;

    /** The synced values: release pressure, valve, canister pressure (kPa, rounded) and moles. */
    public static final int DATA_RELEASE = 0;

    public static final int DATA_OPEN = 1;
    public static final int DATA_PRESSURE = 2;
    public static final int DATA_MOLES = 3;
    public static final int DATA_COUNT = 4;

    private static final double[] STEPS = {0, -100, -10, 10, 100};

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final CanisterBlockEntity canister;

    /** The client's menu. */
    public CanisterMenu(int id, Inventory inventory) {
        this(id, new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL, null);
    }

    /** The server's menu, on a canister. */
    public CanisterMenu(int id, ContainerData data, ContainerLevelAccess access, CanisterBlockEntity canister) {
        super(AtmosMenus.CANISTER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.access = access;
        this.canister = canister;
        addDataSlots(data);
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /** Applies a button: the server's half of the GUI. Unknown ids are refused. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (canister == null || canister.isRemoved()) {
            return false;
        }
        if (id == TOGGLE_VALVE) {
            canister.setValveOpen(!canister.valveOpen());
            return true;
        }
        if (id >= RELEASE_DOWN_100 && id <= RELEASE_UP_100) {
            canister.setReleasePressure(canister.releasePressure() + STEPS[id]);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.CANISTER.get());
    }
}
