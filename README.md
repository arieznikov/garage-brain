# Garage Brain

Open-source, BYOM predictive OBD analytics for DIY mechanics. Import a **Car Scanner** CSV #2 export, build per-vehicle baselines over multiple drives, and get early trend warnings before the check-engine light.

## Quick start (~5 minutes)

**You need:** Java 17+, Node 20+, Docker, and a Car Scanner CSV #2 file from a recorded drive.

### 1. Clone and start Postgres

```bash
git clone <your-repo-url> garage-brain && cd garage-brain
docker compose up -d
```

Wait until Postgres is healthy (`docker compose ps` shows `healthy`), or about 10 seconds on first boot.

### 2. Optional: local LLM config

```bash
cp .env.example .env   # edit LLM_* if you use LM Studio, Ollama, etc.
```

Spring Boot loads repo-root `.env` automatically when you run from `backend/`. Without `.env`, the API defaults to Ollama at `http://localhost:11434`.

**Never commit `.env`** — only `.env.example` belongs in git. See [docs/SECURITY.md](docs/SECURITY.md).

### 3. Start the API

```bash
cd backend
./mvnw spring-boot:run
```

- Health: http://localhost:8080/api/v1/health  
- OpenAPI / Swagger UI: http://localhost:8080/swagger-ui.html  

### 4. Start the UI

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — the dev server proxies `/api` to port 8080.

### 5. First analysis

The UI uses a **diagnostic terminal** layout (see [docs/DESIGN.md](docs/DESIGN.md) — Visual design).

1. **Onboarding** — three-step strip until your first import completes: add a vehicle → upload CSV → wait for import.
2. Name your car in the header (or expand **Advanced** to set a stable vehicle ID).
3. Upload a **Car Scanner CSV #2** export (Data recording → Share).
4. Watch import status until **completed** — baseline metrics and progress appear on the vehicle page.
5. Import **at least five drives** on the same vehicle to unlock trend alerts (cold-start gate).
6. Open **Insights** for alerts and trend baselines; optionally **Generate report** for a BYOM narrative (structured alerts only — never raw CSV).

Wrong export format? The API returns:

> Export as CSV #2 from Car Scanner → Data recording → Share.

## Troubleshooting

| Symptom | Fix |
|--------|-----|
| `Bind for 0.0.0.0:5432 failed: port is already allocated` | Another Postgres is using 5432. In `.env` set `DB_PORT=5433`, run `docker compose up -d`, and use the same port in `DB_PORT` for the API. |
| `Connection to localhost:5432 refused` on API start | Start Postgres first (`docker compose up -d`) and wait for `healthy`. |
| Upload fails / `Maximum upload size exceeded` | Default limit is 50 MB per file (`spring.servlet.multipart` in `application.yml`). Trim the export or split drives. |
| **Degraded mode** on Generate report | Metrics and alerts still work. Check LLM server is running and `.env` `LLM_BASE_URL` / `LLM_MODEL` match your provider. |
| UI cannot reach API | Ensure `./mvnw spring-boot:run` is on port 8080 and you use `npm run dev` (not `preview` without a proxy). |

## BYOM providers

Garage Brain sends **structured JSON only** (vehicle meta, baseline progress, alerts) to your model — never raw CSV.

| Variable | Default | Notes |
|----------|---------|--------|
| `LLM_PROVIDER` | `ollama` | `ollama`, `openai-compatible`, or `lmstudio` |
| `LLM_BASE_URL` | `http://localhost:11434` | LM Studio: `http://localhost:1234` |
| `LLM_MODEL` | `llama3.2` | Your server's model id |
| `LLM_API_KEY` | _(empty)_ | LM Studio: `lm-studio` if required |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | compose defaults | Override when Postgres is not on localhost:5432 |
| `GARAGEBRAIN_DATA_DIR` | `~/.garage-brain` | Parquet archive and import staging |

**Ollama:** `ollama pull llama3.2 && ollama serve`

**LM Studio profile (without `.env`):** `./mvnw spring-boot:run -Dspring-boot.run.profiles=lmstudio`

Legacy `OLLAMA_BASE_URL` / `OLLAMA_MODEL` still map to `LLM_BASE_URL` / `LLM_MODEL`. Shell `export` overrides `.env`.

## Project layout

```
backend/     Spring Boot API — parser, ingestion, analytics, BYOM
frontend/    React + Vite — upload, import polling, insights, report
docs/        Design, security, eng-review artifacts
scripts/     Maintainer utilities (e.g. GPS redaction for fixtures)
```

Data directory (Parquet archives): `~/.garage-brain` by default (`GARAGEBRAIN_DATA_DIR`).

## Tests & CI

```bash
docker compose up -d
cd backend && ./mvnw verify
cd ../frontend && npm ci && npm run build && npm run lint
```

Integration tests prefer **Testcontainers** when Docker is available; otherwise they use compose Postgres on `localhost:5432` (or `DB_PORT` from `.env`). GitHub Actions runs backend `verify` plus frontend build/lint on every push/PR to `main`.

`verify` enforces **80% line coverage** (JaCoCo). Report: `backend/target/site/jacoco/index.html`.

The **golden drift** scenario (`GoldenDriftIntegrationTest`): five stable cruises + one drift fixture → `ltft_drift` alert via REST after the ≥5-session gate.

Parser fixtures: `backend/src/test/resources/fixtures/` — real Car Scanner layouts with **synthetic GPS**; see [fixtures README](backend/src/test/resources/fixtures/README.md).

## Before you push (privacy checklist)

1. Confirm `.env` is ignored: `git check-ignore -v .env`
2. Do not stage `.env`, `backend/target/`, `frontend/node_modules/`, or `~/.garage-brain` data
3. New fixture CSVs: run `python3 scripts/redact-fixture-gps.py`
4. Scan for accidents: `git grep -E '192\.168\.|10\.\d+\.|sk-[a-zA-Z0-9]{20,}' || true`

Details: [docs/SECURITY.md](docs/SECURITY.md).

## License

Apache-2.0 — see [LICENSE](LICENSE).

## Design

See [docs/DESIGN.md](docs/DESIGN.md) for architecture, scope, UI tokens, and eng-review decisions.
