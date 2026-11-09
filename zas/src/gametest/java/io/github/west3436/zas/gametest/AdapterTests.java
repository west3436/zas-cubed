package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.Row;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * The adapter loader (COMPAT-002, SPACE-003, MEK-001), on rows of the tests' own: the table that
 * ships holds real adapters only, and their targets aren't on this server. The entry classes are
 * the nested classes at the end, which only this source set holds.
 *
 * <p>Each test finishes within the tick it starts in, so they don't hear each other's log lines.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdapterTests {
    /** A mod every server has, whose real version the loader looks up for itself. */
    private static final String INSTALLED = "minecraft";

    /** What the entry classes below were handed, in order. */
    private static final List<Object> HANDED = new ArrayList<>();

    /** Set by {@link NeverLoaded} if anything ever loads it. */
    private static volatile boolean loadedTheOneThatMustNotBe;

    private AdapterTests() {}

    private static ModContainer zas() {
        return ModList.get().getModContainerById(ZasMod.MOD_ID).orElseThrow();
    }

    private static List<Status> load(List<Row> rows, Function<String, Optional<String>> versions) {
        HANDED.clear();
        ModContainer zas = zas();
        return Adapters.load(rows, versions, zas.getEventBus(), zas);
    }

    /** The real lookup, except for the mods named here, which are given these versions. */
    private static Function<String, Optional<String>> pretending(Map<String, String> versions) {
        return id -> versions.containsKey(id) ? Optional.of(versions.get(id)) : Adapters.installedVersion(id);
    }

    /**
     * A target outside its row's range: the adapter stays off, its entry class is never loaded, and
     * one warning names the mod, its version and the range. Once with a mod this server really has,
     * read from the mod list, and once with a made-up mod at a made-up version. The server carrying
     * on to finish this suite is the rest of it.
     */
    @GameTest(template = "empty")
    public static void compat_002_an_unsupported_version_turns_the_adapter_off_with_one_warning(GameTestHelper h) {
        String real = Adapters.installedVersion(INSTALLED).orElseThrow();
        List<Row> rows = List.of(
                new Row(INSTALLED, "[1.0,1.1)", NeverLoaded.class.getName()),
                new Row("zas_test_old", "[2.3,3)", NeverLoaded.class.getName()));
        try (Heard log = new Heard()) {
            List<Status> found = load(rows, pretending(Map.of("zas_test_old", "2.2.9")));

            h.assertValueEqual(found.get(0), new Status(rows.get(0), State.UNSUPPORTED, real), "the real mod's row");
            h.assertValueEqual(found.get(1), new Status(rows.get(1), State.UNSUPPORTED, "2.2.9"), "the made-up one");
            h.assertFalse(loadedTheOneThatMustNotBe, "an unsupported target's entry class was loaded");
            h.assertValueEqual(log.at(Level.WARN), 2, "warnings, one for each");
            h.assertValueEqual(log.at(Level.WARN, INSTALLED, real, "[1.0,1.1)"), 1, "warnings naming the real mod");
            h.assertValueEqual(log.at(Level.WARN, "zas_test_old", "2.2.9", "[2.3,3)"), 1, "naming the made-up one");
            h.assertValueEqual(log.events.size(), 2, "lines logged in all");
        }
        h.succeed();
    }

    /**
     * Entry classes that can't start, each in its own way: one that isn't there, one whose static
     * initialiser throws, one whose init throws, one whose init meets a class its target no longer
     * has, and one with no init. Each is off with one error that carries the stack trace, nothing is
     * thrown to the caller, and the row after them all still starts (NFR-013).
     */
    @GameTest(template = "empty")
    public static void compat_002_an_entry_class_that_throws_is_contained_and_logged_once(GameTestHelper h) {
        List<Row> rows = List.of(
                new Row("zas_test_missing", "[1,2)", AdapterTests.class.getName() + "$NoSuchEntry"),
                new Row("zas_test_static", "[1,2)", ThrowsWhenLoaded.class.getName()),
                new Row("zas_test_init", "[1,2)", ThrowsInInit.class.getName()),
                new Row("zas_test_linkage", "[1,2)", MeetsAMissingClass.class.getName()),
                new Row("zas_test_no_init", "[1,2)", NoInit.class.getName()),
                new Row("zas_test_working", "[1,2)", Working.class.getName()));
        Map<String, String> versions = Map.of(
                "zas_test_missing", "1.0",
                "zas_test_static", "1.1",
                "zas_test_init", "1.2",
                "zas_test_linkage", "1.3",
                "zas_test_no_init", "1.4",
                "zas_test_working", "1.5");
        try (Heard log = new Heard()) {
            List<Status> found = load(rows, pretending(versions));

            for (int i = 0; i < 5; i++) {
                String mod = rows.get(i).modId();
                h.assertValueEqual(found.get(i).state(), State.FAILED, mod);
                h.assertValueEqual(log.at(Level.ERROR, mod, versions.get(mod)), 1, "errors naming " + mod);
            }
            h.assertValueEqual(log.at(Level.ERROR), 5, "errors, one for each");
            h.assertValueEqual(log.withStackTrace(Level.ERROR), 5, "errors with a stack trace");
            h.assertValueEqual(log.at(Level.WARN), 0, "warnings");
            // What was logged is the fault itself, with nothing of reflection's wrapped around it.
            // A class whose initialiser failed is a LinkageError of one kind the first time it is
            // asked for and of another from then on.
            log.assertThrown(h, "zas_test_missing", ClassNotFoundException.class);
            log.assertThrown(h, "zas_test_static", LinkageError.class);
            log.assertThrown(h, "zas_test_init", IllegalStateException.class);
            log.assertThrown(h, "zas_test_linkage", NoClassDefFoundError.class);
            log.assertThrown(h, "zas_test_no_init", NoSuchMethodException.class);

            h.assertValueEqual(found.get(5).state(), State.ACTIVE, "the row after the ones that failed");
            ModContainer zas = zas();
            h.assertTrue(HANDED.equals(List.of(zas.getEventBus(), zas)), "the working one was handed " + HANDED);
        }
        h.succeed();
    }

    /**
     * A target in range, here a mod this server really has at its real version: init is called
     * once, with the mod bus and the container, and one line at info says so. An absent target's
     * row beside it loads nothing and logs nothing at info or above.
     */
    @GameTest(template = "empty")
    public static void compat_002_only_a_supported_target_gets_its_entry_class_loaded(GameTestHelper h) {
        String real = Adapters.installedVersion(INSTALLED).orElseThrow();
        h.assertTrue(Adapters.installedVersion("zas_test_absent").isEmpty(), "a mod nobody has is installed");
        List<Row> rows = List.of(
                new Row("zas_test_absent", "[1,2)", NeverLoaded.class.getName()),
                new Row(INSTALLED, "[" + real + "]", Working.class.getName()));
        try (Heard log = new Heard()) {
            List<Status> found = load(rows, Adapters::installedVersion);

            h.assertValueEqual(found.get(0), new Status(rows.get(0), State.ABSENT, null), "the absent target's row");
            h.assertFalse(loadedTheOneThatMustNotBe, "an absent target's entry class was loaded");
            h.assertValueEqual(log.at(Level.INFO, "zas_test_absent"), 0, "lines about the absent target");

            h.assertValueEqual(found.get(1), new Status(rows.get(1), State.ACTIVE, real), "the installed one's row");
            ModContainer zas = zas();
            h.assertTrue(HANDED.equals(List.of(zas.getEventBus(), zas)), "init was handed " + HANDED);
            h.assertValueEqual(log.at(Level.INFO, INSTALLED, real), 1, "lines saying it is on");
            h.assertValueEqual(log.events.size(), 1, "lines logged in all");
        }
        h.assertFalse(Adapters.isActive(INSTALLED), "a test's row was recorded as a shipped adapter");
        h.succeed();
    }

    /**
     * The table that ships, on this server, which holds none of its targets (NFR-022): the mod
     * constructor went through every row, and no adapter is on. The compatibility matrix's run holds
     * other mods on purpose, so only the first half holds there.
     */
    @GameTest(template = "empty")
    public static void compat_002_no_shipped_adapter_is_on_without_its_target(GameTestHelper h) {
        List<Status> statuses = Adapters.statuses();
        h.assertValueEqual(statuses.stream().map(Status::row).toList(), Adapters.rows(), "the rows gone through");
        for (Status status : statuses) {
            String mod = status.row().modId();
            h.assertValueEqual(Adapters.isActive(mod), status.state() == State.ACTIVE, "isActive for " + mod);
            if (!Boolean.getBoolean("zas.compatMatrix")) {
                h.assertValueEqual(status.state(), State.ABSENT, "the adapter for " + mod);
            }
        }
        h.assertFalse(Adapters.isActive("zas_test_absent"), "a mod with no adapter has an active one");
        h.succeed();
    }

    /** What the loader logs while this is open, at info and above. */
    private static final class Heard extends AbstractAppender implements AutoCloseable {
        private static final String LOGGER = Adapters.class.getName();

        final List<LogEvent> events = new CopyOnWriteArrayList<>();
        private final Logger root = (Logger) LogManager.getRootLogger();

        Heard() {
            super("zas_gametest_adapter_log", null, null, true, Property.EMPTY_ARRAY);
            start();
            root.addAppender(this);
        }

        @Override
        public void append(LogEvent event) {
            if (LOGGER.equals(event.getLoggerName()) && event.getLevel().isMoreSpecificThan(Level.INFO)) {
                events.add(event.toImmutable());
            }
        }

        /** How many lines were logged at exactly this level with all of these texts in them. */
        int at(Level level, String... texts) {
            return (int) events.stream()
                    .filter(e -> e.getLevel() == level)
                    .map(e -> e.getMessage().getFormattedMessage())
                    .filter(line -> List.of(texts).stream().allMatch(line::contains))
                    .count();
        }

        int withStackTrace(Level level) {
            return (int) events.stream()
                    .filter(e -> e.getLevel() == level && e.getThrown() != null)
                    .count();
        }

        /** Fails unless the error naming this text carries a throwable of this type. */
        void assertThrown(GameTestHelper h, String text, Class<? extends Throwable> type) {
            for (LogEvent event : events) {
                if (event.getLevel() == Level.ERROR
                        && event.getMessage().getFormattedMessage().contains(text)) {
                    h.assertTrue(type.isInstance(event.getThrown()), text + " logged " + event.getThrown());
                    return;
                }
            }
            h.fail("no error names " + text);
        }

        @Override
        public void close() {
            root.removeAppender(this);
            stop();
        }
    }

    /** An entry class as an adapter writes one. */
    public static final class Working {
        private Working() {}

        public static void init(IEventBus modBus, ModContainer container) {
            HANDED.add(modBus);
            HANDED.add(container);
        }
    }

    /** The entry class of rows that must stay off. Loading it at all is the failure. */
    public static final class NeverLoaded {
        static {
            loadedTheOneThatMustNotBe = true;
        }

        private NeverLoaded() {}

        public static void init(IEventBus modBus, ModContainer container) {
            HANDED.add(modBus);
            HANDED.add(container);
        }
    }

    /** Fails as it is loaded, before init can be called. */
    public static final class ThrowsWhenLoaded {
        private static final Object BROKEN = broken();

        private ThrowsWhenLoaded() {}

        private static Object broken() {
            throw new IllegalArgumentException("the test adapter failing as it loads");
        }

        public static void init(IEventBus modBus, ModContainer container) {
            HANDED.add(BROKEN);
        }
    }

    /** Fails in init. */
    public static final class ThrowsInInit {
        private ThrowsInInit() {}

        public static void init(IEventBus modBus, ModContainer container) {
            throw new IllegalStateException("the test adapter failing in init");
        }
    }

    /** Fails as an adapter does when its target has dropped a class it was compiled against. */
    public static final class MeetsAMissingClass {
        private MeetsAMissingClass() {}

        public static void init(IEventBus modBus, ModContainer container) {
            throw new NoClassDefFoundError("zas_test_linkage/api/GoneInThisVersion");
        }
    }

    /** Has no init to call. */
    public static final class NoInit {
        private NoInit() {}
    }
}
