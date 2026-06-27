# gj.spring.pf4j

[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/wangpengxpy/gj.spring.pf4j/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.wangpengxpy/gj-pf4j?color=green)](https://central.sonatype.com/artifact/io.github.wangpengxpy/gj-pf4j)
[![Stars](https://img.shields.io/github/stars/wangpengxpy/gj.spring.pf4j?style=social)](https://github.com/wangpengxpy/gj.spring.pf4j/stargazers)

A lightweight, modular plugin framework powered by PF4J and Spring, with no heavyweight Spring Boot dependency. Supports both Spring MVC and Spring WebFlux routing — auto-adapts to the host application's web stack.

> [中文文档](README_CN.md)

<p align="center">
  <img src="images/architecture.png" alt="gj.spring.pf4j Plugin Architecture" width="85%">
</p>

---

## Table of Contents

1. [Overview](#1-overview)
2. [Quick Start](#2-quick-start)
3. [Plugin Project Structure](#3-plugin-project-structure)
4. [Plugin Lifecycle](#4-plugin-lifecycle)
5. [REST Endpoints](#5-rest-endpoints)
    * [5.1 Basic Usage](#51-basic-usage)
    * [5.2 Spring MVC vs. WebFlux Dual Routing](#52-spring-mvc-vs-webflux-dual-routing)
    * [5.3 Anonymous Access](#53-anonymous-access)
6. [Data Access](#6-data-access)
    * [6.1 MyBatis-Plus](#61-mybatis-plus-data-access)
    * [6.2 JPA](#62-jpa-data-access)
    * [6.3 SQL Keyword Quoting](#63-sql-keyword-quoting)
7. [Database Auto-Migration](#7-database-auto-migration)
8. [Object Mapping](#8-object-mapping)
9. [Plugin Configuration Management](#9-plugin-configuration-management)
10. [Real-Time Communication](#10-real-time-communication)
11. [Internationalization (i18n)](#11-internationalization-i18n)
12. [Import/Export](#12-importexport)
13. [Scheduled Tasks](#13-scheduled-tasks)
14. [In-Process Event Bus](#14-in-process-event-bus)
15. [JSON Serialization — ObjectMapper](#15-json-serialization--objectmapper)
16. [OpenAPI Documentation](#16-openapi-documentation)
17. [Plugin Packaging & Deployment](#17-plugin-packaging--deployment)
18. [Runtime Plugin Management API](#18-runtime-plugin-management-api)
19. [Plugin Hot-Reload](#19-plugin-hot-reload)
20. [Appendix: Host Application Integration](#20-appendix-host-application-integration)
    * [Version Compatibility](#201-version-compatibility)
    * [Host Application Entry Point](#202-host-application-entry-point)
    * [Optional: @GJModelMapperScan (Shared Models)](#203-optional-gjmodelmapperscan-shared-models)
21. [Claude Code Integration](#21-claude-code-integration)
22. [FAQ](#22-faq)

---

## 1. Overview

gj.spring.pf4j is a lightweight, modular plugin framework built on [PF4J](https://pf4j.org/) with Spring integration. It depends only on Spring core libraries (spring-context, spring-webmvc, spring-jdbc, etc.) and does not require Spring Boot as a runtime dependency. It is suitable for projects that need a modular architecture without the full Spring Boot stack.

### Core Capabilities

<p align="center">
  <img src="images/capabilities.png" alt="Plugin Core Capabilities" width="90%">
</p>

- **[Plugin Lifecycle Management](#4-plugin-lifecycle)** — install, disable, restart, unload, and delete plugins at runtime
- **[Plugin Hot-Reload](#19-plugin-hot-reload)** — hot-reload via API-driven workflow or file watcher; lifecycle events for custom orchestration logic
- **[Runtime Plugin Management API](#18-runtime-plugin-management-api)** — `GJPluginService` provides lock-controlled runtime management APIs
- **[REST Endpoints](#5-rest-endpoints)** — `@RestController` beans are auto-detected and registered into the main application's route table, supporting both MVC and WebFlux
- **[Dual Routing Mode](#52-spring-mvc-vs-webflux-dual-routing)** — supports both Spring MVC (Servlet) and Spring WebFlux (Reactive) routing; plugins require zero changes
- **[OpenAPI Documentation](#16-openapi-documentation)** — powered by SpringDoc; each plugin auto-generates an independent `GroupedOpenApi`
- **[MyBatis-Plus Data Access](#61-mybatis-plus-data-access)** — [MyBatis-Plus](https://baomidou.com/) (built-in); each plugin gets its own `SqlSessionFactory`, `SqlSessionTemplate`, and `TransactionManager`, all sharing the main application's `DataSource`
- **[JPA Data Access](#62-jpa-data-access)** — JPA (Jakarta Persistence API) powered by Hibernate; each plugin gets its own `EntityManagerFactory` and `JpaTransactionManager`, sharing the host's `DataSource`. Works alongside MyBatis-Plus, activated by adding `hibernate-core` to the host application
- **[SQL Keyword Quoting](#63-sql-keyword-quoting)** — MyBatis-Plus `InnerInterceptor` automatically wraps column names with database-specific quote characters to prevent reserved-keyword conflicts; both host app and plugins can register keyword definitions via `GJTableKeywordProvider`
- **[Database Auto-Migration](#7-database-auto-migration)** — automatic `@TableName` entity schema migration (CREATE TABLE / ADD COLUMN only), supports 7 databases, production-safe
- **[Object Mapping](#8-object-mapping)** — powered by [ModelMapper](https://modelmapper.org/); plugins implement `GJPluginModelMapperConfig`, auto-discovered via Spring bean scanning
- **[Import/Export](#12-importexport)** — powered by [EasyExcel](https://easyexcel.opensource.alibaba.com/); multi-sheet read/write with automatic i18n header translation
- **[Real-Time Communication](#10-real-time-communication)** — powered by [netty-socketio](https://github.com/mrniko/netty-socketio); built-in Hub pattern (SignalR-style) with group and user-targeted messaging
- **[Scheduled Tasks](#13-scheduled-tasks)** — powered by [Quartz](https://www.quartz-scheduler.org/); supports cron, fixed-interval, and run-once execution
- **[In-Process Event Bus](#14-in-process-event-bus)** — lightweight in-process event bus with sync/async publishing and Ant-style wildcard matching
- **[Internationalization (i18n)](#11-internationalization-i18n)** — per-plugin `messages.properties` with fallback to the main application

---

## 2. Quick Start

<p align="center">
  <img src="images/quickstart.png" alt="Quick Start Guide" width="85%">
</p>

### 2.1 Install the Archetype Locally

```bash
cd src/gj-archetypes
mvn clean install
```

### 2.2 Generate a Plugin Project

```bash
mvn archetype:generate \
  -DarchetypeGroupId=io.github.wangpengxpy \
  -DarchetypeArtifactId=gj-archetype \
  -DarchetypeVersion=1.1.0 \
  -DgroupId=com.example \
  -DpluginName=user \
  -DpackagePrefix=gj.module
```

**Parameters:**

| Parameter | Meaning | Example |
|---|---|---|
| `groupId` | Maven groupId for the generated project | `com.example` |
| `pluginName` | Short plugin name (used for class/package naming) | `user` |
| `packagePrefix` | Java package prefix for the plugin | `gj.module` |

The generated `plugin.id` will be `gj.module.user`, and all Java classes will reside under the `gj.module.user` package.

### 2.3 Generated Project Structure

```
user-plugin/
├── pom.xml                          # Plugin POM, depends on gj-pf4j
├── pom-parent.xml                   # Build parent POM (packaging rules)
└── src/
    └── main/
        ├── java/
        │   └── gj/module/user/
        │       ├── UserPlugin.java                      # Plugin entry point
        │       ├── UserConfig.java                      # Plugin configuration
        │       ├── controllers/
        │       │   └── UserController.java              # REST controller
        │       ├── dao/
        │       │   └── UserMapper.java                  # MyBatis Mapper
        │       ├── dto/
        │       │   └── EgroupDTO.java                   # Data transfer object
        │       ├── model/
        │       │   └── Test.java                        # Database entity
        │       ├── entity/
        │       │   └── UserEntity.java                  # JPA @Entity (requires host hibernate-core)
        │       ├── repository/
        │       │   └── UserRepository.java              # JPA JpaRepository (requires host hibernate-core)
        │       ├── modelmapper/
        │       │   └── UserModelMapperConfig.java       # ModelMapper config
        │       ├── request/
        │       │   └── UserEventRequest.java            # Request DTO
        │       ├── response/
        │       │   ├── UserResponse.java                # List response DTO
        │       │   └── UserEventResponse.java           # Event response DTO
        │       └── service/
        │           ├── UserService.java                 # Service interface
        │           └── impl/
        │               └── UserServiceImpl.java         # Service implementation
        └── resources/
            ├── plugin.properties                # PF4J plugin descriptor
            └── gj.module.user.properties        # Plugin business configuration
```

---

## 3. Plugin Project Structure

### 3.1 Directory Conventions

| Directory / File | Purpose | Notes |
|---|---|---|
| `{Plugin}.java` | Plugin entry class | Extends `GJPlugin`, lifecycle hooks |
| `{Plugin}Config.java` | Plugin config class | `@ConfigurationProperties` binding |
| `controllers/` | REST controllers | `@RestController`, auto-registered as routes |
| `dao/` | Data access layer | MyBatis Mapper interfaces, extending `BaseMapper<T>` |
| `model/` | Database entities | MyBatis-Plus `@TableName` entities |
| `entity/` | JPA entities | `@Entity` + `@Table`, optional (requires host to add `hibernate-core`) |
| `repository/` | JPA data access | Spring Data JPA `JpaRepository<T, ID>` interfaces, optional (requires host to add `hibernate-core`) |
| `dto/` | Data transfer objects | Non-persistent DTOs |
| `request/` | Request objects | API input DTOs |
| `response/` | Response objects | API return DTOs |
| `service/` | Service interfaces | Business logic interfaces |
| `serviceimpl/` | Service implementations | `@Service`, `@Transactional` |
| `modelmapper/` | Mapping configuration | Implements `GJPluginModelMapperConfig` |
| `plugin.properties` | PF4J descriptor | `plugin.id`, `plugin.class`, `plugin.version` |
| `{pluginId}.properties` | Plugin business config | Business parameters bound to Config class |

### 3.2 plugin.properties

```properties
plugin.id=gj.module.user
plugin.class=gj.module.user.UserPlugin
plugin.version=1.0.0-SNAPSHOT
plugin.description=
plugin.provider=
plugin.dependencies=
plugin.order=100000
```

> **Constraint:** `plugin.id` must exactly match the plugin's main package name.

### 3.3 {pluginId}.properties (Plugin Business Config)

```properties
gj.module.user.enabled=true
gj.module.user.value=iot
```

### 3.4 pom-parent.xml Build Rules

The `pom-parent.xml` is a standalone parent POM with a fully automated build pipeline. Running `mvn clean package` executes the following steps in order:

#### Step 1: Dependency Classification — Auto-Separate Shared vs. Private JARs

`maven-dependency-plugin` scans all runtime dependencies at the `prepare-package` phase and automatically classifies them based on the `excludeGroupIds` list:

| Dependency Type | Criteria | Handling |
|---|---|---|
| **Shared (provided by host)** | groupId in the exclusion list | **Skipped** — not copied, not packaged |
| **Plugin-private** | groupId NOT in the exclusion list | Copied to `target/lib/`, packaged into `lib/` |

The exclusion list covers all frameworks already integrated by the host application: the entire Spring ecosystem, MyBatis-Plus, PF4J, Jackson, Netty, SocketIO, ModelMapper, EasyExcel, Lombok, SLF4J, Hibernate Validator, Jakarta, and more — 60+ groupIds in total.

```xml
<!-- Exclusion config in pom-parent.xml (partial excerpt) -->
<excludeGroupIds>
    org.springframework, org.springframework.boot,  <!-- Spring Framework -->
    org.mybatis, com.baomidou,                      <!-- MyBatis-Plus -->
    org.pf4j,                                        <!-- PF4J Plugin Framework -->
    com.fasterxml.jackson.core,                      <!-- Jackson -->
    io.netty, com.corundumstudio.socketio,           <!-- Netty + Socket.IO -->
    org.modelmapper,                                  <!-- ModelMapper -->
    org.projectlombok,                                <!-- Lombok -->
    org.slf4j, ch.qos.logback,                       <!-- Logging -->
    jakarta.servlet, jakarta.annotation,              <!-- Jakarta -->
    com.alibaba,                                      <!-- EasyExcel -->
    ...
</excludeGroupIds>
```

> **This means:** you simply declare dependencies in `pom.xml`. The build automatically decides — shared JARs are excluded; only plugin-specific third-party libraries end up in `lib/`.

#### Step 2: Private Library Aggregation

If the plugin has **non-Maven-Central private JARs** (e.g., internal SDKs, customized libraries), place them in `src/lib/`. They are automatically merged into `target/lib/` during the build.

```
user-plugin/
  src/
    lib/
      internal-sdk-1.0.jar        ← manually placed, auto-packaged into lib/
```

#### Step 3: MANIFEST.MF Auto-Generation

`maven-antrun-plugin` scans all JARs under `target/lib/` and generates the MANIFEST.MF:

```manifest
Manifest-Version: 1.0
Class-Path: lib/internal-sdk-1.0.jar lib/some-third-party.jar
Plugin-Id: gj.module.user
Plugin-Version: 1.0.0-SNAPSHOT
```

`maven-jar-plugin` uses this MANIFEST.MF when packaging the JAR, ensuring that `GJJarPluginLoader` correctly resolves and loads `lib/` dependencies at runtime.

#### Step 4: Assemble Output Directory

All artifacts converge into `target/plugins/{artifactId}/`:

```
target/plugins/gj.module.user/
├── gj.module.user-1.0.0-SNAPSHOT.jar     ← Plugin main JAR (with generated MANIFEST.MF)
├── gj.module.user.json                   ← Copied from src/main/resources
└── lib/                                   ← Plugin-private dependencies (auto-classified + src/lib/ merged)
    ├── internal-sdk-1.0.jar
    └── some-third-party.jar
```

The entire directory can be copied directly into the host application's `plugins/` directory for deployment (see Chapter 16).

### 3.5 Plugin Dependency Resolution

gj.spring.pf4j supports two mechanisms for controlling plugin startup order. Most scenarios only need `plugin.order` for simple priority-based ordering.

#### 3.5.1 plugin.order — Simple Priority Ordering

`plugin.order` is an integer value that controls the batch startup sequence. Plugins with lower values start first. The default value is `100000`, which ensures backward compatibility for plugins that do not configure this property.

```properties
# plugin-a/plugin.properties — starts first
plugin.order=100

# plugin-b/plugin.properties — starts second
plugin.order=200
```

Startup order: plugin-a → plugin-b. Stop order is automatically reversed: plugin-b → plugin-a.

**When to use:** Most scenarios. Simple, no version constraints, no dependency declarations needed.

#### 3.5.2 plugin.dependencies — Topological Dependency Graph

`plugin.dependencies` is PF4J's built-in mechanism for declaring explicit plugin dependencies with optional version constraints. PF4J resolves these into a directed graph and enforces topological ordering — a plugin always starts after its declared dependencies.

```properties
# plugin-b requires plugin-a at version 1.0 or higher
plugin.dependencies=plugin-a@1.0

# Optional dependency — skipped if plugin-c is not loaded
plugin.dependencies=plugin-c;optional
```

**When to use:** When exact version pinning or optional dependencies are required.

#### 3.5.3 Choosing Between Them

| Scenario | Recommended |
|---|---|
| Simple ordering without version constraints | `plugin.order` |
| Exact version requirements (e.g., `plugin-a@2.0`) | `plugin.dependencies` |
| Optional dependencies (`;optional`) | `plugin.dependencies` |

> **Important:** Do not configure both mechanisms with conflicting order values on the same plugin set. When both are present, `plugin.order` sorting happens after topological resolution and may override dependency order.

---

## 4. Plugin Lifecycle

Every plugin must create a class extending `GJPlugin`. The framework controls initialization through two hooks:

### 4.1 Plugin Entry Class

```java
package gj.module.user;

import gj.pf4j.GJPlugin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class UserPlugin extends GJPlugin {

    /**
     * Called BEFORE the Spring context is refreshed.
     * Use this to programmatically register beans (do NOT rely on
     * @Component/@Service stereotype scanning here).
     */
    @Override
    protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(
            AnnotationConfigApplicationContext context) {
        // Example: register a manually created bean
        context.registerBean("customBean", CustomBean.class);
        return context;
    }

    /**
     * Called AFTER the Spring context has been fully refreshed.
     * Beans can be safely retrieved and initialized here.
     */
    @Override
    protected void afterApplicationContextReady(
            AnnotationConfigApplicationContext context) {
        // Example: retrieve a bean and run initialization logic
        UserService userService = context.getBean(UserService.class);
        userService.initialize();
    }
}
```

### 4.2 Lifecycle Flow

```
Plugin loaded (loadPlugin)
  └─ GJSpringPlugin wrapper created
       └─ start()
            ├─ preCreateApplicationContext()      ← Creates AnnotationConfigApplicationContext
            │    ├─ Registers GJPluginLifecycleManager
            │    ├─ Sets GJPluginBeanNameGenerator (prevents bean name collisions)
            │    ├─ Sets parent context = main app ApplicationContext
            │    └─ Scans the plugin package
            │
            ├─ beforeApplicationContextRefresh()  ← [Hook 1] Programmatic bean registration
            │
            ├─ registerPluginResources()          ← Registers i18n / MyBatis / config files
            │
            ├─ context.refresh()                  ← Spring container refresh
            │
            └─ afterApplicationContextReady()     ← [Hook 2] Post-init logic
```

### 4.3 Key Constraints

- **Naming consistency:** `plugin.id` (e.g., `gj.module.user`) must exactly match the package name of the plugin's main class. Mismatch throws `IllegalStateException` at startup.
- **Bean registration pattern:** The framework auto-scans the plugin package via `scan()`, detecting `@Component`, `@Service`, `@Configuration`, and other Spring stereotypes. `beforeApplicationContextRefresh()` is called before the context is refreshed — use `context.registerBean()` for programmatic registration.
- **Bean name isolation:** The framework automatically prefixes all plugin bean names with `{pluginId}.` to prevent collisions (e.g., `gj.module.user.userService`).

---

## 5. REST Endpoints

### 5.1 Basic Usage

Create `@RestController` classes in the plugin. The framework auto-detects all `@RequestMapping`-annotated methods and registers them into the main application's route table at plugin startup.

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

### 5.2 Spring MVC vs. WebFlux Dual Routing

The framework **supports both Spring MVC (Servlet stack) and Spring WebFlux (Reactive stack)**, automatically adapting to the host application's web architecture. Plugin-side code is **identical** for both modes — you always use `@RestController` + `@RequestMapping`. The only difference is which HandlerMapping is used at runtime:

| Host App Architecture | Registered HandlerMapping | Routing Style |
|---|---|---|
| Spring MVC (Servlet) | `GJPluginRequestMappingHandlerMapping` | `@RequestMapping` annotation-based |
| Spring WebFlux (Reactive) | `GJPluginWebFluxRequestMappingHandlerMapping` | `@RequestMapping` annotation-based |

#### Annotation-Based Routing

Write `@RestController` as usual in the plugin. The framework automatically selects the correct HandlerMapping based on the host application's web type. **Plugins do not need to know whether the host uses MVC or WebFlux.**

#### WebFlux Functional Routing (WebFlux Mode Only)

When the host application uses WebFlux, the framework also supports defining routes via `RouterFunction` (functional style). Inject `GJPluginWebFluxRouterFunctionRegistry` and call `register()`:

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

> Call `unregister()` for cleanup on hot-unload. See **[Appendix: Host Application Integration](#19-appendix-host-application-integration)** for detailed MVC / WebFlux configuration steps.

### 5.3 Anonymous Access

Plugins can mark controller classes or handler methods for anonymous (unauthenticated) access using the `@AllowAnonymous` annotation — similar to .NET Core's `[AllowAnonymous]`. The framework automatically scans the annotation, registers the paths into a `PluginAnonymousPathRegistry` bean, and the host application queries it from its Spring Security configuration.

#### Plugin Usage

`@AllowAnonymous` can be placed on a **class** (all methods in the controller become anonymous) or on individual **methods**. Method-level takes precedence over class-level. The optional `reason` field helps with operational auditing.

```java
package gj.module.sso.controllers;

import gj.pf4j.anonymous.AllowAnonymous;
import org.springframework.web.bind.annotation.*;

// Class-level: every method in this controller is anonymous
@AllowAnonymous(reason = "SSO endpoints invoked by third-party identity provider — no session available")
@RestController
@RequestMapping("/api/v3/sso")
public class SsoCallbackController {

    @PostMapping("/login")
    public Result handleLogin(@RequestBody SsoLoginRequest req) {
        return ssoService.processLogin(req);
    }

    @PostMapping("/logout")
    public Result handleLogout(@RequestBody SsoLogoutRequest req) {
        return ssoService.processLogout(req);
    }
}

// Method-level: only selected endpoints are anonymous
@RestController
@RequestMapping("/api/v3/user")
public class UserController {

    // Anonymous: SSO callback invoked by identity provider
    @AllowAnonymous
    @PostMapping("/sso/callback")
    public Result handleSsoCallback(@RequestBody SsoCallbackRequest req) {
        return ssoService.handleCallback(req);
    }

    // Authenticated: requires login
    @GetMapping("/{id}")
    public Result getUser(@PathVariable String id) {
        return userService.getById(id);
    }
}
```

**Granularity:** The framework matches by **HTTP method + URL pattern**. `POST /api/v3/user/sso/callback` can be anonymous while `GET /api/v3/user/{id}` requires authentication — even though they share the same controller, the different HTTP methods and paths are treated independently.

#### Host Application Integration

The framework registers a `PluginAnonymousPathRegistry` bean in the main application context. The host app injects it and calls `registry.isAnonymous(requestPath, httpMethod)` from its security configuration.

**Spring MVC (Servlet):**

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final PluginAnonymousPathRegistry anonymousPathRegistry;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // Plugin anonymous endpoints — dynamic matching
            .requestMatchers(req ->
                anonymousPathRegistry.isAnonymous(
                    req.getRequestURI(), req.getMethod())
            ).permitAll()
            // Authenticated paths
            .requestMatchers(regex("/api/.*")).authenticated()
            .requestMatchers(regex("/iot/api/.*")).authenticated()
            .requestMatchers("/**").permitAll()
        );
        return http.build();
    }
}
```

**Spring WebFlux (Reactive):**

```java
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final PluginAnonymousPathRegistry anonymousPathRegistry;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http.authorizeExchange(exchanges -> exchanges
            .pathMatchers("/api/**").access((authentication, context) -> {
                ServerWebExchange exchange = context.getExchange();
                if (anonymousPathRegistry.isAnonymous(
                        exchange.getRequest().getURI().getPath(),
                        exchange.getRequest().getMethod().name())) {
                    return Mono.just(new AuthorizationDecision(true));
                }
                return exchange.getRequiredAuthentication();
            })
            .anyExchange().authenticated()
        );
        return http.build();
    }
}
```

For operational visibility, inject the registry and call `listAll()`, `listByPlugin(pluginId)`, or `getCount()` to expose the anonymous endpoint inventory through REST or JMX endpoints.

---

## 6. Data Access

The framework supports dual ORM — [MyBatis-Plus](https://baomidou.com/) (always active with `DataSource`) and JPA/Hibernate (host-opt-in via `hibernate-core`). Both share the host's `DataSource` and coexist within the same plugin under a single `@Primary` transaction manager.

### 6.1 MyBatis-Plus Data Access

#### 6.1.1 DAO Package Convention

Mapper interfaces must reside in the `{pluginId}.dao` package (dots replace hyphens). For example, `plugin.id = gj.module.user` → DAO package is `gj.module.user.dao`.

#### 6.1.2 Entity Class

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

#### 6.1.3 Mapper Interface

```java
package gj.module.user.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gj.module.user.model.User;

public interface UserMapper extends BaseMapper<User> {
}
```

#### 6.1.4 Service Implementation

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

#### 6.1.5 Data Layer Isolation

`GJPluginMybatisSqlSessionManager` creates for each plugin:

- A dedicated `SqlSessionFactory` (camelCase mapping, no cache, no lazy loading)
- A dedicated `SqlSessionTemplate` (cached for reuse)
- A `MapperScannerConfigurer` scoped to the plugin's DAO package only

All plugins share the main application's `DataSource`. Resources are cleaned up automatically when a plugin stops.

> **Performance:** `SqlSessionFactory` creation reuses a shared internal component across plugins, eliminating redundant initialization overhead that previously scaled linearly with the number of plugins.

### 6.2 JPA Data Access

Powered by Hibernate, providing the JPA (Jakarta Persistence API) programming model. Each plugin gets its own isolated `EntityManagerFactory` and `JpaRepository` scanning. MyBatis-Plus and JPA work under the same `@Primary` transaction manager — `@Transactional` just works across both ORMs without qualifiers.

**Prerequisite:** The host application must add `hibernate-core` to its `pom.xml`. Without it, no JPA beans are created — zero impact.

```xml
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-core</artifactId>
    <version>${hibernate.version}</version>
</dependency>
```

#### 6.2.1 Package Convention

| Purpose | Package | Example |
|---------|---------|---------|
| JPA entities | `{pluginId}.entity` | `gj.module.user.entity` |
| JPA repositories | `{pluginId}.repository` | `gj.module.user.repository` |

These are **directory-level** packages, not single files — any number of entity classes and repository interfaces can reside under them. The framework auto-infers the package path from `plugin.id` (dots replace hyphens). No additional configuration needed.

```
gj/module/user/
├── entity/
│   ├── UserEntity.java          ← scanned by Hibernate
│   ├── RoleEntity.java          ← scanned by Hibernate
│   └── PermissionEntity.java    ← scanned by Hibernate
├── repository/
│   ├── UserRepository.java      ← auto-registered as JpaRepository bean
│   ├── RoleRepository.java      ← auto-registered as JpaRepository bean
│   └── PermissionRepository.java ← auto-registered as JpaRepository bean
└── ...
```

#### 6.2.2 Entity Class

```java
package gj.module.user.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "user")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "description")
    private String description;
}
```

#### 6.2.3 Repository Interface

```java
package gj.module.user.repository;

import gj.module.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
}
```

#### 6.2.4 Service Implementation

```java
package gj.module.user.serviceimpl;

import gj.module.user.entity.UserEntity;
import gj.module.user.repository.UserRepository;
import gj.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<UserEntity> getList() {
        return userRepository.findAll();
    }
}
```

> **Note:** `@Transactional` works seamlessly with both MyBatis and JPA without any qualifier.

#### 6.2.5 Data Layer Isolation

`GJPluginJpaEntityManagerManager` creates for each plugin:

- A dedicated `LocalContainerEntityManagerFactoryBean` (persistence unit per plugin, `@Primary`)
- A dedicated `JpaTransactionManager` (shared with MyBatis when both ORMs coexist)
- Auto-scanned `JpaRepositoryFactoryBean` for each interface in the repository package
- A `PersistenceExceptionTranslationPostProcessor` for Spring exception translation

When JPA is active, MyBatis reuses the same `JpaTransactionManager` — no separate `DataSourceTransactionManager` is created. All plugins share the host's `DataSource`. `EntityManagerFactory.close()` is called on plugin stop to release Hibernate resources.

**Host controls activation:** If the host does not include `hibernate-core`, no JPA beans are created — zero runtime overhead.

### 6.3 SQL Keyword Quoting

The framework includes a MyBatis-Plus `InnerInterceptor` that automatically detects the database type at runtime and wraps column names with the correct quote character when they conflict with reserved keywords (e.g., `order`, `comment`, `context`). For JPA/Hibernate, keyword quoting is handled by the Hibernate dialect or `hibernate.auto_quote_keyword` configuration.

**Quote character by database:**

| Database | Quote |
|----------|-------|
| MySQL | `` ` `` (backtick) |
| DM / PostgreSQL / GaussDB / KingbaseES / SQLite / Oracle | `"` (double quote) |

**Extension point:** `GJTableKeywordProvider` — both the host application and plugins implement this interface and register as Spring Beans to declare table-column mappings that may conflict with database keywords.

**Host application example:**

```java
package com.example.config;

import gj.pf4j.mybatis.interceptor.GJTableKeywordProvider;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class AppKeywords implements GJTableKeywordProvider {
    @Override
    public Map<String, Set<String>> getTableKeywords() {
        return Map.of(
            "el-t1",   Set.of("order"),
            "el-t2", Set.of("comment")
        );
    }
}
```

**Plugin example:**

```java
package gj.module.user.keyword;

import gj.pf4j.mybatis.interceptor.GJTableKeywordProvider;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class UserKeywords implements GJTableKeywordProvider {
    @Override
    public Map<String, Set<String>> getTableKeywords() {
        return Map.of(
            "user_table", Set.of("level", "comment", "type")
        );
    }
}
```

Host app providers are auto-scanned at startup; plugin providers are auto-scanned after the plugin context is refreshed. Table and column names are case-insensitive.

---

## 7. Database Auto-Migration

The framework provides automatic database schema migration for plugin entities. When a plugin starts, `@TableName` or `@Entity` entities are automatically scanned and compared against the current database schema. Missing tables and columns are created automatically — no manual SQL migration scripts required.

### 7.1 Supported Databases

The migration engine supports **7 databases** with automatic dialect detection via JDBC connection metadata:

| Database | Detection |
|----------|-----------|
| MySQL | JDBC URL or product name |
| PostgreSQL | JDBC URL or product name |
| GaussDB / openGauss | JDBC URL or product name |
| KingbaseES | JDBC URL or product name |
| DM (Dameng) | JDBC URL or product name |
| SQLite | JDBC URL or product name |
| Oracle | JDBC URL or product name |

No additional configuration is needed — the dialect (identifier quoting, type mapping, DDL rendering) is resolved automatically from the `DataSource` connection.

### 7.2 Production Safety

The migration engine follows a **strict additive-only policy**. Only two DDL operations are ever generated:

| Operation | Condition |
|-----------|-----------|
| **CREATE TABLE** | Table does not exist in the database |
| **ALTER TABLE ADD COLUMN** | Column does not exist in the target table |

No `DROP TABLE`, `DROP COLUMN`, `ALTER COLUMN`, `RENAME`, or any other destructive DDL is ever produced. Existing tables, columns, and data are never modified. This makes auto-migration safe for production use.

### 7.3 Plugin Auto-Migration

Plugins require **zero configuration** for migration. Any `@TableName` or `@Entity` entity class placed under the plugin's package is automatically scanned during plugin startup. The framework compares the entity model against the live database schema and executes any necessary `CREATE TABLE` or `ADD COLUMN` statements.

Migration is triggered automatically when:

- A **new plugin with new entities** is deployed — tables are created
- An **existing plugin adds a new field** to an entity — the column is added
- An **existing plugin adds a new entity** — the table is created

Migration is transparently disabled (zero overhead) when no `GJPluginModelMigrator` bean exists in the main application context — i.e., when `@EnableGJMigration` is not used.

### 7.4 Share Model Migration

The host application can also migrate its own shared model entities (e.g., `User`, `Menu`, `Role` — common entities shared across all plugins). Use the `@EnableGJMigration` annotation with `basePackages` to specify the shared model package paths:

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

**Execution guarantees:**

- **Priority**: Share models are migrated **before any plugin** — the framework guarantees shared tables exist before plugins reference them
- **Once per JVM lifecycle**: Share model migration runs exactly once, regardless of how many plugins are loaded or restarted

> **Note:** Without `@EnableGJMigration`, the host application does not create a `GJPluginModelMigrator` bean, and the entire migration subsystem is inactive. Add the annotation only when you need auto-migration.

---

## 8. Object Mapping

Powered by the open-source library [ModelMapper](https://modelmapper.org/).

### 8.1 Mapping Configuration Class

Implement `GJPluginModelMapperConfig` in your plugin and annotate it with `@Component` to register type mapping rules:

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
            // Simple mapping: same-name fields auto-mapped
            GJPluginTypeMapConfig.of(User.class, UserDTO.class),

            // Custom mapping: explicit field mapping rules
            GJPluginTypeMapConfig.of(User.class, UserResponse.class, typeMap -> {
                typeMap.addMapping(User::getId, UserResponse::setId);
                typeMap.addMapping(User::getName, UserResponse::setUserName);
                typeMap.addMapping(User::getEmail, UserResponse::setEmailAddress);
            })
        );
    }
}
```

### 8.2 Using ModelMapper

The framework builds and registers a `ModelMapper` bean automatically. Inject it directly:

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

### 8.3 Mapping Registration Mechanism

- On plugin start, `GJPluginLifecycleManager` listens for `GJPluginStartedEvent`, scans all `GJPluginModelMapperConfig` beans from the plugin context
- All `GJPluginTypeMapConfig` entries are merged: if the host application already has a `ModelMapper`, mappings are appended to the shared instance; otherwise the framework creates one
- If a `TypeMap` already exists for a source/destination pair, the `merge` strategy is used (append, not replace)

---

## 9. Plugin Configuration Management

### 9.1 Configuration Class

Use `@ConfigurationProperties` to bind plugin-specific configuration:

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

### 9.2 Configuration File

Provide values in `src/main/resources/{pluginId}.properties`:

```properties
gj.module.user.enabled=true
gj.module.user.value=iot
gj.module.user.max-retry=5
gj.module.user.api-url=https://api.example.com
```

### 9.3 Injection and Usage

Any Spring bean in the plugin can inject the configuration class:

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

### 9.4 Configuration Source Priority

The framework loads configuration with the following priority:
1. Plugin container internal environment variables
2. `{pluginId}.properties` file (loaded by `GJPluginLifecycle.registerResource()` into PropertySource)
3. Main application environment variables (inherited from parent context as fallback)

---

## 10. Real-Time Communication

Powered by [netty-socketio](https://github.com/mrniko/netty-socketio). The server-side API design is inspired by the **ASP.NET Core SignalR Hub** pattern — extend `GJHub`, annotate methods with `@GJHubMethod`, and use `getClients().group().sendAsync()` for targeted message delivery. The underlying wire protocol is **Socket.IO**.

### 10.0 Client Integration

Clients must use the **Socket.IO** client library (`socket.io-client`), **not** the SignalR client.

```html
<script src="https://cdn.socket.io/4.x/socket.io.min.js"></script>
```

```js
const socket = io('http://localhost:9600/socket.io/', {
    query: { hub: 'userHub', userName: 'zhangsan' },
    transports: ['websocket']
});
```

**Connection parameters:**

| Parameter | Required | Description |
|---|---|---|
| `hub` | Yes | Hub name (matches the string passed to `GJHub` constructor) |
| `userName` | Yes† | User identifier; also used by nginx for sticky session routing in cluster mode |

† Not required in `dev`/`debug` profile (defaults to `"test"`).

**Sending messages to the server:**

All client-to-server messages are sent via a single Socket.IO event named `invoke`, with a JSON payload containing the target `method` name and `data`:

```js
socket.emit('invoke', {
    method: 'sendMessage',       // matches @GJHubMethod("sendMessage")
    data: { content: 'hello' }   // method argument
});
```

**Receiving messages from the server:**

Listen on the method name used by the server — `hubManager.sendMessage(..., "newMessage", data)` or `getClients().all().sendAsync("newMessage", data)` maps to:

```js
socket.on('newMessage', (msg) => {
    console.log(msg.data);       // the business payload
    console.log(msg.success);    // always true for data messages
});
```

### 10.1 Creating a Hub

Extend `GJHub` and use `@GJHubMethod` to annotate message handler methods:

```java
package gj.module.user.socketio;

import gj.pf4j.socketio.GJHub;
import gj.pf4j.socketio.GJHubMethod;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class UserHub extends GJHub {

    public UserHub() {
        super("userHub");  // hubName — clients route messages by this name
    }

    /**
     * Called when a client connects
     */
    @Override
    public CompletableFuture<Void> onConnectedAsync() {
        return CompletableFuture.runAsync(() -> {
            String connectionId = getContext().getConnectionId();
            System.out.println("User connected: (" + connectionId + ")");
        });
    }

    /**
     * Called when a client disconnects
     */
    @Override
    public CompletableFuture<Void> onDisconnectedAsync() {
        return CompletableFuture.runAsync(() -> {
            System.out.println("disconnected");
        });
    }

    /**
     * Handle the "sendMessage" event from clients
     */
    @GJHubMethod("sendMessage")
    public void onSendMessage(MessageData data) {
        // Broadcast to all clients except the sender
        getClients().others().sendAsync("newMessage", data);

        // Send to a specific group
        getClients().group("admin").sendAsync("newMessage", data);
    }

    /**
     * Handle the "joinGroup" event from clients
     */
    @GJHubMethod("joinGroup")
    public void onJoinGroup(String groupName) {
        getGroups().addToGroupAsync(groupName);
    }
}
```

### 10.2 Client Push API

`getClients()` returns a `GJHubCallerClients` with the following targeting methods:

```java
// All connected clients
getClients().all().sendAsync("eventName", data);

// Only the caller
getClients().caller().sendAsync("eventName", data);

// Everyone except the caller
getClients().others().sendAsync("eventName", data);

// A specific connection
getClients().client("connectionId123").sendAsync("eventName", data);

// A specific group
getClients().group("admin").sendAsync("eventName", data);

// A specific user (by userId)
getClients().user("userId123").sendAsync("eventName", data);

// A group excluding a specific user
getClients().groupExceptUser("admin", "excludedUserId").sendAsync("eventName", data);

// All except certain connections
getClients().allExcept(List.of("connId1", "connId2")).sendAsync("eventName", data);
```

### 10.3 Group Management API

`getGroups()` returns a `GJGroupManager`:

```java
// Join a group
getGroups().addToGroupAsync("groupName");

// Leave a group
getGroups().removeFromGroupAsync("groupName");

// Check group membership
getGroups().isInGroupAsync("groupName").thenAccept(inGroup -> {
    System.out.println("In group: " + inGroup);
});

// Get all groups for the current connection
getGroups().getGroupsForConnectionAsync().thenAccept(groups -> {
    System.out.println("My groups: " + groups);
});

// Get all connection IDs in a group
getGroups().getConnectionsInGroupAsync("groupName").thenAccept(connections -> {
    System.out.println("Connections in group: " + connections);
});
```

### 10.4 Hub Context

Retrieve current connection information inside hub methods via `getContext()`:

```java
GJHubCallerContext ctx = getContext();
String connectionId = ctx.getConnectionId();
Map<String, String> queryParams = ctx.getQueryParams();
```

> Frontend can pass custom parameters via the connection URL (e.g., `?hub=userHub&userName=123`). Access them in the Hub via `ctx.getQueryParam("key")`. Avoid passing plaintext sensitive information in the URL.

### 10.5 Server-Side Configuration

Configure the Socket.IO server in the host application's configuration file as needed, for example:

```properties
socketio.port=9600
socketio.maxConnectionsPerSecond=10
```

See `GJSocketIOConfig` source for all available properties and their defaults.

### 10.6 Cluster Mode (Distributed Deployment)

gj.spring.pf4j supports multi-node horizontal scaling via Redis-backed shared state. When cluster mode is enabled, all connection, group, and user mappings are synchronized to Redis, and cross-node messages are delivered through Redis Pub/Sub.

**Default:** Cluster mode is **disabled**. The framework operates in single-node mode with all state in local JVM memory — zero external dependencies.

#### Prerequisites

- **Nginx** with sticky sessions (see configuration below)
- **Redis** accessible by all nodes (shared via the host application's `RedisConnectionFactory`)

The host application must include `spring-boot-starter-data-redis`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

The framework reuses the host's `RedisTemplate` and `RedisMessageListenerContainer` via Spring bean auto-detection. If the host has no Redis beans, cluster beans are not created — the framework silently degrades to single-node mode.

#### Enabling Cluster Mode

```yaml
socketio:
  cluster:
    enabled: true
  node-id: ${HOSTNAME:}       # leave empty for auto-detection (hostname:PID)
  connection-ttl: 3600         # seconds, Redis key TTL for connection mappings
```

| Property | Default | Description |
|---|---|---|
| `socketio.cluster.enabled` | `false` | Enable cross-node cluster support |
| `socketio.node-id` | (auto) | Node identifier. Auto-detected from `HOSTNAME` env var, falls back to `host:PID` |
| `socketio.connection-ttl` | `3600` | Redis TTL for connection ownership keys; also serves as ultimate fallback cleanup for stale entries |

#### Nginx Configuration

Sticky sessions based on `userName` URL parameter using consistent hashing:

```nginx
upstream socketio_backend {
    hash $arg_userName consistent;
    server node1:9092;
    server node2:9092;
}

server {
    location /socket.io/ {
        proxy_pass http://socketio_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

`consistent` uses a consistent hash ring — when nodes are added or removed, only a small fraction of users are re-routed.

#### Architecture

```
┌──────────────┐     ┌──────────────┐
│   Node A     │     │   Node B     │
│ GJHubManager │     │ GJHubManager │
│   local Maps │     │   local Maps │
└──────┬───────┘     └──────┬───────┘
       │                    │
       └────────┬───────────┘
                │
         ┌──────┴──────┐
         │    Redis     │
         │  shared      │
         │  state +     │
         │  Pub/Sub     │
         └─────────────┘
```

- **Local connections** are tracked in local `ConcurrentHashMap` (as in single-node mode) and synchronized to Redis
- **Message delivery** is local-first: the framework checks the local client registry before falling back to Redis Pub/Sub for remote delivery
- **Node heartbeat**: each node refreshes a Redis key every 30s (TTL 45s); a surviving node's cleanup scheduler detects missing heartbeats and cleans up stale entries from failed nodes
- **Graceful degradation**: if Redis is unreachable, the framework continues in local-only mode

#### Redis Data Model

| Redis Key | Type | Content |
|---|---|---|
| `socketio:conn:{connectionId}` | String | Owning node ID, TTL = `connection-ttl` |
| `socketio:conn:{connectionId}:groups` | Set | Groups the connection belongs to |
| `socketio:group:{groupName}` | Set | Connection IDs in the group |
| `socketio:user:{userId}` | Set | Connection IDs for the user |
| `socketio:node:{nodeId}:connections` | Set | All connection IDs on this node |
| `socketio:node:{nodeId}:heartbeat` | String | Heartbeat timestamp, TTL = 45s |

#### Impact on Plugin Code

None. Hub implementations (`extends GJHub`) are cluster-unaware — the same `getClients().group(...).sendAsync()` API works identically in single-node and cluster mode. The underlying `GJHubManager` transparently handles local vs. remote routing.

---

## 11. Internationalization (i18n)

### 11.1 Plugin i18n Files

Place `i18n/messages*.properties` in the plugin classpath:

```
src/main/resources/
  i18n/
    messages.properties          # Default
    messages_zh_CN.properties    # Simplified Chinese
    messages_en_US.properties    # English
```

Example `i18n/messages_en_US.properties`:

```properties
user.list.title=User List
user.create.success=Created Successfully
user.delete.confirm=Confirm to delete this user?
```

Example `i18n/messages_zh_CN.properties`:

```properties
user.list.title=用户列表
user.create.success=创建成功
user.delete.confirm=确认删除该用户？
```

### 11.2 Injection and Usage

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

### 11.3 Fallback Mechanism

- The framework creates a `GJPluginReloadableMessageSource` for each plugin (bean name: `plugin_i18n_{pluginId}`)
- Key lookup: plugin's own messages first, then falls back to the main application's `messageSource`
- If no match is found, returns the key itself (`useCodeAsDefaultMessage = true`)
- 24-hour cache, UTF-8 encoding

---

## 12. Import/Export

Built on [EasyExcel](https://easyexcel.opensource.alibaba.com/), provides `IImportManager` and `IExportManager` interfaces with multi-sheet read/write and automatic i18n header translation.

### 12.1 Export Example

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
     * Single-sheet export
     */
    public String exportUsers() throws IOException {
        List<User> users = userMapper.selectList(null);
        return exportManager.exportToXlsx(users);
    }

    /**
     * Multi-sheet export
     */
    public String exportMultiSheet() throws IOException {
        Map<String, List<?>> sheets = new LinkedHashMap<>();
        sheets.put("Users", userMapper.selectList(null));
        sheets.put("Roles", roleMapper.selectList(null));
        return exportManager.exportMultiSheetToXlsx(sheets);
    }

    /**
     * Export to byte stream (for HTTP download responses)
     */
    public ByteArrayOutputStream exportToStream() throws IOException {
        List<User> users = userMapper.selectList(null);
        return exportManager.exportToStream(users);
    }
}
```

### 12.2 Import Example

```java
@Service
public class UserImportService {

    private final IImportManager importManager;

    public UserImportService(IImportManager importManager) {
        this.importManager = importManager;
    }

    /**
     * Multi-sheet import
     */
    public void importUsers(InputStream inputStream) {
        List<List<Object>> sheets = importManager.importFromXlsx(
                "users.xlsx",
                inputStream,
                User.class,    // Sheet 0 → User entity
                Role.class     // Sheet 1 → Role entity
        );

        List<Object> userRows = sheets.get(0);  // User sheet
        List<Object> roleRows = sheets.get(1);  // Role sheet

        // Process imported data...
    }
}
```

### 12.3 Header i18n

EasyExcel `@ExcelProperty` annotation values are automatically translated via i18n during both import and export. The framework overrides `SimpleWriteHandler` and `ReadEventListener` to ensure generated and parsed Excel headers match the current locale.

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

## 13. Scheduled Tasks

Powered by [Quartz](https://www.quartz-scheduler.org/). Plugins simply implement the `IPluginJob` interface and annotate it with `@PluginJob` — the framework automatically scans and registers them with the Quartz scheduler after the plugin starts.

### 13.1 Dependency Note

The framework includes built-in Quartz support (`org.quartz-scheduler:quartz`) and auto-creates a `Scheduler` bean via `GJQuartzConfig` (`@ConditionalOnMissingBean`). The host application does not need to add any Quartz dependency. If the host app already has a custom `Scheduler` bean, the framework reuses it automatically.

### 13.2 Creating a Scheduled Job

Create a bean implementing `IPluginJob` in your plugin and annotate it with `@PluginJob`:

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
        // business logic
    }
}
```

### 13.3 @PluginJob Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `name` | String | **required** | Globally unique job identifier |
| `intervalSeconds` | long | -1 | Fixed interval in seconds; mutually exclusive with `cronExpression` |
| `cronExpression` | String | "" | Cron expression; mutually exclusive with `intervalSeconds` |
| `runOnce` | boolean | false | Execute only once |
| `disallowConcurrentExecution` | boolean | true | Disallow concurrent execution of the same job |

### 13.4 Cron Expression Examples

```java
@PluginJob(name = "dailyReport", cronExpression = "0 0 8 * * ?")       // Every day at 8:00
@PluginJob(name = "weeklySync", cronExpression = "0 0 2 ? * MON")       // Every Monday at 2:00
@PluginJob(name = "initData", runOnce = true)                            // Run once on startup
```

### 13.5 Manual Trigger (Injecting Scheduler)

For scenarios requiring manual trigger in business logic, inject the Quartz `Scheduler` directly:

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

## 14. In-Process Event Bus

The framework provides a lightweight in-process event bus for decoupled inter-plugin communication. Listeners implement `GJPluginLocalEventListener<T>` to handle typed events, while event classes use `@EventName` for Ant-style wildcard pattern matching.

### 14.1 Defining Events

Annotate event classes with `@EventName`; name components are dot-separated:

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

### 14.2 Creating Listeners

Implement `GJPluginLocalEventListener<T>` and annotate with `@Component` to register as a Spring bean:

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
        // send welcome email, initialize user data, etc.
    }
}
```

### 14.3 Publishing Events

Inject `GJPluginLocalEventBus` into any Spring bean:

```java
@Service
public class UserService {

    private final GJPluginLocalEventBus eventBus;

    public UserService(GJPluginLocalEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void createUser(String name) {
        // create user logic ...

        // Synchronous — all listeners run on the current thread
        eventBus.publish(new UserCreatedEvent(userId, name));

        // Asynchronous — listeners execute in the thread pool
        eventBus.publishAsync(new UserCreatedEvent(userId, name));
    }
}
```

### 14.4 Wildcard Matching

`@EventName` supports Ant-style wildcards with `.` as the path separator:

```java
@EventName("user.*")           // matches user.created, user.updated, etc.
@EventName("order.cancelled")   // exact match
```

Multiple listeners can match a single event; each listener executes independently.

---


## 15. JSON Serialization — ObjectMapper

Each plugin receives a **fully isolated `ObjectMapper`** instance. The framework copies the host application's `ObjectMapper` and registers it as the `objectMapper` bean in every plugin's Spring context. This guarantees:

- **Serialization/deserialization runs in the plugin's ClassLoader** — no plugin classes leak into the host `ObjectMapper` caches
- **Plugin unload is GC-safe** — when a plugin context is closed, its `ObjectMapper` instance loses all references. Jackson internal caches (`TypeFactory`, `SerializerCache`) are recycled together with the plugin classloader
- **Zero configuration** — plugins simply inject use constructor injection (`@RequiredArgsConstructor`). No static singletons, no manual setup

```java
// Plugin-side usage — inject the isolated ObjectMapper
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {
    private final ObjectMapper objectMapper;
    // ...
}
```

---

## 16. OpenAPI Documentation

### 16.1 Automatic Grouping

The framework automatically creates an independent `GroupedOpenApi` bean (SpringDoc) for each plugin that registers controllers. The group name follows the pattern `pluginGroupedOpenApi-{pluginId}`. In Swagger-UI, select the desired plugin from the top-right dropdown to view its API documentation.

### 16.2 Controller Example (with Swagger Annotations)

```java
@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "User Management", description = "User CRUD operations")
public class UserController {

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public UserResponse getById(
            @Parameter(description = "User ID") @PathVariable Integer id) {
        return userService.getById(id);
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new user")
    public boolean create(
            @Parameter(description = "Create request") @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }
}
```

### 16.3 Access URL

Visit `http://localhost:{port}/swagger-ui/index.html` after startup.

---

## 17. Plugin Packaging & Deployment

### 16.1 Build the Plugin

```bash
cd user-plugin
mvn clean package
```

### 16.2 Output Directory Structure

After a successful build, `target/plugins/{artifactId}/` contains:

```
target/plugins/gj.module.user/
├── gj.module.user-1.0.0-SNAPSHOT.jar     # Plugin main JAR
├── gj.module.user.json                   # Plugin descriptor file
└── lib/                                   # Plugin-private third-party dependency JARs
    ├── some-third-party.jar
    └── ...
```

### 16.3 MANIFEST.MF

```manifest
Plugin-Id: gj.module.user
Plugin-Version: 1.0.0-SNAPSHOT
Class-Path: lib/some-third-party.jar lib/another-lib.jar
```

### 16.4 Deploy to the Host Application

Copy the entire `target/plugins/gj.module.user/` directory into the host application's `plugins/` directory:

```
Host application root/       ← current working directory in dev/debug mode
  plugins/
    gj.module.user/
      gj.module.user-1.0.0-SNAPSHOT.jar
      gj.module.user.json
      lib/
        ...
    gj.module.other/
      ...
```

In production (non-dev/debug profiles), the plugin directory is located at `plugins/` under the Spring Boot JAR's `ApplicationHome` directory.

### 16.5 Version Management

`GJJarPluginRepository` scans each plugin directory, parses version numbers from JAR filenames (format: `{pluginId}-{version}.jar`), and loads the latest version. When multiple versions exist in a directory, only the highest version is loaded, with a log entry recording the selection.

---

## 18. Runtime Plugin Management API

### 18.1 Injecting GJPluginService

```java
@RestController
@RequestMapping("/api/admin/plugins")
public class PluginAdminController {

    private final GJPluginService pluginService;

    public PluginAdminController(GJPluginService pluginService) {
        this.pluginService = pluginService;
    }

    // ... management endpoints
}
```

### 18.2 Load and Start All Plugins

```java
@PostMapping("/load-all")
public void loadAndStartAll() {
    pluginService.loadAndStartAllPlugins();
}
```

### 18.3 Install a Plugin

Loads a JAR from `plugins/{pluginId}/` and starts it. Returns `PluginState.STARTED` on success.

```java
@PostMapping("/{pluginId}/install")
public String installPlugin(@PathVariable String pluginId) {
    PluginState state = pluginService.installPlugin(pluginId);
    return "Plugin " + pluginId + " state: " + state;
}
```

### 18.4 Disable a Plugin

Stops the plugin and sets `PluginState.DISABLED`. The plugin stays in the registry and can be restarted later. Disabled plugins are skipped during bulk startup.

```java
@PostMapping("/{pluginId}/disable")
public ResponseEntity<String> disablePlugin(@PathVariable String pluginId) {
    pluginService.disablePlugin(pluginId);
    return ResponseEntity.ok("Plugin " + pluginId + " disabled");
}
```

### 18.5 Restart a Plugin

Restarts a plugin in-place (same ClassLoader). Supports both `STARTED` and `DISABLED` states — a disabled plugin is re-started without an intermediate stop.

```java
@PostMapping("/{pluginId}/restart")
public String restartPlugin(@PathVariable String pluginId) {
    PluginState state = pluginService.restartPlugin(pluginId);
    return "Plugin " + pluginId + " state: " + state;
}
```

### 18.6 Unload / Delete a Plugin

- `unloadPlugin(id)` — stops, closes ClassLoader, removes from registry. Files on disk are preserved.
- `deletePlugin(id)` — same as unload, plus deletes the `plugins/{id}/` directory.

```java
// Unload (keep files)
@DeleteMapping("/{pluginId}/unload")
public String unloadPlugin(@PathVariable String pluginId) {
    return pluginService.unloadPlugin(pluginId) ? "Unloaded" : "Failed";
}

// Delete (remove files)
@DeleteMapping("/{pluginId}")
public String deletePlugin(@PathVariable String pluginId) {
    return pluginService.deletePlugin(pluginId) ? "Deleted" : "Failed";
}
```

### 18.7 Reload All Plugins

```java
@PostMapping("/reload-all")
public void reloadAll() {
    pluginService.reloadAll();  // stop all → unload all → load all → start all
}
```

> For hot-reload workflows, lifecycle events, file watcher mode, and app-store integration patterns, see **[§19 Plugin Hot-Reload](#19-plugin-hot-reload)**.

---

## 19. Plugin Hot-Reload

### 19.1 Concept & Configuration

Hot-reload updates a plugin to a new version at runtime without restarting the host application. Two modes are available:

```
gj.plugin.hot-reload=watch   (default)
gj.plugin.hot-reload=manual
```

| Property | Default | Description |
|----------|---------|-------------|
| `gj.plugin.hot-reload` | `watch` | Hot-reload mode. `watch` — auto-detect JAR changes; `manual` — API-driven only. |
| `gj.plugin.dir` | (auto) | Plugin directory path. When set, uses the exact path and fails if it does not exist. When unset: `./plugins` in dev/debug profile, `<appHome>/plugins` otherwise. |

### 19.2 manual Mode — API-Driven Workflow

```
1. unloadPlugin(id)        // Remove from memory, keep files on disk
2. Replace plugins/{id}/*.jar
3. installPlugin(id)       // Load new JAR + start
```

Each step returns a result that can be validated by the caller.

### 19.3 manual Mode — App Store Integration

An app store or orchestrator uses the two-step workflow with custom logic:

```
1. Download JAR to staging area
2. Validate SHA256 / signature
3. Backup plugins/{id}/*.jar
4. unloadPlugin(id)           ← caller controls unload timing
5. Replace plugins/{id}/*.jar
6. installPlugin(id)          ← caller controls install timing
7. Check startup result
8. On failure → rollback: restore old JAR + installPlugin(id)
```

### 19.4 manual Mode — Multi-Node Grayscale

```
1. Place new JAR on all nodes (does not trigger reload)
2. For each node sequentially: unload(id) + install(id)
3. Observe metrics after each node
4. Halt rollout on failure; roll back affected nodes
```

### 19.5 watch Mode — File Watcher

A single daemon thread monitors `plugins/` via `WatchService`. A 2s debounce merges rapid file events (e.g., copy-in-progress). After debounce expiration:

- **Existing plugin** (JAR create/modify) → `unloadPlugin(id)` + `installPlugin(id)`
- **New plugin directory** (create) → registers a per-plugin WatchKey + starts debounce
- **Deleted JAR** → debounce then check for remaining JARs; unload if none found

### 19.6 Lifecycle Events

`GJPluginBeforeUnloadEvent` and `GJPluginAfterInstallEvent` are published during hot-reload. Both the **host application** and the **plugin being reloaded** can subscribe via `@EventListener`. Other plugins are unaffected — only the target plugin and the host receive these events.

`GJPluginBeforeUnloadEvent` supports veto: throw `PluginHotReloadVetoException` from any listener to abort the unload.

### 19.7 Event Subscription Example

```java
// Plugin side — close custom port before unload
@Component
public class CleanupListener {
    @EventListener
    public void onBeforeUnload(GJPluginBeforeUnloadEvent e) {
        customNettyServer.shutdown();
    }
}

// Host side — refresh cache after install
@Component
public class HotReloadMonitor {
    @EventListener
    public void onAfterInstall(GJPluginAfterInstallEvent e) {
        cacheManager.invalidateByPlugin(e.getPluginId());
    }
}
```

---

## 20. Appendix: Host Application Integration

### 20.1 Version Compatibility

gj-pf4j depends on Spring core libraries (spring-webmvc, spring-beans, spring-jdbc, etc.) but does **not lock version numbers**. The framework publishes a `gj-dependencies` BOM for unified version management. By importing both gj BOM and Spring Boot BOM with the correct priority order, Spring versions automatically follow the developer's chosen Spring Boot version, avoiding conflicts.

### Importing via BOM (Recommended)

In `dependencyManagement`, import the gj BOM followed by the Spring Boot BOM — **Spring Boot BOM goes last for higher priority**, ensuring Spring versions follow the developer's chosen Spring Boot version. The gj BOM only covers dependencies not managed by Spring Boot (e.g., PF4J, netty-socketio):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.wangpengxpy</groupId>
            <artifactId>gj-dependencies</artifactId>
            <version>1.0.9</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Spring Boot BOM goes last — overrides gj BOM for overlapping keys -->
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

Then add gj-pf4j (version managed by gj BOM):

```xml
<dependencies>
    <dependency>
        <groupId>io.github.wangpengxpy</groupId>
        <artifactId>gj-pf4j</artifactId>
    </dependency>
</dependencies>
```

**Version Resolution:**

| Developer's Spring Boot | gj BOM Spring Version | Effective Version |
|---|---|---|
| 3.5.x | 3.5.5 | 3.5.x (SB BOM overrides) |
| 4.0.x | 3.5.5 | 4.0.x (SB BOM overrides) |

Dependencies in gj BOM that overlap with Spring Boot (spring-webmvc, spring-beans, etc.) are overridden by SB BOM. The gj BOM only governs dependencies not covered by SB BOM (pf4j, netty-socketio, modelmapper, etc.).

### Direct Dependency (Not Recommended)

You can also skip the BOM and depend on gj-pf4j directly, but you must ensure Spring version compatibility yourself:

```xml
<dependency>
    <groupId>io.github.wangpengxpy</groupId>
    <artifactId>gj-pf4j</artifactId>
    <version>1.5.0</version>
</dependency>
```

### 20.2 Host Application Entry Point

**Required:**

```java
@SpringBootApplication
@ComponentScan("gj")     // all framework beans reside under gj.pf4j — must be scanned
public class GJApplication {
    public static void main(String[] args) {
        SpringApplication.run(GJApplication.class, args);
    }
}
```

- The framework includes `GJPluginConfig` and `GJPluginWebFluxConfig`; both are auto-activated via `@ComponentScan("gj")`.
- Plugins under the `plugins/` directory are automatically loaded and started after the main application's `ContextRefreshedEvent` fires.

> For configuring shared ModelMapper mappings in the host app (base model packages), see [20.3](#203-optional-gjmodelmapperscan-shared-models).

### Spring MVC Mode (Default)

Spring Boot defaults to **MVC (Servlet) mode** — no extra configuration is needed. Simply include `spring-boot-starter-web` and `springdoc-openapi-starter-webmvc-ui`:

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

No need to set `WebApplicationType` (defaults to `SERVLET`):

```java
@SpringBootApplication
@ComponentScan("gj")
public class GJApplication {
    public static void main(String[] args) {
        SpringApplication.run(GJApplication.class, args);
    }
}
```

gj-pf4j automatically uses `GJPluginRequestMappingHandlerMapping` (MVC) — plugin `@RestController` routes are registered through the Servlet container.

### Spring WebFlux Mode

If the host application uses a WebFlux reactive architecture, **two steps** are required:

**1. Swap Dependencies**

```xml
<!-- Do NOT use spring-boot-starter-web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<!-- Replace webmvc-ui with webflux-ui -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
</dependency>
```

**2. Explicitly Set the Web Type**

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

Once gj-pf4j detects a `GJPluginWebFluxRequestMappingHandlerMapping` bean, it automatically switches to WebFlux mode for plugin controller route registration.

### Mode Comparison

| | MVC Mode (Default) | WebFlux Mode |
|---|---|---|
| Web Container | Tomcat (Servlet) | Netty (Reactive) |
| Dependency | `spring-boot-starter-web` | `spring-boot-starter-webflux` |
| SpringDoc | `springdoc-openapi-starter-webmvc-ui` | `springdoc-openapi-starter-webflux-ui` |
| WebApplicationType | Not set (defaults to SERVLET) | Explicit `.web(REACTIVE)` |
| Plugin Controller Code | `@RestController` | `@RestController` (identical) |
| Route Registration | `GJPluginRequestMappingHandlerMapping` | `GJPluginWebFluxRequestMappingHandlerMapping` |

### 20.3 Optional: `@GJModelMapperScan` (Shared Models)

When the host application has common base model packages (entities such as `User`, `Menu`, `Role`, plus their Mappers, DTOs, and ModelMapper mappings), add the `gj-modelmapper` artifact and use `@GJModelMapperScan` to register those mappings into a global `ModelMapper` bean. Business plugins inherit this shared instance via the parent context:

```
Host App
  ├─ DataSource
  ├─ SqlSessionFactory → UserMapper, MenuMapper, RoleMapper ...
  ├─ ModelMapper (User→UserDTO, Menu→MenuDTO) ← @GJModelMapperScan
  │
  └─ [parent context] ── Plugin (inherits)
       ├─ [inherits] ModelMapper — final ModelMapper mm → ready to use with all shared mappings
       ├─ [inherits] UserMapper — final UserMapper um → query shared tables directly
       ├─ [own] PluginMapper — query plugin-specific tables
       └─ [appends] plugin-specific mappings — added to the shared ModelMapper automatically, no duplication
```

Configuration:

```xml
<!-- host application pom.xml -->
<dependency>
    <groupId>io.github.wangpengxpy</groupId>
    <artifactId>gj-modelmapper</artifactId>
</dependency>
```

```java
@SpringBootApplication
@ComponentScan("gj")
@GJModelMapperScan(
    basePackages = "your.app.model",      // base model package
    markerInterface = GJModelMapperConfig.class
)
public class GJApplication { ... }
```

Model mapping config inside the base model package:

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

> **Key insight:** `@GJModelMapperScan` (host app) and plugin `GJPluginModelMapperConfig` are independent mechanisms. The former injects a global `ModelMapper` for the host; the latter is auto-discovered as a Spring bean by `GJPluginModelMapperRegistry` and appends mappings to the shared instance. If the host app does not configure `@GJModelMapperScan`, plugin ModelMapper still works — the framework creates one automatically.

---

## 21. Claude Code Integration

The framework ships with built-in [Claude Code](https://claude.ai/code) skills for AI-driven plugin development:

```bash
/gj-plugin-new "user management plugin with CRUD, real-time push, scheduled cleanup"
```

**New projects** (included automatically when generating from archetype):

```bash
mvn archetype:generate -DarchetypeGroupId=io.github.wangpengxpy -DarchetypeArtifactId=gj-archetype ...
```

**Existing projects**: copy from `tools/claude-skills/` into the project root:

```bash
git clone --depth 1 https://github.com/wangpengxpy/gj.spring.pf4j.git /tmp/gj-pf4j
cp -r /tmp/gj-pf4j/tools/claude-skills/* .claude/
```

> Internally uses OpenSpec for requirements analysis and task decomposition, then delegates to the `gj-plugin` skill for code generation.

---

## 22. FAQ

### Q1: Plugin startup fails with `plugin.id` mismatch error?

The `plugin.id` in `plugin.properties` must **exactly match** the plugin main class package name. For example, if `plugin.id=gj.module.user`, the plugin entry class must be in the `gj.module.user` package. Any mismatch throws an `IllegalStateException` at startup. See [Section 3.2](#32-pluginproperties).

### Q2: Plugin failed to start / how to troubleshoot startup failure?

Check the logs for `[PF4J]` entries. Startup failures are recorded in `GJPluginStartingError` with the plugin ID and exception detail. Common causes:

- **Missing JAR**: the plugin directory under `plugins/` must contain a JAR matching `{pluginId}-*.jar`
- **Dependency conflict**: plugin brings a library version incompatible with the host app
- **Bean wiring failure**: a `@Component` in the plugin fails to construct due to missing dependencies

See [Section 4](#4-plugin-lifecycle) for the full lifecycle flow.

### Q3: SQL works in MySQL but fails on DM/PostgreSQL with "invalid identifier"?

A column name likely conflicts with that database's reserved keywords (e.g., `order`, `comment`, `context`). Implement `GJTableKeywordProvider` and register it as a `@Component`:

```java
@Component
public class MyKeywords implements GJTableKeywordProvider {
    @Override
    public Map<String, Set<String>> getTableKeywords() {
        return Map.of("table_name", Set.of("order", "comment"));
    }
}
```

The framework automatically wraps these columns with the correct quote character at runtime. See [Section 6.3](#63-sql-keyword-quoting).

### Q4: Host app has Controllers but they don't appear in Swagger-UI?

The framework auto-creates `GroupedOpenApi` for **plugins only**, not for the host app. Add a `default` group in `application.yml`:

```yaml
springdoc:
  group-configs:
    - group: default
      displayName: default
      packagesToScan: com.example.controller
```

>`packagesToScan` must point to the host application's Controller package, not a plugin package. See [Section 15](#16-openapi-documentation).

### Q5: What is the minimum configuration for the host app?

Only one annotation is required:

```java
@SpringBootApplication
@ComponentScan("gj")   // activates all framework beans
public class GJApplication {
    public static void main(String[] args) {
        SpringApplication.run(GJApplication.class, args);
    }
}
```

Without `@ComponentScan("gj")`, no framework beans are discovered and the entire framework stays inactive. See [Section 20.2](#202-host-application-entry-point).

### Q6: How to share ModelMapper mappings between host app and plugins?

Add the `gj-modelmapper` dependency and use `@GJModelMapperScan` on the host app:

```java
@GJModelMapperScan(
    basePackages = "com.example.model",
    markerInterface = GJModelMapperConfig.class
)
```

Plugins append their own mappings to the shared `ModelMapper` instance automatically. See [Section 20.3](#203-optional-gjmodelmapperscan-shared-models).

### Q7: Does auto-migration ever drop tables or columns?

No. The migration engine follows a **strict additive-only policy** — only `CREATE TABLE` (when table is missing) and `ALTER TABLE ADD COLUMN` (when column is missing) are generated. Existing tables, columns, and data are never modified or deleted. See [Section 7.2](#72-production-safety).

### Q8: JPA `@Entity` / `JpaRepository` beans not working — no error but not injected?

JPA support is **host-controlled**. Add `hibernate-core` to the host application's `pom.xml`:

```xml
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-core</artifactId>
    <version>${hibernate.version}</version>
</dependency>
```

Without it, the framework silently skips JPA initialization — no `EntityManagerFactory` or repository beans are created. Check the startup log for:
```
[Plugin: xxx] JPA EntityManagerManager not available, skipping
```

MyBatis-Plus is unaffected and works normally regardless. See [Section 6.2](#62-jpa-data-access).

### Q9: I set `spring.jpa.hibernate.ddl-auto=update` but it's not working?

The framework defaults `ddl-auto` to `none`. Automatic DDL is handled by the framework's own migration engine (see [Section 7](#7-database-auto-migration)), which supports 7 databases with a strict additive-only policy (CREATE TABLE / ADD COLUMN only). To enable Hibernate's own DDL generation, override the `GJPluginJpaProperties` bean in the host application:

```java
@Bean
@Primary
public GJPluginJpaProperties customJpaProperties() {
    GJPluginJpaProperties props = new GJPluginJpaProperties();
    props.setDdlAuto("update");  // or "validate", "create", "create-drop"
    return props;
}
```

### Q10: Why aren't `@OneToMany` / `@ManyToOne` / `@Embedded` / `@Inheritance` generated by auto-migration?

v1 migration only handles single-table entities with basic fields. Relationship mappings, embeddables, and inheritance hierarchies require manual DDL for the associated tables, foreign keys, or join tables. Hibernate will use these tables normally at runtime once they exist. `@Embedded` and `@ElementCollection` are planned for a future release.
