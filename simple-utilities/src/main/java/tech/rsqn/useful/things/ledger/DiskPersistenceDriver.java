package tech.rsqn.useful.things.ledger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Disk-based persistence driver.
 * <p>
 * Supports optional streaming {@link LedgerCompression#ZSTD} compression. Default is
 * {@link LedgerCompression#NONE} (plain JSONL). When ZSTD is enabled, logical records remain
 * JSONL inside concatenated zstd frames; reverse read and {@link #count()} use a sidecar
 * {@code .idx} file. See package {@code ledger/README.md}.
 *
 * @param <T> The type of record stored.
 */
public class DiskPersistenceDriver<T extends Record> implements PersistenceDriver<T> {
    private static final Logger LOG = Logger.getLogger(DiskPersistenceDriver.class.getName());

    /** Buffer size for {@link #count()} newline scanning (single-byte delimiters, JSONL lines). */
    private static final int COUNT_SCAN_BUFFER_SIZE = 32 * 1024;
    static final int ZSTD_MAGIC = 0xFD2FB528;
    static final int DEFAULT_ZSTD_LEVEL = 3;
    static final int DEFAULT_ZSTD_FRAME_FLUSH_BYTES = 1_048_576;

    private final Path ledgerFile;
    private final Gson gson;
    private final Object fileLock = new Object();
    private BufferedWriter fileWriter;
    private FileOutputStream fileOutputStream;
    private OutputStream compressedOutput;
    private volatile boolean started = false;
    private volatile boolean dirty = false;
    private boolean autoFlush = true;
    private int writeCountSinceFlush = 0;
    private long lastFlushTime = System.nanoTime();
    private int flushIntervalWrites = 5000;
    private long flushIntervalNanos = 5_000_000_000L;
    private final LedgerRegistry ledgerRegistry;

    private LedgerCompression compression = LedgerCompression.NONE;
    private int zstdLevel = DEFAULT_ZSTD_LEVEL;
    private int zstdFrameFlushBytes = DEFAULT_ZSTD_FRAME_FLUSH_BYTES;
    private ZstdLedgerIndex zstdIndex;
    private long currentFrameFileOffset;
    private long uncompressedBytesInFrame;
    private final List<ZstdLedgerIndex.Entry> pendingIndexEntries = new ArrayList<>();

    public DiskPersistenceDriver(Path ledgerFile, LedgerRegistry ledgerRegistry) {
        this.ledgerFile = ledgerFile;
        this.ledgerRegistry = ledgerRegistry;
        this.gson = LedgerGson.create();
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

    /**
     * Sets on-disk compression. Default {@link LedgerCompression#NONE}.
     * Must be called before {@link #start()}.
     *
     * @param compression compression mode (not null)
     * @throws IllegalArgumentException if compression is null
     * @throws IllegalStateException if the driver has already been started
     */
    public void setCompression(LedgerCompression compression) {
        if (compression == null) {
            throw new IllegalArgumentException("compression must not be null");
        }
        synchronized (fileLock) {
            if (started) {
                throw new IllegalStateException("Cannot change compression after start()");
            }
            this.compression = compression;
        }
    }

    /**
     * Zstd compression level for live capture. Default {@value #DEFAULT_ZSTD_LEVEL}.
     * Levels ≥ 10 are archive-oriented (higher CPU).
     *
     * @param level zstd level in {@code [1, 19]}
     * @throws IllegalArgumentException if level is out of range
     * @throws IllegalStateException if the driver has already been started
     */
    public void setZstdLevel(int level) {
        if (level < 1 || level > 19) {
            throw new IllegalArgumentException("zstd level must be in [1, 19], got " + level);
        }
        synchronized (fileLock) {
            if (started) {
                throw new IllegalStateException("Cannot change zstd level after start()");
            }
            this.zstdLevel = level;
        }
    }

    /**
     * Maximum uncompressed bytes buffered in the current zstd frame before a frame is ended.
     * Default {@value #DEFAULT_ZSTD_FRAME_FLUSH_BYTES}. Aligns with batch flush to preserve ratio.
     *
     * @param bytes positive byte threshold
     * @throws IllegalArgumentException if bytes &lt;= 0
     * @throws IllegalStateException if the driver has already been started
     */
    public void setZstdFrameFlushBytes(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("zstdFrameFlushBytes must be > 0");
        }
        synchronized (fileLock) {
            if (started) {
                throw new IllegalStateException("Cannot change zstdFrameFlushBytes after start()");
            }
            this.zstdFrameFlushBytes = bytes;
        }
    }

    public LedgerCompression getCompression() {
        return compression;
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
        if (started) {
            return;
        }

        synchronized (fileLock) {
            if (started) {
                return;
            }
            if (ledgerFile.getParent() != null) {
                Files.createDirectories(ledgerFile.getParent());
            }
            if (compression == LedgerCompression.ZSTD) {
                ZstdNativeSupport.ensureAvailable();
                this.zstdIndex = new ZstdLedgerIndex(ledgerFile);
                reconcileZstdIndexOnStart();
                openZstdAppendWriter();
            } else {
                this.fileWriter = new BufferedWriter(new FileWriter(ledgerFile.toFile(), true));
            }
            this.started = true;
        }
    }

    private void openZstdAppendWriter() throws IOException {
        this.fileOutputStream = new FileOutputStream(ledgerFile.toFile(), true);
        this.currentFrameFileOffset = fileOutputStream.getChannel().position();
        this.uncompressedBytesInFrame = 0;
        this.pendingIndexEntries.clear();
        this.compressedOutput = ZstdNativeSupport.wrappingCompressor(fileOutputStream, zstdLevel);
        this.dirty = false;
        this.writeCountSinceFlush = 0;
        this.lastFlushTime = System.nanoTime();
    }

    /**
     * Rebuild index when missing; if present, verify the last frame and scan any trailing
     * complete frames after the index (crash between frame flush and index write).
     * Truncated/corrupt frames fail the entire start (strict).
     * Frame I/O is seek-based — never loads the whole ledger into memory.
     */
    private void reconcileZstdIndexOnStart() throws IOException {
        if (!Files.exists(ledgerFile) || Files.size(ledgerFile) == 0) {
            zstdIndex.clearMissingFile();
            return;
        }
        if (zstdIndex.size() == 0) {
            List<ZstdLedgerIndex.Entry> rebuilt = rebuildIndexFromLedger();
            zstdIndex.replaceAll(rebuilt);
            return;
        }
        long fileSize = Files.size(ledgerFile);
        ZstdLedgerIndex.Entry last = zstdIndex.get(zstdIndex.size() - 1);
        ZstdNativeSupport.LocatedFrame lastFrame;
        try {
            lastFrame = ZstdNativeSupport.readFrameAt(ledgerFile, last.frameFileOffset);
            ZstdNativeSupport.decompressExactFrame(lastFrame.compressedBytes);
        } catch (IOException e) {
            throw new IOException("Corrupt or truncated zstd frame while verifying index for "
                    + ledgerFile, e);
        }
        long pos = lastFrame.nextFileOffset;
        if (pos > fileSize) {
            throw new IOException("Truncated zstd frame at offset " + last.frameFileOffset
                    + " in " + ledgerFile);
        }
        if (pos == fileSize) {
            return;
        }
        List<ZstdLedgerIndex.Entry> extra = new ArrayList<>();
        while (pos < fileSize) {
            ZstdNativeSupport.LocatedFrame frame;
            try {
                frame = ZstdNativeSupport.readFrameAt(ledgerFile, pos);
            } catch (IOException e) {
                throw new IOException("Corrupt or truncated zstd frame at offset " + pos
                        + " in " + ledgerFile, e);
            }
            byte[] uncompressed = ZstdNativeSupport.decompressExactFrame(frame.compressedBytes);
            addLinesForFrame(extra, frame.fileOffset, uncompressed);
            pos = frame.nextFileOffset;
        }
        if (!extra.isEmpty()) {
            LOG.log(Level.INFO, "Extended zstd ledger index for {0} with {1} trailing entries",
                    new Object[]{ledgerFile, extra.size()});
            zstdIndex.appendEntries(extra);
        }
    }

    private List<ZstdLedgerIndex.Entry> rebuildIndexFromLedger() throws IOException {
        List<ZstdLedgerIndex.Entry> rebuilt = new ArrayList<>();
        long fileSize = Files.size(ledgerFile);
        long pos = 0;
        while (pos < fileSize) {
            ZstdNativeSupport.LocatedFrame frame;
            try {
                frame = ZstdNativeSupport.readFrameAt(ledgerFile, pos);
            } catch (IOException e) {
                throw new IOException("Corrupt or truncated zstd frame at offset " + pos
                        + " in " + ledgerFile, e);
            }
            byte[] uncompressed = ZstdNativeSupport.decompressExactFrame(frame.compressedBytes);
            addLinesForFrame(rebuilt, frame.fileOffset, uncompressed);
            pos = frame.nextFileOffset;
        }
        return rebuilt;
    }

    private static void addLinesForFrame(List<ZstdLedgerIndex.Entry> out, long frameOffset, byte[] uncompressed) {
        long offset = 0;
        int i = 0;
        while (i < uncompressed.length) {
            int start = i;
            while (i < uncompressed.length && uncompressed[i] != '\n') {
                i++;
            }
            boolean endedWithNl = i < uncompressed.length;
            if (endedWithNl) {
                i++;
            }
            int contentLen = (endedWithNl ? i - 1 : i) - start;
            if (contentLen > 0) {
                out.add(new ZstdLedgerIndex.Entry(frameOffset, offset));
            }
            offset = i;
            if (!endedWithNl) {
                break;
            }
        }
    }

    @Override
    public void close() throws Exception {
        flush();
        synchronized (fileLock) {
            if (compression == LedgerCompression.ZSTD) {
                closeZstdWriter();
            } else if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Error closing ledger file writer", e);
                }
                fileWriter = null;
            }
            this.started = false;
        }
    }

    private void closeZstdWriter() {
        if (compressedOutput != null) {
            try {
                compressedOutput.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing zstd ledger stream", e);
            }
            compressedOutput = null;
        }
        fileOutputStream = null;
    }

    @Override
    public void write(T record) throws IOException {
        String json = gson.toJson(record);

        synchronized (fileLock) {
            if (!started) {
                if (compression == LedgerCompression.ZSTD) {
                    throw new IllegalStateException(
                            "Cannot write with LedgerCompression.ZSTD before start(); "
                                    + "refusing one-shot uncompressed append into a compressed ledger");
                }
                if (ledgerFile.getParent() != null) {
                    Files.createDirectories(ledgerFile.getParent());
                }
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(ledgerFile.toFile(), true))) {
                    writer.write(json);
                    writer.newLine();
                }
                return;
            }

            if (compression == LedgerCompression.ZSTD) {
                writeZstdRecord(json);
            } else if (fileWriter != null) {
                fileWriter.write(json);
                fileWriter.newLine();
                dirty = true;
                writeCountSinceFlush++;
                maybeFlushUnlocked();
            }
        }
    }

    private void writeZstdRecord(String json) throws IOException {
        byte[] line = (json + "\n").getBytes(StandardCharsets.UTF_8);
        pendingIndexEntries.add(new ZstdLedgerIndex.Entry(currentFrameFileOffset, uncompressedBytesInFrame));
        compressedOutput.write(line);
        uncompressedBytesInFrame += line.length;
        dirty = true;
        writeCountSinceFlush++;

        boolean sizeTrigger = uncompressedBytesInFrame >= zstdFrameFlushBytes;
        if (autoFlush || sizeTrigger) {
            endFrameAndPersistIndex();
        } else {
            long now = System.nanoTime();
            if (writeCountSinceFlush >= flushIntervalWrites || (now - lastFlushTime) >= flushIntervalNanos) {
                endFrameAndPersistIndex();
            }
        }
    }

    private void maybeFlushUnlocked() throws IOException {
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

    /**
     * Ends the current zstd frame, flushes the file, and persists pending index entries.
     * Starts the next frame at the new file position.
     */
    private void endFrameAndPersistIndex() throws IOException {
        if (compressedOutput == null) {
            return;
        }
        compressedOutput.flush();
        fileOutputStream.flush();
        fileOutputStream.getFD().sync();
        if (!pendingIndexEntries.isEmpty()) {
            zstdIndex.appendEntries(new ArrayList<>(pendingIndexEntries));
            pendingIndexEntries.clear();
        }
        currentFrameFileOffset = fileOutputStream.getChannel().position();
        uncompressedBytesInFrame = 0;
        dirty = false;
        writeCountSinceFlush = 0;
        lastFlushTime = System.nanoTime();
    }

    @Override
    public void read(long fromSequence, ReadCallback<T> callback) {
        if (!Files.exists(ledgerFile)) {
            return;
        }

        try {
            if (shouldReadAsZstd()) {
                readZstdForward(fromSequence, callback);
            } else {
                try (BufferedReader reader = new BufferedReader(new FileReader(ledgerFile.toFile()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!dispatchParsed(line, fromSequence, callback)) {
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (compression == LedgerCompression.ZSTD || fileHasZstdMagicQuiet()) {
                throw new UncheckedIOException("Error reading from ledger " + ledgerFile, e);
            }
            LOG.log(Level.SEVERE, "Error reading from ledger", e);
        }
    }

    private boolean fileHasZstdMagicQuiet() {
        try {
            return fileStartsWithZstdMagic(ledgerFile);
        } catch (IOException e) {
            return false;
        }
    }

    private void readZstdForward(long fromSequence, ReadCallback<T> callback) throws IOException {
        try (InputStream fin = Files.newInputStream(ledgerFile);
             InputStream zin = ZstdNativeSupport.wrappingDecompressor(fin);
             BufferedReader reader = new BufferedReader(new InputStreamReader(zin, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!dispatchParsed(line, fromSequence, callback)) {
                    break;
                }
            }
        }
    }

    private boolean dispatchParsed(String line, long fromSequence, ReadCallback<T> callback) {
        T record = parseRecord(line);
        if (record == null) {
            return true;
        }
        if (fromSequence != -1 && record.getSequenceId() != null && record.getSequenceId() <= fromSequence) {
            return true;
        }
        return callback.onRecord(record);
    }

    private boolean shouldReadAsZstd() throws IOException {
        if (compression == LedgerCompression.ZSTD) {
            return Files.size(ledgerFile) > 0;
        }
        return fileStartsWithZstdMagic(ledgerFile);
    }

    static boolean fileStartsWithZstdMagic(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) < 4) {
            return false;
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] m = in.readNBytes(4);
            if (m.length < 4) {
                return false;
            }
            int magic = (m[0] & 0xff) | ((m[1] & 0xff) << 8) | ((m[2] & 0xff) << 16) | ((m[3] & 0xff) << 24);
            return magic == ZSTD_MAGIC;
        }
    }

    @Override
    public void readReverse(long fromSequence, ReadCallback<T> callback) {
        if (!Files.exists(ledgerFile)) {
            return;
        }

        try {
            if (shouldReadAsZstd()) {
                readZstdReverse(fromSequence, callback);
            } else {
                ReverseFileIterator iterator = new ReverseFileIterator(ledgerFile);
                while (iterator.hasNext()) {
                    T record = iterator.next();
                    if (fromSequence != -1 && record.getSequenceId() != null && record.getSequenceId() >= fromSequence) {
                        continue;
                    }
                    if (!callback.onRecord(record)) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading ledger in reverse " + ledgerFile, e);
        }
    }

    private void readZstdReverse(long fromSequence, ReadCallback<T> callback) throws IOException {
        ensureZstdIndexReady();
        int n = zstdIndex.size();
        for (int i = n - 1; i >= 0; i--) {
            ZstdLedgerIndex.Entry entry = zstdIndex.get(i);
            String line = readLineAtIndexEntry(entry);
            T record = parseRecord(line);
            if (record == null) {
                continue;
            }
            if (fromSequence != -1 && record.getSequenceId() != null && record.getSequenceId() >= fromSequence) {
                continue;
            }
            if (!callback.onRecord(record)) {
                break;
            }
        }
    }

    private void ensureZstdIndexReady() throws IOException {
        if (zstdIndex == null) {
            zstdIndex = new ZstdLedgerIndex(ledgerFile);
        }
        if (zstdIndex.size() == 0 && Files.exists(ledgerFile) && Files.size(ledgerFile) > 0) {
            zstdIndex.replaceAll(rebuildIndexFromLedger());
        }
    }

    private String readLineAtIndexEntry(ZstdLedgerIndex.Entry entry) throws IOException {
        ZstdNativeSupport.LocatedFrame frame =
                ZstdNativeSupport.readFrameAt(ledgerFile, entry.frameFileOffset);
        byte[] uncompressed = ZstdNativeSupport.decompressExactFrame(frame.compressedBytes);
        int start = (int) entry.uncompressedOffset;
        if (start < 0 || start >= uncompressed.length) {
            throw new IOException("Index uncompressed offset out of range: " + entry.uncompressedOffset);
        }
        int end = start;
        while (end < uncompressed.length && uncompressed[end] != '\n') {
            end++;
        }
        return new String(uncompressed, start, end - start, StandardCharsets.UTF_8);
    }

    @Override
    public void flush() throws IOException {
        synchronized (fileLock) {
            if (compression == LedgerCompression.ZSTD) {
                if (compressedOutput != null
                        && (dirty || !pendingIndexEntries.isEmpty() || uncompressedBytesInFrame > 0)) {
                    endFrameAndPersistIndex();
                }
            } else if (fileWriter != null && dirty) {
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
        try {
            if (shouldReadAsZstd()) {
                ensureZstdIndexReady();
                return zstdIndex.size();
            }
            return countNewlinesPlain();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error counting ledger lines", e);
            if (compression == LedgerCompression.ZSTD || fileHasZstdMagicQuiet()) {
                throw new UncheckedIOException(e);
            }
            return -1;
        }
    }

    private long countNewlinesPlain() throws IOException {
        try (InputStream in = Files.newInputStream(ledgerFile)) {
            byte[] buf = new byte[COUNT_SCAN_BUFFER_SIZE];
            long newlineCount = 0;
            int lastByte = -1;
            int n;
            while ((n = in.read(buf)) != -1) {
                for (int i = 0; i < n; i++) {
                    if (buf[i] == '\n') {
                        newlineCount++;
                    }
                }
                lastByte = buf[n - 1] & 0xFF;
            }
            if (lastByte == -1) {
                return 0;
            }
            if (lastByte != '\n') {
                newlineCount++;
            }
            return newlineCount;
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
                return null;
            }

            return (T) gson.fromJson(json, clazz);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error parsing record: " + line, e);
            return null;
        }
    }

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
                this.buffer = new byte[8192];
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
                    if (line == null) {
                        break;
                    }
                    if (line.trim().isEmpty()) {
                        continue;
                    }
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
                    } else {
                        lineBuffer.write(b);
                    }
                }
            }
        }

        private String flushLineBuffer() {
            byte[] bytes = lineBuffer.toByteArray();
            lineBuffer.reset();
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
            if (nextRecord == null) {
                throw new NoSuchElementException();
            }
            T current = nextRecord;
            advance();
            return current;
        }
    }
}
