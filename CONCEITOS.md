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












