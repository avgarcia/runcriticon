# ADR-0007 — Monolito modular

- **Estado**: Propuesto
- **Fecha**: 2026-05-20 · revisado 2026-05-29 (reorganización Nivel 1: índice + premisas heredadas + NFRs + numeración de sub-decisiones D1-D9 con anchors estables; sin cambios en el contenido técnico)
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack), ADR-0002 (modelo de datos), ADR-0004 (esquema por módulo, retención de eventos), ADR-0005 (email por eventos), ADR-0006 (infraestructura), ADR-0008 (arquitectura hexagonal y DDD), ADR-0009 (autorización), ADR-0010 (CI/CD), ADR-0011 (observabilidad), ADR-0014 (RGPD), `risks.md` (R6 — deuda mono-tenant al generalizar)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre la **forma del backend**. Las nueve sub-decisiones se agrupan en tres áreas:

- **Topología y fronteras (D1-D3)** — qué forma tiene el backend, cómo se reparte y cómo se relacionan las partes.
- **Comunicación (D4-D7)** — cómo hablan los módulos entre sí y bajo qué garantías.
- **Enforcement y lectura (D8-D9)** — cómo se sostienen las fronteras y cómo se sirve la lectura.

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

Las nueve sub-decisiones desarrolladas a continuación. Cinco son **estratégicas** (D1, D2, D3, D4, D8 — topología, descomposición, dependencias, communication paradigm, enforcement); el resto son **operativas** (D5, D6, D7, D9 — transacciones, outbox, idempotencia, proyecciones) y derivan o implementan las anteriores.

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
- **Revisión del 2026-05-29 (Nivel 1 parcial)**: el ADR se reestructura con índice, premisas heredadas y NFRs explícitos, y se numeran las sub-decisiones D1-D9 con anchors para que cada una sea localizable y revisable de forma independiente. **No se introducen nuevas sub-decisiones** en esta pasada: las que la revisión identificó como pendientes (contrato de eventos, versionado, política de fallos, ordering, reprocesamiento de proyecciones, distinción `domain event`/`integration event`) quedan abiertas para una segunda tanda. Alineado con ADR-0001, ADR-0002, ADR-0003 y ADR-0004 ya aceptados.
