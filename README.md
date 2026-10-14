# Search Refactoring Lab

Laboratório de **AI coding / refatoração de backend** com Java 17, Spring Boot, Maven e H2. O ponto de partida é executável, mas contém dívidas técnicas deliberadas para que você possa diagnosticá-las, formular prompts, alterar a implementação e medir os efeitos.

## O que a aplicação simula

- **Search**: pesquisa um catálogo de produtos pré-carregado no H2 e retorna resultados ranqueados.
- **I/O-bound**: três provedores externos falsos (`catalog`, `pricing`, `reviews`) simulam latência com espera bloqueante. Nenhuma API externa real é chamada.
- **CPU-bound**: o ranking executa cálculo numérico configurável por produto. O endpoint de jobs dispara muitas tarefas que combinam I/O simulado e CPU.
- **Observabilidade**: latência da busca, estado de cada provedor e métricas básicas do executor/fila.
- **Falhas controladas**: é possível forçar a falha de um provedor ou de tarefas do job para testar degradação parcial e tratamento de erro.

## Requisitos

- JDK 17 ou superior.
- Conexão com a internet na primeira compilação, para o Maven baixar dependências. O script `mvnw`/`mvnw.cmd` usa o Maven instalado, se disponível; caso contrário baixa Maven 3.9.16.

## Executar localmente

Linux/macOS:

```bash
cd search-refactoring-lab
./mvnw test
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd search-refactoring-lab
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

A API sobe em `http://localhost:8080`. O H2 é criado e preenchido automaticamente a cada inicialização; não precisa instalar banco nem usar Docker.

Console do H2: `http://localhost:8080/h2-console`

- JDBC URL: `jdbc:h2:mem:searchlab`
- User: `sa`
- Password: *(vazio)*

## Endpoints

### 1. Pesquisar catálogo

```bash
curl "http://localhost:8080/api/search?q=java"
```

Parâmetros opcionais: `tenant` (padrão `public`), `limit` (1–100), `cpuIterations` (0–250000) e `failProvider` (`catalog`, `pricing` ou `reviews`).

Experimentos:

```bash
curl "http://localhost:8080/api/search?q=distributed&cpuIterations=100000"
curl "http://localhost:8080/api/search?q=java&failProvider=pricing"
curl "http://localhost:8080/api/search?q=cloud&tenant=team-b&limit=50&cpuIterations=50000"
```

A resposta inclui `elapsedMs`, `partialResults`, lista de resultados e status individual dos três provedores.

### 2. Submeter tarefas assíncronas

```bash
curl -X POST "http://localhost:8080/api/lab/jobs"   -H "Content-Type: application/json"   -d '{"tenant":"team-a","tasks":500,"ioDelayMs":40,"cpuIterations":20000,"failEvery":0}'
```

A resposta contém `jobId`. Use o identificador para consultar o andamento:

```bash
curl "http://localhost:8080/api/lab/jobs/COLE-O-JOB-ID"
curl "http://localhost:8080/api/lab/metrics"
```

Campos do job: `tenant`, `tasks` (1–5000), `ioDelayMs` (0–2000), `cpuIterations` (0–250000), `failEvery` (0 desabilita falhas; 10 faz falhar tarefas 10, 20, 30 etc.). As tarefas podem ser usadas para visualizar fila crescente, threads ocupadas, throughput e efeitos de diferentes combinações de espera de I/O e trabalho de CPU.

Actuator: `http://localhost:8080/actuator/health`.

## Dívidas técnicas intencionais do baseline

Elas são parte do exercício e **não devem ser confundidas com recomendações para produção**:

1. Um único `ThreadPoolExecutor` atende chamadas I/O bloqueantes e trabalho CPU-bound.
2. A fila do executor é um `LinkedBlockingQueue` sem limite; aceitar muita carga pode aumentar latência e consumo de memória.
3. As tarefas de um job são colocadas na fila eagerly, em lote, sem fairness por tenant. Um tenant que submete milhares de tarefas pode atrasar os demais.
4. A busca espera futures de provedores em ordem, cada uma com timeout próprio. Em saturação, os timeouts podem se somar.
5. Cada produto gera uma future para calcular o score; faça benchmark antes de presumir que mais paralelismo sempre melhora throughput.
6. O mapa de jobs permanece em memória e não tem política de expiração.
7. A busca valida parâmetros e trata falhas externas parciais, mas não implementa deadline global, circuit breaker, retry com backoff ou limites por tenant.
8. O SQL usa `LOWER(column) LIKE '%term%'`; isso é simples para o laboratório, mas não escala como um índice de busca dedicado para catálogo grande.

## Roteiro de refatoração para AI coding

Antes de alterar o código, registre um baseline: latência da busca, tempo até conclusão de um job, `queueDepth`, `activeThreads`, falhas e comportamento com dois tenants. Faça **uma mudança por vez**, peça à IA que explique trade-offs, revise o diff, rode os testes e compare as medições.

Confira [`EXERCISES.md`](EXERCISES.md) para desafios incrementais, prompts que você pode adaptar e critérios de aceite.

## Estrutura principal

```text
src/main/java/dev/refactorlab/searchlab/
  config/       executor compartilhado (ponto de partida a melhorar)
  controller/   REST API, validação de erros
  model/        records de request/response
  repository/   consulta JDBC no H2
  service/      orquestração de search, mocks de I/O, CPU scorer, jobs
src/main/resources/
  application.yml
  schema.sql
  data.sql
src/test/java/  testes de integração da busca e falha parcial
```

## Limites do laboratório

É um cenário local de estudo, não um serviço pronto para produção. Não execute testes de carga agressivos em ambientes compartilhados; comece com poucos jobs, acompanhe `/api/lab/metrics` e aumente a carga progressivamente.
