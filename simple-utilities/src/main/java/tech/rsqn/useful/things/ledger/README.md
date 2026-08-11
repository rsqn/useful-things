# Ledger disk compression (ZSTD)

Optional **streaming Zstandard** compression for `DiskPersistenceDriver`. Default remains uncompressed JSONL.

## When to enable

Use `LedgerCompression.ZSTD` for high-volume append-only ledgers (e.g. order-book snapshots) where JSONL compresses well. Empirically ~15–20× on repetitive market data.

Do **not** enable by default for small ledgers or when reverse-read tooling outside this driver must treat the file as plain text.

## Dependency

`zstd-jni` is an **optional** Maven dependency of `simple-utilities`. Consumers that enable ZSTD must declare:

```xml
<dependency>
  <groupId>com.github.luben</groupId>
  <artifactId>zstd-jni</artifactId>
  <version>1.5.7-13</version>
</dependency>
```

`start()` fails fast with a clear `IOException` if natives/API are missing when ZSTD is selected.

## Configuration

```java
DiskPersistenceDriver<MyRecord> driver = new DiskPersistenceDriver<>(path, registry);
driver.setCompression(LedgerCompression.ZSTD);
driver.setZstdLevel(3);                 // default 3; 1–19; ≥10 archive-oriented
driver.setZstdFrameFlushBytes(1_048_576); // default ~1 MiB uncompressed per frame
driver.setAutoFlush(false);             // recommended for ratio
driver.setFlushIntervalWrites(5000);
driver.init();
driver.start();
```

Path is caller-owned. Recommended names:

- uncompressed: `market_order_books.jsonl`
- compressed: `market_order_books.jsonl.zst`
- sidecar index: `market_order_books.jsonl.zst.idx`

## Write / flush / shutdown

- Payload inside frames remains JSONL (Gson line + `\n`).
- Flush / auto-flush / byte threshold **ends the current zstd frame** so the file is a concatenation of complete frames.
- **Clean shutdown:** always `flush()` and `close()` so the last frame is finished and the `.idx` is updated. Killing the process mid-frame leaves a truncated trailing frame.
- On restart, a **new** compressor appends **new** frames (never continues a half-written frame).
- Writing with ZSTD before `start()` throws `IllegalStateException` (no one-shot uncompressed append into a `.zst` file).

`autoFlush=true` with ZSTD ends a frame very often and hurts ratio. Prefer batch flush intervals; still call orderly `close()` on shutdown.

## Read / reverse / count

- Forward `read` decompresses transparently (config and/or zstd magic `28 B5 2F FD`).
- `readReverse` and `count` use the sidecar `.idx` (never scan compressed bytes as JSONL newlines).
- Reverse/index rebuild **seek and read one zstd frame at a time** — they do not load the whole ledger into memory.
- **Truncated or corrupt trailing frame:** fail the entire `start()` rebuild / `read` / `count` with `IOException` (no silent salvage). Repair offline (truncate to last good frame or restore backup), then reopen.

## Migration

Existing plain `.jsonl` files are unchanged. To archive offline, use CLI `zstd`. The live driver is for **new** compressed writes, not in-place recompression of historical files.
