package com.example.footballanalysis.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class LogCaptureSession implements AutoCloseable {

    private final Logger logger;
    private final Level originalLevel;
    private final ListAppender<ILoggingEvent> appender;

    private LogCaptureSession(Class<?> loggerType, Level level) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerType);
        this.originalLevel = logger.getLevel();
        this.logger.setLevel(level);

        this.appender = new ListAppender<>();
        this.appender.start();
        this.logger.addAppender(appender);
    }

    public static LogCaptureSession capture(Class<?> loggerType, Level level) {
        return new LogCaptureSession(loggerType, level);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }
}