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
>
> ⚠️ **Isso hoje só acontece com o profile `rag` ativo** (ver seção 12). Por padrão o RAG fica desligado: `spring.docker.compose.enabled=false` e `initialize-schema=false`, pra o boot ser rápido quando não estou mexendo em RAG.

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

## 9. RAG modular / declarativo (`RetrievalAugmentationAdvisor`)

A seção 8 fez RAG **na mão**: o controller chamava `vectorStore.similaritySearch`, montava o contexto e injetava no template. Funciona, mas mistura a lógica de RAG dentro do endpoint.

O Spring AI oferece o **`RetrievalAugmentationAdvisor`**: um advisor (seção 4) que faz todo o pipeline de RAG sozinho, antes da chamada ao modelo. O controller volta a ser só `.user(message).call()` — toda a recuperação acontece na cadeia de advisors.

### O pipeline em 3 fases (módulos plugáveis)

| Fase | Interface | O que faz | Implementação neste projeto |
|------|-----------|-----------|-----------------------------|
| **Pré-retrieval** | `QueryTransformer` | Reescreve a **query** antes da busca | `TranslationQueryTransformer` (traduz p/ inglês) |
| **Retrieval** | `DocumentRetriever` | Busca os documentos relevantes | `VectorStoreDocumentRetriever` (Qdrant) e o custom `WebSearchDocumentRetriever` (Tavily) |
| **Pós-retrieval** | `DocumentPostProcessor` | Trata os docs recuperados antes de injetar | `PIIMaskingDocumentPostProcessor` (mascara PII) |

```java
@Bean
RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(VectorStore vectorStore,
                                                          ChatClient.Builder chatClientBuilder) {
    return RetrievalAugmentationAdvisor.builder()
            .queryTransformers(TranslationQueryTransformer.builder()
                    .chatClientBuilder(chatClientBuilder.clone())   // chama o LLM p/ traduzir
                    .targetLanguage("english")
                    .build())
            .documentRetriever(VectorStoreDocumentRetriever.builder()
                    .vectorStore(vectorStore)
                    .topK(3)
                    .similarityThreshold(0.5)
                    .build())
            .documentPostProcessors(PIIMaskingDocumentPostProcessor.builder())
            .build();
}
```

> Registrado como **default advisor** no `chatMemoryChatClient`, junto com memory, logger e auditoria de tokens. O `topK`/`similarityThreshold` que antes ficavam no `SearchRequest` agora vivem no `VectorStoreDocumentRetriever`.

### 9.1 Ingestão de PDF — `TikaDocumentReader` + `TokenTextSplitter`

A seção 8 ingeria frases soltas (`RandomDataLoader`, hoje com `@Component` comentado). O caso real carrega um **PDF** (`HRPolicyLoader`):

```java
@PostConstruct
public void loadPDF() {
    TikaDocumentReader tikaReader = new TikaDocumentReader(policyFile); // PDF/DOCX/HTML... -> texto
    List<Document> documents = tikaReader.get();

    TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .withChunkSize(100)        // ~100 tokens por chunk
            .withMaxNumChunks(500)
            .build();

    vectorStore.add(textSplitter.split(documents));   // quebra -> embeddings -> Qdrant
}
```

- **`TikaDocumentReader`** (Apache Tika) extrai texto de PDF, DOCX, HTML, etc. → vira `Document`.
- **`TokenTextSplitter`** quebra cada `Document` em **chunks por contagem de tokens** (não de caracteres).

> 💡 **Por que o splitter reduz os tokens do prompt** (observação medida no estudo: ~1200 sem splitter vs ~500 com): sem splitter, o PDF inteiro vira poucos `Document`s gigantes, e o retriever traz blocos enormes (muito texto irrelevante) pro contexto. Com splitter, o índice tem chunks pequenos e coesos → o `topK` traz só os pedaços realmente relevantes à pergunta. Bônus: chunk pequeno gera um **embedding mais "focado"** (representa um assunto só), melhorando a precisão da busca.
>
> Trade-off: `chunkSize` pequeno demais fragmenta ideias (frase cortada perde sentido); grande demais volta a inflar tokens. `chunkOverlap` evita cortar exatamente na fronteira de uma ideia.

### 9.2 Query Transformers — busca cross-lingual

Problema: o PDF está em **inglês**, mas o usuário pergunta em **português**. A busca vetorial compara *vetores de significado*, e os embeddings da OpenAI são multilíngues — então PT já recupera chunks EN razoavelmente. Mas a similaridade cross-lingual é mais fraca que monolíngue.

O **`TranslationQueryTransformer`** resolve traduzindo a query do usuário para o idioma do índice (inglês) **antes** da busca, deixando o retrieval monolíngue (mais preciso). Custo: uma chamada extra e barata ao LLM por pergunta.

> Detalhe importante: o idioma da **query** afeta o *retrieval* (a busca); o idioma da **resposta** é controlado pelo *prompt*. São independentes — dá pra buscar em EN e responder em PT. Outro transformer útil é o `CompressionQueryTransformer`, que usa o histórico de memória pra condensar a conversa numa query autocontida.

### 9.3 Retrieval da Web — `DocumentRetriever` customizado

O `DocumentRetriever` não precisa ser um vector store. O `WebSearchDocumentRetriever` implementa a interface fazendo uma **busca web ao vivo** (API Tavily) e mapeando cada resultado num `Document`:

```java
public class WebSearchDocumentRetriever implements DocumentRetriever {
    @Override
    public List<Document> retrieve(Query query) {
        // POST p/ Tavily com query.text() -> mapeia hits em Document (text + metadata url/title + score)
    }
}
```

Usado no `webSearchRAGChatClient` (`WebSearchRAGChatClientConfig`) através do mesmo `RetrievalAugmentationAdvisor` — só troca o `documentRetriever`. Exposto em `GET /api/rag/web-search/chat`.

> Requer a env var `TAVILY_SEARCH_API_KEY`. Mostra a força da abstração: **a fonte de conhecimento é plugável** — vector store, web, banco, API — sem mudar o resto do pipeline.

### 9.4 Post-Processors — mascaramento de PII

O `DocumentPostProcessor` roda **depois** do retrieval e **antes** de injetar os docs no prompt. O `PIIMaskingDocumentPostProcessor` usa regex pra trocar e-mails e telefones por `[REDACTED_EMAIL]` / `[REDACTED_PHONE]`, evitando vazar dado sensível recuperado pro LLM:

```java
return documents.stream()
        .map(document -> document.mutate()
                .text(maskSensitiveInformation(document.getText()))
                .metadata("pii_masked", true)
                .build())
        .toList();
```

> Outros usos de post-processor: re-ranking, deduplicação, truncamento, filtro por metadata.

### Pontos de atenção (RAG modular)
- **Controller limpo**: toda a lógica de RAG saiu do endpoint e virou configuração de advisor (compare com o bloco comentado em `RAGController`).
- **Chamadas extras ao LLM**: cada `QueryTransformer` que usa LLM (tradução/compressão) é uma chamada a mais por pergunta — custo e latência.
- **Memory + RAG competem por tokens**: o `MessageChatMemoryAdvisor` injeta histórico bruto (cego a relevância) no mesmo prompt do contexto RAG; refinar o RAG não reduz o peso da memória (ver seção 7 — janela menor ou resumo).

---

## 10. Semantic Cache (vector store / Qdrant)

Cache comum (chave-valor) só acerta com a **mesma string exata**. "Qual a política de férias?" e "Como funcionam as férias?" seriam dois misses. O **Semantic Cache** acerta por **significado**: ele guarda o *embedding* da pergunta e, numa nova pergunta semanticamente parecida, devolve a resposta cacheada **sem chamar o LLM**.

### Para que serve
- **Economiza tokens/custo e latência**: hit = zero chamada ao modelo.
- Ideal pra FAQ / perguntas recorrentes reformuladas de jeitos diferentes.

### Backend plugável: Redis OU vector store
O `DefaultSemanticCache` aceita **dois backends** pra guardar os pares (embedding da pergunta → resposta):
- `.jedisClient(redisClient)` → guarda no **Redis** (precisa do serviço Redis no `compose.yml`).
- `.vectorStore(vectorStore)` → guarda num **vector store** qualquer (aqui o **Qdrant**, que já usamos no RAG).

Neste projeto **migramos de Redis para Qdrant** — faz sentido, já que o Qdrant já está de pé pro RAG e evita subir outra peça de infra só pro cache. Os beans de Redis ficaram comentados no `SemanticCacheConfig`.

> Curiosidade: mesmo usando Qdrant, o `DefaultSemanticCache` vem do artefato `spring-ai-redis-semantic-cache` (`pom.xml`) — o nome é histórico; a classe suporta os dois backends.

### Como é montado (`SemanticCacheConfig`)

```java
// 1. um vector store DEDICADO ao cache, em collection PRÓPRIA (separada da do RAG)
@Bean("cacheVectorStore")
VectorStore cacheVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
    return QdrantVectorStore.builder(qdrantClient, embeddingModel)
            .collectionName("semantic-cache")   // != "teste" (a collection do RAG)
            .initializeSchema(true)
            .build();
}

// 2. o cache apontando pro vector store do cache
@Bean
SemanticCache semanticCache(@Qualifier("cacheVectorStore") VectorStore vectorStore,
                            EmbeddingModel embeddingModel) {
    return DefaultSemanticCache.builder()
            .vectorStore(vectorStore)
            .embeddingModel(embeddingModel)     // gera o embedding da pergunta
            .similarityThreshold(0.8)           // só acerta se for parecida o bastante
            .build();
}

@Bean
public SemanticCacheAdvisor semanticCacheAdvisor(SemanticCache semanticCache) {
    return SemanticCacheAdvisor.builder().cache(semanticCache).build();
}
```

> ⚠️ **Collection separada é essencial.** O cache e o RAG **não podem dividir a mesma collection** — senão as perguntas+respostas do cache virariam "documentos" recuperáveis pelo RAG (e vice-versa), poluindo os dois. Por isso o `cacheVectorStore` usa `collectionName("semantic-cache")`, distinta da `teste` do RAG, e é injetado por `@Qualifier` pra não conflitar com o `VectorStore` principal.

### O ciclo (é um advisor — seção 4)
1. Chega a pergunta → o `SemanticCacheAdvisor` gera o embedding e busca na collection `semantic-cache` por uma pergunta anterior com similaridade **≥ 0.8**.
2. **Hit** → devolve a resposta cacheada e **curto-circuita a cadeia** (o LLM nem é chamado).
3. **Miss** → segue pro modelo normalmente e, no retorno, **grava** (embedding da pergunta → resposta) no vector store pra próxima vez.

### Onde está registrado
Como default advisor em dois clients:
- `openChatClient` (`OpenChatClientConfig`): logger + auditoria de tokens + cache. Exposto em `GET /api/open-chat` (`OpenChatController`).
- `chatMemoryChatClient` (`ChatMemoryChatClientConfig`): junto com memory e RAG.

### Pontos de atenção
- **`similarityThreshold` (0.8)** é o tuning crítico: baixo demais devolve resposta de uma pergunta *parecida mas diferente* (falso positivo); alto demais quase nunca acerta o cache. Mais permissivo que a versão Redis anterior (0.9).
- **Confirmar o hit no `TokenUsageAuditAdvisor`**: num hit os tokens de completion caem a zero (não houve geração) — ótima forma de ver o cache agindo.
- **Cache + Memory/RAG é delicado**: o cache curto-circuita antes do modelo, então uma resposta cacheada pode **ignorar o contexto da conversa atual** (memória) ou docs recém-recuperados. Faz mais sentido em perguntas "stateless" (como o `openChatClient`); combinar com memória pede cautela.
- **Custo de embedding no miss/hit**: toda pergunta gera um embedding (chamada ao modelo de embedding) pra poder buscar no cache — barato perto de uma geração, mas não é zero.
- **Invalidação**: respostas cacheadas não expiram sozinhas aqui — se a base/política muda, o cache pode servir resposta velha (TTL/invalidação ficariam a cargo do vector store/config).

---

## 11. Tool Calling (function calling)

Tool calling deixa o LLM **pedir para executar código seu** durante a geração. O modelo não roda nada — ele só decide *qual* função chamar e com *quais* argumentos; quem executa é a aplicação, que devolve o resultado pro modelo continuar a resposta.

### Como é montado neste projeto
Métodos Java anotados com `@Tool` num bean (`TimeTools`), registrados como default no `timeChatClient`:

```java
@Component
public class TimeTools {
    @Tool(name = "getCurrentLocalTime", description = "Get the current time in the user's timezone")
    String getCurrentLocalTime() { ... }

    @Tool(name = "getCurrentTime", description = "Get the current time in the specified time zone.")
    public String getCurrentTime(@ToolParam(description = "Value representing the time zone") String timeZone) { ... }
}
```

```java
// TimeChatClientConfig
chatClientBuilder.defaultTools(timeTools).defaultAdvisors(...).build();
```

Exposto em `GET /api/tools/local-time` (`TimeController`).

### Como o modelo "sabe" das tools — **não** é no prompt
Ponto que confunde: as tools **não** entram no texto do system/user prompt. O Spring AI usa reflection sobre os `@Tool` e monta, pra cada uma, um `ToolDefinition` (**nome + description + JSON Schema dos parâmetros**) que vai num **campo separado** da requisição HTTP — o array `tools` do endpoint *Chat Completions* da OpenAI. A `description` da anotação é literalmente o que o modelo lê pra decidir quando usar a tool.

```json
"tools": [
  { "type": "function", "function": {
      "name": "getCurrentTime",
      "description": "Get the current time in the specified time zone.",
      "parameters": { "type": "object",
        "properties": { "timeZone": { "type": "string", "description": "Value representing the time zone" } },
        "required": ["timeZone"] } } }
]
```

### O round-trip (dentro de um único `.call()`)
1. Requisição vai com o prompt **+** o array `tools`.
2. Modelo responde `finish_reason: "tool_calls"` com `{name, arguments}` (ainda **não** é texto).
3. Spring AI casa o nome com o método Java, executa (dá pra ver o `LOGGER.info` da tool disparar) e devolve o retorno numa mensagem de role `tool`.
4. Segunda chamada ao modelo com o resultado → aí vem o texto final.

### Como observar o array `tools` na prática
O `SimpleLoggerAdvisor` loga no nível do `ChatClient` e **não** mostra bem as tools (são adicionadas na camada HTTP). Pra ver o payload cru, um `RestClientCustomizer` com `requestInterceptor` logando o corpo da requisição mostra o JSON indo pra `api.openai.com`, com o array `tools` dentro.

### Pontos de atenção
- **Description é tudo**: description ruim = o modelo não sabe quando chamar a tool ou passa argumento errado. `@ToolParam(description=...)` documenta cada parâmetro no schema.
- **Duas chamadas ao modelo** por tool call (antes e depois de executar) = mais tokens/latência que um chat simples.

---

## 12. Ligar/desligar o RAG (Spring Profile `rag`)

O RAG (Qdrant + docker-compose + ingestão de PDF) deixava o startup lento e exigia o Qdrant no ar. Como os beans de RAG estão **entrelaçados** em vários `ChatClient` (o `chatMemoryChatClient` e o `openChatClient` dependem do `semanticCacheAdvisor`/`VectorStore`), não dava pra desligar só o loader — o boot quebrava sem Qdrant. Solução: agrupar tudo que é RAG atrás do profile `rag`, **desligado por padrão**.

- `@Profile("rag")` em: `SemanticCacheConfig`, `ChatMemoryChatClientConfig`, `OpenChatClientConfig`, `WebSearchRAGChatClientConfig`, `HRPolicyLoader`, `RAGController`, `OpenChatController`, `ChatMemoryController`.
- O bean `chatMemory` foi extraído para `ChatMemoryConfig` (**sempre ativo**), porque o `timeChatClient` (tool calling) usa memória sem precisar de RAG.
- `application.properties` (padrão): `spring.docker.compose.enabled=false` e `qdrant.initialize-schema=false` → não sobe Docker nem conecta no Qdrant.
- `application-rag.properties`: religa `docker.compose.enabled=true` e `initialize-schema=true`.

| Modo | Comando |
|------|---------|
| RAG **ligado** | `./mvnw spring-boot:run -Dspring-boot.run.profiles=rag` |
| RAG **desligado** (padrão) | `./mvnw spring-boot:run` |

Com o RAG desligado seguem funcionando `/api/chat`, `/api/stream`, structured output, prompt template/stuffing e `/api/tools/local-time`. Ficam desabilitados `/api/rag/**`, `/api/open-chat` e `/api/chat-memory`. Bônus: o teste `contextLoads` sobe sem precisar do Qdrant.

---

## 13. Tool Calling com banco de dados + `ToolContext` (help desk)

Exemplo mais completo de tool calling: um assistente de **help desk** cujas tools **operam no banco** (criam e consultam tickets via JPA). Sobe o degrau de "tool que só lê a hora" (seção 11) para "tool que executa efeito colateral real na aplicação".

### As peças
- **Tools** (`HelpDeskTools`): `createTicket` e `getTicketStatus`, anotadas com `@Tool`, injetam o `HelpDeskTicketService`.
- **Persistência**: `HelpDeskTicket` (`@Entity`) + `HelpDeskTicketRepository` (`JpaRepository`) + `HelpDeskTicketService`. Banco H2 (mesmo do chat memory), tabela criada via `spring.jpa.hibernate.ddl-auto=update`.
- **Client**: `helpDeskChatClient` (`HelpDeskChatClientConfig`) com system prompt próprio (`helpDeskSystemPromptTemplate.st`) + memória + as `TimeTools`.
- **Endpoint**: `GET /api/tools/help-desk` (`HelpDeskController`).

### `ToolContext` — dado que o modelo **não** vê
O ponto-chave desse exemplo. O `username` **não** entra no schema da tool nem no prompt: ele é injetado no `ToolContext` pelo controller e lido dentro da tool.

```java
// Controller — injeta dado fora do alcance do modelo
chatClient.prompt()
    .user(message)
    .tools(helpDeskTools)
    .toolContext(Map.of("username", username))
    .call().content();

// Tool — recebe o ToolContext como parâmetro extra
@Tool(name = "createTicket", description = "Create the Support Ticket", returnDirect = true)
String createTicket(@ToolParam(description = "Details to create a Support ticket") TicketRequest ticketRequest,
                    ToolContext toolContext) {
    String username = (String) toolContext.getContext().get("username");
    ...
}
```

Contraste com o que **vai** no schema: o `TicketRequest` (o campo `issue`) é `@ToolParam`, então **é o modelo quem preenche** a partir da conversa. Ou seja:
- **Argumentos `@ToolParam`** = preenchidos pelo LLM (entram no JSON Schema enviado à OpenAI).
- **`ToolContext`** = preenchido pela aplicação, **invisível ao modelo** — ele não vê nem consegue forjar. Ideal para identidade/tenant/permissões (segurança: o LLM não escolhe o `username` do dono do ticket).

### `returnDirect`
`createTicket` usa `@Tool(returnDirect = true)`: o retorno da tool vira **a resposta final**, sem uma segunda ida ao modelo. Contraria o round-trip normal (seção 11, passo 4) — útil quando o resultado da tool já é a resposta pronta (ex.: "Ticket #12 criado") e não vale gastar mais uma geração. `getTicketStatus` não usa, então a lista de tickets volta pro modelo redigir a resposta.

### Pontos de atenção
- **Efeito colateral real**: diferente de uma tool "read-only", `createTicket` grava no banco. Description precisa ser clara pro modelo chamar na hora certa (e não criar ticket à toa).
- **Objeto como parâmetro**: um `record` (`TicketRequest`) vira um objeto no JSON Schema — o modelo monta o objeto a partir da conversa.
- **Tools registradas em dois níveis**: as `TimeTools` são default no client (`defaultTools`), e as `HelpDeskTools` são passadas por requisição (`.tools(...)`) — dá pra combinar tools globais e pontuais.

---

## 14. MCP (Model Context Protocol)

MCP é um **protocolo aberto** que padroniza como uma aplicação dá ao LLM acesso a **tools, dados e contexto externos**. É o passo além do **Tool Calling** (seções 11 e 13): lá as `@Tool` viviam **dentro** da app; com MCP as capacidades ficam num **servidor separado**, publicadas uma vez e consumidas por qualquer host/LLM — sem reimplementar em cada app.

### Arquitetura — Host, Client, Server

| Papel | O que é | Neste estudo |
|-------|---------|--------------|
| **Host** | A aplicação que roda o LLM e quer usar capacidades externas | `mcpclient` (app Spring AI + OpenAI) |
| **Client** | O conector **dentro** do host; **1 client = 1 conexão com 1 server** (um host pode ter vários) | auto-configurado pelo `spring-ai-starter-mcp-client` |
| **Server** | Expõe as capacidades (aqui, **tools**) via o protocolo | `mcpserverstdio` e `mcpserverremote` |

### Os dois transports

Como client e server trocam mensagens (sempre **JSON-RPC**), por baixo:

| Transport | Onde roda o server | Como conversa | Quando |
|-----------|--------------------|---------------|--------|
| **stdio** | processo **local**, **subido pelo próprio client** (`command` + `args`) | JSON-RPC por **stdin/stdout** | server local, mesma máquina |
| **streamable http** | serviço **web** já no ar (local ou remoto), numa porta | JSON-RPC sobre **HTTP** (com streaming) | server externo/compartilhado |

> ⚠️ No **stdio**, o **stdout é o canal do protocolo** — o server **não pode logar/printar no stdout**, senão corrompe o JSON-RPC. Daí `banner-mode=off` e log em arquivo/stderr.

### 14.1 O Client (`mcpclient`)

Deps: `spring-ai-starter-mcp-client` + `spring-ai-starter-model-openai` (+ webmvc pro endpoint REST). Cada **transport** é declarado de um jeito:

**stdio** → num JSON no **formato do Claude Desktop** (`mcp-servers.json`). Hoje só sobra o `filesystem`; o server stdio de help desk (`mcpserverstdio`) e o `github` (docker) já foram removidos daqui:

```json
// mcp-servers.json
{ "mcpServers": {
    "filesystem": { "command": "cmd", "args": ["/c","npx","-y","@modelcontextprotocol/server-filesystem","C:\\Users\\Artur\\mcp"] }
} }
```

**streamable HTTP** → o server remoto **não** entra no JSON; conecta por properties. A conexão recebe um **nome** (`artur`) que reaparece mais tarde nos handlers de callback (`@McpSampling(clients="artur")`, `@McpProgress`, etc. — ver §14.6+):

```properties
spring.ai.mcp.client.stdio.servers-configuration=classpath:mcp-servers.json

# conexao streamable-http para o mcpserverremote; "artur" e o NOME da conexao
spring.ai.mcp.client.streamable-http.connections.artur.url=http://localhost:8090
spring.ai.mcp.client.streamable-http.connections.artur.endpoint=mcp

spring.ai.mcp.client.request-timeout=60s   # cold start do npx passava dos 20s default
```

> ⚠️ **Dois nomes que confundem:** o **nome da conexão** (`artur`, definido no client) é diferente do **nome que o server anuncia** (`spring.ai.mcp.server.name=helpdesk-mcp-server`, definido no `mcpserverremote`). Os handlers de callback casam pelo **nome da conexão** (`clients="artur"`); o filtro/seleção de tools casa pelo **nome do server** (`"helpdesk-mcp-server"` — ver §14.5).

No boot, o Spring AI **sobe/conecta cada server**, faz o **handshake** e **descobre as tools**. A forma mais simples de expor essas tools ao LLM é pegar o `ToolCallbackProvider` auto-configurado e registrar como default:

```java
// abordagem inicial (global): todas as tools de todos os servers viram default
public MCPClientController(ChatClient.Builder builder, ToolCallbackProvider toolCallbackProvider) {
    this.chatClient = builder
            .defaultTools(toolCallbackProvider)   // as tools MCP viram tools normais
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();
}
```

> **Ligação com a seção 11:** as tools MCP entram no **mesmo array `tools`** da requisição à OpenAI. Pro LLM é indistinguível de uma `@Tool` local — ele nem sabe que veio de um server MCP. MCP muda **de onde vem** a tool, não como o modelo a usa.

> 🔄 **Evolução:** o controller **saiu** desse `.defaultTools(provider)` global e passou a injetar a lista de `McpSyncClient` e **escolher as tools por requisição** (com um filtro global por cima). Ver §14.5.

### 14.2 Server via stdio (`mcpserverstdio`)

- Deps: `spring-ai-starter-mcp-server-webmvc`, mas `spring.main.web-application-type=none` → roda como **processo stdio puro** (sem porta web).
- Tools anotadas com **`@McpTool` / `@McpToolParam`** (`org.springframework.ai.mcp.annotation`) — o equivalente MCP do `@Tool`/`@ToolParam` local. Aqui é o mesmo **help desk** da seção 13 (`createTicket`/`getTicketStatus`), persistindo em H2 via JPA.
- O client o inicia via `command: java.exe, args: [-jar, ...jar]` (ver `mcp-servers.json`).

### 14.3 Server via streamable HTTP (`mcpserverremote`)

**As mesmas tools**, trocando só o transport:

```properties
spring.ai.mcp.server.protocol=streamable
server.port=8090
```

Diferença central pro stdio: sobe **uma vez** como web service e **vários clients** conectam por **URL** — **não** há "um processo por client". É o modelo pra um MCP **compartilhado/remoto**.

### 14.4 Pontos de atenção (troubleshooting vivido no estudo)

- **stdio = 1 processo por client.** Cada client dá o seu `java -jar` e cria a **sua** instância do server. Rodar o **MCP Inspector** e o **app** ao mesmo tempo = duas instâncias do mesmo server → colidem no arquivo H2 `./chatmemory`; a segunda emperra o boot → o client não recebe o `initialize` e estoura o **timeout de inicialização (20s)**: `Did not observe any item ... within 20000ms in 'map'`. **Regra: um dono do server por vez.** (`request-timeout` é outro timeout — não cobre esse.)
- **Windows trava o jar.** Instância sobrando **segura o arquivo** → `mvn clean`/`repackage` falham (`Unable to rename ... .jar.original` / *"arquivo já está sendo usado"*). No Linux/Mac não daria — é peculiaridade do Windows. Matar o processo resolve (função `killjava <trecho>` no bash).
- **`mvnw` usa o `JAVA_HOME`, não o `java` do PATH.** Dava `release version 25 not supported` **mesmo com `java -version` = 25**, porque o `JAVA_HOME` apontava pro JDK 17. `mise activate` no profile do shell mantém o `JAVA_HOME` alinhado ao `mise.toml`.
- **`@McpTool` que grava no banco precisa da tabela.** Sem `spring.jpa.hibernate.ddl-auto=update`, o H2 **file** sobe vazio → o insert quebra com `Table "HELPDESK_TICKETS" not found` (o `create-drop` automático só vale pra H2 **em memória**).

> 🧭 **As capacidades avançadas a seguir (§14.5–14.8) foram feitas só no `mcpserverremote`** (streamable HTTP). O `mcpserverstdio` continua com as duas tools originais (`createTicket`/`getTicketStatus` sem `McpSyncRequestContext`) — os dois servers **divergiram**.

### 14.5 Filtro e seleção de tools (client)

Nem toda tool descoberta precisa chegar ao LLM. Há **dois níveis** de controle, em momentos diferentes:

**Filtro global (na descoberta)** — `McpServerToolFilter implements McpToolFilter`. O Spring AI chama o `test(...)` **uma vez por tool** ao descobrir os servers; retornar `false` faz a tool **nem existir** pro resto da app. Decide pelo **nome do server** (`McpConnectionInfo.initializeResult().serverInfo().name()`) ou pelo nome da tool:

```java
@Component
public class McpServerToolFilter implements McpToolFilter {
    @Override
    public boolean test(McpConnectionInfo info, McpSchema.Tool tool) {
        String server = info.initializeResult().serverInfo().name();
        if (server.toLowerCase().contains("github")) return false; // bloqueia server inteiro
        if (tool.name().contains("write_")) return false;          // bloqueia tools de escrita
        return true;
    }
}
```

**Seleção por requisição** — `ToolUtil.selectToolsFor(mcpClients, serverName, toolName)`. Em vez de mandar **todas** as tools em toda chamada, percorre a lista de `McpSyncClient`, filtra por nome de server/tool (hint `null`/vazio = "casa tudo") e monta um `ToolCallback[]` via `SyncMcpToolCallback`. Por isso o controller passou a injetar `List<McpSyncClient>` no lugar do `ToolCallbackProvider`:

```java
// escolhe só as tools do "helpdesk-mcp-server" pra ESTA chamada
ToolCallback[] toolCallbacks = ToolUtil.selectToolsFor(mcpClients, "helpdesk-mcp-server");
chatClient.prompt().tools(toolCallbacks).user(...).call().content();
```

> **Filtro global vs. seleção por request:** o filtro é **política fixa** aplicada na descoberta (a tool some pra sempre); a seleção decide **caso a caso** quais tools mandar naquela requisição. O `serverName` usado na seleção é o **nome que o server anuncia** (`helpdesk-mcp-server`), não o nome da conexão (`artur`).

### 14.6 Notificações do server → client: progress + logging

Até aqui o fluxo era só client→server (chama a tool, recebe o resultado). Agora o **server manda mensagens de volta** enquanto processa. A porta de entrada no server é o `McpSyncRequestContext` (`ctx`), um **parâmetro extra** na `@McpTool`:

```java
List<HelpDeskTicket> getTicketStatus(@McpToolParam(...) String username,
                                     McpSyncRequestContext ctx) throws InterruptedException {
    ctx.info("Fetching tickets for user: " + username);           // LOGGING p/ o client
    // ... busca ...
    for (int i = 0; i <= 10; i++) {                               // PROGRESS 0..100%
        Thread.sleep(1000);
        int percent = i * 100 / 10;
        ctx.progress(spec -> spec.progress(percent).message(percent + "% completed!"));
    }
    return tickets;
}
```

No **client**, dois beans escutam essas notificações (casando pelo **nome da conexão**, `clients="artur"`):

| Notificação | Server envia | Client escuta |
|-------------|--------------|---------------|
| Logging | `ctx.info(...)` | `@McpLogging` → `HelpDeskLogBridge` |
| Progress | `ctx.progress(...)` | `@McpProgress` → `HelpDeskToolProgressListener` |

> **`progressToken`:** o client injeta um token por requisição via `.toolContext(Map.of("progressToken", UUID.randomUUID()...))`; é ele que **correlaciona** as notificações de progresso àquela chamada específica (aparece no `ProgressNotification.progressToken()`).

### 14.7 Sampling — o server pede um completion ao client

**Inversão de papéis:** normalmente o client (que tem a chave da OpenAI e o LLM) chama o server. No **sampling**, o **server** pede ao **client** que rode um completion de LLM por ele — o server usa a "inteligência" do host **sem ter chave/modelo próprio**.

No server, a tool `summarizeTickets` monta um prompt e chama `ctx.sample(...)`:

```java
if (!ctx.sampleEnabled()) return tickets.toString();   // client não anunciou a capability -> fallback
McpSchema.CreateMessageResult result = ctx.sample(spec -> spec
        .systemPrompt(systemPrompt)
        .message("Here are the support tickets ...\n" + ticketData));
String summary = ((McpSchema.TextContent) result.content()).text();
```

No client, o handler é `@McpSampling` (`HelpDeskSamplingProvider`): traduz o `CreateMessageRequest` do MCP em `Prompt` do Spring AI e chama o **`ChatModel` direto** (`chatModel.call(prompt)`), **não** o `ChatClient` com tools — assim o completion do sampling **não re-dispara** as tools MCP num loop.

> **Endpoint `/api/summarize-tickets`:** quem dirige é o LLM do chat — ele vê a tool `summarizeTickets` e decide chamá-la; a tool (no server) chama de volta o client via sampling pra gerar o texto. O system prompt do endpoint manda devolver a saída da tool **verbatim**, pra não reescrever o resumo já pronto.

### 14.8 Elicitation — o server pede dados ao usuário (via client)

Parente do sampling, mas em vez de pedir um **completion de LLM**, o server pede um **dado estruturado ao usuário humano**. Antes de abrir o ticket, `createTicket` pede `priority` + `contactPhone`:

```java
if (ctx.elicitEnabled()) {
    StructuredElicitResult<TicketContactInfo> r = ctx.elicit(
            spec -> spec.message("Choose a priority (LOW/MEDIUM/HIGH/URGENT) and a contact phone."),
            TicketContactInfo.class);          // o record vira o 'requestedSchema' JSON
    if (r.action() == McpSchema.ElicitResult.Action.ACCEPT && r.structuredContent() != null) {
        // usa r.structuredContent().priority() / .contactPhone()
    }
} // senão: fallback pros defaults (MEDIUM / N/A)
```

O `record TicketContactInfo(String priority, String contactPhone)` é convertido no schema que o client deve preencher, e a resposta do client é mapeada **de volta** no record. No client, `@McpElicitation` (`HelpDeskElicitationProvider`) responde — no estudo simula o preenchimento retornando `ACCEPT` com dados fixos. Ações possíveis: **ACCEPT / DECLINE / CANCEL** (as duas últimas caem no fallback).

> **Sampling vs. elicitation** (ambos = server chamando **de volta** o client): sampling pede **completion de LLM**; elicitation pede **dado estruturado ao usuário**. Efeito colateral no modelo: `HelpDeskTicket` ganhou os campos `priority` e `contactPhone`, e `service.createTicket(...)` agora recebe esses valores.

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
- **RAG modular** = o mesmo RAG feito pelo `RetrievalAugmentationAdvisor` (controller fica limpo), com pipeline plugável: `QueryTransformer` (pré — ex. `TranslationQueryTransformer` p/ cross-lingual) → `DocumentRetriever` (vector store **ou** web/Tavily) → `DocumentPostProcessor` (pós — ex. PII masking). Ingestão de PDF via `TikaDocumentReader` + `TokenTextSplitter` (chunks por token reduzem os tokens do prompt).
- **Semantic Cache** = cache por **significado** (`SemanticCacheAdvisor` + embedding + `similarityThreshold`); hit devolve a resposta sem chamar o LLM (economiza tokens/latência), miss grava p/ a próxima. Backend plugável (Redis **ou** vector store) — aqui no **Qdrant**, em collection **separada** (`semantic-cache`) da do RAG.
- **Tool Calling** = o LLM pede pra executar um método `@Tool` seu (ex. `TimeTools`), decidindo nome + argumentos; a app roda e devolve o resultado pro modelo finalizar. As tools vão num **campo separado** da requisição (array `tools` = nome + description + JSON Schema), **não** no texto do prompt; são **duas** idas ao modelo por chamada.
- **Toggle de RAG** = tudo que é RAG (Qdrant, docker-compose, ingestão, clients que dependem de `VectorStore`/cache) fica atrás do profile `rag`, **off por padrão** pra boot rápido; liga com `-Dspring-boot.run.profiles=rag`.
- **Tool Calling + DB / `ToolContext`** = tools que operam no banco (help desk: `createTicket`/`getTicketStatus` via JPA). Argumentos `@ToolParam` (ex. `TicketRequest`) são preenchidos pelo **LLM** (vão no schema); o `ToolContext` (ex. `username`) é preenchido pela **app** e **invisível ao modelo** (bom pra identidade/segurança). `@Tool(returnDirect=true)` faz o retorno da tool ser a resposta final (sem 2ª ida ao modelo).
- **MCP (Model Context Protocol)** = tool calling com as capacidades **num server separado**, não dentro da app. Papéis **Host** (a app/LLM) → **Client** (conector, 1:1 com um server) → **Server** (expõe as tools). Dois transports: **stdio** (server local subido **pelo próprio client** como processo, JSON-RPC por stdin/stdout — 1 processo por client) e **streamable HTTP** (server web numa porta, vários clients por URL). No client (`mcpclient`), o Spring AI descobre as tools do server e elas viram tools **normais** no array `tools` (o LLM não sabe que vieram de MCP). Começou com `.defaultTools(toolCallbackProvider)` (global) e **evoluiu** para injetar `List<McpSyncClient>` e **selecionar tools por requisição** (`ToolUtil.selectToolsFor`), com um **filtro global** por cima (`McpToolFilter`, bloqueia server/tool na descoberta — §14.5). Servers de estudo: `mcpserverstdio` (stdio, `@McpTool` + `web-application-type=none`) e `mcpserverremote` (streamable, `:8090`).
- **Capacidades MCP além de tools** (feitas só no `mcpserverremote`, via `McpSyncRequestContext` no server + handlers `@Mcp*` no client, casando pelo nome da conexão `artur`): **progress** (`ctx.progress` → `@McpProgress`) e **logging** (`ctx.info` → `@McpLogging`) = server manda notificações **de volta** ao client enquanto processa (§14.6); **sampling** (`ctx.sample` → `@McpSampling`) = server pede um **completion de LLM** ao client, usando a inteligência do host sem ter chave própria (§14.7); **elicitation** (`ctx.elicit` → `@McpElicitation`) = server pede um **dado estruturado ao usuário** (schema derivado de um `record`) antes de agir (§14.8). Sampling e elicitation invertem o fluxo: é o **server chamando de volta o client**.
