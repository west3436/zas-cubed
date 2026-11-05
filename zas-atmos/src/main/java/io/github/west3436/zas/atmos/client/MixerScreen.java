package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.mixer.MixerMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A mixer's GUI (CONT-077): the side port's, with its fractions and four buttons to step them. */
final class MixerScreen extends SidePortScreen<MixerMenu> {
    private static final List<String> PORTS =
            List.of("gui.zas_atmos.mixer.back", "gui.zas_atmos.inline.output", "gui.zas_atmos.mixer.side");
    private static final String[] STEPS = {"-10%", "-1%", "+1%", "+10%"};

    /** Below the most lines: state, a status of two, the target, the fractions, three ports and power. */
    private static final int SWITCH_Y = 121;

    MixerScreen(MixerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PORTS, SWITCH_Y, SWITCH_Y + 40 + 22);
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < STEPS.length; i++) {
            button(8 + 46 * i, ownButtonsY(), 44, Component.literal(STEPS[i]), MixerMenu.MIX_DOWN_BIG + i);
        }
    }

    @Override
    List<Component> settingLines() {
        List<Component> lines = super.settingLines();
        int back = menu.value(MixerMenu.DATA_BACK_PERCENT);
        lines.add(Component.translatable("gui.zas_atmos.mixer.fractions", back, 100 - back));
        return lines;
    }
}
