# gj.spring.pf4j

[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/wangpengxpy/gj.spring.pf4j/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.wangpengxpy/gj-pf4j?color=green)](https://central.sonatype.com/artifact/io.github.wangpengxpy/gj-pf4j)
[![Stars](https://img.shields.io/github/stars/wangpengxpy/gj.spring.pf4j?style=social)](https://github.com/wangpengxpy/gj.spring.pf4j/stargazers)

基于 PF4J 与 Spring 的轻量级模块化插件框架，无 Spring Boot 重依赖。支持 Spring MVC 与 Spring WebFlux 双路由模式，自动适配主应用架构。

> [English](README.md)

---

## 目录

1. [项目概述](#1-项目概述)
2. [快速开始](#2-快速开始)
3. [插件项目结构详解](#3-插件项目结构详解)
4. [插件生命周期](#4-插件生命周期)
5. [REST 端点](#5-rest-端点)
6. [数据访问](#6-数据访问)
7. [数据库自动迁移](#7-数据库自动迁移)
8. [对象映射](#8-对象映射)
9. [插件配置管理](#9-插件配置管理)
10. [实时通信](#10-实时通信)
11. [国际化 i18n](#11-国际化-i18n)
12. [导入导出](#12-导入导出)
13. [定时任务](#13-定时任务)
14. [进程内事件总线](#14-进程内事件总线)
15. [OpenAPI 文档](#15-openapi-文档)
16. [插件打包与部署](#16-插件打包与部署)
17. [插件运行时管理 API](#17-插件运行时管理-api)
18. [附录：主应用集成](#18-附录主应用集成)
    * [版本兼容性说明](#181-版本兼容性说明)
    * [主应用入口配置](#182-主应用入口配置)
    * [Claude Code 集成](#183-claude-code-集成)
    * [按需配置：@GJModelMapperScan（共享模型）](#184-按需配置gjmodelmapperscan共享模型)

---

## 1. 项目概述

gj.spring.pf4j 是基于 [PF4J](https://pf4j.org/) 的轻量级 Spring 插件化框架。它仅依赖 Spring 核心包（spring-context、spring-webmvc、spring-jdbc 等），不引入 Spring Boot 作为运行时依赖，适合需要模块化架构但不想捆绑 Spring Boot 完整技术栈的项目。

### 核心能力

- **[插件生命周期管理](#4-插件生命周期)** — 插件加载、启动、停止、重启、卸载、删除
- **[插件运行时管理 API](#17-插件运行时管理-api)** — GJPluginService 提供带锁控制的运行时管理接口
- **[REST 端点](#5-rest-端点)** — 插件内 @RestController 自动发现并注册到主应用路由表，支持 MVC 和 WebFlux 双路由模式
- **[双路由模式支持](#52-spring-mvc-与-webflux-双路由模式)** — 同时支持 Spring MVC（Servlet）和 Spring WebFlux（Reactive）路由
- **[OpenAPI 文档](#15-openapi-文档)** — 基于 SpringDoc，每个插件自动生成独立 GroupedOpenApi
- **[数据访问](#6-数据访问)** — 基于 [MyBatis-Plus](https://baomidou.com/)，每个插件独立 SqlSessionFactory / SqlSessionTemplate / TransactionManager，共享主应用 DataSource
- **[数据库自动迁移](#7-数据库自动迁移)** — @TableName 实体 Schema 自动迁移（仅建表/加字段），支持 7 种数据库，可安全用于生产环境
- **[对象映射](#8-对象映射)** — 基于 [ModelMapper](https://modelmapper.org/)，插件实现 `GJPluginModelMapperConfig`，Spring Bean 扫描自动发现并注册类型映射
- **[导入导出](#12-导入导出)** — 基于 [EasyExcel](https://easyexcel.opensource.alibaba.com/)，多 Sheet 读写，i18n 表头自动翻译
- **[实时通信](#10-实时通信)** — 基于 [netty-socketio](https://github.com/mrniko/netty-socketio)，内置 Hub 模式（SignalR 风格），支持分组、用户定向推送
- **[定时任务](#13-定时任务)** — 基于 [Quartz](https://www.quartz-scheduler.org/)，支持 cron / 固定间隔 / 一次性执行
- **[进程内事件总线](#14-进程内事件总线)** — 轻量级进程内事件总线，支持同步/异步发布、Ant 风格通配符匹配
- **[国际化 i18n](#11-国际化-i18n)** — 插件独立 messages.properties，兜底继承主应用翻译

---

## 2. 快速开始

### 2.1 安装 Archetype 到本地仓库

```bash
cd src/gj-archetypes
mvn clean install
```

### 2.2 生成插件项目

```bash
mvn archetype:generate \
  -DarchetypeGroupId=io.github.wangpengxpy \
  -DarchetypeArtifactId=gj-archetype \
  -DarchetypeVersion=1.0.2 \
  -DgroupId=com.example \
  -DpluginName=user \
  -DpackagePrefix=gj.module
```

**参数说明：**

| 参数 | 含义 | 示例值 |
|---|---|---|
| `groupId` | 插件项目的 Maven groupId | `com.example` |
| `pluginName` | 插件名称（会生成同名类、包） | `user` |
| `packagePrefix` | 插件包前缀 | `gj.module` |

生成后，`plugin.id` 自动拼接为 `gj.module.user`，所有 Java 类位于包 `gj.module.user` 下。

### 2.3 生成后的项目结构

```
user-plugin/
├── pom.xml                          # 插件 POM，依赖 gj-pf4j
├── pom-parent.xml                   # 构建父 POM（Maven 打包规则）
└── src/
    └── main/
        ├── java/
        │   └── gj/module/user/
        │       ├── UserPlugin.java                      # 插件入口
        │       ├── UserConfig.java                      # 插件配置
        │       ├── controllers/
        │       │   └── UserController.java              # REST 控制器
        │       ├── dao/
        │       │   └── UserMapper.java                  # MyBatis Mapper
        │       ├── dto/
        │       │   └── EgroupDTO.java                   # 数据传输对象
        │       ├── model/
        │       │   └── Test.java                        # 数据库实体
        │       ├── modelmapper/
        │       │   └── UserModelMapperConfig.java       # ModelMapper 映射配置
        │       ├── request/
        │       │   └── UserEventRequest.java            # 请求 DTO
        │       ├── response/
        │       │   ├── UserResponse.java                # 列表响应
        │       │   └── UserEventResponse.java           # 事件响应
        │       └── service/
        │           ├── UserService.java                 # 服务接口
        │           └── impl/
        │               └── UserServiceImpl.java         # 服务实现
        └── resources/
            ├── plugin.properties                # PF4J 插件描述
            ├── gj.module.user.json              # 插件 JSON 描述文件
            └── gj.module.user.properties        # 插件业务配置
```

---

## 3. 插件项目结构详解

### 3.1 标准目录约定

| 目录 / 文件 | 用途 | 说明 |
|---|---|---|
| `{plugin}.java` | 插件入口类 | 继承 `GJPlugin`，生命周期钩子 |
| `{plugin}Config.java` | 插件配置类 | `@ConfigurationProperties` 绑定 |
| `controllers/` | REST 控制器 | `@RestController`，自动注册路由 |
| `dao/` | 数据访问层 | MyBatis Mapper 接口，继承 `BaseMapper<T>` |
| `model/` | 数据库实体 | MyBatis-Plus `@TableName` 实体 |
| `dto/` | 数据传输对象 | 非持久化 DTO |
| `request/` | 请求对象 | 接口入参 DTO |
| `response/` | 响应对象 | 接口返回 DTO |
| `service/` | 服务接口 | 业务逻辑接口 |
| `serviceimpl/` | 服务实现 | `@Service`，`@Transactional` |
| `modelmapper/` | 映射配置 | 实现 `GJPluginModelMapperConfig` |
| `plugin.properties` | PF4J 描述文件 | plugin.id、plugin.class、plugin.version |
| `{pluginId}.properties` | 插件业务配置 | 业务参数，绑定到 Config 类 |

### 3.2 plugin.properties

```properties
plugin.id=gj.module.user
plugin.class=gj.module.user.UserPlugin
plugin.version=1.0.0-SNAPSHOT
plugin.description=
plugin.provider=
plugin.dependencies=
```

> **约束：** `plugin.id` 必须与插件主包名完全一致。

### 3.3 {pluginId}.properties（插件业务配置）

```properties
gj.module.user.enabled=true
gj.module.user.value=iot
```

### 3.4 pom-parent.xml 构建规则

`pom-parent.xml` 是独立存在的父 POM，内置了一条完整的**自动打包流水线**。执行 `mvn clean package` 时按以下顺序运行：

#### 第一步：依赖分类 — 自动区分公共包与私有包

`maven-dependency-plugin` 在 `prepare-package` 阶段扫描所有 runtime 依赖，根据 `excludeGroupIds` 列表自动分流：

| 依赖类型 | 判定规则 | 处理方式 |
|---|---|---|
| **公共依赖（主应用已提供）** | groupId 命中排除列表 | **跳过**，不拷贝，不打包 |
| **插件私有依赖** | groupId 不在排除列表中 | 拷贝到 `target/lib/`，最终打进 `lib/` |

排除列表涵盖了主应用已集成的所有框架：Spring 全家桶、MyBatis-Plus、PF4J、Jackson、Netty、SocketIO、ModelMapper、EasyExcel、Lombok、SLF4J、Hibernate Validator、Jakarta 系列等 60+ 个 groupId。

```xml
<!-- pom-parent.xml 中的排除配置（部分摘录） -->
<excludeGroupIds>
    org.springframework, org.springframework.boot,  <!-- Spring 框架 -->
    org.mybatis, com.baomidou,                      <!-- MyBatis-Plus -->
    org.pf4j,                                        <!-- PF4J 插件框架 -->
    com.fasterxml.jackson.core,                      <!-- Jackson -->
    io.netty, com.corundumstudio.socketio,           <!-- Netty + Socket.IO -->
    org.modelmapper,                                  <!-- ModelMapper -->
    org.projectlombok,                                <!-- Lombok -->
    org.slf4j, ch.qos.logback,                       <!-- 日志 -->
    jakarta.servlet, jakarta.annotation,              <!-- Jakarta -->
    com.alibaba,                                      <!-- EasyExcel -->
    ...
</excludeGroupIds>
```

> **这意味着：** 你只需在 `pom.xml` 中声明依赖，构建插件会自动判断 — 属于公共包的自动排除，只有插件独有的第三方库才会进入 `lib/`。

#### 第二步：私有 lib 整合

如果插件有**非 Maven 中央仓库的私有 JAR**（如内部 SDK、定制版库），放入 `src/lib/` 目录，构建时自动合并到 `target/lib/`。

```
user-plugin/
  src/
    lib/
      internal-sdk-1.0.jar        ← 手动放入，构建时自动打进 lib/
```

#### 第三步：MANIFEST.MF 自动生成

`maven-antrun-plugin` 扫描 `target/lib/` 下的所有 JAR，生成 MANIFEST.MF：

```manifest
Manifest-Version: 1.0
Class-Path: lib/internal-sdk-1.0.jar lib/some-third-party.jar
Plugin-Id: gj.module.user
Plugin-Version: 1.0.0-SNAPSHOT
```

`maven-jar-plugin` 使用此 MANIFEST.MF 打包 JAR，确保插件运行时 `GJJarPluginLoader` 能正确解析并加载 `lib/` 下的依赖。

#### 第四步：组装输出目录

所有产物最终汇集到 `target/plugins/{artifactId}/`：

```
target/plugins/gj.module.user/
├── gj.module.user-1.0.0-SNAPSHOT.jar     ← 插件主 JAR（含自动生成的 MANIFEST.MF）
├── gj.module.user.json                   ← 从 src/main/resources 拷贝
└── lib/                                   ← 插件私有依赖（自动分类 + src/lib/ 合并）
    ├── internal-sdk-1.0.jar
    └── some-third-party.jar
```

整个目录可直接复制到主应用的 `plugins/` 目录下部署使用（详见第 16 章）。

---

## 4. 插件生命周期

每个插件必须创建一个类继承 `GJPlugin`。框架通过两个钩子控制插件的初始化流程：

### 4.1 插件入口类

```java
package gj.module.user;

import gj.pf4j.GJPlugin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class UserPlugin extends GJPlugin {

    /**
     * 在 Spring 容器 refresh 之前调用。
     * 用于编程式注册 Bean（常规 @Component/@Service 等无需手动注册注解）。
     */
    @Override
    protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(
            AnnotationConfigApplicationContext context) {
        // 示例：注册一个手动创建的 Bean
        context.registerBean("customBean", CustomBean.class);
        return context;
    }

    /**
     * 在 Spring 容器 refresh 完成后调用。
     * 此时可以安全地获取已注册的 Bean 进行初始化操作。
     */
    @Override
    protected void afterApplicationContextReady(
            AnnotationConfigApplicationContext context) {
        // 示例：获取 Bean 并执行初始化逻辑
        UserService userService = context.getBean(UserService.class);
        userService.initialize();
    }
}
```

### 4.2 生命周期流程

```
插件加载（loadPlugin）
  └─ 创建 GJSpringPlugin 包装
       └─ start()
            ├─ preCreateApplicationContext()     ← 创建 AnnotationConfigApplicationContext
            │    ├─ 注册 GJPluginLifecycleManager
            │    ├─ 设置 GJPluginBeanNameGenerator（防止 Bean 名冲突）
            │    ├─ 设置父容器 = 主应用 ApplicationContext
            │    └─ 扫描插件包路径
            │
            ├─ beforeApplicationContextRefresh()  ← 【钩子1】编程注册 Bean
            │
            ├─ registerPluginResources()          ← 注册 i18n / MyBatis / 配置文件
            │
            ├─ context.refresh()                  ← Spring 容器刷新
            │
            └─ afterApplicationContextReady()     ← 【钩子2】初始化后逻辑
```

### 4.3 关键约束

- **命名一致性：** `plugin.id`（如 `gj.module.user`）必须与插件主类的包名完全一致，否则启动会抛出 `IllegalStateException`。
- **Bean 注册方式：** 框架通过 `scan()` 自动扫描插件包下的 `@Component`、`@Service`、`@Configuration` 等 Spring 注解。`beforeApplicationContextRefresh()` 在容器 refresh 之前调用，需要编程注册 Bean 时使用 `context.registerBean()`。
- **Bean 名称隔离：** 框架自动为每个插件的 Bean 添加 `{pluginId}.` 前缀，防止不同插件间 Bean 名冲突。

---

## 5. REST 端点

### 5.1 基本用法

插件中创建 `@RestController`，框架会在插件启动时自动将所有 `@RequestMapping` 方法注册到主应用的路由表中。

```java
package gj.module.user.controllers;

import gj.module.user.response.UserResponse;
import gj.module.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/list")
    public List<UserResponse> getList() {
        return userService.getList();
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Integer id) {
        return userService.getById(id);
    }

    @PostMapping("/create")
    public boolean create(@RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return userService.delete(id);
    }
}
```

### 5.2 Spring MVC 与 WebFlux 双路由模式

框架**同时支持 Spring MVC（Servlet 栈）和 Spring WebFlux（Reactive 栈）**，自动适配主应用的 Web 架构。两种模式的插件端代码**完全一样**——都使用 `@RestController` + `@RequestMapping`，区别仅在于运行时注册到的 HandlerMapping 不同：

| 主应用架构 | 注册的 HandlerMapping | 路由方式 |
|---|---|---|
| Spring MVC（Servlet） | `GJPluginRequestMappingHandlerMapping` | 基于 `@RequestMapping` 注解 |
| Spring WebFlux（Reactive） | `GJPluginWebFluxRequestMappingHandlerMapping` | 基于 `@RequestMapping` 注解 |

#### 注解路由

插件中只需正常编写 `@RestController`，框架会根据主应用的 Web 类型自动选择对应的 HandlerMapping 注册。**插件无需关心主应用是 MVC 还是 WebFlux。**

#### WebFlux 函数式路由（仅 WebFlux 模式）

如果主应用使用 WebFlux，框架还支持通过 `RouterFunction` 函数式编程定义路由。注入 `GJPluginWebFluxRouterFunctionRegistry` 并调用 `register()` 注册：

```java
package gj.module.user.route;

import gj.pf4j.webflux.GJPluginWebFluxRouterFunctionRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.*;

import java.util.List;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class UserRouterConfig {

    private final GJPluginWebFluxRouterFunctionRegistry registry;
    private final UserHandler handler;

    public UserRouterConfig(GJPluginWebFluxRouterFunctionRegistry registry, UserHandler handler) {
        this.registry = registry;
        this.handler = handler;
    }

    @PostConstruct
    public void registerRoutes() {
        RouterFunction<ServerResponse> routes = RouterFunctions
            .route(GET("/api/v1/user/list"), handler::getList)
            .andRoute(GET("/api/v1/user/{id}"),
                request -> handler.getById(request.pathVariable("id")));
        registry.register(List.of(routes));
    }
}
```

> 热卸载时调用 `unregister()` 移除。主应用的 MVC / WebFlux 详细配置步骤见 **[附录：主应用集成](#18-附录主应用集成)**。

---

## 6. 数据访问

基于 [MyBatis-Plus](https://baomidou.com/) 实现数据访问层。

### 6.1 DAO 包约定

Mapper 接口必须放在 `{pluginId}.dao` 包下（点号分隔，连字符自动替换）。例如 `plugin.id = gj.module.user`，则 DAO 包为 `gj.module.user.dao`。

### 6.2 实体类

```java
package gj.module.user.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("user")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("name")
    private String name;

    @TableField("email")
    private String email;

    @TableField("description")
    private String description;
}
```

### 6.3 Mapper 接口

```java
package gj.module.user.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gj.module.user.model.User;

public interface UserMapper extends BaseMapper<User> {
}
```

### 6.4 Service 实现

```java
package gj.module.user.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import gj.module.user.dao.UserMapper;
import gj.module.user.model.User;
import gj.module.user.response.UserResponse;
import gj.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<UserResponse> getList() {
        LambdaQueryWrapper<User> queryWrapper = Wrappers.lambdaQuery();
        List<User> users = userMapper.selectList(queryWrapper);
        return users.stream().map(u -> {
            UserResponse resp = new UserResponse();
            resp.setId(u.getId());
            resp.setName(u.getName());
            resp.setEmail(u.getEmail());
            return resp;
        }).toList();
    }
}
```

### 6.5 数据层隔离机制

`GJPluginMybatisSqlSessionManager` 为每个插件：

- 创建独立的 `SqlSessionFactory`（驼峰映射、禁用缓存、禁用懒加载）
- 创建独立的 `SqlSessionTemplate`（缓存复用）
- 创建独立的 `DataSourceTransactionManager`
- 通过 `MapperScannerConfigurer` 只扫描当前插件的 DAO 包

所有插件共享主应用注入的 `DataSource`。插件停止时自动清理缓存。

---

## 7. 数据库自动迁移

框架为插件实体提供数据库 Schema 自动迁移能力。插件启动时，自动扫描 `@TableName` 实体并与当前数据库结构对比，缺失的表和字段会被自动创建，无需手写 SQL 迁移脚本。

### 7.1 支持的数据库

迁移引擎支持 **7 种数据库**，通过 JDBC 连接元数据自动检测方言：

| 数据库 | 识别方式 |
|--------|----------|
| MySQL | JDBC URL 或产品名称 |
| PostgreSQL | JDBC URL 或产品名称 |
| GaussDB / openGauss | JDBC URL 或产品名称 |
| KingbaseES（人大金仓） | JDBC URL 或产品名称 |
| DM（达梦） | JDBC URL 或产品名称 |
| SQLite | JDBC URL 或产品名称 |
| Oracle | JDBC URL 或产品名称 |

无需额外配置 —— 方言选择（标识符引用、类型映射、DDL 渲染）根据 `DataSource` 连接自动识别。

### 7.2 生产安全性

迁移引擎遵循**严格的纯增量策略**，仅生成两种 DDL 操作：

| 操作 | 触发条件 |
|------|----------|
| **CREATE TABLE** | 表在数据库中不存在 |
| **ALTER TABLE ADD COLUMN** | 字段在目标表中不存在 |

不会生成 `DROP TABLE`、`DROP COLUMN`、`ALTER COLUMN`、`RENAME` 等任何破坏性 DDL。已有的表结构、字段和数据不会被修改，因此自动迁移可安全用于生产环境。

### 7.3 插件自动迁移

插件**无需任何配置**即可享受迁移能力。插件包路径下的所有 `@TableName` 实体类在插件启动时被自动扫描，框架对比实体模型与数据库实际结构，执行必要的建表或加字段操作。

以下场景均会自动触发迁移：

- **部署带新实体的插件** —— 自动创建表
- **已有插件增加新的实体字段** —— 自动添加列
- **已有插件增加新的实体类** —— 自动创建新表

当主应用上下文中不存在 `GJPluginModelMigrator` Bean（即未使用 `@EnableGJMigration`）时，迁移子系统完全不激活，零额外开销。

### 7.4 共享模型迁移

主应用自身也可以迁移其公共模型实体（例如所有插件共享的 `User`、`Menu`、`Role` 等基础表）。使用 `@EnableGJMigration` 注解并指定 `basePackages` 即可：

```java
@SpringBootApplication
@ComponentScan("gj")
@EnableGJMigration(basePackages = {"com.example.common.model", "com.example.platform.entity"})
public class GJApplication {
    public static void main(String[] args) {
        SpringApplication.run(GJApplication.class, args);
    }
}
```

**执行保障：**

- **优先级**：共享模型在**所有插件之前**迁移 —— 保证插件引用共享表时表已存在
- **仅执行一次**：无论加载或重启多少个插件，共享模型迁移在整个 JVM 生命周期内仅执行一次

> **注意：** 未添加 `@EnableGJMigration` 时，主应用不会创建 `GJPluginModelMigrator` Bean，整个迁移子系统处于关闭状态。仅在需要自动迁移时按需启用。

---

## 8. 对象映射

基于开源组件 [ModelMapper](https://modelmapper.org/) 实现类型映射。

### 8.1 映射配置类

在插件中实现 `GJPluginModelMapperConfig` 接口，标注 `@Component` 注册为 Spring Bean：

```java
package gj.module.user.modelmapper;

import gj.module.user.dto.UserDTO;
import gj.module.user.model.User;
import gj.pf4j.modelmapper.GJPluginModelMapperConfig;
import gj.pf4j.modelmapper.GJPluginTypeMapConfig;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserModelMapperConfig implements GJPluginModelMapperConfig {

    @Override
    public List<GJPluginTypeMapConfig> getTypeMapConfigs() {
        return List.of(
            // 简单映射：同名字段自动映射
            GJPluginTypeMapConfig.of(User.class, UserDTO.class),

            // 自定义映射：指定字段映射规则
            GJPluginTypeMapConfig.of(User.class, UserResponse.class, typeMap -> {
                typeMap.addMapping(User::getId, UserResponse::setId);
                typeMap.addMapping(User::getName, UserResponse::setUserName);
                typeMap.addMapping(User::getEmail, UserResponse::setEmailAddress);
            })
        );
    }
}
```

### 8.2 使用 ModelMapper

框架自动构建并注册 `ModelMapper` Bean，可以直接注入使用：

```java
@Service
public class UserServiceImpl implements UserService {

    private final ModelMapper modelMapper;
    private final UserMapper userMapper;

    public UserServiceImpl(ModelMapper modelMapper, UserMapper userMapper) {
        this.modelMapper = modelMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<UserDTO> getList() {
        return userMapper.selectList(Wrappers.lambdaQuery())
                .stream()
                .map(user -> modelMapper.map(user, UserDTO.class))
                .toList();
    }
}
```

### 8.3 映射注册机制

- 插件启动时，`GJPluginLifecycleManager` 监听 `GJPluginStartedEvent`，从插件容器扫描所有 `GJPluginModelMapperConfig` Bean
- 合并所有 `GJPluginTypeMapConfig`，若主应用已有 `ModelMapper` 则追加映射到共享实例，否则框架自动创建
- 如果已有同名 TypeMap，使用 `merge` 策略追加而非替换

---

## 9. 插件配置管理

### 9.1 配置类

使用 `@ConfigurationProperties` 绑定插件专属配置：

```java
package gj.module.user;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "gj.module.user")
public class UserConfig {
    private boolean enabled = true;
    private String value;
    private int maxRetry = 3;
    private String apiUrl;
}
```

### 9.2 配置文件

在 `src/main/resources/{pluginId}.properties` 中提供值：

```properties
gj.module.user.enabled=true
gj.module.user.value=iot
gj.module.user.max-retry=5
gj.module.user.api-url=https://api.example.com
```

### 9.3 注入使用

插件内任何 Spring Bean 都可以注入配置类：

```java
@Service
public class UserServiceImpl implements UserService {

    private final UserConfig config;

    public UserServiceImpl(UserConfig config) {
        this.config = config;
    }

    public void doSomething() {
        if (config.isEnabled()) {
            String apiUrl = config.getApiUrl();
            // ...
        }
    }
}
```

### 9.4 配置来源

框架按优先级加载配置：
1. 插件容器内部环境变量
2. `{pluginId}.properties` 文件（由 `GJPluginLifecycle.registerResource()` 加载到 PropertySource）
3. 主应用环境变量（父容器继承兜底）

---

## 10. 实时通信

基于 [netty-socketio](https://github.com/mrniko/netty-socketio) 实现实时通信。

### 10.1 创建 Hub

继承 `GJHub`，使用 `@GJHubMethod` 注解标记消息处理方法：

```java
package gj.module.user.socketio;

import gj.pf4j.socketio.GJHub;
import gj.pf4j.socketio.GJHubMethod;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class UserHub extends GJHub {

    public UserHub() {
        super("userHub");  // hubName，客户端通过此名称路由
    }

    /**
     * 客户端连接回调
     */
    @Override
    public CompletableFuture<Void> onConnectedAsync() {
        return CompletableFuture.runAsync(() -> {
            String connectionId = getContext().getConnectionId();
            System.out.println("User connected: (" + connectionId + ")");
        });
    }

    /**
     * 客户端断连回调
     */
    @Override
    public CompletableFuture<Void> onDisconnectedAsync() {
        return CompletableFuture.runAsync(() -> {
            System.out.println("disconnected");
        });
    }

    /**
     * 处理客户端发送的 "sendMessage" 消息
     */
    @GJHubMethod("sendMessage")
    public void onSendMessage(MessageData data) {
        // 向发送者以外的所有用户广播消息
        getClients().others().sendAsync("newMessage", data);

        // 向特定分组推送
        getClients().group("admin").sendAsync("newMessage", data);
    }

    /**
     * 处理客户端发送的 "joinGroup" 消息
     */
    @GJHubMethod("joinGroup")
    public void onJoinGroup(String groupName) {
        getGroups().addToGroupAsync(groupName);
    }
}
```

### 10.2 客户端推送 API

`getClients()` 返回 `GJHubCallerClients`，提供以下推送渠道：

```java
// 全体客户端
getClients().all().sendAsync("eventName", data);

// 仅调用者自己
getClients().caller().sendAsync("eventName", data);

// 除调用者以外的所有人
getClients().others().sendAsync("eventName", data);

// 指定连接 ID
getClients().client("connectionId123").sendAsync("eventName", data);

// 指定分组
getClients().group("admin").sendAsync("eventName", data);

// 指定用户
getClients().user("userId123").sendAsync("eventName", data);

// 指定分组但排除某个用户
getClients().groupExceptUser("admin", "excludedUserId").sendAsync("eventName", data);

// 排除指定连接
getClients().allExcept(List.of("connId1", "connId2")).sendAsync("eventName", data);
```

### 10.3 分组管理 API

`getGroups()` 返回 `GJGroupManager`：

```java
// 加入分组
getGroups().addToGroupAsync("groupName");

// 离开分组
getGroups().removeFromGroupAsync("groupName");

// 检查是否在分组中
getGroups().isInGroupAsync("groupName").thenAccept(inGroup -> {
    System.out.println("In group: " + inGroup);
});

// 获取当前连接的所有分组
getGroups().getGroupsForConnectionAsync().thenAccept(groups -> {
    System.out.println("My groups: " + groups);
});

// 获取分组内所有连接 ID
getGroups().getConnectionsInGroupAsync("groupName").thenAccept(connections -> {
    System.out.println("Connections in group: " + connections);
});
```

### 10.4 Hub 上下文

在 Hub 方法内通过 `getContext()` 获取当前连接信息：

```java
GJHubCallerContext ctx = getContext();
String connectionId = ctx.getConnectionId();
Map<String, String> queryParams = ctx.getQueryParams();
```

> 前端可通过连接 URL 传递自定义参数（如 `?hub=userHub&userId=123`），Hub 内通过 `ctx.getQueryParam("key")` 获取。注意不要在 URL 中明文传递敏感信息。

### 10.5 服务端配置

在主应用配置文件中按需配置 Socket.IO 服务器参数，例如：

```properties
socketio.port=9600
socketio.maxConnectionsPerSecond=10
```

所有配置项及默认值参见 `GJSocketIOConfig` 源码。

---

## 11. 国际化 i18n

### 11.1 插件 i18n 文件

在插件 classpath 下创建 `i18n/messages*.properties`：

```
src/main/resources/
  i18n/
    messages.properties          # 默认
    messages_zh_CN.properties    # 简体中文
    messages_en_US.properties    # 英文
```

示例 `i18n/messages_zh_CN.properties`：

```properties
user.list.title=用户列表
user.create.success=创建成功
user.delete.confirm=确认删除该用户？
```

示例 `i18n/messages_en_US.properties`：

```properties
user.list.title=User List
user.create.success=Created Successfully
user.delete.confirm=Confirm to delete this user?
```

### 11.2 注入使用

```java
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final MessageSource messageSource;

    public UserController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @GetMapping("/title")
    public String getTitle(Locale locale) {
        return messageSource.getMessage("user.list.title", null, locale);
    }
}
```

### 11.3 兜底机制

- 框架自动为每个插件创建 `GJPluginReloadableMessageSource`（Bean 名 `plugin_i18n_{pluginId}`）
- 查找 i18n key 时，优先查插件自己的 messages，查不到则 fallback 到主应用的 `messageSource`
- 找不到 key 时返回 key 本身（`useCodeAsDefaultMessage = true`）
- 缓存 24 小时，编码 UTF-8

---

## 12. 导入导出

基于 [EasyExcel](https://easyexcel.opensource.alibaba.com/)，提供 `IImportManager` 和 `IExportManager` 接口，支持多 Sheet 读写及 i18n 表头自动翻译。

### 13.1 导出示例

```java
@Service
public class UserExportService {

    private final IExportManager exportManager;
    private final UserMapper userMapper;

    public UserExportService(IExportManager exportManager, UserMapper userMapper) {
        this.exportManager = exportManager;
        this.userMapper = userMapper;
    }

    /**
     * 单 Sheet 导出
     */
    public String exportUsers() throws IOException {
        List<User> users = userMapper.selectList(null);
        return exportManager.exportToXlsx(users);
    }

    /**
     * 多 Sheet 导出
     */
    public String exportMultiSheet() throws IOException {
        Map<String, List<?>> sheets = new LinkedHashMap<>();
        sheets.put("用户列表", userMapper.selectList(null));
        sheets.put("角色列表", roleMapper.selectList(null));
        return exportManager.exportMultiSheetToXlsx(sheets);
    }

    /**
     * 导出到字节流（用于 HTTP 下载响应）
     */
    public ByteArrayOutputStream exportToStream() throws IOException {
        List<User> users = userMapper.selectList(null);
        return exportManager.exportToStream(users);
    }
}
```

### 13.2 导入示例

```java
@Service
public class UserImportService {

    private final IImportManager importManager;

    public UserImportService(IImportManager importManager) {
        this.importManager = importManager;
    }

    /**
     * 多 Sheet 导入
     */
    public void importUsers(InputStream inputStream) {
        List<List<Object>> sheets = importManager.importFromXlsx(
                "users.xlsx",
                inputStream,
                User.class,    // Sheet 0 → User 实体
                Role.class     // Sheet 1 → Role 实体
        );

        List<Object> userRows = sheets.get(0);  // 用户 Sheet
        List<Object> roleRows = sheets.get(1);  // 角色 Sheet

        // 处理导入数据...
    }
}
```

### 13.3 表头 i18n

EasyExcel 的 `@ExcelProperty` 注解值会在导入/导出时通过 i18n 自动翻译。框架重写了 `SimpleWriteHandler` 和 `ReadEventListener`，确保生成和解析的 Excel 表头与当前语言环境匹配。

```java
@Data
public class UserExcelVO {
    @ExcelProperty("user.excel.name")    // i18n key
    private String name;

    @ExcelProperty("user.excel.email")
    private String email;
}
```

---

## 13. 定时任务

基于 [Quartz](https://www.quartz-scheduler.org/) 提供插件定时任务调度能力。插件只需实现 `IPluginJob` 接口并标注 `@PluginJob` 注解，框架会在插件启动后自动扫描并注册到 Quartz 调度器。

### 14.1 依赖说明

框架已内置 Quartz 支持（`org.quartz-scheduler:quartz`），通过 `GJQuartzConfig` 自动创建 `Scheduler` Bean（`@ConditionalOnMissingBean`）。主应用无需额外引入任何 Quartz 依赖。若主应用已有自定义 `Scheduler` Bean，框架自动复用。

### 14.2 创建定时任务

在插件中创建实现 `IPluginJob` 的 Bean，用 `@PluginJob` 注解标记：

```java
package gj.module.user.job;

import gj.pf4j.quartzjob.IPluginJob;
import gj.pf4j.quartzjob.annotation.PluginJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@PluginJob(name = "cleanExpiredTokens", intervalSeconds = 3600)
public class TokenCleanupJob implements IPluginJob {

    @Override
    public void execute() {
        log.info("Cleaning expired tokens...");
        // 业务逻辑
    }
}
```

### 14.3 @PluginJob 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `name` | String | **必填** | 任务唯一标识，全局唯一 |
| `intervalSeconds` | long | -1 | 固定间隔（秒），与 `cronExpression` 二选一 |
| `cronExpression` | String | "" | Cron 表达式，与 `intervalSeconds` 二选一 |
| `runOnce` | boolean | false | 是否仅执行一次 |
| `disallowConcurrentExecution` | boolean | true | 是否禁止并发执行 |

### 14.4 Cron 表达式示例

```java
@PluginJob(name = "dailyReport", cronExpression = "0 0 8 * * ?")       // 每天 8:00
@PluginJob(name = "weeklySync", cronExpression = "0 0 2 ? * MON")       // 每周一凌晨 2:00
@PluginJob(name = "initData", runOnce = true)                            // 启动后执行一次
```

### 14.5 手动触发（注入 Scheduler）

对于需要在业务逻辑中手动触发的场景，可直接注入 Quartz `Scheduler`：

```java
@Service
public class ReportService {

    private final Scheduler scheduler;

    public ReportService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void triggerReport(String pluginId) throws SchedulerException {
        scheduler.triggerJob(new JobKey(pluginId + ":dailyReport", pluginId));
    }
}
```

---

## 14. 进程内事件总线

框架提供轻量级进程内事件总线，支持插件间解耦通信。监听器通过实现 `GJPluginLocalEventListener<T>` 接口处理特定类型的事件，事件类通过 `@EventName` 注解标记名称，支持 Ant 风格通配符匹配。

### 15.1 定义事件

事件类需要标注 `@EventName`，名称支持 `.` 分隔的层级结构：

```java
package gj.module.user.event;

import gj.pf4j.eventbus.EventName;
import lombok.Data;

@Data
@EventName("user.created")
public class UserCreatedEvent {
    private Long userId;
    private String userName;
}
```

### 15.2 创建监听器

实现 `GJPluginLocalEventListener<T>` 接口，标注 `@Component` 注册为 Spring Bean：

```java
package gj.module.user.listener;

import gj.module.user.event.UserCreatedEvent;
import gj.pf4j.eventbus.GJPluginLocalEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserCreatedListener implements GJPluginLocalEventListener<UserCreatedEvent> {

    @Override
    public void HandleEvent(UserCreatedEvent event) {
        log.info("User created: {} ({})", event.getUserName(), event.getUserId());
        // 发送欢迎邮件、初始化用户数据等
    }
}
```

### 15.3 发布事件

在任何 Spring Bean 中注入 `GJPluginLocalEventBus` 发布事件：

```java
@Service
public class UserService {

    private final GJPluginLocalEventBus eventBus;

    public UserService(GJPluginLocalEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void createUser(String name) {
        // 创建用户逻辑 ...

        // 同步发布 — 所有监听器在当前线程执行
        eventBus.publish(new UserCreatedEvent(userId, name));

        // 异步发布 — 监听器在线程池中执行
        eventBus.publishAsync(new UserCreatedEvent(userId, name));
    }
}
```

### 15.4 通配符匹配

`@EventName` 支持 Ant 风格通配符，`.` 作为路径分隔符：

```java
@EventName("user.*")          // 匹配 user.created、user.updated 等
@EventName("order.cancelled")  // 精确匹配
```

一个事件可以被多个监听器匹配，每个监听器独立执行。

---

## 15. OpenAPI 文档

### 16.1 自动分组

框架为每个已注册 Controller 的插件自动创建独立的 `GroupedOpenApi` Bean（SpringDoc），分组名规则为 `pluginGroupedOpenApi-{pluginId}`。访问 Swagger-UI 时，通过右上角下拉菜单选择对应插件查看其 API 文档。

### 16.2 Controller 示例（配合 Swagger）

```java
@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "用户管理", description = "用户 CRUD 接口")
public class UserController {

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询用户")
    public UserResponse getById(
            @Parameter(description = "用户ID") @PathVariable Integer id) {
        return userService.getById(id);
    }

    @PostMapping("/create")
    @Operation(summary = "创建用户")
    public boolean create(
            @Parameter(description = "创建请求") @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }
}
```

### 16.3 访问地址

启动后访问：`http://localhost:{port}/swagger-ui/index.html`

---

## 16. 插件打包与部署

### 17.1 构建插件

```bash
cd user-plugin
mvn clean package
```

### 17.2 输出目录结构

构建完成后，`target/plugins/{artifactId}/` 目录结构如下：

```
target/plugins/gj.module.user/
├── gj.module.user-1.0.0-SNAPSHOT.jar     # 插件主 JAR
├── gj.module.user.json                   # 插件描述文件
└── lib/                                   # 插件私有的非主应用依赖 JAR
    ├── some-third-party.jar
    └── ...
```

### 17.3 MANIFEST.MF

```manifest
Plugin-Id: gj.module.user
Plugin-Version: 1.0.0-SNAPSHOT
Class-Path: lib/some-third-party.jar lib/another-lib.jar
```

### 17.4 部署到主应用

将 `target/plugins/gj.module.user/` 整个目录复制到主应用的 `plugins/` 目录下：

```
主应用根目录/              ← dev/debug 模式下即当前工作目录
  plugins/
    gj.module.user/
      gj.module.user-1.0.0-SNAPSHOT.jar
      gj.module.user.json
      lib/
        ...
    gj.module.other/
      ...
```

生产环境（非 dev/debug profile）下，插件目录位于 `ApplicationHome`（Spring Boot JAR 所在目录）下的 `plugins/`。

### 17.5 版本管理

`GJJarPluginRepository` 自动扫描每个插件目录，解析 JAR 文件名中的版本号（格式 `{pluginId}-{version}.jar`），选择最新版本加载。目录中存在多个版本时只加载最高版本，并在日志中记录。

---

## 17. 插件运行时管理 API

### 18.1 注入 GJPluginService

```java
@RestController
@RequestMapping("/api/admin/plugins")
public class PluginAdminController {

    private final GJPluginService pluginService;

    public PluginAdminController(GJPluginService pluginService) {
        this.pluginService = pluginService;
    }

    // ... 管理接口
}
```

### 18.2 加载并启动所有插件

```java
@PostMapping("/load-all")
public void loadAndStartAll() {
    pluginService.loadAndStartAllPlugins();
}
```

### 18.3 启动单个插件

```java
@PostMapping("/{pluginId}/start")
public String startPlugin(@PathVariable String pluginId) {
    PluginState state = pluginService.startPlugin(pluginId);
    return "Plugin " + pluginId + " state: " + state;
}
```

> 启动时会自动解析并先启动该插件的依赖插件。

### 18.4 停止单个插件

```java
@PostMapping("/{pluginId}/stop")
public String stopPlugin(@PathVariable String pluginId) {
    PluginState state = pluginService.stopPlugin(pluginId);
    return "Plugin " + pluginId + " state: " + state;
}
```

> 停止时会先停止所有依赖该插件的反向依赖插件。

### 18.5 重启单个插件

```java
@PostMapping("/{pluginId}/restart")
public String restartPlugin(@PathVariable String pluginId) {
    PluginState state = pluginService.restartPlugin(pluginId);
    return "Plugin " + pluginId + " state: " + state;
}
```

### 18.6 热加载 / 热卸载单个插件

```java
// 热卸载（从内存中移除，但不删除文件）
@DeleteMapping("/{pluginId}/unload")
public String unloadPlugin(@PathVariable String pluginId) {
    boolean success = pluginService.unloadPlugin(pluginId);
    return success ? "Unloaded" : "Failed";
}

// 热加载（重新从文件系统发现并加载）
@PostMapping("/{pluginId}/reload")
public String reloadPlugin(@PathVariable String pluginId) {
    PluginState state = pluginService.reloadPlugin(pluginId);
    return "Plugin " + pluginId + " state: " + state;
}
```

### 18.7 重载全部插件

```java
@PostMapping("/reload-all")
public void reloadAll() {
    pluginService.reloadAll();  // 停止全部 → 卸载全部 → 加载全部 → 启动全部
}
```

### 18.8 删除插件

```java
@DeleteMapping("/{pluginId}")
public String deletePlugin(@PathVariable String pluginId) {
    boolean deleted = pluginService.deletePlugin(pluginId);
    return deleted ? "Deleted" : "Failed";
}
```

---

## 18. 附录：主应用集成

### 18.1 版本兼容性说明

gj-pf4j 自身依赖 Spring 核心包（spring-webmvc、spring-beans、spring-jdbc 等），但**不锁定版本号**。框架通过发布 `gj-dependencies` BOM 统一管理版本，开发者在项目中按优先级引入 BOM，Spring 全家桶版本会自动跟随开发者所选 Spring Boot 版本，避免冲突。

### 引入 BOM（推荐）

在 `dependencyManagement` 中按顺序引入 gj BOM 和 Spring Boot BOM——**Spring Boot BOM 放后面，优先级更高**，确保 Spring 版本以开发者指定的 Spring Boot 版本为准，gj BOM 仅兜底 Spring Boot 不管理的依赖（如 PF4J、netty-socketio）：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.wangpengxpy</groupId>
            <artifactId>gj-dependencies</artifactId>
            <version>1.0.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Spring Boot BOM 放后面，同 key 时覆盖 gj BOM 的版本 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

然后直接引入 gj-pf4j（无需指定版本，由 gj BOM 管理）：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.wangpengxpy</groupId>
        <artifactId>gj-pf4j</artifactId>
    </dependency>
</dependencies>
```

**版本选择说明：**

| 开发者 Spring Boot | gj BOM 管理的 Spring 版本 | 实际生效版本 |
|---|---|---|
| 3.5.x | 3.5.5 | 3.5.x（SB BOM 覆盖） |
| 4.0.x | 3.5.5 | 4.0.x（SB BOM 覆盖） |

gj BOM 中与 Spring Boot 重叠的依赖（spring-webmvc、spring-beans 等）会被 SB BOM 覆盖，gj BOM 只管 SB BOM 不覆盖的依赖（pf4j、netty-socketio、modelmapper 等）。

### 直接引入（不推荐）

也可跳过 BOM，直接引入 gj-pf4j，但需自行确保 Spring 版本兼容：

```xml
<dependency>
    <groupId>io.github.wangpengxpy</groupId>
    <artifactId>gj-pf4j</artifactId>
    <version>1.0.3</version>
</dependency>
```

### 18.2 主应用入口配置

**必须配置：**

```java
@SpringBootApplication
@ComponentScan("gj")     // 框架所有 Bean 均位于 gj.pf4j 包下，必须扫描
public class GJApplication {
    public static void main(String[] args) {
        SpringApplication.run(GJApplication.class, args);
    }
}
```

- 框架已内置 `GJPluginConfig` 和 `GJPluginWebFluxConfig`，随 `@ComponentScan("gj")` 自动激活。
- 框架会在主应用 `ContextRefreshedEvent` 触发后自动加载并启动 `plugins/` 目录下的所有插件。

> 如需在主应用中配置共享 ModelMapper 映射（基础模型包），参见 [18.4](#184-按需配置gjmodelmapperscan共享模型)。

### Spring MVC 模式（默认）

Spring Boot 主应用**默认即为 MVC（Servlet）模式**，无需任何额外配置。确保引入 `spring-boot-starter-web` 和 `springdoc-openapi-starter-webmvc-ui` 即可：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

主类无需设置 `WebApplicationType`（默认就是 `SERVLET`）：

```java
@SpringBootApplication
@ComponentScan("gj")
public class GJApplication {
    public static void main(String[] args) {
        SpringApplication.run(GJApplication.class, args);
    }
}
```

gj-pf4j 自动使用 `GJPluginRequestMappingHandlerMapping`（MVC），插件的 `@RestController` 通过 Servlet 容器注册路由。

### Spring WebFlux 模式

若主应用使用 WebFlux 响应式架构，需要**两步显式配置**：

**1. 替换依赖**

```xml
<!-- 不使用 spring-boot-starter-web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<!-- webmvc-ui 替换为 webflux-ui -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
</dependency>
```

**2. 显式指定 Web 类型**

```java
@SpringBootApplication
@ComponentScan("gj")
public class GJApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(GJApplication.class)
                .web(WebApplicationType.REACTIVE)
                .run(args);
    }
}
```

gj-pf4j 检测到 `GJPluginWebFluxRequestMappingHandlerMapping` Bean 后，自动使用 WebFlux 模式注册插件 Controller 路由。

### 两种模式对比

| | MVC 模式（默认） | WebFlux 模式 |
|---|---|---|
| Web 容器 | Tomcat（Servlet） | Netty（Reactive） |
| 依赖 | `spring-boot-starter-web` | `spring-boot-starter-webflux` |
| SpringDoc | `springdoc-openapi-starter-webmvc-ui` | `springdoc-openapi-starter-webflux-ui` |
| WebApplicationType | 无需设置（默认 SERVLET） | 显式 `.web(REACTIVE)` |
| 插件 Controller 写法 | `@RestController` | `@RestController`（完全一样） |
| 路由注册 | `GJPluginRequestMappingHandlerMapping` | `GJPluginWebFluxRequestMappingHandlerMapping` |

### 18.3 Claude Code 集成

框架内置 [Claude Code](https://claude.ai/code) skills，通过 AI 命令驱动插件开发：

```bash
/gj-plugin-new "用户管理插件，CRUD + 实时推送 + 定时清理"
```

**新项目**（archetype 生成后自动携带）：

```bash
mvn archetype:generate -DarchetypeGroupId=io.github.wangpengxpy -DarchetypeArtifactId=gj-archetype ...
```

**已有项目**：从 `tools/claude-skills/` 复制到项目根目录：

```bash
git clone --depth 1 https://github.com/wangpengxpy/gj.spring.pf4j.git /tmp/gj-pf4j
cp -r /tmp/gj-pf4j/tools/claude-skills/* .claude/
```

> 内部通过 OpenSpec 进行需求分析和任务拆解，然后调用 `gj-plugin` skill 自动生成代码。

### 18.4 按需配置：`@GJModelMapperScan`（共享模型）

当主应用有通用的基础模型包（如 `User`、`Menu`、`Role` 等实体及其 Mapper、DTO、ModelMapper 映射配置），可以单独引入 `gj-modelmapper` artifact 并用 `@GJModelMapperScan` 扫描，将这些映射注入全局 `ModelMapper` Bean。业务插件通过父容器继承自动共享：

```
主应用
  ├─ DataSource
  ├─ SqlSessionFactory → UserMapper, MenuMapper, RoleMapper ...
  ├─ ModelMapper (User→UserDTO, Menu→MenuDTO) ← @GJModelMapperScan 配置
  │
  └─ [父容器] ── 插件（继承）
       ├─ [继承] ModelMapper — final ModelMapper mm → 直接用，含全套共享映射
       ├─ [继承] UserMapper — final UserMapper um → 直接查共享表
       ├─ [自己的] PluginMapper — 查插件独有表
       └─ [追加] 插件独有映射 — 自动加到共享 ModelMapper 实例上，无需重复配置
```

配置方式：

```xml
<!-- 主应用 pom.xml -->
<dependency>
    <groupId>io.github.wangpengxpy</groupId>
    <artifactId>gj-modelmapper</artifactId>
</dependency>
```

```java
@SpringBootApplication
@ComponentScan("gj")
@GJModelMapperScan(
    basePackages = "your.app.model",      // 基础模型包路径
    markerInterface = GJModelMapperConfig.class
)
public class GJApplication { ... }
```

基础模型包内定义映射配置：

```java
package your.app.model;

import gj.modelmapper.GJModelMapperConfig;
import gj.modelmapper.GJModelMapperTypeMapConfig;
import java.util.List;

public class AppModelMapperConfig implements GJModelMapperConfig {
    @Override
    public List<GJModelMapperTypeMapConfig> getTypeMapConfigs() {
        return List.of(
            GJModelMapperTypeMapConfig.of(User.class, UserDTO.class),
            GJModelMapperTypeMapConfig.of(Menu.class, MenuDTO.class)
        );
    }
}
```

> **关键理解：** `@GJModelMapperScan` 和插件 `GJPluginModelMapperConfig` 是两个独立机制。前者归主应用（注入全局 `ModelMapper`），后者归插件（实现接口并标注 `@Component`，通过 Spring Bean 扫描自动发现，追加映射到共享 `ModelMapper` 实例）。主应用不配 `@GJModelMapperScan` 不影响插件 ModelMapper 正常工作——框架会自动创建。
