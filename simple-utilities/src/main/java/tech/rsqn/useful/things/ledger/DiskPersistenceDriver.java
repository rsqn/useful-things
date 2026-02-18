package tech.rsqn.useful.things.ledger;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Disk-based persistence driver.
 *
 * @param <T> The type of record stored.
 */
public class DiskPersistenceDriver<T extends Record> implements PersistenceDriver<T> {
    private final Path ledgerFile;
    private final Gson gson;
    private final Object fileLock = new Object();
    private BufferedWriter fileWriter;
    private volatile boolean started = false;
    private volatile boolean dirty = false;
    private boolean autoFlush = true;
    private int writeCountSinceFlush = 0;
    private long lastFlushTime = System.nanoTime();
    private int flushIntervalWrites = 5000;
    private long flushIntervalNanos = 5_000_000_000L;
    private final LedgerRegistry ledgerRegistry;

    public DiskPersistenceDriver(Path ledgerFile, LedgerRegistry ledgerRegistry) {
        this.ledgerFile = ledgerFile;
        this.ledgerRegistry = ledgerRegistry;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(RecordType.class, new TypeAdapter<RecordType>() {
                    @Override
                    public void write(JsonWriter out, RecordType value) throws IOException {
                        out.value(value.getValue());
                    }

                    @Override
                    public RecordType read(JsonReader in) throws IOException {
                        return RecordType.of(in.nextString());
                    }
                })
                .registerTypeAdapter(java.time.Instant.class, new TypeAdapter<java.time.Instant>() {
                    @Override
                    public void write(JsonWriter out, java.time.Instant value) throws IOException {
                        out.value(value.toString());
                    }

                    @Override
                    public java.time.Instant read(JsonReader in) throws IOException {
                        return java.time.Instant.parse(in.nextString());
                    }
                })
                .create();
    }

    public void setAutoFlush(boolean autoFlush) {
        this.autoFlush = autoFlush;
    }

    public void setFlushIntervalWrites(int flushIntervalWrites) {
        this.flushIntervalWrites = flushIntervalWrites;
    }

    public void setFlushIntervalSeconds(double seconds) {
        this.flushIntervalNanos = (long) (seconds * 1_000_000_000L);
    }

    @PostConstruct
    public void init() {
        if (ledgerFile == null) {
            throw new IllegalStateException("Ledger file path must be set");
        }
        if (ledgerRegistry == null) {
            throw new IllegalStateException("LedgerRegistry must be set");
        }
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
    public void write(T record) throws IOException {
        // Serialize outside lock
        String json = gson.toJson(record);

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
    public void read(long fromSequence, ReadCallback<T> callback) {
        if (!Files.exists(ledgerFile)) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(ledgerFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                T record = parseRecord(line);
                if (record != null) {
                    if (fromSequence != -1 && record.getSequenceId() != null && record.getSequenceId() <= fromSequence) {
                        continue;
                    }
                    if (!callback.onRecord(record)) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void readReverse(long fromSequence, ReadCallback<T> callback) {
        if (!Files.exists(ledgerFile)) {
            return;
        }

        ReverseFileIterator iterator = new ReverseFileIterator(ledgerFile);
        while (iterator.hasNext()) {
            T record = iterator.next();
            
            // If fromSequence is specified (not -1), we only want records BEFORE that sequence.
            // Since we are iterating in reverse (newest to oldest), we skip records until we find one < fromSequence.
            if (fromSequence != -1 && record.getSequenceId() != null && record.getSequenceId() >= fromSequence) {
                continue;
            }
            
            if (!callback.onRecord(record)) {
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

    @Override
    public long count() {
        if (!Files.exists(ledgerFile)) {
            return 0;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(ledgerFile.toFile()))) {
            long count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        } catch (IOException e) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private T parseRecord(String line) {
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            JsonElement typeElement = json.get("type");
            if (typeElement == null) {
                return null;
            }
            String typeStr = typeElement.getAsString();
            RecordType type = RecordType.of(typeStr);
            
            Class<? extends Record> clazz = ledgerRegistry.getRecordClass(type);
            if (clazz == null) {
                // Unknown record type
                return null;
            }
            
            return (T) gson.fromJson(json, clazz);
        } catch (Exception e) {
            // e.printStackTrace(); // Optional logging
            return null;
        }
    }

    // Inner class for reverse file iteration
    private class ReverseFileIterator implements Iterator<T> {
        private final RandomAccessFile raf;
        private long filePos;
        private final byte[] buffer;
        private int bufferPos;
        private T nextRecord;
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
            nextRecord = null;
            try {
                while (nextRecord == null) {
                    String line = readLineReverse();
                    if (line == null) break;
                    if (line.trim().isEmpty()) continue;

                    nextRecord = parseRecord(line);
                }

                if (nextRecord == null) {
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
            return nextRecord != null;
        }

        @Override
        public T next() {
            if (nextRecord == null) throw new NoSuchElementException();
            T current = nextRecord;
            advance();
            return current;
        }
    }
}
