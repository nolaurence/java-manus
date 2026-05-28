---
name: sandbox-mcp-tools
description: Use the sandbox shell, file, and browser MCP tools to inspect, modify, run, and verify work.
---

# Sandbox MCP Tools

Use this skill whenever the task requires interacting with the sandbox environment, files, shell commands, or browser state.

## Shell

- Use shell tools to run commands, build projects, execute scripts, inspect runtime output, and verify fixes.
- For shell execution, always let the backend provide the current agent session id. Do not invent a new session id.
- Prefer focused commands and read the output before deciding the next action.

## File

- Use file tools to read, search, write, or replace files inside the sandbox.
- Prefer targeted reads and searches before editing.
- Keep edits scoped to the user's request.

## Browser

- Use browser tools for web UI inspection, navigation, screenshots, DOM snapshots, console logs, and network debugging.
- Verify user-visible changes in the browser when the task affects frontend behavior.

## Imported Skills

Imported skills live under the skill storage directory. When a task matches an imported skill, read its `SKILL.md` first, resolve relative references against that skill directory, and use the shell, file, or browser MCP tools to perform the work described by the skill.

## Completion

Continue calling tools until the user-visible task is complete or blocked. When done, respond directly with the result and any verification that matters.
