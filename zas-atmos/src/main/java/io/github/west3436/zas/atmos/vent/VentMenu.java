package io.github.west3436.zas.atmos.vent;

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
 * A vent's GUI (CONT-074, CONT-014): its mode and bounds, and whether it has power. The client
 * sends only button ids; the server checks each id, and {@link VentSettings} clamps the bounds to
 * 0–5,066 kPa.
 */
public class VentMenu extends AbstractContainerMenu {
    /** Cycles the mode: off, blow, siphon. */
    public static final int CYCLE_MODE = 0;

    /** Change the external bound by −100, −10, +10 and +100 kPa (ids 1–4), the internal bound likewise (5–8). */
    public static final int EXTERNAL_DOWN_100 = 1;

    public static final int INTERNAL_DOWN_100 = 5;
    public static final int LAST_BUTTON = 8;

    /** The synced values: mode, bounds (kPa, rounded) and power. */
    public static final int DATA_MODE = 0;

    public static final int DATA_EXTERNAL = 1;
    public static final int DATA_INTERNAL = 2;
    public static final int DATA_POWERED = 3;
    public static final int DATA_COUNT = 4;

    private static final double[] STEPS = {-100, -10, 10, 100};

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final VentBlockEntity vent;

    /** The client's menu. */
    public VentMenu(int id, Inventory inventory) {
        this(id, new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL, null);
    }

    /** The server's menu, on a vent. */
    public VentMenu(int id, ContainerData data, ContainerLevelAccess access, VentBlockEntity vent) {
        super(AtmosMenus.VENT.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.access = access;
        this.vent = vent;
        addDataSlots(data);
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /** The synced mode. */
    public VentMode mode() {
        int i = data.get(DATA_MODE);
        return i >= 0 && i < VentMode.values().length ? VentMode.values()[i] : VentMode.OFF;
    }

    /** Applies a button: the server's half of the GUI. Unknown ids are refused. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (vent == null || vent.isRemoved() || id < CYCLE_MODE || id > LAST_BUTTON) {
            return false;
        }
        VentSettings s = vent.settings();
        if (id == CYCLE_MODE) {
            vent.command(s.withMode(s.mode().next()));
        } else if (id < INTERNAL_DOWN_100) {
            vent.command(new VentSettings(s.mode(), s.external() + STEPS[id - EXTERNAL_DOWN_100], s.internal()));
        } else {
            vent.command(new VentSettings(s.mode(), s.external(), s.internal() + STEPS[id - INTERNAL_DOWN_100]));
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.VENT.get());
    }
}
