# useful-things: AI agent guidance

Library repository for `tech.rsqn.useful-things` (Java). Agents follow Forge plan-first discipline via `.forge/` and IDE rules.

---

## Session start: Tasks Gatekeeper (MANDATORY for agents)

Before changing Forge artifacts (`rules/`, `workflows/`, `skills/`, `templates/`, `standards/`, install/build docs) or before claiming work is **done**, the agent **MUST**:

1. Read `standards/tasks-md-schema.md` and `standards/consumer-failure-taxonomy.md` (under `.forge/` or via `forge-exec`).
2. Follow workflow `tasks-gatekeeper` (which runs skill `tasks-gatekeeper` against root `tasks.md`).
3. If the gatekeeper reports **`fail`** with any **blocking** failure codes → **stop** and fix `tasks.md` (or the underlying issue) before continuing.

Canonical tracker is **`tasks.md`** at the repository root. Do not recreate or use legacy `task.md` as the tracker.
