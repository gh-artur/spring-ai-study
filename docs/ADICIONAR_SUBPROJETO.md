# Adicionar um novo subprojeto (ex.: um MCP)

Este repo é um **guarda-chuva de subprojetos independentes** — cada um é
self-contained (`pom.xml` + Maven wrapper próprios), **sem POM pai**. As notas de
estudo e a config de repo (`.gitignore`, `.gitattributes`, `mise.toml`) vivem só na
**raiz** e valem recursivamente para todos os subprojetos.

Ao gerar um projeto novo (normalmente pelo [Spring Initializr](https://start.spring.io)),
alguns arquivos gerados conflitam com esse padrão. Este guia lista os ajustes — e há um
script que faz a parte mecânica.

## Passo a passo

1. **Gerar** o projeto no Spring Initializr:
   - Java **25**, Spring Boot **4.1.x**, group `com.ghartur`.
   - Dependências conforme o tema (ex.: MCP Client/Server + Model OpenAI).
2. **Descompactar** dentro da raiz do repo, como pasta irmã (ex.: `mcpclient/`, `mcpserver/`).
3. **Normalizar** (roda os ajustes abaixo automaticamente):
   ```bash
   scripts/novo-subprojeto.sh <pasta>
   ```
4. **Buildar de dentro da pasta** para conferir:
   ```bash
   cd <pasta> && ./mvnw clean compile
   ```

## Os ajustes (o que o script faz / confere)

### 1. Remover `.gitignore` e `.gitattributes` locais
O Initializr gera esses dois em cada projeto, mas são **redundantes** — a raiz já cobre
`target/`, `.idea`, `.vscode`, `HELP.md`, etc., **e** ainda cobre `.env` e `*.mv.db`, que
o template local NÃO tem. Manter só o da raiz evita regra espalhada.

### 2. Trocar caminhos absolutos por relativos
Se o `application*.properties` tiver algo tipo
`jdbc:h2:file:C:\...\chatmemory`, troque por relativo (`./chatmemory`). Assim o subprojeto
funciona em qualquer máquina, rodando o `mvnw` de dentro da pasta.

### 3. Lombok (só se o pom usar Lombok)
O JDK 23+ desliga o annotation processing por padrão quando o processador não é declarado.
Sem isso, `@Data`/`@Builder` etc. quebram o build via linha de comando. Adicione ao
`<build><plugins>` do `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```
(A versão do Lombok vem gerenciada pelo parent do Spring Boot — não fixe.)

## Toolchain
Java 25 vem do `mise.toml` da raiz (`mise install`). Não precisa de config de toolchain
por subprojeto. Se o `mvnw` reclamar de `release version 25 not supported`, o `JAVA_HOME`
do shell está apontando pra outro JDK — garanta o `mise activate` no seu profile.
