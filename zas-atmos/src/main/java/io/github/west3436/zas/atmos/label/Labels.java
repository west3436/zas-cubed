package io.github.west3436.zas.atmos.label;

import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What labels share: the one way text becomes a label, and the one way a block's label is read.
 *
 * <p>{@link #of(Level, BlockPos)} is the accessor to use for a block's label, on either side: it
 * finds the device from any block of one that has several, and loads nothing.
 */
public final class Labels {
    /** The most characters a label has. */
    public static final int MAX_LENGTH = 32;

    /**
     * The key of a labelled window's title: the label, then the device's kind. A window reads the two
     * back from it, so the title needs nothing else sent.
     */
    public static final String TITLE_KEY = "gui.zas_atmos.labelled";

    private Labels() {}

    /**
     * Makes a label of what a player typed, the same on both sides. Formatting codes go, and so does
     * every character that isn't printed: control and format characters, private-use ones and
     * unpaired surrogates. Each run of white space becomes one space, with none at either end. The
     * result is cut to {@link #MAX_LENGTH} characters, never through a pair or after a space.
     *
     * @param text any text; null reads as none
     * @return the label, or the empty string if nothing is left
     */
    public static String clean(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(text.length(), MAX_LENGTH));
        boolean space = false;
        int i = 0;
        while (i < text.length()) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (c == '§') {
                // A formatting code is this and the character after it, whichever that is.
                if (i < text.length()) {
                    i += Character.charCount(text.codePointAt(i));
                }
                continue;
            }
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                space = out.length() > 0;
                continue;
            }
            if (!printed(c)) {
                continue;
            }
            if (out.length() + (space ? 1 : 0) + Character.charCount(c) > MAX_LENGTH) {
                break;
            }
            if (space) {
                out.append(' ');
                space = false;
            }
            out.appendCodePoint(c);
        }
        return out.toString();
    }

    private static boolean printed(int c) {
        return switch (Character.getType(c)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.SURROGATE,
                    Character.PRIVATE_USE,
                    Character.UNASSIGNED,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR -> false;
            default -> true;
        };
    }

    /**
     * The label an item's name makes: its text alone, whatever it was styled with or built from, so
     * nothing but plain text is ever kept or shown.
     *
     * @return the label, or the empty string for an item with no name of its own
     */
    public static String of(ItemStack stack) {
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name == null ? "" : clean(name.getString());
    }

    /**
     * The device that keeps the label of the block at a position: the block's own, or for a block of
     * several, the one that stands for them all. That is an airlock's origin, a firelock's lower half
     * and a central controller's lower left block. It loads nothing.
     *
     * @return the device, or null if the block takes no label or isn't loaded
     */
    public static @Nullable Labelled at(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        BlockPos at = pos;
        if (block instanceof AirlockBlock) {
            at = AirlockBlock.origin(state, pos);
        } else if (block instanceof FirelockDoorBlock) {
            at = FirelockDoorBlock.lower(state, pos);
        } else if (block instanceof FirelockBlock) {
            at = FirelockBlock.lower(state, pos);
        } else if (block instanceof CentralControllerBlock) {
            at = CentralControllerBlock.origin(state, pos);
        }
        return level.isLoaded(at) && level.getBlockEntity(at) instanceof Labelled device ? device : null;
    }

    /**
     * The label of the block at a position, on either side.
     *
     * @return the label, or the empty string if it has none, takes none or isn't loaded
     */
    public static String of(Level level, BlockPos pos) {
        Labelled device = at(level, pos);
        return device == null ? "" : device.label();
    }

    /**
     * What a message calls the block at a position: its label, or without one its coordinates, as
     * messages have always named a block.
     */
    public static String named(Level level, BlockPos pos) {
        String label = of(level, pos);
        return label.isEmpty() ? pos.getX() + " " + pos.getY() + " " + pos.getZ() : label;
    }

    /**
     * A window's title: a device's kind, or with a label, the label in its place.
     *
     * @param label the label, or the empty string
     * @param kind the device's kind
     */
    public static Component title(String label, Component kind) {
        return label.isEmpty() ? kind : Component.translatable(TITLE_KEY, label, kind);
    }

    /** The label in a title {@link #title} made, or the empty string if it has none. */
    public static String labelIn(Component title) {
        Object[] args = args(title);
        return args.length < 1 ? "" : clean(args[0] instanceof Component c ? c.getString() : String.valueOf(args[0]));
    }

    /** The device's kind in a title {@link #title} made. */
    public static Component kindIn(Component title) {
        Object[] args = args(title);
        if (args.length < 2) {
            return title;
        }
        return args[1] instanceof Component c ? c : Component.literal(String.valueOf(args[1]));
    }

    private static Object[] args(Component title) {
        return title.getContents() instanceof TranslatableContents t && TITLE_KEY.equals(t.getKey())
                ? t.getArgs()
                : new Object[0];
    }
}
