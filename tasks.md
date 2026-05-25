# Tasks

## Active

- [x] Abstract ledger notification hot-path: [`plans/01-abstract-ledger-notification-hot-path.md`](plans/01-abstract-ledger-notification-hot-path.md)
- [x] Ledger notification fan-in (`execute`, one task per record): [`plans/02-ledger-notification-fan-in-dispatch.md`](plans/02-ledger-notification-fan-in-dispatch.md)
- [ ] Histogram custom boundaries, serialization & snapshot: [`plans/03-histogram-custom-boundaries.md`](plans/03-histogram-custom-boundaries.md)

## Ledger: metrics (Micrometer vs Dropwizard)

Tracked in [plans/01-ledger-metrics-micrometer-alignment.md](plans/01-ledger-metrics-micrometer-alignment.md).

- [ ] **Decision**: Micrometer `provided` in useful-things, pysol-only instrumentation, or `AbstractLedger` listener callback (see plan).
- [ ] **Implement** chosen path and wire metrics where operators expect them (pysol uses Micrometer, not Dropwizard).
- [ ] **Docs**: metric names and dependency expectations for library consumers.

## Ledger: memory / write-behind hardening

Tracked in [.cursor/plans/ledger-memory-writebehind-hardening.md](.cursor/plans/ledger-memory-writebehind-hardening.md).

- [ ] **Close vs `keepRunning`**: stop accepting writes before draining write-behind queue (`WriteBehindMemoryLedger.close()` ordering).
- [ ] **Interrupted `write()`**: reconcile or remove in-memory row when `writeQueue.put` is interrupted; avoid ghost `read()` rows.
- [ ] **`flush()` timeout**: fail loudly or document degraded state when `lastPersistedSeq` lags `lastEnqueuedSeq` after wait.
- [ ] **Housekeeping + write-behind**: evict from memory only when head sequence is persisted (`lastPersistedSeq`); fix shared trim path.
- [ ] **Writer `IOException` policy**: retry, halt, or dead-letter — no silent gaps if strict JSONL is required.
- [ ] **`preferredMaxSize` on write path**: self-managing trim (same persistence rule as housekeeping).
- [ ] **`readReverse` / disk heuristic**: regression tests for trim + write-behind + reverse read; clarify or tighten heuristic.
- [ ] **Docs**: `MemoryLedger` vs `WriteBehindMemoryLedger` write semantics; daemon writer / require `close()`.
