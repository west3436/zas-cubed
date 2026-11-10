package io.github.west3436.zas.compat.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.reaction.ReactionFormatTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/** Where a page's parts go (COMPAT-060, COMPAT-061): everything is on the page and nothing overlaps. */
class ViewerLayoutTest {
    /** Six pixels a character, about the game's font, with a translated line as long as its key's arguments. */
    private static final ToIntFunction<Line> WIDTH = line -> 6
            * (line.translated()
                    ? String.join(" ", line.args()).length() + 8
                    : line.fallback().length());

    @Test
    void compat_060_every_shipped_reaction_fits_its_page() throws IOException {
        GasRegistry registry = ViewerEntriesTest.shipped();
        List<ReactionEntry> entries =
                ViewerEntries.reactions(registry, ReactionFormatTest.shippedReactions(), FireSettings.DEFAULT);
        assertEquals(5, entries.size());
        for (ReactionEntry entry : entries) {
            ViewerLayout.Page page = ViewerLayout.reaction(entry, WIDTH);
            String name = entry.path();
            sound(page, name);
            assertEquals(
                    entry.reactants().size()
                            + entry.products().size()
                            + entry.catalysts().size(),
                    page.slots().size(),
                    name);
            assertEquals(
                    entry.chips(),
                    page.chips().stream().map(ViewerLayout.PlacedChip::chip).toList(),
                    name);
            // What it takes is left of the arrow and what it gives is right of it, on one row.
            for (ViewerLayout.Slot slot : page.slots()) {
                assertEquals(0, slot.y(), name);
                if (slot.role() == ViewerLayout.Role.INPUT) {
                    assertTrue(slot.x() + ViewerLayout.SLOT <= page.arrowX(), name);
                } else {
                    assertTrue(slot.x() >= page.arrowX() + ViewerLayout.ARROW_WIDTH, name);
                }
            }
            assertTrue(page.height() <= 64, name + " is " + page.height() + " tall");
        }
    }

    @Test
    void compat_060_a_reaction_with_many_gases_and_catalysts_still_fits() {
        GasRegistry registry = GasRegistry.EMPTY;
        ReactionDefinition big = ReactionFormatTest.parse("""
                {
                  "reactants": {"p:a": 1, "p:b": 2, "p:c": 3, "p:d": 4, "p:e": 0.5},
                  "products": {"p:f": 1, "p:g": 1, "p:h": 1, "p:i": 1},
                  "enthalpy": -1000,
                  "min_temperature": 300,
                  "catalysts": [{"gas": "p:j", "min": 0.01}, {"gas": "p:k"}],
                  "concentrations": [{"gas": "p:a", "min": 0.1}, {"gas": "p:b", "max": 0.4}]
                }
                """).orElseThrow();
        ReactionEntry entry = ViewerEntries.reaction(registry, big, FireSettings.DEFAULT, false);
        ViewerLayout.Page page = ViewerLayout.reaction(entry, WIDTH);
        sound(page, "big");
        assertEquals(11, page.slots().size());
        List<ViewerLayout.Slot> catalysts = page.slots().stream()
                .filter(slot -> slot.role() == ViewerLayout.Role.CATALYST)
                .toList();
        assertEquals(2, catalysts.size());
        // Two rows of gases, then the catalysts under them, then the chips.
        assertTrue(catalysts.get(0).y() >= 2 * ViewerLayout.SLOT);
        assertTrue(page.chips().get(0).y() >= catalysts.get(0).y() + ViewerLayout.SLOT);
        assertTrue(
                page.chips().get(page.chips().size() - 1).y()
                        > page.chips().get(0).y(),
                "the chips wrap");
    }

    @Test
    void compat_060_a_reaction_that_takes_nothing_has_only_its_products() {
        ReactionEntry entry = new ReactionEntry(
                "p:from_nothing",
                false,
                List.of(),
                List.of(new GasStack(new GasEntry("p:a", "gas.p.a", "A", 0.01, 20, 0xFFFFFF, List.of()), 1)),
                List.of(),
                List.of());
        ViewerLayout.Page page = ViewerLayout.reaction(entry, WIDTH);
        sound(page, "from nothing");
        assertEquals(1, page.slots().size());
        assertEquals(ViewerLayout.SLOT, page.height());
    }

    @Test
    void compat_061_every_shipped_gas_fits_its_page() throws IOException {
        for (GasEntry gas : ViewerEntries.gases(ViewerEntriesTest.shipped())) {
            ViewerLayout.Page page = ViewerLayout.gas(gas, WIDTH);
            sound(page, gas.id());
            assertEquals(List.of(new ViewerLayout.Slot(0, 0, GasStack.of(gas), ViewerLayout.Role.INPUT)), page.slots());
            assertEquals(-1, page.arrowX(), "a gas has no arrow");
            assertEquals(
                    List.of(Line.keyOr(gas.nameKey(), ""), Line.plain(gas.formula())),
                    page.texts().stream().map(ViewerLayout.Text::line).toList());
            assertEquals(
                    ViewerEntries.properties(gas),
                    page.chips().stream().map(ViewerLayout.PlacedChip::chip).toList());
            // One property to a line, under the gas and its name.
            int y = ViewerLayout.SLOT + 5;
            for (ViewerLayout.PlacedChip chip : page.chips()) {
                assertEquals(0, chip.x(), gas.id());
                assertEquals(y, chip.y(), gas.id());
                y += 11;
            }
            assertTrue(page.height() <= 72, gas.id() + " is " + page.height() + " tall");
        }
    }

    @Test
    void compat_060_text_too_long_for_the_page_stays_on_it() {
        Line endless = Line.plain("x".repeat(60));
        ReactionEntry entry = new ReactionEntry(
                "p:wordy", false, List.of(), List.of(), List.of(), List.of(new Chip(Icon.GEAR, endless, Line.NONE)));
        ViewerLayout.Page page = ViewerLayout.reaction(entry, WIDTH);
        sound(page, "wordy");
        assertEquals(ViewerLayout.WIDTH, page.chips().get(0).width(), "the chip is cut to the page");

        GasEntry tagged = new GasEntry("p:a", "gas.p.a", "A", 0.01, 20, 0xFFFFFF, List.of("p:" + "y".repeat(60)));
        sound(ViewerLayout.gas(tagged, WIDTH), "tagged");
    }

    @Test
    void compat_060_the_pointer_finds_the_chip_it_is_on() throws IOException {
        GasRegistry registry = ViewerEntriesTest.shipped();
        ReactionEntry entry = ViewerEntries.reactions(
                        registry, ReactionFormatTest.shippedReactions(), FireSettings.DEFAULT)
                .get(0);
        ViewerLayout.Page page = ViewerLayout.reaction(entry, WIDTH);
        for (ViewerLayout.PlacedChip chip : page.chips()) {
            assertEquals(Optional.of(chip.chip().hint()), page.hintAt(chip.x(), chip.y()));
            assertEquals(
                    Optional.of(chip.chip().hint()),
                    page.hintAt(chip.x() + chip.width() - 1, chip.y() + Icon.SIZE - 1));
        }
        assertEquals(Optional.empty(), page.hintAt(0, 0), "a slot is not a chip");
        assertEquals(Optional.empty(), page.hintAt(-1, page.chips().get(0).y()));
    }

    /** Everything is on the page, and no slot or chip overlaps another. */
    private static void sound(ViewerLayout.Page page, String name) {
        assertEquals(ViewerLayout.WIDTH, page.width(), name);
        List<int[]> boxes = new ArrayList<>();
        for (ViewerLayout.Slot slot : page.slots()) {
            boxes.add(new int[] {slot.x(), slot.y(), ViewerLayout.SLOT, ViewerLayout.SLOT});
        }
        for (ViewerLayout.PlacedChip chip : page.chips()) {
            boxes.add(new int[] {chip.x(), chip.y(), chip.width(), Icon.SIZE});
        }
        if (page.arrowX() >= 0) {
            boxes.add(new int[] {page.arrowX(), page.arrowY(), ViewerLayout.ARROW_WIDTH, ViewerLayout.ARROW_HEIGHT});
        }
        for (int[] box : boxes) {
            assertTrue(box[0] >= 0 && box[1] >= 0, name + ": off the top or left");
            assertTrue(box[0] + box[2] <= page.width(), name + ": off the right");
            assertTrue(box[1] + box[3] <= page.height(), name + ": off the bottom");
        }
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                int[] a = boxes.get(i);
                int[] b = boxes.get(j);
                boolean apart =
                        a[0] + a[2] <= b[0] || b[0] + b[2] <= a[0] || a[1] + a[3] <= b[1] || b[1] + b[3] <= a[1];
                assertFalse(!apart, name + ": two parts overlap");
            }
        }
    }
}
