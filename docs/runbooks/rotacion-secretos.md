# Índice — runbooks de rotación de secretos

Punto de entrada a los procedimientos de rotación de secretos del proyecto (ADR-0013 D10/D11). Cada secreto del [catálogo central](../arquitectura/configuracion-y-secretos-en-modulos.md) tiene (o tendrá) su runbook `rotacion-{secreto}.md` siguiendo la [plantilla del §9](../arquitectura/configuracion-y-secretos-en-modulos.md).

## Reglas comunes

- Todo procedimiento persiste el nuevo valor en SSM (`aws ssm put-parameter --overwrite`) y redespliega App Runner (`aws apprunner start-deployment`).
- Rollback y registro en [`log-rotaciones.md`](log-rotaciones.md) son obligatorios en cada rotación (ADR-0013 D11).
- Ningún secreto de producción sale de la convención `/runcriticon/{env}/{component}/{name}`.

## Runbooks

| Secreto | Runbook | Frecuencia | Estado |
|---|---|---|---|
| `identidad/bootstrap-admin-password` (staging) | [`rotacion-bootstrap-admin-password.md`](rotacion-bootstrap-admin-password.md) | Ante sospecha | ✅ Creado |
| `security/token-hmac-secret` | `rotacion-token-hmac-secret.md` | Anual + sospecha | Pendiente |
| `db/password` | `rotacion-db-password.md` | Trimestral | Pendiente |
| `email/postmark-server-token` | `rotacion-postmark-token.md` | Anual + sospecha | Pendiente |

> Los "Pendiente" se crean antes de su primera rotación (ver [`README.md`](README.md)).
