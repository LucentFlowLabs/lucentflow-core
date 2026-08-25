---
name: test
description: >-
  Runs LucentFlow Maven tests for the modules touched by the current change and
  reports pass/fail. Use when the user types /test, asks to verify, run unit
  tests, or gate a fix before review.
disable-model-invocation: true
---

# LucentFlow test (`/test`)

Run tests. Do not expand the product scope. Do not start an architecture review. Do not declare acceptance.

Chat in Chinese; commands and code in English.

## Instructions

1. Identify changed Maven modules from the working tree (`git status` / `git diff --stat`). Map paths:

   | Path prefix | Module |
   |-------------|--------|
   | `lucentflow-common/` | `lucentflow-common` |
   | `lucentflow-chain-sdk/` | `lucentflow-chain-sdk` |
   | `lucentflow-pipeline-contract/` | `lucentflow-pipeline-contract` |
   | `lucentflow-indexer/` | `lucentflow-indexer` |
   | `lucentflow-analyzer/` | `lucentflow-analyzer` |
   | `lucentflow-api/` | `lucentflow-api` |

2. Run Maven from the repository root. Quote `-D` properties on PowerShell.

   Touched modules (default):

   ```bash
   mvn -q -pl <module>[,<module>...] -am test
   ```

   Named test classes only (when the operator named them):

   ```bash
   mvn -q -pl <module> -am test "-Dtest=ClassA,ClassB" "-Dsurefire.failIfNoSpecifiedTests=false"
   ```

   API / Flyway / Testcontainers (when `lucentflow-api` or migrations changed):

   ```bash
   mvn -q -pl lucentflow-api -am test
   ```

   If Docker is required and unavailable, say so; do not skip tests silently.

3. Do **not** pass `-DskipTests`. Do **not** “fix until green” beyond the current task unless the operator asks.

4. Reply with this structure (fill from the actual run):

   - **Command:** the exact Maven line
   - **Result:** PASS or FAIL
   - **Modules:** list
   - **Failed tests:** class + message (omit if PASS)
   - **Not run:** anything skipped and why

## Examples

- After C1 sink/worker change: `-pl lucentflow-indexer,lucentflow-analyzer -am test` (or the two new test classes with `failIfNoSpecifiedTests=false`).
- Operator: `/test` with no module hint → infer from `git diff --stat`; if the tree is clean, ask which module.
