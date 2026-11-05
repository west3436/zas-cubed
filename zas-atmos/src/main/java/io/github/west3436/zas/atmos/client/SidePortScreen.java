package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.inline.SidePortMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The GUI of a device with a side port: the inline device's, with its target, and a row of buttons
 * to step the target and flip the side port. It has one more port line than a pump, so its switch
 * sits lower. The device's own buttons go below that row.
 */
abstract class SidePortScreen<M extends SidePortMenu> extends InlineDeviceScreen<M> {
    private static final String[] STEPS = {"-100", "-10", "+10", "+100"};
    private static final int WIDTH = 36;
    private static final int GAP = 37;

    private final int switchY;

    /**
     * @param portKeys each port's line's translation key, which takes the pressure in kPa
     * @param switchY where the switch goes, below the most lines there can be
     * @param height the GUI's height
     */
    SidePortScreen(M menu, Inventory inventory, Component title, List<String> portKeys, int switchY, int height) {
        super(menu, inventory, title, portKeys);
        this.switchY = switchY;
        imageHeight = height;
    }

    @Override
    int switchY() {
        return switchY;
    }

    /** Where the device's own buttons start. */
    int ownButtonsY() {
        return switchY + 40;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < STEPS.length; i++) {
            button(8 + GAP * i, switchY + 20, WIDTH, Component.literal(STEPS[i]), SidePortMenu.STEP_DOWN_BIG + i);
        }
        button(
                8 + GAP * STEPS.length,
                switchY + 20,
                WIDTH,
                Component.translatable("gui.zas_atmos.inline.flip"),
                SidePortMenu.FLIP);
    }

    /** The target; a device with more settings adds them. */
    @Override
    List<Component> settingLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.zas_atmos.pump.target", menu.value(SidePortMenu.DATA_TARGET)));
        return lines;
    }
}
