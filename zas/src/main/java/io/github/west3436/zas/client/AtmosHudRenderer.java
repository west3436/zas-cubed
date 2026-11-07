package io.github.west3436.zas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.config.ZasClientConfig;
import io.github.west3436.zas.config.ZasClientConfig.PressureUnit;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.ClientAtmosHud;
import io.github.west3436.zas.hud.HudLevels;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Draws the atmosphere HUD (CLIENT-001, CLIENT-008, CLIENT-009, CLIENT-014): one alert per reading
 * at warning or worse, in a column at the right edge of the screen, as vg's screen alerts are. Each
 * is vg's icon for that alert with the reading beside it, in the client's display units
 * (CLIENT-006): yellow for a warning, red for a danger. Above the column, where a full column
 * doesn't reach it and it doesn't move the alerts, is the internals indicator: vg's internals icon
 * with the tank's pressure beside it, if there is a tank.
 *
 * <p>Colour is never the only cue (CLIENT-014): low and high pressure and cold and heat have
 * different icons, and every danger icon flashes with vg's two-frame animation while warnings stay
 * still. Internals off, on and failing have different icons, and failing flashes. The HUD is hidden
 * while nothing shows it ({@link AtmosHudPayload#shown}), and with the rest of the GUI (F1).
 */
public final class AtmosHudRenderer {
    /** The layer's id. */
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "atmos_hud");

    private static final int ICON = 16;
    private static final int ROW = ICON + 2;
    private static final int MARGIN = 2;
    private static final int MAX_ALERTS = 4;
    private static final int WARNING_COLOUR = 0xFFFFFF55;
    private static final int DANGER_COLOUR = 0xFFFF5555;
    private static final int TANK_ON_COLOUR = 0xFFFFFFFF;
    private static final int TANK_OFF_COLOUR = 0xFFAAAAAA;

    /** One of vg's alert icons: a 32 × 32 texture per frame, frames stacked downward. */
    private enum Icon {
        PRESSURE_LOW_WARNING("pressure_low_warning", 1, 0),
        PRESSURE_LOW_DANGER("pressure_low_danger", 2, 500),
        PRESSURE_HIGH_WARNING("pressure_high_warning", 1, 0),
        PRESSURE_HIGH_DANGER("pressure_high_danger", 2, 500),
        OXYGEN("oxygen", 2, 500),
        TOXINS("toxins", 2, 500),
        COLD_WARNING("temperature_cold_warning", 1, 0),
        COLD_DANGER("temperature_cold_danger", 2, 300),
        HOT_WARNING("temperature_hot_warning", 1, 0),
        HOT_DANGER("temperature_hot_danger", 2, 300),
        INTERNALS_OFF("internals_off", 1, 0),
        INTERNALS_ON("internals_on", 1, 0),
        INTERNALS_FAILING("internals_failing", 2, 500);

        final ResourceLocation texture;
        final int frames;
        final long frameMillis;

        Icon(String name, int frames, long frameMillis) {
            this.texture = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "textures/gui/hud/" + name + ".png");
            this.frames = frames;
            this.frameMillis = frameMillis;
        }
    }

    /** One alert to draw: its icon, whether it flashes, and the reading beside it. */
    private record Alert(Icon icon, boolean danger, Component reading) {}

    private AtmosHudRenderer() {}

    /** Registers the HUD layer above the status effects. */
    static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EFFECTS, LAYER, AtmosHudRenderer::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        AtmosHudPayload hud = ClientAtmosHud.current();
        if (mc.options.hideGui || mc.player == null || !hud.shown()) {
            return;
        }
        List<Alert> alerts = alerts(hud);
        Font font = mc.font;
        int x = g.guiWidth() - MARGIN - ICON;
        int y = g.guiHeight() / 2 - alerts.size() * ROW / 2;
        long now = Util.getMillis();
        RenderSystem.enableBlend();
        for (Alert a : alerts) {
            int colour = a.danger() ? DANGER_COLOUR : WARNING_COLOUR;
            row(g, font, a.icon(), a.danger(), a.reading(), colour, x, y, now);
            y += ROW;
        }
        internals(g, font, hud, x, g.guiHeight() / 2 - MAX_ALERTS * ROW / 2 - ROW, now);
        RenderSystem.disableBlend();
    }

    /** One row: an icon, flashing or not, with a text to its left. */
    private static void row(
            GuiGraphics g, Font font, Icon icon, boolean flashing, Component text, int colour, int x, int y, long now) {
        int frame = flashing && icon.frames > 1 ? (int) ((now / icon.frameMillis) % icon.frames) : 0;
        g.blit(icon.texture, x, y, ICON, ICON, 0, frame * 32, 32, 32, 32, 32 * icon.frames);
        int width = font.width(text);
        g.drawString(font, text, x - 3 - width, y + (ICON - font.lineHeight) / 2 + 1, colour, true);
    }

    /** The internals indicator (CLIENT-009): the icon for what they do, and the tank's pressure if there is a tank. */
    private static void internals(GuiGraphics g, Font font, AtmosHudPayload hud, int x, int y, long now) {
        Component tank = hud.tankKpa() < 0
                ? Component.empty()
                : Component.translatable("hud.zas.tank", pressure(hud.tankKpa(), 0));
        switch (hud.internals()) {
            case AtmosHudPayload.INTERNALS_ON ->
                row(g, font, Icon.INTERNALS_ON, false, tank, TANK_ON_COLOUR, x, y, now);
            case AtmosHudPayload.INTERNALS_FAILING ->
                row(g, font, Icon.INTERNALS_FAILING, true, tank, DANGER_COLOUR, x, y, now);
            default -> row(g, font, Icon.INTERNALS_OFF, false, tank, TANK_OFF_COLOUR, x, y, now);
        }
    }

    /** The alerts to draw, top to bottom: pressure, O₂, toxins, temperature. */
    private static List<Alert> alerts(AtmosHudPayload hud) {
        List<Alert> alerts = new ArrayList<>(4);
        if (hud.pressure() != HudLevels.NONE) {
            boolean danger = Math.abs(hud.pressure()) == HudLevels.DANGER;
            Icon icon = hud.pressure() < 0
                    ? (danger ? Icon.PRESSURE_LOW_DANGER : Icon.PRESSURE_LOW_WARNING)
                    : (danger ? Icon.PRESSURE_HIGH_DANGER : Icon.PRESSURE_HIGH_WARNING);
            alerts.add(new Alert(
                    icon, danger, Component.translatable("hud.zas.pressure", pressure(hud.pressureKpa(), 0))));
        }
        if (hud.oxygen() != HudLevels.NONE) {
            alerts.add(new Alert(
                    Icon.OXYGEN,
                    hud.oxygen() == HudLevels.DANGER,
                    Component.translatable("hud.zas.oxygen", pressure(hud.oxygenKpa(), 1))));
        }
        if (hud.toxins() != HudLevels.NONE) {
            Component gas = hud.toxinGas()
                    .map(id -> (Component) Component.translatableWithFallback(
                            "gas." + id.getNamespace() + "." + id.getPath(), id.toString()))
                    .orElse(Component.empty());
            alerts.add(new Alert(
                    Icon.TOXINS,
                    hud.toxins() == HudLevels.DANGER,
                    Component.translatable("hud.zas.toxin", gas, pressure(hud.toxinKpa(), 1))));
        }
        if (hud.temperature() != HudLevels.NONE) {
            boolean danger = Math.abs(hud.temperature()) == HudLevels.DANGER;
            Icon icon = hud.temperature() < 0
                    ? (danger ? Icon.COLD_DANGER : Icon.COLD_WARNING)
                    : (danger ? Icon.HOT_DANGER : Icon.HOT_WARNING);
            alerts.add(new Alert(
                    icon, danger, Component.translatable("hud.zas.temperature", temperature(hud.temperatureK()))));
        }
        return alerts;
    }

    /**
     * A pressure in the display unit, to the HUD's resolution: 1 kPa for pressure, 0.1 kPa for a
     * partial pressure ({@code extra} = 1), with as many more decimals as the unit needs.
     */
    private static String pressure(double kPa, int extra) {
        PressureUnit unit = ZasClientConfig.pressureUnit();
        int decimals = extra
                + switch (unit) {
                    case KPA -> 0;
                    case PSI -> 1;
                    case ATM, BAR -> 2;
                };
        return String.format(Locale.ROOT, "%." + decimals + "f %s", unit.fromKpa(kPa), unit.symbol());
    }

    /** A temperature in the display unit, to the nearest degree. */
    private static String temperature(double kelvin) {
        ZasClientConfig.TemperatureUnit unit = ZasClientConfig.temperatureUnit();
        return String.format(Locale.ROOT, "%.0f %s", unit.fromKelvin(kelvin), unit.symbol());
    }
}
