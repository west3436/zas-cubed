package io.github.west3436.zas.compat.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Where everything on a page goes, in pixels from the page's top left corner. Both viewers place
 * their slots and draw the rest from one of these, so the pages match. It measures text through a
 * function it is given, so it needs no font and a test can lay a page out.
 *
 * <p>A reaction's page reads left to right: what it takes, an arrow, what it gives, with catalysts
 * under the arrow and the conditions, rate and heat in rows of chips below. A gas's page has the
 * gas, its name and formula, then its properties as chips.
 */
public final class ViewerLayout {
    /** Every page's width, which fits both viewers' panels. */
    public static final int WIDTH = 144;

    /** A slot's width and height, border included; the gas is drawn 1 pixel in. */
    public static final int SLOT = 18;

    /** The space kept for the viewer's own arrow, which is drawn centred in it. */
    public static final int ARROW_WIDTH = 24;

    public static final int ARROW_HEIGHT = 17;

    /** Dark grey, as a vanilla window's labels. */
    public static final int TEXT_COLOUR = 0x404040;

    /** A lighter grey, for what matters less. */
    public static final int FAINT_COLOUR = 0x6A6A6A;

    private static final int SLOTS_PER_SIDE = 3;
    private static final int ARROW_GAP = 4;
    private static final int CHIP_GAP = 6;
    /** The space between a chip's icon and its text. */
    public static final int ICON_GAP = 2;

    private static final int LINE = 11;

    private ViewerLayout() {}

    /** What a slot is to the reaction. */
    public enum Role {
        INPUT,
        OUTPUT,
        CATALYST
    }

    /** A slot, by its top left corner, border included. */
    public record Slot(int x, int y, GasStack stack, Role role) {}

    /** A line of text, by its top left corner. */
    public record Text(int x, int y, Line line, int colour) {}

    /** A chip: its icon's top left corner and its whole width, text included. */
    public record PlacedChip(int x, int y, int width, Chip chip) {
        /** Whether a point is on the chip. */
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + Icon.SIZE;
        }
    }

    /**
     * A page.
     *
     * @param arrowX the left edge of the arrow's space, or -1 on a page without one
     * @param arrowY the top edge of the arrow's space
     */
    public record Page(
            int width, int height, List<Slot> slots, int arrowX, int arrowY, List<Text> texts, List<PlacedChip> chips) {
        public Page {
            slots = List.copyOf(slots);
            texts = List.copyOf(texts);
            chips = List.copyOf(chips);
        }

        /** The name of the chip under a point, if one is. */
        public Optional<Line> hintAt(double px, double py) {
            for (PlacedChip chip : chips) {
                if (chip.contains(px, py)) {
                    return Optional.of(chip.chip().hint());
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Lays out a reaction.
     *
     * @param textWidth the width of a line of text in pixels
     */
    public static Page reaction(ReactionEntry entry, ToIntFunction<Line> textWidth) {
        int arrowX = (WIDTH - ARROW_WIDTH) / 2;
        int left = arrowX - ARROW_GAP;
        int right = arrowX + ARROW_WIDTH + ARROW_GAP;
        List<Slot> slots = new ArrayList<>();
        // What it takes ends at the arrow, and what it gives starts there, however many there are.
        int rows = Math.max(
                1,
                Math.max(rows(entry.reactants().size()), rows(entry.products().size())));
        for (int i = 0; i < entry.reactants().size(); i++) {
            int inRow = Math.min(SLOTS_PER_SIDE, entry.reactants().size() - i / SLOTS_PER_SIDE * SLOTS_PER_SIDE);
            int x = left - (inRow - i % SLOTS_PER_SIDE) * SLOT;
            slots.add(new Slot(x, i / SLOTS_PER_SIDE * SLOT, entry.reactants().get(i), Role.INPUT));
        }
        for (int i = 0; i < entry.products().size(); i++) {
            int x = right + i % SLOTS_PER_SIDE * SLOT;
            slots.add(new Slot(x, i / SLOTS_PER_SIDE * SLOT, entry.products().get(i), Role.OUTPUT));
        }
        int y = rows * SLOT;
        int catalysts = entry.catalysts().size();
        if (catalysts > 0) {
            y += 1;
            int perRow = WIDTH / SLOT;
            for (int i = 0; i < catalysts; i++) {
                int inRow = Math.min(perRow, catalysts - i / perRow * perRow);
                int x = (WIDTH - inRow * SLOT) / 2 + i % perRow * SLOT;
                slots.add(new Slot(x, y + i / perRow * SLOT, entry.catalysts().get(i), Role.CATALYST));
            }
            y += ((catalysts - 1) / perRow + 1) * SLOT;
        }
        List<PlacedChip> chips = new ArrayList<>();
        int bottom = flow(entry.chips(), y + 4, textWidth, chips);
        return new Page(WIDTH, bottom, slots, arrowX, 0, List.of(), chips);
    }

    /**
     * Lays out a gas.
     *
     * @param textWidth the width of a line of text in pixels
     */
    public static Page gas(GasEntry gas, ToIntFunction<Line> textWidth) {
        List<Slot> slots = List.of(new Slot(0, 0, GasStack.of(gas), Role.INPUT));
        List<Text> texts = List.of(
                new Text(SLOT + 4, 0, Line.keyOr(gas.nameKey(), ""), TEXT_COLOUR),
                new Text(SLOT + 4, 10, Line.plain(gas.formula()), FAINT_COLOUR));
        List<PlacedChip> chips = new ArrayList<>();
        // One property to a line, so every gas's page reads the same way down.
        int y = SLOT + 5;
        for (Chip chip : ViewerEntries.properties(gas)) {
            chips.add(new PlacedChip(0, y, width(chip, textWidth), chip));
            y += LINE;
        }
        int bottom = chips.isEmpty() ? SLOT : y - LINE + Icon.SIZE;
        return new Page(WIDTH, Math.max(bottom, SLOT), slots, -1, 0, texts, chips);
    }

    private static int rows(int slots) {
        return (slots + SLOTS_PER_SIDE - 1) / SLOTS_PER_SIDE;
    }

    /** Places chips in rows from the left, starting a new row when one is full. Returns the bottom. */
    private static int flow(List<Chip> chips, int top, ToIntFunction<Line> textWidth, List<PlacedChip> out) {
        if (chips.isEmpty()) {
            return top - 4;
        }
        int x = 0;
        int y = top;
        for (Chip chip : chips) {
            int width = width(chip, textWidth);
            if (x > 0 && x + width > WIDTH) {
                x = 0;
                y += LINE;
            }
            out.add(new PlacedChip(x, y, width, chip));
            x += width + CHIP_GAP;
        }
        return y + Icon.SIZE;
    }

    /** A chip's width, text included. One too long for the page is as wide as the page: its text is cut. */
    private static int width(Chip chip, ToIntFunction<Line> textWidth) {
        int width = Icon.SIZE + (chip.text().isEmpty() ? 0 : ICON_GAP + textWidth.applyAsInt(chip.text()));
        return Math.min(width, WIDTH);
    }
}
