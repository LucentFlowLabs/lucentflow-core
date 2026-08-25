---
name: architect-review
description: >-
  Read-only LucentFlow architecture review of the current diff against AGENTS.md
  hard protocols and PROCESS.md work rules. Use when the user types
  /architect-review or asks for an architect review after a code change. Does
  not replace /review-bugbot or /review-security.
disable-model-invocation: true
---

# LucentFlow architect review (`/architect-review`)

**Do not edit source. Do not commit. Do not fix findings.** Judge only.

This skill checks **repo protocols**, not generic bugs. For likely defects use `/review-bugbot`. For security use `/review-security`.

Chat in Chinese.

## Instructions

1. Read `AGENTS.md` (hard protocols) and `PROCESS.md` section 4 (work rules). Do not paste them into the reply.
2. Review the current change: uncommitted diff, or branch vs default base if the operator says so. Read the files; do not re-implement.
3. Check only the protocol list in [protocols.md](protocols.md). Skip items the diff cannot touch.
4. Output a markdown table, highest severity first:

   `| Severity | Location | Finding | Protocol |`

   - `Location` as `file:line` when known.
   - `Severity`: Critical / High / Medium / Low.
   - `Protocol`: short id from `protocols.md` (e.g. `ID1`, `SINK-ALERT`).

5. End with one line: **Verdict:** `PASS` (no Critical/High) or `FAIL` (any Critical/High). Then: “Fix only if the operator asks.”

6. If the diff is empty, say so in one sentence and stop.

## Examples

- After a sink change: look for swallowed `batchUpdate` and alerts before UPSERT success (`SINK-ALERT`).
- After `updateProgress` SQL: look for unconditional `SET last_scanned_block` (`ID1`).
