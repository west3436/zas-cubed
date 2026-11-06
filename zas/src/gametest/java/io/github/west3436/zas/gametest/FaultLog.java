package io.github.west3436.zas.gametest;

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
 * Collects every error logged while it's open, by any logger on any thread, for NFR-013's checks that
 * a fault is logged at most once a minute. Each line is the logger's name and then the message.
 */
final class FaultLog extends AbstractAppender implements AutoCloseable {
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final Logger root = (Logger) LogManager.getRootLogger();

    FaultLog() {
        super("zas_gametest_fault_log", null, null, true, Property.EMPTY_ARRAY);
        start();
        root.addAppender(this);
    }

    /** Moves the limit's clock on, so whatever it remembers has had its minute. */
    static void aMinutePasses() {
        ThirdPartyFaults.advanceClockForTest(Duration.ofMinutes(1));
    }

    /** How many errors have been logged with this text in the logger's name or the message. */
    int naming(String text) {
        return (int) errors.stream().filter(line -> line.contains(text)).count();
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
