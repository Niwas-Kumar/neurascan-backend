# Deployment Notes

## Overview
This document describes safe deployment and rollback for NeuraScan Backend.

## Preconditions
- Ensure code is passing local tests: `mvn test`.
- Ensure backend container builds: `mvn package`.
- Ensure Python AI microservice is reachable at configured URL (`ai.service.url`).

## Backup + Restore
### Firestore
(Managed by Google; ensure daily backups are enabled in the console.)

### Local DB / SQL (if used)
MySQL:
```bash
mysqldump -u root -p ai_learning_detection > backup_$(date +%F).sql
```
PostgreSQL:
```bash
PGPASSWORD=pass pg_dump -U postgres -d ai_learning_detection > backup_$(date +%F).sql
```

## Deployment Steps
1. Pull from repo `main`.
2. Run tests: `mvn test`
3. Build: `mvn clean package`
4. Deploy to target environment.
5. Smoke check: `curl -s http://<host>:8080/api/v1/health`

## Rollback
1. Roll back to last known good commit:
```bash
git checkout <prev-commit-hash>
```
2. Rebuild and redeploy.
3. Verify health endpoint and core flows.

## Feature flags
- `FEATURE_CONSENT_ENABLED`: enable consent modal + DB consent enforcement
- `FEATURE_RISK_EXPLANATION_ENABLED`: show confidence + explanation data
- `FEATURE_TEACHER_RECOMMENDATIONS_ENABLED`
- `FEATURE_EXPORT_AUDITLOG_ENABLED`
- `FEATURE_OFFLINE_MODE_ENABLED`
- `FEATURE_ADMIN_PANEL_ENABLED`

## ML API connectivity
- Health endpoint does a lightweight check to the ML route (`/health`) with timeout value from `ml.api.timeout`.
- If unreachable, `/api/v1/health` still reports `aiService: DOWN` and returns 503.
