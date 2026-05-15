# Plan 03: Histogram — Custom Boundaries, Serialization & Snapshot

## Goals

Extend `Histogram.java` in the `math-and-data` module to support:

1. **Custom bucket boundaries** — caller supplies a `double[]` boundaries array; values outside the range clamp to the first or last bin (no data loss).
2. **Serialisation accessor** — `toDoubleArray()` returns a documented copy of bin counts for Gson persistence.
3. **Snapshot copy** — `snapshot()` returns a point-in-time `double[]` copy of the bins for thread-safe candle-close capture.
4. **Refactored `findBin()`** — split into `findBinUniform()` and `findBinCustom()` private delegates so modes are cleanly separated.
5. **Incompatible-histogram guard** — `avgDifference()` throws `IllegalArgumentException` when comparing histograms of different modes or boundary sets.
6. **Honest `render()`** — first and last bins labelled as underflow/overflow when in custom-boundary mode.
7. **Raw accessors** — `getBins()` (internal array, documented mutable), `getBoundaries()` (defensive copy), `getNumBins()`.
8. **Weighted normalisation overload** — `normalize(double[] binWeights)` divides each bin by its weight (e.g. bin width) then recomputes `max`; allows density-correct normalisation for non-uniform bins.

---

## Context & Scope

`Histogram.java` (`math-and-data/src/main/java/tech/rsqn/useful/things/mathanddata/`) is a 149-line, zero-dependency histogram backed by a `double[]`. It supports uniform bin widths between a min/max, `normalize()`, `avgDifference()`, and ASCII `render()`.

The pysol trading system needs a custom-boundary variant to model book-depth quality with non-uniform percentage buckets (e.g. 0–0.05 %, 0.05–0.1 %, …, 20 %+). The current class drops out-of-range values silently and has no serialisation or thread-safe snapshot, both of which are required for candle-close persistence.

**In scope:** changes to `Histogram.java` and `HistogramTest.java` only. No new files, no new dependencies, no changes to other modules.

**Users:** pysol (primary); any other useful-things consumer that wants non-uniform binning.

---

## Non-Goals & Boundaries

- `fromDoubleArray()` — deferred. Format contract is documented now; implementation comes later.
- Named bins / semantic labels per bucket — documented by convention in pysol, not in this class.
- Thread synchronisation inside `Histogram` — callers lock externally; `snapshot()` documents this explicitly.
- Migrating `HistogramTest` to JUnit 5 — project standard is TestNG; no framework change.
- Changes to any module other than `math-and-data`.

---

## Interrogation Record — MANDATORY

| # | Question (as asked) | User answer | Confidence (0.0–1.0) | Open / follow-up? |
|---|---------------------|-------------|----------------------|-------------------|
| 1 | Extend Histogram.java in useful-things, new BoundedHistogram.java, or pysol-local class? | Extend `Histogram.java` directly in useful-things | 0.95 | No |
| 2 | Caller supplies raw `double[]` boundaries, or a fixed predefined scheme? | Raw `double[]` boundaries — fully general | 0.95 | No |
| 3 | Serialisation scope: counts only, or counts + boundaries? | Counts only for now; counts+boundaries noted as future-interesting | 0.95 | No |
| 4 | `snapshot()` contract: deep copy, immutable wrapper, or freeze? | Deep copy (`double[]`); live histogram keeps accumulating | 0.95 | No |
| 5 | Backward compat: don't touch existing class, additive only, or refactor freely? | Refactor freely | 0.95 | No |
| 6 | Test framework? | TestNG — whatever the project already uses | 0.97 | No |
| 7 | Out-of-range behaviour with custom boundaries? | Overflow/underflow bins — first and last bins absorb out-of-range; no data loss, no exceptions | 0.95 | No |
| DA-1 | Two modes in one class — which fix? | 1b: private `findBinUniform()` / `findBinCustom()` delegates | 0.95 | No |
| DA-2 | `avgDifference()` incompatible histogram handling? | Throw `IllegalArgumentException` | 0.95 | No |
| P6-1 | `findBinCustom()` bin semantics — right-exclusive or right-inclusive? | Right-inclusive `(boundaries[i] < v <= boundaries[i+1])` matching existing uniform behaviour | 0.95 | No |
| P6-2 | `getBins()` raw accessor? | Yes — add `getBins()` returning internal array with Javadoc mutability warning | 0.95 | No |
| P6-3 | `getBoundaries()` accessor? | Yes — add `getBoundaries()` returning defensive copy | 0.95 | No |
| P6-4 | `getNumBins()` accessor? | Yes — add `getNumBins()` | 0.95 | No |
| P6-5 | `normalize()` in custom mode — lambda, array, or leave as-is? | Add `normalize(double[] binWeights)` overload; weights array passed by caller | 0.95 | No |
| DA-3 | `snapshot()` memory visibility? | Accept race; document in Javadoc; callers lock externally | 0.95 | No |
| DA-4 | `toDoubleArray()` format contract? | Document now in Javadoc; future `fromDoubleArray(double[] counts, double[] boundaries)` | 0.95 | No |
| DA-5 | Boundaries validation at construction? | Validate: length ≥ 2, strictly ascending, all finite; throw `IllegalArgumentException` | 0.95 | No |
| DA-6 | `render()` overflow/underflow labelling? | Update render() to label first/last bins as underflow/overflow in custom mode | 0.95 | No |

---

## SDLC — Requirements & discovery

### Functional requirements

| ID | Requirement |
|----|-------------|
| FR-1 | Constructor `Histogram(double[] boundaries)` accepts a sorted, finite, length-≥-2 boundaries array and creates `boundaries.length - 1` bins. |
| FR-2 | `submit(double)` in custom mode routes values below `boundaries[0]` to bin 0 (underflow) and values above `boundaries[numBins]` to bin `numBins-1` (overflow). No data is dropped. |
| FR-3 | `findBin()` delegates to `findBinUniform()` (existing logic) or `findBinCustom()` (new); modes are mutually exclusive. |
| FR-4 | `toDoubleArray()` returns a defensive copy of the bins array (counts only, indices 0..numBins-1). Format is documented in Javadoc. |
| FR-5 | `snapshot()` returns `Arrays.copyOf(bins, numBins)`. Javadoc states it is not guaranteed to be a consistent point-in-time view without external synchronisation. |
| FR-6 | `avgDifference(Histogram o)` throws `IllegalArgumentException` if modes differ or custom boundaries differ; preserves existing `numBins` mismatch return-0 behaviour. |
| FR-7 | Construction with invalid boundaries (length < 2, non-ascending, non-finite) throws `IllegalArgumentException`. |
| FR-8 | `render()` labels bin 0 as `[underflow+lo-hi]` and bin N-1 as `[lo-hi+overflow]` when in custom-boundary mode. |
| FR-9 | All existing uniform-mode constructors and behaviour are unchanged. |

### Non-functional requirements

- **Zero GC pressure on hot path**: `submit()` must not allocate. Only construction and `toDoubleArray()` / `snapshot()` allocate.
- **No new dependencies**: `java.util.Arrays` is already imported. No collections, no boxing.
- **Thread safety contract**: class is deliberately not thread-safe; snapshot + external lock is the documented pattern.

### Requirements traceability

| FR | Design | Test |
|----|--------|------|
| FR-1 | `Histogram(double[])` constructor | `customBoundaries_validInput_createsBins` |
| FR-2 | `findBinCustom()` underflow/overflow | `customBoundaries_underflow_goesToBinZero`, `customBoundaries_overflow_goesToLastBin` |
| FR-3 | `findBin()` dispatch | Covered by FR-1/FR-2 tests |
| FR-4 | `toDoubleArray()` | `toDoubleArray_returnsCopyOfCounts` |
| FR-5 | `snapshot()` | `snapshot_returnsDefensiveCopy` |
| FR-6 | `avgDifference()` guard | `avgDifference_incompatibleModes_throwsException`, `avgDifference_differentBoundaries_throwsException` |
| FR-7 | Construction validation | `customBoundaries_invalidInput_throwsIllegalArgument` |
| FR-8 | `render()` labels | `render_customMode_labelsOverflowBins` |
| FR-9 | Regression | `shouldPrintSimpleHistogram`, `shouldNormaliseSimpleHistogram` (existing, unchanged) |

---

## SDLC — Architecture & design

### Context & component view

```
math-and-data module
└── Histogram.java          ← all changes here
└── HistogramTest.java      ← new test cases added
```

No other files change. No new classes.

### Interfaces & contracts (APIs, events, schemas)

**New constructor:**
```java
public Histogram(double[] boundaries)
// boundaries: sorted ascending, all finite, length >= 2
// numBins = boundaries.length - 1
// throws IllegalArgumentException if invalid
```

**New methods:**
```java
// Returns defensive copy of bin counts.
// Serialisation contract: double[numBins], index i = count for bin i.
// Future fromDoubleArray signature: fromDoubleArray(double[] counts, double[] boundaries)
public double[] toDoubleArray()

// Returns Arrays.copyOf(bins, numBins).
// NOT guaranteed to be a consistent point-in-time view without external synchronisation.
// Callers must hold their own lock across submit()/snapshot() if consistency is required.
public double[] snapshot()

// Returns the raw internal bins array. NOT a copy — mutation affects the histogram.
// Provided for non-allocating hot-read paths. Do not hold a reference across submit() calls
// without external synchronisation.
public double[] getBins()

// Returns Arrays.copyOf(boundaries, boundaries.length), or null if in uniform mode.
public double[] getBoundaries()

// Returns the number of bins.
public int getNumBins()

// Divides each bin by binWeights[i], then recomputes max.
// Use for density-correct normalisation on non-uniform bins:
//   e.g. normalize(new double[]{ b[1]-b[0], b[2]-b[1], ... }) where b = getBoundaries()
// binWeights.length must equal numBins; throws IllegalArgumentException if not.
// Replaces bin values in-place; not reversible.
public void normalize(double[] binWeights)
```

**Modified methods:**
```java
// Throws IllegalArgumentException if:
//   - one histogram is uniform and the other is custom-boundary
//   - both are custom-boundary but with different boundaries arrays
// Existing numBins-mismatch guard (return 0) preserved.
public double avgDifference(Histogram o)
```

**New private methods:**
```java
private int findBinUniform(double v)   // existing findBin() logic, extracted
private int findBinCustom(double v)    // linear scan through boundaries[]; underflow→0, overflow→numBins-1
```

**render() change:** when `boundaries != null`, bin 0 label prefix becomes `[underflow]` and bin N-1 label suffix becomes `[overflow]`.

### Data model & persistence

New field added to `Histogram`:
```java
private double[] boundaries;  // null = uniform mode; non-null = custom mode
```

All existing fields (`bins`, `numBins`, `max`, `minValue`, `maxValue`, `range`) are preserved. In custom mode, `minValue`, `maxValue`, and `range` remain at their default `-1` sentinels.

`toDoubleArray()` serialisation format: plain `double[numBins]`, counts only. Boundaries are not included. Callers must store boundaries separately for reconstruction. This is documented in Javadoc.

### Design decisions & trade-offs

| Decision | Rationale |
|----------|-----------|
| Extend `Histogram.java` rather than subclass | User elected single-class extension; avoids instanceof checks in pysol |
| Linear scan in `findBinCustom()` | Typical bin count is 10–20; linear scan avoids binary search overhead and allocation |
| Defensive copy in constructor (`Arrays.copyOf`) | Prevents caller mutation of the boundaries array after construction |
| `avgDifference()` throws rather than returns 0 | Silent wrong result is worse than a loud failure; consistent with user choice |
| Snapshot not synchronised | Candle-close callers hold external locks; adding `synchronized` to `submit()` penalises every tick |

---

## SDLC — Implementation & build

### Implementation strategy & milestones

Three phases, each independently testable:

**Phase 1 — Core binning**: new constructor, boundaries validation, `findBinUniform()` / `findBinCustom()` refactor, overflow/underflow clamping, `avgDifference()` guard.

**Phase 2 — Accessors**: `toDoubleArray()` with Javadoc contract, `snapshot()` with Javadoc warning.

**Phase 3 — Observability & tests**: `render()` overflow/underflow labels, full TestNG test coverage for all new paths.

### Key modules / packages / files

| File | Change type |
|------|-------------|
| `math-and-data/src/main/java/tech/rsqn/useful/things/mathanddata/Histogram.java` | Modify |
| `math-and-data/src/test/java/tech/rsqn/useful/things/mathanddata/HistogramTest.java` | Modify (add test methods) |

### Feature flags, migrations, backward-compat stance

No feature flags. All changes are additive to the public API. Existing constructors and method signatures unchanged. `findBin()` is private — internal refactor only.

---

## SDLC — Testing & quality assurance

### Test strategy by layer

**Unit tests (TestNG, `HistogramTest.java`):**

| Test method | Covers |
|-------------|--------|
| `customBoundaries_validInput_createsBins` | FR-1: constructor creates correct number of bins |
| `customBoundaries_submit_routesValuesToCorrectBin` | FR-2: values land in expected bin |
| `customBoundaries_underflow_goesToBinZero` | FR-2: values below `boundaries[0]` → bin 0 |
| `customBoundaries_overflow_goesToLastBin` | FR-2: values above `boundaries[numBins]` → last bin |
| `customBoundaries_invalidInput_throwsIllegalArgument` | FR-7: non-ascending, length < 2, non-finite |
| `toDoubleArray_returnsCopyOfCounts` | FR-4: returns copy, mutation of result does not affect histogram |
| `snapshot_returnsDefensiveCopy` | FR-5: returns copy, further submit() does not mutate snapshot |
| `avgDifference_incompatibleModes_throwsException` | FR-6: uniform vs custom throws |
| `avgDifference_differentBoundaries_throwsException` | FR-6: custom vs custom with different boundaries throws |
| `avgDifference_compatibleCustom_returnsScore` | FR-6: same boundaries, happy path |
| `render_customMode_labelsOverflowBins` | FR-8: first/last bin labels include underflow/overflow |
| `shouldPrintSimpleHistogram` (existing) | FR-9: regression |
| `shouldNormaliseSimpleHistogram` (existing) | FR-9: regression |

### Test environments & data

Local only. No external services. `SecureRandom` retained from existing test for uniform-mode tests.

### Entry / exit criteria for test gates

- Entry: `mvn test -pl math-and-data` green before each phase merge.
- Exit: all tests above pass; no regressions in existing two tests.

---

## SDLC — Security & compliance

### Threats & mitigations

| Threat | Mitigation |
|--------|------------|
| Caller passes adversarial boundaries (NaN, Infinity, unsorted) | Constructor validates: finite, strictly ascending, length ≥ 2 — throws `IllegalArgumentException` |
| Caller mutates boundaries array post-construction | Constructor stores `Arrays.copyOf(boundaries, boundaries.length)` — defensive copy |

### Secrets & configuration

Not applicable — pure numeric utility class, no I/O, no secrets.

### Compliance / policy checkpoints

Not applicable — no PII, no external data.

---

## SDLC — Documentation & knowledge transfer

### Developer documentation

- Javadoc on `Histogram(double[])` constructor: describes boundaries contract, validation, mode semantics.
- Javadoc on `toDoubleArray()`: serialisation format contract; documents future `fromDoubleArray(double[] counts, double[] boundaries)` signature.
- Javadoc on `snapshot()`: explicit warning that this is not a consistent point-in-time view without external synchronisation.
- Javadoc on `avgDifference()`: documents the incompatibility exception.

### Operator / support documentation

Not applicable — library class, no runtime deployment.

---

## SDLC — Build, CI/CD & release management

### Build & artifact layout

```
math-and-data/
  pom.xml        # version bump (patch)
  src/main/java/…/Histogram.java
  src/test/java/…/HistogramTest.java
```

### CI/CD stages & gates

```
mvn test -pl math-and-data    # must pass before any commit
mvn install                   # full repo build to verify no downstream breakage
```

### Release type & communication

**Patch** version bump (additive public API only; no breaking changes). Communicate to pysol: new constructor signature, three new public methods.

---

## SDLC — Deployment & environments

### Environment matrix

| Environment | Action |
|-------------|--------|
| Local dev | `mvn install -pl math-and-data` — publish to local `.m2` |
| pysol | Update `useful-things` dependency version in pysol `pom.xml` |

### Deployment procedure & sequencing

1. Land changes in useful-things `master`.
2. Bump version in `math-and-data/pom.xml` (and root `pom.xml` if needed).
3. `mvn install` locally to publish to `.m2`.
4. Update dependency version in pysol.

### Rollback procedure

Previous version of `math-and-data` jar remains in `.m2`; pysol can pin to prior version. No database or state migration — pure library change.

---

## SDLC — Operations, monitoring & observability

### Monitoring & alerting

Not applicable — library class. Callers (pysol) are responsible for monitoring their own metrics.

### Operational dashboards & health checks

Not applicable.

---

## SDLC — Maintenance, incidents & support

### Support model & escalation

Changes owned by this repo. Issues surface via pysol integration testing.

### Incident response & postmortem hooks

Not applicable — no runtime service.

### Maintenance & deprecation plan

`fromDoubleArray(double[] counts, double[] boundaries)` is the documented future addition. When implemented, it must match the serialisation contract declared in `toDoubleArray()` Javadoc.

---

## Applied Rules

- `plan-first` — plan documented and approved before any code.
- `question-formatting` — all questions numbered with multi-line options.
- `confidence-scoring` — scores on every significant output.
- `definition-of-done` — DoD checklist required before declaring done.
- `devils-advocate` — critique performed, user approved.
- `expert-review` — required after implementation.
- `java-performance` — no allocation on hot path (`submit()`); no boxing; `double[]` throughout.
- `java-testing-standards` — TestNG, naming convention `method_condition_expected`, coverage of happy path + edge cases + exceptions.

---

## Related Documents

| Document | Role | Link |
|----------|------|------|
| — | — | — |

---

## Dependencies & third parties

| Dependency | Version / constraint | Purpose | Risk |
|------------|---------------------|---------|------|
| `java.util.Arrays` | JDK (already imported) | `copyOf`, `equals` for boundaries comparison | None |
| TestNG | As declared in `math-and-data/pom.xml` | Test framework | None |

---

## Master Task List (engineering)

- [x] Phase 1: Add `double[] boundaries` field to `Histogram`
- [x] Phase 1: Implement `Histogram(double[] boundaries)` constructor with validation
- [x] Phase 1: Extract `findBinUniform()` from existing `findBin()` logic (right-inclusive, no logic change)
- [x] Phase 1: Implement `findBinCustom()` — right-inclusive scan; underflow → 0, overflow → numBins-1
- [x] Phase 1: Update `findBin()` to dispatch to correct delegate
- [x] Phase 1: Update `avgDifference()` to throw on incompatible modes / different boundaries
- [x] Phase 2: Implement `toDoubleArray()` with Javadoc serialisation contract
- [x] Phase 2: Implement `snapshot()` with Javadoc visibility warning
- [x] Phase 2: Implement `getBins()` with Javadoc mutability warning
- [x] Phase 2: Implement `getBoundaries()` (defensive copy, null-safe)
- [x] Phase 2: Implement `getNumBins()`
- [x] Phase 2: Implement `normalize(double[] binWeights)` overload with length validation and max recompute
- [x] Phase 3: Update `render()` to label overflow/underflow bins in custom mode
- [x] Phase 3: Write all new TestNG test methods
- [x] Phase 3: Run `mvn test -pl math-and-data` — all green
- [ ] Phase 3: Run `mvn install` — full repo build green (math-and-data green; pre-existing LedgerPerformanceTest flake in simple-utilities unrelated)
- [ ] Bump patch version in `math-and-data/pom.xml`

---

## User Review Required

> [!IMPORTANT]
> - Serialisation format (`toDoubleArray()` = counts only, no boundaries) is locked by this plan. Any future `fromDoubleArray()` implementation **must** match this format or explicitly document a version break.
> - The `avgDifference()` exception is a behavioural change from the current return-0 on mismatch. Confirm no existing callers depend on the silent return-0 for mode mismatches (currently impossible since custom mode doesn't exist yet — no risk).

---

## Implementation Phases (delivery slices)

### Phase 1: Core binning

- **Proposed changes:**
  - `Histogram.java`: add `private double[] boundaries` field
  - `Histogram.java`: new `Histogram(double[] boundaries)` constructor — validates input, sets `numBins = boundaries.length - 1`, allocates `bins`
  - `Histogram.java`: extract `findBinUniform(double v)` from existing `findBin()` body (no logic change)
  - `Histogram.java`: implement `findBinCustom(double v)` — linear scan; `v < boundaries[0]` → 0; `v >= boundaries[numBins]` → `numBins-1`
  - `Histogram.java`: `findBin()` dispatches: `boundaries == null ? findBinUniform(v) : findBinCustom(v)`
  - `Histogram.java`: `avgDifference()` — add mode/boundary compatibility check before existing logic; throw `IllegalArgumentException` on mismatch

- **Testing & acceptance:** existing two tests pass unchanged; manually verify with a scratch test that custom-boundary submit routes correctly.

- **Security / compliance notes:** boundary validation prevents NaN/Infinity/unsorted input.

- **Rollback:** revert `Histogram.java` only; no state persisted.

### Phase 2: Accessors

- **Proposed changes:**
  - `Histogram.java`: `toDoubleArray()` — `return Arrays.copyOf(bins, numBins)` with full Javadoc contract
  - `Histogram.java`: `snapshot()` — `return Arrays.copyOf(bins, numBins)` with Javadoc visibility warning

- **Testing & acceptance:** `toDoubleArray_returnsCopyOfCounts`, `snapshot_returnsDefensiveCopy` pass.

- **Security / compliance notes:** defensive copies prevent caller from mutating internal state via returned array.

- **Rollback:** revert two method additions.

### Phase 3: Observability & full test coverage

- **Proposed changes:**
  - `Histogram.java`: update `render()` — when `boundaries != null`, prefix bin 0 label with `[underflow]` and suffix bin `numBins-1` label with `[overflow]`
  - `HistogramTest.java`: add all test methods listed in the test strategy table

- **Testing & acceptance:** `mvn test -pl math-and-data` fully green; `render_customMode_labelsOverflowBins` passes and output is visually verified in test stdout.

- **Security / compliance notes:** Not applicable.

- **Rollback:** revert render() and test additions.

---

## Critical Path Tests

1. `customBoundaries_submit_routesValuesToCorrectBin` — a value hitting each interior bucket lands in the right index.
2. `customBoundaries_underflow_goesToBinZero` — value below first boundary increments bin 0, not dropped.
3. `customBoundaries_overflow_goesToLastBin` — value above last boundary increments last bin, not dropped.
4. `customBoundaries_invalidInput_throwsIllegalArgument` — unsorted, length-1, NaN, and Infinity inputs each throw.
5. `avgDifference_incompatibleModes_throwsException` — uniform histogram compared to custom-boundary histogram throws.
6. `snapshot_returnsDefensiveCopy` — further `submit()` after snapshot does not mutate the snapshot array.
7. `shouldPrintSimpleHistogram` (existing) — uniform mode fully unchanged.

---

## Verification Plan

### Build & run orchestration

| Action | Command | Notes |
|--------|---------|-------|
| Build | `mvn compile -pl math-and-data` | Compiles main sources only |
| Test | `mvn test -pl math-and-data` | Runs TestNG suite |
| Full repo build | `mvn install` | Verifies no downstream breakage |
| Lint / static analysis | Not applicable — no static analysis configured | — |

### Automated tests (by type)

- **Unit:** all TestNG methods in `HistogramTest` — run via `mvn test -pl math-and-data`
- **Integration:** Not applicable — pure utility class, no I/O
- **E2E:** pysol integration test (out of scope for this plan)

### Manual / UAT

Review `render()` stdout in test output — confirm underflow/overflow labels appear correctly on first and last bins.

### Evidence index (plan-local)

| ID | Type | Location / command | Expected |
|----|------|-------------------|----------|
| E-1 | Unit test run | `mvn test -pl math-and-data` | BUILD SUCCESS, 0 failures |
| E-2 | Full build | `mvn install` | BUILD SUCCESS |
| E-3 | Render output | Test stdout for `render_customMode_labelsOverflowBins` | First bin contains `[underflow]`, last contains `[overflow]` |

---

## Assumption Ledger

| Assumption | Confidence (0.0–1.0) | Evidence to upgrade | If false, impact |
|------------|----------------------|---------------------|------------------|
| Boundaries array is small (< 30 elements); linear scan is adequate | 0.92 | Check pysol bucket config | If very large, replace with `Arrays.binarySearch()` — isolated to `findBinCustom()` |
| No existing callers of `avgDifference()` pass mode-mismatched histograms (currently impossible — custom mode doesn't exist yet) | 0.99 | `grep -r avgDifference` across repo | No risk — new mode can't exist before this plan |
| `double[]` copy in `snapshot()` / `toDoubleArray()` is acceptable for GC budget | 0.92 | Profiling in pysol hot path | If not, expose raw array with documented mutability warning |
| TestNG is the only test framework in `math-and-data` | 0.97 | Confirmed via `pom.xml` grep | No impact — test additions stay in TestNG |
| Version bump is patch (no breaking changes) | 0.95 | Review all public method signatures | Additive only — confirmed |

---

## Risk Register

| # | Phase / area | Risk | Impact | Proximity | Mitigation | Action detail |
|---|--------------|------|--------|-----------|------------|---------------|
| R-1 | Phase 1 — `findBin()` dispatch | Uniform-mode regression if dispatch condition is wrong | High | Immediate | Existing tests catch this; `range == -1` and `boundaries == null` both guard uniform mode | Run existing tests first |
| R-2 | Phase 1 — `avgDifference()` | Boundary equality check with `Arrays.equals()` may fail for semantically equal but differently constructed arrays | Low | Phase 1 | Use `Arrays.equals(boundaries, o.boundaries)`; document that boundaries must be identical double values | Covered by `avgDifference_differentBoundaries_throwsException` test |
| R-3 | Phase 2 — serialisation format | Future `fromDoubleArray()` added with wrong assumption about format | Medium | Future | Javadoc locks the contract now; risk is a future developer not reading it | Mitigated by explicit Javadoc |
| R-4 | Phase 3 — `render()` | Label change breaks any existing output parsing in pysol | Low | Integration | Labels only change in custom-boundary mode; uniform `render()` output is identical | pysol UAT confirms |

---

## Confidence Score (mandatory — gating)

```
Confidence: 93% (Very High)
Basis: Full source reviewed; all Phase 1 questions answered; Devil's Advocate resolved with explicit user decisions
      on all six points; domain is well-established (array-backed histogram); no new dependencies; purely
      additive public API; existing tests provide regression safety.
Assumptions:
  - Boundaries array is small, linear scan adequate (0.92 — check pysol config)
  - No existing avgDifference callers pass mode-mismatched histograms (0.99 — currently impossible)
  - double[] copy acceptable for GC budget (0.92 — profile in pysol if needed)
  - TestNG is sole test framework in math-and-data (0.97 — confirmed via pom.xml)
  - Version bump is patch only (0.95 — additive API confirmed)
```
