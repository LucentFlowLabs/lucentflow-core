---
name: process
description: >-
  Starts a LucentFlow work session from the repo playbook. Use when the user
  @-mentions PROCESS.md, types /process, or asks to open/start a coding session
  with the session playbook.
disable-model-invocation: true
---

# LucentFlow session (`/process`)

## Instructions

1. Read the repository root file `PROCESS.md` in full before any other project markdown.
2. Follow its session-start checklist (goal, single module, read canonical files first).
3. **Put the 会话清单 card from `PROCESS.md` §0 in the first reply**, filled in. Do not keep it internal. If there is no task yet, still emit the card with 目标/模块 as `待指定`.
4. Obey `AGENTS.md` for stack, language, author tag, and hard protocols. Do not paste `AGENTS.md` into the reply.
5. Chat in Chinese; write code and comments in English.
6. Do not load `docs/**` or `PROGRESS.md` unless the task needs them.

## Examples

- User: `@PROCESS.md` or `/process` then a task → read `PROCESS.md`, emit the 会话清单 card, then execute the task.
- User: `/process` with no task → still emit the card with 目标/模块 as `待指定`, then wait.
- User: "按 PROCESS 开会话，修 checkpoint" → same: playbook first, card in the first reply, then the fix.