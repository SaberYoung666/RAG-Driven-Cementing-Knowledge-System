package org.swpu.backend.modules.realtimeconsole.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.swpu.backend.modules.realtimeconsole.model.RealtimeConsoleEntry;

public final class ConsoleOutputCapture {
    private static final Object LOCK = new Object();
    private static boolean installed = false;

    private ConsoleOutputCapture() {
    }

    public static void install(String source) {
        synchronized (LOCK) {
            if (installed) {
                return;
            }
            installed = true;
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            System.setOut(createPrintStream(originalOut, source));
            System.setErr(createPrintStream(originalErr, source));
        }
    }

    private static PrintStream createPrintStream(PrintStream target, String source) {
        return new PrintStream(new MirroringOutputStream(target, source), true, StandardCharsets.UTF_8);
    }

    private static final class MirroringOutputStream extends OutputStream {
        private final PrintStream target;
        private final String source;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private MirroringOutputStream(PrintStream target, String source) {
            this.target = target;
            this.source = source;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            target.write(b);
            buffer.write(b);
            publishCompletedChunks(false);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            target.write(b, off, len);
            buffer.write(b, off, len);
            publishCompletedChunks(false);
        }

        @Override
        public synchronized void flush() throws IOException {
            target.flush();
            publishCompletedChunks(true);
        }

        @Override
        public synchronized void close() throws IOException {
            flush();
        }

        private void publishCompletedChunks(boolean flushPartial) {
            byte[] bytes = buffer.toByteArray();
            int start = 0;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == '\n') {
                    publish(bytes, start, i + 1 - start);
                    start = i + 1;
                }
            }
            buffer.reset();
            if (start < bytes.length) {
                buffer.write(bytes, start, bytes.length - start);
            }
            if (flushPartial && buffer.size() > 0) {
                byte[] partial = buffer.toByteArray();
                buffer.reset();
                publish(partial, 0, partial.length);
            }
        }

        private void publish(byte[] bytes, int offset, int length) {
            if (length <= 0) {
                return;
            }
            String raw = new String(bytes, offset, length, StandardCharsets.UTF_8);
            if (raw.isEmpty()) {
                return;
            }
            RealtimeConsoleBridge.publish(RealtimeConsoleEntry.chunk(source, raw));
        }
    }
}
