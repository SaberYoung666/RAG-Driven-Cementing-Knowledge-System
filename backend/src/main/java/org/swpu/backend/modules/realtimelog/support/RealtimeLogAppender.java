package org.swpu.backend.modules.realtimelog.support;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import org.swpu.backend.modules.realtimelog.model.RealtimeLogEntry;

public class RealtimeLogAppender extends AppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent event) {
        if (event == null) {
            return;
        }
        RealtimeLogBridge.publish(new RealtimeLogEntry(
                "log",
                "backend",
                event.getLevel() == null ? "INFO" : event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                event.getFormattedMessage(),
                Instant.ofEpochMilli(event.getTimeStamp()).toString(),
                toStackTrace(event.getThrowableProxy())
        ));
    }

    private String toStackTrace(IThrowableProxy throwableProxy) {
        if (throwableProxy == null) {
            return null;
        }
        Throwable throwable = throwableProxy instanceof ch.qos.logback.classic.spi.ThrowableProxy proxy ? proxy.getThrowable() : null;
        if (throwable == null) {
            return throwableProxy.getClassName() + ": " + throwableProxy.getMessage();
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
