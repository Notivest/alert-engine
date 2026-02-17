# Guia local - alert-engine

## Que es
Motor de alertas: CRUD de reglas, evaluacion scheduler y eventos disparados.

## Prerrequisitos
- Java 21
- PostgreSQL
- price-fetcher y notification-service alcanzables por URL

## Correr en stack completo (recomendado)
Desde `gateway-api/`:

```bash
docker compose up -d alert-engine-db alert-engine
```

En stack completo:
- app: `http://localhost:8083`
- db: `localhost:5433`

## Correr por Gradle
1. Ajustar variables de entorno (`PORT`, datasource, JWT, `PRICEDATA_BASE_URL`, `NOTIFICATION_BASE_URL`).
2. Ejecutar:

```bash
set -a
source .env
set +a
./gradlew bootRun
```

## Uso directo

```bash
curl -H "Authorization: Bearer <jwt>" \
  "http://localhost:8083/alert-kinds"

curl -H "Authorization: Bearer <jwt>" \
  "http://localhost:8083/alerts"
```

## Uso via gateway

```bash
curl -H "Authorization: Bearer <jwt>" \
  "http://localhost:8080/api/alert/alert-kinds"
```

## Referencias
- `docs/ALERT_ENGINE_ENDPOINTS.md`
- `docs/descripcion-proyecto.md`
