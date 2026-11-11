package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.atmos.analyzer.AnalyzerMenu;
import io.github.west3436.zas.atmos.analyzer.AnalyzerSync;
import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * An analyzer's window (CONT-034): what it reads, then a gauge of the pressure, the pressure and
 * temperature, and each gas's share and partial pressure. A tank in the other hand gets a tab beside
 * the target's. The server reads and sends it, at most four times a second.
 *
 * <p>The gauge's bands are the target's own: for air, the HUD's warning and danger levels (CLIENT-008);
 * for a canister or tank, its window's; for a pipe network, half its burst pressure and the burst
 * (CONT-108). The values take the colour of the band they're in. The player can walk while it's open,
 * so a reading of where they are follows them ({@link WalkingInput}).
 */
public class AnalyzerScreen extends TguiScreen<AnalyzerMenu> {
    private static final int WIDTH = 216;
    private static final int GAUGE_RADIUS = CanisterScreen.GAUGE_RADIUS;
    private static final int READINGS_HEIGHT = 3 * Ui.ROW;

    /** CLIENT-008's pressure levels, kPa: a danger below 20 or from 550, a warning below 50 or above 325. */
    private static final double[] AIR_STOPS = {20, 50, 325, 550, 650};

    private static final int[] AIR_COLOURS = {Ui.BAD_FILL, Ui.AVERAGE, Ui.GOOD, Ui.AVERAGE, Ui.BAD_FILL};

    /** CLIENT-008's temperature levels, K: a danger outside 220–360, a warning below 240 or above 340. */
    private static final double[] AIR_TEMPERATURES = {220, 240, 340, 360};

    private static final double[] NETWORK_STOPS = {
        PipeBurst.THRESHOLD_KPA / 2, PipeBurst.THRESHOLD_KPA, PipeBurst.THRESHOLD_KPA * 1.25
    };
    private static final int[] CONTAINER_COLOURS = {Ui.GOOD, Ui.AVERAGE, Ui.BAD_FILL};

    public AnalyzerScreen(AnalyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, Ui.TITLE_HEIGHT + 2 * Ui.PAD + 2 + READINGS_HEIGHT + 2);
    }

    /** The game adds a screen before it sets the screen's {@code minecraft}, so these ask for it. */
    @Override
    public void added() {
        super.added();
        WalkingInput.start(Minecraft.getInstance());
    }

    @Override
    public void removed() {
        WalkingInput.stop(Minecraft.getInstance());
        super.removed();
    }

    private static int sectionHeight(AnalyzerSync s) {
        int gases = s == null ? 0 : s.gas().moles().size();
        return 2 + READINGS_HEIGHT + (gases > 0 ? 3 + GasParts.sharesHeight(gases) : 0) + 2;
    }

    private static int tabsHeight(AnalyzerSync s) {
        return s != null && s.tank().isPresent() ? 12 + Ui.PAD : 0;
    }

    @Override
    protected int windowHeight() {
        AnalyzerSync s = menu.reading();
        return Ui.TITLE_HEIGHT + 2 * Ui.PAD + tabsHeight(s) + sectionHeight(s);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        AnalyzerSync s = menu.reading();
        if (s == null) {
            return;
        }
        if (s.tank().isPresent()) {
            y += ui.tabs(
                            x,
                            y,
                            w,
                            List.of(s.target(), s.tank().get()),
                            s.showsTank() ? 1 : 0,
                            i -> press(i == 0 ? AnalyzerMenu.SHOW_TARGET : AnalyzerMenu.SHOW_TANK))
                    + Ui.PAD;
        }
        int height = sectionHeight(s);
        ui.section(x, y, w, height, null);
        int inner = x + Ui.PAD;
        double pressure = s.gas().pressure();
        double[] stops = stops(s.kind());
        int[] colours = s.kind().isAir() ? AIR_COLOURS : CONTAINER_COLOURS;
        ui.gauge(inner + GAUGE_RADIUS, y + 2 + GAUGE_RADIUS + 3, GAUGE_RADIUS, pressure, stops, colours);

        boolean unresolved = s.state() == AtmosphereState.UNRESOLVED;
        int labelX = inner + 2 * GAUGE_RADIUS + 6;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int row = y + 2;
        DeviceParts.label(ui, "target", labelX, row, Ui.ROW);
        target(ui, s, valueX, row, x + w - Ui.PAD);
        row += Ui.ROW;
        DeviceParts.label(ui, "pressure", labelX, row, Ui.ROW);
        ui.line(
                Component.literal(Readout.pressure(pressure)),
                valueX,
                row,
                Ui.ROW,
                unresolved ? Ui.DIM : text(band(pressure, stops, colours)),
                Face.BOLD);
        row += Ui.ROW;
        DeviceParts.label(ui, "temperature", labelX, row, Ui.ROW);
        double temperature = s.gas().temperature();
        ui.line(
                Component.literal(s.gas().isEmpty() ? "—" : Readout.temperature(temperature)),
                valueX,
                row,
                Ui.ROW,
                unresolved ? Ui.DIM : s.kind().isAir() ? temperatureColour(temperature) : Ui.WHITE,
                Face.BODY);
        row += Ui.ROW + 3;
        GasParts.shares(ui, inner, row, w - 2 * Ui.PAD, x + 1, w - 2, s.gas(), unresolved ? Ui.DIM : Ui.WHITE);
    }

    /** What it reads, and for air the zone it's in, cut off at the section's edge. */
    private void target(Ui ui, AnalyzerSync s, int x, int y, int right) {
        Component name = s.showsTank() ? s.tank().orElse(s.target()) : s.target();
        ui.clip(x, y, right - x, Ui.ROW);
        ui.line(name, x, y, Ui.ROW, Ui.WHITE, Face.BODY);
        if (s.kind().isAir() && s.state() != null) {
            Component where = Component.translatable("gui.zas_atmos.analyzer.where", air(s));
            ui.line(where, x + ui.width(name, Face.BODY), y, Ui.ROW, Ui.LABEL, Face.BODY);
        }
        ui.endClip();
    }

    /** The air's state: its zone, the open air, another mod's air for the place, or unresolved. */
    private static Component air(AnalyzerSync s) {
        if (s.local()) {
            return Component.translatable("gui.zas_atmos.analyzer.local_air");
        }
        return switch (s.state()) {
            case ZONED -> Component.translatable("gui.zas_atmos.analyzer.zone", s.zone());
            case PAUSED -> Component.translatable("gui.zas_atmos.analyzer.paused", s.zone());
            case AMBIENT -> Component.translatable("gui.zas_atmos.analyzer.open_air");
            case UNRESOLVED -> Component.translatable("gui.zas_atmos.analyzer.unresolved");
        };
    }

    private static double[] stops(AnalyzerSync.Kind kind) {
        return switch (kind) {
            case HERE, POINT -> AIR_STOPS;
            case NETWORK -> NETWORK_STOPS;
            case CANISTER -> CanisterScreen.GAUGE_STOPS;
            case TANK -> TankScreen.GAUGE_STOPS;
        };
    }

    /** The colour of the band a value is in, the top band's past the end. */
    private static int band(double value, double[] stops, int[] colours) {
        for (int i = 0; i < stops.length; i++) {
            if (value < stops[i]) {
                return colours[i];
            }
        }
        return colours[colours.length - 1];
    }

    /** A band's colour as text: white where it's fine, else its warning or danger colour. */
    private static int text(int band) {
        return band == Ui.GOOD ? Ui.WHITE : band == Ui.AVERAGE ? Ui.AVERAGE : Ui.BAD;
    }

    private static int temperatureColour(double kelvin) {
        if (kelvin < AIR_TEMPERATURES[0] || kelvin > AIR_TEMPERATURES[3]) {
            return Ui.BAD;
        }
        if (kelvin < AIR_TEMPERATURES[1] || kelvin > AIR_TEMPERATURES[2]) {
            return Ui.AVERAGE;
        }
        return Ui.WHITE;
    }
}
