# ADR-0007 — Monolito modular

- **Estado**: Aceptado
- **Fecha**: 2026-05-20 · revisado 2026-05-29 (reorganización Nivel 1 + ciclo completo de events-first: índice + premisas heredadas + NFRs + numeración de sub-decisiones D1-D15 con anchors estables; nuevas sub-decisiones D10-D15 sobre contrato de eventos, versionado con JSON Schema, distinción `domain event` interno vs `integration event` público, política de fallos sobre Spring Modulith, ordering por clave de partición `aggregateId` y reprocesamiento de proyecciones; notas de cierre con observabilidad delegada a ADR-0011 y playbook de extracción a microservicio) · **aceptado 2026-05-29**
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack), ADR-0002 (modelo de datos), ADR-0004 (esquema por módulo, retención de eventos), ADR-0005 (email por eventos), ADR-0006 (infraestructura), ADR-0008 (arquitectura hexagonal y DDD), ADR-0009 (autorización), ADR-0010 (CI/CD), ADR-0011 (observabilidad), ADR-0014 (RGPD), `risks.md` (R6 — deuda mono-tenant al generalizar)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre la **forma del backend**. Las quince sub-decisiones se agrupan en cinco áreas:

- **Topología y fronteras (D1-D3)** — qué forma tiene el backend, cómo se reparte y cómo se relacionan las partes.
- **Comunicación (D4-D7)** — cómo hablan los módulos entre sí y bajo qué garantías.
- **Enforcement y lectura (D8-D9)** — cómo se sostienen las fronteras y cómo se sirve la lectura.
- **Contrato y visibilidad de eventos (D10-D12)** — qué forma tiene cada evento, cómo se versiona y qué se expone vs qué se reserva al interior del módulo.
- **Operación de events-first (D13-D15)** — qué hacer cuando un consumidor falla, qué garantías de orden se ofrecen y cómo se recupera una proyección corrompida.

| #  | Sub-decisión                                                              | Capa         |
|----|---------------------------------------------------------------------------|--------------|
| D1 | [Monolito modular como topología](#d1)                                    | Estratégica  |
| D2 | [Descomposición en cuatro bounded contexts](#d2)                          | Estratégica  |
| D3 | [Grafo acíclico de dependencias entre módulos](#d3)                       | Estratégica  |
| D4 | [Comunicación exclusiva por eventos de dominio (events-first)](#d4)        | Estratégica  |
| D5 | [Transacción acotada a un módulo; consistencia eventual entre módulos](#d5) | Operativa  |
| D6 | [Outbox vía Spring Modulith con entrega al-menos-una-vez](#d6)             | Operativa    |
| D7 | [Consumidores idempotentes como invariante de diseño](#d7)                | Operativa    |
| D8 | [Spring Modulith como enforcer de fronteras en build](#d8)                | Estratégica  |
| D9 | [Proyecciones locales por módulo para la lectura cross-context](#d9)      | Operativa    |
| D10 | [Contrato de eventos: seis campos obligatorios + naming en pasado](#d10) | Estratégica  |
| D11 | [Versionado de eventos: JSON Schema en repo + tests de compatibilidad en CI](#d11) | Operativa |
| D12 | [Distinción entre `domain events` internos e `integration events` públicos](#d12) | Estratégica |
| D13 | [Política de fallos sobre Spring Modulith: 5 reintentos, DLQ implícita y endpoint de reproceso](#d13) | Operativa |
| D14 | [Ordering de eventos por clave de partición (`aggregateId`)](#d14) | Estratégica |
| D15 | [Reprocesamiento de proyecciones desde el outbox compactado](#d15) | Operativa |

## Contexto y problema

Hay que decidir la **forma estructural y de despliegue** del backend de Runcriticon: ¿un solo desplegable o varios servicios? ¿con fronteras internas o sin ellas? Y, dentro de esa forma, **cómo se comunican** las partes.

El MVP es mono-club, con carga baja, lo construye un equipo interno pequeño y se despliega como un contenedor en un servicio gestionado de AWS (ADR-0006). Pero el roadmap contempla multi-club, y `risks.md` (R6) advierte que un código sin fronteras internas convierte cualquier evolución futura en una reescritura.

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR** — se asumen y condicionan toda la decisión que sigue. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Spring Boot 3.x sobre JVM (Kotlin)** (ADR-0001). Esto habilita el uso de **Spring Modulith** como librería de enforcement nativa (D8) y del **registro de publicación de eventos de Spring Modulith** como outbox (D6) sin introducir infraestructura adicional.
- **Afinidad estructural Spring↔Angular** (ADR-0001 D3). El equipo full-stack se sostiene en parte porque los módulos del backend tienen una correspondencia natural con áreas funcionales de la SPA (publicar plan, gestionar grupos, etc.).
- **Mono-tenant en MVP con `club_id` desde el día 1** (ADR-0006). El monolito modular *puede* ser multi-tenant; la generalización futura es una decisión aparte. En el MVP todos los eventos llevan `clubId` aunque solo haya un club.
- **Una sola instancia PostgreSQL gestionada con un schema por módulo** (ADR-0004 D3 + D4). Esto permite que la **transacción acotada a un módulo** (D5) funcione con commits locales, sin coordinación distribuida.
- **Datos de salud sujetos a RGPD** (ADR-0014). Los eventos de dominio pueden contener PII; la retención (ADR-0004 D11) y el borrado (ADR-0004 D16) condicionan la operación de events-first y se asumen aquí como restricciones, no se redefinen.

## Requisitos no funcionales

Estas cifras son **restricciones** y condicionan tanto el dimensionado del outbox como las políticas de observabilidad (ADR-0011) que sostienen la comunicación events-first. La carga es baja (mono-club) y el modelo está acotado por ADR-0002.

| Dimensión | Valor objetivo |
|---|---|
| **Latencia evento publicado → consumido** (mismo proceso, mismo módulo o cross-módulo en el monolito) | **p95 < 1 s** en operación normal |
| **Lag aceptable de proyecciones locales** (cuánto puede tardar una proyección en reflejar un cambio del módulo dueño) | **p99 < 5 s** |
| **Tasa de eventos esperada** en pico al arrancar el club | < **200/min** durante 10 min (alta masiva de alumnos) |
| **Tasa de eventos sostenida** una vez el club opera normalmente | < **50/min** |
| **Tamaño del outbox** | < **10.000 filas** activas; coherente con la retención y compactación de 30 días de ADR-0004 D11 |
| **Tiempo de arranque del monolito** | < **30 s** desde JVM lista hasta servir tráfico; condiciona los deploys con cero downtime |
| **Consistencia transaccional dentro de un módulo** | **ACID estricta** (un solo schema PostgreSQL, una transacción local) |
| **Consistencia entre módulos** | **Eventual**, con el lag objetivo de arriba |

A este orden de magnitud, **no hay necesidad de bus de eventos externo** (Kafka, Pulsar, SQS): el outbox de Spring Modulith en el mismo proceso es suficiente y mantiene la operación simple. Cuando se escale más de una instancia (ADR-0006), el outbox sigue funcionando porque vive en la BD compartida — la decisión técnica no se rompe.

## Drivers de la decisión

- **Equipo interno pequeño** → la simplicidad operativa pesa mucho: cuantos menos desplegables, redes y piezas que observar, mejor.
- **Velocidad de MVP**: llegar a algo usable con el club piloto sin gastar tiempo en infraestructura distribuida.
- **El dominio tiene áreas claramente diferenciadas** (identidad, club/taxonomía, planificación, seguimiento) — hay fronteras naturales que merece la pena respetar.
- **Multi-club y la extracción futura de servicios son evoluciones previstas** (R6) → conviene que los módulos sean lo más **autónomos** posible para que esa extracción sea trivial.
- Carga esperada en beta: baja (un club, decenas-cientos de usuarios).

## Opciones consideradas

- **Opción A** — Monolito modular: un único desplegable, dividido internamente en módulos con fronteras explícitas.
- **Opción B** — Microservicios: varios servicios desplegables de forma independiente.
- **Opción C** — Monolito tradicional: un único desplegable sin fronteras internas (solo capas).

### Opción A — Monolito modular

Un solo proceso Spring Boot. Internamente, el código se organiza en **módulos** que se corresponden con áreas del dominio; cada módulo tiene una API pública y un interior privado, y la comunicación entre módulos está controlada y es explícita.

- 👍 Operación simple: **un solo desplegable**, una base de datos, un *pipeline*. Ideal para un equipo pequeño.
- 👍 Fronteras internas explícitas → el código se razona por partes, no como una maraña.
- 👍 El día que un módulo deba convertirse en servicio (multi-club, escalado puntual), la frontera **ya existe** — la extracción es trabajo acotado, no reescritura.
- 👍 Dentro de un módulo, una transacción de base de datos sencilla; entre módulos, comunicación por eventos en el mismo proceso — sin la complejidad de red de los microservicios.
- 👎 Requiere **disciplina**: sin enforcement, los módulos se "filtran" unos en otros y acaba siendo un monolito tradicional encubierto.

### Opción B — Microservicios

- 👍 Despliegue y escalado independientes por servicio.
- 👎 Sobrecoste operativo brutal para un equipo pequeño: varios *pipelines*, comunicación por red, datos distribuidos, observabilidad distribuida, *fallos parciales*.
- 👎 **Prematuro**: un MVP mono-club de carga baja no tiene ningún problema que los microservicios resuelvan. Se pagaría todo el coste sin ninguno de los beneficios.

### Opción C — Monolito tradicional

Un único desplegable organizado solo por capas técnicas (controllers, services, repositories), sin módulos de dominio.

- 👍 Lo más rápido de arrancar; cero ceremonia.
- 👎 Sin fronteras internas, con multi-club y crecimiento de funcionalidad por delante, deriva en un *big ball of mud*: todo depende de todo, difícil de razonar e **imposible de extraer** después sin reescribir.

## Decisión

**Opción A: monolito modular.** Es el equilibrio correcto para este proyecto: la simplicidad operativa de un único desplegable (lo que necesita un equipo pequeño y un MVP) más fronteras internas explícitas que mantienen el código sano y dejan abierta la extracción futura de servicios. Los microservicios son prematuros; el monolito tradicional hipoteca el futuro.

Las quince sub-decisiones desarrolladas a continuación. Ocho son **estratégicas** (D1, D2, D3, D4, D8, D10, D12, D14 — topología, descomposición, dependencias, communication paradigm, enforcement, contrato de eventos, visibilidad pública vs interna, ordering); el resto son **operativas** (D5, D6, D7, D9, D11, D13, D15 — transacciones, outbox, idempotencia, proyecciones, versionado, política de fallos, reprocesamiento) y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — Monolito modular como topología

Un único proceso Spring Boot, un único desplegable, una única base de datos compartida (con un schema por módulo según ADR-0004). El código se organiza internamente en módulos con fronteras explícitas, no como capas técnicas planas.

La elección queda entre los extremos: ni microservicios (Opción B, prematura para este alcance) ni monolito tradicional (Opción C, hipoteca el futuro). El monolito modular es el punto medio que sostiene a la vez la velocidad del MVP y la opcionalidad de extracción futura.

**Implicación operativa**: un único *pipeline* de CI/CD (ADR-0010), una sola configuración de observabilidad (ADR-0011), un único servicio gestionado de despliegue (ADR-0006). Coherente con la apuesta global por simplicidad para un equipo de 4.

<a id="d2"></a>
### D2 — Descomposición en cuatro bounded contexts

A partir del dominio recogido en discovery, specs y wireframes, se proponen **cuatro módulos**. La frontera de cada módulo es un *bounded context* de DDD (ADR-0008):

| Módulo | Responsabilidad | Entidades principales |
|--------|-----------------|------------------------|
| **Identidad y acceso** | Usuarios, roles, invitaciones, login. | Usuario, Rol, Invitación |
| **Club y taxonomía** | El club, los tags, el catálogo de carreras, alumnos y entrenadores como miembros, los grupos (consultas sobre tags). | Club, Tag, Alumno, Entrenador, Grupo |
| **Planificación** | Planes semanales, sesiones, editor, publicación a grupos, personalizaciones por alumno. | PlanSemanal, Sesión, Personalización |
| **Seguimiento** | Reportes de sesión, reajuste de día, panel de alertas, salud del club, marcas privadas del corredor. | ReporteSesión, Alerta, MarcaAlumno |

Esta descomposición es un **punto de partida** suficientemente ajustado para el MVP. El equipo la refina al modelar los *bounded contexts* en ADR-0008 y la revisa explícitamente si una funcionalidad nueva no encaja claramente en ninguno de los cuatro (señal de que falta un módulo o de que dos están solapados).

<a id="d3"></a>
### D3 — Grafo acíclico de dependencias entre módulos

Las dependencias por eventos entre los cuatro módulos forman un **grafo dirigido acíclico** (DAG). Un módulo solo construye sus proyecciones a partir de eventos de los módulos de los que depende conceptualmente; nunca al revés.

```
Identidad y acceso   → publica eventos (no consume de nadie)
Club y taxonomía     → consume de Identidad
Planificación        → consume de Club y taxonomía, Identidad
Seguimiento          → consume de Planificación, Club y taxonomía, Identidad
```

**Por qué acíclico**: los ciclos en eventos producen *bucles infinitos* (el módulo A publica un evento que B consume y reacciona publicando otro que A consume…) o estados inconsistentes difíciles de razonar. El DAG hace que el flujo sea siempre "hacia adelante" y trivialmente trazable.

**Spring Modulith verifica el DAG en build** (D8): si alguien introduce un `import` cruzado que rompa la dirección, el test de fronteras falla.

<a id="d4"></a>
### D4 — Comunicación exclusiva por eventos de dominio (events-first)

Los módulos se comunican **exclusivamente mediante eventos de dominio**. **No hay llamadas síncronas de un módulo a otro.**

- Cuando en un módulo ocurre algo relevante, publica un **evento de dominio** (p. ej. `AlumnoAsignadoAGrupo`, `PlanPublicado`, `UsuarioInvitado`).
- Cada módulo mantiene **proyecciones locales** (D9) de los datos de otros contextos que necesita, alimentadas por esos eventos. Un módulo **nunca pregunta** a otro: ya tiene su propia copia, actualizada.
- La **API pública** de un módulo la consumen sus **adaptadores de entrada** (controladores REST); **entre módulos, solo eventos**.

**Por qué events-first**. Hace que cada módulo sea **autónomo de verdad**: no conoce a los demás, solo sus eventos. La extracción futura a microservicio es casi inmediata — el módulo ya se comunica exactamente como lo haría un servicio (eventos asíncronos, sin acoplamiento síncrono, datos propios). Es la materialización del driver de "no cerrarse la puerta a multi-club ni a la extracción de servicios".

**Coste asumido**. La **consistencia eventual** pasa a ser la norma en toda comunicación entre módulos (D5); hay más eventos y más proyecciones que diseñar, versionar y testear; la idempotencia de los consumidores es obligatoria (D7).

<a id="d5"></a>
### D5 — Transacción acotada a un módulo; consistencia eventual entre módulos

Una **transacción** de base de datos está **acotada a un único módulo** (y, dentro de él, típicamente a un agregado en términos de DDD, ADR-0008). Nunca se abre una transacción que toque schemas de dos módulos a la vez.

Consecuencia directa: **la consistencia entre módulos es eventual**, con el lag objetivo fijado en los NFRs (p99 < 5 s). Lo orquesta la capa de aplicación reaccionando a eventos, no la base de datos con transacciones distribuidas.

Esta restricción simplifica enormemente la operación (no hay 2PC, no hay coordinador transaccional, no hay protocolos distribuidos) a cambio de obligar a pensar **qué es lo que el usuario verá inconsistente y durante cuánto tiempo**. Los wireframes de la SPA ya están diseñados con esta asunción (ej. el toast *"Marta verá la personalización al refrescar"* del ADR-0002 D9).

<a id="d6"></a>
### D6 — Outbox vía Spring Modulith con entrega al-menos-una-vez

La publicación de eventos no es *fire-and-forget* en memoria, que perdería eventos al primer reinicio o crash. Se usa el **registro de publicación de eventos de Spring Modulith** como **outbox**:

- El evento se **persiste en la misma transacción** que el cambio de estado del agregado emisor. Si la transacción falla, ni el cambio ni el evento ocurren.
- Tras el commit, el evento se **entrega a los consumidores registrados**.
- Si un consumidor falla, el evento se **reintenta**: la entrega es **al-menos-una-vez** (*at-least-once*).
- En un crash o reinicio del proceso, los eventos no entregados aún están en el outbox y se procesan al reanudar.

**Por qué outbox y no broker externo**: con una sola instancia y carga baja, el outbox local en la misma BD basta. No introduce un nuevo elemento operativo (broker, conexiones, autenticación). Coherente con los NFRs. Cuando se pase a multiinstancia el outbox sigue funcionando porque la BD es compartida; el cambio a broker externo (Kafka, SQS) solo se justifica si los volúmenes lo exigen y vivirá en otro ADR cuando llegue.

<a id="d7"></a>
### D7 — Consumidores idempotentes como invariante de diseño

Como D6 garantiza entrega **al-menos-una-vez**, un evento **puede entregarse más de una vez**. En consecuencia, **todo consumidor de eventos debe ser idempotente**: procesar el mismo evento dos veces produce el mismo resultado que procesarlo una sola.

Esta no es una recomendación, es **invariante de diseño**. El equipo lo asume al escribir cualquier `@ApplicationModuleListener` y los tests del consumidor lo verifican explícitamente (cruce con ADR-0010).

Patrones aceptables para conseguir idempotencia:

- **UPSERT** (`INSERT … ON CONFLICT DO UPDATE`) cuando la proyección es estado actual del agregado.
- **Tabla de eventos procesados** indexada por `eventId` cuando hay efectos colaterales no idempotentes (envío de email, llamada externa). El consumidor verifica antes de actuar.
- **Operaciones naturalmente idempotentes** (asignar un alumno a un grupo dos veces es lo mismo que asignarlo una vez si el modelo lo permite).

<a id="d8"></a>
### D8 — Spring Modulith como enforcer de fronteras en build

Las fronteras no se mantienen solas. Se usa **Spring Modulith** (proyecto oficial de Spring para monolitos modulares): organiza los módulos por paquetes, **verifica las fronteras en los tests** y documenta las dependencias.

Concretamente:

- Cada módulo se identifica con `@Modulith` (o por convención de paquetes).
- Los tests de fronteras (`@ApplicationModuleTest` y `ApplicationModules.verify()`) **fallan en CI** si:
  - Un módulo importa internas de otro.
  - El grafo de dependencias entre módulos contiene un ciclo.
- Spring Modulith genera documentación (PlantUML / AsciiDoc) del grafo de módulos a partir del código real.

Es más ligero que partir el build en submódulos Gradle (que implicaría JARs separados, configuración Gradle más compleja, build más lento) y encaja nativamente con el stack (ADR-0001). Si en el futuro hiciera falta un aislamiento más duro (módulos como artefactos versionados independientes), partir en submódulos Gradle queda como evolución prevista; **hoy es overhead innecesario**.

<a id="d9"></a>
### D9 — Proyecciones locales por módulo para la lectura cross-context

Cuando un módulo necesita datos de otro contexto, **no los pregunta**: mantiene una **proyección local** (read model) alimentada por los eventos del módulo dueño.

Ejemplo: para resolver el snapshot de membresía al publicar un plan, Planificación **no llama** a Club y taxonomía. Mantiene en su propio schema una proyección `planificacion.miembros_de_grupo` que se actualiza con `@ApplicationModuleListener` cuando llega `AlumnoAsignadoAGrupo` o `AlumnoEliminadoDeGrupo` desde Club y taxonomía.

**Implicaciones**:

- Las proyecciones viven en el schema del módulo consumidor (cruce con ADR-0004 D5).
- Cada proyección está **versionada con su productor**: si cambia el evento, la proyección puede necesitar adaptarse.
- Las proyecciones pueden quedar momentáneamente **desactualizadas** (lag de D5); el código que las consulta debe asumirlo (típicamente irrelevante; las pantallas de admin/entrenador toleran segundos de retraso).
- Es un **CQRS ligero**: el agregado del módulo dueño protege la escritura; las proyecciones de los consumidores sirven la lectura cross-context. No se fuerza la ceremonia de CQRS completo sobre las consultas dentro de un mismo módulo (ADR-0008).

<a id="d10"></a>
### D10 — Contrato de eventos: seis campos obligatorios + naming en pasado

Todo **evento de dominio** del proyecto cumple el siguiente contrato. Sin contrato común, la deduplicación es frágil, la idempotencia (D7) difícil y la auditoría incompleta. Esta sub-decisión la convierte en invariante de diseño verificado en CI.

**Campos obligatorios** en todo evento:

| Campo | Tipo | Para qué |
|---|---|---|
| `eventId` | `UUID v7` | Identidad única del evento. Habilita la **deduplicación** en consumidores y soporta la idempotencia de D7. |
| `aggregateId` | `UUID v7` | A qué cosa del dominio se refiere el evento. Clave para reproyectar proyecciones (`WHERE aggregate_id = ?`). |
| `occurredAt` | `Instant` (`TIMESTAMPTZ` en BD, ADR-0004 D8) | Cuándo ocurrió el evento. Ordenable temporalmente; el `eventId` UUID v7 lo refuerza con ordenación K-sortable a microsegundos. |
| `version` | `Int` | Versión del contrato del evento. Habilita el versionado (D11). Empieza en `1` y se incrementa con cada cambio aditivo del schema. |
| `clubId` | `UUID v7` | Tenant. **Obligatorio incluso en MVP mono-tenant** (ADR-0006): los consumidores filtran por `clubId` desde el día 1 y la generalización futura a multi-tenant no requiere migrar el contrato. |
| `actorId` | `UUID v7?` | Quién originó el cambio (el `userId` que ejecutó la acción). `null` solo cuando la acción es del sistema (job programado, evento derivado sin actor humano). Habilita auditoría de negocio sin tablas adicionales. |

**Convención de naming**: el evento se nombra como el **verbo en pasado de la acción ocurrida** (`PlanPublicado`, `AlumnoAsignadoAGrupo`, `MarcaActualizada`). Imperativos como `PublicarPlan` están **prohibidos** — esos son comandos, no eventos. La diferencia importa porque define qué módulo es el dueño del cambio: un evento expresa un hecho consumado por su productor; un comando es una petición a un futuro productor.

Test ArchUnit: toda clase que implementa `IntegrationEvent` vive en `…api.events…` (`IntegrationEventArchTest`, D12) — eso fuerza indirectamente los seis campos, porque `IntegrationEvent` los declara. **No hay hoy** un guard de naming que verifique el participio pasado (`-do`, `-da`, `-ado`, `-ada`, `-ido`, `-ida`) — un imperativo como `PublicarPlan` compilaría igual; queda como revisión manual de PR.

**Distinción con el `evento_auditoria` de Identidad** (ADR-0003 D15). Son **dos cosas diferentes** que conviene no confundir:

| Aspecto | Event store por módulo (esta D10 + ADR-0004 D11) | `identidad.evento_auditoria` (ADR-0003 D15) |
|---|---|---|
| Qué registra | Cambios de **negocio**: planes publicados, marcas actualizadas, personalizaciones añadidas | Acciones de **identidad/seguridad**: logins, cambios de password, sesiones revocadas, invitaciones |
| Quién lo consume | Otros módulos (vía proyecciones) + investigación de "quién hizo el cambio en negocio" | Sólo admin/seguridad: investigación de incidentes de cuenta |
| Retención | 30 días + compactación al último estado (ADR-0004 D11) | 12 meses sin compactación (ADR-0003 D15) |
| Propietario | El módulo emisor (`identidad.event`, `club_taxonomia.event`, etc.) | Módulo `identidad` exclusivamente |

Con `actorId` obligatorio en cada evento de negocio, el event store responde preguntas tipo *"¿quién publicó el plan de la semana 14?"* sin necesidad de tabla de auditoría adicional. El audit log de identidad sigue siendo la fuente para *"¿quién intentó loguearse a las 3 AM?"*.

<a id="d11"></a>
### D11 — Versionado de eventos: JSON Schema en repo + tests de compatibilidad en CI

Los eventos van a evolucionar; el contrato cambia. Versionar sin disciplina rompe consumidores silenciosamente. Esta sub-decisión fija una estrategia **ligera, sin infra adicional, coherente con el contract-first ya elegido para REST** (ADR-0001 D10).

**Mecanismo**:

- Cada tipo de evento tiene un **JSON Schema** versionado en el repositorio, en `events/<modulo>/<evento>.v<N>.schema.json`. Por ejemplo: `events/planificacion/PlanPublicado.v1.schema.json`.
- El schema es la **fuente de verdad del contrato**, igual que `api/openapi.yaml` lo es para REST (ADR-0001 D10).
- La generación de tests, la documentación de los eventos y la futura externalización a un broker se hacen contra los JSON Schema.
- Los payloads del outbox de Spring Modulith **siguen siendo JSON** en `JSONB` (ADR-0004 D6) — legibles para debugging y para la reescritura del flujo RGPD (ADR-0004 D16).

**Política de compatibilidad** (verificable en CI):

- **Cambios aditivos** (añadir campos opcionales con default): permitidos. El campo `version` del evento (D10) se **incrementa** y el JSON Schema vigente se actualiza. Los consumidores que conocen sólo `version` anteriores siguen funcionando.
- **Breaking changes** (quitar un campo, cambiar un tipo, renombrar): **prohibido en el mismo tipo de evento**. La salida correcta es **emitir un evento nuevo con tipo distinto** (`PlanPublicadoV2`) que coexiste con el original hasta que todos los consumidores hayan migrado. Cuando se cumple, el original se deprecia y se retira en una ventana de mantenimiento.
- **No** se permite versionar mediante mutaciones del schema vigente: el JSON Schema antiguo se conserva (`v1`) y se crea uno nuevo (`v2`) en el mismo directorio. Los dos viven en el repo en paralelo.

**Tests de compatibilidad en CI** (cruzan con ADR-0010):

- **Test de serialización forward**: la clase Kotlin actual del evento, cuando se serializa con Jackson, **debe cumplir el JSON Schema más reciente** del mismo tipo de evento. Si no, el PR no merge.
- **Test de retro-compatibilidad**: payloads JSON de versiones anteriores —commiteados en `src/test/resources/events/<modulo>/<evento>.v<N>.example.json` con cada cambio aditivo— **deben deserializar correctamente contra la clase actual**. Si se rompe la retro-compatibilidad sin haber creado un tipo nuevo, el PR no merge.
- **Test de presencia de los seis campos obligatorios** de D10: cualquier evento del paquete `…events.*` debe declararlos. ArchUnit.

**Lo que NO se hace**:

- **Schema Registry externo** (Confluent, Apicurio): introduce un servicio adicional que el monolito no necesita. Se reabre como decisión cuando se pase a un broker externo (post-MVP, ADR aparte si llega).
- **Avro / Protobuf**: cambian el formato del outbox a binario, pierde legibilidad del payload en BD y obliga al equipo a aprender un nuevo lenguaje de schema. JSON Schema es suficiente para nuestro caso y reutiliza disciplina ya conocida del contract-first REST.
- **Versionado solo por tipos Kotlin sin schema externo**: viable pero pierde la trazabilidad explícita del contrato versionado en el repo, dificulta el debugging y obliga al equipo a leer código para entender la forma del evento.

**Path de evolución futuro**: cuando el proyecto pase a un broker externo (Kafka, SQS), los JSON Schema se convierten a Avro/Protobuf vía herramientas existentes (`json-schema-to-avro`) — el contrato lógico se preserva y la migración es de formato, no de diseño.

<a id="d12"></a>
### D12 — Distinción entre `domain events` internos e `integration events` públicos

D4 establece que la comunicación entre módulos es events-first. Sin embargo, **no todos los eventos que ocurren dentro de un módulo deben ser visibles desde fuera**. La autonomía real de los módulos (la que justifica todo el ADR) exige diferenciar **qué se reserva al interior del módulo** y **qué se expone como contrato público al resto del sistema**. Sin esta distinción, todos los eventos terminan siendo públicos por defecto, los detalles internos se filtran y los módulos dejan de ser autónomos en la práctica aunque el ADR diga que lo son.

#### Dos categorías de evento

**Domain event interno** — un hecho que ha ocurrido dentro de un módulo, expresado en su lenguaje interno, **relevante solo para sí mismo**. Ejemplos en el módulo Planificación:

- `PlanCambioDeEstado(planId, estadoAnterior, estadoNuevo)` — disparado por el agregado al transitar estado; un listener interno crea el snapshot de membresía consultando la proyección de grupos.
- `PersonalizaciónAplicada(planId, sesionId, alumnoId)` — disparado por el agregado al añadir una personalización; un listener interno actualiza el contador `personalizaciones_por_sesion`.

No cumplen necesariamente el contrato de D10 (no necesitan `clubId`, `actorId`, etc. — son detalle de implementación interno).

**Integration event público** — un hecho que ha ocurrido en un módulo y es **relevante para otros**, parte del contrato del módulo con el resto del sistema. Ejemplos:

- `PlanPublicado(eventId, aggregateId=planId, occurredAt, version, clubId, actorId, grupoId, snapshotAlumnos, sesiones)` — Planificación lo emite; Seguimiento lo consume para crear `plan_resuelto_por_alumno`.
- `AlumnoAsignadoAGrupo(eventId, aggregateId=alumnoId, occurredAt, version, clubId, actorId, grupoId)` — Club y taxonomía lo emite; Planificación lo consume para mantener su proyección de membresía.

**Cumplen D10 íntegramente** (los seis campos obligatorios). **Cumplen D11** (versionados con JSON Schema y tests de compatibilidad en CI).

#### Mecanismos de enforcement con Spring Modulith

La distinción se materializa con **dos mecanismos combinados**, verificados por ArchUnit y por Spring Modulith en cada build (`IntegrationEventArchTest`, `ModulithFronterasTest`):

1. **Convención de paquetes**:

   ```
   com.runcriticon.planificacion/
     ├── domain/
     │     └── events/                ← domain events internos
     │           └── PlanCambioDeEstado.kt
     ├── application/                 ← listeners internos
     ├── infrastructure/              ← adaptadores
     └── api/
           └── events/                ← integration events públicos
                 └── PlanPublicado.kt
   ```

   Solo lo que vive bajo `…api/` puede ser importado por otros módulos. Verificado por ArchUnit: toda clase que implementa `IntegrationEvent` debe residir en `…api.events…` (`IntegrationEventArchTest`).

2. **`@NamedInterface` de Spring Modulith** sobre cada tipo público — **no** sobre el paquete: Kotlin no tiene equivalente de `package-info.java`, así que la anotación (`@Target({PACKAGE, TYPE})`) se aplica directamente a cada clase, forma que la propia anotación soporta explícitamente ("or assign a type to a named interface"):

   ```kotlin
   package com.runcriticon.planificacion.api.events

   @org.springframework.modulith.NamedInterface("events")
   data class PlanPublicado(/* ... */) : IntegrationEvent
   ```

   Spring Modulith trata el resto del módulo como interno. `ApplicationModules.verify()` (`ModulithFronterasTest`) falla en CI si un módulo importa una clase de `…planificacion.domain.events.*` desde fuera de Planificación. ArchUnit exige la anotación en todo `IntegrationEvent` (`IntegrationEventArchTest`), para que ningún evento nuevo se quede fuera del mecanismo por olvido.

   **Nota sobre tipos sellados**: una versión anterior de este ADR proponía `sealed interface DomainEvent` / `sealed interface IntegrationEvent : DomainEvent` en `shared` como tercer mecanismo. **No es viable**: Kotlin exige que los implementadores directos de una interfaz sellada residan en el **mismo paquete** que la interfaz sellada (verificado compilando un caso mínimo: `error: A class can only extend a sealed class or interface declared in the same package`). Como los eventos reales viven en `<módulo>.api.events` — paquete distinto de `shared.events`, por diseño (mecanismo 1) — sellar `IntegrationEvent` rompería la compilación en cuanto un módulo intentara implementarlo. `IntegrationEvent` sigue siendo una interfaz normal.

#### Patrón canónico domain → integration

El flujo típico que el equipo debe asumir como guía:

1. **El agregado emite domain events internos** cuando algo ocurre en su modelo: cambios de estado, invariantes activadas, side-effects locales necesarios.
2. **Listeners internos del mismo módulo** reaccionan a esos domain events: actualizan proyecciones internas, recalculan métricas locales, programan jobs.
3. **El módulo decide qué de eso es relevante para el resto del sistema** y emite **integration events al outbox** con la información versionada y completa (D10).

Ventaja directa: el lenguaje interno del módulo evoluciona sin contaminar el contrato externo. Si Planificación añade mañana un estado `EN_REVISION` entre `BORRADOR` y `PUBLICADO`, el domain event interno `PlanCambioDeEstado` cambia libremente — `PlanPublicado` sigue siendo el mismo contrato que los demás módulos esperan.

#### Regla pragmática para Runcriticon

La distinción se aplica con criterio (coherente con la filosofía *"hexagonal con criterio"* de ADR-0008), no como dogma:

- **Por defecto, todos los eventos del proyecto son integration events** (viven en `…api/events/`). Cumplen D10 y D11.
- **Un domain event interno se introduce solo cuando hay un caso de uso concreto**: comunicar entre agregados del mismo módulo sin contaminar el contrato externo, o expresar un cambio de estado interno que no debe ser visible a otros módulos.
- **La infraestructura de paquetes + `@NamedInterface` + ArchUnit se monta desde el día 1**, aunque inicialmente todos los eventos sean de integración. Cuando aparezca el primer caso de domain event interno, la frontera ya está sostenida por enforcement automático.

#### Implicaciones para D10 y D11

Estas dos sub-decisiones aplican únicamente a los **integration events**:

- **D10** (seis campos obligatorios + naming en pasado): contrato del integration event. Los domain events internos pueden tener una forma más libre (al menos `eventId`, `occurredAt` y nombre en pasado para mantener idiomatic events-first; pero no necesitan `clubId` ni `actorId` salvo si los necesita la lógica interna).
- **D11** (JSON Schema en repo + tests de compatibilidad): aplica solo a integration events. Los domain events internos no requieren JSON Schema versionado — son detalle de implementación, no contrato.

#### Tests críticos asociados (cruce con ADR-0010)

- **`ModulithFronterasTest`** (`ApplicationModules.verify()`): ningún módulo importa una clase de `…<otroModulo>.domain.events.*`. Solo se permite importar desde `…<otroModulo>.api.events.*` (named interface `events`).
- **`IntegrationEventArchTest`**: toda clase que implementa `IntegrationEvent` vive en `…api.events…` y lleva `@NamedInterface("events")`. No hay hoy un test equivalente para `DomainEvent` porque el tipo no existe todavía — se añade cuando aparezca el primer domain event interno real (D12 nota sobre tipos sellados: no será una jerarquía `sealed`, sino la misma convención de paquete + ArchUnit).

<a id="d13"></a>
### D13 — Política de fallos sobre Spring Modulith: 5 reintentos, DLQ implícita y endpoint de reproceso

Los consumidores van a fallar a veces. Sin política explícita, dos escenarios degradan en silencio: (1) un evento envenenado atasca el outbox indefinidamente con un *retry storm*; (2) eventos que fallan tras varios intentos se quedan en la tabla `event_publication` sin que nadie se entere — el read model que se construía a partir de ellos diverge del estado real y la aplicación muestra datos incoherentes.

Spring Modulith resuelve la **mayor parte** del problema (outbox persistente, recuperación al reiniciar, reintentos automáticos, métricas vía Micrometer). Esta sub-decisión fija lo que el equipo aún tiene que decidir y configurar.

#### Configuración de reintentos

- **5 intentos máximos** por evento. Configurable como property; vale para todos los consumidores salvo override puntual.
- **Backoff exponencial**: 1 s, 2 s, 4 s, 8 s, 16 s entre intentos. Total ~31 segundos desde el primer fallo hasta marcar el evento como fallido.
- Tras los 5 intentos, el evento queda en `event_publication` con `completion_date` `NULL` y `last_error` cargado. Spring Modulith **no lo borra** — sigue disponible para reproceso manual.

Razón del número 5: es el equilibrio típico entre absorber fallos transitorios (problemas de red, locks de BD efímeros) y no atrancar el outbox con eventos genuinamente rotos. Más reintentos no ayudan a un evento envenenado y oscurecen la causa raíz.

#### DLQ implícita: el propio outbox

**No se introduce una tabla DLQ separada.** El outbox `event_publication` actúa como DLQ implícita: los eventos con `completion_date IS NULL` y `last_error IS NOT NULL` tras los 5 intentos están "atascados", a la espera de intervención humana.

Razón: una tabla DLQ separada introduce dos migraciones (mover el evento de una tabla a otra, mover de vuelta para reprocesar), un job que las mueva, un mecanismo de purga... operación adicional sin beneficio claro a este orden de magnitud. La consulta `WHERE completion_date IS NULL AND publication_date < NOW() - INTERVAL '5 minutes'` localiza los eventos atascados con una mirada al outbox.

#### Endpoint admin de reproceso manual

`POST /admin/events/republish` permite forzar el reintento de eventos no completados. Tres modos de uso:

- **Todos los pendientes**: `POST /admin/events/republish?scope=all` reintenta todos los `event_publication` no completados.
- **Por tipo de evento**: `POST /admin/events/republish?eventType=PlanPublicado` reintenta solo los de ese tipo. Útil tras desplegar el fix de un consumidor concreto.
- **Por id específico**: `POST /admin/events/republish?eventId=<uuid>` reintenta uno solo. Útil para depuración.

Autorización: rol `ADMIN` del club (ADR-0009 cuando lo defina formalmente). En el MVP, restringido al superadmin del sistema mediante una propiedad de Spring Security. El reproceso queda registrado en el audit log de identidad (ADR-0003 D15) con el `actorId` del admin que lo disparó.

#### Métricas obligatorias (cruzan con ADR-0011)

Spring Modulith expone métricas vía Micrometer. Las **mínimas** que el sistema de observabilidad debe vigilar:

- **`modulith.events.publications.pending`** — número de eventos no completados en el outbox. Si crece sostenidamente, hay consumidores atascados.
- **`modulith.events.processing.duration`** — distribución de tiempo desde publicación hasta consumo (alimenta el NFR de p95 < 1 s de este ADR).
- **`modulith.events.failures.total{listener, event_type}`** — número de fallos por listener y tipo de evento. Detecta consumidores rotos o eventos envenenados.

#### Alarma: eventos atascados > 5 minutos

Cruce con ADR-0011. Cuando llegue, debe configurar una alarma sobre `modulith.events.publications.pending` que dispare cuando haya **> 0 eventos no completados con publicación de más de 5 minutos**. Razón: el NFR de p99 < 5 s para el lag de proyecciones (ver *Requisitos no funcionales*) descarta que un evento legítimo tarde más; cualquier cosa > 5 min es bug o consumidor caído. La alarma es la única forma de detectar el problema antes de que el usuario reporte inconsistencia.

#### Política de eventos atascados > 24 h

Cuando un evento lleva más de 24 horas sin completar (señal de bug genuino, no problema transitorio), el admin investiga. Tres acciones posibles:

1. **Corregir el bug y republicar** — el camino normal. El fix del consumidor se despliega y el endpoint admin republica los eventos atascados.
2. **Marcar manualmente como completado** — cuando se decide que ese evento ya no debe procesarse (la información ha quedado obsoleta o se ha resuelto por otra vía). Requiere justificación en el audit log de identidad con motivo explícito.
3. **Descartar** (`DELETE` del evento en el outbox) — último recurso. Documentar la causa y comunicar al equipo el efecto sobre las proyecciones afectadas.

Las tres acciones quedan registradas en el audit log de ADR-0003 D15 (no en el event store del módulo: son operaciones de mantenimiento, no eventos de negocio).

#### Lo que NO se hace

- **Sistema de reintentos propio**: Spring Modulith ya lo trae configurable.
- **Tabla DLQ separada**: el outbox es la DLQ implícita.
- **Cliente de outbox custom**: el registro de Spring Modulith es suficiente.
- **Coordinador de eventos externo** (Kafka Connect, etc.): no aplica en monolito modular con outbox local.

<a id="d14"></a>
### D14 — Ordering de eventos por clave de partición (`aggregateId`)

Spring Modulith garantiza entrega al-menos-una-vez (D6) pero **no garantiza orden estricto entre eventos de tipos distintos**, ni entre eventos del mismo tipo que pertenecen a agregados diferentes. Sin política explícita de ordering, dos bugs reales aparecen: (1) un consumidor recibe `RolAsignado` antes que `UsuarioCreado` y falla porque el usuario aún no existe en su proyección; (2) dos eventos del mismo `PlanSemanal` (`PlanPublicado` seguido de `SesionPersonalizada`) llegan en orden inverso y el read model se construye mal.

El patrón estándar para resolver esto es el de Kafka: una **clave de partición** que garantiza orden FIFO para eventos que comparten clave, sin imponer orden global. Adoptamos este patrón con la **regla más simple y más útil**: el `aggregateId` del propio evento (ya obligatorio por D10) actúa como clave de partición.

#### Garantías

| Caso | Garantía |
|---|---|
| Dos eventos con el **mismo `aggregateId`** | **Orden estricto FIFO** según orden de publicación al outbox |
| Dos eventos con `aggregateId` **distintos** | **Sin garantía de orden**; pueden procesarse en paralelo o en cualquier orden |
| Orden global entre todos los eventos del sistema | **No se garantiza**. Los consumidores deben diseñarse sin depender de él |

**Implicación para el equipo**: cualquier consumidor debe asumir que **dos eventos de aggregates distintos llegan en cualquier orden**. Esto fuerza un estilo de evento **auto-contenido**: cada integration event lleva la información que el consumidor necesita para procesarlo sin depender de que otro evento haya llegado antes. Esto encaja con D10 (los seis campos obligatorios + payload del agregado) y refuerza la autonomía de módulos (D4).

#### Implementación en MVP — single-threaded por listener

En mono-instancia (ADR-0006) con la carga de los NFRs (<200 eventos/min en pico), la implementación más simple es:

- Cada `@ApplicationModuleListener` procesa eventos **secuencialmente, en un solo hilo**, en el orden en que están en el outbox.
- Como el outbox `event_publication` mantiene el orden de publicación (con `publication_date` y PK ordenable), eventos del mismo `aggregateId` se procesan en orden natural.
- Spring Modulith configurado con `spring.modulith.events.republish-outstanding-events-on-restart: true` (`application.yml`) preserva el orden al recuperarse de un reinicio, reintentando los eventos no completados desde el outbox en su orden original.

No requiere lock pesimista, no requiere coordinación distribuida. Es suficiente para los NFRs del ADR y simple operacionalmente.

#### Implementación post-MVP — para multi-instancia

Cuando ADR-0006 active más de una instancia del backend, dos instancias podrían procesar simultáneamente eventos del mismo `aggregateId` y romper el orden. La salida correcta entonces será **una** de estas dos:

- **Lock pesimista por `aggregateId`** en el consumidor: `SELECT … FROM event_publication WHERE aggregate_id = ? FOR UPDATE SKIP LOCKED` antes de procesar. Las otras instancias saltan ese aggregate y procesan otros. Simple y suficiente para órdenes de magnitud medios.
- **Partición de eventos por hash del `aggregateId`** entre las instancias: cada instancia procesa solo los eventos cuyo `hash(aggregateId) % N_instancias == instance_id`. Coordinación vía Spring Cloud o ZooKeeper. Más eficiente pero más infra.

La elección concreta se reabre cuando el escalado lo justifique. No se adelanta al MVP.

#### Test crítico (cruce con ADR-0010)

Test de integración que verifica el invariante:

- Dado un mismo `aggregateId`, publicar dos eventos A→B en orden, en transacciones separadas.
- Verificar que el consumidor los recibe en orden A→B.
- Repetir N veces para descartar paralelismo accidental.

Y un test que verifica la **no-garantía** entre aggregates distintos, para que el equipo no confíe en algo que el sistema no promete:

- Dado dos aggregates distintos, publicar evento A1 (aggregate 1) y B1 (aggregate 2) en orden.
- El consumidor puede recibirlos en orden A1→B1 o B1→A1 — ambos son válidos.

#### Lo que NO se hace

- **Orden global de todos los eventos**: imposible sin coordinación distribuida pesada. Innecesario para los casos de uso reales.
- **Bus de eventos externo con particiones físicas** (Kafka, Pulsar): no aplica en monolito con outbox local. El path de migración a Kafka (cuando llegue) traslada esta misma decisión a particiones físicas sin cambiar el modelo mental: `aggregateId` sigue siendo la clave de partición.
- **Reordenamiento explícito en el consumidor** (recibir eventos desordenados y reordenarlos por timestamp antes de procesar): añade complejidad, no es necesario porque el outbox ya preserva el orden por `aggregateId`.

<a id="d15"></a>
### D15 — Reprocesamiento de proyecciones desde el outbox compactado

Las proyecciones locales (D9) **van a corromperse** en algún momento: un bug en el consumidor que escribe mal en la proyección, un despliegue con una migración Flyway mal aplicada, una colisión de claves no detectada. Cuando ocurra, el módulo debe poder **reconstruir la proyección desde cero** sin restaurar la BD entera desde backup (D14 de ADR-0004) y sin perder los demás módulos.

Esta sub-decisión define cómo se hace y, sobre todo, **resuelve la tensión con ADR-0004 D11**: el outbox `event_publication` retiene eventos solo 30 días y luego se compacta al **último estado válido por aggregate**. ¿Qué se puede reconstruir y qué no?

#### Lo que SÍ se puede reconstruir: estado actual

El outbox compactado contiene, por cada `aggregateId` activo, el **último evento que define su estado**. Por ejemplo:

- Cada `PlanSemanal` activo tiene un único `PlanPublicado` (cuando se publicó) → suficiente para que Seguimiento reconstruya `plan_resuelto_por_alumno` para todos los planes vigentes.
- Cada personalización tiene su último evento (`SesionPersonalizada` si está activa, `PersonalizacionRetirada` si se retiró) → suficiente para reconstruir la sección de personalizaciones del read model.
- Cada `(alumnoId, distancia)` tiene su `MarcaActualizada` más reciente → suficiente para resolver `ritmo_calculado_seg_por_km` (ADR-0002 D8).

**Conclusión**: cualquier read model del MVP que sirve **estado actual** es reconstruible. Y todos los read models del MVP sirven estado actual (la vista "hoy" del alumno no muestra histórico; el panel de alertas no necesita planes de hace 4 meses; la salud del club agrega métricas vivas).

#### Lo que NO se puede reconstruir: histórico anterior a 30 días

La compactación borra el histórico de cambios anteriores a 30 días. **Por diseño** (ADR-0004 D11): no se quiere mantener un event store creciendo sin freno. Implicación: **no se soporta reproyección histórica completa** (tipo "qué versiones del plan ha visto cada alumno a lo largo del trimestre"). Si esa necesidad aparece, se reabre con una sub-decisión de event sourcing completo — no se introduce ahora.

#### Endpoint admin de reproyección

`POST /admin/projections/rebuild` con parámetros:

| Parámetro | Valor | Significado |
|---|---|---|
| `module` | `seguimiento` | Módulo cuyo proyección se reconstruye. |
| `projection` | `plan_resuelto_por_alumno` | Identificador de la proyección concreta. |
| `dryRun` | `true` / `false` (default `false`) | Si es `true`, recorre eventos y reporta qué haría sin tocar la BD. |

El flujo:

1. **Lock de la proyección en modo "rebuilding"** — la propia tabla tiene una columna `is_rebuilding BOOL` o se usa una tabla `projection_status`; lecturas concurrentes ven el flag y deciden si responder con datos parciales o esperar.
2. **`TRUNCATE` de la tabla de proyección** (no `DROP` — las migraciones Flyway gestionan el esquema; el reproceso solo borra contenido).
3. **Reproducción de eventos**: el módulo consumidor itera sobre el outbox del/los módulo(s) origen — primero los eventos compactados (estado actual de cada aggregate), después los eventos < 30 días aún no compactados, en orden de `aggregateId` + `publication_date`.
4. **Procesamiento por el consumidor existente**: los eventos se aplican al consumidor `@ApplicationModuleListener` que ya está implementado. Como D7 (idempotencia) es invariante, reprocesar es seguro.
5. **Quitar el flag de rebuilding** y publicar evento interno `ProyeccionReconstruida(modulo, proyeccion, eventosAplicados)` para observabilidad.

Autorización: rol `ADMIN` del club (ADR-0009 cuando lo defina formalmente). Reproceso registrado en `evento_auditoria` (ADR-0003 D15) con el `actorId` del admin.

#### Durante la reproyección: experiencia de usuario

Mientras una proyección se está reconstruyendo, las lecturas que dependen de ella pueden devolver:

- **Datos parciales** (filas ya reconstruidas en este reproceso): aceptable para vistas que muestran lo que hay y se refrescan periódicamente.
- **Vacío** (si se decide bloquear lecturas hasta tener todo): conservador, mejor UX cuando los datos parciales confundirían al usuario.

La elección por proyección se documenta en su read model. Para el MVP, recomendación pragmática: **datos parciales** + banner en la UI *"Algunos datos se están reconstruyendo, los verás en unos minutos"*. Reconstruir desde cero un read model del MVP no debería tardar más de **5 min** dados los NFRs.

#### Casos no cubiertos

- **Reproyección histórica completa** (cómo evolucionó el estado a lo largo del tiempo): no soportado. Si surge necesidad real (auditoría regulatoria, time-travel debugging), se reabre.
- **Reproyección parcial por aggregate** (solo reconstruir las filas de un alumno o un plan concreto): no en MVP. Se evalúa si emerge un caso de uso operativo.
- **Reproyección entre módulos** (Seguimiento reconstruye a partir de eventos que vienen de Planificación que también está reconstruyendo): se serializa por orden topológico de D3 — primero los emisores (Identidad, Club y taxonomía), luego los consumidores (Planificación, Seguimiento).

#### Implicación crítica: eventos auto-contenidos

D15 refuerza la importancia del estilo **eventos auto-contenidos** que D14 ya pedía: cada integration event debe llevar **toda la información necesaria para que un consumidor lo procese**, sin asumir que ningún otro evento previo está disponible. Si un evento no es auto-contenido, reproyectar desde la compactación es imposible — porque la información que faltaba se compactó hace 31 días.

Concretamente: `PlanPublicado` debe llevar el snapshot completo de alumnos + las sesiones del plan, no asumir que el consumidor pueda preguntar a Planificación por esos datos a posteriori. Esto ya estaba implícito en el modelo de ADR-0002, pero D15 lo eleva a invariante.

#### Test crítico (cruce con ADR-0010)

Test de integración del flujo completo:

- Dado un módulo con una proyección poblada por eventos sintéticos.
- Corromper la proyección a propósito (`UPDATE … SET … = 'basura'`).
- Llamar al endpoint de rebuild.
- Verificar que la proyección queda reconstruida con el estado correcto.
- Repetir con la proyección vacía (`TRUNCATE`) — caso de bootstrap tras migración nueva.

## Consecuencias

### Positivas

- Operación simple: un desplegable, una BD, un *pipeline*. Coherente con ADR-0006.
- Código razonable por partes; onboarding más fácil.
- **Módulos autónomos**: no se conocen entre sí, solo sus eventos.
- Extracción de servicios futura **casi inmediata** (mitiga R6) — los módulos ya se comunican como servicios.
- Consistencia transaccional sencilla **dentro** de cada módulo.

### Negativas / coste asumido

- *Events-first* añade complejidad: **consistencia eventual** como norma entre módulos, más eventos y proyecciones que diseñar y testear, idempotencia obligatoria.
- Exige **disciplina** y enforcement; sin ello, degenera en monolito tradicional.
- Pensar las fronteras y los eventos desde el principio cuesta algo más que arrancar sin ellos.

### Riesgos y mitigaciones

- **Erosión de las fronteras entre módulos** → Spring Modulith verificando las dependencias en cada build (D8); revisión de código atenta a los *imports* cruzados.
- **Consistencia eventual mal gestionada** (proyecciones que divergen, eventos perdidos) → *outbox* de Spring Modulith (D6, entrega al menos una vez), consumidores idempotentes (D7), tests de las proyecciones.
- **Descomposición de módulos o eventos equivocada** → la propuesta de D2 es un punto de partida; revisarla al modelar el dominio (ADR-0008) y de nuevo si una funcionalidad nueva no encaja en ninguno de los cuatro módulos (señal de que falta un quinto o de que dos están solapados).
- **Tentación de microservicios prematura** → no se extrae ningún módulo a servicio hasta que un problema real (escala, equipos independientes) lo justifique.

## Notas

- El paso a multi-club no obliga por sí solo a microservicios: un monolito modular puede ser multi-tenant. La extracción de servicios es una decisión aparte, posterior y guiada por necesidad real.
- La estructura interna de cada módulo (hexagonal) y el grado de DDD se deciden en ADR-0008.
- La comunicación *events-first* condiciona el ADR-0009: la autorización a nivel de objeto resuelve las relaciones contra una **proyección local**, no consultando a otro módulo.
- El plan de formación [`docs/formacion/arquitectura-dirigida-por-eventos.md`](../formacion/arquitectura-dirigida-por-eventos.md) acompaña a este ADR.
- **Observabilidad detallada de events-first → ADR-0011**. D13 fija las **métricas mínimas obligatorias** (`modulith.events.publications.pending`, `modulith.events.processing.duration`, `modulith.events.failures.total{listener, event_type}`) y la **alarma crítica** (eventos no completados con publicación > 5 min). El **cierre completo** —dashboards, alarmas afinadas, integración con la pila concreta (Datadog, Grafana, CloudWatch), correlación de trazas— vive en ADR-0011 cuando se cierre. Este ADR aporta el listado de qué debe observarse; ADR-0011 decide con qué y cómo.
- **Playbook de extracción de un módulo a microservicio** (cuando llegue, no MVP). Si en el futuro un módulo necesita extraerse (multi-club con escalado independiente, equipo dedicado, requisito de aislamiento), la **secuencia de pasos** ya está condicionada por las decisiones de este ADR — la extracción es trabajo acotado, no reescritura:
  1. **Levantar BD propia** del módulo: copiar el schema (ADR-0004 D3, D4) a una nueva instancia, replicar mediante Flyway desde la migración 1.
  2. **Migrar los datos vivos** del schema original a la nueva BD en una ventana de mantenimiento corta.
  3. **Sustituir el publicador de eventos** (`PublicadorDeEventos` en Spring Modulith) por un adaptador a broker externo (Kafka, SQS, RabbitMQ). El contrato de los eventos (D10) **no cambia** — los seis campos siguen siendo los mismos. El versionado (D11) tampoco; los JSON Schema se traducen a Avro/Protobuf si el broker lo prefiere.
  4. **Sustituir los consumidores cross-módulo** en los demás módulos: los `@ApplicationModuleListener` que escuchaban el módulo extraído ahora se suscriben al topic correspondiente del broker. La idempotencia (D7) y la clave de partición `aggregateId` (D14) se preservan.
  5. **Levantar los endpoints REST del módulo** como servicio HTTP independiente. Spring Boot con el mismo código del módulo es suficiente; cambia solo el descriptor de despliegue.
  6. **Cortar el tráfico** del monolito hacia el módulo extraído. Los demás módulos siguen operando sin cambios porque la comunicación seguía siendo events-first.
  
  La duración estimada es de **días, no semanas**, gracias a que el módulo ya cumple D2 (bounded context propio), D4 (events-first), D9 (proyecciones locales) y D14 (ordering por aggregateId). El playbook completo con detalles operativos (rollback, observabilidad, coordinación con consumidores) se redactará entonces como ADR aparte.
- **Revisión del 2026-05-29 (Nivel 1 + ciclo completo de events-first)**: el ADR se reestructura con índice, premisas heredadas y NFRs explícitos, y se numeran las sub-decisiones D1-D15 con anchors para que cada una sea localizable y revisable de forma independiente. Se incorporan seis sub-decisiones nuevas que cierran el ciclo completo de events-first: **D10 — Contrato de eventos** (seis campos obligatorios, naming en pasado, distinción explícita con el `evento_auditoria` de ADR-0003 D15); **D11 — Versionado con JSON Schema** (coherente con el contract-first REST del ADR-0001 D10, sin infra adicional, con tests de compatibilidad forward y retro en CI); **D12 — Distinción `domain event` interno vs `integration event` público** (paquetes + `@NamedInterface` + tipos sealed + ArchUnit como triple mecanismo de enforcement); **D13 — Política de fallos sobre Spring Modulith** (5 reintentos con backoff exponencial, outbox como DLQ implícita, endpoint admin de reproceso, alarma cruzada con ADR-0011 y política de eventos atascados > 24 h); **D14 — Ordering por clave de partición** (`aggregateId` como clave; orden FIFO garantizado por aggregate, sin garantía entre aggregates distintos; implementación MVP single-threaded; path post-MVP con lock pesimista o partición por hash); y **D15 — Reprocesamiento de proyecciones desde el outbox compactado** (resuelve la tensión con ADR-0004 D11: estado actual es reconstruible vía la compactación; histórico anterior a 30 días explícitamente fuera de alcance; endpoint admin de rebuild con flag de "rebuilding" + banner UI; eventos auto-contenidos elevados a invariante). Se añaden además dos notas de cruce con otros ADRs: la **observabilidad detallada** delegada a ADR-0011 (con las métricas mínimas obligatorias ya ancladas en D13) y el **playbook de extracción a microservicio** con la secuencia operativa que las sub-decisiones de este ADR ya habilitan. Alineado con ADR-0001, ADR-0002, ADR-0003 y ADR-0004 ya aceptados.
- **Revisión del 2026-07-11 (D10, D12)**: D12 afirmaba tres mecanismos de enforcement "montados desde el día 1"; solo la convención de paquetes existía. El mecanismo de tipos `sealed` **no era viable**: probado empíricamente que Kotlin exige que los implementadores de una interfaz sellada vivan en su mismo paquete, incompatible con eventos repartidos por `<módulo>.api.events`. Sustituido por `@NamedInterface` aplicado a cada clase de evento (no al paquete — Kotlin no tiene `package-info.java`) + `IntegrationEventArchTest` nuevo, que sí verifica ambos mecanismos contra los 3 eventos reales del proyecto. D10 afirmaba un test ArchUnit de naming (participio pasado) y de herencia de un tipo `DomainEvent` que tampoco existían; corregido a lo que `IntegrationEventArchTest` cubre realmente. Detectado por auditoría de drift documentación-código (23 docs, 61 hallazgos).
