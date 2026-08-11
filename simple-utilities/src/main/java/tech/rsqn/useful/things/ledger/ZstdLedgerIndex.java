package tech.rsqn.useful.things.ledger;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Sidecar index for ZSTD ledgers: maps logical record ordinal → frame file offset +
 * uncompressed offset within that frame.
 * <p>
 * File layout (little-endian):
 * <pre>
 * magic u32 = 'ZLIX' (0x58494C5A)
 * version u32 = 1
 * entryCount u64
 * repeated entryCount times:
 *   frameFileOffset u64
 *   uncompressedOffset u64
 * </pre>
 */
final class ZstdLedgerIndex {
    static final int MAGIC = 0x58494C5A; // 'ZLIX' LE
    static final int VERSION = 1;
    static final int HEADER_SIZE = 4 + 4 + 8;
    static final int ENTRY_SIZE = 8 + 8;

    private final Path indexPath;
    private final List<Entry> entries = new ArrayList<>();
    private boolean loaded;

    ZstdLedgerIndex(Path ledgerFile) {
        this.indexPath = Path.of(ledgerFile.toString() + ".idx");
    }

    Path getIndexPath() {
        return indexPath;
    }

    synchronized int size() {
        ensureLoaded();
        return entries.size();
    }

    synchronized Entry get(int ordinal) {
        ensureLoaded();
        return entries.get(ordinal);
    }

    synchronized void appendEntries(List<Entry> newEntries) throws IOException {
        ensureLoaded();
        if (newEntries.isEmpty()) {
            return;
        }
        entries.addAll(newEntries);
        rewriteFully();
    }

    synchronized void replaceAll(List<Entry> rebuilt) throws IOException {
        entries.clear();
        entries.addAll(rebuilt);
        loaded = true;
        rewriteFully();
    }

    synchronized void clearMissingFile() {
        entries.clear();
        loaded = true;
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        try {
            loadFromDisk();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load zstd ledger index: " + indexPath, e);
        }
    }

    private void loadFromDisk() throws IOException {
        entries.clear();
        if (!Files.exists(indexPath) || Files.size(indexPath) == 0) {
            loaded = true;
            return;
        }
        try (FileChannel ch = FileChannel.open(indexPath, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, header);
            header.flip();
            int magic = header.getInt();
            int version = header.getInt();
            long count = header.getLong();
            if (magic != MAGIC) {
                throw new IOException("Invalid zstd ledger index magic in " + indexPath);
            }
            if (version != VERSION) {
                throw new IOException("Unsupported zstd ledger index version " + version);
            }
            if (count < 0 || count > Integer.MAX_VALUE) {
                throw new IOException("Invalid zstd ledger index entry count " + count);
            }
            ByteBuffer entryBuf = ByteBuffer.allocate(ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            for (long i = 0; i < count; i++) {
                entryBuf.clear();
                readFully(ch, entryBuf);
                entryBuf.flip();
                entries.add(new Entry(entryBuf.getLong(), entryBuf.getLong()));
            }
        }
        loaded = true;
    }

    private void rewriteFully() throws IOException {
        Path parent = indexPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (FileChannel ch = FileChannel.open(indexPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(MAGIC);
            header.putInt(VERSION);
            header.putLong(entries.size());
            header.flip();
            writeFully(ch, header);
            ByteBuffer entryBuf = ByteBuffer.allocate(ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            for (Entry e : entries) {
                entryBuf.clear();
                entryBuf.putLong(e.frameFileOffset);
                entryBuf.putLong(e.uncompressedOffset);
                entryBuf.flip();
                writeFully(ch, entryBuf);
            }
            ch.force(true);
        }
    }

    private static void readFully(FileChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int n = ch.read(buf);
            if (n < 0) {
                throw new EOFException("Unexpected EOF reading " + ch);
            }
        }
    }

    private static void writeFully(FileChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    static final class Entry {
        final long frameFileOffset;
        final long uncompressedOffset;

        Entry(long frameFileOffset, long uncompressedOffset) {
            this.frameFileOffset = frameFileOffset;
            this.uncompressedOffset = uncompressedOffset;
        }
    }
}
