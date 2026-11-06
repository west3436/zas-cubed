package io.github.west3436.zas.atmos.pump;

import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * A pump's GUI (CONT-074): the base's, with its one setting and four buttons to step it. The pump
 * clamps the setting.
 */
public final class PumpMenu extends InlineDeviceMenu {
    /** The largest step down; the smaller step down, the smaller up and the largest up follow. */
    public static final int STEP_DOWN_BIG = TOGGLE + 1;

    /** Sets the setting back to a new pump's. */
    public static final int DEFAULT = STEP_DOWN_BIG + 4;

    public static final int LAST_BUTTON = DEFAULT;

    /** The setting, rounded. */
    public static final int DATA_SETTING = InlineDeviceMenu.DATA_COUNT;

    public static final int DATA_COUNT = DATA_SETTING + 1;

    private final PumpBlockEntity pump;

    /** The client's menu. */
    public PumpMenu(MenuType<?> type, int id) {
        super(type, id, new SimpleContainerData(DATA_COUNT), DATA_COUNT, null);
        this.pump = null;
    }

    /** The server's menu, on a pump. */
    public PumpMenu(MenuType<?> type, int id, PumpBlockEntity pump) {
        super(type, id, pump.data(), DATA_COUNT, pump);
        this.pump = pump;
    }

    @Override
    protected boolean ownButton(int id) {
        if (id < STEP_DOWN_BIG || id > LAST_BUTTON) {
            return false;
        }
        if (id == DEFAULT) {
            pump.reset();
        } else {
            pump.step(id - STEP_DOWN_BIG);
        }
        return true;
    }
}
