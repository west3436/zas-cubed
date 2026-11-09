package io.github.west3436.zas.ponder.scene;

import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * A scene's text. Each line is a key under {@code zas_ponder.ponder.shared.} in {@code en_us.json}, so
 * no text is written in the code (CLIENT-007). Keep lines short, one idea each: the scene shows the
 * rest.
 */
public final class Say {
    /** How long a line stays, in ticks, unless a scene says otherwise. */
    public static final int LINE = 60;

    private Say() {}

    /**
     * Shows a line for a time, and returns it to be placed.
     *
     * @param scene the scene
     * @param ticks how long it stays
     * @param key the line's key, after {@code zas_ponder.ponder.shared.}
     * @param params values for the line's {@code %s}, such as a {@link Gas#pressure}
     * @return the line, for {@code pointAt}, {@code colored} and the like
     */
    public static TextElementBuilder line(SceneBuilder scene, int ticks, String key, Object... params) {
        TextElementBuilder line = scene.overlay().showText(ticks);
        return params.length == 0 ? line.sharedText(key) : line.sharedText(key, params);
    }

    /**
     * Shows a line pointing at a spot, with a keyframe, and waits while it's read: the usual way to say
     * something about a block.
     *
     * @param scene the scene
     * @param at the spot, such as {@code util.vector().topOf(pos)}
     * @param key the line's key, after {@code zas_ponder.ponder.shared.}
     * @param params values for the line's {@code %s}
     */
    public static void at(SceneBuilder scene, Vec3 at, String key, Object... params) {
        line(scene, LINE, key, params).pointAt(at).placeNearTarget().attachKeyFrame();
        scene.idle(LINE + 10);
    }

    /**
     * Shows a line pointing at a spot, with a keyframe, without waiting: for a line read while
     * something happens, such as a {@link Gas#flow}, which then does the waiting.
     *
     * @param scene the scene
     * @param ticks how long it stays, as long as what happens meanwhile
     * @param at the spot, such as {@code util.vector().topOf(pos)}
     * @param key the line's key, after {@code zas_ponder.ponder.shared.}
     * @param params values for the line's {@code %s}
     */
    public static void over(SceneBuilder scene, int ticks, Vec3 at, String key, Object... params) {
        line(scene, ticks, key, params).pointAt(at).placeNearTarget().attachKeyFrame();
    }

    /**
     * Shows a line with no target, with a keyframe, and waits while it's read.
     *
     * @param scene the scene
     * @param key the line's key, after {@code zas_ponder.ponder.shared.}
     * @param params values for the line's {@code %s}
     */
    public static void plain(SceneBuilder scene, String key, Object... params) {
        line(scene, LINE, key, params).independent().attachKeyFrame();
        scene.idle(LINE + 10);
    }
}
