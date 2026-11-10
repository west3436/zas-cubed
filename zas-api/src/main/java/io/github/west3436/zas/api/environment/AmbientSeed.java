package io.github.west3436.zas.api.environment;

import io.github.west3436.zas.api.ServerThread;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Gives dimensions an ambient atmosphere from another mod's data, such as a space mod's list of
 * planets (ENV-014's third source, SPACE-020). Register one with {@link AmbientSeeds#register}.
 *
 * <p>A seed is a starting point, not a claim. It applies to a dimension only where no datapack
 * defines the ambient and no {@link AmbientProvider} does (D-45), so a pack author or a mod that
 * knows the dimension better always wins. A mod that owns a dimension registers a provider; a mod
 * that reads someone else's description of it registers a seed.
 *
 * <p><b>When it runs.</b> As a provider does (ENV-015): ZAS³ asks once per atmos tick of each
 * dimension, on the server thread, as the tick's batch closes, and the answer holds until the next.
 * It is asked about every dimension, so it answers {@code null} for the ones its data doesn't
 * cover. Keep it to a lookup: it counts against the server thread's atmos budget (NFR-001).
 *
 * <p>The answer is checked against the running server's gases: one naming a gas the server doesn't
 * have is skipped, with a warning, as if the seed had answered {@code null}.
 *
 * <p><b>If it throws</b> (NFR-013). ZAS³ catches it and skips the seed for that atmos tick. If the
 * dimension's ambient was this seed's last answer, that answer stands, so a vacuum doesn't turn into
 * air for a tick; otherwise the next seed is asked. ZAS³ logs the fault at most once a minute.
 */
@FunctionalInterface
public interface AmbientSeed {
    /**
     * The ambient another mod's data gives a dimension, for this atmos tick.
     *
     * <p>Thread: server thread only; ZAS³ calls it there.
     *
     * @param level the dimension's level
     * @return the ambient, or {@code null} if the data doesn't cover this dimension, in which case
     *     the next seed is asked and then the fallback applies (ENV-011)
     */
    @ServerThread
    @Nullable
    AmbientDefinition seed(ServerLevel level);
}
