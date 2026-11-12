package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.api.ThirdPartyFaults;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Collects every error logged while it's open, by any logger, for the checks that a script's fault
 * is logged where it should be and at most once a minute (NFR-013). Each line is the logger's name
 * and then the message.
 */
final class FaultLog extends AbstractAppender implements AutoCloseable {
    /** The name KubeJS's console for server scripts logs under. */
    static final String KUBEJS = "KubeJS Server";

    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final Logger root = (Logger) LogManager.getRootLogger();

    FaultLog() {
        super("zas_kubejs_gametest_fault_log", null, null, true, Property.EMPTY_ARRAY);
        start();
        root.addAppender(this);
    }

    /** Moves the limit's clock on, so whatever it remembers has had its minute. */
    static void aMinutePasses() {
        ThirdPartyFaults.advanceClockForTest(Duration.ofMinutes(1));
    }

    /** The errors a logger has logged with this text in them. */
    List<String> by(String logger, String text) {
        return errors.stream()
                .filter(line -> line.startsWith(logger + ": ") && line.contains(text))
                .toList();
    }

    /** Every error, for a failure's message. */
    List<String> all() {
        return List.copyOf(errors);
    }

    @Override
    public void append(LogEvent event) {
        if (event.getLevel().isMoreSpecificThan(Level.ERROR)) {
            errors.add(event.getLoggerName() + ": " + event.getMessage().getFormattedMessage());
        }
    }

    @Override
    public void close() {
        root.removeAppender(this);
        stop();
    }
}
