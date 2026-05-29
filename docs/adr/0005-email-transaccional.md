# ADR-0005 — Proveedor de email transaccional

- **Estado**: Aceptado
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `risks.md` (R10 — email poco fiable rompe la puesta en marcha), ADR-0003 (autenticación invite-only), ADR-0004 (base de datos), ADR-0006 (infraestructura), ADR-0007 (monolito modular — registro de eventos), ADR-0008 (hexagonal y DDD), ADR-0010 (CI/CD), ADR-0014 (protección de datos y RGPD)

## Índice

- [Contexto y problema](#contexto-y-problema)
- [Premisas heredadas](#premisas-heredadas)
- [Drivers de la decisión](#drivers-de-la-decision)
- [Requisitos no funcionales](#requisitos-no-funcionales)
- [Opciones consideradas](#opciones-consideradas)
- [Decisión](#decision)
  - **Proveedor y arquitectura del envío**
    - [D1 — Postmark como proveedor](#d1)
    - [D2 — Envío asíncrono vía outbox de Spring Modulith](#d2)
    - [D3 — Aislar el envío tras un puerto en `domain`](#d3)
    - [D4 — Dominio propio del producto autenticado, no del club](#d4)
  - **Configuración del envío**
    - [D5 — Nombre visible que incluye club + producto](#d5)
    - [D6 — SPF, DKIM y DMARC obligatorios desde el día 1](#d6)
    - [D7 — Plantillas versionadas en código, no en Postmark](#d7)
    - [D8 — Tracking de aperturas y clics desactivado](#d8)
  - **Operación y observabilidad**
    - [D9 — Webhooks de rebote y queja monitorizados](#d9)
    - [D10 — Política de fallos cruzada al outbox (ADR-0007 D13)](#d10)
    - [D11 — Rate limit en la aplicación, no en Postmark](#d11)
  - **Privacidad y cumplimiento**
    - [D12 — Reglas sobre qué se permite poner en un email](#d12)
  - **Continuidad y migración**
    - [D13 — Fallback funcional: enlace compartible manualmente](#d13)
    - [D14 — Tests del flujo: Postmark sandbox en CI, MailHog en local](#d14)
    - [D15 — Plan Trial inicial; SES con disparador concreto](#d15)
- [Consecuencias](#consecuencias)
- [Notas](#notas)

## Contexto y problema

Todo el alta de usuarios depende del email: las **invitaciones** a entrenadores y alumnos (ADR-0003), la activación de cuenta, el reseteo de contraseña, el **magic link** y las notificaciones de cambios sensibles. El journey de puesta en marcha del admin (`journeys/admin-setup.md`) se rompe si los emails no llegan o caen en spam — está registrado como **riesgo R10**.

Hay que decidir **qué proveedor** envía el email transaccional, **cómo** se integra el envío en el backend y, sobre todo, **qué reglas operativas** rodean ese envío: garantías de entrega, fallos, plantillas, privacidad y migración.

## Premisas heredadas

Decisiones ya aceptadas en otros ADRs que este ADR no rediscute:

- **Spring Modulith como infraestructura de eventos y outbox** (ADR-0007 D6, D8). El registro de publicación de eventos (`event_publication`) hace de outbox local: persiste el evento en la misma transacción que la escritura de negocio y lo reintenta.
- **Hexagonal con puertos en `domain` y adaptadores en `infrastructure`** (ADR-0008 D9). Cualquier salida a un servicio externo va tras un puerto.
- **Política de fallos sobre el outbox**: 5 reintentos con backoff exponencial; tras agotarlos, el evento queda en `event_publication` (DLQ implícita) y se republica vía endpoint admin (ADR-0007 D13).
- **Autenticación invite-only** (ADR-0003): el email es el canal **único** para invitar, activar, resetear, cambiar de email, confirmar y notificar al email antiguo. El magic link caduca en **15 min** (ADR-0003 D8). El rate limit por destinatario está fijado en aplicación (ADR-0003 D12).
- **Mono-tenant con `club_id` desde el día 1** (ADR-0006): el producto distingue clubes desde el modelo, no desde el dominio del remitente.
- **Datos de salud sujetos a RGPD** (ADR-0014): impone qué se puede y qué no se puede transportar por email.
- **CI/CD con Testcontainers y dashboard mínimo de GitHub Actions** (ADR-0010 D10, D22): aplica al adaptador de email y a sus alarmas.

## Drivers de la decisión

- **Entregabilidad ante todo**: un email de invitación en spam es un usuario perdido (R10).
- **Volumen bajo en beta** (decenas-cientos de emails/día), con picos en el alta masiva inicial del club.
- **Coste contenido** y sin sorpresas.
- **Integración sencilla** desde Spring Boot.
- **Autenticación de dominio** (SPF, DKIM, DMARC) y observabilidad de entregas/rebotes.
- **Migración asumible** a otro proveedor el día que el volumen y el coste lo justifiquen.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| Volumen sostenido | < 500 emails/día en estado estable |
| Pico en alta inicial del club | ~600 emails en la primera hora |
| **Latencia de entrega al destinatario, p95** | **< 3 min** desde la creación del evento. Anclaje: el magic link de ADR-0003 D8 caduca en **15 min** — si el email tarda más, el flujo se rompe |
| Tasa de hard bounces | < 5 % |
| Tasa de quejas (spam reports) | < 0,1 % |
| Disponibilidad del proveedor | Asumida según SLA de Postmark (~99,95 %); el outbox cubre la indisponibilidad |
| Reintentos por evento | 5 con backoff exponencial (heredado de ADR-0007 D13) |

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

**Opción B: Postmark**, con envío asíncrono apoyado en el outbox de Spring Modulith. El driver nº 1 es la **entregabilidad** (R10): en la beta, una invitación perdida tumba el *onboarding* del club piloto. Postmark da entregabilidad excelente **por defecto**, con menos configuración susceptible de error y sin *sandbox*. SES gana en coste y en coherencia con AWS, pero ese valor es **futuro** y de **comodidad operativa** — no lo que decide el éxito del piloto. El SMTP propio se descarta por entregabilidad.

La decisión se desglosa en quince sub-decisiones agrupadas por área.

### Proveedor y arquitectura del envío

#### <a id="d1"></a>D1 — Postmark como proveedor

Postmark es el proveedor del email transaccional del producto. La elección se basa en entregabilidad por defecto (R10), simplicidad operativa para un equipo pequeño y ausencia de *sandbox* que gestionar el día del lanzamiento. El sobrecoste frente a SES es **irrelevante** al volumen de la beta.

#### <a id="d2"></a>D2 — Envío asíncrono vía outbox de Spring Modulith

El envío de email **no** se ejecuta dentro de la transacción que crea o modifica el usuario: la BD y Postmark son dos sistemas que no se pueden confirmar atómicamente.

- La operación de negocio **confirma** y, en la **misma transacción**, registra un evento (por ejemplo `UsuarioInvitado`, `MagicLinkSolicitado`).
- Un listener `@ApplicationModuleListener` consume el evento y envía el email vía Postmark.
- El registro de publicación de eventos de Spring Modulith (ADR-0007 D6) hace de outbox: persiste el evento, lo reintenta tras un fallo y sobrevive a reinicios. **Garantía**: si la operación de negocio se confirmó, el email se enviará (al menos una vez).
- El alta masiva no bloquea la petición HTTP.

#### <a id="d3"></a>D3 — Aislar el envío tras un puerto en `domain`

El envío de email es un **adaptador de salida** (ADR-0008 D9). En `domain` vive un puerto `EnviadorDeEmail` (o equivalente con verbos del dominio: `enviarInvitacion`, `enviarMagicLink`, `enviarConfirmacionCambioEmail`); en `infrastructure` vive la implementación contra el SDK de Postmark.

Cambiar de proveedor (a SES el día que el volumen y el coste lo justifiquen, ver D15) es **cambiar una implementación**, no tocar `application` ni `domain`.

#### <a id="d4"></a>D4 — Dominio propio del producto autenticado, no del club

Los emails salen del **dominio propio del producto** (autenticado por el equipo), no del dominio del club. El dominio del producto se autentica **una sola vez**; no dependemos del DNS de cada club y la solución escala a multi-club sin trabajo extra por cada alta.

### Configuración del envío

#### <a id="d5"></a>D5 — Nombre visible que incluye club + producto

El nombre visible del remitente incluye el nombre del club para que el usuario lo reconozca, manteniendo el producto como sufijo de confianza: *"Club Atletismo X (vía Runcriticon)"*. El **email** (dirección técnica) es siempre del dominio del producto (D4); el **nombre visible** es el que ve el usuario en su cliente de correo.

#### <a id="d6"></a>D6 — SPF, DKIM y DMARC obligatorios desde el día 1

Antes del primer envío real, el dominio del producto debe tener:

- **SPF** publicado y alineado con Postmark.
- **DKIM** firmado y verificado.
- **DMARC** publicado, inicialmente en política `p=quarantine` o `p=reject` con `rua` apuntando a una dirección monitorizada.

El despliegue inicial **no** se considera listo hasta que los tres registros están verdes en el panel de Postmark y comprobados con una herramienta externa de auditoría de SPF/DKIM/DMARC.

#### <a id="d7"></a>D7 — Plantillas versionadas en código, no en Postmark

Las plantillas (HTML + texto plano) viven en `infrastructure`, versionadas en el repositorio. **No** se usan los *server-side templates* de Postmark.

Razones:

- Coherente con D3 (aislar tras puerto): el día que migremos a SES no hay que rehacer plantillas en otro panel.
- Cambios en plantilla pasan por **PR** y heredan el pipeline de calidad y los tests (ADR-0010).
- Mismo modelo que los JSON Schema de eventos versionados en repo (ADR-0007 D11).
- `domain` permanece puro (ADR-0008 D6): la plantilla, el HTML y el motor de renderizado son detalle de `infrastructure`.

Motor de plantillas: tecnología concreta (Thymeleaf, Mustache u otra) se decide en implementación; es un detalle de infraestructura.

#### <a id="d8"></a>D8 — Tracking de aperturas y clics desactivado

En Postmark, las opciones de *open tracking* y *click tracking* se mantienen **desactivadas** para todos los emails transaccionales.

Razones:

- No aporta valor de producto: el éxito de una invitación se mide porque el usuario activa la cuenta, señal positiva directa.
- Es invasivo: el destinatario no se ha suscrito a un seguimiento de comportamiento.
- Minimiza datos personales recolectados y los proxies en el dominio del proveedor (RGPD).

### Operación y observabilidad

#### <a id="d9"></a>D9 — Webhooks de rebote y queja monitorizados

Postmark notifica vía webhook **bounces** (rebotes duros y blandos) y **complaints** (quejas de spam). El producto:

- Expone un endpoint para recibir esos webhooks, firmado con un secreto compartido con Postmark.
- Marca al destinatario en una tabla de **direcciones bloqueadas** tras un *hard bounce* o una queja: no se le vuelve a enviar email automáticamente hasta intervención manual.
- Las tasas de rebote y queja se exponen en el dashboard de GitHub Actions / observabilidad (ADR-0010 D22 + ADR-0011). Si superan los umbrales de los NFR, alarma.

#### <a id="d10"></a>D10 — Política de fallos cruzada al outbox (ADR-0007 D13)

Cuando Postmark devuelve error transitorio (5xx, timeout, rate limit del proveedor), el listener no maneja reintentos a mano: **se apoya en la política heredada de ADR-0007 D13**:

- 5 reintentos con backoff exponencial.
- Tras agotarlos, el evento queda en `event_publication` (DLQ implícita).
- Alarma cuando hay > N eventos sin entregar en > 5 min (ADR-0010 D22).
- Republicación manual vía endpoint admin `POST /admin/events/republish` tras corregir la causa.

Errores **no transitorios** (dirección inválida, dominio bloqueado, queja previa) no se reintentan: el destinatario va a la tabla de bloqueados (D9) y el caso se registra para el operador.

#### <a id="d11"></a>D11 — Rate limit en la aplicación, no en Postmark

El control de frecuencia por destinatario (3 magic links por hora, 5 resets por día — ADR-0003 D12) vive en la **aplicación**, no se delega al proveedor. Postmark no puede distinguir un alta masiva legítima de un abuso; la aplicación sí.

Postmark tiene además su propia cuota mensual por plan (ver D15); ese límite es **operativo**, no funcional, y se monitoriza.

### Privacidad y cumplimiento

#### <a id="d12"></a>D12 — Reglas sobre qué se permite poner en un email

El email transaccional **puede** transportar:

- Magic links, enlaces de activación, enlaces de reseteo.
- Notificaciones de cambios sensibles (cambio de email — ADR-0003 D9, recuperación por admin — ADR-0003 D16).
- Nombre del usuario, nombre del club, instrucciones de uso.
- Información de contexto necesaria para que el destinatario entienda el correo.

El email transaccional **no debe** transportar:

- **Contraseñas en claro**, nunca, bajo ninguna circunstancia.
- **Datos de salud** (marcas, sesiones, reportes de entrenamiento, lesiones, observaciones médicas).
- Tokens de sesión, claves API, secretos.
- Datos de pago (tarjeta, IBAN).
- Información que el destinatario no haya solicitado explícitamente.

La regla vive aquí porque es **del medio**, no del dominio: el día que alguien añada una "notificación de reporte semanal con la marca incluida", esta sub-decisión es lo único que lo frena. Se cruza con ADR-0014.

### Continuidad y migración

#### <a id="d13"></a>D13 — Fallback funcional: enlace compartible manualmente

Cuando un email concreto no llega (rebote, dirección errónea, retraso, queja anterior), el admin o el entrenador puede **copiar el enlace de invitación** desde la UI y compartirlo por otro canal (WhatsApp, en persona).

- Cada flujo del email genera un enlace recuperable desde la UI del operador (no es regenerable: es el mismo enlace que se envió).
- La caducidad del enlace se respeta (15 min para magic link, ADR-0003 D8); para invitaciones, la ventana es la de ADR-0003.
- Es el cinturón de seguridad de R10: si Postmark falla durante una hora, el alta del club no se cae.

#### <a id="d14"></a>D14 — Tests del flujo: Postmark sandbox en CI, MailHog en local

- **CI** (ADR-0010): el adaptador de Postmark se prueba con el **server token de sandbox** que ofrece Postmark — los envíos no salen a destinatarios reales pero atraviesan la API real. Los tests de integración del módulo de identidad usan ese token.
- **Local**: docker-compose levanta **MailHog** (o equivalente) y la configuración de `application-local.yml` apunta el adaptador a ese SMTP local — no quema cuota de Postmark mientras se itera.
- **Tests E2E** que necesitan recibir el email (clicar el magic link, por ejemplo) usan MailHog: arrancan el contenedor, leen el último email vía su API y completan el flujo.

#### <a id="d15"></a>D15 — Plan Trial inicial; SES con disparador concreto

- **Plan inicial**: **Trial de Postmark** (10 000 emails gratis durante 30 días). Cubre con margen la primera oleada del club piloto.
- **Decisión del plan definitivo** durante H1 tras medir el volumen real de la beta. Si la beta arranca antes de los 30 días, el Trial cubre; si se retrasa, se pasa a plan Starter (~$15/mes, 10 000 emails/mes).
- **Disparador concreto para migrar a SES**: si el volumen estable supera **50 000 emails/mes** sostenidos durante dos meses consecutivos, **o** si el coste mensual de Postmark supera los 100 €/mes, se ejecuta la migración. Por debajo de esos umbrales, SES no aporta valor suficiente para justificar la migración.

## Consecuencias

### Positivas

- Entregabilidad excelente por defecto — ataca directamente R10, el riesgo dominante.
- *Onboarding* simple y observabilidad útil para un equipo pequeño.
- El envío asíncrono desacopla la creación de usuarios de un servicio externo; el alta masiva no bloquea.
- Garantía del outbox: operación confirmada ⇒ email entregado (al menos una vez), aunque Postmark esté caído o el sistema se reinicie.
- Proveedor neutral respecto a la nube.
- Reglas RGPD explícitas (D12) y privacidad cuidada por defecto (D8).
- Migración a SES con disparador medible (D15), no como deuda eterna.

### Negativas / coste asumido

- Un proveedor más, fuera de AWS.
- Coste por email mayor que SES — irrelevante al volumen de la beta, a vigilar conforme al disparador de D15.
- Consistencia eventual en el envío: el email llega poco después de la operación de negocio, no en el mismo instante (el NFR de < 3 min p95 marca el techo).
- Plantillas en código (D7) implican que cualquier cambio de copy pasa por PR — flujo aceptado para el MVP.

### Riesgos y mitigaciones

- **Emails de invitación en spam** (R10) → dominio propio autenticado (D4, D6), remitente reconocible (D5), monitorización de rebotes y quejas (D9), y fallback de enlace manual (D13).
- **Lock-in de Postmark** → envío aislado tras un puerto (D3), plantillas en código (D7), disparador documentado para migrar (D15).
- **Coste si el volumen crece mucho** → disparador concreto en D15.
- **Email atascado tras 5 reintentos sin que nadie lo vea** → cubierto por la alarma del outbox (ADR-0010 D22) y el endpoint admin de republicación (ADR-0007 D13).
- **Información sensible filtrada por email** → reglas explícitas en D12; el código de envío valida tipo de plantilla y rechaza payloads no permitidos.

## Notas

- **DPA (Data Processing Agreement) con Postmark** firmado antes del primer envío real; queda como obligación operativa para cumplir RGPD (ADR-0014).
- **Webhook secret** del endpoint de bounces/complaints (D9): rotación anual y al cambiar de operador del producto.
- **SES** queda como alternativa documentada; el cambio se ejecuta cuando se cumple el disparador de D15, no antes.
- **Email de marketing o newsletters** queda fuera de este ADR — aquí solo email transaccional.
- **Revisión periódica**: este ADR se revisa a los 6 meses de aceptación o si cambia el plan/precio de Postmark, lo que ocurra antes.
