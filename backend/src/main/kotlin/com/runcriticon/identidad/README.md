# Módulo `identidad`

Bounded context de **Identidad y acceso**. Autenticación *invite-only* (ADR-0003), gestión de usuarios (admin,
entrenador, alumno), invitaciones de un solo uso, magic link, reseteo de contraseña, datos del club,
consentimiento de datos de salud y auditoría local de identidad.
**Solo publica** integration events: no consume de ningún otro módulo (ADR-0007, raíz del DAG). Los únicos
`@ApplicationModuleListener` del módulo escuchan sus propios eventos internos de email (ver más abajo).

Detalle complementario: `RGPD.md` (tablas, borrado, consentimiento) y `CONFIG.md` (secretos SSM y propiedades).

## Endpoints REST

Todos bajo `/api`. `/api/alumnos` y `/api/entrenadores` comparten prefijo con controllers de `club_taxonomia`
(`StudentDirectoryController`, `StudentTagController`, `CoachDirectoryController`), cada uno con rutas propias.

| Controller | Endpoint | Caso de uso | Autorización |
|---|---|---|---|
| `SessionController` | `POST /api/sesion` | `AuthenticateUserCommand` (login) | `@NoAuthRequired` |
| | `POST /api/sesion/contrasena` | `ChangeExpiredPasswordCommand` | `@NoAuthRequired` |
| | `GET /api/sesion/actual` | `QueryCurrentSessionQuery` | `@AuthenticatedOnly` |
| | `POST /api/sesion/cierre` | — (cierre de sesión) | `@AuthenticatedOnly` |
| `MagicLinkController` | `POST /api/sesion/magic-link` | `RequestMagicLinkCommand` | `@NoAuthRequired` |
| | `POST /api/sesion/magic-link/consumo` | `ConsumeMagicLinkCommand` | `@NoAuthRequired` |
| `PasswordResetController` | `POST /api/sesion/reseteo` | `RequestPasswordResetCommand` | `@NoAuthRequired` |
| | `POST /api/sesion/reseteo/consumo` | `ConsumePasswordResetCommand` | `@NoAuthRequired` |
| `ActivationController` | `GET /api/activacion` | `ResolveInvitationQuery` | `@NoAuthRequired` |
| | `POST /api/activacion` | `ActivateAccountCommand` | `@NoAuthRequired` |
| `MeController` | `GET /api/me/permissions` | `QueryMyPermissionsQuery` | `@AuthenticatedOnly` |
| | `GET /api/me/consentimiento` | `QueryMyConsentQuery` | `@AuthenticatedOnly` |
| | `POST /api/me/consentimiento` | `GrantConsentCommand` | `CONSENT:GRANT` |
| | `DELETE /api/me/consentimiento` | `RevokeConsentCommand` | `CONSENT:REVOKE` |
| `StudentController` | `POST /api/alumnos` | `InviteStudentCommand` | `STUDENT:INVITE` |
| | `POST /api/alumnos/{id}/invitaciones` | `ResendStudentInvitationCommand` | `STUDENT:INVITE` |
| `CoachController` | `GET /api/entrenadores` | `ListCoachesQuery` | `COACH:LIST` |
| | `POST /api/entrenadores` | `InviteCoachCommand` | `COACH:INVITE` |
| | `POST /api/entrenadores/{id}/invitaciones` | `ResendInvitationCommand` | `COACH:INVITE` |
| `UserAdminController` | `POST /api/usuarios/{id}/revocacion-sesiones` | `RevokeUserSessionsCommand` | `USER:REVOKE_SESSIONS` |
| | `POST /api/usuarios/{id}/desactivacion` | `DeactivateUserCommand` | `USER:DEACTIVATE` |
| | `DELETE /api/usuarios/{id}` | `DeleteUserCommand` | `USER:DELETE` |
| `ClubController` | `GET /api/club` | `QueryClubQuery` | `@AuthenticatedOnly` |
| | `PATCH /api/club` | `UpdateClubCommand` | `CLUB:UPDATE` |

`/me/permissions` es ayuda de UX para ocultar botones, nunca barrera: cada caso de uso vuelve a consultar la
`AuthorizationMatrix` (ADR-0009, regla de oro).

## `AccesoDenegado` (LAL-120)

Los casos de uso protegidos por la matriz (`InviteStudentCommand`, `ResendStudentInvitationCommand`,
`InviteCoachCommand`, `ResendInvitationCommand`, `ListCoachesQuery`, `RevokeUserSessionsCommand`,
`DeactivateUserCommand`, `DeleteUserCommand`, `GrantConsentCommand`, `RevokeConsentCommand`,
`UpdateClubCommand`) publican `AccesoDenegado` (`shared.api.events`) vía `IdentidadAccessAuditor` cuando la
guarda RBAC rechaza. Los rechazos de autenticación pura (login, magic link, activación, reseteo) **no** pasan por
aquí: pertenecen a la auditoría de intentos fallidos de ADR-0003 D15, en `identidad.evento_auditoria`.

## Eventos publicados

| Evento | Cuándo | Schema | Consumido por |
|---|---|---|---|
| `AlumnoInvitado` v1 | Alta de un alumno por invitación (queda `INVITADO`) | `schemas/identidad/alumno-invitado-v1.json` | `club_taxonomia` (`PersonProjectionListener`) |
| `EntrenadorInvitado` v1 | Alta de un entrenador por invitación (queda `INVITADO`) | `schemas/identidad/entrenador-invitado-v1.json` | `club_taxonomia` (`PersonProjectionListener`) |
| `AlumnoActivado` v1 | Un alumno activa su cuenta (pasa a `ACTIVO`) | `schemas/identidad/alumno-activado-v1.json` | `club_taxonomia` (`PersonProjectionListener`) |
| `EntrenadorActivado` v1 | Un entrenador activa su cuenta (pasa a `ACTIVO`) | `schemas/identidad/entrenador-activado-v1.json` | `club_taxonomia` (`PersonProjectionListener`) |
| `AlumnoEliminado` v1 | Se suprime a un alumno y sus datos personales | `schemas/identidad/alumno-eliminado-v1.json` | `club_taxonomia` (`StudentDeletionListener`), `planificacion` (`PlanificacionDeletionListener`), `seguimiento` (`SeguimientoDeletionListener`), `auditoria` (`AuditTrailAnonymizationListener`) |
| `EntrenadorEliminado` v1 | Se suprime a un entrenador y sus datos personales | `schemas/identidad/entrenador-eliminado-v1.json` | `club_taxonomia` (`StudentDeletionListener`), `planificacion` (`PlanificacionDeletionListener`), `seguimiento` (`SeguimientoDeletionListener`), `auditoria` (`AuditTrailAnonymizationListener`) |
| `AdminEliminado` v1 | Se suprime a un admin y sus datos personales (LAL-126) | `schemas/identidad/admin-eliminado-v1.json` | `club_taxonomia` (`StudentDeletionListener`, solo anonimiza — un admin nunca tiene proyección), `auditoria` (`AuditTrailAnonymizationListener`) |
| `ConsentimientoConcedido` v1 | Un alumno concede consentimiento de datos de salud, al activar su cuenta o desde `/me/consentimiento` (LAL-128) | `schemas/identidad/consentimiento-concedido-v1.json` | `seguimiento` (`ConsentProjectionListener`) |
| `ConsentimientoRevocado` v1 | Un alumno revoca su consentimiento desde `/me/consentimiento` (LAL-128) | `schemas/identidad/consentimiento-revocado-v1.json` | `seguimiento` (`ConsentProjectionListener`) |
| `AccesoDenegado` v1 (`shared.api.events`) | Rechazo RBAC de la matriz (ver arriba) | `schemas/shared/acceso-denegado-v1.json` | `auditoria` (`AuditEventListener`) |

> Los tres eventos de supresión viajan **sin `name` ni `email`**, a diferencia del resto: el payload sobrevive en el
> outbox al dato que se acaba de borrar. El consumidor identifica al sujeto por `aggregateId`.

> El contrato de cada evento lo valida el job `contractTest` contra su JSON Schema.
> Un cambio rompiente exige `…-v2.json` + dual-publishing 4 semanas (ver `schemas/README.md`).

### Eventos internos de email (no son integration events)

`InvitationEmailRequested`, `MagicLinkEmailRequested` y `PasswordResetEmailRequested`
(`application/ports/inbound/`) no salen del módulo ni tienen JSON Schema: desacoplan el envío del caso de uso a
través del outbox (ADR-0005). Los consumen `InvitationEmailListener`, `MagicLinkEmailListener` y
`PasswordResetEmailListener` (`infrastructure/events/`), que delegan en el puerto `EmailSender`
(`PostmarkEmailSender` en todos los perfiles salvo `local`, `StubEmailSender` en `local`) en una transacción
propia tras el commit — un fallo de envío no revierte la operación de negocio, y la excepción propagada hace
que el outbox reintente.

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

## Rate limit (ADR-0003)

`application/ratelimit/` define los puertos (`RateLimiter`, `ProgressiveThrottle`) y las extensiones
`consumeForActor`/`consumeForIp` (`ActorRateLimit.kt`), y
`infrastructure/ratelimit/` los implementa (`Bucket4jRateLimiter`, `CaffeineProgressiveThrottle`,
`ClientIpResolver`). Cubre solicitud de magic link y de reseteo (por cuenta y por IP), invitaciones por actor,
backoff progresivo del login fallido y cooldown entre reenvíos de email. Valores por defecto en `CONFIG.md`.

## Tablas

| Tabla | Migración de creación | Categoría RGPD |
|---|---|---|
| `identidad.usuario` | `V202606030002` | `PII_PRIMARIA` |
| `identidad.invitacion` | `V202606190001` | `PII_PRIMARIA` |
| `identidad.evento_auditoria` | `V202606210001` | `AUDITORIA_IDENTIDAD` |
| `identidad.password_historico` | `V202606250001` | `PII_PRIMARIA` |
| `identidad.magic_link` | `V202606290001` | `PII_PRIMARIA` |
| `identidad.club` | `V202607210001` | `SIN_PII` — `ClubBootstrapValidator` falla el arranque si falta la fila del club configurado |
| `identidad.consentimiento` | `V202608250001` | `PII_PRIMARIA` |

La supresión de un usuario (`DeleteUserCommand`, solo ADMIN, irreversible) ocurre en una única transacción:
anonimiza los asientos previos de `evento_auditoria` que mencionaban a la persona, borra físicamente la PII
primaria, revoca sus sesiones y publica el evento `*Eliminado` al outbox — si el borrado hace rollback, el
evento no sale. Ver `RGPD.md`.

## Métricas

| Métrica | Tipo | Tags | Qué mide |
|---|---|---|---|
| `identidad.accounts.activated` | Counter | `module`, `role` | Cuentas activadas, por rol (`IdentidadBusinessMetrics`) |
| `identidad.email.invitations.sent` | Counter | `module`, `result` | Emails de invitación enviados, `success`/`error` (`IdentidadEmailMetrics`) |
| `identidad.email.magic_links.sent` | Counter | `module`, `result` | Emails de magic link enviados |
| `identidad.email.password_resets.sent` | Counter | `module`, `result` | Emails de reseteo enviados |
| `identidad.ratelimit.blocked` | Counter | `module`, `action`, `dimension` | Peticiones bloqueadas por rate limit (`IdentidadRateLimitMetrics`) |
