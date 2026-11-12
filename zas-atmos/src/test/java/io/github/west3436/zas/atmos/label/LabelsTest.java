package io.github.west3436.zas.atmos.label;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** How text becomes a label, and how a window's title carries one. */
class LabelsTest {
    @Test
    void plain_text_is_kept_as_typed() {
        assertEquals("Supply 1", Labels.clean("Supply 1"));
        assertEquals("Lüftung 東 №2", Labels.clean("Lüftung 東 №2"));
        assertEquals("Dock 🚀", Labels.clean("Dock 🚀"), "a character of two units");
    }

    @Test
    void nothing_makes_no_label() {
        assertEquals("", Labels.clean(null));
        assertEquals("", Labels.clean(""));
        assertEquals("", Labels.clean("  \t\n 　 "));
        assertEquals("", Labels.clean("§c§l"));
        assertEquals("", Labels.clean("\u0000\u0007​‮﻿"));
    }

    @Test
    void formatting_codes_go_with_the_character_after_them() {
        assertEquals("Hot side", Labels.clean("§cHot §lside§r"));
        assertEquals("ab", Labels.clean("a§§b"), "a code whose character is the code mark");
        assertEquals("a", Labels.clean("a§"), "a code mark at the end");
        assertEquals("a", Labels.clean("a§🚀"), "a code whose character is of two units");
    }

    @Test
    void characters_that_arent_printed_go() {
        assertEquals("ab", Labels.clean("a\u0000b"), "a control character");
        assertEquals("ab", Labels.clean("a‮b"), "a direction override");
        assertEquals("ab", Labels.clean("a​b"), "a zero-width space");
        assertEquals("ab", Labels.clean("ab"), "a private-use character");
        assertEquals("ab", Labels.clean("a\uD83Db"), "half a pair");
        assertEquals("ab", Labels.clean("a\uDE80b"), "the other half");
        assertEquals("ab", Labels.clean("a￿b"), "a character that is none");
    }

    @Test
    void white_space_becomes_single_spaces_with_none_at_the_ends() {
        assertEquals("a b", Labels.clean("  a \t\r\n b  "));
        assertEquals("a b", Labels.clean("a  b"), "spaces that don't break");
        assertEquals("a b", Labels.clean("a b"), "a line separator");
        assertEquals("a b", Labels.clean("a §c b"), "spaces either side of a code");
    }

    @Test
    void a_long_label_is_cut_at_the_most_characters() {
        assertEquals("a".repeat(Labels.MAX_LENGTH), Labels.clean("a".repeat(Labels.MAX_LENGTH)));
        assertEquals("a".repeat(Labels.MAX_LENGTH), Labels.clean("a".repeat(Labels.MAX_LENGTH + 1)));
        assertEquals("a".repeat(Labels.MAX_LENGTH), Labels.clean("a".repeat(10_000)));
        assertEquals("a".repeat(31), Labels.clean("a".repeat(31) + " b"), "never ends in a space");
        assertEquals("a".repeat(31), Labels.clean("a".repeat(31) + "🚀"), "never through a pair");
        assertEquals("a".repeat(30) + "🚀", Labels.clean("a".repeat(30) + "🚀b"));
        assertEquals(
                "a".repeat(Labels.MAX_LENGTH),
                Labels.clean("§c".repeat(500) + "a".repeat(40)),
                "what is dropped doesn't count");
    }

    @Test
    void a_label_cleaned_again_is_the_same() {
        for (String text : new String[] {"  §cHot\n side ", "a".repeat(40), "a".repeat(31) + " b", "x §"}) {
            String once = Labels.clean(text);
            assertEquals(once, Labels.clean(once), text);
        }
    }

    @Test
    void a_title_carries_the_label_and_the_kind() {
        Component kind = Component.translatable("block.zas_atmos.vent");
        assertSame(kind, Labels.title("", kind), "with no label the title is the kind itself");
        assertEquals("", Labels.labelIn(kind));
        assertSame(kind, Labels.kindIn(kind));

        Component title = Labels.title("Supply 1", kind);
        assertEquals("Supply 1", Labels.labelIn(title));
        assertSame(kind, Labels.kindIn(title));
    }

    @Test
    void a_label_read_from_a_title_is_cleaned() {
        Component forged = Component.translatable(
                Labels.TITLE_KEY, Component.literal("§kx\n" + "y".repeat(50)), Component.literal("Vent"));
        assertEquals("x " + "y".repeat(30), Labels.labelIn(forged));
        assertEquals("Vent", Labels.kindIn(forged).getString());
    }
}
