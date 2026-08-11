package tech.rsqn.useful.things.ledger;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Lazy boundary to {@code zstd-jni}. Kept in a separate class so
 * {@link DiskPersistenceDriver} does not resolve native types on the NONE path.
 * <p>
 * Frame I/O is seek-based: never load an entire ledger file into memory.
 */
final class ZstdNativeSupport {
    private static final int INITIAL_FRAME_READ = 64 * 1024;
    private static final int MAX_FRAME_READ = 64 * 1024 * 1024;

    private ZstdNativeSupport() {
    }

    /** One compressed zstd frame located in a file. */
    static final class LocatedFrame {
        final long fileOffset;
        final byte[] compressedBytes;
        /** File offset immediately after this frame. */
        final long nextFileOffset;

        LocatedFrame(long fileOffset, byte[] compressedBytes, long nextFileOffset) {
            this.fileOffset = fileOffset;
            this.compressedBytes = compressedBytes;
            this.nextFileOffset = nextFileOffset;
        }
    }

    static void ensureAvailable() throws IOException {
        try {
            Class.forName("com.github.luben.zstd.Zstd");
        } catch (ClassNotFoundException e) {
            throw new IOException(
                    "LedgerCompression.ZSTD requires com.github.luben:zstd-jni on the classpath",
                    e);
        } catch (NoClassDefFoundError e) {
            throw new IOException(
                    "LedgerCompression.ZSTD requires com.github.luben:zstd-jni on the classpath",
                    e);
        }
        try {
            if (com.github.luben.zstd.Zstd.defaultCompressionLevel() < 0) {
                throw new IOException("Unexpected zstd default compression level");
            }
        } catch (UnsatisfiedLinkError e) {
            throw new IOException("Failed to load zstd-jni native library", e);
        }
    }

    static OutputStream wrappingCompressor(OutputStream out, int level) throws IOException {
        ensureAvailable();
        ZstdOutputStream zos = new ZstdOutputStream(out, level);
        zos.setCloseFrameOnFlush(true);
        return zos;
    }

    static InputStream wrappingDecompressor(InputStream in) throws IOException {
        ensureAvailable();
        return new ZstdInputStream(in);
    }

    /**
     * Reads exactly one complete zstd frame starting at {@code fileOffset} without loading
     * the rest of the file. Grows a bounded buffer until {@code findFrameCompressedSize} succeeds.
     *
     * @throws IOException if the frame is truncated/corrupt or exceeds {@link #MAX_FRAME_READ}
     */
    static LocatedFrame readFrameAt(Path file, long fileOffset) throws IOException {
        ensureAvailable();
        long fileSize;
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            fileSize = ch.size();
            if (fileOffset < 0 || fileOffset >= fileSize) {
                throw new IOException("Frame offset " + fileOffset + " out of range for " + file
                        + " (size " + fileSize + ")");
            }
            if (fileSize - fileOffset < 4) {
                throw new IOException("Truncated zstd frame header at offset " + fileOffset + " in " + file);
            }

            int window = (int) Math.min(INITIAL_FRAME_READ, fileSize - fileOffset);
            while (true) {
                byte[] buf = new byte[window];
                ch.position(fileOffset);
                readFully(ch, ByteBuffer.wrap(buf));

                if (magicAt(buf, 0) != DiskPersistenceDriver.ZSTD_MAGIC) {
                    throw new IOException("Invalid zstd magic at offset " + fileOffset + " in " + file);
                }

                long compressedSize = tryFindFrameCompressedSize(buf);
                if (compressedSize > 0) {
                    if (compressedSize > MAX_FRAME_READ) {
                        throw new IOException("Zstd frame at offset " + fileOffset
                                + " exceeds max frame read (" + MAX_FRAME_READ + ")");
                    }
                    if (fileOffset + compressedSize > fileSize) {
                        throw new IOException("Truncated zstd frame at offset " + fileOffset + " in " + file);
                    }
                    if (compressedSize <= window) {
                        byte[] frame = compressedSize == window
                                ? buf
                                : Arrays.copyOf(buf, (int) compressedSize);
                        return new LocatedFrame(fileOffset, frame, fileOffset + compressedSize);
                    }
                    // Size known but window too small — read exact frame length once.
                    byte[] exact = new byte[(int) compressedSize];
                    ch.position(fileOffset);
                    readFully(ch, ByteBuffer.wrap(exact));
                    return new LocatedFrame(fileOffset, exact, fileOffset + compressedSize);
                }

                // Need more bytes (incomplete frame in window), or hard error.
                if (fileOffset + window >= fileSize) {
                    throw new IOException("Corrupt or truncated zstd frame at offset " + fileOffset
                            + " in " + file);
                }
                long nextWindow = Math.min((long) window * 2L, fileSize - fileOffset);
                if (nextWindow > MAX_FRAME_READ) {
                    throw new IOException("Zstd frame at offset " + fileOffset
                            + " exceeds max frame read (" + MAX_FRAME_READ + ") while probing size");
                }
                if (nextWindow <= window) {
                    throw new IOException("Corrupt or truncated zstd frame at offset " + fileOffset
                            + " in " + file);
                }
                window = (int) nextWindow;
            }
        }
    }

    static byte[] decompressExactFrame(byte[] frameBytes) throws IOException {
        ensureAvailable();
        try {
            try (InputStream zin = wrappingDecompressor(new ByteArrayInputStream(frameBytes));
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                zin.transferTo(bos);
                return bos.toByteArray();
            }
        } catch (RuntimeException e) {
            throw new IOException("zstd decompressFrame failed", e);
        }
    }

    /** @return compressed size, or -1 if the buffer does not yet contain a complete frame */
    private static long tryFindFrameCompressedSize(byte[] data) throws IOException {
        try {
            long size = com.github.luben.zstd.Zstd.findFrameCompressedSize(data, 0, data.length);
            if (com.github.luben.zstd.Zstd.isError(size)) {
                String name = com.github.luben.zstd.Zstd.getErrorName(size);
                // Incomplete source is expected while growing the window.
                if (name != null && (name.contains("srcSize") || name.contains("memory")
                        || name.contains("prefix") || name.contains("frame"))) {
                    // Distinguish hard corruption vs need-more: if error is clearly corruption, throw.
                    // srcSize_wrong typically means need more data when buffer is a prefix.
                    if (name.contains("srcSize")) {
                        return -1;
                    }
                }
                throw new IOException("zstd findFrameCompressedSize error: " + name);
            }
            return size;
        } catch (com.github.luben.zstd.ZstdException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.toLowerCase().contains("srcsize") || msg.toLowerCase().contains("source size")) {
                return -1;
            }
            throw new IOException("zstd findFrameCompressedSize failed", e);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.toLowerCase().contains("srcsize") || msg.toLowerCase().contains("source size")) {
                return -1;
            }
            throw new IOException("zstd findFrameCompressedSize failed", e);
        }
    }

    private static int magicAt(byte[] data, int pos) {
        return (data[pos] & 0xff)
                | ((data[pos + 1] & 0xff) << 8)
                | ((data[pos + 2] & 0xff) << 16)
                | ((data[pos + 3] & 0xff) << 24);
    }

    private static void readFully(FileChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int n = ch.read(buf);
            if (n < 0) {
                throw new IOException("Unexpected EOF reading " + ch);
            }
        }
    }
}
