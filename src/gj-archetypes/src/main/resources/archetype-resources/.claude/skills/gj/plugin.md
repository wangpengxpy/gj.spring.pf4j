---
name: gj-plugin
description: gj.spring.pf4j plugin development assistant — generate plugins from archetype, implement business features, and produce knowledge graphs. Internally uses OpenSpec skills (bundled under gj/openspec/) for requirements analysis and task decomposition.
---

# gj-plugin Skill

> Reads `CLAUDE.md` for project infrastructure conventions before acting.
> Internally invokes OpenSpec skills at `skills/gj/openspec/{propose,explore,apply,archive}/` for planning phases.
> OpenSpec is NOT exposed to developers — they use `/gj-plugin-new` and `/gj-plugin-change` only.

You are a gj.spring.pf4j plugin development assistant. Your responsibilities cover the full plugin
development lifecycle: generating a plugin project from the archetype, implementing business features
following framework conventions, and producing a knowledge graph of the resulting plugin architecture.

## Prerequisites

Before any plugin work, verify the archetype is installed:

```bash
cd <project-root>
mvn install -pl src/gj-archetypes -DskipTests
```

If `gj-pf4j` has been modified, also reinstall it:

```bash
mvn install -pl src/gj-parent,src/gj-pf4j -DskipTests
```

## 1. Install Template (generate a new plugin project)

When the user asks to create a new plugin, generate it from the archetype:

```bash
mvn archetype:generate \
  -DarchetypeGroupId=io.github.wangpengxpy \
  -DarchetypeArtifactId=gj-archetype \
  -DarchetypeVersion=1.0.0 \
  -DpluginName=<name> \
  -DpackagePrefix=<packagePrefix>
```

- `<name>`: short plugin name in lowercase (e.g. `user`, `order`)
- `<packagePrefix>`: Java package prefix (e.g. `gj.module`)
- Resulting `plugin.id` will be `<packagePrefix>.<name>` (e.g. `gj.module.user`)
- The generated project is placed in `<name>-plugin/` in the current working directory.
- After generation, remind the user that `plugin.id` MUST match the plugin's Java package name.

### Template Structure After Generation

```
<name>-plugin/
├── pom.xml
├── pom-parent.xml
└── src/main/
    ├── java/<packagePrefix>/<name>/
    │   ├── <Name>Plugin.java          → extends GJPlugin
    │   ├── <Name>Config.java          → @ConfigurationProperties
    │   ├── controllers/
    │   ├── dao/
    │   ├── model/
    │   ├── dto/
    │   ├── request/
    │   ├── response/
    │   ├── service/
    │   ├── serviceimpl/
    │   ├── modelmapper/
    │   ├── hubs/
    │   ├── jobs/
    │   └── listeners/
    └── resources/
        ├── plugin.properties
        ├── <packagePrefix>.<name>.json
        └── <packagePrefix>.<name>.properties
```

## 2. Implement Business Features

When the user describes a feature to implement, create the appropriate files following these patterns.
All plugin beans use `@Component` (or `@Service` / `@RestController`) — never `@Extension`.

### Controller

```java
package <packagePrefix>.<pluginName>.controllers;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/<pluginName>")
public class <Name>Controller {
    @GetMapping("/list")
    public List<XxxResponse> getList() { ... }
    @PostMapping("/create")
    public boolean create(@RequestBody XxxRequest request) { ... }
}
```

### Service + ServiceImpl

```java
// service/<Name>Service.java — interface
// serviceimpl/<Name>ServiceImpl.java — @Service + @Transactional
// Inject Mapper via constructor (final field + @RequiredArgsConstructor)
```

### DAO / Mapper

```java
package <packagePrefix>.<pluginName>.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import <packagePrefix>.<pluginName>.model.<Entity>;

public interface <Name>Mapper extends BaseMapper<Entity> {
}
```

- Must be placed in `{pluginId}.dao` package.
- Extend `BaseMapper<T>` only — no annotation needed.

### ModelMapper Config

```java
package <packagePrefix>.<pluginName>.modelmapper;

import gj.pf4j.modelmapper.GJPluginModelMapperConfig;
import gj.pf4j.modelmapper.GJPluginTypeMapConfig;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class <Name>ModelMapperConfig implements GJPluginModelMapperConfig {
    @Override
    public List<GJPluginTypeMapConfig> getTypeMapConfigs() {
        return List.of(
            GJPluginTypeMapConfig.of(Entity.class, DTO.class)
        );
    }
}
```

### Socket.IO Hub

```java
package <packagePrefix>.<pluginName>.hubs;

import gj.pf4j.socketio.GJHub;
import gj.pf4j.socketio.GJHubMethod;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;

@Component
public class <Name>Hub extends GJHub {
    public <Name>Hub() { super("<name>Hub"); }

    @Override public CompletableFuture<Void> onConnectedAsync() { ... }
    @Override public CompletableFuture<Void> onDisconnectedAsync() { ... }

    @GJHubMethod("eventName")
    public void onEvent(DataType data) { ... }
}
```

### Quartz Job

```java
package <packagePrefix>.<pluginName>.jobs;

import gj.pf4j.quartzjob.IPluginJob;
import gj.pf4j.quartzjob.annotation.PluginJob;
import org.springframework.stereotype.Component;

@Component
@PluginJob(name = "<uniqueName>", intervalSeconds = 60)
public class <Name>Job implements IPluginJob {
    @Override
    public void execute() { ... }
}
```

### EventBus Listener

```java
package <packagePrefix>.<pluginName>.listeners;

import gj.pf4j.eventbus.GJPluginLocalEventListener;
import org.springframework.stereotype.Component;

@Component
public class <Name>Listener implements GJPluginLocalEventListener<EventType> {
    @Override
    public void HandleEvent(EventType event) { ... }
}
```

### Event Definition

```java
package <packagePrefix>.<pluginName>.events;

import gj.pf4j.eventbus.EventName;

@EventName("<pluginName>.<action>")
public class XxxEvent {
    private Long id;
    private String name;
    // getters and setters
}
```

## 3. Knowledge Graph Generation

After implementing any features, generate a knowledge graph file at
`<plugin-dir>/lat.md` showing the plugin's architecture as a Graphviz DOT diagram.

### Rules

- File: `<plugin-dir>/lat.md`
- Format: markdown with a `dot` code block
- Node types and colors:
  - **Controller**: blue, shape=component
  - **Service**: green, shape=component
  - **DAO/Mapper**: orange, shape=component
  - **Hub**: purple, shape=component
  - **Job**: red, shape=component
  - **EventListener**: teal, shape=component
  - **ModelMapperConfig**: yellow, shape=component
  - **External dependency** (DataSource, ModelMapper, Scheduler, EventBus): gray, shape=cylinder
- Edges: solid arrow for dependency/call, dashed arrow for data flow
- Use subgraphs for logical grouping (API layer, Business layer, Data layer, Real-time layer)
- Include a legend

### Template

```markdown
# <PluginName> Plugin — Knowledge Graph

\`\`\`dot
digraph <PluginName>Plugin {
    rankdir=TB;
    node [fontname="sans-serif"];

    subgraph cluster_api {
        label="API Layer";
        style=filled; fillcolor="#f0f4ff";
        // Controllers
    }

    subgraph cluster_business {
        label="Business Layer";
        style=filled; fillcolor="#f0fff0";
        // Services, EventListeners, Jobs
    }

    subgraph cluster_data {
        label="Data Layer";
        style=filled; fillcolor="#fff8f0";
        // DAOs, ModelMapperConfigs, Model
    }

    subgraph cluster_realtime {
        label="Real-time Layer";
        style=filled; fillcolor="#f8f0ff";
        // Hubs
    }

    // External dependencies
    // Edges
}
\`\`\`
```

## After Any Feature Creation

1. Verify `mvn compile` passes in the plugin directory.
2. Regenerate `lat.md` to reflect the current architecture.
3. Report what was created and where.
