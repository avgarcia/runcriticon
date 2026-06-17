# Runbooks operativos

Runbooks paso a paso para procedimientos operativos del producto. Cada runbook cubre un procedimiento concreto que el equipo ejecuta de forma reproducible.

## Convención de nombres

- Procedimientos puntuales: `{verbo}-{objeto}.md` — ej. `acceso-rds.md`, `respuesta-a-brecha.md`.
- Rotación de secretos: `rotacion-{secreto}.md` — ej. `rotacion-session-signing-key.md`.

## Estado

Primer runbook de rotación creado: [`rotacion-bootstrap-admin-password.md`](rotacion-bootstrap-admin-password.md), con su índice [`rotacion-secretos.md`](rotacion-secretos.md) (LAL-42). El resto se crean cuando los invoca un ADR aceptado o cuando aparece la necesidad operativa.

## Runbooks previstos por los ADRs (creación en su momento)

| Runbook | Invocado por | Cuándo |
|---|---|---|
| `acceso-rds.md` | ADR-0006 D13 | Cuando el equipo necesite acceso administrativo a RDS por primera vez |
| `rotacion-session-signing-key.md` | ADR-0013 D10, D11 | Antes de la primera rotación anual o ante sospecha |
| `rotacion-db-password.md` | ADR-0013 D10, D11 | Antes de la primera rotación trimestral |
| `rotacion-postmark-token.md` | ADR-0013 D10, D11 | Antes de la primera rotación anual |
| `rotacion-bootstrap-admin-password.md` | ADR-0003 D3, ADR-0013 D10/D11 | ✅ Creado (LAL-42) — semilla del admin en staging |
| `respuesta-a-brecha.md` | ADR-0014 D26 | Antes del lanzamiento de la beta |
| `disaster-recovery.md` | ADR-0006 D29 | Antes del lanzamiento de la beta |
| `alarmas/{alarma}.md` | ADR-0011 D16 | Cuando se configure cada alarma en AMG |
| `derechos-rgpd-acceso.md` | ADR-0014 D12 | Antes del lanzamiento de la beta |
| `derechos-rgpd-oposicion.md` | ADR-0014 D15 | Antes del lanzamiento de la beta |
| `rotacion-secretos.md` (índice) | ADR-0013 D11 | ✅ Creado (LAL-42) — al crearse el primer runbook de rotación |
| `actualizacion-jdk.md` | ADR-0016 D5 | Cuando se planifique el primer upgrade |
| `acceso-secretos.md` | ADR-0013 D14 | En el onboarding del primer dev nuevo |
| `smoke-test-h0.md` | Plan H0 Bloque 6 | Al cerrar H0 |
| `log-rotaciones.md` | ADR-0013 D11 | Cuando se ejecute la primera rotación |

## Referencias

- [`docs/arquitectura/configuracion-y-secretos-en-modulos.md`](../arquitectura/configuracion-y-secretos-en-modulos.md) §9 — plantilla de runbook de rotación.
- [`docs/arquitectura/rgpd-en-modulos.md`](../arquitectura/rgpd-en-modulos.md) §4 — procedimientos de borrado mixto.
- ADRs aceptados que invocan runbooks: ADR-0005 D14, ADR-0006 D13/D26/D29, ADR-0009 D9, ADR-0010 D23, ADR-0011 D16, ADR-0013 D11/D14, ADR-0014 D12/D15/D26, ADR-0016 D5.
