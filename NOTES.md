# Notas — Spring AI

Anotações de estudo sobre **Prompt Template**, **Stuffing**, **System / Default System** e **Advisors / Default Advisors**, ligadas ao código deste projeto.

---

## 1. System vs. Default System

A mensagem **system** define o "papel" / as regras do assistente. Existem dois lugares onde ela pode ser configurada:

### `defaultSystem(...)` — no Builder (uma vez)
Definido na construção do `ChatClient` e aplicado **automaticamente a toda requisição**. É o nosso caso em `ChatClientConfig`:

```java
return chatClientBuilder
        .defaultSystem("""
                Vocês é especialista em queijos, só responda perguntas
                onde o usuário quer tirar alguma dúvida sobre queijos
                """)
        .build();
```

Vantagem: o `ChatController` fica limpo — só envia o `.user(message)` e o system prompt já vai junto em todas as chamadas.

### `.system(...)` — por requisição
Definido dentro de `prompt()`, vale **apenas para aquela chamada** e **sobrescreve** o default:

```java
chatClient.prompt()
        .system("Responda sempre em inglês formal")  // sobrescreve o defaultSystem só nesta chamada
        .user(message)
        .call().content();
```

> Regra mental: `defaultSystem` = configuração global do client; `.system` = override pontual.

---

## 2. Prompt Template

Permite injetar variáveis no prompt em vez de concatenar strings na mão. Spring AI usa placeholders no estilo `{variavel}` e você passa os valores via `.param(...)`.

```java
chatClient.prompt()
        .user(u -> u
                .text("Recomende um queijo para acompanhar {bebida} numa ocasião {ocasiao}")
                .param("bebida", "vinho tinto")
                .param("ocasiao", "formal"))
        .call().content();
```

Também funciona no system (system template):

```java
.system(s -> s
        .text("Você é um sommelier de queijos. Responda no idioma: {idioma}")
        .param("idioma", "português"))
```

Pontos-chave:
- Separa **estrutura do prompt** (template) dos **dados** (params) → mais legível e reutilizável.
- Evita problemas de escaping e concatenação manual.
- A renderização do template acontece antes de a mensagem ser enviada ao modelo.

---

## 3. Stuffing (Prompt Stuffing)

Técnica de **"enfiar" contexto/dados diretamente no prompt** para o modelo responder com base neles — é a forma mais simples de dar conhecimento ao LLM, sem RAG / vector store.

Ideia: você junta um documento (lista de queijos, FAQ, etc.) ao prompt usando um template:

```java
// Ex.: carregar um recurso e injetar no prompt
@Value("classpath:/docs/catalogo-queijos.txt")
private Resource catalogo;

chatClient.prompt()
        .user(u -> u
                .text("""
                        Use APENAS o catálogo abaixo para responder.
                        Catálogo:
                        {catalogo}

                        Pergunta: {pergunta}
                        """)
                .param("catalogo", catalogo)
                .param("pergunta", message))
        .call().content();
```

Quando usar / limites:
- ✅ Bom para **pouco contexto e estático** (cabe na janela de tokens).
- ❌ Não escala: documentos grandes estouram o limite de tokens e aumentam custo.
- Para bases grandes → o caminho é **RAG** (recuperar só os trechos relevantes via vector store) em vez de stuffing.
- Liga direto com o Advisor de auditoria (seção 4) — stuffing infla os **prompt tokens**, dá pra observar isso no log de `Usage`.

---

## 4. Advisors vs. Default Advisors

**Advisors** são interceptadores que envolvem a chamada ao modelo (padrão *around*/cadeia). Servem para logging, auditoria, memória de conversa, RAG, guardrails, etc. — sem poluir o controller.

### Interface implementada neste projeto
`TokenUsageAuditAdvidor` implementa `CallAdvisor` (chamadas síncronas; para streaming existe `StreamAdvisor`):

```java
public class TokenUsageAuditAdvidor implements CallAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        ChatClientResponse resp = chain.nextCall(req);   // 1. segue a cadeia
        ChatResponse chatResponse = resp.chatResponse(); // 2. inspeciona a resposta
        if (chatResponse != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            logger.info("Token usage details: {}", usage);
        }
        return resp;                                     // 3. devolve (pode modificar)
    }

    @Override public String getName() { return "TokenUsageAuditAdvisor"; }
    @Override public int getOrder()  { return 1; } // menor = mais cedo na cadeia
}
```

Anatomia:
- `chain.nextCall(req)` → chama o **próximo advisor** (ou o modelo, se for o último). Código **antes** dele = pré-processamento (mexer no request); **depois** = pós-processamento (inspecionar/mexer na resposta).
- `getOrder()` → define a ordem na cadeia (menor roda primeiro / mais "externo").
- `getName()` → identifica o advisor (aparece em logs/contexto).

### Default Advisors — no Builder (todas as chamadas)
Registrados no `ChatClient.Builder` e aplicados a **toda requisição**, igual ao `defaultSystem`:

```java
chatClientBuilder
        .defaultAdvisors(List.of(new TokenUsageAuditAdvidor()))
        .build();
```

### Advisor por requisição
Também dá pra adicionar pontualmente em uma chamada:

```java
chatClient.prompt()
        .advisors(new SimpleLoggerAdvisor())   // só nesta chamada
        .user(message)
        .call().content();
```

### ⚠️ Call vs. Stream — duas cadeias separadas
Existem **duas cadeias de advisors distintas**, escolhidas pelo método usado no controller:

| Controller | Cadeia | Interface necessária |
|-----------|--------|----------------------|
| `.call().content()`   | `CallAdvisorChain`   | `CallAdvisor` → `adviseCall(...)` |
| `.stream().content()` | `StreamAdvisorChain` | `StreamAdvisor` → `adviseStream(...)` |

Um advisor que implementa **só `CallAdvisor`** **não é acionado no `.stream()`** (a cadeia de stream ignora quem não é `StreamAdvisor`) — mesmo estando em `defaultAdvisors(...)`. Foi por isso que o endpoint de stream "não caía" no advisor.

Solução: implementar **as duas interfaces**. No streaming a resposta vem como `Flux<ChatClientResponse>` (em chunks):

```java
public class TokenUsageAuditAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        ChatClientResponse resp = chain.nextCall(req);
        logUsage(resp);
        return resp;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest req, StreamAdvisorChain chain) {
        return chain.nextStream(req).doOnNext(this::logUsage); // inspeciona cada chunk
    }
}
```

> No streaming, o `Usage` (tokens) só costuma vir preenchido no **último chunk** (nos intermediários vem nulo/zero) → checar `getUsage() != null`.
> Pode ser necessário habilitar: `spring.ai.openai.chat.options.stream-usage=true`.

### `SimpleLoggerAdvisor` (vem pronto no Spring AI)
Loga request/response. Para ver a saída, baixar o nível de log — já configurado em `application.properties`:

```properties
logging.level.org.springframework.ai.chat.client.advisor=DEBUG
```

> Resumo: `defaultAdvisors` = cadeia global; `.advisors(...)` = adiciona só naquela chamada. Mesma lógica do `defaultSystem` vs `.system`.

---

## 5. Chat Options (parâmetros do modelo)

Controlam **como** o modelo gera a resposta (criatividade, tamanho, repetição, etc.). No Spring AI existe a interface portável `ChatOptions` (comum a todos os providers) e a específica `OpenAiChatOptions` (parâmetros extras do OpenAI).

### Os parâmetros

| Parâmetro | O que faz | Faixa típica | Observação |
|-----------|-----------|--------------|------------|
| `model` | Qual modelo usar | ex. `gpt-4o`, `gpt-4o-mini` | Pode trocar por requisição sem mexer no config global |
| `temperature` | Aleatoriedade / criatividade | `0.0`–`2.0` (OpenAI) | Baixo = determinístico e focado; alto = criativo e variado |
| `topP` | *Nucleus sampling*: considera só os tokens que somam `p` de probabilidade | `0.0`–`1.0` | Alternativa ao `temperature` — **ajuste um ou outro, não os dois** |
| `topK` | Considera só os `k` tokens mais prováveis | inteiro | Portável na interface, mas o **OpenAI ignora** (é param de outros modelos, ex. Anthropic/Vertex) |
| `frequencyPenalty` | Penaliza tokens pela **frequência** com que já apareceram | `-2.0`–`2.0` | Positivo reduz repetição literal de palavras |
| `presencePenalty` | Penaliza tokens que **já apareceram** (presença, não importa quantas vezes) | `-2.0`–`2.0` | Positivo incentiva trazer assuntos/palavras novas |
| `maxTokens` | Teto de tokens da **resposta** (completion) | inteiro | Limita custo/tamanho; não conta o prompt |
| `stopSequences` | Strings que **interrompem** a geração quando aparecem | lista | Útil pra cortar a saída em um marcador |

> `temperature` vs `topP`: ambos controlam diversidade por caminhos diferentes. Convenção: mexa em **um** deles e deixe o outro no default.
>
> `frequencyPenalty` vs `presencePenalty`: *frequency* olha **quantas vezes** o token apareceu; *presence* olha apenas **se** já apareceu.

### Configuração global (defaults via `application.properties`)
Aplicado a todas as chamadas, sem código:

```properties
spring.ai.openai.chat.options.model=gpt-4o-mini
spring.ai.openai.chat.options.temperature=0.7
spring.ai.openai.chat.options.max-tokens=500
spring.ai.openai.chat.options.frequency-penalty=0.0
spring.ai.openai.chat.options.presence-penalty=0.0
spring.ai.openai.chat.options.top-p=1.0
spring.ai.openai.chat.options.stop=FIM,###
```

### Configuração no Builder (default do `ChatClient`)
Mesma ideia do `defaultSystem`/`defaultAdvisors`:

```java
chatClientBuilder
        .defaultOptions(OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.7)
                .maxTokens(500)
                .build())
        .build();
```

### Configuração por requisição (`.options(...)`)
Sobrescreve os defaults só naquela chamada:

```java
chatClient.prompt()
        .user(message)
        .options(OpenAiChatOptions.builder()
                .model("gpt-4o")
                .temperature(0.2)          // resposta mais determinística
                .maxTokens(300)
                .frequencyPenalty(0.5)
                .presencePenalty(0.3)
                .topP(1.0)
                .stopSequences(List.of("FIM"))
                .build())
        .call().content();
```

> Use `OpenAiChatOptions` para ter acesso aos params específicos do OpenAI; a `ChatOptions` portável serve quando você quer código independente de provider. `topK` existe na interface portável mas é **ignorado pelo OpenAI**.

---

## 6. Structured Output (resposta como objeto Java)

Em vez de receber `String`, o Spring AI converte a resposta do modelo direto num **objeto Java tipado**. No projeto: `chatBean` → retorna um `CountryCities`.

### O record e o controller

```java
public record CountryCities(String country, List<String> cities) {}
```

```java
CountryCities entity = chatClient
        .prompt()
        .user(message)
        .call()
        .entity(CountryCities.class);   // <- a mágica acontece aqui
```

### Como funciona por baixo
O `.entity(...)` usa um **`BeanOutputConverter`**, que faz duas coisas:
1. **Gera um JSON Schema** a partir da estrutura do `record`/classe.
2. **Anexa ao prompt** uma instrução pedindo ao modelo que responda **apenas** com um JSON naquele formato.

Depois, **desserializa** o JSON da resposta no objeto (`CountryCities`).

> Foi exatamente isso que você viu no `SimpleLoggerAdvisor`: o prompt enviado ao modelo ganha um trecho do tipo *"Your response should be in JSON format... Do not include markdown code blocks..."* seguido do schema gerado. Essa orientação **não foi escrita por você** — o converter injetou automaticamente.

### Outras formas
- `.entity(new ParameterizedTypeReference<List<CountryCities>>() {})` → para **coleções genéricas** (List/Map), preservando o tipo.
- `.entity(new MapOutputConverter())` / `ListOutputConverter` → quando quer `Map`/`List<String>` sem criar um record.

### Pontos de atenção
- Quanto **mais claro o nome dos campos** do record, melhor o modelo acerta o preenchimento.
- Modelo pode falhar o JSON ocasionalmente → vale tratar erro de parsing.
- O schema injetado **gasta tokens de prompt** (dá pra ver no `TokenUsageAuditAdvisor`).
- Detalhe do código atual: este controller cria o próprio `ChatClient` a partir do `Builder` e registra **só** o `SimpleLoggerAdvisor` (não usa o bean global com `defaultSystem` de queijos nem o `TokenUsageAuditAdvisor`).

---

## 7. Chat Memory (histórico da conversa)

Por padrão o LLM é **stateless**: cada chamada é independente, ele não lembra do que foi dito antes. O **Chat Memory** resolve isso guardando o histórico das mensagens e reenviando junto a cada nova requisição, dando a sensação de conversa contínua.

### Como é montado neste projeto

Um `ChatClient` dedicado (`chatMemoryChatClient`) com o `MessageChatMemoryAdvisor` registrado como default advisor:

```java
@Bean(name = "chatMemoryChatClient")
public ChatClient chatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
    SimpleLoggerAdvisor loggerAdvisor = SimpleLoggerAdvisor.builder().build();
    MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

    return chatClientBuilder
            .defaultAdvisors(List.of(loggerAdvisor, memoryAdvisor))
            .build();
}
```

E o controller seta o **id da conversa** por requisição, usando o `username` que vem no header:

```java
@GetMapping("/chat-memory")
public String chat(@RequestHeader("username") String username,
                   @RequestParam String message) {
    return chatClient
            .prompt()
            .user(message)
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, username))
            .call()
            .content();
}
```

### As três peças

| Peça | Papel |
|------|-------|
| `ChatMemory` | A **abstração** que armazena/recupera as mensagens de uma conversa. Por default o Spring AI auto-configura `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`; aqui o bean é declarado manualmente com `JdbcChatMemoryRepository` e janela de 10 (ver subseção de janela). |
| `MessageChatMemoryAdvisor` | O **advisor** que, antes de chamar o modelo, carrega o histórico daquela conversa e injeta no prompt; depois, salva a nova troca (user + assistant). |
| `ChatMemory.CONVERSATION_ID` | A **chave** que separa as conversas. Cada valor distinto = um histórico isolado. Aqui usamos o `username` → cada usuário tem sua própria memória. |

### Como funciona o ciclo (é um advisor, ligado à seção 4)

1. Chega a requisição com `conversationId = username`.
2. O `MessageChatMemoryAdvisor` **lê** o histórico desse id no `ChatMemory` e **prepende** as mensagens antigas ao prompt (pré-processamento — antes do `chain.nextCall`).
3. O modelo responde já "lembrando" do contexto.
4. O advisor **grava** a nova mensagem do user e a resposta do assistant no `ChatMemory` (pós-processamento).

### `MessageChatMemoryAdvisor` vs `PromptChatMemoryAdvisor`
- `MessageChatMemoryAdvisor` → injeta o histórico como **mensagens separadas** (user/assistant) na lista de mensagens. É o mais comum.
- `PromptChatMemoryAdvisor` → injeta o histórico como **texto dentro do system prompt**. Útil para modelos que não lidam bem com muitas mensagens de papéis distintos.

### Janela de mensagens (`MessageWindowChatMemory`)
O `MessageWindowChatMemory` **limita o número de mensagens por conversa** (default ~20) — as mais antigas são descartadas para a conversa não crescer infinito e estourar tokens. É **por `conversationId`**, não global.

Neste projeto o bean foi configurado **manualmente** para usar o repositório JDBC e uma janela de **10 mensagens**:

```java
@Bean
ChatMemory chatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository) {
    return MessageWindowChatMemory.builder()
            .maxMessages(10)                              // janela: últimas 10 mensagens
            .chatMemoryRepository(jdbcChatMemoryRepository)
            .build();
}
```

> `maxMessages(10)` conta **mensagens totais** (user **+** assistant), ou seja ~5 trocas — **não** 10 perguntas. Mensagens de `system` ficam de fora da contagem (são preservadas).

#### Por que as mensagens antigas somem *do banco* (e não só do prompt)
A cada `add`, o `MessageWindowChatMemory` não apenas filtra o que envia ao modelo: ele
1. **lê** o histórico atual do repositório,
2. junta com as novas mensagens,
3. **corta para as últimas N** (a janela), e
4. **regrava a janela inteira** via `saveAll`.

No `JdbcChatMemoryRepository`, esse `saveAll` é um **`DELETE` por `conversationId` seguido de `INSERT`** da janela. Resultado: a tabela `SPRING_AI_CHAT_MEMORY` guarda **apenas a janela**, nunca o histórico completo — por isso, ao mandar mensagens novas, você viu as linhas antigas **desaparecerem fisicamente** da tabela.

> Consequência: a janela é a "fonte da verdade". Se quiser **auditoria/histórico completo**, não dá pra confiar nessa tabela — precisaria persistir as mensagens à parte (outra tabela/append-only), pois o `MessageWindowChatMemory` poda o que excede a janela.

### Pontos de atenção
- **`InMemoryChatMemoryRepository` é volátil**: o histórico vive na heap da aplicação → some ao reiniciar e não é compartilhado entre instâncias. Para persistir/escalar existem repositórios de JDBC, Cassandra, Redis, etc. (trocar o bean `ChatMemoryRepository`).
- **Número de conversas cresce sem limite**: a janela limita mensagens *dentro* de cada conversa, mas cada novo `username` cria um histórico novo que nunca expira sozinho.
- **`username` por header não é autenticação**: qualquer um pode mandar o header de outro e acessar/poluir aquele histórico. Em produção o id viria do contexto de segurança (usuário autenticado), não de um header livre.
- O histórico **gasta tokens de prompt** a cada chamada (cresce até o limite da janela) — dá pra observar no `TokenUsageAuditAdvisor` (seção 4).

### Persistindo em banco (JDBC + H2)
Para o histórico **sobreviver ao restart**, troca-se o repositório volátil por um persistente. Com o starter JDBC, **não muda nenhuma linha de código** (advisor e controller seguem iguais) — só dependências + properties.

**Dependências (`pom.xml`):**
```xml
<!-- substitui o InMemoryChatMemoryRepository pelo JdbcChatMemoryRepository -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency> <!-- opcional: console web em /h2-console -->
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-h2console</artifactId>
</dependency>
```

**Properties (`application.properties`):**
```properties
spring.datasource.url=jdbc:h2:file:C:\\git\\...\\chatmemory;AUTO_SERVER=true
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=username
spring.datasource.password=password

# cria a tabela SPRING_AI_CHAT_MEMORY no startup (create table if not exists)
spring.ai.chat.memory.repository.jdbc.initialize-schema=always
```

Como funciona:
1. O starter JDBC no classpath faz o Spring AI **auto-configurar o `JdbcChatMemoryRepository`** no lugar do in-memory, usando o `DataSource` do Spring. A abstração `ChatMemory` é a mesma — muda só **onde** ela grava.
2. `initialize-schema=always` roda o script que cria a tabela `SPRING_AI_CHAT_MEMORY`.
3. As trocas (user/assistant) passam a ser **gravadas e lidas do banco** por `conversationId`.

> ⚠️ **`file:` vs `mem:` — o detalhe que faz o estado sobreviver.**
> `jdbc:h2:file:...` grava o banco **em arquivo no disco** → o histórico persiste entre reinícios.
> `jdbc:h2:mem:...` (default de muitos tutoriais) vive só na **RAM** → seria perdido no restart, mesmo "sendo um banco". O que persiste não é *ser banco de dados*, é ser **em arquivo**.
> `AUTO_SERVER=true` permite que mais de um processo (app + console H2) abra o mesmo arquivo ao mesmo tempo.

> Trocar H2 por Postgres/MySQL é só mudar o `spring.datasource.*` e o driver — o `JdbcChatMemoryRepository` continua o mesmo.

---

## 8. RAG (Retrieval-Augmented Generation) com Vector Store

RAG é o passo além do **Stuffing** (seção 3): em vez de enfiar um documento inteiro no prompt, você **busca só os trechos relevantes** para a pergunta e injeta apenas eles. Resolve o limite de tokens e o custo do stuffing, e dá ao LLM "conhecimento" que ele não tem no treinamento.

### As duas fases do RAG

| Fase | Quando | O que acontece |
|------|--------|----------------|
| **Ingestão (indexing)** | Uma vez / offline | Os documentos são transformados em **embeddings** (vetores) e gravados no **vector store**. |
| **Recuperação (retrieval)** | A cada pergunta | A pergunta vira embedding, busca-se por **similaridade** os trechos mais próximos, e eles entram no prompt como contexto. |

### Vector Store — o que é
Um banco especializado em **vetores** (embeddings). Em vez de buscar por igualdade (`WHERE x = ?`), busca por **proximidade semântica**: textos com significado parecido ficam próximos no espaço vetorial. Aqui usamos o **Qdrant**, rodando via Docker Compose:

```yaml
# compose.yml
services:
  qdrant:
    image: 'qdrant/qdrant:latest'
    ports:
      - '6333:6333'   # REST/dashboard
      - '6334:6334'   # gRPC (usado pelo Spring AI)
```

```properties
# application.properties
spring.docker.compose.stop.command=down
spring.ai.vectorstore.qdrant.initialize-schema=true   # cria a collection se não existir
spring.ai.vectorstore.qdrant.host=localhost
spring.ai.vectorstore.qdrant.port=6334
spring.ai.vectorstore.qdrant.collection-name=teste
```

> O Spring Boot, com a dependência `spring-boot-docker-compose`, **sobe o `compose.yml` sozinho** no startup. O `VectorStore` é auto-configurado a partir dessas properties.

### Fase 1 — Ingestão (`@PostConstruct`)
Cada `String` vira um `Document`; o `vectorStore.add(...)` gera os embeddings (chamando o modelo de embedding do OpenAI) e grava no Qdrant:

```java
@Component
public class RandomDataLoader {
    private final VectorStore vectorStore;

    public RandomDataLoader(VectorStore vectorStore) { this.vectorStore = vectorStore; }

    @PostConstruct
    public void loadSentencesIntoVectorStore() {
        List<String> sentences = List.of("Java is used for ...", "Bitcoin operates on ...", /* ... */);
        List<Document> documents = sentences.stream().map(Document::new).toList();
        vectorStore.add(documents);   // text -> embedding -> Qdrant
    }
}
```

> ⚠️ **Cuidado com `@PostConstruct` para ingestão:** roda **a cada boot** da aplicação. Como o Qdrant persiste a collection e `Document::new` gera um **id novo** toda vez, reinícios sucessivos **acumulam duplicatas**. Para estudo é ok; em código real, ingestão é um passo separado/idempotente (checar se já existe, ou usar ids estáveis).

### Fase 2 — Recuperação + geração (controller)
```java
@GetMapping("/random/chat")
public ResponseEntity<String> randomChat(@RequestHeader("username") String username,
                                         @RequestParam String message) {
    // 1. busca semântica no vector store
    SearchRequest searchRequest = SearchRequest.builder()
            .query(message)
            .topK(3)                  // traz os 3 trechos mais próximos
            .similarityThreshold(0.5) // descarta os pouco relevantes (0..1)
            .build();
    List<Document> similarDocs = vectorStore.similaritySearch(searchRequest);

    // 2. monta o contexto a partir dos trechos recuperados
    String similarContext = similarDocs.stream()
            .map(Document::getText)
            .collect(Collectors.joining(System.lineSeparator()));

    // 3. injeta o contexto no system prompt (template) e gera a resposta
    String answer = chatClient.prompt()
            .system(spec -> spec.text(promptTemplate).param("documents", similarContext))
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, username)) // reusa o chat memory client
            .user(message)
            .call().content();

    return ResponseEntity.ok(answer);
}
```

Parâmetros da busca:
- **`topK`** → quantos trechos trazer (mais = mais contexto, porém mais tokens).
- **`similarityThreshold`** → corte de relevância (0 a 1). Acima do valor entra; abaixo é descartado. Evita injetar lixo quando nada é realmente parecido.

### O template que "amarra" o LLM ao contexto
A peça que transforma busca + LLM em RAG de verdade é a instrução no system prompt — responder **só** com base nos documentos:

```
You are a helpful assistant, answering questions based on the given context in the
DOCUMENTS section and no prior knowledge. If the answer is not in the DOCUMENTS section,
then reply with "I don't know".

DOCUMENTS:
----------
{documents}
----------
```

Isso reduz **alucinação**: sem contexto relevante (busca não passou do threshold), o modelo é orientado a dizer *"I don't know"* em vez de inventar.

### Pontos de atenção
- **Custo de embeddings:** tanto a ingestão quanto cada pergunta chamam o modelo de embedding (gera tokens/custo à parte do chat).
- **Qualidade depende do retrieval:** se a busca traz o trecho errado (ou nada), a resposta degrada — RAG é "garbage in, garbage out".
- **`topK`/`threshold` são tuning:** valores altos demais inflam tokens; baixos demais perdem contexto.
- **Reuso do `chatMemoryChatClient`:** este controller injeta o mesmo client da seção 7 (`@Qualifier("chatMemoryChatClient")`), então a conversa RAG **também tem memória** por `username`.

---

## Resumo geral (default vs. por requisição)

| Conceito        | Global (no Builder)      | Pontual (no `prompt()`) |
|-----------------|--------------------------|-------------------------|
| System prompt   | `defaultSystem(...)`     | `.system(...)`          |
| Advisors        | `defaultAdvisors(...)`   | `.advisors(...)`        |
| Chat Options    | `defaultOptions(...)` / `application.properties` | `.options(...)` |
| User + template | —                        | `.user(u -> u.text(...).param(...))` |

- **Prompt Template** = estrutura + variáveis (`{x}` + `.param`).
- **Stuffing** = injetar contexto/documento dentro do prompt (simples, mas não escala → RAG).
- **System / defaultSystem** = papel e regras do assistente (por chamada vs. global).
- **Advisors / defaultAdvisors** = interceptadores da cadeia (por chamada vs. global); ex. auditoria de tokens e logging.
- **Chat Options** = parâmetros de geração (`model`, `temperature`, `topP`/`topK`, `frequencyPenalty`, `presencePenalty`, `maxTokens`, `stopSequences`); global vs. por chamada.
- **Structured Output** = resposta convertida direto em objeto Java (`.entity(...)` via `BeanOutputConverter`).
- **Chat Memory** = histórico da conversa via `MessageChatMemoryAdvisor` + `ChatMemory`, isolado por `CONVERSATION_ID` (aqui o `username`); torna o LLM "com memória". Trocar o `ChatMemoryRepository` (in-memory → JDBC/H2 `file:`) faz o histórico **persistir entre reinícios**, sem mexer no código.
- **RAG** = busca semântica (`vectorStore.similaritySearch`, `topK`/`threshold`) num **vector store** (Qdrant) + injeção dos trechos relevantes no system prompt; evolução do **Stuffing** que escala e reduz alucinação ("responda só pelos DOCUMENTS").
