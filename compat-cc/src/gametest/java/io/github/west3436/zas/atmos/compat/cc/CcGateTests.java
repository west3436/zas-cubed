package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.Row;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.VersionGate;
import io.github.west3436.zas.compat.VersionGate.Verdict;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
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
 * How the adapter starts (COMPAT-001, COMPAT-002), with CC: Tweaked really installed: the release
 * jar holds the adapter, {@code zas} started it, and a version outside the range would have left it
 * off. They are in a namespace of their own because they are all that runs with the content off
 * (CONT-155), where there is no device to be a peripheral.
 *
 * <p>The run says which CC: Tweaked it asked for: the oldest supported, which is also the first
 * there is for this Minecraft, or the newest. No build older than the range starts on this
 * Minecraft, so that case is the adapter loader's own, given a version by the test.
 */
@GameTestHolder(CcGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CcGateTests {
    static final String NAMESPACE = "zas_cc_gate";
    private static final String BATCH = "zas_cc_gate";
    private static final String CC = "computercraft";
    private static final String RANGE = "[1.113.0,)";

    private CcGateTests() {}

    private static Status shipped() {
        for (Status status : Adapters.statuses()) {
            if (status.row().modId().equals(CC)) {
                return status;
            }
        }
        throw new GameTestAssertException("no adapter row for CC: Tweaked in " + Adapters.statuses());
    }

    /**
     * With the CC: Tweaked this run installs, the adapter is on: the range holds the version in CC:
     * Tweaked's own metadata, and the peripherals are registered if the content is on.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_002_the_adapter_is_on_with_a_supported_cc_tweaked(GameTestHelper h) {
        Status status = shipped();
        String installed = ModList.get()
                .getModContainerById(CC)
                .orElseThrow(() -> new GameTestAssertException("CC: Tweaked isn't loaded"))
                .getModInfo()
                .getVersion()
                .toString();
        h.assertValueEqual(installed, System.getProperty("zas.cc.version"), "the CC: Tweaked this run loaded");
        h.assertValueEqual(status.row().range(), RANGE, "the range");
        h.assertValueEqual(status.version(), installed, "the version the gate read");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter");
        h.assertTrue(Adapters.isActive(CC), "the adapter isn't active");
        h.assertValueEqual(
                CcCompat.registered(), ZasAtmosMod.contentEnabled(), "whether the peripherals are registered");
        h.succeed();
    }

    /**
     * With the content off (CONT-155) the adapter registers nothing, and the server is up all the
     * same, as its running this test shows. With it on, a block that is no device is no peripheral.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_001_nothing_registers_with_the_content_off(GameTestHelper h) {
        Spot s = new Spot(h, 90);
        s.clear();
        s.set(1, 1, 1, Blocks.FURNACE.defaultBlockState());
        for (Direction side : Direction.values()) {
            Object found = s.peripheral(s.abs(1, 1, 1), side);
            h.assertTrue(
                    found == null || !(found instanceof DevicePeripheral<?>), "a furnace is a device's peripheral");
        }
        if (!ZasAtmosMod.contentEnabled()) {
            h.assertFalse(CcCompat.registered(), "the adapter registered its peripherals with the content off");
            h.assertTrue(PeripheralCapability.get() != null, "CC: Tweaked's capability is gone");
        }
        s.clear();
        h.succeed();
    }

    /**
     * With a version outside the range the adapter stays off: its entry class isn't started, one
     * warning names the mod, the version and the range, and the server carries on. The versions are
     * given by the test: the last build for the Minecraft before, which this one can't load, and
     * older ones.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_002_an_older_cc_tweaked_turns_the_adapter_off_with_one_warning(GameTestHelper h) {
        Row row = shipped().row();
        ModContainer zas = ModList.get().getModContainerById("zas").orElseThrow();
        for (String version : List.of("1.112.0", "1.112.99", "1.100.0", "1.21.1-1.113.0", "0.1")) {
            try (Heard log = new Heard()) {
                List<Status> found = Adapters.load(List.of(row), id -> Optional.of(version), zas.getEventBus(), zas);
                h.assertValueEqual(found, List.of(new Status(row, State.UNSUPPORTED, version)), "at " + version);
                h.assertValueEqual(log.at(Level.WARN, CC, version, RANGE), 1, "warnings at " + version);
                // One line in all, so nothing said the adapter started.
                h.assertValueEqual(log.events.size(), 1, "lines logged at " + version);
            }
        }
        for (String version : List.of("1.113.0", "1.113.1", "1.120.2", "1.121.0", "1.200.0", "2.0.0")) {
            h.assertValueEqual(VersionGate.check(version, RANGE), Verdict.SUPPORTED, version);
        }
        h.assertTrue(Adapters.isActive(CC), "the versions given turned the real adapter off");
        h.succeed();
    }

    /** What the adapter loader logs, at INFO and above, while this is open. */
    private static final class Heard extends AbstractAppender implements AutoCloseable {
        private static final String LOGGER = Adapters.class.getName();

        final List<LogEvent> events = new CopyOnWriteArrayList<>();
        private final Logger root = (Logger) LogManager.getRootLogger();

        Heard() {
            super("zas_cc_gametest_log", null, null, true, Property.EMPTY_ARRAY);
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
