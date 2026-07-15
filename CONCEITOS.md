Artifical intelligence -> machine learning -> deep learning -> generative ai

generative ai: tem image gen e llm, os dois são coisas diferentes, um gera texto outro gera imagem

tudo impulsionado por nlp, que natural language processing
-------------
AI começa em 1950
-------------
Machine learning funciona com reconhecimento de padrão, através de muuuuuuuitos exemplos.
É como os computadores aprendem através de "experiencia".
-------------
Deep Learning é uma forma mais avançada de aprender, utiliza-se de neural networks, algo semelhante a como os humanos
aprendem.

Professor deu um exemplo de camadas, usando uma imagem como exemplo.
layer 1: recognizes lines and edges.
layer 2: combines lines to see shapes.
layer 3: combines shapes to recognize objects
final layer: "this is a cat!"

É um conhecimento em camadas pelo que entendi, próximo do que nós temos, vamos aprofundando nosso conhecimento desde 
criança.

Neural networks, é o que constrói o deep learning, onde os sistemas são desenhados pra trabalhar como um cérebro, onde
cada neurônio processa sua tarefa. "Neurônios" são conectados em networks, e suas ligações possuem diferentes forças.

NLP é a ferramenta que traduz linguagem humana para os máquinas.
-------------

GenAI é a tecnologia criativa, que através de muitos inputs de dados consegue gerar "coisas novas".

------------

Analogia mundo real, um robô chef de cozinha:
AI: ele saber fazer uma refeição, com base em algoritmos pré escritos.
ML: aprender a fazer uma receita através do estudo de vídeos de pessoas cozinhando.
DL: uso de um "sistema cerebral" para aperfeiçoar suas habilidades.
NNs: a liga no cérebro do robô que processa os elementos de sua função, ingredientes e passos.
NLP: o elemento que faz o robô entender um pedido como "fazer macarrão" ou "fritar batata".
GenAI: o robô criando uma nova receita.
LLMs: o robô traduzindo todo seu conhecimento em texto, explicando-nos sua receita.

------------

LLMs

Treinadas em quantidades massivas de texto. Aprendendo padrões através destes dados.
Treinadas de forma não supervisionada.

Conecta ideias por contexto, por exemplo, loja, conecta com dinheiro, itens, carrinhos, compra e etc. Chuva conecta com
guarda-chuva, capa e etc.

O principal ponto da LLM é advinhar a próxima palavra de uma frase.

LLM Wrapper: ChatGPT, Gemini, Claude e etc. LMM é o motor e o wrapper é o carro.

Wrappers fazer um ciclo de enviar e reenviar o input do usuário para o modelo. Exemplo:
"Qual a capital do Brasil?" -> LLM responde "Brasília"
Wrapper pega o retorno e manda novamente pro LLM -> O modelo vai advinhar a próxima palavra -> "Brasilia é"
Wrapper manda "Brasilia é" -> Responde -> "Brasilia é a"

Até montar a frase final "Brasilia é a capital do Brasil."

-------------

Tokens LLM

Na verdade a llm prevê o proximo token. AI/LLM não consegue entender nossa linguagem. Por isso tudo que falamos é
traduzido para tokens através de um processo de tokenização.

Uma palavra não necessariamente será 1 token, ela pode ser dividida em partes e a mesma palavra ser dois tokens.
Exemplo: playfulish pode ser 3 tokens, play - ful - ish

Text -> tokens -> token ids - vectors

Model Vocabulary: é o mapa de todos os tokens que o modelo conhece.
Caso o modelo não conheça uma palavra, ela será quebrada em subpalavras: sobwork tokenization.
Exemplo: tabletop -> tokenization -> "table" e "top"

-------------

Embeddings

Como trazer significado para os tokens que o llm conhece. 

Cada token tem um vetor, que é uma lista de numeros do quanto aquele token é relacionado a determinado conceito.

Exemplo: Rei -> homem, realeza, líder, poder, riqueza

Cada modelo tem o embedding size. Que é quantos conceitos são ligados a cada token.

O modelo cria esses embedding com base no seu treinamento, ou seja, Rei aparece diversas vezes em diversos contextos,
e com base nisso o modelo vai categorizando para cada coisa.

Exemplo: Se eu pegar o embeddind de "Rei", subtrair o embedding de "Homem", e adicionar o embedding de "Mulher", o
resultado será "Rainha".

Embeddings iniciam com numeros aleatorios ou zerados. E durante o treinamento o embedding das palavras vai sendo
atualizado.

Como medir proximidade entre duas palavras? : Multiplica cada valor do vetor de uma palavra por seu correspondente na
outra, soma-se tudo e pelo resultado sabe-se quanto uma palavra relaciona com outra, positivo significa significados
relacionados, zero significa zero relação, negativo significa que o significado é oposto (pequeno e grande).

Projection: Diminuir um embedding de um token para componentes mais importantes.

Static Embeddings: É o que está fixo do treinamento para cada token, fica guardado em uma tabela de lookup. Este embedding
estático independe do contexto que o token está sendo usado.

Por isso, camadas que identificam o contexto para fazer uma projection eficiente são usadas, fazendo com que o contexto
seja levado em conta para definir quais componentes do vetor devem ser analisados.

Positional Embeddings: É o vetor daquele token com base na posição que ele está na frase. 
Exemplo: "João mordeu o cachorro" e "Cachorro mordeu o João"
Ambas as frases possuem os mesmos tokens, mas com significados opostos, saber a posição das palavras é importante pois
é o que trás o real significado.

Attention Layer: Usado para saber quais tokens são mais importantes em uma frase. O significado de uma mesma palavra é
definido somente após análise das outras palavras da frase.
Define quanto de atenção deve ser dado a cada palavra.

-----------
ChatMemory: LLMs não guardam estado, por isso devemos manualmente informa-la sobre o que já foi dito na conversa.

RAG Retrieval Augmented Generation: Da contexto relevante pra LLM. Utilizado para trazer dados que não foram usados
no treinamento do modelo, como dados internos de uma empresa. Exemplo: Se eu tenho um livro dentro do KB (Knowledge base)
ao executar um prompt sobre algo que está nas paginas 10 a 15 do livro, o RAG faz com que somente estas paginas sejam
enviadas ao LLM.

Diferente do Prompt Stuffing, onde todo o livro seria passado no prompt.

Vector Database: Guarda informações com base em seus embeddings, entende contexto e significado, possibilitando consultas
semanticas.
Exemplo: Quando o usuário enviar um prompt, transformamos ele em uma query vector, que trará do vector database somente
as informações relevantes pro prompt do usuário, pegamos este retorno e adicionamos ao llm prompt.

-------------

Text Splitter / Chunking: Antes de jogar um documento no vector database, quebramos ele em pedaços menores (chunks).
Documento gigante = embedding "borrado" (mistura vários assuntos num vetor só) e, na busca, traz um bloco enorme com
muito texto irrelevante pro prompt. Chunk pequeno = embedding focado num assunto + busca traz só o pedaço relevante.
Na prática: carreguei um PDF SEM splitter e o prompt ficou ~1200 tokens; COM splitter caiu pra ~500. Menos token = mais
barato, mais rápido e resposta mais focada.
Cuidado: chunk pequeno demais corta uma ideia no meio e perde sentido; overlap (sobreposição) ajuda a não cortar bem na
fronteira de uma ideia.

-------------

Busca cross-lingual: A busca vetorial compara SIGNIFICADO, não palavra. Então dá pra perguntar em português e achar
documento em inglês, porque os embeddings da OpenAI são multilíngues ("férias" cai perto de "vacation"). Só que cross-lingual
é mais fraco que monolíngue. Solução: traduzir a pergunta pro idioma do índice ANTES de buscar (Query Transformer).
Importante: idioma da PERGUNTA afeta a busca; idioma da RESPOSTA é controlado pelo prompt. Dá pra buscar em inglês e
responder em português.

-------------

RAG modular (pipeline): Em vez de fazer a busca na mão no controller, existe um advisor que faz o RAG inteiro sozinho.
O pipeline tem 3 fases plugáveis:
1. Pré (Query Transformer): mexe na pergunta antes de buscar (ex: traduzir, comprimir o histórico numa query).
2. Retrieval (Document Retriever): de onde vêm os documentos. Pode ser o vector store OU outra fonte, tipo busca na web ao
   vivo (ex: API Tavily). A fonte é trocável sem mexer no resto.
3. Pós (Document Post Processor): trata o que voltou antes de mandar pro LLM (ex: mascarar dados sensíveis/PII como email e
   telefone, re-ranking, deduplicar).

-------------

Semantic Cache: Cache normal só acerta com a string IDÊNTICA. Semantic cache acerta por SIGNIFICADO: guarda o embedding da
pergunta no Redis e, se vier uma pergunta parecida o bastante (acima de um threshold de similaridade), devolve a resposta
guardada SEM chamar o LLM.
Hit = economiza token, custo e tempo (modelo nem roda). Miss = chama o LLM e guarda a resposta pra próxima.
Threshold de propósito nem alto nem baixo demais: alto demais quase nunca acerta; baixo demais devolve resposta de uma
pergunta "parecida mas diferente" (erro). Comecei com 0.9 (Redis) e depois fui pra 0.8.
Cuidado: como ele curto-circuita antes do modelo, uma resposta cacheada pode ignorar o contexto da conversa (memória) ou
documentos novos do RAG. Combina melhor com perguntas "soltas".
Onde guardar o cache: pode ser no Redis OU no próprio vector database. Troquei pra usar o Qdrant (que já tava de pé pro
RAG), assim não preciso subir outra infra só pro cache. IMPORTANTE: o cache tem que ficar numa collection SEPARADA da do
RAG, senão as perguntas/respostas do cache viram "documento" que o RAG recupera, bagunçando os dois.

-------------

Tool Calling (function calling): Deixa o LLM PEDIR pra rodar um código meu no meio da resposta. O modelo não executa nada,
ele só decide QUAL função chamar e com QUAIS argumentos; quem roda é a minha aplicação, que devolve o resultado pro modelo
continuar. Ponto que me confundiu: as tools NÃO vão no texto do prompt — vão num campo SEPARADO da requisição (o array
"tools"), cada uma com nome + descrição + schema dos parâmetros. É a DESCRIÇÃO da tool que o modelo lê pra decidir quando
usar. Ciclo: mando prompt + tools -> modelo responde "quero chamar getCurrentTime(Tóquio)" -> app executa e devolve o
resultado -> modelo gera o texto final. Ou seja, são DUAS idas ao modelo por trás de um único call. Exemplo aqui: tool que
retorna a hora atual num fuso horário.

-------------

Tool Calling com banco + ToolContext (help desk): Evolui a tool "só leitura" pra tools que fazem operação REAL no banco
(criar/consultar tickets via JPA). Aprendizado principal: tem dois tipos de "entrada" numa tool. (1) Argumentos @ToolParam
(ex: o issue do ticket) -> vão no schema e QUEM PREENCHE é o modelo, a partir da conversa. (2) ToolContext (ex: o username)
-> preenchido pela MINHA app no controller (.toolContext(Map.of("username", user))) e o modelo NEM VÊ. Uso o ToolContext
pra identidade/segurança: o LLM não escolhe de quem é o ticket, quem manda isso sou eu. Outro detalhe: @Tool(returnDirect
= true) faz o retorno da tool virar a resposta final direto, sem a 2ª ida ao modelo (economiza uma geração quando o
resultado já é a resposta pronta, tipo "Ticket #12 criado").

-------------

MCP (Model Context Protocol): protocolo aberto pra padronizar como uma aplicação dá ao LLM acesso a tools/dados
externos. Três papéis: HOST (a app que roda o LLM), CLIENT (o conector dentro do host — 1 client = 1 conexão com
1 server) e SERVER (quem expõe as capacidades; aqui, tools). A sacada é desacoplar: o server publica as tools uma
vez e qualquer host/LLM consome, sem reimplementar em cada app.

Transport types (como client e server conversam):
- stdio: o client SOBE o server como um processo local (command + args) e conversa por JSON-RPC no stdin/stdout.
  Pra server local, mesma máquina. IMPORTANTE: o stdout é o canal do protocolo -> o server não pode logar/printar
  no stdout senão corrompe o JSON-RPC (banner off, log em arquivo/stderr).
- streamable http: o server já roda como serviço web (local ou remoto) numa porta e o client conecta por URL
  (JSON-RPC sobre HTTP, com streaming). Pra server externo/compartilhado.

O que montei no estudo (mesma HelpDeskTools da seção de tool calling nos dois servers):
- mcpclient: o HOST. Deps spring-ai-starter-mcp-client + model-openai. stdio -> declaro no mcp-servers.json
  (formato do Claude Desktop: mcpServers { nome: { command, args } }); hoje só o filesystem (tirei o server stdio
  de help desk e o github). streamable http -> conecta por PROPERTY, não pelo JSON, e a conexão tem um NOME
  (streamable-http.connections.ARTUR.url=...). O Spring AI sobe/conecta cada server, faz o handshake e descobre as
  tools -> viram tools NORMAIS de tool calling (pro LLM é só mais uma entrada no array "tools", ele nem sabe que veio
  de MCP). Botei request-timeout=60s por causa do cold start do npx (filesystem). CUIDADO com dois nomes diferentes:
  o nome da CONEXÃO (artur, no client) != o nome que o SERVER anuncia (helpdesk-mcp-server). Os handlers de callback
  casam pelo nome da conexão (clients="artur"); o filtro/seleção de tools casa pelo nome do server.
- mcpserverstdio: server via STDIO. spring-ai-starter-mcp-server-webmvc + web-application-type=none (roda sem
  porta, puro stdio). Tools com @McpTool/@McpToolParam (equivalente ao @Tool local, mas expõe via MCP). Ficou só com
  as 2 tools originais.
- mcpserverremote: streamable http -> spring.ai.mcp.server.protocol=streamable + server.port=8090.
  Diferença central: roda UMA vez como web service e vários clients conectam por URL; não é "um processo por client".
  DIVERGIU do stdio: é aqui que fiz progress/logging/sampling/elicitation (abaixo).

-------------

MCP - filtro e seleção de tools (client): nem toda tool descoberta precisa chegar no LLM. Dois níveis:
- Filtro GLOBAL na descoberta: McpServerToolFilter implements McpToolFilter. O Spring AI chama test(info, tool) uma
  vez por tool ao descobrir os servers; retornar false faz a tool NEM EXISTIR pro resto da app. Bloqueio por nome do
  server (ex: github) ou da tool (ex: write_). É política fixa.
- Seleção POR REQUEST: ToolUtil.selectToolsFor(clients, serverName, toolName). Em vez de mandar TODAS as tools em
  toda chamada, escolho quais mandar naquela requisição (hint null/vazio = casa tudo) e monto ToolCallback[]. Por
  isso o controller passou a injetar List<McpSyncClient> no lugar do ToolCallbackProvider.

-------------

MCP - progress + logging (server -> client): até aqui o fluxo era só client->server. Agora o SERVER manda mensagem de
volta enquanto processa. A porta no server é o McpSyncRequestContext (ctx), um parâmetro extra na @McpTool: ctx.info(...)
= logging, ctx.progress(...) = progresso 0..100%. No client escuto com @McpLogging (HelpDeskLogBridge) e @McpProgress
(HelpDeskToolProgressListener), casando pelo nome da conexão (clients="artur"). Detalhe: o client manda um progressToken
por request (.toolContext(Map.of("progressToken", uuid))) que CORRELACIONA as notificações àquela chamada.

-------------

MCP - sampling: INVERSÃO de papéis. Normal é o client (que tem a chave/LLM) chamar o server. No sampling o SERVER pede
ao CLIENT que rode um completion de LLM por ele -> o server usa a inteligência do host SEM ter chave/modelo próprio. No
server a tool chama ctx.sample(systemPrompt + message) (com ctx.sampleEnabled() + fallback se o client não suporta). No
client respondo com @McpSampling (HelpDeskSamplingProvider): traduzo o request MCP num Prompt do Spring AI e chamo o
ChatModel DIRETO (não o ChatClient com tools, senão o completion re-dispara as tools num loop). Exemplo: tool
summarizeTickets + endpoint /summarize-tickets (o LLM do chat decide chamar a tool, que chama de volta o client pra
gerar o resumo).

-------------

MCP - elicitation: parente do sampling, mas em vez de pedir um completion de LLM, o server pede um DADO ESTRUTURADO ao
USUÁRIO. Antes de abrir o ticket, createTicket chama ctx.elicit(message, TicketContactInfo.class): o record vira o
schema JSON que o client preenche, e a resposta é mapeada de volta no record (StructuredElicitResult). Tem
ctx.elicitEnabled() + fallback (defaults MEDIUM/N/A) e as ações ACCEPT/DECLINE/CANCEL. No client respondo com
@McpElicitation (HelpDeskElicitationProvider), simulando o preenchimento (ACCEPT). Resumo: sampling pede COMPLETION ao
client; elicitation pede DADO ao usuário. Os dois = server chamando DE VOLTA o client.

-------------

MCP na prática — o que me quebrou (e o porquê):
- stdio é UM PROCESSO POR CLIENT. Cada client dá o seu "java -jar" e cria a SUA instância do server. Deixar o MCP
  Inspector aberto E subir o app ao mesmo tempo = duas instâncias do mesmo server -> colidem no arquivo H2
  ./chatmemory; a segunda emperra no boot -> o client não recebe o "initialize" e estoura o timeout de
  inicialização de 20s ("Did not observe any item ... within 20000ms in 'map'"). Regra: um dono do server por vez.
- Windows trava o jar. Instância sobrando segura o arquivo -> "mvn clean/repackage" falha ("Unable to rename" /
  "arquivo já está sendo usado"). Matar o java resolve (criei a função killjava <trecho> no bash).
- mvnw usa o JAVA_HOME, NÃO o java do PATH. Dava "release version 25 not supported" mesmo com java -version = 25,
  porque o JAVA_HOME apontava pro JDK 17. Botei "mise activate" no profile do PowerShell pra manter alinhado.
- @McpTool que grava no banco precisa da tabela: sem spring.jpa.hibernate.ddl-auto=update o H2 file sobe vazio e o
  insert quebra com "Table HELPDESK_TICKETS not found".

-------------

Multimodalidade (áudio e imagem): a mesma ideia do chat, mas com modelos que entram/saem em
outra mídia — no Spring AI cada um tem seu model bean.
- Speech-to-Text (transcrição): TranscriptionModel (Whisper). Mando um Resource de áudio num
  AudioTranscriptionPrompt e recebo o texto. Dá pra passar opções (idioma, temperature, e o
  responseFormat — ex. VTT com timestamps de legenda em vez de texto puro).
- Text-to-Speech (TTS): TextToSpeechModel. Passo texto e recebo bytes de áudio (gravei num
  output.mp3). Nas opções escolho voz (ex. NOVA), velocidade e formato (MP3).
- Image generation: ImageModel + ImagePrompt. Retorna a imagem em base64 (b64Json). Nas
  opções dá pra pedir n imagens e trocar o model.
Ponto-chave: não é "prompt de texto puro" — a entrada/saída é binária (Resource/byte[]), mas o
ciclo (prompt -> model.call -> response) é o mesmo do ChatModel.

-------------

AI Agent

Reason -> Act -> Observe -> Repeat

Um AGENT é um LLM solto num loop com FERRAMENTAS e um OBJETIVO: em vez de eu orquestrar cada
passo, dou a meta + as tools e o modelo decide sozinho o que fazer, faz, observa o resultado e
repete até resolver. É tool calling (seção acima) levado ao extremo — várias idas ao modelo em
cadeia, TODAS automáticas. No Spring AI eu nem escrevo o loop: entrego as tools ao ChatClient
(.defaultTools) e o framework roda Reason->Act->Observe->Repeat por mim; meu trabalho é só o
system prompt (as instruções de como trabalhar) e o input.

Agent vs. chatbot com tools: o chatbot chama 1 tool e responde. O agent encadeia MUITAS chamadas
por conta própria (identifica o cliente -> puxa pedidos -> checa cobrança duplicada -> decide ->
emite refund -> loga ticket) sem eu dizer a ordem. Autonomia = ele escolhe QUAIS tools, em que
ORDEM e QUANDO parar.

O que montei (support-agent-demo, o projeto final do curso): um agente de suporte que trabalha
uma caixa de e-mails de e-commerce SOZINHO. São DUAS apps:
- mcp-server (MySQL, streamable HTTP :8090): expõe como @McpTool a ÚNICA janela do agente pros
  sistemas da empresa. Tools de leitura (achar cliente por e-mail, pedidos, produto/SKU, detectar
  cobrança duplicada, checar garantia, histórico de tickets) e de AÇÃO (emitir refund, logar
  ticket). O banco é semeado com dados de propósito p/ 4 cenários (datas relativas ao CURDATE).
- support-agent (host + LLM): um InboxMonitor faz polling numa caixa Mailpit (fake SMTP + REST);
  cada e-mail novo vira um IncomingEmail e é entregue ao SupportAgent (ChatClient com TODAS as
  tools do MCP + o system prompt). O LLM lê o e-mail, chama as tools que precisar, decide a
  resolução e devolve structured output (AgentResponse: replySubject/replyBody + operatorSummary);
  aí o SupportMailSender responde o cliente por SMTP, no idioma/tom dele.

O que amarra tudo: o agent é a soma do curso — tool calling + MCP (streamable HTTP) + structured
output + system prompt/prompt template + o loop autônomo do Spring AI.

Regras que ficam no SYSTEM PROMPT (é aqui que a autonomia vira confiável): "você só enxerga o
que as tools retornam — nunca invente dado"; a ORDEM sugerida (identificar -> entender -> juntar
fatos -> decidir -> logar ticket sempre no fim); e as GUARDAS de ação real ("só emita refund se
o dado justificar — refund mexe dinheiro de verdade; não chame a tool especulativamente").
Separei tools de LEITURA (@Transactional(readOnly=true)) das de ESCRITA de propósito: ação real
(mover dinheiro) tem que ser deliberada, não efeito colateral de uma consulta.

Pontos de atenção (vividos):
- Duas stacks Docker, dois compose.yaml (MySQL no server, Mailpit no agent). Subo o server 1º.
- Seed roda só em volume NOVO; datas são relativas (CURDATE - INTERVAL n DAY) p/ os cenários não
  "vencerem". Pra re-semear: docker compose down -v.
- A resposta do agente TAMBÉM cai no Mailpit -> filtro a busca (to:support !from:support) senão
  ele reprocessa o próprio e-mail num loop infinito.
- ddl-auto=none no server: o schema é dono do SQL de init, Hibernate não pode recriar/derrubar.














