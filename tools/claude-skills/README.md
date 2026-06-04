# gj.spring.pf4j — Claude Code Skills

This directory contains Claude Code skills for gj.spring.pf4j plugin development.
Copy the contents into your project's `.claude/` directory.

## Install

**New project (recommended):**
```bash
mvn archetype:generate \
  -DarchetypeGroupId=io.github.wangpengxpy \
  -DarchetypeArtifactId=gj-archetype \
  -DarchetypeVersion=1.0.0 \
  -DpluginName=<name> \
  -DpackagePrefix=gj.module
```
The generated project includes `.claude/` automatically.

**Existing project:**
```bash
# Clone the framework repo and copy skills
git clone --depth 1 https://github.com/wangpengxpy/gj.spring.pf4j.git /tmp/gj-pf4j
cp -r /tmp/gj-pf4j/tools/claude-skills/* .claude/
rm -rf /tmp/gj-pf4j
```

Or if using Maven to extract from the JAR:
```bash
mvn dependency:unpack \
  -Dartifact=io.github.wangpengxpy:gj-pf4j:1.0.0 \
  -DoutputDirectory=.claude \
  -Dincludes="claude-skills/**"
```

## Commands

| Command | Purpose |
|---------|---------|
| `/gj-plugin-new "description"` | Create a new plugin from requirements |
| `/gj-plugin-change "description"` | Modify an existing plugin |

## Structure

```
.claude/
├── commands/gj/
│   ├── plugin-new.md     ← new plugin development
│   └── plugin-change.md  ← requirement changes
└── skills/gj/
    ├── plugin.md          ← code generation
    └── openspec/          ← planning engine (internal, bundled)
```
