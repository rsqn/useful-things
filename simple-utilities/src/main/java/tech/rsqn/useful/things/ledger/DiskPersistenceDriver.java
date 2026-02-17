package tech.rsqn.useful.things.ledger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Disk-based persistence driver.
 */
public class DiskPersistenceDriver implements PersistenceDriver {
    private final Path ledgerFile;
    private final Gson gson;
    private final Object fileLock = new Object();
    private BufferedWriter fileWriter;
    private volatile boolean started = false;
    private volatile boolean dirty = false;
    private final boolean autoFlush;
    private int writeCountSinceFlush = 0;
    private long lastFlushTime = System.nanoTime();
    private final int flushIntervalWrites;
    private final long flushIntervalNanos;

    public DiskPersistenceDriver(Path ledgerFile, LedgerConfig config) {
        this.ledgerFile = ledgerFile;
        this.gson = new GsonBuilder().create();
        this.autoFlush = config.getBoolean("ledger.auto_flush", true);
        this.flushIntervalWrites = config.getInt("ledger.flush_interval_writes", 5000);
        double flushSeconds = config.getDecimal("ledger.flush_interval_seconds", java.math.BigDecimal.valueOf(5.0)).doubleValue();
        this.flushIntervalNanos = (long) (flushSeconds * 1_000_000_000L);
    }

    public void start() throws IOException {
        if (started) return;

        synchronized (fileLock) {
            if (ledgerFile.getParent() != null) {
                Files.createDirectories(ledgerFile.getParent());
            }
            this.fileWriter = new BufferedWriter(new FileWriter(ledgerFile.toFile(), true));
            this.started = true;
        }
    }

    @Override
    public void close() throws Exception {
        flush();
        synchronized (fileLock) {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
                fileWriter = null;
            }
            this.started = false;
        }
    }

    @Override
    public void write(BaseEvent event) throws IOException {
        // Serialize outside lock
        String json = gson.toJson(event.toMap());

        synchronized (fileLock) {
            if (!started) {
                // Fallback if not started (append mode)
                if (ledgerFile.getParent() != null) {
                    Files.createDirectories(ledgerFile.getParent());
                }
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(ledgerFile.toFile(), true))) {
                    writer.write(json);
                    writer.newLine();
                }
                return;
            }

            if (fileWriter != null) {
                fileWriter.write(json);
                fileWriter.newLine();
                dirty = true;
                writeCountSinceFlush++;

                if (autoFlush) {
                    fileWriter.flush();
                    dirty = false;
                    writeCountSinceFlush = 0;
                } else {
                    long now = System.nanoTime();
                    if (writeCountSinceFlush >= flushIntervalWrites || (now - lastFlushTime) >= flushIntervalNanos) {
                        fileWriter.flush();
                        dirty = false;
                        writeCountSinceFlush = 0;
                        lastFlushTime = now;
                    }
                }
            }
        }
    }

    @Override
    public void read(long fromSequence, ReadCallback<BaseEvent> callback) {
        if (!Files.exists(ledgerFile)) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(ledgerFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                BaseEvent event = parseEvent(line);
                if (event != null) {
                    if (fromSequence != -1 && event.getEventId() <= fromSequence) {
                        continue;
                    }
                    if (!callback.onEvent(event)) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void readReverse(long fromSequence, ReadCallback<BaseEvent> callback) {
        if (!Files.exists(ledgerFile)) {
            return;
        }

        ReverseFileIterator iterator = new ReverseFileIterator(ledgerFile);
        while (iterator.hasNext()) {
            BaseEvent event = iterator.next();
            
            // If fromSequence is specified (not -1), we only want events BEFORE that sequence.
            // Since we are iterating in reverse (newest to oldest), we skip events until we find one < fromSequence.
            if (fromSequence != -1 && event.getEventId() >= fromSequence) {
                continue;
            }
            
            if (!callback.onEvent(event)) {
                break;
            }
        }
    }

    @Override
    public void flush() throws IOException {
        synchronized (fileLock) {
            if (fileWriter != null && dirty) {
                fileWriter.flush();
                dirty = false;
                writeCountSinceFlush = 0;
                lastFlushTime = System.nanoTime();
            }
        }
    }

    private BaseEvent parseEvent(String line) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = gson.fromJson(line, Map.class);
            return BaseEvent.fromMap(map);
        } catch (Exception e) {
            return null;
        }
    }

    // Inner class for reverse file iteration
    private class ReverseFileIterator implements Iterator<BaseEvent> {
        private final RandomAccessFile raf;
        private long filePos;
        private final byte[] buffer;
        private int bufferPos;
        private BaseEvent nextEvent;
        private final ByteArrayOutputStream lineBuffer;

        public ReverseFileIterator(Path file) {
            try {
                this.raf = new RandomAccessFile(file.toFile(), "r");
                this.filePos = raf.length();
                this.buffer = new byte[8192]; // 8KB buffer
                this.bufferPos = -1;
                this.lineBuffer = new ByteArrayOutputStream();
                advance();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void advance() {
            nextEvent = null;
            try {
                while (nextEvent == null) {
                    String line = readLineReverse();
                    if (line == null) break;
                    if (line.trim().isEmpty()) continue;

                    nextEvent = parseEvent(line);
                }

                if (nextEvent == null) {
                    raf.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private String readLineReverse() throws IOException {
            if (filePos <= 0 && bufferPos < 0 && lineBuffer.size() == 0) {
                return null;
            }

            while (true) {
                if (bufferPos < 0) {
                    if (filePos <= 0) {
                        if (lineBuffer.size() > 0) {
                            return flushLineBuffer();
                        }
                        return null;
                    }

                    long readSize = Math.min(buffer.length, filePos);
                    filePos -= readSize;
                    raf.seek(filePos);
                    raf.readFully(buffer, 0, (int) readSize);
                    bufferPos = (int) readSize - 1;
                }

                while (bufferPos >= 0) {
                    byte b = buffer[bufferPos--];
                    if (b == '\n') {
                        if (lineBuffer.size() > 0) {
                            return flushLineBuffer();
                        }
                        // Skip empty lines or consecutive newlines
                    } else {
                        lineBuffer.write(b);
                    }
                }
            }
        }

        private String flushLineBuffer() {
            byte[] bytes = lineBuffer.toByteArray();
            lineBuffer.reset();
            // Reverse the bytes because we read them backwards
            for (int i = 0; i < bytes.length / 2; i++) {
                byte temp = bytes[i];
                bytes[i] = bytes[bytes.length - 1 - i];
                bytes[bytes.length - 1 - i] = temp;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public boolean hasNext() {
            return nextEvent != null;
        }

        @Override
        public BaseEvent next() {
            if (nextEvent == null) throw new NoSuchElementException();
            BaseEvent current = nextEvent;
            advance();
            return current;
        }
    }
}
