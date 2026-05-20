# ADR-0003 — Autenticación invite-only sin registro público

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance mono-club), `backlog.md` (M1, M2, M3), `risks.md` (R10 email), ADR-0001 (stack), ADR-0005 (email)

## Contexto y problema

El MVP es **mono-club** y no tiene registro público: el admin del club da de alta a entrenadores y alumnos, que reciben una invitación por email. No hay pantalla de "crear cuenta" abierta a cualquiera (decisión cerrada en `vision.md`).

Hay que decidir **cómo se implementa la autenticación**: dónde viven los usuarios, cómo entran, qué proveedor (si alguno) se usa. Afecta a M1 (login), M2 (alta de entrenador), M3 (alta de alumno).

## Drivers de la decisión

- **No hay signup público** — el flujo es siempre: el admin crea la cuenta → se envía invitación → el usuario la activa.
- Coste bajo en fase beta (un club, decenas-cientos de usuarios).
- Evitar dependencia de un proveedor que encarezca al escalar o que ate el producto.
- Datos sensibles de salud (RGPD) → el control sobre el almacén de identidad es deseable.
- El stack es Spring Boot (ADR-0001), que trae Spring Security de serie.
- Permitir login con email/contraseña y con Google (conveniencia para el alumno).

## Opciones consideradas

- **Opción A** — Spring Security con almacén de usuarios propio + invitaciones por token.
- **Opción B** — Proveedor de identidad gestionado (Auth0, AWS Cognito, Clerk).
- **Opción C** — Keycloak autoalojado.

### Opción A — Spring Security + almacén propio

Los usuarios viven en la base de datos del propio sistema. Spring Security gestiona sesiones/JWT. Las invitaciones son tokens de un solo uso con caducidad enviados por email. Login social con Google vía OAuth2 (soportado nativamente por Spring Security).

- 👍 Cero coste de proveedor; cero *vendor lock-in*.
- 👍 Control total sobre los datos de identidad (relevante por RGPD).
- 👍 Spring Security ya viene con el stack; el flujo invite-only es simple de modelar.
- 👍 El modelo de roles (admin / entrenador / alumno) vive junto al resto del dominio.
- 👎 Hay que implementar y mantener el flujo de invitación, reseteo de contraseña y verificación de email.
- 👎 La seguridad es responsabilidad propia (hashing, rotación de tokens, rate limiting).

### Opción B — Proveedor gestionado (Auth0 / Cognito / Clerk)

- 👍 Flujos de invitación, reseteo y MFA ya hechos.
- 👎 Coste recurrente que crece con usuarios activos.
- 👎 *Vendor lock-in*; los datos de identidad salen del sistema (fricción RGPD).
- 👎 El registro público desactivado y el flujo "admin crea la cuenta" exige configuración no trivial en estos proveedores, que están pensados para self-signup.

### Opción C — Keycloak autoalojado

- 👍 Open source, sin coste de licencia, control de los datos.
- 👎 Es un servicio más que desplegar, parchear y mantener — operación pesada para un MVP mono-club.
- 👎 Sobredimensionado para decenas/cientos de usuarios de un solo club.

## Decisión

**Opción A: Spring Security con almacén de usuarios propio e invitaciones por token.**

El flujo invite-only encaja mucho mejor con un almacén propio que con proveedores diseñados para self-signup. Evita coste y *lock-in* en una fase en la que el producto aún se valida, y mantiene los datos de identidad —sensibles por RGPD— dentro del sistema. Spring Security cubre lo esencial sin añadir infraestructura.

Detalles:

- **Almacén**: tabla de usuarios en la BD principal (ADR-0004), con rol (`admin` / `entrenador` / `alumno`) y `club_id`.
- **Alta**: el admin crea el usuario → se genera un **token de invitación de un solo uso con caducidad** (p. ej. 7 días) → se envía por email (ADR-0005) → el usuario fija su contraseña y la cuenta queda activa.
- **Login**: email + contraseña, y **Google OAuth2** como alternativa de conveniencia.
- **Sesión**: la decisión cookie de sesión vs JWT se concreta al implementar; por defecto, sesión con cookie httpOnly (más simple y segura para una webapp).
- **Sin registro público**: no existe endpoint ni pantalla de auto-registro. Una cuenta solo nace de una invitación del admin.

## Consecuencias

### Positivas

- Sin coste de proveedor ni *lock-in* durante la beta.
- Datos de identidad bajo control directo — facilita el cumplimiento RGPD.
- El modelo de roles y `club_id` queda integrado con el dominio.

### Negativas / coste asumido

- Hay que implementar y mantener invitación, reseteo de contraseña y verificación de email.
- La seguridad del flujo (hashing fuerte, caducidad y un solo uso de tokens, rate limiting en login) es responsabilidad del equipo.

### Riesgos y mitigaciones

- **Las invitaciones dependen del email** (R10) → proveedor de email fiable con dominio autenticado; ver ADR-0005. Fallback: el admin puede copiar y compartir un enlace de invitación manualmente.
- **Implementación insegura del propio auth** → usar los mecanismos estándar de Spring Security sin inventar; revisión de seguridad antes del primer usuario real; hashing con algoritmo recomendado vigente.
- **Tokens de invitación filtrados** → un solo uso + caducidad corta + invalidación al activarse.

## Notas

- Si post-MVP se abre el registro público o llega multi-club con SSO corporativo, reabrir esta decisión — un proveedor gestionado podría tener sentido entonces.
- MFA no entra en MVP; se puede añadir sobre Spring Security más adelante sin cambiar este modelo.
