# Contributing

Thanks for your interest in contributing to gj.spring.pf4j!

## Reporting Bugs

- Search [existing issues][issues] first to avoid duplicates.
- Use the bug report template and include:
  - gj-pf4j version and Java version
  - Steps to reproduce
  - Expected vs actual behavior
  - Relevant logs or stack traces

## Suggesting Features

- Open a discussion issue before writing code.
- Explain the use case and why it belongs in the framework rather than in your application.

## Pull Requests

1. Fork the repository and create a branch from `main`.
2. Follow the existing code style (4-space indent, no wildcard imports).
3. Keep changes focused — one PR per feature or fix.
4. Update the README if your change affects documented behavior.
5. Ensure `mvn compile` passes before submitting.

## Development Setup

```bash
git clone https://github.com/wangpengxpy/gj.spring.pf4j.git
cd gj.spring.pf4j/src
mvn clean install -DskipTests
```

The core module is `gj-pf4j`. The parent POM is at `src/gj-parent/pom.xml`.

## Code Style

- Java 17, UTF-8 encoding.
- Package names: `gj.pf4j.<feature>`.
- Class names: `GJ` prefix (e.g., `GJPlugin`, `GJHub`).
- Chinese comments in implementation code are acceptable; public API Javadoc should be in English.
- No Lombok `var` — use explicit types.

## License

By contributing, you agree that your contributions will be licensed under the
MIT License.

[issues]: https://github.com/wangpengxpy/gj.spring.pf4j/issues
