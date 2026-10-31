package io.github.west3436.zas.api.atmosphere;

import io.github.west3436.zas.api.AnyThread;

/**
 * The id of a zone, unique within its dimension.
 *
 * <p>Ids are opaque: compare them, store them and pass them back to
 * {@link Atmospheres#zone}, but don't derive meaning from the number.
 *
 * @param value the raw id
 */
@AnyThread
public record ZoneId(long value) {}
