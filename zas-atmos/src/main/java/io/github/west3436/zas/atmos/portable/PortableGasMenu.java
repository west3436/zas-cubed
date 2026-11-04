package io.github.west3436.zas.atmos.portable;

import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * A portable pump's or scrubber's GUI (CONT-018, CONT-074): its battery and tank slots, on or off,
 * what it did last step, the pressures of the air it works on and of its store, and its battery's
 * charge.
 */
public abstract class PortableGasMenu extends PortableMenu {
    /** Where the slots are drawn. */
    public static final int SLOT_X = 176;

    public static final int BATTERY_Y = 88;
    public static final int TANK_Y = 108;

    /** Where the player's inventory starts. */
    public static final int INVENTORY_Y = 160;

    /** The {@link PortableStatus}'s ordinal. */
    public static final int DATA_STATUS = PortableMenu.DATA_COUNT;

    /** The pressure of the air it works on in kPa, or {@link #NO_AIR}. */
    public static final int DATA_AIR = DATA_STATUS + 1;

    /** 1 if the air is a held tank's, 0 if it is the room's. */
    public static final int DATA_ON_TANK = DATA_STATUS + 2;

    /** Its store's pressure in kPa. */
    public static final int DATA_STORE = DATA_STATUS + 3;

    public static final int DATA_COUNT = DATA_STATUS + 4;

    /** {@link #DATA_AIR}'s value when it has no air to work on, or is off. */
    public static final int NO_AIR = -1;

    /**
     * @param dataCount how many values there must be: {@link #DATA_COUNT} or more
     * @param device the portable; null on the client
     */
    protected PortableGasMenu(
            MenuType<?> type,
            int id,
            Inventory inventory,
            IItemHandler battery,
            IItemHandler tank,
            ContainerData data,
            int dataCount,
            ContainerLevelAccess access,
            Block block,
            PortableGasDevice device) {
        super(
                type,
                id,
                inventory,
                List.of(
                        new SlotItemHandler(battery, 0, SLOT_X, BATTERY_Y),
                        new SlotItemHandler(tank, 0, SLOT_X, TANK_Y)),
                INVENTORY_Y,
                data,
                dataCount,
                access,
                block,
                device);
    }

    /** The synced status. */
    public PortableStatus status() {
        return PortableStatus.of(value(DATA_STATUS));
    }
}
