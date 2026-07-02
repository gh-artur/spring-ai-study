# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Study repository for learning Spring AI and MCP (Model Context Protocol). It is an
**umbrella of independent subprojects** — each subproject is self-contained (its own
`pom.xml` and Maven wrapper) and is built/run on its own. The repository root holds
only the shared study notes and repo-wide config, not source code.

## Layout

```
.                       <- study notes + repo config live here
├── NOTES.md            <- running study notes (per Spring AI / MCP topic)
├── CONCEITOS.md        <- concept summaries
├── mise.toml           <- repo-wide toolchain (Java 25)
└── spring-ai-playground/   <- subproject: Spring AI feature playground (OpenAI chat)
    └── (future MCP subprojects will be added as sibling folders, e.g. mcp-xxx/)
```

Each subproject is independent: there is **no parent/aggregator POM**. Build and run
commands are always run from inside the subproject directory. New MCPs are added as new
top-level folders and may use any stack.

## Commands

Run from inside the relevant subproject (e.g. `cd spring-ai-playground`). Uses the Maven
wrapper (`./mvnw` on Unix, `mvnw.cmd` on Windows).

- Build: `./mvnw clean package`
- Run the app: `./mvnw spring-boot:run` (starts on http://localhost:8080)
- Run all tests: `./mvnw test`
- Run a single test: `./mvnw test -Dtest=SpringAiStudyApplicationTests`
- Run a single test method: `./mvnw test -Dtest=SpringAiStudyApplicationTests#methodName`
- Enable RAG (Qdrant + doc ingestion): `./mvnw spring-boot:run -Dspring-boot.run.profiles=rag`

`OPENAI_API_KEY` must be set in the environment before running — `application.properties`
reads it via `spring.ai.openai.apiKey=${OPENAI_API_KEY}`. The app (and `contextLoads`
test) will fail to start without it.

## Toolchain

- Java 25 (pinned in the root `mise.toml`; run `mise install` if using mise). Applies to
  all Java subprojects.
- `spring-ai-playground`: Spring Boot 4.1.0, Spring AI 2.0.0 (BOM-managed).

## Subproject: spring-ai-playground

The original study app — a collection of Spring AI feature demos, each exposed as its own
controller (chat, streaming, structured output, prompt templating/stuffing, chat memory,
RAG with Qdrant, tool calling, and a help-desk example with JPA persistence). The H2
chat-memory database file (`chatmemory.mv.db`) is created relative to the working
directory, so run commands from the subproject root.

- Package name is `com.ghartur.spring_ai_study` (underscores) because the original
  hyphenated name was invalid — see `spring-ai-playground/HELP.md`.
