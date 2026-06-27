# 插件热加载设计方案 & Bug 修复

## 一、Bug 清单

### Bug 1: `doStopPlugin()` 事件类型错误

**位置**: `GJPluginManager.java:256`

```java
springPlugin.getApplicationContext().publishEvent(
    new GJPluginStartedEvent(pluginWrapper.getPluginId(), springPlugin)  // ← 应该是 StoppedEvent
);
```

`doStopPlugin` 里发了 `StartedEvent`，拷贝粘贴错误。修复：`GJPluginStartedEvent` → `GJPluginStoppedEvent`。

---

### Bug 2: `GJPluginService.stopPlugin()` 锁删除过早

**位置**: `GJPluginService.java:56`

```java
PluginState pluginState = pluginManager.stopPlugin(pluginId);
pluginLocks.remove(pluginId);   // ← stop 后立即删锁
```

stop 后插件在注册表中仍是 STOPPED 状态，后续可被 `restartPlugin()`。删锁后 restart 拿到的是新 ReentrantLock，和 stop 持有的不是同一把，失去并发保护。

修复：`stopPlugin` 不删锁，只在插件彻底从注册表移除时删。

---

## 二、当前代码分析

### 现有 `GJPluginManager` 方法

| 方法 | 实现 | 语义 |
|------|------|------|
| `startPlugin(id)` | `doStartPlugin` → `super.startPlugin` | 启动已加载的插件 |
| `stopPlugin(id)` | `doStopPlugin` → `super.stopPlugin` | 调 `GJSpringPlugin.stop()`，状态 → STOPPED |
| `restartPlugin(id)` | `doStopPlugin` + `doStartPlugin` | 停止 → 启动，同一个 ClassLoader，不读磁盘 |
| `reloadPlugin(id)` | null → load+start；已有 → stop+start | 名字暗示读磁盘，实际已存在插件只是重启 |
| `reloadPlugins(bool)` | 全部 unload → loadPlugins → start | 批量读磁盘 ✓ |
| `unloadPlugin(id)` | PF4J 继承：stop + ClassLoader.close + 移除注册表 | 从内存卸掉，保留磁盘文件 |
| `deletePlugin(id)` | PF4J 继承：unload + 删磁盘文件 | 彻底删除 |

---

## 三、API 重新设计

### 原则

1. 每个方法语义唯一，命名即文档
2. `reloadPlugin` 语义模糊（对已有插件只是 restart），拆掉
3. `startPlugin` / `stopPlugin` 不暴露——用户不关心内部启动/停止，只关心启/禁
4. 利用 PF4J 已有的 `PluginState.DISABLED` 实现禁用

### 最终 API（GJPluginService 暴露 5 个方法）

| 方法 | 状态变化 | 磁盘 | 说明 |
|------|---------|:---:|------|
| `installPlugin(id)` 🆕 | → STARTED | 不动 | 从 `plugins/{id}/*.jar` 加载并启动 |
| `disablePlugin(id)` 🆕 | STARTED → DISABLED | 不动 | 调 `GJSpringPlugin.stop()` + 设 DISABLED |
| `restartPlugin(id)` | 任意 → STARTED | 不动 | 如果是 DISABLED → 先 enable；如果是 STARTED → 先 stop。统一到 start |
| `unloadPlugin(id)` | → 注册表消失 | 保留 | 停 + 关 ClassLoader + 从注册表移除 |
| `deletePlugin(id)` | → 全部消失 | 删除 | unload + 删磁盘目录 |

### 删除的方法

`reloadPlugin` 不保留、不调整，**直接删除**。原因：对已有插件它只是 stop + start（同一个 ClassLoader），从不读磁盘；它做的事情 = `unloadPlugin` + `installPlugin` 两步组合，独立方法无存在必要。

| 删除的方法 | 替代 |
|------|------|
| `reloadPlugin(id)` | `unloadPlugin(id)` + `installPlugin(id)` |
| `startPlugin(id)` | 不暴露，`restartPlugin` 和 `installPlugin` 内部调用 |
| `stopPlugin(id)` | `disablePlugin(id)` |

---

## 四、热加载流程

### 场景 1：已有插件更新 JAR（热加载）

热加载不是一个单独 API 方法，而是 `unloadPlugin` + `installPlugin` 两步操作。`reloadPlugin` 已删除。

```
1. unloadPlugin(id)        // 从内存卸掉
2. 替换 plugins/{id}/*.jar // 宿主/应用商店/文件监听 操作
3. installPlugin(id)       // 重新加载新 JAR + 启动
```

### 场景 2：新增插件

```
1. 放 JAR 到 plugins/{id}/
2. installPlugin(id)       // 加载 + 启动
```

### 场景 3：热加载事件通知

热加载分卸载前和安装后两个阶段，通过 Spring 事件通知宿主和插件自身：

| 事件 | 时机 | veto |
|------|------|:---:|
| `GJPluginBeforeUnloadEvent` | unloadPlugin 之前 | 是（抛 `PluginHotReloadVetoException`） |
| `GJPluginAfterInstallEvent` | installPlugin 完成 | 否 |

宿主和插件自身均可通过 `@EventListener` 订阅。事件从插件上下文发布，Spring 父子上下文自动传播到主应用。

```java
// 插件自身 — 订阅卸载事件，关闭自定义端口
@Component
public class CleanupListener {
    @EventListener
    public void onBeforeUnload(GJPluginBeforeUnloadEvent e) {
        customNettyServer.shutdown();
    }
}

// 宿主 — 订阅安装完成事件，刷新缓存
@Component
public class HotReloadMonitor {
    @EventListener
    public void onAfterInstall(GJPluginAfterInstallEvent e) {
        cacheManager.invalidateByPlugin(e.getPluginId());
    }
}
```

---

## 五、`GJPluginHotReloadManager` 总管 Bean

`@Component` 单例，manual 和 watch 共用。

### 内部结构

```
GJPluginHotReloadManager (@Component, 宿主 ApplicationContext 中)
│
├── GJPluginService                  ← 注入，执行 API 操作（事件在 Manager 内部发布）
├── Map<pluginId, WatchKey>          ← per-plugin 监听键，unload/delete 时 cancel
├── Map<pluginId, ScheduledFuture>   ← per-plugin 防抖计时器
│
└── WatchService (watch 模式)        ← 单 daemon 线程，阻塞 take()
    │
    ├── plugins/ 的 WatchKey
    │   ├── ENTRY_CREATE 子目录 → 注册 per-plugin WatchKey + 启动防抖
    │   └── ENTRY_DELETE 子目录 → 启动防抖（到期后检查目录不存在 → unload + cancel WatchKey）
    │
    └── plugins/{id}/ 的 WatchKey
        ├── ENTRY_CREATE JAR  → 此 id 在注册表中？ → 已有插件：防抖 → unload → install
        │                                              新插件：防抖 → install
        ├── ENTRY_MODIFY JAR  → 已有插件：防抖 → unload → install
        └── ENTRY_DELETE JAR  → 防抖（到期后检查无 JAR → unload + cancel WatchKey）
```

### 已有插件热加载完整流程 (watch 模式)

```
JAR 变更事件
  → 防抖计时器 2s
  → 计时器到期
  → unloadPlugin(id)               // 内部发布 BeforeUnloadEvent + 获取锁 + veto 检查
  → installPlugin(id)              // 内部发布 AfterInstallEvent + 获取锁
```

### 新增插件完整流程 (watch 模式)

```
plugins/ ENTRY_CREATE "新目录X"
  → 注册 plugins/X/ 的 WatchKey
  → 启动防抖 2s
  → plugins/X/ ENTRY_CREATE JAR（重置防抖）
  → 防抖到期
  → installPlugin(id)              // 不触发 BeforeUnloadEvent（新插件没有旧实例）
  → 内部发布 AfterInstallEvent
```

### 防抖

每个 pluginId 维护一个 `ScheduledFuture<?>`，新事件到达时 cancel 旧的、创建新的 2s 计时器。覆盖两个场景：
- **文件复制/下载中**：OS 产生多次 ENTRY_MODIFY，防抖合并为一次
- **回调内产生的文件操作**：backup 插件在 `beforeUnload` 中 cp JAR 产生事件 → 防抖过滤

### 有效性校验

防抖到期后，执行前校验目标路径：
- `plugins/{id}/` 目录存在且包含 `{id}-*.jar` → 执行操作
- 目录不存在 → unload + cancel WatchKey（插件被删了）
- 无合法 JAR → 跳过（可能是临时文件/备份文件）

### WatchKey 生命周期

| 事件 | 操作 |
|------|------|
| 初始扫描 | 遍历 `plugins/` 下已有子目录，逐个 `register()` 并存入 Map |
| 新子目录出现 | `register()` 追加存入 Map |
| 插件 unload/delete | `cancel()` 并移除 WatchKey，防止 fd 泄漏 |
| Manager destroy | 遍历 Map 全部 `cancel()` + WatchService.close() |

### 性能

| 维度 | 影响 |
|------|------|
| 线程 | 1 个 daemon 线程，`watchService.take()` 阻塞等待，空闲时零 CPU |
| 内存 | 每插件 1 个 WatchKey + 1 个 ScheduledFuture ≈ 200B；100 插件 ≈ 20KB |
| 文件描述符 | 每 WatchKey 消耗 1 个 inotify fd（Linux）/ 1 个 HANDLE（Windows）。100 插件 = 100 fd，远低于 `/proc/sys/fs/inotify/max_user_watches`（默认 8192） |

### 配置

```
gj.plugin.hot-reload=manual | watch
```

| 值 | 默认 | 行为 |
|----|:---:|------|
| `watch` | ✓ | Manager 启动 WatchService 守护线程，自动检测变化并执行。默认模式 |
| `manual` | | Manager 存在但不启动 WatchService。宿主调 API，中间逻辑在 `unload` 和 `install` 之间自行处理 |

#### 设计场景

两种模式本质上是**控制反转**的选择——谁掌握重载时机和编排逻辑。

**场景 1：`watch` 模式（文件系统驱动）**

适合开发和简单部署：放文件即触发，零代码介入。

```
1. 宿主/开发者将 JAR 放入 plugins/{id}/
2. WatchService 检测变更 → 2s 防抖合并 → unload + install
3. 完成
```

优点：最简单。适用：单节点、开发环境、演示 Demo。

**场景 2：`manual` 模式（API 驱动）**

适合生产集群和应用商店：调用方精确控制每一步，可编排校验、备份、回滚等周边逻辑。

```
1. 应用商店下载 JAR 到暂存区
2. 校验 SHA256 / 签名
3. 备份 plugins/{id}/*.jar 到备份目录
4. 调用 unloadPlugin(id)           ← 主动控制卸载时机
5. 替换 plugins/{id}/*.jar
6. 调用 installPlugin(id)          ← 主动控制安装时机
7. 检查新插件是否启动成功
8. 失败 → 回滚：恢复旧 JAR + 重新 installPlugin(id)
```

应用商店/编排器在步骤 4-6 之间可以插入业务逻辑（数据库迁移、配置热更新、通知各节点、灰度检查等），这是 `watch` 模式无法覆盖的编排需求。

**场景 3：`manual` 模式（多节点灰度发布）**

```
1. 在所有节点上放置新 JAR（不触发重载）
2. 依次对每个节点调 API：unload(id) + install(id)
3. 节点 1 重载成功 → 观察指标 → 节点 2 → ...
4. 任何节点失败 → 停止推广，已重载节点回滚
```

`watch` 模式下各节点重载时机不可控，无法实现分批灰度。

**场景 4：`manual` 模式（集成测试）**

测试代码需要确定性：

```java
pluginService.unloadPlugin("myPlugin");
// 执行断言：插件已卸载
pluginService.installPlugin("myPlugin");
// 执行断言：新版本插件已启动
```

`watch` 模式的 2s 防抖使测试不确定，`manual` 模式 API 返回即状态确定。

---

## 六、禁用插件（`disablePlugin`）细节

PF4J 的 `PluginState.DISABLED` 已被 `doStartPlugins()` 识别并跳过（line 109）。`disablePlugin` 采用绕过 `super.stopPlugin()` 的实现路径（无中间 STOPPED 态）：

```text
disablePlugin(id):
  ├── GJSpringPlugin.stop()                            // context.close() + 反注册资源
  │     └── 自动发布 GJPluginStoppedEvent              // 通知资源已注销
  ├── pluginWrapper.setPluginState(PluginState.DISABLED)
  └── 发布 GJPluginDisabledEvent                       // 通知宿主禁用完毕
```

事件发布：`GJPluginStoppedEvent`（由 `GJSpringPlugin.stop()` 自动发出）→ `GJPluginDisabledEvent`（Manager 手动发布）。宿主通过 `DisabledEvent` 感知禁用完成。

`restartPlugin(id)` 处理 DISABLED：

```text
restartPlugin(id):
  如果 state == STARTED → doStopPlugin(id)             // 永远发布 StoppedEvent
  如果 state == DISABLED → 跳过 stop（未启动状态无需 stop）
  → doStartPlugin(id)                                   // 永远发布 StartingEvent + StartedEvent
  → 如果此前曾 STARTED → 发布 RestartedEvent
```

---

## 七、新增与变更范围

### 包结构

新增 `gj.pf4j.hotreload` 包，与现有 `gj.pf4j.eventbus`、`gj.pf4j.socketio`、`gj.pf4j.migration` 等独立分包一致。

```
gj.pf4j.hotreload
├── GJPluginHotReloadManager           ← @Component 总管 Bean
└── PluginHotReloadVetoException       ← veto 异常
```

`GJPluginProperties` 放 `gj.pf4j` 根下，和 `GJPluginConfig` 同级。

### 新增

| 类 | 包 | 职责 |
|----|-----|------|
| `GJPluginHotReloadManager` | `gj.pf4j.hotreload` | @Component 总管 Bean：WatchService 监听、防抖、发事件、调 Service |
| `GJPluginBeforeUnloadEvent` | `gj.pf4j.events` | 卸载前事件，支持 veto（抛 `PluginHotReloadVetoException`） |
| `GJPluginAfterInstallEvent` | `gj.pf4j.events` | 安装完成事件，纯通知 |
| `GJPluginDisabledEvent` | `gj.pf4j.events` | 禁用完成事件，通知宿主状态变更 |
| `PluginHotReloadVetoException` | `gj.pf4j.hotreload` | Runtime 异常，`BeforeUnloadEvent` 监听器抛此阻止卸载 |
| `GJPluginProperties` | `gj.pf4j` | `@ConfigurationProperties("gj.plugin")`，框架级配置，与 `GJPluginConfig` 同级 |

### 变更

| 类 | 变更 |
|----|------|
| `GJPluginManager.doStopPlugin()` | Bug 1 修复，`StartedEvent` → `StoppedEvent`；移除 `sendEvent` 参数，永远发布事件；依赖项停用改为获取锁后调 `doStopPlugin` |
| `GJPluginManager.doStartPlugin()` | 移除 `sendEvent` 参数，永远发布 `GJPluginStartedEvent` |
| `GJPluginManager` | 新增 `installPlugin(String)`、`disablePlugin(String)`；重写 `restartPlugin()` 支持 DISABLED；新增 `pluginLocks`（从 Service 移入），暴露 `getPluginLock(id)`；新增 `everStartedPluginIds`，记录历史上线插件 |
| `GJPluginService` | 暴露 `installPlugin`、`disablePlugin`、`restartPlugin`、`unloadPlugin`、`deletePlugin`；Bug 2 修复；移除 `startPlugin`/`stopPlugin`/`reloadPlugin` 公开方法；不再维护 `pluginLocks`，改为调 `pluginManager.getPluginLock(id)` |
| `GJPluginConfig` | 注入 `GJPluginProperties`，创建 `GJPluginHotReloadManager` Bean |
| `GJMainApplicationStartedListener` | watch 模式启动 Manager 的文件监听线程 |

---

## 八、实施步骤

按优先级分组，每一步写清具体细节和注意事项。

---

### P0：Bug 修复（2 项）

#### 步骤 1：Bug 1 — `doStopPlugin` 事件类型修复

**位置**：`GJPluginManager.java:256`

**改动**：`GJPluginStartedEvent` → `GJPluginStoppedEvent`

**注意**：`doStopPlugin` 移除 `sendEvent` 参数后将永远发布此事件（见步骤 4），修改处只有这一行。

---

#### 步骤 2：Bug 2 — `pluginLocks` 归属迁移

**背景**：原 `GJPluginService.stopPlugin` 在 stop 后立即 `pluginLocks.remove(pluginId)`，而插件仍处于 STOPPED 状态。由于 `stopPlugin` 公开方法将被移除（见步骤 8），Bug 2 的具体代码行随之消失。但锁生命周期规则必须在新 API 中贯彻：**锁随注册表生命周期，只在 unloadPlugin / deletePlugin 成功时删除**。

**实施**：本步骤不直接改 Bug 2 所在行，而是在后续步骤中确保——
- `GJPluginManager` 统一维护 `pluginLocks`（步骤 3）
- `disablePlugin` 不删锁（插件仍在注册表，步骤 6）
- `unloadPlugin` / `deletePlugin` 成功时删锁（步骤 7/8）

---

### P1：核心 API 实现（8 项）

#### 步骤 3：`GJPluginManager` 基础设施

**新增字段**：
```java
// GJPluginManager 新增
final Map<String, ReentrantLock> pluginLocks = new ConcurrentHashMap<>();
final Set<String> everStartedPluginIds = ConcurrentHashMap.newKeySet();

ReentrantLock getPluginLock(String pluginId) {
    return pluginLocks.computeIfAbsent(pluginId, k -> new ReentrantLock());
}

void removePluginLock(String pluginId) {
    pluginLocks.remove(pluginId);
}
```

**注意**：
- `pluginLocks` 从 `GJPluginService` 移入，`GJPluginService` 不再维护自己的锁 Map（见步骤 9）
- `everStartedPluginIds` 用于 `GJPluginRestartedEvent` 判断条件修正（见步骤 11）
- 两个集合的生命周期与 `GJPluginManager` 一致，随 JVM 销毁释放

---

#### 步骤 4：新建事件类（4 个，供后续步骤使用）

事件类是纯 POJO，无内部依赖，提前创建。

**新建文件**：

| 类 | 包 | 说明 |
|------|-----|------|
| `GJPluginBeforeUnloadEvent` | `gj.pf4j.events` | 含 `pluginId`、`GJSpringPlugin`；`doUnloadPlugin` 发布 |
| `GJPluginAfterInstallEvent` | `gj.pf4j.events` | 含 `pluginId`、`GJSpringPlugin`；`installPlugin` 发布 |
| `GJPluginDisabledEvent` | `gj.pf4j.events` | 含 `pluginId`；`disablePlugin` 发布 |
| `PluginHotReloadVetoException` | `gj.pf4j.hotreload` | 继承 `RuntimeException`；监听器抛此阻止卸载 |

**注意**：
- 3 个事件类与现有 `GJPluginStartedEvent` 保持一致的构造器签名
- `BeforeUnloadEvent` veto 机制：监听器抛 `PluginHotReloadVetoException` → `doUnloadPlugin` 捕获 → 中止
- Spring 默认 `SimpleApplicationEventMulticaster` 未配置 `ErrorHandler` 时异常天然穿透，无需额外配置
- 本步骤同时删除原设计中的 `GJPluginHotReloadCallback` 接口（已废弃）

---

#### 步骤 5：移除 `sendEvent` 参数

**位置**：`GJPluginManager.doStartPlugin()` 和 `doStopPlugin()`

**改动**：
- `doStartPlugin(String pluginId, boolean sendEvent)` → `doStartPlugin(String pluginId)`
- `doStopPlugin(String pluginId, boolean sendEvent)` → `doStopPlugin(String pluginId)`
- 移除内部的 `if (sendEvent && ...)` 条件分支，永远发布事件

**所有调用方同步更新**：

| 原调用 | 改为 |
|--------|------|
| `doStartPlugin(id, true)` | `doStartPlugin(id)` |
| `doStartPlugin(id, false)` | `doStartPlugin(id)` |
| `doStopPlugin(id, true)` | `doStopPlugin(id)` |
| `doStopPlugin(id, false)` | `doStopPlugin(id)` |

**注意**：`restartPlugin` 将收到完整事件序列 `StoppedEvent → StartingEvent → StartedEvent → RestartedEvent`，此前 `StartedEvent` 被 `sendEvent=false` 抑制的问题消除。

---

#### 步骤 6：`doStopPlugin` 依赖项停用加锁

**改动**：
```java
// 修改前
super.stopPlugin(dependent);

// 修改后
ReentrantLock lock = getPluginLock(dependent);
lock.lock();
try {
    doStopPlugin(dependent);
} finally {
    lock.unlock();
}
```

**注意**：
- `doStopPlugin(dependent)` 会递归处理二级依赖，锁已在此获取，内部递归不再重复获取（ReentrantLock 可重入）
- 依赖项的 `StoppedEvent` 正常发布（因步骤 5 永远发布事件）
- 锁是按 `dependent` 获取的，不是按主插件 ID 获取，避免死锁

---

#### 步骤 7：`disablePlugin` 实现

**实现路径**（绕过 `super.stopPlugin`，无中间 STOPPED 态）：
```text
GJPluginManager.disablePlugin(pluginId):
  ├── 获取 pluginLock
  ├── GJSpringPlugin.stop()
  │     ├── lifecycle.unregisterPluginResources()
  │     ├── 自动发布 GJPluginStoppedEvent（上下文级，GJSpringPlugin.stop 内部）
  │     └── context.close()
  ├── pluginWrapper.setPluginState(PluginState.DISABLED)
  ├── 发布 GJPluginDisabledEvent（事件类已在步骤 4 创建）
  └── 释放 pluginLock
```

**事件说明**：
- 上下文级 `GJPluginStoppedEvent`：`GJSpringPlugin.stop()` 自动发布，告知插件 Bean 上下文即将关闭
- 管理器级 `GJPluginStoppedEvent`：**不发布**。disable 不是常规 stop，状态目标为 DISABLED
- `GJPluginDisabledEvent`：替代上述管理器级事件，宿主通过 `@EventListener(GJPluginDisabledEvent)` 感知禁用完成

**注意**：不删 `pluginLocks`——插件仍在 PF4J 注册表，后续可能 `restartPlugin`

---

#### 步骤 8：`installPlugin` + `doUnloadPlugin` 实现

**`installPlugin` 实现路径**：
```text
GJPluginManager.installPlugin(pluginId):
  ├── 获取 pluginLock
  ├── 扫描 plugins/{pluginId}/ 目录，找 {pluginId}-*.jar
  ├── loadPlugin(jarPath)
  ├── doStartPlugin(pluginId)
  │     ├── everStartedPluginIds.add(pluginId)
  │     ├── 发布 GJPluginStartedEvent
  │     └── 返回 PluginState
  ├── 发布 GJPluginAfterInstallEvent（事件类已在步骤 4 创建）
  └── 释放 pluginLock
```

**注意**：若插件目录不存在或无合法 JAR，抛异常，不创建锁条目

**`doUnloadPlugin` 实现路径**：
```text
GJPluginManager.doUnloadPlugin(pluginId):
  ├── 获取旧的 GJSpringPlugin.getApplicationContext()
  ├── 发布 GJPluginBeforeUnloadEvent（事件类已在步骤 4 创建）← 可 veto
  │     └── 捕获 PluginHotReloadVetoException → 中止，记日志，返回 false
  ├── 调 super.unloadPlugin(pluginId)
  └── 返回 true
```

**注意**：
- 事件在 `super.unloadPlugin()` 之前发布，旧上下文完全活跃
- `BeforeUnloadEvent` 统一在 `GJPluginManager` 内发布，manual 和 watch 两条路径都触发
- `doUnloadPlugin` 返回 boolean，`GJPluginService.unloadPlugin` 据此决定是否删锁

---

#### 步骤 9：`GJPluginService` API 重构

**公开方法**（5 个）：

| 方法 | 内部调用 |
|------|---------|
| `installPlugin(id)` | `pluginManager.getPluginLock(id)` → `pluginManager.installPlugin(id)` |
| `disablePlugin(id)` | `pluginManager.getPluginLock(id)` → `pluginManager.disablePlugin(id)` |
| `restartPlugin(id)` | `pluginManager.getPluginLock(id)` → `pluginManager.restartPlugin(id)` |
| `unloadPlugin(id)` | `pluginManager.getPluginLock(id)` → `pluginManager.doUnloadPlugin(id)` → 成功则 `pluginManager.removePluginLock(id)` |
| `deletePlugin(id)` | `pluginManager.getPluginLock(id)` → `pluginManager.deletePlugin(id)` → 成功则 `pluginManager.removePluginLock(id)` |

**删除的公开方法**：`startPlugin(id)`、`stopPlugin(id)`、`reloadPlugin(id)`

**内部变更**：
- 删除 `GJPluginService` 自己的 `pluginLocks`（`ConcurrentHashMap`）
- 锁获取改为 `pluginManager.getPluginLock(id)`
- 锁删除改为 `pluginManager.removePluginLock(id)`
- `reloadAll()` 和 `loadAndStartAllPlugins()` 保留，内部不变

**注意**：`unloadPlugin` 和 `deletePlugin` 是唯二删锁的方法

---

#### 步骤 10：`restartPlugin` 支持 DISABLED → STARTED

**实现路径**：
```text
GJPluginManager.restartPlugin(pluginId):
  ├── 获取 pluginLock
  ├── PluginState state = getPlugin(pluginId).getPluginState()
  ├── 如果 state == STARTED:
  │     └── doStopPlugin(pluginId)
  ├── 如果 state == DISABLED:
  │     └── 跳过 stop（DISABLED 状态下上下文已在步骤 7 关闭）
  ├── doStartPlugin(pluginId)
  │     └── 内部设 setPluginState(STARTED)
  └── 释放 pluginLock
```

**注意**：不设 RESOLVED 中间态——PF4J `startPlugin` 不检查 RESOLVED 前置条件

---

### P2：热加载基础设施 + 可观测性（6 项）

#### 步骤 11：`GJPluginRestartedEvent` 条件修正

**改动**：`GJSpringPlugin.start()` 和 `GJPluginFactory.create()`

```java
// 修改前（GJPluginFactory.create）
.mainApplicationStarted(pluginManager.isMainApplicationStarted())

// 修改后
.everStarted(pluginManager.getEverStartedPluginIds().contains(wrapper.getPluginId()))
```

`GJPluginContext`：
- 弃用 `mainApplicationStarted` 字段
- 新增 `everStarted` 字段（boolean，构造时快照）

**注意**：`doStartPlugin` 成功后记录 `everStartedPluginIds.add(pluginId)`（已在步骤 8 体现）

---

#### 步骤 12：`GJPluginProperties` 配置类

**新建**：`gj.pf4j.GJPluginProperties`
```java
@ConfigurationProperties("gj.plugin")
public class GJPluginProperties {
    private HotReload hotReload = HotReload.MANUAL;
    
    public enum HotReload { MANUAL, WATCH }
}
```

**注意**：与 `GJPluginConfig` 同级，由 `@ComponentScan("gj")` 自动扫描

---

#### 步骤 13：`GJPluginHotReloadManager` 实现

**职责**：WatchService + 防抖 + 调 Service（不发布事件，不扫描回调）

**内部结构**：
```
GJPluginHotReloadManager (@Component, 宿主 ApplicationContext 中)
│
├── GJPluginService                     ← 注入，执行 API 操作
├── Map<pluginId, WatchKey>             ← per-plugin 监听键
├── Map<pluginId, ScheduledFuture>      ← per-plugin 防抖计时器
│
└── WatchService (watch 模式)           ← 单 daemon 线程，阻塞 take()
```

**热加载流程**（watch 模式，已有插件）：
```
JAR 变更事件
  → 防抖 2s
  → 防抖到期，校验 plugins/{id}/*.jar 存在
  → pluginService.unloadPlugin(id)
  → pluginService.installPlugin(id)
```

事件由 `doUnloadPlugin` / `installPlugin` 内部发布，Manager 无需感知。

**注意**：防抖、WatchKey 生命周期、有效性校验、性能指标保持原设计不变

---

#### 步骤 14：`GJPluginConfig` 更新

**改动**：
```java
@Bean
@ConditionalOnProperty(prefix = "gj.plugin", name = "hot-reload", havingValue = "watch")
public GJPluginHotReloadManager pluginHotReloadManager(GJPluginService pluginService) {
    return new GJPluginHotReloadManager(pluginService, pluginsDir);
}
```

`GJPluginHotReloadManager` 构造器注入 `GJPluginService` + `pluginsDir` 路径。

**注意**：
- `manual` 模式（默认）不创建 Manager Bean——`@ConditionalOnProperty` 限制只有 `watch` 才创建
- Manager 创建后内部自动注册 WatchService

---

#### 步骤 15：热加载全链路日志

以下位置需记录结构化日志，便于排查热加载问题和审计操作记录。

**1. `doUnloadPlugin`—卸载前事件分发**

```
LOG: [热加载] 插件 {pluginId} 开始卸载前通知
  → 发布 BeforeUnloadEvent → 上下文: {ctxId}，监听器数量: {n}
  → 监听器 {className} 执行成功/失败（逐个记录）
  → veto: 无 / {listenerClassName} 否决，原因: {message}
  → 结果: 通知完成，继续卸载 / 被否决，中止
```

**2. `doUnloadPlugin`—卸载执行**

```
LOG: [热加载] 插件 {pluginId} 卸载完成，ClassLoader 已关闭，注册表已移除
ERROR: [热加载] 插件 {pluginId} 卸载失败: {exception}
```

**3. `installPlugin`—安装执行**

```
LOG: [热加载] 插件 {pluginId} 开始安装，JAR: {jarPath}
  → loadPlugin 成功/失败
  → doStartPlugin 成功/失败
  → 发布 AfterInstallEvent → 上下文: {ctxId}，监听器数量: {n}
  → 监听器 {className} 执行成功/失败（逐个记录）
LOG: [热加载] 插件 {pluginId} 安装完成，状态: STARTED
ERROR: [热加载] 插件 {pluginId} 安装失败: {exception}
```

**4. `disablePlugin`—禁用执行**

```
LOG: [生命周期] 插件 {pluginId} 禁用完成
  → 发布 DisabledEvent → 上下文: {ctxId}，监听器数量: {n}
ERROR: [生命周期] 插件 {pluginId} 禁用失败: {exception}
```

**5. WatchService 文件变更**

```
LOG: [热加载] 检测到文件变更: {pluginId}，事件类型: {ENTRY_CREATE|ENTRY_MODIFY|ENTRY_DELETE}
  → 启动/重置防抖 2s
LOG: [热加载] 防抖到期，开始处理: {pluginId}
  → 校验路径: {path}，有效 JAR: {jarName} / 无合法 JAR，跳过
```

**6. 热加载完整链路追踪**

每次热加载生成唯一 `reloadId`（UUID），贯穿 unload → install 全流程：

```
LOG: [热加载] reloadId={uuid}，插件: {pluginId}，触发方式: {manual|watch}
  → 阶段1: beforeUnload 通知完成
  → 阶段2: unloadPlugin 完成
  → 阶段3: installPlugin 完成
  → 阶段4: afterInstall 通知完成
  → 总耗时: {n}ms，结果: 成功/失败
```

**注意**：
- 日志级别：正常流程用 INFO，异常用 ERROR，veto 用 WARN
- 所有日志带 `[热加载]` 或 `[生命周期]` 前缀，可被日志系统（ELK/Splunk）结构化解析
- 该步骤不引入新的日志框架依赖，使用现有 SLF4J + `log.info/warn/error`
- 实施时可分步追加到对应步骤的代码中，不需要集中在一个 commit

---

#### 步骤 16：`GJMainApplicationStartedListener` 更新

**改动**：
```java
@Override
public void onApplicationEvent(ContextRefreshedEvent event) {
    // ... 现有逻辑：loadPlugins + startPlugins ...
    pluginManager.setMainApplicationStarted(true);

    // 🆕 启动热加载文件监听（仅 watch 模式）
    if (hotReloadManager != null) {
        hotReloadManager.startWatching();
    }
}
```

注入 `GJPluginHotReloadManager`（`@Autowired(required = false)`，manual 模式下 Bean 不存在）。

**注意**：`GJMainApplicationStartedListener` 当前 `@Component` 在主应用上下文，直接注入 Manager 即可。Manager 仅 watch 模式存在。

---

### P3：收尾（2 项）

#### 步骤 17：版本号更新

同原 9.1，各模块版本号按表更新。

---

#### 步骤 18：README 更新

同原 9.2，新增 §19 Plugin Hot-Reload 独立章。

---

### 实施依赖关系

```
步骤 1, 2 ──→ 步骤 3 (基础设施)
                ├──→ 步骤 4 (事件类，无依赖)
                ├──→ 步骤 5 (移除 sendEvent)
                │       ├──→ 步骤 6 (依赖项锁)
                │       ├──→ 步骤 7 (disablePlugin)
                │       ├──→ 步骤 8 (installPlugin + doUnloadPlugin)
                │       └──→ 步骤 10 (restartPlugin)
                │               └──→ 步骤 9 (Service 重构，依赖 4/7/8/10)
                │                       └──→ 步骤 13 (Manager)
                │                               └──→ 步骤 14 (GJPluginConfig)
                │                                       └──→ 步骤 16 (Listener)
                └──→ 步骤 11 (RestartedEvent，依赖 everStartedPluginIds)
步骤 12 (Properties，独立)
步骤 15 (日志，横切关注点，随各步骤实施时追加)
步骤 17, 18 ──→ 最后
```

---

## 九、实施完成后步骤

### 9.1 版本号更新

| 模块 | 当前版本 | 新版本 | 理由 |
|------|---------|--------|------|
| `gj-dependencies` (gj-parent) | 1.0.8 | **1.0.9** | 小版本 +1 |
| `gj-pf4j` | 1.4.0 | **1.5.0** | 新增 API + 热加载，小版本 +1 |
| `gj-archetypes` | 1.0.9 | **1.1.0** | 小版本 +1，更新内部 gj-pf4j 依赖为 1.5.0 |

**需改动的文件**：

| 文件 | 改动 |
|------|------|
| `src/gj-parent/pom.xml` | `<version>` 1.0.8 → 1.0.9 |
| `src/gj-pf4j/pom.xml` | `<version>` 1.4.0 → 1.5.0；`<parent><version>` 1.0.7 → 1.0.9 |
| `src/gj-archetypes/pom.xml` | `<version>` 1.0.9 → 1.1.0 |
| `src/gj-archetypes/src/main/resources/archetype-resources/pom.xml` | gj-pf4j 依赖版本 → 1.5.0 |

### 9.2 README 更新

#### 9.2.1 目录（Table of Contents）变更

两个 README 的目录均需更新：

```
§18  Runtime Plugin Management API      ← 重写（纯 API 参考，移除热加载工作流）
§19  Plugin Hot-Reload                   ← 🆕 独立章（热加载全部内容）
§20  Appendix: Host Application Integration ← 原 §19，顺延
§21  Claude Code Integration             ← 原 §20，顺延
§22  FAQ                                 ← 原 §21，顺延
```

---

#### 9.2.2 §1 Overview → Core Capabilities 要点列表

**改动**：修改第 1 条生命周期描述，新增一条热加载要点：

英文：
```
- **[Plugin Lifecycle Management](#4-plugin-lifecycle)** — load, start, stop, restart, unload, and delete plugins at runtime
```
→
```
- **[Plugin Lifecycle Management](#4-plugin-lifecycle)** — install, disable, restart, unload, and delete plugins at runtime
- **[Plugin Hot-Reload](#19-plugin-hot-reload)** — hot-reload via API-driven workflow or file watcher; lifecycle events for custom orchestration logic
```

中文：
```
- **[插件生命周期管理](#4-插件生命周期)** — 插件加载、启动、停止、重启、卸载、删除
```
→
```
- **[插件生命周期管理](#4-插件生命周期)** — 插件安装、禁用、重启、卸载、删除
- **[插件热加载](#19-插件热加载)** — 支持 API 驱动和文件监听两种热加载模式；生命周期事件自定义编排逻辑
```

---

#### 9.2.3 §18 Runtime Plugin Management API — 重写（纯 API 参考）

现有 8 个子节（17.1-17.8），API 变更后调整为 7 个，**热加载工作流和回调移入新 §19**：

| 新节号 | 标题 | 内容 |
|:---:|------|------|
| 18.1 | Injecting GJPluginService | 不变 |
| 18.2 | Load and Start All Plugins | 不变（批量初始化） |
| 18.3 | Install a Plugin | **新** — `installPlugin(pluginId)`，签名、返回值、并发锁说明 |
| 18.4 | Disable a Plugin | **新** — `disablePlugin(pluginId)`，DISABLED 状态含义、与 STOPPED 区别、后续 restart 可恢复 |
| 18.5 | Restart a Plugin | **改** — 语义从 stop→start 扩展为支持 DISABLED→STARTED |
| 18.6 | Unload / Delete a Plugin | **合并** — 原 17.4+17.6+17.8 合并，`unloadPlugin(id)` 与 `deletePlugin(id)` 的差异（前者保留磁盘、后者删目录） |
| 18.7 | Reload All Plugins | 保留 |

**末节末尾加引用**：

> For hot-reload workflows, lifecycle events, file watcher mode, and app-store integration patterns, see **[§19 Plugin Hot-Reload](#19-plugin-hot-reload)**.

**删除的内容**：
- 旧 17.3 Start a Single Plugin（`startPlugin` → 内部方法，不暴露）
- 旧 17.4 Stop a Single Plugin（`stopPlugin` → `disablePlugin`）
- 旧 17.6 Hot-Unload / Hot-Reload 中的 `reloadPlugin` 示例（`reloadPlugin` 已删除）

---

#### 9.2.4 §19 Plugin Hot-Reload — 🆕 独立章，完整结构

排在 §18 之后、附录之前。英文和中文 README 结构一致。

| 节号 | 标题 | 内容 |
|:---:|------|------|
| 19.1 | Concept & Configuration | 热加载概念；`gj.plugin.hot-reload=manual \| watch` 配置项；两种模式的设计场景对照 |
| 19.2 | manual Mode — API-Driven Workflow | `unloadPlugin(id)` → 替换 JAR → `installPlugin(id)` 两步流程；每步返回状态可校验 |
| 19.3 | manual Mode — App Store Integration | 8 步编排示例：下载→校验 SHA256→备份→unload→替换 JAR→install→检查→失败回滚 |
| 19.4 | manual Mode — Multi-Node Grayscale | 多节点灰度发布流程：所有节点放 JAR→逐节点 unload+install→观察→失败回滚 |
| 19.5 | watch Mode — File Watcher | WatchService 守护线程；防抖 2s；已有插件更新 vs 新增插件的检测差异；有效性校验 |
| 19.6 | Lifecycle Events | `GJPluginBeforeUnloadEvent`（可阻止）、`GJPluginAfterInstallEvent`（安装完成）；宿主和业务插件均可通过 `@EventListener` 订阅 |
| 19.7 | Event Subscription Example | 代码示例：业务插件监听自身卸载事件关闭独立端口；宿主监听安装完成事件刷新缓存 |

**19.6 谁可以订阅事件的简短说明**（README 中直接用自然语言，不展开设计原理）：

> **Who can subscribe:** Both the **host application** and the **plugin being reloaded** can define `@EventListener` methods for `GJPluginBeforeUnloadEvent` and `GJPluginAfterInstallEvent`. The plugin can use them to clean up its own resources (e.g., close custom ports); the host can use them for orchestration (e.g., notify an app store). Other plugins are unaffected — only the target plugin and the host receive these events.

中文对应：

> **谁能订阅：** 宿主应用和被热加载的插件自身都可以通过 `@EventListener` 订阅 `GJPluginBeforeUnloadEvent` 和 `GJPluginAfterInstallEvent`。插件可借此清理自身资源（如关闭独立端口），宿主可借此做编排联动（如通知应用商店）。其他插件不受影响——仅目标插件和宿主收到事件。

**19.7 示例**（README 中只列一个简短场景）：

```java
// 插件自身 — 订阅卸载事件，关闭自定义端口
@Component
public class CleanupListener {
    @EventListener
    public void onBeforeUnload(GJPluginBeforeUnloadEvent e) {
        customNettyServer.shutdown();
    }
}

// 宿主 — 订阅安装完成事件，刷新缓存
@Component
public class HotReloadMonitor {
    @EventListener
    public void onAfterInstall(GJPluginAfterInstallEvent e) {
        cacheManager.invalidateByPlugin(e.getPluginId());
    }
}
```

**19.1 中两种模式说明**（核心段落，直接体现设计意图）：

> `manual`（默认）— manager bean exists but WatchService is not started. The caller controls timing via `GJPluginService` API, inserting custom logic between `unload` and `install`. For production clusters, app stores, and CI/CD pipelines.
>
> `watch` — manager bean starts a WatchService daemon thread. JAR changes in `plugins/` are automatically detected (with 2s debounce). For development, single-node deployments, and demos.

---

#### 9.2.5 不动的部分

- §4 Plugin Lifecycle（ASCII 流程图不涉及 API 名称，无需改）
- §5-17 各能力章（REST、数据访问、实时通信等不涉及热加载 API）
- §20 Appendix（原 §19，集成方式不变）
- §22 FAQ（原 §21，可新增一条热加载相关 FAQ）


---

