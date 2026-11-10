/**
 * What the recipe viewers show of gases and reactions (COMPAT-060 to COMPAT-062), with nothing of
 * any viewer in it. The JEI and EMI plugins, in projects of their own, both read these entries, lay
 * them out with {@link io.github.west3436.zas.compat.viewer.ViewerLayout} and draw them with {@link
 * io.github.west3436.zas.compat.viewer.ViewerDraw}, so the two viewers show the same pages.
 *
 * <p>Only {@link io.github.west3436.zas.compat.viewer.ViewerDraw} needs a client. Everything else
 * is plain data and loads anywhere.
 */
package io.github.west3436.zas.compat.viewer;
