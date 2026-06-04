# CLAUDE_CN.md — gj.spring.pf4j

## 项目概述

gj.spring.pf4j 是基于 PF4J 与 Spring 的轻量级模块化插件框架，无 Spring Boot 运行时重依赖。主应用通过
`@ComponentScan("gj")` 激活框架。插件以 JAR 形式打包，放入 `plugins/` 目录即可运行时发现。

- **GitHub**: https://github.com/wangpengxpy/gj.spring.pf4j
- **许可证**: MIT
- **Java**: 17+（硬性要求——Spring 6.x 字节码最低版本）
- **构建**: Maven 3.9+，根目录单聚合 POM

---

## 模块结构（5 个模块）

| 模块 | ArtifactId | 用途 |
|--------|-----------|---------|
| `src/gj-parent` | `gj-dependencies` | 父 POM（BOM），管理全部 46 个依赖版本和编译器配置 |
| `src/gj-pf4j` | `gj-pf4j` | 核心框架——插件生命周期、路由、MyBatis、Socket.IO、Quartz、EventBus、ModelMapper |
| `src/gj-modelmapper` | `gj-modelmapper` | **可选模块**——`@GJModelMapperScan` 注解，主应用 ModelMapper 配置扫描 |
| `src/gj-archetypes` | `gj-archetype` | Maven 原型，用于生成新插件项目 |
| `src/gj-plugin-demo` | （示例） | 参考插件，展示全部能力 |

构建顺序: `gj-parent` → `gj-pf4j` → `gj-modelmapper` / `gj-archetypes` / `gj-plugin-demo`

---

## 插件能力（6 大类）

全部能力通过 **Spring Bean 扫描** 自动发现——**不使用 PF4J @Extension 或 ExtensionPoint**。

### 1. REST Controller
- 插件编写标准 `@RestController` + `@RequestMapping`
- `GJPluginRequestMappingHandlerMapping`（MVC）或 `GJPluginWebFluxRequestMappingHandlerMapping`（WebFlux）
  在 `GJPluginStartedEvent` 时自动注册路由到主应用路由表
- 主应用 Web 模式自动检测，插件代码两种模式完全一致

### 2. MyBatis-Plus 数据访问
- DAO 包路径: `{pluginId}.dao`（如 `gj.module.user.dao`）
- Mapper 接口继承 `BaseMapper<T>`——由 `MapperScannerConfigurer` 发现，不依赖 PF4J
- 每个插件拥有独立 `SqlSessionFactory` / `SqlSessionTemplate` / `TransactionManager`，
  全部共享主应用的 `DataSource`
- 由 `GJPluginMybatisSqlSessionManager` 统一管理

### 3. ModelMapper 对象映射
- 插件实现 `GJPluginModelMapperConfig` + `@Component`，返回 `List<GJPluginTypeMapConfig>`
- 由 `GJPluginLifecycleManager.registerModelMappers()` 通过 `pluginCtx.getBeansOfType()` 自动发现
- 映射追加到主应用的 `ModelMapper` 上（通过父子容器继承共享）
- 若主应用无 `ModelMapper`，框架自动创建

### 4. Socket.IO 实时通信（Hub）
- 插件继承 `GJHub("hubName")` + `@Component`，方法标注 `@GJHubMethod`
- 通过 `pluginCtx.getBeansOfType(GJHub.class)` 自动发现，经 `GJHubManager` 注册
- 支持 `getClients()`（all/caller/others/group/user 定向推送）和 `getGroups()`（加入/离开/查询）
- 组状态由 `GJHubManager` 集中管理（唯一数据源）
- 默认线程池: Cached（0 核心 / 1000 最大 / 60s 存活 / SynchronousQueue）

### 5. Quartz 定时任务
- 插件实现 `IPluginJob` + `@Component` + `@PluginJob(name=..., intervalSeconds=...)`
- 支持: 固定间隔（`intervalSeconds`）、Cron 表达式（`cronExpression`）、一次性执行（`runOnce`）
- `PluginJobManager` 通过 `pluginCtx.getBeansWithAnnotation(PluginJob.class)` 自动扫描
- JobKey 格式: `{pluginId}:{jobName}`
- `GJQuartzConfig` 在主应用未配置 Scheduler 时自动创建默认实例（内存模式）
- 优雅关闭: 暂停 → 等待 30s → 删除

### 6. 进程内 EventBus
- 事件类: 标注 `@EventName("名称.用点.分隔")`，支持 Ant 风格通配符
- 监听器: 实现 `GJPluginLocalEventListener<T>` + `@Component`，方法 `HandleEvent(T event)`
- 发布: 注入 `GJPluginLocalEventBus`，调用 `publish(event)`（同步）或 `publishAsync(event)`（异步）
- 异步线程池: 与 Hub 相同的 Cached 配置

---

## 插件生命周期

```
loadPlugin (PF4J)
  └─ GJSpringPlugin.start()
       ├─ preCreateApplicationContext() → AnnotationConfigApplicationContext(parent = 主应用上下文)
       ├─ beforeApplicationContextRefresh() → [钩子] 编程式注册 Bean
       ├─ GJPluginLifecycle.registerPluginResources() → i18n / MyBatis 初始化
       ├─ context.refresh()
       └─ afterApplicationContextReady() → [钩子]
  → 发布 GJPluginStartingEvent
  → GJPluginManager 发布 GJPluginStartedEvent
       └─ GJPluginLifecycleManager.onApplicationEvent(GJPluginStartedEvent):
            registerControllers → registerHubs → registerModelMappers → registerEventListeners → registerQuartzJobs

stopPlugin
  └─ GJSpringPlugin.stop()
       └─ GJPluginLifecycle.unregisterPluginResources():
            unregisterControllers → unregisterHubs → unregisterI18N → unregisterMybatis → unregisterEventListeners → unregisterQuartzJobs
       └─ context.close()
```

**关键**: 注册在 refresh 之后，注销在 close 之前。全部可选——若某项能力的 Manager Bean 不存在（如未配置 Quartz），对应步骤静默跳过。

---

## 常用开发命令

```bash
# 构建全部模块
mvn clean install -DskipTests

# 构建指定模块
mvn install -pl src/gj-pf4j -DskipTests

# 安装 archetype 到本地仓库（生成插件前必须执行）
mvn install -pl src/gj-archetypes -DskipTests

# 从 archetype 生成新插件项目
mvn archetype:generate \
  -DarchetypeGroupId=io.github.wangpengxpy \
  -DarchetypeArtifactId=gj-archetype \
  -DarchetypeVersion=1.0.0 \
  -DpluginName=<名称> \
  -DpackagePrefix=gj.module
```

---

## 插件项目目录约定

```
{plugin}.java                    → 继承 GJPlugin，生命周期钩子
{plugin}Config.java              → @ConfigurationProperties 绑定
controllers/                     → @RestController（自动注册路由）
dao/                             → 继承 BaseMapper<T>（MapperScannerConfigurer 自动扫描）
model/                           → @TableName 实体
dto/                             → 数据传输对象
request/                         → API 入参 DTO
response/                        → API 返回 DTO
service/                         → 业务接口
serviceimpl/                     → @Service 实现
modelmapper/                     → 实现 GJPluginModelMapperConfig + @Component
hubs/                            → 继承 GJHub + @Component + @GJHubMethod
jobs/                            → 实现 IPluginJob + @Component + @PluginJob
listeners/                       → 实现 GJPluginLocalEventListener<T> + @Component
```

约束: `plugin.id` 必须与插件 Java 包名完全一致。

---

## 核心架构决策

1. **插件不接触 PF4J API** — 全部能力只使用 Spring 注解
2. **可选依赖防护** — `PluginJobManager` 有 `@ConditionalOnBean(Scheduler.class)`，
   EventBus/Hub/ModelMapper 注册前检查 Manager Bean 是否存在
3. **组状态统一** — `GJHubManager.groupConnections` 是组成员关系的唯一数据源
4. **ModelMapper 共享** — 插件映射追加到主应用的 `ModelMapper`，通过父子容器继承
5. **插件上下文继承主应用** — `setParent(mainApplicationContext)` — 插件可注入主应用 Bean
6. **Bean 名称隔离** — `GJPluginBeanNameGenerator` 为每个插件 Bean 添加前缀防冲突
7. **双 Web 路由模式** — MVC 和 WebFlux 同时支持，通过 Bean 存在性自动检测

---

## 命名规范

- 包名: `gj.pf4j.<功能>` 用于框架，`{packagePrefix}.{pluginName}` 用于插件
- 类名: `GJ` 前缀用于框架类（GJHub, GJPlugin, GJPluginManager）
- 插件类: `{PluginName}` 前缀（UserHub, UserJob, UserListener）
- Bean 名: `plugin_{用途}_{pluginId}` 模式用于内部 Bean
