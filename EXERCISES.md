# Exercícios guiados de refatoração com IA

O objetivo não é aceitar o primeiro patch gerado. Peça à IA que inspecione o código, exponha hipóteses, explique trade-offs, escreva/ajuste testes e mostre o diff. Revise cada mudança como engenheira responsável pelo serviço.

## 0. Baseline reproduzível

1. Rode `./mvnw test`.
2. Faça 5 buscas com `q=java` e anote `elapsedMs`.
3. Submeta `team-a` com 800 tarefas (`ioDelayMs=30`, `cpuIterations=15000`). Logo depois, submeta `team-b` com 20 tarefas.
4. Consulte `/api/lab/metrics` durante a execução e anote a profundidade da fila e o tempo de conclusão por tenant.
5. Repita com `cpuIterations=0` e depois com `ioDelayMs=0` para isolar os tipos de carga.

## 1. Concurrency: isolar I/O de CPU

**Prompt sugerido**

> Analise `LabExecutorConfiguration`, `SearchService`, `WorkloadJobService`, `MockExternalProviderClient` e `CpuIntensiveScorer`. Identifique como operações bloqueantes de I/O e CPU-bound compartilham recursos. Proponha uma mudança mínima que use executores separados com configurações justificadas. Explique limites, lifecycle/shutdown, propagação de exceções e riscos de deadlock. Implemente a mudança e crie testes que demonstrem que a aplicação continua correta.

**Aceite:** executores nomeados e configuráveis separadamente; nenhum pool default oculto; lifecycle previsível; testes verdes; explique por que o tamanho de cada pool é diferente.

## 2. Throughput and latency

**Prompt sugerido**

> Faça um plano de medição antes de otimizar. Separe latência de cada provedor, tempo de CPU, tempo total e espera na fila. Em `SearchService`, reduza a latência agregada sem transformar falha parcial em falha total. Compare a implementação atual com uma abordagem de deadline global e coleta concorrente de resultados. Evite otimização sem métrica.

**Aceite:** mesmos contratos funcionais; status correto de cada provedor; timeout total limitado; comparação antes/depois com carga repetível.

## 3. Processing bottlenecks

**Prompt sugerido**

> Inspecione o caminho `/api/search` e o processamento em lote. Liste gargalos de CPU, bloqueio, alocação e banco de dados. Proponha um experimento para distinguir saturação de CPU de espera de I/O; não altere tudo de uma vez. Adicione uma métrica útil ou um teste de carga leve e explique como interpretar os resultados.

**Aceite:** hipótese mensurável, medida anterior/posterior e nenhuma alegação de ganho sem dados. Verifique o custo de criar uma `CompletableFuture` por produto/tarefa.

## 4. Back-pressure e limites de admissão

**Prompt sugerido**

> A fila usada pelo `ThreadPoolExecutor` é ilimitada. Desenhe uma estratégia de back-pressure apropriada para uma API HTTP que recebe jobs. Compare fila limitada com rejeição explícita, `CallerRunsPolicy`, semáforo ou admissão por quota. Implemente uma opção que limite recursos sem executar trabalho pesado inesperadamente na thread HTTP. Retorne erro HTTP estável quando a capacidade estiver esgotada.

**Aceite:** limite configurável; rejeição observável (por exemplo, HTTP 429/503 conforme a semântica escolhida); contadores/métricas; teste de saturação sem dependência de timing frágil.

## 5. Fairness across queues/consumers

**Prompt sugerido**

> Hoje todos os tenants usam a mesma fila FIFO e o endpoint envia todas as tarefas de um job de uma vez. Projete um scheduler com filas por tenant e política round-robin ou weighted fair scheduling. Defina quotas por tenant, número de consumers, comportamento quando uma fila está vazia e como evitar starvation. Implemente uma versão simples, documentando os trade-offs contra uma única fila compartilhada.

**Aceite:** teste concorrente com um tenant enviando uma carga grande e outro enviando uma pequena; o tenant menor deve começar a progredir sem aguardar a drenagem completa do lote maior; sem busy-wait.

## 6. Data structures

**Prompt sugerido**

> Faça uma revisão de estruturas de dados em `SearchService` e `WorkloadJobService`. Avalie `ArrayList`, `HashMap`, `ConcurrentHashMap`, a fila de tarefas e o armazenamento de jobs. Identifique complexidade temporal, contenção, alocação e vazamento por retenção indefinida. Sugira a menor alteração que melhora um problema demonstrável.

**Aceite:** não troque uma estrutura só por estilo; associe cada escolha ao padrão de acesso e apresente teste/medida. Considere expiração e acesso concorrente ao estado do job.

## 7. Architecture trade-offs

**Prompt sugerido**

> Compare três opções para o fluxo de search: (A) MVC bloqueante com executores separados, (B) processamento reativo, (C) fan-out paralelo com limite de concorrência e deadline. Para este código, compare complexidade operacional, uso de threads, back-pressure, cancelamento, testes e latência de cauda. Não faça uma migração de framework automaticamente. Recomende uma opção incremental e implemente somente um primeiro passo.

**Aceite:** decisão escrita e vinculada ao perfil de carga; não introduzir WebFlux sem justificar dependências/bloqueios; preservar contrato HTTP.

## 8. Error handling / resiliência

**Prompt sugerido**

> Revise timeouts, interrupções, falhas parciais dos provedores, exceções nos jobs, falhas de admissão e entradas inválidas. Defina quais erros são retryable, onde um retry pioraria a sobrecarga, e quando devolver 4xx, 429/503 ou resposta parcial 200. Implemente testes de falha e garanta que não vaze stack trace nem dados internos.

**Aceite:** política documentada; timeouts com deadline coerente; retry limitado apenas para falhas transitórias se apropriado; interrupção preservada; métricas para falha/rejeição; contratos estáveis.

## 9. Teste final integrado

Depois das mudanças, compare pelo menos:

- busca `q=java`, `limit=20`, `cpuIterations=10000`;
- busca com `cpuIterations=100000`;
- jobs com I/O predominante (`ioDelayMs=80`, `cpuIterations=1000`);
- jobs com CPU predominante (`ioDelayMs=0`, `cpuIterations=60000`);
- dois tenants concorrentes, um com volume alto e outro baixo;
- falha de provedor via `failProvider=pricing`;
- falhas de job via `failEvery=10`;
- fila cheia depois da implementação de back-pressure.

Não compare números isolados de execuções diferentes como prova. Faça várias repetições, mantenha os parâmetros iguais e registre média/mediana e, se possível, p95.
