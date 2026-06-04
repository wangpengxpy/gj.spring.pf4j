---
name: gj-plugin-new
description: Create a new gj.spring.pf4j plugin — internally uses OpenSpec (skills/gj/openspec/) for planning and gj-plugin (skills/gj/plugin.md) for implementation. OpenSpec is never exposed to the developer.
args: [description]
---

# gj-plugin-new Command

You create new gj.spring.pf4j plugins from a natural-language description.
Internally you delegate to two skill sets:

- **Planning**: `skills/gj/openspec/{propose,explore,apply,archive}/` — OpenSpec SDD workflow
- **Implementation**: `skills/gj/plugin.md` (gj-plugin skill) — generates Java files per framework conventions

Read `CLAUDE.md` for project conventions before starting.

## Overview

```
Developer: /gj-plugin-new "user management plugin with CRUD, real-time push, scheduled cleanup"
    │
    ├─ Phase 1 — Define   (skills/gj/openspec/propose) → proposal.md + specs/
    ├─ Phase 2 — Plan     (skills/gj/openspec/apply)   → design.md + tasks.md
    ├─ Phase 3 — Execute  (skills/gj/plugin.md)        → generate Java code
    ├─ Phase 4 — Verify   (mvn compile)                → compile check + task audit
    └─ Phase 5 — Document (skills/gj/plugin.md)        → lat.md knowledge graph
```

## Phase 1 — Define

Use `skills/gj/openspec/propose/SKILL.md`. The developer's description is the proposal prompt.

1. Determine `change-id` from the description (kebab-case, e.g. `user-management-plugin`).
2. Create `openspec/changes/<change-id>/proposal.md` with Why / What Changes / Impact sections.
3. Create `openspec/changes/<change-id>/specs/<plugin-name>/spec.md` with `## ADDED Requirements`.
   Extract from the description:
   - What entities exist and their fields
   - What REST endpoints are needed
   - What real-time hubs, scheduled jobs, or event listeners are needed

## Phase 2 — Plan

Use `skills/gj/openspec/apply/SKILL.md` to translate specs into implementation plan.

1. Create `openspec/changes/<change-id>/design.md` stating the architecture decisions:
   - Controllers: `@RestController`, auto-registered by `GJPluginRequestMappingHandlerMapping`
   - Data access: MyBatis-Plus `BaseMapper`, isolated `SqlSessionFactory` via `GJPluginMybatisSqlSessionManager`
   - Realtime: `GJHub` + `@GJHubMethod` (if specs mention hub)
   - Scheduled: `IPluginJob` + `@PluginJob` (if specs mention job)
   - Events: `GJPluginLocalEventListener` + `@EventName` (if specs mention event)

2. Create `openspec/changes/<change-id>/tasks.md` — an ordered checkbox list:

   | Spec Requirement | Atomic Tasks |
   |-----------------|-------------|
   | Entity + DAO | `[ ] model/<Entity>.java` → `[ ] dao/<Entity>Mapper.java` |
   | Service | `[ ] service/<Name>Service.java` → `[ ] serviceimpl/<Name>ServiceImpl.java` |
   | Controller | `[ ] controllers/<Name>Controller.java` |
   | ModelMapper | `[ ] modelmapper/<Name>ModelMapperConfig.java` |
   | Hub | `[ ] hubs/<Name>Hub.java` |
   | Job | `[ ] jobs/<Name>Job.java` |
   | Event + Listener | `[ ] events/<EventName>.java` → `[ ] listeners/<Name>Listener.java` |

   Dependency order: Entity → Mapper → Service → ServiceImpl → Controller.
   Jobs, Hubs, EventListeners in parallel after their Service deps.
   Mark inter-task dependencies with `**DependsOn**: <task#>`.

3. If no plugin project exists yet (`pom.xml` missing), add `[ ] 0. Generate plugin project from archetype` first.

## Phase 3 — Execute

Use `skills/gj/plugin.md` (gj-plugin skill) for every code generation step.

For each unchecked task in `tasks.md`, in order:

1. Mark `[ ]` → `[x]`.
2. Delegate to **gj-plugin** skill with a prompt containing:
   - `packagePrefix` and `pluginName` from the spec
   - Target file path (e.g. `model/User.java`, `controllers/UserController.java`)
   - All structured data from that spec requirement (fields, endpoints, schedule, event name, etc.)
3. Verify the written file exists at the expected path.
4. On failure: retry once. If still failing, pause and report.

**Do NOT write code directly. Always call gj-plugin skill.**

## Phase 4 — Verify

1. Run in the plugin directory:
   ```bash
   mvn compile
   ```
2. If compilation fails:
   - Parse errors, fix via gj-plugin skill, re-compile. Max 3 attempts.
   - After 3 failures: mark affected tasks `[!]` in tasks.md, report errors clearly.
3. Confirm all tasks.md items are `[x]`.
4. Verify directory structure matches `CLAUDE.md` conventions.

## Phase 5 — Document

1. Generate `<plugin-dir>/lat.md` — a Graphviz DOT knowledge graph:
   - **Blue** component: Controllers
   - **Green** component: Services
   - **Orange** component: DAO + Model entities
   - **Purple** component: Hubs
   - **Red** component: Jobs
   - **Teal** component: Event Listeners
   - **Gray** cylinder: External deps (DataSource, ModelMapper, Scheduler, GJHubManager, GJPluginLocalEventBus)
   - Solid edges: constructor injection
   - Dashed edges: event publishing, scheduled triggers
   - Subgraphs: API / Business / Data / Real-time layers
   - Legend node.

2. Archive with `skills/gj/openspec/archive/SKILL.md` to merge delta specs.

3. Print summary:
   ```
   ┌─────────────────────────────────────┐
   │  gj-plugin-new Complete             │
   ├─────────────────────────────────────┤
   │  Plugin   : <packagePrefix>.<name>  │
   │  Files    : N created               │
   │  Tasks    : N/N completed           │
   │  Compile  : PASSED                  │
   │  Graph    : <dir>/lat.md            │
   └─────────────────────────────────────┘
   ```

## Error Handling

| Scenario | Action |
|----------|--------|
| No plugin directory | Generate via `mvn archetype:generate` (gj-plugin skill "Install Template") |
| `openspec/` dir missing | Create it, write `project.md` from CLAUDE.md context |
| Task depends on missing REQ | Skip and warn in summary |
| `mvn compile` fails 3x | Mark tasks `[!]`, report all errors, pause |
| gj-plugin skill fails | Retry once, then pause |
| OpenSpec archive fails | Skip archive, note change-id for manual archival |
