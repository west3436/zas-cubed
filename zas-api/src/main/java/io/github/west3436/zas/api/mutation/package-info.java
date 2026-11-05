/**
 * Gas mutations with asynchronous results (API-012, API-016, D-46), zone pins
 * (API-017, API-050), and telling ZAS³ a block's gas geometry changed (GEO-014).
 * All of them may be called only from the server thread; their futures complete
 * there.
 */
package io.github.west3436.zas.api.mutation;
