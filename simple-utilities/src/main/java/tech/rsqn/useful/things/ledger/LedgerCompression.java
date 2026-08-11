package tech.rsqn.useful.things.ledger;

/**
 * On-disk compression mode for {@link DiskPersistenceDriver}.
 * <p>
 * Default is {@link #NONE} (plain JSONL). {@link #ZSTD} requires the optional
 * {@code com.github.luben:zstd-jni} dependency on the runtime classpath.
 */
public enum LedgerCompression {
    /** Uncompressed UTF-8 JSONL (one JSON object per line). */
    NONE,
    /** Streaming Zstandard frames containing JSONL payloads (multi-frame append). */
    ZSTD
}
