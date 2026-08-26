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
| `AlumnoEliminado` v1 | Se suprime a un alumno y sus datos personales | `schemas/identidad/alumno-eliminado-v1.json` | Club y taxonomía (`StudentDeletionListener`), Planificación (`PlanificacionDeletionListener`), Auditoría (`AuditTrailAnonymizationListener`) |
| `EntrenadorEliminado` v1 | Se suprime a un entrenador y sus datos personales | `schemas/identidad/entrenador-eliminado-v1.json` | Club y taxonomía (`StudentDeletionListener`), Planificación (`PlanificacionDeletionListener`), Auditoría (`AuditTrailAnonymizationListener`) |
| `AdminEliminado` v1 | Se suprime a un admin y sus datos personales (LAL-126) | `schemas/identidad/admin-eliminado-v1.json` | Club y taxonomía (`StudentDeletionListener`, solo anonimiza — un admin nunca tiene proyección), Auditoría (`AuditTrailAnonymizationListener`) |
| `ConsentimientoConcedido` v1 | Un alumno concede consentimiento de datos de salud, al activar su cuenta o desde `/me/consentimiento` (LAL-128) | `schemas/identidad/consentimiento-concedido-v1.json` | Seguimiento (proyección local de qué alumnos pueden reportar) |
| `ConsentimientoRevocado` v1 | Un alumno revoca su consentimiento desde `/me/consentimiento` (LAL-128) | `schemas/identidad/consentimiento-revocado-v1.json` | Seguimiento (rechaza nuevos reportes hasta que vuelva a conceder) |

> Los tres eventos de supresión viajan **sin `name` ni `email`**, a diferencia del resto: el payload sobrevive en el
> outbox al dato que se acaba de borrar. El consumidor identifica al sujeto por `aggregateId`.

> El contrato de cada evento lo valida el job `contractTest` contra su JSON Schema.
> Un cambio rompiente exige `…-v2.json` + dual-publishing 4 semanas (ver `schemas/README.md`).

## Consentimiento de datos de salud (LAL-128, ADR-0014 D16/D18)

Base legal del tratamiento de datos de salud que captura `seguimiento.reporte_sesion` (LAL-30):
consentimiento explícito, Art. 9.2.a RGPD. Solo lo concede el **ALUMNO** — es el único interesado.

- **Concesión**: al activar la cuenta (`ActivateAccountCommand`, casilla no premarcada en el
  frontend) o desde `/me/consentimiento` (`GrantConsentCommand`, para quien activó antes de que
  existiera este mecanismo, o para volver a conceder tras revocar).
- **Revocación**: `/me/consentimiento` DELETE (`RevokeConsentCommand`). Consecuencia real: el módulo
  `seguimiento` deja de aceptar nuevos reportes de sesión hasta que vuelva a conceder.
- **Tabla `identidad.consentimiento`**: una fila por concesión (no por usuario), deliberadamente sin
  el `UNIQUE (usuario_id, version_texto)` que sugiere `docs/arquitectura/rgpd-en-modulos.md` §6 —
  detalle completo en `RGPD.md`.
- **Texto del consentimiento**: versionado en `docs/legal/consentimiento/`, hoy `v2026-08-25`,
  marcado como borrador pendiente de validación legal (pendiente jurídico de ADR-0014).
