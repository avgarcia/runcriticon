# Módulo `identidad`

Bounded context de **Identidad y acceso**. Autenticación *invite-only*, gestión de usuarios (admin, entrenador, alumno), invitaciones de un solo uso y auditoría de acceso.
Es el único módulo que **publica** eventos; no consume de ningún otro.

## Eventos publicados

| Evento | Cuándo | Schema | Consumido por |
|---|---|---|---|
| `AlumnoInvitado` v1 | Alta de un alumno por invitación (queda `INVITADO`) | `schemas/identidad/alumno-invitado-v1.json` | Club y taxonomía, Seguimiento (pendientes de construir) |
| `EntrenadorInvitado` v1 | Alta de un entrenador por invitación (queda `INVITADO`) | `schemas/identidad/entrenador-invitado-v1.json` | Club y taxonomía, Seguimiento (pendientes de construir) |
| `AlumnoActivado` v1 | Un alumno activa su cuenta (pasa a `ACTIVO`) | `schemas/identidad/alumno-activado-v1.json` | Club y taxonomía, Seguimiento (pendientes) |
| `EntrenadorActivado` v1 | Un entrenador activa su cuenta (pasa a `ACTIVO`) | `schemas/identidad/entrenador-activado-v1.json` | Club y taxonomía, Seguimiento (pendientes) |

> El contrato de cada evento lo valida el job `contractTest` contra su JSON Schema.
> Un cambio rompiente exige `…-v2.json` + dual-publishing 4 semanas (ver `schemas/README.md`).
