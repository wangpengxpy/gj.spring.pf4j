---
name: gj-plugin-change
description: Modify an existing gj.spring.pf4j plugin — analyze current structure, plan changes with OpenSpec, implement via gj-plugin skill, verify.
args: [change_description]
---

# gj-plugin-change Command

> **Status**: Placeholder. This command handles requirement changes for existing plugins.
> For new plugin development, use `/gj-plugin-new`.

Read `CLAUDE.md` and invoke `skills/gj/plugin.md` for framework conventions.

## Workflow

```
/gj-plugin-change "add export feature to user plugin"
    │
    ├─ 1. Analyze existing plugin structure (read source tree, tasks.md, lat.md)
    ├─ 2. OpenSpec: Explore → generate delta specs (MODIFIED requirements)
    ├─ 3. OpenSpec: Plan → update tasks.md with new/changed sub-tasks
    ├─ 4. gj-plugin: Execute → generate new/modified Java files
    ├─ 5. Verify → mvn compile + task check
    └─ 6. Document → update lat.md knowledge graph
```

## Key Differences from gj-plugin-new

| | gj-plugin-new | gj-plugin-change |
|---|---|---|
| Starting point | Empty archetype | Existing plugin with code |
| OpenSpec mode | Propose (ADDED) | Explore → propose (MODIFIED) |
| Risk | Low (greenfield) | Medium (must not break existing code) |
| Verification | mvn compile | mvn compile + diff review |
