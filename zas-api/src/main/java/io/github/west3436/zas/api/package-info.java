/**
 * The public ZAS³ API. Mods may bundle it (jar-in-jar) and call it whether or
 * not ZAS³ is installed (D-17, API-006). {@link io.github.west3436.zas.api.ZasApi}
 * is the entry point.
 *
 * <p><b>Threading contract</b> (API-004, API-045, API-046). Every method says
 * which thread may call it, and is marked {@link io.github.west3436.zas.api.AnyThread},
 * {@link io.github.west3436.zas.api.ServerThread} or
 * {@link io.github.west3436.zas.api.LevelThread}:
 *
 * <ul>
 *   <li>Queries may be called from any thread. They return immutable snapshots
 *       of the last applied atmos tick, which never change afterwards.
 *   <li>The breathability helper ({@link io.github.west3436.zas.api.life.Breathing})
 *       reads an entity's live state, so it may be called only on the main
 *       thread of the entity's logical side. For a server-side entity it
 *       throws {@link java.lang.IllegalStateException} from any other thread.
 *   <li>Mutations may be called only from the server thread, and throw
 *       {@link java.lang.IllegalStateException} otherwise.
 *   <li>Mutation futures complete on the server thread, once the atmos tick
 *       that applies them has been applied.
 *   <li>Value types (records) are immutable and may be used from any thread.
 * </ul>
 *
 * <p>Nothing here references {@code zas}, the engine or an optional mod
 * (API-003, API-044).
 */
package io.github.west3436.zas.api;
