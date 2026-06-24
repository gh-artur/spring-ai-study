# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot study project for learning Spring AI with the OpenAI chat model. Exposes a single REST endpoint that proxies user messages to an LLM via Spring AI's `ChatClient`.

## Commands

Uses the Maven wrapper (`./mvnw` on Unix, `mvnw.cmd` on Windows).

- Build: `./mvnw clean package`
- Run the app: `./mvnw spring-boot:run` (starts on http://localhost:8080)
- Run all tests: `./mvnw test`
- Run a single test: `./mvnw test -Dtest=SpringAiStudyApplicationTests`
- Run a single test method: `./mvnw test -Dtest=SpringAiStudyApplicationTests#methodName`

`OPENAI_API_KEY` must be set in the environment before running — `application.properties` reads it via `spring.ai.openai.apiKey=${OPENAI_API_KEY}`. The app (and `contextLoads` test) will fail to start without it.

## Toolchain

- Java 25 (pinned in `mise.toml`; run `mise install` if using mise).
- Spring Boot 4.1.0, Spring AI 2.0.0 (BOM-managed).

## Architecture

- `SpringAiStudyApplication` — standard Spring Boot entry point.
- `controller/ChatController` — the only feature. Builds a `ChatClient` from the auto-configured `ChatClient.Builder`, applying a default system prompt in its constructor. `GET /api/chat?message=...` forwards the message to OpenAI and returns the raw text response.

The system prompt currently constrains the assistant to a single domain (cheese / queijos, in Portuguese). When changing assistant behavior, edit the `defaultSystem(...)` block in `ChatController`.

## Notes

- Package name is `com.ghartur.spring_ai_study` (underscores) because the original hyphenated name was invalid — see `HELP.md`.
