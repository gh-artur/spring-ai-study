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
├── spring-ai-playground/   <- subproject: Spring AI feature playground (OpenAI chat)
├── mcpclient/              <- subproject: MCP client/host — consumes MCP servers as tools
├── mcpserverstdio/         <- subproject: MCP server over stdio (local help-desk tools)
└── mcpserverremote/        <- subproject: MCP server over streamable HTTP (:8090)
```

Each subproject is independent: there is **no parent/aggregator POM**. Build and run
commands are always run from inside the subproject directory. New MCPs are added as new
top-level folders and may use any stack.

To add a new subproject (e.g. a fresh Spring Initializr project), follow
`docs/ADICIONAR_SUBPROJETO.md` and run `scripts/novo-subprojeto.sh <folder>` to strip the
redundant per-project `.gitignore`/`.gitattributes` and flag absolute paths / missing
Lombok config.

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

## Subprojects: MCP (client + servers)

Three subprojects exploring the **Model Context Protocol** (see `NOTES.md` §14 and the MCP
section of `CONCEITOS.md`). The two servers expose the **same help-desk tools**
(`@McpTool` `createTicket`/`getTicketStatus`, JPA + H2); only the transport differs.

- `mcpclient` — the **host**. Discovers MCP servers declared in
  `src/main/resources/mcp-servers.json` and exposes their tools to the LLM via a
  `ToolCallbackProvider` (`.defaultTools(...)`). Endpoint `GET /api/chat`.
- `mcpserverstdio` — MCP server over **stdio** (`web-application-type=none`); the client
  launches it as a subprocess (`java -jar`).
- `mcpserverremote` — MCP server over **streamable HTTP** (`spring.ai.mcp.server.protocol=
  streamable`, port **8090**); clients connect by URL.

Gotchas (all hit during the study, documented in `NOTES.md` §14.4):

- **stdio = one server process per client.** Don't run the MCP Inspector and the client at
  the same time — both spawn their own server instance and collide on the H2 `./chatmemory`
  file, so the second stalls and the client times out on the 20s init handshake.
- **Windows jar lock:** a leftover server instance holds the jar, breaking `mvn clean`/
  `repackage` (`Unable to rename ... .jar.original`). Kill it first (a `killjava <substr>`
  bash function was added to the user's `~/.bashrc`).
- `mvnw` obeys `JAVA_HOME`, not the `java` on `PATH` — a stale `JAVA_HOME` on JDK 17 gives
  `release version 25 not supported` even when `java -version` shows 25. `mise activate` in
  the shell profile keeps `JAVA_HOME` aligned with `mise.toml`.
- MCP servers that persist need `spring.jpa.hibernate.ddl-auto=update`, else the file-based
  H2 starts empty and inserts fail with `Table "HELPDESK_TICKETS" not found`.
