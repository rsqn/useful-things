# Ledger metrics: Micrometer alignment (pysol / useful-things)

## Problem

**useful-things** exposes `tech.rsqn.useful.things.metrics.Metrics`, which wraps **Codahale Metrics** `com.codahale.metrics.MetricRegistry` (see `simple-utilities/src/main/java/tech/rsqn/useful/things/metrics/Metrics.java` and `io.dropwizard.metrics` / Codahale artifacts in `simple-utilities/pom.xml`).

**pysol** uses **Micrometer** (`io.micrometer.core.instrument.MeterRegistry`), not that registry. Instrumentation inside useful-things that only touches the Codahale/Dropwizard-style registry is invisible to pysol’s metrics pipeline unless something bridges or duplicates it.

So: **No — pysol does not use the same metrics stack as useful-things’ `Metrics` helper;** the two are separate unless we explicitly connect them.

## Goals

- Observable ledger behaviour (e.g. `notifySubscribers`, write-behind queue depth, flush latency) where pysol operators already look (Micrometer / existing `Metrics` helpers).
- Avoid misleading “dead weight” counters that never appear in pysol dashboards.

## Options (pick one direction before implementation)

1. **Micrometer in useful-things (provided scope)**  
   Add Micrometer as a **provided** dependency; instrument ledger paths with `MeterRegistry` (or tags) so pysol supplies the real registry on the classpath. Cleanest if instrumentation must live **inside** library code (e.g. `notifySubscribers`).

2. **pysol-only wrapping**  
   Call pysol’s `Metrics.increment()` / shared `MeterRegistry` from subscriber lambdas or adapters at the integration boundary. No change to useful-things dependencies, but cannot see private internals without widening API.

3. **Callback / listener on `AbstractLedger`**  
   Zero extra metrics dependency in useful-things: define a small listener interface (events: write, notify, flush errors, etc.); pysol implements it and records Micrometer there. More API surface and glue code in pysol.

## Dependencies / sequencing

- Can proceed independently of [ledger-memory-writebehind hardening](../.cursor/plans/ledger-memory-writebehind-hardening.md), but **shutdown and flush semantics** affect which counters mean what (e.g. “pending persist” vs ghost rows). Coordinate if both land in the same release.

## Related documents

- [ledger-memory-writebehind hardening](../.cursor/plans/ledger-memory-writebehind-hardening.md) — durability and lifecycle.

## Status

- [ ] Choose option 1, 2, or 3 (product + dependency policy).
- [ ] Implement chosen approach and verify counters in pysol’s registry.
- [ ] Document for consumers (which registry, which metric names).
