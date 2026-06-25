# Módulo `identidad`

Bounded context de **Identidad y acceso** (ADR-0003, ADR-0009). Autenticación *invite-only*,
gestión de usuarios (admin, entrenador, alumno), invitaciones de un solo uso y auditoría de acceso.
Es el único módulo que **publica** eventos; no consume de ningún otro.

## Eventos publicados

| Evento | Cuándo | Schema | Consumido por |
|---|---|---|---|
| `AlumnoInvitado` v1 | Alta de un alumno por invitación (queda `INVITADO`); LAL-8 | `schemas/identidad/alumno-invitado-v1.json` | Club y taxonomía, Seguimiento (pendientes de construir) |

> El contrato de cada evento lo valida el job `contractTest` contra su JSON Schema (ADR-0007 D11).
> Un cambio rompiente exige `…-v2.json` + dual-publishing 4 semanas (ver `schemas/README.md`).
