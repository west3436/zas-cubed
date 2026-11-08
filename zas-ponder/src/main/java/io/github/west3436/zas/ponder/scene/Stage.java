package io.github.west3436.zas.ponder.scene;

import net.createmod.ponder.api.scene.SceneBuilder;

/**
 * The floors scenes stand on, and how a scene opens. A floor is a layer of Ponder's white concrete and
 * snow, made by the build ({@code generatePonderStages}) at the bottom of a cube of air as tall as it's
 * wide, since Ponder ignores blocks set outside those bounds. A scene builds everything else with
 * {@link Build}. Ponder sizes the view to the floor, so pick the smallest one the scene fits on.
 */
public final class Stage {
    /** A 3 × 3 floor, for one device and its pipe. */
    public static final String SMALL = "stage_3";

    /** A 5 × 5 floor. */
    public static final String MEDIUM = "stage_5";

    /** A 7 × 7 floor. */
    public static final String LARGE = "stage_7";

    /** A 9 × 9 floor, for a room. */
    public static final String HUGE = "stage_9";

    private Stage() {}

    /**
     * Opens a scene: names it, then raises its floor. Its title is {@code zas_ponder.ponder.<id>.header}
     * in {@code en_us.json}; the code gives none, so text stays out of it (CLIENT-007).
     *
     * @param scene the scene
     * @param id the scene's id, unique in the addon
     */
    public static void open(SceneBuilder scene, String id) {
        scene.title(id, "");
        scene.showBasePlate();
        scene.idle(10);
    }
}
