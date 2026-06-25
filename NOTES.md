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
