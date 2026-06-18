# why-jvm playground

Aplicação Spring Boot (Java 25) que serve de **alvo** para testar o agente
why-jvm de ponta a ponta. Ela reúne cenários reais — erro, lentidão, N+1 de JDBC
e exaustão de pool de conexão — atrás de um console web simples.

**Ecossistema:**

- [`why-jvm`](https://github.com/MarcosAAlbanoJunior/why-jvm) — o agente in-JVM
  (extensão do OpenTelemetry) que captura o incidente do JFR e encaminha os
  agregados.
- [`why-jvm-mcp`](https://github.com/MarcosAAlbanoJunior/why-jvm-mcp) — o serviço
  de análise em Go que recebe o incidente, roda o LLM e despacha o laudo.

> A aplicação é **OTel-cego** de propósito: não traz nenhuma dependência de
> OpenTelemetry. Quem instrumenta é o `opentelemetry-javaagent.jar` por fora.
> Isso evita registrar um `SpanProcessor`/`TracerProvider` próprio que
> competiria com o agente.

## Subir

```bash
docker compose up --build
```

Duas formas de disparar os cenários:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Console**: http://localhost:8080 (botões agrupados por tier)

Na subida, a aplicação aquece o baseline de latência (chamadas HTTP nela mesma
nas rotas rápidas) para que os alertas de lentidão tenham contra o que se destacar.

## Cenários

| Tier (why-jvm) | Rota | O que provoca |
|---|---|---|
| **Tier 3 · N+1** | `GET /api/catalog/books?limit=300` | 1 query da página + 1 por livro (autor lazy). `limit` alto infla o N+1. Compare com `GET /api/catalog/books/optimized?limit=300` (join fetch). |
| **Tier 1 · Lentidão** | `GET /api/catalog/books/{id}?slowDownstreamMs=3000` | Busca rápida por id (rota aquecida) + uma dependência externa lenta simulada → estoura o p99 → SLOW. |
| **Tier 1/2 · Erro** | `GET /api/faults/checkout` · `/invoice` · `/inventory` | Exceções reais não tratadas (divisão por zero, `NullPointerException`, estado inválido) → 500 → incidente imediato. |
| **Banco · Conexão** | `GET /api/database/slow-query?seconds=3` · `POST /api/database/saturate-pool?concurrency=10&holdSeconds=3` | Query lenta segura uma conexão (`pg_sleep`); a saturação dispara várias em paralelo contra o pool pequeno (5) até estourar por timeout → erro. |

## Circuito e2e

`docker compose up --build` sobe o circuito completo, tudo na mesma rede:

- **db** — Postgres.
- **why-jvm-mcp** — o serviço de análise (imagem `marcosaaj/why-jvm-mcp`), que
  recebe os incidentes, roda o LLM e despacha o laudo.
- **app** — esta aplicação com o agente why-jvm anexado **sem tocar no código**.
  O agente vem de releases publicadas no GitHub (estágio `runtime-whyjvm` do
  Dockerfile): `opentelemetry-javaagent.jar` (repo do OpenTelemetry) e
  `why-jvm-otel-extension-all.jar` (repo [`why-jvm`](https://github.com/MarcosAAlbanoJunior/why-jvm)).
  Ele captura o incidente e **encaminha** para `http://why-jvm-mcp:9090`.

Dispare um cenário e acompanhe o laudo: `docker compose logs -f why-jvm-mcp`.

> Pré-requisito: a release do agente precisa estar publicada na tag apontada por
> `WHYJVM_RELEASE` (arg do serviço `app` no compose). A versão do agente OTel é
> `OTEL_AGENT_VERSION` no mesmo lugar.

## Configuração do agente why-jvm

O agente é configurado por propriedades `-Dwhyjvm.*` no `JAVA_TOOL_OPTIONS`
(estágio `runtime-whyjvm` do Dockerfile); também aceitam o formato de env do OTel
(ex.: `WHYJVM_FORWARD_URL`).

| Propriedade | Default | O que é |
|---|---|---|
| `whyjvm.forward.url` | _(vazio)_ | URL do why-jvm-mcp. **Setada → modo split**: o app só captura e encaminha, o LLM roda no why-jvm-mcp. Vazia → modo local (laudo no log do app, sem LLM). Aqui: `http://why-jvm-mcp:9090`. |
| `whyjvm.forward.token` | _(vazio)_ | Bearer enviado no forward. Deve casar com `WHYJVM_INGEST_TOKEN` do why-jvm-mcp. |
| `whyjvm.app.packages` | _(vazio)_ | Pacotes da app (CSV) para atribuir o frame culpado e habilitar o code-aware. Aqui: `io.whyjvm.playground`. |
| `whyjvm.source.dirs` | _(vazio)_ | Diretórios de fonte (CSV) para o trecho de código no laudo. Aqui: `/app/sources`. |
| `whyjvm.slow.threshold` | `3.0` | Multiplicador sobre o p99 do endpoint para marcar um request como SLOW. |
| `whyjvm.cooldown` | `10m` | Janela mínima entre capturas (evita tempestade de incidentes). |
| `whyjvm.evidence.retain` | `false` | Mantém o snapshot `.jfr` em disco após extrair os agregados. |
| `whyjvm.incident.dir` | `incidents` | Diretório do store local + outbox do forward. |

## Configuração do why-jvm-mcp

O serviço de análise é configurado por envs `WHYJVM_*` (no serviço `why-jvm-mcp`
do compose). As principais:

| Variável | Default | O que é |
|---|---|---|
| `WHYJVM_LLM_PROVIDER` | `stub` | Provider do agente: `stub` (sem key) \| `claude` \| `gemini`. |
| `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` | _(vazio)_ | BYOK, conforme o provider escolhido. |
| `WHYJVM_INGEST_TOKEN` | _(vazio)_ | Bearer exigido no ingest. Vazio = sem auth (só dev). |
| `WHYJVM_SINK` | `log` | Canal do laudo: `log` \| `slack`. |
| `WHYJVM_SLACK_WEBHOOK_URL` | _(vazio)_ | Webhook do Slack, exigido se `WHYJVM_SINK=slack`. |
| `WHYJVM_AUTO_INVESTIGATE` | `true` | Investiga automaticamente ao receber o incidente. |

> Lista completa no repo [`why-jvm-mcp`](https://github.com/MarcosAAlbanoJunior/why-jvm-mcp).

## Configuração da aplicação

| Variável | Default | O que é |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/whyjvm` | Banco. |
| `DB_POOL_SIZE` | `5` | Tamanho do pool Hikari (pequeno para o cenário de exaustão). |
| `PLAYGROUND_WARMUP_ENABLED` | `true` | Liga/desliga o aquecimento do baseline na subida. |
| `PLAYGROUND_WARMUP_ITERATIONS` | `120` | Quantas chamadas de aquecimento por rota rápida. |
