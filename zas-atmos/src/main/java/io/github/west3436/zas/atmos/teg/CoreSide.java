package io.github.west3436.zas.atmos.teg;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

/**
 * Which side of a circulator the TEG core it serves is on, for its model (CONT-157): none, or one of
 * the four horizontal ways. Its model then shows a pipe joining it to the core on that side.
 */
public enum CoreSide implements StringRepresentable {
    NONE(null),
    NORTH(Direction.NORTH),
    EAST(Direction.EAST),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST);

    private final @Nullable Direction direction;

    CoreSide(@Nullable Direction direction) {
        this.direction = direction;
    }

    /** The way from the circulator to its core, or null for none. */
    public @Nullable Direction direction() {
        return direction;
    }

    /**
     * The side for a way.
     *
     * @param d the way from the circulator to its core, horizontal, or null for none
     * @return the side; {@link #NONE} for null or a vertical way
     */
    public static CoreSide of(@Nullable Direction d) {
        if (d == null) {
            return NONE;
        }
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NONE;
        };
    }

    /** The side turned with its block. */
    public CoreSide rotate(Rotation rotation) {
        return direction == null ? NONE : of(rotation.rotate(direction));
    }

    /** The side mirrored with its block. */
    public CoreSide mirror(Mirror mirror) {
        return direction == null ? NONE : of(mirror.mirror(direction));
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
