# ADR-0005 — Proveedor de email transaccional

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `risks.md` (R10 — email poco fiable rompe la puesta en marcha), ADR-0003 (autenticación invite-only), ADR-0004 (base de datos), ADR-0006 (infraestructura), ADR-0007 (monolito modular — registro de eventos), ADR-0008 (hexagonal y DDD)

## Contexto y problema

Todo el alta de usuarios depende del email: las **invitaciones** a entrenadores y alumnos (ADR-0003), la activación de cuenta, el reseteo de contraseña y el magic link. El journey de puesta en marcha del admin (`journeys/admin-setup.md`) se rompe si los emails no llegan o caen en spam — está registrado como **riesgo R10**.

Hay que decidir **qué proveedor** envía el email transaccional y **cómo** se integra el envío en el backend.

## Drivers de la decisión

- **Entregabilidad ante todo**: un email de invitación en spam es un usuario perdido (R10).
- Volumen bajo en beta (decenas-cientos de emails), con picos en el alta masiva inicial del club.
- Coste contenido.
- Integración sencilla desde Spring Boot.
- Capacidad de **autenticar el dominio** (SPF, DKIM, DMARC) y de observar entregas/rebotes.

## Opciones consideradas

- **Opción A** — Amazon SES.
- **Opción B** — Proveedor especializado en email transaccional (Postmark).
- **Opción C** — Servidor SMTP propio.

### Opción A — Amazon SES

Servicio de email de AWS.

- 👍 Coste muy bajo por email — ventaja real a volumen alto.
- 👍 Encaja "en familia" con la infraestructura en AWS (ADR-0006).
- 👍 Soporta autenticación de dominio (SPF/DKIM/DMARC) y métricas de entrega/rebote.
- 👎 La buena entregabilidad **no es automática**: depende de configurar bien el dominio.
- 👎 Arranca en *sandbox* — hay que solicitar la salida con margen antes del lanzamiento.

### Opción B — Postmark

Proveedor centrado exclusivamente en email transaccional.

- 👍 **Entregabilidad excelente por defecto** y reputación de IP cuidada — ataca directamente el driver principal (R10).
- 👍 *Onboarding* y plantillas muy sencillos; buena observabilidad; webhooks de rebote/queja.
- 👍 Sin *sandbox* que gestionar el día del lanzamiento.
- 👍 Neutral respecto a la nube — no depende de la decisión de ADR-0006.
- 👎 Coste por email mayor que SES — irrelevante al volumen de la beta.
- 👎 Un proveedor más, fuera de la nube principal.

### Opción C — SMTP propio

- 👍 Sin coste de proveedor.
- 👎 **Entregabilidad mala** sin un trabajo enorme de reputación de IP. Choca de frente con R10. Descartada.

## Decisión

**Opción B: Postmark.**

El driver nº 1 es la **entregabilidad** (R10): en la beta, una invitación perdida tumba el *onboarding* del club piloto. Postmark da entregabilidad excelente **por defecto**, con menos configuración susceptible de error y sin *sandbox*. SES gana en coste y en coherencia con AWS, pero ese valor es **futuro** (cuando el volumen crezca) y de **comodidad operativa** — no lo que decide el éxito del piloto, y el sobrecoste de Postmark es irrelevante al volumen de la beta. El SMTP propio se descarta por entregabilidad.

### Envío asíncrono con *outbox*

El envío de email es un **adaptador de salida** (ADR-0008) y **no** se ejecuta dentro de la transacción que crea el usuario — eso sería un problema de doble escritura (crear el usuario y llamar a Postmark son dos sistemas que no se pueden confirmar de forma atómica).

En su lugar:

- La creación del usuario **confirma** y, en la **misma transacción**, registra un evento de dominio (p. ej. `UsuarioInvitado`).
- Un proceso aparte consume el evento y envía el email vía Postmark, **con reintentos**.
- Se apoya en el **registro de publicación de eventos de Spring Modulith** (ADR-0007), que actúa como *outbox*: persiste el evento y lo reintenta tras un fallo o un reinicio. Garantía: **si el usuario se creó, su email se enviará** (entrega al menos una vez).
- El alta masiva no bloquea la petición HTTP.

### Dominio del remitente

- Los emails salen del **dominio propio del producto** (autenticado por el equipo), no del dominio del club.
- El **nombre visible** del remitente incluye el club para que el usuario lo reconozca (p. ej. *"Club Atletismo X (vía Runcriticon)"*).
- El dominio del producto se autentica **una sola vez**; no se depende del DNS del club y la solución escala a multi-club.

### Requisitos de implementación

- **Autenticar el dominio del producto desde el día 1**: SPF, DKIM y DMARC configurados y verificados antes del primer envío real.
- **Aislar el envío tras un puerto** en el backend → cambiar de proveedor (a SES el día que el volumen haga que el coste importe) es cambiar una implementación.
- **Monitorizar rebotes y quejas** vía los webhooks de Postmark; si la tasa se dispara, actuar.
- **Fallback funcional**: el admin o el entrenador puede copiar y compartir manualmente el enlace de invitación (ADR-0003) si un email concreto no llega.
- **Plantillas de email** simples y en texto claro, remitente reconocible.

## Consecuencias

### Positivas

- Entregabilidad excelente por defecto — ataca directamente R10, el riesgo dominante.
- *Onboarding* simple y buena observabilidad para un equipo pequeño.
- El envío asíncrono desacopla la creación de usuarios de un servicio externo; el alta masiva no bloquea.
- La garantía de *outbox*: usuario creado ⇒ email enviado, aunque Postmark esté caído o el sistema se reinicie.
- Proveedor neutral respecto a la nube.

### Negativas / coste asumido

- Un proveedor más, fuera de AWS.
- Coste por email mayor que SES — irrelevante al volumen de la beta, a vigilar si el volumen crece mucho.
- Consistencia eventual en el envío: el email llega poco después de crear el usuario, no en el mismo instante.

### Riesgos y mitigaciones

- **Emails de invitación en spam** (R10) → dominio del producto autenticado (SPF/DKIM/DMARC), remitente reconocible, monitorización de rebotes, y fallback de enlace manual.
- **Lock-in de Postmark** → envío aislado tras un puerto; migrar a SES es cambiar una implementación.
- **Coste si el volumen crece mucho** → revisar; SES queda como alternativa documentada para volumen alto.

## Notas

- **SES** queda como alternativa para cuando el volumen haga que el coste por email sea significativo; el cambio es de una sola pieza gracias al aislamiento tras el puerto.
- El email *de marketing* o newsletters queda fuera de este ADR — aquí solo email transaccional.
