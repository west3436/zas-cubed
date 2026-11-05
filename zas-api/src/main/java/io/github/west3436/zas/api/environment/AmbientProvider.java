package io.github.west3436.zas.api.environment;

import io.github.west3436.zas.api.ServerThread;
import net.minecraft.server.level.ServerLevel;

/**
 * Supplies a dimension's ambient atmosphere from code (ENV-004). Register one with {@link
 * AmbientProviders#register}.
 *
 * <p><b>When it runs</b> (ENV-015). ZAS³ calls the provider that applies to a dimension once per
 * atmos tick of that dimension, on the server thread, as the tick's batch closes. What it answers
 * holds until the next atmos tick, for the simulation and for every query, so an answer may change
 * from one tick to the next, such as a temperature that follows the time of day (ENV-006). Nothing
 * else calls it, so a query never runs it.
 *
 * <p><b>Keep it cheap.</b> It counts against the server thread's atmos budget (NFR-001). It may read
 * the level, and shouldn't load chunks (GEO-034). It has no position to answer for: ambients that
 * vary by position come with R2 (ENV-005).
 *
 * <p><b>Whether it applies</b> (ENV-014). A datapack definition of the dimension beats every
 * provider, so pack authors get the final say (D-45). The answer is checked against the running
 * server's gases: one naming a gas the server doesn't have is skipped, with a warning, as if the
 * provider had answered {@code null}.
 */
@FunctionalInterface
public interface AmbientProvider {
    /**
     * The dimension's ambient for this atmos tick.
     *
     * <p>Thread: server thread only; ZAS³ calls it there.
     *
     * @param level the dimension's level
     * @return the ambient, or {@code null} if this provider doesn't define it for now, in which case
     *     the next source applies (ENV-014); a provider of lower priority isn't asked (ENV-019)
     */
    @ServerThread
    AmbientDefinition ambient(ServerLevel level);
}
