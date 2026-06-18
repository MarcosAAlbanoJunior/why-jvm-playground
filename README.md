# why-jvm playground

Aplicação Spring Boot (Java 25) que serve de **alvo** para testar o agente
why-jvm de ponta a ponta. Ela reúne cenários reais — erro, lentidão, N+1 de JDBC
e exaustão de pool de conexão — atrás de um console web simples.

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

## Anexar o why-jvm (e2e real)

A aplicação roda sozinha sem o agente. Para anexá-lo **sem tocar no código**, o
agente vem de **releases publicadas no GitHub** — nada de build ou código local:

- `opentelemetry-javaagent.jar` (repo do OpenTelemetry)
- `why-jvm-otel-extension-all.jar` (repo `why-jvm-mcp`)

O Dockerfile baixa os dois (estágio `runtime-whyjvm`). Para subir nesse modo:

```bash
docker compose -f docker-compose.yml -f docker-compose.whyjvm.yml up --build
```

Isso roda em **modo log** (sem `whyjvm.forward.url`): o laudo do incidente sai no
log da própria aplicação. Dispare um cenário e veja em `docker compose logs app`.

> Pré-requisito: a release do `why-jvm-mcp` precisa estar publicada na tag
> apontada por `WHYJVM_RELEASE` (em `docker-compose.whyjvm.yml`). A versão do
> agente OTel é `OTEL_AGENT_VERSION` no mesmo arquivo.

O passo "full" (laudo com narrativa de LLM, indo para Slack) é religar o
`whyjvm.forward.url` apontando para o `analysis-service` (Go).

## Configuração

| Variável | Default | O que é |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/whyjvm` | Banco. |
| `DB_POOL_SIZE` | `5` | Tamanho do pool Hikari (pequeno para o cenário de exaustão). |
| `PLAYGROUND_WARMUP_ENABLED` | `true` | Liga/desliga o aquecimento do baseline na subida. |
| `PLAYGROUND_WARMUP_ITERATIONS` | `120` | Quantas chamadas de aquecimento por rota rápida. |
