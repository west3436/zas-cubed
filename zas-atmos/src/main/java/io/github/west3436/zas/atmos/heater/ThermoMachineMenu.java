package io.github.west3436.zas.atmos.heater;

import io.github.west3436.zas.atmos.portable.PortableMenu;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * A thermomachine's GUI (CONT-019, CONT-074): on or off, its setpoint beside its room's temperature,
 * what it did last step and its battery's charge. The server clamps the setpoint to the machine's
 * range ({@link ThermoMachineBlockEntity#setSetpoint}).
 */
public abstract class ThermoMachineMenu extends PortableMenu {
    /** Changes the setpoint by −10, −1, +1 and +10 K (ids 1–4). */
    public static final int SETPOINT_DOWN_10 = 1;

    public static final int LAST_BUTTON = 4;

    private static final double[] STEPS = {-10, -1, 1, 10};

    /** Where the battery slot is drawn. */
    public static final int BATTERY_X = 176;

    public static final int BATTERY_Y = 81;

    /** Where the player's inventory starts. */
    public static final int INVENTORY_Y = 134;

    /** The setpoint in tenths of a kelvin. */
    public static final int DATA_SETPOINT = PortableMenu.DATA_COUNT;

    /** The room's temperature in tenths of a kelvin, or {@link #NO_ROOM}. */
    public static final int DATA_ROOM = DATA_SETPOINT + 1;

    /** The {@link ThermoStatus}'s ordinal. */
    public static final int DATA_STATUS = DATA_SETPOINT + 2;

    /** The heat moved at the last step, W, rounded. */
    public static final int DATA_WATTS = DATA_SETPOINT + 3;

    public static final int DATA_COUNT = DATA_SETPOINT + 4;

    /** {@link #DATA_ROOM}'s value when it has no room: open air, unresolved, paused, or off. */
    public static final int NO_ROOM = -1;

    private final ThermoMachineBlockEntity machine;

    protected ThermoMachineMenu(
            MenuType<?> type,
            int id,
            Inventory inventory,
            IItemHandler battery,
            ContainerData data,
            ContainerLevelAccess access,
            Block block,
            ThermoMachineBlockEntity machine) {
        super(
                type,
                id,
                inventory,
                List.of(new SlotItemHandler(battery, 0, BATTERY_X, BATTERY_Y)),
                INVENTORY_Y,
                data,
                DATA_COUNT,
                access,
                block,
                machine);
        this.machine = machine;
    }

    /** The synced status. */
    public ThermoStatus status() {
        return ThermoStatus.of(value(DATA_STATUS));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= SETPOINT_DOWN_10 && id <= LAST_BUTTON && live()) {
            machine.setSetpoint(machine.setpoint() + STEPS[id - SETPOINT_DOWN_10]);
            return true;
        }
        return super.clickMenuButton(player, id);
    }
}
