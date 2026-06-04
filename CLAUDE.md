# CLAUDE.md — gj.spring.pf4j

## Project Identity

gj.spring.pf4j is a lightweight modular plugin framework built on PF4J and Spring, with no Spring Boot runtime
dependency. Host applications use `@ComponentScan("gj")` to activate the framework. Plugins are packaged as JARs
and deployed into a `plugins/` directory for runtime discovery.

- **GitHub**: https://github.com/wangpengxpy/gj.spring.pf4j
- **License**: MIT
- **Java**: 17+ (hard requirement — Spring 6.x bytecode)
- **Build**: Maven 3.9+, single aggregator POM at root

---

## Module Structure (3 modules)

| Module | ArtifactId | Purpose |
|--------|-----------|---------|
| `src/gj-parent` | `gj-dependencies` | Parent BOM, all version properties (46 deps), compiler settings |
| `src/gj-pf4j` | `gj-pf4j` | Core framework — plugin lifecycle, routing, MyBatis, Socket.IO, Quartz, EventBus, ModelMapper |
| `src/gj-modelmapper` | `gj-modelmapper` | **Optional** — `@GJModelMapperScan` annotation for host-app ModelMapper config scanning |
| `src/gj-archetypes` | `gj-archetype` | Maven archetype to generate new plugin projects |
| `src/gj-plugin-demo` | (demo) | Reference plugin showing all capabilities |

Build order: `gj-parent` → `gj-pf4j` → `gj-modelmapper` / `gj-archetypes` / `gj-plugin-demo`.

---

## Plugin Capabilities (6 categories)

All capabilities are auto-discovered via Spring bean scanning — **no PF4J @Extension or ExtensionPoint used**.

### 1. REST Controller
- Plugin writes standard `@RestController` + `@RequestMapping`
- `GJPluginRequestMappingHandlerMapping` (MVC) or `GJPluginWebFluxRequestMappingHandlerMapping` (WebFlux)
  auto-registers routes into the host's route table on `GJPluginStartedEvent`
- Host app web mode is auto-detected; plugin code is identical for both stacks

### 2. MyBatis-Plus Data Access
- DAO package: `{pluginId}.dao` (e.g. `gj.module.user.dao`)
- Mapper interfaces extend `BaseMapper<T>` — discovered by `MapperScannerConfigurer`, not PF4J
- Each plugin gets isolated `SqlSessionFactory` / `SqlSessionTemplate` / `TransactionManager`,
  all sharing the host's `DataSource`
- Managed by `GJPluginMybatisSqlSessionManager`

### 3. ModelMapper Object Mapping
- Plugin implements `GJPluginModelMapperConfig` + `@Component`, returns `List<GJPluginTypeMapConfig>`
- Auto-discovered via `pluginCtx.getBeansOfType()` in `GJPluginLifecycleManager.registerModelMappers()`
- Mappings appended to the host application's `ModelMapper` (shared via parent-child context inheritance)
- If host has no `ModelMapper`, framework creates one automatically

### 4. Socket.IO Real-Time Communication (Hub)
- Plugin extends `GJHub("hubName")` + `@Component`, methods annotated with `@GJHubMethod`
- Auto-discovered via `pluginCtx.getBeansOfType(GJHub.class)` and registered through `GJHubManager`
- Supports `getClients()` (all/caller/others/group/user targeting) and `getGroups()` (add/remove/query)
- Group state managed centrally by `GJHubManager` (single source of truth)
- Default thread pool: Cached (0 core / 1000 max / 60s TTL / SynchronousQueue)

### 5. Quartz Scheduled Jobs
- Plugin implements `IPluginJob` + `@Component` + `@PluginJob(name=..., intervalSeconds=...)`
- Supports: fixed interval (`intervalSeconds`), cron expression (`cronExpression`), run-once (`runOnce`)
- Auto-scanned via `pluginCtx.getBeansWithAnnotation(PluginJob.class)` by `PluginJobManager`
- JobKey format: `{pluginId}:{jobName}`
- `GJQuartzConfig` creates default `Scheduler` bean (in-memory) if host hasn't configured one
- Graceful shutdown: pause → wait 30s for running jobs → delete

### 6. In-Process EventBus
- Event class: annotated with `@EventName("name.with.dots")`, supports Ant-style wildcards
- Listener: implements `GJPluginLocalEventListener<T>` + `@Component`, method `HandleEvent(T event)`
- Publishing: inject `GJPluginLocalEventBus`, call `publish(event)` (sync) or `publishAsync(event)` (async)
- Async thread pool: same Cached pool config as Hub

---

## Plugin Lifecycle

```
loadPlugin (PF4J)
  └─ GJSpringPlugin.start()
       ├─ preCreateApplicationContext() → AnnotationConfigApplicationContext(parent = mainAppCtx)
       ├─ beforeApplicationContextRefresh() → [hook] programmatic bean registration
       ├─ GJPluginLifecycle.registerPluginResources() → i18n / MyBatis init
       ├─ context.refresh()
       └─ afterApplicationContextReady() → [hook]
  → GJPluginStartingEvent published
  → GJPluginManager publishes GJPluginStartedEvent
       └─ GJPluginLifecycleManager.onApplicationEvent(GJPluginStartedEvent):
            registerControllers → registerHubs → registerModelMappers → registerEventListeners → registerQuartzJobs

stopPlugin
  └─ GJSpringPlugin.stop()
       └─ GJPluginLifecycle.unregisterPluginResources():
            unregisterControllers → unregisterHubs → unregisterI18N → unregisterMybatis → unregisterEventListeners → unregisterQuartzJobs
       └─ context.close()
```

**Key**: Registration is post-refresh, unregistration is pre-close. All are optional — if a capability's
manager bean is absent (e.g. Quartz not configured), the corresponding step skips silently.

---

## Development Commands

```bash
# Build all modules
cd D:\work\github\gj.spring.pf4j
mvn clean install -DskipTests

# Build specific module
mvn install -pl src/gj-pf4j -DskipTests

# Install archetype to local repo (needed before generating plugins)
mvn install -pl src/gj-archetypes -DskipTests

# Generate a new plugin project from archetype
mvn archetype:generate \
  -DarchetypeGroupId=io.github.wangpengxpy \
  -DarchetypeArtifactId=gj-archetype \
  -DarchetypeVersion=1.0.0 \
  -DpluginName=<name> \
  -DpackagePrefix=gj.module
```

---

## Directory Conventions (Plugin Projects)

```
{plugin}.java                    → extends GJPlugin, lifecycle hooks
{plugin}Config.java              → @ConfigurationProperties binding
controllers/                     → @RestController (auto-registered)
dao/                             → extends BaseMapper<T> (auto-scanned by MapperScannerConfigurer)
model/                           → @TableName entities
dto/                             → data transfer objects
request/                         → API input DTOs
response/                        → API return DTOs
service/                         → business interfaces
serviceimpl/                     → @Service implementations
modelmapper/                     → implements GJPluginModelMapperConfig + @Component
hubs/                            → extends GJHub + @Component + @GJHubMethod
jobs/                            → implements IPluginJob + @Component + @PluginJob
listeners/                       → implements GJPluginLocalEventListener<T> + @Component
```

Constraint: `plugin.id` must match the plugin's Java package name exactly.

---

## Key Architectural Decisions

1. **No PF4J API exposure to plugins** — all capabilities use Spring annotations only
2. **Optional dependencies are defensive** — `PluginJobManager` has `@ConditionalOnBean(Scheduler.class)`,
   EventBus/Hub/ModelMapper registration checks for manager bean existence before acting
3. **Group state unified** — `GJHubManager.groupConnections` is the single source of truth for group membership
4. **ModelMapper shared** — plugin mappings appended to host's `ModelMapper` via parent-child context inheritance
5. **Plugin context inherits from host** — `setParent(mainApplicationContext)` — plugins can inject host beans
6. **Bean name isolation** — `GJPluginBeanNameGenerator` prefixes plugin bean names to prevent conflicts
7. **Dual web routing** — MVC and WebFlux both supported; auto-detected by bean presence

---

## Naming Conventions

- Package: `gj.pf4j.<feature>` for framework, `{packagePrefix}.{pluginName}` for plugins
- Classes: `GJ` prefix for framework classes (GJHub, GJPlugin, GJPluginManager)
- Plugin classes: `{PluginName}` prefix (UserHub, UserJob, UserListener)
- Bean names: `plugin_{purpose}_{pluginId}` pattern for internal beans
