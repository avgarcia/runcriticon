# ADR-0017 — Mecanismo de jobs de retención: Spring `@Scheduled`

- **Estado**: Aceptado
- **Fecha**: 2026-09-18 · **aceptado 2026-09-18**
- **Decisores**: Negocio (Antonio) · Claude
- **Relacionado con**: ADR-0004 D11 (retención de `event_publication`), ADR-0006 D4 (autoescalado App Runner), ADR-0007 (monolito modular, events-first), ADR-0014 D10 (política de retención por categoría RGPD)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre cómo se ejecutan en producción las purgas de retención que otros ADRs ya decidieron en abstracto pero cuyo mecanismo técnico quedó pendiente. Las 8 sub-decisiones se agrupan en 3 áreas:

- **Mecanismo y patrón (D1-D3)** — qué tecnología, cómo se estructura el código, por qué no hace falta lock distribuido.
- **Aplicación concreta (D4-D6)** — las tres purgas pendientes que este ADR desbloquea.
- **Testing y configuración (D7-D8)** — cómo se verifica y cómo se ajusta el horario.

| #   | Sub-decisión                                                                          | Capa         |
|-----|----------------------------------------------------------------------------------------|--------------|
| D1  | [Spring `@Scheduled` dentro del monolito, no `pg_cron` ni job externo](#d1)             | Estratégica  |
| D2  | [Un job de retención por tabla, en `infrastructure/scheduling` del módulo dueño](#d2)   | Operativa    |
| D3  | [Sin lock distribuido: los jobs son `DELETE` idempotentes](#d3)                         | Operativa    |
| D4  | [`club_taxonomia`: `persona_eliminada` + `evento_procesado`, 30 días](#d4)              | Operativa    |
| D5  | [`auditoria.evento`, 24 meses](#d5)                                                     | Operativa    |
| D6  | [`event_publication` (compartida), 30 días de filas completadas](#d6)                   | Operativa    |
| D7  | [Test de integración obligatorio con datos sembrados de fecha antigua](#d7)             | Operativa    |
| D8  | [Horario configurable por `application.yml`, fuera de hora punta](#d8)                  | Operativa    |

## Contexto y problema

Tres purgas de retención llevan pendientes desde que se aceptaron los ADRs que las decidieron en abstracto, todas por el mismo motivo: introducir el primer `@Scheduled` del repo se fue aplazando hasta tener un caso de uso (AC) que lo exigiera de verdad.

- **`club_taxonomia.persona_eliminada` + `evento_procesado`** ([LAL-107](https://linear.app/lalin1982/issue/LAL-107)): las lápidas de supresión y las marcas de idempotencia de listeners crecen sin límite. Solo hacen falta mientras pueda llegar un evento rezagado del outbox (30 días); pasada esa ventana son inertes. Los índices `persona_eliminada_eliminado_en_idx` y `evento_procesado_processed_at_idx` ya existen para esto — sembrados en sus migraciones (agosto 2026) previendo este ADR.
- **`auditoria.evento`**: `RGPD.md` y `README.md` del módulo documentan explícitamente la retención pendiente: *"no hay ningún `@Scheduled` precedente en el repo — introducir el primero sin un AC que lo pida quedó fuera de esta entrega"*.
- **`event_publication`** (outbox de Spring Modulith, tabla compartida): ADR-0004 D11 afirma que existe *"un job de retención... cubierto por tests"* que purga a los 30 días las filas completadas. **No existe ni el job ni el test** — divergencia entre lo documentado y lo real, encontrada al investigar LAL-107. `event_publication_by_completion_date_idx` ya existe, pensado para esta consulta.

`MergeSuggestionListener` (LAL-96) es la prueba de que el aplazamiento fue deliberado, no un olvido: su KDoc dice literalmente que se diseñó vía outbox *"sin introducir el primer `@Scheduled` del repo"*. Este ADR es el que lo autoriza.

## Premisas heredadas (no se revisan en este ADR)

- **Plazos de retención por categoría ya fijados** (ADR-0014 D10): PII primaria 30 días de gracia tras la baja, auditoría de identidad 12 meses, auditoría de autorización 24 meses, outbox 30 días, backups 30 días, logs operativos 90 días. Este ADR no redecide ningún plazo — solo el mecanismo técnico que los ejecuta. **Corrección de premisa**: la fila "Outbox — Job interno de Spring Modulith" de esa tabla asumía que Spring Modulith purga sus propias filas completadas automáticamente; no lo hace (la librería trackea `completion_date` pero no borra nada por sí sola). D6 de este ADR cierra ese hueco con el mismo mecanismo que el resto.
- **Autoescalado App Runner: min 1, max 3 instancias, sin lock compartido en MVP** (ADR-0006 D4). Con `min ≥ 2` la sesión migraría a Redis, pero ningún job de este ADR toca sesión — es la concurrencia de *varias instancias ejecutando el mismo `@Scheduled`* lo que hay que resolver aquí (D3).
- **Monolito modular, events-first, sin llamadas síncronas entre módulos** (ADR-0007). Un job de retención no es un caso de uso de negocio ni consume eventos: corre dentro del proceso, contra el schema de su propio módulo (o, para `event_publication`, contra la tabla compartida del outbox).
- **Retención de `event_publication` a 30 días de filas completadas, nunca de filas con `completion_date IS NULL`** (ADR-0004 D11). Ya decidida; este ADR implementa el job que faltaba.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| Ventana de ejecución | Franja de baja concurrencia (madrugada, hora local del club piloto) |
| Duración máxima por ejecución | Debe terminar antes de la siguiente ventana — a esta escala (unas pocas decenas de miles de filas como mucho) un `DELETE` con índice es cuestión de milisegundos |
| Bloqueo de tabla | Ninguno perceptible: `DELETE ... WHERE <indexado>` no requiere `LOCK TABLE` ni ventana de mantenimiento |
| Idempotencia ante reintento o solape | Total — ejecutar el mismo `DELETE` dos veces (mismo u otro instante) dejando cero filas fuera de rango es el resultado correcto, no un error |

## Drivers de la decisión

- Cerrar tres purgas ya pendientes con **un solo mecanismo**, no tres soluciones distintas.
- Equipo de 4 sin perfil de operaciones de BD dedicado — mínima carga operativa nueva.
- No introducir infraestructura AWS nueva en MVP salvo que un límite real de App Runner lo fuerce (ADR-0006 D5 documenta Fargate como evolución, no aplica aquí).
- Los jobs son housekeeping puro (`DELETE` por fecha), sin lógica de dominio ni eventos que publicar — no justifican el peso de una cola o un runner externo.

## Opciones consideradas

- **Opción A** — Spring `@Scheduled` dentro del monolito
- **Opción B** — `pg_cron` en RDS
- **Opción C** — Job externo (AWS EventBridge Scheduler + Lambda, o tarea programada de ECS)

### Opción A — Spring `@Scheduled` dentro del monolito

Un bean por job, anotado `@Scheduled(cron = ...)`, ejecutando el `DELETE` vía `JdbcTemplate` dentro de la propia aplicación Spring Boot ya desplegada.

- 👍 Cero infraestructura nueva: vive en el código ya desplegado, se prueba con Testcontainers como cualquier otro componente del módulo.
- 👍 El cron vive en `application.yml`, versionado junto al código — ajustarlo es una PR normal, no un cambio de Terraform.
- 👍 Reutiliza observabilidad ya existente (MDC, `{Modulo}Metrics`, logs a CloudWatch vía ADR-0011) sin nada adicional.
- 👎 Con `max = 3` instancias, el cron puede dispararse en más de una a la vez (D3 explica por qué no es un problema real para estos jobs).

### Opción B — `pg_cron` en RDS

La extensión `pg_cron` de PostgreSQL ejecuta el `DELETE` directamente en la base de datos, con su propia tabla `cron.job` de programación.

- 👍 Corre aunque la aplicación esté caída o entre despliegues.
- 👎 Requiere habilitar la extensión en RDS (cambio de Terraform, posible reinicio del parameter group) — sin precedente en ADR-0006, que hoy no la menciona.
- 👎 El cron y su lógica quedan fuera del repositorio de código: se pierde el versionado, la revisión de PR y el test de integración con Testcontainers que sí tiene la Opción A.

### Opción C — Job externo (EventBridge Scheduler + Lambda / tarea ECS)

Un disparador de AWS invoca una función o tarea separada que ejecuta el `DELETE` contra RDS.

- 👍 Desacoplado del ciclo de vida del monolito.
- 👎 Introduce un componente de cómputo nuevo (Lambda o ECS) para tres `DELETE` de housekeeping — desproporcionado a esta escala y contradice el driver de mínima operación de ADR-0006 D3 (App Runner elegido precisamente por bajo esfuerzo operativo).
- 👎 Necesita su propio acceso de red a RDS (otro `VPC Connector` o *security group*) y su propia gestión de secretos — duplica lo que ADR-0006 D12/D13 ya resuelve para la app.

## Decisión

**Opción A — Spring `@Scheduled` dentro del monolito.** Es la única que no añade infraestructura ni saca la lógica de retención del ciclo de vida normal de PR + test + despliegue que ya gobierna el resto del código (ADR-0010), y el inconveniente de la Opción A (solape entre instancias) no es un riesgo real para `DELETE`s idempotentes (D3).

Las 8 sub-decisiones se desarrollan a continuación. Las tres primeras son **el patrón técnico** (D1 estratégica, D2-D3 operativas); las cinco restantes son operativas y aplican ese patrón a los tres huecos identificados.

<a id="d1"></a>
### D1 — Spring `@Scheduled` dentro del monolito, no `pg_cron` ni job externo

Ver "Opciones consideradas". Primer uso de `@Scheduled` en el repo — habilita `@EnableScheduling` en la configuración de arranque de la aplicación.

<a id="d2"></a>
### D2 — Un job de retención por tabla, en `infrastructure/scheduling` del módulo dueño

Sin abstracción compartida entre módulos: cada purga es un `@Component` propio (p. ej. `PersonErasureRetentionJob`, `AuditEventRetentionJob`) en el paquete `infrastructure/scheduling/` del módulo dueño de la tabla, con su `DELETE` vía `JdbcTemplate` — mismo criterio de "SQL plano en infraestructura, sin JPA" que ya usan `PersonProjectionJdbc` y el resto de adaptadores de escritura directa.

Una abstracción compartida (`RetentionJob` genérico en `shared`) se descarta a propósito: acoplaría módulos que hoy no se conocen entre sí (ADR-0007 D2) por un beneficio mínimo — son 3 jobs, cada uno con su tabla, su predicado y sus métricas. Si el número de purgas crece sustancialmente, se reabre este punto.

**Excepción**: la purga de `event_publication` (D6) no pertenece a ningún módulo — es la tabla compartida del outbox de Spring Modulith (ADR-0007 D6). Vive en `shared/events/infrastructure/scheduling/`, junto al resto de la fontanería de eventos compartida.

Cada job expone una métrica de filas borradas por ejecución, siguiendo la convención de `{Modulo}Metrics` (ADR y guía de observabilidad — cardinalidad baja, sin IDs).

<a id="d3"></a>
### D3 — Sin lock distribuido: los jobs son `DELETE` idempotentes

Con `max = 3` instancias (ADR-0006 D4), el mismo `@Scheduled` puede dispararse en más de una instancia si sus relojes casi coinciden. No hace falta `ShedLock` ni ningún mecanismo de lock distribuido: un `DELETE ... WHERE fecha < corte` ejecutado dos veces a la vez no duplica nada ni dejan las dos transacciones un estado inconsistente — la segunda simplemente no encuentra filas que borrar. El único coste es una query de más, despreciable a esta frecuencia (diaria o mensual).

Esta sub-decisión aplica **solo a jobs de purga por fecha, sin efectos colaterales** (no publican eventos, no envían email, no llaman a un tercero). Un futuro job de retención que sí tenga efectos no idempotentes deberá reabrir este punto y evaluar `ShedLock` o equivalente.

<a id="d4"></a>
### D4 — `club_taxonomia`: `persona_eliminada` + `evento_procesado`, 30 días

Cierra [LAL-107](https://linear.app/lalin1982/issue/LAL-107). Dos `DELETE` en el mismo job (o dos jobs hermanos, a decidir en la implementación):

- `persona_eliminada WHERE eliminado_en < now() - INTERVAL '30 days'`
- `evento_procesado WHERE processed_at < now() - INTERVAL '30 days'`

30 días porque ninguna lápida ni marca de idempotencia hace falta más allá de la ventana de reentrega del outbox (ADR-0004 D11). Ambas tablas son `SIN_PII` (categoría RGPD 0): esta purga es housekeeping de outbox, no ejercicio de un derecho RGPD.

<a id="d5"></a>
### D5 — `auditoria.evento`, 24 meses

Aplica directamente ADR-0014 D10, categoría 3 (auditoría de autorización): *"Cron mensual purga filas con `ts < now() - 24 months`"*. Cierra el pendiente documentado en `auditoria/RGPD.md` y `auditoria/README.md`.

Es independiente de `AuditTrailAnonymizationListener` (que anonimiza `actor_id`/`sujeto_id` al recibir `AlumnoEliminado`/`EntrenadorEliminado` — `auditoria/RGPD.md`): la purga por edad borra la fila entera pasados 24 meses, esté o no ya anonimizada.

<a id="d6"></a>
### D6 — `event_publication` (compartida), 30 días de filas completadas

Cierra el hueco real detrás de ADR-0004 D11: `DELETE FROM event_publication WHERE completion_date IS NOT NULL AND completion_date < now() - INTERVAL '30 days'`. La guarda `completion_date IS NOT NULL` es **la condición de seguridad de todo este ADR** — un evento con `completion_date` nulo sigue pendiente o fallado (DLQ implícita, ADR-0007 D13) y este job no debe tocarlo nunca. D7 exige el test que lo verifica explícitamente.

<a id="d7"></a>
### D7 — Test de integración obligatorio con datos sembrados de fecha antigua

Cada job de este ADR lleva un test de integración (Testcontainers, patrón de `docs/arquitectura/testing-de-modulos.md`) que siembra filas dentro y fuera de la ventana de retención — para `event_publication`, además, una fila `completion_date IS NULL` de fecha antigua que **no** debe borrarse. Sin este test, un bug en el predicado borra un evento aún pendiente de entrega (riesgo explícito ya anotado en ADR-0004 D11) o una lápida/marca que todavía hace falta.

<a id="d8"></a>
### D8 — Horario configurable por `application.yml`, fuera de hora punta

El cron de cada job es una propiedad `@ConfigurationProperties` en `application.yml` (no *hardcoded* en la anotación), con un valor por defecto de madrugada (p. ej. `0 30 3 * * *`). No es un secreto — no pasa por la convención SSM de ADR-0013, es configuración operativa normal.

## Lo que este ADR no decide

- **Los plazos de retención por categoría** — ya fijados en ADR-0014 D10; este ADR solo decide cómo se ejecutan.
- **El nombre exacto de las clases y el paquete final** — D2 fija el criterio (`infrastructure/scheduling`, un job por tabla, sin abstracción compartida); el detalle se resuelve en cada PR de implementación.
- **Purga de la categoría 1 (PII primaria, cuentas en gracia tras la baja)** ni de **backups** o **logs operativos** — D10 de ADR-0014 las menciona, pero ninguna tiene hoy un AC que las reclame; se implementan con este mismo mecanismo cuando llegue su ticket, sin necesidad de otro ADR.
- **`ShedLock` o cualquier lock distribuido** — deliberadamente fuera de alcance mientras los jobs sean `DELETE` idempotentes (D3).

## Consecuencias

### Positivas

- Cierra tres purgas pendientes (una de ellas, `event_publication`, corrige una afirmación incorrecta de un ADR ya Aceptado) con un solo patrón, reutilizable para las purgas de categoría 1/5/6 que aún no tienen ticket.
- `persona_eliminada`, `evento_procesado` y `event_publication` dejan de crecer sin límite.
- El patrón queda documentado y probado la primera vez, no reinventado tres veces.

### Negativas / coste asumido

- Primer `@Scheduled` del repo: a partir de aquí, cualquier futuro job debe justificar por qué no sigue este mismo patrón (o reabrir D1-D3 si de verdad no encaja).
- Sin lock distribuido, un job puede ejecutarse hasta 3 veces seguidas en el peor caso de solape entre instancias — coste real: unas pocas queries de más al día, no un problema (D3).

### Riesgos y mitigaciones

- **Un predicado mal escrito borra filas que todavía hacen falta** (evento pendiente del outbox, lápida aún dentro de ventana) → test de integración obligatorio con datos sembrados de fecha antigua y, para `event_publication`, una fila `completion_date IS NULL` que debe sobrevivir (D7).
- **Un futuro job de retención con efectos no idempotentes reutiliza este patrón sin lock** → D3 acota explícitamente su alcance a `DELETE`s sin efectos colaterales; cualquier job que no cumpla esa condición reabre la sub-decisión.

## Notas

- Implementación repartida en PRs por módulo: `club_taxonomia` (LAL-107), `auditoria` y `shared.events` (`event_publication`) como tickets de seguimiento aparte, todos bajo este mismo ADR.
- **Revisión periódica**: no aplica una cadencia fija; se reabre si el número de jobs de retención crece lo suficiente para justificar la abstracción compartida descartada en D2, o si `min` de App Runner sube de 1 y aparece un job con efectos no idempotentes (dispara la revisión de D3).
