package io.github.west3436.zas.atmos.compat;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Whether the Ponder scenes are on, and the one place that starts them (CLIENT-023, NFR-022).
 *
 * <p>The scenes are part of {@code zas_atmos} and ship in the release jar, in a package of their own
 * whose classes name Ponder's. Ponder comes with Create, or with the ZAS³ Ponder addon, which nests
 * it for players without Create. So the scenes may only be loaded on a client that has Ponder, in a
 * version they were written for. A client asks here once, as it sets up:
 *
 * <ul>
 *   <li>Ponder not installed: nothing is loaded, and nothing is logged above debug;
 *   <li>Ponder installed in a version outside {@link #SUPPORTED}: the scenes stay off, and one
 *       warning names the version and the range;
 *   <li>the addon installed in a release that holds its own copy of the scenes, as every release up
 *       to 1.0.0 does: that copy registers itself, so these stay off and each scene is there once;
 *   <li>otherwise the scenes' entry class is loaded by name and registers them with Ponder.
 * </ul>
 *
 * <p>An entry class that can't be loaded, or that throws, doesn't stop the game: the fault is logged
 * once and the scenes are off. With the content off there are no items to explain, so nothing asks
 * (CONT-155).
 *
 * <p>No {@code neoforge.mods.toml} gives Ponder a version range, since a range on an optional
 * dependency stops the game when the installed version is outside it. This class names nothing of
 * Ponder's, so a game without Ponder may load it. The framework's adapter loader has the same version
 * check; it is internal to {@code zas}, which this mod may not name (CONT-095), so the few lines are
 * repeated here, as in the Jade adapter.
 */
public final class PonderGate {
    /** Ponder's mod id. */
    public static final String PONDER = "ponder";

    /** The ZAS³ Ponder addon's mod id. */
    public static final String ADDON = "zas_ponder";

    /**
     * The versions of Ponder the scenes support, written as in {@code neoforge.mods.toml}. 1.0.82 is
     * the copy Create 6.0.10 nests, which the scenes are compiled against so that they use nothing
     * newer. A Ponder 2 may change what they call.
     */
    public static final String SUPPORTED = "[1.0.82,2)";

    /**
     * The scenes' entry class, as text so that nothing here loads it. It is public and has a {@code
     * public static void init()}.
     */
    public static final String ENTRY = "io.github.west3436.zas.atmos.compat.ponder.ZasPonderScenes";

    /**
     * A class that a release of the addon holds if it has its own copy of the scenes, as a file in its
     * jar. Releases since hold no code.
     */
    static final String ADDONS_OWN_SCENES = "io/github/west3436/zas/ponder/ZasPonderPlugin.class";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean asked;

    private static volatile boolean started;

    /** What the gate makes of an installation. */
    public enum Verdict {
        /** The content is off, so there is nothing to explain. */
        CONTENT_OFF,
        /** Ponder isn't installed. */
        ABSENT,
        /** Ponder is installed, in a version outside the range. */
        UNSUPPORTED,
        /** The addon is installed in a release that registers its own copy of the scenes. */
        ADDON_HAS_SCENES,
        /** A client registers the scenes. */
        ON
    }

    private PonderGate() {}

    /**
     * The decision alone: it is given what is installed and reads nothing from the game.
     *
     * @param contentOn whether the content loads
     * @param ponder Ponder's version as its mod metadata gives it, such as {@code 1.0.82+mc1.21.1},
     *     or null if Ponder isn't installed
     * @param addonHasScenes whether the addon is installed in a release that holds its own scenes
     * @return the verdict
     */
    public static Verdict decide(boolean contentOn, @Nullable String ponder, boolean addonHasScenes) {
        if (!contentOn) {
            return Verdict.CONTENT_OFF;
        }
        if (ponder == null) {
            return Verdict.ABSENT;
        }
        if (!supports(ponder, SUPPORTED)) {
            return Verdict.UNSUPPORTED;
        }
        return addonHasScenes ? Verdict.ADDON_HAS_SCENES : Verdict.ON;
    }

    /**
     * What the gate makes of this game's mods. It says the same on a dedicated server, where nothing
     * asks: what a client with these mods does. Any thread.
     */
    public static Verdict verdict() {
        return decide(ZasAtmosMod.contentEnabled(), installed(PONDER), addonHasScenes());
    }

    /**
     * Whether the scenes' entry class has run. False without Ponder, and on a dedicated server: none
     * of the classes that name Ponder's has been loaded. Any thread.
     */
    public static boolean started() {
        return started;
    }

    /**
     * Starts the scenes if {@link #verdict} is {@link Verdict#ON}. The content mod's client calls it
     * as it sets up, which is before Ponder gathers its plugins' scenes. Only the first call does or
     * logs anything.
     */
    public static synchronized void start() {
        if (asked) {
            return;
        }
        asked = true;
        String ponder = installed(PONDER);
        switch (verdict()) {
            case CONTENT_OFF -> LOGGER.debug("The content is off, so the Ponder scenes stay off");
            case ABSENT -> LOGGER.debug("Ponder isn't installed, so the Ponder scenes stay off");
            case UNSUPPORTED ->
                LOGGER.warn(
                        "The Ponder scenes are off: they support Ponder versions {}, and {} is installed",
                        SUPPORTED,
                        ponder);
            case ADDON_HAS_SCENES ->
                LOGGER.info(
                        "ZAS³ Ponder {} registers its own copy of the Ponder scenes, so the built-in ones stay off",
                        installed(ADDON));
            case ON -> {
                try {
                    enter();
                } catch (Exception | LinkageError e) {
                    LOGGER.error("The Ponder scenes failed to start with Ponder {}, so they are off", ponder, e);
                    return;
                }
                started = true;
                LOGGER.info("The Ponder scenes are on, with Ponder {}", ponder);
            }
        }
    }

    /**
     * Whether a version is in a range. Versions compare by Maven's rules, as the loader's do.
     *
     * @throws IllegalArgumentException if {@code range} isn't a range with bounds
     */
    static boolean supports(String installed, String range) {
        VersionRange supported;
        try {
            supported = VersionRange.createFromVersionSpec(range);
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException("Not a version range: " + range, e);
        }
        if (!supported.hasRestrictions()) {
            // Maven reads a bare version as a preference that every version satisfies.
            throw new IllegalArgumentException("Not a version range, as it has no bounds: " + range);
        }
        return supported.containsVersion(new DefaultArtifactVersion(installed));
    }

    /** The installed version of a mod, as its metadata gives it, or null if it isn't installed. */
    private static @Nullable String installed(String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElse(null);
    }

    /** Looks in the addon's jar for its own scenes, as a file, which loads nothing. */
    private static boolean addonHasScenes() {
        IModFileInfo addon = ModList.get().getModFileById(ADDON);
        return addon != null && Files.exists(addon.getFile().findResource(ADDONS_OWN_SCENES.split("/")));
    }

    /** Loads the entry class and calls its {@code init}, throwing what that threw. */
    private static void enter() throws Exception {
        try {
            Class.forName(ENTRY, true, PonderGate.class.getClassLoader())
                    .getMethod("init")
                    .invoke(null);
        } catch (InvocationTargetException e) {
            // What init threw, without reflection's wrapper around it.
            if (e.getCause() instanceof Exception thrown) {
                throw thrown;
            }
            if (e.getCause() instanceof Error thrown) {
                throw thrown;
            }
            throw e;
        }
    }
}
