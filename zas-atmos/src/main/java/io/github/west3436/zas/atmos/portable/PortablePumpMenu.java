package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A portable pump's GUI (CONT-018, CONT-074): see {@link PortableGasMenu}, with which way it pumps and
 * its target pressure, which the server clamps to 0–4,500 kPa ({@link
 * PortablePumpBlockEntity#setTarget}).
 */
public class PortablePumpMenu extends PortableGasMenu {
    /** Switches between releasing and siphoning. */
    public static final int TOGGLE_DIRECTION = 1;

    /** Changes the target by −100, −10, +10 and +100 kPa (ids 2–5). */
    public static final int TARGET_DOWN_100 = 2;

    public static final int LAST_BUTTON = 5;

    /** How far its two rows of settings push down its slots and the player's inventory. */
    public static final int SHIFT = 22;

    /** 1 if it siphons, 0 if it releases. */
    public static final int DATA_SIPHON = PortableGasMenu.DATA_COUNT;

    /** Its target pressure, in kPa. */
    public static final int DATA_TARGET = DATA_SIPHON + 1;

    public static final int DATA_COUNT = DATA_SIPHON + 2;

    private static final double[] STEPS = {-100, -10, 10, 100};

    private final PortablePumpBlockEntity pump;

    /** The client's menu. */
    public PortablePumpMenu(int id, Inventory inventory) {
        this(
                id,
                inventory,
                new Batteries.Slot(() -> {}),
                new TankSlot(() -> {}),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL,
                null);
    }

    /** The server's menu, on a portable pump. */
    public PortablePumpMenu(
            int id,
            Inventory inventory,
            IItemHandler battery,
            IItemHandler tank,
            ContainerData data,
            ContainerLevelAccess access,
            PortablePumpBlockEntity pump) {
        super(
                AtmosMenus.PORTABLE_PUMP.get(),
                id,
                inventory,
                battery,
                tank,
                data,
                DATA_COUNT,
                access,
                AtmosBlocks.PORTABLE_PUMP.get(),
                pump,
                SHIFT);
        this.pump = pump;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < TOGGLE_DIRECTION || id > LAST_BUTTON) {
            return super.clickMenuButton(player, id);
        }
        if (!live()) {
            return false;
        }
        if (id == TOGGLE_DIRECTION) {
            pump.setSiphon(!pump.siphon());
        } else {
            pump.setTarget(pump.target() + STEPS[id - TARGET_DOWN_100]);
        }
        return true;
    }
}
