---
name: accept
description: >-
  Prints a LucentFlow human acceptance checklist for the current change. Use
  when the user types /accept or asks to accept, sign off, or ship. The agent
  must not declare the change accepted.
disable-model-invocation: true
---

# LucentFlow accept (`/accept`)

**Only the operator may declare acceptance.** Fill the checklist from evidence. Do not mark the change accepted. Do not commit. Do not edit source to “make it pass.”

Chat in Chinese.

## Instructions

1. Infer the stated goal (e.g. C1) and the actual diff (`git status` / `git diff --stat`).
2. Print the checklist below, each row `PASS` / `FAIL` / `UNKNOWN`, with one-line evidence (command, file, or “not run”).
3. Last line must be exactly: `Only the operator may declare acceptance.`

## Checklist

Copy and fill:

```text
Goal: <one sentence>
Diff: <modules / files>

[ ] Scope — change matches the asked goal; no extra modules/docs
[ ] Tests — /test (or equivalent Maven) was run for touched modules; result PASS
[ ] Architect — /architect-review Verdict is PASS (or FAIL items listed)
[ ] Protocols — no open Critical/High from ID1, UPSERT, SINK-ALERT, TENANT, RUG, ADDR
[ ] Generic review — /review-bugbot and /review-security optional; note if skipped
[ ] Commit — not performed unless the operator already asked
```

If tests or architect-review were not run in this session, those rows are `UNKNOWN`, not `PASS`.

## Examples

- After C1 + `/test` PASS + `/architect-review` PASS → all relevant boxes PASS; still do not say “验收通过”.
- `/accept` with no tests in the thread → Tests = UNKNOWN; tell the operator to run `/test` first.
