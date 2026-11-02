package io.github.west3436.zas.atmos.vent;

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

/**
 * A vent's GUI (CONT-074, CONT-014): its mode and bounds, whether it has power, and the area air
 * controller that controls it, if one does. The client sends only button ids; the server checks
 * each id, refuses every button while a controller controls the vent, and {@link VentSettings}
 * clamps the bounds to 0–5,066 kPa.
 */
public class VentMenu extends AbstractContainerMenu {
    /** Cycles the mode: off, blow, siphon. */
    public static final int CYCLE_MODE = 0;

    /** Change the external bound by −100, −10, +10 and +100 kPa (ids 1–4), the internal bound likewise (5–8). */
    public static final int EXTERNAL_DOWN_100 = 1;

    public static final int INTERNAL_DOWN_100 = 5;
    public static final int LAST_BUTTON = 8;

    /**
     * The synced values: mode, bounds (kPa, rounded), power, and control: 0 if no controller
     * controls the vent, 1 if one at the synced position does, 2 if one at an unknown position
     * does. Menu data syncs as shorts, so x and z are sent in two halves.
     */
    public static final int DATA_MODE = 0;

    public static final int DATA_EXTERNAL = 1;
    public static final int DATA_INTERNAL = 2;
    public static final int DATA_POWERED = 3;
    public static final int DATA_CONTROLLED = 4;
    public static final int DATA_X_LOW = 5;
    public static final int DATA_X_HIGH = 6;
    public static final int DATA_Y = 7;
    public static final int DATA_Z_LOW = 8;
    public static final int DATA_Z_HIGH = 9;
    public static final int DATA_COUNT = 10;

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

    /** Whether a controller controls the vent, as synced. */
    public boolean controlled() {
        return data.get(DATA_CONTROLLED) != 0;
    }

    /** The synced position of the controller that controls the vent, if one does and it is known. */
    public java.util.Optional<BlockPos> controller() {
        if (data.get(DATA_CONTROLLED) != 1) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new BlockPos(
                join(data.get(DATA_X_LOW), data.get(DATA_X_HIGH)),
                (short) data.get(DATA_Y),
                join(data.get(DATA_Z_LOW), data.get(DATA_Z_HIGH))));
    }

    private static int join(int low, int high) {
        return (high << 16) | (low & 0xFFFF);
    }

    /**
     * Applies a button: the server's half of the GUI. Unknown ids are refused, and so is every
     * button while a controller controls the vent.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (vent == null || vent.isRemoved() || id < CYCLE_MODE || id > LAST_BUTTON) {
            return false;
        }
        VentSettings s = vent.settings();
        VentSettings next;
        if (id == CYCLE_MODE) {
            next = s.withMode(s.mode().next());
        } else if (id < INTERNAL_DOWN_100) {
            next = new VentSettings(s.mode(), s.external() + STEPS[id - EXTERNAL_DOWN_100], s.internal());
        } else {
            next = new VentSettings(s.mode(), s.external(), s.internal() + STEPS[id - INTERNAL_DOWN_100]);
        }
        return vent.setByHand(next);
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
