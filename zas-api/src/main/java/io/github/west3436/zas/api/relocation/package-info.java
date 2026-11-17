/**
 * Moving blocks with their air (API-065 to API-067): a mod that moves a box of blocks, within a
 * dimension or into another, tells ZAS³ so that the zones inside go with them, keeping their gas,
 * temperature and pins, with no breach on the way. Server thread only; the futures complete there.
 * Also where the world shows blocks a mod keeps elsewhere in their level (API-068), on either side.
 * Since API 1.2.0.
 */
package io.github.west3436.zas.api.relocation;
