package io.github.west3436.zas.compat.northstar;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.Breathing;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where Northstar asks about a point or a block, the ZAS³ zone that answers. A point is answered by
 * the zone it is in. A block is answered by the zone it is in or, when it fills its own cell, as a
 * furnace or an engine does, by a zone beside it that a face touches: Northstar asks about the
 * block that burns, and the air it burns is the room's.
 */
final class Zones {
    private Zones() {}

    /** A zoned sample whose oxygen is enough to breathe. */
    static boolean breathable(AtmosphereSample sample) {
        return zoned(sample)
                && sample.partialPressures().getOrDefault(StandardAir.OXYGEN, 0.0) >= Breathing.oxygenMinimum();
    }

    /** A sample in a zone with gas in it. */
    static boolean zoned(AtmosphereSample sample) {
        return sample.state() == AtmosphereState.ZONED
                && sample.zone().isPresent()
                && !sample.mixture().isEmpty();
    }

    /** The sample at a point, if it passes. */
    static @Nullable AtmosphereSample at(Level level, Vec3 point, Predicate<AtmosphereSample> passes) {
        AtmosphereSample sample = Atmospheres.at(level, point);
        return passes.test(sample) ? sample : null;
    }

    /**
     * The sample answering for a block, if one passes, and where it was found.
     *
     * @return the block's own sample or a neighbour's, or null if none passes
     */
    static @Nullable Found at(Level level, Vec3i block, Predicate<AtmosphereSample> passes) {
        BlockPos pos = block instanceof BlockPos p ? p : new BlockPos(block);
        AtmosphereSample own = Atmospheres.at(level, pos);
        if (passes.test(own)) {
            return new Found(pos, own);
        }
        if (own.state() == AtmosphereState.ZONED || !Atmospheres.zoneTouches(level, pos)) {
            return null;
        }
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            AtmosphereSample sample = Atmospheres.at(level, next);
            if (passes.test(sample)) {
                return new Found(next, sample);
            }
        }
        return null;
    }

    /** A sample and the block it was taken at. */
    record Found(BlockPos pos, AtmosphereSample sample) {}
}
