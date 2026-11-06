package io.github.west3436.zas.atmos.vent;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** What a vent does (CONT-014): nothing, blow its network's gas into its zone, or siphon the zone's into its network. */
public enum VentMode implements StringRepresentable {
    OFF,
    BLOW,
    SIPHON;

    /** The block state shows what the vent is doing: its mode while it runs, else off. */
    public static final EnumProperty<VentMode> PROPERTY = EnumProperty.create("mode", VentMode.class);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
