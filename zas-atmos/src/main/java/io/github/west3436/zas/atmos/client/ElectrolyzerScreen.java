package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlockEntity;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerMath;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerMenu;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The electrolyzer's window (CONT-055, CONT-056). Its strip shows the two outlets as a player facing
 * its front sees them, O₂ on the left and H₂ on the right, with the water between them and an arrow
 * out to each that turns green while it makes gas. Below that come its state and switch, and its water
 * as a bar.
 */
final class ElectrolyzerScreen extends InlineScreen<ElectrolyzerMenu> {
    /** The state row, then the water's bar. */
    private static final int SECTION = 2 + STATE_ROW + Ui.ROW + 1;

    ElectrolyzerScreen(ElectrolyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, STRIP + Ui.PAD + SECTION);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        y = noPower(ui, x, y, w);
        outlets(ui, x, y, w);
        y += STRIP + Ui.PAD;
        ui.section(x, y, w, SECTION, null);
        int row = y + 2;
        stateRow(ui, x, row, w);
        row += STATE_ROW;

        int labelX = x + Ui.PAD;
        int barX = labelX + DeviceParts.LABEL_WIDTH;
        int barH = Ui.ROW - 1;
        ui.line(text("water"), labelX, row, barH, Ui.LABEL, Face.BODY);
        int water = menu.water();
        ui.bar(
                barX,
                row,
                x + w - Ui.PAD - barX,
                barH,
                (double) water / ElectrolyzerMath.TANK_MB,
                Ui.BLUE,
                Ui.BLUE_EDGE,
                text("millibuckets", whole(water), whole(ElectrolyzerMath.TANK_MB)));
    }

    /** The O₂ and H₂ outlets either side of the water, with an arrow out to each. */
    private void outlets(Ui ui, int x, int y, int w) {
        cell(ui, x, y, gas(StandardAir.OXYGEN), ElectrolyzerBlockEntity.OXYGEN);
        cell(ui, x + w - CELL_WIDTH, y, gas(ElectrolyzerBlockEntity.H2), ElectrolyzerBlockEntity.HYDROGEN);
        Component water = text("water_formula");
        ui.lineCentred(water, x, w, y, CELL_HEIGHT, Ui.LABEL, Face.BODY);
        float centre = x + w / 2f;
        float half = ui.width(water, Face.BODY) / 2 + ARROW_GAP;
        float middle = y + CELL_HEIGHT / 2f;
        int colour = arrowColour();
        ui.arrow(centre - half, middle, x + CELL_WIDTH + ARROW_GAP, middle, colour);
        ui.arrow(centre + half, middle, x + w - CELL_WIDTH - ARROW_GAP, middle, colour);
    }

    /** "Making gas" rather than the inline devices' "Moving gas". */
    @Override
    protected Component statusWord(InlineStatus status) {
        return status == InlineStatus.MOVING ? text("making") : super.statusWord(status);
    }

    /** A gas's name, after a square of its colour, in the cell's colour. */
    private static Component gas(ResourceLocation gas) {
        return Component.empty()
                .append(Component.literal("■ ").withColor(GasParts.colour(gas.toString()) & 0xFFFFFF))
                .append(GasCodecs.name(gas));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.zas_atmos.electrolyzer." + key, args);
    }

    private static String whole(int mb) {
        return String.format(Locale.ROOT, "%,d", mb);
    }
}
