# Ledger performance bench report

**Dates:** 2026-08-15 (Rounds 1–2), 2026-08-16 (Round 3 + ZSTD index fix)  
**Machine:** macOS, ~16 GiB RAM  
**Harness:** `/tmp/ledger-bench/LedgerMatrixBench.java` (one-shot; not CI)  
**Raw TSV:**  
- Round 1: `/tmp/ledger-bench/results.tsv`  
- Round 2: `/tmp/ledger-bench/results-round2.tsv`  
- Round 3 (full NONE+ZSTD): `/tmp/ledger-bench/results-full.tsv`  
**Unit tests (2026-08-16):** `mvn -pl simple-utilities test` → **95 run, 0 fail** (includes `ZstdLedgerIndexTest`, `DiskPersistenceDriverZstdTest`)

## Executive summary (for another AI)

| Round | Payload | Rows | Comp | DiskLedger write/s | MemoryLedger write/s | WriteBehind write/s | On-disk size |
|-------|---------|------|------|--------------------|----------------------|---------------------|--------------|
| 1 (oversized) | 4 KiB | 10M | NONE | 119,520 | 116,263 | 95,990 | 39.12 GiB |
| 2 / 3 primary | **1024 B** | 10M | **NONE** | **~375–397k** | **~375–384k** | **~314–336k** | **10.51 GiB** |
| 2 / 3 bound | **256 B** | 10M | **NONE** | **~939–950k** | **~833–847k** | **~718–723k** | **3.36 GiB** |
| **3 primary** | **1024 B** | 10M | **ZSTD** | **111,525** | **110,143** | **94,938** | **186 MiB** |
| **3 bound** | **256 B** | 10M | **ZSTD** | **191,026** | **195,354** | **164,463** | **183 MiB** |

- Real dispatch mix ≈ **82% order books ~955 B**; use **1024 B** as the backtest proxy.
- **ZSTD was broken** for large ledgers: `ZstdLedgerIndex.appendEntries()` rewrote the whole `.idx` + `force(true)` every frame → O(n²) (~307 write ops/s). **Fixed** with append-only index updates (`ZstdLedgerIndex.java` + `ZstdLedgerIndexTest`).
- After fix, ZSTD write at 1024 B is ~**95–112k/s** (~5–6× a 20k/s stage02 budget) with **~56× smaller** files than NONE (10.51 GiB → 186 MiB on highly compressible `'x'` payload).
- ZSTD **forward read** stays fast (~335k/s); **reverse read** remains slow (~3k/s) — separate from the index rewrite bug (frame-at-a-time reverse).
- Memory/WB **API forward** = **10k** cache window only; use disk fwd/rev for full history.

---

## 1. Purpose

Compare `DiskLedger`, `MemoryLedger`, and `WriteBehindMemoryLedger` write/read throughput under identical workloads so results can be handed to another AI / correlated with pysol backtest ledger stages (~20k/s stage02).

---

## 2. Real archive line sizes (context for synthetic payloads)

From production/archive sampling (10k-sample), used to choose synthetic sizes:

| Stream | mean | p50 | p90 | share of dispatch |
|--------|------|-----|-----|-------------------|
| `market_order_books` | ~955 B | 955 | 1044 | 82% |
| `market_data` | ~232 B | 231 | 235 | 13% |
| `market_trades` | ~267 B | 266 | 275 | 4% |
| **weighted mix (dispatch)** | **~830 B** | | | |
| backtest_replay sample mean | ~640 B | | | (more data-heavy early rows) |

**Implication:** Backtest dispatch is ~82% order books, so ledger cost is mostly **~1 KiB JSONL lines**, not the tiny `"data"` payloads in `LedgerPerformanceTest`.

| If you can only pick one size | Use |
|-------------------------------|-----|
| Single synthetic (best proxy for backtest) | **~850–1024 B** (1 KiB is fine) |
| Closest to mix average | ~830 B |

| If you can run a few sizes | Use |
|----------------------------|-----|
| Small (ticker/data) | **256 B** |
| Medium (trades) | 256–300 B |
| Large (books — hot path) | **1024 B** (or 960–1000 B) |

**Practical recommendation:** primary run at **1024 B**; optional second at **256 B** to bound market_data/trade.

---

## 3. Round 1 — completed (10M × 4 KiB, NONE only)

### 3.1 Setup

| Param | Value |
|-------|--------|
| Rows | 10,000,000 |
| Payload | 4 KiB (`'x'` × 4096) — **oversized vs production** |
| Compression | `LedgerCompression.NONE` |
| Flush | `autoFlush=false`, flush every 5k writes + final flush |
| Memory | `preferredMaxSize=10_000`, `housekeeping()` every 1k writes |
| WriteBehind queue | 50,000 |
| JVM | `-Xmx2g -XX:+UseG1GC` |

### 3.2 Results (NONE)

| Ledger | Write ops/s | Write ms | API fwd (n @ ops/s) | API rev (n @ ops/s) | Disk fwd ops/s | Disk rev ops/s | File GiB |
|--------|-------------|---------|---------------------|---------------------|----------------|----------------|----------|
| DiskLedger | **119,520** | 83,668 | 10M @ 96,771 | 10M @ 15,609 | 98,004 | 15,038 | 39.12 |
| MemoryLedger | **116,263** | 86,012 | **10k** @ ~5M | 10M @ 15,092 | 93,253 | 15,346 | 39.12 |
| WriteBehindMemoryLedger | **95,990** | 104,177 | **10k** @ (sub-ms) | 10M @ 14,127 | 91,161 | 13,756 | 39.12 |

Exact TSV rows:

```text
DiskLedger	NONE	10000000	83668	119520.01	103337	10000000	640639	10000000	102037	10000000	664989	10000000	42007777787
MemoryLedger	NONE	10000000	86012	116262.85	2	10000	662605	10000000	107235	10000000	651616	10000000	42007777787
WriteBehindMemoryLedger	NONE	10000000	104177	95990.48	0	10000	707844	10000000	109696	10000000	726936	10000000	42007777787
```

### 3.3 Interpretation (Round 1)

- **Write:** Disk ≈ Memory (~116–120k/s); WriteBehind ~20% slower (~96k/s) under backpressure to the same disk path.
- **API forward (Memory / WriteBehind):** only the **~10k in-memory window** after housekeeping — not a full history scan.
- **API reverse (Memory / WriteBehind):** full 10M via memory then disk fall-through; similar cost to disk reverse.
- **Disk forward/reverse:** full 10M Gson parse for all three; reverse ~6× slower than forward.
- **vs pysol ~20k/s stage02:** Round 1 **overstates** ledger capacity for production because **4 KiB ≫ ~1 KiB book lines**. Use Round 2 (1024 B) as the backtest proxy.

### 3.4 ZSTD cells — root cause and fix

**Root cause (Round 1 abort):** `ZstdLedgerIndex.appendEntries()` called `rewriteFully()` + `FileChannel.force(true)` on every frame flush → **O(n²)** index I/O (~307 write ops/s at 10M).

**Fix:** `appendEntries` now **appends entry records** and updates header `entryCount` in place; `replaceAll` still full-rewrites for rebuilds. Covered by `ZstdLedgerIndexTest` (linear file growth + 40k frame-batch timing gate).

Full 10M NONE+ZSTD matrix after the fix: **§8 Round 3**.

---

## 4. Round 2 — completed (production-proxy sizes)

### 4.1 Matrix

Same three ledgers, **NONE only** (ZSTD deferred until index fix), **10M rows**, sequential, delete files between cells.

| Cell | Ledger | Payload | Compression |
|------|--------|---------|-------------|
| R2-1 | DiskLedger | **1024 B** (primary / book proxy) | NONE |
| R2-2 | MemoryLedger | **1024 B** | NONE |
| R2-3 | WriteBehindMemoryLedger | **1024 B** | NONE |
| R2-4 | DiskLedger | **256 B** (data/trade bound) | NONE |
| R2-5 | MemoryLedger | **256 B** | NONE |
| R2-6 | WriteBehindMemoryLedger | **256 B** | NONE |

Raw TSV: `/tmp/ledger-bench/results-round2.tsv` · Log: `/tmp/ledger-bench/run-round2.log`

### 4.2 Observed file sizes

| Payload | File size / 10M rows |
|---------|----------------------|
| 1024 B data field | **10.51 GiB** (11,287,777,787 bytes) |
| 256 B data field | **3.36 GiB** (3,607,777,787 bytes) |

### 4.3 Results (Round 2)

#### 1024 B payload (best backtest / book proxy)

| Ledger | Write ops/s | Write ms | API fwd (n) | API rev ops/s | Disk fwd ops/s | Disk rev ops/s | File GiB |
|--------|-------------|---------|-------------|---------------|----------------|----------------|----------|
| DiskLedger | **384,394** | 26,015 | 10M | 59,947 | 322,300 | 58,670 | 10.51 |
| MemoryLedger | **383,907** | 26,048 | 10k | 58,041 | 320,595 | 59,264 | 10.51 |
| WriteBehindMemoryLedger | **336,033** | 29,759 | 10k | 58,039 | 315,428 | 60,104 | 10.51 |

#### 256 B payload (data / trade bound)

| Ledger | Write ops/s | Write ms | API fwd (n) | API rev ops/s | Disk fwd ops/s | Disk rev ops/s | File GiB |
|--------|-------------|---------|-------------|---------------|----------------|----------------|----------|
| DiskLedger | **950,029** | 10,526 | 10M | 168,970 | 576,868 | 168,879 | 3.36 |
| MemoryLedger | **846,883** | 11,808 | 10k | 167,232 | 562,525 | 163,087 | 3.36 |
| WriteBehindMemoryLedger | **717,927** | 13,929 | 10k | 159,990 | 574,680 | 168,045 | 3.36 |

### 4.4 Interpretation (Round 2) vs pysol

- **Primary proxy (1024 B):** sustained ledger write ~**336–384k ops/s** (WriteBehind–Disk). That is **~17–19×** a ~20k/s stage02 budget — ledger JSONL write is unlikely to be the stage02 limiter at book-sized lines **if** the path matches this buffered NONE driver (no per-write fsync, no ZSTD index rewrite).
- **256 B:** write ~**718–950k ops/s**; useful upper bound for ticker/trade-sized lines.
- **vs Round 1 (4 KiB):** write ~3× faster at 1024 B than at 4 KiB (~120k → ~380k); reverse read similarly scales with bytes on disk.
- **Memory API forward** remains a **10k-window** metric only (housekeeping); use **disk fwd/rev** for full-history cost.
- **ZSTD** at scale: see **§8** (fixed; do not use Round 1 abort numbers).

---

## 5. Metrics legend

| Metric | Meaning |
|--------|---------|
| Write ops/s | Includes final `flush()` |
| API fwd/rev | `Ledger.read` / `readReverse` (Memory variants: fwd = cache window only) |
| Disk fwd/rev | `DiskPersistenceDriver.read` / `readReverse` — full file, apples-to-apples |
| File GiB | Total bytes under work dir after flush (ledger file; + `.idx` if ZSTD) |

---

## 6. Follow-ups

1. ~~Fix ZSTD index append path~~ **Done** (append-only + `ZstdLedgerIndexTest`); Round 3 re-bench complete.
2. Optional: speed up ZSTD **reverse read** (~3k/s vs ~300k+ forward) — frame batching / better seek strategy.
3. Optional: single cell at **~830 B** if mix-average precision matters.
4. Optional: check in a gated TestNG/`-Dledger.bench=true` harness later.
5. **Commit** uncommitted `ZstdLedgerIndex.java` + `ZstdLedgerIndexTest.java` when ready.

---

## 7. Related

- Plan 04 (ZSTD feature): [`plans/04-disk-ledger-zstd-compression.md`](04-disk-ledger-zstd-compression.md)
- Existing microbench: `simple-utilities/src/test/java/tech/rsqn/useful/things/ledger/LedgerPerformanceTest.java` (1M, tiny payload)

---

## 8. Round 3 — full matrix after ZSTD index fix (2026-08-16)

**Matrix:** 10M rows × payloads {1024, 256} × compression {NONE, ZSTD} × ledgers {Disk, Memory, WriteBehind} = **12 cells**.  
**TSV:** `/tmp/ledger-bench/results-full.tsv` · **Log:** `/tmp/ledger-bench/run-round3-zstd.log`  
**Unit tests:** `mvn -pl simple-utilities test` → 95/95 pass.

### 8.1 Results — 1024 B (book / backtest proxy)

| Ledger | Comp | Write ops/s | Disk fwd ops/s | Disk/API rev ops/s | On-disk |
|--------|------|-------------|----------------|--------------------|---------|
| DiskLedger | NONE | **396,621** | 315,288 | 59,149 | 10.51 GiB |
| MemoryLedger | NONE | **375,122** | 316,887 | 58,260 | 10.51 GiB |
| WriteBehindMemoryLedger | NONE | **313,853** | 311,507 | 58,036 | 10.51 GiB |
| DiskLedger | ZSTD | **111,525** | 335,537 | 3,030 | **186 MiB** |
| MemoryLedger | ZSTD | **110,143** | 337,963 | 3,021 | **186 MiB** |
| WriteBehindMemoryLedger | ZSTD | **94,938** | 333,556 | 3,027 | **186 MiB** |

### 8.2 Results — 256 B (data / trade bound)

| Ledger | Comp | Write ops/s | Disk fwd ops/s | Disk/API rev ops/s | On-disk |
|--------|------|-------------|----------------|--------------------|---------|
| DiskLedger | NONE | **939,232** | 548,155 | 168,084 | 3.36 GiB |
| MemoryLedger | NONE | **832,501** | 557,756 | 164,796 | 3.36 GiB |
| WriteBehindMemoryLedger | NONE | **722,543** | 544,099 | 165,824 | 3.36 GiB |
| DiskLedger | ZSTD | **191,026** | 548,125 | 2,979 | **183 MiB** |
| MemoryLedger | ZSTD | **195,354** | 560,727 | 2,922 | **183 MiB** |
| WriteBehindMemoryLedger | ZSTD | **164,463** | 587,751 | 2,950 | **183 MiB** |

### 8.3 Interpretation (Round 3)

| Question | Answer |
|----------|--------|
| Is ZSTD write fixed? | **Yes.** ~95–112k/s @ 1024 B vs ~307/s before (~300–360×). |
| Space win (this synthetic)? | **~56×** at 1024 B (10.51 GiB → 186 MiB); payload is highly compressible `'x'` — real JSON will compress less. |
| Write vs NONE? | ZSTD write ~**3–3.5× slower** than NONE at 1024 B (CPU + frame end + per-frame `force`). |
| Forward read? | ZSTD fwd ≈ NONE fwd (~315–338k/s). |
| Reverse read? | ZSTD rev ~**3k/s** (~20× slower than NONE rev) — still a product limitation, not the O(n²) bug. |
| vs ~20k/s stage02 @ 1024 B ZSTD? | Write still ~**5–6×** headroom on this path. |
