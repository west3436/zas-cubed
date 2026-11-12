package io.github.west3436.zas.atmos.client.tgui;

import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.label.Labels;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The title bar of a window whose device takes a label. It shows the label with the device's kind
 * after it, dimmed, or the kind alone without one, and a pencil beside the close button. The pencil
 * turns the title into a line to type in: Enter, the pencil or a click anywhere else keeps what was
 * typed, Esc drops it, and keeping nothing clears the label.
 *
 * <p>What is kept goes to the server as text, which cleans it again and decides whether to take it.
 * The bar shows what {@link Labels#clean} makes of it here, which is the same.
 */
final class TitleLabel {
    private static final int BUTTON_WIDTH = 16;
    private static final int GAP = 5;
    private static final long BLINK_MILLIS = 500;

    private final Component kind;
    private final Consumer<String> send;
    private String label;

    /** What is being typed, or null while the title is only shown. */
    private @Nullable StringBuilder typed;

    private int caret;
    private char pendingHigh;
    private long touched;
    private int fieldX;
    private int fieldY;
    private int fieldWidth;

    /**
     * @param title the window's title as the server sent it: {@link Labels#title}
     * @param send takes a label to send to the server
     */
    TitleLabel(Component title, Consumer<String> send) {
        this.kind = Labels.kindIn(title);
        this.label = Labels.labelIn(title);
        this.send = send;
    }

    boolean editing() {
        return typed != null;
    }

    /** Draws the title, or the line being typed, and the pencil if the player may label the device. */
    void draw(Ui ui, int x, int y, int w, boolean mayEdit) {
        int left = x + Ui.TITLE_LEFT;
        int buttonX = x + w - Ui.CLOSE_WIDTH - BUTTON_WIDTH;
        int right = (mayEdit ? buttonX : x + w - Ui.CLOSE_WIDTH) - 2;
        if (!mayEdit) {
            typed = null;
        }
        if (typed != null) {
            fieldX = left - Ui.PAD;
            fieldY = y + 2;
            fieldWidth = right - fieldX;
            boolean caretShown = (Util.getMillis() - touched) / BLINK_MILLIS % 2 == 0;
            ui.field(
                    fieldX,
                    fieldY,
                    fieldWidth,
                    Ui.TITLE_HEIGHT - 4,
                    typed.toString(),
                    caret,
                    kind,
                    caretShown,
                    Face.TITLE);
            ui.editButton(buttonX, y, BUTTON_WIDTH, true, this::keep);
            return;
        }
        if (label.isEmpty()) {
            ui.title(ui.fit(kind, Face.TITLE, right - left), left, y, false);
        } else {
            Component name = ui.fit(Component.literal(label), Face.TITLE, right - left);
            ui.title(name, left, y, false);
            float after = left + ui.width(name, Face.TITLE) + GAP;
            if (after + ui.width(kind, Face.TITLE) <= right) {
                ui.title(kind, after, y, true);
            }
        }
        if (mayEdit) {
            ui.editButton(buttonX, y, BUTTON_WIDTH, false, this::edit);
            ui.tooltip(buttonX, y, BUTTON_WIDTH, Ui.TITLE_HEIGHT, Component.translatable("gui.zas_atmos.label"));
        }
    }

    /** Whether a point is on the line being typed. */
    boolean over(double mouseX, double mouseY) {
        return typed != null
                && mouseX >= fieldX
                && mouseX < fieldX + fieldWidth
                && mouseY >= fieldY
                && mouseY < fieldY + Ui.TITLE_HEIGHT - 4;
    }

    private void edit() {
        typed = new StringBuilder(label);
        caret = typed.length();
        pendingHigh = 0;
        touched = Util.getMillis();
    }

    /** Ends typing and keeps what was typed: sent to the server if it changes the label. */
    void keep() {
        if (typed == null) {
            return;
        }
        String kept = Labels.clean(typed.toString());
        typed = null;
        if (!kept.equals(label)) {
            label = kept;
            send.accept(kept);
        }
    }

    private void drop() {
        typed = null;
    }

    /** A key pressed while typing. */
    void key(int key, Supplier<String> clipboard) {
        if (typed == null) {
            return;
        }
        touched = Util.getMillis();
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> keep();
            case GLFW.GLFW_KEY_ESCAPE -> drop();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (caret > 0) {
                    int from = typed.offsetByCodePoints(caret, -1);
                    typed.delete(from, caret);
                    caret = from;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (caret < typed.length()) {
                    typed.delete(caret, typed.offsetByCodePoints(caret, 1));
                }
            }
            case GLFW.GLFW_KEY_LEFT -> caret = caret > 0 ? typed.offsetByCodePoints(caret, -1) : 0;
            case GLFW.GLFW_KEY_RIGHT ->
                caret = caret < typed.length() ? typed.offsetByCodePoints(caret, 1) : typed.length();
            case GLFW.GLFW_KEY_HOME -> caret = 0;
            case GLFW.GLFW_KEY_END -> caret = typed.length();
            default -> {
                if (Screen.isPaste(key)) {
                    insert(StringUtil.filterText(clipboard.get()));
                }
            }
        }
    }

    /** A character typed. One beyond the font's first plane arrives as two, and is kept or dropped whole. */
    void type(char c) {
        if (typed == null) {
            return;
        }
        touched = Util.getMillis();
        if (Character.isHighSurrogate(c)) {
            pendingHigh = c;
            return;
        }
        if (Character.isLowSurrogate(c)) {
            if (pendingHigh != 0) {
                insert(String.valueOf(new char[] {pendingHigh, c}));
            }
        } else if (StringUtil.isAllowedChatCharacter(c)) {
            insert(String.valueOf(c));
        }
        pendingHigh = 0;
    }

    /** Puts text in at the caret, as much of it as a label has room for. */
    private void insert(String text) {
        int room = Labels.MAX_LENGTH - typed.length();
        if (text.length() > room) {
            text = text.substring(0, Math.max(0, room));
            if (!text.isEmpty() && Character.isHighSurrogate(text.charAt(text.length() - 1))) {
                text = text.substring(0, text.length() - 1);
            }
        }
        typed.insert(caret, text);
        caret += text.length();
    }
}
