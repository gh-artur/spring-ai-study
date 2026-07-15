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
├── springai/               <- subproject: multimodal demos (audio transcription/TTS, image gen)
├── mcpclient/              <- subproject: MCP client/host — consumes MCP servers as tools
├── mcpserverstdio/         <- subproject: MCP server over stdio (local help-desk tools)
├── mcpserverremote/        <- subproject: MCP server over streamable HTTP (:8090)
└── support-agent-demo/     <- capstone: autonomous email-support AGENT + its MCP server
    ├── mcp-server/         <-   MCP server (streamable HTTP :8090) over a seeded MySQL DB
    └── support-agent/      <-   the agent: watches a Mailpit inbox, resolves & replies
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
section of `CONCEITOS.md`). Both servers started from the **same help-desk tools**
(`@McpTool` `createTicket`/`getTicketStatus`, JPA + H2), but they have since **diverged**:
only `mcpserverremote` grew the advanced capabilities (progress, logging, sampling,
elicitation, and a `summarizeTickets` tool); `mcpserverstdio` still has just the original two.

- `mcpclient` — the **host**. Connects to two servers: `filesystem` over **stdio** (declared
  in `src/main/resources/mcp-servers.json`) and the remote help-desk over **streamable HTTP**
  (declared via `spring.ai.mcp.client.streamable-http.connections.artur.*` properties — the
  connection is named **`artur`**). Instead of a global `ToolCallbackProvider`, it injects
  `List<McpSyncClient>` and **selects tools per request** (`util/ToolUtil.selectToolsFor`),
  with a global `McpToolFilter` (`util/McpServerToolFilter`) blocking tools at discovery.
  Client-side callback handlers (`util/HelpDesk{Log,Sampling,Elicitation}*` +
  `HelpDeskToolProgressListener`) bind to the connection via `clients = "artur"`. Endpoints:
  `GET /api/chat` and `GET /api/summarize-tickets` (sampling demo).
- `mcpserverstdio` — MCP server over **stdio** (`web-application-type=none`); the client
  launches it as a subprocess (`java -jar`). Original two tools only.
- `mcpserverremote` — MCP server over **streamable HTTP** (`spring.ai.mcp.server.protocol=
  streamable`, name `helpdesk-mcp-server`, port **8090**); clients connect by URL. Its tools
  take an `McpSyncRequestContext` to push progress/logging and to call back into the client
  (`ctx.sample` for sampling, `ctx.elicit` for elicitation).

> ⚠️ Two distinct names that are easy to confuse: the **client connection** name (`artur`,
> which the `@Mcp*` handlers match on) vs. the **server-advertised** name (`helpdesk-mcp-server`,
> which the tool filter/selection matches on).

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

## Subproject: springai

A small **multimodal** playground (separate Spring Initializr project, package
`com.eazybytes.springai`) that exercises the non-text OpenAI models via Spring AI:

- `AudioController` (`/api/*`): speech-to-text with `TranscriptionModel` (Whisper, plus a
  `transcribe-options` variant that sets language/temperature/`VTT` format), and text-to-speech
  with `TextToSpeechModel` (writes `output.mp3` / `speech-options.mp3`, the second picking a
  voice/speed/format).
- `ImageController` (`/api/image`, `/api/image-options`): image generation with `ImageModel`
  (returns base64 JSON).

Needs `OPENAI_API_KEY`. See `NOTES.md` §15.

## Subproject: support-agent-demo (capstone)

The course finale — an **autonomous AI agent** that works a support mailbox end to end, built
as **two independent apps** under one folder (each with its own `pom.xml`/wrapper; still no
aggregator POM). It ties together everything above: tool calling, MCP (streamable HTTP),
structured output, and system prompting. See `NOTES.md` §16 and the "AI Agent" section of
`CONCEITOS.md`.

- `mcp-server` (`com.eazybytes.mcp.server`, streamable HTTP on **:8090**, name
  `support-agent-mcp-server`) — exposes the agent's only window into company systems as MCP
  tools over a **MySQL** database (Docker Compose auto-started; schema + demo data seeded from
  `db/init/*.sql`, so `spring.jpa.hibernate.ddl-auto=none`). Two tool classes: `SupportQueryTools`
  (read-only: `lookup_customer_by_email`, `get_customer_orders`, `get_order_by_number`,
  `search_products`, `get_product_by_sku`, `detect_duplicate_charges`, `check_warranty`,
  `get_customer_ticket_history`) and `SupportActionTools` (writes: `issue_refund`,
  `log_support_ticket`).
- `support-agent` (`com.eazybytes.support.agent`) — the host/agent (`spring-ai-starter-mcp-client`
  + `model-openai`). `InboxMonitor` polls a **Mailpit** inbox (REST on :8025) on a fixed delay;
  each unread mail becomes an `IncomingEmail` and is handed to `SupportAgent`, a `ChatClient`
  wired with **all** MCP tools (`.defaultTools(toolCallbackProvider)`) and the
  `support-agent-system.st` system prompt. The LLM drives the whole Reason→Act→Observe loop
  itself (Spring AI auto-executes the tools); the result is structured output (`AgentResponse`
  = `replySubject`/`replyBody`/`operatorSummary`). `SupportMailSender` emails the reply back over
  SMTP (:1025), threaded onto the original. `POST /seed-mail` drops a test email into the inbox.

Gotchas specific to the capstone:

- **Two Docker stacks, two `compose.yaml`s.** `mcp-server` starts MySQL (:3306, named volume);
  `support-agent` starts Mailpit (:1025 SMTP / :8025 UI+REST). Each app's
  `spring.docker.compose.file` points at its own — bring the server up first.
- **Seed data is date-relative.** `db/init/02-seed.sql` uses `CURDATE() - INTERVAL n DAY`, so the
  four demo scenarios (goodwill refund, pre-sales voltage question, duplicate charge on #4471,
  multilingual/multi-intent) stay valid whenever the MySQL volume is first created. Seed scripts
  only run on a **fresh** volume — `docker compose down -v` to reseed.
- **The agent's outbound replies also land in Mailpit.** `MailpitClient.listUnread` scopes the
  query to `to:support@… !from:support@…` so the agent never reprocesses its own replies.
- Both apps default the MCP server to **:8090** — the agent connects by URL
  (`spring.ai.mcp.client.streamable-http.connections.support-agent.url`).
