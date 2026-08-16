# Tasks

## Active

- [x] Abstract ledger notification hot-path: [`plans/01-abstract-ledger-notification-hot-path.md`](plans/01-abstract-ledger-notification-hot-path.md)
- [x] Ledger notification fan-in (`execute`, one task per record): [`plans/02-ledger-notification-fan-in-dispatch.md`](plans/02-ledger-notification-fan-in-dispatch.md)
- [ ] Histogram custom boundaries, serialization & snapshot: [`plans/03-histogram-custom-boundaries.md`](plans/03-histogram-custom-boundaries.md)
- [ ] Disk ledger optional streaming Zstd compression: [`plans/04-disk-ledger-zstd-compression.md`](plans/04-disk-ledger-zstd-compression.md)
- [x] Ledger performance bench report (Round 1 4KiB + Round 2 1024B/256B NONE): [`plans/05-ledger-performance-bench-report.md`](plans/05-ledger-performance-bench-report.md)

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

## Stage Gates

### Gate: TrackerHygiene_v1
Status: ready
Owner: useful-things maintainers
GateScore: 90
OutputConfidence: 90 (advisory)
EvidenceScore: 95
BlockingFailures:

RequiredEvidence:
  - EVIDENCE_001_schema_alignment
  - EVIDENCE_002_prompt_session_gate
  - EVIDENCE_003_legacy_tracker_absent
SignOffs:
  - Agent (2026-08-11) — scaffolded missing Stage Gates / Assumption Ledger / Risk Register / Evidence Index; added root prompt.md Session start section
Notes:
  - Existing Active checklist preserved; schema sections added so ConsumerAgentSuite v1 can pass

## Assumption Ledger

- A001_tasks_md_is_canonical: Root `tasks.md` is the canonical work tracker for this repo.
  Confidence: 0.95
  EvidenceToUpgrade: Ongoing use when registering new `plans/` files.
  Owner: Maintainers

- A002_prompt_session_gate_sufficient: Minimal `prompt.md` Session start section satisfies gatekeeper prompt alignment.
  Confidence: 0.9
  EvidenceToUpgrade: Re-run tasks-gatekeeper after any prompt wording change.
  Owner: Maintainers

## Risk Register

- R001_tracker_drift: Active work listed without Stage Gates / Evidence Index causes gatekeeper hard-stops.
  Severity: medium
  Owner: Maintainers
  Mitigation: Keep schema sections when editing Active; register new plans under Active.
  Verification: EVIDENCE_001_schema_alignment

## Evidence Index

- EVIDENCE_001_schema_alignment:
  Type: doc_reference
  Location: tasks.md (## Stage Gates, ## Assumption Ledger, ## Risk Register, ## Evidence Index)
  Expected: Required headings present; at least one ### Gate: block with all required fields

- EVIDENCE_002_prompt_session_gate:
  Type: doc_reference
  Location: prompt.md (section **Session start: Tasks Gatekeeper**)
  Expected: Mandates gatekeeper before changing Forge artifacts or claiming done

- EVIDENCE_003_legacy_tracker_absent:
  Type: executable_check
  Location: `test ! -f task.md && test ! -f cli/task.md`
  Expected: Exit 0; no legacy `task.md` tracker
