# Garage Brain

Open-source, BYOM predictive OBD analytics for DIY mechanics. Import a **Car Scanner** CSV #2 export, build per-vehicle baselines over multiple drives, and get early trend warnings before the check-engine light.

## Quick start

### Prerequisites

- Java 17+
- Maven 3.9+ (or use `./backend/mvnw`)
- Node 20+ (for the React UI)
- Docker (for PostgreSQL)

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Run the API

```bash
cd backend
./mvnw spring-boot:run
```

API health: http://localhost:8080/api/v1/health

### 3. Run the UI (dev)

```bash
cd frontend
npm install
npm run dev
```

## Car Scanner workflow

1. In **Car Scanner**, record a drive (Data recording).
2. Export as **CSV #2** and share/save the file.
3. Upload in Garage Brain (UI coming soon) or use the parser tests as a reference.

If export format is wrong, the parser returns:

> Export as CSV #2 from Car Scanner → Data recording → Share.

## Project layout

```
backend/     Spring Boot API — parser, ingestion, analytics (WIP)
frontend/    React + Vite upload UI (WIP)
docs/        Design and eng-review artifacts
```

## Tests

```bash
cd backend
./mvnw test
```

Parser fixtures live in `backend/src/test/resources/fixtures/`. **Replace synthetic fixtures with 3 anonymized real Car Scanner CSV #2 exports** when available.

## License

Apache-2.0 — see [LICENSE](LICENSE).

## Design

See [docs/DESIGN.md](docs/DESIGN.md) for architecture, scope, and eng-review decisions.
