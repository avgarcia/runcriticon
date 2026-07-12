# Glosario — lenguaje ubicuo de Runcriticon

Este glosario fija el **lenguaje ubicuo** del proyecto (DDD, ADR-0008): los términos del dominio que se usan **igual** en el discovery, en las conversaciones de negocio, en los wireframes y en el código. Un término, un significado. El vocabulario está en **castellano** y así se escribe también en el código, para no introducir deriva de traducción.

Si un término cambia o se añade uno nuevo, se actualiza **aquí primero**.

> **Alcance**: dominio + transversales clave (los que un nuevo desarrollador necesita para leer cualquier ADR). Los términos puramente operativos de implementación (AMP, KMS, GHCR, etc.) viven en sus ADRs correspondientes y no se duplican aquí.

## Personas y roles

- **Club** — la organización deportiva que usa Runcriticon. El MVP es mono-club (ADR-0006).
- **Admin** (del club) — administra el club: da de alta entrenadores, gestiona la taxonomía, ve la salud del club.
- **Entrenador** — crea y publica planes; da de alta y sigue a sus alumnos.
- **Alumno** — el corredor; recibe su plan, ejecuta las sesiones y las reporta.

## Taxonomía y grupos

- **Tag** — una etiqueta `{clave, valor}` que el club asigna a sus alumnos. Es la unidad con la que se construye la taxonomía (ADR-0002).
- **TagKey** (clave de tag) — un eje de la taxonomía del club (p. ej. *nivel*, *objetivo*, *terreno*).
- **TagValue** (valor de tag) — un valor posible de una `TagKey` (p. ej. *medio* para *nivel*).
- **Taxonomía** — el conjunto de `TagKey` y `TagValue` que un club ha definido. Cada club inventa la suya.
- **Grupo** — una **consulta nombrada sobre tags**: el conjunto de alumnos que cumplen unos tags requeridos. No es una lista estática; se recalcula (ADR-0002).
- **Carrera** / **objetivo** — un `TagValue` de la clave *objetivo* que representa una carrera; lleva metadata (fecha, distancia).
- **Override de grupo** — excepción manual de pertenencia que prevalece sobre la consulta del grupo.

## Planificación

- **Plan semanal** — el plan de entrenamiento de una semana que un entrenador publica a un grupo.
- **Sesión** — una unidad de entrenamiento dentro de un plan (un día): distancia, ritmo, descripción.
- **Tipo de sesión** — categoría de la sesión. Catálogo canónico del MVP:
  - **Rodaje** — carrera continua a ritmo cómodo (incluye los suaves de recuperación).
  - **Series** — repeticiones intensas con recuperación entre ellas.
  - **Tempo** — esfuerzo continuo sostenido a ritmo de umbral / objetivo.
  - **Tirada larga** — la salida larga de la semana.
  - **Fartlek** — carrera con cambios de ritmo libres.
  - **Cuestas** — repeticiones en pendiente.
  - **Progresivo** — carrera continua donde el ritmo sube progresivamente hacia el final.
  - **Fuerza / Cross** — trabajo de fuerza o entrenamiento cruzado (bici, natación, gimnasio…).
  - **Competición** — el alumno corre una carrera real.
  - **Descanso** — sin entrenamiento.
- **Ritmo** — la intensidad de una sesión. Puede ser **absoluto** o **relativo** (ADR-0002).
- **Ritmo absoluto** — `mm:ss/km` en cifras concretas (p. ej. `3:30/km`). Lo introduce el entrenador y todos los alumnos del grupo lo ven igual.
- **Ritmo relativo** — se expresa como un **delta sobre una marca** del corredor: *"ritmo de 10K + 10s/km"*, *"ritmo de maratón − 5s/km"*. El delta puede ser positivo (más lento que la marca) o negativo (más rápido). Cada alumno ve su ritmo absoluto ya resuelto a partir de **su** marca; un mismo plan se traduce a ritmos distintos por alumno.
- **Marca** — el mejor tiempo del corredor en una distancia estándar (5K, 10K, 21K, 42K). La gestiona **solo el alumno** y nadie más del club la ve — ni entrenador ni admin. Vive en el módulo Seguimiento. Sin marca, los ritmos relativos basados en esa distancia no se pueden resolver y el alumno ve un mensaje para que la rellene.
- **Personalización** — el ajuste de una sesión del plan para un alumno concreto del grupo. Sobrescribe la sesión base solo para ese alumno y vive como entidad hija del `PlanSemanal` en el módulo Planificación. Contiene un *override* de la sesión y, opcionalmente, un *mensaje para el alumno*. El alumno **no** recibe ningún indicador de que su sesión está personalizada; el mensaje, si existe, es la única señal explícita.
- **Mensaje para el alumno** — texto libre opcional que el entrenador adjunta a una personalización; el alumno lo ve junto a su sesión en la vista "hoy". Sustituye al antiguo "motivo" (que era nota interna) por un campo con propósito explícito: comunicación entrenador → alumno.
- **Publicar** (un plan) — la acción de entregar un plan semanal a un grupo; congela un *snapshot* de membresía.
- **Snapshot** — la lista de alumnos resueltos en el momento de publicar; cambios posteriores de tags no la alteran.

## Seguimiento

- **Reporte de sesión** — lo que el alumno registra sobre una sesión ejecutada: **estado** (hecho / parcial / no hecho), **valoración 1-5** de sensaciones (obligatoria si "hecho" o "parcial"), **motivo** si "no hecho" (cansancio · trabajo · viaje · enfermedad · sin tiempo · molestias · otra), **notas** y, opcionalmente, **marca de dolor** (que también se activa de forma automática al elegir "molestias" como motivo).
- **Alerta** — una señal que el sistema levanta para el entrenador a partir de los reportes.
- **Salud del club** — la vista agregada del estado del club; es un *read model* (ADR-0004, ADR-0007).

## Identidad y acceso

- **Invitación** — el mecanismo por el que nace una cuenta: un token de un solo uso enviado por email (ADR-0003 D4). No hay registro público.
- **Token de invitación** — el secreto de un solo uso que se manda en la invitación; se almacena hasheado, caduca a los 7 días y se invalida al consumirlo (ADR-0003 D4, D13).
- **Activación** — el proceso por el que el usuario, al usar el token, fija sus credenciales (contraseña o magic link) y pone la cuenta en estado `ACTIVO` (ADR-0003 D4).
- **Magic link** — enlace de un solo uso para entrar sin contraseña. Caduca en 15 min (ADR-0003 D5/D8).
- **Reseteo de contraseña** — flujo "olvidé mi contraseña" mediante magic link al email del usuario; invalida las sesiones activas (ADR-0003 D8).
- **Cambio de email** — flujo en el que el usuario introduce un email nuevo y debe confirmar haciendo clic en un enlace enviado a ese email nuevo; hasta confirmar, el email no cambia (ADR-0003 D9).
- **Sesión** — el estado de "usuario autenticado" en el servidor, persistido vía Spring Session con cookie `httpOnly` `SameSite=Lax` `Secure` (ADR-0003 D10).
- **Revocación de sesión** — borrado inmediato de las sesiones activas de un usuario. La provoca el logout, el cambio de contraseña, el cambio de email, el reseteo o una acción del admin (ADR-0003 D11).
- **Rate limiting** — políticas que limitan el número de magic links, resets o invitaciones por cuenta, por IP o por destinatario de email en una ventana de tiempo, con throttling progresivo (ADR-0003 D12).
- **Recuperación por admin** — flujo en el que el admin del club restaura el acceso de un usuario con email comprometido o inaccesible: revoca sesiones, fuerza reseteo o cambia el email; todo auditado (ADR-0003 D16).
- **Auditoría de identidad** — registro append-only de eventos de identidad (login, magic link, cambio de contraseña, recuperación por admin, etc.) en `identidad.evento_auditoria`, con retención 12 meses (ADR-0003 D15). Es **distinta** de la auditoría de autorización (ver más abajo).

## Autorización

- **IDOR** (Insecure Direct Object Reference) — la vulnerabilidad nº 1 de OWASP API Security Top 10: un usuario accede a datos de otro usuario sólo cambiando un identificador en la petición. El modelo de autorización está diseñado para cerrarla (ADR-0009).
- **RBAC** (Role-Based Access Control) — control de acceso por **rol** (admin, entrenador, alumno). Responde a *"¿este rol puede ejecutar esta operación?"*; se aplica en el adaptador de entrada con la anotación propia `@Authorize` (o `@NoAuthRequired` justificado), evaluada contra la `MatrizDeAutorizacion` del núcleo compartido — no se usa `@PreAuthorize` de Spring Security (ADR-0009 D2, D6, D13).
- **Nivel de objeto** — comprobación, en cada caso de uso, de la **relación** entre quien pide y el objeto concreto. Responde a *"¿puede este usuario tocar este objeto?"*. Es la capa que cierra IDOR (ADR-0009 D3).
- **Principal** — el usuario autenticado en curso, representado como `(userId, clubId, rol)`. Vive en el núcleo compartido y se obtiene de la sesión actual (ADR-0009 D6).
- **Servicio de autorización por módulo** — bean que cada módulo expone para centralizar sus reglas de relación. El caso de uso llama al servicio antes de la operación; no duplica reglas en cada sitio (ADR-0009 D7).
- **Proyección stale** — proyección local de datos de relación que está atrasada respecto al origen (un evento aún sin procesar). Política **fail-closed con timeout**: si el lag supera 60 s, la autorización deniega para esa relación y se dispara alarma (ADR-0009 D9).
- **`@AuthScope`** — aspecto que inyecta filtros (`club_id`, relación del principal) en las queries de los repositorios para que los listados se filtren **en query, nunca en memoria** (ADR-0009 D10, D11).
- **`@NoAuthScope`** — anotación de excepción que marca un método de repositorio que debe saltarse el aspecto. Su uso es raro, siempre administrativo y siempre auditado (ADR-0009 D11).
- **`XxxError.Forbidden`** — variante del sealed class de error del módulo (p. ej. `IdentidadError.Forbidden`) que devuelven los servicios de autorización al denegar (incluida la razón `proyeccionStale`). El adaptador REST la traduce a HTTP 403 con cuerpo neutro (ADR-0009 D12).
- **ArchUnit guard** — tests de arquitectura obligatorios que verifican en cada PR que todo `@ApplicationService` autoriza y todo `@Repository` declara `@AuthScope` o `@NoAuthScope`. Hace imposible olvidar autorizar (ADR-0009 D13).
- **Tests de acceso cruzado** — tests obligatorios por caso de uso que intentan que un principal del mismo rol acceda al objeto de otro (dos alumnos, dos entrenadores con grupos distintos) y esperan denegación o lista vacía (ADR-0009 D14).
- **Auditoría de autorización** — registro append-only de **denegaciones siempre** y **accesos a datos sensibles** (salud + perfil personal de terceros). Vive en el módulo `auditoria` con su propio esquema y retención de 24 meses. Es **distinta** de la auditoría de identidad (ADR-0009 D15-D17).
- **Módulo `auditoria`** — módulo dedicado, consumidor de los eventos `AccesoDenegado` y `AccesoADatosSensibles` emitidos por los demás módulos. **No decide**: solo registra. No es un módulo central de autorización (ADR-0009 D17).
- **`GET /me/permissions`** — endpoint que devuelve los permisos del principal calculados server-side desde la matriz fija. El frontend lo usa para **ocultar botones** (ayuda de UX), **no como barrera**. La regla de oro sigue: cada petición se autoriza en el servidor (ADR-0009 D18).

## Protección de datos (RGPD)

- **PII primaria** — datos personales identificables que constituyen la cuenta y sus datos de salud (`identidad.usuario`, `seguimiento.alumno_perfil`, `seguimiento.reporte_sesion`, `seguimiento.marca`). Al ejercer derecho al olvido: **borrado físico** (ADR-0014 D5, D6).
- **Datos derivados** — logs de auditoría, outbox, backups y logs operativos. Al ejercer derecho al olvido: **anonimización** (campos identificadores → `NULL`, IP truncada) o caducidad pasiva, según la categoría. Mantiene la responsabilidad proactiva (ADR-0014 D6).
- **Borrado mixto** — política conjunta: físico para PII primaria + anonimización o caducidad pasiva para datos derivados. Es el patrón que resuelve la tensión entre derecho al olvido y responsabilidad proactiva del RGPD (ADR-0014 D6).
- **Anonimización** — sustitución de campos identificadores (`actor_id`, `sujeto_id`, IP completa) por `NULL` o por valores irrecuperables (IP truncada a /24), preservando la fila y su utilidad estadística sin reidentificar (ADR-0014 D6, D9).
- **Consentimiento explícito (Art. 9.2.a)** — base legal del tratamiento de datos de salud en el MVP. El usuario consiente al activar la cuenta sobre un texto versionado; el consentimiento se persiste con fecha, IP, versión y se puede revocar (ADR-0014 D16, D18).
- **RAT** (Registro de Actividades de Tratamiento, Art. 30) — documento versionado en `docs/legal/rat.md` que enumera los tratamientos, finalidades, categorías de datos, destinatarios, transferencias y plazos. Obligatorio por tratar datos de salud (ADR-0014 D19). **Pendiente de redactar** — el directorio `docs/legal/` no existe todavía en el repo.
- **DPIA** (Data Protection Impact Assessment, Art. 35) — evaluación de impacto en la protección de datos. El MVP documenta una **DPIA simplificada** antes del lanzamiento (ADR-0014 D20).
- **DPO** (Delegado de Protección de Datos, Art. 37) — figura de la organización responsable de la protección de datos. En el MVP, **sin DPO formal** con análisis documentado de por qué no aplica (ADR-0014 D21).
- **Subencargado** — proveedor externo que trata datos personales en nombre del responsable. La lista nominal del MVP es AWS, Postmark y GitHub (ADR-0014 D22).
- **DPA** (Data Processing Agreement) — acuerdo de encargado del tratamiento que el responsable firma con cada subencargado. Pendiente jurídico, no técnico (ADR-0014 D22).
- **Responsable del tratamiento** — la entidad jurídica que decide los fines y medios del tratamiento. En Runcriticon: **Runcriticon S.L.** (ADR-0014 D23).
- **Brecha de seguridad** — incidente que compromete la confidencialidad, integridad o disponibilidad de datos personales. Se notifica a la AEPD en ≤ 72 h (Art. 33) y, si hay alto riesgo, a los afectados (Art. 34). Existe un runbook (ADR-0014 D24-D26).

## Conceptos técnicos transversales

### Modularidad y comunicación entre módulos

- **Módulo** / **bounded context** — una de las áreas del dominio con frontera explícita: Identidad y acceso, Club y taxonomía, Planificación, Seguimiento, Auditoría (ADR-0007).
- **Spring Modulith** — framework que materializa los módulos del backend: detecta sus fronteras, las verifica con tests y provee el outbox local (ADR-0007 D6).
- **Eventos-first** (events-first) — patrón de comunicación entre módulos: nunca llaman síncronamente unos a otros; se comunican publicando **eventos**. Mantienen sus propias **proyecciones locales** de lo que necesitan saber del resto (ADR-0007).
- **Evento de dominio** — un hecho relevante que ha ocurrido **dentro** de un módulo, nombrado en pasado (`PlanPublicado`). Lo consume el propio módulo y, eventualmente, otros como evento de integración (ADR-0007 D7).
- **Evento de integración** — evento de dominio elevado a contrato público entre módulos. Tiene seis campos obligatorios (`eventId`, `aggregateId`, `occurredAt`, `version`, `clubId`, `actorId`) y JSON Schema versionado en el repo (ADR-0007 D11).
- **Outbox** — patrón que persiste el evento en la **misma transacción** que la operación de negocio, para entregarlo después de forma fiable. En el MVP, el outbox es el **registro de eventos de Spring Modulith** (tabla `event_publication` en el mismo PostgreSQL), no Kafka ni RabbitMQ (ADR-0007 D6).
- **`event_publication`** — tabla del outbox local de Spring Modulith donde se persisten los eventos pendientes de entrega y los que ya se entregaron. Se compacta a los 30 días (ADR-0007 D6, D15).
- **DLQ implícita** — eventos en `event_publication` que han agotado los 5 reintentos. Disparan alarma; el admin los republica vía endpoint dedicado (ADR-0007 D13).
- **Política de fallos del outbox** — 5 reintentos con backoff exponencial; tras agotarlos, DLQ implícita + alarma + republicación admin (ADR-0007 D13).
- **`@ApplicationModuleListener`** — anotación de Spring Modulith que marca un consumidor de eventos entre módulos. Es la API estándar para los listeners (ADR-0007 D6).
- **Proyección local** / **read model** — copia local que un módulo mantiene de datos de otro, alimentada por eventos. Soluciona la lectura sin acoplamiento síncrono (ADR-0007 D8).
- **Idempotencia** — un consumidor de eventos debe poder procesar el mismo evento más de una vez sin efectos colaterales (el outbox garantiza **at-least-once**, no exactly-once) (ADR-0007 D9).
- **Reproyección** — reconstrucción de una proyección local a partir del histórico de eventos. Permite recuperarse de la compactación de los 30 días o de cambios de esquema (ADR-0007 D15).

### Hexagonal y DDD

- **Agregado** / **aggregate root** — raíz de un grupo de objetos del dominio que protege sus invariantes. El acceso al estado interno pasa por la raíz (ADR-0008).
- **Value object** — concepto del dominio sin identidad propia, inmutable, definido por sus valores (ej. `Ritmo`, `Distancia`) (ADR-0008).
- **Puerto** — interfaz declarada en `domain` que representa algo que cruza a infraestructura (repositorio, publicador de eventos, enviador de email) (ADR-0008 D9).
- **Adaptador** — implementación de un puerto, vive en `infrastructure`. Hay adaptadores de **entrada** (controladores REST, listeners de eventos) y de **salida** (repositorios JPA, clientes HTTP) (ADR-0008 D9).
- **`@ApplicationService`** — marca un servicio de la capa de aplicación que orquesta el dominio. Cada método público es un caso de uso (ADR-0008 D7).
- **`Either<XxxError, T>`** — patrón (Arrow-kt, con Raise DSL) para devolver errores como **valor**, no como excepción. Los errores de dominio (validación, autorización, conflicto) cruzan capas como `Either`; las excepciones quedan para fallos del framework (ADR-0008 D11).
- **`XxxError`** — sealed class de error **por módulo** (`IdentidadError`, `PlanificacionError`, …) — sin núcleo de errores compartido, cada módulo enumera las suyas. Variantes comunes: `Forbidden`, `NotFound`, `InvalidInput`, `Conflict`, `ProjectionStale`. El adaptador REST lo traduce a códigos HTTP estables (ADR-0008 D11).
- **Konvert** — librería de mapping en tiempo de compilación (no reflection) que el proyecto usa para convertir entre agregados de dominio y entidades de persistencia. Coherente con dominio puro (ADR-0008 D6).
- **Typed IDs** — identificadores tipados como `value class UUID v7` (`PlanId`, `AlumnoId`, `ClubId`), no `String` ni `UUID` genéricos. Evita pasar un ID de tipo equivocado a un método (ADR-0008).
- **Dominio puro** — la capa `domain` no tiene **ningún** import de framework (ni Spring, ni JPA, ni Jackson). Es código Kotlin puro, completamente testeable sin servicios (ADR-0008 D6).
- **ArchUnit** — librería de tests que verifica las reglas de arquitectura: dependencias entre capas, ausencia de imports prohibidos, fronteras de Modulith, garantías de autorización (ADR-0008 D14, ADR-0009 D13, ADR-0010).

### Tenencia y aislamiento

- **`club_id`** — identificador de club presente en **todas** las tablas de dominio desde la primera migración, para preparar el multi-club aunque el MVP sea mono-club. Filtro sistemático en repositorios y en el aspecto `@AuthScope` (ADR-0006 D22, ADR-0009 D4).
- **Mono-tenant en MVP** — el MVP soporta un único club. La preparación multi-tenant vive en `club_id` desde el día 1 y en el subdominio por club al activar el multi-club (ADR-0006 D16, D22).

### Identificador de correlación

- **`trace_id`** — identificador único de una petición que se propaga a logs, métricas y trazas. Permite reconstruir end-to-end (incluyendo flujos asíncronos vía eventos) qué pasó (ADR-0011 D4, D5).
- **W3C Trace Context** — estándar de propagación del `trace_id` entre servicios y eventos (cabecera `traceparent`). Es lo que OpenTelemetry usa por defecto (ADR-0011 D4).
- **MDC** (Mapped Diagnostic Context) — contexto operativo que se añade a cada línea de log: `trace_id`, `club_id`, `user_id_hash`, `module`, `env`. El frontend y el backend respetan el mismo contrato (ADR-0011 D5).

> Referencias: ADR-0002 (tags, grupos, ritmos), ADR-0003 (identidad), ADR-0007 (módulos y eventos), ADR-0008 (lenguaje ubicuo, hexagonal), ADR-0009 (autorización), ADR-0014 (RGPD), y los documentos de discovery en `docs/`.
