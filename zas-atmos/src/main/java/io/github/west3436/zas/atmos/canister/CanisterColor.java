package io.github.west3436.zas.atmos.canister;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A canister's paint: yellow by default, any dye's colour, or red with a white stripe for the
 * creative nitrous oxide canister (texture procurement §6.4). Each is a texture of its own.
 */
public enum CanisterColor implements StringRepresentable {
    YELLOW,
    WHITE,
    ORANGE,
    MAGENTA,
    LIGHT_BLUE,
    LIME,
    PINK,
    GRAY,
    LIGHT_GRAY,
    CYAN,
    PURPLE,
    BLUE,
    BROWN,
    GREEN,
    RED,
    BLACK,
    /** Red with a white stripe; no dye gives it. */
    RED_WHITE;

    public static final EnumProperty<CanisterColor> PROPERTY = EnumProperty.create("color", CanisterColor.class);

    /** The colour a dye paints. */
    public static CanisterColor of(DyeColor dye) {
        return valueOf(dye.name());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
