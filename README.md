# Java Manus

java version of manus with playwright mcp to operate browser

this project is in alpha stage with active development

### road map
- [x] skill mechanism
- [ ] standalone edition
- [ ] cluster mode perfection


## 注意事项

1. RUN yum install -y bellsoft-java11 会耗时较久

## Copilot-style Java agent loop

The default (non-plan-mode) executor now runs an in-process Java loop that follows
the same stable protocol shape as Copilot's agent runtime, without depending on
the Copilot SDK, CLI, or another language runtime:

1. Send the user message and call the chat model.
2. Append the assistant message to the transcript.
3. Execute every requested tool through a JSON-Schema definition and an async
   handler, preserving each tool-call ID.
4. Append tool results and continue until the model returns no tool calls.

`backend/src/main/java/cn/nolaurene/cms/service/sandbox/backend/copilot` contains
the loop, event envelope, permission callback, deterministic tool registry, and
`SKILL.md` loader. Skills are prompt modules discovered from immediate child
directories with optional YAML frontmatter (`name` and `description`); they are
not exposed as synthetic `skill_*` tools. Existing Planner/LangChain execution
remains available as a compatibility fallback.

The current LangChain4j MCP client adapter exposes text tool content to this
loop. MCP image/resource payloads require a raw MCP transport adapter. The
standard Java result model already includes `binaryResultsForLlm`, but the
legacy client path does not yet decode those payloads.

When no permission handler is configured, requests are emitted and remain
pending until the host resolves them through `CopilotToolRegistry`, matching
Copilot's permission event flow. The web executor uses the explicit
`allow-tools` policy; set it to false for deterministic denial in headless
deployments.

`defer` is retained on tool definitions for wire compatibility; this Java
runtime currently uses eager declarations because it does not ship a separate
tool-search model/tool.

When `max-turns` is reached, the runtime emits `session.limit` and
`LIMIT_REACHED` and does not emit a normal `DONE` completion. The session is
left resumable instead of being reported as successfully completed.

Model cancellation is best-effort at the Java future boundary. Providers that
ignore thread interruption may finish their underlying HTTP request in a
daemon worker after the session has already emitted its abort/timeout event.

Restart snapshots preserve the normalized text transcript and tool-call IDs.
Multimodal message content and binary MCP resources are intentionally left to a
future raw-MCP adapter instead of being silently serialized as text.
Snapshots are written at turn start and completion (and on loop errors/abort),
so a process kill during an in-flight model request still preserves the new
user prompt, but cannot provide a full assistant/tool replay for that partial
turn. A side-effecting tool that completed after the turn-start snapshot may
be invoked again after a crash; such tools should be idempotent when crash
recovery matters.

Useful configuration overrides:

```yaml
copilot:
  loop:
    enabled: true
    max-turns: 30
    model-timeout-ms: 300000
    tool-timeout-ms: 300000
    max-transcript-messages: 1000
    allow-tools: true
  skills:
    disabled: "experimental-feature,deprecated-tool"
    directories: "/workspace/.github/skills,/workspace/skills"
```

`copilot.skills.directories` accepts comma-separated Copilot-style skill roots;
each root is scanned one level deep for `SKILL.md`, with earlier roots taking
priority for duplicate names.
