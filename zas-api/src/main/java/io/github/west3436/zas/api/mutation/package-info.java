/**
 * Gas mutations with asynchronous results (API-012, API-016, D-46), and zone
 * pins (API-017, API-050). Mutations and pins may be called only from the
 * server thread; their futures complete there.
 */
package io.github.west3436.zas.api.mutation;
