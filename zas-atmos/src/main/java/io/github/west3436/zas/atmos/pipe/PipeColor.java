package io.github.west3436.zas.atmos.pipe;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A pipe's dye (CONT-114): none, or one of the 16 dyes. The colour is cosmetic; it doesn't change
 * what a pipe connects to. Pipes are drawn in light greys and tinted with the dye's colour.
 */
public enum PipeColor implements StringRepresentable {
    NONE(null),
    WHITE(DyeColor.WHITE),
    ORANGE(DyeColor.ORANGE),
    MAGENTA(DyeColor.MAGENTA),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE),
    YELLOW(DyeColor.YELLOW),
    LIME(DyeColor.LIME),
    PINK(DyeColor.PINK),
    GRAY(DyeColor.GRAY),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY),
    CYAN(DyeColor.CYAN),
    PURPLE(DyeColor.PURPLE),
    BLUE(DyeColor.BLUE),
    BROWN(DyeColor.BROWN),
    GREEN(DyeColor.GREEN),
    RED(DyeColor.RED),
    BLACK(DyeColor.BLACK);

    /** The block state property of dyed pipes and caps. */
    public static final EnumProperty<PipeColor> PROPERTY = EnumProperty.create("color", PipeColor.class);

    private final DyeColor dye;

    PipeColor(DyeColor dye) {
        this.dye = dye;
    }

    /** The colour a dye gives. */
    public static PipeColor of(DyeColor dye) {
        return valueOf(dye.name());
    }

    /** The tint, as 0xRRGGBB: white (no tint) when undyed. */
    public int tint() {
        return dye == null ? 0xFFFFFF : dye.getTextureDiffuseColor() & 0xFFFFFF;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
