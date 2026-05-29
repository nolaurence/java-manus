# Skill-Based Agent Loop Implementation

## Overview
This PR introduces a skill-based agent loop system inspired by [pi_cloud_agent](https://github.com/nolaurence/pi_cloud_agent)'s skills architecture. Skills are modular, installable packages that extend agent capabilities with additional tools and metadata.

## Features

### Backend
- **Skill Management API** (`/api/skills/*`)
  - `GET /api/skills/{userId}` - List installed skills
  - `PUT /api/skills/{userId}/install` - Install skill from zip
  - `PUT /api/skills/{userId}/{skillId}/enabled` - Toggle skill on/off
  - `GET /api/skills/agent/{agentId}` - Get agent's skills
  - `POST /api/skills/agent/{agentId}` - Set agent's skills

- **Skill Domain Model**
  - `Skill` - Domain object with metadata + tool specifications
  - `SkillManifest` - Parsed from SKILL.md front matter
  - `SkillService` - Handles zip extraction, metadata parsing, tool loading

- **Agent Integration**
  - Agent model extended with `List<Skill> skills`
  - `AgentSession` loads enabled skills and merges their `ToolSpecification`s into available tools
  - `AgentController` loads user's enabled skills when creating an agent
  - Skills contribute tools to the Langchain4j tool-calling loop

### Frontend
- **Skills Management Page** (`/skills`)
  - List installed skills with metadata (name, version, description, tags)
  - Toggle enable/disable per skill
  - Upload and install new skills via zip file
  - Empty state with CTA for first-time users

- **Navigation**
  - Added wrench icon in HomePage header for quick access to Skills
  - Route registered at `/skills`

### Database
- `skills` table - stores skill metadata per user
- `agent_skills` table - stores agent-skill associations

## Skill Package Format
Skills are zip packages containing:
```
skill-package.zip
├── SKILL.md          # Metadata (front matter YAML)
└── tools/
    └── tools.json    # Tool specifications (optional)
```

### SKILL.md Example
```markdown
---
name: Web Scraper
description: Extract structured data from web pages
version: 1.0.0
tags: web, scraping, data
---

# Web Scraper Skill

Extracts data from web pages using CSS selectors.
```

## Compatibility
- **Fully backward compatible** - existing agents work without skills
- Skills are opt-in per user
- Existing Planner/Executor/ReAct loop unchanged
- Skills simply extend the available tool set
- SSE event stream format unchanged

## Architecture Diagram
```
User Uploads Zip
      ↓
SkillService.extract() → ~/.java-manus/skills/{userId}/{skillId}/
      ↓
Parse SKILL.md → SkillManifest
Parse tools/tools.json → ToolSpecification[]
      ↓
Store in DB (skills table)
      ↓
Agent Creation → Load user's enabled skills
      ↓
AgentSession.initialize() → Merge skill tools into agent.toolSpecifications
      ↓
ExecutionSubAgent → LLM sees skill tools in function calling
```

## Testing
1. Run `sql/skills.sql` to create tables
2. Navigate to `/skills` page
3. Upload a zip containing SKILL.md
4. Enable the skill
5. Create a new agent - the skill's tools will be available

## Files Changed
- **New**: 11 files (domain, service, controller, entity, mapper, frontend, SQL)
- **Modified**: 5 files (Agent model, AgentSession, AgentController, routes, HomePage)

## TODOs for Follow-up PRs
- [ ] Skill tool execution handler (custom tool implementations beyond MCP)
- [ ] Skill prompt templates (custom system prompts per skill)
- [ ] Skill marketplace / discovery
- [ ] Skill versioning and updates
- [ ] Granular skill permissions
