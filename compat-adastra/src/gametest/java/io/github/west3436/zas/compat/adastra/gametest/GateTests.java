package io.github.west3436.zas.compat.adastra.gametest;

import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.Row;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.VersionGate;
import io.github.west3436.zas.compat.VersionGate.Verdict;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * The adapter's version check (SPACE-003), with Ad Astra really installed: the release jar holds
 * the adapter, {@code zas} started it, and a version outside the range would have left it off.
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class GateTests {
    private static final String AD_ASTRA = "ad_astra";
    private static final String RANGE = "[1.16.26,1.17)";

    private GateTests() {}

    private static Status shipped(GameTestHelper h) {
        for (Status status : Adapters.statuses()) {
            if (status.row().modId().equals(AD_ASTRA)) {
                return status;
            }
        }
        throw new GameTestAssertException("no adapter row for Ad Astra in " + Adapters.statuses());
    }

    /**
     * With the Ad Astra this run installs, the adapter is on: its row's range holds the version in
     * Ad Astra's own metadata, which is its bare version with no Minecraft version in front.
     */
    @GameTest(template = "empty")
    public static void space_003_the_adapter_is_on_with_a_supported_ad_astra(GameTestHelper h) {
        Status status = shipped(h);
        String installed = ModList.get()
                .getModContainerById(AD_ASTRA)
                .orElseThrow()
                .getModInfo()
                .getVersion()
                .toString();
        h.assertValueEqual(status.row().range(), RANGE, "the range");
        h.assertValueEqual(status.version(), installed, "the version the gate read");
        h.assertValueEqual(installed, "1.16.26", "the Ad Astra this run installs");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter");
        h.assertTrue(Adapters.isActive(AD_ASTRA), "the adapter isn't active");
        h.succeed();
    }

    /**
     * With a version outside the range the adapter stays off: its entry class isn't started, one
     * warning names the mod, the version and the range, and the server carries on, as its finishing
     * this suite shows. The versions are mocked: just below the range, at its end and past it, and
     * what a Minecraft-prefixed version would read as.
     */
    @GameTest(template = "empty")
    public static void space_003_an_out_of_range_version_turns_the_adapter_off_with_one_warning(GameTestHelper h) {
        Row row = shipped(h).row();
        ModContainer zas = ModList.get().getModContainerById("zas").orElseThrow();
        for (String version : List.of("1.16.25", "1.16.0", "1.17", "1.17.0", "2.0.0", "1.21.1-1.16.26")) {
            try (Heard log = new Heard()) {
                List<Status> found = Adapters.load(List.of(row), id -> Optional.of(version), zas.getEventBus(), zas);
                h.assertValueEqual(found, List.of(new Status(row, State.UNSUPPORTED, version)), "at " + version);
                h.assertValueEqual(log.at(Level.WARN, AD_ASTRA, version, RANGE), 1, "warnings at " + version);
                // One line in all, so nothing said the adapter started.
                h.assertValueEqual(log.events.size(), 1, "lines logged at " + version);
            }
        }
        for (String version : List.of("1.16.26", "1.16.27", "1.16.100")) {
            h.assertValueEqual(VersionGate.check(version, RANGE), Verdict.SUPPORTED, version);
        }
        h.assertTrue(Adapters.isActive(AD_ASTRA), "the mocked versions turned the real adapter off");
        h.succeed();
    }

    /** What the adapter loader logs, at INFO and above, while this is open. */
    private static final class Heard extends AbstractAppender implements AutoCloseable {
        private static final String LOGGER = Adapters.class.getName();

        final List<LogEvent> events = new CopyOnWriteArrayList<>();
        private final Logger root = (Logger) LogManager.getRootLogger();

        Heard() {
            super("zas_adastra_gametest_log", null, null, true, Property.EMPTY_ARRAY);
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

        @Override
        public void close() {
            root.removeAppender(this);
            stop();
        }
    }
}
