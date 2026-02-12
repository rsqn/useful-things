package tech.rsqn.useful.things.ledger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Individual ledger for a specific event type.
 */
public class EventLedger implements AutoCloseable {
    private final EventType eventType;
    private final Path ledgerFile;
    private final LedgerConfig config;
    private final boolean autoFlush;
    private final ExecutorService notificationExecutor;
    private final Gson gson;

    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final List<Consumer<BaseEvent>> subscribers = new ArrayList<>();
    
    // Memory cache
    private final List<BaseEvent> memoryCache = new ArrayList<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private volatile boolean isMemoryEnabled = false;

    // File I/O
    private BufferedWriter fileWriter;
    private volatile boolean started = false;
    private volatile boolean keepRunning = true;

    // Flush tracking
    private int writeCountSinceFlush = 0;
    private long lastFlushTime = System.nanoTime();
    private volatile boolean dirty = false;
    private volatile boolean bulkMode = false;
    private final int flushIntervalWrites;
    private final long flushIntervalNanos;
    private final boolean flushBeforeRead;

    public EventLedger(EventType eventType, Path ledgerFile, LedgerConfig config) {
        this(eventType, ledgerFile, config, null);
    }

    public EventLedger(EventType eventType, Path ledgerFile, LedgerConfig config, ExecutorService notificationExecutor) {
        this.eventType = eventType;
        this.ledgerFile = ledgerFile;
        this.config = config;
        this.autoFlush = config.getBoolean("ledger.auto_flush", true);
        this.notificationExecutor = notificationExecutor != null ? notificationExecutor : Executors.newCachedThreadPool();
        this.gson = new GsonBuilder().create();

        this.flushIntervalWrites = config.getInt("ledger.flush_interval_writes", 5000);
        double flushSeconds = config.getDecimal("ledger.flush_interval_seconds", java.math.BigDecimal.valueOf(5.0)).doubleValue();
        this.flushIntervalNanos = (long) (flushSeconds * 1_000_000_000L);
        this.flushBeforeRead = config.getBoolean("ledger.flush_before_read", true);

        loadSequenceCounter();
    }

    public Path getLedgerPath() {
        return ledgerFile;
    }

    public void start() throws IOException {
        if (started) return;
        
        if (ledgerFile.getParent() != null) {
            Files.createDirectories(ledgerFile.getParent());
        }
        this.fileWriter = new BufferedWriter(new FileWriter(ledgerFile.toFile(), true));
        this.started = true;
        this.keepRunning = true;
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    public void stop() {
        this.keepRunning = false;
        forceFlush();
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

    public long writeEvent(Map<String, Object> data, Instant timestamp) {
        if (!keepRunning) return -1;

        long eventId = sequenceCounter.incrementAndGet();
        Instant ts = timestamp != null ? timestamp : Instant.now();

        BaseEvent event = new BaseEvent(eventType, ts, data, eventId);
        
        // Update memory cache
        if (isMemoryEnabled) {
            cacheLock.writeLock().lock();
            try {
                memoryCache.add(event);
            } finally {
                cacheLock.writeLock().unlock();
            }
        }

        // Write to file
        try {
            synchronized (this) {
                if (started && fileWriter != null) {
                    String json = gson.toJson(event.toMap());
                    fileWriter.write(json);
                    fileWriter.newLine();
                    dirty = true;
                    writeCountSinceFlush++;

                    if (!bulkMode) {
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
                } else {
                    // Fallback if not started (append mode)
                    if (ledgerFile.getParent() != null) {
                        Files.createDirectories(ledgerFile.getParent());
                    }
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(ledgerFile.toFile(), true))) {
                        String json = gson.toJson(event.toMap());
                        writer.write(json);
                        writer.newLine();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // Log error
            return -1;
        }

        notifySubscribers(event);

        return eventId;
    }

    public void forceFlush() {
        if (fileWriter != null && dirty) {
            try {
                fileWriter.flush();
                dirty = false;
                writeCountSinceFlush = 0;
                lastFlushTime = System.nanoTime();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void bulkWriteMode(Runnable operation) {
        boolean oldBulkMode = this.bulkMode;
        this.bulkMode = true;
        try {
            operation.run();
        } finally {
            this.bulkMode = oldBulkMode;
            if (!oldBulkMode) {
                forceFlush();
            }
        }
    }

    private void ensureFlushed() {
        if (dirty && flushBeforeRead) {
            forceFlush();
        }
    }

    public Stream<BaseEvent> readEvents(Integer limit) {
        ensureFlushed();

        if (isMemoryEnabled) {
            cacheLock.readLock().lock();
            try {
                // Return a copy to avoid concurrency issues if the stream is processed later
                List<BaseEvent> snapshot = new ArrayList<>(memoryCache);
                Stream<BaseEvent> s = snapshot.stream();
                if (limit != null) {
                    s = s.limit(limit);
                }
                return s;
            } finally {
                cacheLock.readLock().unlock();
            }
        }

        if (!Files.exists(ledgerFile)) {
            return Stream.empty();
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(ledgerFile.toFile()));
            Stream<String> lines = reader.lines();
            if (limit != null) {
                lines = lines.limit(limit);
            }
            
            return lines.map(this::parseEvent).filter(Objects::nonNull).onClose(() -> {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (FileNotFoundException e) {
            return Stream.empty();
        }
    }

    public Stream<BaseEvent> readEventsReverse() {
        ensureFlushed();

        if (isMemoryEnabled) {
            cacheLock.readLock().lock();
            try {
                List<BaseEvent> snapshot = new ArrayList<>(memoryCache);
                Collections.reverse(snapshot);
                return snapshot.stream();
            } finally {
                cacheLock.readLock().unlock();
            }
        }

        if (!Files.exists(ledgerFile)) {
            return Stream.empty();
        }

        // Reverse reading from file
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                new ReverseFileIterator(ledgerFile), Spliterator.ORDERED), false);
    }

    public Stream<BaseEvent> readEventsFiltered(Predicate<BaseEvent> filter) {
        return readEvents(null).filter(filter);
    }

    public Optional<BaseEvent> getLatestEvent() {
        return readEventsReverse().findFirst();
    }

    public void subscribe(Consumer<BaseEvent> subscriber) {
        synchronized (subscribers) {
            subscribers.add(subscriber);
        }
    }

    public void subscribe(Consumer<BaseEvent> subscriber, Predicate<BaseEvent> filter) {
        subscribe(event -> {
            if (filter.test(event)) {
                subscriber.accept(event);
            }
        });
    }

    private void notifySubscribers(BaseEvent event) {
        List<Consumer<BaseEvent>> subs;
        synchronized (subscribers) {
            subs = new ArrayList<>(subscribers);
        }
        
        if (subs.isEmpty()) return;

        if (notificationExecutor != null && !notificationExecutor.isShutdown()) {
            notificationExecutor.submit(() -> doNotify(subs, event));
        } else {
            doNotify(subs, event);
        }
    }

    private void doNotify(List<Consumer<BaseEvent>> subs, BaseEvent event) {
        for (Consumer<BaseEvent> sub : subs) {
            try {
                sub.accept(event);
            } catch (Exception e) {
                e.printStackTrace(); // Log error
            }
        }
    }

    BaseEvent parseEvent(String line) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = gson.fromJson(line, Map.class);
            return BaseEvent.fromMap(map);
        } catch (Exception e) {
            return null;
        }
    }

    private void loadSequenceCounter() {
        if (!Files.exists(ledgerFile)) {
            sequenceCounter.set(0);
            return;
        }

        // Tail scan for last event ID
        try (RandomAccessFile raf = new RandomAccessFile(ledgerFile.toFile(), "r")) {
            long length = raf.length();
            if (length == 0) {
                sequenceCounter.set(0);
                return;
            }

            long pos = length - 1;
            long scanSize = Math.min(4096, length);
            raf.seek(Math.max(0, length - scanSize));
            
            byte[] bytes = new byte[(int) scanSize];
            raf.readFully(bytes);
            String tail = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = tail.split("\n");
            
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                
                try {
                    BaseEvent event = parseEvent(line);
                    if (event != null && event.getEventId() != null) {
                        sequenceCounter.set(event.getEventId());
                        return;
                    }
                } catch (Exception e) {
                    // Ignore parse errors in tail scan
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Fallback to 0 if not found (or could do full scan)
        sequenceCounter.set(0);
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
    
    // Memory cache methods
    
    public void enableMemoryCache() {
        cacheLock.writeLock().lock();
        try {
            isMemoryEnabled = true;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    public void disableMemoryCache() {
        cacheLock.writeLock().lock();
        try {
            isMemoryEnabled = false;
            memoryCache.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    public int hydrate(Instant cutoffTime) {
        if (!Files.exists(ledgerFile)) return 0;
        
        // We need to read from file, so ensure we don't use memory cache for reading
        // But we want to populate memory cache
        
        // Clear existing cache
        cacheLock.writeLock().lock();
        try {
            memoryCache.clear();
            isMemoryEnabled = false; // Temporarily disable to force read from disk
        } finally {
            cacheLock.writeLock().unlock();
        }
        
        int count = 0;
        // Use a separate reader to avoid triggering the memory cache check in readEvents
        try (BufferedReader reader = new BufferedReader(new FileReader(ledgerFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                BaseEvent event = parseEvent(line);
                if (event != null) {
                    if (cutoffTime == null || !event.getTimestamp().isBefore(cutoffTime)) {
                        cacheLock.writeLock().lock();
                        try {
                            memoryCache.add(event);
                        } finally {
                            cacheLock.writeLock().unlock();
                        }
                        count++;
                    }
                }
            }
            
            cacheLock.writeLock().lock();
            try {
                if (count > 0) {
                    isMemoryEnabled = true;
                }
            } finally {
                cacheLock.writeLock().unlock();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return count;
    }
    
    public int cleanupMemory(Instant cutoffTime) {
        if (!isMemoryEnabled) return 0;
        
        cacheLock.writeLock().lock();
        try {
            int initialSize = memoryCache.size();
            memoryCache.removeIf(event -> event.getTimestamp().isBefore(cutoffTime));
            return initialSize - memoryCache.size();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("eventType", eventType.getValue());
        status.put("started", started);
        status.put("sequenceCounter", sequenceCounter.get());
        status.put("subscriberCount", subscribers.size());
        status.put("memoryEnabled", isMemoryEnabled);
        if (isMemoryEnabled) {
            cacheLock.readLock().lock();
            try {
                status.put("memoryCacheSize", memoryCache.size());
            } finally {
                cacheLock.readLock().unlock();
            }
        }
        return status;
    }
}
