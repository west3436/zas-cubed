package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.gas.GasMixture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Says what air living things are in at places outside zones that another mod keeps breathable by
 * its own means, such as the area around a space mod's oxygen machine (SPACE-017). Register one
 * with {@link LocalAtmospheres}.
 *
 * <p><b>If it throws</b> (NFR-013). ZAS³ catches it, takes it as {@code null} for that check and
 * logs the fault at most once a minute.
 */
@FunctionalInterface
public interface LocalAtmosphereProvider {
    /**
     * The air a living thing is in at a point, as the breathing rules and hazards should take it.
     *
     * <p>ZAS³ asks only about points outside zones, where the atmosphere is the dimension's
     * ambient: once per breathing check of an entity there, and whenever something reads the air
     * at a point with {@link LocalAtmospheres#at}, as an analyzer does up to four times a second.
     * Keep it to a lookup.
     *
     * <p>Thread: server thread only; ZAS³ calls it there.
     *
     * @param level the level
     * @param point the point: an entity's eye position, or a point something reads
     * @return the air there, of any volume, or {@code null} if this provider has nothing to say
     *     about the point, in which case the next provider is asked and then the ambient applies
     */
    @ServerThread
    @Nullable
    GasMixture at(ServerLevel level, Vec3 point);
}
