package io.github.west3436.zas.atmos.client.tgui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Draws the TGUI look (D-76) in code, with no textures. A screen redraws itself from its state every
 * frame, as a TGUI window re-renders, and what can be clicked is whatever was drawn last frame.
 * Positions are in GUI pixels. One GUI pixel is two pixels of TGUI's own 12 px design, so a
 * {@linkplain Face#BODY body} line is 6 GUI pixels and a {@link #ROW row} is 10.
 *
 * <p>Text uses DejaVu Sans. Minecraft rasterises a TrueType font once for each definition, at its size
 * times its oversample, and draws it unfiltered, so text is sharp only where the oversample is the GUI
 * scale. The build writes a definition for each {@link Face} and each scale up to {@value
 * #MAX_OVERSAMPLE}, and glyphs DejaVu lacks fall back to Minecraft's own font.
 */
public final class Ui {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int DIM = 0x80FFFFFF;
    public static final int LABEL = 0xFF8093A6;
    public static final int GOOD = 0xFF5BA626;
    public static final int GOOD_EDGE = 0xFF4F9121;
    public static final int AVERAGE = 0xFFF2890D;
    public static final int BAD = 0xFFF05050;
    public static final int BAD_FILL = 0xFFDC2323;
    public static final int BAD_EDGE = 0xFFC41F1F;
    public static final int NOTICE = 0xFFBE9D67;
    public static final int NOTICE_DANGER = 0xFFA3201F;
    public static final int NOTICE_INFO = 0xFF40668C;
    public static final int TEAL = 0xFF00B3B3;
    public static final int TEAL_EDGE = 0xFF009999;
    public static final int ORANGE = 0xFFF26C0D;
    public static final int ORANGE_EDGE = 0xFFD95F0A;
    public static final int BLUE = 0xFF2A79C8;
    public static final int BLUE_EDGE = 0xFF2569AD;

    private static final int WINDOW_TOP = 0xFF2B2B2B;
    private static final int WINDOW_BOTTOM = 0xFF212121;
    private static final int WINDOW_EDGE = 0xB3000000;
    private static final int TITLE_BAR = 0xFF393939;
    private static final int TITLE_TEXT = 0xBFFFFFFF;
    private static final int SECTION = 0x54000000;
    private static final int PRIMARY = 0xFF48739E;
    private static final int STRIPE = 0x40000000;
    private static final int NOTICE_STRIPE = 0x1A000000;
    private static final int TAB_ON = 0x66606060;
    private static final int TAB_HOVER = 0x30808080;
    private static final int TAB_ON_TEXT = 0xFFD9E6F2;
    private static final int CELL = 0x4748739E;
    private static final int CELL_HOVER = 0x8048739E;
    private static final int CELL_OFF = 0x66FFFFFF;
    private static final int CELL_OFF_HOVER = 0x20FFFFFF;
    private static final int CELL_ON = 0xFF1B9745;
    private static final int TRACK = 0x14FFFFFF;
    private static final int TOOLTIP = 0xE6000000;
    private static final int SLOT = 0x80000000;
    private static final int SLOT_EDGE = 0x24FFFFFF;
    private static final int SLOT_LOCKED = 0x47788290;
    private static final int SLOT_LOCKED_EDGE = 0x4DFFFFFF;
    private static final int GHOST = 0xA0111111;
    private static final int SCROLL_TRACK = 0x14FFFFFF;
    private static final int SCROLL_THUMB = 0xFF6A6A6A;
    private static final int TITLE_DIM = 0x59FFFFFF;
    private static final int FIELD = 0xFF0A0A0A;
    private static final int FIELD_EDGE = 0xFF88BFFF;

    /** What ends text that was cut to fit. */
    private static final String ELLIPSIS = "…";

    /** The title bar's height. */
    public static final int TITLE_HEIGHT = 16;

    /** Where the title starts, from the window's left edge. */
    static final int TITLE_LEFT = 22;

    /** The close button's width. */
    static final int CLOSE_WIDTH = 20;

    /** The padding inside the window, and the gap between its sections. */
    public static final int PAD = 3;

    /** A line of text in a list or a table. */
    public static final int ROW = 10;

    /** A row holding a button. */
    public static final int BUTTON_ROW = 11;

    /** A button in such a row. */
    public static final int BUTTON_HEIGHT = 10;

    /** A notice of one line. */
    public static final int NOTICE_HEIGHT = 11;

    private static final int MAX_OVERSAMPLE = 9;
    private static final float CORNER = 0.5f;
    private static final float HAIRLINE = 0.5f;
    private static final int BASELINE = 7;

    /** A font and weight. */
    public enum Face {
        BODY("regular_6", 6),
        BOLD("bold_6", 6),
        TITLE("regular_7", 7),
        HEADING("bold_7", 7),
        ICON("regular_8", 8);

        private static final float CAP_HEIGHT = 0.729f;

        private final String definition;
        private final float capHeight;

        Face(String definition, int size) {
            this.definition = definition;
            this.capHeight = size * CAP_HEIGHT;
        }
    }

    /** A button's colours. */
    public enum Tone {
        DEFAULT(0xFF40668C, 0xFF48739E),
        SELECTED(0xFF1B9745, 0xFF1FAD4E),
        CAUTION(0xFFC29B08, 0xFFD8AD06),
        DANGER(0xFFC41F1F, 0xFFDC2323);

        private final int colour;
        private final int hover;

        Tone(int colour, int hover) {
            this.colour = colour;
            this.hover = hover;
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private final Style[] styles = new Style[Face.values().length];
    private GuiGraphics g;
    private Font font;
    private int mouseX;
    private int mouseY;
    private int guiScale;
    private int stylesFor;
    private Component tooltip;
    private Object armed;

    /** Starts a frame. */
    void begin(GuiGraphics graphics, Font font, int mouseX, int mouseY, int guiScale) {
        this.g = graphics;
        this.font = font;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.guiScale = Math.max(1, guiScale);
        hits.clear();
        tooltip = null;
        int oversample = Math.min(this.guiScale, MAX_OVERSAMPLE);
        if (oversample != stylesFor) {
            stylesFor = oversample;
            for (Face face : Face.values()) {
                styles[face.ordinal()] =
                        Style.EMPTY.withFont(ZasAtmosMod.id("tgui/" + face.definition + "_x" + oversample));
            }
        }
    }

    /** Runs what was clicked last frame, if anything was. */
    boolean click(double x, double y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (x >= hit.x && x < hit.x + hit.w && y >= hit.y && y < hit.y + hit.h) {
                hit.action.run();
                return true;
            }
        }
        return false;
    }

    private boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ---- Text -------------------------------------------------------------------------------

    private MutableComponent styled(Component text, Face face) {
        return text.copy().withStyle(styles[face.ordinal()]);
    }

    /** A warning's icon before a word, large enough that its shape reads at GUI scale 2 (CLIENT-014). */
    public Component alert(String icon, Component word) {
        MutableComponent glyph = Component.literal(icon).withStyle(styles[Face.ICON.ordinal()]);
        return Component.empty().append(glyph).append(" ").append(word);
    }

    /** How wide a piece of text is. */
    public float width(Component text, Face face) {
        return font.getSplitter().stringWidth(styled(text, face));
    }

    /**
     * Text cut to fit a width, ending in an ellipsis where it is cut: a name a player chose can be
     * longer than its column. Text that fits comes back as it is.
     */
    public Component fit(Component text, Face face, float width) {
        if (width(text, face) <= width) {
            return text;
        }
        String whole = text.getString();
        for (int n = whole.length() - 1; n > 0; n--) {
            if (Character.isHighSurrogate(whole.charAt(n - 1))) {
                continue;
            }
            Component cut = Component.literal(whole.substring(0, n).stripTrailing() + ELLIPSIS)
                    .withStyle(text.getStyle());
            if (width(cut, face) <= width) {
                return cut;
            }
        }
        return Component.literal(ELLIPSIS);
    }

    /** Text starting at {@code x}, centred in a line {@code height} tall. */
    public void line(Component text, float x, int top, int height, int colour, Face face) {
        draw(styled(text, face).getVisualOrderText(), x, top, height, colour, face);
    }

    /** Text ending at {@code right}, centred in a line {@code height} tall. */
    public void lineRight(Component text, float right, int top, int height, int colour, Face face) {
        line(text, right - width(text, face), top, height, colour, face);
    }

    /** Text centred in a box. */
    public void lineCentred(Component text, int x, int w, int top, int height, int colour, Face face) {
        line(text, x + (w - width(text, face)) / 2, top, height, colour, face);
    }

    /** Text wrapped to a width, in rows of {@code rowHeight}, starting at {@code top}. */
    public void wrapped(Component text, int x, int top, int w, int rowHeight, int colour, Face face) {
        for (FormattedCharSequence part : font.split(styled(text, face), w)) {
            draw(part, x, top, rowHeight, colour, face);
            top += rowHeight;
        }
    }

    private void draw(FormattedCharSequence text, float x, int top, int height, int colour, Face face) {
        // The baseline is 7 down from where text is drawn, whatever the font's size.
        float y = top + height / 2f + face.capHeight / 2 - BASELINE;
        g.drawString(font, text, snap(x), snap(y), colour, false);
    }

    /** The nearest screen pixel, which a glyph's texels must land on to stay sharp. */
    private float snap(float v) {
        return (float) Math.round(v * guiScale) / guiScale;
    }

    // ---- Shapes -----------------------------------------------------------------------------

    /** A filled box. */
    public void fill(int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + h, colour);
    }

    /** A filled box whose edges needn't fall on GUI pixels. */
    public void box(float x, float y, float w, float h, int colour) {
        addBox(x, y, w, h, colour);
        g.flush();
    }

    private void addBox(float x, float y, float w, float h, int colour) {
        quad(x, y, x, y + h, x + w, y + h, x + w, y, colour);
    }

    /** A filled box with its corners taken off, as TGUI's buttons have. */
    public void rounded(int x, int y, int w, int h, int colour) {
        addBox(x + CORNER, y, w - 2 * CORNER, CORNER, colour);
        addBox(x, y + CORNER, w, h - 2 * CORNER, colour);
        addBox(x + CORNER, y + h - CORNER, w - 2 * CORNER, CORNER, colour);
        g.flush();
    }

    private void outline(int x, int y, int w, int h, int colour) {
        addBox(x, y, w, HAIRLINE, colour);
        addBox(x, y + h - HAIRLINE, w, HAIRLINE, colour);
        addBox(x, y + HAIRLINE, HAIRLINE, h - 2 * HAIRLINE, colour);
        addBox(x + w - HAIRLINE, y + HAIRLINE, HAIRLINE, h - 2 * HAIRLINE, colour);
        g.flush();
    }

    /** Four corners in the order {@link GuiGraphics#fill} gives them: down the left, up the right. */
    private void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int colour) {
        Matrix4f pose = g.pose().last().pose();
        VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
        buffer.addVertex(pose, x0, y0, 0).setColor(colour);
        buffer.addVertex(pose, x1, y1, 0).setColor(colour);
        buffer.addVertex(pose, x2, y2, 0).setColor(colour);
        buffer.addVertex(pose, x3, y3, 0).setColor(colour);
    }

    /** A shape symmetrical about a line, {@code halfHeight} tall at each distance along it. */
    private void strip(float cx, float cy, float halfWidth, DoubleUnaryOperator halfHeight, int colour) {
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            float x0 = -halfWidth + 2 * halfWidth * i / segments;
            float x1 = -halfWidth + 2 * halfWidth * (i + 1) / segments;
            float h0 = (float) halfHeight.applyAsDouble(x0);
            float h1 = (float) halfHeight.applyAsDouble(x1);
            quad(cx + x0, cy - h0, cx + x0, cy + h0, cx + x1, cy + h1, cx + x1, cy - h1, colour);
        }
    }

    private void disc(float cx, float cy, float radius, int colour) {
        strip(cx, cy, radius, x -> Math.sqrt(Math.max(0, radius * radius - x * x)), colour);
    }

    /** TGUI's status eye: an almond with a ring and a pupil. */
    private void eye(float cx, float cy, int colour) {
        float halfWidth = 6;
        strip(cx, cy, halfWidth, x -> 3.8 * Math.cos(x / halfWidth * Math.PI / 2), colour);
        disc(cx, cy, 2.7f, TITLE_BAR);
        disc(cx, cy, 1.6f, colour);
        g.flush();
    }

    /** A quad whose corners can run either way round: the game culls the ones that run against {@link #quad}'s. */
    private void anyQuad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int colour) {
        if ((x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0) > 0) {
            quad(x3, y3, x2, y2, x1, y1, x0, y0, colour);
        } else {
            quad(x0, y0, x1, y1, x2, y2, x3, y3, colour);
        }
    }

    /** A line from one point to another. */
    private void addLine(float x0, float y0, float x1, float y1, float thickness, int colour) {
        double length = Math.max(1e-3, Math.hypot(x1 - x0, y1 - y0));
        float nx = (float) (-(y1 - y0) / length * thickness / 2);
        float ny = (float) ((x1 - x0) / length * thickness / 2);
        anyQuad(x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, colour);
    }

    /** An arrow from one point to another. */
    public void arrow(float x0, float y0, float x1, float y1, int colour) {
        double length = Math.max(1e-3, Math.hypot(x1 - x0, y1 - y0));
        float ux = (float) ((x1 - x0) / length);
        float uy = (float) ((y1 - y0) / length);
        float headLength = 3.5f;
        float headWidth = 2.5f;
        float baseX = x1 - ux * headLength;
        float baseY = y1 - uy * headLength;
        addLine(x0, y0, baseX, baseY, 1, colour);
        anyQuad(
                baseX - uy * headWidth,
                baseY + ux * headWidth,
                baseX + uy * headWidth,
                baseY - ux * headWidth,
                x1,
                y1,
                x1,
                y1,
                colour);
        g.flush();
    }

    /** A padlock, 5 wide and 6 tall. */
    public void lock(float x, float y, int colour) {
        float leg = 0.75f;
        addBox(x + 1, y, 3, leg, colour);
        addBox(x + 1, y, leg, 2.5f, colour);
        addBox(x + 4 - leg, y, leg, 2.5f, colour);
        addBox(x, y + 2.5f, 5, 3.5f, colour);
        g.flush();
    }

    /**
     * A half-round gauge with a needle: bands of colour from 0 up to each stop.
     *
     * @param cx the middle of its flat side
     * @param cy the same
     * @param radius how far its arc reaches
     * @param value where the needle points
     * @param stops where each band ends, ascending; the last is the top of the scale
     * @param colours each band's colour
     */
    public void gauge(float cx, float cy, float radius, double value, double[] stops, int[] colours) {
        double top = stops[stops.length - 1];
        double from = 0;
        for (int i = 0; i < stops.length; i++) {
            arc(cx, cy, radius - 4, radius, from / top, stops[i] / top, colours[i]);
            from = stops[i];
        }
        double angle = Math.PI * (1 - Math.max(0, Math.min(1, value / top)));
        float reach = radius - 2;
        addLine(cx, cy, (float) (cx + reach * Math.cos(angle)), (float) (cy - reach * Math.sin(angle)), 1, WHITE);
        disc(cx, cy, 1.75f, WHITE);
        g.flush();
    }

    private void arc(float cx, float cy, float inner, float outer, double from, double to, int colour) {
        int segments = Math.max(1, (int) Math.ceil(32 * (to - from)));
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * (1 - (from + (to - from) * i / segments));
            double a1 = Math.PI * (1 - (from + (to - from) * (i + 1) / segments));
            anyQuad(
                    (float) (cx + inner * Math.cos(a0)),
                    (float) (cy - inner * Math.sin(a0)),
                    (float) (cx + outer * Math.cos(a0)),
                    (float) (cy - outer * Math.sin(a0)),
                    (float) (cx + outer * Math.cos(a1)),
                    (float) (cy - outer * Math.sin(a1)),
                    (float) (cx + inner * Math.cos(a1)),
                    (float) (cy - inner * Math.sin(a1)),
                    colour);
        }
    }

    /** Clips what's drawn until {@link #endClip} to a box. */
    public void clip(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    /** Ends {@link #clip}. */
    public void endClip() {
        g.disableScissor();
    }

    // ---- Parts of a window ------------------------------------------------------------------

    /** The window, its title bar and the close button. The title bar's eye shows who can act. */
    void window(int x, int y, int w, int h, Component title, int eye, Runnable onClose) {
        frame(x, y, w, h, eye, onClose);
        title(title, x + TITLE_LEFT, y, false);
    }

    /** The same with no title, for a window that draws its own in the bar. */
    void frame(int x, int y, int w, int h, int eye, Runnable onClose) {
        fill(x - 1, y - 1, w + 2, h + 2, WINDOW_EDGE);
        g.fillGradient(x, y, x + w, y + h, WINDOW_TOP, WINDOW_BOTTOM);
        fill(x, y, w, TITLE_HEIGHT, TITLE_BAR);
        box(x, y + TITLE_HEIGHT - HAIRLINE, w, HAIRLINE, SECTION);
        eye(x + 11, y + TITLE_HEIGHT / 2f, eye);
        titleButton(x + w - CLOSE_WIDTH, y, CLOSE_WIDTH, Component.literal("✕"), false, onClose);
    }

    /** A title in the title bar, starting at {@code x}: the window's own, or dimmer, what follows it. */
    void title(Component text, float x, int y, boolean dim) {
        line(text, x, y, TITLE_HEIGHT, dim ? TITLE_DIM : TITLE_TEXT, Face.TITLE);
    }

    /** A button in the title bar, as the close button is: a mark that lights under the mouse, or while it is on. */
    void titleButton(int x, int y, int w, Component mark, boolean on, Runnable onClick) {
        lineCentred(mark, x, w, y, TITLE_HEIGHT, titleButton(x, y, w, on, onClick), Face.BODY);
    }

    /** The same with a pencil for its mark: the button that turns the title into a line to type in. */
    void editButton(int x, int y, int w, boolean on, Runnable onClick) {
        pencil(x + w / 2f, y + TITLE_HEIGHT / 2f, titleButton(x, y, w, on, onClick));
    }

    /** A title-bar button's ground and where it is clicked. Returns the colour its mark is drawn in. */
    private int titleButton(int x, int y, int w, boolean on, Runnable onClick) {
        boolean over = hovered(x, y, w, TITLE_HEIGHT);
        if (over || on) {
            fill(x, y, w, TITLE_HEIGHT, TAB_HOVER);
        }
        hits.add(new Hit(x, y, w, TITLE_HEIGHT, onClick));
        return over || on ? WHITE : DIM;
    }

    /**
     * A pencil about a point, its tip down to the left, beside the line it writes: 8 wide and 9 tall.
     * A font's pencil is too fine to read at this size.
     */
    private void pencil(float cx, float cy, int colour) {
        float half = 0.9f; // half the pencil's width, along each axis
        float neckX = cx - 1.5f;
        float neckY = cy + 0.5f;
        // The tip, the wood behind it up to the band, and the end past the band.
        anyQuad(
                cx - 3.5f,
                cy + 2.5f,
                neckX - half,
                neckY - half,
                neckX + half,
                neckY + half,
                neckX + half,
                neckY + half,
                colour);
        addLine(neckX, neckY, cx + 1.6f, cy - 2.6f, 2 * half * 1.414f, colour);
        addLine(cx + 2.1f, cy - 3.1f, cx + 3.0f, cy - 4.0f, 2 * half * 1.414f, colour);
        addBox(cx - 1, cy + 3, 5, 1, colour);
        g.flush();
    }

    /**
     * A line being typed, as TGUI's input: a dark box with a lit edge, the text, and a caret. Text too
     * long for the box moves along so the caret stays in it. With nothing typed it shows what the line
     * stands for, dimmed.
     *
     * @param caret how many characters of the text come before the caret
     * @param placeholder what an empty line shows
     * @param caretShown whether the caret is in the lit half of its blink
     */
    void field(
            int x, int y, int w, int h, String text, int caret, Component placeholder, boolean caretShown, Face face) {
        fill(x, y, w, h, FIELD);
        outline(x, y, w, h, FIELD_EDGE);
        int inside = w - 2 * PAD;
        float before = width(Component.literal(text.substring(0, caret)), face);
        float along = Math.max(0, before - inside);
        clip(x + 1, y, w - 2, h);
        if (text.isEmpty()) {
            line(fit(placeholder, face, inside), x + PAD, y, h, CELL_OFF, face);
        } else {
            line(Component.literal(text), x + PAD - along, y, h, WHITE, face);
        }
        if (caretShown) {
            box(x + PAD + before - along, y + 2, HAIRLINE, h - 4, WHITE);
        }
        endClip();
    }

    /**
     * A section's background and, if it has one, its title and rule.
     *
     * @return where its content starts
     */
    public int section(int x, int y, int w, int h, Component title) {
        fill(x, y, w, h, SECTION);
        if (title == null) {
            return y;
        }
        int titleHeight = 13;
        line(title, x + PAD, y, titleHeight, WHITE, Face.HEADING);
        fill(x, y + titleHeight, w, 1, PRIMARY);
        return y + titleHeight + 1;
    }

    /**
     * A striped notice in bold italics, wrapped to its width.
     *
     * @return its height
     */
    public int notice(int x, int y, int w, int colour, int textColour, Component text) {
        return notice(x, y, w, colour, textColour, text, 0);
    }

    /**
     * The same, with its text moved along to leave room for a mark at its start.
     *
     * @return its height
     */
    public int notice(int x, int y, int w, int colour, int textColour, Component text, int indent) {
        Component italic = text.copy().withStyle(s -> s.withItalic(true));
        int rows = font.split(styled(italic, Face.BOLD), w - 2 * PAD - indent).size();
        int rowHeight = 9;
        int h = rows * rowHeight + 2;
        fill(x, y, w, h, colour);
        clip(x, y, w, h);
        float band = 7.07f;
        for (float sx = x - h; sx < x + w; sx += 2 * band) {
            quad(sx + h, y, sx, y + h, sx + band, y + h, sx + band + h, y, NOTICE_STRIPE);
        }
        g.flush();
        endClip();
        wrapped(italic, x + PAD + indent, y + 1, w - 2 * PAD - indent, rowHeight, textColour, Face.BOLD);
        return h;
    }

    /** How wide a button needs to be for a label. */
    public int buttonWidth(Component label) {
        return (int) Math.ceil(width(label, Face.BODY)) + 8;
    }

    /** A button. One with no action is greyed out. */
    public void button(int x, int y, int w, int h, Component label, Tone tone, Runnable onClick) {
        boolean enabled = onClick != null;
        boolean over = enabled && hovered(x, y, w, h);
        int colour = over ? tone.hover : tone.colour;
        rounded(x, y, w, h, enabled ? colour : half(colour));
        lineCentred(label, x, w, y, h, enabled ? WHITE : DIM, Face.BODY);
        if (enabled) {
            hits.add(new Hit(x, y, w, h, onClick));
        }
    }

    private static int half(int colour) {
        return colour & 0x00FFFFFF | 0x80000000;
    }

    /**
     * A button that asks for a second click, as TGUI's confirm button does: it arms on the first click
     * and fires on the second, and moving the mouse off it disarms it. With no action it's greyed out.
     */
    public void confirm(
            int x, int y, int w, int h, Component label, Component confirmLabel, Tone tone, Runnable onClick) {
        Object key = label.getString();
        if (onClick == null || !hovered(x, y, w, h)) {
            if (key.equals(armed)) {
                armed = null;
            }
        }
        if (onClick == null) {
            button(x, y, w, h, label, tone, null);
        } else if (key.equals(armed)) {
            button(x, y, w, h, confirmLabel, Tone.DANGER, () -> {
                armed = null;
                onClick.run();
            });
        } else {
            button(x, y, w, h, label, tone, () -> armed = key);
        }
    }

    /**
     * A row of buttons of which one is the current choice. Clicking the current one does nothing. With
     * no action they're all greyed out.
     *
     * @param caution the button drawn in {@link Tone#CAUTION} until it's chosen, or -1
     * @return where the row ends
     */
    public int choice(int x, int y, List<Component> labels, int selected, int caution, IntConsumer onSelect) {
        for (int i = 0; i < labels.size(); i++) {
            Component label = labels.get(i);
            int w = buttonWidth(label);
            int choice = i;
            Tone tone = i == selected ? Tone.SELECTED : i == caution ? Tone.CAUTION : Tone.DEFAULT;
            Runnable action = onSelect == null ? null : i == selected ? () -> {} : () -> onSelect.accept(choice);
            button(x, y, w, BUTTON_HEIGHT, label, tone, action);
            x += w + 1;
        }
        return x - 1;
    }

    /**
     * Buttons either side of a value, with a default button after them if it has one. The steps are
     * half down and half up, and a tooltip gives each step's size in the unit the server applies, which
     * the value's own unit needn't be. With no action a part is greyed out.
     *
     * @param steps each step's label, the lowest first
     * @param unit what each step is in
     * @param onStep given a step's index
     * @param defaultLabel the default button's label, or null for none
     */
    public void stepper(
            int x,
            int y,
            int w,
            Component value,
            int colour,
            String[] steps,
            String unit,
            IntConsumer onStep,
            Component defaultLabel,
            Runnable onDefault) {
        int left = x;
        int right = x + w;
        if (defaultLabel != null) {
            int defaultWidth = buttonWidth(defaultLabel) - 2;
            right -= defaultWidth;
            button(right, y, defaultWidth, BUTTON_HEIGHT, defaultLabel, Tone.DEFAULT, onDefault);
            right -= 2;
        }
        for (int i = steps.length - 1; i >= steps.length / 2; i--) {
            right -= step(right, y, steps[i], unit, i, onStep, true) + 1;
        }
        for (int i = 0; i < steps.length / 2; i++) {
            left += step(left, y, steps[i], unit, i, onStep, false) + 1;
        }
        lineCentred(value, left, right + 1 - left, y, BUTTON_HEIGHT, colour, Face.BOLD);
    }

    /** One of a stepper's buttons, starting at {@code x} or, if {@code endsThere}, ending there. */
    private int step(int x, int y, String label, String unit, int index, IntConsumer onStep, boolean endsThere) {
        Component text = stepLabel(label);
        int w = (int) Math.ceil(width(text, Face.BODY)) + 5;
        int at = endsThere ? x - w : x;
        button(at, y, w, BUTTON_HEIGHT, text, Tone.DEFAULT, onStep == null ? null : () -> onStep.accept(index));
        tooltip(at, y, w, BUTTON_HEIGHT, Component.literal(label + " " + unit));
        return w;
    }

    /** A step's label. Its sign is bold, as a thin "+" loses its stem at GUI scale 2 and reads as a "−". */
    public Component stepLabel(String label) {
        return Component.empty()
                .append(Component.literal(label.substring(0, 1)).withStyle(styles[Face.BOLD.ordinal()]))
                .append(label.substring(1));
    }

    /** A box and its label, which click together. With no action it's greyed out. */
    public void checkbox(int x, int y, int w, int h, Component label, boolean on, Runnable onClick) {
        boolean enabled = onClick != null;
        int size = 7;
        int boxY = y + (h - size) / 2;
        if (on) {
            rounded(x, boxY, size, size, enabled ? Tone.SELECTED.colour : half(Tone.SELECTED.colour));
            lineCentred(Component.literal("✔"), x, size, boxY, size, enabled ? WHITE : DIM, Face.BODY);
        } else {
            rounded(x, boxY, size, size, enabled && hovered(x, y, w, h) ? CELL_HOVER : CELL);
        }
        line(label, x + size + 3, y, h, enabled ? WHITE : DIM, Face.BODY);
        if (enabled) {
            hits.add(new Hit(x, y, w, h, onClick));
        }
    }

    /**
     * A row of tabs.
     *
     * @return their height
     */
    public int tabs(int x, int y, int w, List<Component> labels, int selected, IntConsumer onSelect) {
        int h = 12;
        fill(x, y, w, h, SECTION);
        int tabX = x + 1;
        for (int i = 0; i < labels.size(); i++) {
            Component label = labels.get(i);
            int tabWidth = Math.max(24, (int) Math.ceil(width(label, Face.BODY)) + 10);
            boolean on = i == selected;
            if (on) {
                fill(tabX, y + 1, tabWidth, h - 1, TAB_ON);
                fill(tabX, y + h - 1, tabWidth, 1, TAB_ON_TEXT);
            } else if (hovered(tabX, y + 1, tabWidth, h - 1)) {
                fill(tabX, y + 1, tabWidth, h - 1, TAB_HOVER);
            }
            lineCentred(label, tabX, tabWidth, y + 1, h - 2, on ? TAB_ON_TEXT : DIM, Face.BODY);
            int tab = i;
            hits.add(new Hit(tabX, y + 1, tabWidth, h - 1, () -> onSelect.accept(tab)));
            tabX += tabWidth + 1;
        }
        return h;
    }

    /** A progress bar with its text at the right. */
    public void bar(int x, int y, int w, int h, double fraction, int colour, int edge, Component label) {
        double full = Math.max(0, Math.min(1, fraction));
        box(x, y, (float) (w * full), h, colour);
        outline(x, y, w, h, edge);
        lineRight(label, x + w - PAD, y, h, WHITE, Face.BODY);
    }

    /** A bar split in two, each share in its own colour with its own label at its end. */
    public void split(
            int x, int y, int w, int h, double left, int leftColour, int rightColour, Component a, Component b) {
        float split = (float) (w * Math.max(0, Math.min(1, left)));
        box(x, y, split, h, leftColour);
        box(x + split, y, w - split, h, rightColour);
        line(a, x + PAD, y, h, WHITE, Face.BODY);
        lineRight(b, x + w - PAD, y, h, WHITE, Face.BODY);
    }

    /** An item slot's frame, for an item drawn at (x, y). */
    public void slot(int x, int y) {
        fill(x - 1, y - 1, 18, 18, SLOT);
        outline(x - 1, y - 1, 18, 18, SLOT_EDGE);
    }

    /** The frame of a slot whose item can't be moved. */
    public void lockedSlot(int x, int y) {
        fill(x - 1, y - 1, 18, 18, SLOT_LOCKED);
        outline(x - 1, y - 1, 18, 18, SLOT_LOCKED_EDGE);
    }

    /** A faint picture of what an empty slot takes. */
    public void ghost(int x, int y, ItemStack item) {
        g.renderFakeItem(item, x, y);
        g.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, GHOST);
    }

    /**
     * A scrollbar.
     *
     * @param x its left edge
     * @param y its top
     * @param h its height
     * @param scroll how far the list has scrolled
     * @param maxScroll how far it can
     * @param fullHeight the whole list's height
     */
    public void scrollbar(int x, int y, int h, int scroll, int maxScroll, int fullHeight) {
        if (maxScroll <= 0) {
            return;
        }
        int thumb = Math.max(8, h * h / fullHeight);
        fill(x, y, 2, h, SCROLL_TRACK);
        fill(x, y + (h - thumb) * scroll / maxScroll, 2, thumb, SCROLL_THUMB);
    }

    /** A dark stripe behind every other row of a table. */
    public void stripe(int x, int y, int w, int h) {
        fill(x, y, w, h, STRIPE);
    }

    /** A cell of a table that can be picked for editing. */
    public void cell(int x, int y, int w, int h, Component text, boolean off, boolean selected, Runnable onClick) {
        boolean over = hovered(x, y, w, h);
        if (selected) {
            rounded(x, y, w, h, CELL_ON);
            outline(x, y, w, h, WHITE);
        } else if (off) {
            if (over) {
                rounded(x, y, w, h, CELL_OFF_HOVER);
            }
        } else {
            rounded(x, y, w, h, over ? CELL_HOVER : CELL);
        }
        lineCentred(text, x, w, y, h, off && !selected ? CELL_OFF : WHITE, Face.BODY);
        hits.add(new Hit(x, y, w, h, onClick));
    }

    /** A small coloured square, as beside a gas's name. */
    public void dot(int x, int y, int colour) {
        fill(x, y, 4, 4, colour);
    }

    /** The track behind a bar made of several colours. */
    public void track(int x, int y, int w, int h) {
        rounded(x, y, w, h, TRACK);
    }

    /** Shows a tooltip this frame while the mouse is over a box. */
    public void tooltip(int x, int y, int w, int h, Component text) {
        if (hovered(x, y, w, h)) {
            tooltip = text;
        }
    }

    /** Draws this frame's tooltip, over everything. */
    void drawTooltip(int screenWidth) {
        if (tooltip == null) {
            return;
        }
        int w = (int) Math.ceil(width(tooltip, Face.BODY)) + 6;
        int h = ROW + 1;
        int x = Math.min(mouseX + 6, screenWidth - w - 1);
        int y = Math.max(1, mouseY - h - 2);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        fill(x, y, w, h, TOOLTIP);
        line(tooltip, x + 3, y, h, WHITE, Face.BODY);
        g.pose().popPose();
    }
}
