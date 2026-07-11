# ADR-0003 — Autenticación invite-only sin registro público

- **Estado**: Aceptado
- **Fecha**: 2026-05-20 · revisado 2026-05-27 (reorganización Nivel 1: premisas heredadas + índice + numeración de sub-decisiones; incorporación de política de contraseñas detallada, reseteo por magic link, cambio de email con confirmación, auditoría de identidad, CSRF, Spring Session desde el día 1, rate limiting en tres dimensiones con cifras concretas, caducidad de contraseñas, hash de tokens con SHA, formato UUID del `userId`, recuperación por admin (D16) y estrategia de tests críticos) · **aceptado 2026-05-27**
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance mono-club, sin signup público), `backlog.md` (M1, M2, M3), `risks.md` (R10 email), ADR-0001 (stack, cookie de sesión en mismo origen), ADR-0004 (base de datos), ADR-0005 (email transaccional), ADR-0007 (monolito modular), ADR-0009 (autorización), ADR-0014 (RGPD)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre identidad y acceso. Las dieciséis sub-decisiones se agrupan en cuatro áreas:

- **Modelo de identidad (D1-D3)** — dónde viven los usuarios, qué forma tienen y cómo nacen las cuentas.
- **Flujos de identidad (D4-D9)** — invitación, activación, login, política de contraseñas, reseteo, cambio de email.
- **Endurecimiento y operación (D10-D15)** — sesión, logout, rate limiting, hashing, CSRF, auditoría.
- **Recuperación de cuentas (D16)** — qué hacer cuando el email del usuario está comprometido o inaccesible.

| #   | Sub-decisión                                                                          | Capa         |
|-----|---------------------------------------------------------------------------------------|--------------|
| D1  | [Almacén propio + Spring Security (no proveedor gestionado)](#d1)                      | Estratégica  |
| D2  | [Modelo de identidad: UUID + `club_id` + rol simple](#d2)                              | Estratégica  |
| D3  | [Creación de cuentas: semilla del admin + delegación a entrenadores](#d3)              | Operativa    |
| D4  | [Invitación y activación: token un solo uso, 7 días, hasheado](#d4)                    | Operativa    |
| D5  | [Métodos de login: contraseña + magic link en MVP](#d5)                                | Estratégica  |
| D6  | [Política de contraseñas: 12 caracteres, sin composición exigida, HIBP aplazado (ADR-0015), histórico 5](#d6) | Operativa |
| D7  | [Caducidad de contraseña a 90 días + invalidación de sesiones](#d7)                    | Operativa    |
| D8  | [Reseteo de contraseña: magic link de 15 minutos](#d8)                                 | Operativa    |
| D9  | [Cambio de email: link de confirmación al nuevo email](#d9)                            | Operativa    |
| D10 | [Sesión por cookie + Spring Session desde el día 1](#d10)                              | Operativa    |
| D11 | [Logout y revocación: instantáneo en servidor; revocación por admin](#d11)             | Operativa    |
| D12 | [Rate limiting en tres dimensiones + throttling progresivo](#d12)                      | Operativa    |
| D13 | [Hashing: Argon2id para contraseñas, SHA-256 + HMAC para tokens](#d13)                 | Operativa    |
| D14 | [CSRF activado por defecto en Spring Security](#d14)                                   | Operativa    |
| D15 | [Auditoría de eventos de identidad](#d15)                                              | Operativa    |
| D16 | [Recuperación cuando el email está comprometido o inaccesible](#d16)                   | Operativa    |

## Contexto y problema

El MVP es **mono-club** y no tiene registro público: las cuentas de entrenadores y alumnos las crea alguien con autoridad dentro del club, y el usuario las activa mediante una invitación. No hay pantalla de "crear cuenta" abierta a cualquiera (decisión cerrada en `vision.md`).

Hay que decidir **cómo se implementa la autenticación**: dónde viven los usuarios, cómo se crean las cuentas, cómo entran los usuarios y cómo se protege el flujo. Afecta a M1 (login), M2 (alta de entrenador) y M3 (alta de alumno).

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR**. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Web responsive como única plataforma del MVP** (ADR-0001). No hay app nativa que añada flujos de autenticación específicos (OAuth de iOS/Android, biometría del sistema, etc.).
- **Aplicación login-walled — sin landing pública en MVP** (ADR-0001). El primer pixel que ve el usuario está tras autenticación, lo que simplifica las decisiones de visibilidad.
- **Spring Security disponible de serie** como parte del stack JVM/Spring Boot (ADR-0001 D2/D3).
- **Datos de salud sujetos a RGPD** (ADR-0014). Obliga a control directo del almacén de identidad y a la posibilidad de **revocación inmediata** de sesiones.
- **Proveedor de email transaccional fiable** (ADR-0005). Habilita invitaciones, magic links, reseteos y confirmación de cambio de email; sin un email fiable, todo el flujo del ADR cae.
- **Mono-tenant en MVP** (ADR-0006). `club_id` está en todas las tablas desde el día 1 como preparación, pero el MVP no se enfrenta todavía a aislamiento multi-tenant ni a SSO corporativo.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| **Logins/día** en estado estable | ~500-1.100 (550 usuarios × 1-2 sesiones/día) |
| **Invitaciones al arrancar el club piloto** | ~550 en la primera semana; goteo después |
| **Magic links/día** tras la beta | 50-200 estimado bruto |
| **Magic links/minuto** en pico al arrancar el club | < 20 |
| **Reseteos de contraseña/semana** | esperable < 20 (orden mochila) |
| **Latencia p95 del login con contraseña** | < 800 ms (incluye hash Argon2id) |
| **Latencia p95 del login con magic link** | < 400 ms (sin Argon2id en la ruta) |
| **SLA del email transaccional** | cubierto por ADR-0005; promesa *"el magic link llega en < 5 min"* se sostiene |

A este orden de magnitud, **rate limiting en memoria** (D12) es suficiente en MVP. Transitar a Redis se hace cuando ADR-0006 active más de una instancia.

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

**Opción A: Spring Security con almacén de usuarios propio e invitaciones por token.** El flujo invite-only encaja mucho mejor con un almacén propio que con proveedores diseñados para self-signup. Evita coste y *lock-in* en una fase en la que el producto aún se valida, y mantiene los datos de identidad —sensibles por RGPD— dentro del sistema. Spring Security cubre lo esencial sin añadir infraestructura.

Las dieciséis sub-decisiones desarrolladas a continuación. Tres son **estratégicas** (D1, D2, D5 — almacén, modelo de identidad, métodos de login); el resto son **operativas** y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — Almacén propio + Spring Security (no proveedor gestionado)

Los usuarios viven en la base de datos del propio sistema (ADR-0004), dentro del módulo **Identidad y acceso** (schema `identidad`, ADR-0007). Spring Security gestiona la autenticación y la sesión usando los mecanismos estándar del framework, sin inventar piezas propias salvo donde el flujo invite-only lo exija (controlador de activación, magic link).

Razones detalladas en *Opciones consideradas*: la Opción B (proveedor gestionado) está diseñada para self-signup que no usamos; la Opción C (Keycloak) es overhead operativo para mono-club. La A da control sobre los datos (RGPD), cero coste de proveedor y revocación inmediata.

<a id="d2"></a>
### D2 — Modelo de identidad: UUID + `club_id` + rol simple

Tabla `identidad.usuario` con la siguiente forma mínima:

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `UUID` | **UUID v4** generado en aplicación. No secuencial, no se puede adivinar |
| `club_id` | `UUID NOT NULL` | desde el día 1 (ADR-0006), aunque MVP sea mono-tenant |
| `email` | `VARCHAR` con índice `UNIQUE` por `(club_id, lower(email))` | normalizado a minúsculas para la unicidad; se guarda como lo tecleó el usuario para visualizar |
| `nombre` | `VARCHAR` | |
| `rol` | enum `ADMIN` \| `ENTRENADOR` \| `ALUMNO` | **un único rol por usuario en MVP**. Si en el futuro un usuario tiene varios roles (e.g. admin + entrenador), se reabrirá esta decisión |
| `password_hash` | `VARCHAR NULL` | Argon2id (D13). NULL para cuentas que solo usan magic link |
| `password_actualizada_en` | `TIMESTAMPTZ NULL` | usado por D7 (caducidad) |
| `estado` | enum `INVITADO` \| `ACTIVO` \| `DESACTIVADO` | |
| `creado_en`, `modificado_en` | `TIMESTAMPTZ` | |

**Rol único en MVP**: simplifica autorización (ADR-0009). Para el caso "admin que también entrena", se crea una segunda cuenta con email distinto en el MVP — no es elegante pero es trivial y no bloquea ningún caso de uso real del piloto. Se reabre cuando aparezca.

<a id="d3"></a>
### D3 — Creación de cuentas: semilla del admin + delegación a entrenadores

- **Primer admin del club** — se crea por **semilla**: un comando de *setup* parametrizado y versionado que crea el club y su primer admin. Ese admin activa después su cuenta por el flujo normal (D4). **No se insertan filas a mano** en la base de datos.
- **Entrenadores** — alta individual por el admin del club. Volumen bajo, confianza alta.
- **Alumnos** — **delegación a entrenadores**: cada entrenador da de alta a sus propios alumnos (el admin también puede). Reparte la carga de registro. Exige que el modelo de permisos (ADR-0009) autorice al rol `ENTRENADOR` a crear cuentas de `ALUMNO`.
- Toda cuenta creada genera una **invitación** (D4); **no existe auto-registro** ni endpoint público de creación de cuenta.
- **Evolución prevista (no MVP)** — *solicitud de acceso + aprobación*: el propio usuario teclea sus datos en un formulario y el club los aprueba. Mueve la mecanografía al usuario manteniendo el control.

<a id="d4"></a>
### D4 — Invitación y activación: token un solo uso, 7 días, hasheado

- La invitación es un **token de un solo uso** generado al crear la cuenta y **enviado por email** (ADR-0005).
- Caducidad: **7 días** desde la emisión.
- Almacenado **hasheado** en la BD (D13). Al usarse, se marca como consumido — no se puede reutilizar aunque el atacante tenga el texto claro del enlace.
- Al activar, el usuario:
  - Si elige contraseña, debe cumplir D6.
  - Si elige magic link, no fija contraseña y `password_hash` queda `NULL` (su login será siempre vía magic link hasta que decida configurar contraseña en su perfil).
- La propia invitación **verifica el email**: el usuario solo puede activar la cuenta si recibió y abrió el enlace en el email indicado. No hace falta un paso adicional de verificación.
- **Reinvitación**: el admin/entrenador puede reenviar la invitación si la primera caduca o se pierde. La reinvitación **emite un token nuevo** que invalida el anterior aunque no esté caducado.

<a id="d5"></a>
### D5 — Métodos de login: contraseña + magic link en MVP

Dos métodos, **ambos disponibles desde el MVP**:

- **Contraseña** — si el usuario fijó una al activar o desde su perfil. Sujeta a D6 (política), D7 (caducidad).
- **Magic link** — login mediante un enlace de un solo uso enviado al email del usuario. Caducidad **15 minutos**. No requiere contraseña previa.

El usuario puede tener contraseña fijada Y usar magic link a la vez (el magic link siempre está disponible si el email del usuario está verificado). La existencia de contraseña no desactiva el magic link.

**Login con Google (OAuth2)** y **MFA** quedan aplazados a post-MVP. El modelo los admite como añadido posterior sin cambio estructural.

<a id="d6"></a>
### D6 — Política de contraseñas

Basada en **OWASP ASVS 2024 L1** y **NIST SP 800-63B**. Conscientemente alejada de las prácticas antiguas que NIST desaconseja desde 2017 (mayúsculas + minúsculas + número + símbolo, que empujan a contraseñas predecibles como `Pass123!`).

**Requisitos al fijar contraseña**:

| Regla | Valor | Razón |
|---|---|---|
| Longitud mínima | **12 caracteres** | ASVS L1; equilibrio entre fuerza real y memorización |
| Longitud máxima | **128 caracteres** | No limitar artificialmente |
| Composición exigida | **Ninguna** (ni mayúsculas ni símbolos ni números obligados) | NIST 800-63B §5.1.1.2 |
| Comprobación contra filtraciones | **Aplazada fuera del MVP** (ADR-0015): sin verificación contra HaveIBeenPwned en H0. Diseño previsto para cuando se active — Pwned Passwords con k-anonymity (5 primeros caracteres del hash SHA-1, comparación local de los restantes) | Rechazaría contraseñas conocidas manteniendo privacidad; disparador de reapertura en ADR-0015 |
| Comparación con datos personales | **Sí**: rechazar si la contraseña contiene el nombre, el email del usuario o el nombre del club | ASVS L1 |
| Indicador de fortaleza en UI | **zxcvbn** (sugerencia, no bloqueo) | Educativo |

**Histórico de no-reutilización**: la nueva contraseña **no puede ser igual a las 5 anteriores**. Tabla `identidad.contraseña_historica (usuario_id, hash, creada_en)` con índice `(usuario_id, creada_en DESC)`. Se purga automáticamente más allá de las 5 últimas.

**Validación a doble vía**: frontend para UX (feedback inmediato), backend como única fuente de verdad. Mensajes de error específicos por regla incumplida. Cuando se active HIBP (ADR-0015), su rechazo llevará mensaje genérico (*"esta contraseña aparece en filtraciones públicas; elige otra"*) para no revelar el motivo exacto.

**Lo que NO se hace**:

- No se exige composición arbitraria — práctica obsoleta según NIST.
- No se usan preguntas de seguridad — NIST 800-63B las desaconseja desde 2017.
- No se mantiene lista propia de palabras prohibidas además de HIBP — arbitraria y débil frente a HIBP.

<a id="d7"></a>
### D7 — Caducidad de contraseña a 90 días + invalidación de sesiones

- **Caducidad**: 90 días desde `password_actualizada_en`.
- **Al caducar**: el siguiente login (con contraseña o magic link) detecta la caducidad y **fuerza al usuario a crear una nueva contraseña** antes de seguir.
- **Al cambiar la contraseña** (por caducidad o voluntariamente desde el perfil): se **invalidan todas las sesiones activas** del usuario y se le obliga a volver a iniciar sesión en cualquier dispositivo donde estuviera logueado. Implementación: se borran sus filas en la tabla de sesiones de Spring Session (D10).
- **El magic link no caduca por esta regla**: el usuario sigue pudiendo entrar con magic link aunque su contraseña haya expirado. Al entrar, se le mostrará la pantalla de "tu contraseña ha expirado, créa una nueva" antes de continuar al destino.
- **Recordatorio antes de la caducidad**: ~7 días antes, banner en la UI invitando a cambiar voluntariamente.

<a id="d8"></a>
### D8 — Reseteo de contraseña: magic link de 15 minutos

- Flujo: usuario pide reseteo → se envía un **magic link de 15 minutos** al email del usuario → al hacer clic, el usuario fija una contraseña nueva (sujeta a D6) sin necesidad de conocer la antigua.
- El reseteo **invalida todas las sesiones activas** del usuario (igual que D7).
- Sin verificación cruzada de la contraseña antigua: el reseteo está pensado para *"olvidé mi contraseña"*. Para *"quiero cambiar mi contraseña porque sí"* desde dentro de la app, se pide la antigua + la nueva en el perfil del usuario.
- **Mensaje genérico**: la respuesta a *"pedí reseteo"* es siempre la misma (*"Si el email existe, te hemos enviado un enlace"*) — no revelar si una cuenta existe.

<a id="d9"></a>
### D9 — Cambio de email: link de confirmación al nuevo email

El usuario puede cambiar su email desde su perfil. El flujo:

1. El usuario introduce el email nuevo.
2. El sistema **envía un link de confirmación al email nuevo** (no al actual). Caducidad **15 minutos**.
3. Hasta que el usuario hace clic en ese link, **el email no cambia** — el email actual sigue siendo válido para login, magic link, etc.
4. Al confirmar, el `email` se actualiza, el evento queda auditado (D15) y se **invalidan todas las sesiones activas** del usuario.
5. Opcionalmente, se envía un email informativo al email **antiguo** notificando del cambio (defensa contra secuestro de cuenta).

Mensaje genérico para no revelar si el nuevo email pertenece ya a otra cuenta (caso colisión).

<a id="d10"></a>
### D10 — Sesión por cookie + Spring Session desde el día 1

Tras el login, la sesión se mantiene con:

- Cookie **`httpOnly`, `SameSite=Lax`, `Secure`**.
- Servida en el **mismo dominio** que la SPA (ADR-0001 D11). No JWT.
- **Sliding**: la cookie se renueva en cada uso. Caduca tras **30 días de inactividad**.
- **Tope absoluto de 90 días**: pasado ese tiempo el usuario se reautentica aunque haya estado activo — un punto extra acorde con datos de salud.

**Spring Session desde el día 1** — aunque el MVP corra con una sola instancia, **se usa la API de Spring Session** desde la primera línea de código, no `HttpSession` ad-hoc del contenedor. Razón: cuando ADR-0006 active más de una instancia y la sesión deba pasar a un almacén compartido (Redis), el cambio es de **configuración** (un *bean* del backend de almacenamiento), no de **código**. Implementación inicial: Spring Session con almacén `Map` en memoria. Migración a Redis cuando proceda.

Esta decisión es crítica para que la transición a multi-instancia sea barata. Si el equipo usa `HttpSession` ad-hoc, refactorizar más tarde cuesta.

<a id="d11"></a>
### D11 — Logout y revocación de sesión

- **Logout del usuario**: borra la sesión actual en el servidor de forma inmediata (la fila correspondiente en el almacén de Spring Session). Sin esperar a la caducidad.
- **Revocación por admin**: el admin del club puede **forzar el logout de un usuario concreto** desde una pantalla de administración. Se borran **todas** las sesiones activas de ese usuario en el almacén de Spring Session. Caso típico: el alumno deja el club, el admin desactiva la cuenta y revoca sus sesiones.
- **Desactivación de cuenta**: cambiar `estado` a `DESACTIVADO` invalida automáticamente las sesiones activas en el próximo *gate check* (Spring Security verifica el estado del usuario al renovar la cookie).
- El logout también ocurre como **efecto colateral** en otras decisiones: D7 (cambio de contraseña), D8 (reseteo), D9 (cambio de email).

**Logout de todos los dispositivos del propio usuario** (sin pasar por admin): **pendiente de análisis**. No entra en el MVP, se documenta como tarea futura en `plan-implementacion-mvp.md`. El logout efectivo por *cambio de contraseña* (D7) ya cubre el caso de uso *"sospecho que me han robado la contraseña"* — el usuario cambia la contraseña y desloguea todos sus dispositivos. Para el caso "no tengo acceso a mi email", la salida es la **recuperación por admin** (D16).

<a id="d12"></a>
### D12 — Rate limiting en tres dimensiones + throttling progresivo

**Tres dimensiones** de rate limiting aplicadas a login, solicitud de magic link, reseteo de contraseña y confirmación de cambio de email:

1. **Por IP de origen** — protege frente a ataques de fuerza bruta distribuidos por bots.
2. **Por cuenta de usuario** (`email` o `usuario_id`) — protege a la cuenta concreta.
3. **Por destinatario de email** — limita el número de magic links / invitaciones / reseteos que pueden dirigirse al mismo email en una ventana de tiempo. Necesario para evitar **spam dirigido** (un atacante pide 100 magic links al email de la víctima, llenando su bandeja) y para no quemar la cuota del proveedor de email (ADR-0005).

**Throttling progresivo** ante fallos de login (retardo creciente: 1s, 5s, 15s, 60s…) **en lugar de bloqueo duro de cuenta**. Evita que un atacante bloquee a una víctima a propósito (denegación de servicio).

**Cifras concretas iniciales** — aplican a cada uno de los flujos emisores de email (magic link, reseteo, invitación, confirmación de cambio de email). Se ajustan en cualquier momento por configuración si la beta muestra que son inadecuadas; no requieren reabrir el ADR:

| Acción | Por cuenta de usuario (email solicitante) | Por IP de origen |
|---|---|---|
| Magic link de login | **3 / hora · 10 / día** | 20 / hora · 100 / día |
| Reseteo de contraseña | 3 / hora · **5 / día** | 20 / hora · 100 / día |
| Invitación / re-invitación | (no aplica — la pide admin/entrenador) | **100 / hora desde el actor** (admin o entrenador) |
| Confirmación de cambio de email | 3 / hora · 5 / día | 20 / hora · 100 / día |

**Throttling progresivo entre peticiones del mismo email** (independiente de los límites globales): petición 2 tras 30 s de la 1, petición 3 tras 2 min de la 2, petición 4 tras 5 min de la 3. Si pide antes del *cooldown*, la respuesta es la misma genérica pero el email no se envía.

**Comportamiento al alcanzar el límite o el cooldown**:

- **Respuesta genérica siempre**: *"Si el email existe, te hemos enviado un enlace"* — idéntica con o sin límite alcanzado, para no revelar a un atacante si una cuenta existe (mismo principio que en D8).
- **El email no se envía**.
- **Audit log** (D15) registra el evento `MAGIC_LINK_RATE_LIMITED` / `RESETEO_RATE_LIMITED` / etc. con `email_hash` y `ip` para que el admin pueda investigar.
- **No se bloquea la cuenta** — solo se silencia el envío hasta que se libere la ventana. Coherente con el rechazo del bloqueo duro de cuenta como protección anti-DoS de víctima.

**Argumento de las cifras (magic link como caso base)**:

- *3/hora por cuenta* cubre el caso real *"pido, no llega, pido otra vez a los 2 min, sigue sin llegar, pido un tercero a los 10 min"*. Más allá de 3, el problema no es de rate limiting — el usuario debe contactar al admin (D16).
- *10/día por cuenta* corta el abuso sostenido sin penalizar uso multi-dispositivo legítimo.
- *20/hora por IP* cabe en el pico esperado de los NFRs (< 20 magic links/min al arrancar el club) sin penalizar IPs corporativas / WiFi compartido.
- *100/día por IP* corta ataques distribuidos limitados sin afectar uso institucional.

**Implementación**:

- **MVP**: contadores en memoria (Caffeine o equivalente).
- **Al activar más de una instancia** (ADR-0006): los contadores pasan a Redis. La API de rate limiting vive detrás de una abstracción desde el día 1 para que el cambio sea de configuración, no de código (mismo patrón que Spring Session en D10).

<a id="d13"></a>
### D13 — Hashing: Argon2id para contraseñas, SHA-256 + HMAC para tokens

- **Contraseñas** — **Argon2id** (primera recomendación vigente de OWASP). Parámetros iniciales: `m=19 MiB, t=2, p=1` (los valores OWASP de baseline en 2024; se revisan en cada revisión de OWASP ASVS — ver Notas).
- **Tokens** de un solo uso (invitación, magic link, reseteo, cambio de email) — **SHA-256 con HMAC** y secreto de aplicación. **No Argon2id**: los tokens ya son aleatorios largos (al menos 256 bits de entropía); aplicar Argon2 sería pagar coste de cómputo sin beneficio frente a fuerza bruta. SHA-256 + HMAC es suficiente para evitar que un atacante con acceso a la BD pueda reproducir tokens.
- **Comparación constante** (timing-safe equality) al validar contraseñas y tokens — evita *side channels* por diferencias de tiempo.

<a id="d14"></a>
### D14 — CSRF activado por defecto en Spring Security

- **Spring Security CSRF activado** para todos los endpoints que modifican estado (POST, PUT, DELETE, PATCH). Token CSRF generado por Spring; el frontend lo lee de la cookie y lo reenvía como cabecera (`X-XSRF-TOKEN`).
- **Razón**: aunque la cookie es `SameSite=Lax` (D10) — que reduce el ataque CSRF — no lo elimina por completo. Activar CSRF tiene coste cero adicional, lo trae Spring Security por defecto.
- **No desactivar CSRF "porque molesta en pruebas"**: en lugar de ello, los tests de integración configuran el token correctamente. La desactivación de CSRF en producción es una vulnerabilidad de manual.
- Endpoints de login y de activación de invitación tienen **mecanismos propios** de protección (rate limiting de D12 + token un solo uso de D4) y técnicamente no necesitan CSRF — pero se mantiene activado para uniformidad y como defensa en profundidad.

<a id="d15"></a>
### D15 — Auditoría de eventos de identidad

Tabla `identidad.evento_auditoria` que registra eventos relevantes para investigar incidentes:

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `UUID` | |
| `tipo` | enum `INVITACION_EMITIDA \| INVITACION_ACTIVADA \| LOGIN_OK \| LOGIN_FALLIDO \| MAGIC_LINK_EMITIDO \| MAGIC_LINK_USADO \| PASSWORD_CAMBIADA \| PASSWORD_CADUCADA \| RESETEO_INICIADO \| EMAIL_CAMBIO_INICIADO \| EMAIL_CAMBIO_CONFIRMADO \| SESION_REVOCADA \| CUENTA_DESACTIVADA` | |
| `actor_id` | `UUID NULL` | quien ejecuta la acción (admin que invita, usuario que se loguea…). Puede ser `NULL` para acciones del sistema |
| `sujeto_id` | `UUID NULL` | sobre quién recae la acción (usuario invitado, usuario que cambió de email…) |
| `ts` | `TIMESTAMPTZ` | |
| `ip` | `INET NULL` | IP de origen cuando aplica |
| `metadata` | `JSONB NULL` | información adicional específica del tipo de evento |

**Retención**: 12 meses por defecto. Después, se purgan los más antiguos. Si entra una exigencia regulatoria que pida más, se reabre ADR-0014.

**Privacidad**: la auditoría no es visible para el usuario auditado salvo a través de un *Data Subject Access Request* en el marco RGPD (ADR-0014). El admin del club puede ver eventos relativos a sus usuarios desde una pantalla de administración (cubrir esta pantalla está fuera de este ADR; será una funcionalidad de la sección de Salud del club).

<a id="d16"></a>
### D16 — Recuperación cuando el email está comprometido o inaccesible

El resto del ADR asume implícitamente que el email del usuario funciona y es accesible. Pero hay dos casos reales que sin esta sub-decisión dejarían al usuario bloqueado:

- **Email comprometido** — un atacante tiene acceso al email del usuario y puede pedir magic links, resetear contraseña o confirmar cambio de email (D9). El propio usuario sabe que está pasando pero no tiene cómo recuperarse usando los flujos normales.
- **Email inaccesible** — el usuario ya no tiene acceso a su email (despido y dominio corporativo cancelado, cuenta cerrada, contraseña perdida del proveedor). Todos los flujos de recuperación pasan por email; sin acción del admin, el usuario queda fuera para siempre.

El modelo invite-only del MVP da una salida natural: **el admin del club tiene autoridad para recuperar usuarios bloqueados o comprometidos**, coherente con el modelo de confianza ya documentado (mono-club, alta confianza interna, admin como autoridad última sobre las cuentas).

#### Flujo A — Email comprometido

1. El usuario contacta al admin **fuera de banda** (presencial, teléfono — no por la app porque está comprometida).
2. El admin, desde una pantalla de administración, **revoca todas las sesiones activas** del usuario (extiende D11).
3. El admin **fuerza un reseteo de contraseña** — emite un magic link de 15 minutos al email del usuario (igual que D8).
4. **Opcionalmente** (si el email se confirma como comprometido), el admin **cambia el email del usuario** al email nuevo que el usuario indique fuera de banda. Esto invalida cualquier reseteo en curso emitido al email viejo.
5. Audit log (D15) registra: `SESION_REVOCADA`, `RESETEO_INICIADO_POR_ADMIN`, `EMAIL_CAMBIADO_POR_ADMIN`.

#### Flujo B — Email inaccesible

1. El usuario contacta al admin fuera de banda.
2. El admin **cambia el email del usuario** al nuevo. **Sin confirmación por email del propio usuario** — la confianza la aporta el admin, que verifica la identidad fuera de banda.
3. El sistema emite una **invitación nueva** al email nuevo (no un magic link de login normal). Fuerza al usuario a fijar credenciales como en su primer acceso.
4. La cuenta y todo su historial (sesiones reportadas, marcas, personalizaciones) se mantienen — solo cambia el email asociado y el estado pasa a `INVITADO` hasta que el usuario active.
5. Audit log: `EMAIL_CAMBIADO_POR_ADMIN`, `INVITACION_REEMITIDA`.

#### Reglas de fondo

- El admin del club **puede cambiar el email de un usuario directamente, sin confirmación por email del propio usuario**. La confirmación es **fuera de banda** (responsabilidad del admin). Esto es seguro en mono-club con confianza alta; **no escala a multi-club** y se reabre en ese momento.
- **El cambio de email por el admin invalida todas las sesiones activas** del usuario (mismo efecto que D9 cuando lo cambia el propio usuario).
- **Re-invitación, no magic link** cuando el cambio es por inaccesibilidad: el usuario debe activar de nuevo con el flujo de D4 (fija contraseña o elige magic link). Un magic link de login no basta porque la cuenta queda equivalente a "vuelta a invitada".
- **El audit log es obligatorio** para todas las acciones del admin sobre identidad de otros usuarios. El admin no puede actuar "en silencio" sobre ninguna cuenta — el audit log es invariante de diseño, no opcional.
- **Notificación al email antiguo**: cuando el admin cambia el email por compromiso (no por inaccesibilidad), el sistema envía un email informativo al email antiguo notificando del cambio — defensa contra abuso del propio admin.

#### Lo que NO entra

- **Recuperación sin admin** (preguntas de seguridad, "alternative email") — descartada. NIST 800-63B desaconseja preguntas de seguridad; mantener un "alternative email" duplica el problema sin resolverlo.
- **Recuperación basada en verificación de identidad documental** — sobrecoste para MVP. Si en el futuro entra un club con datos más sensibles, o si el modelo deja de ser mono-club, se reabre.
- **Acción autónoma del usuario sobre su email cuando ha perdido acceso** — descartada: si el usuario no controla su email actual, cualquier autorización propia es trivialmente sospechosa. El admin es la única salida razonable.

## Estrategia de tests críticos

Los tipos de test los fija **ADR-0010** (pirámide: unitarios + integración con Testcontainers + contrato + ArchUnit + fronteras de Modulith). Esta tabla señala qué **casos** del modelo de identidad son los que duelen si fallan en producción. Si CI verde no cubre estos casos concretos, el ADR-0003 no se considera implementado.

| Ámbito | Caso crítico | Tipo de test | Por qué duele |
|---|---|---|---|
| **D4 — token de invitación** | Token usado una segunda vez → rechazado. Token caducado → rechazado. Token con email distinto al destinatario → rechazado. | Integración con Testcontainers | Sin esto, un atacante con un token filtrado puede activar la cuenta varias veces o tras la caducidad. |
| **D5 — magic link** | Magic link de 15 min → caduca a los 16. Un solo uso. Login OK invalida el token. | Integración con Testcontainers | Magic links reusables son tickets abiertos para tomar la cuenta. |
| **D6 — política de contraseñas** | Longitud < 12 → rechazada. Contraseña que contiene el email del usuario → rechazada. Las 5 anteriores → rechazadas. Sin requisitos arbitrarios de composición. HIBP aplazado (ADR-0015): sin test hasta que se active. | Unitario del validador (`PasswordPolicyTest`) | Si la política no es estricta, el resto de las defensas se debilita. |
| **D7 — caducidad** | Contraseña con `password_actualizada_en` > 90 días → siguiente login fuerza cambio. Cambio voluntario o forzado → todas las sesiones se invalidan. | Integración con Testcontainers | Si la invalidación no funciona, una sesión robada sobrevive al cambio de contraseña. |
| **D8 — reseteo** | Tras reseteo, todas las sesiones del usuario se borran. La nueva contraseña debe cumplir D6. | Integración con Testcontainers | Bug en la invalidación = la víctima sigue compartiendo sesión con el atacante. |
| **D9 — cambio de email** | Hasta confirmar, el email **no cambia**. Tras confirmar, sesiones invalidadas y email notificado al antiguo. Mensaje genérico ante email colisión. | Integración con Testcontainers + email mock | Bug en el flujo permite secuestrar cuentas con email de víctima en lista. |
| **D10 — Spring Session** | El backend usa `org.springframework.session.Session`, no `javax.servlet.http.HttpSession` ad-hoc. Test ArchUnit que falle si aparece `HttpSession` directo. | ArchUnit | Si se cuela `HttpSession`, migrar a Redis cuesta. |
| **D11 — revocación** | Admin revoca → todas las sesiones del usuario desaparecen del almacén Spring Session. Cuenta `DESACTIVADO` → siguiente *gate check* rechaza. | Integración con Testcontainers | La revocación es la palanca de seguridad principal — debe funcionar siempre. |
| **D12 — rate limiting** | Tras N intentos de login en T tiempo desde la misma IP, throttling progresivo. Idem por cuenta. Idem por destinatario de email (no se pueden pedir 100 magic links al mismo email en 1 hora). | Integración + tests de tiempo controlado | Sin rate limiting efectivo, brute force trivial y spam dirigido. |
| **D13 — hashing** | Argon2id con los parámetros de baseline. Comparación constante (timing-safe). Tokens hasheados SHA-256+HMAC, no guardados en texto claro. | Unitario + ArchUnit (no `equals` con tokens) | Errores aquí abren todas las puertas. |
| **D14 — CSRF** | POST sin token CSRF → rechazado por Spring Security. Test que verifique que CSRF está activado en producción. | Integración | Una desactivación accidental abriría CSRF; el test lo detecta. |
| **D15 — auditoría** | Cada evento de la enumeración de D15 emite una fila correctamente. Acciones que fallan no emiten fila de éxito. | Integración con Testcontainers | Sin auditoría, investigar incidentes es imposible. |
| **D16 — recuperación por admin** | Admin revoca sesiones de un usuario → invalida todas en almacén Spring Session + emite `SESION_REVOCADA` con `actor_id = admin`. Admin cambia email de un usuario → invalida sesiones + emite `EMAIL_CAMBIADO_POR_ADMIN` + reemite invitación al email nuevo + (si cambio por compromiso) email informativo al antiguo. Un rol no `ADMIN` que intente estas acciones → rechazado por autorización (cruza con ADR-0009). | Integración con Testcontainers + email mock | El admin es la última línea de recuperación; si los flujos fallan, hay cuentas bloqueadas para siempre o cambios silenciosos no auditados. |

## Consecuencias

### Positivas

- Sin coste de proveedor ni *vendor lock-in* durante la beta.
- Datos de identidad bajo control directo — facilita el cumplimiento RGPD; revocación de sesión inmediata.
- El modelo de roles y `club_id` queda integrado con el dominio (módulo Identidad y acceso).
- La delegación a entrenadores reparte la carga de alta de alumnos.
- El magic link reduce la dependencia de contraseñas y su superficie de robo.
- La política de contraseñas basada en NIST / OWASP 2024 evita prácticas obsoletas que empujan a contraseñas predecibles.
- Spring Session desde el día 1 hace barata la transición a multi-instancia con Redis.
- Auditoría desde el día 1 permite investigar incidentes y rendir cuentas RGPD si se exige.

### Negativas / coste asumido

- Hay que implementar y mantener: invitación, activación, reseteo, magic link, cambio de email, delegación de alta, rate limiting, throttling, auditoría.
- La seguridad del flujo (hashing, caducidad y un solo uso de tokens, rate limiting, CSRF) es responsabilidad del equipo.
- La delegación a entrenadores no elimina la mecanografía, solo la reparte, y exige ampliar el modelo de permisos (ADR-0009).
- Comprobación contra HIBP (aplazada fuera del MVP, ADR-0015) introduciría una dependencia externa al fijar contraseña — mitigación prevista: *k-anonymity* (no se filtra la contraseña) y *fallback* documentado (ver Riesgos), a aplicar cuando se active.

### Riesgos y mitigaciones

- **Las invitaciones dependen del email** (R10) → proveedor de email fiable con dominio autenticado (ADR-0005). Fallback: el admin o el entrenador puede copiar y compartir el enlace de invitación manualmente.
- **Implementación insegura del propio auth** → usar los mecanismos estándar de Spring Security sin inventar; revisión de seguridad antes del primer usuario real; Argon2id; tokens hasheados con SHA-256+HMAC; comparación constante.
- **Tokens de invitación / magic link filtrados** → un solo uso + caducidad corta + hasheados en BD + invalidación al usarse.
- **Bloqueo de cuenta usado como denegación de servicio** → throttling progresivo en lugar de bloqueo duro.
- **API HIBP no disponible al fijar contraseña** (aplica cuando se active HIBP, ADR-0015 — hoy no hay llamada a HIBP) → fallback previsto: aceptar la contraseña si cumple el resto de reglas D6 y emitir warning operativo. **No** bloquear al usuario por una dependencia externa caída. Audit log marca el evento.
- **Cookies sin banner RGPD** → la cookie de sesión es **técnica esencial** y queda fuera del consentimiento RGPD según interpretación común (ePrivacy + AEPD). Decisión por ahora: **no se muestra banner de cookies en MVP**. Si entra analítica de terceros u otras cookies no esenciales, se reabre con ADR-0014.

## Notas

- Si post-MVP se abre el registro público o llega multi-club con SSO corporativo, reabrir esta decisión — un proveedor gestionado podría tener sentido entonces.
- **Login con Google** y **MFA** no entran en el MVP; ambos se añaden sobre este modelo sin cambio estructural.
- La **solicitud de acceso + aprobación** es la evolución prevista del registro cuando el producto crezca.
- **Logout de todos los dispositivos por el propio usuario** sin pasar por admin queda como **tarea pendiente de análisis** (apuntada en `plan-implementacion-mvp.md`). El logout efectivo por cambio de contraseña (D7) cubre temporalmente el caso de uso "sospecho que me han robado la contraseña".
- **Revisión periódica del algoritmo de hashing**: cada nueva versión del OWASP ASVS o de las recomendaciones de NIST 800-63B se revisan los parámetros de Argon2id y la posibilidad de cambiar de algoritmo. Mientras tanto, los parámetros son los valores de baseline OWASP en el momento del arranque del desarrollo.
- **Multi-rol por usuario** (e.g., admin que también entrena) no entra en MVP. Caso real se resuelve creando dos cuentas con emails distintos; trivial en el piloto. Se reabre si el dolor aparece.
- **Reorganización del 2026-05-27 (Nivel 1)**: el ADR se reestructura con índice, premisas heredadas, NFRs explícitos, numeración D1-D15 con anchors, política de contraseñas detallada (D6), caducidad e invalidación de sesiones (D7), reseteo por magic link (D8), cambio de email con confirmación (D9), Spring Session desde el día 1 (D10), tercera dimensión de rate limiting (D12), distinción de hash entre contraseñas y tokens (D13), CSRF activado explícitamente (D14), tabla de auditoría (D15), y tabla de tests críticos. Alineado con ADR-0001 y ADR-0002.
- **Revisión del 2026-07-11**: D6 formaliza el aplazamiento de la comprobación HIBP — el código (`PasswordPolicy.kt`) ya la omitía desde H0 sin que constara aquí ni en ADR-0015. Entrada añadida a la tabla maestra de ADR-0015 con disparador de reapertura; sin cambio de comportamiento. Detectado por auditoría de drift documentación-código (23 docs, 61 hallazgos).
