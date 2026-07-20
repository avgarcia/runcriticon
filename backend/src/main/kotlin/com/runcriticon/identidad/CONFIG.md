# Configuración — módulo Identidad

Catálogo de secretos y propiedades no secretas que consume este módulo, según [`configuracion-y-secretos-en-modulos.md`](../../../../../../../docs/arquitectura/configuracion-y-secretos-en-modulos.md) §3. Fuente de verdad verificada contra `backend/src/main/resources/application.yml`.

## Secretos consumidos

| Secreto SSM | Variable de entorno | Tipo | Uso |
|---|---|---|---|
| `/runcriticon/{env}/db/password` | `DB_PASSWORD` | SecureString | Conexión RDS PostgreSQL |
| `/runcriticon/{env}/security/token-hmac-secret` | `TOKEN_HMAC_SECRET` | SecureString | Hash de tokens de un solo uso — invitación, magic link, reseteo |
| `/runcriticon/{env}/crypto/userid-hash-salt` | `USERID_HASH_SALT` | SecureString | Hash determinístico de `userId` para logs — núcleo `shared/observability`, no exclusivo de identidad |
| `/runcriticon/{env}/email/postmark-server-token` | `POSTMARK_SERVER_TOKEN` | SecureString | Cabecera `X-Postmark-Server-Token` para el envío de emails transaccionales |
| `/runcriticon/{env}/identidad/bootstrap-admin-password` | `RUNCRITICON_BOOTSTRAP_ADMIN_PASSWORD` | SecureString | Contraseña del admin de semilla — solo `staging`, `IdentidadSeeder` |

## Propiedades no secretas

| Propiedad | Valor por defecto | Uso |
|---|---|---|
| `runcriticon.email.from-address` | `invitaciones@runcriticon.com` | Remitente de los emails transaccionales |
| `runcriticon.email.from-name` | `Runcriticon` | Nombre visible del remitente |
| `runcriticon.email.base-url` | `https://app.runcriticon.com` | Base para construir enlaces de activación/magic link |
| `runcriticon.email.postmark.server-url` | `https://api.postmarkapp.com` | Endpoint de la API de Postmark |
| `runcriticon.security.session-sliding-timeout` | `30d` | Expiración deslizante de la sesión |
| `runcriticon.security.session-absolute-max` | `90d` | Tope absoluto de sesión desde la autenticación |
| `runcriticon.identidad.ratelimit.magic-link` | `account-hourly: 3, account-daily: 10, ip-hourly: 20, ip-daily: 100` | Rate limit de solicitud de magic link |
| `runcriticon.identidad.ratelimit.password-reset` | `account-hourly: 3, account-daily: 5, ip-hourly: 20, ip-daily: 100` | Rate limit de reseteo de contraseña |
| `runcriticon.identidad.ratelimit.invitation-per-actor-hourly` | `100` | Rate limit de invitaciones por actor |
| `runcriticon.identidad.ratelimit.login` | `[1s, 5s, 15s, 60s]` | Backoff progresivo de login fallido |
| `runcriticon.identidad.ratelimit.email-cooldown` | `[30s, 2m, 5m]` | Cooldown entre reenvíos de email al mismo destinatario |

## Solo local (`application-local.yml`, nunca en staging/producción)

| Propiedad | Uso |
|---|---|
| `runcriticon.bootstrap.admin-email` | Email del admin de semilla en desarrollo local |
| `runcriticon.bootstrap.admin-password` | Contraseña del admin de semilla en desarrollo local — en `staging` viene de SSM (ver tabla de secretos), nunca sembrado en producción |
| `runcriticon.bootstrap.club-id` | `clubId` fijo del MVP mono-club |
