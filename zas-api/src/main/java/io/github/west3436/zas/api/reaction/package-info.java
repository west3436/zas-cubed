/**
 * Gas reactions: running the server's reactions on a standalone mixture (API-019), and reaction
 * types implemented in code that datapack definitions can name (API-020). Everything here may be
 * called from any thread; reaction types run on the atmos worker and must be pure.
 */
package io.github.west3436.zas.api.reaction;
