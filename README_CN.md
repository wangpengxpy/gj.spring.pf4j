# gj.spring.pf4j

[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/wangpengxpy/gj.spring.pf4j/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.wangpengxpy/gj-pf4j?color=green)](https://central.sonatype.com/artifact/io.github.wangpengxpy/gj-pf4j)

基于 PF4J 与 Spring 的轻量级模块化插件框架，无 Spring Boot 重依赖。可插拔扩展点允许任意第三方组件以零框架改动的方式接入插件生命周期。支持 Spring MVC 与 Spring WebFlux 双路由模式，自动适配主应用架构。

开放插件自定义鉴权让插件通过 SPI 自由定义认证逻辑，Provider 链式委托配合可插拔策略分发请求，六位 Filter 插槽供宿主编排任意安全过滤器——受"安装 ≠ 启用"模型统一管控。

> [English](README.md) | [完整文档 → Wiki](https://github.com/wangpengxpy/gj.spring.pf4j/wiki)

<p align="center">
  <img src="images/architecture.png" alt="gj.spring.pf4j 插件化架构设计" width="85%">
</p>

---

## 核心能力

<p align="center">
  <img src="images/capabilities.png" alt="插件核心能力" width="90%">
</p>

---

## 捐赠

如果本项目对你有帮助，欢迎支持开发者。

| 支付宝 | 微信支付 |
|:------:|:--------:|
| <img src="images/ali_pay.jpg" alt="支付宝" height="260"> | <img src="images/wechat_pay.jpg" alt="微信支付" height="260"> |
