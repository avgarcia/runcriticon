# ADR-0003 — Autenticación invite-only sin registro público

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance mono-club), `backlog.md` (M1, M2, M3), `risks.md` (R10 email), ADR-0001 (stack y cookie de sesión), ADR-0004 (base de datos), ADR-0005 (email), ADR-0007 (monolito modular)

## Contexto y problema

El MVP es **mono-club** y no tiene registro público: las cuentas de entrenadores y alumnos las crea alguien con autoridad dentro del club, y el usuario las activa mediante una invitación. No hay pantalla de "crear cuenta" abierta a cualquiera (decisión cerrada en `vision.md`).

Hay que decidir **cómo se implementa la autenticación**: dónde viven los usuarios, cómo se crean las cuentas, cómo entran los usuarios y cómo se protege el flujo. Afecta a M1 (login), M2 (alta de entrenador) y M3 (alta de alumno).

## Drivers de la decisión

- **No hay signup público** — el flujo es siempre: alguien con autoridad crea la cuenta → invitación → el usuario la activa.
- **Reducir la fricción del registro**, sobre todo de los alumnos (que son muchos), sin abrir el registro a cualquiera.
- Coste bajo en fase beta (un club, decenas-cientos de usuarios).
- Evitar dependencia de un proveedor que encarezca al escalar o que ate el producto.
- **Datos sensibles de salud (RGPD)** → control sobre el almacén de identidad y **revocación inmediata** de sesiones.
- El stack es Spring Boot (ADR-0001), que trae Spring Security de serie.
- Credenciales sencillas para el usuario, reduciendo fricción.

## Opciones consideradas

- **Opción A** — Spring Security con almacén de usuarios propio + invitaciones por token.
- **Opción B** — Proveedor de identidad gestionado (Auth0, AWS Cognito, Clerk).
- **Opción C** — Keycloak autoalojado.

### Opción A — Spring Security + almacén propio

Los usuarios viven en la base de datos del propio sistema. Spring Security gestiona la sesión. Las invitaciones son tokens de un solo uso con caducidad enviados por email.

- 👍 Cero coste de proveedor; cero *vendor lock-in*.
- 👍 Control total sobre los datos de identidad (relevante por RGPD).
- 👍 Spring Security ya viene con el stack; el flujo invite-only es simple de modelar.
- 👍 El modelo de roles (admin / entrenador / alumno) vive junto al resto del dominio.
- 👎 Hay que implementar y mantener el flujo de invitación, activación, reseteo y magic link.
- 👎 La seguridad del flujo es responsabilidad propia (hashing, rotación de tokens, rate limiting).

### Opción B — Proveedor gestionado (Auth0 / Cognito / Clerk)

- 👍 Flujos de invitación, reseteo y MFA ya hechos.
- 👎 Coste recurrente que crece con usuarios activos.
- 👎 *Vendor lock-in*; los datos de identidad salen del sistema (fricción RGPD).
- 👎 El registro público desactivado y el flujo "alguien del club crea la cuenta" exige configuración no trivial en estos proveedores, pensados para self-signup.

### Opción C — Keycloak autoalojado

- 👍 Open source, sin coste de licencia, control de los datos.
- 👎 Es un servicio más que desplegar, parchear y mantener — operación pesada para un MVP mono-club.
- 👎 Sobredimensionado para decenas/cientos de usuarios de un solo club.

## Decisión

**Opción A: Spring Security con almacén de usuarios propio e invitaciones por token.**

El flujo invite-only encaja mucho mejor con un almacén propio que con proveedores diseñados para self-signup. Evita coste y *lock-in* en una fase en la que el producto aún se valida, y mantiene los datos de identidad —sensibles por RGPD— dentro del sistema. Spring Security cubre lo esencial sin añadir infraestructura.

### Almacén e identidad

- Tabla de usuarios en la BD principal (ADR-0004), dentro del módulo **Identidad y acceso** (schema `identidad`, ADR-0007). Cada usuario tiene rol (`admin` / `entrenador` / `alumno`) y `club_id`.

### Creación de cuentas

- **Entrenadores** — alta individual por el admin del club. Volumen bajo, confianza alta.
- **Alumnos** — **delegación a entrenadores**: cada entrenador da de alta a sus propios alumnos (el admin también puede). Reparte la carga de registro. Exige que el modelo de permisos autorice al entrenador a crear cuentas de alumno.
- Toda cuenta creada genera una **invitación**; **no existe auto-registro**.
- **Primer admin del club** — se crea por **semilla**: un comando de *setup* parametrizado y versionado que crea el club y su primer admin; ese admin activa después su cuenta por el flujo normal. No se insertan filas a mano en la base de datos.
- **Evolución prevista (no MVP)** — *solicitud de acceso + aprobación*: el propio usuario teclea sus datos en un formulario y el club los aprueba. Mueve la mecanografía al usuario manteniendo el control.

### Activación y credenciales

- La invitación es un **token de un solo uso con caducidad (~7 días)** enviado por email (ADR-0005). Al activarla, el usuario fija sus credenciales.
- Dos métodos de credencial, **ambos en el MVP**:
  - **Contraseña**.
  - **Magic link** (passwordless): login mediante un enlace de un solo uso enviado al email.
- En invite-only, la propia invitación **verifica el email** (el usuario recibe y abre el enlace) — no hace falta un paso de verificación de email aparte.
- **Login con Google (OAuth2)**: aplazado a post-MVP. El modelo lo admite como añadido posterior sin cambio estructural.

### Sesión

- Tras el login, la sesión se mantiene con una **cookie de sesión** `httpOnly`, `SameSite=Lax`, `Secure`, servida en el mismo dominio que la SPA (ADR-0001). No se usan JWT.
- **Sesión deslizante**: la cookie es persistente y se renueva en cada uso; **caduca tras 30 días de inactividad**.
- **Tope absoluto de 90 días**: pasado ese tiempo el usuario se reautentica aunque haya estado activo — un punto extra de seguridad acorde con los datos de salud.
- **Cierre de sesión instantáneo**: la sesión se invalida en el servidor.
- Al escalar a varias instancias, la sesión pasa a un almacén compartido (**Spring Session sobre Redis**) — decisión para entonces, sin cambiar este modelo.

### Endurecimiento del flujo

- **Rate limiting** en login, solicitud de magic link y reseteo de contraseña (por IP y por cuenta). Contadores en memoria en el MVP; en Redis al escalar.
- **Throttling progresivo** ante fallos de login (retardo creciente) en lugar de **bloqueo duro de cuenta** — evita que un atacante bloquee a una víctima a propósito (denegación de servicio).
- **Tokens hasheados**: los tokens de invitación, magic link y reseteo se guardan **hasheados** en la BD, son de **un solo uso** y tienen **caducidad corta** (magic link y reseteo ~15 min; invitación ~7 días).
- **Contraseñas hasheadas con Argon2id** (primera recomendación vigente de OWASP).
- **Sin registro público**: no existe endpoint ni pantalla de auto-registro. Una cuenta solo nace de una invitación.

## Consecuencias

### Positivas

- Sin coste de proveedor ni *lock-in* durante la beta.
- Datos de identidad bajo control directo — facilita el cumplimiento RGPD; revocación de sesión inmediata.
- El modelo de roles y `club_id` queda integrado con el dominio (módulo Identidad y acceso).
- La delegación a entrenadores reparte la carga de alta de alumnos.
- El magic link reduce la dependencia de contraseñas y su superficie de robo.

### Negativas / coste asumido

- Hay que implementar y mantener: invitación, activación, reseteo, magic link, delegación de alta, rate limiting y throttling.
- La seguridad del flujo (hashing, caducidad y un solo uso de tokens, rate limiting) es responsabilidad del equipo.
- La delegación a entrenadores no elimina la mecanografía, solo la reparte, y exige ampliar el modelo de permisos.

### Riesgos y mitigaciones

- **Las invitaciones dependen del email** (R10) → proveedor de email fiable con dominio autenticado (ADR-0005). Fallback: el admin o el entrenador puede copiar y compartir el enlace de invitación manualmente.
- **Implementación insegura del propio auth** → usar los mecanismos estándar de Spring Security sin inventar; revisión de seguridad antes del primer usuario real; Argon2id; tokens hasheados.
- **Tokens de invitación / magic link filtrados** → un solo uso + caducidad corta + hasheados en BD + invalidación al usarse.
- **Bloqueo de cuenta usado como denegación de servicio** → throttling progresivo en lugar de bloqueo duro.

## Notas

- Si post-MVP se abre el registro público o llega multi-club con SSO corporativo, reabrir esta decisión — un proveedor gestionado podría tener sentido entonces.
- **Login con Google** y **MFA** no entran en el MVP; ambos se añaden sobre este modelo sin cambio estructural.
- La **solicitud de acceso + aprobación** es la evolución prevista del registro cuando el producto crezca.
- La delegación de alta a entrenadores implica que M3 (alta de alumno) debe permitir al rol entrenador crear cuentas de alumno — refinar en el backlog.
