# gj.spring.pf4j

[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/wangpengxpy/gj.spring.pf4j/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.wangpengxpy/gj-pf4j?color=green)](https://central.sonatype.com/artifact/io.github.wangpengxpy/gj-pf4j)
[![Stars](https://img.shields.io/github/stars/wangpengxpy/gj.spring.pf4j?style=social)](https://github.com/wangpengxpy/gj.spring.pf4j/stargazers)

A lightweight, modular plugin framework powered by PF4J and Spring, with no heavyweight Spring Boot dependency. Pluggable extension points enable any third-party component to integrate into the plugin lifecycle without modifying framework source code. Supports both Spring MVC and Spring WebFlux routing — auto-adapts to the host application's web stack.

Open plugin authentication lets plugins define their own auth logic through an SPI; a provider chain with pluggable strategy delegates requests; six-position filter slots let hosts compose arbitrary security filters — all governed by a host-controlled "install ≠ enable" model.

> [中文文档](README_CN.md) | [Full Documentation → Wiki](https://github.com/wangpengxpy/gj.spring.pf4j/wiki)

<p align="center">
  <img src="images/architecture.png" alt="gj.spring.pf4j Plugin Architecture" width="85%">
</p>

---

## Core Capabilities

<p align="center">
  <img src="images/capabilities.png" alt="Plugin Core Capabilities" width="90%">
</p>

---

## Sponsor

If this project helps you, consider supporting the developer.

| Alipay | WeChat Pay |
|:------:|:----------:|
| <img src="images/ali_pay.jpg" alt="Alipay" height="260"> | <img src="images/wechat_pay.jpg" alt="WeChat Pay" height="260"> |

