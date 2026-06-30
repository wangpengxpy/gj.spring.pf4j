# gj.spring.pf4j

[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/wangpengxpy/gj.spring.pf4j/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.wangpengxpy/gj-pf4j?color=green)](https://central.sonatype.com/artifact/io.github.wangpengxpy/gj-pf4j)
[![Stars](https://img.shields.io/github/stars/wangpengxpy/gj.spring.pf4j?style=social)](https://github.com/wangpengxpy/gj.spring.pf4j/stargazers)

基于 PF4J 与 Spring 的轻量级模块化插件框架，无 Spring Boot 重依赖。可插拔扩展点允许任意第三方组件以零框架改动的方式接入插件生命周期。支持 Spring MVC 与 Spring WebFlux 双路由模式，自动适配主应用架构。

> [English](README.md) | [完整文档 → Wiki](https://github.com/wangpengxpy/gj.spring.pf4j/wiki)

<p align="center">
  <img src="images/architecture.png" alt="gj.spring.pf4j 插件化架构设计" width="85%">
</p>

---

## 核心能力

<p align="center">
  <img src="images/capabilities.png" alt="插件核心能力" width="90%">
</p>

- **[插件扩展](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Plugin-Extensions_CN)** — 可插拔扩展点；任意组件（如 MongoDB、Kafka、MQTT、gRPC 等）通过 `PluginResourceRegistrar` 接入，无需修改框架源码
- **[插件生命周期管理](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Plugin-Lifecycle_CN)** — 运行时安装、禁用、重启、卸载和删除插件
- **[插件热加载](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Plugin-Hot-Reload_CN)** — 通过 API 驱动工作流或文件监听实现热加载；生命周期事件支持自定义编排逻辑
- **[插件运行时管理 API](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Runtime-Management-API_CN)** — `GJPluginService` 提供加锁控制的运行时管理 API
- **[REST 端点](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/REST-Endpoints_CN)** — `@RestController` Bean 自动探测并注册到主应用路由表，支持 MVC 和 WebFlux
- **[双路由模式](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/REST-Endpoints_CN#52-spring-mvc-与-webflux-双路由模式)** — Spring MVC (Servlet) 与 Spring WebFlux (Reactive)；插件无需任何改动
- **[OpenAPI 文档](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/OpenAPI_CN)** — SpringDoc 驱动；每个插件自动生成独立的 `GroupedOpenApi`
- **[MyBatis-Plus 数据访问](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Data-Access_CN)** — 每个插件获得独立的 `SqlSessionFactory`、`SqlSessionTemplate` 和 `TransactionManager`，共享主应用 `DataSource`
- **[JPA 数据访问](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Data-Access_CN)** — Hibernate 驱动，每个插件独立的 `EntityManagerFactory` 和 `JpaTransactionManager`；与 MyBatis-Plus 可共存
- **[SQL 关键字引号处理](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Data-Access_CN)** — 通过 MyBatis-Plus `InnerInterceptor` 自动使用数据库特定引号包裹列名
- **[数据库自动迁移](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Database-Auto-Migration_CN)** — 自动 `@TableName` 实体 Schema 迁移（仅 CREATE TABLE / ADD COLUMN），支持 7 种数据库，生产安全
- **[对象映射](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Object-Mapping_CN)** — ModelMapper；插件实现 `GJPluginModelMapperConfig`，Spring Bean 扫描自动发现
- **[导入导出](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Import-Export_CN)** — EasyExcel 多 Sheet 读写，自动 i18n 表头翻译
- **[实时通信](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Real-Time-Communication_CN)** — netty-socketio Hub 模式（SignalR 风格），支持分组和用户定向推送
- **[定时任务](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Scheduled-Tasks_CN)** — 基于 Quartz 的 cron、固定间隔和一次性执行
- **[进程内事件总线](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Event-Bus_CN)** — 同步/异步发布，支持 Ant 风格通配符匹配
- **[国际化 i18n](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Internationalization_CN)** — 每个插件独立的 `messages.properties`，支持向主应用兜底

---

## 快速开始

<p align="center">
  <img src="images/quickstart.png" alt="快速上手指南" width="85%">
</p>

> 详见 [快速开始 → Wiki](https://github.com/wangpengxpy/gj.spring.pf4j/wiki/Quick-Start_CN)
