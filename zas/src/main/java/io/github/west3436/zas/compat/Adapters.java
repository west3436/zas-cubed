package io.github.west3436.zas.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The adapters for other mods, and the one place that starts them (COMPAT-002, SPACE-003, MEK-001,
 * NFR-022).
 *
 * <p>An adapter is code that names another mod's classes, so it may only be loaded when that mod
 * is installed, in a version the adapter was written for. Each has a {@link Row} in the table
 * below. {@link #load} goes through the rows once, while {@code zas} is being constructed:
 *
 * <ul>
 *   <li>target not installed: nothing is loaded, and nothing is logged above debug;
 *   <li>target installed in a version outside the row's range: the adapter stays off, and one
 *       warning names the mod, its version and the range;
 *   <li>otherwise the row's entry class is loaded by name and its {@code public static void
 *       init(IEventBus modBus, ModContainer container)} is called, with {@code zas}'s mod bus and
 *       container.
 * </ul>
 *
 * <p>An entry class that can't be loaded, or whose {@code init} throws, doesn't stop the game: the
 * fault is logged once with its stack trace and that adapter is off (NFR-013). What it had already
 * registered stays registered, so an {@code init} should do anything that can fail first.
 *
 * <p>No {@code neoforge.mods.toml} names a target, so the target may be constructed after {@code
 * zas}. An {@code init} therefore only registers: listeners, deferred registers, capabilities. It
 * calls into the target later, from those.
 */
public final class Adapters {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<Row> ROWS = table(shipped());

    /** What {@link #load} found, in the table's order. Empty until it has run. */
    private static volatile List<Status> statuses = List.of();

    private Adapters() {}

    /**
     * The table. An adapter adds one line here, and nothing else in {@code zas} names it.
     *
     * <p>A row is the target's mod id, the versions of it the adapter supports (see {@link
     * VersionGate} for how a range is written) and the full name of the adapter's entry class, as
     * text so that nothing here loads the class. The entry class ships in this jar; it is public
     * and has a {@code public static void init(IEventBus modBus, ModContainer container)}. A target
     * has one row.
     */
    private static List<Row> shipped() {
        List<Row> rows = new ArrayList<>();
        // One line per adapter, like this one:
        // rows.add(new Row("examplemod", "[1.2,2)", "io.github.west3436.zas.compat.examplemod.ExampleModCompat"));

        // Ad Astra (SPACE-003): 1.16.26 is the build the adapter is tested with, and 1.17 may change its API.
        rows.add(new Row("ad_astra", "[1.16.26,1.17)", "io.github.west3436.zas.compat.adastra.AdAstraCompat"));

        return rows;
    }

    /**
     * One adapter.
     *
     * @param modId the target's mod id
     * @param range the versions of the target the adapter supports, as {@link VersionGate} reads
     *     them
     * @param entryClass the binary name of the class whose {@code init} starts the adapter
     */
    public record Row(String modId, String range, String entryClass) {
        /** @throws IllegalArgumentException if the range isn't one */
        public Row {
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(entryClass, "entryClass");
            VersionGate.range(Objects.requireNonNull(range, "range"));
        }
    }

    /** How an adapter stands once {@link #load} has run. */
    public enum State {
        /** Its target isn't installed. */
        ABSENT,
        /** Its target is installed in a version outside the row's range, so it is off. */
        UNSUPPORTED,
        /** Its entry class couldn't be loaded or threw, so it is off. */
        FAILED,
        /** Its entry class's {@code init} returned. */
        ACTIVE
    }

    /**
     * An adapter and how it stands.
     *
     * @param row its row
     * @param state how it stands
     * @param version the installed version of its target, or null if the target is absent
     */
    public record Status(Row row, State state, @Nullable String version) {}

    /**
     * Starts every adapter in the table whose target is installed in a supported version. Called
     * once, last in the mod constructor, so an entry class finds the API in place.
     *
     * @param modBus {@code zas}'s mod bus, handed to each entry class
     * @param container {@code zas}'s container, handed to each entry class
     */
    public static void load(IEventBus modBus, ModContainer container) {
        statuses = load(ROWS, Adapters::installedVersion, modBus, container);
    }

    /**
     * Goes through a table as {@link #load(IEventBus, ModContainer)} does the shipped one, and
     * returns what it found without recording it. This is how a test loads rows of its own.
     *
     * @param rows the table
     * @param versions the installed version of a mod id, empty if that mod is absent
     * @param modBus handed to each entry class
     * @param container handed to each entry class
     * @return each row and how it stands, in the table's order
     */
    public static List<Status> load(
            List<Row> rows, Function<String, Optional<String>> versions, IEventBus modBus, ModContainer container) {
        List<Status> found = new ArrayList<>(rows.size());
        for (Row row : rows) {
            String version = versions.apply(row.modId()).orElse(null);
            found.add(new Status(row, start(row, version, modBus, container), version));
        }
        return List.copyOf(found);
    }

    /**
     * The installed version of a mod, as its metadata gives it.
     *
     * @return empty if no such mod is installed
     */
    public static Optional<String> installedVersion(String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(mod -> mod.getModInfo().getVersion().toString());
    }

    /**
     * Whether the adapter for a mod is running: its target is installed in a supported version and
     * its entry class started. False for a mod with no adapter, and for every mod before {@link
     * #load} has run. Any thread.
     *
     * @param modId the target's mod id
     */
    public static boolean isActive(String modId) {
        for (Status status : statuses) {
            if (status.row().modId().equals(modId)) {
                return status.state() == State.ACTIVE;
            }
        }
        return false;
    }

    /**
     * Every shipped adapter and how it stands, in the table's order. Empty before {@link #load} has
     * run. Any thread.
     */
    public static List<Status> statuses() {
        return statuses;
    }

    /** The shipped table. */
    public static List<Row> rows() {
        return ROWS;
    }

    /**
     * Checks a table: a target has one row, so that one warning and one answer to {@link
     * #isActive} are all it can have.
     *
     * @throws IllegalArgumentException if two rows name one mod
     */
    static List<Row> table(List<Row> rows) {
        Set<String> targets = new HashSet<>();
        for (Row row : rows) {
            if (!targets.add(row.modId())) {
                throw new IllegalArgumentException("Two adapter rows for " + row.modId());
            }
        }
        return List.copyOf(rows);
    }

    private static State start(Row row, @Nullable String version, IEventBus modBus, ModContainer container) {
        switch (VersionGate.check(version, row.range())) {
            case ABSENT -> {
                LOGGER.debug("{} isn't installed, so its adapter stays off", row.modId());
                return State.ABSENT;
            }
            case UNSUPPORTED -> {
                LOGGER.warn(
                        "The adapter for {} is off: it supports versions {}, and {} is installed",
                        row.modId(),
                        row.range(),
                        version);
                return State.UNSUPPORTED;
            }
            case SUPPORTED -> {}
        }
        try {
            enter(row.entryClass(), modBus, container);
        } catch (Exception | LinkageError e) {
            LOGGER.error("The adapter for {} {} failed to start, so it is off", row.modId(), version, e);
            return State.FAILED;
        }
        LOGGER.info("The adapter for {} {} is on", row.modId(), version);
        return State.ACTIVE;
    }

    /** Loads an entry class and calls its {@code init}, throwing what that threw. */
    private static void enter(String entryClass, IEventBus modBus, ModContainer container) throws Exception {
        try {
            Class.forName(entryClass, true, Adapters.class.getClassLoader())
                    .getMethod("init", IEventBus.class, ModContainer.class)
                    .invoke(null, modBus, container);
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
