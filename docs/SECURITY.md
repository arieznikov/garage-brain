# Security and privacy

Garage Brain is **local-first**: your drives stay on your machine unless you choose to share them.

## Never commit secrets

| File | Safe to commit? |
|------|-----------------|
| `.env.example` | Yes — placeholders only |
| `.env` | **No** — gitignored. Copy from `.env.example`. |
| `~/.garage-brain/` | **No** — runtime data (Parquet, imports). Outside the repository. |

Before pushing to GitHub:

```bash
git check-ignore -v .env          # should show .gitignore rule
git status                        # .env must not appear as tracked
git grep -E '192\.168\.|10\.|sk-' || true   # no LAN IPs or API keys in tree
```

Do not put real API keys, LAN URLs, or production passwords in `.env.example`, README, or CI logs.

## What the API sends to an LLM

BYOM report generation sends **structured JSON only** (vehicle label, baseline progress, alert summaries). It does **not** send raw CSV or full time-series dumps. You control the model endpoint via `LLM_*` in `.env`.

## Test fixtures

Real Car Scanner exports produce the parser fixtures under `backend/src/test/resources/fixtures/`. **We replaced GPS coordinates with synthetic values** (`45.0, -93.0`) before publication. OBD readings can still resemble a real vehicle. Do not add new fixtures with identifiable routes or VINs without redaction.

To re-scrub GPS after adding fixtures:

```bash
python3 scripts/redact-fixture-gps.py
```

## Reporting issues

For vulnerabilities in this project, open a GitHub security advisory or issue (no public exploit details until fixed).
