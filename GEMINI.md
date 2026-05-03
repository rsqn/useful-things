---
description: forge-exec is not registered as a native CLI server. Invoke forge tools
  directly using the forge-exec CLI.
globs: ["**/*"]
alwaysApply: true
---

# Forge Tool Invocation — Direct Exec Mode

You do not have native CLI tool access to forge. Run `forge-exec` to invoke forge tools directly from the shell.

Whenever a workflow or rule says to "use `forge-list`", "call `forge-search`", etc., use the shell commands below instead.

## Binary

`~/.local/bin/forge-exec`

## Usage

```
forge-exec <subcommand> [flags]
```

- Output is **plain text** written to stdout (real newlines, no JSON)
- Errors go to stderr; exit code 0 = success, 1 = error
- `--project-path` is optional everywhere; defaults to the current working directory

## Tool Commands

### list — browse all available Forge resources

```bash
~/.local/bin/forge-exec list
~/.local/bin/forge-exec list --category rules
```

### search — full-text keyword search

```bash
~/.local/bin/forge-exec search "plan first template"
~/.local/bin/forge-exec search "context window" --category skills
```

### read — read a specific Forge asset

```bash
~/.local/bin/forge-exec read plan-first --category rules
~/.local/bin/forge-exec read software-module-plan-template --category templates
```

### index — build the search index

```bash
~/.local/bin/forge-exec index
~/.local/bin/forge-exec index --target knowledge_base
```

### echo — connectivity check

```bash
~/.local/bin/forge-exec echo "connectivity check"
```

### debug — diagnostics

```bash
~/.local/bin/forge-exec debug
```

## Errors & Debugging

- **Binary not found** at `~/.local/bin/forge-exec`: forge is not installed — follow `install-instructions.md` Step 3.
- **`"Search index not found"`**: run `forge-exec index` to build the search index first.
- **Exit code 1 + error on stderr**: the error message describes the problem — act on it directly.
- **No output**: verify the binary path is correct and the binary is executable (`chmod +x ~/.local/bin/forge-exec`).

## Rule Resolution Fallback

Whenever you are required to know a rule, skill, or template by its *canonical name* (whether mentioned by the user, explicitly required by a workflow like `/plan`, or referenced inside another rule), you **MUST** use `forge-exec search` then `forge-exec read` to locate and read it from the knowledge base. Never use directory listings or file paths. NEVER use `forge-exec read` without first using `forge-exec search`.
